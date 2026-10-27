# 0003. The generation snapshot and the team effects view

Status: proposed, 2026-10-27 (E0-S5, #186). Spec: §3.1 seeding, §6 Mod-gated nodes, §6.1 Volatile Veins and
Molten Seam, §11 Dynamic Chunk Generator. `CLAUDE.md` technical rule 1. Settled decisions S13 and S15
(`docs/TEAM_REQUIREMENTS.md`). Absorbs old #16.

## Context

Chunk generation runs on a background executor. Team `SavedData` is main-thread-only (`CLAUDE.md` rule 1).
The last build's `VaultChunkGenerator.SkillSnapshot.of(OreVaultTeamData)` was built correctly on the main
thread, but it lived in the generator class and took the team data type as its argument, so `worldgen`
depended on `data` and one convenience call would have read `SavedData` off-thread (S15; `git show
5baeb7c:src/main/java/com/orevault/orevault/worldgen/VaultChunkGenerator.java`, the record at the top of
the class). That snapshot also held every owned node and the points invested, which is more than placement
reads and so changed on purchases that change nothing in the ground.

Since then the design added state that generation reads and that is not a node: the reset count in the
seed (Nick, 2026-10-27, Q2), the team-voted applied state of Volatile Veins and Molten Seam (Q1), the 40%
floor clip (S13), and a per-chunk stamp the Survey panel compares (Q13). Several nodes also need the
team's tree on the main thread many times a tick (the drop listener, the wear mixin of ADR 0008,
attribute recompute, Silent Stone), and each would otherwise derive tiers, dormancy and tradeoffs from
`SavedData` on its own.

Read on 2026-10-27 from the decompiled NeoForge 26.1.2.95 sources (paths inside the sources tree):

- `net/minecraft/world/level/chunk/ChunkGenerator.java` line 111: `protected abstract MapCodec<? extends
  ChunkGenerator> codec();` and line 640: `public abstract CompletableFuture<ChunkAccess>
  fillFromNoise(Blender, RandomState, StructureManager, ChunkAccess)`.
- The last build placed its ore inside `fillFromNoise` against 26.1 and returned `MapCodec.unit(this)`
  from `codec()` (commit 5baeb7c, same file).
- `net/minecraft/world/level/storage/DerivedLevelData.java` line 23: `getGameTime()` returns the wrapped
  (overworld) level data's game time, so every level, a Vault included, reads one game clock.

## Decision

### Two immutable views, both built on the main thread

There are two views of a team's tree, with different readers and different contents. Neither is
`SavedData`, neither is mutable, and both are rebuilt whole and swapped in by reference.

**`GenSnapshot`** (package `core.gen`, no Minecraft imports) is what the chunk generator reads. It holds
only what placement reads:

| Field | Why placement reads it |
|---|---|
| Tiers of the owned generation nodes, by skill id (Vein Expansion, Vein Proliferation, Gravel Purge, Guide Vein, Deep Veins, Stone Reduction, Vein Shaping, Ore Attunement, Full Spectrum, Geode Clusters, Ancient Traces, Volatile Veins, Molten Seam) | Vein size and count, extra features, pools |
| Fork choices of the two generation forks, Vein Shape and Ore Focus | Which option the parent's tier acts through |
| Applied state of Volatile Veins and Molten Seam | Their generation half acts only while the team's applied state is on (below) |
| Team level | Required by the ticket and listed by the Architect's T4 line in `docs/TEAM_REQUIREMENTS.md`. No 1.0 generation node reads it today; it is carried so a level-gated generation effect does not need a new field, and it is left out of the stamp (below) |
| Dimension variant (`orevault:ore_vault` or `orevault:ore_vault_expanded`) | Which bands exist; Ancient Traces and Deep Veins depend on it. The generator is built per variant as well, and asserts the two agree |
| `vault_generation`, the reset count | Part of every seed (Q2) |
| The 40% floor clip factor | S13: the floor scales every rarity's vein count by one factor, computed when the snapshot is built from the requested counts by the baseline model of ADR 0009 (E0-S7). If ADR 0009 computes one factor per band, the snapshot holds one per band |
| `stamp`, a long (64-bit) | The chunk records it as `gen_stamp` (ADR 0004); the Survey panel compares it with the current snapshot's (Q13) |

A tier counts only nodes that are owned and not dormant. A node owned but hidden because its mod or tag is
missing is dormant (§6 Mod-gated nodes) and is left out. No generation node is mod-gated today; the rule
is stated so that one added later cannot generate while hidden.

`stamp` is a hash of the generation node tiers, the two generation fork choices and the applied
generation tradeoffs, in a fixed order (skill ids sorted), with a fixed hash function implemented in
`core.gen`, never `Object.hashCode` of a collection. It leaves out team level, the clip factor (which
follows from the tiers), the variant and `vault_generation` (a reset regenerates every chunk anyway), so
a level-up never marks the player's chunk "not current". A golden JUnit test pins the stamp of a fixed
snapshot, so a refactor cannot silently mark every chunk out of date.

**`TeamEffects`** (built in `data`, read on the main thread only) is what everything else reads: the drop
listener and its pipeline, the wear mixin (ADR 0008's "main-thread team snapshot" is this view), attribute
recompute, Silent Stone's ticker and the Volatile Veins roll. It holds the owned tier of every skill (dormant
nodes excluded), every fork choice, the hidden set, the applied generation tradeoffs, team level, team size,
each online member's active per-player tradeoffs, and the expected ore in a newly generated chunk under the
current generation nodes, the closed form from `OreBaseline` with the clip applied, which the Seismic Sense
shade reads (ADR 0004 Amendment 1). Those readers read nothing else for tree state: not
`SavedData`, not the layout, not the hidden set separately. This puts the dormancy, fork and tradeoff rules
in one place instead of in each consumer, and turns a per-break derivation into a field read.

### Holders and the supplier

`GenSnapshots` (package `data`) keeps one holder per team, an `AtomicReference<GenSnapshot>`. ADR 0002
creates and fills a team's holder at Vault load and at restore, before the level ticks. The generator is
constructed with `Supplier<GenSnapshot>` (the holder's `get`) and the team id, and nothing else from the
team. `TeamEffects` is kept per team in a plain main-thread map in the same class, since only the main
thread reads it.

The generator calls the supplier exactly once per chunk, at the start of `fillFromNoise`, and passes that
one snapshot to every placement step for the chunk: layers, lava pools, ore, gravel and clay, geodes,
debris, the vein index entries and the stamp. All Ore Vault placement for a chunk happens in that one
method, as the last build's did, so no later chunk status can read a second, newer snapshot and give one
chunk two tree states.

### Rebuild triggers

Both views are rebuilt on the main thread, from the team's `SavedData` and the layout, on:

- a node purchase, a path purchase (once, after the whole path), and a refund;
- a fork option chosen, swapped or cleared;
- a level-up;
- a dimension reset (after `vault_generation` + 1 and the variant change, before the new level ticks);
- restore at server start and Vault load (ADR 0002);
- server start and `/reload`, after the hidden set is computed (E8a-S7), since dormancy can change;
- a member's team change (`FTBTeamsEvent.PlayerChangedTeam`, which FTB Teams posts for every change,
  online or offline: `dev/ftb/mods/ftbteams/data/AbstractTeam.java` `onPlayerChangeTeam` in the
  ftb-teams 26.1.2.4 sources jar, read 2026-10-27), for both
  the old and the new team, since the vote tally and member set change;
- Volatile Veins' or Molten Seam's applied state changing (below).

`TeamEffects` is also rebuilt when a member toggles a per-player tradeoff, logs in or logs out, since it
holds online members' tradeoffs. `GenSnapshot` is not, because no per-player state reaches generation.

### Generation tradeoffs: one team state

Per-player tradeoffs do not reach the snapshot at all. The two tradeoffs that change generation are
team-wide (Nick, 2026-10-27, Q1; §6.1 Volatile Veins):

- Each member's switch on Volatile Veins or Molten Seam is their vote, saved in their `PlayerStats`
  `active_tradeoffs` (ADR 0005). It can change inside or outside the Vault.
- The tally counts the team's current members only, online or not, read through the team helper. A former
  member's `PlayerStats` stays in the old team's data (Q3); its stale vote is never counted.
- The vote passes with more than half of the members, or one of two, or a solo player's own switch.
- The applied state is saved on the team as `generation_tradeoffs` (ADR 0005). The pending change is not
  saved: it is the difference between the tally and the applied state, both of which are saved, so it is
  derived whenever it is needed.
- It is applied when the Vault level has no player in it: when the last player leaves (any route ADR 0002
  watches, logout included), when a vote or a team change moves the tally while the Vault is empty, and at
  server start for every team before anyone connects. Applying sets `generation_tradeoffs` and rebuilds
  both views.
- The effective state is applied and owned and not dormant. Refunding the node removes it from
  `generation_tradeoffs` at once (its effect stops with the node anyway), so buying it again starts off and
  waits for the next empty Vault rather than switching on under players already inside.

The test for "empty" is the Vault level's player list, which matches Nick's words ("once no one is in the
ore vault") and also covers a player who left the team while inside.

### Seeds and per-chunk rolls

Every random choice in a chunk comes from a seed of the team id (both halves of the UUID), `vault_generation`,
the chunk's x and z, and a per-feature salt, mixed by one fixed function in `core.gen` and pinned by a
golden test. Never the world seed (§3.1), never `UUID.hashCode()` alone.

Each feature takes its own seeded random from its own salt: baseline ore, each generation node's extra
placement, gravel and clay, lava pools, geodes, debris. Adding a feature later, or a node that adds veins,
then does not shift another feature's stream.

Every "one per N newly explored chunks" rate is a per-chunk roll from that chunk's seed, never a counter
carried between chunks: Geode Clusters rolls 1 in 12 (then 1 in 6), Ancient Traces 1 in 4 (then 1 in 2),
Molten Seam 1 in 2. A counter would depend on the order chunks happen to generate, which differs per
player and per restart, and it would be state the generator must not keep. The rate is met on average.

### Generator plumbing (old #16)

- `codec()` returns `MapCodec.unit(this)`. The generator is built per team in code (ADR 0002), never parsed
  from a datapack, and an instance-bound unit codec is what the last build shipped on 26.1.
- The Vault biome, `data/orevault/worldgen/biome/vault.json`, has empty spawner lists and empty
  `spawn_costs`, which is what disables mob spawning in 26.1 (old #13; E0-S1 corrects §3.1).

### Enforcement

S15's static rule is the enforcement: an ArchUnit rule, owned by the Reviewer and shown failing once
against a planted violation (S43), that no class in `worldgen` or `core.gen` depends on `data`, `team`,
`net.minecraft.world.level.saveddata`, or any `dev.ftb` type. `GenSnapshot` lives in `core.gen`; the code
that builds it from team data lives in `data`. ADR 0001 (E0-S3) lists the rule with the others.

## Consequences

- A purchase is visible in the next chunk generated after the rebuild. A chunk whose generation started
  before the purchase uses the snapshot it read; that is correct, and the chunk's stamp says so.
- A level-up, a per-player toggle or a login rebuilds `GenSnapshot` cheaply or not at all, and never
  changes the stamp.
- The vote needs the team helper's member list on the main thread; the team helper is still the only
  `dev.ftb.mods.ftbteams` importer.
- A team whose applied state is pending at shutdown gets it applied at the next start, before anyone joins.
- The seed and stamp functions are save data in effect: changing either changes the Vault every existing
  team will generate next, or marks every chunk out of date. Their golden tests make such a change a
  visible, reviewed decision.
- Molten Seam's "one per 2 chunks" and the geode and debris rates become probabilities per chunk. The
  Balancer reviews that reading; in a run of chunks the count varies, which a counter would not.

## Alternatives rejected

- **Reading `SavedData` from the generator, even read-only.** A race (`CLAUDE.md` rule 1).
- **One view for both readers.** The generator needs a small, stable set and a stamp that ignores
  everything else; the main-thread readers need per-player tradeoffs, the hidden set and team size, which
  change often. One view would either rebuild generation state on every toggle and login or give the
  drop listener less than it needs.
- **The snapshot as the full owned set** (the last build's `SkillSnapshot`). It changes on purchases that
  change nothing in the ground, so it cannot back the Q13 stamp.
- **Per-player generation tradeoffs feeding a team snapshot**, for example "on if any member has it on".
  Rejected by Nick (Q1, `DESIGN_LOG.md` 2026-10-27); it gave the bigger veins without the risk.
- **Saving the pending vote result.** It follows from two saved values; saving it as well gives two sources
  that can disagree.
- **A counter for "one per N chunks".** Order-dependent and off-thread state.
- **Calling the supplier per placement step.** One chunk could straddle a purchase.

## Spec sections changed

§11 Dynamic Chunk Generator (triggers, one supplier call per chunk, the effects view, the stamp, codec and
biome), §3.1 seeding (per-feature salts and per-chunk rolls), §6.1 Volatile Veins (current members only;
applied when the Vault level is empty; refund clears the applied state) and §11 Tool Wear Hook (reads the
effects view). The exact edits are in the E0 run's `spec-changes/E0-S5.md` for the Writer to apply. E0-S18
already carries Q1 and Q2 into §3.1, §6.1 and §11; this record matches that text and adds the mechanics.

## Reviews

Matrix row, system design: developer (buildable), reviewer (rules), qa (testable); ticket adds writer and
balancer. Each verdict lists what it checked.

- developer:
- reviewer:
- qa:
- writer:
- balancer:
