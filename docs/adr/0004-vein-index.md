# 0004. Vein extents and the per-chunk vein index

Status: proposed, 2026-10-27 (E0-S6, #224); Amendment 1 (E0-S12, #231) at the end. Round 1 reviews are
answered below. Accepted once the carry-over spike (E6-S4, #209) and the size measurement (E4-S1, #234) are
written into the pending sections below, Nick has answered the Wanderer's Cache question (Amendment 1), and
the matrix reviewers have signed round 2. Spec: §3.1, §3.4, §11 Vein Index; S20
(`docs/TEAM_REQUIREMENTS.md`); `docs/research/vanilla-ore-generation.md` §5.

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
- A player's break removes the block before drops are computed: `ServerPlayerGameMode#destroyBlock` removes
  it (`net/minecraft/server/level/ServerPlayerGameMode.java` line 293) before `playerDestroy` reaches
  `BlockDropsEvent` (line 296), as the Developer read in round 1.

The body of `copyChunkAttachmentsOnPromotion`, and so which attachment types it copies, is NeoForge code
not in the unpacked set. The spike (E6-S4) decides it in play.

## Decision

### Placement returns extents; veins stay inside their chunk (S20)

`core.gen.VeinPlacer` (pure, ADR 0001) returns each vein as a value, `VeinExtent`: the vein's box origin and
size in chunk-local coordinates, its standing positions as a bitset over the box, its ore, its ordinal and
whether it is a guide vein. The ore is the group id ADR 0009 defines: the `Identifier` string of the group's
stone-band block, so stone and deepslate variants are one ore and the id is a block id, not a name derived
from the classifier's grouping. The generator writes blocks from these values; the index is built from the
same values. Nothing reads the world to find a vein.

The shape is vanilla's (`core.gen.VeinShape`, the port in `docs/research/vanilla-ore/orefeature.py`),
rolled about its origin. The placer takes the rolled shape's actual bounding box and places the origin
uniformly where that box fits inside the chunk's 16 by 16 columns and the band's Y range.

The bound, asserted in code: a vein of configured size `s` spreads at most `s/8` from its origin along its
axis, plus a sphere radius of at most `s/16 + 1/2`, so its horizontal span after flooring is at most
`3s/8 + 2` blocks. That is at most 16 for `s ≤ 37` and over 16 from `s = 38`. `VeinShape.MAX_IN_CHUNK =
37` is a constant with a unit test that computes the worst case from the formula and one that rolls the
shape at 37 from a fixed seed, 10,000 times, and checks every box.

Size bonuses are composed as the Balancer ruled in round 1 (§11 composition): a "+x% vein size" is +x% of
mean blocks per vein, and the placer picks the configured size whose `VeinShape.meanBlocks` matches through
the inverse of the shape table; size bonuses sum (Vein Expansion at tier 5 with Volatile Veins is +125%);
Abundance's −20% multiplies after; Guide Veins take no generation node's size or count bonus and stay at
configured size 24. On that reading the largest request in 1.0 is a common vein of about 26 blocks, about
configured size 15, so nothing in 1.0 reaches 37. The split stays as a safety net: a request above 37 is
split into the fewest veins of equal size at or under 37 whose expected block total matches the request
(blocks per vein grow faster than size, so this is not `s / n`; the table from the shape port is the
oracle). A 4,000-roll estimate from the port puts the largest observed span at 12 blocks for size 37 and 16
for size 48, so the formula is conservative.

Within a chunk, veins are placed in ordinal order. A later vein that overwrites an earlier vein's block
takes it: the earlier vein's bit is cleared before the extent is returned. Gravel and clay are placed after
ore and replace stone only, and Molten Seam's shells are placed before ore and refuse it (§3.1, §6.1). So at
generation the index matches the world exactly.

### The attachment

One chunk attachment, `orevault:vein_index`, with a codec. Its id is save data from the first release (ADR
0005). It is written only for the chunk being generated, by the generator for that chunk (or by the main
thread from the pending map, below), and after that only on the main thread.

The attachment type, its record and its codec live in a leaf package, `worldgen.index`, that imports only
Minecraft and `core.vein` (ADR 0001). It is registered from the mod constructor on the mod bus. The
generator (Result A), `mining`, `vault` and `network` reach it there; none of them needs `registry` or
`data`, whose imports would make the generator's import of the type break ADR 0001 rule 1 transitively.
The same leaf holds Result B's pending-map value type.

Encoding, chosen for size (measurement below):

- `data_version`: the attachment's shape version, 1 (ADR 0005's key, as every shape has).
- `ores`: the palette, a list of block ids as `Identifier` strings (ADR 0005 section 1), one per ore used in
  this chunk, each the group id above. A vein refers to an index.
- `veins`: `long[]`, one header per vein: box origin (x 4 bits, z 4 bits, y 9 bits for 384 heights), box
  size (x and z 4 bits each, stored minus one; y 5 bits), palette index (8 bits), ordinal (17 bits) and
  flags (`started`, `completed`, `vanished`, `clean_cut_broken`, `guide`, `twin`). The exact bit layout is
  in `core.vein.VeinRecord` and pinned by a golden test, so changing it is a version bump with a
  migration.
- `bits`: `long[]`, the standing bitsets of all veins, concatenated in header order. A vein's offset and
  length follow from the box sizes before it, so they are not stored.
- The chunk fields listed below.

A golden test pins the palette's encoding (a list of strings, in first-use order) beside the header layout.

The remaining count is the bitset's population count: derived, not stored (ADR 0005's rule). Completion is
the step that empties it.

Ordinals: generated veins take 0 to 65,535 (`< 1 << 16`). Twin Veins copies take `1 << 16` upwards, as old
#91 did, so a copy is recognisable and never collides with a generated vein.

### What a vein's ore matches

A block is in a vein's ore when it is the block the vein's palette entry names, or a block the current
`OreTable` groups with that block (ADR 0009). The palette names a block, not a group, so a mod added later
whose feature also targets `minecraft:iron_ore` cannot change what a stored iron vein is; at most it adds
blocks that also match.

A palette id the block registry or `OreTable` does not know (a mod removed) is kept as stored, as a string.
Loading it never throws, and its vein's bits are kept. Reconciliation then compares each position with the
stored id alone. If the mod returns, the vein is intact; if its blocks are gone from the world, the bits
clear on the next touch and the vein closes silently, like any other vein emptied by reconciliation.

### What changes the index

A position is standing only while its bit is set. Every removal clears the bit, so a position is decremented
at most once, and ore later placed at a cleared position is never indexed: that is how player-placed ore
stays out (§11) without a check on placement.

| Cause | Index | Completion effect |
|---|---|---|
| Hand break (§3.4), including each block of an FTB Ultimine chain (§9) | Clears the bit in the single `BlockDropsEvent` listener (`mining`) | Fires if this break empties the vein. An Ultimine operation resolves completion once for the operation (old #141) |
| Machine break, fake player, no breaker (§3.4) | Clears the bit in the same listener | Never. If the break empties the vein, it is marked completed silently and counts toward nothing (§3.4: no Chapter's End count) |
| Brittle Stone's destroyed neighbour (§6.1) | Cleared as part of the hand break that caused it | Counts toward completion as that hand break (§6.1); if it is the vein's last block, completion fires for that break, Last Ore included (Balancer, round 1) |
| Budding Seam (§6.1) | The hand break clears the bit; the budding block placed after it is not ore and is not indexed | As a hand break |
| Volatile Veins' vanish (§6.1) | The mod sets the rest of the vein to air and clears every bit; flag `vanished` | None. The decrement reports `VANISHED` and the vein closes silently: no Vein Discipline change, no Chapter's End count, no Last Ore, and the Clean Cut bonus held for it is dropped (Balancer, round 1) |
| Explosion: TNT, a creeper. Blast-Wise changes explosion damage to players and no blocks | Not watched by a listener. Cleared by reconciliation (below) | Never |
| Twin Veins' copy | Adds a vein with a new ordinal from the reserved range, in the same chunk (below) | Its own completion later, as any vein |
| Another mod's `setBlock`, a piston, a creative break (which never reaches `dropResources`, old #149) | Cleared by reconciliation | Never |

Reconciliation: whenever the index touches a vein (every decrement, a Vein Sight or Prospector's Eye
outline, Chapter's End's reveal, Stonecaller's chosen vein, an abandon check), it reads the vein's standing
positions in the loaded chunk and clears every bit whose block is no longer in the vein's ore (above). Cost
is bounded by the vein's size: a size-37 vein averages about 153 blocks in the shape port. A vein emptied by
reconciliation alone is completed silently.

Order on a break: the listener first clears the broken position's bit as that break (hand or machine), then
reconciles the rest of the vein. The block is already air when `BlockDropsEvent` fires (Context), so
reconciling first would clear the broken position silently and leave the break nothing to clear, and the
vein would never complete. If a hand break empties a vein whose other blocks were cleared by
reconciliation in the same step, the hand break was the last ore taken, so completion fires.

When count and world disagree the world wins: a set bit over a block not in the vein's ore is cleared
silently; ore over a clear bit is not part of any vein.

The rules are pure. `core.vein` owns the decrement, reconciliation and the completion outcome, taking the
breaker kind and a position predicate ("is the block here in this vein's ore") from the listener, so they
are unit-tested without Minecraft. Unit cases, at least: a hand break empties a vein (fires); a machine
break empties a vein (silent completion); a hand break empties a vein whose other bits reconciliation
cleared in the same step (fires); reconciliation alone empties a vein (silent); a block placed at a cleared
bit is not indexed; a vanished vein reports `VANISHED`; a palette id the predicate does not know keeps its
bits until a position fails the stored-id test. Gametests: a hand break, a machine break by a fake player,
and TNT, each checked against the expected outcome. Ultimine's once-per-operation completion is a playtest
step, since Ultimine is `compileOnly`.

### What consumers get (old #141)

- Completion as an outcome value returned by the decrement to the drop pipeline, which hands it to the
  completion stage in its fixed order (§11 Drop Pipeline): vein ordinal, ore (the palette's block id), last
  position, breaker kind, `clean_cut_broken`, whether it is a guide vein, and the chunk's completed count
  after it, or `VANISHED`. No lookups and no mod-internal event bus. The outcome type is in `core.vein`,
  which `core.mining` may import (ADR 0001).
- The standing-ore count of a vein, after reconciliation, for abandoning (Vein Discipline). Which veins a
  player has started is the in-memory trip record's (§11 Vault Session State), as references (chunk, ordinal).
- Clean Cut's flag: when a player breaks a non-ore block inside a started, unfinished vein's box, the
  listener sets `clean_cut_broken` on that vein. Only player breaks fire `BreakBlockEvent`, and
  reconciliation reads ore positions only, so a machine that clears a non-ore block in the box is not seen;
  that gap is accepted. The box test runs over the chunk's headers, cached in memory per loaded chunk by
  section. The vein's withheld Resonance is held in memory by the drop pipeline per vein reference and lost
  on a restart, which costs the bonus for that vein only.
- The per-chunk completed count and Chapter's End's fired flag (below).
- Nearest vein within a radius (Stonecaller, Prospector's Eye): a search over this chunk's headers and,
  for a radius that crosses a border, the loaded neighbours' headers. An unloaded neighbour is skipped, not
  loaded.

### Chunk fields on the same attachment

- `generated_at`: game time (ADR 0005's clock) when the chunk first became a `LevelChunk`, set on the main
  thread in `ChunkEvent.Load` when its flag is true. Frontier Bonus reads it. It survives a restart, so it
  replaces the in-memory freshness map of old #142.
- `pathfinder_claimed`: whether an indexed ore in this chunk has been hand broken. Set by the first hand
  break of an indexed ore in the chunk whether or not the team owns Pathfinder's Claim; the node pays on
  that break only when the team owns it (Balancer, round 1). Team-wide: the chunk pays at most once, and a
  chunk the team broke into before buying the node never pays later.
- `chapter_completed` (counts only completions that fire, saturating at 255) and `chapter_fired`
  (Chapter's End, §6.1).
- `entered`: whether a member of the team has stood in this chunk (Amendment 1, Wanderer's Cache), absent
  reads as false. Nick chose counting chunks walked into (2026-10-27).
- Per vein, the `guide` flag in its header (the Survey panel's guide-vein count, §8).
- `gen_stamp`: the `stamp` of the `GenSnapshot` the chunk was generated with, a 64-bit long, copied, not
  recomputed. ADR 0003 defines it: a fixed hash in `core.gen` over the generation tiers, the two generation
  fork choices and the applied generation tradeoffs, sorted by id, never `Object.hashCode` or a set's
  iteration order; the clip factor, the variant and the reset count are left out. The Survey panel compares
  it with the current snapshot's (Q13; ADR 0006 sends the flag). A chunk with no attachment reads as not
  current.

A chunk generated before the index existed has no attachment: its veins never complete, it pays no
Pathfinder's Claim and no Frontier Bonus, it counts for no Wanderer's Cache, and it reads as not current
(§11, S20).

### Twin Veins

A copy is placed inside the source vein's chunk, 2 to 4 blocks clear of the source's box (§6.1), replacing
stone and deepslate only; positions where the shape meets anything else are left out of both the world and
the copy's bitset. If no direction fits inside the chunk, there is no copy. A copy never writes another
chunk's attachment and never loads a neighbour (S20). It is written on the main thread, in the completion
stage, with the next free ordinal from `1 << 16`, and its palette entry is the source vein's.

### Pending E6-S4 (#209): does an attachment written on the `ProtoChunk` reach the `LevelChunk`?

The source says the promotion constructor calls `copyChunkAttachmentsOnPromotion` (above); it does not say
what that copies. The spike has two halves, both on Nick's PC, and a result rule.

The gametest half: a test Vault whose generator sets a test attachment of the real type in `fillFromNoise`
generates at least 81 fresh chunks, and the test checks at `ChunkEvent.Load` (flag true) that every one of
them carries the attachment and that it is equal to what was set. One chunk without it, or with a
different value, is a failure.

The restart half: a gametest cannot restart its server, so this is a scripted dedicated-server run (the
headless restart script of E3-S10, #200). The script starts the server, has a player or a forced load
generate a stated number of chunks, stops the server while chunks are mid-generation, starts it again and
lets them finish. Every chunk that finishes after the restart is checked the same way, since a `ProtoChunk`
saved at a partial status carries its attachments through `SerializableChunkData`. The script and its chunk
count are written into this section with the result.

Result A, carried over: both halves pass, every chunk. The generator calls `setData` on the `ProtoChunk` it
is filling, on its worker, which owns that chunk. `generated_at` is added on the main thread at
`ChunkEvent.Load`. No pending map exists.

Result B, not carried over: any failure in either half, including a mixed result where fresh chunks carry
the attachment and chunks finished after the restart lose it. The generator puts the built index in a
per-level `ConcurrentHashMap<Long, VeinIndexData>` keyed by packed chunk position, held by the Vault's
`VaultDimensions` entry and never by `SavedData`. The main thread drains a chunk's entry into `setData` in
`ChunkEvent.Load` when the flag is true, and adds `generated_at` there. An entry for a chunk that never
becomes full before the server stops is lost, and that chunk is treated as generated before the index. The
map is cleared when the Vault is unloaded or reset (ADR 0002, ADR 0007). §11's last Vein Index rule already
describes this branch.

Either result is written here with the date, the build, the gametest name and the script before this
record is accepted, and §11's conditional sentence is replaced by the branch taken.

### Pending measurement: index size at the full deepslate stack (E4-S1, #234, before E4-S3, #247)

At the 60% ceiling a 16 × 16 × 384 expanded chunk holds about 59,000 ore blocks
(`docs/research/manager-notes.md` gives the 59,000 positions; 98,304 × 0.6 = 58,982). One `int` per position
would be about 236 KB a chunk before compression. The chosen encoding stores a bitset over each vein's box:
the shape port's boxes average 2.0 to 2.5 times the vein's blocks for sizes 12 to 37, so about 130,000 bits
(16 KB), plus one 8-byte header per vein, about 12 to 20 KB for 1,500 to 2,500 veins at the sizes the full
stack implies. About 30 to 36 KB a chunk, before the region file's compression. These are estimates from
`docs/research/vanilla-ore/orefeature.py`, not a measurement.

The measurement is a JUnit test in E4-S1 (#234) over the pure placer and encoder at the full stack (every
generation node at its top tier, the floor clip applied, expanded variant), run on Nick's PC. It counts the
encoded bytes per chunk as the raw `veins` and `bits` arrays (8 bytes a long) plus the palette's strings,
with no NBT overhead, over 81 chunks, and reports the mean and the maximum. Minecraft classes do not load in
the unit source set (`CLAUDE.md` Tests), so the comparator is fixed now: the chunk's block data at 6 bits a
block, 98,304 × 6 / 8 = 73,728 bytes. The encoding stands if the maximum over the 81 chunks is at most
73,728 bytes. If it is larger, the record goes back to the Manager before E4-S3 (#247) with the numbers;
performance roles are deferred (Nick, 2026-10-26), so this is the only size gate.

## Consequences

- Ore thins by a block or two toward chunk borders, the cost of S20; Nick's ten-minute T4 mine looks for it
  (research §6 item 6).
- Every vein query is local to one chunk, plus loaded neighbours for radius searches. No synchronous chunk
  load on a break, which old #91 needed.
- The world is the authority, so a mod that changes blocks without events cannot leave a vein stuck
  incomplete, at the cost of reading a vein's blocks on each decrement.
- The palette names blocks, so adding or removing a mod changes no stored vein's identity; a removed mod's
  veins close silently once their blocks are gone, and nothing throws.
- Completion effects come only from hand breaks that empty a vein. Machines, explosions, vanishing and
  silent reconciliation never pay, which is §3.4 by construction.
- A chunk written before an index change is read by the attachment's own `data_version`; a changed layout
  is a bump and a migration (ADR 0005).
- Clean Cut's withheld Resonance and Vein Discipline's started set are in memory. A crash in the middle of
  a vein loses the Clean Cut bonus on that vein and applies no abandon. A clean stop logs every player out,
  so it applies the abandon once, as any logout does (ADR 0005 section 4; Manager's ruling, round 1); the
  Clean Cut bonus on that vein is lost either way.

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
- A palette of the classifier's group names (round 1 text). The classifier reruns at every start from tags
  and biome modifiers, so a group's name or membership can change when a mod is added, and reconciliation
  would then clear every vein of that ore in every saved chunk silently (Reviewer, RV-2).
- A pending map always, without the spike: an extra moving part if the promotion copy already works.
- Watching explosions with a listener: one more event for a cause that never pays; reconciliation covers it.
- A team-wide map of chunk states (completed counts, fired flags): it only grows; on the chunk it costs
  nothing for chunks never visited and goes with them on a reset (§6.1 Chapter's End).

## Spec sections changed

§11 Vein Index (the encoding, the block-id palette, the removal table, reconciliation, reserved ordinals,
the chunk fields and the size bound) and §3.1 (the in-chunk size bound of 37 and the split). Applied in the
same PR (#160).

## Reviews

Matrix row "System design before code": developer (buildable), reviewer (rules), qa (testable); the ticket
adds writer. The Balancer reviewed the 37 cap. Round 1, 2026-10-27; one block covers this record and
Amendment 1, which were reviewed together. Rulings are the Manager's triage of round 1.

- developer: approve with comments. Checked the header layout (61 of 64 bits), that `ChunkEvent.Load`'s
  flag means first promotion (`ChunkStatusTasks.java` line 204, `SerializableChunkData.java` line 349), the
  `explored_map.dat` path from `SavedDataStorage#getDataFile`, and the map and popcount costs. DEV-9
  (reconciliation would see air at the broken position): fixed, the order is stated under Reconciliation.
  DEV-10 (Clean Cut "by anyone"): fixed, "a player", with the machine gap accepted in the text. DEV-11 (a
  clean stop): fixed in Consequences per the Manager's ruling that a clean stop is a logout. DEV-1 (the
  generator cannot reach a type in `registry`): fixed, the type is in the leaf `worldgen.index`.
- reviewer: block. Checked the 37 bound and its tests, old #45's rejection, the versioned attachment
  written only for its own chunk, reserved ordinals, the header budget, machine breaks firing nothing,
  player-placed ore never indexed, pre-index chunks, both carry-over branches, the map on the main thread,
  `generated_at` on game time, the credit rule's candidates. RV-2 (the palette's ore identity is not stable,
  and reconciliation deletes on mismatch): fixed, the palette stores block ids, "What a vein's ore matches"
  states the match and the unknown-id rule, and a golden test pins the palette; ADR 0005 adds the palette to
  its golden key sets and its never-throw rules. RV-1's note on the explored map: fixed in Amendment 1's
  Consequences (WARN, never throws).
- qa: request changes. Checked the 37 bound and its tests, the encoding and golden layout, the removal
  table row by row, reconciliation, consumer outputs, chunk fields, the spike, the size measurement, and the
  amendment's storage, write points, payload, compass, shade and credit rule. QA-11 (a gametest cannot
  restart): fixed, the spike is a gametest half and a scripted restart half, and a mixed result is Result B.
  QA-12 (comparator): fixed, 73,728 bytes, raw arrays plus palette, the maximum over 81 chunks decides.
  QA-13 (reconciliation untested): fixed, the rules are pure in `core.vein` with the listed cases and three
  gametests; the 37 roll test has a fixed seed and count. QA-14: fixed in Amendment 1, the entry credit, the
  compass and the cell encoding are pure `core.vein` functions with listed cases.
- writer: request changes. Checked the bound, ordinals, removal table and chunk fields against §11 Vein
  Index, and the amendment against §6.1 Seismic Sense, Wanderer's Cache, Pathfinder's Claim, §8 Tab 3 and
  §11 SavedData Pattern, and the request cadence. WRITER-6 (the machine-break row reads as always
  completing): fixed. WRITER-1 (pointer to a run-folder file): fixed in both "Spec sections changed". WRITER-4
  and WRITER-5 are spec text, applied by the Writer.
- balancer: approve on the 37 cap (`3s/8 + 2 ≤ 16` gives `s ≤ 37.3`). BAL-5 (a size bonus needs a unit):
  fixed, the composition is stated and the split is reworded as a safety net. Round 1 rulings also applied
  here: Pathfinder's Claim flag set whether or not the node is owned; a vanished vein closes silently; Last
  Ore pays on completion by the vein's own collateral.

## Amendment 1 (E0-S12, #231): explored chunks and the Seismic Sense map

Status: proposed, 2026-10-27 (E0-S12, #231). The Wanderer's Cache rule below is Nick's answer of
2026-10-27 (chunks walked into, 50 each). Spec: §6.1 Seismic Sense, Wanderer's Cache, Frontier Bonus and Pathfinder's Claim; §8 Tab 3;
§11 Vein Index and SavedData Pattern. Builds on this record, ADR 0005 (the `chunks_explored` field) and ADR
0006 (`SurveyState`, `MAX_MAP_SIDE`).

### Context

Seismic Sense's tier 1 points a compass toward denser rock, tier 2 draws explored chunks on a map and tier
3 shades them by ore density (§6.1). Wanderer's Cache counts chunks per player, Frontier Bonus reads how
long ago a chunk was generated, and Pathfinder's Claim pays once per chunk. §8 Tab 3 equates the two words:
"Chunks explored (generated) in the Vault". None of the map's state had an owner, a growth bound or a sync
path, and the obvious owner, a team-wide map of every chunk, only grows.

The record above already stores, for every chunk generated since the index existed, the vein bitsets
(whose population counts are the chunk's standing indexed ore), `generated_at` and `pathfinder_claimed`.
That is enough while the chunk is loaded. A map of 33 by 33 chunks (ADR 0006's `MAX_MAP_SIDE`) is mostly
unloaded chunks, and their attachments are inside region files.

Round 1 found that counting generated chunks for Wanderer's Cache pays about 20 times the intended rate:
crossing one chunk generates 21 at view distance 10, so tunnelling drops a cache about every 40 seconds,
arriving drops 8 at once, digging straight down drops none, and the rate moves with view distance
(Balancer, BAL-9). The cache now counts chunks entered (Nick, 2026-10-27).

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
- `SavedDataStorage#readSavedData` returns null on a parse error and on any exception, so `get` cannot tell
  an absent file from an unreadable one (`net/minecraft/world/level/storage/SavedDataStorage.java` lines 95
  to 111; ADR 0005 section 2).
- Reading an unloaded chunk's tag goes through `SimpleRegionStorage#read(ChunkPos)`, which returns a
  `CompletableFuture<Optional<CompoundTag>>` (`net/minecraft/world/level/chunk/storage/SimpleRegionStorage.java`
  line 36): a disk read and a decompression per chunk, finished off the main thread.

`ChunkEvent` itself is NeoForge's class, not in the unpacked tree; the accessor name for the flag is
Unverified: to be read by E1-S5 (#195).

### Decision

#### "Explored" means generated, team-wide, per Vault generation

A chunk is explored when it is a chunk of the team's current Vault that has become a `LevelChunk`. It is
the same event for the Seismic map, Frontier Bonus's clock and the "newly explored chunk" rolls of ADR 0003.
A chunk generated at the edge of view distance that nobody walked into is explored. Explored is not tracked
per player: Seismic Sense is a team node. A reset deletes the Vault folder and with it everything explored
(ADR 0007), so nothing needs a generation tag.

Wanderer's Cache does not count explored chunks; it counts chunks entered (below).

#### What is stored

Per chunk: the `entered` flag on the attachment (record above). Explored is the
attachment's presence and `generated_at`; density is the sum of the population counts of its vein bitsets.

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

Reading it never throws. The reader uses `get…Or` defaults as ADR 0005 section 6 requires, and a `regions`
entry whose `cells` is not 2,048 bytes is dropped. When `get` returns null and the file exists, the file
could not be read: the accessor logs a WARN with the path and creates a fresh map. The original is not
copied aside, unlike team data (ADR 0005 section 2), because the map is a cache whose source is the region
files; its cells refill as chunks load.

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
team's generator and so the pending map if Result B (ADR 0002), and may import `data`, `team`, `registry`
and the leaf `worldgen.index` (ADR 0001). The bit-clear updates are in `mining`, beside the other vein index
updates, reaching the attachment through `worldgen.index`. The pure parts are in `core.vein`: cell
encoding, the window cut, the compass choice, the shade and the entry credit (below), unit-tested without
Minecraft.

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
- The compass, every tier, reads explored chunks only: predicting ore in a chunk not yet generated would
  need the snapshot it will be generated with, which can change before then (kept in round 1 by the
  Balancer and the Playtester). Candidates are the explored chunks in the window other than the
  requester's own, excluding chunks of unknown density. The compass names a candidate only when its `n`
  exceeds the requester's own chunk's `n` by at least `COMPASS_MARGIN` blocks. Among those, it keeps the
  target it named for this player last time while that target is still a candidate that clears the margin,
  and moves to another only when that one's `n` exceeds the kept target's by at least `COMPASS_MARGIN`. A
  new target is chosen by the highest `n`, ties broken by the nearer chunk, then the lower z, then the lower
  x. The last target is held per player in memory, never saved, and dropped when the player leaves the
  Vault. When no candidate clears the margin, both fields name the requester's own chunk, which the client
  draws as no denser rock nearby, in words. The margin stops the needle swinging between neighbours whose
  counts differ by roll luck on every 40-tick refresh (Playtester, round 1). `COMPASS_MARGIN` is a constant
  in `core.vein`; its value is the Balancer's, was not given in round 1, and is set before E9-S32a (#336),
  which checks the compass in play.

`SurveyState` is answered at most once per 20 ticks per player (ADR 0006); building it reads at most four
regions' arrays and 1,089 cells on the main thread.

#### Wanderer's Cache counts chunks entered (Nick, 2026-10-27)

This is the recommended option of the Manager's question 2 to Nick, written here as the plan until he
answers. The count is the chunks a team member is the first of the team to stand in, 50 per cache, about
800 blocks or 13 minutes of straight tunnel at any view distance (BAL-9).

1. At the end of each server tick, on the main thread, each player in a Vault level whose chunk differs from
   the last chunk recorded for them (in memory, never saved) is checked, in `vault` beside the end-of-tick
   empty-Vault check (ADR 0001, ADR 0003). The hook that runs it is Unverified: to be read by E1-S5 (#195).
2. Candidates are current members of the Vault's team (through the team helper) who are not spectators. A
   non-member in the level (a player who left the party while inside, an operator) and a spectator credit
   nothing and set nothing.
3. If the chunk has a vein index attachment and its `entered` flag is false, the flag is set and the player's
   `PlayerStats` `chunks_explored` in the Vault's team rises by one; Wanderer's Cache checks for a multiple
   of 50 there. A chunk with no attachment (generated before the index) credits nobody.
4. Players are taken in UUID order within a tick, so when two members enter the same new chunk in one tick
   the lower UUID is credited. A chunk credits one player once for the team, so a team walking together
   does not count a chunk several times.

The pure part is one function in `core.vein` taking (member, spectator, has attachment, already entered)
and returning credit or not. Unit cases: a member's first entry (credited); a second member entering the
same chunk (not credited); a non-member; a spectator; a chunk with no attachment; two members in one tick
(lower UUID); the 50th credit (cache), the 49th and 51st (none).

The Seismic map keeps showing generated chunks, so the map and the cache count measure different things.

Rejected by Nick (2026-10-27): keeping the count on generated chunks at one cache per 1,000, with the
round 1 credit rule: on `ChunkEvent.Load` with the flag true, the chunk is credited to the current,
non-spectator member of the Vault's team whose chunk is within the server's view distance
(`PlayerList#getViewDistance()`, Chebyshev distance in chunks) and nearest by squared horizontal distance
in chunks, ties to the lower UUID; with nobody in range (an Anchor ticket, Far Reach's trailing tickets, a
machine, a command, generation at server start) it is credited to nobody.

#### The other exploration nodes read the attachment, not the map

- Frontier Bonus: `now − generated_at < 12,000` ticks, for the chunk the ore is in (or the member stands in,
  for Silent Stone). Game time, so a restart neither ages nor freshens a chunk. A chunk with no
  attachment pays nothing.
- Pathfinder's Claim: the first hand break of an indexed ore in a chunk whose `pathfinder_claimed` is false
  sets the flag whether or not the team owns the node, and pays only if it does. A chunk generated before
  the purchase and never mined pays once; a chunk already broken into never pays later. No purchase time
  is stored (Balancer, round 1).

### Consequences

- One more save shape with its own `data_version`, its id `orevault:explored_map` frozen as save data from
  T5 (ADR 0005 section 6), and the same never-throw load rule. A map that fails to read is rebuilt cell by
  cell as chunks load, so the worst case is a blank map and a WARN, not a lost save.
- The map shows what the team generated, wider than where anyone walked. If the Playtester finds that
  reads wrong, a "visited" flag per cell would be a new key with a default, not a migration.
- Density changes as the team mines, so the compass points away from mined-out ground, and the shade's
  reference rises with the team's generation nodes, so chunks made before a purchase read paler.
- Wanderer's Cache's rate depends on ground covered, not on view distance or direction, at the cost of
  one flag per chunk and a per-player chunk check each tick (Nick, 2026-10-27).
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
- A compass with no margin (round 1 text): it pointed at whichever chunk led by a single block, so it
  could change direction on every refresh.
- Explored per player. Seismic Sense is a team node; one map per player multiplies storage for no reader.
- Counting generated chunks at 50 per cache (round 1 text): about 20 times the intended rate, none for a
  player digging down, more at a higher view distance (BAL-9).
- Crediting every member in range, or every member who enters a chunk. A team moving together would earn
  caches several times faster than a solo player for the same ground.
- Crediting the player whose ticket caused the generation. Tickets from several players overlap and
  nothing in the chunk pipeline names one.
- Copying an unreadable explored map aside, as team data is. It is a cache rebuilt from the region files;
  the WARN is enough.

### Spec sections changed

§6.1 Seismic Sense (explored means generated; density is the standing ore; the window; the compass
margin), §6.1 Wanderer's Cache (the entry credit), §6.1 Pathfinder's Claim (the flag
is set whether or not the node is owned), §8 Tab 3 (the readout appears inside the Vault), §11 Vein Index
(one bullet for the per-Vault map) and §11 SavedData Pattern (the map's file and version). Applied in the
same PR (#160); the Wanderer's Cache text follows Nick's answer.

### Reviews

Round 1 reviewed this amendment with the record above; the verdicts and answers are in the record's
Reviews block. Comments from roles outside the matrix: the Playtester found the credit rule fair and asked
for a compass margin (applied); UI-UX accepted the shade reference; the Designer's map cell size is UI-UX's
and the Designer's, with no change to the wire.
