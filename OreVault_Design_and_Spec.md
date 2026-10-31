# Ore Vault — Technical Design & Specification Document

> **Version:** 1.2 — Restart revision. The implementation was removed on 2026-10-25 and is being rebuilt; this document carries the design forward  
> **Target:** Minecraft 26.1, NeoForge  
> **Hard Dependencies:** FTB Teams  
> **Soft Dependencies:** FTB Ultimine, Mekanism, generic ore-dust mods; several pack mods by tags and data only (§9)  
> **Book Item:** Tome of the Deep Seam  
> **Last Updated:** 2026-10-27 — Nick's answers of 2026-10-27 to the ticket questions (team-voted generation tradeoffs, reset seed, on-screen Resonance bar, tooltips, reset vote and respec limits); before that, Nick's answers of 2026-10-26 and the Path of Exile style Resonance tree  

---

## How to Use This Document

This document is the single source of truth for the Ore Vault mod. It covers design intent, system architecture, every node definition, balance values, and implementation notes. Feed this file to Claude Code at the start of every development session before writing any code. A checklist section at the bottom tracks implementation progress.

Issue and ticket numbers written `#N` or `[N]` in §1 to §12 are **pre-restart**: they name the old issue tracker and the old plan's ids, and are kept because their commits and discussions are still in `git log`. Current work is numbered T1 to T14 (§12) and filed as stories; §13 names each story by its draft id and current issue, as `E3-S4 (#229)`, and writes a pre-restart issue as "old #N".

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
- **Long-term progression.** The skill tree is designed for 80–120 hours of team play to reach level 30, by which a team owns about 70% of it (§4.3). Early nodes provide immediate utility; late nodes are meaningful milestones.
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
| NeoForge Version | Latest stable for 26.1, pinned in the build |
| Java Version | 25 |
| Hard Dependencies | FTB Teams |
| Soft Dependencies | FTB Ultimine, Mekanism |

### Dependency Behaviour

**FTB Teams (hard dependency)**
- Mod will not load without it. Declared in `neoforge.mods.toml` as mandatory.
- Every system that involves player data, dimension ownership, Resonance pools, Animus pools, or skill tree state operates on the FTB Team ID.
- Solo players automatically have a single-member team and function normally.

**FTB Ultimine (soft dependency)**
- Detected at runtime via `ModList.get().isLoaded("ftbultimine")`.
- If absent: Ultimine-specific skill nodes do not appear in the UI at all. No greyed-out state, no tooltip, simply not rendered. They sit on a dead-end spur, so hiding them changes no other node's distance (§6).
- If present: Ultimine nodes appear in the Resonance tree and the Ultimine integration hooks are active.

**Mekanism (soft dependency)**
- Detected by tags, not by mod: the extra tiers appear when the `c:clumps/...`, `c:shards/...` and `c:crystals/...` item tags are non-empty (§9).
- If those tags are empty: Ore Doubling functions with raw ore / dust fallback only.
- If they are filled: Ore Doubling unlocks additional tiers using the processing outputs.
- No compile-time reference to Mekanism and no reflection. All integration goes through `c:` item tags (§9).

---

## 3. Core Systems

### 3.1 The Vault Dimension

**One dimension per FTB Team.** Dimension registry keys follow the pattern:
```
orevault:vault_<teamId>
```
Where `<teamId>` is the UUID of the FTB Team, stripped of hyphens.

**Dimensions are created lazily — on the first portal trip by a member of that team, and never before.** No dimension is created at server start, and none is created when a team is registered. This matters because FTB Teams auto-creates a single-member team for every player who logs in; eager creation would mean one full `ServerLevel` (chunk map, storage, region directory) per player account the server has ever seen, whether or not they use the mod.

**Restoration is eager.** Vaults already on disk are re-registered at server start, before anyone connects, because a player may have logged out inside one; otherwise they would reload at Vault coordinates in the Overworld sky. Creating and restoring are different paths (§11 Dimension-per-Team Registration).

The dimension type JSON is static (two variants, below), but the chunk generator is a custom implementation that reads a main-thread snapshot of the team's tree (§11) at chunk generation time, allowing node purchases to affect newly generated chunks immediately without a server restart.

**Dimension type variants.** Two types exist; a team's Vault uses the base type until the Vault Expansion keystone is purchased and the dimension is reset, at which point it is re-created under the expanded type. The only difference is how deep the world goes before bedrock:

| | `orevault:ore_vault` (base) | `orevault:ore_vault_expanded` |
|---|---|---|
| `min_y` | `0` | `-64` |
| `height` / `logical_height` | `320` | `384` |
| Bedrock floor | Y=0 | Y=−64 |
| Deepslate band | none | Y=−63…−1 — the densest ore band in the mod |

Shared fields for both types, in the 26.1 `DimensionType` codec (`net/minecraft/world/level/dimension/DimensionType.java`, quoted in `docs/research/neoforge-26.1-sources.md` "DimensionType codec fields"). The gameplay and visual rules are environment attributes under `attributes`, keyed by their full id:
- `ambient_light: 1.0` and `has_skylight: true`, with the attributes `minecraft:visual/sky_light_factor: 1.0` and `minecraft:visual/ambient_light_color` — fullbright terrain, no torches needed. 26.1 renders block brightness from these attributes; `ambient_light` alone no longer lights terrain (`docs/LESSONS_LEARNED.md`, "Vault lighting wrong"). The ambient light colour must be set: its default, `-16777216`, is opaque black (`EnvironmentAttributes.java` line 97). Its value is chosen in `runClient` by E3-S5 (#233); the old build used `#c8c8c8`
- `has_fixed_time: true` (a boolean, not a time of day) and no `timelines`, so the sun holds at the default `minecraft:visual/sun_angle` of 0.0, the midday position in vanilla's `day` timeline. That the sun then draws at midday is inferred from the data, not traced through the renderer: **unverified, to be checked in `runClient`** by E3-S5 (#233). With a fixed time it is never "dark outside", so a `when_dark` rule never fires
- `minecraft:gameplay/bed_rule: { "can_sleep": "never", "can_set_spawn": "never" }` — no sleeping and no spawn set in the Vault. `explodes` is left at its default, `false`, so a bed only says you cannot sleep here: the Vault is where teams build machines next to their ore, and the old build's `bed_works: false`, which made a bed explode as in the Nether, was never a choice the spec made. Nick chose no explosion on 2026-10-27
- `minecraft:gameplay/respawn_anchor_works: false` (also its default)
- `minecraft:gameplay/can_start_raid: false` — the default is `true`, so the Vault must set it
- `monster_spawn_light_level: 0` (an IntProvider; a constant 0) and `monster_spawn_block_light_limit: 0` — monsters could only spawn in total darkness, which the fullbright Vault never has
- Mob spawning is off because the Vault biome has empty spawner lists (`"spawners"` with every category empty, `"spawn_costs": {}`), not through the dimension type, which in 26.1 has no field for it. Whether another mod's NeoForge biome modifier can add spawns to the Vault biome is unverified until NeoForge's own sources are read (E1-S5, #195)
- The required fields with nothing Vault-specific: `has_ceiling: false`, `has_ender_dragon_fight: false`, `coordinate_scale: 1.0`, `infiniburn: #minecraft:infiniburn_overworld`, and `min_y`, `height` and `logical_height` from the table above. The last build's working file is `src/main/resources/data/orevault/dimension_type/ore_vault.json` at commit 9fc3086, which also set the sky and fog colours of old #83 (`minecraft:visual/sky_color` `#78a7ff`, `minecraft:visual/fog_color` `#c0d8ff`) and an ambient light colour of `#c8c8c8`, and still carried a pre-26.1 spawning key the 26.1 codec does not read

**World generation (overworld-style layering, data-driven — #76):**
- The layer stack is a bottom-up list of `{block, thickness}` pairs, jamd-style, loaded per dimension type from `data/orevault/worldgen/vault_layers/<type>.json` and validated against the dimension height; missing/malformed configs fall back to the built-in stack for that type
- Base stack (`ore_vault`, Y=0…319): bedrock Y=0, stone Y=1…245, dirt band Y=246…249, grass surface Y=250, open air Y=251…319 (69 blocks of open working space)
- Expanded stack (`ore_vault_expanded`, Y=−64…319): bedrock Y=−64, deepslate Y=−63…−1, then identical to the base stack from Y=0 upward
- Entry point: **the team anchor (X=0, Z=0)**, the Vault origin, one per team, standing on the grass surface. Vault arrival never mirrors or otherwise derives from Overworld coordinates — every trip in lands on the same block, and the return portal stands there permanently (§3.2)
- No aquifers, no caves by default (open to adding cave generation as a future node)
- Ore generation handled entirely by the custom chunk generator (ores replace the filler inside each mineralizable band), not static placed features
- Every vein lies inside one chunk (§11 Vein Index). Ore thins by a block or two toward chunk borders as a result
- A hard floor of 40% stone content is enforced regardless of skill tree state — the Vault will never be more than 60% ore by volume. It is enforced by scaling: every rarity's vein count in a band is multiplied by one factor, `min(1, 0.60 / densest)`, so the mix does not shift. `densest` is the highest ore density the team's generation nodes request in any one region they create in that band, not the band's average: Deep Veins' lowest 30 layers and a Vein Singularity chunk that carries 2.08 times the baseline both ask for more than the average, and a factor taken on the average would leave them above 60%. There is still one factor per band (stone, deepslate). The clip test checks every 16-layer slab of every chunk, not the band total. The factor is computed on the main thread and carried in the generation snapshot (§11; `docs/adr/0009-baseline-ore-model-and-classifier.md`). Gravel and clay are not scaled; they count as stone

**Baseline ore distribution (#152).** What a team with zero skill points spent sees. Node effects (#45, #46) modify these numbers; they do not replace them.

| | Value | Why |
|---|---|---|
| Stone band density | **3%** of the band, by placed blocks | About 1.6 to 1.9 times vanilla's Y0..63 band and 2.3 times its Y-64..63 average, measured in solid rock (`docs/research/vanilla-ore-generation.md`). Stone still dominates and a good vein is still an event; the skill tree is what makes a Vault rich, working toward the 40% floor above. Kept at 3% by Nick on 2026-10-26 over a proposed 4%. |
| Deepslate band density | **6%** | About seven times vanilla's deepslate (0.83%). §6.1 sells the expanded Vault's deepslate as the richest band in the mod, and Vault Expansion costs a keystone to reach. |
| Rarity mix, by placed blocks | Stone band **68% common, 25% uncommon, 7% rare**; deepslate **60/30/10** | The stone mix is vanilla's own below Y=64. Deepslate leans rare but less than vanilla's 21/58/21, which would make that band pay 2.6 times the stone band's Resonance an hour. §4.3's level curve is derived from these shares: the generator and the curve read one source. |
| Veins per chunk | Stone band about **141 common, 82 uncommon, 33 rare**; deepslate about **65, 52, 25** | Derived from the density, mix and vein sizes, allowing for overlap between veins (about 1.5% of blocks). Placed by count, never filled toward a target. |
| Vein size, as vanilla's configured `size` | Common **9–12** (mean 9.2 blocks, never empty), uncommon **7–9** (5.8 blocks), rare **5–7** (4.05 blocks, 3% empty) | `size` is not a block count: vanilla's shape places 6.8 blocks at size 9. These match vanilla's means below Y=64 (8.1, 5.7, diamond 3.9); rare uses 5–7 rather than diamond's 4, which is empty a quarter of the time. |
| Gravel and clay | Gravel **9 blobs** of configured size 33 (about 112 blocks) per chunk in the stone band and **2** in deepslate, about 1.6%; clay **3** and **1**, about 0.5% | Gravel at vanilla's rate. Underground clay in vanilla is lush-caves-only, at a rate that would flood the Vault, so its amount is a choice. Both are placed after ore, replace stone only, and count as stone for the 40% floor. Gravel Purge removes them. |

**How the baseline is checked.** Not by filling a test cube with ore. The count comes first from a pure JUnit test over the placement function, then from a read-only `/orevault density [chunks]` debug command on a dedicated server. Each band's density must land within ±5% of its value, relative, over at least 81 chunks, and the rarity shares, counted by placed blocks, within ±2 points of 68/25/7 in the stone band and 60/30/10 in deepslate. Every slab of a band must also land within ±10% of the band's density, relative, over the same 81 chunks, which is what holds the curve wherever a player mines (below, "Where each ore sits"). Slabs are 16 layers counted up from each band's floor, and a partial top slab is merged into the one below it: the stone band's top slab is Y225..245, and deepslate's is Y-31..-1. A vein may overhang its band's floor or top, and only its blocks inside the band are placed, so the layers at a band's edge hold the same density as the rest; the counts make up the cut blocks (`docs/adr/0009-baseline-ore-model-and-classifier.md`). The check runs on a vanilla-only pack with Guide Vein off. These figures are decided but not yet playtested: Nick's ten-minute mining session at T4 is the first time anyone mines this baseline.

**Which ores** comes from the §4.2 rarity classification, not from a list in the generator: everything in `#c:ores` that has an overworld `PlacedFeature` is placeable, so a kitchen-sink pack's modded ore generates with no per-mod work and the thing that decides what an ore is *worth* is the thing that decides how often it appears.

**Where each ore sits.** Nick chose the density half of this on 2026-10-27: the same density at every height, over vanilla's height profile. He chose the other half on 2026-10-31 (question 4): which ore a vein is follows its vanilla height, as the rule was first written (below; the options he turned down and why are in `DESIGN_LOG.md`, 2026-10-31).

Ore density is the same at every height. Each rarity's vein count (above) is spread evenly through the band's height, so every slab of the stone band is 3% ore at 68/25/7, and every slab of the deepslate band 6% at 60/30/10. Height can decide only *which* ore of that rarity a vein is, never how many veins there are, so the identity rule below moves none of the counts, the density, the shares or §4.3's curve: Resonance is paid by rarity, and a vein's ore is picked after its rarity and its layer.

Each ore's overworld height is normalized. The classifier already samples each ore's real placement heights to decide its rarity, so the same data says where it belongs. The overworld's Y-64..319 maps linearly onto Y-63..245, the expanded type's column, in both dimension types; in the base type, whose stone band starts at Y=1 and which has no deepslate, what the map puts below Y=1 counts as the stone band's lowest 16 layers. An ore's weight is its expected blocks a chunk in the overworld times the share of the overworld's biomes whose feature lists place it: 1.0 for a feature most biomes list (§11 Ore Rarity Classification), about 0.18 for emerald on vanilla. Weighted that way, emerald is about 18% of the band-wide rare mix (its weight over the sum of the rare weights) rather than the 55% an unweighted count gives a mountain-only ore. That 18% is not emerald's share of rare veins, because height decides the pick (next paragraph).

Which ore a vein is follows its height (Nick, 2026-10-31, question 4: "as written"). Once a vein has its rarity and its layer, its ore is drawn from the ores of that rarity that generate at its height, each in proportion to its weight (above) times its mapped height profile at that height, and where no ore of that rarity generates at a vein's height, from the ores of the nearest height in the band that has one (the lower on a tie). Nothing in the pick reads the band-wide mix on its own. Nick chose it on its measured consequence, which is this (the Balancer's figures, derived, unplaytested). The map puts diamond's vanilla range below Vault Y=1.4, redstone's below 0.6, gold's below 14 and buried lapis's below 40, while emerald spans Vault Y-24..245. In the base type, emerald is about 93.5% of rare veins and every rare vein above Y=16, and diamond about 6.5%, all of it in Y1..16 (about 9 diamond blocks a chunk against about 124 emerald); lapis is about 95% of uncommon veins, with gold about 2% and redstone about 3%, both only in Y1..16. In the expanded type, deepslate's rare veins are almost all diamond and its uncommon veins mixed, and its stone band is about 99% emerald for rare and 98% lapis for uncommon. For the player this means that away from the floor there is no diamond, gold or redstone: strip-mining the stone band's lowest 16 layers, or the deepslate of an expanded Vault, is where diamonds are. It is not where more ore is: density and Resonance an hour are the same at every height, so the curve does not reward it. Nick's T4 ten-minute mine is the first look at the mix by height in play (`docs/adr/0009-baseline-ore-model-and-classifier.md`).

Depth pays through the nodes priced for it, Deep Veins, Deep Harvest and Bedrock Communion, not through the baseline. Placed by vanilla's height profiles instead, most gold, redstone and lapis and nearly half the diamond fall below the map's Y=1 and pile into the stone band's lowest 16 layers: those layers come out about 15% ore and every slab above them 1.7 to 2.7%. A player strip-mining just above bedrock would earn about 7.5 times the rate §4.3's curve is priced on, so its 100 hours would take about 13, and the arrival height would be thinner than 3%. Pricing the curve on the floor instead would make everyone who mines anywhere else about 7 times slower.

Generation is seeded from the team id, the team's reset count (`vault_generation`, §11) and the chunk coordinates, never the world seed: Vault chunks generate lazily over months, so within one generation of the Vault a chunk has to come out the same whenever it is first visited. Each feature mixes in its own salt, so adding one never shifts another's placement. The reset count is in the seed so that a reset (§3.5) gives fresh vein positions rather than the same veins in the same places (Nick, 2026-10-27).

Every rate stated as "one per N newly explored chunks" (Geode Clusters, Ancient Traces) or as a share of chunks (Molten Seam's 50%) is a roll of 1 in N made for each chunk from that chunk's seed, never a counter carried from chunk to chunk: chunks generate in whatever order players reach them, and a counter would make the result depend on that order.

**A generated chunk is never changed by a later purchase**, for any generation node; only the dimension reset (§3.5) regenerates it (Nick, 2026-10-26).

An ore in `#c:ores` with no overworld `PlacedFeature` is neither classified nor placed unless a classifier override (§10) names it. Ancient debris is not placed by the baseline at all; Ancient Traces (§6.1) handles it.

> **Implementation gotcha — mid-session dimension creation.** After putting the new `ServerLevel` into `server.forgeGetWorldMap()`, you **must** call `server.markWorldsDirty()`. `MinecraftServer` ticks levels from a cached array (`getWorldArray()`) that is only rebuilt when that marker changes; without the call, a dimension created after the first server tick is never ticked at all. Symptom: block-breaking stalls after one `BreakSpeed` event and `BREAK_BLOCK` never fires, entities never tick, and relogging "fixes" it. See issues #82 / #89.

**Disband:**
When an FTB Team is disbanded, nothing kept for the team is deleted: its Vault world, its tree and its stats are archived. In FTB Teams only a party is ever disbanded, when its last member leaves; a player's own solo team is never deleted, joining a party included (§9). No level is deleted or moved while the server runs. Disbanding sets `disbanded_at` on the team's data and evacuates any online player inside the Vault to their return position; the Vault's folder and the team's data file are moved to `<world>/orevault_archive/<teamUuid>/<disbandedAt>/` at the next server start, before existing Vaults are restored, and the restore pass skips the team. Reset backups are kept apart, in `<world>/orevault_backups/` (§3.5). A server log entry records the archive. An admin command, `/orevault archive purge`, deletes archives.

---

### 3.2 Portal and Frame

**Vault Frame Block**
- Crafting recipe: 8 iron ingots surrounding 1 redstone dust (shapeless, fills all 8 outer slots), yielding **one** Vault Frame per craft
- Hardness: 1.5 (mines at stone speed), blast resistance: 6.0
- Mineable with any pickaxe (`minecraft:mineable/pickaxe` tag; MC 26.1 harvest rules are driven by the item `Tool` component)
- Requires correct tool to drop
- Sound: metal
- Right-clicking with a Vault Igniter triggers the portal shape scan
- Breaking any block of the frame's four sides dissolves the whole portal (`updateShape` whole-frame re-validation). Corners are optional (below), so a corner block is not part of the frame and breaking one leaves the portal standing

**VaultPortalShape Scanner**
- Scans outward from the clicked frame block in both X and Z orientations
- Valid frame: rectangular, all four sides made of Vault Frame blocks. **Corners are optional**: the four corner positions may hold anything, as with a nether portal
- Interior dimensions: minimum 2 wide × 3 tall, maximum 21 wide × 21 tall
- Interior must be entirely air or existing portal blocks
- If valid: fills interior with the tier-matched Ore Vault Portal variant, oriented to match the frame axis
- If invalid: plays a failure sound, no portal created. Nothing says which side is wrong: like a nether portal, the frame either lights or it does not (Nick, 2026-10-27, declining an action-bar line naming the problem)

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
    → retrieve the saved return position (`return_pos` on the player attachment)
    → teleport to Overworld at saved position (fallback: world spawn)
Else:
    → note current Overworld position
    → find or create team's Vault dimension
    → ensure the team's return portal exists at the team anchor (§3.1) — a single-plane
      4×5 frame with a 2×3 interior, standing on the grass surface at X=0, Z=0
    → teleport to the team anchor (or the player's personal entry point, Tier 2+)
    → only once the teleport has succeeded: save the noted Overworld position as
      `return_pos` on the player attachment
```

The return position is saved **only after the teleport succeeds**. Saving it first means a trip that fails part-way leaves a return position pointing at a trip that never happened.

**The Vault side is fixed; only the Overworld side is remembered.** Every trip into a team's Vault lands on the same block — the team anchor at X=0, Z=0 — regardless of where in the Overworld the portal was. Vault arrival never mirrors, offsets, or otherwise derives from Overworld coordinates. There is no nether-style travel logic on either side: no coordinate scaling and no search for a matching portal. Exactly one return portal exists per Vault, built at that anchor on first entry and idempotent thereafter, and it is tier-coloured to match the highest igniter tier that has opened the Vault (upgrading, never downgrading).

Return position is stored in the player's NeoForge data attachment `orevault:player`, registered with `copyOnDeath`, as `return_pos {dim, x, y, z}`. The same attachment holds `in_vault`, `tome_granted`, the personal entry points, the Homeward Seam anchor and cooldown and the last Vault death (§11 SavedData Pattern). It survives death and dimension changes. The saved spot is walked out along the approach direction past the last portal block, so returning never instantly re-triggers the portal.

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

Personal entry points are stored per player as `entry_points` on the player attachment `orevault:player` (§11 SavedData Pattern). A Vault Anchor set as an entry point (§3.4) takes one of these slots, so a Tier 2 igniter's one slot stays one (Nick, 2026-10-27). Without a Tier 4 igniter the reset button does not appear in the UI, and Vault Anchors cannot be crafted at all.

> **Resonance Crystals.** Not craftable from raw materials — the *only* source is Attuned ore, which drops from the Runic Attunement skill (§6.1). Recipe: **4 Attuned raw ore (any type) + 1 Amethyst Shard → 1 Resonance Crystal** (shapeless). This deliberately gates the igniter ladder behind a skill investment rather than a free recipe, and gives Runic Attunement a concrete purpose — it is the supply line for igniter upgrades and Vault Anchors. Total cost of a Tier 4 igniter is 28 Crystals = 112 Attuned ore, which at Runic Attunement Tier 3 (20% attune chance) is roughly 560 ore mined: a genuine but reachable investment.

---

### 3.4 Chunk Loading

**Chunk tickets**
The mod registers chunk loading tickets through NeoForge's `TicketController`, registered on the mod event bus, for chunks inside the Vault that contain an active Vault Anchor block. Forced tickets are saved with the level, but the server reactivates them only for the levels that exist when it prepares its levels at start (`MinecraftServer#prepareLevels`). A Vault is not one of them: it is registered afterwards, by the restore pass (§11 Dimension-per-Team Registration). So the restore pass reactivates a restored Vault's tickets itself, right after posting `LevelEvent.Load`, mirroring what `prepareLevels` does for the vanilla levels: it reads the level's saved tickets, activates the deactivated ones, then has NeoForge's `ForcedChunkManager` do the same for the mod's forced tickets (`docs/adr/0002-vault-lifecycle.md`). Without this step every Vault Anchor stops loading its chunk after a restart. The NeoForge call and when the `TicketController`'s loading-validation callback runs for a level added at runtime are unverified, to be read by E1-S5 (#195). A ticket is kept only if its Vault Anchor block is still present and the team is under its ceiling. A ticket is removed when its anchor is.

**Vault Anchor Block**
- Crafted from Vault Frame blocks + Resonance Crystal + a **Tier 4 Sovereign Vault Igniter**, which is *not consumed* — it is returned to the crafting grid as a crafting remainder, the same pattern vanilla uses for buckets and water bottles
- The igniter is therefore a **recipe gate, not a placement gate.** Gating placement instead would mean the block pops out of the world and back into the player's inventory when they lack the igniter, which reads as a bug rather than a rule. Gating the recipe means an anchor that exists can always be placed
- Placeable inside the Vault only
- Registers a chunk loading ticket for its chunk on placement
- Deregisters on removal
- Serves dual purpose: chunk loader and personal waypoint (right-click to set as a personal entry point with a Tier 2+ igniter). An Anchor used as an entry point takes one of the player's entry-point slots (§3.3: one at Tier 2, three at Tier 3); it adds none (Nick, 2026-10-27)
- Maximum simultaneous tickets per team determined by Vault Presence skill node (see skill tree)
- Admin config can set a hard ceiling on max tickets regardless of node level

**Cross-Dimension Behaviour**

**What machine-broken means.** A block break is a hand break only when the breaker is a `ServerPlayer` whose `isFakePlayer()` is false and who is the player the server's player list holds for that UUID. The second check catches a mod's fake player that extends `ServerPlayer` without overriding `isFakePlayer()`. Every other break is machine-broken: a machine, a fake player, or a break with no breaker at all. One field computed once per break carries the answer, and Resonance, the per-block travel stats, Tempered, Ascending Edge and Echoing Edge all read it. A block broken in an FTB Ultimine chain is a hand break (§9).

**Only the Vault's own team is paid.** A Vault belongs to one team (§3.1). An ore broken in it pays Resonance, and moves `PlayerStats`, only when the breaker is a current member of that Vault's team. A player whose team changes while inside another team's Vault is sent to their return position with a chat line saying why (§11 Dimension-per-Team Registration).

Blocks broken by machines in a ticket-loaded Vault chunk **never award Resonance**, in any quantity, under any node. This is a deliberate hard rule, not a balance value to tune: the level curve (§4.3) is calibrated against a player mining by hand, and a quarry in a 60%-ore dimension exceeds that by orders of magnitude. Allowing even a fraction would collapse a 100-hour progression into an evening on any tech pack.

What automation *does* get is the Automated Extraction node (§6.1), which increases machine **yield** inside the Vault and makes machine-broken ore count toward vein completion (§11) and player statistics. A vein whose last block a machine broke fires no completion effect: no Vault Echo burst, Twin Veins roll, Last Ore, Clean Cut, Vein Discipline step or Chapter's End count. Automation is therefore worth building for materials, and worth nothing for progression — progression is always earned by hand.

**Vanilla XP is untouched.**
Ore broken in a Vault drops vanilla experience exactly as it would in the Overworld. Resonance is an addition, never a substitution: a player who mines here must not end up behind one who mined the same ore outside, or the dimension becomes a trap rather than a reward. Nothing in the mod calls or suppresses `popExperience`, and nothing should — the Resonance orb is a separate entity that pays the team pool, and the two systems do not interact.

The places they touch deliberately are Echo Chamber (§6.1), which makes Vault Echo bursts grant vanilla XP equal to the Resonance awarded, and the nodes that drop bonus vanilla XP: Stone Memory, Stone Curse, Ancient Knowledge, Stonecutter's Patience and Pathfinder's Claim (§6.1). All of them are *additions* on top of the vanilla amount, which is another reason the baseline must stay intact.

---

### 3.5 Dimension Reset

**Trigger:** Reset button in the Ore Memory tab of the Tome of the Deep Seam (§8), shown only to a viewing player who holds a Tier 4 Sovereign Vault Igniter in their own inventory. A client can see only its own inventory, so the button follows the viewer, not the team.

**Voting logic:**
- Count currently online team members
- If only one member is online: that player can reset unilaterally
- If two members are online: both must confirm
- If three or more members are online: majority (more than half) must confirm
- Offline members are not counted and cannot block the reset
- The members counted are those online when the vote opens; a member who disconnects is removed from the count and their vote dropped, and one who connects later is not added
- A team has at most one open vote. A vote that has not passed within 120 seconds (2,400 ticks, a constant, not config) fails. Two minutes leaves a member who is mid-vein or mid-fall time to finish, open chat and click the line; a longer window lets the set of online members drift. Unplaytested; Nick's E11 reset session confirms it

**Reset process:**
1. Every other online team member gets a toast and a clickable chat line; clicking the line opens the vote screen. No screen opens by itself, because it would take control from someone mid-swing inside the Vault, and a missed toast can be reopened from the chat line (Nick, 2026-10-27)
2. Once vote passes, a 10-second countdown is shown to anyone inside the Vault with a warning message. The initiator can cancel the countdown; a cancel ends the reset and changes nothing
3. Any players still inside the Vault at countdown end are moved out to their saved return position before the level is unloaded, and once the new level is registered they are placed at the team anchor (X=0, Z=0), where the portal arrives (§3.1). Not left at their saved return position, and not returned to where they stood, which may be inside rock the reset replaced (Nick, 2026-10-26). A player offline inside the Vault is placed at the team anchor when they next log in (§11 Dimension-per-Team Registration). (One exception: if the server stops after the reset is recorded complete but before the new Vault is registered, the new Vault is made on the next portal trip, and a player offline inside who logs in before then finds no Vault to be placed in and goes to their return position; `docs/adr/0007-reset-disband-archive.md` Consequences.)
4. All chunk data for the team's Vault dimension is deleted, once its storage is closed and after the backup, if one was asked for. The team's data records that a reset is under way before anything is deleted, so a crash or a stop part-way is finished at the next start
5. The team's reset count (`vault_generation`) goes up by one, and the dimension is re-registered fresh (same registry key, new generation state). The reset count is part of the generation seed (§3.1), so the new Vault has new vein positions. If the team has purchased the **Vault Expansion** keystone, the dimension is re-created under `orevault:ore_vault_expanded` instead of the base type — this is the only moment the dimension type can change (§3.1). The type is decided from the team's ownership when the new Vault's folder is made and kept in a file in that folder, not in team data (§11 SavedData Pattern)
6. Team skill tree progress, Resonance pool, Animus pool, and all skill point investments are fully preserved
7. **Free respec window:** for 10 minutes after a reset, node refunds cost no XP (§4.4). A fresh Vault is the natural moment to rebuild a mining strategy, and it gives the reset a second purpose beyond regenerating terrain. The window opens only if no free window opened in the previous 24 hours of server time (Nick, 2026-10-27); a reset inside those 24 hours regenerates the Vault and opens no window. Without the limit a solo player could reset to refund a keystone for free and buy it back later, which is what the 30-level keystone refund price exists to stop
8. A server log entry records the reset with timestamp and team ID

**If the Vault cannot be unloaded.** When the live Vault's level cannot be unloaded, everyone inside is sent out of the Vault, and the Vault regenerates at the next server start instead, with a message saying so.

**Backup option:** A checkbox in the reset confirmation UI labelled "Export chunk data before reset", shown only when `reset.allow_backup_on_reset` is true (§10). If checked, the dimension's folder is copied to `<world>/orevault_backups/<teamId>/<timestamp>/` after its storage is closed and before deletion, and the log records the path and the bytes written. If the copy fails, the reset is abandoned and nothing is deleted. This is purely a safety net and is not used by the mod itself after creation; it is separate from the archive of disbanded teams (§3.1).

---

## 4. Resonance System

### 4.1 What Resonance Is

Resonance is a team-shared numerical counter stored in the team's SavedData. It is not a physical item and never appears in inventory. It is displayed in the Tome of the Deep Seam's header and, while the player is inside a Vault, on an on-screen Resonance bar that each player can turn off (§8; Nick, 2026-10-27). Players mine ores inside the Vault → Resonance accrues to the team pool → when the pool crosses a threshold the team gains a skill point → skill points are spent on Resonance tree nodes.

### 4.2 Resonance Gain

Resonance orbs are spawned as floating entities when an ore block is broken inside the Vault. They behave identically to XP orbs visually and mechanically — they float toward the nearest team member within 8 blocks, are absorbed on contact, and add to the team's Resonance pool. 8 blocks is vanilla's experience orb radius (`ExperienceOrb`, `XpOrbTargetingEvent(this, 8.0)`). Sound and particle effects match XP orbs. The Resonance Magnetism option raises the radius to 12, 16 and 24 blocks (§6.1).

**Base gain rates** (constants, not config; one constant per row, read by the orb, the curve and this table):
| Ore Tier | Base Resonance |
|---|---|
| Common (iron, copper, coal) | 2 |
| Uncommon (gold, lapis, redstone) | 5 |
| Rare (diamond, emerald) | 12.5 |
| Deepslate (with Stone Memory T3) | 0.05 (fractional, accumulates) |
| Vault Echo burst | 25 / 35 / 50 at tiers 1 / 2 / 3 |

**Rarity classification** happens at every server start over the blocks in the `#c:ores` tag (§11). Classification uses each ore's world-gen placement data: the expected vein count and the sampled heights of the overworld placed features that place it (it reads every one and uses those §11 Ore Rarity Classification selects), with stone and deepslate variants counted as one ore. An ore with at least 100 expected blocks per chunk (over the features the classifier uses for it, Y-64..319: its all-biome features, or its biome-only ones when it has none, §11 Ore Rarity Classification) is Common; otherwise it is Rare when its mean vein places under 4.5 blocks, and Uncommon when not. On vanilla this gives coal, iron and copper Common; gold, redstone and lapis Uncommon; diamond and emerald Rare. The earlier count-and-range rule made emerald Common (100 attempts a chunk, under half a block each). The admin can override specific ore classifications in config. **A block that is not a classified ore pays no Resonance**: there is no default rarity, so stone, deepslate and an ore tag member the classifier could not read pay nothing.

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

**Levels and skill points are decoupled.** The level cap is 30 and the rings in §6 run from 0 to 30 (keystones at 18 to 26; Ore Working's last Mekanism-tag nodes at 25 and 30), so the level track paces when nodes open, and the points decide how much of the tree a team can hold.

**Calculation (performed in code at startup, not configured manually):**

1. The level cap is a constant: `LEVEL_CAP = 30` for both trees.
2. Points awarded per level are a constant: `POINTS_PER_LEVEL = 5`, so a team has 150 points at level 30. Every node costs 1 point (§6), so 150 points buy 150 nodes. The server sends the value to clients with the team's progress.

   **Pinned, not derived (Nick, 2026-10-26).** The tree holds 193 to 199 nodes in a vanilla pack and 207 to 213 in FTB Evolution 2 (§6.1 Totals). A team therefore owns 75% to 78% of a vanilla tree at level 30, and 70% to 72% of the pack's, which is about 85% and 82% of the nodes that are not travel. Deriving the award as `ceil(0.7 × loadedNodeCount / 30)` gives 5 today but turns into 6 at 215 loaded nodes, two to eight more than the pack's tree (fewer than Ore Working's three Mekanism-tag nodes), and at 6 a level a team owns nearly every node that is not travel by level 30, so routes stop mattering. With 5 pinned, adding content lowers the share owned, which is visible and can be argued about; a rounding flip is neither. Every level up to 30 buys something. The tiered tree this replaces could be bought whole by level 26, leaving levels 27 to 30, about 47 hours, with nothing to buy.
3. From config, read `target_play_hours` (default: 100) and `curve_divisor` (default: 1.0) from the `[resonance]` block in §10. Both are read once at server start; changing either requires a restart, because moving a threshold mid-session would move it under teams that had already passed it.
4. Calculate average Resonance per hour for a **solo** player: `resonancePerHour = averageOresPerHour * weightedAverageResonancePerOre`. `averageOresPerHour` is a **constant in code**, not config — it measures how fast a player mines rather than expressing a preference, so an admin has no way to know the right value and a wrong one silently distorts the whole curve. The knob a server owner wants is `curve_divisor` in the next step.

   Both inputs are **derived from the §3.1 baseline distribution, not chosen independently**. A player tunnelling with an enchanted pickaxe sustains roughly 7,200 blocks an hour once travel and inventory handling are counted against continuous breaking; that figure is the constant, and `averageOresPerHour` is it times the stone band's density, about **219 ore an hour** at 3% (7,200 × 3.04% is 218.9, at the stone band's density as placed, §3.1). `weightedAverageResonancePerOre` reads the same 68/25/7 rarity shares the generator places, counted as placed blocks. Both the density and the shares are computed from the one baseline record the generator reads (`docs/adr/0009-baseline-ore-model-and-classifier.md`), never stored beside it. If either the density or the mix changes, this step changes with it — otherwise the curve is priced for a Vault that does not exist, and nothing in play says so.
5. Total Resonance needed: `totalResonance = resonancePerHour * targetPlayHoursResonance / curve_divisor`. Because the divisor is applied here, before the distribution in step 6, it scales every level threshold by the same factor: the 100:1 last-to-first ratio and the milestone spacing below are preserved exactly, and every ring in §6 keeps its intended pacing. This is the supported way to make Ore Vault a 40-hour mod rather than a 100-hour one, without touching the tree or the level cap.
6. Distribute `totalResonance` across the 30 levels using an exponential curve where the last level costs `LAST_TO_FIRST_RATIO` (100) times the first:
```
levelCost(n) = baseCost * (growthFactor ^ n)
growthFactor = LAST_TO_FIRST_RATIO ^ (1 / (LEVEL_CAP - 1))
```
`baseCost` is derived so the sum across all levels equals `totalResonance`, with the final level absorbing rounding drift.

7. This array of level costs is computed once at server start and stored.

**Resulting pacing** (100-hour target, 30 levels, ratio 100):

| Milestone | Level | ≈ Hours in |
|---|---|---|
| First nodes purchasable | 1–2 | < 1 |
| Mid-tree branches open | 8–10 | about 2 to 3.5 |
| Vault Expansion keystone, for a team heading straight for it | 18 | ~15 |
| Vault Expansion keystone, for a team that does not aim for it | about 29 | about 85 |
| About 70% of the tree owned (150 points) | 30 | 100 |

**What "100 hours" means.** A team that buys nodes as it earns the points (Nick, 2026-10-26). The curve above is derived for a miner who buys nothing, and every node that raises ore or Resonance shortens it. When T14's measurements show by how much, the curve is stretched to fit and the nodes keep their strength.

**Pacing check.** The 7,200 blocks an hour in step 4 is a derivation until a timed session measures it. Before any node effect merges, one player mines for 30 minutes in freshly generated chunks with zero points spent and nothing bought, using a tool the Balancer names. Each figure must land within ±20% of its derived value:

| Measure | Band |
|---|---|
| Blocks broken an hour | 5,760 to 8,640 |
| Ore broken an hour | 176 to 264 |
| Level 1 reached | 7 to 11 minutes in |
| Level 2 reached | 15 to 23 minutes in |

Ore per hour outside its band means the curve is re-derived before any node effect merges.

**Skill point award:** When the team's cumulative Resonance crosses the threshold for the next level, the team automatically receives `pointsPerLevel` skill points. A toast notification appears for all online team members. The Resonance pool is not reset — it continues accumulating past each threshold.

### 4.4 Spending Skill Points

Skill points are spent in the Resonance tree tab of the Tome of the Deep Seam (§8). **Every node costs 1 point** (Nick, 2026-10-26). A node can be bought when all of these hold:

- it touches the start or a node the team owns (§6 adjacency);
- the team's level is at least the node's ring (§6);
- the team has an unspent point;
- no exclusive partner of it is owned (§6);
- it is not hidden (§6, mod-gated nodes).

The effect applies at once. A node that changes chunk generation applies to chunks generated afterwards; chunks already generated are unchanged (§3.1). Shift-clicking buys a whole highlighted path in one confirmed step (§8), and the server checks each node of the path in order by the same rules. Every online team member is told what was bought and by whom.

Unspent points are `5 × level − owned visible nodes`, derived and never stored (§11). If a later version of the tree leaves that number below zero, buying is blocked until it is positive again; nothing is refunded automatically.

**Refund.** A node can be refunded when every other owned node stays connected to the start without it. The check is a breadth-first search from the start over owned nodes, not a leaf-only rule: where paths form a loop, a node inside the loop can go as long as the loop still joins up. There are no cascades. A refund that would cut nodes off is refused, and the Tome names the nodes it would strand. When a node cannot be refunded, the Tome always says why.

Refunds cost Minecraft XP levels:

```
REFUND_XP_PER_NODE     = 3     // any node, travel included
REFUND_XP_PACT         = 12
REFUND_XP_KEYSTONE     = 30
```

Moving a route already costs its length: rerouting a 10-node path is 30 levels. Pacts and keystones cost more because a flat 3 levels would let a team dodge their downside: refund Bedrock Communion before a trip through the stone band, where its penalty applies, and buy it again after, and a keystone becomes a toggle. At 30 levels that trick costs what the 10-point keystone of the tiered tree cost to refund.

**Refunds happen outside the Vault only**, the same rule as tradeoffs and fork options (§6), so a node cannot be dropped for one vein and bought back for the next. Every online team member is told who refunded what.

**Free undo window.** A node bought within the last 60 seconds can be refunded for no XP, so a misclick costs nothing. The window is session state, held in memory like the §11 Vault session state, not saved. Sixty seconds is long enough to see a misclick and too short to use: refunds happen only outside the Vault, so buying a keystone, mining with it and refunding it free would buy under a minute of mining.

**Free respec after a reset.** For 10 minutes after a dimension reset (§3.5) every refund costs no XP. The end of this window is saved as `free_respec_until` (§11). A reset opens the window only if no window opened in the previous 24 hours of server time; when the last one opened is saved as `free_respec_opened_at` (§11). Nothing else limits how often a team resets, so without this a team could reset to refund a keystone free and buy it back on the next trip (Nick, 2026-10-27).

**Fork options are free both ways.** A fork option is a petal, not a node (§6): it costs nothing to pick and nothing to unpick, and swapping is allowed only outside the Vault. A free choice that is expensive to reverse is a trap, and the fork is a decision a team can revisit between trips. When the team refunds its last node of a fork parent, the choice is cleared with it. A choice that some owned nodes depend on cannot be swapped away until those nodes are refunded: Ore Working's nodes 4 to 6 work only with Ore Doubling, so a team holding any of them refunds them (at the normal price) before it can choose Smelter's Intuition.

**Exclusive pairs.** To swap Greedy Seams for Resonant Overload, refund the leaf (12 levels, a pact) and buy the other (1 point). Both hang off the same junction (§6), so no other node is lost.

The XP price replaced a formula that priced every refund the same whatever it undid (`investedPoints / totalTreeCost * 50`), which made an early mistake nearly free and a late respec nearly impossible. Several nodes are exclusive pairs and forks (§6.1), and permanent-feeling refunds would turn each of them into a trap.

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

Identical to Resonance: `pointsPerLevel` points per level (§5.2), spent in the Mob tree tab. Refund costs scale with total points invested in the Mob tree specifically, not the combined total.

The Animus tree below is still written in the tiered form the Resonance tree had before 2026-10-26 (costs, level requirements, prerequisites). It is redesigned to the §6 rules when the Animus epic is scheduled; until then its costs and `ceil(totalAnimusTreeCost / 30)` describe intent, not the model to build.

---

## 6. Skill Trees — Complete Node Definitions

### Notation

The Resonance tree is a Path of Exile style passive tree (Nick, 2026-10-26; `DESIGN_LOG.md` 2026-10-26). It replaces the tree of named clusters behind points-spent anchors that this section described before. The rules:

- **One point a node.** Every node costs exactly 1 skill point. A node is one step on the tree; there are no multi-point tiers.
- **Start.** The tree has one start node at its centre. The team owns it from the beginning; it has no effect and is never refunded.
- **Adjacency.** A node can be bought when an edge joins it to the start or to a node the team owns. One owned neighbour is enough, so a team can walk through a wheel without finishing it. There are no other prerequisites.
- **Distance (`d`).** A node's distance is the fewest nodes on a path from the start to it, counting the node itself, so it is the fewest points that can buy it. It is computed by breadth-first search when the layout loads. Distance is the control for power: the stronger a node, the further out it sits.
- **Ring.** Every node carries an authored ring, which is its level floor: a node needs team level of at least its ring. For a node that existed before the PoE tree, its ring is the level requirement that tier had (Nick, 2026-10-26: distance factor 1.0). The layout places every node at `d >= max(1, ring)` and draws it in its ring's band; a test fails the build otherwise. The code reads the authored ring, never the distance, so moving a node in the layout cannot silently move its level. A team buying as it goes rarely meets the floor; a team walking straight out to a keystone does (Vault Expansion is ring 18, Bedrock Communion ring 26).
- **Skill and tier.** Nodes that share a skill id are copies of one skill: Vein Expansion is five nodes. **A skill's tier is the number of its nodes the team owns**, wherever they lie on the tree, and its effect table in §6.1 is keyed by that count. Tier is derived from the owned node ids and never stored. Copies are placed in ring order: no copy sits nearer the start than a copy with a lower ring. Every copy of a skill has the same icon.
- **Wheel.** A group of six rim nodes around a notable at the centre. The notable is reached only from the rim node opposite the wheel's entry, and the wheel's exits leave from its two shoulders, so a path can pass through a wheel without buying its notable. A rim holds tier nodes of several skills at about the same ring, plus one or two travel nodes; one skill's tiers run outward along one arm across successive wheels, since a skill's rings are too far apart to share one wheel. A notable is always a single node. The area around the start is a group of nodes rather than a wheel, with at least four growth nodes next to the start so level 1 has obvious picks.
- **Fork.** A fork parent is an ordinary skill whose nodes do nothing until an option is chosen (§6.1 Forks). Its options are petals: drawn beside the parent's lowest-ring node, the fork's `petalAnchor` in the layout, and joined to nothing. A petal has no edges, is never on a path and costs no points, so choosing an option can never open or block a route.
- **Exclusive pair.** Two single nodes that hang as leaves off one junction node, at the same ring. Owning one locks the other. The travel nodes leading to them lie before the junction, so locking a leaf never strands anything.
- **Mod-gated nodes.** A node whose effect needs a mod, or a tag only a mod fills, sits on a dead-end leaf or spur. When the mod is absent it is hidden: not drawn, not counted, not buyable, never shown as locked. Because nothing lies beyond it, hiding it never changes another node's distance or what can be reached. A node hidden because its mod or tag is missing that a team already owns stays in its save, dormant: it has no effect and does not count as spent, and it works again if the mod returns. A node hidden by a fork choice is different: it still counts as spent, and the choice cannot be swapped away while the team owns it (§4.4), so a swap can never turn spent points into free ones.
- **Saved state.** The team's tree is saved as `{data_version, owned[], fork_choice{}, free_respec_until, free_respec_opened_at, pinned_node, pinned_by, bought_by{}}` (§11 SavedData Pattern): the owned node ids, the chosen option per fork, the end of the free respec window after a reset and when the last one opened (§4.4), the team's shared pin (a nullable node id and the UUID of the member who set it; absent means no pin) and, per owned node, the UUID of the member who last bought it (absent means empty). On load a pin whose node is gone or hidden is dropped. Nothing derived is saved: tiers, points spent and unspent, and distances all follow from `owned` and the layout. On load, an id the layout no longer has is dropped, which returns its point, and an owned node no longer connected to the start after a layout change is pruned for free. Tradeoff toggles are per player and live on `PlayerStats` (§11), except the applied state of the two generation tradeoffs, Volatile Veins and Molten Seam, which is the team's (§6.1, §11).
- **Layout data.** The tree's shape is authored data, not code: a JSON resource in the mod jar, not a datapack, so a server and its clients always read the same file. It lists every node (id, skill, class, marks, ring, the mod or tag it requires, exclusive group, drawing position) and every edge, plus each fork's `petalAnchor`. Positions are for drawing only; the rules read edges and rings. The server sends the layout's hash at login, and a client whose own layout hashes differently is disconnected with a message naming the mismatch (`docs/adr/0006-network-channel-and-tome-sync.md`). It syncs ownership and the hidden nodes as two bitsets in layout order, plus the fork choices and the pin, and resends all of them, with the Ascending Edge ladder, on `OnDatapackSyncEvent` (each join and each `/reload`). Tests over the file pin the rules above: every node's distance at least its ring, the copies of a skill in ring order, every mod-gated node on a dead end, exclusive leaves off one junction, petals with no edges, no edge through another node.

**Node classes and marks.** Every node has exactly one **class** and any number of **marks**. The classes are small, travel, notable, keystone, pact, growth and fork parent; fork options are petals, not nodes. Class decides how the node is drawn (§8). The marks are tradeoff, exclusive and requires; a mark adds a rule to a node of any class.

| Class | Meaning |
|---|---|
| *(no tag)* | **Small node.** One tier of a skill: its node adds one to the skill's count. Pure upside. These are the bulk of the tree's real nodes. |
| **[TRAVEL]** | **Travel node.** One step of a travel stat (§6.1 Travel stats), sized so each node is felt on its own. Travel nodes sit on the paths between real nodes. |
| **[NOTABLE]** | A single node granting a *distinct mechanic* rather than a bigger number, usually at the centre of a wheel. Always pure upside. |
| **[KEYSTONE]** | Build-defining and **always carries a real downside**. A keystone should change how you mine, not just how fast. Keystones sit at rings 18 to 26, at the far ends of the tree. |
| **[PACT]** | Build-defining and carrying a real downside, like a keystone, but at a low ring (6 or 13). Pacts exist because some of the tree's sharpest choices are only interesting while they are cheap: Greedy Seams undercutting Ore Doubling is a decision at level 6 and nothing at level 26. |
| **[GROWTH]** | Strong immediately; **its reward stops mattering as the team grows**, because the bonus is a flat number or a hard level cutoff. A growth node never switches off (Nick, 2026-10-26). The point stays spent. That is the price of an easier start, and refunding it once it stops mattering would make all five free picks. The Tome greys the node and labels it **Outgrown** once its condition can no longer be met for the viewing player (§8 Tab 1, Tooltip); no chat message announces it. |
| **[FORK PARENT: name]** | A skill whose nodes **do nothing until an option is chosen**. Its tier, the count of its nodes owned, acts through whichever option is chosen. |
| **[FORK OPTION: name]** | A petal, not a node. **Costs nothing**, can be picked once the team owns a node of its parent, and decides what the parent's tiers do. One option at a time; swapping is free and allowed **only while outside the Vault.** |

| Mark | Meaning |
|---|---|
| **[TRADEOFF]** | Toggleable on and off at no cost, per player, **but only while outside the Vault.** The two tradeoffs that change generation, Volatile Veins and Molten Seam, are switched by a team vote instead (§6.1 Volatile Veins). |
| **[EXCLUSIVE: other]** | Cannot be held at the same time as the named node. Both sides are paid for, and both are leaves off one junction. |
| **[REQUIRES: mod or tag]** | Shown only when the named mod is loaded or the named tag has entries. Hidden entirely otherwise, never shown as locked, and always on a dead-end leaf or spur. |

> **Why tradeoffs, fork options and refunds can only be changed outside the Vault.** Previously they could be flipped at any moment, which meant no commitment: a player would enable Tithe while mining ore and disable it before touching stone, taking every upside and paying no cost. Requiring the change to happen outside makes a build a loadout you commit to before you delve, which is the whole point, while keeping the freedom to change strategy between trips.

---

### 6.1 Resonance Tree

#### Shape of the tree

A start node at the centre, nodes joined by edges, and stronger nodes further out. Power is placed by distance, not by price: every node costs one point, and the level a node needs is its ring, which is also the nearest it may sit to the start. Tiered skills from the earlier tree became one node per tier with the effect values unchanged; their rings are the old level requirements. Between them run travel nodes, each a felt step of one of twelve travel stats. Flavour notables and keystones, some of them for mods in Nick's pack, sit on leaves and at the far ends.

Old prerequisites are gone, with a few that carried meaning kept as placement: the dependent node sits on a leaf whose only neighbour is the node it depends on, so adjacency reproduces the rule. These are Second Wind: Homeward off Second Wind, Echo Chamber off the furthest Vault Echo node, Prospector's Eye off Vein Sight (Nick, 2026-10-26), and Vault Smith off Vault-Forged. Vein Sight lies before Vault Echo on every path from the start, so finishing a vein is something a team can plan before the nodes that pay for it (`DESIGN_LOG.md` 2026-09-07, Pacts, growth nodes, and the Claim split). Every other old prerequisite is dropped; distance covers it.

**The authored layout lives in a data file** (§6 Layout data), owned by UI/UX, and is not drawn in this document. No renderer code is written until a preview of that file, rendered with `tools/tree-preview` at GUI scales 2, 3 and 4, has Nick's yes (`CLAUDE.md`: anything visual needs his yes on a rendered preview). The two-band preview of 2026-10-26 used one faint ring per level and came out about 2,400 tree pixels across for the whole tree.

#### Totals

Every count below is derived, not measured, and the hours rest on the unmeasured 219 ore an hour (§4.3).

| | Vanilla pack | FTB Evolution 2 |
|---|---|---|
| Skill tier nodes | 118 | 124 (6 for FTB Ultimine) |
| Flavour notables | 14 | 21 |
| New keystones | 3 | 4 |
| Real nodes | 135 | 149 |
| Travel nodes | 58 to 64 | 58 to 64 |
| Tree | 193 to 199 | 207 to 213 |
| Points at level 30 | 150 | 150 |
| Share of the tree owned at level 30 | 75% to 78% | 70% to 72% |
| Share of real nodes owned at level 30 | about 85% | about 82% |

Ore Working's three Mekanism-tag nodes add 3 where the `c:clumps/`, `c:shards/` and `c:crystals/` tags are filled (§9). The real-node shares are estimates; the Balancer's model was last run before the second round of flavour nodes. A value-first team leaves a few far ends unbought at level 30, and which far end a team skips is the build choice the tree is meant to create.

#### Forks

A fork is **one skill plus free petals**:

- The **fork parent** is an ordinary skill, one node per tier. Its nodes have **no effect at all** until an option is chosen, and the Tome washes every one of them as inert until then (§8). Buying it commits to the mechanic, not yet to a flavour of it.
- Each **fork option** is a petal beside the parent's lowest-ring node: it costs nothing, has no edges and decides what the parent's tiers do. Exactly one option may be active.
- Unpicking an option is **free**. Refunding the parent's nodes follows the normal §4.4 price; when the last one goes, the choice is cleared.

**An option must cover every tier its parent has.** A parent tier with nothing behind it in the chosen option is a paid step that does nothing — Hoarder's Instinct had exactly that gap when the forks were converted and gained a third tier to close it. The only sanctioned exception is a tier range the option itself gates, like Ore Working's Mekanism-tag nodes, which are hidden rather than empty.

**Swapping an option is free, but only outside the Vault.** Same rule as tradeoffs (§6 notation), for the same reason: inside, a player would re-specialize per vein — Rare Focus for the deepslate layer, Common Focus on the way back up — and take every upside of a choice they never actually made. The restriction is on *changing* an option, not on holding one; nothing about a build stops working when you enter.

Making each option its own paid branch was rejected on 2026-09-03 ("buy the tiers once instead of once per rival branch") and again with the PoE tree: it would make one fork cost three times its depth.

#### Reserved node ids

These ids belonged to nodes in the pre-restart build and are never reused, so a stray old save or a
leftover lang key can never be read as a different node:

`common_ore_boost`, `uncommon_ore_boost`, `rare_ore_boost`, `ore_sense`, `motherlode`,
`efficient_miner`, `disturbed_zone_unlock`

Saves from before the restart are not supported (Nick, 2026-10-26): team data starts at
`data_version` 1 and no migration reads the old ids. `ore_sense` became `vein_fortune`, `efficient_miner`
became `miners_constitution`, the three Ore Boosts became `ore_attunement` and its Focus options,
`motherlode` became the Vein Singularity option, and `disturbed_zone_unlock` is deferred to the post-1.0
Animus epic. Saved data is still versioned from its first field (§11), and migrating saves
across tree changes after release is a stretch goal, not a requirement.

#### Reading a node

Every entry below carries its class and marks in its tag line, as listed in §6 notation, and the Tome draws each class distinctly (§8). An entry with no class tag is a skill of small nodes. A skill's table has one row per node: the **Tier** column is the count of the skill's nodes owned that the row's effect needs, and the **Ring** column is that node's ring, its level floor and the nearest it may sit to the start (§6). A single-node entry has one row.

The tables give every tier so the effect is fixed in one place; the player does not see them whole. The Tome's tooltip shows the effect at the tier the team owns and a dimmer "Next:" line with the next tier's effect, both filled from the effects constants, never every tier (§8 Tab 1 Tooltip; Nick, 2026-10-27, replacing the 2026-09-09 every-tier rule).

The nodes are listed in this order: the skills carried over from the tiered tree, with their pacts, tradeoffs, forks and growth nodes; then the travel stats; then the flavour notables; then the keystones. The order is for reading. It is not the layout, and nothing in the tree is grouped or gated by it.

#### Skills

**Vein Expansion**

> Ore veins in newly explored chunks are larger: +15% size, then +30%, +50%, +75%, +100%.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +15% vein size | 0 |
| 2 | +30% vein size | 2 |
| 3 | +50% vein size | 4 |
| 4 | +75% vein size | 7 |
| 5 | +100% vein size | 10 |

---

**Stone Memory**

> Stone and deepslate mined in the Vault drop XP: 1 per block, rising to 2, 3, 4 and 5 per block in all. Tier 2 also gives stone a 10% chance to drop flint, tier 3 makes deepslate pay 0.05 Resonance, tier 4 gives stone a 2% chance to drop an iron or copper nugget, and tier 5 gives it a 0.05% chance to fire a 50 Resonance burst.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Stone drops 1 XP when mined | 0 |
| 2 | Stone drops 2 XP; 10% of stone broken drops flint | 3 |
| 3 | Stone drops 3 XP; deepslate pays 0.05 Resonance per block | 6 |
| 4 | Stone drops 4 XP; 2% of stone broken drops one nugget of a common metal (iron or copper on vanilla) | 10 |
| 5 | Stone drops 5 XP; 0.05% of stone broken fires a 50 Resonance burst (Vault Echo T3) | 14 |

> The XP column is the total a block drops at that tier, not an addition to the tier before: 1, 2, 3, 4, 5, never 1 + 2 + 3 + 4 + 5 = 15. The Balancer's XP-a-minute figures for this node (about 115 a minute at tier 1) use this reading.
>
> **The tier 4 nugget** is an existing item, not a new one. The candidates are the ores classified Common (§4.2) whose material has an item in the `c:nuggets/<material>` item tag, matched by material name (`c:ores/iron` to `c:nuggets/iron`). One is picked per drop, weighted by its whole weight in the baseline, the expected blocks a chunk ADR 0009 gives it (`docs/adr/0009-baseline-ore-model-and-classifier.md`), not by its share in the band being mined; on vanilla iron's 646 against copper's 134 makes about 83% iron and 17% copper. The drop is the first item in its nugget tag. Coal has no nugget and is never picked. On vanilla that leaves iron and copper. Unverified: whether 26.1's `c:nuggets/copper` tag holds the copper nugget is to be read by E1-S5 (#195); if it does not, the nugget is always iron. Defined 2026-10-27.
>
> Tiers 3 and 5 were 0.5 Resonance per deepslate block and a 0.5% burst chance, set on 2026-09-09 without being measured. They paid about twice the ore in the deepslate band and about 2.3 times the ore in the stone band, so every build that skipped Stone Memory was about three times slower. At 0.05 and 0.05% each pays about a fifth of its band's ore rate (Nick, 2026-10-27). Derived, unplaytested; T9 confirms them.

---

**Gravel Purge**

> The gravel and clay the Vault generates (§3.1) no longer generate in newly explored Vault chunks.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | No gravel or clay generates | 1 |

---

**Miner's Constitution**

> Hunger drains −20% slower inside the Vault, then −45%, −65%, −85%, then not at all. Tier 2 also adds +20% saturation from food, tier 3 grants Regeneration I for 5 seconds when you eat, tier 4 stops starvation damage and adds +2 max hearts, and tier 5 adds +4 max hearts. The extra hearts apply only inside the Vault.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | −20% hunger drain | 0 |
| 2 | −45% hunger drain; food restores 20% more saturation inside the Vault | 3 |
| 3 | −65% hunger drain; eating grants Regeneration I for 5 seconds | 6 |
| 4 | −85% hunger drain; no starvation damage; **+2 max hearts inside the Vault** | 10 |
| 5 | Hunger and saturation frozen completely; eating still grants the Regeneration I from Tier 3; **+4 max hearts inside the Vault** | 15 |

> Renamed from "Efficient Miner". Hunger is one skill, not a branch of its own.
>
> The bonus hearts are an attribute modifier applied on entering the Vault and removed on leaving, so they never follow a player into the overworld. Current health is clamped, not scaled, on removal.

---

**Sure Footing**

> Fall damage taken inside the Vault is halved, then removed entirely.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Fall damage taken inside the Vault is halved | 2 |
| 2 | No fall damage inside the Vault | 6 |

---

**Deep Breath**

> Below Y=32 inside the Vault you cannot drown. Tier 2 adds immunity to fire and lava damage below Y=32.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Immune to drowning below Y=32 inside the Vault | 12 |
| 2 | Also immune to lava and fire damage below Y=32 inside the Vault | 18 |

> Tier 2 is the counterweight to **Molten Seam**: a team that turns lava generation on has a way to stop it being a death sentence, but has to walk out to rings 12 and 18 for it.

---

**Novice's Luck** `[GROWTH]`

> +(30 − team level)% ore drops: +28% at team level 2, +10% at level 20, nothing at level 30. Outgrown at the level cap.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `(30 − teamLevel)%` bonus ore drops, floor 0 | 0 |

> Enormous at level 2, exactly zero at level 30. Cannot be a late-game problem because the ceiling is the level cap itself.

---

**Apprentice's Ledger** `[GROWTH]`

> The first 100 ore blocks you mine by hand on each Vault trip while the team owns this node pay double Resonance. The 101st onward pay normally. A trip that starts within 10 minutes of the end of your last one continues that trip's count instead of starting a new 100.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | The first 100 ore blocks mined by hand on each Vault trip while the team owns the node pay double Resonance; a trip starting within 10 minutes of the end of the last one continues its count | 0 |

> **Only ore mined while the team owns the node counts toward the 100.** Ore mined earlier on the same trip, before the purchase, does not use up the bonus. At level 1 the difference is up to about 33 doubled ore, about 113 Resonance, a whole level 1; the generous reading is also the simpler rule to state (Balancer and Writer, Epic 0 round 1).

> A flat cap against rising throughput: transformative when a trip yields 150 ore, a rounding error when it yields 3,000.
>
> Without the 10-minute rule, stepping out through the portal and back every 100 ore would keep the bonus on for good, and the node would never stop mattering (Nick, 2026-10-27: "I don't think anyone will cheese it like that because it's a pain", taking the recommendation anyway). The count is held in memory with the other trip state (§11 Vault Session State).

---

**Shallow Grace** `[GROWTH]`

> +50% Resonance from all ore while every team member currently inside the Vault has an all-time deepest Y above 100. Members outside the Vault are not counted. A member who has been below Y=100 once stops the bonus whenever they are inside.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +50% Resonance from all ore while every team member inside the Vault has an all-time deepest recorded Y above 100; members outside are ignored | 0 |

> Reads `deepest_y` from `PlayerStats` for each member inside the Vault. It is a lifetime record and never decreases, so a member's first real delve ends the bonus for any trip they are on, and no refund or reset brings it back. That is the point. Counting only the members inside follows Nick's answer of 2026-10-26.

---

**Guide Vein** `[GROWTH]`

> Twelve large veins, about 51 blocks each, generate in every Vault chunk first generated after purchase, spread evenly through the stone band's height. Each vein is copper or iron, at even odds.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | 12 extra veins of configured size 24 (about 51 blocks) per newly generated chunk, spread through the stone band; each is copper or iron, 50/50 | 0 |

> Exists so an unlucky first hour does not read as a broken mod. A guide vein is three to four times the size of a baseline common vein, so a player can tell one apart. At the 3% baseline it adds about 32% to a chunk's ore and about 18% to its Resonance; with Vein Expansion and Vein Proliferation at tier 5 it adds about 7%. **Guide veins take no generation node's size or count bonus**: they stay a fixed 12 at configured size 24, so they stay three to four times a common vein and recognisable. The 7% follows from that and from vein-size bonuses being measured in blocks (§11 Resonance and Mining-Speed Composition, "Vein size and count"). A flat count, so it matters less as the team grows, and it never switches off. The Survey panel shows guide-vein ore mined this trip. Chunks generated before purchase are not changed; they get guide veins only after a dimension reset (Nick, 2026-10-26; §3.1).
>
> **Copper or iron is decided per vein by the chunk's seeded random** (§3.1), 50/50, and by nothing else. Generation runs off the main thread with no player involved (§11 Dynamic Chunk Generator; `CLAUDE.md` rule 1), so no player's choice, stats or position can enter it, and a chunk comes out the same whenever it is first visited. There is no setting to choose the metal. Defined 2026-10-27.

---

**Salvager's Eye** `[GROWTH]`

> 5% of stone blocks you break in the Vault drop the raw form of a common ore (raw iron, raw copper or coal on vanilla), while the team is below level 12. Machine-broken stone never rolls it.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | 5% of stone blocks broken in the Vault drop one raw common ore (raw iron, raw copper or coal on vanilla) while team level < 12 | 1 |

> Makes the earliest and least productive mining pay something. Subject to the §3.4 rule: machine-broken stone never rolls this.
>
> **What drops.** No one chooses it. Each drop picks one of the ores classified Common (§4.2), weighted by its whole weight in the baseline, the expected blocks a chunk ADR 0009 gives it (`docs/adr/0009-baseline-ore-model-and-classifier.md`), not by its share in the band being mined: on vanilla coal 1,108, iron 646 and copper 134, so about 59% coal, 34% raw iron and 7% raw copper. It drops one of its raw form: the first item in the `c:raw_materials/<material>` item tag, matched by material name (`c:ores/iron` to `c:raw_materials/iron`). Where the picked ore has no raw form, as coal ore has none, the drop is one coal. Defined 2026-10-27.

---

**Vein Proliferation**

> More ore veins per newly explored chunk: +20% count, then +40%, +65%, +90%, +120%. Capped so stone never falls below 40% of a chunk.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +20% vein count | 2 |
| 2 | +40% vein count | 4 |
| 3 | +65% vein count | 7 |
| 4 | +90% vein count | 10 |
| 5 | +120% vein count | 14 |

> **Note:** Vein count is capped such that stone content never drops below 40% of generated blocks, regardless of combined Vein Expansion + Vein Proliferation investment.

---

**Deep Veins**

> The lowest 30 blocks above bedrock generate ore at 1.5× their band's baseline density (§3.1), raised to 2× at tier 2.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | The lowest 30 blocks above bedrock generate at 1.5× their band's baseline density (§3.1) | 5 |
| 2 | The same blocks raised to 2× their band's baseline density | 9 |

---

**Stone Reduction**

> 10% of the stone blocks touching a vein are converted into that vein's ore, raised to 20% at tier 2. Widens the veins already there rather than adding new ones.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | 10% of the stone blocks touching a vein are converted into that vein's ore | 3 |
| 2 | Raised to 20% of the stone touching a vein | 7 |

---

**Vein Shaping** `[FORK PARENT: Vein Shape]`

> Sets the shape ore takes in newly generated chunks. Does nothing on its own: pick Abundance, Vein Singularity or Stratified from the petals beside it, and each tier applies that option's row for the tier. Applies after Vein Expansion and Vein Proliferation and overrides both, still subject to the 40% stone floor.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Shaping applies at its listed strength | 4 |
| 2 | Shaping applies at its second-tier strength | 8 |

---

**Abundance** `[FORK OPTION: Vein Shape]`

> Through Vein Shaping tier 1: +50% vein count and −20% vein size. Tier 2: +100% count and −20% size. Every chunk has ore in it.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns a Vein Shaping node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Vein Shaping tier | Effect through this option |
|---|---|
| 1 | +50% vein count, −20% vein size |
| 2 | +100% vein count, −20% vein size |

---

**Vein Singularity** `[FORK OPTION: Vein Shape]`

> Through Vein Shaping tier 1: a chunk's baseline ore budget becomes 1, 2 or 3 veins, equally likely, each vein's rarity drawn from the band's mix, with total volume unchanged. Tier 2: 40% of chunks generate no ore, and the rest generate 1 or 2 veins carrying 2.08 times the baseline, a net +25% of ore. The 40% stone floor still applies.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns a Vein Shaping node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Vein Shaping tier | Effect through this option |
|---|---|
| 1 | The chunk's baseline ore budget becomes 1, 2 or 3 veins, equally likely, rarity drawn from the band mix; total volume unchanged |
| 2 | 40% of chunks generate no ore; the rest generate 1 or 2 veins carrying 2.08× the baseline, a net +25%; the 40% stone floor still applies |

> These numbers are derived and unplaytested; T9 confirms them.
>
> Replaces the earlier "Motherlode" node, which was mechanically identical to Vein Expansion
> under a different name. It carried a `[KEYSTONE]` tag while forks were paid nodes; as a free
> option under Vein Shaping it is a shape choice like its two siblings, and keystones live only at
> the far ends of the tree.

---

**Stratified** `[FORK OPTION: Vein Shape]`

> Ore generates in rarity-sorted horizontal bands instead of scattered blobs: rare ore only at Y=60 and below, uncommon from Y=61 to Y=150, common from Y=151 to Y=245. In an expanded Vault the rare band runs down to Y=−63. Vein Shaping tier 2 makes each band pick one ore type per chunk, weighted by share, which carries 90% of that band's ore, and narrows the blend at each band edge from 16 blocks to 4.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns a Vein Shaping node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Vein Shaping tier | Effect through this option |
|---|---|
| 1 | Rare ore only at Y≤60, uncommon Y=61–150, common Y=151–245, edges blending over 16 blocks; the Tome draws the band edges |
| 2 | Each band picks one ore type per chunk, weighted by share, carrying 90% of the band's ore; edges blend over 4 blocks |

> Tier 2's numbers and the 16-block blend are derived and unplaytested; T9 confirms them.

---

**Deep Habit**

> Every 1,000 blocks broken in a single trip adds +5% Resonance for the rest of that trip, up to +25% at 5,000 blocks. Resets to zero when you leave the Vault, and counts only blocks you break yourself.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Every 1,000 blocks broken in a single trip grants +5% Resonance for the rest of that trip, to +25% | 7 |

> Resets to zero on leaving the Vault, so it cannot compound across sessions. Counts only player-broken blocks (§3.4).

---

**Long Delve**

> Every 20 minutes inside the Vault grants a Delve stack worth +10% mining speed and −25% hunger drain, up to 2 stacks, raised to 4 at tier 2. A stack only accrues while you have broken a block in the last 60 seconds, and all stacks are lost on leaving.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Every 20 minutes inside the Vault grants a Delve stack, to 2, counting only time in which you have broken a block in the last 60 seconds. Each stack: +10% mining speed, −25% hunger drain | 7 |
| 2 | Stack cap raised to 4 | 12 |

> **AFK guard:** a stack only accrues while you have broken a block in the last 60 seconds. Without it this node pays players for standing still, which is the opposite of what it is for. Stacks are lost on leaving the Vault.

---

**Volatile Veins** `[TRADEOFF]`

> +25% vein size in newly explored chunks, but every ore block a team member breaks has a 3% chance to make the rest of that vein vanish into air with no drops. After 3 triggers in a row the next 10 ore breaks are guaranteed safe; that counter resets on logout. With FTB Ultimine the roll happens once per operation rather than once per block. Switched on and off by a team vote, and a change takes effect only once nobody is inside the Vault.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +25% vein size, 3% disappearance chance per ore block broken | 6 |

> **Generation tradeoffs are team-voted** (Nick, 2026-10-27: "Make it a vote system to enable where you need a majority (50% works if there are only 2 people), the change only takes effect once no one is in the ore vault."). This applies to Volatile Veins and Molten Seam, the two tradeoffs that change generation; every other tradeoff stays a per-player switch.
>
> - Each member's switch on the node is their vote. A member can change it anywhere, inside the Vault or out, since nothing changes until the Vault is empty.
> - The vote passes when more than half of the team's members have switched it on, or one of the two in a two-member team. A solo player's switch decides alone. The team's current members are counted whether or not they are online, since each member's switch is saved; a former member's switch, left in the old team's records (§9), is not counted.
> - The result is applied, on or off, only when nobody is inside the Vault: at the end of the tick in which the Vault becomes empty, or at the end of the tick the vote passes if it already is, and at server start before anyone connects, before the Vault is registered. Empty means no player other than a spectator is in the Vault's level; the check runs at the end of each tick for every Vault with a pending change, because a player who logs out is still in the level when the logout event fires. Until then the old state holds for everyone, and the Tome shows the vote count and the pending change. The pending change is not saved; it follows from the saved switches and the saved applied state.
> - Refunding the node switches the applied state off with it. Buying it again leaves it off until the next time the Vault is empty and the vote passes, so it never switches on under players already inside.
> - The applied state is the team's and holds for every member: everyone's chunks generate with it, and everyone's ore breaks roll the 3% while Volatile Veins is on. A per-player switch would give the bigger veins to the whole team while only the members who switched it on carried the vanishing risk.
>
> **A vanished vein closes silently**, as a machine-finished vein does (§3.4): no Vein Discipline step and no −3, no Chapter's End count, no Last Ore, no Clean Cut, no Vault Echo. Counting it as completed would let a 3% roll finish a vein for the streak after one block; counting it as abandoned would charge twice for a risk already paid in lost ore.
>
> **Its +25% sums with Vein Expansion's**, on the blocks a vein places (§11 Resonance and Mining-Speed Composition, "Vein size and count"): Vein Expansion tier 5 with Volatile Veins is +125%, not 2 × 1.25.

---

**Molten Seam** `[TRADEOFF]`

> Each newly explored chunk has a 50% chance of a lava pool of 5 to 20 lava blocks in the Vault's stone band, inside a two-block netherrack shell so you see one before you break into it. Pools are placed before ore, and no ore generates inside a shell. Ore mined within 8 blocks of lava drops already smelted and pays +25% Resonance. Switched on and off by a team vote (see Volatile Veins), taking effect only once nobody is inside the Vault. Voting this off stops new pools generating but leaves every pool already placed.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | 50% of newly explored chunks get a lava pool of 5 to 20 blocks in the stone band, in a two-block netherrack shell, placed before ore; no ore inside a shell. Ore mined within 8 blocks of lava drops smelted and pays +25% Resonance | 8 |

> The pool chance and size are derived and unplaytested; T9 confirms them.
>
> **This tradeoff is not fully reversible and the tooltip must say so.** It changes generation, so voting it off stops new pools appearing but leaves every pool already generated exactly where it is. Like Volatile Veins it is team-voted, and the applied state holds for every member. The netherrack shell is a hard requirement, not decoration: the whole reason a lava node is acceptable is that you never break the last block into an unseen pool.
>
> The smelted drop stacks with **Smelter's Intuition** by taking the better of the two rolls, not by rolling twice.

---

**Greedy Seams** `[PACT]` `[EXCLUSIVE: Resonant Overload]`

> +100% ore drops and −50% Resonance from ore. Cannot be held at the same time as Resonant Overload.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +100% ore drops, −50% Resonance from ore | 6 |

---

**Resonant Overload** `[PACT]` `[EXCLUSIVE: Greedy Seams]`

> +100% Resonance from ore and −50% ore drops. Cannot be held at the same time as Greedy Seams.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +100% Resonance from ore, −50% ore drops | 6 |

> Together these two form the tree's central axis: **materials now** versus **progression now**, and you must pick a side or neither. Greedy Seams at ring 6 deliberately undercuts Ore Doubling at tier 3 (a fork option whose third Ore Working node is ring 16) on raw yield — buying it early is a real option that costs you the pace of the whole tree. That early-buy option is why both are `[PACT]` rather than `[KEYSTONE]`: at a keystone's ring it could not exist. Both are leaves off one junction (§6), and each costs 12 XP levels to refund (§4.4).

---

**Ore Attunement** `[FORK PARENT: Ore Focus]`

> Sets which rarity band the Vault favours. Does nothing on its own: pick Common, Uncommon or Rare Focus from the petals beside it, and this node's 3 tiers apply through it. One Focus at a time unless Full Spectrum lifts the restriction.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Focus applies at its listed strength | 3 |
| 2 | Focus applies at its second-tier strength | 6 |
| 3 | Focus applies at its third-tier strength | 10 |

---

**Common Focus** `[FORK OPTION: Ore Focus]`

> Through Ore Attunement: +25% common ore vein count at tier 1, +50% at tier 2, +80% at tier 3.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns an Ore Attunement node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Ore Attunement tier | Effect through this option |
|---|---|
| 1 | +25% common ore vein count |
| 2 | +50% common ore vein count |
| 3 | +80% common ore vein count |

---

**Uncommon Focus** `[FORK OPTION: Ore Focus]`

> Through Ore Attunement: +25% uncommon ore vein count at tier 1, +50% at tier 2, +80% at tier 3.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns an Ore Attunement node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Ore Attunement tier | Effect through this option |
|---|---|
| 1 | +25% uncommon ore vein count |
| 2 | +50% uncommon ore vein count |
| 3 | +80% uncommon ore vein count |

---

**Rare Focus** `[FORK OPTION: Ore Focus]`

> Through Ore Attunement: +30% rare ore vein count at tier 1, +60% at tier 2, +100% at tier 3.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns an Ore Attunement node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Ore Attunement tier | Effect through this option |
|---|---|
| 1 | +30% rare ore vein count |
| 2 | +60% rare ore vein count |
| 3 | +100% rare ore vein count |

---

**Geode Clusters**

> Amethyst geodes generate in the Vault at one per 12 newly explored chunks, then one per 6. The overworld rate is roughly one per 24.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | One geode per 12 newly explored chunks | 4 |
| 2 | One geode per 6 newly explored chunks | 8 |

---

**Ancient Traces**

> Ancient debris generates below Y=0 at one per 4 newly explored chunks, then one per 2. Needs an expanded Vault to have a band below Y=0 at all. Debris ignores every ore-multiplying node in the tree and always drops exactly one.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | One ancient debris per 4 newly explored chunks, below Y=0 only | 21 |
| 2 | Raised to one per 2 newly explored chunks | 26 |

> **Balance rules — ancient debris is exempt from everything.** It is not affected by Vein Expansion, Vein Proliferation, the Vein Shape fork, Ore Focus, Vein Fortune, Ore Doubling, Smelter's Intuition, Greedy Seams, Twin Veins, Kindred Rock, or Molten Seam's smelted drop. It generates at a fixed rate and drops exactly one. This is deliberate: the node exists so a team never *has* to go back to the Nether, not so the Vault becomes a netherite farm. Its rings, 21 and 26, put it at roughly the 20-hour mark rather than early, and it pays nothing before Vault Expansion opens a band below Y=0.

---

**Ancient Knowledge**

> Ore blocks drop +1 bonus XP on top of their normal amount, then +2, then +4.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +1 XP per ore mined | 2 |
| 2 | +2 XP per ore mined | 5 |
| 3 | +4 XP per ore mined | 9 |

---

**Stonecutter's Patience**

> +1 XP per ore mined for every 10,000 natural stone blocks you have broken in the Vault, capped at +3, raised to +5 at tier 2.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +1 XP per ore for every 10,000 natural stone broken in the Vault, to a maximum of +3 | 6 |
| 2 | Maximum raised to +5 | 11 |

> **What counts as stone** is the `#orevault:vault_stone` block tag, which the mod ships containing `#minecraft:base_stone_overworld` (stone, granite, diorite, andesite, tuff, deepslate) and `#c:stones`. Modded stone that follows either convention counts with no per-mod work, and a pack can extend the tag in a datapack for anything that does not. `stone_broken` on `PlayerStats` counts against the same tag, so a team's existing total carries forward.

---

**Calloused Hands**

> Resonance from every ore rises with your lifetime blocks broken, by 0.03 × ln(1 + blocks / 10,000) — about +7% at 100,000 blocks and +12% at 500,000. Tier 2 raises the coefficient to 0.05 and tier 3 to 0.07, giving +17% and +28% at those counts. Hard-capped at +40%.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +Resonance equal to `0.03 × ln(1 + blocks / 10000)`, a fraction of the base: 0.072 is +7.2% | 6 |
| 2 | Coefficient raised to `0.05` | 12 |
| 3 | Coefficient raised to `0.07` | 18 |

> Reads `total_blocks_broken` on `PlayerStats`, which is lifetime and never resets. Hard-capped at +40%, which tier 3 reaches at (e^(0.4/0.07) − 1) × 10,000, about 3.0 million blocks, and therefore never in practice — the cap exists so no future coefficient change can produce an unbounded multiplier.
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

> Breaking the first block of a vein outlines the rest of that vein through solid stone for 30 seconds.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | On breaking a vein's first block, the remainder of that vein is outlined for 30 seconds | 5 |

> Distinct from **Prospector's Eye**, which outlines *other* veins nearby. This one outlines the one you are standing in.
>
> This node is load-bearing for the whole vein-completion group. Without it, finishing a vein is something that happens to you and Vault Echo, Vein Discipline, Last Ore, Clean Cut and Chapter's End all pay out for luck. With it, completion becomes a thing you choose to do. It lies before Vault Echo on every path from the start so that it arrives first (§6.1 Shape of the tree), and Prospector's Eye hangs off it as a leaf.

---

**Stonecaller** `[NOTABLE]`

> Stone you break in the Vault has a 3% chance to drop the ore type of the nearest vein within 8 blocks. Nothing happens if no vein is in range, so it pays in fresh rock and not in stripped-out areas.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Stone has a 3% chance to drop the ore type of the nearest vein within 8 blocks | 12 |

> Uses the vein index (§11) for the nearest-vein lookup — no flood fill, no scan. If no indexed vein is within range the roll is skipped, so this is worthless in stripped-out areas and best in fresh chunks, which is the intended pull.

---

**Prospector's Eye** `[NOTABLE]`

> Breaking the first block of a vein outlines every other vein within 16 blocks through solid stone for 5 seconds.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | On first block of a vein: nearby veins outlined for 5 seconds, 16-block radius | 8 |

> A leaf whose only neighbour is Vein Sight, so it needs Vein Sight (Nick, 2026-10-26).

---

**Stone Curse** `[TRADEOFF]`

> Stone Memory XP is tripled, but stone drops no items at all inside the Vault — no cobblestone, no flint, no nuggets.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | 3× Stone Memory XP, stone drops nothing | 4 |

---

**Vein Fortune**

> Ore mined in the Vault is treated as having Fortune I, then Fortune II, then Fortune III. Adds to the tool's own Fortune rather than replacing it.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Fortune I equivalent | 5 |
| 2 | Fortune II equivalent | 9 |
| 3 | Fortune III equivalent | 13 |

> Renamed from "Ore Sense", which described a sensing mechanic it never had — that name now
> belongs to Prospector's Eye above.

---

**Ore Working** `[FORK PARENT: Yield]`

> Squeezes more out of each ore block. Does nothing on its own: pick Ore Doubling or Smelter's Intuition from the petals beside it, and this node's tiers apply through it. Nodes 4 to 6 exist only with Ore Doubling chosen and the Mekanism-style tags filled (§9). Vein Fortune is separate and applies on top of either option.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Working applies at its listed strength | 8 |
| 2 | Working applies at its second-tier strength | 12 |
| 3 | Working applies at its third-tier strength | 16 |
| 4 | *(Ore Doubling + Mekanism only)* | 20 |
| 5 | *(Ore Doubling + Mekanism only)* | 25 |
| 6 | *(Ore Doubling + Mekanism only)* | 30 |

> Nodes 4–6 sit on a dead-end spur past the third Ore Working node. They are shown only when Ore
> Doubling is the chosen option **and** the `c:clumps/`, `c:shards/` and `c:crystals/` item tags have
> entries (§9); otherwise they are hidden, not shown as locked. While the team owns any of them, Ore
> Doubling cannot be swapped away; those nodes are refunded first (§4.4). Hidden because the tags
> emptied, an owned one is dormant (§6).
> Smelter's Intuition tops out at tier 3, so a team on that option sees three Ore Working nodes.

---

**Ore Doubling** `[FORK OPTION: Yield]`

> Through Ore Working: +25% average ore yield at tier 1, +50% at tier 2, a guaranteed 2× at tier 3. With Mekanism loaded, tier 4 drops clumps (3×), tier 5 shards (4×) and tier 6 crystals (5×). Without Mekanism it falls back to mod ore dust, then to extra raw ore.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns an Ore Working node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Ore Working tier | Effect through this option |
|---|---|
| 1 | +25% average ore yield |
| 2 | +50% average ore yield |
| 3 | Guaranteed 2× ore yield |

> **Mekanism-tag tiers.** Ore Working's nodes 4–6 exist only while this option is chosen and the
> Mekanism-style tags are filled (§9):

| Ore Working tier | Effect through this option |
|---|---|
| 4 | Drops Clumps (3×) |
| 5 | Drops Shards (4×) |
| 6 | Drops Crystals (5×) |

---

**Smelter's Intuition** `[FORK OPTION: Yield]`

> Through Ore Working: ore has a 15% chance to drop already smelted instead of raw at tier 1, 40% at tier 2, 75% at tier 3. No extra material — it skips the furnace.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns an Ore Working node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Ore Working tier | Effect through this option |
|---|---|
| 1 | 15% chance of smelted drop |
| 2 | 40% chance |
| 3 | 75% chance |

---

**Runic Attunement**

> 5% of ore drops come out Attuned, rising to 12%, then 20%. Four Attuned raw ore of any type plus one amethyst shard craft a Resonance Crystal, which every Vault Igniter upgrade and every Vault Anchor needs.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | 5% chance ore drops are Attuned | 10 |
| 2 | 12% chance | 14 |
| 3 | 20% chance | 17 |

> Attuned ore is an ordinary raw-ore stack carrying a data component. It smelts, stacks, and processes exactly like the unattuned version, so nothing breaks if a player feeds it into a machine — they simply lose the crystal. Recipe: 4 Attuned raw ore (any type) + 1 Amethyst Shard → 1 Resonance Crystal. *(The previous version of this node granted "bonus effects when processed by magic-based ore processing mods", naming no mod and defining no mechanism — 10 skill points that did nothing.)*

---

**Kindred Rock**

> Mining 1,000 blocks of any single ore type permanently grants you +15% Resonance from that type. A type that has earned the bonus is kindred to you, and tier 2 makes kindred types break 20% faster. Tracked per player and per ore type, with no limit on how many types can become kindred.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Mining 1,000 of any single ore type permanently grants +15% Resonance from that type, for that player | 10 |
| 2 | Kindred types (those that have earned the +15%) also break 20% faster | 15 |

> Reads the existing `ores_mined` on `PlayerStats` map, keyed by block id, so it works with any modded ore in a kitchen-sink pack with no per-mod configuration and no ore list to maintain. Ancient debris is exempt (see Ancient Traces).
>
> Per-player rather than per-team on purpose: it is a record of what *you* have mined, and making it team-wide would let one member's grind hand the bonus to everyone.

---

**Highwater Mark**

> Each ore you mine repairs the held tool by 1 durability, plus 1 more for every full 16 blocks your all-time deepest Y sits below Y=64: floor((64 − deepest_y) / 16), clamped to 0 to 8, so up to 9 per ore. With no deepest Y recorded the extra is 0. Tier 2 sends the same repair to damaged armour when the held tool needs none. Costs no XP and stacks with Mending.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Each ore mined repairs the held tool by 1 durability, plus floor((64 − deepest_y) / 16), clamped to 0 to 8 and 0 with no deepest Y recorded — up to 9 per ore | 9 |
| 2 | When the held tool needs no repair, the same amount is applied to worn armour, split across damaged pieces | 14 |

> The rounding is derived and unplaytested; T9 confirms it.
>
> Reads `deepest_y` on `PlayerStats`, which is lifetime and never decreases, so the bonus is earned once by delving and kept forever. Repair is capped at the item's maximum durability, never applies to items that cannot be damaged, and unlike Mending consumes no XP — the two stack.
>
> Replaces an earlier version that paid +1% ore per 8 blocks of depth record. That topped out near +8%, which is not worth a node; durability is the thing a miner actually runs out of.

---

**Brittle Stone** `[PACT]`

> Every block in the Vault breaks instantly. In exchange each ore block has a 10% chance to shatter with no drops, and every ore you break destroys 1 random face-adjacent ore block of the same vein, if there is one. The destroyed block drops nothing and pays nothing, but counts toward completing the vein.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | All blocks in the Vault break instantly; each ore block has a 10% chance to shatter with no drops, and each ore break destroys one random face-adjacent ore block of the same vein, if any, which drops and pays nothing but counts toward completion | 13 |

> Which block is destroyed is derived and unplaytested; T9 confirms it.
>
> The adjacent-ore collateral is what makes instant breaking a real bargain rather than a straight upgrade: with it, a big vein loses a large fraction of itself to the speed. It also interacts badly with Vein Discipline on purpose — the collateral counts as breaking the vein, so the two together are a build that wants deliberate handling.

---

**Vault Fever** `[TRADEOFF]`

> Haste II for as long as you are inside the Vault, and −25% Resonance from every ore.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Haste II inside the Vault, −25% Resonance from ore | 7 |

> The cost used to be +50% hunger drain, which Miner's Constitution Tier 5 (hunger frozen entirely) cancelled outright — the two together gave permanent free Haste II. Pricing Fever in Resonance instead makes the two nodes independent, and leaves Miner's Constitution as a clean quality-of-life ladder with no hidden interaction.

---

**Vault Presence**

> The team's Vault can keep 4 more chunks loaded at once, then 8 more (12 total), then 16 more (28 total). Server config caps the total, at 32 by default.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +4 simultaneous loaded chunks | 5 |
| 2 | +8 simultaneous loaded chunks (12 total) | 9 |
| 3 | +16 simultaneous loaded chunks (28 total) | 14 |

> Admin config can set a hard ceiling on maximum loaded chunks regardless of node level. Default ceiling: 32.

---

**Automated Extraction**

> +25% ore yield from blocks broken by machines in the Vault, raised to +50%. Machine-broken blocks still award no Resonance, but they do count toward vein completion and statistics.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +25% ore yield from machine-broken blocks in the Vault; machine breaks count toward vein completion and statistics | 8 |
| 2 | +50% ore yield from machine-broken blocks | 12 |

> **Implementation:** hook `BlockDropsEvent`, whose `getBreaker()` is a `@Nullable Entity` ("or null if unknown") and whose drop list is mutable. This covers machines that route through `Block.dropResources` / `Level.destroyBlock`, which is the large majority. Mods that build drops themselves and insert straight into their own inventory will not fire it; that gap is accepted.

---

**Far Reach**

> Chunks you pass through stay loaded for 30 seconds after you leave them, raised to 2 minutes. These count against the Vault Presence limit like any other, oldest evicted first.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Chunks you pass through remain loaded for 30 seconds after you leave | 12 |
| 2 | Raised to 2 minutes | 16 |

> Trailing tickets count against the Vault Presence ceiling and the config ceiling like any other, oldest evicted first. This node makes the ceiling matter rather than raising it.

---

**Seismic Sense** `[NOTABLE]`

> The Tome gains a compass pointing toward denser rock, at chunk granularity. Tier 2 turns it into a map that draws explored chunks and leaves unexplored ones blank; tier 3 shades the drawn chunks by ore density.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Chunk-level ore density compass in the Tome UI | 6 |
| 2 | The compass becomes a map: explored chunks are drawn, unexplored ones are blank | 11 |
| 3 | Drawn chunks are shaded by ore density | 15 |

> Tiers 2 and 3 absorb what was going to be a separate Cartographer's Instinct node. Two chunk-density readouts competing in the same Tome is one too many, so the map is this node's later tiers instead of a rival to it.
>
> **What it reads.** An explored chunk is a chunk of the team's current Vault that has been generated: the same event as "newly explored" in the generation nodes of §6.1, whether or not anyone walked into it. Wanderer's Cache counts something else, the chunks a member has stood in (Nick, 2026-10-27; below), so the map and the cache count measure different things. A chunk's density is its standing indexed ore, so it falls as the team mines. The compass and the map cover 33 by 33 chunks centred on the viewer's chunk, and only inside the team's Vault. The compass points only into explored chunks, never into ground nobody has generated. It points to the explored chunk in that window with the most standing ore when that chunk beats the viewer's own by a margin, and keeps its current target until another chunk beats that target by the same margin; below the margin it shows "no denser rock nearby", which the readout says in words. The margin stops the needle swinging between neighbours whose counts differ by roll luck from one refresh to the next. The reference for both the margin and tier 3's shade is the standing ore a newly generated chunk would hold under the team's current generation nodes. The margin is the named constant `COMPASS_MARGIN`, 0.10: a candidate must hold more standing ore than the chunk it is compared with (the viewer's own, or the kept target) by at least 10% of the reference, rounded up to a whole block. With no generation nodes that is 192 blocks in the base type (10% of 1,911) and 292 in the expanded type (10% of 2,915). A margin in proportion to the reference stays at or above about 5 standard deviations of a fresh chunk's roll luck under every generation node; a fixed block count would not, since 192 blocks is only 4.2 in the expanded type (Balancer, 2026-10-31). So the needle moves once the viewer's chunk has lost about a tenth of its ore, about an hour of mining in one chunk, or toward ground generated after a generation purchase: Vein Expansion tier 1, Vein Proliferation tier 1 and Guide Vein each clear it. The value is derived and unplaytested; E9-S32a (#336), where the Seismic readout is built, confirms it. Chunks not yet generated are never predicted. A reset starts the map empty. The state is kept per Vault (§11 Vein Index; `docs/adr/0004-vein-index.md`, Amendment 1).

---

**Wanderer's Cache**

> Every 50 Vault chunks you are the first of your team to stand in drops a Wanderer's Cache, a supply cache of food and torches that also restores 25% of maximum durability to the most-damaged tool in the opener's hotbar. Tier 2 doubles the contents, raises the repair to 50%, and adds a 20% chance of an enchanted book.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Every 50 Vault chunks a player is the first of the team to stand in drops a Wanderer's Cache: food and torches, and opening it restores 25% of maximum durability to the most-damaged tool in the opener's hotbar | 6 |
| 2 | Cache contents doubled, the repair raised to 50%, with a 20% chance of an enchanted book | 12 |

> The repair amounts are derived and unplaytested; T9 confirms them.
>
> **The item.** `orevault:wanderers_cache`, named "Wanderer's Cache": one item for both tiers, with its own texture, model, item definition and creative tab entry (§13). The id is save data from T5 (§11 SavedData Pattern).
>
> **How a cache drops.** Nick chose chunks walked into, 50 a cache, on 2026-10-27, over generated chunks. The count is per player: `chunks_entered` on `PlayerStats` (§8 Tab 3). The key says entered, not explored, because an explored chunk is a generated one (Seismic Sense, above), and the cache counts something else. A chunk of the team's current Vault is credited to the first member of the Vault's team to stand in it, once: an end-of-tick check on the main thread looks at the chunk each member stands in, not at the chunk's first load, and the chunk attachment carries one flag, set the first time a member stands in the chunk (§11 Vein Index), and a chunk with the flag set never counts again for anyone. Spectators and players who are not current members are passed over and do not set the flag. Each time a player's count reaches a multiple of 50 while the team owns the node, one cache drops as an item at that player's position; chunks counted before the purchase move the count but drop nothing. Fifty chunks is about 800 blocks of straight tunnel, about 13 minutes, at any view distance and in any direction. Counting generated chunks instead, as first written, paid about 20 times the intended rate: crossing one chunk generates 21 at view distance 10, so tunnelling dropped a cache about every 40 seconds, the first arrival dropped 8 at once, digging straight down dropped none, and a higher view distance dropped more. Chunks loaded by an Anchor, Far Reach, a machine or a command are never stood in, so they earn no caches. Because the count is on `PlayerStats`, it survives logouts, server restarts and dimension resets; a team change leaves it with the old team (§9).
>
> **Which tier.** The tier is the dropping player's team's tier at the moment of the drop, stamped on the item as the item component `orevault:cache_tier`, frozen with the other registry ids (ADR 0005 section 6). Opening the cache later, or by a player on another team, uses the stamped tier.
>
> **Opening it.** Using the cache opens it: it rolls the loot table `orevault:chests/wanderers_cache` into the opener's inventory, dropping what does not fit, and repairs the most-damaged tool in the opener's hotbar by 25% of its maximum durability (50% at tier 2). Only damageable items are candidates; "most damaged" is damage as a fraction of maximum durability, and a tie goes to the lower hotbar slot. With no damaged item in the hotbar nothing is repaired. At tier 2 the table is rolled twice, and the loot table `orevault:chests/wanderers_cache_book` is rolled once: it holds one enchanted book behind a `random_chance` condition of 0.2, so the 20% is the shipped table's value and a pack can retune it like the rest of the cache. The contents are loot tables so that a pack can retune them without touching code. Defined 2026-10-27.

---

**Frontier Bonus**

> Ore mined in a chunk generated within the last 10 minutes pays +40% Resonance.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Ore mined in a chunk generated within the last 10 minutes pays +40% Resonance | 8 |

> Deliberately rewards moving on rather than strip-mining one spot to exhaustion, which is the behaviour every other node in the tree encourages.

> "Generated within the last 10 minutes" is 12,000 ticks of server game time since the chunk first became a full chunk, recorded on the chunk (§11 Vein Index), so a restart neither ages nor freshens it.

---

**Pathfinder's Claim**

> The first vein you mine in each newly generated chunk grants 50 vanilla XP.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | The first vein mined in each newly generated chunk grants 50 vanilla XP | 7 |

> Each chunk pays at most once, team-wide, for its first hand-broken indexed ore. The chunk's `pathfinder_claimed` flag (§11 Vein Index) is set on that first break **whether or not the team owns the node**, and the 50 XP is paid only if it does. So a chunk the team has already mined into never pays later, and a chunk generated before the purchase but never mined pays once, which matches "the first vein mined in each chunk" with no stored purchase time. Setting the flag only while the node is owned would pay 50 XP for every old chunk the team revisits after buying it (Balancer, Epic 0 round 1). Chunks generated before the index existed pay nothing (§11 Vein Index).

---

**Homeward Seam** `[NOTABLE]`

> Set one anchor anywhere inside the Vault and teleport back to it from anywhere else inside. The cast takes 3 seconds and is cancelled if you take damage; the five-minute cooldown starts only after a cast succeeds. Never crosses into or out of the Vault, and a dimension reset clears the anchor.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Set one anchor point inside the Vault; teleport to it from anywhere else inside the Vault with a 3-second (60-tick) cast, cancelled if you take damage while casting. Five-minute cooldown, starting after a successful cast | 10 |

> The 3-second cast is derived and unplaytested; T9 confirms it.
>
> Only works Vault-to-Vault, never in or out. The anchor is stored per player and is cleared by a dimension reset (§3.5).

---

**The Vault Keeps It**

> Items you drop on death inside the Vault never despawn and cannot be destroyed by fire, lava or explosion, and the Tome marks the spot. Carrying them out returns them to normal rules.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Items dropped on death inside the Vault never despawn and cannot be destroyed by fire, lava or explosion; the Tome marks the spot | 10 |

> Removes the worst case of a death without removing the setback — you still have to walk back for your things. Items are exempted from despawn only inside the Vault; carrying them out returns them to normal rules.
>
> The spot the Tome marks is the Survey panel's death marker (§8 Tab 3), the player's last death in the current Vault.

---

**Second Wind**

> Once per Vault trip, damage that would kill you leaves you at half a heart instead. Resets when you leave the Vault.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Once per Vault trip, damage that would kill you leaves you at half a heart instead. Resets on leaving the Vault | 14 |

---

**Second Wind: Homeward** `[TRADEOFF]`

> While enabled, a Second Wind save also returns you to the portal you entered by, instead of leaving you where you nearly died.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | While enabled, a Second Wind save also returns you to your entry portal | 20 |

> A separate node rather than a second tier of Second Wind precisely so it can be toggled: the tradeoff machinery already stores on/off state per player (§6.1 notation), and being yanked out of a deep shaft is not always what a player wants. Its ring, 20, is six levels past Second Wind's, as it turns a survival save into a free escape. It is a leaf whose only neighbour is a Second Wind node.

---

**Vault's Blessing** `[EXCLUSIVE: Vault's Purity]`

> Potion effect durations do not tick down at all while you are inside the Vault. Cannot be held at the same time as Vault's Purity.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Potion effect durations are frozen inside the Vault | 8 |

---

**Vault's Purity** `[EXCLUSIVE: Vault's Blessing]`

> Potion effects are stripped when you enter the Vault and none can be applied inside. In exchange, while you are unaffected: +20% Resonance and +1 effective Fortune. Cannot be held at the same time as Vault's Blessing.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | No potion effects can exist inside the Vault; while unaffected: **+20% Resonance and +1 effective Fortune** | 8 |

> Purity previously granted nothing at all in exchange for stripping effects — a node of pure downside that only ever mattered against witches, which do not spawn in a Vault. As a pair these now describe two real builds: stack potions and keep them forever, or forgo them entirely for flat power that never runs out.

---

**Tithe** `[TRADEOFF]`

> 25% of the ore blocks you mine are consumed by the Vault — the block breaks and drops nothing — and each consumed block pays 1.75× its Resonance instead. Only ore blocks are taken: Stone Memory drops, nuggets and flint are untouched.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | 25% ore consumed, 1.75× Resonance on consumed ores | 5 |

---

**Vault Echo**

> Mining a vein to completion pays a burst of 25 Resonance, rising to 35, then 50.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +25 Resonance burst on vein completion | 3 |
| 2 | +35 Resonance burst | 6 |
| 3 | +50 Resonance burst | 9 |

> Every path from the start reaches Vein Sight (ring 5) before the first Vault Echo node, even though Vault Echo's first ring is 3 (§6.1 Shape of the tree).

---

**Echo Chamber** `[NOTABLE]`

> Vault Echo bursts also grant vanilla XP equal to the Resonance awarded, so a 50 Resonance burst is also 50 XP.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Vault Echo bursts additionally grant XP 1:1 with the Resonance awarded | 11 |

> A leaf off the furthest Vault Echo node, so it is reached through Vault Echo.

---

**Twin Veins**

> Completing a vein has a 1% chance to spawn a copy of it, rising to 5%, then 10%. The copy has the same shape and sits 2 to 4 blocks clear of the original's bounding box in a random horizontal direction, inside the same chunk, replacing only stone and deepslate. If no direction fits, no copy is made. The new vein is indexed like any other, so it can trigger this again.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | 1% chance on vein completion | 6 |
| 2 | 5% chance on vein completion | 10 |
| 3 | 10% chance on vein completion | 14 |

> The placement rule is derived and unplaytested; T9 confirms it.
>
> Veins created by Twin Veins are registered into the vein index (§11) exactly as generated veins are, so they themselves can trigger Vault Echo and Twin Veins on completion.

---

**Last Ore**

> The final ore block of any vein drops double and pays double Resonance. Stacks with the Vault Echo burst, which is counted separately.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | The final ore block of any vein drops double and pays double Resonance | 10 |

> Makes finishing feel different from mining, which is the whole reason the completion group exists. Stacks multiplicatively with the Vault Echo burst, which is separate.
>
> Last Ore pays on the hand break that completes the vein, including when the vein's last blocks go to that break's own collateral, such as Brittle Stone's: in a compact vein the last two blocks usually touch, and otherwise Brittle Stone would switch Last Ore off. A vein that Volatile Veins vanishes, or that a machine finishes, pays no Last Ore.

---

**Clean Cut**

> A vein completed without a player breaking a single non-ore block inside its bounds between its first and last ore pays +50% Resonance across the whole vein.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | A vein completed with no non-ore block inside its bounds broken by a player between its first and last ore pays +50% Resonance across the whole vein | 13 |

> "By a player" because only a player's break is seen: a machine that clears a non-ore block inside the vein's bounds does not spoil the bonus (`docs/adr/0004-vein-index.md`).

> Rewards precision, and is the one node in the tree that Ultimine makes harder rather than easier. It pairs naturally with **Vein Sight**, which is what makes a clean cut plannable at all.

---

**Vein Discipline**

> +2% Resonance for each vein completed in a row, stacking to +30% at 15 veins and persisting across trips. Abandoning a part-mined vein — leaving the Vault or moving more than 48 blocks away with ore still standing — drops the streak by 3 rather than resetting it.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +2% Resonance per completed vein, stacking to +30% at 15 veins. Persists across trips | 12 |

> **Abandoning** is defined as: you have broken at least one block of a vein, and then either leave the Vault or move more than 48 blocks from it, with ore still standing. Abandoning drops the streak by 3, not to zero.
>
> **The streak is saved and survives logout.** It lives on `PlayerStats` (`vein_discipline_streak`, §8), so a relog, a server restart and a dimension reset all keep it; a team change leaves it with the old team (§9). Leaving the Vault means every route §11 Vault Session State watches: the portal, death, a command and logging out. So a logout with a part-mined vein is an abandon: the streak drops by 3 at the logout, worked out from the in-memory trip record (§11), which lists the veins you have broken into and not finished. Nothing about a part-mined vein is saved, so a server crash, which runs no logout, applies no abandon and the streak keeps its last saved value. A clean server stop is a logout for every player, so it applies the abandon to each player with a part-mined vein, once; in singleplayer, quitting the world is both. Settled from this section and §11 (2026-10-27; the clean stop by the Manager in Epic 0 round 1, since it costs at most one slip a session, which the decay rule absorbs).
>
> A hard reset was the first version and it is wrong: one stray swing at the edge of a vein you had not noticed would cost fifteen veins of work, and the correct response to that is to stop exploring and mine defensively — the opposite of what the tree wants. Decay keeps the pressure without the feel-bad.
>
> Stored as `vein_discipline_streak` on `PlayerStats`, defaulting to 0.

---

**Chapter's End** `[NOTABLE]`

> Completing 5 veins within a single chunk fires a 150 Resonance burst and reveals every vein still standing in that chunk. Counted over all time rather than per trip, so each chunk fires once.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Completing 5 veins within a single chunk fires a Resonance burst worth 3× Vault Echo T3 and reveals every remaining vein in that chunk | 16 |

> Named to avoid reusing "Motherlode", which is a reserved node id. Counts veins completed in the chunk over all time, not per trip, so it fires once per chunk.
>
> **The reveal lasts 30 seconds (600 ticks).** It fires once per chunk, so it must last long enough to tunnel to the nearest vein it shows, about 30 blocks at 60 blocks a minute. Prospector's Eye's 5-second outline fires on every vein and is unchanged. Unplaytested; T9 confirms it.
>
> **Storage.** The count and the fact that a chunk has fired live on the chunk itself, in the versioned chunk attachment that holds the vein index (§11), not in `PlayerStats` or the team's data. A team-wide map of every chunk would only ever grow; on the chunk, the state costs nothing for chunks never visited and is cleared with the chunks by a reset.

---

**Deep Harvest** `[NOTABLE]`

> Ore mined below Y=0 drops 1 extra Resonance orb worth half the ore's base Resonance (1, 2.5 or 6.25 by rarity), with no bonuses applied. Only an expanded Vault has a band below Y=0 for this to apply in.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Ore below Y=0 drops one extra Resonance orb worth half the ore's base Resonance (1 / 2.5 / 6.25), no bonuses applied | 20 |

> The orb's value is derived and unplaytested; T9 confirms it.

---

**Resonant Draw** `[FORK PARENT: Orb Collection]`

> Changes how Resonance orbs reach you. Does nothing on its own: pick Resonance Magnetism or Hoarder's Instinct from the petals beside it, and this node's 3 tiers apply through it.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | Draw applies at its listed strength | 5 |
| 2 | Draw applies at its second-tier strength | 9 |
| 3 | Draw applies at its third-tier strength | 13 |

---

**Resonance Magnetism** `[FORK OPTION: Orb Collection]`

> Through Resonant Draw: orbs are pulled to you from 12 blocks at tier 1, 16 blocks at tier 2, 24 blocks at tier 3, against the 8 blocks every orb already has (§4.2).

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns a Resonant Draw node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Resonant Draw tier | Effect through this option |
|---|---|
| 1 | Orb attraction radius: 12 blocks |
| 2 | Orb attraction radius: 16 blocks |
| 3 | Orb attraction radius: 24 blocks |

> Tier 1 was 8 blocks, which is vanilla's experience orb radius and the base every Resonance orb already has (§4.2), so it did nothing; it became 12 (Balancer, Epic 0 round 1). Unplaytested; T9 confirms it.

---

**Hoarder's Instinct** `[FORK OPTION: Orb Collection]`

> Orbs never move toward you and never despawn; orbs resting within 4 blocks of each other merge into a single cache. An orb is resting once it has been on the ground for 20 ticks moving less than 0.01 blocks a tick; merges are checked every 20 ticks. Through Resonant Draw tier 2, collecting a cache of N orbs (N counts orbs, not Resonance) pays ×(1 + 0.1 × N) up to ×3; tier 3 lets caches pull in orbs from other caches within 8 blocks and raises the cap to ×4.

**A petal, not a node.** Costs nothing, has no edges and lies on no path. Can be picked once the team owns a Resonant Draw node. One option at a time; picking one locks its siblings, and swapping or unpicking is free, outside the Vault only (§4.4).

| Resonant Draw tier | Effect through this option |
|---|---|
| 1 | Orbs are stationary and permanent; orbs resting within 4 blocks merge into one cache |
| 2 | Cache collection pays ×(1 + 0.1 × N), capped at ×3 |
| 3 | Caches pull in orbs from neighbouring caches within 8 blocks; cap raised to ×4 |

> The resting rule and the 20-tick check are derived and unplaytested; T9 confirms them.
>
> The previous version of this node removed orb attraction and gave a flat 2× on manual pickup, which was strictly worse than Magnetism Tier 1 in ordinary play — an exclusive choice where one side was simply wrong. Merging plus permanence makes it a different way to mine (clear-then-sweep) rather than a worse one.

---

> **Deferred to the Animus epic.** The **Disturbed Zone Unlock** node previously sat at the root of this tree. Disturbed Zones and the whole Animus system have moved to a separate post-1.0 epic, and this node moves with them — it will re-enter the Resonance tree as a node when that epic is scheduled.

---

The next three skills need FTB Ultimine. They sit on a dead-end spur with Steady Chain (§6.1 Flavour notables) and are hidden without Ultimine.

**Ultimine Expansion** `[REQUIRES: FTB Ultimine]`

> +25% to the maximum number of blocks FTB Ultimine breaks per operation inside the Vault, then +50%, then +100%.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | +25% max block break count | 3 |
| 2 | +50% max block break count | 7 |
| 3 | +100% max block break count | 12 |

---

**Ultimine Safety** `[REQUIRES: FTB Ultimine]`

> −1% Volatile Veins disappearance chance during Ultimine operations, then −2%. Against Volatile Veins' 3% base that leaves Ultimine operations at 2%, then 1% — reduced, never zero.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | −1% disappearance chance during Ultimine operations | 5 |
| 2 | −2% disappearance chance, floored at 1% | 9 |

---

**Volatile Veins: Ultimine Gambit** `[TRADEOFF]` `[REQUIRES: FTB Ultimine]`

> While the team's Volatile Veins is on, each Ultimine operation rolls Volatile Veins' 3% twice instead of once, so 5.91% of operations vanish. Operations that survive pay +20% Resonance.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 | The 3% disappearance roll is made twice per Ultimine operation (5.91% vanish); surviving operations pay +20% Resonance | 9 |

> The double roll is derived and unplaytested; T9 confirms it.
>
> On the hidden Ultimine dead-end spur with Ultimine Expansion, Ultimine Safety and Steady Chain, like every other mod-gated node. It is still a per-player switch, and acts only while the team's vote has Volatile Veins on.

---

#### Travel stats

> Travel nodes are the steps between real nodes. Each grants one step of one of the twelve stats below, sized so a player feels a single node on its own (+10% mining speed, one heart, one block of reach), and each stat has a cap for the whole tree. A stat's node count is its cap divided by its step. All twelve work with no pack mods installed. Ten were chosen for mining and the mods in Nick's pack; the last two make work outside the Vault easier (Nick, 2026-10-26).

| Stat | Per node | Nodes | Whole-tree cap | Applies | Hook |
|---|---|---|---|---|---|
| Mining speed | +10% | 6 | +60% | Vault | `MiningSpeedModifiers` bonus sum (§11) |
| Resonance gain | +5% | 4 | +20% | Vault | `ResonanceBonuses` bonus sum (§11) |
| Vanilla XP from ore | +10% | 3 | +30% | Vault | Added to the ore's dropped experience in the Bonus stage (§11); the vanilla amount is never reduced (§3.4) |
| Move speed | +5% | 6 to 8 | +40% at 8 | Everywhere | `minecraft:movement_speed`, add multiplied base |
| Block reach | +1 block | 3 | +3 | Vault | `minecraft:block_interaction_range`, add |
| Max health | +2 (one heart) | 8 | +16 | Everywhere | `minecraft:max_health`, add |
| Tool care | +5% chance a point of durability a hand-broken block costs the held tool is skipped | 6 | 30% from Tool care; the summed care chance tops out at 80% (rule below) | Vault | Tool wear hook (§11, `docs/adr/0008-tool-wear-mixin.md`) |
| Tool charge | 50 FE a block into the tool breaking it; +5% Tool care instead when that tool holds no energy | 6 to 8 | 400 FE a block at 8 | Vault | `Capabilities.Energy.ITEM` on the breaking tool, `insert` |
| Safe fall | +2 blocks | 4 | +8 | Everywhere | `minecraft:safe_fall_distance`, add |
| Armour | +1 | 6 to 8 | +8 at 8 | Everywhere | `minecraft:armor`, add |
| Mason's Reach | +1 block reach | 2 | +2 | Outside the Vault | `minecraft:block_interaction_range`, add; removed on entering a Vault, so the Vault cap of +3 holds |
| Kit Care | +5% chance that a point of durability lost by a held item or worn armour, from any cause, is skipped | 4 | 20% | Outside the Vault | Tool wear hook, as Tool care |

The tree holds 58 to 64 travel nodes. The ranged counts take what the final layout needs; no stat goes past its cap, and if the layout needs more travel, UI/UX shortens paths instead.

Rules for all twelve:

- Attribute stats are transient modifiers, recomputed from the team's owned nodes on login, respawn, dimension change, team change, buy and refund. Max health is the exception: a transient modifier is not there when health loads at login, so health is clamped to 20 and the extra hearts are lost. It uses a saved (non-transient) modifier with a fixed id, replaced on the same triggers. Vault-only attributes are added on entering a Vault level and removed on leaving it; outside-only ones the other way round. Max health applies everywhere so that leaving the Vault never takes hearts away (Nick, 2026-10-26).
- Mining speed and Resonance gain are **not attributes**. They are contributors to the §11 accumulators, summed with the other percentage bonuses, so Bedrock Communion's halving and the pact penalties still apply after them.
- The per-block stats (vanilla XP, Tool care, Tool charge) act only on a hand break (§3.4). A block broken in an FTB Ultimine chain is a hand break, and each block of the chain counts on its own (§9).
- Tool care, the Charge fallback and Hammer and Paxel Affinity's +10% add into one chance, rolled once per point of durability after Unbreaking; they are not separate rolls. Tool care alone stops at 30% and the Charge fallback alone at 40%, so with all six Tool care and all eight Charge nodes on a tool with no energy storage the summed chance is 70%, and 80% on a hammer or paxel with the Affinity. That 80% is the most any combination reaches. Tempered rolls on its own after that: with Tempered III, 14% of the points that pass Unbreaking still cost durability.
- 400 FE a block at 7,200 blocks an hour is about 40 FE a tick, all of it into the one held tool, which cannot pass it on. It is a tool's running cost, never a power source for machines (§3.4). The per-block costs of Mining Gadgets and of Just Dire Things abilities are unmeasured; a playtest reads a tool's charge before and after ten minutes of mining.
- Left out on purpose: ore yield, vein size or density, flat Resonance per ore, orb pickup radius and hunger, because each undercuts an existing node or the curve's baseline; mob XP, because with mob farms it would cheapen refunds, the 30-level keystone refund included; and other mods' attributes (Ultimine vein size, Paraglider stamina, `ftbevolutioncompanion:*`), because a travel node on a path cannot be hidden when its mod is missing. Those go on leaf notables.

The Tome shows a running total per stat (§8).

#### Flavour notables

> Notables added on 2026-10-26: tool upgrades, mod hooks, and nodes that make work outside the Vault easier. All are pure upside except where a toggle is noted. A node that needs a mod or a mod's tag is a dead-end leaf marked `[REQUIRES: …]` and hidden without it. Ring is the level floor (§6). "Hand break" means a block broken by a real player (§3.4); an FTB Ultimine chain is a hand break per block (§9).

**Spelunker's Stride** `[NOTABLE]`

> Inside the Vault, step height +0.5, so you walk up full blocks.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `minecraft:step_height` +0.5 inside the Vault | 3 |

---

**Landscaper's Swing** `[NOTABLE]`

> Outside the Vault, +25% break speed on blocks in `mineable/axe`, `mineable/shovel` and `mineable/hoe`. Pickaxe blocks are left out, so it never becomes mining speed.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +25% break speed outside the Vault on axe, shovel and hoe blocks; `PlayerEvent.BreakSpeed` with a dimension check | 4 |

---

**Seam Courier** `[NOTABLE]`

> Link one container by sneak-using it with the Tome. Ore drops from blocks you break by hand in the Vault go straight into that container while its chunk is loaded and it has room. Anything else drops as normal.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Drops from hand-broken ore in the Vault (`#c:ores` sources, after the whole drop pipeline) go into the player's linked container (`courier_link` on `PlayerStats`, §11) if `level.isLoaded(pos)` and it has room; overflow, stone and gravel drop as normal | 6 |

> Replaces Magnetic Seam, which pulled drops into the inventory: Simple Magnets and the Sophisticated Backpacks magnet already do that in Nick's pack. What a magnet does not solve is the full inventory and the walk home. Hooked in the last stage of the one `BlockDropsEvent` listener (§11) through the block item-handler capability (its 26.1 name is checked in the decompiled sources; the transfer API changed). It checks `level.isLoaded(pos)` and never places a chunk ticket, so it cannot load chunks. A delivered drop never spawns an item entity, so it does not compete with a magnet, including the Sophisticated Backpacks magnet that puts items straight into a backpack (Nick, 2026-10-26). No yield change, nothing from automation.

---

**Kiln Touch** `[NOTABLE]` `[TRADEOFF]`

> While enabled, non-ore drops that smelt into a block come out smelted: cobblestone to stone, sand to glass, clay to terracotta. Ore is excluded; that is Smelter's Intuition.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Non-ore drops in the Vault with a smelting recipe that yields a block drop the result instead; Transform stage (§11) | 6 |

> A toggle, because sometimes you want cobblestone.

---

**Wayfarer's Lungs** `[NOTABLE]` `[REQUIRES: Paraglider]`

> +30% glider stamina efficiency.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `paraglider:stamina_efficiency` +30%, by attribute id | 6 |

---

**Vault-Forged** `[NOTABLE]`

> Combine a tool with 1 Resonance Crystal in an anvil for +50% maximum durability, once per tool.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Anvil: tool + 1 Resonance Crystal = +50% max durability, once per tool, recorded on the item | 8 |

> `AnvilUpdateEvent` and `DataComponents.MAX_DAMAGE`. The upgrade is stored as `forged` in the item's `orevault:vault_tool` component (Ascending Edge, below) and re-applied to the new base item after an Ascending Edge swap, which removes the old item's `MAX_DAMAGE`.

---

**Resonant Dynamo** `[NOTABLE]` `[REQUIRES: an item with energy storage]`

> Each ore you mine by hand in the Vault sends 400 FE, split evenly, into the energy items you carry: main inventory, armour and offhand, when they match `orevault:dynamo_targets`.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | 400 FE per hand-mined Vault ore, split evenly across carried items in `orevault:dynamo_targets` that have energy storage | 8 |

> Default tag: `#c:tools`, armour and optional `justdirethings:pocket_generator`. Energy goes through `Capabilities.Energy.ITEM`. Only the main inventory, armour and offhand are read; accessory slots added by other mods are not, since reading them needs that mod's API (`CLAUDE.md` rule 5). Hidden when no registered item has energy storage, checked once at server start. 219 ore an hour is about 87,600 FE an hour, about 1 FE a tick: it keeps a jetpack and a pocket generator topped up and is never a power source (§3.4).

---

**Hammer and Paxel Affinity** `[NOTABLE]` `[REQUIRES: a filled hammer or paxel tag]`

> +25% mining speed and +10% to the care chance while holding a hammer, paxel or mining tool.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +25% mining speed (`MiningSpeedModifiers`) and +10% added to the summed care chance (§6.1 Travel stats), on hand breaks in the Vault, while the held item is in `c:ore_hammers`, `c:tools/paxel`, `c:tools/mining_tool` or `cucumber:paxels` | 8 |

> Tags only, no mod named. Hidden when all four tags are empty.

---

**Harvester's Lot** `[NOTABLE]`

> Mature crops you break by hand outside the Vault have a 25% chance to drop one extra crop item.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | 25% chance of +1 crop item from mature crops in `#orevault:harvest_bonus` broken by hand outside the Vault | 8 |

> Default tag: `#minecraft:crops`. Mystical Agriculture crops are left out by default, since their essence is that mod's progression; a pack adds crops by tag. Hooked in the one `BlockDropsEvent` listener, in an outside-the-Vault stage.

---

**Tempered** `[NOTABLE]`

> Each tool counts the ores it mines in the Vault, shown in its tooltip. At 500, 2,000 and 5,000 it gains Tempered I, II and III: 10%, 20% and 30% fewer durability hits.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Per-tool count of hand-mined Vault ores; at 500 / 2,000 / 5,000, each point of durability a hand-broken Vault block costs is skipped with chance 10% / 20% / 30%, rolled separately from the summed care chance | 10 |

> About 2, 9 and 23 hours of solo mining. The count is `lifetime_ores` in the `orevault:vault_tool` item component it shares with Ascending Edge (below), so an ascended tool keeps its Tempered level. The roll is in the tool wear hook (§11).

---

**Bookseam** `[NOTABLE]`

> 1 in 2,500 ores mined by hand in the Vault drops an enchanted book with one random enchantment that can go on a pickaxe, at a random level from 1 to its maximum.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | 1 in 2,500 hand-mined Vault ores drops a book with one random enchantment from `#minecraft:enchantable/mining`, level 1 to its maximum; Bonus stage (§11) | 10 |

> About one book every 11 hours solo. Apothic Enchanting's mining enchantments join through the tag when they are in it.

---

**Steady Chain** `[NOTABLE]` `[REQUIRES: FTB Ultimine]`

> FTB Ultimine's cooldown and its hunger cost are halved.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `ftbultimine:cooldown_modifier` −50% and `ftbultimine:exhaustion_modifier` −50% | 10 |

> On the Ultimine spur with Ultimine Expansion, Ultimine Safety and the Gambit.

---

**Delver's Lungs** `[NOTABLE]`

> Everywhere: air lasts four times as long underwater, and you move through water as with Depth Strider I.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `minecraft:oxygen_bonus` +3 and `minecraft:water_movement_efficiency` +0.33, everywhere | 10 |

---

**Vault Smith** `[NOTABLE]`

> Anvil work on a Vault-Forged tool never raises its repair cost, so it never becomes "Too Expensive".

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `AnvilUpdateEvent`: no repair-cost increase on a tool carrying the Vault-Forged marker | 12 |

> A leaf whose only neighbour is Vault-Forged.

---

**Ore Imprint** `[NOTABLE]`

> Combine a tool with one metal storage block in an anvil to imprint that metal on it. When the tool mines that metal's ore in the Vault: +1 Fortune and +25% mining speed.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Anvil: tool + one `c:storage_blocks/<metal>` block imprints `<metal>`; one imprint per tool, stored as `imprint` in the `orevault:vault_tool` component; a new one replaces it. On `c:ores/<metal>` in the Vault: +1 Fortune (Fortune stage, §11) and +25% mining speed | 12 |

> Per tool, with no limit on tools and no slot in the tree, so it is not the rejected Attunement slots (`DESIGN_LOG.md`).

---

**Gem Seam** `[NOTABLE]` `[REQUIRES: an Apotheosis gem loot table]`

> Ore you mine by hand in the Vault can drop Apotheosis gems: rarer ore drops them more often and more of them, and Apotheosis picks the gem and its purity from your world tier.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | On a hit, runs `orevault:gameplay/gem_seam/<rarity>` with the player as looting entity and the luck below; chance per ore by its §4.2 rarity | 12 |

| Ore rarity | Chance per ore | Gems per hit | Loot luck |
|---|---|---|---|
| Common | 1 in 2,000 | 1 | +0 |
| Uncommon | 1 in 600 | 1 | +1 |
| Rare | 1 in 150 | 2 | +2 |

> About 0.37 gems an hour on the stone band's 68/25/7 mix, 37 in a solo run; 0.47 an hour on deepslate's 60/30/10. The three loot tables ship as conditional data (`neoforge:conditions`, mod loaded `apotheosis`) holding Apotheosis's own random-gem entry, so Apotheosis chooses the gem and purity by world tier, as for its mob drops. Ore Vault names no gem, purity or tier: no compile dependency, no class reference, no reflection. If Apotheosis has a world-tier loot condition, per-tier frequency goes in the same JSON as extra pools. Hidden when none of the three tables has a pool after datapacks load. Gems skip the Fortune stage and every yield multiplier; one roll per ore. To verify in the Apotheosis 9.1.0 jar before building: the gem entry's type id, whether it reads loot luck for purity, and whether a tier condition exists. Playtest: compare 37 a run with an hour of Apotheosis mob kills at the same tier; if the Vault out-earns the mob path, halve the common chance.

---

**Unyielding Edge** `[NOTABLE]`

> In the Vault, a tool at 1 durability stops taking damage instead of breaking, and mines at half speed until repaired.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | In the Vault, durability lost to a hand-broken block never takes the held tool below 1; at 1 durability its mining speed is ×0.5, a penalty applied last (§11) | 14 |

> The clamp is in the tool wear hook (§11), after every other roll. FTB Ultimine's `PREVENT_TOOL_BREAK` setting stops chains near the end of durability before the clamp matters; a gametest covers the two together.

---

**Resonant Trait** `[NOTABLE]` `[REQUIRES: Hephaestus Tools]`

> Ships a "resonant" trait for Hephaestus tools: +15% mining speed in the Vault per trait level, up to level 2.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | A Hephaestus trait shipped as conditional data (`neoforge:mod_loaded`): +15% Vault mining speed per level, to 2 | 14 |

> Hephaestus traits are JSON loaded with `neoforge:conditions` support, so this is datapack-only. The trait schema must be read before the ticket opens.

---

**Blast-Wise** `[NOTABLE]`

> Everywhere: 30% less explosion damage and half the explosion knockback.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Explosion damage −30% (`LivingIncomingDamageEvent` on `#minecraft:is_explosion`) and `minecraft:explosion_knockback_resistance` +0.5, everywhere | 14 |

---

**Budding Seam** `[NOTABLE]` `[REQUIRES: GeOre]`

> 1 in 500 metal ores you mine by hand in the Vault leaves GeOre's budding block for that metal in place of air.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | 1 in 500 hand-mined metal ores is replaced by the block in `orevault:budding/<metal>` instead of air | 15 |

> The block tags ship with optional GeOre entries. A player's break changes the block, not world generation, so generated chunks are still never changed by a purchase (§3.1).

---

**Ascending Edge** `[NOTABLE]`

> A pickaxe that mines enough ore in the Vault turns into the next pickaxe up the ladder, keeping its enchantments, its name and everything else stored on it. The ladder runs through every pickaxe in the pack, modded ones included, and each step needs more ore than the last.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | When a tool's ores since its last step reach the next rung's count, it becomes the next rung's item | 18 |

> **The ladder** (Nick, 2026-10-26: "make the upgrade tree really long as it moves through pretty much all pickaxe types"). Built on the server from each candidate item's default stack when tags load, at startup and on `/reload`, and sent to clients for tooltips; clients never compute it.
>
> - Candidates: `#minecraft:pickaxes` plus `#orevault:ascension/include`, minus `#orevault:ascension/exclude` (by default optional `#c:ore_hammers`). Paxels form a separate ladder keyed by `#orevault:ascension/paxels` (by default optional `#c:tools/paxel` and `#cucumber:paxels`); a pickaxe never turns into a paxel or back. Mystical Agriculture's essence pickaxes are in `#minecraft:pickaxes` and join with no extra work.
> - Excluded: items whose stats live on the stack. That is any item whose default stack has an energy handler (Just Dire Things, Mining Gadgets, Ender IO, Oritech), any item whose speed is computed per stack, and Hephaestus tools, found by namespace `hephaestus_tools` or the `hephaestus_tools:construction` component looked up by id. A swap would lose their charge, upgrades or parts (Nick: Hephaestus tools are not upgraded). There is no toggle.
> - Stats, read from the default stack: durability `getMaxDamage()` (an unbreakable item counts as infinite), speed `getDestroySpeed` against stone, never the `Tool` component's default speed, which is 1.0 for every pickaxe; harvest level as the set of probe blocks in `orevault:ascension/harvest_probes` the item can harvest.
> - Item B is above item A when B's durability and speed are both at least A's, B harvests everything A does, and durability or speed grows by at least the minimum step, 5% (`ASCENSION_MIN_STEP = 0.05`, a constant): the smallest change a player notices, it keeps near-duplicates such as 2,031 against 2,032 durability from becoming rungs, and every step Nick named (2,000 to 2,500 durability, +25%) passes. A's next rung is the B above it with the lowest durability, then the lowest speed, then the same namespace as A, then `minecraft`, then id order, so the ladder climbs through every durability step and favours durability over speed. Equal items are siblings, never rungs, so the ladder cannot loop.
> - Gold is off the ladder: its 32 durability is below wood's, so nothing climbs into it, and a gold pick climbs only if a modded pick has at least its speed. Its tooltip says so.
> - A NeoForge data map on items, `orevault:tool_ascension` (`{ "next": <item>, "ores": <int, optional> }`), overrides a computed step. An override that makes a loop is dropped and logged.
>
> **Ores per rung** depend on the rung being reached: `N = durability × sqrt(speed / 4)`, rounded to the nearest ore, where 4 is stone's speed. Durability enters linearly and speed under a square root, so durability counts for more. A rung with infinite durability takes its count from the data map; with no entry, `N` uses twice the highest finite durability among the candidates, so an unbreakable pick is always the dearest rung on its ladder, because it removes durability as a cost for good. Vanilla figures, to be checked against the decompiled 26.1 `ToolMaterial`:
>
> | Rung | Durability / speed | Ores to reach | Hours at 219 ore an hour |
> |---|---|---|---|
> | Wood | 59 / 2 | start only | |
> | Stone | 131 / 4 | 131 | 0.6 |
> | Copper | 190 / 5 | 212 | 1.0 |
> | Iron | 250 / 6 | 306 | 1.4 |
> | Diamond | 1,561 / 8 | 2,208 | 10.1 |
> | Netherite | 2,031 / 9 | 3,047 | 13.9 |
>
> Wood to netherite is 5,904 ores, 27 hours of mining; iron to netherite, what a team holds at level 18, is 24 hours. A modded 2,500-durability pick at speed 9 or 10 costs 3,750 or 3,953 ores. Each member's tool climbs separately.
>
> **Known risk (Balancer): ores per rung do not scale with the size of the step.** `N` depends only on the rung being reached, so a 6% step from a 2,031-durability pick to a 2,150 one costs about 3,200 ores, more than the whole climb from diamond to netherite. A pack with many pickaxes a few percent apart above diamond gives a long ladder of dear rungs, and the climb past diamond can take several times the 24 hours above. The ladder is computed once per tag reload and written to the server log at start-up, rung, stats and ores to reach; the first modded playtest checks it against the pack and, if the top of the ladder is too slow, the fix is a data map override or making `N` scale with the step, a Balancer change.
>
> **Counting.** The `orevault:vault_tool` data component is saved with the item and synced to clients for tooltips: `data_version`, `lifetime_ores`, `ores_since_rung` (default 0), `forged` (Vault-Forged applied, default false), `imprint` (Ore Imprint's metal, default none) and `echoes` (Echoing Edge's echo count per enchantment, default empty). Its keys are fixed and every rename or removal gets a codec migration (`CLAUDE.md` rule 6, §11 SavedData Pattern). It counts a classified ore broken by hand in a Vault, in the final stage of the §11 drop listener, on `player.getMainHandItem()`, never on the event's tool, which is a copy taken before damage. Each ore of an FTB Ultimine chain counts on its own (§9).
>
> **The swap** is queued and applied in `PlayerTickEvent.Post`, only if the main hand still holds the same stack, so a chain finishes on one tool. The new stack copies the old stack's component patch (enchantments, including any raised past the maximum by Echoing Edge, name, lore, repair cost, trims, other mods' components), removes the old item's patched `MAX_DAMAGE`, `TOOL` and `ATTRIBUTE_MODIFIERS`, re-applies Vault-Forged if it was there, and carries damage over as the same fraction of maximum durability. `ores_since_rung` resets to 0 and `lifetime_ores` is kept. The old item's own behaviour is lost (for example Mystical Agriculture's augment slots); that is why per-stack tools are excluded.
>
> Ring 18 stays: before then a team crafts its own picks in minutes, so the node's value is diamond to netherite and the modded rungs above. Ascending Edge and Echoing Edge are not exclusive; both are paced so neither throws a team far ahead (Nick, 2026-10-26).

---

#### Keystones

> Each one changes how you mine and each one costs you something. They sit at rings 18 to 26, at the far ends of the tree. A keystone costs 30 XP levels to refund (§4.4), so its downside cannot be dodged by refunding it for one trip. The first five are carried over from the tiered tree; the last four are new (2026-10-26).

**Vault Expansion** `[KEYSTONE]`

> Bedrock moves from Y=0 down to Y=−64, opening a 63-block deepslate band that carries the highest ore density in the mod. Takes effect only on a dimension reset, which regenerates the Vault and everything built in it.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Vault re-created under `ore_vault_expanded` on next reset; deepslate band below Y=0 unlocked | 18 |

---

**Full Spectrum** `[KEYSTONE]`

> The two Ore Focus options you did not choose apply at 50% strength alongside the one you did, and every ore pays −20% Resonance.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Unchosen Ore Focus options apply at 50% effect alongside the chosen one; **−20% Resonance from all ore** | 22 |

---

**Silent Stone** `[KEYSTONE]`

> Ore pays no Resonance at all. Instead each active team member earns 0.30 Resonance a second, raised by the team's Resonance bonuses that apply to that member, and the pool takes the members' sum through the same §4.2 team scaling as ore. Active means inside the Vault, below Y=0, and having broken a block by hand in the last 10 seconds.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Ore awards no Resonance. Each active member (inside the Vault, below Y=0, broke a block in the last 10 seconds) earns 0.30 Resonance a second × (1 + that member's counted Resonance bonuses); the pool gains the sum, divided by team size, × (1 + 0.1 × (team size − 1)) | 24 |

> Divorces progression from extraction. You can chase materials as hard as you like without slowing the tree, or delve slowly and still advance. Ore-multiplying nodes stop contributing to progression, which is a large sacrifice for a team invested in the yield nodes, and it makes Greedy Seams a pure upside rather than a pact — a combination that is allowed and is meant to be found. The Resonance nodes the team has bought keep most of their value, because the state-based ones raise the rate (Nick, 2026-10-26).
>
> **The rate.** Evaluated once a second on the server:
>
> ```
> memberRate    = 0.30 × (1 + sum of that member's counted bonuses), floored at 0     // each active member
> teamPoolGain  = sum(memberRate) / teamSize × (1 + 0.1 × (teamSize − 1))            // per second
> ```
>
> With no bonuses this is 0.30 a second for a solo player and 0.30 × 5 / 5 × 1.4 = 0.42 for five members all active; four active out of five is 0.336. A member who is not active contributes nothing but still counts in `teamSize`, exactly as a member who is not mining ore does (§4.2). Tithe's 1.75× does not apply: it is a payment for a consumed ore block. The 0.30 is a constant, not config (Nick, 2026-10-26).
>
> **What "ore pays no Resonance" covers.** Every payment triggered by breaking ore: the per-break Resonance, Deep Harvest's extra orb, Last Ore, Clean Cut, and the Vault Echo and Chapter's End bursts, which are paid for finishing ore veins. Stone Memory's payments for stone and deepslate are not ore and still pay.
>
> **Which bonuses count.** A bonus counts when it can be evaluated for a member at any second without reference to a particular ore break or vein event. Position-based bonuses are evaluated at the member's own position.
>
> | §11 bonus | Counts | Evaluated as |
> |---|---|---|
> | Shallow Grace (+50%) | Would count, never applies | An active member is below Y=0, so the deepest-Y condition has already failed |
> | Apprentice's Ledger | No | Tied to a trip's first 100 ore breaks |
> | Deep Habit (+5% per 1,000 blocks, to +25%) | Yes | The member's blocks broken this trip |
> | Calloused Hands (to +40%) | Yes | The member's lifetime blocks broken |
> | Kindred Rock (+15%) | No | Tied to the type of the ore broken |
> | Vein Discipline (+2% per vein, to +30%) | Yes | The member's current streak |
> | Molten Seam (+25%) | No | Tied to the ore's distance from lava, and pools are only in the stone band |
> | Frontier Bonus (+40%) | Yes | The chunk the member stands in was generated within the last 10 minutes |
> | Vault's Purity (+20%) | Yes | The member has no potion effect |
> | Last Ore, Clean Cut | No | Vein events |
> | Resonant Overload (+100%) | Yes | Unconditional |
> | Resonant Symbiosis (+50%) | Yes | Two or more team members inside the Vault |
> | Bedrock Communion (+2% per block below Y=0) | Yes | The member's Y; an active member is always below Y=0 |
> | Volatile Veins: Ultimine Gambit | No | Tied to a surviving Ultimine operation |
> | Travel Resonance gain (+5% a node, to +20%) | Yes | Unconditional, like Resonant Overload |
>
> **No penalty applies to the rate**, including Resonant Symbiosis's −40% alone. The penalties in §11 are priced against ore Resonance, and Silent Stone has already taken all of it; charging them again would double the cost, and it would undo the stated intent that Silent Stone makes Greedy Seams a pure upside. Greedy Seams, Vault Fever and Full Spectrum therefore cost a Silent Stone team nothing in progression; what they still trade is materials and speed, which is the side of the game this keystone frees. Resonant Overload stays a choice against Greedy Seams: +100% rate for half the ore. With no penalty the floor at zero (§11, Nick's N6) can never be reached; it is stated so the code applies it anyway.
>
> **Derived, unplaytested.** Curve figures are the §4.3 solo derivation in the stone band, about 219 ore an hour (218.9) at 3.485 Resonance, 0.212 a second; a team of five all mining earns 0.297. Levels 25 to 30 cost about 47,400 Resonance, 62 hours at 0.212. "Ore, same nodes" counts the same bonuses plus the ore-only ones a build has by then (Kindred Rock +15%, Last Ore about +13% spread over a vein; in the maximum case Clean Cut +50% on every vein), and leaves out the bursts. The typical level-24 build has Deep Habit +25%, Calloused Hands tier 2 at about 200,000 blocks +15%, Vein Discipline +20%, Vault's Purity +20% and Frontier Bonus +20% averaged over half the time, so +100%, and +150% for five with Resonant Symbiosis; no Resonant Overload and no Bedrock Communion, which needs level 26. The maximum adds Resonant Overload, Bedrock Communion at Y=−63 and every counted bonus at its cap: +381% solo, +431% for five. These figures leave out travel Resonance gain, which counts (above): it adds the same points to both columns, +10% for a typical level-24 team holding half of it and +20% at most, so the typical solo rate becomes 0.63 a second against 0.50 for ore and every ratio in the table moves by under 0.05.
>
> | Build | Silent Stone, solo | Ore, same nodes, solo | Silent Stone, five active | Ore, same nodes, five | Levels 25–30 (solo SS / solo ore) |
> |---|---|---|---|---|---|
> | No bonuses | 0.30 /s | 0.212 /s (1.42×) | 0.42 /s | 0.297 /s (1.42×) | 44 h / 62 h |
> | Typical level 24 | 0.60 /s | 0.48 /s (1.24×) | 1.05 /s | 0.82 /s (1.27×) | 22 h / 27 h |
> | Maximum counted | 1.44 /s | 1.18 /s (1.22×) | 2.23 /s | 1.81 /s (1.23×) | 9 h / 11 h |
>
> **No cap.** Silent Stone takes 1.4 times the base and a subset of the bonuses an ore build takes, so it cannot pass about 1.4 times an ore build with the same nodes and no penalties, and the more ore-only bonuses a build owns, the closer it falls to 1.0. The maximum is 1.2 times. Against an ore build that holds Greedy Seams, Vault Fever and Full Spectrum (×0.3 together) it is about 4.7 times, which is the pure upside described above, not a stacking accident.
>
> **Against the deepslate band.** An active member is in deepslate, which is twice as dense and pays 3.95 a block at 60/30/10 (§3.1). If 7,200 blocks an hour held there, bare ore would pay 0.47 a second and 0.30 would be about 0.63 of it. Deepslate is twice as hard as stone, so the real figure is lower; T14's timed session below Y=0 settles it. The trade is a constant rate for a slow or distracted delve against a higher one for a fast miner.
>
> **AFK guard:** a member accrues only while that member has broken a block in the last 10 seconds. Blocks broken by a machine do not count (§3.4).
>
> The 3 a second first written here was about 14 times the solo rate and would have turned levels 25 to 30, 62 hours on the curve, into about four.

---

**Resonant Symbiosis** `[KEYSTONE]`

> While 2 or more team members are inside the Vault at once, each of them gains +50% Resonance and +25% ore. Alone inside the Vault, −40% Resonance.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | While two or more team members are inside the Vault at once, each of them gains +50% Resonance and +25% ore. Alone inside the Vault, −40% Resonance | 20 |

> This is a team mod whose tree otherwise has nothing to say about teams. It makes co-op a build rather than a convenience, and the solo penalty is steep enough to be a genuine choice rather than a free bonus for groups. Solo players are a team of one (§2), so for them it is a trap they can see coming — which is exactly what a keystone is.
>
> Interacts with §4.2 team scaling, which already divides the pool by team size: Symbiosis pays per member *after* that division, so a pair with it beats a pair without it but does not beat two solo players with separate Vaults on throughput alone.

---

**Bedrock Communion** `[KEYSTONE]`

> +2% Resonance and ore per block below Y=0, reaching +126% at Y=−63. Between Y=32 and Y=245 ore pays no Resonance at all and mining speed is halved. At Y=246 and above there is no penalty, so surface builds and machine floors are untouched.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +2% Resonance and ore per block below Y=0, reaching +126% at expanded bedrock. Between Y=32 and Y=245: no Resonance from ore, and mining speed halved. At Y=246 and above: no penalty | 26 |

> **The Y=246 exemption is the whole reason this node is playable.** Y=246 is the base of the dirt band (§3.1: dirt Y=246–249, grass Y=250, open air Y=251–319), so every surface build, machine room and automation floor sits above the line and is untouched. Without it the keystone would tax the one part of the Vault players build in.
>
> The penalty is a mining-speed reduction, not damage. Damage over time in a dimension you spend hours in is an irritation rather than a cost, and it would stack lethally with lava from Molten Seam.
>
> +126% is the ceiling and it is a fixed one: the expanded Vault floors at Y=−63 with bedrock at −64, so there is no depth left to scale into. Stating the cap is better than leaving the formula open-ended and discovering it in a spreadsheet.

---

**Long Arm** `[KEYSTONE]`

> Block reach +3 more in the Vault, +6 with all travel reach. In exchange mining speed is ×0.75.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | `minecraft:block_interaction_range` +3 in the Vault; mining speed ×0.75, a penalty applied last with Bedrock Communion's halving (§11) | 18 |

> Strip a whole face without stepping, but slower per block. This is the reach cap's own trade-off, offered rather than forbidden.

---

**Glass Edge** `[KEYSTONE]`

> +50% mining speed. In exchange every block you break in the Vault costs 3 durability.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | +50% mining speed (`MiningSpeedModifiers` bonus sum); in the Vault, the durability a hand-broken block costs the held tool is multiplied by 3 before Unbreaking, which then rolls per point; other durability loss (combat, a flint and steel) is unchanged | 20 |

> It turns the durability nodes from comfort into a build.
>
> Known interaction (unplaytested): with an Efficiency VII netherite pick from Echoing Edge, Glass Edge brings stone-band ore to speed 88.5 against the 90 that breaks it in one tick, so one more +10% makes ore instant. Allowed; see Echoing Edge.

---

**Galvanic Seam** `[KEYSTONE]` `[REQUIRES: an item with energy storage]`

> Tool charge nodes give triple, 150 FE a node, and Resonant Dynamo is doubled. In exchange tools with no energy storage take 2 durability per block in the Vault.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Tool charge ×3 (150 FE a block per node); Resonant Dynamo ×2 (800 FE an ore); in the Vault, a tool with no energy storage has the durability a hand-broken block costs it multiplied by 2 before Unbreaking | 20 |

> A tech team mines with energy tools only. Hidden when no registered item has energy storage, checked once at server start.

---

**Echoing Edge** `[KEYSTONE]`

> Each ore you mine by hand in the Vault can echo: at 1 in 1,000 at first, and rarer as an item fills, an enchantment on your tool or other gear rises one level, up to 2 past its maximum, or the item gains a new enchantment at level I. Half the echoes go to the tool in your hand, half to your armour, offhand or first hotbar weapon. Echoed items can't be used in an anvil, grindstone or smithing table.

| Tier (nodes owned) | Effect | Ring |
|---|---|---|
| 1 (single node) | Each hand-mined Vault ore rolls once: half the rolls target the held tool, half one other eligible item at random. Chance on the target `1 / (1,000 × (1 + 3f))`, `f` the share of its headroom already used. An echo raises an enchantment by one level, up to `getMaxLevel() + 2`, or 1 echo in 4 adds a compatible enchantment at level I. An echoed item is locked out of the anvil, grindstone and smithing table | 22 |

> **Decided (Nick, 2026-10-26):** the roll is per ore, and every ore of an FTB Ultimine chain rolls on its own, since a chain is not one mining instance (§9); each echo is announced to the team in chat with a sound; the cap follows Apotheosis's higher maxima, and levels above the maximum work as vanilla computes them (Efficiency VII on netherite mines stone instantly); an echo may add an enchantment and may reach tools, weapons and armour, not only the held pickaxe; and Echoing Edge and Ascending Edge are **not exclusive**: owning one does not pause the other, and both take long enough not to throw a team too far forward. The rule below is the Balancer's round 3; Nick suggested the headroom slowdown as a "maybe", and the rule implements it.
>
> **The roll.** Each ore a player breaks by hand in a Vault (§3.4) rolls once, in the Deliver stage of the §11 listener, on the live stacks, never the event's tool copy. An Ultimine chain of 7 ores is 7 rolls. No roll for a machine break, a fake player or a block outside `#c:ores`. No Resonance is paid.
>
> **Eligible items**, the player's own at the moment of the break: the main-hand item that broke the ore (the tool); the four worn armour pieces and the offhand item; and one hotbar item, the leftmost one other than the tool in `#orevault:echo_hotbar` (default `#minecraft:enchantable/weapon`, `/bow`, `/crossbow`, `/trident`, `/mace`). Not the whole hotbar or inventory: a stack of spare picks would not raise the rate, which is fixed, but would spread echoes onto items nobody uses. An item is eligible only while its headroom `R` (below) is above 0.
>
> **Target first, then the roll.** Half of all rolls go to the tool. The other half pick one of the other eligible items at random, equal odds. If the tool is full, or there is no other eligible item, that half is lost, not passed on. The total echo rate therefore never rises: taking armour off does not speed the pick, and a full pick does not speed the armour.
>
> **The chance falls with the share of headroom used.** For the chosen item:
>
> ```
> cap(e) = min(255, e.value().getMaxLevel() + 2)       // read at runtime, never a vanilla constant
> R      = sum over its enchantments of (cap − level)  // skipping curses, max-level-1 enchantments and #orevault:echoing_edge_excluded
>          + 1 for each enchantment that could be added (below)
> n      = echoes this item already has                // sum of orevault:vault_tool echoes
> f      = n / (n + R)
> chance = 1 / (1,000 × (1 + 3f))
> ```
>
> The chance starts at 1 in 1,000 on every item and never falls below 1 in 4,000. Each echo slows the next by 3,000 / (n + R) ores, so an item that can hold more enchantment levels takes less per echo, as Nick asked. Expected rolls on one item to reach `k` echoes: `1,000 × (k + 1.5 k (k − 1) / (n + R))`.
>
> **What an echo does.** If the item can take a new enchantment, 1 echo in 4 adds one; otherwise, and on the other 3 in 4, it raises a random enchantment below its cap by one level, equal odds per entry. A new enchantment must pass `Enchantment.isSupportedItem(stack)` and `Enchantment.areCompatible` with every enchantment already on the item, is not a curse or excluded, and is drawn by registry weight as the enchanting table draws. It is added **at level I**; later echoes can raise it, which also raises `R`, so the item slows less. Treasure enchantments (Mending, Soul Speed) can be added: it is one level, and Nick likes being overpowered. Enchantments whose maximum is 1 (Silk Touch, Mending, Infinity, Channeling) can be added but are never raised. With nothing to raise or add, the item is full and its tooltip says so.
>
> **Apothic Enchanting.** The cap reads `getMaxLevel()` at runtime with no `isLoaded` branch, so Apothic's raised maxima give "Apothic's maximum + 2". To verify in the Apothic Enchanting 2.1.0 jar before building: whether its raised maxima are the enchantment's `max_level` (a data override or a mixin on `getMaxLevel`). If they live in its own config instead, `getMaxLevel` returns vanilla's value and the cap is short; the fallback is an Ore Vault data map `orevault:echo_max_level` (enchantment to level), shipped as conditional data holding Apothic's values, and read in place of `getMaxLevel` where it has an entry.
>
> **Downside: an echoed item cannot be used in an anvil, grindstone or smithing table** (Nick, 2026-10-26, round 2, extended in round 3 to the grindstone and smithing table). No new enchantments by book, no anvil repair, no netherite upgrade, no socketing at a smithing table. It bites because the target is random: an echo can land on diamond armour before its netherite upgrade, or on gear that still needed an Apotheosis socket, and that item is then finished as it stands. A team completes its kit before level 22 or accepts what the echoes freeze. Mending, Highwater Mark, Tool care and Unyielding Edge keep an echoed item alive. Rejected as the downside: extra durability per block (Highwater Mark and Mending make it free), a Resonance tax (the curve stays derived from ore only), an XP cost per echo (free once a mob farm runs), and pausing Ascending Edge (Nick).
>
> **With Ascending Edge.** Both roll per ore and neither pays Resonance. An ascending pick keeps its component patch (above), so its `orevault:vault_tool` echo counts and its raised levels climb with it; the new item does not change any cap, which is per enchantment, not per material.
>
> **Derived, unplaytested.** Echoes depend on ores mined, not on ore rate, since the roll is per ore. Hours are at 219 / 500 / 1,000 ore an hour. Typical level-22 pick: Efficiency V, Unbreaking III, Fortune III, Mending, headroom 6.
>
> | On one item | Ores | Hours at 219 / 500 / 1,000 |
> |---|---|---|
> | First echo anywhere in the kit | 1,000 | 4.6 / 2 / 1 |
> | Pick, 1st echo (half the rolls) | 2,000 | 9 / 4 / 2 |
> | Pick, 3rd | 9,000 | 41 / 18 / 9 |
> | Pick, 6th: full at Efficiency VII, Unbreaking V, Fortune V | 27,000 | 123 / 54 / 27 |
> | One armour piece, 1st echo (a tenth of the rolls, with five other items) | 10,000 | 46 / 20 / 10 |
>
> From level 22 to 30 a solo player mines about 16,000 ores and expects about 4 echoes on the pick and 1 on each armour piece and the sword, 11 in all; a member of a team of five about 3 on the pick and 8 in all, 42 for the team. A typical solo level-30 pick is about Efficiency VI, Fortune IV, Unbreaking IV. Neither keystone slingshots: with Ascending Edge the two together give about one step, a rung or an echo, every 8 to 10 hours of mining after level 22.
>
> **Known interaction: Glass Edge and Efficiency VII make ore instant.** A netherite pick at Efficiency VII mines at speed 59. Stone-band ore and deepslate stone need 90 to break in one tick. Glass Edge's +50% gives 88.5, so any one more +10% (a travel node, Long Delve, Ore Imprint) makes them instant; six travel mining-speed nodes alone give 94 without Glass Edge, and Apothic's higher Efficiency caps make it easier. Recommendation: allow it. It needs two echoes on Efficiency, which a typical solo pick does not have by level 30 (above), Nick likes being overpowered, and echoes are per ore, so a faster miner does not echo more per level. It does raise ore an hour above the curve's 219, which shortens the late levels like any speed node (§4.3). The late-game playtest measures ore an hour with an Efficiency VII pick and Glass Edge, then the hour of the first echo.
>
> **Pure functions with unit tests**, kept out of the event handler: `echoChance(n, r)` pinning 1 in 1,000 at n 0, 1 in 3,500 at n 5 r 1, and never below 1 in 4,000; `chooseTarget(toolEligible, others, rnd)` pinning the half-and-half split and the lost half; `chooseEffect(raisable, addable, rnd)` pinning 1 in 4 new. Item tooltip: "Echoes: 3 of 9. Next: 1 in 2,000."

---

### 6.2 Animus (Mob) Tree

#### BRANCH: Disturbed Zone Enhancement

**Zone Frequency**

> +25% mob spawn frequency inside Disturbed Zones, then +50%, +80%, +120%.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +25% spawn frequency | 1 | 0 | None |
| 2 | +50% spawn frequency | 1 | 3 | Tier 1 |
| 3 | +80% spawn frequency | 2 | 6 | Tier 2 |
| 4 | +120% spawn frequency | 2 | 10 | Tier 3 |

---

**Zone Pack Size**

> +1 mob per spawn attempt in Disturbed Zones, then +2, then +3.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +1 mob per pack | 1 | 1 | None |
| 2 | +2 mobs per pack | 1 | 4 | Tier 1 |
| 3 | +3 mobs per pack | 2 | 8 | Tier 2 |

---

**Zone Radius**

> Each Disturbed Zone block spawns mobs within 32 blocks instead of the default 16, raised to 48, then 64.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | Radius: 32 blocks (up from default 16) | 1 | 2 | Zone Frequency T1 |
| 2 | Radius: 48 blocks | 2 | 6 | Tier 1 |
| 3 | Radius: 64 blocks | 3 | 11 | Tier 2 |

---

**Mob Diversity**

> Adds undead to Disturbed Zone spawns (zombies, skeletons, wither skeletons), then arthropods (spiders, cave spiders, silverfish), then illagers (vindicators, evokers, pillagers), then witches and endermen.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | Undead mobs (zombies, skeletons, wither skeletons) | 1 | 0 | None |
| 2 | Arthropod mobs (spiders, cave spiders, silverfish) | 1 | 3 | Tier 1 |
| 3 | Illager mobs (vindicators, evokers, pillagers) | 2 | 7 | Tier 2 |
| 4 | Rare mobs (witches, endermen) | 3 | 12 | Tier 3 |

---

#### BRANCH: Mob Rewards

**Reaper's Claim**

> +50% XP from mobs killed in Disturbed Zones, then +100%, then +150%.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +50% XP from zone mobs | 1 | 2 | Zone Frequency T1 |
| 2 | +100% XP from zone mobs | 2 | 5 | Tier 1 |
| 3 | +150% XP from zone mobs | 2 | 9 | Tier 2 |

---

**Corrupted Veins**

> 5% of mobs killed in Disturbed Zones drop a random common raw ore in place of their normal loot, rising to 10% including uncommon ores, then 15% including rare ores.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | 5% chance of random common raw ore drop | 1 | 3 | Zone Frequency T2 |
| 2 | 10% chance, includes uncommon ores | 2 | 7 | Tier 1 |
| 3 | 15% chance, includes rare ores | 3 | 12 | Tier 2 |

---

**Plunderer's Share**

> +25% item drop quantity from mobs killed in Disturbed Zones, then +50%, then +80%.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +25% item drop quantity | 1 | 2 | None |
| 2 | +50% item drop quantity | 1 | 5 | Tier 1 |
| 3 | +80% item drop quantity | 2 | 9 | Tier 2 |

---

**Animus Amplifier**

> +25% Animus per mob kill, then +50%, then +80%.

| Tier | Effect | Cost | Level Req (Animus) | Prereq |
|---|---|---|---|---|
| 1 | +25% Animus per kill | 1 | 1 | None |
| 2 | +50% Animus per kill | 1 | 4 | Tier 1 |
| 3 | +80% Animus per kill | 2 | 8 | Tier 2 |

---

**Soul Harvest** *(Animus Tree Keystone)*

> 5% of mobs killed drop a Soul Harvest item. It is the currency for a Vault Shop that does not exist yet, so this node buys nothing today.

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
- Opens a custom GUI with tabs at the top. 1.0 ships two: the Resonance Tree and a small Ore Memory tab. The Animus Tree tab arrives with the post-1.0 Animus epic
- Does not go in the offhand slot (marked as mainhand/inventory only)
- Right-click to open

### Tab 1: Resonance Tree

**Layout.** The Path of Exile passive tree: the start at the centre, nodes joined by straight edges, stronger nodes further out (§6). Everything is drawn from the authored layout file, positions included, and the renderer holds no placement code. Faint level rings, one per level, mark where each level opens ("Level 12 opens here"), so the level gate reads as geography rather than as a lock on every node. Wheels, petals and exclusive junctions are drawn as §6 describes them. Edges run border to border and never cross another node; a layout test checks it. The layout is approved by Nick on a rendered preview before renderer code is written (§6.1).

**Nodes are icons** (Nick, 2026-10-26). Every node, travel included, is an icon with no text on the page; its name and numbers are in a tooltip on hover. Icon sizes, in GUI units, are set by class:

| Class | Icon size |
|---|---|
| Travel | 14 |
| Small, growth, fork parent | 18 |
| Notable | 26 |
| Pact | 26, with keystone-style ornament |
| Keystone | 36 |
| Fork option (petal) | 12 |

Travel is a size step below small nodes, not only a plainer frame, because a frame-weight difference of a texel or two is gone at the smallest zoom (18 × 0.2 is 3.6 units), and paths must still read as paths. A pact differs from a keystone in size and ornament, not only in hue. The sizes are provisional until Nick's yes on the E8-S5 (#283) preview (Designer, Epic 0 round 1). Every copy of a skill uses the same icon, and each travel stat has its own. Icons are drawn at one texel per GUI unit (`CLAUDE.md` Textures). Retro Diffusion is not used for them unless it is absolutely necessary (Nick).

**The page is opaque and zooms.** The tree is drawn on its own solid page, not over the world, so thin edges stay readable. The mouse wheel scales it between a fifth and one and a half times full size, anchored on the pointer, with a readout of the zoom.

**Search.** A search box in the tree tab's strip, focused with Ctrl+F, matches node names and effect text and highlights the matching nodes.

**Hover.** Hovering a node highlights the cheapest path to it from what the team owns, the one with fewest unowned nodes, and every other copy of the same skill. The tooltip states the path's point cost and the highest ring on it.

**Click** on a node buys it. A petal is not a node (§6): left-click on a petal picks it, free and outside the Vault only (§4.4), and right-click on a picked petal unpicks it.

**Shift-click** buys the highlighted path, with one confirmation that names the number of points. The server checks each node of the path in order (§4.4); a path that crosses a ring above the team's level, or costs more than the unspent points, is refused before anything is bought.

**Shared pin.** Any member can pin one node for the team. Every member sees the pin, its path highlighted and "Pinned by <name>"; pinning another node moves it. The pin is saved with the team as `pinned_node` and `pinned_by` (§6, §11; Architect, 2026-10-26); a pin whose node is gone or hidden is dropped on load.

**Travel totals panel.** A panel lists every travel stat the tree shows, as "Mining speed +30% (3 of 6 owned)". A travel node's tooltip gives the effect and the team total: "+10% mining speed. Team total +30%, +40% with this".

**Tooltip.** Name; the description; class and marks; the effect at the tier the team owns, then a dimmer "Next:" line with the next tier's effect. For a skill: "Vein Expansion 2 of 5 owned: +30% vein size", then "Next: +50% vein size". A skill the team does not own shows only the "Next:" line; a skill at its last tier shows no "Next:" line. Both lines are filled from the effects constants (§11 Tree Layout Data), never typed into the lang string. The tooltip no longer lists every tier: this replaces the 2026-09-09 rule for the tooltip (Nick, 2026-10-27); the every-tier effect table stays in §6.1. A node that changes generation adds "Only in chunks generated after you buy it." A team-voted tradeoff shows the vote count, the threshold and any change waiting for the Vault to empty. For a fork parent: the chosen option, or that it does nothing until one is chosen. On a growth node the team does not own whose condition can no longer be met for the viewing player, "Outgrown: <why>" above the "Next:" line, and buying it asks one confirmation that names it, as a pact or keystone confirms its downside. Such a node carries the Outgrown badge on its icon, so it is seen without hovering, but is not greyed while unowned, since greying is the owned-and-outgrown treatment below: the badge means it will not pay for you, greying means a point was spent on it. Outgrown is the viewing player's state, not the team's. The server computes it for each viewer from that player's own `deepest_y`, which the client does not have, and sends it with the tree state (`docs/adr/0006-network-channel-and-tome-sync.md`). Shallow Grace counts only the members inside the Vault (§6.1), so one member's record never stops it paying the others, and two members can see the same node outgrown and not outgrown. The reason names the viewer: for Shallow Grace, "You have been below Y=100, so it pays nothing while you are inside." (`node.orevault.shallow_grace.outgrown`). The ring, and whether the team has reached it. When the node cannot be bought or refunded, the reason, naming the node, level or partner in the way (§4.4). Who last bought it. Tooltips wrap with `font.splitIgnoringLanguage`, within the width and line limits UI/UX set. **Every node has a description**; a node without one shows the player a raw translation key, which is a visible defect.

**Lang keys.** Every key is `<kind>.orevault.<name>`, with these kinds: `node` for node text, `tooltip` for shared tooltip parts, `refusal` for a refused Tome request (ADR 0006), `disconnect` for a disconnect reason, and `message` for a chat or action-bar line the server sends outside the Tome. A skill's name is `node.orevault.<skill id>` and its description `node.orevault.<skill id>.desc`; every copy of a skill shares them. A notable, keystone or travel stat uses its own id the same way.

Effect lines (settled 2026-10-31). Each tier has its own key, `node.orevault.<skill id>.effect.<tier>`, the tier from 1 to the number of copies in the layout; a single-node skill, a notable, a keystone and a travel stat use `.effect.1`. A key per tier, not a tier argument, because tiers of one skill can differ in wording and not only in a number (Stone Memory's tier 3 adds "per deepslate block", Nick's Q8), and a translator cannot branch on an argument. The arguments are numbers only, positional (`%1$s`), filled from that tier's effects constants (§11 Tree Layout Data) and formatted by one client formatter; units and signs stay in the lang string ("+%1$s%% vein size"), so a translation can move them. Shared keys wrap them: `tooltip.orevault.next` ("Next: %s") around the next tier's line, `tooltip.orevault.tier_owned` for the owned line's head ("2 of 5 owned: "), and `tooltip.orevault.travel_total` for a travel node's team total. A growth node's reason is `node.orevault.<skill id>.outgrown`. Every rendered effect line, at every tier, wraps to at most 2 lines at 240 GUI units, and search matches the text of every tier, owned or not.

**States.** Owned; buyable now; in reach, buyable but for a point; and three locked states, each drawn differently. A node in reach is one the team could buy now if it had an unspent point. It is drawn exactly as buyable, full colour and class frame with no badge and no dimming, but without the buyable highlight, a lit outline told apart by shape and brightness rather than hue. Its tooltip's reason line names the level that brings the next point ("No unspent points. Next point at level N."), and a click on it is still answered with the no-points refusal. A point shortage is team-wide and already in the header, so it is not drawn on every node: at level 0 on first login, and after most shopping trips, every node next to what the team owns is in this state, and drawn as locked the start would read as a wall. Final on Nick's yes at the E8-S5 (#283) preview. The three locked states:

| Locked state | When | Drawn as |
|---|---|---|
| Out of reach | No edge joins it to an owned node or the start | Dimmed, no badge |
| Level | It touches an owned node, but its ring is above the team's level | Lock badge with the opening level; only reachable nodes carry a badge |
| Excluded | Its exclusive partner is owned | Exclusive lock mark, naming the partner |

**Class is visible without hovering:**

| Class | Treatment |
|---|---|
| Small | Plain icon frame |
| Travel | Smaller, plainer frame, so paths read as paths |
| Notable | Larger icon, distinct frame |
| Keystone | Largest, ornamented, unmistakable |
| Pact | Keystone ornamentation in a different, colder frame, so it reads as dangerous without reading as a keystone |
| Growth | Plain frame while live. Once its condition can no longer be met for the viewing player, greyed out if owned and labelled **Outgrown** in its tooltip, with its effect struck through — the point is spent and the player should see why. Unowned and outgrown: the Outgrown badge, not greyed (Tooltip, above) |
| Fork parent | Every node of an unspecialised parent is washed as inert, the state most likely to be reported as a bug; once an option is chosen, each parent node shows that option's badge |
| Fork option | A petal beside the parent's lowest-ring node, joined to nothing; siblings shown locked once one is picked |
| Tradeoff | Toggle element on the node; disabled inside the Vault with "Leave the Vault to change this". On Volatile Veins and Molten Seam the toggle is the member's vote and works anywhere; beside it, the vote count against the threshold and any change waiting for the Vault to empty |
| Mod-gated | Not drawn at all when its mod or tag is absent |

**Team.** Every online member is told when a node is bought or refunded and by whom (§4.4), and when the team levels up. The top bar shows the team's Resonance level, unspent points and progress to the next level as a bar.

**Empty-state line.** While the team's Resonance pool is 0 and its level is 0, the tree tab shows one line, "Mine ore inside your Vault to earn Resonance." The condition is read from the team progress the client already has, so it needs nothing more on the wire; for a new team it means the same as "has not mined in a Vault yet", and the line goes with the first Resonance. The top bar's Animus row is hidden until the Animus system is built, behind one switch that turns it back on.

### On-screen Resonance bar

The Tome is closed while a player mines, so without a second readout a bonus such as Ore Focus or Silent Stone shows nothing until the Tome is opened. 1.0 ships both bars (Nick, 2026-10-27: "I like both and make the non-tome one toggleable"):

- the Tome header bar above, unchanged;
- a small on-screen bar, drawn while the player is inside a Vault, showing the team's level and progress to the next level from the same synced state as the header.

The on-screen bar is on by default and each player can turn it off in the client config (§10). It is client-side only and changes nothing on the server. Its position, size and how it shows the share of a gain that came from bonuses (Nick's reading of the gain display as a second segment of the bar, 2026-10-26) are settled on a rendered preview with Nick's yes before it is built.

### Tab 2: Animus Tree

Post-1.0, with the Animus epic (§12).

- Identical layout to Resonance tree but for Animus nodes
- Team's Animus level and skill point count shown in top bar
- Animus pool progress bar

### Tab 3: Ore Memory

**In 1.0 this tab is small** (Nick, 2026-10-26). It holds a **Survey panel** and the Vault Reset button:

- Survey panel: a depth gauge marked with the band edges (§3.1), a death marker, and Seismic Sense's
  readouts (§6.1) once the team owns that node, shown while the viewer is in the team's Vault
- The death marker is the viewing player's last death in the team's current Vault. It is drawn on the
  depth gauge at the death's Y, and its tooltip gives the X, Y and Z. It is stored on the player
  attachment as `last_vault_death`, holding the team, the reset count (`vault_generation`, §11) and the
  position, and each death in the Vault overwrites it. It is cleared when the team's reset count changes
  (§3.5), because the spot it names no longer exists, and a marker recorded for another team is not drawn.
  The Vault Keeps It (§6.1) marks this same spot; there is one death marker, with or without that node.
  With no death recorded the gauge draws no marker
- Whether the chunk the player stands in was generated with the team's current generation nodes. A
  generation node changes only chunks generated after it is bought (§3.1), and the chunks around the
  arrival point already exist when the first points arrive, so without this line Vein Expansion or Guide
  Vein bought at level 1 shows nothing and reads as broken (Nick, 2026-10-27). Each chunk records a stamp of
  the generation state it was made with (§11 Vein Index), and the panel compares it with the team's
  current one. The line has three readings: generated with the current nodes; generated with older
  nodes (made before the team's last generation purchase); and "Generated before this was tracked", for a
  chunk with no stamp. The last is kept apart from the second so that a team that owns no generation node,
  standing in a chunk made before stamps existed, is not told its chunk is out of date
- Seismic Sense's map at tier 3 (§6.1): each explored chunk drawn in one of five shades by its standing
  ore against what a new chunk would hold now (under a quarter: mined out; a quarter to three quarters:
  thin; three quarters to one and a quarter: like new ground; to one and three quarters: rich; above:
  very rich), unexplored chunks left as blank page, and an explored chunk whose density is unknown drawn
  hatched, never as a shade, so it is not read as mined out. At tier 2 every explored chunk is one flat
  fill. One line under the map reads "Shade: ore left, compared with a new chunk". The compass at "no
  denser rock nearby" is a still needle with that text. The steps and colours are settled on the
  preview; the full value travels on the wire, so moving a step needs no protocol change
- Vault Reset button, bottom right (below)

The stats panels below stay deferred to post-1.0. They are specified here so the design is not lost.

- **Team Stats panel** (left): aggregate totals for the whole team
- **Player Stats panel** (right): stats for the viewing player; dropdown to view any team member's individual stats
- Trophy icon next to the team member with the highest value in each stat category (purely cosmetic)
- **Stats tracked (per player and team aggregate):**
  - Ores mined by type
  - Stone blocks broken
  - Total blocks broken
  - Deepest Y level reached inside the Vault
  - Chunks first walked into in the Vault: chunks the player was the first of the team to stand in (§6.1 Wanderer's Cache; Nick, 2026-10-27). Player text says "walked into" or "stood in" for this count, and "explored" only for generated chunks (the Seismic Sense map)
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
  - Ore types made kindred by Kindred Rock, with progress toward the next

**The `PlayerStats` keys these read** (§11 SavedData Pattern has the full table): `ores_mined`,
`stone_broken`, `total_blocks_broken`, `deepest_y`, `chunks_entered`, `time_in_vault_ticks`,
`veins_completed`, `vein_discipline_streak` and `deaths_in_vault`. The other tracked stats above
(lifetime and session Resonance, Vault Echo and Twin Veins triggers, the Volatile Veins counts, mob kills
and Animus) are not written in 1.0; each arrives with the stats panels as an added key. Chapter's End's
per-chunk count is not a `PlayerStats` key: it lives on the chunk (§6.1, §11 Vein Index), and neither is
the death marker, which is on the player attachment (`last_vault_death`, above). Every key is part of a
player save file, so a tag written before it existed must still read correctly: **an absent key reads
as its documented default.** For the counters that default is zero. It is not zero for every key: an
absent `deepest_y` means the player has never entered the Vault, and reading it as 0 would start Shallow
Grace already outgrown and give Highwater Mark 4 free steps of depth.

Kindred Rock needs no key of its own: it reads `ores_mined` and derives which types are kindred from the
count.

### Vault Reset Button

- Only appears in the UI if the viewing player holds a Tier 4 Sovereign Vault Igniter in their inventory
- Located in the Ore Memory tab (bottom right corner, clearly separated from stats)
- Click → the initiator confirms (with the backup checkbox); every other online team member gets a toast and a clickable chat line that opens the vote screen. No screen opens by itself (§3.5; Nick, 2026-10-27)
- Voting follows the majority rule (see Section 3.5)
- Backup checkbox present in the voting dialog
- 10-second countdown warning if vote passes with players inside the Vault

---

## 9. Mod Integrations

### FTB Teams (Hard Dependency)

- Every system keyed on FTB Team UUID
- Dimension registry key: `orevault:vault_<teamIdNoHyphens>`
- Team data saved under the id `orevault:team_<teamUuidNoHyphens>` (§11 SavedData Pattern)
- Resonance and Animus pools stored per team
- Skill tree state stored per team
- Player stat tracking stored per player, queryable by team
- Per-player tradeoff toggle state stored per player (§6.1), **not** on the team; the applied state of the two team-voted generation tradeoffs, Volatile Veins and Molten Seam, is the team's (§6.1)
- A player who leaves a team or joins another takes no personal records with them: their `PlayerStats` (deepest Y, ores mined, Kindred Rock's types, the Vein Discipline streak, the Seam Courier link, tradeoff switches and votes) stay in the old team's data, and they start fresh in the new team (Nick, 2026-10-27: "I am fine with the player losing progress when he swaps teams."). Rejoining the old team finds the record where it was left
- Dimension created lazily, on the first portal trip by any team member, and never before (§3.1)
- A player's solo team is their personal FTB team, whose id is their UUID; FTB Teams never deletes it, and joining a party only points it at the party. A solo Vault therefore stays on disk while its player is in a party, is restored at each start, and is theirs again when they leave
- On the FTB Teams disband event, which fires only when a party's last member leaves, the team's Vault world, tree and stats are archived, not deleted: `disbanded_at` is set and online players inside are evacuated, and the folder and the team's data file move to `<world>/orevault_archive/<teamUuid>/<disbandedAt>/` at the next server start (§3.1). An admin command purges archives. Joining a party never disbands the player's solo team, so a party join archives nothing
- Solo players use their auto-created single-member FTB team. Because FTB Teams creates that team automatically for every player who logs in, `getTeamForPlayerID` never returns empty on a live server — **nothing in this mod may gate behaviour on "has a team"**, since the condition can never be false

### FTB Ultimine (Soft Dependency)

- Detection: `ModList.get().isLoaded("ftbultimine")`
- Ultimine is a `compileOnly` dependency. Every Ultimine type is referenced only from the `integration.ultimine` package, which is entered only after that check returns true, so nothing loads an Ultimine class when Ultimine is absent. Its 26.1 API is typed registration events, so it cannot be reached without importing its types; the boundary is the package, not the import
- If not loaded: the Ultimine nodes (Ultimine Expansion, Ultimine Safety, the Gambit, Steady Chain) are hidden, and no Ultimine hooks are registered
- If loaded: register through Ultimine's events to apply the Volatile Veins roll once per operation and Ultimine Expansion's max block count modifier; Steady Chain sets `ftbultimine:cooldown_modifier` and `ftbultimine:exhaustion_modifier`
- **Each block of a chain is its own break.** Ultimine 26.1 cancels the original `BreakBlockEvent` and breaks every block of the shape, the clicked one included, through `player.gameMode.destroyBlock(pos)` as the real player, so `BreakBlockEvent` and `BlockDropsEvent` fire once per block with the player as breaker. Ore Vault ignores the cancelled event (`receiveCanceled` false). Resonance, the per-block travel stats, Tempered, Ascending Edge and Echoing Edge therefore count each ore of a chain separately with no Ultimine code (Architect, 2026-10-26, from Ultimine's source). Ultimine refuses fake players, so a chain is always a hand break. A mod that registers an Ultimine break handler bypassing `destroyBlock` produces no events and so no payout

### Mekanism (Soft Dependency)

- Detection: by tags, not by mod. Ore Working tiers 4-6 (through Ore Doubling) appear only when the `c:clumps/...`, `c:shards/...` and `c:crystals/...` item tags are non-empty, whether Mekanism, FTB Materials or another mod fills them
- If the tags are empty: tiers 4-6 do not appear in the UI; doubling uses the dust or raw ore fallback
- If they are filled: tiers 4-6 appear; each ore's clump, shard and crystal are found through the `c:clumps/<material>`, `c:shards/<material>` and `c:crystals/<material>` item tags
- No compile-time reference to Mekanism and no reflection. Tags only: they survive Mekanism updates, and FTB Materials, in Nick's pack, ships Mekanism-style clumps, shards and crystals under the same `c:` tags with no Mekanism code (`docs/research/ftb-materials.md`)
- For ores with no item under those tags: fallback to raw ore double

### Generic Ore Dust Mods (Soft Dependency)

- At server start, scan the common item tag **`c:dusts/<oreName>`** for each classified ore block. Note the namespace: NeoForge 26.1 uses `c:` for common tags — the `forge:` namespace named in earlier drafts of this document does not exist and would silently match nothing (the same class of mistake as the `orevault:mineable/pickaxe` bug in #87)
- If a dust tag exists for an ore, Ore Doubling tiers 1-3 drop dust instead of raw ore
- If no dust tag exists, drop raw ore
- No specific mod is referenced; this works with any mod that registers proper `c:` dust tags

### Other mods (tags, data and capabilities only)

The flavour notables of §6.1 reach other mods in Nick's pack without a compile-time reference, reflection or class reference. Each such node is `[REQUIRES: …]`, on a dead-end leaf and hidden when its condition fails.

| Mod | How Ore Vault reaches it | Nodes |
|---|---|---|
| Apotheosis | Conditional loot tables (`neoforge:conditions`, mod loaded) holding Apotheosis's own gem entry | Gem Seam |
| Apothic Enchanting | Echoing Edge's cap reads `getMaxLevel()`, which should return Apothic's raised maxima; if the 2.1.0 jar keeps them in its own config instead, the `orevault:echo_max_level` data map ships them as conditional data | Echoing Edge |
| Paraglider | Attribute `paraglider:stamina_efficiency`, by id | Wayfarer's Lungs |
| GeOre | Optional entries in `orevault:budding/<metal>` block tags | Budding Seam |
| Hephaestus Tools | A trait shipped as conditional data; Hephaestus tools are excluded from Ascending Edge by namespace and component id | Resonant Trait, Ascending Edge |
| Mystical Agriculture, Cucumber | `#minecraft:pickaxes`, `cucumber:paxels` | Ascending Edge, Hammer and Paxel Affinity |
| Energy tools (Just Dire Things, Mining Gadgets, Ender IO, Oritech) | NeoForge `Capabilities.Energy.ITEM` on the item; no soft dependency | Tool charge, Resonant Dynamo, Galvanic Seam |
| Hammer and paxel mods | `c:ore_hammers`, `c:tools/paxel`, `c:tools/mining_tool` | Hammer and Paxel Affinity |

Hephaestus tools are not generated as Ascension rungs. Doing so would need a `compileOnly` dependency on Hephaestus to call its tool-building code for every part combination, which `CLAUDE.md` rule 5 does not list, and a pick that climbed into a Hephaestus tool would then stop, since those are excluded (`DESIGN_LOG.md` 2026-10-26).

#### Unverified: check in the jars before building

These names and behaviours come from documentation, other mods' code or memory, not from the 26.1 jars in Nick's pack. Each is read from the jar, or the decompiled sources, before the node that uses it is built, and the result is written here.

- FTB Ultimine's attribute `ftbultimine:cooldown_modifier` (with `ftbultimine:exhaustion_modifier`) exists under that id and is applied per player (Steady Chain).
- Paraglider's attribute `paraglider:stamina_efficiency` exists under that id and scales stamina use as Wayfarer's Lungs assumes.
- The shape of `Capabilities.Energy.ITEM` in NeoForge 26.1: the context type it is queried with and the name and signature of the insert method (Tool charge, Resonant Dynamo, Galvanic Seam).
- Whether Apothic Enchanting 2.1.0's raised maxima show in `Enchantment#getMaxLevel()`, or live in its own config so the `orevault:echo_max_level` fallback is needed (Echoing Edge).

---

## 10. Configuration

Configuration is intentionally minimal. Most values are derived dynamically from the defined skill tree. Admins can control only things with server performance implications.

**Config file:** `orevault-server.toml` (a `SERVER` config)

Each value below is added to the config in the ticket that first reads it, never before: a value with no reader is the failure recorded in `DESIGN_LOG.md` 2026-09-03 (`CLAUDE.md` rule 7). The block shows the intended file once every reader exists.

```toml
[resonance]
    # Target hours of play to reach Resonance level 30.
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
    # A list of "modid:block_id=common|uncommon|rare" strings; each entry is
    # validated, and a malformed entry or unknown rarity is rejected.
    overrides = ["minecraft:diamond_ore=rare"]

[disturbed_zones]
    # Maximum number of Disturbed Zone blocks placeable per team.
    max_zones_per_team = 10

[reset]
    # Whether team members can take backups before resetting.
    # Disable if disk space is a concern.
    allow_backup_on_reset = true
```

A temporary `[debug]` block also exists — `enable_debug_commands` and `log_resonance_gain` — and is
removed with the rest of the playtest instrumentation at T14, before 1.0. `enable_debug_commands` gates
only the debug subcommands of `/orevault`, such as `/orevault density`. The `/orevault` root and the
commands that are not debug tools are registered whatever the flag says: `/orevault reset vote`, which
the reset vote's clickable chat line runs for every member (§3.5), and the admin commands
`/orevault archive list` and `/orevault archive purge` (§3.1).

**When a change takes effect.** The config screen is reachable in game from Escape → Mods → Ore Vault
→ Config, and NeoForge only offers a `SERVER` config there in a singleplayer world that is not
published to LAN. What a saved change does from there varies, so each value says so in its own
comment and tooltip rather than leaving the player to guess:

| Setting | Takes effect |
| --- | --- |
| `resonance.target_play_hours` | next world load — the curve is computed once at server start |
| `resonance.curve_divisor` | next world load — same curve |
| `ore_classification.overrides` | next world load — `OreClassifier` builds its table at server start |
| `chunk_loading.*` | added with its reader in T10 |
| `disturbed_zones.max_zones_per_team` | added with its reader in the post-1.0 Animus epic |
| `reset.allow_backup_on_reset` | added with its reader in T11 |
| `debug.enable_debug_commands` | immediately |
| `debug.log_resonance_gain` | immediately — read on each orb pickup |

The three world-load values are marked `worldRestart()` on the spec builder, which is what makes the
config screen warn about it instead of silently accepting an edit that does nothing until a reload.

`enable_debug_commands` is immediate but needed help to look that way. The Brigadier `requires`
predicate is evaluated server-side on every execution, so the command really does start working the
moment the flag flips — but the *client's* copy of the command tree is only sent on login, so the
command stayed invisible and un-completable until a reload. `ConfigReload` listens for
`ModConfigEvent.Reloading` and re-sends the tree to everyone online inside `server.execute(...)`, which re-runs the predicate.

**Client config:** `orevault-client.toml` (a `CLIENT` config), each player's own:

```toml
[hud]
    # Show the on-screen Resonance bar while inside a Vault (§8).
    # The Tome's header bar is always shown.
    show_resonance_bar = true
```

| Setting | Takes effect |
| --- | --- |
| `hud.show_resonance_bar` | immediately, read each frame; added with its reader, the on-screen bar (T7) |

**Values derived automatically (not configurable):**
- Level thresholds for Resonance and Animus (derived from the §3.1 baseline, scaled by `curve_divisor`)
- Level cap (the constant 30, §4.3)
- Points per level (the constant 5, §4.3) and every node's ring (authored in the tree layout file, §6)
- Stone content floor (40%, hardcoded constant)
- `averageOresPerHour` (constant in `ResonanceSystem`, see §4.3 step 4)

---

## 11. Implementation Notes and Technical Gotchas

### Dynamic Chunk Generator

The core of the world-gen system. Rather than static registered `PlacedFeature` entries (which are frozen at startup), the mod uses a custom `ChunkGenerator` implementation that reads a snapshot of the team's skill tree state at chunk generation time and constructs the ore placement configuration on the fly.

**Consequence:** Every chunk generated in a team's Vault reflects the skill tree state at the time of generation. Purchasing a node takes effect immediately in newly generated chunks. Already-generated chunks are unchanged (except via the voluntary dimension reset).

**Performance note:** The custom chunk generator runs marginally more code per chunk than vanilla. On a server generating many new chunks rapidly (e.g. a quarry running at speed), this may cause slight TPS impact. This is acceptable and noted in server documentation.

**Implementation pattern.** Chunk generation runs on a background executor; team `SavedData` is main-thread-only (`CLAUDE.md` rule 1). The generator therefore never reads `SavedData`. It is handed a `Supplier` of an immutable snapshot, which the main thread rebuilds whenever the team's tree changes (`docs/adr/0003-generation-snapshot.md`):

```java
// Main thread, on purchase, path purchase, refund, fork change, level-up, reset, restore, Vault load,
// server start and /reload (after the hidden set), a member's team change, and a generation tradeoff applied:
snapshotHolder.set(GenSnapshot.of(teamNodeState));   // immutable copy

// The generator holds only the supplier, never the team data:
Supplier<GenSnapshot> skills = snapshotHolder::get;   // e.g. an AtomicReference
GenSnapshot snap = skills.get();                      // background thread: safe
// Pure core.gen placement returns each vein as a VeinExtent value; the generator writes blocks
// and the vein index from the same values (ADR 0004, ADR 0009). Arguments illustrative:
List<VeinExtent> veins = VeinPlacer.place(snap, chunkPos);
```

`GenSnapshot` holds only what placement reads: the tiers of the owned generation nodes (dormant mod-hidden nodes left out, §6), the Vein Shape and Ore Focus choices, the applied state of the team-voted generation tradeoffs (§6.1 Volatile Veins), the dimension variant, the reset count for the seed (§3.1), the 40% floor clip factors (one for the stone band, one for deepslate, §3.1), and a stamp. Team level is not in it: no 1.0 generation node reads it, and the snapshot is memory only, so adding it when a node needs it costs no migration (`CLAUDE.md` rule 7, no value without a reader). The stamp is a fixed hash of the generation tiers, the two fork choices and the applied tradeoffs; the chunk records it and the Survey panel compares it with the current one (§8 Ore Memory, §11 Vein Index). The reset count is not in the stamp.

The generator calls the supplier once per chunk, at the start of `fillFromNoise`, and uses that one snapshot for every placement step of the chunk, all of which run in that method. Its `codec()` returns `MapCodec.unit(this)`, since it is built per team in code and never parsed from data, and the Vault biome has empty spawner lists. `GenSnapshot` lives in `core.gen` with no dependency on the team data classes, and a static architecture test fails the build if anything in `worldgen` or `core.gen` reaches `data`, `team` or `SavedData`.

Everything else that reads the tree on the main thread (the drop listener, the tool wear hook, attribute recompute, Silent Stone) reads a second immutable view, the team effects view: owned tiers with dormant nodes left out, fork choices, the hidden set, the applied generation tradeoffs, team level and size, and each online member's per-player tradeoffs. It is rebuilt on the same triggers and also when a member toggles a tradeoff, logs in or logs out.

### SavedData Pattern

Persistent team state (the Resonance pool, skill tree state, player stats) is stored with `SavedData`, declared through a `SavedDataType<T>` and a codec, in the server's storage (`MinecraftServer#getDataStorage()`), where 26.1 keeps its own world-wide data such as the scoreboard and command storage. One `SavedData` instance per team, id `orevault:team_<teamUuidNoHyphens>`, which 26.1 writes to `<world>/data/orevault/team_<id>.dat`. Data about one Vault's terrain is kept in that Vault's own folder instead, so it moves with the folder: the explored map (§11 Vein Index) in the level's own storage, at `<world>/dimensions/orevault/vault_<hex>/data/orevault/explored_map.dat`, and the Vault type file beside it (below, "Per-Vault data"), each with its own `data_version`. Team data is created on its first write, never when the team is created: read paths use `get` and treat a missing file as every field at its default. The shape is fixed by `docs/adr/0005-persisted-data-and-versioning.md`; the tables below are the contract its golden tests pin.

**Keys and clock.** Every persisted key in every shape the mod writes is snake_case: team data, `PlayerStats`, the player attachment, the chunk attachment, item components and entity data. Java fields are camelCase and the reader maps them; the spec names the key. Every saved time is overworld game time in ticks (`level.getGameTime()`, one clock for every level), so a window or cooldown does not run out while the server is stopped: 10 minutes is 12,000 ticks and 24 hours is 1,728,000. UUIDs are stored as hyphenated strings, ids as their string form.

**Team data.**

| Key | Type | Absent reads as | Meaning |
|---|---|---|---|
| `data_version` | int | 1 | Shape version |
| `team_id` | UUID | the file's own id | For people reading the file; the file's id wins |
| `resonance_pool` | long, thousandths of a Resonance | 0 | Lifetime Resonance; never reset by a level-up (§4.3). Thousandths, because payments such as Stone Memory's 0.05 have fractions |
| `level_reached` | int | 0 | Highest level the team has had; never decreases |
| `owned` | list of node ids | empty | Owned nodes; the start is implicit |
| `fork_choice` | {fork parent: option} | empty | The chosen option per fork |
| `free_respec_until` | long, game time | 0 | End of the free respec window (§4.4) |
| `free_respec_opened_at` | long, game time | never opened | When the last window opened; a reset within 24 hours of it opens none (§4.4) |
| `pinned_node`, `pinned_by` | node id, UUID | no pin | The shared pin (§8) |
| `bought_by` | {node id: UUID} | empty | Who last bought each owned node |
| `vault_generation` | int | 0 | Reset count, + 1 per reset; part of the generation seed (§3.1) |
| `generation_tradeoffs` | list of node ids | empty | Applied state of Volatile Veins and Molten Seam (§6.1) |
| `disbanded_at` | long, game time | not disbanded | Set on disband; archived at the next start (§3.1) |
| `pending_reset` | {`requested_at` game time, `backup` boolean} | no reset under way | Written before a reset deletes anything and cleared when it finishes, so a crash or stop part-way is finished at the next start (§3.5, `docs/adr/0007-reset-disband-archive.md`) |
| `player_stats` | {player UUID: `PlayerStats`} | empty | Per-player records for this team |

A Vault's dimension type is not team data; it is kept in the Vault's own folder, because it describes that Vault's terrain and changes only when a reset makes a new folder (§3.5). Team data had a `vault_variant` key until 2026-10-31; nothing was built with it, so its removal needs no migration.

**Per-Vault data.** Two files of ours live in each Vault's folder: the explored map (§11 Vein Index) and the Vault type file, `<world>/dimensions/orevault/vault_<hex>/orevault_vault.dat`. The type file is raw NBT `{data_version, variant}` (`data_version` 1; `variant` is `orevault:ore_vault` or `orevault:ore_vault_expanded`), outside the level's own `data/` storage, written once when the folder is made (temp file, then rename) and never rewritten; backed up, deleted and archived with the folder. It is not a `SavedData`, because it is read before the level exists. On first entry, or when a reset re-registers the Vault, the type is expanded if the team owns Vault Expansion and base otherwise (§3.5 step 5), and the file is written before the level is registered, so no Vault folder exists without one. A missing or unreadable type file, or an unknown `variant`, has no default: it is never read as base. Unlike every other absent key it refuses the Vault instead (§11 Dimension-per-Team Registration), because a wrong guess costs terrain: an expanded Vault read as base has no Y range below 0 for its deepslate (`docs/adr/0005-persisted-data-and-versioning.md` section 3a).

**`PlayerStats`**, nested and versioned on its own:

| Key | Type | Absent reads as | Read by |
|---|---|---|---|
| `data_version` | int | 1 | |
| `ores_mined` | {block id: int} | empty | Kindred Rock, §8 stats |
| `stone_broken` | int | 0 | §8 stats |
| `total_blocks_broken` | int | 0 | Calloused Hands |
| `deepest_y` | int | never entered | Shallow Grace, Highwater Mark |
| `time_in_vault_ticks` | long | 0 | §8 stats |
| `chunks_entered` | int | 0 | Wanderer's Cache |
| `veins_completed` | int | 0 | §8 stats |
| `vein_discipline_streak` | int | 0 | Vein Discipline |
| `deaths_in_vault` | int | 0 | §8 stats |
| `active_tradeoffs` | list of node ids | empty | Per-player tradeoffs; the member's vote for Volatile Veins and Molten Seam |
| `courier_link` | {dim, x, y, z, generation} | no link | Seam Courier; a link into the team's Vault from before a reset reads as no link |

A chunk of the team's current Vault is credited to `chunks_entered` of the first member of the Vault's team to stand in it, once, by an end-of-tick check under the rule in §6.1 Wanderer's Cache; the chunk attachment's flag records that it has been credited (§11 Vein Index). Nick chose this on 2026-10-27.

**The player attachment `orevault:player`** (`copyOnDeath`): `data_version`; `return_pos {dim, x, y, z}`; `in_vault {team, generation}` (the Vault the player is in and its `vault_generation` at entry, absent when outside; `docs/adr/0002-vault-lifecycle.md`); `tome_granted`; `entry_points`, a list of up to three `{team, generation, x, y, z}`; `homeward {team, generation, x, y, z}`; `homeward_cooldown_until` (game time); `last_vault_death {team, generation, x, y, z}` (§8 Tab 3). A position whose team is not the player's current team, or whose generation is behind that team's `vault_generation`, reads as absent; that is how a reset clears them without editing offline players' files.

**Also save data:** the chunk attachment `orevault:vein_index` (§11 Vein Index, ADR 0004), the item components `orevault:vault_tool` and `orevault:attuned` and `orevault:cache_tier` (Wanderer's Cache, §6.1), the item `orevault:wanderers_cache`, the orb entities (§11 Resonance Orb Entity), the Vault type file's name `orevault_vault.dat` (with the dimension key and folder scheme, §3.1), the max-health modifier id `orevault:travel_max_health` (§6.1 Travel stats), and every registry id: blocks, items, entity types, components, attachments, dimension types, the biome, the ticket controller and every node, skill and option id. All are frozen once Nick's world carries them (from T5).

**Nothing derived is stored.** The current level, unspent skill points and every skill's tier follow from the pool, the curve, `owned` and the layout, and the number of chunk tickets from the anchors placed; storing them as well gives two sources that can disagree. `level_reached` is the exception and is stored on purpose: the curve can move under `curve_divisor` (§4.3), and a team must not lose a level, or the points it paid out, when it does. The team's level is the curve's level for the pool or `level_reached`, whichever is higher, and `level_reached` is raised whenever the curve's level passes it. Animus fields arrive with the post-1.0 epic.

**Tradeoff toggles live on `PlayerStats`, not on the team.** §6.1 specifies them as per-player — one member of a team can run Tithe while another does not — so they are `active_tradeoffs` on `PlayerStats`. Seam Courier's linked container is per player too: `courier_link` on `PlayerStats`, absent meaning no link. For Volatile Veins and Molten Seam a member's entry in `active_tradeoffs` is their vote; the team's applied state is `generation_tradeoffs`, since it changes only while nobody is inside and so cannot be derived from the votes. The pending change is not saved: it is the difference between the two.

**A team change leaves `PlayerStats` behind.** The entry stays in the old team's map, and the new team creates a fresh one on its first write, with every field at its documented default (`deepest_y` absent, so the player has never entered that team's Vault) (Nick, 2026-10-27). The change is seen through FTB Teams' `PlayerChangedTeam` event, which fires for offline players too; nothing is copied between team files. A personal team's id is the player's UUID and FTB Teams never deletes it, so a player who leaves a party finds their personal record where they left it.

**Saves carry over from T5 to 1.0** (Nick, 2026-10-27). Nick's playtest world is kept between builds, so every change to a saved shape from T5 on reads the tag the previous build wrote: an added field reads its default, and a rename or removal comes with a migration.

**`data_version` and migration.** Each shape (team data, `PlayerStats`, the player attachment, the chunk attachment, the explored map, the Vault type file, `orevault:vault_tool`, the orb) has its own `data_version`, and they move independently. Every load reads it first and migrates forward if it is behind the current version, through a fall-through chain that takes any older tag to the current shape in one pass; each step keeps frozen copies of what it translates from and running it twice changes nothing. Data starts at version 1, and a tag with no `data_version` reads as 1; saves from before the restart are not supported (§6.1 Reserved node ids). An added field never bumps the version: it reads its documented default from a tag written without it. A rename, a removal or a change of type or unit bumps it, with a migration, a fixture of the old tag and an updated golden key set in the same PR. A tag from a newer build loads best-effort with one WARN.

**Loading never throws.** In 26.1 `SavedDataStorage` turns a parse error or an exception during load into a missing file (`SavedDataStorage#readSavedData` returns null), and the next save then writes an empty team over the real one. Two things stop that. First, an unreadable file is copied aside before anything is written: when `get` returns null but the team's file exists on disk, the one write accessor logs an ERROR naming the path, copies the file to `team_<hex>.dat.unreadable-<gametime>` beside it, and only then creates the team's data, so play goes on and the original bytes are kept for someone to recover. The restore pass uses the same check to tell a team with no file from a team whose file cannot be read, logs an unreadable one at ERROR naming the path, and still restores the Vault. A Vault's type is kept in its own folder, not in team data, so a damaged team file costs the team's progress, never its terrain. An unreadable explored map (§11 Vein Index) is logged at WARN instead, and never throws either. Refusing all writes to that team for the session was rejected: it loses a whole session of play with nothing on screen saying so (`DESIGN_LOG.md` 2026-10-27, Epic 0 round 1). So was refusing the team's Vault for the session, and so was dropping every write for that team (`DESIGN_LOG.md` 2026-10-31). Second, the team codec maps a `CompoundTag` through a hand-written reader with defaults for every key, not a strict record codec, and no input can make it throw. On load, an owned id the layout no longer has is dropped (its point returns), a node cut off from the start is pruned free, a fork choice or pin that no longer resolves is dropped, entries in `bought_by`, `generation_tradeoffs` and `active_tradeoffs` for ids that are not owned or not known are dropped, and a mod-hidden owned node is kept dormant (§6).

**In memory only, never saved:** trip state and the kept Apprentice's Ledger count (§11 Vault Session State), Volatile Veins' pity counters, the free undo window (§4.4), the reset vote, the pending generation tradeoff change, the generation snapshot and team effects view (§11 Dynamic Chunk Generator), the classifier table and the level-cost array.

### Ore Rarity Classification

Run at every server start (not cached to disk), before any Vault is restored. The candidates are the blocks in the `#c:ores` block tag, not blocks of a particular class: a modded ore is an ore because its mod tags it as one. The features are the `OreConfiguration` placed features in the underground-ores step of the overworld biome source's biomes, after NeoForge biome modifiers. A feature listed by more than half of those biomes is all-biome; an ore uses its all-biome features, and its biome-only ones only when it has none. Blocks targeted by the same feature are one ore, so stone and deepslate variants are not counted twice. Placement fields are read by encoding each modifier through `PlacementModifier.CODEC`, not through access transformers. Heights are sampled, about 4,096 fixed-seed draws per height provider against the real overworld's generation context, so every provider type, modded ones included, goes through one path. Apply the §4.2 rule on expected blocks per chunk and mean blocks per vein. The details and the tests are in `docs/adr/0009-baseline-ore-model-and-classifier.md`.

An ore in `#c:ores` with no overworld `PlacedFeature` is not classified, and so not placed, unless an override names it. Ancient debris is handled by Ancient Traces (§6.1), not by the classifier.

Apply admin config overrides after classification. Store classification in memory for the session.

### FTB Teams Event Hooks

- `FTBTeamsEvent.TeamCreated` → nothing. FTB Teams creates a team for every player who logs in; team data is created on its first write, not here
- Tome of the Deep Seam → granted on the player's first login, through the vanilla login event rather than an FTB Teams event (FTB's party-join event never fires for a solo player), and recorded as `tome_granted` so it is given once
- `FTBTeamsEvent.TeamDeleted` (posted only when a party's last member leaves) → set `disbanded_at` on the team's data and evacuate online players inside its Vault. Nothing is deleted or moved while the server runs: the Vault's folder and the team's data file move to `<world>/orevault_archive/<teamUuid>/<disbandedAt>/` at the next server start, before restore, and restore skips it (§3.1). The start-of-server pass reads team files as raw NBT, so it never caches a `SavedData` for a file it then moves
- `FTBTeamsEvent.PlayerChangedTeam` → the team-change triggers (snapshot, sync). It fires for offline players too; `PlayerJoinedPartyTeam` does not

### Volatile Veins Pity System

Track per player (not per team), **in memory only**, not in `PlayerStats` and not saved: the counters reset on logout anyway, so persisting them would only add fields to a save file.
```java
int volatileVeinsTriggerStreak; // consecutive triggers
boolean volatileVeinsSafeWindow; // currently in safe window
int volatileVeinsSafeBlocksRemaining;
```
On logout: `volatileVeinsTriggerStreak = 0`, `volatileVeinsSafeWindow = false`.
After 3 consecutive triggers: enter safe window for 10 blocks.

### Dimension-per-Team Registration

Dimensions are registered **lazily, on the first portal trip by a team member** (§3.1). Earlier drafts of this section recommended registering every team's dimension at server start and creating more on team-created events; that recommendation is withdrawn. FTB Teams auto-creates a single-member team for every player who logs in, so eager registration means one full `ServerLevel` per player account the server has ever seen — chunk map, storage, region directory and all — for a dimension most of them will never enter.

Registration itself, in the order of `docs/adr/0002-vault-lifecycle.md`: fill the team's snapshot holder; build the `LevelStem`; unfreeze the live `LEVEL_STEM` `MappedRegistry`, register the stem, and re-freeze in a `finally`, skipping registration when the key is already present (a registry entry cannot be removed, and after a reset the old one is still there); construct the `ServerLevel` through its public constructor with `DerivedLevelData`, mirroring `MinecraftServer#createLevels`; insert it through `VaultLevels.register`, the only code that touches the world map, which calls `markWorldsDirty()`; add the world border listener; post `LevelEvent.Load`; then, for a restored Vault, reactivate its saved chunk tickets (§3.4). Whether `MinecraftServer.storageSource` can be reached without an access transformer is unverified: the sources read so far were decompiled with the old build's access transformer already applied, so a field shown as public there is not evidence. The plan keeps the access transformer, with its ticket comment, until E1-S5 (#195) reads a clean decompile (`docs/adr/0002-vault-lifecycle.md`). Nothing deletes a level's registry entry or folder while the server runs: a disbanded Vault is archived at the next start (§3.1), and a reset unloads a live Vault only if the level-unload spike (E3-S11) showed it safe, otherwise it waits for the next start (§3.5). Removal, where it happens, closes the level's storage before any file is touched, goes through `VaultLevels.remove`, which also calls `markWorldsDirty()`, clears the team's generator, snapshot and key entries, and logs the team and the reason. `forgeGetWorldMap()` and `markWorldsDirty()` are deprecated NeoForge internals with no supported replacement, so the dimension path is re-tested on every NeoForge version change.

**Eager restore.** At server start, before anyone connects, the restore pass scans `<world>/dimensions/orevault/` and re-registers every Vault found there. For each it reads the Vault's type from the type file in its folder and `disbanded_at` from team data, and skips archived Vaults. A folder whose type file is missing or unreadable, or names an unknown type, is logged at ERROR with the path and skipped; nothing is written into it, and its portal refuses entry with a message saying why. A team file that is absent or unreadable does not stop the restore. No skipped folder ever stops the server or the other Vaults. `in_vault` names the Vault and the team's reset count at entry. On login, a player whose `in_vault` names a Vault they are not in (not loaded, or archived), a Vault whose team has disbanded, or a Vault whose team is not the player's current team, is sent to their return position; a player inside a Vault that was reset while they were offline (its reset count is now higher) is placed at the team anchor (X=0, Z=0) (§3.5).

**A team change while inside.** When `PlayerChangedTeam` fires for an online player who is inside a Vault that is not their new team's, the player is sent to their return position with a chat line saying why, for example after a party owner removes a member who is mining in the party's Vault. An offline player is handled by the login check above. Mining in a Vault pays only a member of that Vault's team (§3.4); the player's records stay with the old team (§9; Nick, 2026-10-27, Q3).

**The Vault's type at creation.** Creating a Vault writes its type file before the level is registered and writes no team data. With no folder (a team's first entry, or the re-registration after a reset), the type is expanded if the team owns Vault Expansion and base otherwise (§3.5 step 5). With a folder, the type is read from its file each time; nothing guesses a type or caches a refusal, so an admin who puts a damaged file back has the Vault at the next attempt. A refused portal entry tells the player `message.orevault.vault_damaged`: "This Vault's save is damaged, so it cannot be opened. The server log names the file for an admin." It is a chat line, not a Tome refusal (`docs/adr/0002-vault-lifecycle.md` creation step 2).

> **The `markWorldsDirty()` trap.** After `server.forgeGetWorldMap().put(key, level)` you **must** call `server.markWorldsDirty()`. `MinecraftServer` ticks levels from a cached `ServerLevel[]` built by `getWorldArray()`, which is only rebuilt when `worldArrayMarker` changes — and `markWorldsDirty()` is the only thing that changes it. Omit the call and a dimension created after the first server tick is placed in the map but **never ticked**.
>
> The failure mode is deeply misleading. Because packet handlers run synchronously, block *placement* works and the first left-click produces exactly one `BreakSpeed` event — then nothing. `ServerPlayerGameMode.tick()` never runs, so destroy progress never advances and `BREAK_BLOCK` never fires; the block appears to break client-side and then snaps back. Nothing is cancelled, no protection mod is involved, and relogging "fixes" it because the level is then recreated by `createLevels` before the cache exists. Dimensions created during `ServerStartedEvent` also work, because the cache is still `null` at that point — which is exactly what makes the bug look like it is about team registration rather than tick scheduling. See issues #82 and #89.

### Vein Index

Many nodes need to know where a vein is, which vein a block belongs to, or when a vein has been *completely* mined. Every consumer in §6.1 and §8:

| Needs | Consumers |
|---|---|
| Completion | Vault Echo (burst), Echo Chamber (through Vault Echo), Twin Veins (clone the vein), Last Ore (which block is the final one), Clean Cut (the first and last ore of the vein), Vein Discipline (completion and abandoning), Chapter's End (completions per chunk) |
| The rest of a vein | Volatile Veins (vanish the *remaining* blocks), Vein Sight (outline the vein you are in) |
| Other veins nearby | Prospector's Eye (veins within 16 blocks), Stonecaller (nearest vein's ore type within 8 blocks), Chapter's End (reveal the veins still standing in the chunk) |
| A vein's identity | Pathfinder's Claim (the first vein mined in a chunk), Prospector's Eye and Vein Sight (the first block of a vein), the Survey panel's guide-vein count (Guide Vein, §8) |
| Machine breaks | Automated Extraction (machine-broken ore counts toward completion, but a vein a machine finishes fires no completion effect, §3.4) |

The obvious implementation — a flood fill from every broken ore block — is far too expensive in a 60%-ore dimension being quarried.

Instead: **the chunk generator places the veins, so it already knows their exact extents.** Persist a per-chunk index of each vein's box, ore, ordinal and standing positions at generation time. Completion is then a decrement to zero, "remaining blocks" is a lookup, and nearest-vein is a bounded search over an index rather than the world.

Rules the implementation must hold:
- The generator's placement returns each vein's extents as values. The index is persisted in a versioned chunk attachment, `orevault:vein_index`, written only for the chunk being generated, and **veins never cross a chunk border**: a vein's shape is rolled about its origin and placed where its bounding box fits inside the chunk. Any configured size up to 37 always fits; a larger request is split into veins of 37 or less with the same expected block count.
- The attachment stores, per vein, a header (box, ore, ordinal, flags) and a bitset of its standing positions over its box. The remaining count is derived from the bitset. A header names its ore by an index into the chunk's palette, and the palette stores block ids, not the classifier's grouping: each ore is the `Identifier` string of its stone-band block, the same group id ADR 0009 uses, so the id means the same thing after a mod is added or removed. On load the palette is reconciled against the stored id and the current ore grouping; an id that is no longer a known ore keeps its vein's bits and never throws (`docs/adr/0004-vein-index.md`, `docs/adr/0005-persisted-data-and-versioning.md`). Generated veins take ordinals below 65,536; Twin Veins copies take ordinals from 65,536 up.
- Every removal clears a position's bit, so nothing is counted twice. A hand break that empties a vein fires its completion effects. A machine break, an explosion, another mod's block change and Volatile Veins' vanish clear bits but fire nothing. Whenever a vein is read, positions whose block is no longer its ore are cleared silently: the world is the authority.
- Veins created by **Twin Veins** are registered into the index when spawned, inside the source vein's chunk, so they behave identically to generated veins. A copy that does not fit in the chunk is not made, and a copy never writes another chunk.
- **Player-placed ore is never indexed.** This closes the otherwise obvious exploit — place ore, break it, farm Vault Echo — by construction rather than by a check that could be missed.
- Chunks generated before the index existed have no entries, and their veins never complete.
- The Seismic Sense map (§6.1) needs chunks that are not loaded, and a chunk's attachment is never read from disk on demand. Each Vault therefore keeps one more saved file in its own folder, `orevault:explored_map`, holding two bytes per generated chunk: zero when not generated, otherwise the chunk's standing indexed ore plus one, or a mark for "generated, density unknown" when the chunk has no index. It is written on the main thread at every chunk load and at every change to a loaded chunk's index, and it is deleted, backed up and archived with the Vault folder (§3.5). It grows by at most 2 KB per 32 by 32 chunk region the Vault reaches, and never across a reset.
- The `orevault:vein_index` attachment also holds the chunk's other state: when it was first generated (Frontier Bonus), `pathfinder_claimed`, set on the chunk's first hand-broken indexed ore whether or not the team owns Pathfinder's Claim (§6.1), whether a team member has stood in it, which credits Wanderer's Cache once (§6.1; Nick, 2026-10-27), Chapter's End's count and fired flag (§6.1), and which veins are guide veins (§8).
- The `orevault:vein_index` attachment also records a stamp of the generation snapshot the chunk was made with, which the Survey panel compares with the team's current one (§8 Ore Memory). A chunk with no stamp was generated before stamps existed and reads as "Generated before this was tracked", not as out of date.
- If an attachment written on the `ProtoChunk` does not carry over to the `LevelChunk`, the generator puts the index in a pending map keyed by chunk, which the main thread drains into the attachment at chunk load.

### Drop Pipeline

Many nodes modify what a block break drops, and the order they apply in changes the result substantially. Rather than each node ticket patching the same handler, every one of them registers into a single ordered pipeline on `BlockDropsEvent`. Re-derived from §6.1:

| Stage | Nodes |
|---|---|
| 1. Consume | Tithe (block consumed, no drops), Brittle Stone (shatter roll, and the adjacent ore it destroys), Stone Curse (stone drops nothing). This stage clears items only and never calls `setDroppedExperience`: vanilla XP from the block always drops (§3.4) |
| 2. Fortune | Vein Fortune, Vault's Purity's +1 Fortune, Ore Imprint's +1 Fortune on its metal |
| 3. Quantity | Ore Doubling, Greedy Seams (+100%), Resonant Overload (−50%), Novice's Luck, Resonant Symbiosis (+25% with company), Bedrock Communion (+2% per block below Y=0), Last Ore (final block doubled), Automated Extraction (machine breaks only) |
| 4. Transform | Smelter's Intuition, Molten Seam (smelted near lava; the better of its roll and Smelter's Intuition's), Runic Attunement (Attuned marking), dust/Mekanism substitution, Kiln Touch (non-ore drops smelted) |
| 5. Bonus | Items: Stone Memory (flint, nugget), Salvager's Eye, Stonecaller. XP and orbs, from the same listener: Stone Memory XP (tripled by Stone Curse), Ancient Knowledge, Stonecutter's Patience, Pathfinder's Claim, Vault Echo and Echo Chamber, Chapter's End, Stone Memory T5's burst, Deep Harvest, travel vanilla XP from ore. Items, rolled once per ore and skipping stages 2 to 4: Bookseam, Gem Seam |
| 6. Deliver | Budding Seam replaces the broken metal ore with its budding block; Seam Courier moves the final ore drops into the linked container; the counters for Tempered and Ascending Edge and the Echoing Edge roll run here, on the live main-hand stack |

Ancient debris skips stages 2 to 4 entirely and always drops exactly one (Ancient Traces, §6.1). Harvester's Lot runs in the same listener for blocks broken outside the Vault, the only drop change made there.

> **Fortune is the awkward stage.** `BlockDropsEvent` fires *after* `Block#getDrops` has already rolled the loot table, so a Fortune bonus cannot simply be added there. The Fortune stage therefore discards the existing drop list and re-rolls `state.getDrops(...)` with a copy of the tool carrying the appropriate Fortune level. Everything else operates on the list in place.
>
> That re-roll is why Fortune runs before Quantity rather than after it, which is a change from the original ordering. Rolling Quantity first and then discarding the list would have silently deleted everything Ore Doubling and Greedy Seams had just added, whenever a Fortune node was also unlocked. The current order also answers the other two ordering questions cleanly: Consume short-circuits before Fortune ever rolls, and Transform still follows Quantity, so Smelter's Intuition smelts the doubled output.

Consume, Fortune and Quantity **contribute** to an outcome the pipeline applies once, rather than editing the drop list themselves. That is what makes two guarantees structural instead of conventional: Quantity multipliers compose multiplicatively because they are multiplied together, and the loot table is re-rolled exactly once no matter how many nodes raise Fortune. Transform and Bonus do edit the list, because replacing and appending compose in the obvious way and there is nothing to accumulate.

### Resonance and Mining-Speed Composition

The drop pipeline settles ordering for item drops, and there the Quantity multipliers compose multiplicatively on purpose. Resonance and mining speed follow a different rule: **percentage bonuses sum; penalties multiply after the bonuses are summed; the result is floored at zero** (Nick, 2026-10-26):

```
value = base × (1 + sum of bonuses) × product of (1 − each penalty), floored at 0
```

A penalty is a penalty however it is written: Greedy Seams' "−50% Resonance" multiplies by 0.5 after the bonuses, it is not added to them. Every node that pays or costs Resonance or mining speed is listed here.

| Quantity | Accumulator | Bonuses (summed) | Penalties (multiplied after) |
|---|---|---|---|
| Resonance from one ore break | `ResonanceBonuses` | Shallow Grace (+50%), Apprentice's Ledger (+100% on a trip's first 100 ore), Deep Habit, Calloused Hands, Kindred Rock, Vein Discipline, Molten Seam (+25%), Frontier Bonus (+40%), Vault's Purity (+20%), Last Ore (+100% on the final block), Clean Cut (+50% across the vein), Resonant Overload (+100%), Resonant Symbiosis (+50% with company), Bedrock Communion (+2% per block below Y=0), Volatile Veins: Ultimine Gambit (+20% on surviving operations), travel Resonance gain (+5% a node) | Greedy Seams (−50%), Vault Fever (−25%), Full Spectrum (−20%), Resonant Symbiosis alone (−40%), Bedrock Communion between Y=32 and Y=245 (×0), Silent Stone (×0) |
| Mining speed on one block | `MiningSpeedModifiers` | Long Delve (+10%/stack), Kindred Rock (+20% on kindred types), travel mining speed (+10% a node), Hammer and Paxel Affinity (+25%), Ore Imprint (+25% on its metal), Resonant Trait (+15% a level), Glass Edge (+50%) | Bedrock Communion (halve), Long Arm (×0.75), Unyielding Edge at 1 durability (×0.5) |

Not in either accumulator, and still to be placed by the Balancer: the flat payments (Stone Memory's 0.05 on deepslate and its T5 burst, Vault Echo, Chapter's End), which are not per-break percentages and for which it is not yet stated whether the bonuses apply (Deep Harvest's extra orb takes none); Hoarder's Instinct's cache multiplier, paid on collection; Brittle Stone's instant break, which replaces mining speed rather than modifying it; and Vault Fever's Haste II, a potion effect.

**Silent Stone's rate** is placed: it reads the bonuses in this table that can be evaluated for a member at any second (Deep Habit, Calloused Hands, Vein Discipline, Frontier Bonus at the member's chunk, Vault's Purity, Resonant Overload, Resonant Symbiosis with company, Bedrock Communion at the member's Y, travel Resonance gain), sums them the same way, and takes no penalty; the per-member results then go through the §4.2 team scaling. The other bonuses are tied to an ore break or a vein event and do not apply. The full rule and its numbers are with the node in §6.1.

Summing is what keeps a node's stated number meaning what it says wherever it sits in the tree: +25% is a quarter of the base rate whether it is the first bonus or the seventh. Multiplying a dozen small percentage nodes together compounds far past a curve calibrated for 100 hours, and no node's tooltip could then state its own effect honestly.

Applying a penalty last is what keeps it a real cost — halving the base speed before the bonuses would let enough stacked bonuses cancel Bedrock Communion out entirely, and adding −50% into a sum of +200% would cost a sixth instead of a half.

**Vein size and count.** Generation bonuses follow the same rule. A "+x% vein size" is +x% of the mean *blocks* a vein places, not of vanilla's configured `size`, which is not a block count (§3.1: size 9 places 6.8 blocks, size 17 about 22): the generator picks the configured size whose mean block count matches, through the inverse of the shape table (`docs/adr/0004-vein-index.md`). Size bonuses sum: Vein Expansion tier 5 with Volatile Veins is +125%, not 2 × 1.25. Abundance's −20% is a penalty and multiplies after the sum. Guide veins take no size or count bonus (§6.1 Guide Vein). On this reading the largest 1.0 request is a common vein of about 26 blocks, configured size about 15, so nothing in 1.0 reaches the configured size of 37 above which §11 Vein Index splits a vein; the split is a safety net.

Tithe's 1.75× and the §4.2 team divisor stay outside the accumulator. Both are stated as multipliers on a value rather than as percentage bonuses, and both apply once per break rather than per node.

### Vault Session State

Three nodes count something per Vault visit — Deep Habit (blocks this trip), Long Delve (unbroken minutes this trip), Apprentice's Ledger (the trip's first 100 ore). One per-player trip record holds all of it, created on entering a Vault dimension and discarded on leaving, with one exception: Apprentice's Ledger's ore count and the time the trip ended are kept, and a trip that starts within 10 minutes (12,000 ticks of game time, like every other window, §11 SavedData Pattern) of that end continues the count (§6.1; Nick, 2026-10-27). The Ledger's count moves only for ore mined by hand while the team owns the node, so ore mined earlier on a trip does not use up the bonus (§6.1).

The record is **in memory only, not `SavedData`**: a relog is a new trip for Deep Habit and Long Delve. That is the simpler rule and the one that cannot be farmed by logging out to bank a Delve stack. The kept Ledger count is held in memory per player UUID, so it survives a logout within the 10 minutes and is lost only when the server stops.

Leaving is not watched through the portal code, because a player also leaves by dying, by a command such as `/execute in <dimension> run tp`, and by logging out, and only the portal path goes through `VaultTeleport`. Nor is the dimension-change event enough: `PlayerChangedDimensionEvent` is posted only from the cross-level branch of `ServerPlayer#teleport`, and a death respawn never calls `teleport` (`PlayerList#respawn` builds a new `ServerPlayer` in the respawn level and posts only `PlayerRespawnEvent`; `docs/research/neoforge-26.1-sources.md` "Respawn and PlayerChangedDimensionEvent"). A death exit therefore fires no dimension-change event; the last build never ended a trip on death (old #149). So trips are keyed by player UUID, never by the `ServerPlayer` object, which respawn replaces, and a per-tick guard ends the trip of any player who has one and is no longer in a Vault, whatever route they left by. The dimension-change and logout events end a trip at once; the guard catches the rest within a tick. Login is watched too: a player who logged out inside a Vault reconnects straight into one with no dimension change to observe.

The trip record also lists the veins the player has broken into and not finished, which is what Vein Discipline's abandon reads (§6.1). Leaving by any of the routes above, logging out included, applies the abandon from that list before the record is discarded. The list is never saved, so a server crash applies no abandon; the streak itself is on `PlayerStats` and keeps its last saved value.

The §8 Ore Memory counters on `PlayerStats` (`ores_mined`, `stone_broken`, `total_blocks_broken`, `deepest_y`, `time_in_vault_ticks`) are written from the same place, off the single `BlockDropsEvent` listener the drop pipeline already owns, so "which Vault, whose team, ore or machine" is derived once. §3.4 applies: nothing a machine broke moves a player's record.

### Resonance Orb Entity

Resonance and Animus orbs share a `VaultOrbEntity` base that pays a team pool rather than player XP. It is **modelled on `ExperienceOrb` rather than extending it**: the two behaviours that matter here are exactly the two vanilla hard-codes wrongly for a team-owned orb — `playerTouch` grants the toucher experience, and the follow logic chases the nearest player of any allegiance. Subclassing would have left both one missed override away from paying out to whoever walked past.

Movement is server-authoritative. Vanilla runs the follow logic on both sides and lets the client predict, which works because "nearest player" is knowable client-side; team membership is not, so a client prediction would pull orbs toward players who cannot collect them. The client interpolates what the server sends, which is why the entity type uses a short update interval.

One renderer draws both types. It reuses the vanilla orb sprite sheet — an orb should read as an orb and differ by colour — sizing by value on vanilla's own thresholds and taking its tint from the entity: Resonance = blue/cyan, Animus = red/dark red.

### Tree Layout Data

The Resonance tree's shape is one JSON resource in the mod jar (§6 Layout data), read at startup into an immutable `TreeLayout`. Pure classes over that object compute distances by breadth-first search, check that a node can be bought or refunded (§4.4), find the cheapest path for the Tome's hover and shift-click (§8), and derive each skill's tier from the owned ids. None of them imports Minecraft, so all of it is tested in JUnit. The tests that pin the layout's rules are listed in §6.

Constants that are not layout live in one class beside it: `LEVEL_CAP = 30`, `POINTS_PER_LEVEL = 5` (§4.3) and the refund prices (§4.4). Per-tier effect values live in one effects-constants source, keyed by skill id and count owned, read by the effect code, the lang test and the balance tests. Each entry is the ordered list of that tier's effect-line arguments, the arguments of `node.orevault.<skill id>.effect.<tier>` in order (§8 Lang keys); a tier whose line has no number holds an empty list, and the lang test checks that each key's placeholder count equals its list's length.

### Network and Sync

One payload channel, registered on the mod bus through `RegisterPayloadHandlersEvent`, carries everything between the Tome and the server (`docs/adr/0006-network-channel-and-tome-sync.md`). Serverbound payloads carry intent only, never a team, cost, tier, level or price; handlers run on the main thread through `enqueueWork` and re-check every rule of §4.4 and §6 on the server. Every mutation, accepted or refused, is followed by a full tree sync to the players it concerns, and a refusal names a lang key the Tome shows. The team's tree state travels separately from the Resonance progress, which is sent at most twice a second while orbs are collected. `PROTOCOL_VERSION` bumps when a payload or a node id changes; the layout hash catches every other layout difference. Every decoded string and collection has a size bound.

### Tool Wear Hook

NeoForge posts no event when an item loses durability, and eight nodes need one: Tool care and the Charge fallback, Kit Care, Hammer and Paxel Affinity, Tempered, Unyielding Edge, Glass Edge and Galvanic Seam. Ore Vault therefore has one mixin class with two injections into the NeoForge-patched `ItemStack`, which call one pure `WearRules` class (`docs/adr/0008-tool-wear-mixin.md`).

- At the head of `hurtAndBreak(int, ServerLevel, LivingEntity, Consumer<Item>)`, the amount is multiplied (Glass Edge ×3; Galvanic Seam ×2 on a tool with no energy storage), before the item's own `damageItem` and before vanilla Unbreaking, so Unbreaking still rolls per point and an energy tool pays the same amount in FE.
- At the return of the private `processDurabilityChange(int, ServerLevel, LivingEntity)`, which is vanilla's durability processing, each remaining point rolls once against the summed care chance of Tool care, the Charge fallback and Hammer and Paxel Affinity, then once on its own for Tempered (§6.1 Travel stats); then Unyielding Edge clamps so the damage never reaches the maximum. Outside the Vault the only roll is Kit Care's. Both `hurtAndBreak` and `hurtWithoutBreaking` (a trident's throw and riptide wear) go through this method, so Kit Care covers a trident too.
- The scope comes from `core.wear.WearScope`, a main-thread table keyed by player UUID that the mining code fills from the team effects view (§11 Dynamic Chunk Generator, `docs/adr/0003-generation-snapshot.md`). When `BreakBlockEvent` passes for a hand break (§3.4) in a Vault, before `mineBlock` damages the tool, the player's break entry is set; it is cleared when that break finishes, and at the latest at the end of the server tick, so a cancelled break cannot leave it set. Tool care, the Charge fallback, Hammer and Paxel Affinity, Tempered, Unyielding Edge, Glass Edge and Galvanic Seam act only while it is set and only on the main-hand item, so a sword hit, a flint and steel, a trident or a hoe in the Vault takes normal wear.
- Kit Care does not use the break entry. At the end of each server tick every online real player's Kit Care tier is set, or 0 inside a Vault. It acts on any durability loss of a held item or worn armour outside a Vault, from any cause, when the entity is a real player: a `ServerPlayer` that is the one the server's player list holds for its UUID (§3.4). A dispenser or a machine never qualifies.
- The mixin checks the player-list identity and the item's slot, looks up `WearScope` and calls `WearRules`. It holds no other logic and reads nothing else of Ore Vault's.

The mixin config `orevault.mixins.json` is declared by a `[[mixins]]` entry in `neoforge.mods.toml` and sets `injectors.defaultRequire: 1`, so a NeoForge bump that moves either target fails at load instead of leaving wear vanilla. Neither injection reads a local variable, so no bytecode slot needs pinning; a gametest or playtest that breaks a block with each node owned pins the order.

---

## 12. Suggested Build Order

The repo was restarted on 2026-10-25 and is being rebuilt. Tasks are T1 to T14, defined with owners, reviewers and gates in `docs/TEAM_REQUIREMENTS.md` "Shared task list"; this table is the summary.

**The playable loop comes first.** Toolchain and an empty mod that runs; one block and one item seen in game; the dimension path complete and survived a logout inside it; the ore baseline at its real density and mined for ten minutes; Resonance and a level curve derived from that density; persisted data versioned; then the Tome, then nodes. No world-facing task opens until Nick has seen the one before it in a client. Pure logic for later tasks (curve, classifier, node data, layout maths) may proceed while a client gate waits. The first build did the reverse and shipped a Vault with almost no ore in it for a month.

**1.0 scope: the Resonance half.** Mining, the Resonance tree, the Tome with a small Ore Memory tab, chunk loading, reset, integrations, content. 1.0 ships no advancements and no Vault Recall item (Nick, 2026-10-27: "I don't really care about advancements"). Whether it ships a guide is decided after the first-ten-minutes playtest, from what the player needed explained, such as how to build the portal; if one is needed, a guide opened by hovering an item in JEI and holding a key is preferred over a guide book. The Animus half is a separate post-1.0 epic: it is parallel to the Resonance system rather than foundational to it.

| Task | Focus | Status |
|---|---|---|
| T1 | Toolchain and an empty mod: build, unit tests, dev client and server, gametest server boot | |
| — | Docs PR: the spec corrections that must land before T3 | |
| T2 | Vault Frame and Crude Vault Igniter, registered the 26.1 way and seen in game | |
| T3 | Dimension path: lazy creation, restore at server start, portal, return position, logout inside a Vault | |
| T4 | Chunk generator, rarity classifier and the §3.1 baseline, measured | |
| T8a | Node data model and tree layout file: node and skill ids, rings, classes, marks, edges, petals, mod conditions, reserved ids, the layout tests of §6 | |
| T5 | Resonance loop: orb, pool, level curve, point award, pacing check (§4.3) | |
| T6 | Persistence: versioned team, player and chunk data | |
| T7 | Network and the Tome shell | |
| T8 | Tree layout and renderer; first node bought through the Tome | |
| T9 | Node effects, in §13 order | |
| T10 | Chunk loading: Vault Anchor, tickets, Vault Presence, Automated Extraction | |
| T11 | Reset and disband | |
| T12 | Integrations: FTB Ultimine, Mekanism by tags, dust fallback | |
| T13 | Content: recipes, loot, tags, models, textures, the full lang file | |
| T14 | Release: load test in FTB Evolution 2 (the version current when the dimension path lands at T3, re-checked at release), debug commands removed, curve re-derived from measurements, `KNOWN_ISSUES.md` | |
| — | **Post-1.0 epic:** Animus system, Disturbed Zones, Animus tree, Soul Harvest + shop, Ore Memory stats panels | deferred |

---

## 13. Implementation Checklist

Use this to track progress. Tick a line only when the work has merged and been seen working; never from memory. Every feat PR ticks its own lines.

**Everything was unticked at the restart.** The code these lines once described was removed on 2026-10-25. Each line now names a current behaviour and the filed story that builds it, as `E3-S4 (#229)`: the draft id and the issue in the current tracker. A line with more than one story is ticked when the last of them merges. A pre-restart issue is written "old #N" and is history only; its commits are in `git log` before the restart, and `docs/research/issue-harvest.md` holds what it said.

### Infrastructure
- [x] NeoForge 26.1 project scaffold (`build.gradle`, `settings.gradle`, `gradle.properties` pins, `neoforge.mods.toml`) — E1-S1 (#188)
- [x] FTB Teams declared as a mandatory dependency and the mod boots with it; FTB Ultimine `compileOnly` and optional — E1-S1 (#188)
- [x] Gametest harness, one passing and one deliberately failing gametest — E1-S2 (#193)
- [ ] Package structure created as `docs/adr/0001-toolchain-and-package-layout.md` lays it out: pure `core.*` packages with no Minecraft imports, Minecraft-facing adapters around them, and an ArchUnit test with one rule per package plus the named rules, each shown failing against a planted violation (S43) — E1-S3 (#194)
- [x] Main mod class taking the `modEventBus` — E1-S1 (#188)
- [ ] Data generators for blockstates, models, recipes, tags and loot, with a check that committed output matches — E1-S9 (#198)
- [ ] Command root `/orevault`, always registered, with only its debug subcommands behind `[debug].enable_debug_commands`; `/orevault reset vote` and `/orevault archive` are registered whatever the flag (§10); and the config screen — E3-S1 (#205)
- [ ] Tree layout file and `TreeLayout` (§6, §11), with the constants class beside it — E8a-S9 (#189), E8a-S1 (#208), E8a-S4 (#212), E8a-S3a (#239)
- [ ] Team data `SavedData`, one per team, id `orevault:team_<teamUuidNoHyphens>` (§11 SavedData Pattern, ADR 0005): the shell with `vault_generation` and `disbanded_at`, and the Vault type file `orevault_vault.dat` in each Vault folder, its name frozen as save data with the key and folder scheme (§11 SavedData Pattern, Per-Vault data) — E3-S9 (#232); `resonance_pool` and `level_reached` — E6-S1 (#241); the tree (`owned`, `fork_choice`, the respec window, pin and buyers) — E6-S3 (#251)
- [ ] `PlayerStats` nested in team data with its own `data_version` (§11) — E6-S2 (#276)
- [ ] `data_version` on every saved shape, with a fall-through migration chain and golden key-set tests (§11 SavedData Pattern) — E3-S9 (#232) first, then each story that adds a shape
- [ ] `TeamHelper`, the only class that imports FTB Teams; team size never below 1 (§9) — E3-S8 (#206)
- [ ] FTB Teams event hooks (§11): `PlayerChangedTeam` starts the player fresh in the new team (§9) — E6-S5 (#282); `TeamDeleted` sets `disbanded_at` and evacuates — E11-S6 (#289). The Tome grant listens to the vanilla login event, not FTB's party-join event, which never fires for a solo player — E7-S3 (#277)
- [ ] Per-team dimension key `orevault:vault_<teamUuidNoHyphens>`, and restore mapping a folder name back to a team (§3.1, §11) — E3-S6a (#237)

### Dimension
- [ ] Dimension types `orevault:ore_vault` and `orevault:ore_vault_expanded`: base `min_y: 0` / `height: 320`, expanded `min_y: -64` / `height: 384` — E3-S5 (#233)
- [ ] The 26.1 fields of §3.1 in both types: `ambient_light: 1.0`, `has_skylight: true`, `has_fixed_time: true` with no `timelines`, `monster_spawn_light_level: 0` and `monster_spawn_block_light_limit: 0`, and the attributes `minecraft:visual/sky_light_factor`, `minecraft:visual/ambient_light_color`, `minecraft:gameplay/bed_rule` (`can_sleep` and `can_set_spawn` never), `minecraft:gameplay/respawn_anchor_works: false` and `minecraft:gameplay/can_start_raid: false`; the midday sun seen in `runClient` — E3-S5 (#233)
- [ ] The Vault biome with empty spawner lists, which is what keeps mobs from spawning — E3-S5 (#233)
- [ ] Custom chunk generator with the §3.1 layer stack loaded from `vault_layers/<type>.json`: bedrock, stone, dirt, grass surface and 69 blocks of open air — E3-S5 (#233)
- [ ] Vault creation on the portal path only: `VaultDimensions#findOrCreate` is the one entry, and no login or team event creates a Vault (§3.1, ADR 0002) — E3-S6a (#237)
- [ ] Eager restore at server start of every Vault on disk, before anyone connects, skipping unreadable folders and archived teams (§11 Dimension-per-Team Registration) — E3-S6a (#237)
- [ ] `VaultLevels.register` is the only world-map insert and calls `server.markWorldsDirty()` (§11) — E3-S6a (#237)
- [ ] Level-unload spike for reset and disband, its result written into ADR 0002 — E3-S11 (#245)
- [ ] Ore rarity classifier over `#c:ores` from sampled placement data, at every server start (§4.2, §11, ADR 0009) — E4-S2 (#238)
- [ ] Admin override for rarity classification (`ore_classification.overrides`, §10) — E4-S2 (#238)
- [ ] Baseline ore distribution: density, rarity mix, depth and vein size (§3.1) — E4-S1 (#234), E4-S3 (#247); measured by `/orevault density` — E4-S4 (#254); mined for ten minutes — E4-S5 (#261)
- [ ] 40% stone floor, enforced by scaling every rarity's count by one factor held in the snapshot (§3.1) — E4-S3 (#247)
- [ ] Generation snapshot rebuilt on every trigger ADR 0003 names, so generation nodes change newly generated chunks (§11 Dynamic Chunk Generator) — E9-S3 (#307); each generation node in its own story below
- [ ] Per-chunk vein index in the `orevault:vein_index` chunk attachment, written at generation and kept across unloads and restarts (§11 Vein Index, ADR 0004) — E6-S4 (#209) spike, E9-S4 (#308)
- [ ] Vault Expansion: the Vault re-created under the expanded type at the next reset (§3.5) — E11-S7 (#290)

### Portal and Igniter
- [ ] Vault Frame block: hardness 1.5, blast resistance 6.0, metal sound, drops itself only to a pickaxe (§3.2) — E2-S2 (#210)
- [ ] Vault Frame `minecraft:mineable/pickaxe` tag in the **`minecraft` namespace** — E2-S2 (#210)
- [ ] Vault Frame crafting recipe (8 iron + 1 redstone, one frame) — E2-S2 (#210)
- [ ] Vault Frame and Crude Vault Igniter textures at 16×16 — E2-S1 (#204)
- [ ] `VaultPortalShape` scanner as pure logic (both axes, 2×3 to 21×21, corners optional) — E3-S2 (#207)
- [ ] Ore Vault Portal block: no collision, unbreakable, `HORIZONTAL_AXIS`, four tier variants, failure sound only on a wrong frame (§3.2) — E3-S4 (#229)
- [ ] Whole-frame dissolve when a side block breaks (`updateShape`) — E3-S4 (#229)
- [ ] Crude Vault Igniter (Tier 1), with capability accessors (`entryPointCapacity`, `hasInstantTravel`, `canResetVault`) that no call site replaces with a tier-number comparison (§3.3) — E2-S3 (#219)
- [ ] Attuned, Resonant and Sovereign igniters (Tiers 2 to 4) with their recipes (§3.3) — E10-S1 (#369); textures — E13-S2b (#218)
- [ ] Tier 2: one personal entry point; Tier 3: three, chosen from a list, and instant travel. Stored as `entry_points` on the player attachment (§11) — E10-S1 (#369); list design — E10-S7 (#293)
- [ ] Tier 4: the reset button appears only for a viewer holding it (§3.5) — E11-S5 (#298); the Vault Anchor recipe takes it and returns it — E10-S3 (#373)
- [ ] Resonance Crystal item and recipe (4 Attuned raw ore + 1 Amethyst Shard, §3.3) — E9-S28 (#332); texture — E13-S2a (#185)

### Teleportation
- [ ] Overworld → Vault routing to the team's Vault (§3.2) — E3-S6a (#237)
- [ ] Vault → Overworld routing to the return position, world spawn as fallback — E3-S6a (#237)
- [ ] Return position as `return_pos` on the player attachment `orevault:player` (`copyOnDeath`), saved only after the teleport succeeds (§3.2) — E3-S6a (#237)
- [ ] Vanilla nether-style charge-up of 80 ticks with the wavy overlay, the travel sound, and an 80-tick re-entry cooldown (§3.2) — E3-S4 (#229)
- [ ] Fixed team anchor at X=0, Z=0, with safe arrival on the surface; nothing derived from Overworld coordinates (§3.1, §3.2) — E3-S6b (#244)
- [ ] Exactly one 4×5 return portal per Vault, built at the anchor and idempotent — E3-S6b (#244)
- [ ] Return portal coloured by the highest igniter tier that has entered, read back from the placed blocks — E3-S6b (#244)
- [ ] No team gate: nothing is gated on having a team (§3.2, §9) — E3-S8 (#206)
- [ ] Tier 3+ skips the charge-up and the cooldown — E10-S1 (#369)
- [ ] The `in_vault` login check: a player whose Vault is gone goes to their return position; one whose Vault was reset while they were offline goes to the team anchor (§11, ADR 0002) — E3-S6a (#237)

### Resonance System
- [ ] Resonance orb entity on a `VaultOrbEntity` base: vanilla orb sprite sized by value, tinted blue/cyan, carrying `orb_count`, the number of orbs merged into it (Hoarder's Instinct's N, §6.1) — E5-S2 (#260)
- [ ] Resonance orb spawns on ore broken by hand in a Vault — 2/5/12.5 by rarity (§4.2); unclassified blocks and machine breaks award nothing — E5-S3 (#264); ×1.75 when Tithe consumed the block — E9-S31 (#335)
- [ ] Orb floats to the nearest team member within 8 blocks, vanilla's orb radius (§4.2), server-authoritative; only members of the owning team attract or collect it — E5-S2 (#260); Resonance Magnetism raises it to 12 / 16 / 24 — E9-S39 (#347)
- [ ] Team Resonance pool, stored in thousandths (§11) — E6-S1 (#241)
- [ ] Level curve: cap 30, 5 points per level (§4.3) — E5-S1 (#240)
- [ ] `[resonance]` config block: `target_play_hours`, `curve_divisor` — E5-S1 (#240)
- [ ] Team scaling: `sum / teamSize × (1 + 0.1 × (teamSize − 1))`, with no `ASSUMED_TEAM_SIZE` anywhere (§4.2) — E5-S1 (#240)
- [ ] Skill point award at every level crossed, announced to every online member — E5-S4 (#274); the announcement becomes a toast with the Tome shell — E7-S6 (#291)
- [ ] Drop pipeline: one `BlockDropsEvent` listener with the six ordered stages and the break context (§11) — E5-S3 (#264); the accumulators and the effects view — E9-S2 (#306); the Fortune re-roll with the first Fortune node — E9-S26 (#330)
- [ ] Vault session state: trips keyed by player UUID, a per-tick guard that ends the trip of a player no longer in a Vault (a death exit fires no dimension-change event), logout ending a trip (§11) — E9-S5 (#309)
- [ ] Vein index consumers: completion detection, Twin Veins registration, player-placed ore never indexed (§11 Vein Index) — E9-S4 (#308), E9-S36b (#344)
- [ ] Refund (§4.4): connectivity rule, 3 XP levels a node, 12 a pact, 30 a keystone, outside the Vault only, team told; free undo window; free for 10 minutes after a reset (`free_respec_until`), at most once per 24 hours of server time (`free_respec_opened_at`) — E8a-S6 (#230), E8-S10 (#303), E11-S3 (#284)
- [ ] Pacing tools and session: per-minute readout, team dump and wipe — E5-S5 (#275); the log parser — E5-S9 (#280); the T5 session — E5-S7 (#287); the ledger — E5-S12 (#285)
- [ ] On-screen Resonance bar while mining, on by default, turned off by the client config `hud.show_resonance_bar` (§8, §10) — E5-S11 (#281) preview, E5-S13 (#288)

### Skill Tree — Resonance Nodes
- [ ] Node data model: skill ids, classes (small, travel, notable, keystone, pact, growth, fork parent), marks (tradeoff, exclusive, requires), reserved ids never registered (§6) — E8a-S1 (#208)
- [ ] Per-tier effect constants, one source for code, lang and tests — E8a-S2a (#235), E8a-S2b (#211), E8a-S2c (#236)
- [ ] The tree graph in the layout file, and its drawing positions — E8a-S3a (#239), E8a-S3b (#259)
- [ ] Tree graph from the layout file: adjacency, distances, tier derived from owned ids, layout hash — E8a-S4 (#212)
- [ ] Buy validation (adjacent, ring, points, exclusive partner, not hidden) and the cheapest-path buy (§4.4) — E8a-S5 (#220)
- [ ] Node refund by the connectivity rule (§4.4) — E8a-S6 (#230)
- [ ] Fork enforcement — parent inert until an option is chosen; one option at a time; options cost nothing to pick or unpick and change outside the Vault only (§4.4) — E8a-S6 (#230)
- [ ] Mod-gated nodes: requirements evaluated into the hidden set at server start and on `/reload` (§6) — E8a-S7 (#248)
- [ ] Load rules: hidden nodes dormant in the save, unknown ids dropped with the point returned, disconnected nodes pruned free (§6) — E8a-S6 (#230), E6-S3 (#251)
- [ ] Tradeoff switches per player, saved as `active_tradeoffs` on `PlayerStats`, **changed only outside the Vault** — E8a-S6 (#230), E6-S2 (#276)
- [ ] Team vote for Volatile Veins and Molten Seam: majority of current members (one of two suffices), applied only while nobody is inside, snapshot rebuilt when applied (§6.1) — E9-S18 (#323), E9-S19 (#324), E9-S3 (#307); the vote control in the Tome — E8-S10 (#303)
- [ ] Reserved node ids (§6.1) never reused and never written; no migration from pre-restart saves — E8a-S1 (#208), E6-S3 (#251)
- [ ] Debug purchase `/orevault node buy`, with Stone Memory tier 1 as the technical check — E5-S6 (#278)
- [ ] Payments and stacking rules placed by the Balancer before the node stories that use them — E9-S1a (#215), E9-S1b (#305)

> In §6.1's order, which is authoritative. The groups below are for reading only; nothing in the
> tree is grouped or gated by them, and keeping two orderings was how the code and the spec drifted
> apart in the first place.

- **Skills**
  - [ ] Vein Expansion (T1-T5) — E9-S10 (#314)
  - [ ] Stone Memory (T1-T5) — T1 E5-S6 (#278); T2 to T5 E9-S11 (#315)
  - [ ] Gravel Purge (T1) — E9-S12 (#316)
  - [ ] Miner's Constitution (T1-T5) *(T4/T5 add max health)* — E9-S13 (#317)
  - [ ] Sure Footing (T1-T2) — E9-S13 (#317)
  - [ ] Deep Breath (T1-T2) — E9-S13 (#317)
  - [ ] Novice's Luck *(growth)* — E9-S14b (#319)
  - [ ] Apprentice's Ledger *(growth)* — E9-S14b (#319)
  - [ ] Shallow Grace *(growth)* — E9-S14b (#319)
  - [ ] Guide Vein *(growth)* — E9-S14a (#318)
  - [ ] Salvager's Eye *(growth)* — E9-S14b (#319)
  - [ ] Vein Proliferation (T1-T5) — E9-S15 (#320)
  - [ ] Deep Veins (T1-T2) — E9-S15 (#320)
  - [ ] Stone Reduction (T1-T2) — E9-S15 (#320)
  - [ ] **[FORK: Vein Shape]** `vein_shaping` parent + Abundance / Vein Singularity / Stratified as free options — E9-S16 (#321)
  - [ ] Deep Habit *(breakpoint)* — E9-S17 (#322)
  - [ ] Long Delve (T1-T2) *(breakpoint, with the 60-second AFK guard)* — E9-S17 (#322)
  - [ ] Volatile Veins *(team-voted tradeoff, with the pity system)* — E9-S18 (#323)
  - [ ] Molten Seam *(team-voted tradeoff; the netherrack shell is a hard requirement, not decoration)* — E9-S19 (#324)
  - [ ] Greedy Seams *(pact, exclusive: Resonant Overload)* — E9-S20 (#325)
  - [ ] Resonant Overload *(pact, exclusive: Greedy Seams)* — E9-S20 (#325)
  - [ ] **[FORK: Ore Focus]** `ore_attunement` parent + Common / Uncommon / Rare Focus as free options — E9-S21 (#326)
  - [ ] Geode Clusters (T1-T2) — E9-S22 (#327)
  - [ ] Ancient Traces (T1-T2) — below Y=0 only, exempt from all multipliers — E9-S22 (#327)
  - [ ] Ancient Knowledge (T1-T3) — E9-S23 (#328)
  - [ ] Stonecutter's Patience (T1-T2) *(breakpoint, needs the `orevault:vault_stone` tag)* — E9-S23 (#328)
  - [ ] Calloused Hands (T1-T3) *(breakpoint, logarithmic, capped at +40%)* — E9-S23 (#328)
  - [ ] Vein Sight *(notable, before Vault Echo on every path)* — E9-S24 (#378)
  - [ ] Stonecaller *(notable)* — E9-S24 (#378)
  - [ ] Prospector's Eye *(notable)* — E9-S24 (#378)
  - [ ] Stone Curse *(tradeoff)* — E9-S25 (#329)
  - [ ] Vein Fortune (T1-T3) — E9-S26 (#330)
  - [ ] **[FORK: Yield]** `ore_working` parent (T1-T3) + Ore Doubling / Smelter's Intuition as free options — E9-S27 (#331); T4-T6, Mekanism-style tags and Ore Doubling only — E12-S4 (#371)
  - [ ] Runic Attunement (T1-T3) — Attuned ore → Resonance Crystals — E9-S28 (#332)
  - [ ] Kindred Rock (T1-T2) *(breakpoint, per player, reads `ores_mined`)* — E9-S29 (#333)
  - [ ] Highwater Mark (T1-T2) *(breakpoint, tool and armour repair)* — E9-S29 (#333)
  - [ ] Brittle Stone *(pact)* — E9-S30 (#334)
  - [ ] Vault Fever *(tradeoff; the cost is −25% Resonance)* — E9-S31 (#335)
  - [ ] Vault Presence (T1-T3) — E10-S4 (#379)
  - [ ] Automated Extraction (T1-T2) — yield only, never Resonance — E10-S5 (#380)
  - [ ] Far Reach (T1-T2) *(trailing tickets count against the ceiling)* — E10-S4 (#379)
  - [ ] Seismic Sense (T1-T3) *(notable)*: compass readout — E9-S32a (#336); the map of explored chunks — E9-S32b (#337)
  - [ ] Wanderer's Cache (T1-T2) *(exploration, loot table driven)* — E9-S33 (#338); its item `orevault:wanderers_cache`: one 16×16 texture, model and item definition in E13-S2d (#392), ordered before E9-S33; registration, creative tab and lang key in E9-S33 (#338)
  - [ ] Frontier Bonus *(exploration)* — E9-S33 (#338)
  - [ ] Pathfinder's Claim *(exploration)* — E9-S33 (#338)
  - [ ] Homeward Seam *(notable, Vault-to-Vault only)* — E9-S34a (#339)
  - [ ] The Vault Keeps It *(survival)* — E9-S34b (#340)
  - [ ] Second Wind *(survival)* — E9-S34c (#341)
  - [ ] Second Wind: Homeward *(tradeoff)* — E9-S34a (#339)
  - [ ] Vault's Blessing / Vault's Purity *(exclusive pair)* — E9-S35 (#342)
  - [ ] Tithe *(tradeoff)* — E9-S31 (#335)
  - [ ] Vault Echo (T1-T3) — E9-S36a (#343)
  - [ ] Echo Chamber *(notable)* — E9-S36a (#343)
  - [ ] Twin Veins (T1-T3) — E9-S36b (#344)
  - [ ] Last Ore *(completion)* — E9-S37a (#345)
  - [ ] Clean Cut *(completion)* — E9-S37a (#345)
  - [ ] Vein Discipline *(completion; the streak decays by 3, never resets to 0)* — E9-S37a (#345)
  - [ ] Chapter's End *(notable, completion)* — E9-S37b (#383)
  - [ ] Deep Harvest *(notable)* — E9-S38 (#346)
  - [ ] **[FORK: Orb Collection]** `resonant_draw` parent + Resonance Magnetism / Hoarder's Instinct as free options — E9-S39 (#347)
  - [ ] Ultimine Expansion (T1-T3) *(requires FTB Ultimine)* — E12-S3 (#374)
  - [ ] Ultimine Safety (T1-T2) *(requires FTB Ultimine)* — E12-S3 (#374)
  - [ ] Volatile Veins: Ultimine Gambit *(tradeoff, requires FTB Ultimine)* — E12-S3 (#374)
- **Travel stats** (§6.1)
  - [ ] Attribute stats as transient modifiers, recomputed on login, respawn, dimension change, team
        change, buy and refund: move speed, Vault reach, safe fall, armour, Mason's Reach; max health as a saved modifier with a fixed id — E9-S6 (#310)
  - [ ] Mining speed and Resonance gain through the §11 accumulators, not attributes — E9-S9 (#312)
  - [ ] Vanilla XP from ore (Bonus stage) — E9-S9 (#312)
  - [ ] Tool care, Kit Care (tool wear hook, ADR 0008) — E9-S7 (#311)
  - [ ] Tool charge through `Capabilities.Energy.ITEM`, with the Tool care fallback — E9-S8 (#313)
  - [ ] Travel totals panel in the Tome (§8) — E8-S8 (#300)
- **Flavour notables** (§6.1)
  - [ ] Spelunker's Stride — E9-S40 (#348)
  - [ ] Landscaper's Swing *(outside the Vault)* — E9-S40 (#348)
  - [ ] Seam Courier — E9-S41 (#349)
  - [ ] Kiln Touch *(tradeoff)* — E9-S42 (#350)
  - [ ] Wayfarer's Lungs *(requires Paraglider)* — E12-S5 (#372)
  - [ ] Vault-Forged — E9-S43 (#351)
  - [ ] Resonant Dynamo *(requires an item with energy storage)* — E9-S45 (#352)
  - [ ] Hammer and Paxel Affinity *(requires a filled hammer or paxel tag)* — E9-S45 (#352)
  - [ ] Harvester's Lot *(outside the Vault)* — E9-S42 (#350)
  - [ ] Tempered *(tool wear hook)* — E9-S44 (#355)
  - [ ] Bookseam — E9-S42 (#350)
  - [ ] Steady Chain *(requires FTB Ultimine)* — E12-S3 (#374)
  - [ ] Delver's Lungs — E9-S40 (#348)
  - [ ] Vault Smith *(leaf off Vault-Forged)* — E9-S43 (#351)
  - [ ] Ore Imprint — E9-S43 (#351)
  - [ ] Gem Seam *(requires an Apotheosis gem loot table)* — E12-S6 (#375)
  - [ ] Unyielding Edge *(tool wear hook)* — E9-S44 (#355)
  - [ ] Resonant Trait *(requires Hephaestus Tools; trait schema read first)* — E12-S5 (#372)
  - [ ] Blast-Wise — E9-S40 (#348)
  - [ ] Budding Seam *(requires GeOre)* — E12-S5 (#372)
  - [ ] Ascending Edge *(ladder built when tags load; `orevault:vault_tool` component; Hephaestus and energy tools excluded)* — E9-S46 (#356)
- **Keystones**
  - [ ] Vault Expansion — E11-S7 (#290)
  - [ ] Full Spectrum — E9-S47 (#353)
  - [ ] Silent Stone *(0.30 a second per active member below Y=0 × (1 + the member's counted Resonance bonuses, no penalties), summed and team-scaled as ore (§4.2), a constant)* — E9-S48 (#354)
  - [ ] Resonant Symbiosis — E9-S49 (#357)
  - [ ] Bedrock Communion *(penalty lifts at Y=246, speed not damage)* — E9-S50 (#358)
  - [ ] Long Arm — E9-S51 (#359)
  - [ ] Glass Edge *(tool wear hook)* — E9-S51 (#359)
  - [ ] Galvanic Seam *(requires an item with energy storage)* — E9-S52 (#360)
  - [ ] Echoing Edge — E9-S53 (#361)
- **How the player knows each node worked** (§8) — E9-S58 (#221), the previews E9-S57 (#292), vein outlines E9-S56 (#362); playtests E9-S59 (#363) and E9-S60 to E9-S66 (#364 to #368, #384, #387)

### Deferred to the post-1.0 Animus epic (E15, #177; placeholder E15-S1, #192)
- [ ] Animus orb entity, pool, level track, skill point award — E15-S1 (#192)
- [ ] Animus tree: Zone Frequency / Pack Size / Radius, Mob Diversity, Reaper's Claim, Corrupted Veins, Plunderer's Share, Animus Amplifier — E15-S1 (#192)
- [ ] Soul Harvest keystone + the shop that gives it a sink — E15-S1 (#192)
- [ ] Disturbed Zone block, spherical spawn logic, zone particles, mob spawning, Animus drops — E15-S1 (#192)
- [ ] Disturbed Zone mob tagging (mobs stay "zone mobs" after wandering out) — E15-S1 (#192)
- [ ] Disturbed Zone Unlock node (re-enters the Resonance tree with this epic) — E15-S1 (#192)
- [ ] `disturbed_zones.max_zones_per_team` config, added with its reader (§10) — E15-S1 (#192)
- [ ] Ore Memory stats panels — team/player stats, trophies (cosmetic). The small 1.0 tab is under UI below — E15-S1 (#192)

### UI — Tome of the Deep Seam
- [ ] Network channel: one channel on the mod bus with the payloads, size bounds, triggers and refusal keys of ADR 0006 (`docs/adr/0006-network-channel-and-tome-sync.md`) — intent-only serverbound payloads (buy, path buy, refund, fork, tradeoff, generation vote, pin, reset, entry point), team progress and tree state clientbound, the layout hash checked at join (§11 Network and Sync) — E7-S2a (#255), E7-S2b (#262)
- [ ] Client entrypoint (`@Mod(dist = Dist.CLIENT)`) and client packet handlers; client team state empty until the first sync — E7-S1 (#265)
- [ ] Tome of the Deep Seam item: granted once on first login (`tome_granted`), recipe cobblestone and a book, kept out of the offhand, right-click opens the screen (§8 Item) — E7-S3 (#277)
- [ ] Tome art: window, tab icons, top bar, progress bar, Survey panel sprites, the item — E7-S4 (#213)
- [ ] Tome screen shell on vanilla `TabNavigationBar`/`TabManager` with **two tabs in 1.0**, Resonance Tree and Ore Memory; the Animus tab arrives with the post-1.0 epic (§8 Item) — E7-S5 (#286) preview, E7-S6 (#291)
- [ ] Tome interface strings and refusal reasons — E7-S8 (#190)
- [ ] Team Resonance level bar and skill point display in the Tome header: level, unspent points, a progress bar with the gain segment, the pool on hover; drawn only after the first sync (§4.1, §8) — E7-S6 (#291)
- [ ] Resonance tree tab — the Path of Exile renderer drawn from the layout file, opaque page, edges ending at node borders, zoom 0.2× to 1.5× anchored on the pointer (§8) — E8-S1 (#252) preview tool, E8-S5 (#283) Nick's yes, E8-S7 (#297)
- [ ] Node frames per class and the badge sheet — E8-S2 (#256); node icons — E8-S3a (#263), E8-S3b (#269), E8-S3c (#270), E8-S3d (#271), E8-S4a (#272), E8-S4b (#273)
- [ ] Node states (§8): owned, buyable and the three locked states, never told apart by colour alone; class shown by frame in every state, keystones distinct from small nodes and notables — E8-S7 (#297)
- [ ] Mod-gated nodes not drawn; they sit on dead ends, so the layout leaves no gap (§6) — E8-S7 (#297)
- [ ] Tooltip in §8's order: the current tier plus a dimmer "Next:" line from the constants, the vote state of a team-voted tradeoff, the refusal reason, who last bought it — E8-S8 (#300)
- [ ] Search box (Ctrl+F), hover path and copy highlighting, faint level rings, travel totals panel (§8) — E8-S8 (#300)
- [ ] Buy on click, shift-click path buy with one confirmation, the shared pin (§8) — E8-S9 (#302)
- [ ] Refund, fork petals and tradeoff switches from the Tome; inside the Vault the switch, the swap and the fork-parent refund show disabled with "Leave the Vault to change this"; an unspecialised fork parent is drawn inert (§8) — E8-S10 (#303)
- [ ] Node names and descriptions for every node, fork option and travel stat, numbers filled from the constants — E8-S6a (#214), E8-S6b (#242)
- [ ] Ore Memory tab, small for 1.0: Survey panel (depth gauge with band edges, the death marker `last_vault_death`, Seismic Sense's slot) and the reset button (§8 Tab 3) — E7-S7 (#295)
- [ ] Survey panel line: whether the current chunk was generated with the team's current generation nodes (§8 Tab 3) — E7-S7 (#295), E9-S3 (#307)
- [ ] Seismic Sense ore-density readout, gated on the node — E9-S32a (#336)
- [ ] Vault reset button, shown only to a viewer holding a Tier 4 igniter — E11-S5 (#298)
- [ ] Reset vote: toast and clickable chat line for other online members, vote screen opened from the line, never by itself (§3.5) — E11-S2 (#294) design, E11-S5 (#298)
- [ ] Reset countdown warning — E11-S2 (#294), E11-S5 (#298)
- [ ] Reset count in the generation seed (`vault_generation`, §3.1) — E3-S5 (#233) seeds from the team, E9-S3 (#307), E11-S3 (#284)

### Chunk Loading
- [ ] Vault Anchor block, placeable inside the Vault only — E10-S3 (#373); texture — E10-S2 (#216)
- [ ] Vault Anchor crafting recipe, returning the Tier 4 igniter — E10-S3 (#373)
- [ ] `TicketController` on the mod bus: ticket on placement, removed on removal, validated on load — E10-S3 (#373)
- [ ] A restored Vault's saved tickets reactivated by the restore pass, so an Anchor keeps loading its chunk after a restart (§3.4, ADR 0002); checked by setting an Anchor, restarting with nobody joining, and seeing a furnace in the anchored chunk advance — E10-S3 (#373)
- [ ] Ticket ceiling per team, the lower of the Vault Presence node and the config — E10-S3 (#373), E10-S4 (#379)
- [ ] Admin config hard ceiling `max_loaded_chunks_per_team` and `vault_presence_enabled` — E10-S3 (#373)

### Reset and Disband
- [ ] Reset vote state machine in `core.reset` (§3.5, ADR 0007) — E11-S1 (#266)
- [ ] Reset execution: countdown, evacuation, unload or the next-start fallback, delete, `vault_generation` + 1, re-registration, players to the team anchor, the respec window (§3.5) — E11-S3 (#284)
- [ ] Export chunk data before reset, after storage is closed; a failed copy abandons the reset (§3.5) — E11-S4 (#267)
- [ ] Disband archives the team's Vault folder and data file at the next start, never deleting while the server runs; admin purge (§3.1) — E11-S6 (#289)
- [ ] Reset, vote, countdown, disband and archive text — E11-S9 (#268)

### Integrations
- [ ] Integration names read from the 26.1 jars — E12-S1 (#217)
- [ ] FTB Ultimine detection and its own integration package, booted with and without the jar — E12-S2 (#370)
- [ ] Mekanism-tier outputs found through `c:clumps/`, `c:shards/`, `c:crystals/` item tags, no reflection; Ore Working tiers 4 to 6 hidden when the tags are empty — E12-S4 (#371)
- [ ] Generic dust found through `c:dusts/` tags — E9-S27 (#331), E12-S4 (#371)
- [ ] Ore Doubling fallback chain (Mekanism tags → dust → raw ore) — E12-S4 (#371)

### Config
- [ ] `orevault-server.toml` generated with defaults; each value added with its reader by the story that uses it (`CLAUDE.md` rule 7), the first being `[debug]` — E3-S1 (#205)
- [ ] `vault_presence_enabled` toggle — E10-S3 (#373)
- [ ] `max_loaded_chunks_per_team` ceiling — E10-S3 (#373)
- [ ] `ore_classification.overrides` map — E4-S2 (#238)
- [ ] `allow_backup_on_reset` toggle — E11-S4 (#267)
- [ ] Client config `hud.show_resonance_bar` — E5-S13 (#288)

### Polish
- [ ] Portal activation particles — E13-S5 (#376)
- [ ] Teleport particles — E13-S5 (#376)
- [ ] Vault Echo Resonance burst visual — E13-S5 (#376)
- [ ] Twin Veins trigger flash — E13-S5 (#376)
- [ ] Volatile Veins vein-disappearance sound and particle — E9-S18 (#323) working version, E13-S5 (#376)
- [ ] All crafting recipes, through the data generators — E1-S9 (#198) and the story of each item
- [ ] `en_us.json` complete against the lang coverage tests — E1-S11 (#203), E13-S3 (#257)
- [ ] Mod logo as `logoFile` in `neoforge.mods.toml`, not `pack.png` — E13-S2c (#191)
- [ ] Block and item textures — **16×16, as vanilla** (Nick, 2026-10-26, replacing a 64×64 rule), checked by the texture size test — E1-S8 (#197).
      Each texture uses its sixteen pixels to read clearly as what it is at slot size. GUI art is
      drawn at one texel per GUI unit. It applies to anything shipped under
      `assets/orevault/textures/`. It does **not** apply to vanilla sprites the mod borrows — the Resonance orb tints
      `minecraft:textures/entity/experience/experience_orb.png` and ships no texture of its own,
      and re-sizing a vanilla sprite is not ours to do
- [ ] Tome node art — craggly, stained-paper frames per node class, readable at every GUI scale, the paper a frame and a wash, never a busy fill behind text; one icon per skill and per travel stat (§8) — E8-S2 (#256) and the icon stories under UI
- [ ] Translucent portal strip, one near-white animated texture, tinted per tier in four colours (§3.2) — E3-S3 (#181)
- [ ] Blockstate and model JSONs, through the data generators — E1-S9 (#198)
- [ ] In-game guide, only if the first-ten-minutes playtest shows one is needed, preferring a hover-in-JEI-and-hold-a-key guide to a book (§12) — E9-S59 (#363) decides, E13-S7 (#377)

### Not in 1.0
- Advancements (Nick, 2026-10-27: "I don't really care about advancements"; old draft E13-S6, dropped)
- A Vault Recall item (not asked for; old draft E13-S8, dropped)
- The Animus half, above

### Release (E14, #176)
- [ ] Load test in FTB Evolution 2 at the version pinned when the dimension path landed — E3-S12 (#246) smoke test, E14-S1 (#382)
- [ ] Release-candidate playtest — E14-S2 (#389)
- [ ] Level curve re-derived from playtest data — E14-S3 (#391)
- [ ] Debug commands and instrumentation removed — E14-S4 (#386)
- [ ] `KNOWN_ISSUES.md`, §12 and §13 synced with what shipped — E14-S5 (#388)
- [ ] Release-jar smoke test — E14-S6 (#390)

---

*End of Ore Vault Design and Specification Document v1.2*
