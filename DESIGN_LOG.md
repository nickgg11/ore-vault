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

## 2026-09-09 — Descriptions state the effect, and the nodes that could not be described got defined

**Feedback (playtest of the hub renderer).**

- "I don't want to see things like 'The mirror image.' or 'Take the material now and pay for it in
  progress.' These are statements for explaining to me. I don't want that in the descriptions. I just
  want the descriptions to explain what the buffs do."
- "Nothing can be generic — the user needs to be able to read the description and know exactly what
  they're getting. Putting the numeric values in the description would help."
- "Things that cannot be allowed are things like in Stratified's description where it says 'Rare ore
  always sits at a known depth.' What is that depth? How does the user know? This cannot be allowed,
  it must be specified."
- "If there are any generic descriptions that come because the nodes aren't clearly defined then we
  need to clearly define the nodes so the descriptions can be clear."

**Where the bad descriptions came from.** Every node in §6.1 has a blockquote under its name and a
table beside it. The blockquote was flavour — written to sell the node to a reader of the design
doc — and the table held the effect. The lang file was filled from the blockquote. So the Tome was
showing players the pitch and keeping the numbers in a document they will never open. "The mirror
image" is a perfectly good sentence in a design document, sitting directly above a table that says
`+100% Resonance from ore, −50% ore drops`, and a catastrophic one in a tooltip on its own.

**Decisions.**

| Decision | Reasoning |
|---|---|
| The blockquote under each node **is** the description, and it states the effect with its numbers | Two texts, one of which is player-facing, is how they drifted. Now there is one, written once and copied into `en_us.json` from the same source, so the spec cannot say one thing while the tooltip says another. |
| Rename history and design rationale move out of the description into the notes below the table | "Renamed from Efficient Miner" is worth keeping and is not a description of a buff. Three nodes carried that kind of parenthetical inside the sentence the player reads. |
| Multi-tier nodes list every tier's value in the description | A player deciding whether to spend the next point is comparing tier 3 against tier 4, and a description of tier 1 does not help them. Vein Expansion now reads "+15% size, then +30%, +50%, +75%, +100%" rather than "increases the size of ore veins". |
| Twelve nodes whose spec tables were adjectives got real numbers, and those numbers are constants in `NodeCosts` | This is the substantive half of the change. "Moderate geode frequency", "occasionally drops flint", "sparse ancient debris", "a burst of vanilla XP", "brief Regeneration I", "1–3% (balance TBD)" — each of these was an open design question wearing the costume of a specification, and none could be described honestly because nobody had decided. Putting them in `NodeCosts` means the tooltip, the spec table and whatever implements them quote one number. |
| Stratified names its band depths: rare at Y≤60, uncommon Y=61–150, common Y=151–245 | This was the example in the feedback and it was the sharpest case: the node's entire value proposition is that you know where to dig, and it did not say where. Fixed rather than proportional boundaries so the answer is the same sentence in both dimension variants — expanding the Vault deepens the rare band instead of moving every line. |
| `NodeLangTest` fails the build on a description that hedges instead of counting, or that is too short to state an effect | The banned words are the ones that stood in for a number that had never been chosen. Qualifying a real number still passes: "about +7% at 100,000 blocks" is fine, "moderate" is not. Verified by putting "The mirror image." and "a moderate frequency" back and watching both tests fail. |
| The Tome wraps a description at 220px instead of drawing it as one line | Descriptions went from a phrase to a paragraph, and a tooltip line is not wrapped for you. Unwrapped, the longest of them is a strip of text wider than the screen. |

**One number moved because pinning it exposed a balance bug.** Volatile Veins was first set at
2%, which read fine on its own. Ultimine Safety subtracts a flat 1% then 2%, so a 2% base meant
3 skill points took the tradeoff to exactly zero risk while keeping its +25% vein size — a downside
that another node switches off is not a downside. At 3% the floor is 1%, Ultimine Safety is still
clearly worth buying, and the pact stays a pact. The old "1–3% (balance TBD)" hid this: with the
range unresolved, nobody could notice that one end of it broke a different node.

**Rejected.**

- **Keeping the flavour line and adding the numbers after it.** Tried it on a few nodes and every one
  read as a tooltip apologising before getting to the point. The flavour is not load-bearing; the
  numbers are.
- **A test that every description contains a digit.** It sounds like the rule the feedback asked for
  and it is not: "Fall damage inside the Vault is halved, then removed entirely" is completely
  specific and has no digit in it, and so are Vein Fortune, Stone Curse and half a dozen others. The
  allowlist needed to make that test pass would have been long enough to hide a real regression in.
- **Leaving the twelve underspecified nodes alone and describing them vaguely but honestly.** This
  was the tempting option, because picking numbers is a design decision and the feedback was about
  wording. But a description that cannot be written is the symptom, not the problem — the node was
  never specified, and it would have been implemented by whoever got there first picking a number
  anyway, with no record of it.

**Numbers pinned here for the first time, and therefore the ones most worth arguing with:** Stone
Memory's flint 10% / nugget 2% / burst 0.5%, Miner's Constitution's 5-second Regeneration, Deep
Veins' 1.5× at tier 1, Stone Reduction converting stone that touches a vein rather than filler
anywhere, Geode Clusters at one per 12 then one per 6 chunks against the overworld's ~24, Ancient
Traces at one per 4 then one per 2, Volatile Veins' disappearance chance settling at 3%, Wanderer's
Cache's 20% book, Pathfinder's Claim's 50 XP, Hoarder's Instinct's 4-block merge radius, and
Stratified's two band lines. None is playtested. They are pinned so they can be argued with, which
is more than the adjectives allowed.

---

## 2026-09-07 — Descriptions, prerequisite chains, and zoom

**Feedback (playtest of the hub renderer).**

- "Some of the node descriptions aren't working, they just [say] `node.orevault.<node name>.desc`."
- "The ring design is cool but there is a clarity issue on the outer ring nodes, it's hard to tell
  what the prerequisite nodes are to unlock them."
- "Since the tree is so big now the zoom should be able to go out farther."

**Decisions.**

| Decision | Reasoning |
|---|---|
| Write the 49 missing node names and descriptions, taken from §6.1 rather than invented | Half the tree had no lang entry at all. Names survived because the Tome falls back to `NodeDef#name()`; descriptions have nothing to fall back to, so the tooltip printed the key. The spec already carries a written description for all but three nodes, and using it keeps the two from drifting. |
| `NodeLangTest` fails the build if any node lacks either key, or if a key names a node that is gone | The bug is invisible except by hovering one node at a time, and 30 nodes arrived in a single pass (#137). It was verified by deliberately breaking one key and watching the test catch it, rather than trusting that it would. |
| Hovering a node lights its **whole** chain of prerequisites, not just the immediate ones | The question a player is asking on an outer ring is what do I have to buy to get this, and the honest answer is the path, not the last step of it. The halo sits outside the box so the node's own purchase colour stays readable underneath. |
| A locked node's reason **names** what is in the way | "Needs an earlier node first" was true and useless. It now names the node and the tier, the level and the level you are, or the cost and the points you hold. |
| The tooltip lists every prerequisite whether or not the node is locked, with the cluster each one is in | The chain highlight answers "where", the tooltip answers "what", and cross-cluster prerequisites are the ones a player cannot find by looking — Ancient Traces in Assay waits on Vault Expansion in Mastery. |
| Mouse wheel zooms between 0.2x and 1.5x, anchored on the pointer; drag still pans | Wheel used to scroll vertically, which a 944 by 6008 page makes nearly useless — it is a two-axis canvas, not a column. Anchoring on the pointer is what makes "zoom out, find the cluster, zoom back in on it" one gesture instead of a zoom followed by hunting for where it went. |
| Zoomed out, node text is dropped and lines are thickened | Three-pixel text is not small text, it is noise over the shape the player zoomed out to see. Lines are drawn in tree coordinates and scaled with everything else, so at a fifth of full size a hairline is a fifth of a pixel and simply is not there — zooming out to see the shape of the tree and losing the lines that give it that shape would be a poor trade. |
| A zoom readout in the corner | A zoom nobody knows about is not a zoom, and the previous build had none, which is why the feedback was phrased as wanting the existing one to go farther. |

**Rejected.**

- **A minimum zoom that fits the whole tree on one screen.** That is about 0.09x, at which a node box
  is two pixels and a cluster is a smudge. 0.2x puts a whole cluster on screen and the run of hubs
  within a couple of drags, which is as far out as the picture still says anything.
- **Keeping wheel-scroll and putting zoom on a modifier.** The tree is a canvas that has to be crossed
  in both directions; panning is what drag is for, and reserving the wheel for the axis that needs it
  least would have kept the more useful gesture behind a chord.

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
