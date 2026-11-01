# Decisions only Nick can make

**Answered by Nick on 2026-10-26.** His answers are in `docs/TEAM_REQUIREMENTS.md` under "Nick's answers"
(N0 to N8, Na to Nk) and the `DESIGN_LOG.md` entry of the same date. The questions below are kept as asked.

Ordered by how soon each blocks work. Each changes what the player experiences, changes scope, or cannot be
undone, which is why the documents could not settle it. Everything else the roles disputed is settled in
`docs/TEAM_REQUIREMENTS.md` under "Settled decisions". Ask one at a time; the smaller questions at the end
can go as one batch.

## 0. Use Pufferfish's Skills for the skill tree, or borrow its ideas?

Blocks: T7 and T8.

You asked to use Puffish if it fits. It stores skills per player and each skill is one unlock. Ore Vault's
tree belongs to the team and has tiers, free fork options that only swap outside the Vault, toggles and
Outgrown nodes. Its code is All Rights Reserved.

- Borrow its ideas, own the tree. Node positions as data, a frame per node class, pan and zoom, drawn by our
  own vanilla screen.
- Depend on it. Puffish draws the tree; we write a sync layer that copies the team's tree onto every
  member, and redesign forks, toggles and Outgrown nodes around what it supports. Each tier becomes its own
  box, so about 87 boxes become about 200.

Recommendation: borrow; the parts that went wrong last time (layout, frames) are the parts we can copy
without the dependency.

Argued: Developer, Designer, UI-UX, Architect and the library-scan thread all recommend borrowing. Nobody
argued for depending on it.

## 1. Should worlds saved by the old build still load?

Blocks: the docs PR before T3 (the §6.1 migration table) and T6, which lands with T5's first saved field.

The old build saved node ids that have since been renamed or removed. Supporting those saves means carrying
a migration table forever.

- No. Saves start fresh at data version 1. The §6.1 migration table and its paragraphs go. The old ids go
  on a reserved list so they are never reused. Saving stays versioned, so later changes can still migrate.
- Yes. T6 must read and convert every old id (Ore Sense to Vein Fortune, refunds for removed nodes and so
  on), with tests against real old save files.

Recommendation: no. The only worlds from the old build are your test worlds, and the rebuild should not
carry code whose only job is to read them.

Argued: Manager, Developer, Reviewer, Writer and Architect all recommend no. Nobody argued yes.

## 2. May the Tome's GUI art be drawn at one texture pixel per screen pixel unit, with 64x64 kept for blocks and items?

Blocks: T7 (Tome art) and the scope of the texture-size test.

Your rule is 64x64 where possible, never below 32x32. A small icon in the Tome, such as a lock badge, is
drawn about 9 units wide. A 64x64 source shrunk to that size is resampled unevenly at GUI scale 3 and looks
soft next to Minecraft's font. Drawn at one texel per unit it is crisp at every scale. Every file would still
be at least 32x32, because frames are stretchable sources and small icons sit together in 64x64 sheets.

- Yes. GUI art at one texel per unit; blocks and items stay 64x64. `CLAUDE.md`, §13 and a log entry change
  together, and the size test checks `gui/` by the 32x32 floor only.
- No. Every texture follows 64x64 where possible, GUI included; icons are drawn at 64x64 and scaled down.

Recommendation: yes; it is the only way the icons sit sharp beside the vanilla text, and every file still
meets the floor.

Argued: Designer and UI-UX for yes; Developer supports. QA and Reviewer take no side but require the rule
to be amended before any such sprite merges.

## 3. Where do the reset button and the Seismic Sense, Stratified and death-marker readouts live in 1.0?

Blocks: T7 (which tabs the Tome has) and T11 (reset).

The spec puts the reset button in the Ore Memory tab, but that tab is deferred past 1.0. Seismic Sense's
compass and map, Stratified's band edges and The Vault Keeps It's death marker also have nowhere to appear.
The top bar you accepted on 2026-09-03 stays as it is either way.

- A small Ore Memory tab in 1.0, holding a "Survey" panel (depth gauge with band edges, death marker,
  Seismic Sense's readouts) and the reset button bottom right, shown only to a player holding a Tier 4
  key. The stats panels stay deferred.
- The same tab, also showing the counters nodes read that a player cannot otherwise see (deepest Y, veins
  completed, Vein Discipline streak, Kindred Rock progress).
- The reset button in the Tome's header, Tier 4 only. Changes the accepted top bar and still leaves the
  readouts homeless.

Recommendation: the first option; a destructive, rarely used button belongs somewhere a player goes on
purpose, and those counters already show in node tooltips.

Argued: UI-UX and Designer for the first; Playtester for the second; Developer and Writer proposed the
header before the top bar was pointed out.

## 4. Guide Vein and Gravel Purge do nothing a player can see. Rework them, remove them, or keep them?

Blocks: freezing the node list for the T8 layout, since node costs set the tree total and the gates.

Gravel Purge removes gravel and clay, but the Vault never generates either, so it does nothing, yet Stone
Reduction requires it. Guide Vein guarantees one copper or iron vein per chunk; it was written when the
Vault held two coal veins per chunk, and at 3% a chunk already holds about 250 veins, so one more is
invisible.

- Remove both. The tree drops to 352 points; Mastery's gate moves from 160 to 159; Stone Reduction loses
  its prerequisite. At level 1 a team then has 12 points and 7 nodes to spend them on.
- Rework both: the Balancer proposes a visible effect for each (or, for Gravel Purge, T4 starts placing
  some gravel and clay), and you approve before T8.
- Keep both as written.

Recommendation: rework both, because level 1 already gives more points (12) than there are nodes to buy
(9), and removing two makes that worse.

Argued: Balancer (remove Gravel Purge; at first proposed Guide Vein as the first node), Playtester (Guide
Vein needs a new effect or removal).

## 5. Two "growth" nodes do not wear off as promised. Change them?

Blocks: T9's Prospecting cluster, and the §6 wording.

Growth nodes are meant to be strong early and retire on their own. Apprentice's Ledger (first 100 ore of
each trip pay double) resets every trip, so it never retires; a Tier 3 key makes trips free, so it
stays a permanent bonus. Shallow Grace (+50% while your deepest Y is above 100) reads each player's depth
but pays the team, so one member who stays shallow keeps it on for everyone. Separately, a retired node
currently says so only by greying out in the Tome.

- Apprentice's Ledger stops at team level 15; Shallow Grace reads the team's deepest point; a node that
  retires says so once in chat, naming itself and why.
- Keep the nodes as written, and change the spec's claim that growth nodes retire "by construction".

Recommendation: the first option; it makes the nodes do what their class promises, and the chat line stops
a permanent change reading as a bug.

Argued: Balancer for the node changes; Playtester for the chat line.

## 6. Should Resonance penalties be applied after the bonuses, so they cannot be cancelled out?

Blocks: T9 (how every node's Resonance is combined) and the §11 amendment.

The spec adds percentage bonuses together, but does not say how penalties like Greedy Seams' "−50%
Resonance" combine. If they are added in, a team with +200% of bonuses loses only a sixth of its Resonance
to Greedy Seams, not half, and enough penalties stacked together can push the total below zero.

- Penalties multiply after the bonuses are summed, and the result never drops below zero.
- Penalties are added in with the bonuses, as plain percentages.

Recommendation: multiply after; pacts are meant to "always carry a real downside", and the spec already
argues the same way for Bedrock Communion's speed penalty.

Argued: Balancer for multiply-after; Writer agrees it amends §11 rather than adding a rule. Nobody argued
for adding.

## 7. Does "about 100 hours to finish the tree" mean a team buying nodes as it goes, or a miner who buys nothing?

Blocks: judging T9's numbers, and the curve recalibration at T14. Needed before T9's first cluster merges.

The level curve is priced for a player mining at the base rate with no nodes. The Balancer's model says a
team that buys nodes as they unlock reaches level 30 in roughly 8 to 24 hours, because the multipliers are
all owned by the time the expensive levels arrive. The model is a prediction until playtested.

- A typical build. Keep every node's strength; after the T14 measurements, stretch the curve so a team that
  buys as it goes takes about 100 hours.
- A miner who buys nothing. Keep the curve; accept that real teams finish far sooner.
- Weaken the nodes to fit the current curve.

Recommendation: a typical build; you like being overpowered, so the curve should absorb the power rather
than the nodes losing it.

Argued: Balancer for a typical build and against weakening nodes.

## 8. When a team disbands, archive its Vault or delete it?

Blocks: T11, and the disband wording in §3.1, §9 and §11.

The spec deletes a disbanded team's Vault, which destroys every member's builds and cannot be undone. If
joining a friend's party disbands a player's own team (the Developer is checking), deletion would wipe a
solo player's Vault the moment they join someone. Either way nothing is touched while the server runs: the
Vault is handled at the next restart.

- Archive: move the Vault to `orevault_backups/` at the next start; an admin command can purge archives.
- Delete at the next start, as the spec says.

Recommendation: archive; disk space is cheaper than a lost world.

Argued: Manager, Architect, Developer and Reviewer for not deleting (the Reviewer for no automatic deletion
at all); Playtester adds the party-join risk.

## Smaller questions, one batch

In the order they block work.

a. A GitHub Actions build check (T1). You asked for all code to run on your PC. QA and the Reviewer wanted a
cloud build workflow as a second check. Recommendation: no for now, keeping your rule; revisit if PRs start
merging broken.

b. The portal's entry price (T2, T3). With corners required, the smallest frame takes 14 Vault Frames, 112
iron plus redstone, before the mod does anything; with corners optional, as a nether portal, 10 frames. The
spec does not say how many frames one craft makes. Recommendation: corners optional, one frame per craft
as written. Argued by the Playtester.

c. Art tools (T2, T8). Approve running `mc-asset` and the Node and Playwright preview tool on your PC now;
Retro Diffusion spending only when T13 starts. Recommendation: yes.

d. Hide the Animus row of the Tome's header until the Animus epic (T7)? The header has one row per tree, and
a row of zeros for a system that does not exist reads as a bug. Recommendation: hide it. Argued by UI-UX.

e. A short, throttled line above the hotbar showing Resonance progress when an orb is collected (T5, T7)?
Without it, Apprentice's Ledger and Shallow Grace have no visible effect while mining. Recommendation: yes.
Argued by the Playtester; UI-UX planned no extra on-screen display in 1.0.

f. Prospector's Eye's prerequisite (T8 layout). It sits in Assay but requires Vein Fortune from Metallurgy,
a leftover of a rename. Replace it with Vein Sight: buyable one level earlier, one point cheaper, no gate
moves. Recommendation: yes. Argued by the Balancer, UI-UX and Designer.

g. Silent Stone's rate (T9 Mastery). It pays 3 Resonance a second, about 15 times a solo player's rate,
which turns the last five levels from 62 hours into about 4. Recommendation: 0.30 a second (1.5 times solo,
as the spec intended) per member below Y=0, after team scaling, as a fixed number. Argued by the Balancer.

h. Players still inside a Vault when a reset finishes (T11): the spec sends them to world spawn.
Recommendation: their saved return position. Argued by the Playtester.

i. If T3's test shows a running Vault cannot be safely unloaded (T11), should a reset send everyone out
straight away and regenerate the Vault at the next server restart, with a message saying so?
Recommendation: yes. Argued by the Architect and Developer.

j. Mekanism support by item tags only, or tags plus reflection into Mekanism's code (T12)? The lessons doc
says tags only; `CLAUDE.md` and the spec allow reflection. Recommendation: tags only; they survive Mekanism
updates. Argued by the Developer; the Reviewer accepts either with a fallback.

Not asked now: whether cross-cluster prerequisites draw as short tags or full lines, and whether zoom snaps
to sharp-text steps. Both appear side by side in the T8 preview for you to judge.
