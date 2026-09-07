# Design Log

Dated record of playtest feedback and the design decisions that came out of it, with the reasoning.

`OreVault_Design_and_Spec.md` says **what** the design is. This file says **why it changed and what
was rejected**, which the spec deliberately does not carry. Tickets record what was planned; this
records what a playtest actually produced.

Read this before proposing a change to the skill tree, the Tome UI, or the Resonance curve. Several
decisions below were reached after a version that looked reasonable on paper failed in play, and the
rejected version is usually the one a fresh reading of the spec would arrive at again.

Newest first.

---

## 2026-09-07 — Hubs, not lanes: the tree radiates

**Feedback (playtest of the #136 band renderer, before it merged).**

- "The spec document states that the skill tree should look like the one in Diablo 4, so there should
  be central nodes that the sub nodes radiate from, right now it's just a list downward."
- "The background is too transparent to see the connecting lines clearly."
- "The node boxes do not expand to account for the required writing not fitting in the boxes."

**Decisions.**

| Decision | Reasoning |
|---|---|
| Each cluster becomes a **hub**: the anchor at the centre, the cluster's nodes on rings around it, hubs strung down one spine | Three lanes read as a list because that is what three lanes are. Staggering nodes either side of a centre line is not the same thing as radiating from a centre, and the spec's own reference — Diablo 4's tree — is hubs you follow down a spine, each surrounded by its own skills. Distance from the anchor now carries the meaning vertical position used to. |
| Radial distance from a hub is **prerequisite depth inside that cluster**, and nothing else | The rule #136 exists for, restated in the new geometry. A node on the first ring waits on nothing but the cluster gate, which is why it gets a spoke straight to the anchor. |
| A **corridor** either side of vertical is kept empty at every radius | The spine has to run through the middle of a cluster to reach the next hub, and an edge leaving a cluster has to get out past every ring. Reserving the wedge gives both a place to go that is free by construction rather than free today. |
| A prerequisite in **another cluster no longer counts** toward depth | Found while asserting the new rule. Vein Proliferation opens Excavation and requires Vein Expansion over in Prospecting, and the old depth count put it on the second ring with an empty first ring underneath it — a node visibly held back by something that is not on the page. |
| The page is drawn **opaque** | The tree sat on the screen's translucent backdrop, and one-pixel prerequisite lines competed with whatever the player was standing in front of. A tree that is only readable facing a wall is not readable. |
| A box is measured from **every line it will ever draw** | It was measured from the node's name. The second line is the tier, the cost and the level requirement, and for a specialised fork parent it is the chosen option's display name — routinely longer than the parent's own. Measuring all of them, including the states the node is not in yet, is also what stops a box resizing when it is bought and shoving its neighbours. |

**The cost, stated plainly.** The page is now 944 by 6008 pixels, against roughly 500 by 1540 for the
bands. A radial cluster is wide because a box lying broadside to its hub pushes the next ring out by
half its own width, and eight clusters stacked at that radius are tall. Panning already worked in
both directions and the tab now opens centred on the first hub rather than on an empty corner. If it
plays too large, the lever is a narrower, taller node box — wrapping the name onto its own line
roughly halves ring spacing — not a return to lanes.

**Superseded.**

- **Three lanes.** Recorded in the entry below and correct for a band layout, which this is not. The
  reasoning there — that a 900-pixel-wide tree pans sideways across the cluster order instead of
  scrolling down it — did not survive contact with the actual complaint, which is that a tall narrow
  tree reads as a list no matter how well the lanes are packed. The tree is now wide *and* tall, and
  the cluster order is still the scroll direction.
- **Band gaps and gutters as the routing proof.** Replaced by the same idea in polar form: rings are
  disjoint annuli, boxes on a ring do not overlap in angle, and the corridor is clear at every
  radius. An edge travels along a node's own ray, around an arc inside a gap, or up a corridor, and
  nothing else. The gutters survive unchanged for edges between clusters.

---

## 2026-09-07 — Three lanes, and edges that route through gaps

**Superseded the same day by the entry above.** Kept because the routing argument carried over and
the lane reasoning is the one a fresh reading of the spec keeps arriving at.

Implementing the cluster/stagger renderer (#136) forced two choices the spec had left open.

**Three lanes, not five.** §6.1 asks for nodes staggered left and right of a centre line, which is a
centre lane and one either side. Five lanes were tried first because they make the tree shorter, and
they were wrong for two reasons. A five-lane tree is roughly 900 pixels wide, which does not fit a
Tome page at a normal GUI scale — so it pans horizontally, across the cluster order, instead of
scrolling down it. And the widest fork in the tree has three options, so five lanes meant every fork
sat in a band with two leftover lanes beside it that nothing could use, because a node placed there
would read as belonging to the fork.

Three lanes make the tree tall and narrow. That is the right shape for a book: the clusters already
run top to bottom, so the scroll follows the reading order, and a fork fills a band exactly.

**Edges are routed, not drawn straight.** The grid drew an elbow between two box centres, which is
why lines crossed the boxes in between. Replacing it with a shortest-path router would have been the
obvious fix and would have needed a collision test against every box on every frame.

Instead the geometry is arranged so that crossing is impossible. Every box in a band shares a top
edge and a height, so the gap between two bands is an empty horizontal strip all the way across, and
the left and right gutters are empty vertical strips. An edge only ever travels through a gap or a
gutter. Boxes one band apart get a short elbow through the gap between them; anything further apart
goes out into a gutter and back. No route can cross a box, whatever nodes are added later, and the
test asserts it as a property of the routing rather than of today's node set.

**Rejected: shortening "Volatile Veins: Ultimine Gambit".** It is the longest name in the tree by
some margin and it was the one name that would not fit the box-width cap. §6.1 pins it as a display
name deliberately, so the cap moved to fit the name rather than the reverse. A test fails if a future
node outgrows it, which is the right place to have that argument.

---

## 2026-09-03 — Skill tree redesigned around clusters

**Feedback (playtest of the #36 grid renderer).**

- The grid stacked unrelated nodes. Gravel Purge sat directly under Common Ore Boost while both were
  available from the start, so vertical position read as a prerequisite chain that did not exist.
- Prerequisite lines terminated at node *centres*, so they crossed the boxes and their text.
- Node boxes were a fixed width; longer names were truncated.
- The top bar of the Tome was accepted as-is.
- The GUI was confirmed consistent across all three GUI scales.
- The three rarity Ore Boosts were "3 nodes instead of one node with 3 choices".
- Nothing on screen distinguished a fork or a keystone from an ordinary node.

**Decisions.**

| Decision | Reasoning |
|---|---|
| Tree becomes a vertical run of named **clusters**, each headed by a non-purchasable **anchor** | Vertical position now means one thing: how deep into the craft you are. The reference is Diablo 4's skill paths — a spine you follow downward with clusters fanning off it. |
| Anchors gate on **skill points spent in the tree**, not team level | Chosen over team level and over "both". A team that ground to level 25 without committing to anything has not earned the deep clusters; a team that has spent 100 points has, whatever their level says. It is also independent of Resonance curve tuning. |
| Cluster names are mining-and-knowledge flavoured and read as a ladder | Prospecting → Excavation → Assay → Metallurgy → Deep Lore → Mastery, plus Broad Cut for Ultimine. Find it, dig it, identify it, process it, listen to it, master it. |
| All keystones move to the **Mastery** cluster at the bottom | Requested directly. Consequence: the early-purchase keystones are now mispriced (see open decisions). |
| Forks become **one paid parent plus free options** | The old three-Ore-Boost chain stacked, so everyone bought all three in the same order and nothing was ever decided. The parent costs points and is inert until specialized; the option costs 0 and decides what the parent's tiers do. |
| Fork **tiering is preserved**, moved onto the parent | Explicitly asked for. Per-tier effect values moved verbatim; the number of paid steps and the number of decisions are unchanged. What changed is that you buy the tiers once instead of once per rival branch. |
| An option must cover **every tier its parent has** | A parent tier with nothing behind it is a paid step that does nothing. Hoarder's Instinct had exactly that gap on conversion (2 tiers under a 3-tier parent) and gained a third tier to close it. |
| Fork options are **freely swappable outside the Vault, locked inside** | Same rule and same reason as tradeoffs: inside, a player would re-specialize per vein and take every upside of a choice they never committed to. |
| Vein Singularity **loses its `[KEYSTONE]` tag** | It was tagged both `[FORK]` and `[KEYSTONE]`. A free option cannot be a keystone, and keystones now live only in Mastery. Its jackpot flavour is unchanged. |
| Node art wants **craggly stained paper** frames, one per node class | Readability wins over texture — the paper is a frame and a wash, not a busy fill behind text. The Tome's top bar is already right and does not change. Tracked on #69. |

**Rejected or superseded.**

- The `TreeLayout` grid (branch → column, prerequisite depth → row). Correct as code and wrong as a
  design: deriving position from data is right, but branch and depth were the wrong two axes.
- Ore Attunement's standalone "+10% vein count for all rarities". A fork parent now does nothing
  until specialized, so the parent cannot carry its own bonus.
- Gating anchors on team level, and on level-and-points together.

**Resolved by the 2026-09-07 entry below.** The three open items — repricing the keystones against
the Mastery gate, replacing the early-game slots they vacated, and new node mechanics — were taken to
#137 and answered there.

---

## 2026-09-07 — Pacts, growth nodes, and the Claim split

**Where this came from.** #137 was opened with three proposals and answered inline, choice by choice.
Everything here is a decision made in that issue, not a reading of the spec.

**Decisions.**

| Decision | Reasoning |
|---|---|
| Greedy Seams, Resonant Overload and Brittle Stone keep their original prices and become a new `[PACT]` class outside Mastery | Option A was chosen for all three, which put keystones in early clusters and broke "keystones live at the bottom". Repricing them for a 100-point gate would have deleted the thing they exist for: Greedy Seams undercutting Ore Doubling on raw yield is a decision at 4 points and nothing at all at 100. A pact is a keystone that is allowed to be early. |
| A `[GROWTH]` class for nodes that retire themselves | Novice's Luck, Apprentice's Ledger, Shallow Grace, Guide Vein, Salvager's Eye. Each is capped by a flat number or a hard level cutoff, so rising throughput retires it rather than a designer's judgement. |
| A retired growth node **keeps** its spent point | Refunding on retirement would make all five free picks, which removes the decision they are meant to pose. The Tome greys the node and labels it Outgrown instead. |
| Deep Lore splits; the infrastructure half becomes **Claim** at 55 | Chunk tickets, automation, navigation and the Vault's death-side interventions were sitting under the same 70-point anchor as the burst mechanics. A team wants the utility earlier, and Deep Lore now means one thing. Ladder reads: find it, dig it, identify it, work it, hold it, listen to it, master it. |
| Anchor gates are stored as a **fraction of tree cost**, not a point total | The tree grew from 225 points to 354 in this pass alone. Absolute gates silently stop meaning what they were chosen to mean; `NodeDefs.anchorGate` derives them and a test pins the ordering. |
| Bedrock Communion's penalty is a **mining-speed halving that lifts at Y=246** | Damage over time was rejected outright as an irritation rather than a cost, and it would have stacked lethally with Molten Seam. Y=246 is the base of the dirt band (§3.1), so every surface build and automation floor is above the line. |
| Vein Discipline's streak **decays by 3** on abandon | A hard reset makes one stray swing at the edge of an unnoticed vein cost fifteen veins of work, and the rational response is to stop exploring and mine defensively — the opposite of what the completion group is for. |
| Calloused Hands is **logarithmic**, capped at +40% | The proposed `blocks × 0.0001` read as a multiplier reaches ×30 over a 100-hour run. Read as a percentage the scale is right, and the logarithm means doubling the block count adds a constant rather than a multiple. |
| Highwater Mark pays in **tool and armour durability**, not a percentage | The depth-record version topped out near +8%, which is not worth a node. Durability is the thing a miner actually runs out of, and it opened the wider direction: rewards should be things a player wants while playing, not always more ore. |
| Second Wind's return-to-portal is a **separate, toggleable node** at twice the cost | Asked for as an expensive, toggleable second tier. A separate node is what makes it toggleable at all — the tradeoff machinery already stores per-player on/off state — and being yanked out of a deep shaft is not always wanted. |
| Efficient Miner becomes **Miner's Constitution**; the Hunger branch is deleted | Hunger is one line inside the Prospecting survival group, not a category. Tiers 4 and 5 gain max health. This is a node-id rename, so it joins `ore_sense` → `vein_fortune` in the save migration. |
| Vein Sight sits in **Assay**, ahead of the nodes it enables | Without it, finishing a vein is something that happens to you, and Vault Echo, Vein Discipline, Last Ore, Clean Cut and Chapter's End all pay out for luck. It arrives first on purpose. |
| Removed nodes **refund their points**; `motherlode` maps to nothing | Points really were spent, and this build offers nothing to spend them on again. Vein Singularity is a free option under a parent a Motherlode owner never paid for, so mapping it across would hand out a paid node. |

**Rejected.**

- **Hollow Earth** (cave-generation keystone). "Seems tedious and not useful. There will be plenty of
  ways players can use other mods to quickly mine large areas." It was also the most work in the set,
  needing its own generation mode.
- **Attunement slots** as a third balance axis. "I like being overpowered so I'll pass on this. The
  power loss will come from fork choices." Recorded so a future pass does not re-derive it.
- **Rites** (activated, timed buffs). Interesting, but post-1.0 and only after more investigation:
  the risk is a mechanic whose power has to be balanced against the annoyance of reactivating it
  constantly. Not a no, a not-yet with a named failure mode.
- **First Light** and **Borrowed Tools**, two growth nodes that were not picked.
- **Cartographer's Instinct** as a standalone node. Folded into Seismic Sense tiers 2 and 3 — two
  chunk-density readouts competing in the same Tome is one too many.
- A hard streak reset on Vein Discipline, and the linear form of Calloused Hands. Both are the
  version a fresh reading arrives at, and both are wrong at an end.

**Still open.** Animus is untouched and stays deferred. The gameplay effects of the 30 new nodes are
unimplemented — this pass landed the spec, the registry, the unlock rules, the migration and the
maths, not the behaviour.

---

## 2026-09-03 — Config values that silently did nothing

**Feedback.** Enabling debug commands from the mods menu appeared to need a world reload.

**Finding.** It did not. The Brigadier `requires` predicate is evaluated server-side on every
execution, so the command already worked when typed out in full. What was stale was the *client's*
copy of the command tree, which the server only sends on login — so the command showed red and would
not tab-complete, which is indistinguishable from a setting that did not take.

**Decisions.**

- `ConfigReload` re-sends the command tree to every online player on `ModConfigEvent.Reloading`.
- An audit found **one of nine config options was actually live**. Three are cached at server start
  and now carry NeoForge's `worldRestart()`, so the config screen warns instead of silently accepting
  an edit. Four have no reader anywhere in the tree and now say "does nothing yet" in both the TOML
  comment and the in-game tooltip, rather than looking functional.
- Inert settings stay in the file rather than being removed and re-added later, because that churns
  every player's TOML.

---

## 2026-09-03 — Vanilla XP in the Vault

**Feedback.** "I still want ore to drop regular exp in the mining dimension too. They shouldn't be
punished for using my mining dimension."

**Decision.** Vanilla XP from ore is never suppressed inside the Vault. Resonance is additive to it,
not a replacement. Written into §4.2 so a future node cannot quietly remove it.

Verified at the time that no suppression existed — `popExperience` appears nowhere in the tree. The
rule exists to stop it being added, not to fix something.

---

## 2026-09-03 — Textures

**Decision.** Every texture Ore Vault ships is **64×64 where possible and never below 32×32**
(§13 Polish). Vanilla's 16×16 is not the target; Minecraft renders higher-resolution textures without
a resource pack, so this is a choice about how the mod looks rather than a limitation.

The rule already existed in the spec and was being contradicted by the very ticket that creates
textures, with all six shipped textures following the wrong one. Restated in `CLAUDE.md`, which is
what a session actually reads before writing an asset.

Does **not** apply to vanilla sprites the mod borrows — the Resonance orb tints the vanilla
experience-orb texture and ships nothing of its own.
