# 0002. The Vault lifecycle: create, restore, unload and remove

Status: proposed, 2026-10-27 (E0-S4, #223). Accepted once the level-unload spike (E3-S11, #245) is written
into the pending section below and the matrix reviewers have signed. Spec: §3.1, §3.2 Teleportation Logic,
§3.5, §11 Dimension-per-Team Registration; S14, S19 (`docs/TEAM_REQUIREMENTS.md`).

The 26.1 facts below were read on 2026-10-27 from the decompiled, NeoForge-patched Minecraft sources of
`neoforge-26.1.2.95` (E1-S5, #195) and from `ftb-teams-26.1.2.4-sources.jar` (E1-S5's FTB Teams read). Paths
are inside those jars. NeoForge's own classes (`net.neoforged.neoforge.*`, such as `AttachmentInternals` and
`TicketController`) were not in the unpacked set; where this record depends on one it says so.

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
  `this.getPlayerList().addWorldborderListener(level)`.
- The `ServerLevel` constructor (`net/minecraft/server/level/ServerLevel.java` lines 230 to 241) is public:
  `(MinecraftServer, Executor, LevelStorageSource.LevelStorageAccess, ServerLevelData, ResourceKey<Level>,
  LevelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner>, boolean tickTime)`. It reads
  `levelStem.seedOverride()` first (a NeoForge addition).
- `MinecraftServer.storageSource` is declared `public final LevelStorageSource.LevelStorageAccess
  storageSource;` (line 218). The access transformer the old build needed (old #14,
  `src/main/resources/META-INF/accesstransformer.cfg` at aa431c6: "dynamic per-team ServerLevel creation
  needs the level storage access") is not needed against 26.1.2.95.
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
- Shutdown closes a level in this order (`MinecraftServer#stopServer`, lines 652 to 707): drain
  `chunkMap.hasWork()` while calling `deactivateTicketsOnClosing()` and ticking the chunk source,
  `saveAllChunks(false, true, false)`, then per level post `LevelEvent.Unload` and call `close()`.
  `ServerLevel#close` closes the chunk source and the entity manager (lines 1799 to 1802);
  `ServerChunkCache#close` saves, closes its data storage, the light engine and the chunk map (lines 313
  to 318).
- `ServerLifecycleHooks.handleServerStarted` is called from `runServer` after `initServer()` and before the
  tick loop starts (`MinecraftServer` lines 735 to 741), so `ServerStartedEvent` returns before any login
  is placed. `PlayerList#placeNewPlayer` fires `EventHooks.firePlayerLoggedIn(player)` as its last call
  (`net/minecraft/server/players/PlayerList.java` line 227), before the player's first tick.
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
  `restoreAll(server)` (start), `unload(teamId, reason)` (removal, below) and the in-memory maps keyed by
  team: dimension key, variant, generator. Snapshot holders belong to ADR 0003; this class asks for them
  and clears them.

### Dimension key and folder, frozen as save data

The key is `orevault:vault_<teamUuidNoHyphens>`, lower-case hex, as `TeamHelper.dimensionKey` wrote it at
1e7cdad. The folder is what `DimensionType.getStorageFolder` makes of it
(`name.identifier().resolveAgainst(baseFolder.resolve("dimensions"))`,
`net/minecraft/world/level/dimension/DimensionType.java` lines 143 to 145):
`<world>/dimensions/orevault/vault_<32 hex>/`. Both are save data from the first release: player files
store the key in `Dimension`, `return_pos` and entry points store it (ADR 0005), and tickets live inside
the folder. The scheme never changes without a migration that renames folders. For a solo player the hex
is the player's own UUID, since FTB Teams uses it as the personal team id.

Restore maps a folder back to a team by matching `^vault_[0-9a-f]{32}$` and re-inserting hyphens at 8, 12,
16 and 20 before `UUID.fromString`, as `VaultDimensions#teamIdFromDirectoryName` did at 9e9d8ae. A name that
does not match is not ours: logged at WARN and skipped.

### Creation (`findOrCreate`, and the shared `ensureTeamDimension` used by restore and reset)

On the server thread, in this order. Steps marked (aa431c6) worked in the first build as written there;
the other marks say where they came from.

1. If `server.getLevel(key)` is present, record it and return (idempotent).
2. Read the team's `vault_variant` (ADR 0005), base when absent. On a new Vault, write `vault_variant` to the
   team's data now, so every Vault on disk has team data to restore from.
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
   constructor (aa431c6). `server.storageSource` is public in 26.1.2.95, so no access transformer; a
   comment at the call says so, pointing at this record and at old #14's transformer, so a later version
   that hides the field is recognised at once. `createLevels` passes `this.executor`, which is private;
   whether it differs from `Util.backgroundExecutor()` is checked when T3's code is written, and either
   choice is recorded in the class Javadoc.
10. `VaultLevels.register(key, level)`: the map insert and `markWorldsDirty()` (9ded125).
11. `level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize())` and
    `server.getPlayerList().addWorldborderListener(level)`, as `createLevels` does.
12. Post `new LevelEvent.Load(level)` on `NeoForge.EVENT_BUS`.
13. Record key, variant and generator in the team maps; log the key, the dimension type, the Y range, the
    team and the server tick (aa431c6 logged the same; the tick is what found #89).

### Restore (`restoreAll`, in `ServerStartedEvent`)

`ServerStartedEvent` returns before the tick loop runs, so before any login is placed (read above). The
listener runs, in order: the start-of-server pass of ADR 0007 (archive moves and pending resets), the ore
classifier, then the restore.

1. List `<world>/dimensions/orevault/`. If it is missing, there is nothing to restore.
2. For each directory, in its own `try`/`catch` so one failure never stops the others (9e9d8ae): match and
   parse the name (above); read the team's data for `vault_variant` and `disbanded_at`.
3. Team data absent: restore under the base variant. That is a Vault entered before any write; step 2 of
   creation makes it rare.
4. Team data present but unreadable, or a name that does not parse: log at ERROR with the folder and
   skip. A player inside it is caught by the login check below.
5. `disbanded_at` set: skip (S19). ADR 0007's pass has normally moved it already; this is the guard for a
   move that failed.
6. Otherwise `ensureTeamDimension(server, teamId, variant)`, which fills the snapshot holder (creation step
   3) before the level exists and so before it ticks.
7. Log the count restored and the count skipped.

A solo player who is in a party keeps their solo Vault on disk and it is restored like any other: its team
id is still the player's personal team, which FTB Teams never deletes. The cost is one `ServerLevel` per
such Vault at every start. It is accepted: each one was entered, and the player gets it back on leaving the
party.

### The `in_vault` login check

`in_vault` on the player attachment (ADR 0005) names the Vault the player is in: the team id and that
team's `vault_generation` at entry. It is set on entering a Vault and cleared on leaving, both watched
through the dimension change and the per-tick guard of §11 Vault Session State, not through the portal
code. On `PlayerLoggedInEvent` (game bus), which fires at the end of `placeNewPlayer` before the first
tick:

1. `in_vault` is set and the player is not in that Vault's level (the level was skipped, archived or
   failed to restore; vanilla has put them in the Overworld at Vault coordinates): teleport to `return_pos`,
   or to the Overworld respawn point when it is absent; clear `in_vault`; log the player and the reason.
2. The player is in a Vault whose team has `disbanded_at` set (disbanded earlier this session): the same as
   1.
3. The player is in a Vault whose team's `vault_generation` is greater than the one in `in_vault` (a reset
   ran while they were offline inside; ADR 0007): place them at the Vault anchor at X=0, Z=0 (Nh), as
   reset does for players online inside, and update `in_vault`.
4. Otherwise nothing.

This is S14's check. It is also the net under every restore failure, so a skipped folder costs a trip
home, not a fall.

### Removal (`unload`), for T11

Nothing is removed while the server runs unless the spike below proves the steps safe (S19). When it does,
this is the order, modelled on `stopServer` for one level:

1. Precondition: no player in the level. The caller (reset, ADR 0007) has evacuated them; `unload` refuses
   and logs otherwise.
2. Release the team's Vault Anchor tickets through the mod's `TicketController` (ADR 0007).
3. Drain the chunk map: while `level.getChunkSource().chunkMap.hasWork()`, call
   `deactivateTicketsOnClosing()` and tick the chunk source, as `stopServer` does.
4. `level.save(null, true, false)`, then post `LevelEvent.Unload(level)` and call `level.close()`, which
   closes chunk storage, the level's data storage and the entity manager. Nothing touches the folder
   before this returns (old #93).
5. `VaultLevels.remove(key)`, which calls `markWorldsDirty()`.
6. Clear the team's generator, snapshot holder and effects view (ADR 0003), its pending vein-index entries
   if ADR 0004 took the pending-map branch, and the key and variant entries.
7. Log the team id, the key and the reason (reset or disband) at INFO (old #93).

The `LEVEL_STEM` registry entry stays until the server stops: there is no removal (read above). A later
`ensureTeamDimension` for the same team skips registration (creation step 7) and builds the `ServerLevel`
from its fresh `LevelStem`, which is passed to the constructor directly. Whether anything at runtime reads
the stale registry entry instead is one of the spike's questions.

### Pending E3-S11 (#245): can a live Vault be unloaded?

The spike runs on Nick's PC, on a dedicated dev server with two dev clients (Q10). It creates a Vault,
generates and edits chunks in it, sets a forced ticket, evacuates both players, runs the removal steps
above, and then checks:

1. The tick loop runs on for at least a minute with no exception and the removed level never ticks again
   (a log line in `LevelTickEvent` for that key).
2. The folder can be deleted and moved at once, with nothing left open; tried on Nick's OS, since a file
   still held open fails a delete on Windows.
3. A new `ServerLevel` with the same key is built from a fresh stem, under the other variant, with the
   registry entry left as it is; a player enters it, mines, logs out inside and back in, and the server
   restarts cleanly with the new terrain and the new variant.
4. `saveAllChunks` and the stop sequence never write into the deleted folder.
5. `/execute in orevault:vault_<hex>` and the client's dimension list behave after step 3.

Result A, all five pass. Reset unloads, deletes and re-registers while the server runs (ADR 0007's live
sequence). Nick's answer Ni is kept as the fallback for any single unload that fails at run time.

Result B, any check fails. A live Vault is never unloaded. Every reset takes Ni's path: everyone inside is
sent out, the Vault is closed to entry with a message, and it regenerates at the next start (ADR 0007).
`unload` is not written; the start-of-server pass is the only removal.

Either result is written here with the date, the build and the log lines, and the Status line changes to
accepted. Disband never unloads at run time in either branch (S19, §3.1).

### NeoForge bumps

`forgeGetWorldMap()` and `markWorldsDirty()` are deprecated internals with no supported replacement (PR
#108, and the annotations quoted above). The dimension path is re-tested on every NeoForge version change:
QA's regression list gains "create a Vault mid-session and break a block; log out inside; restart; log in"
as a scripted headless restart (S14), and it is a required check on any PR that changes `neo_version`.

## Consequences

- A team that never opens a portal costs nothing; a Vault that exists on disk costs one `ServerLevel` at
  every start, including a solo Vault whose player is in a party.
- The registry keeps one stale `LevelStem` entry per reset until the next start. Bounded by resets per
  session, and gone at restart.
- The `storageSource` access transformer is gone. If a later NeoForge hides the field, compilation fails
  at the one call site that the comment explains.
- The login check makes every restore failure recoverable, so restore can skip a bad folder instead of
  failing the start.
- Writing `vault_variant` at creation makes the first portal trip a team's first data write (S18 still
  holds: nothing is written at team creation).
- Removal exists in code only if the spike passes. If it fails, the reset path loses its live branch, and
  nothing else changes.
- Team change: the player's next portal trip goes to the effective team's Vault. Listening for team
  changes uses `FTBTeamsEvent.PlayerChangedTeam`, which fires for offline players, not
  `PlayerJoinedPartyTeam`, which does not.

## Rejected alternatives

- Creating every team's Vault at start or on `TeamCreated` (§11, 9ded125): one `ServerLevel` per account
  ever seen.
- Restoring lazily, on the first login that needs it: the player is already placed at Vault coordinates in
  the Overworld when `PlayerLoggedInEvent` fires; the restore has to come first.
- A list of existing Vaults in team data instead of a folder scan: two sources for one fact. The folder is
  what the player's saved position points at.
- Unregistering the `LevelStem` on removal by reflection into `MappedRegistry`: no supported path, and the
  ids of later entries would shift.
- Keeping the `storageSource` access transformer anyway: an AT on a field that is already public is noise
  that hides a real change later.
- Archiving a solo Vault when its player joins a party (the conditional in DESIGN_LOG 2026-10-27): FTB Teams
  never deletes the personal team, so nothing triggers it, and the player returns to the same team id.

## Spec sections changed

§11 Dimension-per-Team Registration (creation steps, the `finally`, `VaultLevels`, the login check's
generation case, removal steps, the re-test on NeoForge bumps) and §9 FTB Teams (the solo Vault in a party).
The exact text is in `spec-changes/E0-S4.md` for the Writer to apply.

## Reviews

To be filled. Matrix row "System design before code": developer (buildable), reviewer (rules), qa
(testable); the ticket adds writer. Each verdict lists what it checked.

- developer:
- reviewer:
- qa:
- writer:
