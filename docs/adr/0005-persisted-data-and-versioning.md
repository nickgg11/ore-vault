# 0005. Persisted data and versioning

Status: accepted 2026-10-31, after the round 2 verdicts and the Manager's round-2 ruling (recorded under
Reviews), on condition that the Reviewer, QA, the Developer and the Writer confirm the text of that ruling
here (sections 2, 3, 3a and 6). If one does not, the record stays proposed on that item alone. Proposed
2026-10-27 (E0-S8, #225). Spec: §3.2 Teleportation Logic, §4.3, §4.4, §6 Saved state,
§6.1 (Vein Discipline, Wanderer's Cache, Homeward Seam, The Vault Keeps It, Seam Courier, Hoarder's
Instinct, Travel stats, Ascending Edge), §8 Tab 3, §9 FTB Teams, §11 SavedData Pattern, Vault Session State
and Resonance Orb Entity. `CLAUDE.md` technical rule 6. Settled decisions S16, S17, S18 and N1
(`docs/TEAM_REQUIREMENTS.md`). Nick's answers of 2026-10-27, Q1 to Q4 and Q14 (`DESIGN_LOG.md`).
Absorbs old PR #110, PR #111, PR #115, PR #135 and #93 (the variant).

## Context

These are player save files from the first build that writes them. Nick keeps his playtest world from T5
to 1.0 (Q4), so from T5 on a change to a saved shape either reads the tag the previous build wrote or
loses his progress. The persisted-data table in `docs/TEAM_REQUIREMENTS.md` (Architect, "Persisted data")
predates the Path of Exile tree: it stores `nodes {id: tier}` and `forks`, which the tree no longer has.
The spec mixed key styles (`forkChoice` beside `resonance_pool`; `version` and `lifetimeOres` on the
`orevault:vault_tool` component) and named a field per section without one place listing them all.

Read on 2026-10-27 from the decompiled NeoForge 26.1.2.95 sources (paths inside the sources tree):

- `net/minecraft/world/level/saveddata/SavedDataType.java`: `record SavedDataType<T>(Identifier id,
  Factory<T> factory, Factory<Codec<T>> codecFactory, @Nullable DataFixTypes dataFixType)`, with a
  convenience constructor `(Identifier, Supplier<T>, Codec<T>)`.
- `net/minecraft/world/level/storage/SavedDataStorage.java` `getDataFile`: the file is
  `id.withSuffix(".dat").resolveAgainst(dataFolder)`, so `orevault:team_<id>` is
  `<dataFolder>/orevault/team_<id>.dat`. `readSavedData` parses with `resultOrPartial` and returns
  null on a parse error, and also on any exception, logging it (lines 95 to 111), which includes an
  `IOException` from a truncated or zero-byte file before any codec runs. `get` caches that as empty
  (lines 85 to 93), and `computeIfAbsent` then creates a fresh instance and marks it dirty (lines 74 to 83),
  so the next save overwrites the team's file with an empty team.
- `SavedDataStorage#encodeUnchecked` writes the file's root as `{data: {...}, DataVersion}` (`tag.put("data",
  ...)`), and no method saves one file: `saveAndJoin()` writes every dirty entry of a storage and blocks on
  `Util.ioPool()` (read by the Developer in round 1).
- 26.1 has two kinds of `SavedDataStorage`. The server's own is built at
  `new SavedDataStorage(storageSource.getLevelPath(LevelResource.DATA), fixerUpper, ...)`
  (`net/minecraft/server/MinecraftServer.java` line 329), that is `<world>/data/`, and returned by
  `MinecraftServer#getDataStorage()` (lines 2225 to 2227). Each level has its own, built in `ServerChunkCache`
  at `levelStorage.getDimensionPath(level.dimension()).resolve("data")` (`ServerChunkCache.java` lines 94 to
  102) and returned by `ServerLevel#getDataStorage()` (`ServerLevel.java` lines 1489 to 1491); for the
  overworld that is `<world>/dimensions/minecraft/overworld/data/`.
- Vanilla 26.1 keeps its world-wide data in the server's storage: random sequences, weather, game rules,
  the clock manager, custom boss events, scheduled events, the scoreboard, command storage, stopwatches and
  the wandering trader's state (`MinecraftServer.java` lines 342 to 365, 436 and 443 to 445). A level's own
  storage holds what belongs to that level: chunk tickets (`ServerChunkCache.java` line 103), raids, the
  world border and the dragon fight (`ServerLevel.java` lines 283, 305 and 1470). The server's storage is
  saved after every level in `saveAllChunks` (lines 599 to 618) and closed after every level at shutdown
  (line 700).
- `net/minecraft/world/level/storage/DerivedLevelData.java`: `getGameTime()` returns the overworld's game
  time; `ServerLevel#tickTime` advances it once a tick while the server runs.
- `net/minecraft/nbt/CompoundTag.java`: `getIntOr`, `getLongOr`, `getCompoundOrEmpty`, which never throw.
- `net/minecraft/world/entity/ExperienceOrb.java`: entities save through `addAdditionalSaveData(ValueOutput)`
  and `readAdditionalSaveData(ValueInput)` (`Age`, `Value`, `Count`).
- `net/minecraft/world/entity/ai/attributes/AttributeModifier.java`: `record AttributeModifier(Identifier
  id, double amount, Operation operation)`; `AttributeInstance#addPermanentModifier` exists.

Read from the ftb-teams 26.1.2.4 sources jar (2026-10-27): a personal team's id is the player's UUID and
the personal team is never deleted (`TeamManagerImpl#createPlayerTeam`); joining a party points the
personal team at the party and posts `PlayerChangedTeamEvent`, never `TeamDeletedEvent`
(`PartyTeam#join`); `PlayerChangedTeamEvent` is posted for offline players too (`AbstractTeam#onPlayerChangeTeam`).

Not in the decompiled Minecraft tree and so Unverified: to be read by E1-S5 (#195): NeoForge's
`AttachmentType` builder and its `copyOnDeath()`, the chunk attachment carry-over from `ProtoChunk` to
`LevelChunk` (ADR 0004), and NeoForge's registry alias API for a renamed block or item id.

## Decision

### 1. One key convention, one clock, one id form

- Every persisted key is snake_case, in every shape this mod writes: team `SavedData`, `PlayerStats`,
  the player attachment, the chunk attachment, item components, entity data. Java fields stay camelCase;
  the codec or the read/write method maps them. When the spec talks about saved data it names the key
  (`deepest_y`), not the Java field.
- Every time field is overworld game time, in ticks, read with `level.getGameTime()` (one clock for
  every level, `DerivedLevelData`): `free_respec_until`, `free_respec_opened_at`, `disbanded_at`,
  `pending_reset`'s `requested_at`, `homeward_cooldown_until` and the chunk's `generated_at` for Frontier
  Bonus (ADR 0004). Game time does
  not move while the server is stopped, so "10 minutes" is 12,000 ticks and "24 hours of server time"
  (Q14) is 1,728,000 ticks of a running server. A window survives a restart with no extra code (old #95).
  Wall-clock time is never stored.
- UUIDs are stored as hyphenated strings, as map keys and as values. Node, skill and option ids are
  stored as their layout id strings. Dimension and block ids are stored as `Identifier` strings, the vein
  index's ore palette (ADR 0004) and the Vault type file's `variant` (section 3a) among them.
- A position is `{dim, x, y, z}` where the dimension can vary, and `{x, y, z}` inside a shape that
  already names it.

### 2. Where everything lives

| Shape | Storage | Version key |
|---|---|---|
| Team data | `SavedData`, id `orevault:team_<teamUuidNoHyphens>`, in the server's storage (`MinecraftServer#getDataStorage()`): `<world>/data/orevault/team_<hex>.dat`. Created on first write, never on `FTBTeamsEvent.TeamCreated` (S18) | `data_version` |
| `PlayerStats` | Nested in team data under `player_stats`, one entry per player UUID | its own `data_version` |
| Player data | NeoForge player attachment `orevault:player`, `copyOnDeath` (unverified API, E1-S5) | `data_version` |
| Vein index and chunk fields | Chunk attachment `orevault:vein_index`, written only for the chunk being generated (ADR 0004 owns its fields, its block-id palette among them) | `data_version` |
| Explored map | `SavedData` `orevault:explored_map` in the Vault level's own storage (`ServerLevel#getDataStorage()`): `<world>/dimensions/orevault/vault_<hex>/data/orevault/explored_map.dat`. Deleted, backed up and archived with the Vault folder (ADR 0004 Amendment 1 owns its fields) | `data_version` |
| Vault type | Raw NBT file at the top of the Vault folder, outside the level's storage: `<world>/dimensions/orevault/vault_<hex>/orevault_vault.dat`. Written once when the folder is made; deleted, backed up and archived with it (section 3a) | `data_version` |
| Tool counters | Item component `orevault:vault_tool` | `data_version` |
| Wanderer's Cache tier | Item component `orevault:cache_tier` on the `orevault:wanderers_cache` item, an int | none (see 11) |
| Attuned ore | Item component `orevault:attuned`, a marker with no fields | none (see 5) |
| Resonance orbs | Entity data of the orb entity types | `data_version` |
| Max health | A permanent vanilla attribute modifier with the fixed id `orevault:travel_max_health`, saved by vanilla with the player | none; the id is the contract |
| Anchor tickets | NeoForge `TicketController` storage, validated on load (§3.4) | none of ours; the count is derived |

Where data goes follows what it describes. Data about a team, which outlives any one Vault, is in the
server's storage, beside vanilla's other world-wide data. Data about one Vault's terrain is in that Vault's
folder, so it goes wherever the folder goes: the explored map in the level's storage, the Vault's type in
the type file beside it. Data about one chunk is on the chunk.

One path helper in `data`, `DataPaths` (final, static methods), gives the team file's path and the Vault
type file's, so the write accessor, restore (ADR 0002) and ADR 0007's raw read never each rebuild them
(DEV2-4). `SavedDataStorage#getDataFile` is private (`SavedDataStorage.java` line 65), so the team file is
built from public parts the same way: `server.getWorldPath(LevelResource.DATA)` (`MinecraftServer.java` line
2058, `LevelResource.java` line 15), then the id's namespace, then its path plus `.dat`, giving
`<world>/data/orevault/team_<hex>.dat`. The type file is the Vault folder ADR 0002 names (what
`DimensionType.getStorageFolder` makes of the key under the world root) plus `orevault_vault.dat`; the world
root is the parent of the `DATA` path unless E1-S5 (#195) finds a root constant in `LevelResource`. The name
parts (`team_<hex>`, `vault_<hex>`) come from `core.key.VaultKeys`.

The `SavedData` id uses the team UUID without hyphens, the same form as the dimension key
`orevault:vault_<teamUuidNoHyphens>`, so one pure function, `core.key.VaultKeys` (ADR 0001), parses both.
A personal team's id is its player's UUID (FTB Teams), so a solo team's file is named after the player. Read
paths call `SavedDataStorage#get` and treat `null` as "every field at its default"; only a write calls
`computeIfAbsent`, so a player who logs in and never mines creates no file.

An unreadable team file is never overwritten. `get` returns null both for an absent file and for one that
failed to read (Context), so the one write accessor in `data`, when `get` returned null, checks whether the
data file exists. If it does not, it creates the team as usual. If it does, it logs ERROR with the path,
copies the file to `team_<hex>.dat.unreadable-<gametime>` beside it, and only then creates. Play goes on
and the original bytes stay on disk for repair by hand. Restore (ADR 0002) and ADR 0007's pass use the same
check to tell an absent file from an unreadable one; they log an unreadable one at ERROR and still restore
the Vault, because its type is in its folder (section 3a). A damaged team file costs the team's progress,
never its terrain. The explored map gets a WARN and no copy (ADR 0004 Amendment 1).

### 3. Team data

| Key | Type | Absent reads as | Meaning |
|---|---|---|---|
| `data_version` | int | 1 | Shape version (section 6) |
| `team_id` | UUID string | the id the file was opened for | For people reading the file. The file's own id wins; a mismatch logs a WARN |
| `resonance_pool` | long, thousandths of a Resonance | 0 | Lifetime Resonance earned. Never reset by a level-up (§4.3) |
| `level_reached` | int | 0 | Highest level the team has had. Never decreases |
| `owned` | list of node ids | empty | Owned nodes. The start node is implicit and never stored |
| `fork_choice` | {fork parent skill id: option id} | empty | The chosen option per fork |
| `free_respec_until` | long, game time | 0 | End of the free respec window after a reset (§4.4) |
| `free_respec_opened_at` | long, game time | never opened | When the last free window opened; a reset within 1,728,000 ticks of it opens none (Q14). Absent, not 0, so a reset in a world's first day still opens one |
| `pinned_node` | node id | no pin | The team's shared pin (§8) |
| `pinned_by` | UUID string | none | Who set it |
| `bought_by` | {node id: UUID string} | empty | Who last bought each owned node |
| `vault_generation` | int | 0 | Reset count, + 1 per reset, part of every chunk seed (Q2, ADR 0003) |
| `generation_tradeoffs` | list of node ids | empty | Applied state of Volatile Veins and Molten Seam (Q1, ADR 0003) |
| `disbanded_at` | long, game time | not disbanded | Set by the FTB Teams delete event; the folder moves to the archive at the next start (ADR 0007) |
| `pending_reset` | {`requested_at` long, game time; `backup` boolean} | no reset under way | Written and saved before a reset deletes anything, cleared in the same write that raises `vault_generation`; a start that finds it finishes the reset (ADR 0007). The start-of-server pass reads it from raw NBT, so the reader for this file must also work on a bare `CompoundTag`: the file's root is `{data: {...}, DataVersion}`, and the reader reads the `data` compound. "Saved at once" is `server.getDataStorage().saveAndJoin()` |
| `player_stats` | {player UUID string: `PlayerStats`} | empty | One entry per player who has recorded anything for this team |

The Vault's dimension type is not team data. It is in the Vault's folder (section 3a): owning Vault
Expansion is not the same as having an expanded Vault, and the type changes only when a reset makes a new
folder (§3.5). Team data had a `vault_variant` key until round 2 (2026-10-31); nothing was built with it,
so its removal costs no migration.

Nothing derived is stored. Not the current level, unspent points, any skill's tier, distances, the
ticket count, the team's deepest Y, a chunk's completed veins or the team size. The current level is the
curve's level for `resonance_pool`, raised to `level_reached` if that is higher; whenever the curve's
level passes `level_reached`, `level_reached` is raised to it. Unspent points are `5 × that level − owned
visible nodes` (§4.4). A `curve_divisor` change can therefore move future thresholds but never takes a
level, or its points, back (§4.3; old PR #115).

The pool is fixed-point. Payments have fractions: Stone Memory pays 0.05 per deepslate block, Deep
Harvest's orb 1, 2.5 or 6.25, and every percentage bonus makes fractions of the base. A long of whole
Resonance would drop the 0.05 payments entirely. An amount is rounded once, half up, to the nearest
thousandth where it enters the pool, after the §4.2 team scaling; an orb's `value` holds the thousandths
before scaling, so the double rounding stays within 0.001 (Balancer, round 1). Sums stay exact.
`LevelCurve` stores cumulative thresholds in the same unit, each rounded from the cumulative double, never
a sum of rounded per-level costs, so no drift builds up by level 30. The Resonance bar shows progress
toward level `max(curve level, level_reached) + 1`.

### 3a. The Vault type file

One per Vault folder, at `<world>/dimensions/orevault/vault_<hex>/orevault_vault.dat`, outside the level's
own `SavedDataStorage` (the folder's `data/`), so no `SavedData` holds a second copy. It is raw NBT read and
written by `data.VaultTypeFile`, not a `SavedData`, so it can be read before the level exists. The root is
the compound below, with no `data` wrapper.

| Key | Type | Absent reads as | Meaning |
|---|---|---|---|
| `data_version` | int | 1 | Shape version (section 6), from the first build that writes the file; `migrate()` from version 1 |
| `variant` | dimension type id | a refusal, never a default | `orevault:ore_vault` or `orevault:ore_vault_expanded`: the type this folder's Vault was made under (old #93: restore hard-coded the base type and bedrock came back at Y=0) |

- Written once, by ADR 0002 creation step 2, when the folder is made (a team's first entry, or the
  re-registration after a reset, ADR 0007 step 10), before the level is registered: to `orevault_vault.dat.tmp` in the folder, then
  renamed onto the name with `Files.move(..., ATOMIC_MOVE)`. Never rewritten, since the type changes only
  with a new folder.
- Read before the level exists, by ADR 0002 creation step 2, which restore runs for every folder.
- A missing file, one that cannot be read, and a `variant` that is absent or names neither type are each a
  refusal: ERROR naming the path, the Vault is not registered, the portal says why, and nothing writes into
  the folder (ADR 0002). Unlike every other shape's absent key this has no default, because a wrong guess
  costs terrain. A folder holding only the temp file is a creation cut short and is treated as no folder.
- A newer `data_version` loads best-effort with one WARN (section 6); a known `variant` is used.
- Backed up, deleted and archived with the folder (ADR 0007), so a backup restored by hand comes back with
  its own type.

Which NBT read and write calls are used (`NbtIo`, or `SavedDataStorage#readTagFromDisk`, which takes a path,
`SavedDataStorage.java` line 118) is Unverified: to be read by E1-S5 (#195).

### 4. `PlayerStats`

Nested in the team's data, versioned on its own. Its lifetime: it survives logout, death and server
restart; a dimension reset keeps it (it is a record of the player, not of the terrain); a team change
leaves it in the old team's map and the new team starts a fresh entry on its first write (Q3). Nothing is
copied between team files. Leaving a party returns the player to their personal team, whose id never
changes, so they find their old personal record again (§9).

| Key | Type | Absent reads as | Read by |
|---|---|---|---|
| `data_version` | int | 1 | |
| `ores_mined` | {block id: int} | empty | §8 stats, Kindred Rock |
| `stone_broken` | int | 0 | §8 stats; counted against `#orevault:vault_stone` (§6.1 Stonecutter's Patience) |
| `total_blocks_broken` | int | 0 | Calloused Hands |
| `deepest_y` | int | never entered (no sentinel, no flag, S16) | Shallow Grace, Highwater Mark, team deepest Y (derived) |
| `time_in_vault_ticks` | long | 0 | §8 stats |
| `chunks_entered` | int | 0 | Wanderer's Cache (rule in ADR 0004 Amendment 1). Named for chunks walked into, since "explored" means generated elsewhere in the spec (§6.1 Seismic Sense; WRITER-21) |
| `veins_completed` | int | 0 | §8 stats |
| `vein_discipline_streak` | int | 0 | Vein Discipline (rule below) |
| `deaths_in_vault` | int | 0 | §8 stats |
| `active_tradeoffs` | list of node ids | empty | Per-player tradeoffs; for Volatile Veins and Molten Seam, the member's vote (Q1) |
| `courier_link` | {dim, x, y, z, generation} | no link | Seam Courier. `generation` is the team's `vault_generation` when the link was set and is checked only when `dim` is the team's Vault; a link into a Vault that has since been reset reads as no link |

A counter from a machine break never moves (§3.4). The §8 post-1.0 stats (lifetime Resonance, Vault Echo
triggers, mob kills, Animus) are not written in 1.0; each arrives later as an addition with a default.

Which chunks `chunks_entered` counts, and which player each is credited to, is ADR 0004 Amendment 1's
rule (chunks entered, Nick's answer of 2026-10-27); this record owns only the key.

Vein Discipline (settled, E0-S13). The streak is `vein_discipline_streak` on `PlayerStats`, so it
survives logout, restart and reset (§6.1 "persisting across trips") and stays with the old team on a team
change. A logout with a part-mined vein is an abandon: §11 Vault Session State counts a logout as leaving
the Vault, and §6.1 defines abandoning as leaving with ore standing. The abandon is applied at the logout
from the in-memory trip record, which lists the veins broken into and not finished. A clean server stop
logs every player out, so it applies the abandon once, as any logout does (Manager's ruling, round 1). That
list is never saved, so a crash applies nothing and the streak keeps its last saved value. A team change
while inside is a leave for the old team's streak.

### 5. Player attachment `orevault:player`

`copyOnDeath`, so it survives death; it is the player's own and independent of team data, so it survives
team changes. Each team-scoped position carries the team and the `vault_generation` it was recorded under.
One that names another team, or a generation behind that team's current one, reads as absent and is
removed on the player's next write. That is how a reset "clears" entry points, the Homeward anchor and the
death marker without editing offline players' files (ADR 0007).

| Key | Type | Absent reads as | Meaning |
|---|---|---|---|
| `data_version` | int | 1 | |
| `return_pos` | {dim, x, y, z} | none (world spawn is the fallback, §3.2) | Saved only after the teleport in succeeds |
| `in_vault` | {team, generation} | not in a Vault | The Vault the player is in, as its team id and that team's `vault_generation` at entry; the login check reads it (ADR 0002). The dimension key follows from the team id |
| `tome_granted` | boolean | false | The Tome was given once (S18) |
| `entry_points` | list of {team, generation, x, y, z}, at most 3 | empty | Tier 2 and 3 personal entry points; a Vault Anchor set as one takes a slot (Q15) |
| `homeward` | {team, generation, x, y, z} | no anchor | Homeward Seam's anchor; cleared by a reset (§6.1) |
| `homeward_cooldown_until` | long, game time | 0 | End of the five-minute cooldown. Kept across death, reset and team change |
| `last_vault_death` | {team, generation, x, y, z} | no marker | The player's last death in a Vault (settled, E0-S13): the Survey panel's death marker and The Vault Keeps It's spot. Each death in the team's Vault overwrites it; it is drawn only for the viewer's current team and generation |

### 6. Versioning rules

- Each shape has its own `data_version`, and they move independently (old PR #111): team data,
  `PlayerStats`, the player attachment, the chunk attachment, the explored map, the Vault type file,
  `orevault:vault_tool` and the orb entity. `orevault:attuned` has no fields and `orevault:cache_tier` is a bare int; if either ever
  gains a field, it becomes a record whose codec also reads the old form.
- Every shape starts at 1. A tag with no `data_version` reads as 1: no build writes one without it,
  and pre-restart saves are not supported (N1). The last build's version 0 for unversioned tags is not
  carried over.
- Additions never bump. A new key reads its documented default from a tag written without it.
- A rename, a removal, a change of type or of unit bumps `data_version` and ships a migration, a
  fixture of the previous version's tag, and an updated golden key set, in the same PR. From T5 on this is
  required, not a stretch goal, because Nick's world carries over (Q4); N1's stretch goal is migrating
  saves across tree changes after 1.0.
- `migrate(tag, storedVersion)` is a fall-through chain: each step upgrades one version in place and
  falls through to the next, so any older tag reaches the current shape in one pass. Each step keeps frozen
  copies of the ids and constants it translates from, never the live ones, and running it twice changes
  nothing (old PR #135). A test runs each step twice.
- A tag from a newer build loads best-effort: known keys are read, unknown keys are dropped on the next
  save, and one WARN names the shape, both versions and the team or player. It never throws.
- Loading never fails. The team codec is `CompoundTag.CODEC.xmap(read, write)` with a hand-written
  reader that uses `get…Or` defaults, as the last build's was; the same holds for every other shape. No
  read throws, for any input. This is not style: `SavedDataStorage#readSavedData` turns a parse error or
  an exception into `null`. A codec cannot cover a file that fails before it runs (truncated or not gzip);
  section 2's copy-aside does.
- Registry ids are save data. Every id below is frozen from T5: block and item ids (the
  `orevault:wanderers_cache` item among them), the orb entity types, `orevault:player`,
  `orevault:vein_index`, `orevault:vault_tool`, `orevault:attuned`, `orevault:cache_tier`, the dimension
  types `orevault:ore_vault` and `orevault:ore_vault_expanded`, the dimension key and `SavedData` id
  patterns, the `SavedData` id `orevault:explored_map`, the biome `orevault:vault`, the `TicketController` id, `orevault:travel_max_health`, and every
  node, skill and option id in the layout (with the §6.1 reserved ids never reused), and the Vault type
  file's name `orevault_vault.dat`. All of them are string constants in one pure class (`core` has no
  Minecraft imports) that registration reads, pinned by a golden JUnit test. Renaming a block or item id needs NeoForge's alias mechanism (unverified API, E1-S5);
  renaming any other id needs a migration.
- Tests (S17). A golden key-set test per shape, the chunk attachment's including its `ores` palette of
  block id strings (ADR 0004) and the Vault type file's `{data_version, variant}`; a fixture of each shape at each version that loads; and an absent-field test
  per field reading its default. Wrong-type fixtures per key load to the default without throwing, at
  least `resonance_pool` as a string, `owned` as an int, `player_stats` as a list, and a palette entry that
  is not a string or names no registered block. Pure unit cases: 20 payments of 0.05 sum to exactly 1,000
  thousandths; a `curve_divisor` raise keeps `level_reached` and its points; a reset 1,727,999 ticks after
  `free_respec_opened_at` opens no window and one at 1,728,000 opens one; a team-scoped position from an
  older generation reads absent. Those on a tag run in JUnit: `CompoundTag` and `NbtIo` load in the unit source
  set, shown by `CompoundTagLoadsTest` (E1-S3, #194, also in `CLAUDE.md` Tests). That holds only for read,
  write and migrate code in plain classes: nothing shows a `SavedData` subclass loads there. The case that
  needs a registry, a palette entry naming no registered block, is a gametest.
- A gametest is one server lifetime and `SavedDataStorage#get` cannot drop a cached entry, so the restart
  checks are a scripted dedicated-server stop and start (E3-S10, #200), comparing named fields from the S44
  debug dump before and after: team `SavedData`, the explored map, and the chunk attachment across unload
  and restart. The player attachment across death is a gametest if the test player respawns through
  `PlayerList#respawn`, otherwise a playtest step.
- Damaged files, in the same script (QA's steps, Manager's round-2 ruling; a correct build passes them).
  Case A needs an expanded Vault: use the S44 debug path if it can make one under the expanded type; until it
  or a reset exists, case A runs on a base Vault and A4 and A6's deepslate checks are listed as unverified.
  - Case A, a damaged team file: play goes on and the terrain is kept. (1) Make a team with an expanded
    Vault, mine some ore, stop; note the team's level and the type file's content. (2) Overwrite
    `<world>/data/orevault/team_<hex>.dat` with garbage bytes and record its SHA-256. (3) Start. Pass: one
    ERROR naming the team file's path; the restore log lists this Vault restored under the expanded type; no
    `.unreadable-` copy exists yet. (4) Log in and `/execute in orevault:vault_<hex> run tp @s 0 -30 0`.
    Pass: deepslate there, not void. (5) Mine one ore. Pass: `team_<hex>.dat.unreadable-<gametime>` exists
    with the recorded SHA-256; an ERROR names the path; the dump shows the team at level 0. (6) Back in the
    Overworld, toggle a tradeoff or pin a node (a team write from outside the Vault). Stop, start. Pass: no
    ERROR for the team file; restore lists the Vault under the expanded type; Y=-30 is still deepslate; the
    ore mined in step 1 below Y=0 is still gone (the same chunks, not regenerated). Fail: any start restores
    the Vault under the base type, or the original bytes are not on disk.
  - Case B, a damaged Vault type file. (1) Stop; overwrite the type file with garbage bytes (and, as a
    second run, delete it). (2) Start. Pass: an ERROR naming the type file; the Vault is not registered; a
    player who logged out inside is placed at `return_pos` with a log line (ADR 0002 login case 1); the
    portal refuses with the lang message; the folder's file list is unchanged. (3) Restart. Pass: the same,
    nothing written. (4) Put the original file back and restart. Pass: the Vault restores under its type.
  - Case C, a new Vault. A team with no Vault enters by portal. Pass: the type file exists, with
    `data_version` 1 and the base type, before any region file is written (check the folder right after
    entry). Creation itself writes no team data (S18); the team's file appears with its first recorded
    field.

### 7. Load rules for the tree (§6)

Applied after migration, by pure functions in `core.tree` over the layout and the hidden set, so they are
unit-tested. Each change is logged once and the data is marked dirty only if something changed.

- An owned id the layout does not have is dropped, which returns its point (points are derived). An
  unknown id can never throw (old PR #110 crashed on one).
- An owned node no longer connected to the start after a layout change is pruned free. Dormant nodes count
  as present for this check.
- A fork choice whose parent or option is unknown is dropped; a choice whose parent has no owned node is
  cleared.
- `bought_by` and `generation_tradeoffs` entries for nodes not owned are dropped; unknown ids in
  `active_tradeoffs` are dropped.
- A pin whose node is gone or hidden is dropped, on load and again when `/reload` changes the hidden set.
- A node owned but hidden because its mod or tag is missing is kept, dormant: no effect, not counted as
  spent, active again if the mod returns. It is excluded from both views of ADR 0003.

The same never-throw rule holds outside the tree. An unknown palette id in the vein index (a block id the
registry or `OreTable` does not know) never throws: it is kept as stored and its vein reconciles against
that id alone (ADR 0004).

### 8. In memory only, never saved

Trip state (§11 Vault Session State), including the veins broken into this trip and Apprentice's Ledger's
kept count for ten minutes after a trip (Q9); Volatile Veins' pity counters (§11, reset on logout); the
60-second free undo window (§4.4); the reset vote session (ADR 0007); the pending generation tradeoff
change (derived, ADR 0003); `GenSnapshot` and `TeamEffects` (ADR 0003); the classifier table (§11); the
level-cost array (§4.3); the per-player hand-break flag (ADR 0008); the vein-index pending map if ADR 0004
needs one.

### 9. Team change (Q3)

Handled on `FTBTeamsEvent.PlayerChangedTeam`, which fires for every change, online or offline, including a
party join (`PlayerJoinedPartyTeam` does not fire for an offline player). Nothing is copied: the old
team's `PlayerStats` entry stays, and the new team writes a fresh one when the player next records
anything. Both teams' views are rebuilt (ADR 0003). An online player inside a Vault that is not their new
team's is sent to `return_pos` with a chat line saying why, and login treats a Vault whose team is not the
player's current team as one to leave (ADR 0002). Mining in a Vault pays only members of that Vault's team.

### 10. The Resonance orb

Saved through `addAdditionalSaveData`/`readAdditionalSaveData`, snake_case keys, its own `data_version`:
`team` (UUID string), `value` (long, thousandths of a Resonance), `orb_count` (int, default 1: how many
orbs merged into this one, the N of Hoarder's Instinct's cache multiplier; one field, confirmed in round 1, not a source-ore count) and `age` (int ticks, default
0). Whether an orb despawns is not stored: while its team has Hoarder's Instinct chosen, the orb's age does
not advance (read from `TeamEffects`), so swapping the option away lets old caches age normally. An orb
whose team has no data or is disbanded pays nothing and despawns normally.

### 11. Item components

`orevault:vault_tool` keys become snake_case with the rest: `data_version`, `lifetime_ores`,
`ores_since_rung` (default 0), `forged` (default false), `imprint` (default none), `echoes` (default empty).
The spec's `version` key is renamed `data_version`. Nothing has been written yet, so this rename costs no
migration. `orevault:attuned` is a marker; its id is save data from the first release.

`orevault:cache_tier` is an int, 1 or 2, stamped on an `orevault:wanderers_cache` item when it drops: the
dropping team's Wanderer's Cache tier at that moment (Manager's ruling, round 1). Opening reads the
stamp, not the opener's team, so a cache keeps the tier it dropped at. A cache item with no stamp opens at
tier 1.

## Consequences

- A damaged team file costs the team's progress, never its terrain; a damaged Vault type file refuses that
  Vault until an admin restores it (ADR 0002 Consequences, with the accepted residuals).
- One table per shape is the contract the golden tests pin. A PR that changes one changes the table in
  §11, the fixture and the test together.
- The pool's unit is thousandths: debug output and `/orevault` readouts divide by 1,000; an NBT editor
  shows 1,000 times the Resonance. The Balancer checks the curve in the same unit.
- Read paths must use `get` and defaults, not `computeIfAbsent`, or every login creates a team file.
- Stale team-scoped positions are cleaned lazily, so an offline player's file can hold a dead entry point
  until they log in. Every reader applies the team-and-generation check, so it is never used.
- Votes of former members stay in old `PlayerStats` entries and are ignored by the tally (ADR 0003).
- The fixed-point pool and the game-time clock are decided now because changing either later is a
  migration of every team file.

## Alternatives rejected

- A double for the pool. Exact sums and identical results on every machine matter more than avoiding
  a unit; a long of thousandths gives both.
- A long of whole Resonance, as the last build had. It silently drops Stone Memory's 0.05 payments.
- Storing the current level, points or tiers. Two sources that disagree (§11; old PR #115). Only
  `level_reached` is stored, on purpose.
- One `data_version` for the whole team file. `PlayerStats` grows on its own schedule; lockstep bumps
  would be noise and would force migrations for untouched shapes (old PR #111).
- A codec built with `RecordCodecBuilder` for team data. A field that fails to decode fails the whole
  parse, which `SavedDataStorage` turns into an empty team.
- `PlayerStats` on the player attachment, moving with the player. Q3 chose option (b); and team stats
  must be queryable by team while members are offline (§9).
- A has-record flag for `deepest_y` (old #149's fix). S16 settled on absence: one source, no flag to
  forget.
- Saving the pending vote result, or the Volatile Veins pity counters. Derived, or reset on logout
  anyway.
- Editing offline players' files on a reset. Generation tags make the stale entry harmless instead.
- The overworld's storage (`ServerLevel#getDataStorage()` on the overworld,
  `<world>/dimensions/minecraft/overworld/data/`), which `CLAUDE.md` rule 6 and §11 named and the last
  build used (`overworld.getDataStorage()` in `data/OreVaultTeamData.java` at 1e7cdad). In 26.1 that is the
  overworld's per-level storage, beside its chunk tickets and raids; vanilla moved its own world-wide data,
  command storage and the scoreboard among it, to the server's storage. Team data is world-wide. Nothing
  has been written yet and pre-restart saves are not supported (N1), so choosing now costs nothing, while
  moving after T5 would be a file move with a migration. Settled in the cross-ADR pass of E0-S12;
  `CLAUDE.md` rule 6 is reworded to match.
- The Vault's type as a team data key (`vault_variant`, the design until the round-2 ruling). After the
  copy-aside a fresh team file has no variant, so at the next start an expanded Vault read as base and lost
  its deepslate band (RV2-1, ARCH-R2-1). `CLAUDE.md` rule 6 puts a fact about one Vault's terrain in that
  Vault's folder.
- The type file as a `SavedData` in the Vault level's own storage. It must be read before the level, and so
  its storage, exists, and a `SavedData` there would be a second holder of the same fact.
- A default for an absent or unknown type. Every other absent key has one; this one does not, because base
  read for an expanded Vault deletes its deepslate band as chunks load and save.
- Dropping every write for a team whose file is unreadable (RV2-1's form). With no admin action the team is
  locked out at every start, on its most-written file; the silent-failure class M2 refused, made permanent.
- Refusing writes for a team whose file is unreadable, for the rest of the session (Reviewer, RV-1). It
  keeps the original bytes as the copy-aside does, but loses a whole session of play with nothing in game
  saying so, the silent-failure class `docs/LESSONS_LEARNED.md` lists (Manager's ruling, round 1).
- Wall-clock timestamps. A server stopped for a day would close every window and finish every
  cooldown while nobody could use them.

## Spec sections changed

§11 SavedData Pattern (the sketch becomes the key tables above; the key convention, the clock and the
load rules), §8 Tab 3 ("New `PlayerStats` fields" names the keys, adds `chunks_entered`), §6 Saved state,
§3.1, §3.2, §3.5, §4.4, §6.1 (Shallow Grace, Calloused Hands, Kindred Rock, Highwater Mark, Wanderer's
Cache, Vein Discipline, Seam Courier, Tempered, Ascending Edge), §9, §11 Vault Session State and §13 key
names. Applied in the same PR (#160). E0-S18's
answers (Q1 to Q4, Q14) and E0-S13's (streak, death marker) are already in the spec or in flight; this
record matches them and only renames their keys.

Round 2 (2026-10-31), applied by the Writer in the same PR: §11 SavedData Pattern (the team-data table
loses `vault_variant`; the Vault type file is listed under per-Vault data with its two keys; "Loading never
throws" gains the sentence that a damaged team file costs progress, never terrain), §11 Dimension-per-Team
Registration (as ADR 0002 lists), §13 (the team data shell and the type file), and `chunks_explored`
renamed `chunks_entered` in §6.1, §8 Tab 3 and §11.

## Reviews

Matrix row, system design: developer (buildable), reviewer (rules), qa (testable); ticket adds writer and
balancer. Round 1, 2026-10-27; rulings are the Manager's triage of round 1.

- developer: request changes. Checked `SavedDataStorage` `get`, `computeIfAbsent`, `readSavedData`, `set`,
  `scheduleSave`, `encodeUnchecked` and `close`; `SavedDataType` equality by id; the server's storage built
  with no level; `CompoundTag#getIntOr` and friends; the `AttributeModifier` record. Server storage over
  overworld storage, the fixed-point pool, the clock and the key convention found buildable. DEV-12 (a
  truncated file is overwritten with an empty team): fixed in section 2, check for the file, ERROR, copy
  aside, then create; restore uses the same check. DEV-13 (the raw reader and "save at once"): fixed in the
  `pending_reset` row, the `data` compound and `saveAndJoin()`. DEV-24a (which tier a cache opens at):
  fixed, `orevault:cache_tier` in sections 2, 6 and 11.
- reviewer: block. Checked the E0-S8 criteria, the key convention, one clock, `get` against
  `computeIfAbsent`, a `data_version` on every shape, additions never bumping, idempotent fall-through
  migrations, the newer-tag WARN, the §6 load rules in `core.tree`, `PlayerStats` staying with the old team,
  the pool, frozen registry ids, and §11 SavedData Pattern key for key. RV-1 (corrupt file overwritten):
  fixed with the copy-aside form in section 2; refusing writes for the session is rejected (Alternatives
  rejected). RV-2 (palette identity): fixed here as block ids in section 1, the palette in the golden key
  sets and a never-throw rule in section 7; ADR 0004 holds the rest. RV-8 (team change inside a Vault):
  fixed in section 9, pointing to ADR 0002's eviction.
- qa: request changes. Checked the key tables, defaults, versioning and load rules, the S17 test plan and
  the in-memory list against `SavedDataStorage`. QA-15 (a gametest cannot restart): fixed, the restart
  checks are the E3-S10 (#200) script compared through the S44 dump, and E1-S3 (#194) records whether NBT
  loads in JUnit. QA-16 (wrong-type fixtures and the unreadable file): fixed in sections 2 and 6, with the
  corrupt-file step in the script. QA-17 (unit cases): fixed, listed in section 6.
- writer: request changes. Checked every table against §11 SavedData Pattern and §8 Tab 3, snake_case, one
  clock, Q3, Q4, Q14 and the orb. WRITER-8 (the credit rule stated twice): fixed, section 4 points to ADR
  0004 Amendment 1. WRITER-9 (explored map missing from the version list): fixed. WRITER-10 (orb field):
  answered, one field, `orb_count`, orbs merged; the Writer fixes §13. `FTBTeamsEvent.TeamCreated` in
  section 2: fixed. Bolded inline headers: removed. WRITER-1 (pointer to a run-folder file): fixed. DES-2
  (the cache item id), from the Designer: `orevault:wanderers_cache` added to the frozen ids.
- balancer: approve with comments. Checked the pool unit, rounding error, overflow, `level_reached`, the
  clock constants and the stats reset on team change. BAL-7 (round once at pool entry; cumulative
  thresholds; the bar's target): fixed in section 3.

Round 2, 2026-10-31, settled by the Manager's round-2 triage. Its section 1 ruling: the Vault's type moves
into the Vault's folder and play goes on (option b of three; the spec's "play goes on" kept, `CLAUDE.md` rule
6 as the source).

- reviewer: block, on RV2-1 only. Checked round 1's RV-1 and RV-2 at their lines and the palette golden
  test and wrong-type fixtures against section 6. RV2-1 (the copy-aside wrote a team file with no
  `vault_variant`, so the next start opened an expanded Vault as base): answered by the ruling, not by the
  change asked for (dropping every write for the team, now under Alternatives rejected). Team data loses
  `vault_variant` (section 3), the type is in the folder (sections 2 and 3a), and case A step 6 of section
  6 is the Reviewer's scenario. The Reviewer confirms the new text closes the overwrite across restarts.
- developer: approve with comments. Checked `SavedDataStorage` `get`, `computeIfAbsent`, `saveAndJoin` and
  `readTagFromDisk` as public. DEV2-4 (the team file's path is private to `SavedDataStorage`): fixed in
  section 2, `data.DataPaths`, used by the accessor, restore and ADR 0007's raw read.
- qa: request changes. Checked the corrupt-file step against ADR 0002's round-1 refusal. QA-39 (the step
  failed on a correct build): merged in the ruling; section 6's damaged-file cases A, B and C replace it,
  case C being new. QA confirms the steps.
- writer: approve with comments. WRITER-21 (one word, two counts): fixed, the key is `chunks_entered` in
  sections 4 and "Spec sections changed"; the Writer renames it in the spec and the log. WRITER-20 (raised
  on ADR 0002): ruled in section 1; the Writer confirms the §11 text matches sections 2, 3 and 3a.
- balancer: approve. Checked the pool unit and BAL-7 at its lines; nothing new.
- architect: approve; consistent with ADRs 0001, 0003, 0006 and 0008 on every name checked. ARCH-R2-1
  (raised on ADR 0002) is the source of section 3a.
