# Pufferfish's Skills: should Ore Vault use it?

Studied 2026-10-25 from the jar Nick supplied, `puffish_skills-0.19.2-26.1-neoforge.jar`, by reading its
`neoforge.mods.toml`, licences, bundled config and the public signatures of its classes with `javap`. No
code was decompiled into this repo and none should be (see Licence).

## Recommendation

**Do not make Puffish the engine for the Resonance tree. Borrow its presentation model, and own the state.**

Puffish draws a good tree, and three of its ideas fix exactly the problems the old Tome had. But its data
model is per player and one-click-per-skill, and Ore Vault's tree is per team, tiered, and full of rules
Puffish has no slot for. Making it fit would mean either bending the spec around Puffish or writing a sync
layer that mirrors a team's state into every member's Puffish data, which is a new class of bug (offline
members, joins, leaves, disbands) on top of a closed-source dependency we cannot patch.

This is a Manager decision for Nick (it appears in the open-decisions list), because it changes the shape of
the work. The alternative is laid out below so it can be chosen with the costs visible.

## What it is

| | |
|---|---|
| Mod id | `puffish_skills`, display name "Pufferfish's Skills", version 0.19.2 |
| Loader | NeoForge, requires `neoforge >= 26.1.2.71` and Minecraft `>= 26.1`, both sides |
| Code licence | **All Rights Reserved** (`LICENSE.txt`) |
| Asset licence | MIT (`LICENSE-RESOURCES.txt`) |
| Uses mixins | Yes (`puffish_skills.mixins.json`) |

A skill tree is a datapack "category" under `data/<namespace>/puffish_skills/`: a `category.json` plus
`definitions.json`, `skills.json`, `connections.json` and optionally `experience.json`.

- **Definitions** (`SkillDefinitionConfig`): `title`, `description`, `extra_description`, `icon` (item,
  texture or effect), `frame` (an advancement frame, or a texture frame with one texture per state:
  `locked`, `available`, `affordable`, `unlocked`, `excluded`), `size`, `cost`, `rewards`,
  `required_skills`, `required_points`, `required_spent_points`, `required_exclusions`, `required_mods`.
- **Skills** (`SkillConfig`): an instance of a definition placed at an **explicit `x`, `y`**, optionally a
  root. Positions are authored, not computed.
- **Connections**: `normal` and `exclusive` groups, each unidirectional or bidirectional. Exclusive
  connections are how it models "pick one of these".
- **Category** (`GeneralConfig`): title, icon, tiled `background` texture, colours for connections and
  points, `starting_points`, `spent_points_limit`, `unlocked_by_default`, `exclusive_root`,
  `erase_on_death`.
- **Experience**: its own level curve (`experience_per_level`, `level_limit`) fed by built-in sources
  (`mine_block`, `break_block`, `kill_entity`, `craft_item`, and about ten more) or custom ones.
- **Rewards**: built in are `attribute`, `command`, `tag`, `scoreboard`, `points`, `dummy`. Mods register
  their own with `SkillsAPI.registerReward(id, factory)`; a reward gets `update(ctx)` with the player and how
  many times it is held, and `dispose(ctx)`.
- **Screen** (`SkillsScreen`): reuses the vanilla advancement window, pans by drag, zooms by wheel between a
  min and max scale, batches icon, frame and connection drawing, and already uses the 26.1 render-state API
  (`extractRenderState`, `GuiGraphicsExtractor`, `MouseButtonEvent`).

## Where it fits Ore Vault

| Ore Vault needs (spec) | Puffish has |
|---|---|
| Anchors gate on points spent in the tree (§6.1) | `required_spent_points` on a definition: a direct match |
| Ultimine nodes absent without Ultimine (§2, §8) | `required_mods`, so long as it hides rather than greys (unverified) |
| Exclusive pairs (§6 notation) | `exclusive` connections |
| Per-class frames (§8: small, notable, keystone, pact, ...) | A texture frame per definition with a texture per state |
| A tree that reads like a map, not a grid | Explicit `x`, `y` per node, pan and zoom |
| A custom effect per node | Custom reward types through the API |

## Where it does not

| Ore Vault needs (spec) | Puffish |
|---|---|
| **Team-shared** points, level and unlocks (§2, §4) | Every API call takes a `ServerPlayer`. State, points and experience are per player. A team tree would need a sync layer that copies every unlock to every member, including members who are offline when it happens. |
| Tiered nodes, T1 to T5 on one node (§6) | One skill is one unlock. Five tiers would be five skills in a chain, so ~70 nodes become ~200 boxes on screen, which is the "node is two pixels" problem the old tree already hit. |
| Fork parent inert until an option is picked; options cost 0, swap free, **only outside the Vault** (§6.1) | Exclusive connections forbid holding two at once, but nothing expresses "free to swap, but only in this dimension". Refunds would need to go through our code anyway. |
| Tradeoff toggles, per player, only outside the Vault (§6) | No toggle concept. |
| Growth nodes shown as Outgrown (§6, §8) | No state for "held but retired". The five states are locked, available, affordable, unlocked, excluded. |
| Anchors re-lock when points are refunded (§4.4) | Unknown; `required_spent_points` gates buying, behaviour on refund not verified. |
| Refunds cost `3 × tier cost` vanilla XP levels, free for 10 min after a reset (§4.4) | Its own reset and lock calls; the pricing would be ours to bolt on. |
| Resonance from orbs, team scaling, machine breaks never pay (§3.4, §4.2) | Its experience sources pay the breaking player directly. We would bypass them and call `addPoints` ourselves, which leaves its level bar showing something other than our level. |
| Level curve derived from tree cost (§4.3) | Its own curve. |

## Licence and dependency risk

The code is All Rights Reserved. Depending on the published jar at runtime is normal practice and fine.
Copying, decompiling into the repo, or bundling the jar inside Ore Vault is not. If it were a hard
dependency, any 26.1.x breakage in Puffish blocks Ore Vault until its author ships a fix, and its mixins
become part of every bug report we triage. Its art (MIT) can be reused with attribution if a frame or icon
is ever worth borrowing.

## What to borrow

These are the parts that answer what went wrong last time, and none needs the dependency:

1. **Positions are authored data, not computed at runtime.** The old Tome computed layout in Java and was
   rebuilt three times. If the layout is a JSON file of `x`, `y` per node, the Designer can lay it out with a
   tool, render it to a PNG outside the game, get Nick's sign-off, and the mod reads the same file. Changing
   the look stops being a code change.
2. **A frame texture per node class and per state.** This is exactly the hook §8's class treatments and the
   "craggly stained paper" frames on #69 need: `small`, `notable`, `keystone`, `pact`, `anchor`,
   `fork_parent`, `fork_option` frames, each with `locked`, `available`, `owned`, `maxed`, `outgrown`.
3. **Pan and zoom inside an advancement-style window**, with a min and max scale, so the tree is never
   squeezed onto one screen.

## The alternative, costed

Use Puffish as a **hard dependency** for the tree screen and unlock rules, keep our own team state, and
mirror it into each member's Puffish data through the API on every change and on login. What it saves: the
screen, pan and zoom, frames, connections. What it costs: the sync layer, a 5x node count from tier chains,
redesigning forks, tradeoffs and growth nodes around what Puffish can express, and a closed-source mixin mod
on the critical path. The saving looks smaller than the cost.

A middle option is an **optional integration later**: if Puffish is present, offer a Puffish category that
reflects the team tree read-only. Not worth doing before 1.0.
