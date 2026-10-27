# 0004. Vein extents and the per-chunk vein index

Status: proposed, 2026-10-27 (E0-S6, #224); Amendment 1 (E0-S12, #231) at the end. Accepted once the
carry-over spike (E6-S4, #209) and the size measurement are written into the pending sections below and the
matrix reviewers have signed. Spec: §3.1,
§3.4, §11 Vein Index; S20 (`docs/TEAM_REQUIREMENTS.md`); `docs/research/vanilla-ore-generation.md` §5.

Source facts are from the decompiled, NeoForge-patched Minecraft sources of `neoforge-26.1.2.95`, read on
2026-10-27 (E1-S5, #195); paths are inside that jar. NeoForge's own classes (`AttachmentInternals`,
`AttachmentHolder`, the explosion event) were not in the unpacked set.

## Context

Many nodes need to know where a vein is or when it is finished (§11 Vein Index lists them). A flood
fill from each broken ore is too slow in a 60%-ore dimension, so the generator, which places every vein,
records what it placed.

The old tracker designed this twice. Old #45 let veins cross chunk borders: each chunk re-derived its 3×3
neighbourhood's veins from the seed and wrote only its own blocks. Old #91 built the index on that: a
`veinId = (originChunkX, originChunkZ, ordinal)`, membership stored in every chunk the vein touched, the
remaining count only in the origin chunk, reached by a synchronous `level.getChunk`, and Twin Veins'
copies numbered from `1 << 16`. Old #141 listed what the consumers need. S20 and the vanilla research then
kept every vein inside its chunk, which removes the cross-chunk half of #91.

What the 26.1.2.95 sources say about chunk attachments:

- `ChunkAccess` implements `IAttachmentHolder` and holds `AttachmentHolder.AsField attachmentHolder`
  (`net/minecraft/world/level/chunk/ChunkAccess.java` lines 60 and 485 to 486). `setData` and `removeData`
  call `markUnsaved()` first (lines 511 to 521).
- `ImposterProtoChunk#getAttachmentHolder` returns the wrapped `LevelChunk`'s holder
  (`ImposterProtoChunk.java` lines 287 to 288).
- The `LevelChunk` constructor that promotes a `ProtoChunk` calls
  `AttachmentInternals.copyChunkAttachmentsOnPromotion(level.registryAccess(),
  protoChunk.getAttachmentHolder(), this.getAttachmentHolder())` (`LevelChunk.java` line 158).
- That constructor runs in `ChunkStatusTasks#full`, submitted to `context.mainThreadExecutor()`, which then
  posts `ChunkEvent.Load(levelChunk, !(protoChunk instanceof ImposterProtoChunk))` on the main thread
  (`net/minecraft/world/level/chunk/status/ChunkStatusTasks.java` lines 173 to 209). The flag is true the
  first time a chunk becomes a `LevelChunk`.
- `fillFromNoise` is called from `ChunkStatusTasks#generateNoise` (lines 83 to 88) on a generation
  worker.
- Chunk saves serialise attachments for every `ChunkAccess`, `ProtoChunk` included:
  `SerializableChunkData#copyOf` calls `chunk.writeAttachmentsToNBT(...)` and catches and logs an exception
  so the chunk still saves without them (`net/minecraft/world/level/chunk/storage/SerializableChunkData.java`
  lines 432 to 437); loading calls `readAttachmentsFromNBT` (lines 344 to 346). `ChunkMap` builds the copy
  synchronously and encodes it on `Util.backgroundExecutor()` (`net/minecraft/server/level/ChunkMap.java`
  lines 767 to 769).

The body of `copyChunkAttachmentsOnPromotion`, and so which attachment types it copies, is NeoForge code
not in the unpacked set. The gametest spike (E6-S4) decides it in play.

## Decision

### Placement returns extents; veins stay inside their chunk (S20)

`core.gen.VeinPlacer` (pure, ADR 0001) returns each vein as a value, `VeinExtent`: the vein's box origin and
size in chunk-local coordinates, its standing positions as a bitset over the box, its ore (the classifier's
grouped ore id, so stone and deepslate variants are one ore), its ordinal and whether it is a guide vein.
The generator writes blocks from these values; the index is built from the same values. Nothing reads the
world to find a vein.

The shape is vanilla's (`core.gen.VeinShape`, the port in `docs/research/vanilla-ore/orefeature.py`),
rolled about its origin. The placer takes the rolled shape's actual bounding box and places the origin
uniformly where that box fits inside the chunk's 16 by 16 columns and the band's Y range.

The bound, asserted in code: a vein of configured size `s` spreads at most `s/8` from its origin along its
axis, plus a sphere radius of at most `s/16 + 1/2`, so its horizontal span after flooring is at most
`3s/8 + 2` blocks. That is at most 16 for `s ≤ 37` and over 16 from `s = 38`. `VeinShape.MAX_IN_CHUNK =
37` is a constant with a unit test that computes the worst case from the formula and one that rolls the
shape at 37 many times and checks every box. A request above 37 is split into the fewest veins of equal
size at or under 37 whose expected block total matches the request (blocks per vein grow faster than size,
so this is not `s / n`; the table from the shape port is the oracle). The requests that reach it are a Guide
Vein (configured size 24) under the larger Vein Expansion tiers, and common veins only if size bonuses stack
past about +200%; how Vein Expansion and Volatile Veins' +25% combine is the Balancer's (§11 composition).
A 4,000-roll estimate from the port puts the largest observed span at 12 blocks for size 37 and 16 for size
48, so the formula is conservative.

Within a chunk, veins are placed in ordinal order. A later vein that overwrites an earlier vein's block
takes it: the earlier vein's bit is cleared before the extent is returned. Gravel and clay are placed after
ore and replace stone only, and Molten Seam's shells are placed before ore and refuse it (§3.1, §6.1). So at
generation the index matches the world exactly.

### The attachment

One chunk attachment, `orevault:vein_index`, registered in `registry` with a codec. Its id is save data
from the first release (ADR 0005). It is written only for the chunk being generated, by the generator for
that chunk (or by the main thread from the pending map, below), and after that only on the main thread.

Encoding, chosen for size (measurement below):

- `data_version`: the attachment's shape version, 1 (ADR 0005's key, as every shape has).
- `ores`: a palette, the list of grouped ore ids used in this chunk. A vein refers to an index.
- `veins`: `long[]`, one header per vein: box origin (x 4 bits, z 4 bits, y 9 bits for 384 heights), box
  size (x and z 4 bits each, stored minus one; y 5 bits), palette index (8 bits), ordinal (17 bits) and
  flags (`started`, `completed`, `vanished`, `clean_cut_broken`, `guide`, `twin`). The exact bit layout is
  in `core.vein.VeinRecord` and pinned by a golden test, so changing it is a version bump with a
  migration.
- `bits`: `long[]`, the standing bitsets of all veins, concatenated in header order. A vein's offset and
  length follow from the box sizes before it, so they are not stored.
- The chunk fields listed below.

The remaining count is the bitset's population count: derived, not stored (ADR 0005's rule). Completion is
the step that empties it.

Ordinals: generated veins take 0 to 65,535 (`< 1 << 16`). Twin Veins copies take `1 << 16` upwards, as old
#91 did, so a copy is recognisable and never collides with a generated vein.

### What changes the index

A position is standing only while its bit is set. Every removal clears the bit, so a position is decremented
at most once, and ore later placed at a cleared position is never indexed: that is how player-placed ore
stays out (§11) without a check on placement.

| Cause | Index | Completion effect |
|---|---|---|
| Hand break (§3.4), including each block of an FTB Ultimine chain (§9) | Clears the bit in the single `BlockDropsEvent` listener (`mining`) | Fires if this break empties the vein. An Ultimine operation resolves completion once for the operation (old #141) |
| Machine break, fake player, no breaker (§3.4) | Clears the bit in the same listener | Never. The vein is marked completed silently and counts toward nothing (§3.4: no Chapter's End count) |
| Brittle Stone's destroyed neighbour (§6.1) | Cleared as part of the hand break that caused it | Counts toward completion as that hand break (§6.1); if it is the vein's last block, completion fires for that break |
| Budding Seam (§6.1) | The hand break clears the bit; the budding block placed after it is not ore and is not indexed | As a hand break |
| Volatile Veins' vanish (§6.1) | The mod sets the rest of the vein to air and clears every bit; flag `vanished` | No completion. Whether a vanished vein counts as completed or abandoned for Vein Discipline is the Balancer's (open, `docs/research/manager-notes.md`); the index reports `VANISHED` and records nothing else |
| Explosion: TNT, a creeper. Blast-Wise changes explosion damage to players and no blocks | Not watched by a listener. Cleared by reconciliation (below) | Never |
| Twin Veins' copy | Adds a vein with a new ordinal from the reserved range, in the same chunk (below) | Its own completion later, as any vein |
| Another mod's `setBlock`, a piston, a creative break (which never reaches `dropResources`, old #149) | Cleared by reconciliation | Never |

Reconciliation: whenever the index touches a vein (every decrement, a Vein Sight or Prospector's Eye
outline, Chapter's End's reveal, Stonecaller's chosen vein, an abandon check), it reads the vein's standing
positions in the loaded chunk and clears every bit whose block is no longer in the vein's ore group. Cost is
bounded by the vein's size: a size-37 vein averages about 153 blocks in the shape port. A vein emptied by reconciliation alone is
completed silently. If a hand break empties a vein whose other blocks were cleared by reconciliation in the
same step, the hand break was the last ore taken, so completion fires.

When count and world disagree the world wins: a set bit over a non-ore block is cleared silently; ore
over a clear bit is not part of any vein.

### What consumers get (old #141)

- Completion as an outcome value returned by the decrement to the drop pipeline, which hands it to the
  completion stage in its fixed order (§11 Drop Pipeline): vein ordinal, ore, last position, breaker kind,
  `clean_cut_broken`, whether it is a guide vein, and the chunk's completed count after it. No lookups and
  no mod-internal event bus.
- The standing-ore count of a vein, after reconciliation, for abandoning (Vein Discipline). Which veins a
  player has started is the in-memory trip record's (§11 Vault Session State), as references (chunk, ordinal).
- Clean Cut's flag: when a non-ore block inside a started, unfinished vein's box is broken, by anyone, the
  listener sets `clean_cut_broken` on that vein. The box test runs over the chunk's headers, cached in
  memory per loaded chunk by section. The vein's withheld Resonance is held in memory by the drop pipeline
  per vein reference and lost on a restart, which costs the bonus for that vein only.
- The per-chunk completed count and Chapter's End's fired flag (below).
- Nearest vein within a radius (Stonecaller, Prospector's Eye): a search over this chunk's headers and,
  for a radius that crosses a border, the loaded neighbours' headers. An unloaded neighbour is skipped, not
  loaded.

### Chunk fields on the same attachment

- `generated_at`: game time (ADR 0005's clock) when the chunk first became a `LevelChunk`, set on the main
  thread in `ChunkEvent.Load` when its flag is true. Frontier Bonus reads it. It survives a restart, so it
  replaces the in-memory freshness map of old #142.
- `pathfinder_claimed`: whether the first vein in this chunk has paid Pathfinder's Claim. Team-wide: the
  chunk pays once.
- `chapter_completed` (counts only completions that fire, saturating at 255) and `chapter_fired`
  (Chapter's End, §6.1).
- Per vein, the `guide` flag in its header (the Survey panel's guide-vein count, §8).
- `gen_stamp`: the `stamp` of the `GenSnapshot` the chunk was generated with, a 64-bit long, copied, not
  recomputed. ADR 0003 defines it: a fixed hash in `core.gen` over the generation tiers, the two generation
  fork choices and the applied generation tradeoffs, sorted by id, never `Object.hashCode` or a set's
  iteration order; team level, the clip factor, the variant and the reset count are left out. The Survey
  panel compares it with the current snapshot's (Q13; ADR 0006 sends the flag). A chunk with no attachment
  reads as not current.

A chunk generated before the index existed has no attachment: its veins never complete, it pays no
Pathfinder's Claim and no Frontier Bonus, and it reads as not current (§11, S20).

### Twin Veins

A copy is placed inside the source vein's chunk, 2 to 4 blocks clear of the source's box (§6.1), replacing
stone and deepslate only; positions where the shape meets anything else are left out of both the world and
the copy's bitset. If no direction fits inside the chunk, there is no copy. A copy never writes another
chunk's attachment and never loads a neighbour (S20). It is written on the main thread, in the completion
stage, with the next free ordinal from `1 << 16`.

### Pending E6-S4 (#209): does an attachment written on the `ProtoChunk` reach the `LevelChunk`?

The source says the promotion constructor calls `copyChunkAttachmentsOnPromotion` (above); it does not say
what that copies. The spike, a gametest on Nick's PC, generates fresh chunks in a test Vault whose
generator sets a test attachment of the real type in `fillFromNoise`, and checks at `ChunkEvent.Load` (flag
true) that it is present and equal. It also stops the server while chunks are mid-generation, restarts, and
checks the chunks that finish afterwards, since a `ProtoChunk` saved at a partial status carries its
attachments through `SerializableChunkData`.

Result A, carried over. The generator calls `setData` on the `ProtoChunk` it is filling, on its worker,
which owns that chunk. `generated_at` is added on the main thread at `ChunkEvent.Load`. No pending map exists.

Result B, not carried over. The generator puts the built index in a per-level
`ConcurrentHashMap<Long, VeinIndexData>` keyed by packed chunk position, held by the Vault's
`VaultDimensions` entry and never by `SavedData`. The main thread drains a chunk's entry into `setData` in
`ChunkEvent.Load` when the flag is true, and adds `generated_at` there. An entry for a chunk that never
becomes full before the server stops is lost, and that chunk is treated as generated before the index. The
map is cleared when the Vault is unloaded or reset (ADR 0002, ADR 0007). §11's last Vein Index rule already
describes this branch.

Either result is written here with the date, the build and the gametest name before this record is
accepted, and §11's conditional sentence is replaced by the branch taken.

### Pending measurement: index size at the full deepslate stack (before E4-S3)

At the 60% ceiling a 16 × 16 × 384 expanded chunk holds about 59,000 ore blocks
(`docs/research/manager-notes.md` gives the 59,000 positions; 98,304 × 0.6 = 58,982). One `int` per position
would be about 236 KB a chunk before compression. The chosen encoding stores a bitset over each vein's box:
the shape port's boxes average 2.0 to 2.5 times the vein's blocks for sizes 12 to 37, so about 130,000 bits
(16 KB), plus one 8-byte header per vein, about 12 to 20 KB for 1,500 to 2,500 veins at the sizes the full
stack implies. About 30 to 36 KB a chunk, before the region file's compression. These are estimates from
`docs/research/vanilla-ore/orefeature.py`, not a measurement.

The measurement is a JUnit test over the pure placer and encoder at the full stack (every generation node
at its top tier, the floor clip applied, expanded variant), run on Nick's PC: encoded bytes per chunk, mean
and maximum over 81 chunks, beside the same chunk's block data encoded with vanilla's paletted containers
if that can be done in JUnit, else beside 98,304 blocks at 6 bits. The encoding stands if the index is no
larger than the block data. If it is larger, the record goes back to the Manager before E4-S3 with the
numbers; performance roles are deferred (Nick, 2026-10-26), so this is the only size gate.

## Consequences

- Ore thins by a block or two toward chunk borders, the cost of S20; Nick's ten-minute T4 mine looks for it
  (research §6 item 6).
- Every vein query is local to one chunk, plus loaded neighbours for radius searches. No synchronous chunk
  load on a break, which old #91 needed.
- The world is the authority, so a mod that changes blocks without events cannot leave a vein stuck
  incomplete, at the cost of reading a vein's blocks on each decrement.
- Completion effects come only from hand breaks that empty a vein. Machines, explosions and silent
  reconciliation never pay, which is §3.4 by construction.
- A chunk written before an index change is read by the attachment's own `data_version`; a changed layout is a bump
  and a migration (ADR 0005).
- Clean Cut's withheld Resonance and Vein Discipline's started set are in memory; a restart in the middle of
  a vein loses the Clean Cut bonus on that vein and treats nothing as abandoned (ADR 0005, E0-S13).

## Rejected alternatives

- Cross-chunk veins (old #45): each chunk derives its 3×3 neighbourhood. Rejected by S20: a vein's state
  would live in several chunks, a decrement would need a synchronous load of the origin chunk (old #91), and
  per-chunk floor truncation made the membership differ by chunk. Veins inside their chunk need none of it.
- Membership in every touched chunk with the count in the origin (old #91): only needed for cross-chunk
  veins.
- A flood fill on break: too slow in a dense, quarried Vault (§11).
- Recomputing a vein's positions from the seed instead of storing them: the reconstruction needs the full
  snapshot at generation time and every overlap, and a stored bitset is needed anyway to record what is
  still standing.
- One `int` or packed 17-bit position per indexed block: six to eight times the size of the box bitsets.
- A pending map always, without the spike: an extra moving part if the promotion copy already works.
- Watching explosions with a listener: one more event for a cause that never pays; reconciliation covers it.
- A team-wide map of chunk states (completed counts, fired flags): it only grows; on the chunk it costs
  nothing for chunks never visited and goes with them on a reset (§6.1 Chapter's End).

## Spec sections changed

§11 Vein Index (the encoding, the removal table, reconciliation, reserved ordinals, the chunk fields and
the size bound) and §3.1 (the in-chunk size bound of 37 and the split). The exact text is in
`spec-changes/E0-S6.md` for the Writer to apply.

## Reviews

To be filled. Matrix row "System design before code": developer (buildable), reviewer (rules), qa
(testable); the ticket adds writer. Each verdict lists what it checked.

- developer:
- reviewer:
- qa:
- writer:

## Amendment 1 (E0-S12, #231): explored chunks and the Seismic Sense map

Status: proposed, 2026-10-27 (E0-S12, #231). Spec: §6.1 Seismic Sense, Wanderer's Cache, Frontier Bonus and
Pathfinder's Claim; §8 Tab 3; §11 Vein Index and SavedData Pattern. Builds on this record, ADR 0005 (the
`chunks_explored` field and its credit rule) and ADR 0006 (`SurveyState`, `MAX_MAP_SIDE`).

### Context

Seismic Sense's tier 1 points a compass toward denser rock, tier 2 draws explored chunks on a map and tier
3 shades them by ore density (§6.1). Wanderer's Cache counts explored chunks per player, Frontier Bonus
reads how long ago a chunk was generated, and Pathfinder's Claim pays once per chunk. §8 Tab 3 equates
the two words: "Chunks explored (generated) in the Vault". None of the map's state had an owner, a growth
bound or a sync path, and the obvious owner, a team-wide map of every chunk, only grows.

The record above already stores, for every chunk generated since the index existed, the vein bitsets
(whose population counts are the chunk's standing indexed ore), `generated_at` and `pathfinder_claimed`.
That is enough while the chunk is loaded. A map of 33 by 33 chunks (ADR 0006's `MAX_MAP_SIDE`) is mostly
unloaded chunks, and their attachments are inside region files.

Read on 2026-10-27 from the decompiled NeoForge 26.1.2.95 sources (paths inside the sources tree):

- A level's own `SavedDataStorage` lives in its folder: `ServerChunkCache` builds it at
  `levelStorage.getDimensionPath(level.dimension()).resolve("data")`
  (`net/minecraft/server/level/ServerChunkCache.java` lines 94 to 102), and `ServerLevel#getDataStorage()`
  returns it (`net/minecraft/server/level/ServerLevel.java` lines 1489 to 1491). For a Vault that is
  `<world>/dimensions/orevault/vault_<hex>/data/` (ADR 0002). `ServerLevel#save` saves it (lines 916 to
  920) and `ServerChunkCache#close` closes it, so it follows the level's save and unload (ADR 0002).
- `ChunkStatusTasks#full` posts `ChunkEvent.Load(levelChunk, !(protoChunk instanceof ImposterProtoChunk))`
  on the main thread (`net/minecraft/world/level/chunk/status/ChunkStatusTasks.java` line 204): the flag
  is true the first time the chunk becomes a `LevelChunk`, as the record above says.
- The server's view distance is `server.getPlayerList().getViewDistance()`, the value every level's chunk
  source is built with (`ServerLevel.java` line 271).
- A spectator generates no chunks unless the game rule `GameRules.SPECTATORS_GENERATE_CHUNKS` is on: `ChunkMap#skipPlayer`
  (`net/minecraft/server/level/ChunkMap.java` lines 1049 to 1051).
- Reading an unloaded chunk's tag goes through `SimpleRegionStorage#read(ChunkPos)`, which returns a
  `CompletableFuture<Optional<CompoundTag>>` (`net/minecraft/world/level/chunk/storage/SimpleRegionStorage.java`
  line 36): a disk read and a decompression per chunk, finished off the main thread.

`ChunkEvent` itself is NeoForge's class, not in the unpacked tree; the accessor name for the flag is
Unverified: to be read by E1-S5 (#195).

### Decision

#### "Explored" means generated, team-wide, per Vault generation

A chunk is explored when it is a chunk of the team's current Vault that has become a `LevelChunk`. It is
the same event for every reader: Wanderer's Cache's count, the Seismic map, Frontier Bonus's clock and
Pathfinder's Claim's flag, and the "newly explored chunk" rolls of ADR 0003. A chunk generated at the edge
of view distance that nobody walked into is explored. Explored is not tracked per player: Seismic Sense
is a team node, and the per-player number is only a count (below). A reset deletes the Vault folder and with
it everything explored (ADR 0007), so nothing needs a generation tag.

#### What is stored

Per chunk: nothing new. Explored is the attachment's presence and `generated_at`; density is the sum of
the population counts of its vein bitsets.

Per team: nothing new in team data. The per-player count is ADR 0005's `chunks_explored` on `PlayerStats`.

Per Vault: one `SavedData` in the Vault level's own storage, id `orevault:explored_map`, file
`<world>/dimensions/orevault/vault_<hex>/data/orevault/explored_map.dat`. It is the only place the state of
an unloaded chunk is kept outside its region file. Keys, snake_case as ADR 0005 requires:

| Key | Type | Absent reads as | Meaning |
|---|---|---|---|
| `data_version` | int | 1 | Shape version (ADR 0005 section 6) |
| `regions` | list of {`rx` int, `rz` int, `cells` byte array of 2,048} | empty | One entry per 32 by 32 chunk region that holds an explored chunk. `cells` holds 1,024 unsigned 16-bit values, big-endian, row-major by z then x within the region |

A cell is 0 for a chunk not explored; `1 + n` for an explored chunk with `n` standing indexed ore blocks,
`n` saturating at 65,533; and 65,535 for an explored chunk whose density is unknown (no attachment: a chunk
generated before the index existed, or one whose pending entry was lost, Result B above). The exact count
is stored, not a shade, because the shade depends on a reference that moves as the team buys generation
nodes (below).

This is a cache of a derived value, which ADR 0005 otherwise forbids, kept because its source cannot be
read while the chunk is unloaded. The world still wins: every load of the chunk rewrites its cell from the
attachment.

Growth bound: 2 KB for each 32 by 32 chunk region that holds an explored chunk, so 2 bytes a chunk in a
filled region and at most 2 KB for a region touched by one chunk. A player at view distance 10 generates a
band 21 chunks wide, so a region they cross is at least about two thirds filled. A Vault of 50,000
explored chunks, whose region files hold several gigabytes and whose vein index alone is estimated at
30 KB a chunk (above), holds a map of about 100 KB. The map grows only as the Vault does, never across a
reset, and is archived with the folder on a disband (ADR 0007).

#### When it is written

All on the main thread; the generator never reads or writes it (`CLAUDE.md` rule 1).

- `ChunkEvent.Load` in a Vault level, any load: after the vein index listener has settled the attachment
  (and drained the pending entry if Result B), set the cell from the attachment, or to 65,535 when there is
  none. One listener does both, in that order, so the map never reads a chunk before its index is in
  place. The popcount is over about 16 KB of bits at the full stack (above), once per load.
- Every change to a loaded chunk's standing count, from any row of the removal table above or a Twin Veins
  copy: the same code that clears or sets the bits updates the in-memory standing count kept with the
  chunk's cached headers, and writes the cell. `setDirty` is a flag; the file is encoded at the level's
  save.
- Nothing is written at unload: the cell is already current.

The `SavedData` class is in `data` (a persisted shape, with its codec and migration); its in-memory form is
a map from region key to `short[1024]`. The `ChunkEvent.Load` listener is in `vault`, which holds the
team's generator and so the pending map if Result B (ADR 0002), and may import `data`, `team` and
`registry` (ADR 0001). The bit-clear updates are in `mining`, beside the other vein index updates. The pure
parts are in `core.vein`: cell encoding, the window cut, the compass choice and the shade (below),
unit-tested without Minecraft.

#### Unloaded chunk attachments are never read from disk on demand

Not for the map, the compass or any node. A Survey request every 40 ticks (ADR 0006) would mean up to 1,089
region reads and decompressions per player per request through `SimpleRegionStorage#read`, an asynchronous
path whose results arrive after the request is answered. The map file costs 2 bytes a chunk instead. The
radius searches above already skip unloaded neighbours; this keeps that rule for the map.

#### What reaches the client

ADR 0006's `SurveyState` carries `Optional<SeismicReadout(int tier, int towardChunkX, int towardChunkZ,
Optional<SeismicMap(int originChunkX, int originChunkZ, int side, byte[] cells)>)>`. This amendment fixes
what it holds:

- Present only when the team owns Seismic Sense and the requester is in the team's current Vault level.
  Elsewhere there is nothing to read, and the panel shows the node's text without a readout.
- `side` is 33 (`MAX_MAP_SIDE`), and the window is centred on the requester's chunk: `originChunkX` is the
  requester's chunk x minus 16, likewise z. The decoder refuses a `side` above 33 or a `cells` length other
  than `side²`.
- `cells`, row-major by z then x: 0 not explored; 1 explored with no shade (tier 2, or density unknown);
  2 to 255 a shade at tier 3. At tier 1 the map is absent. 1,089 bytes, plus about 20 bytes of header.
- The shade is `2 + floor(253 × min(1, n / (2 × fresh)))`, where `fresh` is the expected ore in a newly
  generated chunk under the team's current generation nodes: the closed form from `OreBaseline` with the
  floor clip applied (ADR 0009), computed when the snapshot is built and carried on `TeamEffects` (ADR
  0003). A fresh chunk shades near the middle, a mined-out one near the bottom, and a chunk richer than
  today's average (lucky rolls, or a Twin Veins copy) toward the top. How shades are drawn is UI-UX's and
  the Designer's; the wire carries the number.
- The compass, every tier: `towardChunkX` and `towardChunkZ` name the explored chunk in the window, other
  than the requester's own, with the highest known `n`, ties broken by the nearer chunk, then the lower z,
  then the lower x. A chunk of unknown density is not a candidate. When no candidate has a higher `n`
  than the requester's own chunk, both fields name the requester's own chunk, which the client draws as no
  denser rock nearby. The compass reads explored chunks only: predicting ore in a chunk not yet generated
  would need the snapshot it will be generated with, which can change before then.

`SurveyState` is answered at most once per 20 ticks per player (ADR 0006); building it reads at most four
regions' arrays and 1,089 cells on the main thread.

#### Which player a newly generated chunk is credited to

ADR 0005's rule, confirmed and made exact. On `ChunkEvent.Load` with the flag true, in a Vault level, in
the same listener as above:

1. Candidates are the players in that Vault level who are current members of the Vault's team (through the
   team helper) and are not spectators. A non-member in the level (a player who left the party while
   inside, an operator) is passed over, not counted as nearest.
2. A candidate is in range when the Chebyshev distance in chunks between their chunk and the new chunk is at
   most the server's view distance (`PlayerList#getViewDistance()`): the square in which their presence
   keeps chunks generating.
3. The chunk is credited to the candidate in range with the smallest squared horizontal distance in chunk
   coordinates, ties broken by the lower UUID; their `PlayerStats` `chunks_explored` in the Vault's team
   rises by one, and Wanderer's Cache checks for a multiple of 50 there.
4. With no candidate in range, the chunk is credited to nobody. That covers chunks loaded by an Anchor
   ticket, Far Reach's trailing tickets, a machine or a command, and chunks that finish generating at
   server start before anyone joins.

Every explored chunk is on the map whoever is credited. Credit is one player per chunk, so a team walking
together does not count a chunk several times.

#### The other exploration nodes read the attachment, not the map

- Frontier Bonus: `now − generated_at < 12,000` ticks, for the chunk the ore is in (or the member stands in,
  for Silent Stone). Game time, so a restart neither ages nor freshens a chunk. A chunk with no
  attachment pays nothing.
- Pathfinder's Claim: the first hand-broken indexed ore in a chunk whose `pathfinder_claimed` is false pays
  and sets it. With the stored state, that includes chunks generated before the team bought the node,
  each once; limiting it to chunks generated after the purchase would need the purchase time, which ADR
  0005 does not store. That reading is the Balancer's to confirm.

### Consequences

- One more save shape with its own `data_version`, its id `orevault:explored_map` frozen as save data from
  T5 (ADR 0005 section 6), and the same never-throw load rule. A map that fails to read is rebuilt cell by
  cell as chunks load, so the worst case is a blank map, not a lost save.
- The map shows what the team generated, wider than where anyone walked. If the Playtester finds that
  reads wrong, a "visited" flag per cell would be a new key with a default, not a migration.
- Density changes as the team mines, so the compass points away from mined-out ground, and the shade's
  reference rises with the team's generation nodes, so chunks made before a purchase read paler.
- ADR 0006's `SeismicReadout` fields are unchanged; their meaning is fixed here. ADR 0003's `TeamEffects`
  gains one number (`fresh`), rebuilt with the rest.

### Rejected alternatives

- A team-wide map in team data. It would grow across resets unless every cell carried a generation, it
  would be rewritten on every save of a file that changes on every ore, and it would outlive the Vault it
  describes. In the Vault's folder it is deleted, backed up and archived with the terrain.
- Reading unloaded attachments on demand, for the reasons above.
- Storing a shade per cell. The reference moves with the team's generation nodes, so stored shades would
  go stale; the count does not.
- A density fixed at generation. The compass would keep pointing at ground the team has already cleared.
- Predicting density for chunks not yet generated, from the seed. It needs the snapshot at generation time,
  and it would map terrain nobody has seen.
- Explored per player. Seismic Sense is a team node; one map per player multiplies storage for no reader.
- Crediting every member in range. A team moving together would earn caches several times faster than a
  solo player for the same ground.
- Crediting the player whose ticket caused the generation. Tickets from several players overlap and
  nothing in the chunk pipeline names one; the nearest member is the stand-in.

### Spec sections changed

§6.1 Seismic Sense (explored means generated; density is the standing ore; the window), §6.1 Wanderer's
Cache (the credit rule, replacing "settled by ADR 0005"), §6.1 Pathfinder's Claim (which chunks pay), §8
Tab 3 (the readout appears inside the Vault), §11 Vein Index (one bullet for the per-Vault map) and §11
SavedData Pattern (the map's file and version). The exact text is in the E0 run's `spec-changes/E0-S12.md`
for the Writer to apply.

### Reviews

To be filled. Matrix row "System design before code": developer (buildable), reviewer (rules), qa
(testable); the ticket adds writer. Each verdict lists what it checked.

- developer:
- reviewer:
- qa:
- writer:
