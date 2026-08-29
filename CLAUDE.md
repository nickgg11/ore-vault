# Ore Vault

A **NeoForge 26.1 mod for Minecraft 26.1, targeting Java 25.** Per-team mining
dimensions with boosted ore density, a shared Resonance/Animus skill tree, and
opt-in mob farming via Disturbed Zones.

This is **not** a Bukkit/Spigot/Paper plugin and **not** Fabric or legacy Forge.
There is no `plugin.yml`, no `JavaPlugin`, no `@EventHandler`, no scheduler API.
Suggestions built on those APIs are wrong here.

`OreVault_Design_and_Spec.md` is the source of truth. Code comments cite it as
`§3.1`, and cite work items as `[13]` (task) or `#82` (issue). Keep that up: when
behaviour and spec disagree, the spec wins or the spec gets updated in the same PR.

## Build

```
./gradlew build          # compile + unit tests
./gradlew test           # unit tests only
./gradlew runClient      # dev client
./gradlew runServer      # dev server
./gradlew runGameTestServer
```

`gradle.properties` pins `org.gradle.java.installations.paths` to a **local
absolute path** (`E:/Github/Ore-Vault_2/.tools/jdk25/...`). That path exists only
on this machine — any CI or another checkout must supply its own JDK 25 toolchain
rather than inherit this line.

Versions are pinned in `gradle.properties` (`neo_version`, `ftb_teams_version`,
`ftb_ultimine_version`). Don't bump them incidentally inside a feature PR.

## The five things most likely to be got wrong

**1. Threading: chunk generation is off-thread, SavedData is not.**
`VaultChunkGenerator` runs on a background executor. `SavedData` (`OreVaultTeamData`)
is main-thread-only. `VaultDimensions` maintains a thread-safe
`VaultChunkGenerator.SkillSnapshot` map, refreshed on the main thread and handed to
each generator as a `Supplier`, precisely so the generator never touches SavedData.
**Any new code that reads team/skill state from inside generation must go through
the snapshot.** Reaching for `OreVaultTeamData.get(...)` there is a race, not a
style question.

**2. Dimension creation is lazy; restoration is eager.**
FTB Teams auto-creates a single-member team for *every* player who logs in. Creating
a dimension per team would mean one `ServerLevel` per account the server has ever
seen. Only `VaultDimensions#findOrCreate`, on the portal path, creates a new Vault.
Vaults already on disk are re-registered at server start, before any player connects,
because a player may have logged out inside one. **Creating dimensions on a
team-created event is a serious regression** — it has been fixed once already
(see `#82`, `#89`).

**3. Two event buses, and they are not interchangeable.**
`NeoForge.EVENT_BUS` takes gameplay events (`FtbEvents`, `PortalEvents`,
`VaultDimensions`, `VaultDiag`). The `modEventBus` passed to the `@Mod` constructor
takes registries and lifecycle (`DeferredRegister`, colour handlers, config). Putting
a listener on the wrong bus fails silently — it just never fires.

**4. Side safety.**
Anything under `client/` is client-only and must stay behind
`FMLEnvironment.getDist().isClient()`, as `VaultPortalColors` is in `OreVault`'s
constructor. A common-path reference to a client class crashes a dedicated server at
class-load, which no unit test here will catch. Server logic uses `ServerLevel` and
`ServerPlayer`, never bare `Level`/`Player`, when it mutates state.

**5. Dependency boundaries.**
- **FTB Teams** — hard dependency, compiled and required. **Only `TeamHelper` may
  import `dev.ftb.mods.ftbteams.*`.** The rest of the mod goes through it.
- **FTB Ultimine** — `compileOnly`. Compile against its API; never assume presence.
- **Mekanism** — *no compile-time reference at all.* Integration is tag-based per
  §9. An `import mekanism.*` anywhere is a bug.
- Any soft-dependency code path must be gated on `ModList.get().isLoaded(...)` at
  runtime. There is currently no such call in the tree; new integration code
  introduces the first ones.

## MC 26.1 API names

The 26.1 renames trip up anything trained on older Minecraft. This codebase is
already correct — match it, don't "fix" it:

| Use | Not |
| --- | --- |
| `net.minecraft.resources.Identifier` | `ResourceLocation` |
| `Identifier.fromNamespaceAndPath(...)` | `new ResourceLocation(...)` |
| `SavedDataType<T>` + codec | `SavedData` load/save overrides |
| `CompoundTag#getCompoundOrEmpty` | `getCompound` returning null |
| `RegisterColorHandlersEvent.BlockTintSources` | `ColorHandlerEvent.Block` |

Nullability uses **JSpecify** (`org.jspecify.annotations.Nullable`), not the
NeoForge or JetBrains annotations.

## Persistence

`OreVaultTeamData` is per-team `SavedData` stored in the **overworld's**
`SavedDataStorage`, keyed `orevault_team_<uuid>`, serialized via
`CompoundTag.CODEC.xmap(...)` with hand-written `toNbt`/`fromNbt`. `PlayerStats`
nests inside it under `player_stats`.

**These are player save files.** Removing or retyping a field silently destroys
progression in existing worlds. A field addition needs a default that a tag without
it reads correctly; a rename or removal needs a migration path. Flag any PR that
changes these shapes without one.

## Teams

Every player always has a team — solo players are a team of one (§2). `TeamHelper`
falls back to the player's own UUID when the manager has no record yet, so team
lookups do not return null and code must not branch on "has no team". Dimension keys
are `orevault:vault_<uuid-without-hyphens>`.

## Access transformers

`src/main/resources/META-INF/accesstransformer.cfg` widens a small number of vanilla
members. Every entry carries a comment naming the task and why no public API exists.
Keep that. A new AT entry without justification, or one that could be avoided through
public API, is worth flagging — each one is a fresh breakage risk on MC updates.

## Datapack JSON ↔ Java constants

`VaultDimensions.Variant` deliberately duplicates the world-height values from
`data/orevault/dimension_type/*.json` rather than reading them back, so
`VaultLayerConfig#load` can validate the layer stack and fail loudly at creation
instead of generating a subtly wrong world. **Changing a height in one place and not
the other is a bug**, and the duplication is intentional — don't refactor it away.

## Tests

JUnit 5, under `src/test/java`, run by `./gradlew test`. These are **pure-logic tests
only** — `OreClassifier`, `LevelCurve`, `NodeCosts`/`SkillTree`, `TeamScaling`,
`VaultPortalShape`. Minecraft classes are not loadable in this source set, so
anything touching `Level`, registries, or NBT cannot be unit tested here; it belongs
in a gametest (`neoforge.enabledGameTestNamespaces`) or manual playtest.

Do not ask for unit tests on code that cannot have them. Do expect them on new pure
logic — especially skill-tree maths and classification rules, where the balance
regressions have historically landed.

## Java language server

`.claude/skills/jdtls-local/` is a project-scoped plugin wiring Eclipse JDT.LS to the
vendored `.tools/jdtls` and `.tools/jdk25`, giving real compiler diagnostics on Java
edits. It registers automatically — no skill invocation needed — and the `LSP` tool
(`goToDefinition`, `findReferences`, `hover`, `documentSymbol`) works once it's up.

Two constraints:

- **Launch Claude Code from the repo root.** Project-scoped plugins load only from the
  session's primary working directory. In a git worktree the server still runs but
  the files aren't on its classpath, so you get syntax errors only — no type checking.
- Keep `jdtls-lsp@claude-plugins-official` **disabled**. It hardcodes `command: "jdtls"`
  on `PATH`, which doesn't exist here. If both register, only the first to claim
  `.java` starts.

`.tools/` and `.gradle-home/` are gitignored, so a fresh clone has no language server
until they're restored.

## Conventions

- Commits: `feat:`, `fix:`, `docs:`, `ci:`, with the issue number in the subject —
  `fix: restore existing Vault dimensions at server start (#89)`.
- Helper classes are `final` with a private constructor and static methods
  (`TeamHelper`, `VaultPortalColors`, `ModTags`).
- Registry holders live in `ModBlocks` / `ModItems` / `ModTags`; items need creative
  tab membership or they're invisible in creative and JEI.
- Javadoc on non-trivial classes explains *why*, with a `§` reference. Match that
  density rather than narrating what the code already says.
- `debug/VaultDiag.java` is playtest instrumentation, held to a lighter bar than
  gameplay code.

## Out of scope for review

`build/`, `run/`, `logs/`, `.gradle*/`, `.tools/`, `.inspect/`, `.research/`, and
`src/generated/` are build output or local scratch and are gitignored.
