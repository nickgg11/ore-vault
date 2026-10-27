# NeoForge 26.1 source reads

E1-S5 (#195). Each section quotes the decompiled source, names the file and lines, says what it settles,
and names the stories it unblocks. Story ids and issue numbers are from the filed ticket table.

## Provenance

- Minecraft and NeoForge patches: `neoforge-26.1.2.95-sources.jar`, the pinned `neo_version=26.1.2.95`
  (`CLAUDE.md` Build). Its `version.json` reads `"id": "26.1.2"`, `"world_version": 4790`,
  `"java_version": 25`.
- The jar is the one NeoGradle wrote to `.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/26.1.2.95/`
  in Nick's old build folder (`E:\Github\Ore-Vault_2`). It was copied from Nick's PC on 2026-10-27 and read
  in a cloud session. It was not produced by a fresh E1-S1 build. If E1-S1 re-checks the pin and it moves,
  these reads are stale.
- FTB Teams: `ftb-teams-26.1.2.4-sources.jar` from the same Gradle cache, read on 2026-10-27 (copied in
  below under "FTB Teams party join and disband").
- Paths below are paths inside the jar. Line numbers are lines of the decompiled `.java` file.

### What the jar does not contain

The jar holds 26,874 entries: `net/minecraft`, `com/mojang`, `assets/minecraft` and `data/minecraft`. It is
the NeoForge-patched Minecraft only. No `net/neoforged/**` source is in it (`unzip -l` lists one entry
containing "neoforged", the directory `net/`). NeoForge's own API classes, such as `BlockDropsEvent`,
`Capabilities`, `PayloadRegistrar`, `RegisterPayloadHandlersEvent`, `AttachmentType`,
`AttachmentInternals`, `ForcedChunkManager`, `TicketController` and the `common.crafting` ingredients, are
in NeoForge's own jar, which was not copied. The `c:` tags are in that jar too.

Where a read needs one of those classes, this file quotes the patched Minecraft call site, which shows the
class and method name NeoForge exposes at that point, and marks the rest "Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these)." with the class and method that must be read. Nothing below is written from memory.

## Summary

| Read | Result | Unblocks |
| --- | --- | --- |
| `DimensionType` codec fields | Read | E0-S1 (#222), E0-S4 (#223) |
| `ItemStack#hurtAndBreak`, `processDurabilityChange` | Read; local slots need javap | E0-S11 (#226), E9-S7 (#311) |
| FTB Teams party join and disband | Read (FTB Teams jar) | E0-S18 (#178), E0-S10 (#258), E0-S4 (#223), E3-S8 (#206) |
| `ProtoChunk` attachment carry-over | Call sites read; NeoForge copy rule not read | E6-S4 (#209), E0-S6 (#224) |
| Client payload handler registration | Unverified: NeoForge sources not yet read | E0-S9 (#183) |
| `BlockDropsEvent` experience accessors | Call sites read; accessors not read | E5-S3 (#264), E9-S9 (#312) |
| GUI sprite nine-slice `.mcmeta` | Read | E7-S4 (#213) |
| Chunk ticket API; forced tickets after `createLevels` | Read (vanilla and the NeoForge hook call) | E10-S3 (#373), E0-S4 (#223) |
| `Capabilities.Energy.ITEM`, item-handler capability | Block item capability name read; energy not read | E9-S8 (#313), E9-S45 (#352), E9-S41 (#349), E12-S1 (#217) |
| Data-component ingredient | Custom-ingredient hook read; the ingredient class not read | E9-S28 (#332), E12-S1 (#217) |
| `MobEffectInstance` duration access | Read | E9-S35 (#342) |
| `FoodData.exhaustionLevel` access | Read | E9-S17 (#322), E9-S13 (#317) |
| Respawn and `PlayerChangedDimensionEvent` | Read | E0-S1 (#222), E9-S5 (#309) |

## DimensionType codec fields

Unblocks E0-S1 (#222, §3.1 shared attributes and §13 Dimension), E0-S4 (#223).

`net/minecraft/world/level/dimension/DimensionType.java` lines 109 to 135, the full direct codec:

```java
Codec.BOOL.optionalFieldOf("has_fixed_time", false).forGetter(DimensionType::hasFixedTime),
Codec.BOOL.fieldOf("has_skylight").forGetter(DimensionType::hasSkyLight),
Codec.BOOL.fieldOf("has_ceiling").forGetter(DimensionType::hasCeiling),
Codec.BOOL.fieldOf("has_ender_dragon_fight").forGetter(DimensionType::hasEnderDragonFight),
Codec.doubleRange(1.0E-5F, 3.0E7).fieldOf("coordinate_scale").forGetter(DimensionType::coordinateScale),
Codec.intRange(MIN_Y, MAX_Y).fieldOf("min_y").forGetter(DimensionType::minY),
Codec.intRange(16, Y_SIZE).fieldOf("height").forGetter(DimensionType::height),
Codec.intRange(0, Y_SIZE).fieldOf("logical_height").forGetter(DimensionType::logicalHeight),
TagKey.hashedCodec(Registries.BLOCK).fieldOf("infiniburn").forGetter(DimensionType::infiniburn),
Codec.FLOAT.fieldOf("ambient_light").forGetter(DimensionType::ambientLight),
DimensionType.MonsterSettings.CODEC.forGetter(DimensionType::monsterSettings),
DimensionType.Skybox.CODEC.optionalFieldOf("skybox", DimensionType.Skybox.OVERWORLD).forGetter(DimensionType::skybox),
CardinalLighting.Type.CODEC
    .optionalFieldOf("cardinal_light", CardinalLighting.Type.DEFAULT)
    .forGetter(DimensionType::cardinalLightType),
attributeMapCodec.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(DimensionType::attributes),
RegistryCodecs.homogeneousList(Registries.TIMELINE).optionalFieldOf("timelines", HolderSet.empty()).forGetter(DimensionType::timelines),
WorldClock.CODEC.optionalFieldOf("default_clock").forGetter(DimensionType::defaultClock)
```

Lines 159 to 166, the monster settings, inlined into the same object (it is a `MapCodec`):

```java
public record MonsterSettings(IntProvider monsterSpawnLightTest, int monsterSpawnBlockLightLimit) {
    public static final MapCodec<DimensionType.MonsterSettings> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                IntProviders.codec(0, 15).fieldOf("monster_spawn_light_level").forGetter(DimensionType.MonsterSettings::monsterSpawnLightTest),
                Codec.intRange(0, 15).fieldOf("monster_spawn_block_light_limit").forGetter(DimensionType.MonsterSettings::monsterSpawnBlockLightLimit)
```

The constructor (lines 79 to 88) throws if `height < 16`, `min_y + height > MAX_Y + 1`,
`logical_height > height`, or `height` or `min_y` is not a multiple of 16.

What is gone: there is no `fixed_time`, `natural`, `bed_works`, `respawn_anchor_works`, `has_raids`,
`ultrawarm` or `piglin_safe` field in the codec. `has_fixed_time` is a boolean, not a time.

The gameplay and visual rules moved into `attributes`, keyed by environment attribute id.
`net/minecraft/world/attribute/EnvironmentAttributes.java` registers each with
`Identifier.withDefaultNamespace(id)` (line 183); the ones §3.1 needs:

```java
EnvironmentAttribute<Integer> AMBIENT_LIGHT_COLOR = register(
    "visual/ambient_light_color", EnvironmentAttribute.builder(AttributeTypes.RGB_COLOR).defaultValue(-16777216).spatiallyInterpolated().syncable()   // lines 96-97
EnvironmentAttribute<Float> SKY_LIGHT_FACTOR = register(
    "visual/sky_light_factor",                                                                     // lines 89-90
EnvironmentAttribute<Boolean> CAN_START_RAID = register("gameplay/can_start_raid", EnvironmentAttribute.builder(AttributeTypes.BOOLEAN).defaultValue(true));   // line 122
EnvironmentAttribute<BedRule> BED_RULE = register(
    "gameplay/bed_rule", EnvironmentAttribute.builder(AttributeTypes.BED_RULE).defaultValue(BedRule.CAN_SLEEP_WHEN_DARK)   // lines 126-127
EnvironmentAttribute<Boolean> RESPAWN_ANCHOR_WORKS = register(
    "gameplay/respawn_anchor_works", EnvironmentAttribute.builder(AttributeTypes.BOOLEAN).defaultValue(false)   // lines 129-130
```

Note the defaults: `can_start_raid` defaults to `true`, so the Vault must set it to `false`;
`respawn_anchor_works` defaults to `false`; `bed_rule` defaults to "sleep when dark, always set spawn".
Other visual ids in the same file: `visual/fog_color`, `visual/sky_color`, `visual/sky_light_color`,
`visual/sun_angle`, `visual/cloud_color`, `visual/block_light_tint`.

`net/minecraft/world/attribute/BedRule.java` lines 12 to 25 and 39 to 42:

```java
public record BedRule(BedRule.Rule canSleep, BedRule.Rule canSetSpawn, boolean explodes, Optional<Component> errorMessage) {
    ...
            BedRule.Rule.CODEC.fieldOf("can_sleep").forGetter(BedRule::canSleep),
            BedRule.Rule.CODEC.fieldOf("can_set_spawn").forGetter(BedRule::canSetSpawn),
            Codec.BOOL.optionalFieldOf("explodes", false).forGetter(BedRule::explodes),
            ComponentSerialization.CODEC.optionalFieldOf("error_message").forGetter(BedRule::errorMessage)
    ...
    public static enum Rule implements StringRepresentable {
        ALWAYS("always"),
        WHEN_DARK("when_dark"),
        NEVER("never");
```

The shipped `data/minecraft/dimension_type/the_nether.json` shows the JSON shape:

```json
"attributes": {
  "minecraft:gameplay/bed_rule": { "can_set_spawn": "never", "can_sleep": "never", "explodes": true },
  "minecraft:gameplay/can_start_raid": false,
  "minecraft:gameplay/respawn_anchor_works": true,
  "minecraft:visual/ambient_light_color": "#302821",
  "minecraft:visual/sky_light_factor": 0.0,
  ...
},
"has_fixed_time": true,
"has_skylight": false,
"monster_spawn_block_light_limit": 15,
"monster_spawn_light_level": 7,
```

and `overworld_caves.json` the IntProvider form:
`"monster_spawn_light_level": { "type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0 }`.

Fixed time: `net/minecraft/world/level/Level.java` lines 399 to 405 are the only readers of `hasFixedTime()`
in the tree:

```java
public boolean isBrightOutside() {
    return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
}

public boolean isDarkOutside() {
    return !this.dimensionType().hasFixedTime() && !this.isBrightOutside();
}
```

So with `has_fixed_time: true` it is never "dark outside", and a `when_dark` bed rule never lets anyone
sleep. The sun's position is the `visual/sun_angle` attribute, animated by `timelines`. In
`data/minecraft/timeline/day.json` the `minecraft:visual/sun_angle` track has its keyframes at tick 6000
with values 360.0 and 0.0, so the default `sun_angle` of 0.0 is the midday position. A Vault type with no
`timelines` therefore holds the midday sun. That last step is an inference from the data, not a traced
render path; E0-S1 can state it and the dimension story confirms it in `runClient`.

Mob spawning: with `natural` gone, nothing in the dimension type turns spawning off. The biome's spawner
lists do. `data/minecraft/worldgen/biome/the_void.json` shows the empty form, `"spawn_costs": {}` and
`"spawners": { "ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
"underground_water_creature": [], "water_ambient": [], "water_creature": [] }`, and
`net/minecraft/world/level/biome/MobSpawnSettings.java` lines 35 and 38 read `fieldOf("spawners")` and
`fieldOf("spawn_costs")`. A NeoForge biome modifier from another mod can still add spawns to our biome; that
API is in NeoForge's jar. Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: NeoForge's biome modifier registry and the `neoforge:add_spawns` modifier, to know whether one can target `orevault:vault`.

## ItemStack#hurtAndBreak and processDurabilityChange

Unblocks E0-S11 (#226, ADR 0008) and E9-S7 (#311).

`net/minecraft/world/item/ItemStack.java` lines 469 to 534, verbatim:

```java
469    public void hurtAndBreak(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak) {
470        this.hurtAndBreak(amount, level, (LivingEntity) player, onBreak);
471    }
472
473    public void hurtAndBreak(int amount, ServerLevel level, @Nullable LivingEntity player, Consumer<Item> onBreak) {
474        amount = getItem().damageItem(this, amount, player, onBreak);
475        int newAmount = this.processDurabilityChange(amount, level, player);
476        if (newAmount != 0) {
477            this.applyDamage(this.getDamageValue() + newAmount, player, onBreak);
478        }
479    }
480
481    private int processDurabilityChange(int amount, ServerLevel level, @Nullable ServerPlayer player) {
482        return processDurabilityChange(amount, level, (LivingEntity) player);
483    }
484
485    private int processDurabilityChange(int amount, ServerLevel level, @Nullable LivingEntity player) {
486        if (!this.isDamageableItem()) {
487            return 0;
488        } else if (player != null && player.hasInfiniteMaterials()) {
489            return 0;
490        } else {
491            return amount > 0 ? EnchantmentHelper.processDurabilityChange(level, this, amount) : amount;
492        }
493    }
494
495    private void applyDamage(int newDamage, @Nullable ServerPlayer player, Consumer<Item> onBreak) {
496        applyDamage(newDamage, (LivingEntity) player, onBreak);
497    }
498
499    private void applyDamage(int newDamage, @Nullable LivingEntity player, Consumer<Item> onBreak) {
500        if (player instanceof ServerPlayer serverPlayer) {
501            CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, this, newDamage);
502        }
503
504        this.setDamageValue(newDamage);
505        if (this.isBroken()) {
506            Item item = this.getItem();
507            this.shrink(1);
508            onBreak.accept(item);
509        }
510    }
511
512    public void hurtWithoutBreaking(int amount, Player player) {
513        if (player instanceof ServerPlayer serverPlayer) {
514            int newAmount = this.processDurabilityChange(amount, serverPlayer.level(), serverPlayer);
...
524    public void hurtAndBreak(int amount, LivingEntity owner, InteractionHand hand) {
525        this.hurtAndBreak(amount, owner, hand.asEquipmentSlot());
526    }
527
528    public void hurtAndBreak(int amount, LivingEntity owner, EquipmentSlot slot) {
529        if (owner.level() instanceof ServerLevel serverLevel) {
530            this.hurtAndBreak(
531                amount, serverLevel, owner, brokenItem -> owner.onEquippedItemBroken(brokenItem, slot)
532            );
533        }
534    }
```

What this settles for ADR 0008:

- The target exists with that exact signature. JVM descriptor, derived from the quoted signature:
  `hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V`.
  `ItemStack` has four `hurtAndBreak` overloads and two private `processDurabilityChange` overloads (the
  `ServerPlayer` ones delegate), so every mixin target must carry the full descriptor, never the bare name.
- Order inside it: `damageItem` (line 474), then `processDurabilityChange` (475), then `applyDamage` (477),
  as the ADR says. `amount` is reassigned at line 474, so a change made at the head of the method is what
  `damageItem` sees.
- Every slot-based wear funnels into it: `hurtAndBreak(int, LivingEntity, EquipmentSlot)` calls it (line
  530); the block-break path reaches it through `Item#mineBlock`
  (`net/minecraft/world/item/Item.java` line 280,
  `itemStack.hurtAndBreak(tool.damagePerBlock(), owner, EquipmentSlot.MAINHAND);`); armour wear reaches it
  through `LivingEntity.java` line 1924 (`itemStack.hurtAndBreak(durabilityDamage, this, slot);`).
  Dispensers call it with a `null` entity (`DispenseItemBehavior.java` lines 213 and 386,
  `ShearsDispenseItemBehavior.java` line 27).
- `hurtWithoutBreaking` (lines 512 to 522) calls `processDurabilityChange` without going through
  `hurtAndBreak`. Its only caller in the tree is `TridentItem.java` line 81. A mixin on `hurtAndBreak` does not
  see that wear, so Kit Care's "any cause" misses a trident's riptide wear unless the hook is put on
  `processDurabilityChange` instead. For the Architect to decide; it is a one-item gap.
- Unbreaking rolls per point. `EnchantmentHelper.processDurabilityChange`
  (`net/minecraft/world/item/enchantment/EnchantmentHelper.java` lines 102 to 106) runs each enchantment's
  `minecraft:item_damage` effects; `data/minecraft/enchantment/unbreaking.json` uses
  `minecraft:remove_binomial`, and `RemoveBinomial#process`
  (`net/minecraft/world/item/enchantment/effects/RemoveBinomial.java` lines 14 to 31) loops
  `for (int y = 0; y < n; y++) { if (random.nextFloat() < p) { drop++; } }` for amounts up to 128 and uses a
  Gaussian approximation above that.
- The ADR's rejected "enchantment `minecraft:item_damage` effect" is now confirmed rather than unverified:
  `Enchantment#modifyDurabilityChange(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack,
  MutableFloat change)` (`net/minecraft/world/item/enchantment/Enchantment.java` line 235) receives no
  entity, so it cannot find the player's team.
- The tool is damaged before drops fire. `net/minecraft/server/level/ServerPlayerGameMode.java#destroyBlock`:
  `CommonHooks.fireBlockBreak(...)` (line 271), `itemStack.mineBlock(this.level, adjustedState, pos,
  this.player);` (line 292), `removeBlock(...)` (293), `block.playerDestroy(...)` (296), and
  `EventHooks.onPlayerDestroyItem(...)` only if the stack emptied (301). `Block#playerDestroy`
  (`Block.java` lines 467 to 471) calls `dropResources(state, level, pos, blockEntity, player,
  destroyedWith)`, which calls `CommonHooks.handleBlockDrops(...)` (line 403).

### Local variables

Source line numbers are not bytecode local indices. From the signature and the JVM's rules for an
instance method, the parameter slots of the target are fixed: 0 `this`, 1 `amount` (int), 2 `level`,
3 `player`, 4 `onBreak`. The only declared local, `newAmount` (int), is expected at slot 5, but a local's
slot is the compiler's choice, and the class the dev run loads and the class a player's install loads may
not come from the same compilation (how NeoGradle and the installer produce them was not read here).

What still needs a bytecode check on Nick's PC, if the mixin uses any local by index or ordinal:
`javap -c -p -l` on `net.minecraft.world.item.ItemStack` from the Minecraft jar the dev run uses and from the
production NeoForge install, reading the `LocalVariableTable` of `hurtAndBreak` with the descriptor above.
Injection forms that need no local index avoid the check: a `@ModifyVariable` on the `int` argument at
`HEAD` with `argsOnly = true` for the head multiplier, and for the post-Unbreaking roll either a
modification of `processDurabilityChange`'s return value at its call in `hurtAndBreak` or an injection at
`RETURN` of the private `processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)I`.
Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: whether NeoForge 26.1.2.95 bundles MixinExtras (its jar-in-jar list), before the ADR picks `@ModifyExpressionValue` over a plain `@Inject` at `RETURN`.

## FTB Teams party join and disband

Unblocks E0-S18 (#178), E0-S10 (#258), E0-S4 (#223), E3-S8 (#206). Read from
`ftb-teams-26.1.2.4-sources.jar` on 2026-10-27; paths are inside that jar. Copied from the read notes.

1. The personal team id is the player's UUID and the personal team is never deleted.
   `dev/ftb/mods/ftbteams/data/TeamManagerImpl.java` `createPlayerTeam(UUID playerId, String playerName)`:
   `PlayerTeam team = new PlayerTeam(this, playerId);` ... `team.addMember(playerId, TeamRank.OWNER);`
   and in `playerLoggedIn`: `team = createPlayerTeam(id, name); teamMap.put(id, team);`
2. Joining or creating a party does not disband or delete the personal team. It points the personal team
   at the party. `PartyTeam.java` `join(@Nullable ServerPlayer player, NameAndId playerProfile)`:
   `playerTeam.setEffectiveTeam(this); ranks.put(id, TeamRank.MEMBER); ... playerTeam.ranks.remove(id); playerTeam.markDirty(); ... onPlayerChangeTeam(oldTeam, id, player, false);`
   `TeamManagerImpl.java` `createParty(...)`: `playerTeam.setEffectiveTeam(team); team.addMember(playerId, TeamRank.OWNER); ... playerTeam.removeMember(playerId); ... team.onPlayerChangeTeam(playerTeam, playerId, player, false);`
   The last argument (`deleted`) is false, so no `TeamDeletedEvent` fires for the personal team.
3. Team lookup, `TeamManagerImpl.java`:
   `getTeamForPlayerID(UUID uuid) { PlayerTeam t = knownPlayers.get(uuid); return t == null ? Optional.empty() : Optional.of(t.getEffectiveTeam()); }`
   `getPersonalTeamForPlayerID(UUID uuid) { return knownPlayers.get(uuid); }`
4. Leaving a party returns the player to the same personal team (same id). The party is deleted only when
   its last member leaves. `PartyTeam.java` `leave(UUID id)`:
   `PlayerTeam playerTeam = manager.getPersonalTeamForPlayerID(id); playerTeam.setEffectiveTeam(playerTeam); playerTeam.ranks.put(id, TeamRank.OWNER); ... ranks.remove(id); ... if (getMembers().isEmpty()) { deletingTeam = true; invalidateTeam(); manager.deleteTeam(this); } ... playerTeam.onPlayerChangeTeam(this, id, player, deletingTeam);`
   `kick(...)` likewise sets the personal team back with `deleted` false. `forceDisband` kicks every
   non-owner, then `leave(owner)`, which deletes the party.
5. Events: `AbstractTeam.java` `onPlayerChangeTeam(prev, player, serverPlayer, deleted)` posts
   `PlayerChangedTeamEvent` always; `PlayerLeftPartyTeamEvent` when prev is a `PartyTeam` and this is a
   `PlayerTeam`; `PlayerJoinedPartyTeamEvent` when prev is a `PlayerTeam` and `serverPlayer != null`;
   `TeamDeletedEvent(prev)` only when `deleted`. NeoForge-native classes (ftb-teams-neoforge jar,
   `dev/ftb/mods/ftbteams/api/neoforge/FTBTeamsEvent.java`): `FTBTeamsEvent.PlayerChangedTeam`,
   `.PlayerJoinedPartyTeam`, `.PlayerLeftPartyTeam`, `.TeamDeleted`, `.TeamCreated`, `.TeamLoaded`,
   `.TeamPlayerLoggedIn`, `.PlayerTransferredOwnership`. `PlayerJoinedPartyTeamEvent` is not posted when the
   joining player is offline (`serverPlayer` null); `PlayerChangedTeamEvent` is.
6. Team deletion moves the FTB Teams file: `TeamManagerImpl.java` `deleteTeam(AbstractTeam team)` removes
   from `teamMap`, saves, and moves `<team id>.json5` into `<world>/ftbteams/deleted/`.

Consequences: the solo team is never disbanded by a party join, so the conditional in E0-S18 and E0-S10 ("a
party join is exempt from disband archiving if FTB Teams disbands the solo team") never triggers; disband
archiving keys on `TeamDeleted`, which fires only for party teams. A solo Vault (`vault_<playerUuid>`) stays
on disk while its player is in a party and is theirs again when they leave (same team id). Team-change
triggers listen to `PlayerChangedTeam`, which fires for offline players too, not `PlayerJoinedPartyTeam`.

## ProtoChunk attachment carry-over

Unblocks E6-S4 (#209, the spike) and E0-S6 (#224, ADR 0004).

Chunk attachments live on `ChunkAccess`, so a `ProtoChunk` has them during generation.
`net/minecraft/world/level/chunk/ChunkAccess.java`:

```java
60   public abstract class ChunkAccess implements LightChunk, StructureAccess, BiomeManager.NoiseBiomeSource, net.neoforged.neoforge.attachment.IAttachmentHolder {
485      // Neo: Hook in AttachmentHolder to chunks for data storage and retrieval
486      private final net.neoforged.neoforge.attachment.AttachmentHolder.AsField attachmentHolder = new net.neoforged.neoforge.attachment.AttachmentHolder.AsField(this);
511      public <T> T setData(net.neoforged.neoforge.attachment.AttachmentType<T> type, T data) {
512          markUnsaved();
513          return getAttachmentHolder().setData(type, data);
```

Promotion to `LevelChunk`, `net/minecraft/world/level/chunk/LevelChunk.java` line 158, in the constructor that
takes the `ProtoChunk`:

```java
net.neoforged.neoforge.attachment.AttachmentInternals.copyChunkAttachmentsOnPromotion(level.registryAccess(), protoChunk.getAttachmentHolder(), this.getAttachmentHolder());
```

A chunk that is already full is wrapped, not copied: `ImposterProtoChunk.java` lines 287 and 288,
`getAttachmentHolder() { return wrapped.getAttachmentHolder(); }`.

Save and load, `net/minecraft/world/level/chunk/storage/SerializableChunkData.java`: the write at lines 432
to 437 (`attachmentData = chunk.writeAttachmentsToNBT(level.registryAccess());`, with an exception logged
as "It will not persist"); the read at line 217 (`chunkData.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)`)
and lines 344 and 345 (`chunk.readAttachmentsFromNBT(level.registryAccess(), this.attachmentData);`), which
runs before the `ChunkType.LEVELCHUNK` branch, so a proto chunk saved mid-generation keeps its attachments.

Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: `net.neoforged.neoforge.attachment.AttachmentInternals#copyChunkAttachmentsOnPromotion`, which
decides whether every attachment is copied or only some, and the `AttachmentType` builder for any flag
that controls it; and `net.neoforged.neoforge.attachment.AttachmentHolder` for whether it is safe to write
from the generation thread. E6-S4 still has to answer both, by reading that class or by its spike in a running game.

## Client payload handler registration

Unblocks E0-S9 (#183, ADR 0006).

Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: `net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent`,
`net.neoforged.neoforge.network.registration.PayloadRegistrar` (every `playTo*` method and `optional()`), and
any client-side registration event in `net.neoforged.neoforge.client.network`. `RegisterPayloadHandlersEvent`, `PayloadRegistrar` and `IPayloadContext`
are not in the patched Minecraft tree, and no Minecraft class references a client-only registration event.
What the tree shows is NeoForge's channel negotiation on the client:
`net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl.java` lines 167 to 182 route
`MinecraftRegisterPayload`, `MinecraftUnregisterPayload`, `CommonVersionPayload` and `CommonRegisterPayload`
to `net.neoforged.neoforge.network.registration.NetworkRegistry`.

The old build's `ModNetwork` compiled against the same pin
(`git show 86360a3^:src/main/java/com/orevault/orevault/network/ModNetwork.java` lines 228 to 240):
`PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);`,
`registrar.playToServer(PurchaseNode.TYPE, PurchaseNode.CODEC, ModNetwork::onPurchaseNode);`,
`registrar.playToClient(SyncTeamProgress.TYPE, SyncTeamProgress.CODEC, ModNetwork::onClientPayload);`. That
is evidence that those calls exist in 26.1.2.95, not a read of the class. Whether a client-only handler
registration exists, `PayloadRegistrar#optional()`, and what happens on a decode failure stay open for a read
of NeoForge's jar.

## BlockDropsEvent experience accessors

Unblocks E5-S3 (#264) and E9-S9 (#312, vanilla XP bonus).

Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: `net.neoforged.neoforge.event.level.BlockDropsEvent` (its experience getter
and setter) and `net.neoforged.neoforge.common.CommonHooks#handleBlockDrops` (where the experience is computed
and the orb popped). The call sites show where XP is decided in 26.1:

- Drops reach the event through `CommonHooks.handleBlockDrops((ServerLevel)level, pos, state, blockEntity,
  captured, breaker, tool)` (`net/minecraft/world/level/block/Block.java` line 403), from the
  `dropResources` overload that `playerDestroy` calls.
- Ore XP is no longer dropped by the block's `spawnAfterBreak`. NeoForge patches in `getExpDrop`, and the
  vanilla ore class returns the sample instead of popping it.
  `net/minecraft/world/level/block/DropExperienceBlock.java` lines 33 to 40:

  ```java
  // Neo: Patch-in override for getExpDrop. Original vanilla logic passes this.xpRange to tryDropExperience.
  @Override
  public int getExpDrop(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos,
          net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity,
          net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity breaker, ItemStack tool) {
      return this.xpRange.sample(level.getRandom());
  }
  ```

  `RedStoneOreBlock`, `SculkSensorBlock`, `SculkCatalystBlock` and `SpawnerBlock` carry the same patch.
- The orb is spawned by `Block#popExperience(ServerLevel, BlockPos, int)` (`Block.java` lines 446 to 450),
  gated on the `BLOCK_DROPS` game rule.

The Minecraft side therefore shows that ore XP comes from `getExpDrop`, not from `spawnAfterBreak`; that the
event carries that sample is expected but sits in `CommonHooks#handleBlockDrops` and is unverified. The getter and setter names (expected by E5-S3 as `getDroppedExperience` and `setDroppedExperience`) and
whether the event or `handleBlockDrops` applies Fortune or Silk Touch through
`EnchantmentHelper.processBlockExperience` remain open.

## GUI sprite nine-slice .mcmeta format

Unblocks E7-S4 (#213, S28).

`net/minecraft/client/resources/metadata/gui/GuiMetadataSection.java`: the section is named `gui` and holds
one optional field, `scaling`, defaulting to stretch:

```java
GuiSpriteScaling.CODEC.optionalFieldOf("scaling", GuiSpriteScaling.DEFAULT).forGetter(GuiMetadataSection::scaling)
...
public static final MetadataSectionType<GuiMetadataSection> TYPE = new MetadataSectionType<>("gui", CODEC);
```

`net/minecraft/client/resources/metadata/gui/GuiSpriteScaling.java`, lines 22 to 32, 55 to 71 and 108 to 111:

```java
public record NineSlice(int width, int height, GuiSpriteScaling.NineSlice.Border border, boolean stretchInner) implements GuiSpriteScaling {
    ...
            ExtraCodecs.POSITIVE_INT.fieldOf("width").forGetter(GuiSpriteScaling.NineSlice::width),
            ExtraCodecs.POSITIVE_INT.fieldOf("height").forGetter(GuiSpriteScaling.NineSlice::height),
            GuiSpriteScaling.NineSlice.Border.CODEC.fieldOf("border").forGetter(GuiSpriteScaling.NineSlice::border),
            Codec.BOOL.optionalFieldOf("stretch_inner", false).forGetter(GuiSpriteScaling.NineSlice::stretchInner)
    ...
    public record Border(int left, int top, int right, int bottom) {
        private static final Codec<GuiSpriteScaling.NineSlice.Border> VALUE_CODEC = ExtraCodecs.POSITIVE_INT ...
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("left") ... fieldOf("top") ... fieldOf("right") ... fieldOf("bottom")
        private static final Codec<GuiSpriteScaling.NineSlice.Border> CODEC = Codec.either(VALUE_CODEC, RECORD_CODEC)
...
    STRETCH("stretch", GuiSpriteScaling.Stretch.CODEC),
    TILE("tile", GuiSpriteScaling.Tile.CODEC),
    NINE_SLICE("nine_slice", GuiSpriteScaling.NineSlice.CODEC);
```

The validator (lines 34 to 46) rejects a border where `left + right >= width` or `top + bottom >= height`
("Nine-sliced texture has no horizontal center slice"). `border` is either one positive int or an object with
all four sides; `stretch_inner` defaults to `false`, which tiles the centre and edges.

Shipped examples. `assets/minecraft/textures/gui/sprites/widget/button.png.mcmeta`:

```json
{ "gui": { "scaling": { "type": "nine_slice", "width": 200, "height": 20, "border": 3 } } }
```

`assets/minecraft/textures/gui/sprites/widget/tab_selected_highlighted.png.mcmeta` uses
`"border": { "left": 2, "top": 2, "right": 2, "bottom": 0 }`, and
`assets/minecraft/textures/gui/sprites/tooltip/frame.png.mcmeta` sets `"stretch_inner": true`.

Where sprites are found: `assets/minecraft/atlases/gui.json` adds the directory source `gui/sprites` with an
empty prefix, so a mod sprite at `assets/orevault/textures/gui/sprites/tome/window.png` (with its
`.png.mcmeta` beside it) is drawn by id `orevault:tome/window` through
`GuiGraphicsExtractor#blitSprite(RenderPipeline, Identifier, int x, int y, int width, int height)`
(`net/minecraft/client/gui/GuiGraphicsExtractor.java` line 376). `width` and `height` in the `.mcmeta` are
the sprite's size in GUI units, which is how one texel per GUI unit (`CLAUDE.md` Textures) is expressed.

## Chunk ticket API and forced tickets after createLevels

Unblocks E10-S3 (#373) and E0-S4 (#223).

Vanilla ticket types, `net/minecraft/server/level/TicketType.java` lines 10 to 29:

```java
public record TicketType(long timeout, int flags, boolean forceNaturalSpawning) {
    public static final int FLAG_PERSIST = 1;
    public static final int FLAG_LOADING = 2;
    public static final int FLAG_SIMULATION = 4;
    public static final int FLAG_KEEP_DIMENSION_ACTIVE = 8;
    public static final int FLAG_CAN_EXPIRE_IF_UNLOADED = 16;
    ...
    public static final TicketType FORCED = register("forced", 0L, 15);
```

The calls, `net/minecraft/server/level/ServerChunkCache.java` lines 487 to 516:
`addTicket(Ticket ticket, ChunkPos pos)`, `addTicketAndLoadWithRadius(TicketType type, ChunkPos pos, int
radius)` (returns a `CompletableFuture<?>`, throws for a type that does not load or can expire),
`addTicketWithRadius(TicketType type, ChunkPos pos, int radius)`, `removeTicketWithRadius(...)`,
`updateChunkForced(ChunkPos pos, boolean forced)`. `ServerLevel#setChunkForced(int chunkX, int chunkZ,
boolean forced)` (line 1520) wraps the last. NeoForge's `TicketController` is not in the tree and was not
read; the patched code reaches NeoForge's forced chunks through
`net.neoforged.neoforge.common.world.chunk.ForcedChunkManager` (below and `ServerLevel.java` line 412).

Storage: each level loads its own tickets. `ServerChunkCache.java` line 103,
`this.ticketStorage = this.savedDataStorage.computeIfAbsent(TicketStorage.TYPE);`, and
`net/minecraft/world/level/TicketStorage.java` lines 37 to 44 fold NeoForge's data into the same file:

```java
.and(net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.defineExtraStorageParams())
.apply(i, (tickets, neoData) -> net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.readStoredTickets(TicketStorage::fromPacked, tickets, neoData))
...
public static final SavedDataType<TicketStorage> TYPE = new SavedDataType<>(
    Identifier.withDefaultNamespace("chunk_tickets"), TicketStorage::new, CODEC, DataFixTypes.SAVED_DATA_FORCED_CHUNKS
```

Loaded tickets start deactivated. `TicketStorage#fromPacked` (lines 62 to 72) ends with
`return new TicketStorage(new Long2ObjectOpenHashMap<>(4), ticketsToLoad);`, the second argument being
`deactivatedTickets`. They become live only in `activateAllDeactivatedTickets()` (lines 99 to 107).

The only caller is `MinecraftServer#prepareLevels` (`net/minecraft/server/MinecraftServer.java` lines 555 to
566), which walks the levels that exist at that moment:

```java
private void prepareLevels() {
    ChunkLoadCounter chunkLoadCounter = new ChunkLoadCounter();

    for (ServerLevel level : this.levels.values()) {
        chunkLoadCounter.track(level, () -> {
            TicketStorage savedTickets = level.getDataStorage().get(TicketStorage.TYPE);
            if (savedTickets != null) {
                savedTickets.activateAllDeactivatedTickets();
                net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.activateAllDeactivatedTickets(level, savedTickets);
            }
        });
    }
```

and `loadLevel()` runs it once, straight after `createLevels()` (lines 409 to 411). The lifecycle events sit
around that call: `DedicatedServer.java` line 289 posts `handleServerAboutToStart`, line 291 calls
`loadLevel()`, line 330 posts `handleServerStarting`; `IntegratedServer.java` lines 101, 102 and 107 do the
same.

Answer: no. A Vault level created after `createLevels`, in `ServerStartingEvent`, `ServerStartedEvent` or on
the portal path, is not in `this.levels` when `prepareLevels` runs, so neither its vanilla persistent tickets
nor NeoForge's forced tickets are reactivated. Restore must do it itself, mirroring the two lines above:
`level.getDataStorage().get(TicketStorage.TYPE)`, then `activateAllDeactivatedTickets()`, then NeoForge's
`ForcedChunkManager.activateAllDeactivatedTickets(level, savedTickets)`. Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read:
`net.neoforged.neoforge.common.world.chunk.ForcedChunkManager#activateAllDeactivatedTickets` (is it public)
and `net.neoforged.neoforge.common.world.chunk.TicketController` (its API, and any reinstate path of its own). `MinecraftServer#markWorldsDirty()` is at line 1899.

## Capabilities.Energy.ITEM and the item-handler capability

Unblocks E9-S8 (#313), E9-S45 (#352), E9-S41 (#349, Seam Courier), E12-S1 (#217) for §9's Unverified list.

`Capabilities.Energy` does not appear anywhere in the patched Minecraft tree. Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read:
`net.neoforged.neoforge.capabilities.Capabilities` (the `Energy.ITEM` field, its type and context type), the
energy handler interface it returns and its insert method, and the item handler interface behind
`Capabilities.Item.BLOCK` with its insert method.

The block item capability does appear. `net/minecraft/world/level/block/entity/HopperBlockEntity.java`
line 450:

```java
var blockItemHandler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, state, null, side);
```

and insertion goes through NeoForge's transfer API, `net/minecraft/world/level/block/CrafterBlock.java` line
204:

```java
remaining = net.neoforged.neoforge.transfer.item.ItemUtil.insertItemReturnRemaining(containerOrHandler.itemHandler(), remaining, false, null);
```

So in 26.1.2.95 the block item-handler capability is `Capabilities.Item.BLOCK`, queried with
`level.getCapability(cap, pos, state, blockEntity, side)`, and the transfer types live in
`net.neoforged.neoforge.transfer` (`item.ItemResource`, `item.ItemUtil`, `transaction.TransactionContext`
are referenced). The handler's interface name and its insert signature are in NeoForge's jar. A javadoc at
`HopperBlockEntity.java` line 398 still says "Use IItemHandler capability instead"; it is a stale comment, and
the code beside it uses the transfer API.

## Data-component ingredient

Unblocks E9-S28 (#332, the Resonance Crystal recipe) and E12-S1 (#217).

Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: the `ICustomIngredient` implementations in
`net.neoforged.neoforge.common.crafting` (the one that matches data components), their codec and JSON `type` id. What the tree shows is the hook a component-matching
ingredient plugs into. `net/minecraft/world/item/crafting/Ingredient.java`:

```java
34  public static final Codec<Ingredient> CODEC = net.neoforged.neoforge.common.crafting.IngredientCodecs.codec(ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC).xmap(Ingredient::new, i -> i.values));
36  private net.neoforged.neoforge.common.crafting.@org.jspecify.annotations.Nullable ICustomIngredient customIngredient = null;
51  public Ingredient(net.neoforged.neoforge.common.crafting.ICustomIngredient customIngredient) {
75  public boolean test(ItemStack input) {
76      if (this.customIngredient != null) {
77          return this.customIngredient.test(input);
78      }
79      return input.is(this.values);
```

Vanilla's own ingredient is a `HolderSet<Item>` and matches by item only (line 79). Matching a component on
any `#c:raw_materials` item therefore needs a NeoForge `ICustomIngredient`; its class name and JSON `type` id
remain to be read.

## MobEffectInstance duration access

Unblocks E9-S35 (#342, "potion durations do not tick down").

`net/minecraft/world/effect/MobEffectInstance.java`: the field is private and has no setter.

```java
41   private int duration;
196  public int getDuration() {
197      return this.duration;
198  }
...
248  private void tickDownDuration() {
249      if (this.hiddenEffect != null) {
250          this.hiddenEffect.tickDownDuration();
251      }
252
253      this.duration = this.mapDuration(d -> d - 1);
254  }
```

The server ticks it in `tickServer(ServerLevel, LivingEntity, Runnable)` (lines 216 to 233, calling
`tickDownDuration()` at 225), and the client counts down its own copy in `tickClient()` (lines 235 to 242).
`net/minecraft/world/entity/LivingEntity.java#tickEffects` (lines 849 to 871) calls `tickServer` on the
server and `tickClient` on the client, and resends an effect only through `onEffectUpdated` when it changes
or every 600 ticks (`else if (effect.getDuration() % 600 == 0)`, line 862).

The public ways to raise a duration: `MobEffectInstance#update(MobEffectInstance takeOver)` (lines 125 to
168) takes the longer duration at the same amplifier, and `LivingEntity#addEffect(MobEffectInstance,
Entity)` (lines 1022 to 1037) calls it and then `onEffectUpdated(effect, true, source)`, which also
refreshes attributes. `withScaledDuration(float)` (line 182) returns a copy.

For the story: holding a timer still needs either an access transformer on `duration` (or on
`tickDownDuration`) plus a resend, because the client's `tickClient` keeps counting down, or a mixin on
`tickDownDuration` that skips the decrement on both sides when the effect's holder is in scope. A per-tick
`addEffect` refund works without either but refreshes attributes every tick. The choice is E9-S35's (with an
ADR if it is a mixin); the source facts above are what it chooses from.

## FoodData.exhaustionLevel access

Unblocks E9-S17 (#322, Long Delve) and E9-S13 (#317, hunger reduction built once).

`net/minecraft/world/food/FoodData.java`: the field is private, with no getter or setter.

```java
16   private float exhaustionLevel;
...
32   public void tick(ServerPlayer player) {
...
35       if (this.exhaustionLevel > 4.0F) {
36           this.exhaustionLevel -= 4.0F;
...
100  public void addExhaustion(float amount) {
101      this.exhaustionLevel = Math.min(this.exhaustionLevel + amount, 40.0F);
102  }
```

The class's public methods are `eat`, `tick`, the two save methods (key `"foodExhaustionLevel"`, lines 78
and 85), `getFoodLevel`, `hasEnoughFood`, `needsFood`, `addExhaustion`, `getSaturationLevel`, `setFoodLevel`
and `setSaturation`. `Player#causeFoodExhaustion(float)` (`net/minecraft/world/entity/player/Player.java`
lines 1629 to 1635) only calls `addExhaustion` and posts no event.

So reading the rise needs an access transformer, as the old build did
(`git show 0e0222b`, `public net.minecraft.world.food.FoodData exhaustionLevel` in
`META-INF/accesstransformer.cfg`). `addExhaustion` with a negative amount would lower the value without an
AT, but it has no floor at 0, and the rise still has to be read, so the AT stays.

## Respawn and PlayerChangedDimensionEvent

Unblocks E0-S1 (#222, §11 Vault Session State) and E9-S5 (#309); old #149.

`PlayerChangedDimensionEvent` is posted in one place, the cross-level branch of `ServerPlayer#teleport`.
`net/minecraft/server/level/ServerPlayer.java` line 1134 opens
`public @Nullable ServerPlayer teleport(TeleportTransition transition)`, line 1150 takes the same-level branch
(`if (newLevel.dimension() == lastDimension)`), and line 1191, in the other branch:

```java
net.neoforged.neoforge.event.EventHooks.firePlayerChangedDimensionEvent(this, lastDimension, transition.newLevel().dimension());
```

A death respawn does not call `teleport`. `net/minecraft/server/network/ServerGamePacketListenerImpl.java`
line 1913 calls `this.server.getPlayerList().respawn(this.player, false, Entity.RemovalReason.KILLED);`, and
`net/minecraft/server/players/PlayerList.java#respawn` (lines 397 to 445) removes the old entity
(`serverPlayer.level().removePlayerImmediately(serverPlayer, removalReason);`, line 400), builds a new
`ServerPlayer` in the respawn level (line 407), adds it with `level.addRespawnedPlayer(player);` (line 439),
and posts only `EventHooks.firePlayerRespawnEvent(player, keepAllPlayerData);` (line 445), after
`firePlayerRespawnPositionEvent` (line 403).

Confirmed: a death exit from the Vault fires no `PlayerChangedDimensionEvent`. A per-tick guard that ends the
trip of any player not in a Vault, keyed by UUID, catches it, as does a `PlayerRespawnEvent` listener. Note
also that respawn creates a new `ServerPlayer` object, so trip state must not be keyed by the entity
instance.

## Also found

- §3.1's copper nugget question (`c:nuggets/copper`): the `c:` tags are in NeoForge's jar. Unverified: NeoForge sources not yet read (E1-S5 #195 stays open for these). Must read: `data/c/tags/item/nuggets/copper.json` in NeoForge's jar.
  Vanilla 26.1 has the item, `Items.COPPER_NUGGET` (`net/minecraft/world/item/Items.java` line 1906), and
  `data/minecraft/tags/item/metal_nuggets.json` lists `minecraft:copper_nugget`, `minecraft:iron_nugget` and
  `minecraft:gold_nugget`.

## Still to read, from NeoForge's own jar on Nick's PC

One Remote Control session with `net.neoforged:neoforge:26.1.2.95`'s classes (or its sources jar) closes
these. `find ~/.gradle -name 'neoforge-26.1.2.95*'` on Nick's PC lists what NeoGradle downloaded.

1. `BlockDropsEvent`: the experience getter and setter, and where Fortune and Silk Touch apply (E5-S3, E9-S9).
2. `Capabilities.Energy.ITEM`: its context type and the insert method's name and signature; the item
   handler's interface and insert signature behind `Capabilities.Item.BLOCK` (E9-S8, E9-S45, E9-S41, E12-S1).
3. The data-component ingredient's class and JSON `type` id (E9-S28).
4. `AttachmentInternals.copyChunkAttachmentsOnPromotion` and the `AttachmentType` builder: which attachments
   are copied, and thread safety from the generator (E6-S4, E0-S6, ADR 0005's `copyOnDeath()`).
5. `RegisterPayloadHandlersEvent` and `PayloadRegistrar`: a client-only handler registration,
   `optional()`, decode failure behaviour (E0-S9).
6. `ForcedChunkManager.activateAllDeactivatedTickets` visibility and `TicketController`'s API (E10-S3, E0-S4).
7. Whether MixinExtras ships with NeoForge 26.1.2.95 (E0-S11, E9-S7).
8. `c:nuggets/copper` in NeoForge's tag data (§3.1 tier 4 nugget).
9. `javap -c -p -l` on `ItemStack#hurtAndBreak`, only if ADR 0008's mixin uses a local by index or ordinal
   (E0-S11).
