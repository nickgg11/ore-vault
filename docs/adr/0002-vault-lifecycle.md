# 0002. The Vault lifecycle: create, restore, unload and remove

Status: proposed, 2026-10-27 (E0-S4, #223). Round 1 and round 2 reviews are recorded below and their
changes applied, round 2 by the Manager's ruling of 2026-10-31 (the Vault's type moves into the Vault's
folder). Accepted once the level-unload spike (E3-S11, #245) is written into the pending section below and the
matrix reviewers have signed. Spec: §3.1, §3.2 Teleportation Logic, §3.5, §11 Dimension-per-Team
Registration; S14, S19 (`docs/TEAM_REQUIREMENTS.md`).

The 26.1 facts below were read on 2026-10-27 from the decompiled, NeoForge-patched Minecraft sources of
`neoforge-26.1.2.95` (E1-S5, #195) and from `ftb-teams-26.1.2.4-sources.jar` (E1-S5's FTB Teams read). Paths
are inside those jars. NeoForge's own classes (`net.neoforged.neoforge.*`, such as `AttachmentInternals`,
`ForcedChunkManager` and `TicketController`) were not in that set; they were read on 2026-10-31 from
NeoForge's sources jar (`docs/research/neoforge-26.1-sources.md`), and the facts below that rest on them say
so. That tree also carries the old build's access transformers, so a member's visibility is taken from
`javap -p` on the compiled jar, not from the tree (same file, Provenance).

## Context

The first build lost a player to a missing restore path. Commit 9ded125 made creation lazy and, in the
same change, removed the `ServerStartedEvent` sweep that was the only thing registering a Vault before
players connected. Commit 9e9d8ae put the restore back after a player who logged out inside a Vault fell
from Y=251 in the Overworld. The source shows why: `PrepareSpawnTask#start`
(`net/minecraft/server/network/config/PrepareSpawnTask.java`) resolves the saved level with
`loadedPosition.dimension().map(this.server::getLevel).orElseGet(...)` and falls back to the respawn level,
but takes the position from `loadedPosition.position()` unchanged.

Creation, restore and removal each have steps that §11 states in one sentence. The old code
(`src/main/java/com/orevault/orevault/worldgen/VaultDimensions.java` at 1e7cdad) did creation and restore
correctly and never wrote removal: it carried `TODO [77] (issue #93)` for it, and
`event/FtbEvents.java#onTeamDeleted` deleted the team's data file on disband, which N8 now forbids.

What the 26.1.2.95 sources say about the machinery this rests on:

- `MinecraftServer` keeps levels in `private final Map<ResourceKey<Level>, ServerLevel> levels` (line 238),
  exposed by `forgeGetWorldMap()` (lines 1891 to 1894) under `@Deprecated //Forge Internal use Only, You can
  screw up a lot of things if you mess with this map.` The tick loop iterates `getWorldArray()` (line 1143),
  which returns a cached array rebuilt only when `worldArrayMarker` changes, and `markWorldsDirty()` (lines
  1898 to 1901, `@Deprecated //Forge Internal use Only, use to protect against concurrent modifications in
  the world tick loop.`) is the only writer of that marker. Both are `synchronized`. There is no supported
  replacement in the patched sources.
- `MinecraftServer#createLevels` (lines 428 to 489) builds every non-overworld level as
  `new ServerLevel(this, this.executor, this.storageSource, derivedLevelData, dimension, entry.getValue(),
  isDebug, biomeZoomSeed, ImmutableList.of(), false)` with `new DerivedLevelData(this.worldData,
  levelData)`, puts it in `levels`, posts `LevelEvent.Load`, then calls
  `level.getWorldBorder().setAbsoluteMaxSize(this.getAbsoluteMaxWorldSize())` and
  `this.getPlayerList().addWorldborderListener(level)`. Its `executor` is assigned
  `this.executor = Util.backgroundExecutor();` (line 354).
- The `ServerLevel` constructor (`net/minecraft/server/level/ServerLevel.java` lines 230 to 241) is public:
  `(MinecraftServer, Executor, LevelStorageSource.LevelStorageAccess, ServerLevelData, ResourceKey<Level>,
  LevelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner>, boolean tickTime)`. It reads
  `levelStem.seedOverride()` first (a NeoForge addition).
- `MinecraftServer.storageSource` is `protected final` in 26.1.2.95 (`javap -p` on the compiled jar and on
  Mojang's `client.jar`; the sources doc, "The five members, in a clean 26.1.2.95"). The tree read shows it
  public only because it carries the old build's transformer, one of the five lines of
  `git show 1e7cdad:src/main/resources/META-INF/accesstransformer.cfg`. NeoForge does not widen it, and
  `MinecraftServer` has no getter for the object: `getWorldPath(LevelResource)` returns a `Path`, and the
  `ServerLevel` constructor needs the `LevelStorageAccess` itself.
- `MappedRegistry#register` (`net/minecraft/core/MappedRegistry.java` lines 92 to 131) throws
  `IllegalStateException("Adding duplicate key ...")` when the key is present, and the class has no
  removal method. `unfreeze(boolean clearTags)` (line 285) only clears `frozen`; `freeze()` (line 294)
  re-runs bake callbacks and validates tags.
- Runtime stems are not written to the world's generation settings: `WorldDimensions(Registry<LevelStem>)`
  copies the registry into a map when it is built (`net/minecraft/world/level/levelgen/WorldDimensions.java`
  lines 54 to 56), and the server builds its `WorldGenSettings` once at load (`MinecraftServer` lines 331
  and 332). A Vault therefore has to be registered again at every start.
- A level's chunk tickets, NeoForge's forced tickets included, are saved per level:
  `ServerChunkCache` creates its `SavedDataStorage` at `levelStorage.getDimensionPath(dimension)
  .resolve("data")` and calls `computeIfAbsent(TicketStorage.TYPE)` (lines 94 to 103), and
  `TicketStorage.CODEC` folds in `ForcedChunkManager.defineExtraStorageParams()`
  (`net/minecraft/world/level/TicketStorage.java` lines 37 to 41). The file is
  `<world>/dimensions/orevault/vault_<hex>/data/minecraft/chunk_tickets.dat`.
- Saved tickets load deactivated: `TicketStorage#fromPacked` (lines 62 to 72) puts them in
  `deactivatedTickets`, and they become live only in `activateAllDeactivatedTickets()` (lines 99 to 107).
  The only caller is `MinecraftServer#prepareLevels` (lines 555 to 566), which `loadLevel()` runs once,
  straight after `createLevels()`, over the levels that exist then: it calls
  `level.getDataStorage().get(TicketStorage.TYPE)`, then `activateAllDeactivatedTickets()`, then
  `ForcedChunkManager.activateAllDeactivatedTickets(level, savedTickets)`. A Vault restored in
  `ServerStartedEvent` is not in `levels` at that moment, so without its own call its tickets, Vault
  Anchor tickets included, never come back (`docs/research/neoforge-26.1-sources.md`, "Chunk ticket API
  and forced tickets after createLevels").
- Shutdown closes a level in this order (`MinecraftServer#stopServer`, lines 652 to 707): drain
  `chunkMap.hasWork()` while calling `deactivateTicketsOnClosing()` and ticking the chunk source,
  `saveAllChunks(false, true, false)`, then per level post `LevelEvent.Unload` and call `close()`.
  That drain is a `while` loop with `waitUntilNextTick()`, run after the tick loop has ended.
  `ServerLevel#close` closes the chunk source and the entity manager (lines 1799 to 1802);
  `ServerChunkCache#close` saves, closes its data storage, the light engine and the chunk map (lines 313
  to 318).
- `ServerLifecycleHooks.handleServerStarted` is called from `runServer` after `initServer()` and before the
  tick loop starts (`MinecraftServer` lines 735 to 741), so `ServerStartedEvent` returns before any login
  is placed. `PlayerList#placeNewPlayer` fires `EventHooks.firePlayerLoggedIn(player)` as its last call
  (`net/minecraft/server/players/PlayerList.java` line 227), before the player's first tick.
- `SavedDataStorage#get` returns `null` both when the file is absent and when reading it throws
  (`readSavedData` catches every exception, `net/minecraft/world/level/storage/SavedDataStorage.java`
  lines 95 to 111), so "absent" and "unreadable" are told apart only by whether the file exists.
- FTB Teams 26.1.2.4: a player's personal team id is the player's UUID (`TeamManagerImpl#createPlayerTeam`,
  `new PlayerTeam(this, playerId)`), and the personal team is never deleted. Joining or creating a party
  points the personal team at the party (`PartyTeam#join`: `playerTeam.setEffectiveTeam(this)`; the
  `deleted` argument of `onPlayerChangeTeam` is `false`). Leaving a party sets the personal team back
  (`PartyTeam#leave`). `TeamDeletedEvent` fires only when a party's last member leaves.
  `PlayerChangedTeamEvent` fires on every change, for offline players too.

## Decision

### Classes

Two classes in `vault` (ADR 0001's package table) own the lifecycle.

- `VaultLevels` is the only code that touches `forgeGetWorldMap()` or `markWorldsDirty()`. `register(key,
  level)` puts the level in the map and calls `markWorldsDirty()`; `remove(key)` removes it and calls
  `markWorldsDirty()` (old #93: without it a stale cached array keeps ticking a removed level). Both run on
  the server thread. The Reviewer's ArchUnit set gains a rule that no other class calls either method.
- `VaultDimensions` holds `findOrCreate(teamId)` (the portal path, and the only creator of a new Vault),
  `restoreAll(server, resetTeamIds)` (start), `unload(teamId, reason)` (removal, below) and the in-memory maps
  keyed by team (dimension key, variant, generator). It holds no per-session list of refused teams: whether a
  Vault can open is read from its folder each time (creation step 2). Snapshot holders belong to ADR 0003;
  this class asks for them and clears them. The Vault type file is read and written by `data.VaultTypeFile`
  (ADR 0005 section 3a), at the path `data.DataPaths` gives (ADR 0005 section 2).

The key and folder-name functions are pure and live in `core.key.VaultKeys` (ADR 0001), the one function
that parses both the Vault dimension key and the team save-file id (ADR 0005): `dimensionPath(UUID)` gives
`vault_<32 hex>`, and `teamIdFromDirectoryName(String)` gives the UUID or nothing. Unit cases (QA-5): a
round trip; upper-case hex rejected; 31 and 33 hex digits rejected; a hyphenated name rejected;
`vault_<32 hex>.deleting` rejected, since a leftover of ADR 0007 step 8 is the one name restore must never
register.

### Dimension key and folder, frozen as save data

The key is `orevault:vault_<teamUuidNoHyphens>`, lower-case hex, as `TeamHelper.dimensionKey` wrote it at
1e7cdad. The folder is what `DimensionType.getStorageFolder` makes of it
(`name.identifier().resolveAgainst(baseFolder.resolve("dimensions"))`,
`net/minecraft/world/level/dimension/DimensionType.java` lines 143 to 145):
`<world>/dimensions/orevault/vault_<32 hex>/`. Both are save data from the first release: player files
store the key in `Dimension`, `return_pos` and entry points store it (ADR 0005), and tickets live inside
the folder. The scheme never changes without a migration that renames folders. For a solo player the hex
is the player's own UUID, since FTB Teams uses it as the personal team id.

The folder also holds the Vault's type: `orevault_vault.dat`, `{data_version, variant}`, written once when
the folder is made and never rewritten (ADR 0005 section 3a). It sits at the folder's top level, outside the
level's own `data/` storage, and goes wherever the folder goes: backup, delete and archive (ADR 0007). The
file name is save data, frozen with the key.

Restore maps a folder back to a team by matching `^vault_[0-9a-f]{32}$` and re-inserting hyphens at 8, 12,
16 and 20 before `UUID.fromString`, as `VaultDimensions#teamIdFromDirectoryName` did at 9e9d8ae. A name that
matches always parses. A name that does not match is not ours: logged at WARN and skipped.

### Creation (`findOrCreate`, and the shared `ensureTeamDimension` used by restore and reset)

On the server thread, in this order. Steps marked (aa431c6) worked in the first build as written there;
the other marks say where they came from.

1. If `server.getLevel(key)` is present, record it and return (idempotent).
2. Take the variant from the Vault's folder (round 2 ruling; `CLAUDE.md` rule 6: the type is a fact about
   the Vault's terrain).
   - No folder (a team's first entry, or the re-registration after a reset deleted the old folder, ADR 0007
     step 10, live or through restore's `resetTeamIds`): the variant is expanded if the team owns Vault
     Expansion and base otherwise (§3.5 step 5), read through ADR 0005's read path, so a team with no data,
     or an unreadable file, owns nothing and gets base. Create the folder and write the type file there, a
     temp file renamed into place, before any later step, so no Vault folder exists without one. Creation
     writes no team data (S18 holds).
   - A folder that holds nothing but a leftover temp file of that write: a creation cut short before the
     rename, which wrote no chunk. It is treated as no folder: the temp file is deleted and the type file
     written.
   - The folder exists: read the type file and use its `variant`. If the file is missing, cannot be read, or
     names neither `orevault:ore_vault` nor `orevault:ore_vault_expanded`, log at ERROR naming the path,
     register nothing and return. On the portal path the player is refused entry with a message (a lang
     key, the Writer's). Nothing writes into that folder and nothing guesses a type, because the file is the
     only record of it. Nothing is cached either: each attempt reads the file again, so an admin who puts
     the file back has the Vault at the next attempt.
   A failed write of the type file is logged at ERROR with the path and refuses entry the same way.
3. Fill the team's generation snapshot holder and the main-thread effects view (ADR 0003) from the team
   data, before the generator is built. The old code did this as its first act
   (`refreshSkillSnapshot(server, teamId)` in `ensureTeamDimension`, 1e7cdad).
4. Look up `Holder.Reference<DimensionType>` for the variant and `Holder.Reference<Biome>` for
   `orevault:vault` through `server.registryAccess().lookupOrThrow(...)` (aa431c6).
5. Load the layer config for the variant, validated against the heights duplicated in `Variant` (9fc3086,
   `CLAUDE.md` rule 8).
6. Build the generator with the team id, the biome, the variant's heights, the snapshot `Supplier` and the
   layer config; its codec is `MapCodec.unit(this)` (ADR 0003). Wrap it in `new LevelStem(type,
   generator)`.
7. Register the stem in the live `LEVEL_STEM` `MappedRegistry`, unless the key is already there:
   `unfreeze(false)`, then `register(stemKey, stem, RegistrationInfo.BUILT_IN)` inside `try`, with
   `freeze()` in the `finally`. The first build froze after a successful register only (1e7cdad), so an
   exception left the registry unfrozen. A key that is already present is not an error: it happens on the
   re-registration after a reset (below), because `MappedRegistry` has no removal and a duplicate key
   throws.
8. `new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData())`.
9. `new ServerLevel(server, Util.backgroundExecutor(), server.storageSource, derivedLevelData, key, stem,
   false, BiomeManager.obfuscateSeed(server.overworld().getSeed()), List.of(), false)`, through the public
   constructor (aa431c6). `Util.backgroundExecutor()` is what `createLevels` passes (`this.executor`,
   assigned at `MinecraftServer` line 354). `server.storageSource` is protected with no getter (Context), so
   the old build's access transformer line stays (old #14), with its ticket comment.
10. `VaultLevels.register(key, level)`: the map insert and `markWorldsDirty()` (9ded125).
11. `level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize())` and
    `server.getPlayerList().addWorldborderListener(level)`, as `createLevels` does.
12. Post `new LevelEvent.Load(level)` on `NeoForge.EVENT_BUS`.
13. Reactivate the level's saved chunk tickets, as `prepareLevels` does for the levels it sees:
    `TicketStorage saved = level.getDataStorage().get(TicketStorage.TYPE)`; if it is not null,
    `saved.activateAllDeactivatedTickets()`, then
    `ForcedChunkManager.activateAllDeactivatedTickets(level, saved)`. On restore this brings back
    `/forceload` chunks and the mod's Vault Anchor tickets; on a new Vault or the re-registration after a
    reset there is no ticket file and it does nothing. `ForcedChunkManager.activateAllDeactivatedTickets`
    is public, static and `@ApiStatus.Internal` (`ForcedChunkManager.java` lines 107 and 108). There is no
    supported alternative, so E3-S6a (#237) calls it with a comment saying so, on the same footing as
    `forgeGetWorldMap()`. It runs each `TicketController`'s validation callback itself, on the calling
    thread, with the level in the server's map (step 10) and before any of its forced tickets go live
    (lines 125 to 137). So the Vault Anchor's validation (§3.4) is the `LoadingValidationCallback` on its
    controller, and this step makes no separate call. Two limits: a level with no deactivated tickets gets
    no callback (line 111), and a callback that reads a block from `level` loads that chunk synchronously,
    once per Anchor. Whether that read works during restore is unverified, because it needs a running game:
    E10-S3 (#373) checks it across a restart.
14. Record key, variant and generator in the team maps; log the key, the dimension type, the Y range, the
    team and the server tick (aa431c6 logged the same; the tick is what found #89).

### Restore (`restoreAll`, in `ServerStartedEvent`)

`ServerStartedEvent` returns before the tick loop runs, so before any login is placed (read above). The
root listener (the `ServerStartedEvent` listener in the root package, ADR 0001's root row; RV2-2) calls, in
order: the start-of-server pass of ADR 0007 (`vault`: archive moves and the deferred resets), the ore
classifier (`ore`), the pending generation-tradeoff changes of ADR 0003 (applied first, so the snapshot that
restore fills reads the applied state), then the restore (`vault`). It sits in the root package because
`vault` does not import `ore`. ADR 0007's pass hands the restore the list of team ids whose reset it
finished at this start (`resetTeamIds`).

A Vault's type comes from its own folder, never from team data. Team data is read only for `disbanded_at`,
and a team file that is absent or unreadable does not stop a Vault from being restored.

1. Collect the team ids to restore: every directory under `<world>/dimensions/orevault/` whose name
   parses (above; a name that does not is logged at WARN and skipped), together with every id in
   `resetTeamIds`. An id from the list is restored whether or not a folder exists: the pass deleted the
   old folder, and the new Vault must be registered so that a player who logged out inside it is placed
   at the team anchor (login case 3), not sent home. If there is no folder and the list is empty, there
   is nothing to restore.
2. For each team id, in its own `try`/`catch` so one failure never stops the others (9e9d8ae): read the
   team's data for `disbanded_at` only, through `SavedDataStorage#get` (ADR 0005's read path, never
   `computeIfAbsent`). The pass of ADR 0007 has already finished with any file it moves.
3. Team data absent (`get` returned `null` and the file at `data.DataPaths`' team path does not exist): not
   disbanded. That is a Vault entered before the team recorded anything; it restores like any other.
4. Team data unreadable (`get` returned `null` and the file exists; M2): log at ERROR naming the file's
   path, and go on: the Vault is restored under its own type file (step 6). The team file is not touched
   here; ADR 0005's copy-aside keeps its bytes at the team's first write. The team's progress in that file
   is lost to play until it is repaired by hand; the terrain is not.
5. `disbanded_at` set: skip (S19). ADR 0007's pass has normally moved it already; this is the guard for a
   move that failed.
6. Otherwise `ensureTeamDimension(server, teamId)`. Its step 2 reads the type from the folder's type file,
   read raw before the level exists; for an id on `resetTeamIds` with no folder it computes the variant
   from ownership and writes the new folder's type file. It fills the snapshot holder (creation step 3)
   before the level exists and so before it ticks, and reactivates the level's tickets (creation step 13).
   A type file that is missing, unreadable or names an unknown type is logged at ERROR naming its path
   (creation step 2); the Vault is not registered and counts as skipped, and a player who logged out inside
   it is caught by login case 1.
7. Log the count restored and the count skipped, and the reason for each skip.

A solo player who is in a party keeps their solo Vault on disk and it is restored like any other: its team
id is still the player's personal team, which FTB Teams never deletes. The cost is one `ServerLevel` per
such Vault at every start. It is accepted: each one was entered, and the player gets it back on leaving the
party.

### The `in_vault` login check

`in_vault` on the player attachment (ADR 0005) names the Vault the player is in: the team id and that
team's `vault_generation` at entry. It is set on entering a Vault and cleared on leaving, both watched
through the dimension change and the per-tick guard of §11 Vault Session State, not through the portal
code. On `PlayerLoggedInEvent` (game bus), which fires at the end of `placeNewPlayer` before the first
tick, the cases are tried in this order:

1. `in_vault` is set and the player is not in that Vault's level (the level was skipped, archived or
   failed to restore; vanilla has put them in the Overworld at Vault coordinates): teleport to `return_pos`,
   or to the Overworld respawn point when it is absent; clear `in_vault`; log the player and the reason.
2. The player is in a Vault whose team has `disbanded_at` set (disbanded earlier this session), or whose
   team is not the player's current effective team (RV-8: they left or were removed from that team while
   offline, or an operator teleported into another team's Vault): the same as 1, plus a chat line saying
   why.
3. The player is in a Vault whose team's `vault_generation` is greater than the one in `in_vault` (a reset
   ran while they were offline inside; ADR 0007): place them at the team anchor (X=0, Z=0) (Nh), as reset
   does for players online inside, and update `in_vault`.
4. Otherwise nothing.

This is S14's check. It is also the net under every restore failure, so a skipped folder costs a trip
home, not a fall.

### Team change while inside (RV-8)

A Vault belongs to one team (§3), and a player's records stay with the team they leave (§9, Q3). So a
player whose effective team changes while they stand in a Vault that is not their new team's is sent out.
`team` hears `FTBTeamsEvent.PlayerChangedTeam` (rule 5: only the team helper imports FTB Teams) and hands
`vault` the player id and the new team id as values. If the player is online and in a Vault level whose
team id (from `core.key.VaultKeys`) is not the new team, `vault` teleports them to `return_pos` (the Overworld
respawn point when absent), clears `in_vault`, and sends a chat line saying why (a lang key, the Writer's).
This covers a member kicked from a party while mining in its Vault, and a player standing in their solo
Vault who joins a party. An offline player is handled at login by case 2.

Mining in a Vault pays only a current member of that Vault's team. The break pipeline checks it, so a
non-member who is still inside for the rest of the tick, or an operator visiting by command, pays and
records nothing.

### Removal (`unload`), for T11

Nothing is removed while the server runs unless the spike below proves the steps safe (S19). When it does,
this is the order, modelled on `stopServer` for one level. `stopServer` drains the chunk map in a loop
after the tick loop has stopped; `unload` runs inside the tick loop, where such a loop would block the
server for as long as the map has work (DEV-5). So the drain is a per-tick step: `VaultDimensions` keeps
the unload in progress for the team and advances it from a `ServerTickEvent.Post` listener, and ADR 0007's
reset waits on it.

1. Precondition: no player in the level. The caller (reset, ADR 0007) has evacuated them; `unload` refuses
   and logs otherwise.
2. Release the team's Vault Anchor tickets through the mod's `TicketController` (ADR 0007), and call
   `level.getChunkSource().deactivateTicketsOnClosing()`, as `stopServer` does. Outside the validation
   callback NeoForge has no list-and-remove: `TicketController` only has `forceChunk` with one owner and one
   chunk, so the release needs each Anchor's owner, chunk and spawning flag from the mod's own data
   (sources doc, "TicketController, and when validation runs"). Where that lives is open with E10-S3 (#373).
3. Each following tick, while the level is still in the map and so still ticked by the server, check
   `level.getChunkSource().chunkMap.hasWork()`. When it reports no work, go on to step 4 in that tick. If
   it still has work after 200 ticks (10 seconds; a technical bound, checked by the spike), the unload
   fails: log at ERROR and hand back to the caller, which takes Nick's answer Ni (ADR 0007 step 6).
4. `level.save(null, true, false)`, then post `LevelEvent.Unload(level)` and call `level.close()`, which
   closes chunk storage, the level's data storage and the entity manager. Nothing touches the folder
   before this returns (old #93).
5. `VaultLevels.remove(key)`, which calls `markWorldsDirty()`.
6. Clear the team's generator, snapshot holder and effects view (ADR 0003), its pending vein-index entries
   if ADR 0004 took the pending-map branch, and the key and variant entries. This is the only place they
   are cleared while the server runs; the level has stopped generating by now.
7. Log the team id, the key, the reason (reset or disband) and the drain's tick count at INFO (old #93).

The `LEVEL_STEM` registry entry stays until the server stops: there is no removal (read above). A later
`ensureTeamDimension` for the same team skips registration (creation step 7) and builds the `ServerLevel`
from its fresh `LevelStem`, which is passed to the constructor directly. Whether anything at runtime reads
the stale registry entry instead is one of the spike's questions.

### Pending E3-S11 (#245): can a live Vault be unloaded?

The spike runs on Nick's PC, on a dedicated dev server with two dev clients (Q10). For the spike, a
`LevelTickEvent` listener logs each Vault key at INFO once a second while it ticks. The spike creates a
Vault, generates and edits chunks in it, sets a forced ticket, evacuates both players, runs the removal
steps above, and then checks:

1. The tick loop runs on for 60 seconds with no exception, and the server log has no tick line for the
   removed key after the removal line.
2. The folder can be deleted and moved at once, with nothing left open; tried on Nick's OS, since a file
   still held open fails a delete on Windows.
3. A new `ServerLevel` with the same key is built from a fresh stem, under the other variant, with the
   registry entry left as it is; a player enters it, mines, logs out inside and back in, and the server
   restarts cleanly with the new terrain and the new variant.
4. `saveAllChunks` and the stop sequence never write into the deleted folder.
5. After step 3, `/execute in orevault:vault_<hex> run tp @s 0 251 0` from a client lands in the new level
   (F3 shows the new variant's floor), and the server log has no exception naming the key.
6. The drain of removal step 3 finishes within 200 ticks, with its tick count in the log.

The spike is run twice, once in each variant order (base then expanded, expanded then base). Result A
needs all six checks to pass on both runs. A check that fails once, even intermittently, is Result B.

Result A, all checks pass. Reset unloads, deletes and re-registers while the server runs (ADR 0007's live
sequence). Nick's answer Ni is kept as the fallback for any single unload that fails at run time,
including a drain that passes 200 ticks.

Result B, any check fails. A live Vault is never unloaded. Every reset takes Ni's path: everyone inside is
sent out, the Vault is closed to entry with a message, and it regenerates at the next start (ADR 0007).
`unload` is not written; the start-of-server pass is the only removal.

Either result is written here with the date, the build and the log lines, and the Status line changes to
accepted. Disband never unloads at run time in either branch (S19, §3.1).

### NeoForge bumps

`forgeGetWorldMap()` and `markWorldsDirty()` are deprecated internals with no supported replacement (PR
#108, and the annotations quoted above). The dimension path is re-tested on every NeoForge version change,
with two lines in QA's regression list, both required on any PR that changes `neo_version`:

- Headless, scripted on a dedicated dev server (E3-S10, #200): create a Vault by debug command after the
  first tick, stop, start, and run `/execute in orevault:vault_<hex>` from the console before anyone
  connects. Then an S44 debug command breaks a block at a given position in the Vault through a fake
  `ServerPlayer`'s `gameMode.destroyBlock` and prints whether the block is gone, and the level's tick logger
  (the E3-S11 spike's `LevelTickEvent` listener) logs lines for the key (QA-38). That a Vault created after
  the first tick ticks at all stays a gametest (QA T3): a gametest is one server lifetime and cannot act in
  the restarted server.
- In a client, once per bump: log out inside a Vault, restart the server, log in, and land where you
  logged out (S14).

### Tests

- `VaultKeys` unit cases (above).
- A restore fixture for T3, a scripted dedicated-server start (E3-S10, #200) against a prepared world with
  six folders: (a) a base Vault with its type file and team file; (b) an expanded Vault with its type file
  and a team file of garbage bytes; (c) `vault_zzz`; (d) a Vault whose type file is garbage bytes; (e) a
  Vault with region files and no type file; (f) a Vault whose type file names `orevault:nonsense`. Pass: (a)
  and (b) restored, (b) under the expanded type; a WARN naming `vault_zzz`; one ERROR naming (b)'s team file
  path; one ERROR naming the type file path of each of (d), (e) and (f); "restored 2, skipped 4" with a
  reason per skip; the bytes of (b)'s team file and of (d) and (f)'s type files unchanged, and the file
  lists of (d), (e) and (f) unchanged. Second case: a team id on `resetTeamIds` with no folder is
  registered, and its new folder holds a type file with `data_version` 1 and the variant its ownership
  gives. Third case: a folder holding only the type file's temp file is restored as a new Vault, the temp
  file gone and the type file written. The end-to-end cases (a damaged team file across two restarts, a
  damaged type file, a new Vault) are ADR 0005 section 6's.
- Login case 1 is checked by renaming a Vault folder while the server is down and logging in. Case 2 is a
  T11 step: two dev clients, one leaves the party while inside (E11-S8, #301). Case 3 is ADR 0007's T11
  step (QA-24).
- Ticket reactivation (QA-6), at T3 with vanilla's half: `/execute in orevault:vault_<hex> run forceload add
  0 0`, put a furnace smelting in that chunk, restart with nobody joining, and the furnace's progress
  advances. At T10 the same with a Vault Anchor (E10-S3, #373).

## Consequences

- A team that never opens a portal costs nothing; a Vault that exists on disk costs one `ServerLevel` at
  every start, including a solo Vault whose player is in a party.
- The registry keeps one stale `LevelStem` entry per reset until the next start. Bounded by resets per
  session, and gone at restart.
- The `storageSource` access transformer stays: the field is protected with no getter (Context). If a later
  version changes that, the failure is a compile error or a stray AT line at one place, never a runtime one.
- The login check makes every restore failure recoverable, so restore can skip a bad folder instead of
  failing the start.
- A damaged team file costs the team's progress, never its terrain; a damaged Vault type file refuses that
  Vault until an admin restores it. The type file is written once and never rewritten, so a cut autosave,
  the usual cause of a damaged team file, cannot reach it.
- Accepted residuals of an unreadable team file, all logged at ERROR. ADR 0007's start-of-server pass cannot
  see its `pending_reset` or `disbanded_at` and does nothing for that team, so the Vault restores as it is
  on disk; a disbanded party has no members, so login case 2 sends anyone inside out. A reset cut between
  ADR 0007 steps 8 and 9 whose team file is then also unreadable makes the next Vault from generation 0's
  seed; that needs two faults at once. After the copy-aside the team's `vault_generation` reads 0, so chunks
  generated from then on are seeded as generation 0, while the chunks on disk are unchanged.
- Creation writes no team data, so S18 holds (nothing is written at team creation) and a team that enters
  and never records anything has a Vault folder and no team file. Restore needs none.
- Removal exists in code only if the spike passes. If it fails, the reset path loses its live branch, and
  nothing else changes.
- Restored Vaults keep their forced chunks across a restart, which §3.4 promises for Vault Anchors.
- Team change: the player's next portal trip goes to the effective team's Vault, and a player inside the
  old team's Vault is sent out. Listening for team changes uses `FTBTeamsEvent.PlayerChangedTeam`, which
  fires for offline players, not `PlayerJoinedPartyTeam`, which does not.

## Rejected alternatives

- Creating every team's Vault at start or on `TeamCreated` (§11, 9ded125): one `ServerLevel` per account
  ever seen.
- Restoring lazily, on the first login that needs it: the player is already placed at Vault coordinates in
  the Overworld when `PlayerLoggedInEvent` fires; the restore has to come first.
- A list of existing Vaults in team data instead of a folder scan: two sources for one fact. The folder is
  what the player's saved position points at. The one list restore does take, from ADR 0007's pass, covers
  only the resets finished at this start, whose folders that pass deleted a moment before.
- Unregistering the `LevelStem` on removal by reflection into `MappedRegistry`: no supported path, and the
  ids of later entries would shift.
- Draining the chunk map in a loop inside one tick, as `stopServer` does after its tick loop: it blocks the
  server, or cannot wait for the next tick at all (DEV-5).
- Restoring a Vault whose type file is missing or unreadable under the base variant, or any guessed one:
  wrong for an expanded Vault, and the cost lands on the terrain.
- The variant in team data (`vault_variant`, the round 1 design). After ADR 0005's copy-aside the fresh team
  file has no variant, so at the next start an expanded Vault reads as base and loses its deepslate band
  (RV2-1, ARCH-R2-1). The type is a fact about the terrain, and `CLAUDE.md` rule 6 puts that in the Vault's
  folder.
- Refusing a team's Vault for the session when its team file is unreadable (the round 1 fix, removed in round
  2). It held for one session only, since any write from outside the Vault recreated the team file, and it
  contradicted §11 "Loading never throws" and `DESIGN_LOG.md` ("play goes on"). Dropping every write for such
  a team (RV2-1's form) was rejected too: it locks the team out at every start until an admin acts, on the
  file most exposed to a power cut.
- Letting a player who changed team stay in the old team's Vault with only a message: their breaks would
  pay a team they are not in, or none (§3, §9 Q3).
- Archiving a solo Vault when its player joins a party (the conditional in DESIGN_LOG 2026-10-27): FTB Teams
  never deletes the personal team, so nothing triggers it, and the player returns to the same team id.

## Spec sections changed

§11 Dimension-per-Team Registration (creation steps, the `finally`, `VaultLevels`, ticket reactivation at
restore, the restore list handed over by the start-of-server pass, the login check's generation and
team-change cases, the per-tick removal steps, the re-test on NeoForge bumps; the `storageSource` sentence
removed), §3.4 (forced tickets persist across restarts because restore reactivates them; mining in a Vault
pays only that Vault's team), §13 Chunk Loading (ticket reactivation, E10-S3), §3.1, §3.5 and §11 (the team
anchor (X=0, Z=0) as the one name for the arrival point) and §9 FTB Teams (the solo Vault in a party).
Applied in the same PR (#160).

Round 2 (2026-10-31), applied by the Writer in the same PR from this record and ADR 0005: §11 SavedData
Pattern (team data loses `vault_variant`; the Vault type file under per-Vault data; "Loading never throws"
says a damaged team file costs progress, never terrain), §11 Dimension-per-Team Registration (Eager restore
reads the type from the Vault's folder and `disbanded_at` from team data, and a folder whose type file is
missing or unreadable is logged and skipped with the portal saying why; the sentence on restoring under the
base type and creation writing `vault_variant` is removed), §13 (the team data shell and the Vault type
file), and the lang key for the refusal.

## Reviews

Matrix row "System design before code": developer (buildable), reviewer (rules), qa (testable); the
ticket adds writer. Round 1, 2026-10-27, settled by the Manager's triage. Round 2 confirms each change.

- developer: request changes. Checked the `ServerLevel` constructor and `seedOverride`, `LevelStem`'s
  two-argument constructor, `MappedRegistry` register, `unfreeze` and `freeze`, `createLevels`,
  `DerivedLevelData`, `stopServer`, `ServerLevel#close`, `ServerChunkCache#close`, `PrepareSpawnTask` and
  `runServer` against the 26.1.2.95 tree; creation steps and restore order buildable. DEV-3 (the tree had
  the old ATs applied, so `storageSource` is not shown public): fixed, Context bullet, creation step 9 and
  Consequences say unverified, the AT is kept, the rejected alternative is dropped. DEV-4 (restored tickets
  stay deactivated): fixed, creation step 13. DEV-5 (the drain blocks inside a tick): fixed, removal is
  per-tick with a 200-tick bound and spike check 6. DEV-6 (the executor): taken, step 9 cites line 354
  and the caveat is gone. DEV-12, raised on ADR 0005: fixed here for restore, steps 3 and 4.
- reviewer: request changes. Checked `VaultLevels` as the only map writer, `markWorldsDirty()` on insert
  and removal, the registry `finally`, the public constructor, world border listener and `LevelEvent.Load`
  on the game bus, restore before any login, one bad folder never stopping the rest, the key scheme as
  save data, the login check, the removal order against `stopServer`, the deprecation note, the party-join
  read. RV-6 (tickets): fixed, creation step 13 with the E1-S5 items named. RV-7 (`storageSource`): fixed,
  as DEV-3. RV-8 (team change while inside): fixed, the "Team change while inside" section and login case
  2. RV-3, raised on ADR 0007: fixed, restore takes `resetTeamIds` (step 1). RV-1, raised on ADR 0005:
  restore's side fixed in step 4; the copy-aside form (DEV-12's) was the Manager's ruling (M2).
- qa: request changes. Checked creation, restore, the login check, removal and the spike against S14 and
  QA's T3 line. QA-3: fixed, checks 1 and 5 are pass/fail, the spike runs twice and any failure is Result
  B. QA-4: fixed, two regression lines, headless and client. QA-5: fixed, `VaultKeys` cases and the restore
  fixture under Tests. QA-6: fixed, the forceload and furnace check under Tests.
- writer: request changes. Checked creation, restore, the login check and removal against §11
  Dimension-per-Team Registration and §9, the key scheme, Nh and Ni. WRITER-1: fixed, "Applied in the same
  PR". WRITER-2: fixed, "the team anchor (X=0, Z=0)" in login case 3. WRITER-3: fixed, one level (WARN) for
  a name that does not match, and the "does not parse" branch is gone from restore.
- architect (E0-S2 review, ARCH-3, the same finding as RV-6): fixed with RV-6.

Round 2, 2026-10-31, settled by the Manager's round-2 triage (section 1 ruling and section 2), every item
answered by a fix in this record unless named otherwise.

- developer: approve with comments. Checked removal steps 2 to 4 against the tree (`chunkMap`, `hasWork`,
  `deactivateTicketsOnClosing`, `TicketStorage.TYPE`), the `SavedDataStorage` public methods and the
  `.deleting` rename as plain `Files` work. DEV2-4 (the team file's path is private to `SavedDataStorage`):
  fixed in ADR 0005 section 2, `data.DataPaths`, used here by restore step 3. DEV2-5
  (`ForcedChunkManager.activateAllDeactivatedTickets` is public): fixed, creation step 13 keeps
  "unverified" for the validation timing only.
- reviewer: approve with comments. Checked round 1's RV-3, RV-6, RV-7, RV-8 and RV-14 at their lines, and
  ticket reactivation against the reset path. RV2-1 (raised on ADR 0005; an unreadable team's fresh file
  reopened the expanded-as-base loss at the next start): answered by the Manager's section 1 ruling, a
  different fix from the one asked for. The variant is in the Vault's folder (folder section, creation step
  2), restore reads team data only for `disbanded_at` (restore steps 2 to 4), and the unreadable set is
  gone; the Reviewer confirms against case A step 6 of ADR 0005 section 6. RV2-2 (the start listener
  cannot be in `vault`): fixed, the restore intro names the root listener.
- qa: approve with comments. Checked the spike's pass/fail rules, the two regression lines, the
  `VaultKeys` cases, the restore fixture and the ticket check. QA-38 (a gametest player cannot act in the
  restarted server): fixed in "NeoForge bumps", the S44 break command and the tick logger; `qa.md` takes the
  same wording (Manager, WP-R2-6). QA-39 (raised on ADR 0005): merged in section 1; the restore fixture
  under Tests now covers each type-file state.
- writer: request changes. Checked the unreadable-file behaviour against §11 "Loading never throws",
  Eager restore and `DESIGN_LOG.md` row 32. WRITER-20 (spec and record disagree; no lang key): ruled in
  section 1. The record now says play goes on; the one remaining refusal (a missing or unreadable type
  file, creation step 2) takes a lang key worded by the Writer, and the §11 lines are listed under "Spec
  sections changed".
- architect: request changes. ARCH-R2-1 (the session unreadable set held for one session only): fixed, by
  the design proposed there and ruled in section 1. Addition 2 (b): fixed, the `.deleting` `VaultKeys`
  case.

Amended 2026-10-31 by E1-S5 (#195), from `docs/research/neoforge-26.1-sources.md`: `storageSource` is protected
with no getter, so the AT stays; the activation call is `@ApiStatus.Internal` and runs validation, so step 13
makes no extra call; removal step 2 needs the mod's own record of each Anchor. The Anchor callback's block read
stays unverified until E10-S3 (#373). No decision changed.
