> Snapshot of the handoff doc Nick wrote on 2026-10-25, copied into the repo so every agent can read it
> offline. The live copy is https://claude.ai/artifact/LQPX9CRc9rSCr24YAfSNxZ. The code it describes was
> removed in the restart that added this file: "Where things stand", the PR numbers and "First thing to do now"
> describe that old code, not the current tree. Everything else (what went well, what went poorly, the
> technical traps, the rejected designs, the build order) is the reason the agent team in `.claude/agents/` exists.

# Ore Vault: Handoff and Lessons Learned

2026-10-25 · Nick

Ore Vault is a NeoForge 26.1 mod (Minecraft 26.1, Java 25) that gives each FTB team its own mining dimension with boosted ore, a team-shared Resonance skill tree, and later an opt-in mob-farming layer. Between 2026-08-26 and 2026-09-27 it went from an empty repo to a playable loop: build a portal, enter your team's Vault, mine ore, earn Resonance, spend points in the Tome. Most of the skill tree is still a registry with no gameplay behind it.

## Where things stand

The repo has 119 commits, 84 main-source Java files, 21 test files and about 223 JUnit tests. 54 issues are closed and about 60 are open, tracked under epic #1.

| Area | State |
|---|---|
| Scaffold, FTB Teams wrapper, SavedData, config | Done (Phase 0) |
| Per-team dimensions, chunk generator, ore classifier | Done (Phase 1) |
| Portal frame, igniters (4 tiers), teleport, return portal | Done (Phase 2) |
| Resonance pool, orbs, drop pipeline, level curve | Done (Phase 3 core) |
| Network channel, Tome item, Tome screen, tree tab | Done |
| Radial hub tree renderer | PR #146, open |
| Vault session tracking + 6 breakpoint node effects | PR #150, open |
| Baseline ore distribution (3% stone, 6% deepslate) | PR #153, open |
| About 45 node effects, Ultimine, Mekanism fallback | Open tickets, unimplemented |
| Vault reset/vote, chunk loader, Animus, Disturbed Zones | Open, Animus deferred to post-1.0 |
| Textures at 64x64 | #69, open; the six shipped are 16x16 |

The three open PRs are meant to merge together, in the order #146 then #150 then #153, and be playtested as one build. The current branch `feat/baseline-ore-distribution` already merges #146 into #153's work, so test it as a unit. None of the three has been playtested in a client yet.

Sources of truth, in order: `OreVault_Design_and_Spec.md` (what the design is, cited as `§N`), `DESIGN_LOG.md` (why it changed and what was rejected), `CLAUDE.md` (the rules for working in the code), `.github/TICKET_WORKFLOW.md` (how to close tickets).

## Timeline

The build ran in four bursts: three days of scaffold and worldgen, a day of playtest firefighting, a week of Resonance and UI, then three weeks of skill-tree redesign driven by playtests. Newest first.

| Date | What landed | Notes |
|---|---|---|
| 2026-09-27 | Session tracking + breakpoint nodes (#150), baseline ore distribution (#153), merged with the Tome renderer | Found the Vault was still nearly all stone; level curve retuned 600 to 220 ore/hour |
| 2026-09-09 | Node descriptions rewritten to state effects with numbers | 12 vague nodes got real constants; `NodeLangTest` bans hedge words |
| 2026-09-07 | Tree rebuilt on clusters, pacts, growth nodes (#135); radial hub renderer (#146) | Three-lane layout built and discarded the same day |
| 2026-09-03 | Orbs and drop pipeline (#116, #117), config screen, igniter capabilities, network, Tome item and screen, grid tree tab (#134) | About 15 PRs merged in one day; grid tree rejected at first playtest |
| 2026-08-29 | Playtest blockers (#108), dimension type split, level-curve rebalance, SavedData versioning, Claude review CI, CLAUDE.md | The lazy-dimension fix and its own regression both landed this day |
| 2026-08-28 | Portal blocks, igniters, teleport (Phase 2), then a wave of 26.1 startup crashes and playtest fixes #76 to #84 | First client run surfaced most of the 26.1 API problems |
| 2026-08-27 | Dimension types, ore classifier, chunk generator, per-team dimension registry (Phase 1) |  |
| 2026-08-26 | Old code removed; scaffold, skill-tree constants, SavedData, FTB Teams hooks, config (Phase 0) | Tasks numbered `[02]` to `[11]`, one PR each |

The repo was a restart: commit `afd91b3` removed an earlier implementation, and the rebuild followed the spec's §13 phase checklist.

## What went well

The habits worth copying are the ones that turned a vague complaint into a precise, recorded fix.

- **A spec as the single source of truth.** Every code comment cites `§N` and every commit names its ticket. When code and spec disagreed, one of them was changed in the same PR. That made it possible to answer "why is this number 220?" weeks later.
- **A design log that records rejected designs.** `DESIGN_LOG.md` keeps the version that read well and failed in play (the grid tree, three lanes, linear Calloused Hands, a hard streak reset). Without it, a fresh reading of the spec arrives at those same rejected versions again.
- **Playtest after every build, then spec first for redesigns.** The owner playtests each merge and gives concrete feedback. Bug-shaped feedback gets fixed directly. Design-shaped feedback gets a docs-only spec PR, reviewed before code is touched. That was much cheaper than rebuilding 35 nodes and a renderer around a misread brief.
- **Pure logic split out so it can be unit tested.** `SkillTree`, `LevelCurve`, `NodeCosts`, `TeamScaling`, `OreClassifier`, `OreDistribution`, `TreeLayout` and `TradeoffToggle` take plain values. `ServerPlayer` overloads are zero-logic adapters. That is how the test count reached about 223 in a codebase where Minecraft classes cannot load in tests.
- **Tests that guard the regressions that actually happened.** `NodeLangTest` fails on a missing lang key or a hedging description. The tree layout test asserts that no edge can cross a box, as a property of the routing rather than today's nodes. Each was proven by breaking the code and watching the test fail.
- **One constant read by everything that depends on it.** The rarity mix 70/25/5 is read by both the generator and the level curve. Node numbers live in `NodeCosts` and the spec, tooltip and implementation all quote it.
- **Commit messages that explain the bug.** The fixes for #82/#89 and the logout-in-Vault regression record the misleading symptom, the real cause and the evidence. Read `git log` for these before touching `VaultDimensions`.
- **SavedData versioning landed early (#104).** `CURRENT_DATA_VERSION` and `migrate()` on both `OreVaultTeamData` and `PlayerStats` meant the node renames in #137 could ship a migration instead of a wipe.

## What went poorly

The biggest single miss was building a whole progression system on top of a world that had no ore in it. Most other problems came from moving fast without a client in the loop.

- **The Vault shipped nearly empty for a month.** The generator from #15 placed two coal veins per chunk, 0.013% of volume against vanilla's roughly 0.5%, and deepslate got nothing. Every open generation ticket was a node that modified a baseline nobody had written. It was only noticed on 2026-09-27 because someone asked. Meanwhile the level curve was priced for an 8% dimension, so the intended 100-hour progression was really about 270 hours. Build the core loop end to end before the modifiers.
- **Phases 0 to 2 were merged without a single client run.** The first `runClient` on 2026-08-28 hit a startup crash (blocks and items need id-aware properties in 26.1), invisible items (missing creative tab, 26.1 item model format, `pack.mcmeta`), broken lighting and nine playtest tickets (#76 to #84). Unit tests and `runGameTestServer` do not catch any of that.
- **A fix that caused a worse regression.** Making dimension creation lazy (#82, #89) was correct, but it removed the only code that registered existing Vaults before players connect. A player who logged out inside a Vault reloaded at Vault coordinates in the Overworld sky and died, with a stuck death screen after. It was caught in a playtest log, not a test.
- **Config options that did nothing.** An audit found one of nine config values was live. Three were cached at startup with no warning and four had no reader at all. Do not add config before a reader exists.
- **The skill tree renderer was built three times.** Grid (rejected at first playtest), three lanes (rejected the same day), then radial hubs. The spec said "like Diablo 4" from the start; the lane version was a literal reading of "staggered left and right of a centre line". Prototype the layout against the reference before writing the router.
- **Node descriptions copied from flavour text.** The lang file was filled from the spec's sales blurb above each node, not the effect table beside it, so players saw "The mirror image." Then 49 nodes had no lang entry at all and tooltips printed the raw key.
- **Too many adjectives in the spec.** Twelve nodes said things like "moderate geode frequency" or "1–3% (balance TBD)". Pinning them exposed a real bug: Volatile Veins at 2% plus Ultimine Safety's 1% + 2% took its risk to exactly zero.
- **Merging before the work was finished.** The owner merges as soon as a PR appears. PR #113 merged between opening and a follow-up push, which stranded the commit and needed PR #114 to recover. Stacked PRs (#116, #117) added more of the same risk.
- **A review check that was green and did nothing.** The Claude review workflow skipped silently until the OAuth secret was added on 2026-08-29. Every earlier "passing" review was a no-op.
- **Wrong-namespace data file.** The pickaxe mineable tag shipped under `data/orevault/` instead of `data/minecraft/`, so the Vault Frame mined at bare-hand speed and dropped nothing (#87). Nothing failed loudly.

## Tricky technical lessons

The worst bugs here had symptoms that pointed somewhere else entirely. Each entry gives the symptom you will see, then the real cause.

### Dimensions

- **A dimension created after the first tick is never ticked.** Symptom: blocks in a new Vault cannot be broken, but placing works, and relogging fixes it. It looked like a protection mod or a team bug. Cause: `MinecraftServer` ticks levels from a cached array that is rebuilt only when `markWorldsDirty()` is called. Adding a level to the world map is not enough. Always call it after registering a runtime level.
- **Create Vaults lazily, restore them eagerly.** FTB Teams makes a one-player team for every login, so creating a Vault per team means one `ServerLevel` per account ever seen. Only the portal path (`VaultDimensions#findOrCreate`) creates one. But any Vault with a folder under `<world>/dimensions/orevault/` must be re-registered in `ServerStartedEvent`, before players connect.
- **Why the restore matters.** `ServerPlayer.SavedPosition` stores dimension and position as separate optionals. If the saved dimension is missing, the player lands in the Overworld at their saved coordinates. Standing at Y=251 in the Vault meant falling from Y=251 in the Overworld.
- **Save the return position only after the trip is known to succeed.** It was saved first, so a bounced teleport overwrote the real return point.
- **World heights live in two places on purpose.** `VaultDimensions.Variant` duplicates the heights in `data/orevault/dimension_type/*.json` so `VaultLayerConfig#load` can fail loudly. Change both or neither.

### Threading and teams

- **Chunk generation runs off the main thread; SavedData does not.** The generator reads team skill state only through `VaultChunkGenerator.SkillSnapshot`, refreshed on the main thread and passed as a `Supplier`. Calling `OreVaultTeamData.get(...)` from generation is a race.
- **An unknown team has size 1, not 0.** `TeamHelper.getTeamId` falls back to the player's UUID, but `teamSize` then found no team and returned 0. That 0 became a divisor in `TeamScaling` and threw on orb pickup in singleplayer.
- **Only ****`TeamHelper`**** imports FTB Teams.** Ultimine is `compileOnly`; Mekanism is tags only, never imported.

### Ore and drops

- **Ore rarity is a property of the modpack.** `OreClassifier` reads placed-feature data at server start (it needs access transformers for that). Which ore counts as rare changes with the pack. Ore depth in the Vault comes from the same data, normalised.
- **New generation only affects new chunks.** To test density without walking for miles, use `/orevault testore [radius] [ore]`.
- **One ****`BlockDropsEvent`**** listener for the whole mod.** `DropPipeline` fixes the stage order. Fortune runs before Quantity; the spec was changed to match. Handlers add to an `Outcome` instead of editing the list, so multipliers compose and the loot table is re-rolled exactly once.
- **Vanilla XP from ore is never suppressed.** Resonance adds to it. This is in §4.2 so no node removes it.

### Minecraft 26.1 and NeoForge

| What broke | Fix |
|---|---|
| Startup crash "Block/Item id not set" | Register through `BLOCKS.registerBlock(name, factory, props -> ...)` (and the item equivalent), which sets the id on the properties; see `ModBlocks` |
| Items invisible in creative and JEI | Creative tab membership, 26.1 `items/` model definitions, a `pack.mcmeta` |
| Tome rendered as the purple-black cube | Each item needs an `items/` definition, a model and a texture |
| Vault lighting wrong | 26.1 dimension types take lighting as attributes, e.g. `minecraft:visual/ambient_light_color`, beside `ambient_light` |
| Pickaxe did not mine the frame | Tool tags go in `data/minecraft/tags/block/mineable/`, not the mod namespace |
| `hasPermission(2)` gone | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| Screens do not compile | `extractRenderState`, `GuiGraphicsExtractor`, `graphics.text`; see the table in `CLAUDE.md` |
| Long tooltips run off screen | Wrap yourself with `font.splitIgnoringLanguage`; nothing wraps for you |
| `ResourceLocation` not found | It is `Identifier.fromNamespaceAndPath` |

When unsure about a 26.1 name, read the decompiled sources jar under `.gradle/repositories/ng_dummy_ng/...` instead of guessing.

### Config, commands and event buses

- **A command enabled by config shows red until relog.** The server checks `requires` on every execution, but the client's command tree is only sent at login. `ConfigReload` re-sends it on `ModConfigEvent.Reloading`.
- **Config read once at startup must say so.** Mark it `worldRestart()` so the config screen warns instead of silently ignoring the edit.
- **Two event buses.** Gameplay events go on `NeoForge.EVENT_BUS`; registries, colour handlers and config go on the mod bus. A listener on the wrong bus never fires and logs nothing.
- **Client classes must stay behind ****`FMLEnvironment.getDist().isClient()`****.** A stray reference crashes a dedicated server at class load, and no unit test catches it. Run `runGameTestServer` to check.

## Design lessons: do not re-propose these

Each row below is a design that read well on paper and was rejected, usually in play. A fresh reading of the spec tends to land on the rejected version again. Full reasoning is in `DESIGN_LOG.md`.

| Rejected | Why | What replaced it |
|---|---|---|
| Skill tree as a grid (branch = column, depth = row) | Vertical position read as a prerequisite chain that did not exist; lines crossed boxes | Clusters headed by anchors |
| Three or five vertical lanes | Read as a list; the spec's reference is Diablo 4 | Radial hubs on one spine, distance = depth inside the cluster |
| Fitting the whole tree on one screen | At 0.09x a node is two pixels | Wheel zoom 0.2x to 1.5x, anchored on the pointer |
| Three separate Ore Boost nodes | Everyone bought all three in the same order | One paid fork parent with free options |
| Anchors gated on team level | A team can grind levels without committing | Gated on points spent, as a fraction of total tree cost |
| Repricing early keystones for the Mastery gate | Removes the early decision they exist for | `[PACT]` class: keystones allowed to be early |
| Refunding growth nodes when they retire | Makes them free picks | Point kept, node shown as Outgrown |
| Flavour text in descriptions | "The mirror image." tells a player nothing | Description states the effect precisely; since Q12 (2026-10-27) the numbers are in the per-tier effect line, filled from the effects constants, not typed into the description |
| A test that every description has a digit | Many precise descriptions have none | `NodeLangTest` bans hedge words like "moderate" |
| Vague-but-honest descriptions for undefined nodes | The node was never specified | Pick the number, put it in `NodeCosts` |
| Hard reset on Vein Discipline's streak | Punishes exploring | Streak decays by 3 |
| Linear Calloused Hands | Reaches 30x over 100 hours | Logarithmic, capped at +40% |
| Damage over time for Bedrock Communion | Irritating, stacks lethally with Molten Seam | Mining speed halved below Y=246 |
| Hollow Earth, Attunement slots | Owner declined: tedious; "I like being overpowered" | Nothing; fork choices carry the power cost |
| Rites (timed active buffs) | Risk of constant reactivation | Deferred post-1.0, not refused |
| 8% or 15% baseline ore | The unspent Vault already feels finished | 3% stone band, 6% deepslate |
| Ore density as config | Admins cannot know the right value and it silently skews the curve | Constant; `curve_divisor` is the knob |
| Suppressing vanilla XP in the Vault | "They shouldn't be punished for using my mining dimension" | Resonance is added on top |

Open numbers worth arguing with: 220 ore per hour is derived, not measured (it assumes 7,200 blocks broken an hour). Every number pinned on 2026-09-09 is unplaytested. The first full playtest should check how fast a team actually levels.

## Process and tooling pitfalls

Most of these cost an hour or a PR once. They are easy to avoid if you know them.

### Build

- **Never run ****`./gradlew clean`**** in a git worktree.** It wipes `build/neoForm/` and the `:neoFormPatch` step then fails. Plain `./gradlew build` recovers.
- **A cached test run reports nothing.** `BUILD SUCCESSFUL` looks the same for a cached no-op. Use `./gradlew test --rerun-tasks` and read the counts from `build/test-results/test/*.xml` before quoting them in a PR.
- **The JDK path in ****`gradle.properties`**** is local to one machine** (`E:/Github/Ore-Vault_2/.tools/jdk25/...`). Any other checkout or CI must supply its own JDK 25.
- **Unit tests cannot load Minecraft classes.** Anything touching `Level`, registries or NBT needs a gametest or a playtest. Do not wrap logic around `ServerPlayer` where a plain boolean or int would do; that is what keeps it testable.
- **Run three things before calling work done:** `./gradlew build`, `./gradlew runGameTestServer` (catches client-class leaks on a dedicated server) and a real `runClient` for anything with assets, models, GUIs or worldgen.
- **Do not bump ****`neo_version`**** or the FTB versions inside a feature PR.**

### Language server

- `jdtls-local` gives real Java diagnostics but only works when Claude Code is launched from the repo root. In a worktree it gives syntax errors only. It is untracked, so a fresh clone does not have it.
- Keep `jdtls-lsp@claude-plugins-official` disabled; it expects `jdtls` on `PATH` and will claim `.java` first.

### PRs and tickets

- **Say "ready to merge" in those words.** The owner merges as soon as a PR appears. While commits are still coming, say "don't merge yet". Branches are deleted on merge, so a follow-up goes on a new branch from `main`.
- **Avoid stacked PRs** unless needed. When they are, say so in the body and give the merge order.
- **A green Claude review check does not prove a review ran.** Check with `gh run view <id> --json jobs` that steps 3 and 4 executed.
- **Follow ****`.github/TICKET_WORKFLOW.md`****.** Tick the acceptance criteria you met, comment on the ones you did not and name the blocker, put `Closes #N` in the body, close superseded tickets as `NOT_PLANNED` with the replacement named.
- **Commit format:** `feat:`, `fix:`, `docs:`, `ci:` with the issue number, e.g. `fix: restore existing Vault dimensions at server start (#89)`.
- **Keep the spec checklist (§13) in sync.** It drifted once and needed PR #118 to catch up.

### Working with the owner

- They playtest every build and send specific, quoted feedback. Bug-shaped feedback: just fix it. Design-shaped feedback: spec PR first, then code.
- They delegate naming and expect a recommendation, not a menu. Ask only about decisions that change the shape of the work.
- Flag balance consequences instead of silently retuning numbers.
- Write issues and PR bodies plainly. No inflated language, no emoji, no bolded-header bullet lists. They are read later as documentation.
- Add a dated `DESIGN_LOG.md` entry whenever a playtest changes a decision, including what was rejected.

### Plugins

- `serena` and `feature-dev` are disabled on purpose; `jdtls-local` and the superpowers skills cover the same ground.
- `remember` stays on. It records what happened in a session, dead ends included, which the issue tracker does not. It needs auto-compact off.
- Use `superpowers:test-driven-development`, not the other `tdd` skill; merged PRs follow its format.
- The skill list is at its context budget; adding plugins will start truncating it.

## If you start over

Build the playable loop end to end in a client first, then layer the skill tree on top. The previous order did the reverse and paid for it with an empty Vault and a mispriced curve.

1. Read `CLAUDE.md`, then `DESIGN_LOG.md`, then the spec sections you are about to touch. Do it in that order.
1. Set up the toolchain: JDK 25, the decompiled 26.1 sources jar, `jdtls-local` from the repo root. Confirm `runClient`, `runServer` and `runGameTestServer` all start on an empty mod.
1. Register one block and one item the 26.1 way, with creative tab, item model definition and texture. See them in a client before adding more.
1. Build the dimension path completely: lazy creation on the portal, `markWorldsDirty()`, eager restore at server start, return position saved after success. Test logging out inside a Vault and logging back in.
1. Build the ore baseline in the generator next, with the rarity mix as one shared constant. Mine in it for ten minutes before writing anything that modifies it.
1. Add Resonance, orbs and the drop pipeline. Derive the level curve from the actual density, and check how fast a level comes in play.
1. Add SavedData versioning before the first field that players will keep.
1. Prototype the Tome tree layout against the Diablo 4 reference with real node counts before building the router. Hubs, not lanes.
1. Write each node's description from its effect table with every tier's number. Any node you cannot describe precisely is not designed yet; decide it first.
1. Implement node effects in the order the tickets group them, each with pure-logic tests and a playtest.

### Before every PR

- `./gradlew build` and `./gradlew test --rerun-tasks` pass; counts quoted from the XML
- `runGameTestServer` boots clean
- Anything visual or worldgen tried in `runClient`
- Spec updated if behaviour changed; design log entry if a playtest changed a decision
- SavedData shape unchanged, or a migration added
- No new FTB import outside `TeamHelper`, no `mekanism` import, no client class on a common path
- `Closes #N`, acceptance criteria ticked, "ready to merge" stated

### First thing to do now

Merge #146, #150 and #153 in that order, then playtest them together. The questions that playtest has to answer: does 3% ore feel right, does a team level at about the rate the 100-hour curve expects, and does the radial tree read clearly at its 944 by 6008 pixel size.
