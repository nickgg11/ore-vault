# Ore Vault

A **NeoForge 26.1 mod for Minecraft 26.1, targeting Java 25.** Per-team mining dimensions with boosted ore
density, a team-shared Resonance skill tree, and (post-1.0) opt-in mob farming via Disturbed Zones.

This is **not** a Bukkit/Spigot/Paper plugin and **not** Fabric or legacy Forge. There is no `plugin.yml`,
no `JavaPlugin`, no `@EventHandler`, no scheduler API. Suggestions built on those APIs are wrong here.

**The repo was restarted on 2026-10-25.** The previous implementation was removed. What it taught is in
`docs/LESSONS_LEARNED.md`, and `git log` before the restart still holds the old code and its commit
messages, several of which record a misleading symptom and its real cause. Read them before rebuilding the
same area.

## Sources of truth, in order

1. `OreVault_Design_and_Spec.md`: what the design is. Cited as `§N`. When behaviour and spec disagree, the
   spec wins or the spec is updated in the same PR.
2. `DESIGN_LOG.md`: why it changed and what was tried and rejected. **Read it before proposing a change to
   the skill tree, the Tome UI or the Resonance curve.** The rejected version is usually the one a fresh
   reading of the spec arrives at again.
3. `docs/LESSONS_LEARNED.md`: what went well and badly in the first build, the technical traps, and the
   designs not to re-propose.
4. `docs/TEAM_REQUIREMENTS.md`: what each role needs, checks and produces, written by the roles together.
5. This file: the rules for working in the repo.
6. `.github/TICKET_WORKFLOW.md`: how tickets and PRs are written and closed.

## The team

Work is done by ten roles, defined in `.claude/agents/`. Each is focused on one discipline and reviews
the others' work against its own checklist.

| Role | Owns |
| --- | --- |
| `manager` | Order of work, tickets, assignments, running reviews, breaking ties, asking Nick |
| `developer` | Java, Gradle, datapack JSON, resources |
| `qa` | Builds, test counts, dedicated-server boot, regression list, playtest scripts |
| `reviewer` | Reading diffs for bugs, rule violations, save-data risk, spec drift |
| `architect` | Package layout, data model, persistence and versioning, networking, threading; decision records in `docs/adr/` |
| `designer` | Art: textures, GUI sprites, the Tome's page, node frames and icons |
| `ui-ux` | Screen structure, the tree's layout rules and layout data, controls, tooltips, legibility |
| `playtester` | The player's experience: clarity, pacing in play, Nick's playtests and turning his feedback into tickets or design changes |
| `writer` | Spec, design log, lang text, tickets, PR bodies |
| `balancer` | Every number, the level curve, node interactions |

### How they work together

The roles are meant to disagree. Quick agreement is a reason to look harder, not a sign of quality.

1. **The Manager opens the work.** A ticket with spec references, why before what, checkable acceptance
   criteria, one owner role and the reviewers from the matrix below.
2. **The owner produces.** Code, art, a spec change, a balance table.
3. **Every reviewer in the matrix reviews independently,** against its own checklist only, and returns a
   verdict: approve, approve with comments, request changes, or block. A verdict lists what was checked.
   "Looks good" with nothing checked is sent back.
4. **The owner answers each objection** with a fix or with evidence (a spec line, a test, a screenshot, a
   number). Preference is not evidence.
5. **Two rounds, then the Manager.** Anything still disputed goes to the Manager, who decides from the
   documents and says which source settled it, or takes it to Nick.
6. **Only the Manager asks Nick.** One question at a time, with a recommendation. Anything visual needs
   Nick's yes on a rendered preview before it merges.

Roles for modpack compatibility and performance are deferred to later in development (Nick, 2026-10-26).

### Review matrix

| Work | Owner | Must review |
| --- | --- | --- |
| System design before code (T1, T3 to T8) | architect | developer (buildable), reviewer (rules), qa (testable) |
| Java, build, datapack | developer | reviewer, qa; architect if it touches persistence, sync or threading; balancer if it has numbers; ui-ux if it draws a screen |
| Textures, GUI art, frames | designer | ui-ux (legible), qa (seen in a client); then Nick |
| Screens, tree layout, tooltips | ui-ux | designer (art fits), developer (drawable from data), playtester (clear to a new player); then Nick |
| Numbers, node design | balancer | developer (buildable), writer (stated precisely), playtester (noticeable in play); ui-ux if it moves the tree |
| Spec, design log, lang | writer | balancer (numbers match), developer (matches the code) |
| Plan and sequencing | manager | balancer (baseline before modifiers), qa (each step verifiable), architect (foundations first) |
| Playtest plan and feedback triage | playtester | qa (steps), writer (design log) |

Whatever its row, any PR that adds or changes a lang key, a registry entry, or spec or design-log text
lists the Writer as a reviewer.

### Build order

Build the playable loop end to end in a real client first, then layer the tree on it: toolchain and an
empty mod that runs; one block and one item done the 26.1 way and seen in game; the dimension path complete
and survived a logout inside it; the ore baseline at the real density and mined for ten minutes; Resonance
and a level curve derived from that density; persisted data versioned; then the Tome, then nodes. The first
build did the reverse and shipped a Vault with almost no ore in it for a month.

## Build

**All code runs on Nick's PC, not in the cloud** (Nick, 2026-10-26). Builds, tests, `runClient`,
`runServer` and gametests run on his machine, where JDK 25, the Gradle caches and the dependencies already
are, and where he can playtest the same build. A cloud session can read and edit the repo but does not try
to build it: it hands off to a session on Nick's machine (Remote Control) for anything that compiles or
runs. Never report a build or test result that was not produced there.

```
./gradlew build                      # compile, unit tests, compile the gametests
./gradlew test --rerun-tasks         # unit tests, never cached
./gradlew runClient                  # dev client
./gradlew runServer                  # dev server
./gradlew runGameTestServer          # gametests on a dedicated server; catches client classes on common paths
```

`-Pruntime_profile=ultimine` or `-Pruntime_profile=materials` adds that mod to any run; the default `none` has
neither. `run/server/eula.txt` is untracked and needs `eula=true` for `runServer`.

Versions are pinned in `gradle.properties` and NeoGradle's in the `build.gradle` `plugins` block, with the
reasons in ADR 0001's pin table: `neo_version=26.1.2.95`,
FTB Teams 26.1.2.4, FTB Library 26.1.2.11, FTB Ultimine 26.1.2.6 (`compileOnly`), NeoGradle
`net.neoforged.gradle.userdev` 7.1.38. A bump is its own PR, never inside a feature PR. Do **not** pin
`org.gradle.java.installations.paths` to a machine-local path in a committed file; that broke every other
checkout last time.

**Before every PR:** `./gradlew build` and `./gradlew test --rerun-tasks` pass, with counts quoted from
`build/test-results/test/*.xml` (a cached `BUILD SUCCESSFUL` looks identical to a real one);
`runGameTestServer` passes with its total quoted and the expected total stated (passing tests log no name);
anything visual or worldgen was seen in `runClient` or is listed as unverified with playtest steps. Never run `./gradlew clean` in a git worktree; it breaks `:neoFormPatch`.

## Technical rules

**1. Threading: chunk generation is off-thread, SavedData is not.** The chunk generator runs on a background
executor. Team `SavedData` is main-thread-only. Hand the generator a thread-safe snapshot of team skill
state, refreshed on the main thread and passed as a `Supplier`. Reading `SavedData` from generation is a
race, not a style question.

**2. Dimension creation is lazy; restoration is eager.** FTB Teams auto-creates a one-player team for every
login, so a Vault per team would mean a `ServerLevel` per account ever seen. Only the portal path creates a
Vault. Vaults already on disk are re-registered at server start, before anyone connects, because a player
may have logged out inside one (otherwise they reload at Vault coordinates in the Overworld sky). After
adding a level at runtime, call `server.markWorldsDirty()`, or the level is never ticked and block breaking
silently stalls. Save the return position only after the teleport succeeds.

**3. Two event buses.** `NeoForge.EVENT_BUS` takes gameplay events. The `modEventBus` passed to the `@Mod`
constructor takes registries and lifecycle (`DeferredRegister`, colour handlers, config). A listener on
the wrong bus never fires and logs nothing.

**4. Side safety.** Client code lives under `client/` behind a `@Mod(dist = Dist.CLIENT)` entrypoint or
`FMLEnvironment.getDist().isClient()`. A common-path reference to a client class crashes a dedicated server
at class load, and no unit test catches it. Server logic that mutates state uses `ServerLevel` and
`ServerPlayer`.

**5. Dependency boundaries.**
- **FTB Teams**: hard dependency. Only the team helper class imports `dev.ftb.mods.ftbteams.*`. An unknown
  team has size 1, never 0. Every player always has a team (§2); never branch on "has no team".
- **FTB Library**: comes with FTB Teams, so its GUI toolkit costs players nothing (see
  `docs/research/library-scan.md`).
- **FTB Ultimine**: `compileOnly`, gated on `ModList.get().isLoaded("ftbultimine")`. Its 26.1 API is
  registration events.
- **Mekanism**: no compile-time reference at all. Item tags only, no reflection (§9; Nick, 2026-10-26).
- **Pufferfish's Skills**: not a dependency. See `docs/research/puffish-skills.md`.

**6. Persistence.** Team data is per-team `SavedData` in the server's storage
(`MinecraftServer#getDataStorage()`, `<world>/data/`), not the overworld's: in 26.1 `ServerLevel#getDataStorage()`
is a per-level store under `<world>/dimensions/`. Data about one Vault's terrain goes in that Vault level's
storage, so it is deleted and archived with the folder; data about one chunk goes on the chunk. These are
player save files. Version the data from the first field (`data_version` plus `migrate()`); every rename or removal gets
a migration, every added field reads correctly from a tag written without it.

**7. Config.** No config value without a reader. A value read once at start is marked `worldRestart()`.
Ore density and ore-per-hour are constants, not config (§4.3, design log).

**8. Data files.** Tool tags live in `data/minecraft/tags/block/mineable/`, not the mod namespace. Common
tags use `c:`, not `forge:`. Dimension heights are duplicated in Java on purpose so the layer config fails
loudly; change both or neither.

**9. Gameplay invariants.** Vanilla XP from ore is never suppressed (§3.4). Machine-broken blocks never pay
Resonance (§3.4). One `BlockDropsEvent` listener with fixed stages (§11).

## MC 26.1 API names

| Use | Not |
| --- | --- |
| `net.minecraft.resources.Identifier` | `ResourceLocation` |
| `Identifier.fromNamespaceAndPath(...)` | `new ResourceLocation(...)` |
| `SavedDataType<T>` + codec | `SavedData` load/save overrides |
| `CompoundTag#getCompoundOrEmpty` | `getCompound` returning null |
| `RegisterColorHandlersEvent.BlockTintSources` | `ColorHandlerEvent.Block` |
| `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` | `source.hasPermission(2)` |
| `GuiGraphicsExtractor` | `GuiGraphics` |
| `Screen#extractRenderState(graphics, mx, my, a)` | `Screen#render(...)` |
| `Screen#extractBackground(...)` | `Screen#renderBackground(...)` |
| `graphics.text(font, …)` / `centeredText` | `drawString` / `drawCenteredString` |
| `mouseClicked(MouseButtonEvent, boolean)` | `mouseClicked(double, double, int)` |
| `mouseDragged(MouseButtonEvent, dx, dy)` | `mouseDragged(double, double, int, double, double)` |
| `BLOCKS.registerBlock(name, factory, props -> …)` | `new Block(props)` without an id (startup crash) |
| `Inventory#getNonEquipmentItems()` | `Inventory.items`, private |
| `InteractionResult.SUCCESS` / `SUCCESS_SERVER` | `InteractionResult.sidedSuccess` |
| `MinecraftServer#execute(Runnable)`, or `schedule(TickTask)` | `BlockableEventLoop#tell` |
| `ServerPlayer#sendSystemMessage(message)` / `sendOverlayMessage(message)` | `Player#displayClientMessage` |
| `Level#getRandom()` | `Level.random`, protected |
| `ServerPlayer#level()`, or `(ServerLevel) entity.level()` on the server, then `getServer()` | `Entity#getServer()` |
| `CompoundTag#getInt`, `getString`, `getCompound` return `Optional`; `getIntOr` takes a default | getters returning the value |
| `CompoundTag#contains(String)` | `contains(String, int)` |
| `ChunkPos` is a record: `x()`, `z()`, `getMinBlockX()` | `ChunkPos.x`, `ChunkPos.z` as public fields |
| `getRespawnData().globalPos().pos()` | `getSharedSpawnPos()` |

Also: every item needs an `items/` model definition, a model and a texture, plus creative tab membership,
or it is invisible or the purple cube. Dimension lighting in 26.1 is set through attributes such as
`minecraft:visual/ambient_light_color`. Long tooltips do not wrap on their own; use
`font.splitIgnoringLanguage`.

Settle any doubt from the decompiled sources, not from memory: NeoGradle puts them in
`.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/<neo_version>/neoforge-<neo_version>-sources.jar`. Nullability uses
JSpecify (`org.jspecify.annotations.Nullable`).

## Tests

JUnit 5 under `src/test/java`, pure logic only: Minecraft's game classes do not load in this source set,
with one exception. `CompoundTag` and `NbtIo` do (`CompoundTagLoadsTest`, ADR 0005), so save-shape key-set,
default and migrate tests are unit tests as long as read, write and migrate sit in plain classes. Nothing
shows a `SavedData` subclass loads. Anything touching `Level` or registries belongs in a gametest or a
playtest. Gametests live in `src/gametest/java` and never ship; ADR 0001 has the conventions. `SavedData` and chunk attachments across a restart are the scripted stop and start in E3-S10
(#200), because a gametest is one server lifetime. Keep logic in plain classes so it
can be tested; do not ask for unit tests on code that cannot have them. Expect them on new pure logic,
especially skill-tree maths, the level curve and ore classification. Use
`superpowers:test-driven-development`, not another TDD skill.

## Textures

Block and item textures under `assets/orevault/textures/` are **16×16, as vanilla** (Nick, 2026-10-26;
this replaced a 64×64 rule). The bar is that the 16×16 is used well: the item reads as what it is at slot
size, not as a blob. GUI art is drawn at one texel per GUI unit so it sits sharp beside the vanilla font.
Vanilla sprites the mod borrows (the tinted experience orb) are not ours to redraw. The Designer's process and
the art tools are in `.claude/agents/designer.md` and `docs/research/art-tooling.md`.

## Conventions

- Commits: `feat:`, `fix:`, `docs:`, `ci:`, with the issue number: `fix: restore existing Vault dimensions
  at server start (#89)`.
- Helper classes are `final` with a private constructor and static methods.
- Javadoc on non-trivial classes explains *why*, with a `§` reference.
- Prose for humans (issues, PRs, comments) is plain: no inflated significance, no bolded inline-header
  lists, no rule-of-three padding, no emoji.
- **Say "ready to merge" in those words** when a PR is finished, and "don't merge yet" while commits are
  still coming. Nick merges as soon as a PR appears. Branches are deleted on merge
  (`delete_branch_on_merge`), so follow-ups go on a new branch from `main`. Avoid stacked PRs; if needed,
  give the merge order.
- Design-shaped feedback from a playtest gets a spec PR first, then code. Add a dated `DESIGN_LOG.md`
  entry whenever a playtest or Nick changes a decision.
- Name clashes with other mods in FTB packs (a Resonance mod and an Animus mod exist) are accepted for now;
  Nick may rename near the end, which would also touch flavour text.

## Automated PR review

`.github/workflows/claude-code-review.yml` runs a Claude review on every PR. With no
`CLAUDE_CODE_OAUTH_TOKEN` it now fails instead of skipping (#196), so a green check means the review steps ran.
`.claude/skills/address-review` pulls unresolved review comments
off a PR, fixes them and replies.

## Tooling

`jdtls-local` (Eclipse JDT.LS against a vendored `.tools/jdk25`) gave real Java diagnostics on Nick's
machine; it is untracked and only works when Claude Code is launched from the repo root. Keep
`jdtls-lsp@claude-plugins-official` and `serena` disabled. `remember` needs auto-compact off.
