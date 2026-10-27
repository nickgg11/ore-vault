# 0001. Toolchain, package layout and architecture rules

Status: proposed, 2026-10-27, for T1 (E0-S3, #179). Spec: §2; `CLAUDE.md` Build and technical rules 1, 3, 4
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
| FTB Ultimine | `ftb_ultimine_version=26.1.2.5` | `gradle.properties` | `compileOnly`. Pinned at the last build's 26.1.2.5 and re-checked against 26.1.2.6, the version FTB Evolution 2 ships (`docs/research/library-scan.md`); T1 either keeps 26.1.2.5 or moves to 26.1.2.6 and pins that. |
| NeoGradle | `net.neoforged.gradle.userdev` 7.1.38 | `build.gradle` `plugins` | |
| FTB maven | `https://maven.ftb.dev/releases`, restricted with `content { includeGroup 'dev.ftb.mods' }` | `build.gradle` `repositories` | As the last build had it. |
| JUnit 5 and ArchUnit (`archunit-junit5`) | chosen at T1, then pinned | `gradle.properties`, `testImplementation` | T1 confirms the chosen ArchUnit release reads Java 25 class files. |

Each version is re-checked once, at T1, then pinned. A bump of any of them is its own PR that changes
nothing else and repeats T1's checks on Nick's PC (`build`, `test --rerun-tasks` with XML counts,
`runGameTestServer`, `runClient`); never inside a feature PR (`CLAUDE.md` Build; Reviewer gate 12). A
NeoForge bump also re-runs ADR 0008's wear gametest and the dimension creation path, since both lean on
NeoForge internals.

Access transformers are in `src/main/resources/META-INF/accesstransformer.cfg`, registered with
`minecraft.accessTransformers.file`, as the last build did. Every line is preceded by a comment naming the
ticket that needs it (Reviewer gate 7). T1 commits the file empty or absent; each AT arrives with the code
that uses it.

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
| `core.tree` | `SkillTree`: buyable when adjacent to an owned node and the ring is at most team level; refund by connectivity and price (§4.4); a skill's tier as the count of its owned nodes; fork choices; exclusive pairs; hidden and dormant nodes; points from level. The tree constants (`LEVEL_CAP`, `POINTS_PER_LEVEL`, refund prices, §11 Tree Layout Data). The effects constants keyed by skill id and count owned. The reserved ids (§6.1). The team vote rule for Volatile Veins and Molten Seam (Q1). No clusters, anchors, gates or node costs | `core.layout` |
| `core.curve` | `LevelCurve`, `TeamScaling` | `core.gen` (the baseline's density and shares), `core.ore` |
| `core.gen` | `OreBaseline` (ADR 0009), `VeinShape`, `VeinPlacer`, `GenSnapshot` (ADR 0003), vein extents (ADR 0004), the chunk seed | `core.ore`, `core.tree` |
| `core.mining` | `ResonanceBonuses`, the drop-stage outcome (§11 Drop Pipeline), `MiningSpeedModifiers` | `core.ore`, `core.tree` |
| `core.vein` | Vein index arithmetic: remaining count, completion, abandon (ADR 0004) | nothing |
| `core.session` | The trip record (§11 Vault Session State), including Apprentice's Ledger's 10-minute continuation (Q9) | nothing |
| `core.reset` | The reset vote state machine (ADR 0007) | nothing |
| `core.wear` | `WearRules` (ADR 0008) and `WearScope`, the per-player wear inputs the mixin reads | `core.tree` |

Minecraft-facing adapters. They hold as little logic as they can and call into `core`.

| Package | Holds | May import |
|---|---|---|
| root | `OreVault`, the `@Mod` entry; wires the mod bus and `NeoForge.EVENT_BUS` (rule 3) | MC, core, every adapter except `client`; `integration.*` only through its entry class, after the `isLoaded` check |
| `registry` | `DeferredRegister` holders, data components, attachments, tags, creative tab | MC, core, `vault`, `mining` (the blocks, items and entities it registers) |
| `team` | `TeamHelper` and the FTB Teams event listeners | MC, `dev.ftb.mods.ftbteams..`, core, `data`, `vault` |
| `data` | `OreVaultTeamData`, `PlayerStats`, attachment codecs, migrations; the main-thread builders of `GenSnapshot` and the effects view (ADR 0003) | MC, core |
| `ore` | `OreClassifier`: reads `#c:ores` and the placed features at server start and publishes an immutable `OreTable` (ADR 0009) | MC, core |
| `vault` | `VaultLevels` (the one world-map insert, with `markWorldsDirty()`), `VaultDimensions` (find-or-create, restore, archive), teleport, the portal and frame blocks, the igniters, the layer config (ADR 0002) | MC, core, `data`, `worldgen`, `team`, `registry` |
| `worldgen` | `VaultChunkGenerator` and its codec | MC, core, `ore`; never `data` or `team` |
| `mining` | The single `BlockDropsEvent` listener (§11 Drop Pipeline), the orb entity, the award, stat recording, vein index updates, filling `core.wear.WearScope` | MC, core, `data`, `ore`, `team`, `registry` |
| `network` | Payload records and server handlers (ADR 0006) | MC, core, `data`, `team`, `vault` |
| `mixin` | ADR 0008's mixin; no logic | MC, `core.wear` |
| `chunkload`, `reset`, `config`, `debug` | Adapters for §3.4 chunk loading, ADR 0007, §10 and S44 | MC, core, `data`, `ore`, `team`, `vault`, `worldgen`, `registry`, `network` |
| `integration.ultimine` | Everything naming an FTB Ultimine type, entered only after `ModList.get().isLoaded("ftbultimine")` | MC, `dev.ftb.mods.ftbultimine..` and whatever FTB Library types its API exposes (to confirm at T12), core, `data`, `mining` |
| `integration.processing` | The `c:clumps/`, `c:shards/` and `c:crystals/` tag lookups for Ore Doubling's extra tiers (§2, §9) | MC, core |
| `client` | The `@Mod(dist = Dist.CLIENT)` entry, `client.tome`, `client.tree`, renderers, the on-screen Resonance bar, `ClientTomeState` | anything, subject to the named rules below |

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
of the tick; it fills Kit Care's entry from the effects view (ADR 0003) for real players only. The mixin
casts the entity to `ServerPlayer`, looks up its UUID and passes the scope and the amount to `WearRules`;
an absent entry means vanilla wear. This refines ADR 0008's "reads the team's nodes from the main-thread
team snapshot"; E0-S11 (#226) should fold it in when it confirms that record.

### Architecture rules

One ArchUnit test class in `src/test/java`, at test scope, importing the main classes of
`com.orevault.orevault` without test classes.

One rule per row of the two tables above: classes in that package should only depend on classes in the
packages its row allows (plus `java..`, `org.jspecify..` and itself). For `client` the row rule is empty
and only the named rules apply.

Named rules, which hold whatever the rows say:

1. No class in `worldgen` depends, directly or transitively through Ore Vault's own classes, on
   `..orevault.data..`, `..orevault.team..`, `net.minecraft.world.level.saveddata.SavedData` or
   `net.minecraft.world.level.storage.SavedDataStorage` (S15; `CLAUDE.md` rule 1). The same holds for
   `core.gen`. In 26.1 the level's storage class is `SavedDataStorage`, returned by
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

S43 stands as settled: at T1 each rule is shown failing once against a planted violation, because ArchUnit
fails a rule that matches nothing unless told not to, and a vacuous pass is not evidence. This record adds
how: each rule is a field the main test runs over the main classes, and a second test runs every rule over
a fixture set of planted violations under `src/test/java` (a `worldgen` class importing a `data` class, a
`core` class importing a Minecraft class, and so on) and asserts that each one fails. That keeps the
evidence permanent rather than a one-off screenshot. No rule is weakened with `allowEmptyShould(true)`; a
rule over a package that has no classes yet runs only against its fixture until the package's first class
lands, in the same PR.

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

- T1 lands the package skeleton, the ArchUnit test and its fixture test, `gradle.properties` with the pins
  above and no machine path. Each later ticket adds classes into a package this record names, or amends
  this record first.
- A new package needs a row here and its rule in the same PR.
- `core.layout` depends on Gson. T1 confirms Gson is on the unit test classpath; if it is not, it is added
  as a `testImplementation` at the version Minecraft ships, or the layout file is parsed in the adapter and
  handed over as plain records.
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
- Strict acyclic rules across the adapters. `team` must call `vault` on a disband and `vault` must ask
  `team` for a player's team; breaking that cycle would need the internal event bus this layout rules out.
- Keeping `integration.mekanism` as the package name, for the reason above.
- Pinning the JDK through `org.gradle.java.installations.paths` in the repo. It broke every other checkout.

## Spec sections changed

§13 Infrastructure's "Package structure created" line names the pre-restart packages. The replacement text
is in the run's `spec-changes/E0-S3.md` for the Writer to apply. No other spec section changes.

## Reviews

To be filled: developer, reviewer, qa, writer. Each verdict lists what it checked.
