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
