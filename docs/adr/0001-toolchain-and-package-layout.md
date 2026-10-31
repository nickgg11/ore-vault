# 0001. Toolchain, package layout and architecture rules

Status: accepted 2026-10-31 (E0-S3, #179), after the round 2 verdicts below; proposed 2026-10-27. The
check owed to T1, that the pinned ArchUnit release reads Java 25 class files, was met by E1-S3 (#194). Spec: §2; `CLAUDE.md` Build and technical rules 1, 3, 4
and 5; settled decisions S15, S42 and S43 in `docs/TEAM_REQUIREMENTS.md`.

## Context

The repo was restarted on 2026-10-25 and holds no code. The previous build's worst bugs were structural.
Its generator held a `SkillSnapshot` with a static `of(OreVaultTeamData)` factory, so `worldgen` depended
on `data` and was one call away from reading `SavedData` off-thread (S15). Pure logic and Minecraft-facing
code shared packages (`skill.TradeoffToggle` imported `ServerPlayer` and `VaultDimensions`), so what could
be unit-tested was decided class by class. The committed `gradle.properties` pinned
`org.gradle.java.installations.paths` to `E:/Github/...`, which broke every other checkout.

The seams have to be agreed while the tree is empty (S43), and the package table in the Architect's section
of `docs/TEAM_REQUIREMENTS.md` was written for the hub tree. Since `DESIGN_LOG.md` 2026-10-26, "The
Resonance tree becomes a Path of Exile tree", there are no clusters, anchors, gates or node costs: every
node costs one point, adjacency is the only prerequisite, a node's ring is its level floor, and the tree's
shape is an authored JSON resource read into a `TreeLayout` (§6 Layout data, §11 Tree Layout Data). ADR 0008
added the mod's first mixin and a pure `WearRules` class, and the 2026-10-27 tickets add vein index
arithmetic and the trip record as pure logic.

## Decision

### Toolchain and pinned versions

Versions live in `gradle.properties`, except the NeoGradle plugin version, which is in the `plugins` block
of `build.gradle`, and the Gradle version, which is in `gradle/wrapper/gradle-wrapper.properties`.

| What | Pin | Where | Note |
|---|---|---|---|
| Java | 25, through `java.toolchain.languageVersion` | `build.gradle` | No `org.gradle.java.installations.paths` in any committed file. Each machine supplies its own JDK 25 through Gradle's auto-detection or its own user-level `~/.gradle/gradle.properties`. |
| Minecraft | `minecraft_version=26.1`, `minecraft_version_range=[26.1,26.2)` | `gradle.properties` | |
| NeoForge | `neo_version=26.1.2.95` | `gradle.properties` | The version the decompiled sources were read from (`docs/research/vanilla-ore-generation.md` section 7). |
| FTB Teams | `ftb_teams_version=26.1.2.4` | `gradle.properties` | Hard dependency, `implementation`, mandatory in `neoforge.mods.toml` (§2). |
| FTB Ultimine | `ftb_ultimine_version=26.1.2.6` | `gradle.properties` | `compileOnly`, and also `localRuntime` in the `ultimine` runtime profile. 26.1.2.6 is what FTB Evolution 2 ships (`docs/research/library-scan.md`) and the latest on the FTB maven; chosen over the last build's 26.1.2.5 in E1-S1 (#188). |
| NeoGradle | `net.neoforged.gradle.userdev` 7.1.38 | `build.gradle` `plugins` | |
| FTB maven | `https://maven.ftb.dev/releases`, restricted with `content { includeGroup 'dev.ftb.mods' }` | `build.gradle` `repositories` | As the last build had it. |
| FTB Library | `ftb_library_version=26.1.2.11` | `gradle.properties`, `implementation` | Comes with FTB Teams at 26.1.2.3, with Ultimine at 26.1.2.7 and with Materials at 26.1.2.8. Declared so compile and every runtime profile use one version: what FTB Evolution 2 ships. |
| Foojay toolchain resolver | `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 | `settings.gradle` `plugins` | As the last build had it. |
| JEI | `jei_version=29.22.0.74`, artifact `mezz.jei:jei-26.1.2-neoforge` | `gradle.properties`, `localRuntime` | Dev runtime only, in every profile. Later releases tried (29.34.0.91, 29.43.0.107) require NeoForge 26.1.2.99 or above and refuse to load on the pin. |
| BlameJared maven | `https://maven.blamejared.com`, restricted with `content { includeGroup 'mezz.jei'; includeGroup 'net.mezzdev.config' }` | `build.gradle` `repositories` | JEI, and its config library. |
| FTB Materials | `ftb_materials_version=26.1.2.6`, artifact `dev.ftb.mods:ftb-materials` | `gradle.properties`, `localRuntime` | Only in the `materials` runtime profile. `ftb-materials-neoforge` holds the 21.1 line only. |
| JSpecify | `jspecify_version=1.0.0` | `gradle.properties`, `compileOnly`, `testCompileOnly` | |
| JUnit 5 | `junit_version=5.12.2` | `gradle.properties`, `testImplementation` | |
| ArchUnit (`archunit-junit5`) | `archunit_version=1.5.1` | `gradle.properties`, `testImplementation` | Reads Java 25 class files: the main architecture test asserts it imported more than zero classes, and an unreadable class file fails that import rather than a rule (below). |
| Gson (`com.google.code.gson:gson`) | `gson_version=2.13.2`, the version Minecraft 26.1 ships, read from the dependency tree | `gradle.properties`, `testImplementation` | Declared at T1 so `core.layout`'s tests do not depend on what NeoGradle happens to put on the unit test classpath (Developer, round 1). |

Each version is re-checked once, at T1, then pinned. A bump of any of them is its own PR that changes
nothing else and repeats T1's checks on Nick's PC (`build`, `test --rerun-tasks` with XML counts,
`runGameTestServer`, `runClient`); never inside a feature PR (`CLAUDE.md` Build; Reviewer gate 12). A
NeoForge bump also re-runs ADR 0008's wear gametest and the dimension creation path, since both lean on
NeoForge internals.

Access transformers are in `src/main/resources/META-INF/accesstransformer.cfg`, registered with
`minecraft.accessTransformers.file`, as the last build did. Every line is preceded by a comment naming the
ticket that needs it (Reviewer gate 7). T1 commits the file empty or absent; each AT arrives with the code
that uses it.

### Gametests

Gametests live in their own source set, `src/gametest/java`, package `com.orevault.orevault.gametest`, added to
the `gameTestServer` run with `modSource`. None of it reaches the jar, and it sits outside the ArchUnit
import, so it has no package row. `check` compiles it.

The rules for a new gametest (E1-S2, #193):

- It is registered in `GameTests`, the one class listening for these events: its function under `RegisterEvent` for `Registries.TEST_FUNCTION`
  and its instance under `RegisterGameTestsEvent` (mod bus). No `test_instance` JSON.
- Namespace `orevault`, id `<area>/<name>`, where the area is the system under test: `harness/passes`.
- 26.1 forms batches from test environments, so a batch is an environment. Every test uses `orevault:default`
  unless it needs a setup of its own (weather, time of day), which gets its own environment named for it.
- A test is `required`. `runGameTestServer` exits with the number of required failures, so one failure fails
  the Gradle task.
- A count quoted from the log includes the optional `minecraft:always_pass`, which is not ours, and the PR
  states the total it expects: passing tests log no name, and a test that failed to register exits 0. The
  total is 2 at E1-S2.
- There is no namespace filter: nothing in NeoForge 26.1.2.95 reads `neoforge.enabledGameTestNamespaces`, so the
  build does not set it. `--tests` is the vanilla selector.

### Mixins

ADR 0008's mixin is the only one. Its classes live in `com.orevault.orevault.mixin`; its config is
`src/main/resources/orevault.mixins.json`, declared by a `[[mixins]]` entry in
`src/main/resources/META-INF/neoforge.mods.toml`. Unverified: to be read by E1-S5 (#195): the exact
`[[mixins]]` keys NeoForge 26.1.2.95's FML accepts, the mixin config's `compatibilityLevel` for Java 25,
and whether a refmap is needed (the NeoForge sources tree read for this run holds patched Minecraft only,
not FML). The mixin config lands with the first mixin class, not at T1.

Any further mixin needs its own ADR, stating why no NeoForge event or extension point serves, the target
and its signature as read from the sources, and the gametest that pins it.

### Package layout

Base package `com.orevault.orevault`. The split that matters is pure against Minecraft-facing. Every
package may import `java.*`, `org.jspecify.annotations.*` and its own classes; the last column lists what
else it may import. "MC" means `net.minecraft..`, `net.neoforged..`, `com.mojang..` and `org.slf4j..`.
"core" means any `core.*` package.

Pure packages. None imports Minecraft, NeoForge or Mojang classes, so all of it runs in JUnit.

| Package | Holds | May also import |
|---|---|---|
| `core.ore` | `Rarity` with its base Resonance (§4.2); the rarity rule (100 expected blocks a chunk, 4.5 blocks a vein, §4.2); `OreTable`, the classifier's output as plain values; `HeightProfile` (ADR 0009) | nothing |
| `core.layout` | `TreeLayout` over the layout file (§6 Layout data): nodes with id, skill, class, marks, ring, required mod or tag, exclusive group and drawing position; edges; each fork's `petalAnchor`; the layout hash. `NodeClass` and the marks. Distances by breadth-first search, the cheapest path, and the layout rules §6 lists | `com.google.gson`, to read the file from the jar |
| `core.tree` | `SkillTree`: buyable when adjacent to an owned node and the ring is at most team level; refund by connectivity and price (§4.4); a skill's tier as the count of its owned nodes; fork choices; exclusive pairs; hidden and dormant nodes; points from level. The `Refusal` enum with its lang keys (ADR 0006). The tree constants (`LEVEL_CAP`, `POINTS_PER_LEVEL`, refund prices, §11 Tree Layout Data). The effects constants keyed by skill id and count owned. The reserved ids (§6.1). The team vote rule for Volatile Veins and Molten Seam (Q1). No clusters, anchors, gates or node costs | `core.layout` |
| `core.curve` | `LevelCurve`, `TeamScaling` | `core.gen` (the baseline's density and shares), `core.ore` |
| `core.gen` | `OreBaseline` (ADR 0009), `VeinShape`, `VeinPlacer`, `GenSnapshot` and `TeamEffects` with their pure builders from plain values (ADR 0003), vein extents (ADR 0004), the chunk seed | `core.ore`, `core.tree` |
| `core.mining` | `ResonanceBonuses`, the drop-stage outcome (§11 Drop Pipeline), `MiningSpeedModifiers` | `core.ore`, `core.tree`, `core.vein` (the completion outcome the vein index decrement returns to the drop pipeline, ADR 0004) |
| `core.vein` | Vein index arithmetic: remaining count, completion, abandon; `VeinRecord`, the header bit layout, and the encoder from `core.gen`'s vein extents (ADR 0004); the explored map's cells, window, compass choice and shade (ADR 0004 Amendment 1) | `core.gen` (`VeinExtent`) |
| `core.session` | The trip record (§11 Vault Session State), including Apprentice's Ledger's 10-minute continuation (Q9) | nothing |
| `core.reset` | The reset vote state machine (ADR 0007) | nothing |
| `core.wear` | `WearRules` (ADR 0008) and `WearScope`, the per-player wear inputs the mixin reads | `core.tree` |
| `core.key` | `VaultKeys`: the one function pair between a team UUID and the lower-case hex form used by the dimension path `vault_<teamUuidNoHyphens>` and the team `SavedData` id (ADR 0002, ADR 0005 "one function parses both"), so any adapter can tell whether a level is a Vault and whose without importing `vault` | nothing |

Minecraft-facing adapters. They hold as little logic as they can and call into `core`.

| Package | Holds | May import |
|---|---|---|
| root | `OreVault`, the `@Mod` entry; wires the mod bus and `NeoForge.EVENT_BUS` (rule 3); the two ordering listeners, `ServerStartedEvent` and `ServerTickEvent.Post` (below) | MC, core, every adapter except `client`; `integration.*` only through its entry class, after the `isLoaded` check. `OreVault.MOD_ID` may be read by any adapter; no other root member may be, and `core` may not read it |
| `registry` | `DeferredRegister` holders, data components, attachments, tags, creative tab | MC, core, `vault`, `mining` (the blocks, items and entities it registers) |
| `team` | `TeamHelper` and the FTB Teams event listeners | MC, `dev.ftb.mods.ftbteams..`, core, `data`, `vault` |
| `data` | `OreVaultTeamData`, `PlayerStats`, the per-Vault explored map `SavedData` (ADR 0004 Amendment 1), attachment codecs other than the vein index's, migrations; `GenSnapshots`, the holders and the main-thread code that reads team data into plain values and calls `core.gen`'s builders (ADR 0003); `SyncOutbox`, the outbound sync set (below) | MC, core. Never `team`: a rebuild takes the team's member list and online members as values from its caller |
| `ore` | `OreClassifier`: reads `#c:ores` and the placed features at server start and publishes an immutable `OreTable` (ADR 0009) | MC, core |
| `vault` | `VaultLevels` (the one world-map insert, with `markWorldsDirty()`), `VaultDimensions` (find-or-create, restore, archive), `VaultFolders` and the start-of-server pass (ADR 0007), the Vault `ChunkEvent.Load` listener (vein index settle, explored map; ADR 0004 Amendment 1), the end-of-tick empty-Vault check that applies a pending generation vote (ADR 0003), the end-of-tick chunk entry check that sets a chunk's `entered` flag and credits `chunks_entered` (ADR 0004 Amendment 1, Nick 2026-10-27), teleport, the portal and frame blocks, the igniters, the layer config (ADR 0002) | MC, core, `data`, `worldgen`, `worldgen.index`, `team`, `registry` |
| `worldgen` | `VaultChunkGenerator` and its codec | MC, core, `ore`, `worldgen.index`; never `data` or `team` |
| `worldgen.index` | The `orevault:vein_index` chunk attachment: its record, its codec and its `AttachmentType` holder in its own `DeferredRegister`, registered on the mod bus from the `@Mod` constructor (ADR 0004 "The attachment"). A leaf, so the generator can call `setData` on the `ProtoChunk` without reaching `registry`, which reaches `data` through `vault`. The same leaf holds the map value type if ADR 0004 takes its Result B | MC, `core.vein` |
| `mining` | The single `BlockDropsEvent` listener (§11 Drop Pipeline), the orb entity, the award, stat recording, vein index updates, filling `core.wear.WearScope` | MC, core, `data`, `ore`, `team`, `registry`, `worldgen.index` |
| `network` | Payload records, server handlers and `TomeActions` (ADR 0006); the end-of-tick drain of `data.SyncOutbox` | MC, core, `data`, `team`, `vault`, `reset` (`StartReset` and `CastResetVote` drive ADR 0007's sequence), `worldgen.index` (`RequestSurvey` reads the chunk's stamp) |
| `mixin` | ADR 0008's mixin; no logic | MC, `core.wear` |
| `chunkload`, `reset`, `config`, `debug` | Adapters for §3.4 chunk loading, ADR 0007, §10 and S44 | MC, core, `data`, `ore`, `team`, `vault`, `worldgen`, `worldgen.index`, `registry`, `network` |
| `integration.ultimine` | Everything naming an FTB Ultimine type, entered only after `ModList.get().isLoaded("ftbultimine")` | MC, `dev.ftb.mods.ftbultimine..` and whatever FTB Library types its API exposes (to confirm at T12), core, `data`, `mining` |
| `integration.processing` | The `c:clumps/`, `c:shards/` and `c:crystals/` tag lookups for Ore Doubling's extra tiers (§2, §9) | MC, core |
| `client` | The `@Mod(dist = Dist.CLIENT)` entry, `client.tome`, `client.tree`, `client.net`, renderers, the on-screen Resonance bar, `ClientTomeState` | anything, subject to the named rules below |

A row names one package, not its subpackages: `worldgen` and `worldgen.index` are separate rows and
separate rules.

Outbound sync goes through one pattern. `team`, `mining` and `vault` never import `network`. When one of
them changes something a client shows, it records what changed in `data.SyncOutbox`, a main-thread set of
entries keyed by team or player and payload kind (a team's `TreeState`, `TeamProgress` or `ResetVoteState`
is stale, a player's `EntryPoints` is stale, a player has a `VeinOutline` waiting with its positions), and returns.
`network` drains the outbox at the end of the server tick, last in the root tick listener (below), builds each payload once from the current state
and sends it (ADR 0006 Triggers). The triggers that use it are the ones raised outside `network`: a member's
team change (`team`), Resonance gain, a level-up and a vein outline (`mining`), and a pending generation
vote applied in an empty Vault (`vault`). A level-up's `TeamProgress` still reaches the client in the tick
it happens, so ADR 0006's "at once on a level-up" holds. `reset`, `chunkload`, `config` and `debug` may call
`network` directly, as their row allows; mutations `network` itself accepts or refuses resync in the
handler. Several changes in one tick then cost one payload, which is also what the 10-tick coalescing of
`TeamProgress` needs.

Two listeners in the root package fix an order that no single adapter can, because each calls jobs in
more than one package. The root row may import every adapter except `client`, so neither widens another
row (Reviewer RV2-2, Developer DEV2-3, round 2).

- The `ServerStartedEvent` listener calls, in order: the start-of-server pass (`vault`, ADR 0007), the ore
  classifier (`ore`, ADR 0009), the pending generation-tradeoff changes (ADR 0003), then the restore
  (`vault`, ADR 0002). `vault` does not import `ore`; the order lives here.
- One `ServerTickEvent.Post` listener calls, in order: the unload drain (`vault`, ADR 0002), the empty-Vault
  vote apply (`vault`, ADR 0003), the chunk entry credit (`vault`, ADR 0004 Amendment 1), `WearScope`
  clearing and Kit Care tiers (`mining`, ADR 0008), then the `data.SyncOutbox` drain (`network`) last. The
  drain runs last so that every change made in the tick, a level-up's `TeamProgress` and an applied vote's
  `TreeState` among them, is sent in that tick. No adapter registers its own `ServerTickEvent.Post`
  listener for these jobs; each exposes a method the root listener calls. Where another record says a job
  runs from a `ServerTickEvent.Post` listener (ADR 0002's unload drain), this is that listener.

Blocks, items and entities live in the package of the system that owns their behaviour: the frame, portal
and igniters in `vault`, the orb entity in `mining`. `registry` only holds the registers. Where the Tome item
sits is decided by ADR 0006, with its screen-opening path.

`integration.mekanism` in the Architect's table is renamed `integration.processing`. Mekanism has no code
reference at all (§2, Nj); the package reads `c:` tags that FTB Materials fills too
(`docs/research/ftb-materials.md`), and a package of ours named `mekanism` would trip the rule that no
import contains "mekanism".

`core.*` packages may depend on each other only as the table allows, and never in a cycle. Helper classes
are `final` with a private constructor (`CLAUDE.md` Conventions). No interfaces for single
implementations, no service layer, no mod-internal event bus.

`core.wear.WearScope` exists so the mixin can call `core.wear` only. It is a main-thread table, keyed by
player UUID, of the wear inputs `WearRules` needs (whether the hand-breaking flag is set, the summed care
chance, Tempered, Unyielding Edge, Glass Edge and Galvanic Seam multipliers, Kit Care's tier). `mining`
fills an entry when a hand break passes `BreakBlockEvent` and clears it when the break ends and at the end
of the tick (the root tick listener); at the end of each tick it sets each online real player's Kit Care tier from the effects view
(ADR 0003), 0 inside a Vault, and removes it at logout. The mixin checks that the entity is the
`ServerPlayer` the player list holds for its UUID, looks up the entry and passes the scope and the amount
to `WearRules`; an absent entry means vanilla wear. ADR 0008 (accepted by E0-S11, #226) has the details.

### Architecture rules

One ArchUnit test class in `src/test/java`, at test scope, importing the main classes of
`com.orevault.orevault` without test classes.

One rule per row of the two tables above: classes in that package should only depend on classes in the
packages its row allows (plus `java..`, `org.jspecify..` and itself). For `client` the row rule is empty
and only the named rules apply.

Named rules, which hold whatever the rows say:

1. No class in `worldgen..` (`worldgen.index` included) or `core.gen` depends, directly or transitively
   through Ore Vault's own classes, on `..orevault.data..`, `..orevault.team..`, any class in
   `net.minecraft.world.level.saveddata..`, `net.minecraft.world.level.storage.SavedDataStorage`, or any
   `dev.ftb..` class (S15; `CLAUDE.md` rule 1; the same list as ADR 0003, Enforcement). In 26.1 the level's storage class is `SavedDataStorage`, returned by
   `ServerLevel#getDataStorage()` (`net/minecraft/server/level/ServerLevel.java` line 1489, NeoForge
   26.1.2.95 sources); `DimensionDataStorage` does not exist in that tree.
2. No class outside `team` depends on `dev.ftb.mods.ftbteams..` (`CLAUDE.md` rule 5).
3. No class depends on a class whose fully qualified name contains `mekanism`, case-insensitive (§2, rule
   5). The Reviewer's grep for `mekanism` in imports stays as a second check.
4. No class outside `integration.ultimine` depends on `dev.ftb.mods.ftbultimine..` (S24).
5. No class outside `client` depends on `..orevault.client..`, `net.minecraft.client..`,
   `com.mojang.blaze3d..` or `net.neoforged.neoforge.client..` (rule 4; the first part of Reviewer gate
   4).
6. No class in `core..` depends on `net.minecraft..`, `net.neoforged..` or `com.mojang..`, and the `core`
   packages are free of cycles.
7. Only `vault.VaultLevels` calls `MinecraftServer#forgeGetWorldMap()` or `MinecraftServer#markWorldsDirty()`
   (both public in `net/minecraft/server/MinecraftServer.java`, lines 1892 and 1899, NeoForge 26.1.2.95
   sources), and only `VaultDimensions`' find-or-create and restore paths call `VaultLevels.register`
   (Reviewer gate 2; `CLAUDE.md` rule 2; the details are ADR 0002's).
8. Classes in `mixin` depend on nothing of Ore Vault's except `core.wear` (ADR 0008).

S43 stands as settled: at T1 each rule is shown failing once against a planted violation, except named
rule 7, which lands with E3-S6a (#237) where its classes exist and is shown failing there, because ArchUnit
fails a rule that matches nothing unless told not to, and a vacuous pass is not evidence. This record adds
how: each rule is an entry in `ArchitectureRules` the main test runs over the main classes, and a second test runs every rule over
a fixture set of planted violations under `src/test/java` (a `worldgen` class importing a `data` class, a
`core` class importing a Minecraft class, and so on) and asserts that each one fails. That keeps the
evidence permanent rather than a one-off screenshot. No rule is weakened with `allowEmptyShould(true)`; a
rule over a package that has no classes yet runs only against its fixture until the package's first class
lands, in the same PR.

Because `allowEmptyShould` is off, a rule also fails when it matches no class at all, so a fixture whose
package name is mistyped would make the fixture test pass while proving nothing (QA, round 1). Each fixture
assertion therefore checks that the rule's failure report (`EvaluationResult#getFailureReport`) names the
planted class, for example `PlantedWorldgenImportsData`, not only that the rule failed. The main test
asserts that the imported main class set is not empty; that is also T1's evidence that the pinned ArchUnit
reads Java 25 class files, since a class file it cannot read fails the import.

T1's PR quotes `git grep -n installations.paths -- ':!*.md'` returning nothing, as the check that no
committed file outside the documents pins a machine-local JDK.

### Porting the old pure classes (S42)

The old pure classes are ported, one ticket per class, through normal review; they are not rewritten from
the spec. Each port goes into its `core` package with no Minecraft imports, without stored derived totals
and without `NodeMigration`. Minecraft-facing adapters are rewritten. The candidates, read at the last code
commit before the restart (1e7cdad) and on old PR #153 (`refs/pull/153/head`):

- `skill/LevelCurve.java` and `skill/TeamScaling.java` (no Minecraft imports) into `core.curve`; the curve
  is re-derived from `OreBaseline` (ADR 0009), not from the old 70/25/5 constants.
- `skill/ResonanceBonuses.java` and `skill/MiningSpeedModifiers.java` (PR #153, no Minecraft imports) into
  `core.mining`, and the pure outcome logic of `event/DropPipeline.java` split from its listener.
- `session/VaultTrip.java` (PR #153) into `core.session`, without its `NodeCosts` import.
- The hub-era `TreeLayout`, `SkillTree`, `NodeDefs`, `NodeCosts`, `Cluster` and `NodeMigration` are not
  ported: the PoE tree retired what they model. Their tests are worth reading for the property-test style.

Nick can overrule S42.

## Consequences

- T1 lands the ArchUnit rules, their test and its fixtures, `gradle.properties` with the pins
  above and no machine path. Each later ticket adds classes into a package this record names, or amends
  this record first.
- A new package needs a row here and its rule in the same PR.
- `core.layout` depends on Gson. T1 declares it as a `testImplementation` at the version Minecraft ships
  (pin table), so the unit tests do not rely on NeoGradle's test classpath.
- `team`, `mining` and `vault` reach the client only through `data.SyncOutbox`, so a sync is one tick late
  at most and is built from the state at the end of the tick, not from the state at the moment of the
  change.
- The mixin seam goes through `core.wear.WearScope`, which is main-thread state in a pure package. ArchUnit
  cannot check threading; the Reviewer does.
- Builds and every check above run on Nick's PC (`CLAUDE.md` Build). Nothing in this record was compiled.

## Rejected alternatives

- One `skill` package for rules, data and effects, as before. It made purity a per-class accident and let
  `TradeoffToggle` import `ServerPlayer`.
- A Gradle subproject for the pure code. It enforces purity at compile time, but NeoGradle's userdev setup
  with two projects is more toolchain risk than one ArchUnit test, and the rules beyond purity (FTB Teams
  only in `team`, nothing from `worldgen` to `data`) still need ArchUnit.
- Grep checks instead of ArchUnit. They miss fully qualified references and transitive paths, which is how
  the old generator reached `data`.
- Widening the `team`, `mining` and `vault` rows to import `network`, which the round 1 review found each
  ADR would otherwise force on its first PR. It makes every listener that changes state also a sender, and
  several changes in one tick then send several payloads; the outbox gives one place that sends.
- Strict acyclic rules across the adapters. `team` must call `vault` on a disband and `vault` must ask
  `team` for a player's team; breaking that cycle would need the internal event bus this layout rules out.
- Keeping `integration.mekanism` as the package name, for the reason above.
- Pinning the JDK through `org.gradle.java.installations.paths` in the repo. It broke every other checkout.

## Spec sections changed

§13 Infrastructure's "Package structure created" line, which named the pre-restart packages. Applied in the
same PR. No other spec section changes.

## Reviews

Matrix row, system design: developer (buildable), reviewer (rules), qa (testable); writer added because the
record changes a spec line. Round 1, 2026-10-27. Each objection is marked fixed (what changed here) or
rebutted (the evidence). The record stays proposed until each reviewer confirms its objections landed in
round 2.

- developer: request changes (DEV-1, DEV-2). Checked every package row against what ADRs 0002 to 0009 make
  each package call; the eight named rules against rule 1's transitive wording; `SavedDataStorage`
  (`ServerLevel.java` line 1489, no `DimensionDataStorage` in the tree); `forgeGetWorldMap` and
  `markWorldsDirty` (`MinecraftServer.java` lines 1892, 1899); the old pins (`git show 1e7cdad`
  `gradle.properties` and `build.gradle`). Found pins, AT location, mixin placement and the S42 port list
  buildable as written.
  - DEV-1, the generator cannot reach the vein-index attachment type without breaking rule 1: fixed. The
    attachment's record, codec and holder are in the leaf `worldgen.index` (MC and `core.vein` only), with
    its own row; `worldgen`, `vault`, `mining`, `network` and the shared adapter row may import it.
  - DEV-2, rows missing imports: fixed. `network` imports `reset` and `worldgen.index`; `data` rebuilds take
    the member list as values from the caller; `mining` tells a Vault from its key through the new pure
    `core.key` rather than importing `vault`.
  - Gson answer (architect question 4): fixed. Gson is a pinned `testImplementation` at T1; the "if it is
    not" branch is gone.
- reviewer: request changes (RV-9). Checked package rows and import columns; named rules 1 to 8; S15 and S43
  with a permanent fixture test and no `allowEmptyShould`; pins and the re-check-once rule; no machine JDK
  path; the AT file with ticket comments; mixin location and "further mixins need an ADR"; the S42 port
  list; the `integration.processing` rename.
  - RV-9, import columns that forbid calls other ADRs require: fixed, with the pattern the Reviewer named.
    `team`, `mining` and `vault` record changes in `data.SyncOutbox` and `network` drains it at the end of
    the tick, so none of the three imports `network`; `network` may import `reset`; `data` takes the member
    list as values; `core.mining` may import `core.vein`. Named rule 1 now carries ADR 0003's list (the
    whole `saveddata` package and any `dev.ftb` class) and covers `worldgen..`.
  - RV-16 (raised under ADR 0006) is the same finding and is answered by the same change.
- qa: approve with comments (QA-1, QA-2). Checked that each package row and named rule maps to an ArchUnit
  rule; S43's "shown failing" mechanism; T1's version and machine-path checks; Gson on the classpath and
  ArchUnit reading Java 25 as T1 pass/fail items.
  - QA-1, a mistyped fixture passes vacuously: fixed. Each fixture assertion checks that the failure report
    names the planted class; the main test asserts a non-empty import.
  - QA-2, no stated check for "no machine path" and "reads Java 25": fixed. T1's PR quotes `git grep -n
    installations.paths` empty, and the non-empty import is the Java 25 check.
- writer: approve with comments (WRITER-1). Checked the package table against the ticket's list
  (`core.vein`, `core.session`, `core.wear`, `mixin`, no clusters or costs); the named rules against
  `CLAUDE.md` rules 1, 2, 4, 5 and S15, S24, S43; the pin table against `CLAUDE.md` Build; §13
  Infrastructure against the record; the rarity rule quoted in `core.ore` against §4.2.
  - WRITER-1, "Spec sections changed" pointed at a session file outside the repo: fixed. The section now
    names the §13 line and says it was applied in the same PR.

Round 2, 2026-10-31, against HEAD 0c5736c; every item ruled in the Manager's round 2 triage (section 2), and
there is no round 3: each role that raised an item confirms its line landed. Every round 1 objection above
was reported resolved by the role that raised it.

- developer: request changes (DEV2-1, DEV2-3). Checked each round 1 fix at its line (the `worldgen.index`
  row and rule 1, the `network` and `data` rows, `core.key`, the Gson pin) and the end-of-tick jobs the other
  records add across four packages.
  - DEV2-1, the `vault` row still credited the Wanderer's Cache count from `ChunkEvent.Load`, the rule Nick
    rejected on 2026-10-27: fixed as ruled. The credit is the end-of-tick chunk entry check in the `vault`
    row; the `ChunkEvent.Load` listener keeps the vein index settle and the explored map only.
  - DEV2-3, five end-of-tick jobs with no stated order: fixed as ruled. One root `ServerTickEvent.Post`
    listener calls them in a stated order with the `SyncOutbox` drain last, so the level-up promise in the
    outbound sync paragraph holds.
- reviewer: approve with comments (RV2-2). Checked named rule 1 against the `worldgen.index` leaf and the
  `worldgen` to `ore` import (no path to `data`, `team` or `saveddata`), rule 7 against `reset` calling
  `ensureTeamDimension`, and the `SyncOutbox` drain against ADR 0006's "at once on a level-up".
  - RV2-2, the `vault` row cannot run the classifier ADR 0002 orders inside its `ServerStartedEvent`
    listener: fixed as ruled. The listener is in the root package and calls `vault`, `ore`, the tradeoff
    apply and `vault` in that order; the `vault` row does not gain `ore`. ADR 0002's restore intro and ADR
    0007's pass intro name the root listener (WP-R2-1).
- qa: approve. Checked QA-1 and QA-2 at their lines (the failure report names the planted class; `git grep`
  empty; the non-empty import as the Java 25 check).
- writer: approve with comments (WRITER-22, merged into DEV2-1; WRITER-21). Checked the round 1 pointer fix
  and the `vault` row against spec §6.1 Wanderer's Cache.
  - WRITER-22: fixed with DEV2-1.
  - WRITER-21, one name for two counts: fixed as ruled. The saved count is `chunks_entered`; "explored"
    in this record now means generated chunks only (the explored map).
- architect (read across ADRs 0001 to 0009): approve; consistent on every name checked (`core.key`,
  `data.SyncOutbox`, `worldgen.index`).

Amended 2026-10-31 by E1-S1 (#188), on the Architect's sketch verdict: the pin table's new rows and Ultimine choice, the check moved from E1-S1 to E1-S3, and the root row's `OreVault.MOD_ID` clause. That clause is the Architect's ruling. ArchUnit cannot enforce it, because javac inlines the constant, so the Reviewer checks it by eye.

Accepted on these verdicts and the Manager's rulings. The ArchUnit check on Java 25 class files is owed to
T1 and is not a condition of acceptance, since nothing can be compiled before T1 exists.
