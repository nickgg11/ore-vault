# 0003. The generation snapshot and the team effects view

Status: accepted 2026-10-31 (E0-S5, #186), after the round 2 verdicts below; proposed 2026-10-27. No check
is owed to a later story for acceptance; the tests and the S44 readout below land with the code that they test. Spec: §3.1 seeding, §6 Mod-gated nodes, §6.1 Volatile Veins and
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

`GenSnapshot` (package `core.gen`, no Minecraft imports) is what the chunk generator reads. It holds only
what placement reads:

| Field | Why placement reads it |
|---|---|
| Tiers of the owned generation nodes, by skill id (Vein Expansion, Vein Proliferation, Gravel Purge, Guide Vein, Deep Veins, Stone Reduction, Vein Shaping, Ore Attunement, Full Spectrum, Geode Clusters, Ancient Traces, Volatile Veins, Molten Seam) | Vein size and count, extra features, pools |
| Fork choices of the two generation forks, Vein Shape and Ore Focus | Which option the parent's tier acts through |
| Applied state of Volatile Veins and Molten Seam | Their generation half acts only while the team's applied state is on (below) |
| Dimension variant (`orevault:ore_vault` or `orevault:ore_vault_expanded`) | Which bands exist; Ancient Traces and Deep Veins depend on it. The generator is built per variant as well, and asserts the two agree |
| `vault_generation`, the reset count | Part of every seed (Q2) |
| Two floor clip factors, one for the stone band and one for the deepslate band | S13: the 40% stone floor scales every rarity's vein count in a band by one factor, so the mix does not shift. Each is computed when the snapshot is built, by ADR 0009's rule: `min(1, 0.60 / densest)`, where `densest` is the highest ore density the team's generation nodes request in any one region of that band (Balancer, round 1) |
| `stamp`, a long (64-bit) | The chunk records it as `gen_stamp` (ADR 0004); the Survey panel compares it with the current snapshot's (Q13) |

Team level is not in `GenSnapshot`. No 1.0 generation node reads it, and the snapshot is memory only (ADR
0005 section 8), so a level-gated generation effect added later adds the field then at no migration cost
(`CLAUDE.md` rule 7's principle: no value without a reader). The E0-S5 ticket listed it; the Manager's round
1 ruling dropped it and amends the ticket.

The snapshot's collections are built with `Map.copyOf` and `List.copyOf`, so the record is immutable all the
way down. A `HashMap` handed in and later changed on the main thread would be the race `CLAUDE.md` rule 1
forbids, even inside a final field.

A tier counts only nodes that are owned and not dormant. A node owned but hidden because its mod or tag is
missing is dormant (§6 Mod-gated nodes) and is left out. No generation node is mod-gated today; the rule
is stated so that one added later cannot generate while hidden.

`stamp` is a hash of the generation node tiers, the two generation fork choices and the applied
generation tradeoffs, in a fixed order (skill ids sorted), with a fixed hash function implemented in
`core.gen`, never `Object.hashCode` of a collection. It leaves out the clip factors (which follow from the
tiers), the variant and `vault_generation` (a reset regenerates every chunk anyway). Team level is not in
the snapshot at all, so a level-up never marks the player's chunk "not current". A golden JUnit test pins the stamp of a fixed
snapshot, so a refactor cannot silently mark every chunk out of date.

`TeamEffects` (a record in `core.gen`, read on the main thread only) is what everything else reads: the drop
listener and its pipeline, the wear mixin (ADR 0008's "main-thread team snapshot" is this view), attribute
recompute, Silent Stone's ticker and the Volatile Veins roll. It holds the owned tier of every skill (dormant
nodes excluded), every fork choice, the hidden set, the applied generation tradeoffs, team level, team size,
each online member's active per-player tradeoffs, and the expected ore in a newly generated chunk under the
current generation nodes, the closed form from `OreBaseline` with the clip applied, which the Seismic Sense
shade reads (ADR 0004 Amendment 1). Those readers read nothing else for tree state: not
`SavedData`, not the layout, not the hidden set separately. This puts the dormancy, fork and tradeoff rules
in one place instead of in each consumer, and turns a per-break derivation into a field read. It also
holds whether the team has a pending generation change (the tally differs from the applied state), which
the empty-Vault check below reads.

### Pure builders, main-thread extraction

Both views are built by pure functions in `core.gen` from plain values: the owned node ids, the fork
choices, the hidden set, the applied generation tradeoffs, each current member's generation votes, the
member list and which members are online with their per-player tradeoffs, team level, team size, the
variant, `vault_generation` and the `TreeLayout`. `data.GenSnapshots` only reads those values out of the
team's `SavedData` on the main thread and calls the builders. The member list and the online set come from
the caller as values, read through the team helper by whichever adapter raised the trigger, so `data` never
imports `team` (ADR 0001, the `data` row). The dormancy, stamp and vote rules are then unit-testable, which
a builder that reads `SavedData` would not be.

Unit cases, at least: a dormant node is excluded from tiers and stamp; the stamp is unchanged by team level,
variant and `vault_generation`, and changed by a generation tier, a generation fork choice and an applied
tradeoff; a refund removes the node from the applied state; the vote passes at 1 of 1, 1 of 2, 2 of 3 and 3
of 4, and fails at 1 of 3 and 2 of 4; a former member's vote is ignored.

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
- a level-up (`TeamEffects` only, since `GenSnapshot` holds nothing a level changes);
- a dimension reset (after `vault_generation` + 1 and the variant change, before the new level ticks);
- restore at server start and Vault load (ADR 0002);
- server start and `/reload`, after the hidden set is computed (E8a-S7), since dormancy can change;
- a member's team change (`FTBTeamsEvent.PlayerChangedTeam`, which FTB Teams posts for every change,
  online or offline: `dev/ftb/mods/ftbteams/data/AbstractTeam.java` `onPlayerChangeTeam` in the
  ftb-teams 26.1.2.4 sources jar, read 2026-10-27), for both
  the old and the new team, since the vote tally and member set change;
- Volatile Veins' or Molten Seam's applied state changing (below).

`TeamEffects` is also rebuilt when a member toggles a per-player tradeoff or a generation vote, logs in or
logs out, since it holds online members' tradeoffs and the pending flag. `GenSnapshot` is not, because no
per-player state reaches generation.

So that each trigger can be seen to fire before the Survey panel exists (T7), S44's debug commands gain a
snapshot readout: the team's current `stamp`, applied generation tradeoffs, `vault_generation` and clip
factors, and the `gen_stamp` of the chunk the player stands in. Each trigger then has a check: do it, run
the readout, see the stamp change, or for a level-up see it stay the same (QA, round 1).

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
- It is applied when the Vault level has no player in it, by one check at the end of each server tick
  (package `vault`): for each team whose `TeamEffects` shows a pending change, count the non-spectator
  players in its Vault level; at zero, apply. A team with no Vault level loaded counts as empty. One rule
  covers every way out (portal, death and respawn, command, logout) and a vote or team change that moves
  the tally while the Vault is already empty. A logout listener could not do it: `PlayerList#remove` posts
  `PlayerLoggedOutEvent` (line 315) before it removes the player from the level (line 334), so the leaving
  player is still counted (Developer, round 1). The cost is one lookup per team with a pending change per
  tick.
- At server start, the pending change is applied for every team before anyone connects, and before ADR
  0002's restore calls `ensureTeamDimension`, so a restored Vault's first chunks are generated under the
  applied state rather than the stale one (Reviewer, round 1).
- Applying sets `generation_tradeoffs`, rebuilds both views, and records a `TreeState` for every online
  member in `data.SyncOutbox` (ADR 0001), which `network` sends at the end of the tick.
- The effective state is applied and owned and not dormant. Refunding the node removes it from
  `generation_tradeoffs` at once (its effect stops with the node anyway), so buying it again starts off and
  waits for the next empty Vault rather than switching on under players already inside.

The test for "empty" is the Vault level's players that are not spectators, which matches Nick's words
("once no one is in the ore vault") and also covers a player who left the team while inside. A spectating
operator does not hold the change back. A dead player stays in the level's list until respawn, so the
change applies once they respawn outside, not while they are on the death screen.

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
player and per restart, and it would be state the generator must not keep. The rate is met on average, and
a JUnit test pins how closely: over 10,000 chunk seeds per rate, the count is within three standard errors
of the expectation (Geode Clusters at 1 in 12: 833 expected, within ±83). A golden test shows that adding a
salt leaves another feature's stream unchanged.

### Generator plumbing (old #16)

- `codec()` returns `MapCodec.unit(this)`. The generator is built per team in code (ADR 0002), never parsed
  from a datapack, and an instance-bound unit codec is what the last build shipped on 26.1.
- The Vault biome, `data/orevault/worldgen/biome/vault.json`, has empty spawner lists and empty
  `spawn_costs`, so nothing spawns by default (old #13; E0-S1 corrects §3.1). That is no guarantee in a pack:
  biome modifiers run over every biome at server start, and a `neoforge:add_spawns` modifier with the
  `neoforge:any` holder set reaches `orevault:vault` (`docs/research/neoforge-26.1-sources.md`, "Whether another
  mod's biome modifier can add spawns"). The biome stays out of broad tags such as `#minecraft:is_overworld`, and
  the generator closes the rest:
  - `getMobsAt` returns an empty list for every category. Natural spawning asks the generator, not the biome
    (`NaturalSpawner.java` line 318).
  - `spawnOriginalMobs`, abstract on `ChunkGenerator`, does nothing. It is the chunk-generation mob pass, the one
    path that reads the biome's list directly (`NaturalSpawner.java` line 352, `ChunkStatusTasks.java` line 167).
  - No `LevelEvent.PotentialSpawns` listener (Architect, 2026-10-31). The event can still add to the empty list,
    but a mod has to aim at the Vault to do that, and a second hook for that case earns nothing.
  - A Disturbed Zone (§7) cannot use natural spawning in a Vault, so it spawns through its own block logic.
  E3-S5's (#233) gametest calls `getMobsAt` for every `MobCategory` and expects an empty list.

### Enforcement

S15's static rule is the enforcement: an ArchUnit rule, owned by the Reviewer and shown failing once
against a planted violation (S43), that no class in `worldgen` or `core.gen` depends on `data`, `team`,
`net.minecraft.world.level.saveddata`, `SavedDataStorage`, or any `dev.ftb` type. `GenSnapshot` and its
builder live in `core.gen`; the code that reads team data into plain values lives in `data`. ADR 0001
(E0-S3) lists the rule with the others as its named rule 1, with the same list.

## Consequences

- A purchase is visible in the next chunk generated after the rebuild. A chunk whose generation started
  before the purchase uses the snapshot it read; that is correct, and the chunk's stamp says so.
- A level-up, a per-player toggle or a login does not rebuild `GenSnapshot`, and never changes the stamp.
- The vote needs the team's member list on the main thread. The adapter that raises the trigger reads it
  through the team helper and passes it in as values; the team helper is still the only
  `dev.ftb.mods.ftbteams` importer.
- A team whose applied state is pending at shutdown gets it applied at the next start, before anyone joins.
- The seed and stamp functions are save data in effect: changing either changes the Vault every existing
  team will generate next, or marks every chunk out of date. Their golden tests make such a change a
  visible, reviewed decision.
- Molten Seam's "one per 2 chunks" and the geode and debris rates become probabilities per chunk. The
  Balancer reviews that reading; in a run of chunks the count varies, which a counter would not.

## Alternatives rejected

- Reading `SavedData` from the generator, even read-only. A race (`CLAUDE.md` rule 1).
- One view for both readers. The generator needs a small, stable set and a stamp that ignores
  everything else; the main-thread readers need per-player tradeoffs, the hidden set and team size, which
  change often. One view would either rebuild generation state on every toggle and login or give the
  drop listener less than it needs.
- The snapshot as the full owned set (the last build's `SkillSnapshot`). It changes on purchases that
  change nothing in the ground, so it cannot back the Q13 stamp.
- Per-player generation tradeoffs feeding a team snapshot, for example "on if any member has it on".
  Rejected by Nick (Q1, `DESIGN_LOG.md` 2026-10-27); it gave the bigger veins without the risk.
- Saving the pending vote result. It follows from two saved values; saving it as well gives two sources
  that can disagree.
- A counter for "one per N chunks". Order-dependent and off-thread state.
- Calling the supplier per placement step. One chunk could straddle a purchase.
- Team level in `GenSnapshot`, as the ticket listed. No 1.0 generation node reads it, and adding it later
  costs no migration (DEV-8, round 1).
- Applying the pending change from the logout listener, or from any one exit route. The leaving player is
  still in the level when logout is posted, and each route would need its own hook (DEV-7, round 1).
- Building the views inside `data` from `SavedData` directly. Correct on the thread, but the dormancy, stamp
  and vote rules could then be tested only in a gametest (QA-8, round 1).

## Spec sections changed

§11 Dynamic Chunk Generator (triggers, one supplier call per chunk, the effects view, the stamp, codec and
biome), §3.1 seeding (per-feature salts and per-chunk rolls), §6.1 Volatile Veins (current members only;
applied when the Vault level is empty; refund clears the applied state) and §11 Tool Wear Hook (reads the
effects view). Applied in the same PR. E0-S18 already carries Q1 and Q2 into §3.1, §6.1 and §11; this
record matches that text and adds the mechanics. The round 1 change to when a pending vote applies ("at the
end of the tick in which the Vault becomes empty") is the Writer's §6.1 Volatile Veins edit in the same PR.

## Reviews

Matrix row, system design: developer (buildable), reviewer (rules), qa (testable); ticket adds writer and
balancer. Each verdict lists what it checked.

Round 1, 2026-10-27. Each objection is marked fixed (what changed here), rebutted (the evidence) or deferred
(the story that takes it). The record stays proposed until each reviewer confirms in round 2.

- developer: approve with comments (DEV-7, DEV-8). Checked `fillFromNoise` and `codec()`
  (`ChunkGenerator.java` lines 111, 640); `DerivedLevelData#getGameTime`; `ServerLevel#tickTime` (lines 484
  to 492, only the overworld advances it); that one supplier call per chunk is implementable because all
  Ore Vault placement is in `fillFromNoise`.
  - DEV-7, a logout listener still sees the leaving player: fixed. A pending change applies from an
    end-of-tick check of each Vault with a pending change, counting non-spectator players.
  - DEV-8, drop team level from `GenSnapshot`: fixed. The Manager ruled for dropping it (no 1.0 node reads
    it; the snapshot is memory only) and amends E0-S5 (#186) and E9-S3 (#307).
- reviewer: approve with comments (RV-13, RV-14). Checked the `GenSnapshot` fields against the AC list;
  immutability and the `AtomicReference` swap; one supplier call per chunk; every AC trigger, `/reload`,
  team change and the applied vote among them; dormant nodes excluded; per-chunk rolls from salted seeds;
  `vault_generation` in the seed; `MapCodec.unit(this)`; empty spawners; the S15 rule; `TeamEffects`
  main-thread only.
  - RV-13, collections deeply immutable: fixed. `Map.copyOf` and `List.copyOf`.
  - RV-14, order at server start and which players count: fixed. Apply first, then `ensureTeamDimension`;
    only non-spectator players count.
  - The Reviewer's answer to the Architect's question 1 (keep team level, since the AC lists it): not taken.
    The Manager ruled for DEV-8 from this record's own table and ADR 0005 section 8, and the ticket is
    amended instead.
- qa: approve with comments (QA-7, QA-8, QA-9, QA-10). Checked the snapshot fields, stamp rules and golden
  test, the one-supplier-call rule, every rebuild trigger, the vote rule, applied-when-empty, seeds and
  per-chunk rolls.
  - QA-7, no in-game sign that a trigger fired: fixed. S44 gains the snapshot readout named under Rebuild
    triggers (the Manager adds it to S44).
  - QA-8, rules reachable only by gametest: fixed. Both views are built by pure `core.gen` functions from
    plain values, with QA's unit cases listed.
  - QA-9, "met on average" needs a tolerance: fixed. 10,000 seeds per rate within three standard errors,
    and a salt-isolation golden test.
  - QA-10, the two-client applied-when-empty playtest: deferred to E9-S60 (#364), which carries QA's four
    steps by an amendment comment.
- writer: approve with comments (WRITER-1, bolded inline headers). Checked the snapshot fields and triggers
  against the ticket; the vote rule against §6.1 Volatile Veins and Nick's quote in `DESIGN_LOG.md`; the
  per-chunk rates against §6.1 Geode Clusters, Ancient Traces and Molten Seam; §11 Dynamic Chunk Generator.
  - WRITER-1, pointer to a session file: fixed. "Applied in the same PR."
  - Bolded inline headers in the two views and the rejected list: fixed. Plain lead sentences.
- balancer: approve with comments (BAL-6). Checked the trigger list, including tradeoff application and
  `/reload`; one snapshot per chunk; the per-chunk rolls for Geode Clusters (1/12, 1/6), Ancient Traces
  (1/4, 1/2) and Molten Seam (1/2); `vault_generation` in the seed; the team-wide vote; dormant nodes left
  out.
  - BAL-6, "if ADR 0009 computes one factor per band": fixed. Two factors, stone and deepslate, each
    `min(1, 0.60 / densest)` as BAL-3 set it in ADR 0009.

Round 2, 2026-10-31, against HEAD 0c5736c; ruled in the Manager's round 2 triage, with no round 3. Every
round 1 objection above was reported resolved by the role that raised it, and nothing new was raised
against this record.

- developer: approve. Checked DEV-7 (the end-of-tick check counting non-spectator players, and spec §6.1
  Volatile Veins) and DEV-8 (team level out of `GenSnapshot`, its table and the paragraph after it).
- reviewer: approve. Checked RV-13 (`Map.copyOf`, `List.copyOf`) and RV-14 (apply before ADR 0002's
  restore; only non-spectator players count) at their lines.
- qa: approve. Checked QA-7 (the S44 snapshot readout), QA-8 (pure `core.gen` builders from plain values)
  and QA-9 (10,000 seeds, three standard errors, salt isolation).
- writer: approve. Checked that no bolded inline header or session-folder pointer is left. "Newly
  explored" here means newly generated, the sense WRITER-21 keeps for "explored", so it stays.
- balancer: approve. Checked BAL-6 (two factors, stone and deepslate).
- architect (read across ADRs 0001 to 0009): approve; `GenSnapshot` without team level, `TeamEffects` on the
  main thread and `data.SyncOutbox` read the same in every record.

One change made elsewhere touches this record without changing it: ADR 0001 now places the two checks
here (the empty-Vault apply at the end of each tick, the apply for every team at server start) in the root
`ServerTickEvent.Post` and `ServerStartedEvent` listeners, in the order those listeners state (DEV2-3,
RV2-2). The end-of-tick check stays in `vault`, as written above.

Amended 2026-10-31 by E1-S5 (#195), from `docs/research/neoforge-26.1-sources.md`: empty spawner lists are not a
guarantee in a pack, so the generator overrides `getMobsAt` and leaves `spawnOriginalMobs` empty. Behaviour stays
no mobs, so this is not a design change.
