# Sketch: node data model (#208, E8a-S1)

Status: Architect's sketch for the Developer, approved by all five reviewers with comments; the Manager's rulings
(2026-11-25) are applied below and marked "Ruling". Spec: §6 Notation (classes, marks, Fork, Saved state),
§6.1 Forks, Reserved node ids and the node list, §4.4 refund prices and windows, §11 Tree Layout Data. Records:
ADR 0001 (`core.layout` and `core.tree` rows), ADR 0005 ("Registry ids are save data"), ADR 0006 (golden id
lists), `docs/tree-layout-format.md`. No sketch convention existed; this folder starts one (`docs/sketches/<ticket>-<name>.md`).

## The rule this sketch holds

The layout file is the one source of nodes: ids, classes, marks, rings, edges, petal positions. Java holds what
the layout cannot: the skill and option ids that effect code, lang keys and saves are keyed by, the reserved
ids, the tree constants, and one check that the two agree. Java never names a node id, and never repeats a
node's class, ring or count.

## Packages

`core.layout` may import Gson only, and `core.tree` may import `core.layout` (ADR 0001, already ArchUnit rows).
So anything the layout parser must recognise goes in `core.layout`, and anything keyed to effects or saves goes
in `core.tree`. The ticket's title says `core.tree`; ADR 0001 puts `NodeClass` and the marks in `core.layout`,
and that wins, because the #212 parser has to refuse an unknown class or mark without importing `core.tree`.

| Class | Package | What it is |
|---|---|---|
| `NodeClass` | `core.layout` | Enum: `START, SMALL, TRAVEL, NOTABLE, KEYSTONE, PACT, GROWTH, FORK_PARENT`. Each holds its format token (`start`, `small`, ..., `fork_parent`); `static Optional<NodeClass> fromToken(String)`, exact and case-sensitive. `START` is the format's token, not a §6 class; its Javadoc says so |
| `Mark` | `core.layout` | Enum: `TRADEOFF, EXCLUSIVE, REQUIRES`, with tokens and `fromToken` as above |
| `SkillId` | `core.tree` | Enum, one constant per §6.1 skill, notable, keystone and travel stat (decision below). Field `String id`; `id()`; `static Optional<SkillId> fromId(String)` |
| `ForkOption` | `core.tree` | Enum, one constant per petal. Fields `String id`, `SkillId parent`, `int tiers` (rows in the option's §6.1 table); `static Optional<ForkOption> fromId(String)`, `static List<ForkOption> of(SkillId parent)` |
| `ReservedIds` | `core.tree` | Final helper. `static final Set<String> ALL`, the seven §6.1 ids, immutable; `static boolean isReserved(String)` |
| `ForkCoverage` | `core.tree` | Final helper. `static List<String> gaps(ForkOption option, int parentNodesForOption)` : one message if `option.tiers() != parentNodesForOption` (equality, not "at least") |
| `TreeIds` | `core.tree` | Final helper, the agreement check. `static List<String> problems(Collection<String> nodeIds, Collection<String> skillIds, Map<String, ? extends Collection<String>> optionsByParent)` |
| `TreeConstants` | `core.tree` | Exists. Gains the refund prices and windows (below) |

No interface, no registry object, no `Node` class in `core.tree`: `TreeLayout`'s node record (#212) is the node.

## Skill ids: an enum, not string constants

An enum because every consumer wants a closed set: the effects constants (#235, #211, #236) as an
`EnumMap<SkillId, ...>` whose completeness a test can check by `values()`; effect code with exhaustive
`switch`; the Outgrown rule per growth node (ADR 0006). String constants give none of that and let a typo
compile. The cost, a Java change to add a skill, is paid anyway: a new skill needs effect code.

Ruling 4: the Javadoc on `SkillId` and `ForkOption` bans persisting or sending `ordinal()` or `name()`; saves and
the wire use `id()`. A test checks every `id()` is lower snake case and is not a reserved id or `start`.

The saved and wired form is always `id()`, never `name()`, so renaming a Java constant changes no save. The
layout keeps `skill` as a plain string (`core.layout` cannot see `SkillId`); `core.tree` resolves it with
`fromId`. Ids: §6.1's heading, lower case, apostrophes dropped, every other run of non-`[a-z0-9]` one `_`
(`miners_constitution`, `vein_fortune`, `blast_wise`, `second_wind_homeward`, `the_vault_keeps_it`), at most 64
characters (`MAX_ID`, ADR 0006). A fork parent is an ordinary `SkillId` (`vein_shaping`, `ore_attunement`,
`ore_working`, `resonant_draw`); it is a fork parent because `ForkOption` names it, not because of a flag.

Travel stats, one `SkillId` each: `mining_speed`, `resonance_gain`, `vanilla_xp_from_ore`, `move_speed`, `block_reach`,
`max_health`, `tool_care`, `tool_charge`, `safe_fall`, `armour`, `masons_reach`, `kit_care`.

Ruling 1: the travel stat `ore_xp` became `vanilla_xp_from_ore`, so the derivation rule above has no exceptions.
`armour` stays. With the 95 ids from §6.1's headings (fork options are not skills), `SkillId` has 107 constants;
`SkillIdTranscriptionTest` pins the count and the full literal list.

Fork options: `abundance`, `vein_singularity`, `stratified` (vein_shaping, 2 tiers each); `common_focus`,
`uncommon_focus`, `rare_focus` (ore_attunement, 3); `ore_doubling` 6, `smelters_intuition` 3 (ore_working);
`resonance_magnetism`, `hoarders_instinct` (resonant_draw, 3). These replace the format doc's placeholders and
are frozen from this PR, so the Writer reviews them here rather than at #239.

## Forks and the Ore Working exception

Petals, `petalAnchor` and petal positions are layout (`forks[]` in the format) and stay there. Java holds the
option-to-parent map and each option's tier count. Coverage: the parent nodes an option must cover are the
parent's nodes with no `forOption` or with `forOption` equal to that option.

Ruling 2: coverage counts a parent's nodes by their layout `forOption` (`{fork, option}`), never by what is visible
at runtime, so empty Mekanism tags cannot break the tree. Hence `parentNodesForOption`, and its Javadoc says so. Ore Working's nodes 4 to 6 carry
`forOption: ore_doubling`, so Ore Doubling must cover 6 and Smelter's Intuition 3. The sanctioned exception is
therefore not a special case in code; it falls out of `forOption`. #212 adds the layout test that only
`ore_working` nodes carry `forOption`, so no second exception appears unreviewed.

Effects per option (#236) are keyed by `ForkOption`; #236 adds a test that each option's table has exactly
`tiers()` rows, so `tiers` is not a second source that can drift.

## Reserved ids

`ReservedIds.ALL` is the seven ids of §6.1. "Never registered" means: no `SkillId` or `ForkOption` id is
reserved (unit test), and `TreeIds.problems` reports any node, skill or option id in the layout that is. The
save side, never writing one, is #251's. An id retired later joins the set in its own PR (format doc,
"Frozen ids and free fields").

## The agreement check, before #212 exists

`TreeIds.problems` takes plain collections, so #208 needs no layout reader and no Gson. It reports, naming
every offender, not only the first: a layout skill id with no `SkillId` ("has no constants"); a `SkillId` with
no node ("constant has no node"); a reserved id among nodes, skills or options; a fork parent that is not the
`parent` of some `ForkOption`; an option id unknown, under the wrong parent, or a `ForkOption` missing from
the layout; an option id equal to a skill or node id. At #208 it is unit-tested on small literal sets.
#212 owns the run over the shipped file: a `TreeLayout` overload (or the start adapter passing its sets), a
test over `resonance.json`, and a start-time call so a mismatched jar fails at start with the message.
Manager: please add that line to #212's acceptance criteria.

Ruling 3: `TreeIds.problems(...)` stays test-only at #208. Running it over the shipped `resonance.json`, and any
start-time call, are owed to #239: the provisional file at #212 lacks most skills and would fail every dev run.

Ruling 6: the `petalAnchor` checks and old #135's "exclusivity mutual" guard are layout checks and are owned by #212.

## Constants added to `TreeConstants`

`REFUND_XP_PER_NODE = 3`, `REFUND_XP_PACT = 12`, `REFUND_XP_KEYSTONE = 30` (§4.4, names as the spec block);
`UNDO_WINDOW_TICKS = 60 * 20`, `FREE_RESPEC_WINDOW_TICKS = 10 * 60 * 20`, in game ticks because
`free_respec_until` is saved as game time (ADR 0005). Choosing a price from a `NodeClass` is #230's, not here.
The 24-hour respec cooldown is not in this ticket's criteria; it lands with the reset work that reads it.
(Ruling 8 confirms the five names and values: 3, 12, 30, 1200 and 12000.)

## Tests (JUnit, `src/test/java/.../core/tree` and `core/layout`)

1. `NodeClassTest`, `MarkTest`: tokens equal the format doc's lists exactly; `fromToken` refuses unknown and
   wrong-case tokens.
2. `SkillIdTest`: ids unique, `[a-z0-9_]{1,64}`, none is `start`, none reserved; `fromId(id())` round-trips;
   `fromId` of a reserved or unknown id is empty.
3. `SkillIdTranscriptionTest`: the set of ids equals literal lists typed from §6.1, one per section (skills,
   travel stats with exactly 12, flavour notables, keystones). Writer signs the names, Balancer the travel 12.
4. `ForkOptionTest`: ids unique, form as above, none reserved, none equal to a `SkillId` id; parents are exactly
   the four §6.1 fork parents, each with at least two options; `tiers` per option as listed above.
5. `ForkCoverageTest`: §6.1's counts pass (Vein Shaping 2, Ore Attunement 3, Resonant Draw 3, Ore Working 6
   under Ore Doubling and 3 under Smelter's Intuition); Hoarder's Instinct at 2 tiers fails (the historical
   gap); an option with more tiers than nodes fails; Smelter's Intuition seeing 6 nodes fails.
6. `ReservedIdsTest`: exactly the seven; the set is unmodifiable.
7. `TreeIdsTest`: clean sets give no problems; each problem kind above is reported with its id; two offenders
   of one kind are both reported.
8. `TreeConstantsTest`: every value pinned (Balancer signs).
9. ArchUnit: the existing `core.layout` and `core.tree` rows cover the new classes; no new rule. The PR shows
   one test failing against deliberately broken code (for example a reserved id added to `SkillId`).

## Deliberate mutations (Ruling 5)

One per new class; each must turn its test red. At least one is demonstrated in the PR with its output.

| Class | Mutation | Test that fails |
|---|---|---|
| `SkillId` | add a constant with id `ore_sense` (reserved) | `SkillIdTest`, `SkillIdTranscriptionTest` |
| `ForkOption` | `HOARDERS_INSTINCT` tiers 3 to 2 | `ForkOptionTest`, `ForkCoverageTest` |
| `NodeClass` | `fromToken` ignores case | `NodeClassTest` |
| `Mark` | token `exclusive` to `exclusive_group` | `MarkTest` |
| `TreeConstants` | `REFUND_XP_PACT` 12 to 13 | `TreeConstantsTest` |
| `ReservedIds` | drop `motherlode` from `ALL` | `ReservedIdsTest`, `SkillIdTest` |
| `ForkCoverage` | `!=` to `>` (at least) | `ForkCoverageTest` |
| `TreeIds` | return after the first offender of a kind | `TreeIdsTest` |
