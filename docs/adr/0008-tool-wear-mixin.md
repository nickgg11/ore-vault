# 0008. Tool wear through one mixin on `ItemStack#hurtAndBreak`

Status: proposed, 2026-10-26, from the Architect's ruling in the skill tree thread (`architect-tool-upgrades.md`
question 4). Spec: §6.1 Travel stats and Flavour notables, §11 Tool Wear Hook.

## Context

Eight nodes change how much durability an item loses: Tool care and the Tool charge fallback (travel
stats), Kit Care (travel, outside the Vault), Hammer and Paxel Affinity (+10% to the care chance), Tempered, Unyielding Edge, Glass Edge and Galvanic Seam.
They are team-owned nodes, so whatever applies them must read the team's tree for the player holding the
item.

In NeoForge 26.1 no event is posted when an item loses durability. The patched
`ItemStack#hurtAndBreak(int, ServerLevel, LivingEntity, Consumer<Item>)` calls the item's own
`damageItem(...)`, then the private `processDurabilityChange` (vanilla enchantment effects such as
Unbreaking, and `hasInfiniteMaterials`), then `applyDamage`. `EventHooks` only has
`onPlayerDestroyItem`, after the item is gone. On a block break the tool is damaged in `mineBlock`,
before `BlockDropsEvent` fires.

## Decision

One Mixin into that `hurtAndBreak` overload, calling one pure `WearRules` class.

- At the head of the method, change `amount`: Glass Edge ×3, and Galvanic Seam ×2 on a tool with no
  energy storage. This runs before `damageItem` and before Unbreaking, so Unbreaking still rolls per
  point as in vanilla, and an energy tool that pays in FE through `damageItem` sees the same amount.
- After `processDurabilityChange` returns its amount, each remaining point rolls once against one
  summed care chance (Tool care, the Charge fallback and Hammer and Paxel Affinity's +10%, at most 80%),
  then once on its own for Tempered, as the Balancer specified; then Unyielding Edge clamps the result so
  damage never reaches the maximum. Outside a Vault the only roll is Kit Care's.
- Scope comes from a per-player hand-breaking-block flag. The break handler sets it when
  `BreakBlockEvent` passes for a hand break (§3.4) in a Vault, before `mineBlock` damages the tool, and
  clears it when the break finishes, and at the latest at the end of the server tick. Tool care, the
  Charge fallback, Hammer and Paxel Affinity, Tempered, Unyielding Edge, Glass Edge and Galvanic Seam act
  only while it is set and only on the main-hand item. Kit Care ignores the flag: it acts on any
  durability loss of a held item or worn armour outside a Vault, from any cause, when the entity is a
  real player (a `ServerPlayer` that is not a fake player and is the one in the player list).
- The mixin checks the flag or Kit Care's scope, reads the team's nodes from the main-thread team
  snapshot and calls `WearRules`. It holds no other logic.
- `WearRules` imports no Minecraft class and carries the unit tests.

## Consequences

- This is Ore Vault's first mixin. It needs a `[[mixins]]` entry in `neoforge.mods.toml` and a mixin
  config.
- The target is a NeoForge-added overload with a private helper. Its exact signature and local indices
  are read from the decompiled 26.1 sources on Nick's PC, not from memory, and pinned by a gametest that
  breaks a block with each node owned. A NeoForge bump that changes the method fails that gametest.
- FTB Ultimine's `PREVENT_TOOL_BREAK` setting stops chains near the end of durability before Unyielding
  Edge's clamp matters; the gametest covers the two together.
- Unyielding Edge's half speed is not part of this hook; it is a `MiningSpeedModifiers` entry read in
  `BreakSpeed` (§11).
- The break-only nodes cannot touch combat, a flint and steel or any wear that is not a hand break,
  because the flag is set only around one. The flag is per player and cleared by the end of the tick, so
  a cancelled break or an exception cannot leave it set.
- Kit Care runs outside the Vault on armour and off-hand items and on any cause of wear, so it is the one
  node that does not read the flag.

## Alternatives rejected

- A NeoForge event: none exists on this path.
- An enchantment `minecraft:item_damage` effect: it runs only for an enchantment the item actually
  carries, its context is not known to include the player (unverified for 26.1), so it cannot read the
  team's nodes, and it would show as an enchantment on every tool.
- Giving durability back after the break: the tool has already broken in `mineBlock` before
  `BlockDropsEvent`, so Unyielding Edge cannot be built this way, and Glass Edge's extra cost would skip
  Unbreaking.
- `IItemExtension#damageItem`: a per-item override, usable only on items Ore Vault registers.
