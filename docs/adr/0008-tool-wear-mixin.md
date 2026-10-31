# 0008. Tool wear through one mixin on `ItemStack`

Status: accepted 2026-10-27 (E0-S11, #226), after the round 1 verdicts below; three first-load checks in
E9-S7 (#311), listed under Consequences. Proposed 2026-10-26 from the Architect's ruling in the skill tree thread
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
  `.../callback/CallbackInfoReturnable.java`, master branch, read 2026-10-27). NeoForge 26.1.2.95 ships
  `sponge-mixin` 0.17.3+mixin.0.8.7 (`ivy-26.1.2.95-ng2.xml` line 152); what that build does with these two
  forms was not read, and it is first-load check 2.

No `javap` check is needed: nothing reads `newAmount` or any other local, and the argument slots are fixed
by the signature. The research file's item 9 closes as not needed.

### (c) MixinExtras

Not required. `@ModifyExpressionValue` on the `processDurabilityChange` call inside `hurtAndBreak` would
have done the second injection, but it would see only the `hurtAndBreak` path, not the trident, and the
`RETURN` injection does the job in plain Mixin. The research file's item 7 closes as not needed. NeoForge's
dependency list names `mixinextras-neoforge` 0.5.4 (`ivy-26.1.2.95-ng2.xml` line 162), for a later ticket that
wants it.

### Scope: `WearScope` and `TeamEffects`

The mixin reads `core.wear.WearScope` (ADR 0001) and nothing else of Ore Vault's. `WearScope` is a
main-thread table keyed by player UUID. `mining` fills it from `TeamEffects`, the main-thread team effects
view of ADR 0003, which replaces this record's earlier "main-thread team snapshot". Because `mining` resolves
everything that needs Minecraft or NeoForge (tags, energy capability, per-tool counts) when it fills an
entry, the values the mixin passes on are plain numbers:

- Break scope. When `BreakBlockEvent` passes for a hand break (§3.4) in a Vault, before `mineBlock` damages
  the tool, `mining` sets the player's break entry. The listener is registered on `NeoForge.EVENT_BUS` at
  `EventPriority.LOWEST` and does not receive cancelled events, so it runs after every other mod's listener
  has had the chance to cancel; a break another mod cancels never sets an entry that the next wear in the
  same tick (a sword hit) would consume. The entry holds the summed care chance (Hammer and Paxel Affinity
  included only if the held tool is in its tags), Tempered's chance for that tool, whether Unyielding Edge
  is owned, and the head multiplier (Glass Edge's ×3 and, if the tool has no energy storage, Galvanic Seam's
  ×2). It clears the entry when the break finishes, and at the latest at the end of the server tick, so an
  exception cannot leave it set.
- Kit Care. At the end of each server tick `mining` sets each online real player's Kit Care tier from
  `TeamEffects` (`WearRules` turns it into a chance with the `core.tree` constants), or 0 while the player
  is in a Vault, and removes it at logout. This replaces a list of
  refresh triggers (purchase, refund, dimension change, respawn) with one place; the cost is a field read
  per online player per tick, and the worst case is one tick of stale scope after a change.
- Server stop. `WearScope` is static state in a pure package, so `mining` clears the whole table on
  `ServerStoppedEvent`. Otherwise a singleplayer player who quits to the menu and opens another world in the
  same JVM would carry entries into it.

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

`WearRules` imports no Minecraft class and carries the unit tests, with a seeded random source: the
multiplier before the per-point rolls (so Unbreaking, which the mixin's position puts between them, sees
the multiplied amount), care then Tempered, the clamp last, the 80% cap, Kit Care alone outside, and that a
non-positive amount passes unchanged. The order is pinned here and not in a gametest, because a gametest
draws from `level.getRandom()` and cannot fix the rolls (QA, round 1).

### Mixin config and `[[mixins]]` entry for E9-S7

`src/main/resources/orevault.mixins.json`:

```json
{
  "required": true,
  "package": "com.orevault.orevault.mixin",
  "compatibilityLevel": "JAVA_25",
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

These were read on 2026-10-31 (`docs/research/neoforge-26.1-sources.md`, "Mixin entry, compatibility level and
refmap"). `config` is the one required key (`ModFileParser.java` lines 61 to 83). `JAVA_25` is a constant of the
shipped Mixin's `CompatibilityLevel`, and the mod compiles for Java 25. No refmap: 26.1 is not obfuscated, so
runtime names are compile-time names, and NeoForge's own `neoforge.mixins.json` has no `refmap` key.

One trap: `ModFileParser` catches any exception reading the entry, logs "Failed to load mixin configs from mod
file" and returns an empty list (lines 80 to 82). A malformed entry loads no mixins and does not crash, and
`defaultRequire: 1` cannot catch that because nothing runs. The gametest below is the guard.

All of this is read, not run. The mixin config is unverified in a running game until E9-S7 (#311) loads it.

## Consequences

- This is Ore Vault's first mixin (ADR 0001, Mixins). The config and the `[[mixins]]` entry land with it in
  E9-S7, not at T1.
- First-load checks E9-S7 runs on Nick's PC before the PR. None of them changes the design; each fails
  loudly at load if wrong, except a malformed `[[mixins]]` entry (above), which check 3 and the gametest catch:
  1. The config loads as written above: the `config` key, `compatibilityLevel` `JAVA_25`, no refmap. All three
     are answered from source; a read cannot show the first load.
  2. The Mixin build NeoForge 26.1.2.95 ships supports target-argument capture on `@ModifyVariable` and
     `cancellable` on a `RETURN` injection, as upstream Mixin documents. If the first were missing, the
     head handler could not see the entity, and break scope would instead be looked up by the main-hand
     stack `mining` registered, by identity, so the handler needs only `this`. That would be an amendment
     to this record, not a new one.
  3. The game log at load shows both injections applied, with `defaultRequire: 1` in force (proved once by
     pointing one injection at a wrong descriptor and seeing the load fail, then restoring it).
- A gametest covers the hook end to end with deterministic cases only: Glass Edge alone on an unenchanted
  tool loses exactly 3; a tool at one below its maximum damage with Unyielding Edge owned loses nothing
  more; with no scope set a break loses exactly 1. The player is a survival `ServerPlayer` built the way
  `GameTestHelper#makeMockServerPlayerInLevel()` builds one (an anonymous `ServerPlayer` passed through
  `PlayerList#placeNewPlayer`, so it is the instance the player list holds and passes step 1), but without
  that helper's `gameMode()` override to CREATIVE: a creative player has infinite materials, and
  `processDurabilityChange` returns 0 for it (line 488), so every wear test would pass with no wear. The test
  asserts `!player.hasInfiniteMaterials()` before the break and breaks through
  `player.gameMode.destroyBlock(pos)`, so `BreakBlockEvent` fires and the scope is set by the real listener.
  The helper is deprecated for removal; if a bump removes it, the test keeps its own copy of the
  construction. The node effects are therefore a gametest, not a playtest step (Developer and QA, round 1).
- Kit Care on a trident (a throw and a riptide launch, outside the Vault) is a playtest step. `mining` logs
  the points Kit Care saves at DEBUG, so one throw is enough to see it apply rather than a count of throws
  against the tier's chance.
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
- `@ModifyExpressionValue` (MixinExtras): sees only the `hurtAndBreak` call, so it would miss the trident.
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
§6.1 Kit Care is unchanged; this record implements it as written. Applied in the same PR, with the
matching changes to ADR 0001 and `docs/research/neoforge-26.1-sources.md`. No `DESIGN_LOG.md` entry: no
decision changed.

## Reviews

Matrix row, system design: developer (buildable), reviewer (rules), qa (testable); ticket adds writer. Each
verdict lists what it checked.

Round 1, 2026-10-27: four approvals with comments, none requesting changes, so the record is accepted
without a second round; each reviewer confirms its comment landed. Each comment is marked fixed (what
changed here).

- developer: approve with comments (DEV-18, DEV-19). Checked `hurtAndBreak` and both
  `processDurabilityChange` overloads (`ItemStack.java` lines 469 to 534); `TridentItem.java` line 81;
  `ItemStack.java` line 588; `EquipmentSlot.java` lines 13 to 18; `PlayerList#getPlayer(UUID)` line 841; the
  break order in `ServerPlayerGameMode#destroyBlock` (lines 271 to 301); `BreakBlockEvent` as the 26.1 name
  (imported by `event/PortalEvents.java` at 1e7cdad, which compiled on the same pin). Found that the two
  injections need no local slot and no MixinExtras, and agreed the research file's items 7 and 9 close.
  - DEV-18, listener priority: fixed. `EventPriority.LOWEST`, cancelled events skipped.
  - DEV-19, the mock player is creative: fixed. Survival player built as the helper does; node effects are
    a gametest.
- reviewer: approve with comments (RV-17, RV-18). Checked both descriptors against the source; that the
  `ServerPlayer` overloads delegate, so each call is seen once; that `processDurabilityChange` has exactly
  two callers; no local captured; `defaultRequire: 1`; that the mixin calls only `core.wear` (ADR 0001 rule
  8); the player-list identity check (§3.4); creative and repair paths untouched.
  - RV-17, a later canceller can leave the break entry set: fixed, same change as DEV-18.
  - RV-18, `WearScope` survives a world switch in one JVM: fixed. Cleared on `ServerStoppedEvent`.
- qa: approve with comments (QA-25, QA-26). Checked the injection points against the quoted source,
  `defaultRequire`, the three first-load checks, the gametest and playtest plan.
  - QA-25, order cannot be pinned through `level.getRandom()`: fixed. Order pinned in `WearRules` unit tests
    with a seeded source; the gametest has the three deterministic cases QA listed.
  - QA-26, the gametest player: fixed, same change as DEV-19, with the `hasInfiniteMaterials()` assertion,
    `gameMode.destroyBlock`, the copy if the helper is removed, and DEBUG logging of Kit Care's saved
    points for the trident step.
- writer: approve with comments (WRITER-1, status). Checked §11 Tool Wear Hook against the record line by
  line (two injections, trident coverage, `WearScope`, the player-list check, `defaultRequire: 1`), and that
  §6.1 Kit Care's "from any cause" is unchanged and now implemented as written.
  - WRITER-1, pointer to a session file: fixed. "Applied in the same PR."
  - Status read "accepted" with an empty Reviews block: fixed. The status now says accepted after these
    verdicts, the rule ADRs 0002, 0004 and 0007 state.

Round 2, 2026-10-31, against HEAD 0c5736c, as the confirmation round 1 asked for; ruled in the Manager's
round 2 triage, with no round 3. Status unchanged: accepted 2026-10-27, with the three first-load checks still
owed to E9-S7 (#311).

- developer: approve. Checked DEV-18 (`EventPriority.LOWEST`, cancelled events skipped) and DEV-19 (the
  survival gametest player) at their lines.
- reviewer: approve. Checked RV-17 (same change as DEV-18) and RV-18 (`WearScope` cleared on
  `ServerStoppedEvent`).
- qa: approve. Checked QA-25 (order pinned in `WearRules` unit tests with a seeded source; three
  deterministic gametest cases) and QA-26 (the gametest player and its `hasInfiniteMaterials()` assertion).
- writer: approve. Checked the status line against the Reviews block and the session-folder pointer.
- architect (read across ADRs 0001 to 0009): approve; `core.wear.WearScope` and ADR 0001 named rule 8 agree.

One change made elsewhere touches this record without changing it: ADR 0001 now runs `WearScope`'s
end-of-tick clearing and the Kit Care tiers from the root `ServerTickEvent.Post` listener, after the
`vault` jobs and before the `SyncOutbox` drain (DEV2-3). The mixin and `WearRules` are unaffected.

Amended 2026-10-31 by E1-S5 (#195), from `docs/research/neoforge-26.1-sources.md`: the config's `compatibilityLevel`
is `JAVA_25`, `config` is the right key, and no refmap is needed; a malformed `[[mixins]]` entry loads nothing
without a crash; MixinExtras is on NeoForge's dependency list and still unused. First-load check 1 stays E9-S7's
(#311), since the config is unverified until it loads. The research file's open trident question was already
decided here, by the `RETURN` injection.
