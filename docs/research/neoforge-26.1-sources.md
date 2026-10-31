# NeoForge 26.1 source reads

E1-S5 (#195). Each section quotes the source, names the file and lines, says what it settles, and names the
stories it must land before. Story ids and issue numbers are from the filed ticket table.

## Provenance

Rechecked at `neo_version=26.1.2.121` on 2026-10-31. Line numbers below are 26.1.2.95's. Each NeoForge source
file this document cites was compared between the two NeoForge sources jars, and each cited Minecraft class
between the two patched jars by class bytes. Minecraft is 26.1.2 at both. No conclusion here changes:

- Five cited NeoForge files changed. `ClientNetworkRegistry` adds optional channels and moves lines. `CommonHooks`
  changes `onPlayerAttackTarget` only. `ResourcePackLoader` guards overlay parsing.
- `NetworkRegistry` refuses the modded setup payloads serverbound, and `onMinecraftRegister` gains a cap on
  ad-hoc channels. Registered payloads are unaffected.
- sponge-mixin moves to 0.17.4 and mixinextras to 0.5.5. `JAVA_25` is still there, so the mixin notes hold.
- `GenericPacketSplitter` now caps a reassembled packet at NeoForge's `splitPacketMaxSizeLimitMb`, 128 MB by
  default, and disconnects above it. ADR 0006's bounds are far under that.
- Of the patched Minecraft classes, 20 differ and one is cited: `EnchantmentHelper` gains a
  `processEquipmentDropChance` overload, and `processDurabilityChange` and `processBlockExperience` are unchanged.

The Gradle sources jar for 26.1.2.121 is an empty stub, so Minecraft's source text below stays 26.1.2.95's.

Three sources, each for a different job. All were read on Nick's PC on 2026-10-31 unless a section says
otherwise. Paths below are paths inside the jar, and line numbers are lines of the `.java` file. No jar was
run; the only tools were `unzip` and `javap`.

- Patched Minecraft bodies: `.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/26.1.2.95/neoforge-26.1.2.95-sources.jar`.
  It matches the pinned `neo_version=26.1.2.95` (`CLAUDE.md` Build). Its `version.json` reads `"id": "26.1.2"`,
  `"world_version": 4790`, `"java_version": 25`. It holds `net/minecraft`, `com/mojang`, `assets/minecraft`
  and `data/minecraft`, and no NeoForge source.
- Visibility of Minecraft members: `javap -p` on the compiled jar beside it,
  `.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/26.1.2.95/neoforge-26.1.2.95.jar`, cross-checked
  against the unpatched `.gradle/caches/minecraft/versions/26.1.2/client.jar`. NeoForge's own transformers are
  `ats/accesstransformer.cfg` and `ats/accesstransformergenerated.cfg` inside
  `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.95/a6a9bc0fb2039e1643752fd42fbde8743141f784/neoforge-26.1.2.95-userdev.jar`.
- NeoForge's own classes: `https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.95/neoforge-26.1.2.95-sources.jar`
  (2,212 files once unpacked), fetched 2026-10-31. FML: `https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/11.0.15/loader-11.0.15-sources.jar`,
  the version in the Gradle cache.
- FTB Teams: `ftb-teams-26.1.2.4-sources.jar` from the Gradle cache, read on 2026-10-27 (section "FTB Teams
  party join and disband").

### The sources jar still carries the old transformers; the compiled jar does not

The plan was that #188's build would rewrite the `ng_dummy_ng` sources jar with no project transformers, which
would have ended the caveat this section used to carry. It did not. The file on disk has the modified time
2026-08-29 12:04, while the compiled jar beside it is 2026-10-31 13:14, and its contents have not moved:

```java
218    public final LevelStorageSource.LevelStorageAccess storageSource;                       // net/minecraft/server/MinecraftServer.java
12     public final IntProvider count;                                                         // .../levelgen/placement/CountPlacement.java
17     public final HeightProvider height;                                                     // .../levelgen/placement/HeightRangePlacement.java
23     public final VerticalAnchor minInclusive;                                               // .../levelgen/heightproviders/UniformHeight.java
24     public final VerticalAnchor maxInclusive;
```

Those are the five lines of the old build's `META-INF/accesstransformer.cfg`
(`git show 86360a3^:src/main/resources/META-INF/accesstransformer.cfg`). The current tree has no such file, and
`build.gradle` names none.

The caveat is closed all the same, by the compiled jar. `javap -p` on it gives the real answer for the five,
below, and the same output on Mojang's own `client.jar` agrees. So:

- A Minecraft member's visibility is read with `javap -p` on the compiled jar. A `private` or `protected` read
  in the sources jar stays reliable, because a transformer only widens.
- Bodies and line numbers from the sources jar are good. A transformer changes modifiers only, and the five
  above are the only members the file named.
- If a later task needs the sources jar itself clean, it has to be regenerated. Nothing here needs that.

### The five members, in a clean 26.1.2.95

`javap -p`, on the compiled jar and on `client.jar`, same result on both:

```
protected final net.minecraft.world.level.storage.LevelStorageSource$LevelStorageAccess storageSource;   // net.minecraft.server.MinecraftServer
private final net.minecraft.util.valueproviders.IntProvider count;                                       // ...levelgen.placement.CountPlacement
private final net.minecraft.world.level.levelgen.heightproviders.HeightProvider height;                  // ...levelgen.placement.HeightRangePlacement
private final net.minecraft.world.level.levelgen.VerticalAnchor minInclusive;                            // ...levelgen.heightproviders.UniformHeight
private final net.minecraft.world.level.levelgen.VerticalAnchor maxInclusive;                            // ...levelgen.heightproviders.UniformHeight
```

NeoForge widens none of them: neither of its two transformer files names `storageSource`, `CountPlacement`,
`HeightRangePlacement` or `UniformHeight`. `RarityFilter.chance` reads `private final int chance` in both
jars, as a control.

What each means, for the story that must have it:

- `MinecraftServer.storageSource` is `protected`, so `VaultDimensions` cannot read it from outside a subclass.
  `MinecraftServer` offers no getter for the object: the only public method on the way is
  `getWorldPath(LevelResource)`, which returns a `Path`, and the `ServerLevel` constructor needs the
  `LevelStorageAccess` itself. ADR 0002's access transformer line stays, carrying its ticket comment. Before E3-S6a (#237).
  In a built jar, `META-INF/accesstransformer.cfg` is found with no entry in `neoforge.mods.toml`: it is FML's
  default path (`net/neoforged/fml/loading/moddiscovery/ModFile.java` line 45), and `[[accessTransformers]]` with
  a `file` key is only for another path (`ModFileParser.java` lines 86 to 105).
- `CountPlacement.count`, `HeightRangePlacement.height`, `UniformHeight.minInclusive` and `maxInclusive` are
  `private`. ADR 0009's codec path needs no transformer, as it says, and its fallback really would need the
  four lines of old #15. Needed before E4-S2 (#238) only if the codec path fails.

## Summary

| Read | Result | Must land before |
| --- | --- | --- |
| The five members' clean visibility | Read (`javap`): `storageSource` protected, the other four private | E3-S6a (#237); E4-S2 (#238) only if its codec path fails |
| `DimensionType` codec fields | Read | E0-S1 (#222), E0-S4 (#223) |
| `ItemStack#hurtAndBreak`, `processDurabilityChange` | Read; the `javap` check is not needed (ADR 0008) | E0-S11 (#226), E9-S7 (#311) |
| FTB Teams party join and disband | Read | E0-S18 (#178), E0-S10 (#258), E0-S4 (#223), E3-S8 (#206) |
| Attachment copy, builder, `copyOnDeath`, generator-thread writes | Read | E3-S6a (#237), E6-S4 (#209) |
| `ChunkEvent.Load` new-chunk flag | Read: `isNewChunk()` | E6-S4 (#209) |
| `TicketController`, when validation runs | Read: at activation, so ADR 0002 step 13 runs it | E3-S6a (#237), E10-S3 (#373) |
| Forced tickets after `createLevels` | Read | E10-S3 (#373), E0-S4 (#223) |
| `c:ores` key | Read: `Tags.Blocks.ORES` | E4-S2 (#238) |
| Biome modifier spawns on `orevault:vault` | Read: yes they can; see the section | E3-S5 (#233) |
| `BlockDropsEvent` experience accessors | Read | E5-S3 (#264), E9-S9 (#312) |
| Payload registration, optional, decode failure, size caps, configuration tasks | Read | E7-S2a (#255) |
| `Capabilities.Energy.ITEM`, item handler | Read | E9-S8 (#313), E9-S45 (#352), E9-S41 (#349), E12-S1 (#217) |
| Data-component ingredient | Read: `neoforge:components`; JSON not yet loaded in a game | E9-S28 (#332), E12-S1 (#217) |
| `[[mixins]]` keys, `compatibilityLevel`, refmap | Read | E9-S7 (#311) |
| Registry alias | Read: `DeferredRegister#addAlias` | The first PR that renames a block or item id |
| `logoFile`, `logoBlur`, `pack.png` | Read | E13-S2c (#191) |
| `c:nuggets/copper` | Read | E9-S11 (#315) |
| GUI sprite nine-slice `.mcmeta` | Read | E7-S4 (#213) |
| `MobEffectInstance` duration access | Read | E9-S35 (#342) |
| `FoodData.exhaustionLevel` access | Read | E9-S17 (#322), E9-S13 (#317) |
| Respawn and `PlayerChangedDimensionEvent` | Read | E0-S1 (#222), E9-S5 (#309) |

## DimensionType codec fields

Must land before E0-S1 (#222, §3.1 shared attributes and §13 Dimension), E0-S4 (#223).

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
`fieldOf("spawn_costs")`. That is not the whole story; see the next subsection.

### Whether another mod's biome modifier can add spawns to orevault:vault

Read 2026-10-31. Must land before E3-S5 (#233). Paths starting `net/neoforged` are in NeoForge's sources jar.

Yes, it can. Biome modifiers run once at server start, over every biome in the registry, datapack biomes
included. `net/neoforged/neoforge/server/ServerLifecycleHooks.java` line 93 calls `runModifiers(server)` from
`handleServerAboutToStart`, and lines 166 to 171 go through `biomeRegistry.listElements()` calling
`biome.modifiableBiomeInfo().applyBiomeModifiers(biomeHolder, biomeModifiers, registries)`.
`Biome#getMobSettings` then returns the modified result
(`net/minecraft/world/level/biome/Biome.java` lines 105 to 106,
`return this.modifiableBiomeInfo().get().mobSpawnSettings();`).

`neoforge:add_spawns` (`net/neoforged/neoforge/common/NeoForgeMod.java` line 243) is
`AddSpawnsBiomeModifier(HolderSet<Biome> biomes, WeightedList<SpawnerData> spawners)`, and it adds its spawns when
`phase == Phase.ADD && this.biomes.contains(biome)`
(`net/neoforged/neoforge/common/world/BiomeModifiers.java` lines 148 and 161 to 162). `biomes` is any holder set.
So `orevault:vault` is hit by a modifier that names it, by one using NeoForge's `neoforge:any` holder set
(`NeoForgeMod.java` line 332), and by a tag only if our biome is in that tag. The biome should stay out of broad
tags such as `#minecraft:is_overworld`, which is the form most mods use, but `neoforge:any` still reaches it.

So empty spawner lists keep mobs out by default, and do not prove zero spawning in a pack. The generator can close
the gap in one method. Natural spawning asks the generator, not the biome:
`net/minecraft/world/level/NaturalSpawner.java` line 318:

```java
return net.neoforged.neoforge.event.EventHooks.getPotentialSpawns(level, mobCategory, pos, generator.getMobsAt(biome != null ? biome : level.getBiome(pos), structureManager, mobCategory, pos));
```

`ChunkGenerator#getMobsAt` is public and not final
(`net/minecraft/world/level/chunk/ChunkGenerator.java` lines 449 to 471), so a Vault generator override returning
an empty list stops every biome-modifier spawn at this call. Two paths skip it. One is the fortress
branch at `NaturalSpawner.java` lines 312 to 316, which needs a fortress structure, and the Vault has none. The
other is `NaturalSpawner.java` line 352, `biome.value().getMobSettings()`, which is the chunk-generation mob pass; that runs
through `generator.spawnOriginalMobs` (`ChunkStatusTasks.java` line 167), which the Vault generator also controls.
The last call that can still add to the list is `LevelEvent.PotentialSpawns`
(`net/neoforged/neoforge/event/level/LevelEvent.java` line 122), which a mod would have to target on purpose. A
listener could cancel it for Vault levels, but that is a second hook for a rare case, and it is the Architect's call.

## ItemStack#hurtAndBreak and processDurabilityChange

Must land before E0-S11 (#226, ADR 0008) and E9-S7 (#311).

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
ADR 0008 chose plain Mixin with no local by index, so the `javap` check is not needed (item 9 under "Still to
read"). NeoForge's dependency list does name `io.github.llamalad7:mixinextras-neoforge` 0.5.4 (`ivy-26.1.2.95-ng2.xml`
line 162), for a later ticket that wants it (item 7).

## FTB Teams party join and disband

Must land before E0-S18 (#178), E0-S10 (#258), E0-S4 (#223), E3-S8 (#206). Read from
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

Must land before E6-S4 (#209, the spike) and E0-S6 (#224, ADR 0004).

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

### What promotion copies

Read 2026-10-31. Must land before E3-S6a (#237), which first stores `return_pos` on the player attachment, and
E6-S4 (#209), the carry-over spike. Paths in this subsection are in NeoForge's sources jar unless they start
`net/minecraft`.

`net/neoforged/neoforge/attachment/AttachmentInternals.java` lines 25 to 47, verbatim:

```java
25    private static <H extends AttachmentHolder> void copyAttachments(HolderLookup.Provider provider, H from, H to, Predicate<AttachmentType<?>> filter) {
26        if (from.attachments == null) {
27            return;
28        }
29        for (var entry : from.attachments.entrySet()) {
30            AttachmentType<?> type = entry.getKey();
31            if (type.serializer == null) {
32                continue;
33            }
34            @SuppressWarnings("unchecked")
35            var copyHandler = (IAttachmentCopyHandler<Object>) type.copyHandler;
36            if (filter.test(type)) {
37                Object copy = copyHandler.copy(entry.getValue(), to.getExposedHolder(), provider);
38                if (copy != null) {
39                    to.getAttachmentMap().put(type, copy);
40                }
41            }
42        }
43    }
44
45    public static void copyChunkAttachmentsOnPromotion(HolderLookup.Provider provider, AttachmentHolder.AsField from, AttachmentHolder.AsField to) {
46        copyAttachments(provider, from, to, type -> true);
47    }
```

The rule: every attachment with a serializer is copied, with no flag to opt in. One with no serializer is
skipped without a log. `AttachmentType.java` line 56 says the same: "Serializable attachments are copied from a
{@link ProtoChunk} to a {@link LevelChunk} on promotion." `orevault:vein_index` has a codec, so it is copied,
which is ADR 0004's Result A. E6-S4 still runs, because the ADR's rule needs its two halves in a real game, but
the expected answer is now read rather than guessed.

The copy is not the same object. The default handler (`AttachmentType.java` lines 76 to 100) writes the value
out and reads it back through the serializer, once per chunk, on the main thread, because the `LevelChunk`
constructor runs inside `ChunkStatusTasks#full` on `context.mainThreadExecutor()`
(`net/minecraft/world/level/chunk/status/ChunkStatusTasks.java` lines 176 to 209). Three consequences:

- The vein index, which is large and immutable (ADR 0004), is encoded and decoded on the main thread for every
  fresh chunk. `Builder#copyHandler` (lines 251 to 258) replaces that, and for an immutable value
  `(value, holder, provider) -> value` is correct. E6-S4 should measure the difference.
- `copyAttachments` has no `try`. A default handler that throws, on a reporter with problems (lines 88 to 90 and
  95 to 97), throws out of the `LevelChunk` constructor on the main thread. Saving is gentler:
  `AttachmentHolder#serializeAttachments` (lines 120 to 137) catches and logs.
- A value that a write refuses is dropped: a serializer returning `false` makes the handler return `null` (lines
  85 to 87), and line 38 skips it.

### The builder

`AttachmentType.java`, class `Builder<T>`, lines 163 to 310. `AttachmentType.builder(Supplier<T>)` or
`builder(Function<IAttachmentHolder, T>)` starts one; `serializable(...)` starts one for a `ValueIOSerializable`.

```java
182    public Builder<T> serialize(IAttachmentSerializer<T> serializer)
198    public Builder<T> serialize(MapCodec<T> codec)
210    public Builder<T> serialize(MapCodec<T> codec, Predicate<? super T> shouldSerialize)
237    public Builder<T> copyOnDeath()
251    public Builder<T> copyHandler(IAttachmentCopyHandler<T> cloner)
263    public Builder<T> sync(AttachmentSyncHandler<T> syncHandler)
274    public Builder<T> sync(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)
286    public Builder<T> sync(BiPredicate<IAttachmentHolder, ServerPlayer> sendToPlayer, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)
307    public AttachmentType<T> build()
```

Points that bite at T3 and T6:

- `serialize` takes a `MapCodec`, not a `Codec`. There is no `Codec` overload.
- Order matters. `copyOnDeath()` and `copyHandler` throw `IllegalStateException` unless `serialize` was called
  first (lines 238 to 239, 254 to 255), and `serialize` throws if called twice (lines 184 to 185).
- A failed read loses the whole attachment, not one field. The codec read is
  `parsingResult.orElseThrow(() -> buildException("read"))` (lines 215 to 216), and
  `AttachmentHolder#deserializeAttachments` (lines 159 to 163) catches that, logs "Failed to deserialize data
  attachment {}. Skipping.", and leaves the type absent. For `orevault:player`, a required field missing from an
  older tag would therefore reset `return_pos` on load with one log line. `orevault:player` therefore uses ADR 0005
  section 6's hand-written reader, so that `migrate()` sees the tag before
  anything decodes (ADR 0005). An attachment id with no registered type is also logged and skipped (lines 153 to
  157), so a rename needs the registry alias (item 12) or the data is gone at the next save.

`copyOnDeath()`: `AttachmentInternals.java` lines 52 to 59 run
`copyAttachments(from.registryAccess(), from, to, isDeath ? type -> type.copyOnDeath : type -> true)` from a
`PlayerEvent.Clone` listener. `ServerPlayer#restoreFrom` posts that event as
`EventHooks.onPlayerClone(this, oldPlayer, !restoreAll)` (`net/minecraft/server/level/ServerPlayer.java` line
1747), called from `PlayerList#respawn` line 409. A death copies only the `copyOnDeath` types; the non-death clone
(return from the End) copies every serializable type. The copy is a fresh value through the same handler, so
nothing may hold a reference to the old value across a respawn. ADR 0005's `copyOnDeath` line is confirmed as an
API.

### Writing from the generator thread

Must land before E6-S4 (#209).

The holder has no synchronisation. `AttachmentHolder.java` lines 40 to 51: a plain `Map` field, null until first
used, then `new IdentityHashMap<>(4)`, with no lock and no `volatile`. `getData` writes too: lines 76 to 80 put
the default into the map if the type is absent. So "safe from a worker" depends only on whether another thread
can reach the same `ProtoChunk`'s map at the time. From the source, it cannot, for a chunk being generated:

- `ChunkAccess#setData` is `markUnsaved()` then the holder (`ChunkAccess.java` lines 511 to 514). `unsaved` is
  `volatile` (line 65) and `markUnsaved` only sets it (lines 262 to 264).
- `syncData` does nothing on a `ProtoChunk`: only `LevelChunk` overrides it (`LevelChunk.java` line 752), so the
  empty default on `IAttachmentHolder` (lines 122 to 124) runs.
- Saves skip a chunk still in generation. `ChunkMap#saveAllChunks` (lines 425 to 431) and `saveChunkIfNeeded`
  (lines 721 to 722) only take `ImposterProtoChunk` or `LevelChunk`. The third path, `scheduleUnload` (lines 513
  to 526), which does save a true `ProtoChunk`, waits on `getSaveSyncFuture()`, and generation adds its own
  dependency to that future while its ref count is above zero (`GenerationChunkHolder.java` lines 236 to 238). So
  the partial-status save in ADR 0004's restart half happens after the worker has finished.
- Promotion is the only other reader, and it is a later step on the main thread. That a step's writes are seen by
  the next step relies on the `CompletableFuture` chain between them; that is an inference from the steps'
  structure, not a line that says so.

So ADR 0004's "on its worker, which owns that chunk" holds as written, for `setData` on the `ProtoChunk` that
`fillFromNoise` was handed. It holds only for that chunk: a neighbour's `ProtoChunk` reached through a region is
not owned, and `getData` from the main thread on anything reached through `getLatestChunk()` is a race, since it
can write. `getExistingDataOrNull` (lines 87 to 93) does not write, but still reads an unguarded map. This is a
read of the code, not a run; the spike's restart half remains the check.

## ChunkEvent.Load new-chunk flag

Read 2026-10-31. Must land before E6-S4 (#209). `net/neoforged/neoforge/event/level/ChunkEvent.java` lines 46 to
63, in NeoForge's sources jar:

```java
46    public static class Load extends ChunkEvent<LevelChunk> {
47        private final boolean newChunk;
...
50        public Load(LevelChunk chunk, boolean newChunk) {
...
56         * {@return true if this is a newly-generated chunk, instead of one loaded from disk}
...
60        public boolean isNewChunk() {
61            return newChunk;
62        }
```

The accessor is `isNewChunk()`. The event is posted on `NeoForge.EVENT_BUS` (`ChunkEvent.java` line 19). The
javadoc at lines 43 and 44 sets a rule for every listener: the event fires before the `LevelChunk` is promoted to
`FULL`, and "Interactions with the {@link LevelChunk#getLevel() level} must be delayed until the next game tick to
prevent deadlocking the game." The chunk's own attachment is fine to touch there, and that is all ADR 0004's
listener does to the chunk; its explored map write is to the listener's own data.

The flag is the second constructor argument, `!(protoChunk instanceof ImposterProtoChunk)`
(`net/minecraft/world/level/chunk/status/ChunkStatusTasks.java` line 204). A chunk saved at `FULL` comes back from disk as
`new ImposterProtoChunk((LevelChunk)chunk, false)`, and one saved at a partial status comes back as a plain
`ProtoChunk` (`net/minecraft/world/level/chunk/storage/SerializableChunkData.java` lines 289, 306 and 349). So the
flag is false for the first and true for the second when it finishes after a restart, which ADR 0004's restart
half relies on: "newly generated" here means newly promoted, as its javadoc does not quite say.

## Client payload handler registration

Read 2026-10-31. Must land before E7-S2a (#255). Paths are in NeoForge's sources jar unless they start
`net/minecraft`.

### Registration and threads

`RegisterPayloadHandlersEvent` is a mod-bus event (`IModBusEvent`) with one method,
`registrar(String version)` (`net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent.java` lines 31 to
42). `PayloadRegistrar` (`net/neoforged/neoforge/network/registration/PayloadRegistrar.java`) has, for play:

```java
44    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
52    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
60    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
70    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(...)
```

with `configuration...` and `common...` twins, and `executesOn(HandlerThread)` (line 180), `versioned(String)`
(line 196) and `optional()` (line 209), each returning a modified copy.

A client-only handler registration does exist. The two-argument `playToClient` at line 52 registers the payload in
common code with no handler; its javadoc says "The missing handler must be registered via {@code
RegisterClientPayloadHandlersEvent}". That event is
`net/neoforged/neoforge/client/network/event/RegisterClientPayloadHandlersEvent.java`, a mod-bus event with
`register(type, handler)` (line 32). So a common class never needs to name a client handler. A placeholder handler
in common code is not needed. The check is loud: `ClientNetworkRegistry.java` lines 71 to 91 throw
`IllegalStateException("Some clientbound payloads are missing client-side handlers: " ...)` when the client network
registry is set up, so a forgotten client handler stops the client at start.

Handlers already run on the main thread. `PayloadRegistrar.java` line 29 sets `HandlerThread.MAIN` as the default,
and lines 224 to 229 wrap each handler in `MainThreadPayloadHandler`, whose whole body is
`context.enqueueWork(() -> this.handler().handle(payload, context));`
(`net/neoforged/neoforge/network/handling/MainThreadPayloadHandler.java` line 16). A handler that also calls
`context.enqueueWork` is harmless and redundant. `RegisterClientPayloadHandlersEvent#register(type, handler)` also
uses `MAIN` (line 33).

`optional()`: a payload is required unless marked (line 28, `optional = false`). The javadoc at lines 203 to 208:
"If any non-optional payloads are missing during a connection attempt, the connection will fail." So ADR 0006's
required default is right: a client without the mod is refused.

### Decode failure

ADR 0006's expectation holds: the connection closes. The registered codec is called from
`net/minecraft/network/protocol/common/custom/CustomPacketPayload.java`'s `decode`, which rethrows any
`RuntimeException` as "Failed decoding custom payload" with the payload's id. `PacketDecoder#decode`
(`net/minecraft/network/PacketDecoder.java`) rethrows it, and `Connection#exceptionCaught` (lines 109 to 135)
disconnects with "Internal Exception", logging at ERROR for play and configuration. A codec that leaves bytes unread
is the same failure, as an `IOException` "was larger than I expected" from `PacketDecoder`. So a bad decode kicks
that one player. An unregistered id logs "No registration for payload {}; refusing to decode."
(`NetworkRegistry.java` line 252) and falls through to the discarded payload codec.

### Size caps (item 16)

Registered payloads are not held to vanilla's caps. `ClientboundCustomPayloadPacket`'s 1048576 and
`ServerboundCustomPayloadPacket`'s 32767 are only passed to `DiscardedPayload.codec(id, ...)`, the fallback
(`ClientboundCustomPayloadPacket.java` line 18, `ServerboundCustomPayloadPacket.java` line 16). `CustomPacketPayload`'s
`findCodec` asks `NetworkRegistry.getCodec` first and uses the fallback only when that returns null, and a registered
codec has no such limit.

Above the frame limit NeoForge splits. `net/neoforged/neoforge/network/filters/GenericPacketSplitter.java` lines 96
to 126 encode the packet and, if it is over `CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH` (8388608) with a
compressor or `MAXIMUM_COMPRESSED_LENGTH` (2097152) without, send it in `SplitPacketPayload` parts. It applies
only to a compatible remote (`isRemoteCompatible`, line 183).

ADR 0006's own bounds (about 10 KB up, about 1.07 MB down) stay under both split limits, so nothing changes. Vanilla's
caps stop being a rule from the transport; the ADR's bounds stay as our own limits, and they are what protects
the decoder.

### Configuration tasks (item 16)

`RegisterConfigurationTasksEvent` is a mod-bus event, posted once per connecting client from
`net/minecraft/server/network/ServerConfigurationPacketListenerImpl.java` (the line reading
`ModLoader.postEventWithReturn(new ...RegisterConfigurationTasksEvent(this))`). It has
`register(ConfigurationTask task)` and `getListener()`
(`net/neoforged/neoforge/network/event/RegisterConfigurationTasksEvent.java` lines 38 and 57). The javadoc recommends
`ICustomConfigurationTask` when a task sends payloads, with one method to write,
`void run(Consumer<CustomPacketPayload> sender)` (`net/neoforged/neoforge/network/configuration/ICustomConfigurationTask.java`
line 32). A task ends when something calls `listener.finishCurrentTask(ConfigurationTask.Type)`
(`IServerConfigurationPacketListenerExtension.java` line 22); `CheckExtensibleEnums.java` is the worked example, ending
its task from the reply handler through `context.finishCurrentTask(TYPE)` (line 159). So ADR 0006's rejected
layout check is one task and one `configurationBidirectional` payload pair. E7-S2a can revisit it with that known.

## BlockDropsEvent experience accessors

Must land before E5-S3 (#264) and E9-S9 (#312, vanilla XP bonus).

Read 2026-10-31. Must land before E5-S3 (#264) and E9-S9 (#312).

The accessors are as E5-S3 expected. `net/neoforged/neoforge/event/level/BlockDropsEvent.java` lines 57, 113 and
123:

```java
57        this.experience = EnchantmentHelper.processBlockExperience(level, tool, state.getExpDrop(level, pos, blockEntity, breaker, tool));
...
113    public int getDroppedExperience() {
...
123    public void setDroppedExperience(int experience) {
124        Preconditions.checkArgument(experience >= 0, "May not set a negative experience drop.");
```

The value is worked out in the event's constructor, before any listener runs, and the setter's javadoc (line 118)
calls it "the true value, after enchantments have been applied". So Silk Touch has already applied by the time our
listener sees it: `data/minecraft/enchantment/silk_touch.json` carries a `minecraft:block_experience` effect of
`{"type": "minecraft:set", "value": 0.0}`, and `EnchantmentHelper#processBlockExperience`
(`net/minecraft/world/item/enchantment/EnchantmentHelper.java` lines 114 to 118) runs it. Fortune has no effect
entry at all, so it never touches XP, and the Fortune stage's re-roll of `state.getDrops(...)` (§11) cannot change
the XP. E5-S3's helper only has to raise the value, on top of the amount already in the event.

`net/neoforged/neoforge/common/CommonHooks.java` lines 574 to 587 is the rest:

```java
575        BlockDropsEvent event = new BlockDropsEvent(level, pos, state, blockEntity, drops, breaker, tool);
576        NeoForge.EVENT_BUS.post(event);
577        if (!event.isCanceled()) {
578            for (ItemEntity entity : event.getDrops()) {
579                level.addFreshEntity(entity);
580            }
581            // Always pass false for the dropXP (last) param to spawnAfterBreak since we handle XP.
582            state.spawnAfterBreak((ServerLevel) level, pos, tool, false);
583            if (event.getDroppedExperience() > 0) {
584                state.getBlock().popExperience(level, pos, event.getDroppedExperience());
```

Cancelling the event drops the XP as well (lines 96 to 98 of `BlockDropsEvent.java`). §3.4 says vanilla XP is never
suppressed, so the listener must never cancel it, even for a stage that consumes the block: clear the list
instead, as §11's Consume stage says. A consuming stage's empty list still pays XP.

Earlier notes from the Minecraft call sites:

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

## GUI sprite nine-slice .mcmeta format

Must land before E7-S4 (#213, S28).

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

Must land before E10-S3 (#373) and E0-S4 (#223).

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
boolean forced)` (line 1520) wraps the last. NeoForge's forced chunks go through
`net.neoforged.neoforge.common.world.chunk.ForcedChunkManager` and `TicketController` (below).

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
`ForcedChunkManager.activateAllDeactivatedTickets(level, savedTickets)`.

`ForcedChunkManager.activateAllDeactivatedTickets` is public and static, first settled from the call site
(Developer, round 2 DEV2-5). `MinecraftServer.java` line 563 calls it as
`net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.activateAllDeactivatedTickets(level,
savedTickets)`: through the class name, with no instance, so it is static; and from `MinecraftServer`, in
package `net.minecraft.server`, on a class in `net.neoforged.neoforge.common.world.chunk`, so the method and
its class are public. It takes the level and the `TicketStorage`, in that order, as restore will pass
them.

`ForcedChunkManager.java` confirms that and adds one thing the call site could not show: lines 107 and 108 read
`@ApiStatus.Internal` then `public static void activateAllDeactivatedTickets(ServerLevel level, TicketStorage
saveData)`. It is public and marked internal. There is no supported alternative, so E3-S6a calls it with a
comment saying so, the same footing as `forgeGetWorldMap()` in ADR 0002.

### TicketController, and when validation runs

Read 2026-10-31. Must land before E3-S6a (#237), the restore, and E10-S3 (#373), the Vault Anchor. Paths are in
NeoForge's sources jar, under `net/neoforged/neoforge/common/world/chunk/`.

`TicketController.java` lines 27 to 69 is a record with three force methods:

```java
27    public record TicketController(Identifier id, @Nullable LoadingValidationCallback callback) {
37        public TicketController(Identifier id) {
47        public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean forceNaturalSpawning) {
57        public boolean forceChunk(ServerLevel level, Entity owner, int chunkX, int chunkZ, boolean add, boolean forceNaturalSpawning) {
67        public boolean forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
```

Registration is `RegisterTicketControllersEvent#register(TicketController)`, on the mod bus
(`RegisterTicketControllersEvent.java` lines 12 to 31). `forceChunk` throws `IllegalArgumentException` for a
controller that was not registered (`ForcedChunkManager.java` lines 85 to 87). With `add` true and a new ticket it
calls `level.getChunk(chunkX, chunkZ)`, so it is a synchronous load (lines 95 to 98).

Validation runs at activation, not at `readStoredTickets`. `ForcedChunkManager.java` lines 107 to 138:

```java
111        if (blockForcedChunks.hasNoDeactivatedTickets() && entityForcedChunks.hasNoDeactivatedTickets()) {
112            //Skip if there are no deactivated tickets at all
113            return;
114        }
...
125            controllers.forEach((value) -> {
...
131                    value.getValue().callback().validateTickets(level, new TicketHelper(saveData, value.getKey(), ownedBlockTickets, ownedEntityTickets));
...
136        blockForcedChunks.activateAllDeactivatedSources();
137        entityForcedChunks.activateAllDeactivatedSources();
```

NeoForge itself calls that only from `MinecraftServer#prepareLevels`, so it never does this for a level added
later. But ADR 0002's creation step 13 calls the same method, and that call runs the callback, on the calling
thread, before any of the level's forced tickets go live, with the level already in the server's map. So ADR 0002
step 13's third possibility ("or never") does not arise: the Vault Anchor's validation is the
`LoadingValidationCallback` passed to its controller, and step 13 needs no separate call. Two limits. A level with
no deactivated tickets gets no callback (line 111). And since the tickets are not live yet, a callback that reads
the block from `level` loads that chunk synchronously, once per Anchor; `ServerStartedEvent` is on the main thread,
so this should work, but it is untested here. E10-S3 checks it across a restart.

`readStoredTickets` (lines 213 to 232) makes one check, during the `ServerLevel` constructor: a controller id that
is not registered is dropped with a WARN, "it will be removed from the level save". `RegistrationEvents.init`
calls `ForcedChunkManager.init()` (`net/neoforged/neoforge/internal/RegistrationEvents.java` line 20), which posts
the registration event during mod loading, so a controller registered on the mod bus is known before any Vault's
ticket file is read. Removing the controller in a later version deletes those tickets at next load.

The `TicketHelper` the callback gets (`TicketHelper.java` lines 36 to 110) has `getBlockTickets()`,
`getEntityTickets()`, `removeAllTickets(BlockPos | UUID)` and `removeTicket(owner, chunk, forceNaturalSpawning)`.

For ADR 0002's `unload` step 2 ("Release the team's Vault Anchor tickets through the mod's `TicketController`"):
outside the callback there is no list-and-remove. `TicketController` only has `forceChunk` with one owner and one
chunk; `ForcedChunkManager.forceChunk` and `TicketOwner` are package-private (lines 83 and 237), and `TicketTracker`
has no public way to list its sources. So the release needs each Anchor's owner, chunk and spawning flag from the
mod's own data. E10-S3 stores them.

NeoForge's ticket types (`net/neoforged/neoforge/common/NeoForgeMod.java` lines 396 to 399) are
`new TicketType(0L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION)`, with no `FLAG_PERSIST` and no
`FLAG_KEEP_DIMENSION_ACTIVE`. They persist through the `neo_ticket_data` field instead
(`ForcedChunkManager.java` line 187), and a level stays active through `ForcedChunkManager.hasForcedChunks(this)`
(`net/minecraft/server/level/ServerLevel.java` line 412).

`MinecraftServer#markWorldsDirty()` is at line 1899.

## Capabilities.Energy.ITEM and the item-handler capability

Must land before E9-S8 (#313), E9-S45 (#352), E9-S41 (#349, Seam Courier), E12-S1 (#217) for §9's Unverified list.

Read 2026-10-31. Paths are in NeoForge's sources jar unless they start `net/minecraft`.

`net/neoforged/neoforge/capabilities/Capabilities.java` lines 21 to 52:

```java
22        public static final BlockCapability<EnergyHandler, @Nullable Direction> BLOCK = ...
24        public static final ItemCapability<EnergyHandler, ItemAccess> ITEM = ItemCapability.create(create("energy_handler"), EnergyHandler.class, ItemAccess.class);
...
38        public static final BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> BLOCK = BlockCapability.createSided(create("item_handler"), ResourceHandler.asClass());
49        public static final ItemCapability<ResourceHandler<ItemResource>, ItemAccess> ITEM = ...
```

So `Capabilities.Energy.ITEM` gives a `net.neoforged.neoforge.transfer.energy.EnergyHandler`, and its context type is
`net.neoforged.neoforge.transfer.access.ItemAccess`. There is no `IEnergyStorage` and no `IItemHandler` on this path.

`EnergyHandler` (`net/neoforged/neoforge/transfer/energy/EnergyHandler.java`):

```java
39    long getAmountAsLong();
55    default int getAmountAsInt() {
75    long getCapacityAsLong();
94    default int getCapacityAsInt() {
110    int insert(int amount, TransactionContext transaction);
124    int extract(int amount, TransactionContext transaction);
```

`insert` returns the amount taken, and throws `IllegalArgumentException` on a negative amount (lines 103 to 106). It
takes a transaction: nothing sticks unless the transaction commits. `Transaction.openRoot()`
(`net/neoforged/neoforge/transfer/transaction/Transaction.java` line 73) in a try-with-resources, then `commit()`.

Getting one for a tool: `IItemStackExtension#getCapability(ItemCapability<T, C> capability, C context)`
(`net/neoforged/neoforge/common/extensions/IItemStackExtension.java` line 350), or `ItemAccess#getCapability`
(`ItemAccess.java` line 143). An `ItemAccess` for a slot is `ItemAccess.forPlayerSlot(Player player, int slot)`
(line 87). `forPlayerInteraction(player, hand)` (line 46) is the hand-held form. A write goes back through the access,
so a `forStack` access (line 121), which can only change the stack it wraps, is the one to avoid unless the item
is known not to swap. For Tool charge and Resonant Dynamo, `forPlayerSlot` is right.

Block item handler: `Capabilities.Item.BLOCK` gives `ResourceHandler<ItemResource>`
(`net/neoforged/neoforge/transfer/ResourceHandler.java`), queried with `level.getCapability(cap, pos, state,
blockEntity, side)` (`net/minecraft/world/level/block/entity/HopperBlockEntity.java` line 450). Its insert methods:

```java
161    int insert(int index, T resource, int amount, TransactionContext transaction);
181    default int insert(T resource, int amount, TransactionContext transaction) {
```

The helper for Seam Courier, which starts from a stack, is `ItemUtil#insertItemReturnRemaining(handler, stack, simulate,
transaction)` (`net/neoforged/neoforge/transfer/item/ItemUtil.java` lines 44 to 47), which returns the leftover and opens
its own root transaction when `transaction` is `null`. `net/minecraft/world/level/block/CrafterBlock.java` line
204 calls it that way. `HopperBlockEntity.java` line 398's "Use IItemHandler capability instead" is a stale comment.

Must land before E9-S8 (#313), E9-S45 (#352), E9-S41 (#349) and E12-S1 (#217). These names can leave §9's Unverified
list.

## Data-component ingredient

Must land before E9-S28 (#332, the Resonance Crystal recipe) and E12-S1 (#217).

Read 2026-10-31. The class is `net.neoforged.neoforge.common.crafting.DataComponentIngredient`. Its `type` id is
`neoforge:components` (`net/neoforged/neoforge/common/NeoForgeMod.java` line 359,
`INGREDIENT_TYPES.register("components", ...)`), and the JSON key that picks a custom ingredient is
`neoforge:ingredient_type`, not `type` (`common/crafting/IngredientCodecs.java` line 22). The fields are in
`DataComponentIngredient.java` lines 41 to 43: `items` (a holder set), `components` (a `DataComponentPatch`) and
`strict`, optional, default false.

So the Resonance Crystal's attuned slot, from those fields:

```json
{
  "neoforge:ingredient_type": "neoforge:components",
  "items": "#c:raw_materials",
  "components": { "orevault:attuned": {} }
}
```

`strict` false is right: `test` (lines 57 to 70) then allows other components on the stack. A patch value is compared
with `equals` (line 76), so a marker with no fields (ADR 0005) matches any Attuned stack. If `orevault:attuned` ever
gained a field, the recipe would match only the listed value. `Tags.Items.RAW_MATERIALS` is `c:raw_materials`
(`Tags.java` line 798). The Java factories take `ItemLike...` (lines 133 onwards), not a tag, so the JSON recipe is the
simple path. The exact JSON for the marker's value depends on the component's codec, and this form has not been
loaded in a game: E9-S28's gametest is the check. Must land before E9-S28 (#332) and E12-S1 (#217).

What the patched tree shows is the hook it plugs into.
`net/minecraft/world/item/crafting/Ingredient.java`:

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

Vanilla's own ingredient is a `HolderSet<Item>` and matches by item only (line 79), so the custom ingredient is needed.

## MobEffectInstance duration access

Must land before E9-S35 (#342, "potion durations do not tick down").

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

Must land before E9-S17 (#322, Long Delve) and E9-S13 (#317, hunger reduction built once).

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

Must land before E0-S1 (#222, §11 Vault Session State) and E9-S5 (#309); old #149.

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

## Mixin entry, compatibility level and refmap

Read 2026-10-31. Must land before E9-S7 (#311). This answers ADR 0008's first-load check 1 from source; E9-S7 still
runs it at first load. Paths are in `loader-11.0.15-sources.jar`.

`net/neoforged/fml/loading/moddiscovery/ModFileParser.java` lines 61 to 83: `[[mixins]]` takes `config` (required;
"Missing \"config\" in [[mixins]] entry" otherwise), `requiredMods` (a list of mod ids, default empty) and
`behaviorVersion` (a version string, default FML's, checked in `MixinFacade.java` lines 206 to 228). So ADR 0008's
`config = "orevault.mixins.json"` is correct. One trap: lines 80 to 82 catch any exception there, log "Failed to load
mixin configs from mod file" and return an empty list, so a malformed entry loads no mixins with no crash, which
`defaultRequire: 1` cannot catch because nothing runs. ADR 0008's gametest is the guard.

`compatibilityLevel`: `JAVA_25` exists. `javap -p` on
`~/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin/0.17.3+mixin.0.8.7/.../sponge-mixin-0.17.3+mixin.0.8.7.jar`, the
version named in `ivy-26.1.2.95-ng2.xml` line 152, lists `MixinEnvironment$CompatibilityLevel.JAVA_25` as the last
constant. `FMLMixinService.java` lines 123 to 130 set the minimum to `JAVA_21` and no maximum. NeoForge's own
`neoforge.mixins.json` sets no level at all. Use `"JAVA_25"`, since the mod compiles for Java 25.

Refmap: not needed. The names at runtime are the names at compile time: Mojang's unmodified
`.gradle/caches/minecraft/versions/26.1.2/client.jar` already carries `storageSource` and the rest under `javap`, so
26.1 is not obfuscated and there is nothing for a refmap to map. NeoForge's own `neoforge.mixins.json` has no `refmap`
key either; it reads `required`, `package`, `mixins`, `client`, `injectors` and `minVersion`.

## Registry alias for a renamed id

Read 2026-10-31. Must land before the first PR that renames a registered block or item id. Paths are in NeoForge's
sources jar unless they start `net/minecraft`.

`net/neoforged/neoforge/registries/DeferredRegister.java` lines 295 to 307:

```java
303    public void addAlias(Identifier from, Identifier to) {
304        if (seenRegisterEvent)
305            throw new IllegalStateException("Cannot add aliases to DeferredRegister after RegisterEvent has been fired.");
```

So the call goes in the `@Mod` constructor, or wherever the `DeferredRegister` is set up. The javadoc: "Any registry
lookups that target the first name will resolve as the second name, if the first name is not present."
`IRegistryExtension#addAlias` (`IRegistryExtension.java` line 75) is the same on a registry. Chains resolve, and a
loop throws (`BaseMappedRegistry.java` lines 64 to 87). Lookups go through it:
`net/minecraft/core/MappedRegistry.java` lines 149, 164, 169, 184 and 216 all call `resolve`. NeoForge's registry
snapshot carries aliases too (`RegistryManager.java` line 199).

An alias is permanent. A save holding the old id is only rewritten when that chunk or inventory is next saved, so the line
stays for good, with a golden test as ADR 0005 asks. The attachment type registry is a registry like the others,
so a renamed attachment id is covered too (see the attachment read above).

## Mod logo keys

Read 2026-10-31. Must land before E13-S2c (#191).

`logoFile` and `logoBlur` are read from the `[[mods]]` entry, falling back to the file's top level
(`net/neoforged/fml/loading/moddiscovery/ModInfo.java` lines 86 to 91, in the FML jar). `logoBlur` defaults to `true`.

`logoFile` resolves from the root of the mod's pack, so from the jar root, as expected. In NeoForge's sources jar,
`net/neoforged/neoforge/client/gui/ModListScreen.java` line 399:
`packResources.getRootResource(logoFile.split("[/\\\\]"))`. `logoFile="logo.png"` with `logo.png` at the resources
root, as `docs/TEAM_REQUIREMENTS.md` says, is right. A subfolder works too, since the path is split.

`logoBlur`: line 409 reads `var filter = selectedMod.getLogoBlur() ? FilterMode.LINEAR : FilterMode.NEAREST;`. The
default is linear filtering. For a logo drawn at 64 and scaled up by whole numbers, set `logoBlur=false`, or it is
smoothed when the Mods screen draws it. This is the one answer the Designer was waiting on.

No per-mod entry appears in the resource pack screen, so `pack.png` would not be used.
`net/neoforged/neoforge/resource/ResourcePackLoader.java` line 119 computes `isRequired` from
`modFileInfo.showAsResourcePack()`, and lines 141 to 145 add a mod's pack to the screen only if that is true; otherwise it goes
in `hiddenPacks`, which line 152 folds into one "Mod Resources" entry. §13's `logoFile`, not `pack.png`, stands.

## Also found

- §6.1 Stone Memory's tier 4 nugget question (`c:nuggets/copper`): the tag exists and vanilla fills it. Must land
  before E9-S11 (#315). `net/neoforged/neoforge/common/Tags.java` line 706,
  `public static final TagKey<Item> NUGGETS_COPPER = tag("nuggets/copper");`, and NeoForge's
  `data/c/tags/item/nuggets/copper.json` holds `minecraft:copper_nugget`. `Items.COPPER_NUGGET` is
  `net/minecraft/world/item/Items.java` line 1906, and `data/minecraft/tags/item/metal_nuggets.json` lists it too.
- `c:ores`, the block tag. Must land before E4-S2 (#238). `Tags.java` class `Blocks`, line 201:
  `public static final TagKey<Block> ORES = tag("ores");`, and `tag` is
  `BlockTags.create(Identifier.fromNamespaceAndPath("c", name))` (lines 330 to 332). So the Java name is
  `Tags.Blocks.ORES`. There is an item twin, `Tags.Items.ORES` (line 744), which is not the one the classifier
  wants. NeoForge's `data/c/tags/block/ores.json` lists `#c:ores/coal`, `copper`, `diamond`, `emerald`, `gold`,
  `iron`, `lapis`, `netherite_scrap`, `redstone` and `quartz`, each of which leans on the vanilla tag
  (`ores/iron.json` is `#minecraft:iron_ores`). So ancient debris and nether quartz are in `#c:ores`; ADR 0009 and
  E4-S2 already leave them out by having no overworld feature.

## Still to read

Nothing. Every item is read or marked not needed, all on 2026-10-31 from the sources under Provenance. Numbers are kept
because ADRs and tickets cite them.

1. `BlockDropsEvent` experience accessors. Read: section "BlockDropsEvent experience accessors". Before E5-S3 (#264)
   and E9-S9 (#312).
2. `Capabilities.Energy.ITEM` and the item handler. Read: section "Capabilities.Energy.ITEM and the item-handler
   capability". Before E9-S8 (#313), E9-S45 (#352), E9-S41 (#349) and E12-S1 (#217).
3. The data-component ingredient. Read: section "Data-component ingredient". Before E9-S28 (#332) and E12-S1 (#217).
4. `AttachmentInternals`, the `AttachmentType` builder, `copyOnDeath()`, generator-thread writes. Read: section
   "ProtoChunk attachment carry-over". Before E3-S6a (#237) and E6-S4 (#209).
5. `RegisterPayloadHandlersEvent`, `PayloadRegistrar`, client-only registration, `optional()`, decode failure. Read:
   section "Client payload handler registration". Before E7-S2a (#255).
6. `TicketController`'s API and validation timing. Read: section "Chunk ticket API and forced tickets after
   createLevels". Before E3-S6a (#237) and E10-S3 (#373).
7. MixinExtras. Not needed: ADR 0008 uses plain Mixin only. For a later ticket, it is on NeoForge's dependency list
   at 0.5.4 (section "Local variables").
8. `c:nuggets/copper`. Read: section "Also found". Before E9-S11 (#315).
9. `javap -c -p -l` on `ItemStack#hurtAndBreak`. Not needed: ADR 0008 uses no local by index or ordinal.
10. `[[mixins]]` keys, `compatibilityLevel`, refmap. Read: section "Mixin entry, compatibility level and refmap".
    Before E9-S7 (#311).
11. The `ChunkEvent.Load` new-chunk accessor. Read: section "ChunkEvent.Load new-chunk flag". Before E6-S4 (#209).
12. The registry alias API. Read: section "Registry alias for a renamed id". Before the first PR that renames a block
    or item id.
13. Clean visibility of the five members. Read: Provenance. Before E3-S6a (#237); for the placement fields, before E4-S2
    (#238) only if its codec path fails.
14. The `c:ores` key. Read: section "Also found". Before E4-S2 (#238).
15. Biome modifiers and `orevault:vault`. Read: section "DimensionType codec fields". Before E3-S5 (#233).
16. Payload size caps and `RegisterConfigurationTasksEvent`. Read: section "Client payload handler registration".
    Before E7-S2a (#255).
17. `logoFile`, `logoBlur`, per-mod pack entry. Read: section "Mod logo keys". Before E13-S2c (#191).

Unverified, because a read of the source cannot settle it, each with the story that checks it in a running game:
whether an attachment written on a `ProtoChunk` survives both halves of the spike (E6-S4, #209); the JSON form of the
data-component ingredient at load (E9-S28, #332); a Vault Anchor callback reading its block during restore (E10-S3,
#373); the mixin config at first load (E9-S7, #311); the `assumeMapUnsafe(CompoundTag.CODEC.xmap(...))` wrapper on
`orevault:player`, first run by #237's gametest (E3-S6a).
