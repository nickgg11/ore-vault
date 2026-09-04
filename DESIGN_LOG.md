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

**Open, awaiting a decision.** See #137.

- Repricing the five keystones against the 100-point Mastery gate. Greedy Seams and Resonant Overload
  are at 4 points / level 6 and Brittle Stone at 5 / level 13; the spec explicitly promises that
  buying Greedy Seams early "is a real option", which the gate removes.
- Replacement keystones for the early-game slots those vacate.
- New node mechanics beyond tiers and forks.

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
