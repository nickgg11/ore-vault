# 0008. Tool wear through one mixin on `ItemStack#hurtAndBreak`

Status: proposed, 2026-10-26, from the Architect's ruling in the skill tree thread (`architect-tool-upgrades.md`
question 4). Spec: §6.1 Travel stats and Flavour notables, §11 Tool Wear Hook.

## Context

Eight nodes change how much durability an item loses: Tool care and the Tool charge fallback (travel
stats), Kit Care (travel, outside the Vault), Hammer and Paxel Affinity (+10% Tool care), Tempered, Unyielding Edge, Glass Edge and Galvanic Seam.
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
- After `processDurabilityChange` returns its amount, roll each remaining point independently for Tool
  care, the Charge fallback, Kit Care and Tempered, after Unbreaking as the Balancer specified; then
  Unyielding Edge clamps the result so damage never reaches the maximum.
- The mixin only checks scope, cheapest first, and calls `WearRules`: the entity is a hand-break player
  (§3.4: a `ServerPlayer` that is not a fake player and is the one in the player list); the node's scope
  matches (in a Vault and the main-hand item for the Vault nodes; outside a Vault and any held or worn
  item for Kit Care); the team's nodes come from the main-thread team snapshot. It holds no other logic.
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
- Kit Care runs outside the Vault on armour and off-hand items, so the scope check cannot short-circuit
  on "not in a Vault" alone.

## Alternatives rejected

- A NeoForge event: none exists on this path.
- An enchantment `minecraft:item_damage` effect: it runs only for an enchantment the item actually
  carries, its context is not known to include the player (unverified for 26.1), so it cannot read the
  team's nodes, and it would show as an enchantment on every tool.
- Giving durability back after the break: the tool has already broken in `mineBlock` before
  `BlockDropsEvent`, so Unyielding Edge cannot be built this way, and Glass Edge's extra cost would skip
  Unbreaking.
- `IItemExtension#damageItem`: a per-item override, usable only on items Ore Vault registers.
