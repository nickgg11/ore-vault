# Ore Vault — Technical Design & Specification Document

> **Version:** 1.1 — Design revision (skill tree restructure, Animus deferred to post-1.0)  
> **Target:** Minecraft 26.1, NeoForge  
> **Hard Dependencies:** FTB Teams  
> **Soft Dependencies:** FTB Ultimine, Mekanism, generic ore-dust mods  
> **Book Item:** Tome of the Deep Seam  
> **Last Updated:** 2026-08-28 — post-playtest design review  

---

## How to Use This Document

This document is the single source of truth for the Ore Vault mod. It covers design intent, system architecture, every node definition, balance values, and implementation notes. Feed this file to Claude Code at the start of every development session before writing any code. A checklist section at the bottom tracks implementation progress.

---

## Table of Contents

1. [Overview and Design Philosophy](#1-overview-and-design-philosophy)
2. [Dependencies and Mod Identity](#2-dependencies-and-mod-identity)
3. [Core Systems](#3-core-systems)
4. [Resonance System](#4-resonance-system)
5. [Animus System](#5-animus-system)
6. [Skill Trees — Complete Node Definitions](#6-skill-trees--complete-node-definitions)
7. [Disturbed Zones](#7-disturbed-zones)
8. [UI — Tome of the Deep Seam](#8-ui--tome-of-the-deep-seam)
9. [Mod Integrations](#9-mod-integrations)
10. [Configuration](#10-configuration)
11. [Implementation Notes and Technical Gotchas](#11-implementation-notes-and-technical-gotchas)
12. [Suggested Build Order](#12-suggested-build-order)
13. [Implementation Checklist](#13-implementation-checklist)

---

## 1. Overview and Design Philosophy

### What the Mod Is

Ore Vault adds a per-team mining dimension with dramatically increased ore density, zero ambient mob spawning, and full universal brightness. Players build a rectangular portal frame from Vault Frame blocks, ignite it with a Vault Igniter item, and step through into their team's private Vault dimension. Inside, they mine ores, accumulate Resonance, and progress through a shared skill tree that enhances their Vault experience over time.

A secondary system — Disturbed Zones — lets teams opt into mob farming within specific areas of the Vault using a placeable block, progressing a separate Animus-driven skill tree focused on combat rewards.

### Design Philosophy

- **Safe paradise by default.** The Vault is a mob-free, fully-lit mining environment. Danger is opt-in via Disturbed Zones and tradeoff nodes.
- **Long-term progression.** The skill tree is designed for 80–120 hours of team play to fully complete. Early nodes provide immediate utility; late nodes are meaningful milestones.
- **Modpack-aware but not modpack-dependent.** The mod detects and integrates with FTB Ultimine and Mekanism at runtime. If they are absent the mod functions identically without them. No hard references to soft dependencies anywhere in code.
- **Team-first.** All progression is shared via FTB Teams. Solo players are supported as a team of one. The mod requires FTB Teams and will not load without it.
- **Config-light.** Most values are derived dynamically from the defined skill tree. Admins only configure things with server performance implications.

---

## 2. Dependencies and Mod Identity

### Mod Metadata

| Field | Value |
|---|---|
| Mod ID | `orevault` |
| Display Name | Ore Vault |
| Minecraft Version | 26.1 |
| Forge Version | Latest stable for 26.1 |
| Java Version | 25 |
| Hard Dependencies | FTB Teams |
| Soft Dependencies | FTB Ultimine, Mekanism |

### Dependency Behaviour

**FTB Teams (hard dependency)**
- Mod will not load without it. Declared in `mods.toml` as mandatory.
- Every system that involves player data, dimension ownership, Resonance pools, Animus pools, or skill tree state operates on the FTB Team ID.
- Solo players automatically have a single-member team and function normally.

**FTB Ultimine (soft dependency)**
- Detected at runtime via `ModList.get().isLoaded("ftbultimine")`.
- If absent: Ultimine-specific skill nodes do not appear in the UI at all. No greyed-out state, no tooltip, simply not rendered.
- If present: Ultimine nodes appear in the Resonance tree and the Ultimine integration hooks are active.

**Mekanism (soft dependency)**
- Detected at runtime via `ModList.get().isLoaded("mekanism")`.
- If absent: Ore Doubling nodes function with raw ore / dust fallback only.
- If present: Ore Doubling nodes unlock additional tiers using Mekanism processing outputs.
- No direct import of Mekanism classes anywhere. All integration goes through reflection or the Forge ore dictionary / tag system.

---

## 3. Core Systems

### 3.1 The Vault Dimension

**One dimension per FTB Team.** Dimension registry keys follow the pattern:
```
orevault:vault_<teamId>
```
Where `<teamId>` is the UUID of the FTB Team, stripped of hyphens.

**Dimensions are created lazily — on the first portal trip by a member of that team, and never before.** No dimension is created at server start, and none is created when a team is registered. This matters because FTB Teams auto-creates a single-member team for every player who logs in; eager creation would mean one full `ServerLevel` (chunk map, storage, region directory) per player account the server has ever seen, whether or not they use the mod.

The dimension type JSON is static (two variants, below), but the chunk generator is a custom implementation that reads the team's current skill tree state at chunk generation time, allowing node purchases to affect newly generated chunks immediately without a server restart.

**Dimension type variants.** Two types exist; a team's Vault uses the base type until the Vault Expansion keystone is purchased and the dimension is reset, at which point it is re-created under the expanded type. The only difference is how deep the world goes before bedrock:

| | `orevault:ore_vault` (base) | `orevault:ore_vault_expanded` |
|---|---|---|
| `min_y` | `0` | `-64` |
| `height` / `logical_height` | `320` | `384` |
| Bedrock floor | Y=0 | Y=−64 |
| Deepslate band | none | Y=−63…−1 — the densest ore band in the mod |

Shared attributes for both types:
- `ambient_light: 1.0` — full universal brightness, no torches needed
- `visual/sky_light_factor: 1.0` + `visual/ambient_light_color` — fullbright terrain (MC 26.1 renders block brightness from environment attributes; `ambient_light` alone no longer lights terrain)
- `fixed_time: 6000` — always midday aesthetically
- `monster_spawn_light_level: 0` — monsters would only spawn in total darkness, which the fullbright environment never has
- `has_raids: false`
- `bed_works: false` / `respawn_anchor_works: false`
- `natural: false` — disables passive mob spawning, sleep and spawn protection

**World generation (overworld-style layering, data-driven — #76):**
- The layer stack is a bottom-up list of `{block, thickness}` pairs, jamd-style, loaded per dimension type from `data/orevault/worldgen/vault_layers/<type>.json` and validated against the dimension height; missing/malformed configs fall back to the built-in stack for that type
- Base stack (`ore_vault`, Y=0…319): bedrock Y=0, stone Y=1…245, dirt band Y=246…249, grass surface Y=250, open air Y=251…319 (69 blocks of open working space)
- Expanded stack (`ore_vault_expanded`, Y=−64…319): bedrock Y=−64, deepslate Y=−63…−1, then identical to the base stack from Y=0 upward
- Entry point: a **fixed anchor per team** at the Vault origin (X=0, Z=0), standing on the grass surface. Vault arrival never mirrors or otherwise derives from Overworld coordinates — every trip in lands on the same block, and the return portal stands there permanently (§3.2)
- No aquifers, no caves by default (open to adding cave generation as a future node)
- Ore generation handled entirely by the custom chunk generator (ores replace stone inside the configured stone band), not static placed features
- A hard floor of 40% stone content is enforced regardless of skill tree state — the Vault will never be more than 60% ore by volume

> **Implementation gotcha — mid-session dimension creation.** After putting the new `ServerLevel` into `server.forgeGetWorldMap()`, you **must** call `server.markWorldsDirty()`. `MinecraftServer` ticks levels from a cached array (`getWorldArray()`) that is only rebuilt when that marker changes; without the call, a dimension created after the first server tick is never ticked at all. Symptom: block-breaking stalls after one `BreakSpeed` event and `BREAK_BLOCK` never fires, entities never tick, and relogging "fixes" it. See issues #82 / #89.

**Dimension cleanup:**
When an FTB Team is disbanded, the team's Vault dimension is deleted. This includes all chunk data and the team's SavedData entry. A server log entry is written noting the deletion.

---

### 3.2 Portal and Frame

**Vault Frame Block**
- Crafting recipe: 8 iron ingots surrounding 1 redstone dust (shapeless, fills all 8 outer slots)
- Hardness: 1.5 (mines at stone speed), blast resistance: 6.0
- Mineable with any pickaxe (`minecraft:mineable/pickaxe` tag; MC 26.1 harvest rules are driven by the item `Tool` component)
- Requires correct tool to drop
- Sound: metal
- Right-clicking with a Vault Igniter triggers the portal shape scan
- Breaking any frame block — including a corner, which is diagonal to every portal block — dissolves the whole portal (`affectNeighborsAfterRemoval` scan + `updateShape` whole-frame re-validation)

**VaultPortalShape Scanner**
- Scans outward from the clicked frame block in both X and Z orientations
- Valid frame: rectangular, all four sides made of Vault Frame blocks
- Interior dimensions: minimum 2 wide × 3 tall, maximum 21 wide × 21 tall
- Interior must be entirely air or existing portal blocks
- If valid: fills interior with the tier-matched Ore Vault Portal variant, oriented to match the frame axis
- If invalid: plays a failure sound, no portal created

**Ore Vault Portal Block**
- No collision
- Hardness: -1.0 (unbreakable), blast resistance: 3,600,000
- Light level: 11
- Has HORIZONTAL_AXIS block state property for orientation
- Four variants, one per igniter tier (#84): common (green), uncommon (blue), rare (purple), legendary (orange) — the igniter's tier selects which variant fills the frame. All four share one near-white texture tinted client-side via BlockColors (`tintindex`), and everything that recognises "a portal block" uses the `orevault:vault_portals` block tag.
- Implements MC 26.1's `Portal` interface: entering starts the vanilla nether-style charge-up — 80 ticks (4 seconds) inside the portal with the wavy "confusion" screen overlay, giving players the chance to step back out — followed by a portal travel sound and the teleport
- `entityInside()` handles teleportation (players only)
- `updateShape()` breaks to air if a neighbouring block is neither Vault Frame nor portal block (whole-frame re-validation)
- Teleport cooldown: 80 ticks (4 seconds) using vanilla `portalCooldown`; Tier 3+ igniter holders skip the wait and the cooldown entirely (§3.3)

**No team gate.** FTB Teams auto-creates a single-member team for every player, so there is no such thing as a teamless player at runtime and nothing is gated on team membership. `getTeamForPlayerID` never returns empty on a live server; any check written against it is dead code. Solo players get a solo Vault, exactly as §2 describes.

**Teleportation Logic**
```
If player is in Ore Vault dimension:
    → retrieve saved return position from player persistent data
    → teleport to Overworld at saved position (fallback: world spawn)
Else:
    → save current Overworld position to player persistent data
    → find or create team's Vault dimension
    → ensure the team's return portal exists at the Vault anchor (§3.1) — a single-plane
      4×5 frame with a 2×3 interior, standing on the grass surface at X=0, Z=0
    → teleport to the Vault anchor (or the player's personal entry point, Tier 2+)
```

**The Vault side is fixed; only the Overworld side is remembered.** Every trip into a team's Vault lands on the same block — the team anchor at X=0, Z=0 — regardless of where in the Overworld the portal was. Vault arrival never mirrors, offsets, or otherwise derives from Overworld coordinates. Exactly one return portal exists per Vault, built at that anchor on first entry and idempotent thereafter, and it is tier-coloured to match the highest igniter tier that has opened the Vault (upgrading, never downgrading).

Return position is stored in `player.getPersistentData()` under key `orevault_return` as an NBT compound with x/y/z integers. This survives death and dimension changes. The saved spot is walked out along the approach direction past the last portal block, so returning never instantly re-triggers the portal.

> **Ore-block-look portals (investigation, #84):** the portal interior is a flat translucent plane (nether-portal style), so a literal ore-block appearance would need a full-cube model or a block-entity renderer — losing the flat translucency. Conclusion: keep the tinted plane; an "ore-look" would be done as a texture swap (ore-vein pattern on the plane), not a block-shaped portal.

> **Spawn safety (implementation note):** the open air layer (§3.1) means the team anchor needs no terrain carving — the player arrives standing on the grass surface in open air. Personal entry points (Tier 2+) scan upward from the stored block for a 2-block air pocket, carving one only if none is found nearby (e.g. the entry was set against a solid wall).

---

### 3.3 Vault Igniter Tiers

The Vault Igniter replaces Flint & Steel as the portal activation item. Four tiers, each crafted from the previous tier plus additional materials.

**Every tier grants a persistent capability, not a buff.** Earlier drafts gave the tiers short potion effects on arrival (Speed I for 5s, Haste I, Haste II); those were removed. They were cosmetic noise, and the Vault Fever and Miner's Constitution nodes already own the haste and hunger axes far more meaningfully — a 15-second Haste II on entry is worthless next to a node granting it permanently. The igniter is the player's *key*: what it carries is access, not stats.

| Tier | Name | Recipe | Capability |
|---|---|---|---|
| 1 | Crude Vault Igniter | Iron ingot + Redstone dust (shaped, horizontal) | Opens the portal. Nothing else. |
| 2 | Attuned Vault Igniter | Tier 1 + Gold ingot + 4 Resonance Crystals | Set and recall **one** personal entry point inside the Vault (right-click a block while holding the igniter) |
| 3 | Resonant Vault Igniter | Tier 2 + Diamond + 8 Resonance Crystals | Instant travel — skips the 4-second portal charge and the re-entry cooldown entirely; personal entry points raised to **3**, selectable as a waypoint list |
| 4 | Sovereign Vault Igniter | Tier 3 + Netherite ingot + 16 Resonance Crystals | Unlocks the Vault Reset button in the Tome UI; required (and returned) by the Vault Anchor recipe (§3.4) |

Personal entry points are stored per-player in persistent data. Without a Tier 4 igniter the reset button does not appear in the UI, and Vault Anchors cannot be crafted at all.

> **Resonance Crystals.** Not craftable from raw materials — the *only* source is Attuned ore, which drops from the Runic Attunement node (§6.1, Fortune branch). Recipe: **4 Attuned raw ore (any type) + 1 Amethyst Shard → 1 Resonance Crystal** (shapeless). This deliberately gates the igniter ladder behind a skill investment rather than a free recipe, and gives Runic Attunement a concrete purpose — it is the supply line for igniter upgrades and Vault Anchors. Total cost of a Tier 4 igniter is 28 Crystals = 112 Attuned ore, which at Runic Attunement Tier 3 (20% attune chance) is roughly 560 ore mined: a genuine but reachable investment.

---

### 3.4 Chunk Loading

**Forge Ticket System**
The mod registers Forge chunk loading tickets for chunks inside the Vault that contain an active Vault Anchor block. Tickets persist until the anchor is removed or the server restarts.

**Vault Anchor Block**
- Crafted from Vault Frame blocks + Resonance Crystal + a **Tier 4 Sovereign Vault Igniter**, which is *not consumed* — it is returned to the crafting grid as a crafting remainder, the same pattern vanilla uses for buckets and water bottles
- The igniter is therefore a **recipe gate, not a placement gate.** Gating placement instead would mean the block pops out of the world and back into the player's inventory when they lack the igniter, which reads as a bug rather than a rule. Gating the recipe means an anchor that exists can always be placed
- Placeable inside the Vault only
- Registers a chunk loading ticket for its chunk on placement
- Deregisters on removal
- Serves dual purpose: chunk loader and personal waypoint (right-click to set as a personal entry point with a Tier 2+ igniter)
- Maximum simultaneous tickets per team determined by Vault Presence skill node (see skill tree)
- Admin config can set a hard ceiling on max tickets regardless of node level

**Cross-Dimension Behaviour**
Blocks broken by machines in a ticket-loaded Vault chunk **never award Resonance**, in any quantity, under any node. This is a deliberate hard rule, not a balance value to tune: the level curve (§4.3) is calibrated against a player mining by hand, and a quarry in a 60%-ore dimension exceeds that by orders of magnitude. Allowing even a fraction would collapse a 100-hour progression into an evening on any tech pack.

What automation *does* get is the Automated Extraction node (§6.1), which increases machine **yield** inside the Vault and makes machine-broken ore count toward vein completion (§11) and player statistics. Automation is therefore worth building for materials, and worth nothing for progression — progression is always earned by hand.

**Vanilla XP is untouched.**
Ore broken in a Vault drops vanilla experience exactly as it would in the Overworld. Resonance is an addition, never a substitution: a player who mines here must not end up behind one who mined the same ore outside, or the dimension becomes a trap rather than a reward. Nothing in the mod calls or suppresses `popExperience`, and nothing should — the Resonance orb is a separate entity that pays the team pool, and the two systems do not interact.

The one place they touch deliberately is Vault Echo (§6.1), which grants vanilla XP equal to the Resonance awarded, and the Ore Quality branch (§6.1), which drops bonus vanilla XP. Both are *additions* on top of the vanilla amount, which is another reason the baseline must stay intact.

---

### 3.5 Dimension Reset

**Trigger:** Reset button in Tome of the Deep Seam UI (only visible if at least one team member holds a Tier 4 Sovereign Vault Igniter).

**Voting logic:**
- Count currently online team members
- If only one member is online: that player can reset unilaterally
- If two members are online: both must confirm
- If three or more members are online: majority (more than half) must confirm
- Offline members are not counted and cannot block the reset

**Reset process:**
1. Voting UI appears for all online team members
2. Once vote passes, a 10-second countdown is shown to anyone inside the Vault with a warning message
3. Any players still inside the Vault at countdown end are teleported to Overworld spawn
4. All chunk data for the team's Vault dimension is deleted
5. The dimension is re-registered fresh (same registry key, new generation state). If the team has purchased the **Vault Expansion** keystone, the dimension is re-created under `orevault:ore_vault_expanded` instead of the base type — this is the only moment the dimension type can change (§3.1)
6. Team skill tree progress, Resonance pool, Animus pool, and all skill point investments are fully preserved
7. **Free respec window:** for 10 minutes after a reset, node refunds cost no XP (§4.4). A fresh Vault is the natural moment to rebuild a mining strategy, and it gives the reset a second purpose beyond regenerating terrain
8. A server log entry records the reset with timestamp and team ID

**Backup option:** A checkbox in the reset confirmation UI labelled "Export chunk data before reset." If checked, the dimension's region files are copied to `world/orevault_backups/<teamId>/<timestamp>/` before deletion. This is purely a safety net and is not used by the mod itself after creation.

---

## 4. Resonance System

### 4.1 What Resonance Is

Resonance is a team-shared numerical counter stored in the team's SavedData. It is not a physical item and never appears in inventory. It is displayed only within the Tome of the Deep Seam UI. Players mine ores inside the Vault → Resonance accrues to the team pool → when the pool crosses a threshold the team gains a skill point → skill points are spent on Resonance tree nodes.

### 4.2 Resonance Gain

Resonance orbs are spawned as floating entities when an ore block is broken inside the Vault. They behave identically to XP orbs visually and mechanically — they float toward the nearest team member within range, are absorbed on contact, and add to the team's Resonance pool. Sound and particle effects match XP orbs. The Resonance Magnetism node increases collection radius.

**Base gain rates (configurable as multipliers only, not absolute values):**
| Ore Tier | Base Resonance |
|---|---|
| Common (iron, copper, coal) | 2 |
| Uncommon (gold, lapis, redstone) | 5 |
| Rare (diamond, emerald) | 10–15 |
| Stone (with Stone Memory node) | 0.5 (fractional, accumulates) |
| Vault Echo burst | 25–40 |

**Rarity classification** happens at every server start by scanning all registered ore blocks. Classification uses the block's world-gen placement data — specifically the vein count and height range from registered PlacedFeatures. Low count + low Y range = Rare. High count + wide Y range = Common. Everything else = Uncommon. The admin can override specific ore classifications in config.

**Tithe node modifier:** If Tithe is active, 25% of ore blocks mined are consumed (the block breaks but drops nothing) and the Resonance that would have come from that ore is multiplied by 1.75 and added to the pool. Stone Memory bonus drops and other secondary drops are not affected by Tithe.

**Team pool scaling.** The intent is that joining a team does not push progression ahead of playing solo — a team gets to mine together and share a tree, not to progress five times faster. The pool receives the sum of member gains, divided by team size, with a small coordination bonus:

```
teamPoolGain = sum(memberGains) / teamSize * (1 + 0.1 * (teamSize - 1))
```

| Team size | Rate vs. solo |
|---|---|
| 1 | 1.0× |
| 2 | 1.1× |
| 3 | 1.2× |
| 5 | 1.4× |

A five-person team progresses 40% faster than a solo player, not 500% faster. The earlier `1 + (teamSize - 1) * 0.7` formula was both ambiguous (it read as a multiplier applied *on top of* an already-summed pool, which would have made a team of five progress 19× as fast) and far too generous. Because the curve below is now calibrated for a solo player, the `assumedTeamSize` constant is no longer needed anywhere and has been removed.

### 4.3 Level Thresholds and Skill Points

**Levels and skill points are decoupled.** Earlier drafts set the level cap equal to the tree's total skill-point cost, awarding one point per level. With a 225-point Resonance tree that meant a 225-level track, and since the highest level requirement anywhere in §6 is 30, every gate in the entire mod opened inside the first hour of a 100-hour curve. The level requirements in §6 were authored for a ~30-level track; the track now matches them.

**Dynamic calculation (performed in code at startup, not configured manually):**

1. At startup, sum the skill point cost of every node at every tier in the tree. Call this `totalTreeCost`.
2. The level cap is a constant: `LEVEL_CAP = 30` for both trees.
3. Points awarded per level: `pointsPerLevel = ceil(totalTreeCost / LEVEL_CAP)`. Reaching level 30 therefore grants at least enough points to buy the whole tree. Adding or removing nodes automatically adjusts the award, not the cap.
4. From config, read `target_play_hours` (default: 100) and `curve_divisor` (default: 1.0) from the `[resonance]` block in §10. Both are read once at server start; changing either requires a restart, because moving a threshold mid-session would move it under teams that had already passed it.
5. Calculate average Resonance per hour for a **solo** player: `resonancePerHour = averageOresPerHour * weightedAverageResonancePerOre`. `averageOresPerHour` is a **constant in code**, not config — it measures how fast a player mines rather than expressing a preference, so an admin has no way to know the right value and a wrong one silently distorts the whole curve. The knob a server owner wants is `curve_divisor` in the next step.
6. Total Resonance needed: `totalResonance = resonancePerHour * targetPlayHoursResonance / curve_divisor`. Because the divisor is applied here, before the distribution in step 7, it scales every level threshold by the same factor: the 100:1 last-to-first ratio and the milestone spacing below are preserved exactly, and every level requirement in §6 keeps its intended pacing. This is the supported way to make Ore Vault a 40-hour mod rather than a 100-hour one, without touching node costs or the level cap.
7. Distribute `totalResonance` across the 30 levels using an exponential curve where the last level costs `LAST_TO_FIRST_RATIO` (100) times the first:
```
levelCost(n) = baseCost * (growthFactor ^ n)
growthFactor = LAST_TO_FIRST_RATIO ^ (1 / (LEVEL_CAP - 1))
```
`baseCost` is derived so the sum across all levels equals `totalResonance`, with the final level absorbing rounding drift.

8. This array of level costs is computed once at server start and stored.

**Resulting pacing** (100-hour target, 30 levels, ratio 100):

| Milestone | Level | ≈ Hours in |
|---|---|---|
| First nodes purchasable | 1–2 | < 1 |
| Mid-tree branches open | 8–10 | 3–5 |
| Vault Expansion keystone | 18 | ~15 |
| Tree fully purchasable | 30 | 100 |

**Skill point award:** When the team's cumulative Resonance crosses the threshold for the next level, the team automatically receives `pointsPerLevel` skill points. A toast notification appears for all online team members. The Resonance pool is not reset — it continues accumulating past each threshold.

### 4.4 Spending Skill Points

Skill points are spent in the Resonance tree tab of the Tome of the Deep Seam. Clicking an available node spends one skill point (or more for premium nodes — see node definitions) and unlocks that node immediately. Effects take place immediately. For nodes that affect chunk generation, newly generated chunks will reflect the node's effect; already-generated chunks are unchanged.

**Refund:** Node-by-node refund is available at a cost of Minecraft XP:

```
refundCost = REFUND_XP_PER_POINT * tierSkillPointCost      // REFUND_XP_PER_POINT = 3
```

A 1-point node costs 3 XP levels to refund; the 10-point Vault Expansion keystone costs 30. A full 225-point respec is ~675 levels — a serious commitment, but achievable, and always proportional to what is actually being undone.

The previous formula (`investedPoints / totalTreeCost * 50`) priced every node identically regardless of what it cost, which made a single early mistake nearly free and a late-game respec effectively impossible (~3,250 levels). Because several nodes are exclusive pairs and one-way forks (§6.1), permanent-feeling refunds would have turned every fork into a trap.

**Fork options are free both ways.** A `[FORK OPTION]` costs 0 skill points to pick, so it costs 0 XP
to unpick — a free choice that is expensive to reverse is a trap, and the fork is meant to be a
decision you can revisit as the build changes. Refunding a fork *parent* is priced normally and
clears the chosen option along with it.

**Anchors are never refunded.** They are not bought, hold no points, and unlock from points spent. An
anchor can therefore re-lock: refund enough of the tree and a cluster closes behind you. Nodes already
purchased inside a closed cluster keep working — the gate governs buying, not keeping — but nothing
further in it can be bought until the spend is back above the threshold.

**Free respec window:** refunds cost nothing for 10 minutes after a dimension reset (§3.5).

---

## 5. Animus System

The Animus system is a complete parallel to the Resonance system, driven by mob kills in Disturbed Zones rather than mining. It has its own currency (Animus), its own level track, its own skill point pool, and its own tree tab.

### 5.1 Animus Gain

Animus orbs drop from mobs killed inside a Disturbed Zone, behaving identically to XP orbs. They float toward the nearest team member, are absorbed on contact, and add to the team's Animus pool. Animus does not drop outside Disturbed Zones.

Animus gain rates scale with mob difficulty (to be defined per mob type in a config file or data-driven JSON). Suggested defaults:
| Mob Type | Base Animus |
|---|---|
| Common (zombie, skeleton, spider) | 3 |
| Uncommon (creeper, witch, enderman) | 8 |
| Rare (wither skeleton, blaze equivalent) | 20 |

### 5.2 Level Thresholds

Identical dynamic calculation to Resonance (§4.3), using:
- `totalAnimusTreeCost` (sum of all mob tree node costs)
- `targetPlayHoursAnimus` (default: 100, can differ from Resonance target)
- Average Animus per hour for a **solo** player, based on mob kill rates in a Disturbed Zone
- The same `LEVEL_CAP = 30` and `pointsPerLevel = ceil(totalAnimusTreeCost / 30)`
- The same team scaling formula from §4.2, applied to the Animus pool

The two level tracks (Resonance level and Animus level) are completely independent. A team could be Resonance level 15 and Animus level 3 if they mostly mine.

> **Section 5, Section 7, and the Animus half of Section 6 are deferred to the post-1.0 Animus epic.** They remain specified here so the design is not lost, but nothing in this half is in 1.0 scope. See §12.

### 5.3 Spending Animus Skill Points

Identical to Resonance: one point per level, spent in the Mob tree tab. Refund costs scale with total points invested in the Mob tree specifically, not the combined total.

---

## 6. Skill Trees — Complete Node Definitions

### Notation

- **Cost:** Skill points required to purchase this tier
- **Level req:** Minimum team Resonance or Animus level required to purchase this tier (cap: 30 — see §4.3)
- **Prereq:** Nodes that must be unlocked before this tier is available
- All costs and level requirements are defined as constants in a single `NodeCosts.java` file for easy adjustment

**Node classes.** The tree is built from four kinds of node, in deliberate imitation of the Path of Exile passive tree and the Diablo 4 skill paths:

| Marker | Meaning |
|---|---|
| *(unmarked)* | **Small node.** A tiered percentage bonus with no downside. These are the filler you path through — individually modest, collectively the bulk of the tree. |
| **[ANCHOR]** | The head of a cluster. **Not purchasable and free.** Unlocks once enough skill points have been spent in the tree, and gates everything in its cluster. |
| **[NOTABLE]** | A single-tier node granting a *distinct mechanic* rather than a bigger number. Always pure upside. |
| **[KEYSTONE]** | Build-defining, expensive, and **always carries a real downside**. A keystone should change how you mine, not just how fast. All keystones live in the Mastery cluster at the bottom of the tree. |
| **[PACT]** | A bargain struck early. Build-defining and always carrying a real downside, exactly like a keystone, but bought from an ordinary cluster instead of from behind the 100-point Mastery gate. Pacts exist because three of the tree's sharpest choices are only interesting while they are cheap: Greedy Seams undercutting Ore Doubling is a decision at 4 points and nothing at all at 100. Keystones stay bottom-only; pacts are how an early node gets to be dangerous. |
| **[GROWTH]** | Strong immediately and worthless later **by construction** — the bonus is a flat number or a hard level cutoff, so rising throughput retires it. The point stays spent. That is the price of an easier start, and refunding it on retirement would make all five free picks. The Tome greys the node and labels it **Outgrown** once its condition can no longer be met. |
| **[FORK PARENT: name]** | A tiered node that costs points and **does nothing until specialized**. Its tiers act through whichever option is chosen. |
| **[FORK OPTION: name]** | **Costs 0 points.** Requires its parent at tier 1 and decides what the parent's tiers do. One option at a time; siblings lock until refunded, refunding is free, and swapping is allowed **only while outside the Vault.** |
| **[TRADEOFF]** | Toggleable on and off at no cost, **but only while outside the Vault.** |
| **[EXCLUSIVE: other]** | Cannot be held at the same time as the named node. Unlike a fork, both sides are paid for. |
| **[ULTIMINE ONLY]** | Only appears if FTB Ultimine is loaded. Hidden entirely otherwise, never shown as locked. |

> **Why tradeoffs and fork options can only be changed outside the Vault.** Previously they could be flipped at any moment, which meant no commitment: a player would enable Tithe while mining ore and disable it before touching stone, taking every upside and paying no cost. Requiring the toggle to happen outside makes a tradeoff a loadout you commit to before you delve, which is the whole point of the mechanic, while keeping the freedom to change strategy between trips.

---

### 6.1 Resonance Tree

#### Shape of the tree

The tree is a **run of clusters**, not a grid of branches. Each cluster is a named stage of the
miner's craft, and each one is built around an **anchor**: a node you cannot buy, which unlocks once
you have spent enough skill points anywhere in the tree. The anchor sits at the middle of its
cluster and the cluster's nodes **radiate outward from it**, on rings. The anchors themselves run
down a single vertical spine, in the order below.

Distance from the anchor means one thing: how many prerequisites deep inside that cluster the node
is. Nodes with nothing to wait for sit on the first ring around the anchor; a node that requires one
of those sits on the second, and so on. The grid this replaces put unrelated nodes directly above
one another — Gravel Purge sat under Common Ore Boost while both were available from the start — so
the layout implied a prerequisite chain that did not exist.

| Cluster | Anchor unlocks at | What it is |
|---|---|---|
| **Prospecting** | 0 points | Reading the rock. Open from the first point. |
| **Excavation** | 10 points | Moving rock in bulk, and choosing what shape it comes in. |
| **Assay** | 25 points | Telling one ore from another; what the stone remembers. |
| **Metallurgy** | 45 points | Getting more out of each ore than the ore contains. |
| **Claim** | 55 points | The ground is yours. Holding it, reaching into it, being kept by it. |
| **Deep Lore** | 70 points | The Vault answering back, and what it gives you for finishing what you started. |
| **Broad Cut** | 25 points | Wide-swing mining. Absent entirely without FTB Ultimine. |
| **Mastery** | 100 points | Keystones only, at the bottom of the tree. |

**Claim is the half of Deep Lore that was about infrastructure rather than echoes.** Chunk tickets,
automation, navigation and the Vault's death-side interventions were sitting alongside burst
mechanics under one anchor, which made Deep Lore mean two things and pushed the utility a team wants
early behind the same 70-point gate as Vault Echo's payoff. Split out at 55, the ladder reads: find
it, dig it, identify it, work it, hold it, listen to it, master it.

**Anchors are not purchasable and cost nothing.** They gate, they name the stage, and they are what
the eye follows down the tree. The gate is *points spent in this tree*, not team level: a team that
has ground to level 25 without committing to anything has not earned Mastery, and a team that has
spent 100 points has, whatever their level says. Level requirements still apply per node, as before.

Gate values are stated in points and are pinned to a fraction of the tree's total cost rather than to
absolute numbers, because that total moves whenever nodes are added: 0% / 4% / 11% / 11% / 20% / 25% /
31% / 45% for Prospecting, Excavation, Assay, Broad Cut, Metallurgy, Claim, Deep Lore and Mastery.
`NodeCosts` derives the point values from `NodeDefs.totalTreeCost(RESONANCE)` so the two cannot drift,
and a test pins the derived numbers.

#### Forks

A fork is now **one paid node plus free specializations**, not a row of rival nodes:

- The **fork parent** is an ordinary tiered node. It costs points and has **no effect at all** until
  specialized. Buying it is committing to the mechanic; it is not yet committing to a flavour of it.
- Each **fork option** costs **0 points**, requires the parent at tier 1, and decides what the
  parent's tiers actually do. Exactly one option may be active; the siblings lock until refunded.
- Refunding a fork option is **free** — it cost nothing, so unpicking it costs nothing. Refunding the
  parent follows the normal §4.4 XP price and clears the option with it.

**Tiering is not lost — it moved.** Every fork that was tiered is still tiered to exactly the same
depth; the tiers now sit on the parent and the option says what each one does. Common Focus was
T1/T2/T3 at +25/50/80%, and it still is: Ore Attunement is a three-tier node and Common Focus is a
table keyed by which of those three tiers you have bought. The number of decisions is unchanged, the
number of paid steps is unchanged, and the per-tier effects are the original values moved verbatim.
What changed is that you buy the tiers once instead of once per rival branch.

**An option must cover every tier its parent has.** A parent tier with nothing behind it in the
chosen option is a paid step that does nothing — Hoarder's Instinct had exactly that gap when the
forks were converted and gained a third tier to close it. The only sanctioned exception is a tier
range the option itself gates, like Ore Working's Mekanism tiers, which are hidden rather than empty.

**Swapping an option is free, but only outside the Vault.** Same rule as tradeoffs (§6 notation), for
the same reason: inside, a player would re-specialize per vein — Rare Focus for the deepslate layer,
Common Focus on the way back up — and take every upside of a choice they never actually made. Outside,
the fork is a loadout you commit to before you delve and can rethink between trips. The restriction is
on *changing* an option, not on holding one; nothing about your build stops working when you enter.

This replaces three sequential Ore Boost nodes with one Ore Attunement node and three choices, which
is what the fork was always supposed to be: the old chain stacked, so everyone bought all three in
the same order and nothing was ever decided.

#### What the code still has to catch up on

`NodeDefs.java` predates this section and is the authority for nothing. Reconciling it is [87] (#103)
with the fork mechanic in [86] (#102). Precisely:

| In code | Action |
|---|---|
| `common_ore_boost`, `uncommon_ore_boost`, `rare_ore_boost` | **Remove.** Replaced by `ore_attunement` plus the three free Focus options. |
| `ore_sense` | **Rename** to `vein_fortune`. The name "Ore Sense" now belongs to Prospector's Eye. |
| `motherlode` | **Remove.** Replaced by `vein_singularity`, a Vein Shape option. |
| `disturbed_zone_unlock` | **Remove.** Deferred to the post-1.0 Animus epic (#90). |
| `efficient_miner` | **Rename** to `miners_constitution`, and gain the max-health effects on tiers 4 and 5. The Hunger branch is deleted; the node lives in Prospecting with the rest of the survival line. |
| `ultimine_gambit` | Keep; display name is "Volatile Veins: Ultimine Gambit". |
| *(new)* | `vein_shaping`, `ore_working`, `resonant_draw` — the three fork parents authored here. |
| *(new)* | The eight cluster anchors, which are data, not purchasable nodes. |
| *(new)* | The `[PACT]` and `[GROWTH]` node classes, and a `nodeClass` field on `NodeDef` to carry them — `tradeoff`, `exclusiveWith` and `ultimineOnly` are booleans bolted on one at a time and do not extend to eight classes. |
| *(new)* | The 30 nodes authored in this section: five growth, five breakpoint, five vein-completion, five exploration, six survival, plus Molten Seam, Vein Sight, Deep Habit and the three new Mastery keystones. |

Unlocked tiers persist keyed by node id in `OreVaultTeamData`, so **every removal and rename above is
a save-data change** and needs a migration that reads the old id, not just a new constant. A team that
bought Ore Sense to tier 3 must find Vein Fortune at tier 3, a team that bought Efficient Miner to
tier 5 must find Miner's Constitution at tier 5, and a team that bought the three Ore Boosts must be
refunded the points rather than silently losing them.

The migration runs once, on load, keyed off a data version. Renames copy the tier across verbatim.
Removals sum the tiers actually paid for and hand the points back to the team's unspent pool, which
is the only honest outcome — a removed node cannot be re-bought and the points were really spent.
`motherlode` is the one removal with a successor: it maps to nothing, because Vein Singularity is a
free fork option and awarding it for free to teams that had Motherlode would hand them a Vein Shaping
parent they never paid for.

#### Reading a node

Every node in this section carries its class in its tag line — `[NOTABLE]`, `[KEYSTONE]`,
`[FORK PARENT: name]`, `[FORK OPTION: name]`, `[TRADEOFF]`, `[EXCLUSIVE: other]`, `[ULTIMINE ONLY]` —
and the Tome draws each class distinctly (§8). An unmarked node is a small node.

#### CLUSTER: Prospecting

> Reading the rock, and surviving long enough to keep reading it. Nothing here requires a decision. Open from the first skill point.

> Prospecting is deliberately the widest cluster: it is the only one open from zero, and it holds all five `[GROWTH]` nodes, which retire out of relevance as the team advances. Its visual weight drops as you progress even though its node count does not.

**Vein Expansion**
> Increases the size of ore veins generated in newly explored Vault chunks.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +15% vein size | 1 | 0 | None |
| 2 | +30% vein size | 1 | 2 | Tier 1 |
| 3 | +50% vein size | 2 | 4 | Tier 2 |
| 4 | +75% vein size | 2 | 7 | Tier 3 |
| 5 | +100% vein size | 3 | 10 | Tier 4 |

---

**Stone Memory**
> Stone and deepslate drop XP and provide additional benefits at higher tiers when mined inside the Vault.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Stone drops 1 XP when mined | 1 | 0 | None |
| 2 | +2 XP; stone occasionally drops flint | 1 | 3 | Tier 1 |
| 3 | +3 XP; deepslate drops a small amount of Resonance | 2 | 6 | Tier 2 |
| 4 | +4 XP; small chance stone drops a random common ore nugget | 2 | 10 | Tier 3 |
| 5 | +5 XP; rare chance stone triggers a Resonance burst equal to Vault Echo T3 | 3 | 14 | Tier 4 |

---

**Gravel Purge**
> Removes gravel and clay from Vault generation in newly explored chunks.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | No gravel or clay generates | 1 | 1 | None |

---

**Miner's Constitution**
> Reduces hunger drain inside the Vault, and at higher tiers toughens the miner. *(Renamed from "Efficient Miner". The tree no longer has a Hunger branch — hunger is one line inside the Prospecting survival group, not a category of its own.)*

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | −20% hunger drain | 1 | 0 | None |
| 2 | −45% hunger drain; food restores 20% more saturation inside the Vault | 1 | 3 | Tier 1 |
| 3 | −65% hunger drain; eating grants brief Regeneration I | 2 | 6 | Tier 2 |
| 4 | −85% hunger drain; no starvation damage; **+2 max hearts inside the Vault** | 2 | 10 | Tier 3 |
| 5 | Hunger and saturation frozen completely; eating still grants the Regeneration I from Tier 3; **+4 max hearts inside the Vault** | 3 | 15 | Tier 4 |

> The bonus hearts are an attribute modifier applied on entering the Vault and removed on leaving, so they never follow a player into the overworld. Current health is clamped, not scaled, on removal.

---

**Sure Footing**
> The Vault's floor is a long way down and its ceiling is 69 blocks of open air. This is the node that stops that mattering.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Fall damage taken inside the Vault is halved | 1 | 2 | None |
| 2 | No fall damage inside the Vault | 2 | 6 | Tier 1 |

---

**Deep Breath**
> Below Y=32 the Vault stops trying to drown or burn you.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Immune to drowning below Y=32 inside the Vault | 2 | 12 | Miner's Constitution T3 |
| 2 | Also immune to lava and fire damage below Y=32 inside the Vault | 4 | 18 | Tier 1 |

> Tier 2 is the counterweight to **Molten Seam**: a team that turns lava generation on has a way to stop it being a death sentence, but has to pay 6 points across two tiers for it.

---

**Novice's Luck** `[GROWTH]`
> The Vault is kind to people who do not know what they are doing yet.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | `(30 − teamLevel)%` bonus ore drops, floor 0 | 1 | 0 | None |

> Enormous at level 2, exactly zero at level 30. Cannot be a late-game problem because the ceiling is the level cap itself.

---

**Apprentice's Ledger** `[GROWTH]`
> The first ore of a trip is worth writing down. The hundredth is not.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | The first 100 ore blocks of each Vault trip pay double Resonance | 1 | 0 | None |

> A flat cap against rising throughput: transformative when a trip yields 150 ore, a rounding error when it yields 3,000.

---

**Shallow Grace** `[GROWTH]`
> While you have never been deep, the shallows pay well.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | +50% Resonance from all ore while your all-time deepest recorded Y is above 100 | 1 | 0 | None |

> Reads `deepestY` from `PlayerStats`, which is a lifetime record and never decreases. The first real delve ends this node permanently, and no refund or reset brings it back. That is the point.

---

**Guide Vein** `[GROWTH]`
> A leader vein: a small, obvious deposit the Vault leaves out where a beginner will trip over it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | One guaranteed copper or iron vein generates per newly explored chunk while team level < 15 | 1 | 0 | None |

> Exists so an unlucky first hour does not read as a broken mod. Applies at generation, so chunks generated before the cutoff keep their guide vein afterwards.

---

**Salvager's Eye** `[GROWTH]`
> Early on, even the waste rock has something in it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | 5% of stone blocks broken in the Vault drop a raw common ore while team level < 12 | 1 | 1 | None |

> Makes the earliest and least productive mining pay something. Subject to the §3.4 rule: machine-broken stone never rolls this.

---

#### CLUSTER: Excavation

> Moving rock in bulk, and deciding what shape it comes in. **Anchor unlocks at 10 skill points spent anywhere in the Resonance tree.**

**Vein Proliferation**
> Increases the number of ore veins generated per chunk.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +20% vein count | 1 | 2 | Vein Expansion T1 |
| 2 | +40% vein count | 1 | 4 | Tier 1 |
| 3 | +65% vein count | 2 | 7 | Tier 2 |
| 4 | +90% vein count | 2 | 10 | Tier 3 |
| 5 | +120% vein count | 3 | 14 | Tier 4 |

> **Note:** Vein count is capped such that stone content never drops below 40% of generated blocks, regardless of combined Vein Expansion + Vein Proliferation investment.

---

**Deep Veins**
> Shifts ore generation weighting toward lower Y levels.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Moderate shift toward lower Y | 2 | 5 | Vein Proliferation T2 |
| 2 | Strong shift; the lowest 30 blocks above bedrock have 2× standard density | 3 | 9 | Tier 1 |

---

**Stone Reduction**
> Replaces a portion of filler stone with ore-bearing rock, increasing ore surface area.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 10% of filler stone replaced with ore-adjacent stone | 1 | 3 | Gravel Purge |
| 2 | 20% replaced | 2 | 7 | Tier 1 |

---

**Vein Shaping** `[FORK PARENT: Vein Shape]`
> Decides the shape ore takes in newly generated chunks. **Inert until specialized:** pick one of the three Vein Shape options below and every tier of this node applies through it. All three shapes are subject to the 40% stone floor, and all three apply *after* Vein Expansion and Vein Proliferation, overriding the resulting count and size.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Shaping applies at its listed strength | 2 | 4 | Vein Proliferation T2 |
| 2 | Shaping applies at double strength | 3 | 8 | Tier 1 |

---

**Abundance** `[FORK OPTION: Vein Shape]`
> Many small deposits scattered throughout. Reliable, steady, never a dry chunk.

**Costs 0 skill points.** Requires Vein Shaping tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Vein Shaping tier | Effect through this option |
|---|---|
| 1 | +50% vein count, −20% vein size |
| 2 | +100% vein count, −20% vein size |

---

**Vein Singularity** `[FORK OPTION: Vein Shape]`
> All ore in a chunk is concentrated into 1–3 enormous deposits. Finding one is a jackpot; many chunks have nothing at all. *(Replaces the earlier "Motherlode" node, which was mechanically identical to Vein Expansion under a different name. It carried a `[KEYSTONE]` tag while forks were paid nodes; as a free option under Vein Shaping it is a shape choice like its two siblings, and keystones now live only in Mastery.)*

**Costs 0 skill points.** Requires Vein Shaping tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Vein Shaping tier | Effect through this option |
|---|---|
| 1 | Chunk ore consolidated into 1–3 veins; total ore volume unchanged |
| 2 | Consolidation intensifies; +25% total ore volume, ~40% of chunks generate no ore at all |

---

**Stratified** `[FORK OPTION: Vein Shape]`
> Ore generates in flat horizontal bands sorted by rarity instead of scattered blobs. Rare ore always sits at a known depth. Rewards planned strip-mining over wandering.

**Costs 0 skill points.** Requires Vein Shaping tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Vein Shaping tier | Effect through this option |
|---|---|
| 1 | Ore generates in rarity-sorted layers; band positions shown in the Tome |
| 2 | Bands thicken and purify — each band is near-single-ore |

---

**Deep Habit**
> The longer a single trip runs, the more the Vault gives up. Rewards committing to a delve instead of hearthing out every ten minutes.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Every 1,000 blocks broken in a single trip grants +5% Resonance for the rest of that trip, to +25% | 2 | 7 | Vein Proliferation T1 |

> Resets to zero on leaving the Vault, so it cannot compound across sessions. Counts only player-broken blocks (§3.4).

---

**Long Delve**
> Time under the rock, rather than rock broken. Pays in comfort rather than yield.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Every 20 unbroken minutes inside the Vault grants a Delve stack, to 2. Each stack: +10% mining speed, −25% hunger drain | 2 | 7 | Deep Habit T1 |
| 2 | Stack cap raised to 4 | 3 | 12 | Tier 1 |

> **AFK guard:** a stack only accrues while you have broken a block in the last 60 seconds. Without it this node pays players for standing still, which is the opposite of what it is for. Stacks are lost on leaving the Vault.

---

**Volatile Veins** `[TRADEOFF]`
> Increases vein size by 25%, but each ore broken has a small chance of causing the remaining connected vein to vanish instantly, replaced with air, with no drops. A pity counter prevents more than three consecutive triggers; after three triggers the next several ore breaks are guaranteed safe. The pity counter resets on logout.
> When using FTB Ultimine, the disappearance roll occurs once per Ultimine operation rather than per block.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25% vein size, 1–3% disappearance chance (balance TBD) | 2 | 6 | Vein Expansion T2 |

---

**Molten Seam** `[TRADEOFF]`
> Lava pools generate in the Vault's stone band, each one ringed by a two-block shell of netherrack so you always see one coming before you break into it. Ore mined near the heat comes out already smelted.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Lava pools generate in newly explored chunks, netherrack-shelled. Ore mined within 8 blocks of lava drops smelted and pays +25% Resonance | 2 | 8 | Deep Veins T1 |

> **This tradeoff is not fully reversible and the tooltip must say so.** It changes generation, so switching it off stops new pools appearing but leaves every pool already generated exactly where it is. The netherrack shell is a hard requirement, not decoration: the whole reason a lava node is acceptable is that you never break the last block into an unseen pool.
>
> The smelted drop stacks with **Smelter's Intuition** by taking the better of the two rolls, not by rolling twice.

---

**Greedy Seams** `[PACT]` `[EXCLUSIVE: Resonant Overload]`
> Take the material now and pay for it in progress. Ore yields double; the Vault gives back only half the Resonance.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | +100% ore drops, −50% Resonance from ore | 4 | 6 | Vein Expansion T1 |

---

**Resonant Overload** `[PACT]` `[EXCLUSIVE: Greedy Seams]`
> The mirror image. Progress at double speed and take home half the material.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | +100% Resonance from ore, −50% ore drops | 4 | 6 | Vein Expansion T1 |

> Together these two form the tree's central axis: **materials now** versus **progression now**, and you must pick a side or neither. Greedy Seams at 4 points deliberately undercuts Ore Doubling T3 (a Metallurgy fork option behind an 8-point parent) on raw yield — buying it early is a real option that costs you the pace of the whole tree. That early-buy option is why both are `[PACT]` rather than `[KEYSTONE]`: behind the 100-point Mastery gate it could not exist.

---

#### CLUSTER: Assay

> Telling one ore from another, and knowing what the stone remembers. **Anchor unlocks at 25 skill points spent anywhere in the Resonance tree.**

**Ore Attunement** `[FORK PARENT: Ore Focus]`
> Decides which rarity band the Vault favours. **Inert until specialized:** pick Common, Uncommon or Rare Focus below and every tier of this node applies through it. Exactly one Focus at a time — the others lock until refunded, or until **Full Spectrum** lifts the restriction entirely.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Focus applies at its listed strength | 1 | 3 | None |
| 2 | Focus applies at its second-tier strength | 2 | 6 | Tier 1 |
| 3 | Focus applies at its third-tier strength | 2 | 10 | Tier 2 |

---

**Common Focus** `[FORK OPTION: Ore Focus]`

**Costs 0 skill points.** Requires Ore Attunement tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Ore Attunement tier | Effect through this option |
|---|---|
| 1 | +25% common ore vein count |
| 2 | +50% common ore vein count |
| 3 | +80% common ore vein count |

---

**Uncommon Focus** `[FORK OPTION: Ore Focus]`

**Costs 0 skill points.** Requires Ore Attunement tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Ore Attunement tier | Effect through this option |
|---|---|
| 1 | +25% uncommon ore vein count |
| 2 | +50% uncommon ore vein count |
| 3 | +80% uncommon ore vein count |

---

**Rare Focus** `[FORK OPTION: Ore Focus]`

**Costs 0 skill points.** Requires Ore Attunement tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Ore Attunement tier | Effect through this option |
|---|---|
| 1 | +30% rare ore vein count |
| 2 | +60% rare ore vein count |
| 3 | +100% rare ore vein count |

---

**Geode Clusters**
> Amethyst geodes generate inside the Vault at higher frequency than the overworld.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Moderate geode frequency | 1 | 4 | Stone Reduction T1 |
| 2 | High geode frequency | 2 | 8 | Tier 1 |

---

**Ancient Traces**
> Ancient debris generates in the Vault — but only in the deepslate band below Y=0, and the Vault refuses to multiply it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Sparse ancient debris below Y=0 | 5 | 21 | Rare Focus T2 *or* Full Spectrum, **and** Vault Expansion |
| 2 | Roughly doubled ancient debris frequency below Y=0 | 5 | 26 | Tier 1 |

> **Balance rules — ancient debris is exempt from everything.** It is not affected by Vein Expansion, Vein Proliferation, the Vein Shape fork, Ore Focus, Vein Fortune, Ore Doubling, Smelter's Intuition, Greedy Seams, Twin Veins, Kindred Rock, or Molten Seam's smelted drop. It generates at a fixed rate and drops exactly one. This is deliberate: the node exists so a team never *has* to go back to the Nether, not so the Vault becomes a netherite farm. Requiring Vault Expansion first also means it arrives at roughly the 20-hour mark rather than early.

---

**Ancient Knowledge**
> Ore blocks drop bonus vanilla XP orbs in addition to standard amounts.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +1 XP per ore mined | 1 | 2 | Stone Memory T1 |
| 2 | +2 XP per ore mined | 1 | 5 | Tier 1 |
| 3 | +4 XP per ore mined | 2 | 9 | Tier 2 |

---

**Stonecutter's Patience**
> Every block of waste rock you have ever broken is worth a little more XP on the ore that follows it. Rewards tunnelling rather than only ore-hunting.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +1 XP per ore for every 10,000 natural stone broken in the Vault, to a maximum of +3 | 2 | 6 | Stone Memory T2 |
| 2 | Maximum raised to +5 | 3 | 11 | Tier 1 |

> **What counts as stone** is the `#orevault:vault_stone` block tag, which the mod ships containing `#minecraft:base_stone_overworld` (stone, granite, diorite, andesite, tuff, deepslate) and `#c:stones`. Modded stone that follows either convention counts with no per-mod work, and a pack can extend the tag in a datapack for anything that does not. `PlayerStats#stoneBroken` counts against the same tag, so a team's existing total carries forward.

---

**Calloused Hands**
> Lifetime blocks broken, on a curve that never stops rising and never runs away. Slow enough that it is a decade-long drip rather than a build.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +Resonance equal to `0.03 × ln(1 + blocks / 10000)` | 2 | 6 | Stone Memory T2 |
| 2 | Coefficient raised to `0.05` | 3 | 12 | Tier 1 |
| 3 | Coefficient raised to `0.07` | 4 | 18 | Tier 2 |

> Reads `PlayerStats#totalBlocksBroken`, which is lifetime and never resets. Hard-capped at +40%, which the curve reaches at roughly 10.8 million blocks and therefore never in practice — the cap exists so no future coefficient change can produce an unbounded multiplier.
>
> | Blocks | T1 | T2 | T3 |
> |---|---|---|---|
> | 10,000 | +2.1% | +3.5% | +4.9% |
> | 100,000 | +7.2% | +12.0% | +16.8% |
> | 500,000 | +11.8% | +19.6% | +27.5% |
>
> A 100-hour progression lands somewhere between 300,000 and 600,000 blocks, so tier 3 finishes near +25%. The linear form originally proposed (`blocks × 0.0001` as a multiplier) reaches ×30 over the same span; read as a percentage instead it is the right scale, and the logarithm removes the need for a cap to do the work.

---

**Vein Sight** `[NOTABLE]`
> Breaking the first block of a vein outlines the rest of *that* vein through solid stone. You can see what you have started.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | On breaking a vein's first block, the remainder of that vein is outlined for 30 seconds | 2 | 5 | None |

> Distinct from **Prospector's Eye**, which outlines *other* veins nearby. This one outlines the one you are standing in.
>
> This node is load-bearing for the whole Deep Lore completion group. Without it, finishing a vein is something that happens to you and Vault Echo, Vein Discipline, Last Ore, Clean Cut and Chapter's End all pay out for luck. With it, completion becomes a thing you choose to do. It sits in Assay rather than with the nodes it enables so that it arrives first.

---

**Stonecaller** `[NOTABLE]`
> The Vault's stone remembers what grows near it. Stone mined inside the Vault has a chance to convert into the ore type of the nearest vein within 8 blocks. Standing in rich rock makes even the filler pay.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Stone has a 3% chance to drop the ore type of the nearest vein within 8 blocks | 3 | 12 | Stone Memory T4 |

> Uses the vein index (§11) for the nearest-vein lookup — no flood fill, no scan. If no indexed vein is within range the roll is skipped, so this is worthless in stripped-out areas and best in fresh chunks, which is the intended pull.

---

**Prospector's Eye** `[NOTABLE]`
> Breaking the first block of a vein briefly outlines every other vein within 16 blocks through solid stone. Turns exploratory mining into reading the rock.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | On first block of a vein: nearby veins outlined for 5 seconds, 16-block radius | 3 | 8 | Vein Fortune T1 |

---

**Stone Curse** `[TRADEOFF]`
> Stone Memory XP gain is tripled, but stone drops no items (no cobblestone, no flint, no nuggets).

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 3× Stone Memory XP, stone drops nothing | 2 | 4 | Stone Memory T2 |

---

#### CLUSTER: Metallurgy

> Getting more out of each ore than the ore contains. **Anchor unlocks at 45 skill points spent anywhere in the Resonance tree.**

**Vein Fortune**
> Grants a passive Fortune effect to all ore mining inside the Vault. Stacks additively with tool enchantments. *(Renamed from "Ore Sense", which described a sensing mechanic it never had — that name now belongs to Prospector's Eye above.)*

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Fortune I equivalent | 2 | 5 | Vein Proliferation T1 |
| 2 | Fortune II equivalent | 3 | 9 | Tier 1 |
| 3 | Fortune III equivalent | 4 | 13 | Tier 2 |

---

**Ore Working** `[FORK PARENT: Yield]`
> Squeezes more out of each ore block than it contains. **Inert until specialized:** pick Ore Doubling or Smelter's Intuition below and every tier of this node applies through it. Vein Fortune is separate and feeds both.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Working applies at its listed strength | 2 | 8 | Vein Fortune T1 |
| 2 | Working applies at its second-tier strength | 2 | 12 | Tier 1 |
| 3 | Working applies at its third-tier strength | 3 | 16 | Tier 2 |
| 4 | *(Ore Doubling + Mekanism only)* | 7 | 20 | Tier 3 |
| 5 | *(Ore Doubling + Mekanism only)* | 10 | 25 | Tier 4 |
| 6 | *(Ore Doubling + Mekanism only)* | 15 | 30 | Tier 5 |

> Tiers 4–6 exist only when Ore Doubling is the chosen option **and** Mekanism is loaded; they are
> hidden otherwise, not shown as locked. Smelter's Intuition tops out at tier 3, so a team on that
> option sees a three-tier node. Costs above are the ones the Mekanism tiers previously carried on
> Ore Doubling itself, moved here with the rest of the paid tiers.

---

**Ore Doubling** `[FORK OPTION: Yield]`
> Raw multiplication. Fallback order: Mekanism processing output → mod ore dust (`c:dusts/<ore>`) → extra raw ore.

**Costs 0 skill points.** Requires Ore Working tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Ore Working tier | Effect through this option |
|---|---|
| 1 | +25% average ore yield |
| 2 | +50% average ore yield |
| 3 | Guaranteed 2× ore yield |

> **Mekanism tiers.** Ore Working extends to tiers 4–6 only while this option is chosen and Mekanism
> is loaded:

| Ore Working tier | Effect through this option |
|---|---|
| 4 | Drops Clumps (3×) |
| 5 | Drops Shards (4×) |
| 6 | Drops Crystals (5×) |

---

**Smelter's Intuition** `[FORK OPTION: Yield]`
> No extra material, but what you get needs no furnace. A chance that ore blocks drop the already-smelted result rather than raw ore.

**Costs 0 skill points.** Requires Ore Working tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Ore Working tier | Effect through this option |
|---|---|
| 1 | 15% chance of smelted drop |
| 2 | 40% chance |
| 3 | 75% chance |

---

**Runic Attunement**
> Ore drops have a chance to come out **Attuned** — resonance-charged raw ore. Attuned ore is the only source of Resonance Crystals (§3.3), which every Vault Igniter upgrade and every Vault Anchor requires.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 5% chance ore drops are Attuned | 3 | 10 | Any Yield fork T1 |
| 2 | 12% chance | 3 | 14 | Tier 1 |
| 3 | 20% chance | 4 | 17 | Tier 2 |

> Attuned ore is an ordinary raw-ore stack carrying a data component. It smelts, stacks, and processes exactly like the unattuned version, so nothing breaks if a player feeds it into a machine — they simply lose the crystal. Recipe: 4 Attuned raw ore (any type) + 1 Amethyst Shard → 1 Resonance Crystal. *(The previous version of this node granted "bonus effects when processed by magic-based ore processing mods", naming no mod and defining no mechanism — 10 skill points that did nothing.)*

---

**Kindred Rock**
> Mine enough of one thing and the Vault starts handing it to you. Every ore type is tracked separately and there is no limit on how many you can attune to.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Mining 1,000 of any single ore type permanently grants +15% Resonance from that type, for that player | 3 | 10 | Vein Fortune T1 |
| 2 | Attuned types also break 20% faster | 3 | 15 | Tier 1 |

> Reads the existing `PlayerStats#oresMined` map, keyed by block id, so it works with any modded ore in a kitchen-sink pack with no per-mod configuration and no ore list to maintain. Ancient debris is exempt (see Ancient Traces).
>
> Per-player rather than per-team on purpose: it is a record of what *you* have mined, and making it team-wide would let one member's grind hand the bonus to everyone.

---

**Highwater Mark**
> The tool in your hand remembers how deep you have been. Ore you mine repairs it, and the deeper your record, the more it repairs.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Each ore mined repairs the held tool by 1 durability, plus 1 more for every 16 blocks your all-time deepest Y sits below Y=64 — up to 9 per ore at expanded bedrock | 2 | 9 | Stone Memory T3 |
| 2 | When the held tool needs no repair, the same amount is applied to worn armour, split across damaged pieces | 3 | 14 | Tier 1 |

> Reads `PlayerStats#deepestY`, which is lifetime and never decreases, so the bonus is earned once by delving and kept forever. Repair is capped at the item's maximum durability, never applies to items that cannot be damaged, and unlike Mending consumes no XP — the two stack.
>
> Replaces an earlier version that paid +1% ore per 8 blocks of depth record. That topped out near +8%, which is not worth a node; durability is the thing a miner actually runs out of.

---

**Brittle Stone** `[PACT]`
> The Vault's stone gives way at a touch — and so do its veins. Everything breaks instantly, but ore has a habit of crumbling to nothing.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | All blocks in the Vault break instantly; each ore block has a 10% chance to shatter with no drops, and each ore break destroys one adjacent ore block with no drops | 5 | 13 | Vein Fortune T2 |

> The adjacent-ore collateral is what makes instant breaking a real bargain rather than a straight upgrade: with it, a big vein loses a large fraction of itself to the speed. It also interacts badly with Vein Discipline on purpose — the collateral counts as breaking the vein, so the two together are a build that wants deliberate handling.

---

**Vault Fever** `[TRADEOFF]`
> Grants permanent Haste II inside the Vault. You mine faster than you can listen — the Vault yields less Resonance for every ore taken.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Haste II inside the Vault, −25% Resonance from ore | 2 | 7 | Miner's Constitution T2 |

> The cost used to be +50% hunger drain, which Miner's Constitution Tier 5 (hunger frozen entirely) cancelled outright — the two together gave permanent free Haste II. Pricing Fever in Resonance instead makes the two nodes independent, and leaves Miner's Constitution as a clean quality-of-life ladder with no hidden interaction.

---

#### CLUSTER: Claim

> The ground is yours now. Holding it, reaching into it from outside, and being kept by it when things go wrong. **Anchor unlocks at 55 skill points spent anywhere in the Resonance tree.**

> This cluster is the half of the old Deep Lore that was about infrastructure rather than echoes. Splitting it out puts chunk tickets, automation and navigation before the burst mechanics rather than alongside them, which is the order a team actually wants them in, and leaves Deep Lore to mean one thing.

**Vault Presence**
> Increases the number of chunk-loading tickets the team's Vault can maintain simultaneously.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +4 simultaneous loaded chunks | 2 | 5 | None |
| 2 | +8 simultaneous loaded chunks (12 total) | 2 | 9 | Tier 1 |
| 3 | +16 simultaneous loaded chunks (28 total) | 3 | 14 | Tier 2 |

> Admin config can set a hard ceiling on maximum loaded chunks regardless of node level. Default ceiling: 32.

---

**Automated Extraction**
> Machines mining inside the Vault extract more from each block. **Machine-broken blocks never award Resonance** (§3.4) — this node is about materials, not progression.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25% ore yield from machine-broken blocks in the Vault; machine breaks count toward vein completion and statistics | 2 | 8 | Vault Presence T1 |
| 2 | +50% ore yield from machine-broken blocks | 3 | 12 | Tier 1 |

> **Implementation:** hook `BlockDropsEvent`, whose `getBreaker()` is a `@Nullable Entity` ("or null if unknown") and whose drop list is mutable. This covers machines that route through `Block.dropResources` / `Level.destroyBlock`, which is the large majority. Mods that build drops themselves and insert straight into their own inventory will not fire it; that gap is accepted.

---

**Far Reach**
> The chunks behind you stay awake for a while after you leave them, so machines keep running while you walk on.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Chunks you pass through remain loaded for 30 seconds after you leave | 2 | 12 | Automated Extraction T1 |
| 2 | Raised to 2 minutes | 3 | 16 | Tier 1 |

> Trailing tickets count against the Vault Presence ceiling and the config ceiling like any other, oldest evicted first. This node makes the ceiling matter rather than raising it.

---

**Seismic Sense** `[NOTABLE]`
> The Tome gains a directional readout of surrounding ore density — which way the rock gets richer, at chunk granularity. Navigation rather than power.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Chunk-level ore density compass in the Tome UI | 2 | 6 | Vault Presence T1 |
| 2 | The compass becomes a map: explored chunks are drawn, unexplored ones are blank | 2 | 11 | Tier 1 |
| 3 | Drawn chunks are shaded by ore density | 3 | 15 | Tier 2 |

> Tiers 2 and 3 absorb what was going to be a separate Cartographer's Instinct node. Two chunk-density readouts competing in the same Tome is one too many, so the map is this node's later tiers instead of a rival to it.

---

**Wanderer's Cache**
> The Vault leaves something for people who keep walking.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Every 50 newly explored Vault chunks drops a supply cache: food, torches, and a tool repair | 2 | 6 | None |
| 2 | Caches are larger and occasionally contain an enchanted book | 3 | 12 | Tier 1 |

> Reads `PlayerStats#chunksExplored`, which is already tracked and currently read by nothing. Cache contents come from a loot table (`orevault:chests/wanderers_cache`) so a pack can retune them without touching code.

---

**Frontier Bonus**
> Fresh rock pays better than rock you have already been standing on.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Ore mined in a chunk generated within the last 10 minutes pays +40% Resonance | 2 | 8 | None |

> Deliberately rewards moving on rather than strip-mining one spot to exhaustion, which is the behaviour every other node in the tree encourages.

---

**Pathfinder's Claim**
> Staking new ground is worth something on its own.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | The first vein mined in each newly generated chunk grants a burst of vanilla XP | 2 | 7 | None |

---

**Homeward Seam** `[NOTABLE]`
> One anchor point, anywhere in the Vault, and a way back to it. Removes the walk-back tax that otherwise makes exploring cost more than it pays.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Set one anchor point inside the Vault; teleport to it from anywhere else inside the Vault. Five-minute cooldown, cancelled if you take damage while casting | 3 | 10 | Vault Presence T1 |

> Only works Vault-to-Vault, never in or out. The anchor is stored per player and is cleared by a dimension reset (§3.5).

---

**The Vault Keeps It**
> The Vault does not eat what you drop in it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Items dropped on death inside the Vault never despawn and cannot be destroyed by fire, lava or explosion; the Tome marks the spot | 3 | 10 | None |

> Removes the worst case of a death without removing the setback — you still have to walk back for your things. Items are exempted from despawn only inside the Vault; carrying them out returns them to normal rules.

---

**Second Wind**
> Once per delve, the Vault decides you are not finished.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Once per Vault trip, damage that would kill you leaves you at half a heart instead. Resets on leaving the Vault | 4 | 14 | Miner's Constitution T3 |

---

**Second Wind: Homeward** `[TRADEOFF]`
> The same save, but it puts you back at the portal instead of leaving you where you nearly died.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | While enabled, a Second Wind save also returns you to your entry portal | 8 | 20 | Second Wind |

> A separate node rather than a second tier of Second Wind precisely so it can be toggled: the tradeoff machinery already stores on/off state per player (§6.1 notation), and being yanked out of a deep shaft is not always what a player wants. Twice the cost of the node it extends, as it turns a survival save into a free escape.

---

**Vault's Blessing** `[EXCLUSIVE: Vault's Purity]`
> The Vault sustains what you bring into it. Potion effects do not tick down at all while you are inside.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Potion effect durations are frozen inside the Vault | 3 | 8 | None |

---

**Vault's Purity** `[EXCLUSIVE: Vault's Blessing]`
> Nothing comes in with you. Potion effects are stripped on entry and cannot be applied inside — and the Vault rewards the discipline directly.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | No potion effects can exist inside the Vault; while unaffected: **+20% Resonance and +1 effective Fortune** | 3 | 8 | None |

> Purity previously granted nothing at all in exchange for stripping effects — 2 skill points of pure downside that only ever mattered against witches, which do not spawn in a Vault. As a pair these now describe two real builds: stack potions and keep them forever, or forgo them entirely for flat power that never runs out.

---

**Tithe** `[TRADEOFF]`
> 25% of ore blocks mined are consumed by the Vault (block breaks, no drop). The Resonance value of the consumed ore is multiplied by 1.75 and added to the pool. Only affects ore blocks — Stone Memory bonus drops, nuggets, flint, and other secondary sources are unaffected.
> In-game tooltip explicitly states: "Does not affect bonus drops from Stone Memory or other secondary sources."

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 25% ore consumed, 1.75× Resonance on consumed ores | 2 | 5 | None |

---

#### CLUSTER: Deep Lore

> The Vault answering back: bursts, echoes, and what it gives you for finishing what you started. **Anchor unlocks at 70 skill points spent anywhere in the Resonance tree.**

**Vault Echo**
> When a vein is fully mined, a Resonance burst is awarded.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25 Resonance burst on vein completion | 1 | 3 | Vein Expansion T2 |
| 2 | +35 Resonance burst | 1 | 6 | Tier 1 |
| 3 | +50 Resonance burst | 2 | 9 | Tier 2 |

---

**Echo Chamber** `[NOTABLE]`
> Vault Echo bursts also grant vanilla XP equal to the Resonance awarded. Finishing a vein becomes worth doing for its own sake rather than something that happens incidentally.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Vault Echo bursts additionally grant XP 1:1 with the Resonance awarded | 3 | 11 | Vault Echo T3 |

---

**Twin Veins**
> When a vein is fully mined to completion, there is a chance a second identical vein spawns adjacent to the mined area with a visual flash effect.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 1% chance on vein completion | 2 | 6 | Vein Expansion T3 |
| 2 | 5% chance on vein completion | 2 | 10 | Tier 1 |
| 3 | 10% chance on vein completion | 3 | 14 | Tier 2 |

> Veins created by Twin Veins are registered into the vein index (§11) exactly as generated veins are, so they themselves can trigger Vault Echo and Twin Veins on completion.

---

**Last Ore**
> The block that finishes a vein is worth more than the ones before it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | The final ore block of any vein drops double and pays double Resonance | 3 | 10 | Vault Echo T1 |

> Makes finishing feel different from mining, which is the whole reason the completion group exists. Stacks multiplicatively with the Vault Echo burst, which is separate.

---

**Clean Cut**
> Finish a vein without chewing through the rock around it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | A vein completed without breaking any non-ore block between its first and last ore pays +50% Resonance across the whole vein | 3 | 13 | Vault Echo T2 |

> Rewards precision, and is the one node in the tree that Ultimine makes harder rather than easier. It pairs naturally with **Vein Sight**, which is what makes a clean cut plannable at all.

---

**Vein Discipline**
> Finish what you start, over and over, and the Vault starts expecting it of you.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | +2% Resonance per completed vein, stacking to +30% at 15 veins. Persists across trips | 3 | 12 | Vault Echo T2 |

> **Abandoning** is defined as: you have broken at least one block of a vein, and then either leave the Vault or move more than 48 blocks from it, with ore still standing. Abandoning drops the streak by 3, not to zero.
>
> A hard reset was the first version and it is wrong: one stray swing at the edge of a vein you had not noticed would cost fifteen veins of work, and the correct response to that is to stop exploring and mine defensively — the opposite of what the tree wants. Decay keeps the pressure without the feel-bad.
>
> Requires a new `veinDisciplineStreak` field in `PlayerStats`, defaulting to 0.

---

**Chapter's End** `[NOTABLE]`
> Clear a chunk properly and the Vault shows you what is left of it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Completing 5 veins within a single chunk fires a Resonance burst worth 3× Vault Echo T3 and reveals every remaining vein in that chunk | 4 | 16 | Twin Veins T2 |

> Named to avoid reusing "Motherlode", which is a removed node id and would collide in existing save data. Counts veins completed in the chunk over all time, not per trip, so it fires once per chunk.
>
> **Watch the storage.** An all-time per-chunk counter is a map that only ever grows, in a file written on every save, for a dimension a team explores without limit. Store the partial counts (1 to 4) in one map and, when a chunk fires, drop its count and add the chunk to a set of fired chunks. The counts stay small because they are work in progress; the fired set is the part that accumulates, at 8 bytes per chunk cleared — a team that clears ten thousand chunks pays about 80 KB, which is affordable and, unlike the count map, cannot be pruned without letting a chunk fire twice.

---

**Deep Harvest** `[NOTABLE]`
> Ore mined below Y=0 — the deepslate band that only exists in an expanded Vault — drops an additional Resonance orb. The reward for committing to the Vault Expansion keystone.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Ore below Y=0 drops one extra Resonance orb | 4 | 20 | Deep Veins T2, Vault Expansion |

---

**Resonant Draw** `[FORK PARENT: Orb Collection]`
> Changes how Resonance orbs reach you. **Inert until specialized:** pick Resonance Magnetism or Hoarder's Instinct below and every tier of this node applies through it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Draw applies at its listed strength | 1 | 5 | None |
| 2 | Draw applies at its second-tier strength | 1 | 9 | Tier 1 |
| 3 | Draw applies at its third-tier strength | 2 | 13 | Tier 2 |

---

**Resonance Magnetism** `[FORK OPTION: Orb Collection]`
> Orbs are drawn to the player from greater distances. Convenience — mine and never think about collection again.

**Costs 0 skill points.** Requires Resonant Draw tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Resonant Draw tier | Effect through this option |
|---|---|
| 1 | Orb attraction radius: 8 blocks |
| 2 | Orb attraction radius: 16 blocks |
| 3 | Orb attraction radius: 24 blocks |

---

**Hoarder's Instinct** `[FORK OPTION: Orb Collection]`
> Orbs never move toward you — but they never despawn either, and orbs that come to rest near each other merge into a single growing cache. Collecting a merged cache of N orbs pays a bonus. Rewards clearing an area completely, then sweeping it.

**Costs 0 skill points.** Requires Resonant Draw tier 1. One option at a time; picking this locks its siblings until refunded, and unpicking it is free (§4.4).

| Resonant Draw tier | Effect through this option |
|---|---|
| 1 | Orbs are stationary and permanent; nearby orbs merge into caches |
| 2 | Cache collection pays ×(1 + 0.1 × N), capped at ×3 |
| 3 | Caches pull in orbs from neighbouring caches within 8 blocks; cap raised to ×4 |

> The previous version of this node removed orb attraction and gave a flat 2× on manual pickup, which was strictly worse than Magnetism Tier 1 in ordinary play — an exclusive choice where one side was simply wrong. Merging plus permanence makes it a different way to mine (clear-then-sweep) rather than a worse one.

---

> **Deferred to the Animus epic.** The **Disturbed Zone Unlock** node previously sat at the root of this tree. Disturbed Zones and the whole Animus system have moved to a separate post-1.0 epic, and this node moves with them — it will re-enter the Resonance tree as a Core-branch node when that epic is scheduled.

---

#### CLUSTER: Broad Cut `[ULTIMINE ONLY]`

> Wide-swing mining. The whole cluster is absent unless FTB Ultimine is loaded. **Anchor unlocks at 25 skill points spent anywhere in the Resonance tree.**

**Ultimine Expansion**
> Increases the maximum number of blocks FTB Ultimine can break per operation inside the Vault.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25% max block break count | 1 | 3 | None |
| 2 | +50% max block break count | 2 | 7 | Tier 1 |
| 3 | +100% max block break count | 3 | 12 | Tier 2 |

---

**Ultimine Safety**
> Reduces the Volatile Veins disappearance chance when using FTB Ultimine.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | −1% disappearance chance during Ultimine operations | 1 | 5 | Ultimine Expansion T1 |
| 2 | −2% disappearance chance | 2 | 9 | Tier 1 |

---

**Volatile Veins: Ultimine Gambit** `[TRADEOFF]`
> When using FTB Ultimine with Volatile Veins active, the effective block count for disappearance checks is increased by 1, increasing the risk. In exchange, successful Ultimine operations that don't trigger disappearance award a 20% Resonance bonus.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Riskier Ultimine, 20% Resonance bonus on safe operations | 2 | 9 | Volatile Veins, Ultimine Expansion T1 |

---

#### CLUSTER: Mastery

> Keystones only. Each one changes how you mine and each one costs you something. **Anchor unlocks at 100 skill points spent anywhere in the Resonance tree.**

> The three cheap keystones that used to sit here — Greedy Seams, Resonant Overload and Brittle Stone — kept their original prices and moved out to Excavation and Metallurgy as `[PACT]` nodes. They were priced to be buyable early and that is what they are for; behind a 100-point gate the option they were built around could not exist. Everything left in Mastery is priced for arriving last.

**Vault Expansion** `[KEYSTONE]`
> The Vault's floor drops away. Bedrock moves from Y=0 down to Y=−64, opening a 63-block deepslate band that carries the highest ore density in the mod. **Requires a dimension reset to take effect** — the existing Vault, and everything built in it, is regenerated.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Vault re-created under `ore_vault_expanded` on next reset; deepslate band below Y=0 unlocked | 10 | 18 | Ore Attunement T3, Vein Expansion T5, Miner's Constitution T4 |

---

**Full Spectrum** `[KEYSTONE]`
> The Vault stops favouring anything. The two focus branches you did not choose apply at half effect alongside the one you did — but the Vault's generosity is spread thin, and every ore yields less Resonance.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Unchosen Ore Focus options apply at 50% effect alongside the chosen one; **−20% Resonance from all ore** | 8 | 22 | Ore Attunement T3 |

---

**Silent Stone** `[KEYSTONE]`
> Ore pays no Resonance at all. The pool fills instead from time spent below Y=0, faster than average mining pays.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Ore awards no Resonance. While below Y=0 in the Vault, the team pool gains 3 Resonance per second | 8 | 24 | Vault Expansion |

> Divorces progression from extraction. You can chase materials as hard as you like without slowing the tree, or delve slowly and still advance. Every ore-multiplying node you own stops contributing to progression, which is a large sacrifice for anyone deep in Metallurgy, and it makes Greedy Seams a pure upside rather than a pact — a combination that is allowed and is meant to be found.
>
> **AFK guard:** accrues only while you have broken a block in the last 10 seconds. The rate is a config value, because "faster than average mining pays" is a claim that only a playtest can settle — 3/second is roughly 1.5× a solo player's observed mixed-rarity rate.

---

**Resonant Symbiosis** `[KEYSTONE]`
> The Vault listens harder when there is more than one of you in it.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | While two or more team members are inside the Vault at once, each of them gains +50% Resonance and +25% ore. Alone inside the Vault, −40% Resonance | 8 | 20 | None |

> This is a team mod whose tree otherwise has nothing to say about teams. It makes co-op a build rather than a convenience, and the solo penalty is steep enough to be a genuine choice rather than a free bonus for groups. Solo players are a team of one (§2), so for them it is a trap they can see coming — which is exactly what a keystone is.
>
> Interacts with §4.2 team scaling, which already divides the pool by team size: Symbiosis pays per member *after* that division, so a pair with it beats a pair without it but does not beat two solo players with separate Vaults on throughput alone.

---

**Bedrock Communion** `[KEYSTONE]`
> Live at the bottom or do not bother. The deeper you are, the more the Vault gives; anywhere above the deep, the rock fights you.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | +2% Resonance and ore per block below Y=0, reaching +126% at expanded bedrock. Between Y=32 and Y=245: no Resonance from ore, and mining speed halved. At Y=246 and above: no penalty | 10 | 26 | Vault Expansion, Deep Harvest |

> **The Y=246 exemption is the whole reason this node is playable.** Y=246 is the base of the dirt band (§3.1: dirt Y=246–249, grass Y=250, open air Y=251–319), so every surface build, machine room and automation floor sits above the line and is untouched. Without it the keystone would tax the one part of the Vault players build in.
>
> The penalty is a mining-speed reduction, not damage. Damage over time in a dimension you spend hours in is an irritation rather than a cost, and it would stack lethally with lava from Molten Seam.
>
> +126% is the ceiling and it is a fixed one: the expanded Vault floors at Y=−63 with bedrock at −64, so there is no depth left to scale into. Stating the cap is better than leaving the formula open-ended and discovering it in a spreadsheet.

---

### 6.2 Animus (Mob) Tree

#### BRANCH: Disturbed Zone Enhancement

**Zone Frequency**
> Increases mob spawn frequency within Disturbed Zones.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +25% spawn frequency | 1 | 0 | None |
| 2 | +50% spawn frequency | 1 | 3 | Tier 1 |
| 3 | +80% spawn frequency | 2 | 6 | Tier 2 |
| 4 | +120% spawn frequency | 2 | 10 | Tier 3 |

---

**Zone Pack Size**
> Increases the number of mobs per spawn attempt in Disturbed Zones.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +1 mob per pack | 1 | 1 | None |
| 2 | +2 mobs per pack | 1 | 4 | Tier 1 |
| 3 | +3 mobs per pack | 2 | 8 | Tier 2 |

---

**Zone Radius**
> Increases the radius of each Disturbed Zone block's spawn area.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | Radius: 32 blocks (up from default 16) | 1 | 2 | Zone Frequency T1 |
| 2 | Radius: 48 blocks | 2 | 6 | Tier 1 |
| 3 | Radius: 64 blocks | 3 | 11 | Tier 2 |

---

**Mob Diversity**
> Unlocks specific mob categories that can spawn in Disturbed Zones beyond basic vanilla mobs.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | Undead mobs (zombies, skeletons, wither skeletons) | 1 | 0 | None |
| 2 | Arthropod mobs (spiders, cave spiders, silverfish) | 1 | 3 | Tier 1 |
| 3 | Illager mobs (vindicators, evokers, pillagers) | 2 | 7 | Tier 2 |
| 4 | Rare mobs (witches, endermen) | 3 | 12 | Tier 3 |

---

#### BRANCH: Mob Rewards

**Reaper's Claim**
> Mobs killed in Disturbed Zones drop more XP.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +50% XP from zone mobs | 1 | 2 | Zone Frequency T1 |
| 2 | +100% XP from zone mobs | 2 | 5 | Tier 1 |
| 3 | +150% XP from zone mobs | 2 | 9 | Tier 2 |

---

**Corrupted Veins**
> Mobs killed in Disturbed Zones have a chance to drop raw ore instead of standard loot.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | 5% chance of random common raw ore drop | 1 | 3 | Zone Frequency T2 |
| 2 | 10% chance, includes uncommon ores | 2 | 7 | Tier 1 |
| 3 | 15% chance, includes rare ores | 3 | 12 | Tier 2 |

---

**Plunderer's Share**
> Increases standard item drop quantity from mobs in Disturbed Zones.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +25% item drop quantity | 1 | 2 | None |
| 2 | +50% item drop quantity | 1 | 5 | Tier 1 |
| 3 | +80% item drop quantity | 2 | 9 | Tier 2 |

---

**Animus Amplifier**
> Increases Animus dropped per mob kill.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +25% Animus per kill | 1 | 1 | None |
| 2 | +50% Animus per kill | 1 | 4 | Tier 1 |
| 3 | +80% Animus per kill | 2 | 8 | Tier 2 |

---

**Soul Harvest** *(Animus Tree Keystone)*
> Mobs occasionally drop a Soul Harvest item on death — a separate currency usable in a future Vault Shop (to be designed). Placeholder node; shop system is a future feature.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | 5% chance of Soul Harvest drop | 5 | 15 | Reaper's Claim T3, Corrupted Veins T2, Plunderer's Share T2 |

---

## 7. Disturbed Zones

### Block Behaviour

The Disturbed Zone block is craftable after the Disturbed Zone Unlock node is purchased in the Resonance tree. Crafting recipe: Vault Frame block + Nether Star (significant cost, intentional — Disturbed Zones are opt-in for players who have progressed).

Alternative cheaper recipe suggestion: Vault Frame block + 4 Bone Blocks + 4 Rotten Flesh — more accessible, thematically appropriate. Final recipe is a design decision for implementation.

When placed inside the Vault, the block creates a spherical spawn zone around it. Only vanilla mobs spawn in these zones. Default radius: 16 blocks, increased by Zone Radius nodes. The block emits a red particle effect so its zone boundary is visually apparent.

Since all players in a Vault dimension belong to the same team with the same shared skill tree, all Disturbed Zone blocks have identical stats. No conflict resolution between overlapping zones is needed.

Mobs spawned by Disturbed Zones count as Disturbed Zone mobs for all node effects (Reaper's Claim, Corrupted Veins, Plunderer's Share, Animus drops). Mobs that wander outside the zone radius still count as Disturbed Zone mobs until they despawn or are killed.

### Animus Drop Behaviour

Animus orbs drop from mobs killed in Disturbed Zones, behaving identically to XP orbs. They float toward the nearest team member within a base radius of 8 blocks (modified by node). Any team member in range can collect them. No instancing — first player to walk near them collects them.

---

## 8. UI — Tome of the Deep Seam

### Item

- Obtained on first player spawn (given automatically) and craftable with cobblestone + book
- Opens a custom GUI with three tabs at the top
- Does not go in the offhand slot (marked as mainhand/inventory only)
- Right-click to open

### Tab 1: Resonance Tree

**Layout.** The reference is Diablo 4's skill tree: hubs you follow down a spine, each one with its
own skills radiating around it. Each cluster is drawn as a **hub** — its anchor at the centre, and
the cluster's nodes on **rings** around it, so a node's distance from its anchor is its prerequisite
depth inside that cluster and nothing else. A node on the first ring is joined to the anchor by a
**spoke**, because the anchor's gate really is the only thing holding it. Fork options sit together
on the ring just outside their parent. Hub centres run down one vertical **spine**, in the §6.1
cluster order, joined by a line.

Nothing is placed straight above or below a hub. A wedge either side of vertical is kept empty at
every radius, giving each cluster a **corridor** the spine runs through and any edge leaving the
cluster can use. What is left is two sectors, and the cluster's nodes fan into them, left and right.

Position must never imply a relationship that does not exist. Two nodes are drawn one outside the
other only when one requires the other.

**Edges.** Prerequisite lines connect **border to border**, stopping at the edge of each node box —
never routed to the box centre, which draws the line across the node and over its text. A line only
ever travels along a node's own ray out from its hub, around an arc inside the empty gap between two
rings, or up and down a corridor, so it cannot cross a node box.

**Node boxes size to their content.** A box is as wide as the **widest line it will ever draw**,
within a minimum and a maximum. That is not the same as the widest name: the second line carries the
tier, the cost and the level requirement, and a specialised fork parent's second line carries the
display name of the option it is set to. Nothing is truncated at a fixed width, and a box does not
change size when it is bought.

**The page is opaque.** The tree is drawn on its own solid page rather than over the world behind
the screen. Prerequisite lines are one pixel wide, and against a translucent backdrop they read
against whatever the player happened to be standing in front of.

**Per node:** name, current tier / max tier, next tier's skill-point cost and level requirement, and
a short description on hover.

**Class is visible without hovering:**

| Class | Treatment |
|---|---|
| Small | Plain box |
| Anchor | At the centre of its cluster, framed twice over, no cost shown; displays its points-spent gate and whether it is met |
| Notable | Larger box, distinct frame |
| Keystone | Largest, ornamented, unmistakable — grouped in Mastery at the bottom |
| Pact | Keystone ornamentation in a different, colder frame, so it reads as dangerous without reading as a keystone. A pact in Excavation must not look like a keystone that has escaped Mastery |
| Growth | Plain box while live. Once its condition can no longer be met, greyed out and labelled **Outgrown**, with its effect shown struck through — the point is spent and the player should be able to see why |
| Fork parent | Marked as a fork; shows which option is active, or that it is **inert** while none is |
| Fork option | Small, on the ring just outside the parent and beside its siblings, marked free; siblings shown locked once one is picked |
| Tradeoff | Toggle element on the node |
| Exclusive | Lock mark when its partner is held |

- Locked nodes are dimmed, with a tooltip naming the specific requirement that is missing
- A fork parent with no option chosen is flagged as doing nothing — this is the state most likely to
  be mistaken for a bug
- Ultimine nodes hidden entirely if FTB Ultimine not loaded; the Broad Cut cluster disappears whole
- Team's current Resonance level and skill point count shown in the top bar
- Team's Resonance pool progress toward next level shown as a bar

### Tab 2: Animus Tree

- Identical layout to Resonance tree but for Animus nodes
- Team's Animus level and skill point count shown in top bar
- Animus pool progress bar

### Tab 3: Ore Memory

- **Team Stats panel** (left): aggregate totals for the whole team
- **Player Stats panel** (right): stats for the viewing player; dropdown to view any team member's individual stats
- Trophy icon next to the team member with the highest value in each stat category (purely cosmetic)
- **Stats tracked (per player and team aggregate):**
  - Ores mined by type
  - Stone blocks broken
  - Total blocks broken
  - Deepest Y level reached inside the Vault
  - Chunks explored (generated) in the Vault
  - Total time spent inside the Vault
  - Total Resonance earned (lifetime)
  - Resonance earned this session
  - Vault Echo triggers
  - Twin Veins triggers
  - Total mobs killed in Disturbed Zones
  - Mobs killed by type
  - Total Animus earned (lifetime)
  - Animus earned this session
  - Volatile Veins disappearance triggers
  - Volatile Veins pity activations
  - Veins completed (lifetime) and current Vein Discipline streak
  - Deaths inside the Vault
  - Ore types attuned by Kindred Rock, with progress toward the next

**New `PlayerStats` fields these require.** `veinsCompleted`, `veinDisciplineStreak`,
`deathsInVault`, and a per-chunk completion tally for Chapter's End. Each is a field addition to a
player save file, so each needs a default that a tag written before it existed still reads correctly
(§11 SavedData Pattern) — zero and empty in every case, which is also the correct starting value, so
no `PlayerStats` data-version bump is needed for the additions alone. The node-id renames in §6.1
*do* need one.

Kindred Rock needs no new field: it reads the existing `oresMined` map and derives attunement from
the count.

### Vault Reset Button

- Only appears in the UI if the viewing player holds a Tier 4 Sovereign Vault Igniter in their inventory
- Located in the Ore Memory tab (bottom right corner, clearly separated from stats)
- Click → voting dialog appears for all online team members
- Voting follows the majority rule (see Section 3.5)
- Backup checkbox present in the voting dialog
- 10-second countdown warning if vote passes with players inside the Vault

---

## 9. Mod Integrations

### FTB Teams (Hard Dependency)

- Every system keyed on FTB Team UUID
- Dimension registry key: `orevault:vault_<teamIdNoHyphens>`
- SavedData keys prefixed with team UUID
- Resonance and Animus pools stored per team
- Skill tree state stored per team
- Player stat tracking stored per player, queryable by team
- Per-player tradeoff toggle state stored per player (§6.1), **not** on the team
- Dimension created lazily, on the first portal trip by any team member, and never before (§3.1)
- Dimension deleted on team disband event (listen to FTB Teams disband event)
- Solo players use their auto-created single-member FTB team. Because FTB Teams creates that team automatically for every player who logs in, `getTeamForPlayerID` never returns empty on a live server — **nothing in this mod may gate behaviour on "has a team"**, since the condition can never be false

### FTB Ultimine (Soft Dependency)

- Detection: `ModList.get().isLoaded("ftbultimine")`
- If not loaded: Ultimine branch nodes not rendered in UI, Ultimine hooks not registered
- If loaded: hook into Ultimine's block-break event to apply Volatile Veins roll once per operation, and apply Ultimine Expansion node's max block count modifier
- No direct import of Ultimine classes; use event-based integration only

### Mekanism (Soft Dependency)

- Detection: `ModList.get().isLoaded("mekanism")`
- If not loaded: Ore Doubling tiers 4-6 do not appear in the UI; doubling uses raw ore or dust fallback
- If loaded: Ore Doubling tiers 4-6 appear; output item lookup done via reflection against Mekanism's ore processing registry to find the correct processing-tier item for each ore
- No direct import of Mekanism classes anywhere; all lookups via reflection or tag system
- For ores without a Mekanism processing chain: fallback to raw ore double

### Generic Ore Dust Mods (Soft Dependency)

- At server start, scan the common item tag **`c:dusts/<oreName>`** for each classified ore block. Note the namespace: NeoForge 26.1 uses `c:` for common tags — the `forge:` namespace named in earlier drafts of this document does not exist and would silently match nothing (the same class of mistake as the `orevault:mineable/pickaxe` bug in #87)
- If a dust tag exists for an ore, Ore Doubling tiers 1-3 drop dust instead of raw ore
- If no dust tag exists, drop raw ore
- No specific mod is referenced; this works with any mod that registers proper `c:` dust tags

---

## 10. Configuration

Configuration is intentionally minimal. Most values are derived dynamically from the defined skill tree. Admins can control only things with server performance implications.

**Config file:** `orevault-server.toml` (server-side only, not synced to clients)

```toml
[resonance]
    # Target hours of play to make the full Resonance tree purchasable (level 30).
    # Read once at server start; takes effect on the next world load.
    target_play_hours = 100

    # Divides the total Resonance required across the whole curve.
    # 2.0 = half the grind; 0.5 = double it. The shape of the curve is unchanged,
    # so every level requirement in the skill tree keeps its intended pacing.
    # Read once at server start; takes effect on the next world load.
    curve_divisor = 1.0

[chunk_loading]
    # Whether the Vault Presence skill nodes are enabled.
    # Set to false to disable cross-dimension chunk loading entirely.
    vault_presence_enabled = true

    # Hard ceiling on simultaneous loaded chunks per team, regardless of node level.
    # Set to 0 for unlimited (not recommended).
    max_loaded_chunks_per_team = 32

[ore_classification]
    # Override the automatic rarity classification for specific ore blocks.
    # Format: "modid:block_id" = "common|uncommon|rare"
    # Example: "minecraft:diamond_ore" = "rare"
    overrides = {}

[disturbed_zones]
    # Maximum number of Disturbed Zone blocks placeable per team.
    max_zones_per_team = 10

[reset]
    # Whether team members can take backups before resetting.
    # Disable if disk space is a concern.
    allow_backup_on_reset = true
```

A temporary `[debug]` block also exists — `enable_debug_commands` and `log_resonance_gain` — and is
removed with the rest of the playtest instrumentation before 1.0 (#120).

**When a change takes effect.** The config screen is reachable in game from Escape → Mods → Ore Vault
→ Config, and NeoForge only offers a `SERVER` config there in a singleplayer world that is not
published to LAN. What a saved change does from there varies, so each value says so in its own
comment and tooltip rather than leaving the player to guess:

| Setting | Takes effect |
| --- | --- |
| `resonance.target_play_hours` | next world load — the curve is computed once at server start |
| `resonance.curve_divisor` | next world load — same curve |
| `ore_classification.overrides` | next world load — `OreClassifier` builds its table at server start |
| `chunk_loading.*` | nothing reads it yet ([30], [52]) |
| `disturbed_zones.max_zones_per_team` | nothing reads it yet (post-1.0) |
| `reset.allow_backup_on_reset` | nothing reads it yet ([80]) |
| `debug.enable_debug_commands` | immediately |
| `debug.log_resonance_gain` | immediately — read on each orb pickup |

The three world-load values are marked `worldRestart()` on the spec builder, which is what makes the
config screen warn about it instead of silently accepting an edit that does nothing until a reload.

`enable_debug_commands` is immediate but needed help to look that way. The Brigadier `requires`
predicate is evaluated server-side on every execution, so the command really does start working the
moment the flag flips — but the *client's* copy of the command tree is only sent on login, so the
command stayed invisible and un-completable until a reload. `ConfigReload` listens for
`ModConfigEvent.Reloading` and re-sends the tree to everyone online, which re-runs the predicate.

**Values derived automatically (not configurable):**
- Level thresholds for Resonance and Animus (derived from tree structure, scaled by `curve_divisor`)
- Level cap (derived from tree structure)
- Node costs (defined as constants in `NodeCosts.java`)
- Stone content floor (40%, hardcoded constant)
- `averageOresPerHour` (constant in `ResonanceSystem`, see §4.3 step 5)

---

## 11. Implementation Notes and Technical Gotchas

### Dynamic Chunk Generator

The core of the world-gen system. Rather than static registered `PlacedFeature` entries (which are frozen at startup), the mod uses a custom `ChunkGenerator` implementation that reads the team's current skill tree state from `SavedData` at chunk generation time and constructs the ore placement configuration on the fly.

**Consequence:** Every chunk generated in a team's Vault reflects the skill tree state at the time of generation. Purchasing a node takes effect immediately in newly generated chunks. Already-generated chunks are unchanged (except via the voluntary dimension reset).

**Performance note:** The custom chunk generator runs marginally more code per chunk than vanilla. On a server generating many new chunks rapidly (e.g. a quarry running at speed), this may cause slight TPS impact. This is acceptable and noted in server documentation.

**Implementation pattern:**
```java
// In your ChunkGenerator:
TeamSkillState skills = OreVaultSavedData.getTeamSkills(teamId);
OreConfiguration config = OreConfigBuilder.buildFromSkills(skills);
// Apply config to chunk
```

### SavedData Pattern

All persistent mod state (Resonance pool, Animus pool, skill tree state, player stats, chunk loading tickets) is stored using Forge's `SavedData` system, keyed to the server's overworld level. One `SavedData` instance per team, keyed by team UUID.

```java
public class OreVaultTeamData extends SavedData {
    int dataVersion;            // bumped whenever this shape changes; read on load to migrate
    UUID teamId;
    long resonancePool;
    long animusPool;
    int resonanceLevel;
    int animusLevel;
    int resonanceSkillPoints;
    int animusSkillPoints;
    Map<NodeId, Integer> unlockedNodes; // NodeId -> tier
    Map<UUID, PlayerStats> playerStats; // player UUID -> stats
    int chunkLoadingTickets;
}
```

**Tradeoff toggles live on `PlayerStats`, not here.** §6.1 specifies them as per-player — one member of a team can run Tithe while another does not — so `activeTradeoffs` is a `Set<NodeId>` field on `PlayerStats`. An earlier version of this sketch put it on the team object, which contradicted §6.1; the implementation follows §6.1 and this sketch has been corrected to match.

**`dataVersion` and migration.** Every load reads `dataVersion` first and migrates forward if it is behind `CURRENT_DATA_VERSION`. This costs almost nothing to add now and is the difference between a schema change being a migration and being a wipe. The shape above is still growing through the remaining phases, so assume it will be needed.

### Ore Rarity Classification

Run at every server start (not cached to disk). Scan `ForgeRegistries.BLOCKS` for all registered `OreBlock` and `DeepslateOreBlock` instances. For each, look up its registered `PlacedFeature` entries and extract vein count and height range. Apply thresholds:
- count ≤ 4 AND max height ≤ Y=32 → Rare
- count ≥ 15 AND height range ≥ 128 blocks → Common
- Everything else → Uncommon

Apply admin config overrides after classification. Store classification in memory for the session.

### FTB Teams Event Hooks

- `TeamEvent.CREATED` → initialise `OreVaultTeamData` for the new team
- `TeamEvent.PLAYER_JOINED` → give Tome of the Deep Seam if player doesn't have one
- `TeamEvent.DISBANDED` → delete team's Vault dimension and `OreVaultTeamData`

### Volatile Veins Pity System

Track per-player (not per-team) in `PlayerStats`:
```java
int volatileVeinsTriggerStreak; // consecutive triggers
boolean volatileVeinsSafeWindow; // currently in safe window
int volatileVeinsSafeBlocksRemaining;
```
On logout: `volatileVeinsTriggerStreak = 0`, `volatileVeinsSafeWindow = false`.
After 3 consecutive triggers: enter safe window for 10 blocks.

### Dimension-per-Team Registration

Dimensions are registered **lazily, on the first portal trip by a team member** (§3.1). Earlier drafts of this section recommended registering every team's dimension at server start and creating more on team-created events; that recommendation is withdrawn. FTB Teams auto-creates a single-member team for every player who logs in, so eager registration means one full `ServerLevel` per player account the server has ever seen — chunk map, storage, region directory and all — for a dimension most of them will never enter.

Registration itself: build the `LevelStem`, briefly unfreeze the live `LEVEL_STEM` `MappedRegistry` to register it, re-freeze, construct the `ServerLevel` mirroring what `MinecraftServer#createLevels` does for non-overworld dimensions, and put it into the world map. Deletion removes the registry entry and the level's storage directory — handle carefully to avoid registry desync.

> **The `markWorldsDirty()` trap.** After `server.forgeGetWorldMap().put(key, level)` you **must** call `server.markWorldsDirty()`. `MinecraftServer` ticks levels from a cached `ServerLevel[]` built by `getWorldArray()`, which is only rebuilt when `worldArrayMarker` changes — and `markWorldsDirty()` is the only thing that changes it. Omit the call and a dimension created after the first server tick is placed in the map but **never ticked**.
>
> The failure mode is deeply misleading. Because packet handlers run synchronously, block *placement* works and the first left-click produces exactly one `BreakSpeed` event — then nothing. `ServerPlayerGameMode.tick()` never runs, so destroy progress never advances and `BREAK_BLOCK` never fires; the block appears to break client-side and then snaps back. Nothing is cancelled, no protection mod is involved, and relogging "fixes" it because the level is then recreated by `createLevels` before the cache exists. Dimensions created during `ServerStartedEvent` also work, because the cache is still `null` at that point — which is exactly what makes the bug look like it is about team registration rather than tick scheduling. See issues #82 and #89.

### Vein Index

Four nodes need to know when a vein has been *completely* mined, and one needs to know which vein is nearest: Vault Echo (burst on completion), Twin Veins (clone the vein on completion), Volatile Veins (vanish the *remaining* connected blocks), and Stonecaller (nearest vein's ore type). The obvious implementation — a flood fill from every broken ore block — is far too expensive in a 60%-ore dimension being quarried.

Instead: **the chunk generator places the veins, so it already knows their exact extents.** Persist a per-chunk index of `{veinId, oreType, blockPositions}` at generation time. Completion is then a decrement to zero, "remaining blocks" is a lookup, and nearest-vein is a bounded search over an index rather than the world.

Two rules the implementation must hold:
- Veins created by **Twin Veins** are registered into the index when spawned, so they behave identically to generated veins.
- **Player-placed ore is never indexed.** This closes the otherwise obvious exploit — place ore, break it, farm Vault Echo — by construction rather than by a check that could be missed.

### Drop Pipeline

Nine nodes modify what an ore break drops, and the order they apply in changes the result substantially. Rather than each node ticket patching the same handler, all of them register into a single ordered pipeline on `BlockDropsEvent`:

| Stage | Nodes |
|---|---|
| 1. Consume | Tithe (block consumed, no drops), Brittle Stone (shatter roll) |
| 2. Fortune | Vein Fortune, Vault's Purity's +1 Fortune |
| 3. Quantity | Ore Doubling, Greedy Seams, Automated Extraction |
| 4. Transform | Smelter's Intuition, Runic Attunement (Attuned marking), dust/Mekanism substitution |
| 5. Bonus | Stone Memory, Ancient Knowledge, Stonecaller, Vault Echo, Deep Harvest |

> **Fortune is the awkward stage.** `BlockDropsEvent` fires *after* `Block#getDrops` has already rolled the loot table, so a Fortune bonus cannot simply be added there. The Fortune stage therefore discards the existing drop list and re-rolls `state.getDrops(...)` with a copy of the tool carrying the appropriate Fortune level. Everything else operates on the list in place.
>
> That re-roll is why Fortune runs before Quantity rather than after it, which is a change from the original ordering. Rolling Quantity first and then discarding the list would have silently deleted everything Ore Doubling and Greedy Seams had just added, whenever a Fortune node was also unlocked. The current order also answers the other two ordering questions cleanly: Consume short-circuits before Fortune ever rolls, and Transform still follows Quantity, so Smelter's Intuition smelts the doubled output.

Consume, Fortune and Quantity **contribute** to an outcome the pipeline applies once, rather than editing the drop list themselves. That is what makes two guarantees structural instead of conventional: Quantity multipliers compose multiplicatively because they are multiplied together, and the loot table is re-rolled exactly once no matter how many nodes raise Fortune. Transform and Bonus do edit the list, because replacing and appending compose in the obvious way and there is nothing to accumulate.

### Resonance Orb Entity

Resonance and Animus orbs share a `VaultOrbEntity` base that pays a team pool rather than player XP. It is **modelled on `ExperienceOrb` rather than extending it**: the two behaviours that matter here are exactly the two vanilla hard-codes wrongly for a team-owned orb — `playerTouch` grants the toucher experience, and the follow logic chases the nearest player of any allegiance. Subclassing would have left both one missed override away from paying out to whoever walked past.

Movement is server-authoritative. Vanilla runs the follow logic on both sides and lets the client predict, which works because "nearest player" is knowable client-side; team membership is not, so a client prediction would pull orbs toward players who cannot collect them. The client interpolates what the server sends, which is why the entity type uses a short update interval.

One renderer draws both types. It reuses the vanilla orb sprite sheet — an orb should read as an orb and differ by colour — sizing by value on vanilla's own thresholds and taking its tint from the entity: Resonance = blue/cyan, Animus = red/dark red.

### Node Cost Constants

All skill point costs and level requirements are defined in `NodeCosts.java` as public static final int constants. Example:
```java
public class NodeCosts {
    // Vein Branch
    public static final int VEIN_EXPANSION_BASE_COST = 1;
    public static final int[] VEIN_EXPANSION_LEVEL_REQ = {0, 2, 4, 7, 10};
    // etc.
}
```
The dynamic level threshold calculator reads total tree cost by summing all `_BASE_COST` values multiplied by their tier counts. Changing a constant automatically recalibrates the entire level curve.

---

## 12. Suggested Build Order

Work through these phases with Claude Code. Start each session by instructing the agent to read this document before writing any code.

**Two scope decisions shape this order.** First, the Tome UI moves ahead of the node-effect work: a node cannot be *purchased* without it, so every node effect built before the UI exists is unverifiable. Second, the entire Animus half — Animus orbs, Animus levels, Disturbed Zones, the Animus tree, Soul Harvest — moves to a separate post-1.0 epic. It is parallel to the Resonance system rather than foundational to it, and deferring it takes roughly a third of the remaining tickets off the critical path without weakening 1.0.

**1.0 scope: the Resonance half.** Mining, the Resonance tree, the Tome, chunk loading, reset, integrations, content.

| Phase | Focus | Status |
|---|---|---|
| 0 | Project scaffold, FTB Teams hard dependency, skill tree data structures, `NodeCosts`, config | ✅ complete |
| 1 | Dimension registration, ore rarity classifier, custom chunk generator | ✅ complete |
| 2 | Vault Frame, portal block, shape scanner, igniter tiers, teleportation | ✅ complete |
| 3 | **Foundations for everything below:** vein index, drop pipeline, ore break handler, Resonance orb + system, level curve | |
| 4 | **Tome UI:** network channel, Tome item, screen shell, Resonance tree tab renderer — nodes become purchasable here | |
| 5 | Resonance node effects — world-gen (Vein branch, Ore Focus fork, Vein Shape fork, Stone Reduction, Geodes, Ancient Traces) | |
| 6 | Resonance node effects — player-facing (Fortune branch, XP/Stone, Hunger, Utility, keystones, tradeoffs) | |
| 7 | Chunk loading — Vault Anchor, tickets, Vault Presence, Automated Extraction | |
| 8 | Vault reset — dimension deletion, vote state machine, countdown + evacuation, backup export, vote UI | |
| 9 | Integrations — soft-dep detection, FTB Ultimine, Mekanism / `c:dusts` fallback chain | |
| 10 | Content — recipes, models and textures, lang, advancements, loot tables, particles and sounds | |
| 11 | Testing — JUnit for pure logic, load test on a heavy pack, KNOWN_ISSUES.md | |
| — | **Post-1.0 epic:** Animus system, Disturbed Zones, Animus tree, Soul Harvest + shop, Ore Memory tab | deferred |

---

## 13. Implementation Checklist

Use this to track progress. Update at the end of each development session.

### Infrastructure
- [x] Forge 26.1 project scaffold (build.gradle, settings.gradle, mods.toml)
- [x] FTB Teams hard dependency declared and verified
- [x] Package structure created (`block`, `item`, `portal`, `worldgen`, `data`, `config`, `client`, `event`) — plus `skill`, `team`, `ore`, `resonance`, `entity`, `tags` and `debug`
- [x] `OreVault.java` main mod class
- [x] `NodeCosts.java` constants file
- [x] `OreVaultTeamData` SavedData class
- [x] `PlayerStats` data class
- [x] FTB Teams event hooks (CREATED, DISBANDED) plus the first-join Tome grant (#34). The grant
      listens to the vanilla login event, **not** FTB's PlayerJoinedPartyTeamEvent: that fires only
      for party joins, so a solo player would never receive a Tome
- [x] Per-team dimension key generation

### Dimension
- [x] Dimension type JSON (`ore_vault.json`, `ore_vault_expanded.json`)
- [x] Dimension types differentiated: base `min_y: 0` / `height: 320`, expanded `min_y: -64` / `height: 384`
- [x] Dynamic dimension registration per team
- [x] **Lazy creation on the portal path only** — `TeamCreated` initializes team data and nothing else;
      only `VaultDimensions#findOrCreate` makes a new Vault. The `ServerStartedEvent` pass is still
      there and is meant to be: it re-registers Vaults already on disk before anyone connects, because
      a player may have logged out inside one. Creating versus restoring are different things (#82, #89)
- [x] **`server.markWorldsDirty()` after inserting the level into the world map** (#82 / #89)
- [ ] Dimension deletion on team disband
- [x] Custom chunk generator skeleton
- [x] Open air layer at the top (69 blocks), solid fill below with grass surface
- [x] Ore rarity classifier (scans registry at server start)
- [x] Admin config override for rarity classification
- [ ] Dynamic ore placement from skill state
- [x] 40% stone content floor enforcement
- [ ] Per-chunk vein index persisted at generation time
- [ ] Vault Expansion: re-create under the expanded type on reset

### Portal and Igniter
- [x] Vault Frame block
- [x] Vault Frame `minecraft:mineable/pickaxe` tag in the **`minecraft` namespace** (#87)
- [ ] Vault Frame crafting recipe (8 iron + 1 redstone)
- [x] `VaultPortalShape` scanner (both axes, 2×3 to 21×21)
- [x] Ore Vault Portal block (no collision, unbreakable, AXIS state)
- [x] Portal frame integrity check (`updateShape`)
- [x] Vault Igniter Tier 1 item
- [x] Igniter tier capabilities overhaul — potion buffs removed, capabilities per §3.3 (#100)
- [x] Tier 2: one personal entry point
- [x] Tier 3: instant travel + 3 personal entry points — stored and cycled with a right-click
      in the air; the waypoint *list UI* needs the network channel and screens from Phase 4
- [ ] Tier 4: reset button gate + Vault Anchor recipe ingredient (returned as crafting remainder)
      — `VaultIgniterItem#canResetVault` exists; nothing calls it until the reset flow (Phase 8)
      and the Vault Anchor block (#30) land
- [ ] Resonance Crystal item + recipe (4 Attuned ore + 1 Amethyst Shard)

### Teleportation
- [x] Overworld → Vault routing (per team)
- [x] Vault → Overworld routing (return position)
- [x] Return position persistence (player persistent data)
- [x] Teleport cooldown (80 ticks)
- [x] Nether-style portal wait (80 ticks) with wavy overlay and travel sound
- [x] **Fixed team anchor at X=0, Z=0** — Overworld XZ mirroring removed
- [x] Exactly one return portal per Vault, built at the anchor, single-plane (#85) — `VaultPortalShape#ensureReturnPortal`
- [x] Return portal tier-coloured to the highest igniter tier seen (#86)
- [x] Remove the team-required gate and its message (#88 — the condition can never be false)
- [x] Tier 3+ skips the wait and the cooldown

### Resonance System
- [x] Resonance orb entity — registered, collectable and drawn: vanilla orb sprite sized by
      value, tinted blue/cyan, shared with the Animus orb post-1.0
- [x] Resonance orb spawns on ore break in Vault — 2/5/12 by rarity, ×1.75 when Tithe consumed
      the block; machine breaks award nothing (§4.2)
- [x] Orb floats to nearest team member in radius — 8 blocks base, server-authoritative;
      only members of the owning team attract or collect it
- [x] Team Resonance pool accumulation
- [x] Level curve: cap 30, `ceil(totalTreeCost / 30)` points per level
- [x] `[resonance]` config block: `target_play_hours`, `curve_divisor`
- [x] Team scaling: `sum / teamSize × (1 + 0.1 × (teamSize − 1))`
- [x] Remove `ASSUMED_TEAM_SIZE` from `NodeCosts` and `LevelCurve`
- [x] Skill point award on level-up — every level crossed is paid; the notification is an
      overlay message until the network channel lands, then it becomes a real toast
- [x] Drop pipeline (5 ordered stages on `BlockDropsEvent`) — the single listener, the stage
      contract and the Fortune re-roll; the nine node effects register into it as they land
- [ ] Vein index: completion detection, Twin Veins registration, player-placed ore excluded
- [x] Refund: `3 XP levels × tier cost`, free for 10 min after a reset — window state and
      `startFreeRespecWindow` are in place; the reset that opens it is owned by the reset ticket
- [x] `dataVersion` field + migration on `OreVaultTeamData`

### Skill Tree — Resonance Nodes
- [x] Skill tree data structure and prerequisite graph
- [x] Unlock validation (level req, prereq, exclusive conflicts, skill points)
- [x] Node-by-node refund (new formula)
- [ ] Tradeoff toggle per-player persistence — **toggleable only outside the Vault**
- [ ] Exclusive node pair enforcement
- [ ] Fork enforcement — parent is inert until an option is chosen; one option at a time; options
      cost 0 points to pick and 0 XP to unpick (§4.4)
- [ ] Cluster anchors — eight non-purchasable nodes gating on points spent in the tree (§6.1). An
      anchor can re-lock when points are refunded below its threshold; already-purchased nodes in a
      closed cluster keep working
- [ ] Node removals and renames with save migration — `common`/`uncommon`/`rare_ore_boost` and
      `motherlode` removed with a point refund, `disturbed_zone_unlock` removed, `ore_sense` renamed
      to `vein_fortune` and `efficient_miner` to `miners_constitution`, both carrying their tier.
      See the table in §6.1
- [ ] New fork parents: `vein_shaping`, `ore_working`, `resonant_draw`
- [ ] `[PACT]` and `[GROWTH]` node classes — a `nodeClass` enum on `NodeDef` replacing the
      `tradeoff` boolean, with the anchor and fork kinds folded into it

> Grouped by cluster, matching §6.1, which is authoritative. The old branch groupings are gone — a
> node's branch no longer affects anything the player sees, and keeping two orderings was how the
> code and the spec drifted apart in the first place.

- **Prospecting** (anchor: 0 points)
  - [ ] Vein Expansion (T1-T5)
  - [ ] Stone Memory (T1-T5)
  - [ ] Gravel Purge (T1)
  - [ ] Miner's Constitution (T1-T5) *(renamed from Efficient Miner; T4/T5 add max health)*
  - [ ] Sure Footing (T1-T2) *(new)*
  - [ ] Deep Breath (T1-T2) *(new)*
  - [ ] Novice's Luck *(growth, new)*
  - [ ] Apprentice's Ledger *(growth, new)*
  - [ ] Shallow Grace *(growth, new)*
  - [ ] Guide Vein *(growth, new)*
  - [ ] Salvager's Eye *(growth, new)*
- **Excavation** (anchor: 10 points)
  - [ ] Vein Proliferation (T1-T5)
  - [ ] Deep Veins (T1-T2)
  - [ ] Stone Reduction (T1-T2)
  - [ ] **[FORK: Vein Shape]** `vein_shaping` parent + Abundance / Vein Singularity / Stratified as free options
  - [ ] Deep Habit *(breakpoint, new)*
  - [ ] Long Delve (T1-T2) *(breakpoint, new — needs the 60-second AFK guard)*
  - [ ] Volatile Veins *(tradeoff, with pity system)*
  - [ ] Molten Seam *(tradeoff, new — netherrack shell is a hard requirement, not decoration)*
  - [ ] Greedy Seams *(pact, exclusive: Resonant Overload)*
  - [ ] Resonant Overload *(pact, exclusive: Greedy Seams)*
- **Assay** (anchor: 25 points)
  - [ ] **[FORK: Ore Focus]** `ore_attunement` parent + Common / Uncommon / Rare Focus as free options (replaces the three sequential Ore Boosts)
  - [ ] Geode Clusters (T1-T2)
  - [ ] Ancient Traces (T1-T2) — below Y=0 only, exempt from all multipliers
  - [ ] Ancient Knowledge (T1-T3)
  - [ ] Stonecutter's Patience (T1-T2) *(breakpoint, new — needs the `orevault:vault_stone` tag)*
  - [ ] Calloused Hands (T1-T3) *(breakpoint, new — logarithmic, capped at +40%)*
  - [ ] Vein Sight *(notable, new — build this before the Deep Lore completion group)*
  - [ ] Stonecaller *(notable)*
  - [ ] Prospector's Eye *(notable)*
  - [ ] Stone Curse *(tradeoff)*
- **Metallurgy** (anchor: 45 points)
  - [ ] Vein Fortune (T1-T3) *(renamed from Ore Sense)*
  - [ ] **[FORK: Yield]** `ore_working` parent (T1-T3, T4-T6 Mekanism+Doubling only) + Ore Doubling / Smelter's Intuition as free options
  - [ ] Runic Attunement (T1-T3) — Attuned ore → Resonance Crystals
  - [ ] Kindred Rock (T1-T2) *(breakpoint, new — per player, reads `oresMined`)*
  - [ ] Highwater Mark (T1-T2) *(breakpoint, new — tool and armour repair)*
  - [ ] Brittle Stone *(pact)*
  - [ ] Vault Fever *(tradeoff — cost is −25% Resonance, no longer hunger)*
- **Claim** (anchor: 55 points)
  - [ ] Vault Presence (T1-T3)
  - [ ] Automated Extraction (T1-T2) — yield only, never Resonance
  - [ ] Far Reach (T1-T2) *(new — trailing tickets count against the ceiling)*
  - [ ] Seismic Sense (T1-T3) *(notable; T2/T3 are the absorbed Cartographer map)*
  - [ ] Wanderer's Cache (T1-T2) *(exploration, new — loot table driven)*
  - [ ] Frontier Bonus *(exploration, new)*
  - [ ] Pathfinder's Claim *(exploration, new)*
  - [ ] Homeward Seam *(notable, new — Vault-to-Vault only)*
  - [ ] The Vault Keeps It *(survival, new)*
  - [ ] Second Wind *(survival, new)*
  - [ ] Second Wind: Homeward *(tradeoff, new)*
  - [ ] Vault's Blessing / Vault's Purity *(exclusive pair, both reworked)*
  - [ ] Tithe *(tradeoff)*
- **Deep Lore** (anchor: 70 points)
  - [ ] Vault Echo (T1-T3)
  - [ ] Echo Chamber *(notable)*
  - [ ] Twin Veins (T1-T3)
  - [ ] Last Ore *(completion, new)*
  - [ ] Clean Cut *(completion, new)*
  - [ ] Vein Discipline *(completion, new — streak decays by 3, never resets to 0)*
  - [ ] Chapter's End *(notable, completion, new)*
  - [ ] Deep Harvest *(notable)*
  - [ ] **[FORK: Orb Collection]** `resonant_draw` parent + Resonance Magnetism / Hoarder's Instinct as free options
- **Broad Cut** (anchor: 25 points, conditional on FTB Ultimine)
  - [ ] Ultimine Expansion (T1-T3)
  - [ ] Ultimine Safety (T1-T2)
  - [ ] Volatile Veins: Ultimine Gambit *(tradeoff)*
- **Mastery** (anchor: 100 points — keystones only)
  - [ ] Vault Expansion
  - [ ] Full Spectrum
  - [ ] Silent Stone *(new — rate is a config value)*
  - [ ] Resonant Symbiosis *(new)*
  - [ ] Bedrock Communion *(new — penalty lifts at Y=246, speed not damage)*

### Deferred to the post-1.0 Animus epic
- [ ] Animus orb entity, pool, level track, skill point award
- [ ] Animus tree: Zone Frequency / Pack Size / Radius, Mob Diversity, Reaper's Claim, Corrupted Veins, Plunderer's Share, Animus Amplifier
- [ ] Soul Harvest keystone + the shop that gives it a sink
- [ ] Disturbed Zone block, spherical spawn logic, zone particles, mob spawning, Animus drops
- [ ] Disturbed Zone mob tagging (mobs stay "zone mobs" after wandering out)
- [ ] Disturbed Zone Unlock node (re-enters the Resonance tree Core branch with this epic)
- [ ] Ore Memory tab — team/player stats panels, trophies (cosmetic; deferred)

### UI — Tome of the Deep Seam
- [x] Network channel (`ModNetwork`) — five payloads on one channel (#33). Purchase and tradeoff
      toggle are server-authoritative and live; the clientbound progress and reset-vote payloads
      have their final shape and are handled on the client as of [39] (#40); reset voting is
      refused until the state machine in #94. Refund has no packet yet — it needs the Tome to
      have somewhere to trigger it from ([35])
- [x] Client entrypoint (`OreVaultClient`) + client packet handlers (`ClientPacketHandlers`) (#40)
      — a `@Mod(dist = CLIENT)` class the JVM never loads on a dedicated server, which is a
      stronger guarantee than the `FMLEnvironment` check it replaced. Synced team progress is
      stored for the Tome screens to read; screen registration lands with [34]/[35]/[38]
- [x] Tome item (auto-given on first join; recipe is [67]) (#34) — right-click opens the screen
      through an opener the client installs; until [34] lands it says so rather than doing nothing.
      Kept out of the offhand by canEquip plus a server-side inventoryTick eviction
- [x] Main screen shell (Resonance tab for 1.0; Animus and Ore Memory tabs land with their epics)
      (#35) — vanilla `TabNavigationBar`/`TabManager`, all three tabs present and switching, each
      one a placeholder that names what will fill it. `TomeTab` adds the body draw call vanilla's
      `Tab` lacks, since two of the three pages are a node graph rather than a column of widgets
- [x] Resonance tree tab — hub renderer (#136, #147). The grid version shipped in #36 and was replaced
      on playtest: it stacked unrelated nodes, implying prerequisites that did not exist, and its edges
      ran to node centres so lines crossed the boxes. A band-and-lane layout replaced it and was itself
      rejected on playtest for reading as a list. The layout is now a hub per cluster — anchor at the
      centre, nodes on rings around it, hubs down one spine — per §6.1 and §8. Edges terminate at box
      borders and travel only along a node's own ray, an arc inside a ring gap, or a corridor, so none
      can cross a box. Boxes size to the widest line they will ever draw, not to the node's name, and
      the page is opaque so one-pixel edges do not compete with the world behind the screen.
      Panning, scrolling and click-to-purchase from #36 carry over; the tab opens centred on the first
      hub. The page is 944 by 6008 pixels, which is the price of a radial cluster — see `DESIGN_LOG.md`
- [x] Node locked/unlocked/toggleable visual states — state by colour landed in #36 (gold maxed,
      green owned, cyan tradeoff active, white available, grey locked) with the lock reason named in
      the tooltip. #136 added the per-class ring: keystone, pact, growth, notable and fork each get a
      second outline inside the state border, so class stays legible in every purchase state. The
      craggly per-class *frames* are art and belong to #69
- [x] Fork indicator (#136) — a bought but unspecialized parent reads "Not specialised" on the node
      itself rather than only in its tooltip, since a paid node that is lit up and does nothing is the
      state most likely to be reported as a bug. Siblings show as locked once one option is picked
- [x] Anchor nodes (#136) — wide, centred, showing their points-spent gate and whether it is met.
      Drawn from `Cluster` rather than from `NodeDefs`, and unreachable from the hit test, so no click
      path can offer to buy one
- [x] Exclusive node lock indicator (#36) — a mark on the node whose partner is already owned, plus
      the reason in the tooltip
- [ ] Keystone visual treatment (distinct from small nodes and notables)
- [ ] Tradeoff toggle disabled with a reason while inside the Vault *(server refuses it (#104); the
      screen does not yet grey the toggle out ahead of the refusal)*
- [x] Ultimine node conditional visibility (#36) — Ultimine nodes are dropped from the node list
      before layout, so they leave no gap. Read from the client's own mod list until `SoftDeps` ([41])
- [x] Team Resonance level bar and skill point display (#35) — the Tome's header, one row per
      tree: level, unspent points, a progress bar and the pool on hover. Drawn only once a sync
      has arrived; before that it says it is waiting rather than showing zeroes. The server
      pushes on login and on every Resonance gain
- [ ] Seismic Sense ore-density readout (gated on the node)
- [ ] Vault reset button (conditional on Tier 4 igniter)
- [ ] Reset voting dialog
- [ ] Reset countdown warning

### Chunk Loading
- [ ] Vault Anchor block
- [ ] Vault Anchor crafting recipe
- [ ] Forge ticket registration on placement
- [ ] Forge ticket deregistration on removal
- [ ] Max tickets per team enforcement (Vault Presence node)
- [ ] Admin config hard ceiling
- [ ] Cross-dimension block-break Resonance award (Automated Extraction node)

### Integrations
- [ ] FTB Ultimine detection and conditional hook registration
- [ ] Mekanism detection and conditional Ore Doubling tier activation
- [ ] Mekanism ore processing lookup via reflection
- [ ] Generic dust mod detection via Forge tags
- [ ] Ore Doubling fallback chain (Mekanism → dust → raw ore)

### Config
- [x] `orevault-server.toml` generated with defaults
- [x] `vault_presence_enabled` toggle
- [x] `max_loaded_chunks_per_team` ceiling
- [x] `overrides` ore classification map
- [x] `max_zones_per_team` limit
- [x] `allow_backup_on_reset` toggle

### Polish
- [ ] Portal activation particles
- [ ] Teleport flash effect
- [ ] Vault Echo Resonance burst visual
- [ ] Twin Veins trigger flash
- [ ] Volatile Veins vein-disappearance sound/particle
- [ ] All crafting recipes
- [ ] `en_us.json` lang file (all block names, item names, node names, tooltips)
- [ ] Mod icon (`pack.png`)
- [ ] Block and item textures — **64×64 preferred, 32×32 the hard floor** for every Ore Vault
      asset ([68]). Vanilla is 16×16; Minecraft renders higher-resolution textures without a
      resource pack, so this is a choice about how the mod looks rather than a limitation to work
      around. It applies to anything shipped under `assets/orevault/textures/`. It does **not**
      apply to vanilla sprites the mod borrows — the Resonance orb tints
      `minecraft:textures/entity/experience/experience_orb.png` and ships no texture of its own,
      and re-sizing a vanilla sprite is not ours to do
- [ ] Tome node art — the skill-tree nodes want **craggly, stained-paper** frames rather than the
      plain filled boxes they have now: something that reads as a page in an old miner's book. The
      Tome's top bar is already right and should not change. **Readability wins over texture** —
      node name, tier and cost stay legible at every GUI scale, so the paper is a frame and a wash,
      not a busy fill behind text. A frame per node class (small / anchor / notable / keystone /
      fork parent / fork option) is what §8's class treatments need to hang on
- [ ] Translucent portal texture (nether-portal-style see-through, gray theme)
- [ ] Blockstate JSONs
- [ ] Model JSONs

---

*End of Ore Vault Design and Specification Document v1.0*
