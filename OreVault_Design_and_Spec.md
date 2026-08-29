# Ore Vault — Technical Design & Specification Document

> **Version:** 1.0 — Pre-Development  
> **Target:** Minecraft 26.1, Forge  
> **Hard Dependencies:** FTB Teams  
> **Soft Dependencies:** FTB Ultimine, Mekanism, generic ore-dust mods  
> **Book Item:** Tome of the Deep Seam  
> **Last Updated:** Pre-development brainstorm complete  

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

Dimensions are created dynamically the first time any member of a team activates a portal. The dimension type JSON is static (defined once), but the chunk generator is a custom implementation that reads the team's current skill tree state at chunk generation time, allowing node purchases to affect newly generated chunks immediately without a server restart.

**Dimension type properties (base: `min_y: -64`, `height: 384`, `logical_height: 384`):**
- `ambient_light: 1.0` — full universal brightness, no torches needed
- `visual/sky_light_factor: 1.0` + `visual/ambient_light_color` — fullbright terrain (MC 26.1 renders block brightness from environment attributes; `ambient_light` alone no longer lights terrain)
- `fixed_time: 6000` — always midday aesthetically
- `monster_spawn_light_level: 0` — monsters would only spawn in total darkness, which the fullbright environment never has
- `has_raids: false`
- `bed_works: false` / `respawn_anchor_works: false`
- `natural: false` — disables passive mob spawning, sleep and spawn protection

**World generation (overworld-style layering, data-driven — #76):**
- The layer stack is a bottom-up list of `{block, thickness}` pairs, jamd-style, loaded per dimension type from `data/orevault/worldgen/vault_layers/<type>.json` and validated against the dimension height; missing/malformed configs fall back to the classic stack
- Classic stack (base type): bedrock Y=-64, deepslate Y=-63…-1, stone Y=0…245, dirt band Y=246…249, grass surface Y=250, open air Y=251…319 (69 blocks of open working space)
- Default entry point: the heightmap surface at the mirrored XZ (first air block above the ground, verified 2-high), falling back to feet at Y=251; the exit portal is built standing on that surface
- No aquifers, no caves by default (open to adding cave generation as a future node)
- Ore generation handled entirely by the custom chunk generator (ores replace stone inside the configured stone band), not static placed features
- A hard floor of 40% stone content is enforced regardless of skill tree state — the Vault will never be more than 60% ore by volume

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
- `entityInside()` handles teleportation (players only); FTB team required (teamless players get a hint instead of portal charge-up)
- `updateShape()` breaks to air if a neighbouring block is neither Vault Frame nor portal block (whole-frame re-validation)
- Teleport cooldown: 80 ticks (4 seconds) using vanilla `portalCooldown`; Tier 4 igniter holders skip the wait and the cooldown entirely

**Teleportation Logic**
```
If player is in Ore Vault dimension:
    → retrieve saved return position from player persistent data
    → teleport to Overworld at saved position (fallback: world spawn)
Else (requires an FTB team):
    → save current position to player persistent data
    → find or create team's Vault dimension
    → ensure the team's exit portal exists at the Vault's default entry point (auto-built 4×5 frame with 2×3 portal, offset from the arrival point)
    → teleport to Vault at mirrored XZ, standing on the grass surface (top of the solid fill, one block below the air layer)
```

Return position is stored in `player.getPersistentData()` under key `orevault_return` as an NBT compound with x/y/z integers. This survives death and dimension changes. The saved spot is walked out along the approach direction past the last portal block, so returning never instantly re-triggers the portal.

> **Ore-block-look portals (investigation, #84):** the portal interior is a flat translucent plane (nether-portal style), so a literal ore-block appearance would need a full-cube model or a block-entity renderer — losing the flat translucency. Conclusion: keep the tinted plane; an "ore-look" would be done as a texture swap (ore-vein pattern on the plane), not a block-shaped portal.

> **Spawn safety (implementation note):** the open air layer (§3.1) means the default entry needs no terrain carving — the player arrives standing on the deepslate surface in open air. Custom entry points (Tier 3+) scan upward from the stored block for a 2-block air pocket, carving one only if none is found nearby (e.g. the entry was set against a solid wall).

---

### 3.3 Vault Igniter Tiers

The Vault Igniter replaces Flint & Steel as the portal activation item. Four tiers, each crafted from the previous tier plus additional materials.

**Tier 1 — Crude Vault Igniter**
- Recipe: Iron ingot + Redstone dust (shaped, horizontal)
- Function: Opens the portal, standard activation
- No special effects

**Tier 2 — Attuned Vault Igniter**
- Recipe: Crude Vault Igniter + Gold ingot + 4 Resonance Crystals (craftable from accumulated... see note below)
- Portal opens with a particle burst on activation
- Player receives Speed I for 5 seconds on entering the Vault
- Portal activation animation is 30% faster

**Tier 3 — Resonant Vault Igniter**
- Recipe: Attuned Vault Igniter + Diamond + 8 Resonance Crystals
- Grants Haste I for 10 seconds on entering the Vault
- Unlocks ability to set a custom entry point inside the Vault (right-click a block inside the Vault while holding the igniter to set it as personal spawn point)
- Custom entry point stored per-player in persistent data

**Tier 4 — Sovereign Vault Igniter**
- Recipe: Resonant Vault Igniter + Netherite ingot + 16 Resonance Crystals
- Grants Haste II for 15 seconds on entering the Vault
- Teleportation is instant with no cooldown: skips the 4-second portal wait and the re-entry cooldown entirely
- Unlocks the Vault Reset button in the Tome of the Deep Seam UI
- Without a Tier 4 igniter, the reset button does not appear in the UI

> **Resonance Crystals:** A craftable item representing condensed Resonance. Crafted at a ratio defined in config (default: 100 Resonance worth of... ). Actually Resonance is not a physical item — it is a team-pool counter. Resonance Crystals should instead be crafted from materials that feel thematically appropriate, such as: Amethyst Shard + Iron Nugget. The exact recipe is a design decision to finalise in implementation. The key point is that Igniter tiers require both a crafting cost and progression — the Tier 4 igniter being a significant material investment.

---

### 3.4 Chunk Loading

**Forge Ticket System**
The mod registers Forge chunk loading tickets for chunks inside the Vault that contain an active Vault Anchor block. Tickets persist until the anchor is removed or the server restarts.

**Vault Anchor Block**
- Crafted from Vault Frame blocks + Resonance Crystal
- Placeable inside the Vault only
- Registers a chunk loading ticket for its chunk on placement
- Deregisters on removal
- Serves dual purpose: chunk loader and personal waypoint (right-click to set as return point with Tier 3+ igniter)
- Maximum simultaneous tickets per team determined by Vault Presence skill node (see skill tree)
- Admin config can set a hard ceiling on max tickets regardless of node level

**Cross-Dimension Behaviour**
When a player is in the Overworld and a machine breaks a block in a ticket-loaded Vault chunk, the Automated Extraction node (if unlocked) awards Resonance to the team pool for that block. The block break event fires normally on the server regardless of which dimension the player is in, as long as the chunk is loaded.

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
5. The dimension is re-registered fresh (same registry key, new generation state)
6. Team skill tree progress, Resonance pool, Animus pool, and all skill point investments are fully preserved
7. A server log entry records the reset with timestamp and team ID

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

**Team pool scaling with diminishing returns:**
The team pool accrues Resonance as the sum of all members' gains, but with a soft diminishing return on the multiplier as team size grows. Formula:
```
effectiveMultiplier = 1 + (teamSize - 1) * 0.7
```
So a team of 1 gets 1.0x, team of 2 gets 1.7x, team of 3 gets 2.4x, team of 5 gets 3.8x. Raw additive would be 5.0x for a team of 5 — this keeps large teams from trivialising the progression timeline.

### 4.3 Level Thresholds and Skill Points

**Dynamic calculation (performed in code at startup, not configured manually):**

1. At startup, sum the skill point cost of every node at every tier in the Resonance tree. Call this `totalResonanceTreeCost`.
2. From config, read `targetPlayHoursResonance` (default: 100) and `assumedTeamSize` (default: 2.5).
3. Calculate average Resonance per hour: `resonancePerHour = averageOresPerHour * weightedAverageResonancePerOre * effectiveMultiplier(assumedTeamSize)`.
4. Total Resonance needed: `totalResonance = resonancePerHour * targetPlayHoursResonance`.
5. Total levels needed = `totalResonanceTreeCost` (one skill point per level).
6. Distribute `totalResonance` across levels using an exponential curve, where early levels are cheap and late levels are expensive. The curve formula:
```
levelCost(n) = baseCost * (growthFactor ^ n)
```
Where `baseCost` and `growthFactor` are derived to make the sum across all levels equal `totalResonance`.

7. This array of level costs is computed once at server start and stored. Adding or removing nodes from the tree automatically recalibrates all costs.

**Skill point award:** When the team's cumulative Resonance crosses the threshold for the next level, the team automatically receives one skill point. A toast notification appears for all online team members. The Resonance pool is not reset — it continues accumulating past each threshold.

### 4.4 Spending Skill Points

Skill points are spent in the Resonance tree tab of the Tome of the Deep Seam. Clicking an available node spends one skill point (or more for premium nodes — see node definitions) and unlocks that node immediately. Effects take place immediately. For nodes that affect chunk generation, newly generated chunks will reflect the node's effect; already-generated chunks are unchanged.

**Refund:** Node-by-node refund is available at a cost of Minecraft XP. Refund cost = `(totalSkillPointsInvestedInTree / totalTreeCost) * maxRefundXPCost`. `maxRefundXPCost` is a constant set in code (suggested: 50 XP levels for a fully invested tree). This means refunding a node early is cheap; refunding when the tree is nearly full is expensive.

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

Identical dynamic calculation to Resonance, using:
- `totalAnimusTreeCost` (sum of all mob tree node costs)
- `targetPlayHoursAnimus` (default: 100, can differ from Resonance target)
- `assumedTeamSize`
- Average Animus per hour based on mob kill rates in a Disturbed Zone

The two level tracks (Resonance level and Animus level) are completely independent. A team could be Resonance level 15 and Animus level 3 if they mostly mine.

### 5.3 Spending Animus Skill Points

Identical to Resonance: one point per level, spent in the Mob tree tab. Refund costs scale with total points invested in the Mob tree specifically, not the combined total.

---

## 6. Skill Trees — Complete Node Definitions

### Notation

- **Cost:** Skill points required to purchase this tier
- **Level req:** Minimum team Resonance or Animus level required to purchase this tier
- **Prereq:** Nodes that must be unlocked before this tier is available
- Nodes marked **[TRADEOFF]** are toggleable on/off at any time with no cost
- Nodes marked **[EXCLUSIVE: X]** cannot be active simultaneously with node X; unlocking one locks the other until refunded
- Nodes marked **[ULTIMINE]** only appear if FTB Ultimine is loaded
- All costs and level requirements are defined as constants in a single `NodeCosts.java` file for easy adjustment

---

### 6.1 Resonance Tree

#### BRANCH: Core (prerequisite for all other branches)

---

**Disturbed Zone Unlock**
> Unlocks the Disturbed Zone block, allowing it to be crafted and placed inside the Vault. Required to access the mob farming system.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Disturbed Zone block craftable | 1 | 0 | None |

---

#### BRANCH: Vein

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
> Shifts ore generation weighting toward lower Y levels where deposits become especially dense below Y=30.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Moderate shift toward lower Y | 2 | 5 | Vein Proliferation T2 |
| 2 | Strong shift; below Y=30 has 2x standard density | 3 | 9 | Tier 1 |

---

**Twin Veins**
> When a vein is fully mined to completion (no more connected ore blocks of that type remain), there is a chance a second identical vein spawns adjacent to the mined area with a visual flash effect.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 1% chance on vein completion | 2 | 6 | Vein Expansion T3 |
| 2 | 5% chance on vein completion | 2 | 10 | Tier 1 |
| 3 | 10% chance on vein completion | 3 | 14 | Tier 2 |

---

**Vault Echo**
> When a vein is fully mined, a Resonance burst is awarded.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25 Resonance burst on vein completion | 1 | 3 | Vein Expansion T2 |
| 2 | +35 Resonance burst | 1 | 6 | Tier 1 |
| 3 | +50 Resonance burst | 2 | 9 | Tier 2 |

---

#### BRANCH: Ore Quality

**Common Ore Boost**
> Increases spawn rates of common ores (dynamically classified at server start).

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25% common ore vein count | 1 | 1 | None |
| 2 | +50% common ore vein count | 1 | 3 | Tier 1 |
| 3 | +80% common ore vein count | 2 | 6 | Tier 2 |

---

**Uncommon Ore Boost**
> Increases spawn rates of uncommon ores.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25% uncommon ore vein count | 1 | 2 | Common Ore Boost T1 |
| 2 | +50% uncommon ore vein count | 2 | 5 | Tier 1 |
| 3 | +80% uncommon ore vein count | 2 | 8 | Tier 2 |

---

**Rare Ore Boost**
> Increases spawn rates of rare ores.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +30% rare ore vein count | 2 | 5 | Uncommon Ore Boost T1 |
| 2 | +60% rare ore vein count | 2 | 9 | Tier 1 |
| 3 | +100% rare ore vein count | 3 | 13 | Tier 2 |

---

**Ancient Traces**
> Adds ancient debris to Vault generation.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Small ancient debris veins generate at low Y | 5 | 12 | Rare Ore Boost T2 |
| 2 | Increased ancient debris frequency | 5 | 16 | Tier 1 |

---

**Gravel Purge**
> Removes gravel and clay from Vault generation in newly explored chunks.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | No gravel or clay generates | 1 | 1 | None |

---

**Stone Reduction**
> Replaces a portion of filler stone with ore-bearing rock, increasing ore surface area.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 10% of filler stone replaced with ore-adjacent stone | 1 | 3 | Gravel Purge |
| 2 | 20% replaced | 2 | 7 | Tier 1 |

---

**Geode Clusters**
> Amethyst geodes generate inside the Vault at higher frequency than the overworld.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Moderate geode frequency | 1 | 4 | Stone Reduction T1 |
| 2 | High geode frequency | 2 | 8 | Tier 1 |

---

#### BRANCH: Fortune

**Ore Sense**
> Grants a passive Fortune effect to all ore mining inside the Vault. Stacks additively with tool enchantments.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Fortune I equivalent | 2 | 5 | Vein Proliferation T1 |
| 2 | Fortune II equivalent | 3 | 9 | Tier 1 |
| 3 | Fortune III equivalent | 4 | 13 | Tier 2 |

---

**Ore Doubling**
> Ore drops are multiplied. Fallback order: Mekanism processing output → mod ore dust → raw ore double. Output is always equivalent to 2x the raw ore at maximum, regardless of processing chain. For ores with multiple byproducts, only the primary raw material is doubled by this node (extra raw ore, not processed byproducts).

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 25% chance of bonus ore (or dust/processed equivalent) for 1.2x | 3 | 8 | Ore Sense T1 |
| 2 | 50% chance for 1.2x| 3 | 12 | Tier 1 |
| 3 | 75% chance for 1.2x| 4 | 16 | Tier 2 |

> **Mekanism tiers (only if Mekanism is loaded):**

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 4 | Drops Clumps (3x) | 7 | 20 | Tier 3 |
| 5 | Drops Shards (4x) | 10 | 25 | Tier 4 |
| 6 | Drops Crystals (5x) | 15 | 30 | Tier 5 |

---

**Runic Attunement**
> Ore drops have a small chance to be "attuned," granting bonus effects when processed by magic-based ore processing mods. If no magic processing mod is present, attuned ores function identically to normal ores.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 5% chance ore drops are attuned | 3 | 10 | Ore Doubling T1 |
| 2 | 12% chance | 3 | 14 | Tier 1 |
| 3 | 20% chance | 4 | 17 | Tier 2 |

---

**Smelter's Intuition**
> A chance that ore blocks drop the already-smelted result rather than raw ore.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 5% chance of smelted drop | 2 | 7 | Ore Doubling T1 |
| 2 | 15% chance | 2 | 11 | Tier 1 |
| 3 | 30% chance | 3 | 15 | Tier 2 |

---

#### BRANCH: XP and Stone

**Stone Memory**
> Stone and deepslate drop XP and provide additional benefits at higher tiers when mined inside the Vault.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Stone drops 1 XP when mined | 1 | 0 | None |
| 2 | +2 XP; stone occasionally drops flint | 1 | 3 | Tier 1 |
| 3 | +3 XP; deepslate drops a small amount of Resonance | 2 | 6 | Tier 2 |
| 4 | +4 XP; small chance stone drops a random common ore nugget | 2 | 10 | Tier 3 |
| 5 | +5 XP; rare chance stone triggers a Vault Echo-equivalent Resonance burst | 3 | 14 | Tier 4 |

---

**Ancient Knowledge**
> Ore blocks drop bonus vanilla XP orbs in addition to standard amounts.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +1 XP per ore mined | 1 | 2 | Stone Memory T1 |
| 2 | +2 XP per ore mined | 1 | 5 | Tier 1 |
| 3 | +4 XP per ore mined | 2 | 9 | Tier 2 |

---

#### BRANCH: Hunger

**Efficient Miner**
> Reduces hunger drain inside the Vault, with additional effects at higher tiers.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | −20% hunger drain | 1 | 0 | None |
| 2 | −45% hunger drain; food restores 20% more saturation inside the Vault | 1 | 3 | Tier 1 |
| 3 | −65% hunger drain; eating grants brief Regeneration I | 2 | 6 | Tier 2 |
| 4 | −85% hunger drain; no starvation damage (hunger can reach 0 but won't damage) | 2 | 10 | Tier 3 |
| 5 | Hunger and saturation frozen completely; eating still grants the Regeneration I from Tier 3 | 3 | 15 | Tier 4 |

---

#### BRANCH: Utility

**Resonance Magnetism** `[EXCLUSIVE: Hoarder's Instinct]`
> Vault Resonance orbs are attracted to the player from greater distances.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Orb attraction radius: 8 blocks | 1 | 2 | None |
| 2 | Orb attraction radius: 16 blocks | 1 | 5 | Tier 1 |
| 3 | Orb attraction radius: 24 blocks | 2 | 9 | Tier 2 |

---

**Hoarder's Instinct** `[EXCLUSIVE: Resonance Magnetism]`
> Resonance orbs do not float toward the player automatically. Instead, orbs manually walked over grant a 2x Resonance bonus. Rewards deliberate collection.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 2x Resonance on manual collection | 2 | 4 | None |

---

**Automated Extraction**
> Ore blocks broken by non-player means (machines, drills, etc.) inside ticket-loaded Vault chunks still award Resonance to the team pool.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Automated mining awards 50% of normal Resonance | 2 | 8 | Vault Presence T1 |
| 2 | Automated mining awards 100% of normal Resonance | 3 | 12 | Tier 1 |

---

**Vault Presence**
> Increases the number of Forge chunk-loading tickets the team's Vault can maintain simultaneously, enabling larger automated operations.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +4 simultaneous loaded chunks | 2 | 5 | None |
| 2 | +8 simultaneous loaded chunks (12 total) | 2 | 9 | Tier 1 |
| 3 | +16 simultaneous loaded chunks (28 total) | 3 | 14 | Tier 2 |

> Admin config can set a hard ceiling on maximum loaded chunks regardless of node level. Default ceiling: 32.

---

**Vault Expansion** *(Keystone)*
> Expands the Vault's vertical height from Y=0–255 to Y=-64–320, matching modern world height. Unlocks a new ultra-deep layer below Y=0 with the highest ore density. Requires a dimension reset to take effect. The reset button prompt will inform the player of this.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 (only tier) | Vault height expanded; ultra-deep layer below Y=0 unlocked after reset | 10 | 18 | Rare Ore Boost T3, Vein Expansion T5, Efficient Miner T4 |

---

#### BRANCH: FTB Ultimine `[ULTIMINE ONLY — hidden if FTB Ultimine not loaded]`

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

#### TRADEOFF NODES (Resonance Tree)

Tradeoff nodes are toggled on/off by the player at any time at no cost. Toggle state is saved per-player (not per-team — one player can run a tradeoff another doesn't want).

---

**Volatile Veins** `[TRADEOFF]`
> Increases vein size by 25%, but each ore broken has a small chance of causing the remaining connected vein to vanish instantly, replaced with air, with no drops. A pity counter prevents more than three consecutive triggers; after three triggers the next several ore breaks are guaranteed safe. The pity counter resets on logout.
> When using FTB Ultimine, the disappearance roll occurs once per Ultimine operation rather than per block.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +25% vein size, 1–3% disappearance chance (balance TBD) | 2 | 6 | Vein Expansion T2 |

---

**Volatile Veins: Ultimine Gambit** `[TRADEOFF] [ULTIMINE ONLY]`
> When using FTB Ultimine with Volatile Veins active, the effective block count for disappearance checks is increased by 1 (as if one extra block was mined), increasing the risk. In exchange, successful Ultimine operations that don't trigger disappearance award a 20% Resonance bonus.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Riskier Ultimine, 20% Resonance bonus on safe operations | 2 | 9 | Volatile Veins, Ultimine Expansion T1 |

---

**Greedy Seams** `[TRADEOFF]`
> Ore drops are doubled, but Resonance gained from each ore is halved.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 2x ore drops, 0.5x Resonance gain | 2 | 5 | Vein Expansion T1 |

---

**Stone Curse** `[TRADEOFF]`
> Stone Memory XP gain is tripled, but stone drops no items (no cobblestone, no flint, no nuggets).

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 3x Stone Memory XP, stone drops nothing | 2 | 4 | Stone Memory T2 |

---

**Vault Fever** `[TRADEOFF]`
> Grants permanent Haste II inside the Vault, but hunger drains faster.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | Haste II, +50% hunger drain rate | 2 | 7 | Efficient Miner T2 |

---

**Tithe** `[TRADEOFF]`
> 25% of ore blocks mined are consumed by the Vault (block breaks, no drop). The Resonance value of the consumed ore is multiplied by 1.75 and added to the pool. Only affects ore blocks — Stone Memory bonus drops, nuggets, flint, and other secondary sources are unaffected.
> In-game tooltip explicitly states: "Does not affect bonus drops from Stone Memory or other secondary sources."

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | 25% ore consumed, 1.75x Resonance on consumed ores | 2 | 5 | None |

---

#### EXCLUSIVE NODE PAIRS (Resonance Tree)

**Abundance** `[EXCLUSIVE: Motherlode]`
> Vein count per chunk increased significantly. Many small deposits scattered throughout.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +50% vein count (stacks with Vein Proliferation, but both are subject to 40% stone floor) | 3 | 7 | Vein Proliferation T2 |
| 2 | +100% vein count | 4 | 12 | Tier 1 |

---

**Motherlode** `[EXCLUSIVE: Abundance]`
> Vein size greatly increased, vein count halved. Rare but enormous deposits.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +100% vein size, −50% vein count (stacks with Vein Expansion and Proliferation) | 3 | 7 | Vein Proliferation T2 |
| 2 | +150% vein size, −50% vein count | 4 | 12 | Tier 1 |

> **Important implementation note:** Abundance and Motherlode must be treated as overrides to the base vein count/size after all other modifiers are applied. The 40% stone floor still applies.

---

**Vault's Blessing** `[EXCLUSIVE: Vault's Purity]`
> All potion effects active when entering the Vault last 50% longer while inside.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | +50% effect duration | 2 | 8 | None |

---

**Vault's Purity** `[EXCLUSIVE: Vault's Blessing]`
> All potion effects are stripped on entry to the Vault and cannot be applied while inside. The Vault is clean — pure skill, no potions.

| Tier | Effect | Cost | Level Req | Prereq |
|---|---|---|---|---|
| 1 | No potion effects inside the Vault | 2 | 8 | None |

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

- Visual node graph rendered with connecting lines showing prerequisites
- Nodes display: name, current tier, max tier, skill point cost, level requirement, and a short description
- Locked nodes are dimmed with a tooltip explaining what is required to unlock
- Tradeoff nodes show a toggle switch UI element
- Exclusive nodes show a lock icon when the conflicting node is active
- Ultimine nodes hidden entirely if FTB Ultimine not loaded
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
- Dimension created on first portal activation by any team member
- Dimension deleted on team disband event (listen to FTB Teams disband event)
- Solo players use their auto-created single-member FTB team

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

- At server start, scan the Forge item tag `forge:dusts/<oreName>` for each classified ore block
- If a dust tag exists for an ore, Ore Doubling tiers 1-3 drop dust instead of raw ore
- If no dust tag exists, drop raw ore
- No specific mod is referenced; this works with any mod that registers proper Forge dust tags

---

## 10. Configuration

Configuration is intentionally minimal. Most values are derived dynamically from the defined skill tree. Admins can control only things with server performance implications.

**Config file:** `orevault-server.toml` (server-side only, not synced to clients)

```toml
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

**Values derived automatically (not configurable):**
- Level thresholds for Resonance and Animus (derived from tree structure)
- Level cap (derived from tree structure)
- Node costs (defined as constants in `NodeCosts.java`)
- Stone content floor (40%, hardcoded constant)

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
    UUID teamId;
    long resonancePool;
    long animusPool;
    int resonanceLevel;
    int animusLevel;
    int resonanceSkillPoints;
    int animusSkillPoints;
    Map<NodeId, Integer> unlockedNodes; // NodeId -> tier
    Set<NodeId> activeTradeoffs;
    Map<UUID, PlayerStats> playerStats; // player UUID -> stats
    int chunkLoadingTickets;
}
```

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

Forge 26.1 supports dynamic dimension registration via `ServerLifecycleEvents` or by adding dimensions to the server's registry before world load. The recommended approach is to register all team dimensions at server start by iterating existing teams, then handle new team creation via event. Dimension deletion is done via the level's storage and removing the registry entry — requires careful handling to avoid registry desync.

### Resonance Orb Entity

Subclass or repurpose `ExperienceOrb` for Resonance and Animus orbs. Override the pickup behaviour to add to the team pool instead of player XP. Use a custom renderer (or simply recolour via render type) to distinguish from vanilla XP — suggested colours: Resonance = blue/cyan, Animus = red/dark red.

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

Work through these sessions with Claude Code. Start each session by instructing the agent to read this document before writing any code.

| Session | Focus |
|---|---|
| 1 | Project scaffold, Forge 26.1 setup, FTB Teams hard dependency, dimension registration skeleton |
| 2 | Vault Frame block, portal block, VaultPortalShape scanner, igniter item tiers 1-4 |
| 3 | Teleportation logic, return position persistence, per-team dimension routing |
| 4 | Resonance orb entity, Resonance SavedData, level threshold dynamic calculator |
| 5 | Animus orb entity, Animus SavedData, parallel level system |
| 6 | Ore rarity classifier, custom chunk generator, dynamic ore placement |
| 7 | Skill tree data structures, NodeCosts.java, prerequisite graph, unlock validation |
| 8 | Resonance tree node effects (world-gen affecting nodes) |
| 9 | Resonance tree node effects (player-affecting nodes: hunger, fortune, XP, tradeoffs) |
| 10 | Disturbed Zone block, mob spawn logic, Animus tree node effects |
| 11 | Tome of the Deep Seam UI — item, screen, three tabs, node graph renderer |
| 12 | Ore Memory tab — stat tracking, per-player stats, trophy system |
| 13 | Vault reset system — voting logic, countdown, chunk deletion, dimension re-registration |
| 14 | Chunk loading — Vault Anchor block, Forge ticket registration, Vault Presence nodes |
| 15 | FTB Ultimine integration — conditional node registration, event hooks |
| 16 | Mekanism integration — reflection-based processing lookup, Ore Doubling tiers 4-6 |
| 17 | Generic dust mod integration — Forge tag scanning, Ore Doubling fallback chain |
| 18 | Particles and sounds — portal activation, teleport flash, Vault Echo burst, Twin Veins flash |
| 19 | Recipes, advancements, lang file, loot tables |
| 20 | Testing, JUnit for pure logic systems, compile verification, KNOWN_ISSUES.md review |

---

## 13. Implementation Checklist

Use this to track progress. Update at the end of each development session.

### Infrastructure
- [x] Forge 26.1 project scaffold (build.gradle, settings.gradle, mods.toml)
- [x] FTB Teams hard dependency declared and verified
- [ ] Package structure created (`block`, `item`, `portal`, `worldgen`, `data`, `config`, `client`, `event`) — `data`/`config`/`event`/`skill`/`team`/`worldgen`/`ore` done; `block`/`item`/`portal`/`client` land in later phases
- [x] `OreVault.java` main mod class
- [x] `NodeCosts.java` constants file
- [x] `OreVaultTeamData` SavedData class
- [x] `PlayerStats` data class
- [ ] FTB Teams event hooks (CREATED, PLAYER_JOINED, DISBANDED) — CREATED + DISBANDED done; PLAYER_JOINED (Tome grant) pending Tome item
- [x] Per-team dimension key generation

### Dimension
- [x] Dimension type JSON (`ore_vault.json`, `ore_vault_expanded.json`)
- [x] Dynamic dimension registration per team
- [ ] Dimension deletion on team disband (deferred to `[31]` VaultReset; TODO in `VaultDimensions`)
- [x] Custom chunk generator skeleton
- [x] Open air layer at the top (64 blocks), stone below with deepslate surface
- [x] Ore rarity classifier (scans registry at server start)
- [x] Admin config override for rarity classification
- [ ] Dynamic ore placement from skill state (skill snapshot wired; node math deferred to `[44]`/`[45]`)
- [x] 40% stone content floor enforcement
- [ ] Vault Expansion keystone (height extension, post-reset)

### Portal and Igniter
- [x] Vault Frame block
- [ ] Vault Frame crafting recipe (8 iron + 1 redstone) — recipe lands in [67]
- [x] `VaultPortalShape` scanner (both axes, 2×3 to 21×21)
- [x] Ore Vault Portal block (no collision, unbreakable, AXIS state)
- [x] Portal frame integrity check (`updateShape`)
- [x] Vault Igniter Tier 1 item (recipe in [67])
- [x] Vault Igniter Tier 2 item and effects (recipe in [67])
- [x] Vault Igniter Tier 3 item and effects — custom entry point (recipe in [67])
- [x] Vault Igniter Tier 4 item and effects (reset-button gate in [31]; recipe in [67])

### Teleportation
- [x] Overworld → Vault routing (per team)
- [x] Vault → Overworld routing (return position)
- [x] Return position persistence (player persistent data)
- [x] Teleport cooldown (80 ticks, 0 for Tier 4 igniter)
- [x] Nether-style portal wait (80 ticks) with wavy overlay and travel sound
- [x] Team requirement: teamless players cannot activate or use a portal
- [x] Exit portal auto-built at the Vault's default entry point
- [x] Tier 2 Speed I on arrival
- [x] Tier 3 Haste I on arrival
- [x] Tier 4 Haste II on arrival
- [ ] [73] Vault Recall item — expensive consumable teleporting the user back to a saved point in the dimension it was saved in
- [ ] [74] Vault Return structure — dedicated exit block/keystone at the Vault's entry area (0,0 spawn platform), polished replacement for the auto-built exit portal

### Resonance System
- [ ] Resonance orb entity (visual distinction from XP)
- [ ] Resonance orb spawns on ore break in Vault
- [ ] Orb floats to nearest team member in radius
- [ ] Team Resonance pool accumulation
- [ ] Dynamic level threshold calculator
- [ ] Skill point award on level-up
- [ ] Level-up toast notification
- [ ] Resonance Magnetism node (orb radius modification)
- [ ] Hoarder's Instinct node (manual collection 2x)
- [ ] Tithe node (25% consume, 1.75x Resonance)
- [ ] Tithe tooltip clarification in UI

### Animus System
- [ ] Animus orb entity (visual distinction)
- [ ] Animus orb spawns on mob kill in Disturbed Zone
- [ ] Orb floats to nearest team member
- [ ] Team Animus pool accumulation
- [ ] Separate dynamic level threshold calculator for Animus
- [ ] Animus skill point award on level-up

### Skill Tree — Resonance Nodes
- [ ] Skill tree data structure and prerequisite graph
- [ ] Unlock validation (level req, prereq, exclusive conflicts, skill points)
- [ ] Node-by-node refund (scaled XP cost)
- [ ] Tradeoff toggle per-player persistence
- [ ] Exclusive node pair enforcement
- **Vein Branch**
  - [ ] Vein Expansion (T1-T5)
  - [ ] Vein Proliferation (T1-T5)
  - [ ] Deep Veins (T1-T2)
  - [ ] Twin Veins tradeoff (T1-T3)
  - [ ] Vault Echo (T1-T3)
- **Ore Quality Branch**
  - [ ] Common Ore Boost (T1-T3)
  - [ ] Uncommon Ore Boost (T1-T3)
  - [ ] Rare Ore Boost (T1-T3)
  - [ ] Ancient Traces (T1-T2)
  - [ ] Gravel Purge (T1)
  - [ ] Stone Reduction (T1-T2)
  - [ ] Geode Clusters (T1-T2)
  - [ ] Structural Echoes (T1-T2)
- **Fortune Branch**
  - [ ] Ore Sense (T1-T3)
  - [ ] Ore Doubling (T1-T3 base, T4-T6 Mekanism)
  - [ ] Runic Attunement (T1-T3)
  - [ ] Smelter's Intuition (T1-T3)
- **XP and Stone Branch**
  - [ ] Stone Memory (T1-T5, all tier effects)
  - [ ] Ancient Knowledge (T1-T3)
- **Hunger Branch**
  - [ ] Efficient Miner (T1-T5, all tier effects)
- **Utility Branch**
  - [ ] Resonance Magnetism (T1-T3)
  - [ ] Hoarder's Instinct (T1)
  - [ ] Automated Extraction (T1-T2)
  - [ ] Vault Presence (T1-T3)
  - [ ] Vault Expansion keystone
  - [ ] Disturbed Zone Unlock node
- **Tradeoff Nodes**
  - [ ] Volatile Veins (with pity system)
  - [ ] Greedy Seams
  - [ ] Stone Curse
  - [ ] Vault Fever
  - [ ] Tithe
- **Exclusive Pairs**
  - [ ] Abundance / Motherlode
  - [ ] Resonance Magnetism / Hoarder's Instinct
  - [ ] Vault's Blessing / Vault's Purity
- **Ultimine Branch (conditional)**
  - [ ] Ultimine Expansion (T1-T3)
  - [ ] Ultimine Safety (T1-T2)
  - [ ] Volatile Veins: Ultimine Gambit

### Skill Tree — Animus Nodes
- **Disturbed Zone Enhancement**
  - [ ] Zone Frequency (T1-T4)
  - [ ] Zone Pack Size (T1-T3)
  - [ ] Zone Radius (T1-T3)
  - [ ] Mob Diversity (T1-T4)
- **Mob Rewards**
  - [ ] Reaper's Claim (T1-T3)
  - [ ] Corrupted Veins (T1-T3)
  - [ ] Plunderer's Share (T1-T3)
  - [ ] Animus Amplifier (T1-T3)
  - [ ] Soul Harvest keystone

### Disturbed Zones
- [ ] Disturbed Zone block (craftable after unlock node)
- [ ] Disturbed Zone crafting recipe
- [ ] Spherical spawn zone logic
- [ ] Zone particle effect (boundary visualisation)
- [ ] Mob spawn in zone (vanilla mobs, respects Mob Diversity node)
- [ ] Mob kill Animus drop
- [ ] Zone stat tracking (for node effects)

### UI — Tome of the Deep Seam
- [ ] Tome item (auto-given on first spawn, craftable cobblestone + book)
- [ ] Main screen with three tabs
- [ ] Resonance tree tab — node graph renderer
- [ ] Node locked/unlocked/toggleable visual states
- [ ] Exclusive node lock indicator
- [ ] Ultimine node conditional visibility
- [ ] Team Resonance level bar and skill point display
- [ ] Animus tree tab — parallel to Resonance tab
- [ ] Ore Memory tab — team stats panel
- [ ] Ore Memory tab — player stats panel with member dropdown
- [ ] Trophy icon logic (highest per stat)
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
- [ ] Block and item textures — **at least 64×64** for all Ore Vault assets ([68])
- [ ] Translucent portal texture (nether-portal-style see-through, gray theme)
- [ ] Blockstate JSONs
- [ ] Model JSONs

---

*End of Ore Vault Design and Specification Document v1.0*
