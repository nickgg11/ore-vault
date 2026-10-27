# 0008. Tool wear through one mixin on `ItemStack`

Status: accepted, 2026-10-27 (E0-S11, #226), with three checks E9-S7 (#311) runs at first load, listed under
Consequences. Proposed 2026-10-26 from the Architect's ruling in the skill tree thread
(`architect-tool-upgrades.md` question 4). Spec: §3.4 (what a hand break is), §6.1 Travel stats and Flavour
notables, §11 Tool Wear Hook. Sources: `docs/research/neoforge-26.1-sources.md` "ItemStack#hurtAndBreak and
processDurabilityChange" (E1-S5, #195). Folds in ADR 0001's `core.wear.WearScope` and ADR 0003's
`TeamEffects`.

## Context

Eight nodes change how much durability an item loses: Tool care and the Tool charge fallback (travel
stats), Kit Care (travel, outside the Vault), Hammer and Paxel Affinity (+10% to the care chance), Tempered,
Unyielding Edge, Glass Edge and Galvanic Seam. They are team-owned nodes, so whatever applies them must read
the team's tree for the player holding the item.

In NeoForge 26.1 no event is posted when an item loses durability. `EventHooks` only has
`onPlayerDestroyItem`, after the item is gone. On a block break the tool is damaged in `mineBlock`, before
`BlockDropsEvent` fires (`ServerPlayerGameMode.java#destroyBlock`, lines 271 to 301).

### What the 26.1.2.95 sources say

Read by the Developer from `neoforge-26.1.2.95-sources.jar`, the NeoForge-patched Minecraft that NeoGradle
wrote on Nick's PC, copied from there on 2026-10-27 and read in a cloud session (provenance in the research
file). `net/minecraft/world/item/ItemStack.java`:

```java
473    public void hurtAndBreak(int amount, ServerLevel level, @Nullable LivingEntity player, Consumer<Item> onBreak) {
474        amount = getItem().damageItem(this, amount, player, onBreak);
475        int newAmount = this.processDurabilityChange(amount, level, player);
476        if (newAmount != 0) {
477            this.applyDamage(this.getDamageValue() + newAmount, player, onBreak);
478        }
479    }

485    private int processDurabilityChange(int amount, ServerLevel level, @Nullable LivingEntity player) {
486        if (!this.isDamageableItem()) {
487            return 0;
488        } else if (player != null && player.hasInfiniteMaterials()) {
489            return 0;
490        } else {
491            return amount > 0 ? EnchantmentHelper.processDurabilityChange(level, this, amount) : amount;
492        }
493    }

512    public void hurtWithoutBreaking(int amount, Player player) {
513        if (player instanceof ServerPlayer serverPlayer) {
514            int newAmount = this.processDurabilityChange(amount, serverPlayer.level(), serverPlayer);
515            if (newAmount == 0) {
516                return;
517            }
518
519            int newDamage = Math.min(this.getDamageValue() + newAmount, this.getMaxDamage() - 1);
520            this.applyDamage(newDamage, serverPlayer, i -> {});
521        }
522    }
```

Descriptors, derived from those signatures:

- `hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V`
- `processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)I` (private)

`ItemStack` has four `hurtAndBreak` overloads and two private `processDurabilityChange` overloads. The
`ServerPlayer` overload of each (lines 469 and 481) only casts and delegates to the `LivingEntity` one, so a
target that names the `LivingEntity` descriptor sees each call exactly once, and a bare method name would be
ambiguous.

Local slots of `hurtAndBreak`, from the signature and the JVM's rules for an instance method: 0 `this`,
1 `amount`, 2 `level`, 3 `player`, 4 `onBreak`. These are fixed. The one declared local, `newAmount`, is
expected at slot 5 but not confirmed (no `javap` was run). This record uses no local at all, so slot 5 does
not matter (Decision, injection form).

Two paths reach `processDurabilityChange`:

- `hurtAndBreak` (line 475). Every slot-based wear funnels into it: `hurtAndBreak(int, LivingEntity,
  EquipmentSlot)` (line 530), the block break through `Item#mineBlock` (`Item.java` line 280), armour
  through `LivingEntity.java` line 1924, a weapon hit at `ItemStack.java` line 588. Dispensers call it with a
  `null` entity.
- `hurtWithoutBreaking` (line 514), whose only caller is `TridentItem#releaseUsing` (`TridentItem.java` line
  81). That line runs on every trident release that gets past the hold-time and riptide-in-water checks,
  before the branch that throws it, so it is the wear for both a throw and a riptide launch, not riptide
  only. It skips `hurtAndBreak` and `damageItem`, and clamps damage below the maximum itself.

Unbreaking rolls per point inside `EnchantmentHelper.processDurabilityChange` (`RemoveBinomial#process`), so
whatever runs before line 475 is seen by Unbreaking and whatever changes line 475's result is not.

## Decision

One mixin class, `com.orevault.orevault.mixin.ItemStackWearMixin`, with two injections into `ItemStack`,
calling one pure class, `core.wear.WearRules`. Both injections use plain Mixin. Neither uses a local by
index or ordinal, and neither needs MixinExtras.

### (a) Where the hook sits

The multiplier stays at the head of `hurtAndBreak`. The per-point rolls and the clamp move to the return of
the private `processDurabilityChange`.

- Head of `hurtAndBreak(I, ServerLevel, LivingEntity, Consumer)V`: Glass Edge ×3, and Galvanic Seam ×2 on a
  tool with no energy storage. This runs before `damageItem` (line 474) and before Unbreaking, so
  Unbreaking still rolls per point as in vanilla, and an energy tool that pays in FE through `damageItem`
  sees the same amount. `processDurabilityChange` runs after `damageItem`, so the multiplier cannot move
  there without changing what an FE tool pays.
- Return of `processDurabilityChange(I, ServerLevel, LivingEntity)I`: when the returned amount is above 0,
  each point rolls once against one summed care chance (Tool care, the Charge fallback and Hammer and Paxel
  Affinity's +10%, at most 80%), then once on its own for Tempered, as the Balancer specified; then
  Unyielding Edge clamps the result so damage never reaches the maximum. Outside a Vault the only roll is
  Kit Care's. This is the same point in the order as before, "after vanilla's durability processing", for
  every `hurtAndBreak` call, since line 475 is the only use of the result there.

Why the move: §6.1 Kit Care reads "a point of durability lost by a held item or worn armour, from any
cause", and a trident in the hand is a held item. Left on `hurtAndBreak`, Kit Care would skip trident wear
and the spec would need an exception for one item. Moved, the hook covers both callers with the same number
of injections, and the spec stays as written. Nothing else changes: the break-only nodes cannot act on a
trident release, because their scope is only set inside a hand break (below), and `hurtWithoutBreaking`
already clamps, so Unyielding Edge is never reached on that path.

`processDurabilityChange` returns 0 early for an item that is not damageable and for a player with infinite
materials, and returns a non-positive amount unchanged. The handler does nothing unless the returned value
is above 0, so creative players and repairs are untouched.

### (b) Injection form

```java
@Mixin(ItemStack.class)
abstract class ItemStackWearMixin {

    @ModifyVariable(
        method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true)
    private int orevault$scaleWear(int amount, int amountArg, ServerLevel level,
                                   @Nullable LivingEntity entity, Consumer<Item> onBreak) { ... }

    @Inject(
        method = "processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)I",
        at = @At("RETURN"), cancellable = true)
    private void orevault$rollCare(int amount, ServerLevel level, @Nullable LivingEntity entity,
                                   CallbackInfoReturnable<Integer> cir) { ... }
}
```

This is a sketch of the shape for E9-S7, not code to copy.

- `@ModifyVariable` with `argsOnly = true` and no `ordinal`, `index` or `name` runs in Mixin's implicit
  mode: it captures the one argument of the handler's type. `amount` is the only `int` argument of the
  target, so the capture is unambiguous, and if a later version adds a second `int` argument, Mixin throws
  `InvalidInjectionException` at load instead of guessing. The handler appends the target's arguments after
  the captured value to get the entity and level.
- `@Inject` at `RETURN` reads the value being returned and replaces it with `setReturnValue`, which cancels
  the callback and therefore needs `cancellable = true`. It captures only the target's arguments, never its
  locals.
- Upstream Mixin documents both: `ModifyVariable`'s javadoc ("implicit mode", and "the callback signature
  can optionally include the target method arguments by simply appending them to the handler method
  signature") and `CallbackInfoReturnable#setReturnValue` calling `cancel()`
  (`SpongePowered/Mixin`, `src/main/java/org/spongepowered/asm/mixin/injection/ModifyVariable.java` and
  `.../callback/CallbackInfoReturnable.java`, master branch, read 2026-10-27). The Mixin build that NeoForge
  26.1.2.95 ships was not read; it is first-load check 2.

No `javap` check is needed: nothing reads `newAmount` or any other local, and the argument slots are fixed
by the signature. The research file's item 9 closes as not needed.

### (c) MixinExtras

Not required. `@ModifyExpressionValue` on the `processDurabilityChange` call inside `hurtAndBreak` would
have done the second injection, but it would see only the `hurtAndBreak` path, not the trident, and the
`RETURN` injection does the job in plain Mixin. Whether NeoForge 26.1.2.95 bundles MixinExtras no longer
affects this record; the research file's item 7 closes as not needed. If a later ticket wants MixinExtras,
it checks presence then.

### Scope: `WearScope` and `TeamEffects`

The mixin reads `core.wear.WearScope` (ADR 0001) and nothing else of Ore Vault's. `WearScope` is a
main-thread table keyed by player UUID. `mining` fills it from `TeamEffects`, the main-thread team effects
view of ADR 0003, which replaces this record's earlier "main-thread team snapshot". Because `mining` resolves
everything that needs Minecraft or NeoForge (tags, energy capability, per-tool counts) when it fills an
entry, the values the mixin passes on are plain numbers:

- Break scope. When `BreakBlockEvent` passes for a hand break (§3.4) in a Vault, before `mineBlock` damages
  the tool, `mining` sets the player's break entry: the summed care chance (Hammer and Paxel Affinity
  included only if the held tool is in its tags), Tempered's chance for that tool, whether Unyielding Edge
  is owned, and the head multiplier (Glass Edge's ×3 and, if the tool has no energy storage, Galvanic Seam's
  ×2). It clears the entry when the break finishes, and at the latest at the end of the server tick, so a
  cancelled break or an exception cannot leave it set.
- Kit Care. At the end of each server tick `mining` sets each online real player's Kit Care tier from
  `TeamEffects` (`WearRules` turns it into a chance with the `core.tree` constants), or 0 while the player
  is in a Vault, and removes it at logout. This replaces a list of
  refresh triggers (purchase, refund, dimension change, respawn) with one place; the cost is a field read
  per online player per tick, and the worst case is one tick of stale scope after a change.

The mixin's own work is limited to the checks that need Minecraft objects, then a call into `WearRules`:

1. The entity is a `ServerPlayer` and is the instance `level.getServer().getPlayerList().getPlayer(uuid)`
   returns (`PlayerList.java` line 841, `ServerLevel.java` line 1321). This is §3.4's player-list check; a
   fake player that shares its owner's UUID fails it, and a `null` entity (a dispenser) fails it. Fail:
   vanilla wear.
2. Look up the `WearScope` entry by UUID. Absent: vanilla wear.
3. Slot, by identity: break-scope values apply only when `this` is `entity.getItemBySlot(EquipmentSlot.MAINHAND)`;
   Kit Care applies only when `this` is the stack in `MAINHAND`, `OFFHAND` or a slot of type
   `EquipmentSlot.Type.HUMANOID_ARMOR` (`EquipmentSlot.java` lines 13 to 18).
4. At the return injection only, read `getDamageValue()` and `getMaxDamage()` for Unyielding Edge's clamp.
5. Call `WearRules` with the amount, the entry's numbers, the slot result, the damage values and a uniform
   random source wrapped in a plain Java functional type around `level.getRandom()`, so unit tests can seed
   it.

`WearRules` imports no Minecraft class and carries the unit tests: the multiplier, the per-point rolls in
order, the 80% cap, Kit Care alone outside, the clamp, and that a non-positive amount passes unchanged.

### Mixin config and `[[mixins]]` entry for E9-S7

`src/main/resources/orevault.mixins.json`:

```json
{
  "required": true,
  "package": "com.orevault.orevault.mixin",
  "compatibilityLevel": "<first-load check 1>",
  "mixins": ["ItemStackWearMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

The class goes under `mixins`, not `server` or `client`: `ItemStack` loads on both sides and the handlers
reference only common classes; on a client the `ServerLevel` paths are simply never called. `defaultRequire:
1` makes every injection in the config fail the load if it matches nothing, so a NeoForge bump that renames
or reshapes `hurtAndBreak` or `processDurabilityChange` stops the game at start instead of silently leaving
wear vanilla.

`src/main/resources/META-INF/neoforge.mods.toml`:

```toml
[[mixins]]
config = "orevault.mixins.json"
```

## Consequences

- This is Ore Vault's first mixin (ADR 0001, Mixins). The config and the `[[mixins]]` entry land with it in
  E9-S7, not at T1.
- First-load checks E9-S7 runs on Nick's PC before the PR. None of them changes the design; each fails
  loudly at load if wrong:
  1. The `[[mixins]]` key FML 26.1.2.95 accepts (`config` is expected), the `compatibilityLevel` value for
     Java 25, and whether a refmap is needed (ADR 0001 lists the same three as unverified).
  2. The Mixin build NeoForge 26.1.2.95 ships supports target-argument capture on `@ModifyVariable` and
     `cancellable` on a `RETURN` injection, as upstream Mixin documents. If the first were missing, the
     head handler could not see the entity, and break scope would instead be looked up by the main-hand
     stack `mining` registered, by identity, so the handler needs only `this`. That would be an amendment
     to this record, not a new one.
  3. The game log at load shows both injections applied, with `defaultRequire: 1` in force (proved once by
     pointing one injection at a wrong descriptor and seeing the load fail, then restoring it).
- A gametest breaks a block with each break-scope node owned and pins the order: multiplier before
  Unbreaking, care then Tempered, clamp last. The gametest's player must pass the player-list check in
  step 1; if the gametest framework cannot provide one, the node effects are a `runClient` playtest step and
  the gametest covers only that vanilla wear is unchanged with no scope set. Kit Care on a trident (a throw
  and a riptide launch, outside the Vault) is a playtest step.
- FTB Ultimine's `PREVENT_TOOL_BREAK` setting stops chains near the end of durability before Unyielding
  Edge's clamp matters; the gametest or playtest covers the two together.
- Unyielding Edge's half speed is not part of this hook; it is a `MiningSpeedModifiers` entry read in
  `BreakSpeed` (§11).
- The break-only nodes cannot touch combat, a flint and steel, a trident or any wear that is not a hand
  break, because their entry exists only around one, and only for the main-hand stack.
- Kit Care covers every durability loss of a held or worn item outside a Vault, including a trident's,
  which is what §6.1 says. It does not cover a dispenser or a machine, since neither is a listed player.
- A NeoForge bump re-runs the three first-load checks and the gametest (ADR 0001).

## Alternatives rejected

- A NeoForge event: none exists on this path.
- The whole hook on `hurtAndBreak` (the proposed form): misses trident wear, so Kit Care's "any cause" would
  need a spec exception, for no saving.
- The whole hook on `processDurabilityChange`, multiplier included: it runs after `damageItem`, so an FE tool
  would pay its energy before Glass Edge or Galvanic Seam applied.
- A third injection into `hurtWithoutBreaking`: the same coverage as the `RETURN` injection with one more
  target to keep matched.
- `@ModifyVariable` on `newAmount` after its store, or any capture by index or ordinal: needs the local slot,
  which was not confirmed and may differ between the dev and production class files.
- `@ModifyExpressionValue` (MixinExtras): sees only the `hurtAndBreak` call, and adds a dependency whose
  presence in NeoForge 26.1.2.95 is unverified.
- An enchantment `minecraft:item_damage` effect: `Enchantment#modifyDurabilityChange(ServerLevel, int,
  ItemStack, MutableFloat)` (`Enchantment.java` line 235) receives no entity, so it cannot find the team; it
  runs only for an enchantment the item carries, and would show as one on every tool.
- Giving durability back after the break: the tool has already broken in `mineBlock` before
  `BlockDropsEvent`, so Unyielding Edge cannot be built this way, and Glass Edge's extra cost would skip
  Unbreaking.
- `IItemExtension#damageItem`: a per-item override, usable only on items Ore Vault registers.
- Keying break scope by the stack instead of the player: the slot identity check in the mixin already ties
  it to the main-hand stack, and a UUID key is what ADR 0001 defined.

## Spec sections changed

§11 Tool Wear Hook: the target becomes two injection points, trident wear is covered, the scope comes from
`WearScope`, and the closing paragraph names the config and `defaultRequire: 1` instead of "local indices".
§6.1 Kit Care is unchanged; this record implements it as written. The exact text, and the matching changes
to ADR 0001 and `docs/research/neoforge-26.1-sources.md`, are in the E0 run's `spec-changes/E0-S11.md` for
the Writer and the file owners to apply. No `DESIGN_LOG.md` entry: no decision changed.

## Reviews

Matrix row, system design: developer (buildable), reviewer (rules), qa (testable); ticket adds writer. Each
verdict lists what it checked.

- developer:
- reviewer:
- qa:
- writer:
