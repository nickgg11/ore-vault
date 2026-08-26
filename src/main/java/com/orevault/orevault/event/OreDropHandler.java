package com.orevault.orevault.event;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.entity.ResonanceOrbEntity;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All block-drop and break-side effects for the Vault (design spec sections 4 and 6):
 * Resonance orbs, Tithe, Greedy Seams, Ore Doubling (raw/dust/clump/shards/crystals),
 * Smelter's Intuition, Runic Attunement, Stone Memory, Stone Curse, Ancient Knowledge,
 * Volatile Veins (with pity), Twin Veins and Vault Echo vein-completion triggers,
 * Automated Extraction.
 */
public final class OreDropHandler {
    /** Breaking player for the current break→drops sequence (used by Ore Sense fortune). */
    public static final ThreadLocal<ServerPlayer> ACTIVE_MINER = new ThreadLocal<>();

    private OreDropHandler() {
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        ACTIVE_MINER.remove();
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel) || !TeamHelper.isVaultDimension(level)) {
            return;
        }
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(serverLevel.getServer(), teamId);
        BlockState state = event.getState();
        Block block = state.getBlock();
        BlockPos pos = event.getPos();

        Player breaker = event.getBreaker() instanceof Player p ? p : null;
        boolean machineBroke = breaker == null;
        ServerPlayer serverBreaker = breaker instanceof ServerPlayer sp ? sp : null;

        // Stats for any player breaker in the vault.
        if (serverBreaker != null) {
            PlayerStats stats = data.statsFor(serverBreaker.getUUID());
            stats.addBlocks(1);
            stats.trackY(pos.getY());
        }

        if (OreClassifier.isOre(block)) {
            handleOre(event, serverLevel, data, teamId, serverBreaker, machineBroke, state, block, pos);
        } else if (block == Blocks.STONE || block == Blocks.DEEPSLATE) {
            handleStone(event, serverLevel, data, serverBreaker, block, pos);
        }
    }

    // --- ores -------------------------------------------------------------------

    private static void handleOre(BlockDropsEvent event, ServerLevel level, OreVaultTeamData data,
                                  UUID teamId, ServerPlayer breaker, boolean machineBroke,
                                  BlockState state, Block block, BlockPos pos) {
        OreClassifier.OreClass oreClass = OreClassifier.classify(block);
        long baseResonance = switch (oreClass) {
            case COMMON -> 2;
            case UNCOMMON -> 5;
            case RARE -> 10 + level.getRandom().nextInt(6);
        };

        // Automated Extraction: machines award 50%/100% of normal Resonance, and only in
        // ticket-loaded chunks.
        if (machineBroke) {
            int tier = data.nodeTier("automated_extraction");
            if (tier <= 0 || !isTicketLoaded(level, pos)) {
                return;
            }
            double mult = tier == 1 ? 0.5 : 1.0;
            ResonanceSystem.addResonance(level.getServer(), teamId, Math.max(1, Math.round(baseResonance * mult)), null);
            return;
        }

        // Player-driven break.
        PlayerStats stats = data.statsFor(breaker.getUUID());
        stats.addOre(BuiltInRegistries.BLOCK.getKey(block).toString(), 1);
        stats.addBlocks(1);
        stats.trackY(pos.getY());

        boolean tithe = data.isTradeoffActiveFor("tithe", breaker.getUUID());
        boolean greedy = data.isTradeoffActiveFor("greedy_seams", breaker.getUUID());
        boolean volatileVeins = data.isTradeoffActiveFor("volatile_veins", breaker.getUUID());

        long resonance = baseResonance;
        boolean titheConsumed = false;
        if (tithe && level.getRandom().nextFloat() < 0.25F) {
            // The Vault consumes the block: no drops, 1.75x Resonance.
            titheConsumed = true;
            event.getDrops().clear();
            event.setDroppedExperience(0);
            resonance = Math.round(baseResonance * 1.75);
        }
        if (greedy) {
            // 2x drops, 0.5x Resonance.
            doubleDrops(event);
            resonance = Math.max(1, resonance / 2);
        }

        if (!titheConsumed) {
            applyOreDropNodes(event, level, data, breaker, block, oreClass);
        }

        // Resonance: Tithe consumed ores add directly to the pool; regular ores spawn orbs.
        if (titheConsumed) {
            ResonanceSystem.addResonance(level.getServer(), teamId, resonance, breaker.getUUID());
        } else {
            ResonanceOrbEntity.spawn(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), resonance, teamId);
        }

        // Ancient Knowledge: bonus XP per ore.
        int ak = data.nodeTier("ancient_knowledge");
        if (ak > 0) {
            event.setDroppedExperience(event.getDroppedExperience() + switch (ak) {
                case 1 -> 1;
                case 2 -> 2;
                default -> 4;
            });
        }

        // Volatile Veins disappearance roll (post-break).
        if (volatileVeins && !titheConsumed) {
            rollVolatileVeins(level, data, breaker, block, pos);
        }

        // Vein completion: Twin Veins + Vault Echo.
        if (!titheConsumed) {
            onVeinCompletion(level, data, breaker, block, pos);
        }
    }

    private static void applyOreDropNodes(BlockDropsEvent event, ServerLevel level, OreVaultTeamData data,
                                          ServerPlayer breaker, Block block, OreClassifier.OreClass oreClass) {
        // Smelter's Intuition
        int smelter = data.nodeTier("smelters_intuition");
        if (smelter > 0) {
            float chance = switch (smelter) {
                case 1 -> 0.05F;
                case 2 -> 0.15F;
                default -> 0.30F;
            };
            if (level.getRandom().nextFloat() < chance) {
                List<ItemEntity> smelted = new ArrayList<>();
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack result = smeltedResult(level, drop.getItem());
                    if (!result.isEmpty()) {
                        ItemEntity entity = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(),
                                result.copyWithCount(drop.getItem().getCount()));
                        entity.setDefaultPickUpDelay();
                        smelted.add(entity);
                    }
                }
                if (!smelted.isEmpty()) {
                    event.getDrops().clear();
                    event.getDrops().addAll(smelted);
                }
            }
        }

        // Ore Doubling: tiers 1-3 add bonus raw ore/dust; tiers 4-6 (Mekanism) replace with
        // clumps/shards/crystals.
        int doubling = data.nodeTier("ore_doubling");
        if (doubling > 0) {
            applyDoubling(event, level, data, block, doubling);
        }

        // Runic Attunement: mark drops (cosmetic data for magic processing mods).
        int attune = data.nodeTier("runic_attunement");
        if (attune > 0) {
            float chance = switch (attune) {
                case 1 -> 0.05F;
                case 2 -> 0.12F;
                default -> 0.20F;
            };
            if (level.getRandom().nextFloat() < chance) {
                for (ItemEntity drop : event.getDrops()) {
                    net.minecraft.nbt.CompoundTag custom = new net.minecraft.nbt.CompoundTag();
                    custom.putBoolean("attuned", true);
                    drop.getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.of(custom));
                }
            }
        }
    }

    private static void applyDoubling(BlockDropsEvent event, ServerLevel level, OreVaultTeamData data,
                                      Block block, int doubling) {
        String material = OreClassifier.oreMaterialName(block);
        if (doubling >= 4) {
            // Mekanism tiers: replace primary drops with clumps (3x), shards (4x), crystals (5x).
            String tagPath = switch (doubling) {
                case 4 -> "clumps";
                case 5 -> "shards";
                default -> "crystals";
            };
            ItemStack processed = firstTagItem(level, "mekanism", tagPath + "/" + material);
            if (processed.isEmpty()) {
                processed = firstTagItem(level, "c", "dusts/" + material);
            }
            if (processed.isEmpty()) {
                return;
            }
            int total = 0;
            for (ItemEntity drop : event.getDrops()) {
                total += drop.getItem().getCount();
            }
            event.getDrops().clear();
            if (total > 0) {
                ItemEntity entity = new ItemEntity(level, event.getPos().getX() + 0.5,
                        event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                        processed.copyWithCount(total));
                entity.setDefaultPickUpDelay();
                event.getDrops().add(entity);
            }
            return;
        }
        float chance = switch (doubling) {
            case 1 -> 0.25F;
            case 2 -> 0.50F;
            default -> 0.75F;
        };
        if (level.getRandom().nextFloat() >= chance) {
            return;
        }
        // Fallback chain: dust tag -> raw ore double. Always capped at 2x total (one bonus).
        ItemStack bonus = firstTagItem(level, "c", "dusts/" + material);
        if (bonus.isEmpty()) {
            bonus = firstTagItem(level, "forge", "dusts/" + material);
        }
        if (bonus.isEmpty()) {
            bonus = rawOreFor(block);
        }
        if (!bonus.isEmpty()) {
            int bonusCount = Math.max(1, countPrimaryDrops(event) / 2);
            ItemEntity entity = new ItemEntity(level, event.getPos().getX() + 0.5,
                    event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                    bonus.copyWithCount(bonusCount));
            entity.setDefaultPickUpDelay();
            event.getDrops().add(entity);
        }
    }

    private static int countPrimaryDrops(BlockDropsEvent event) {
        return event.getDrops().stream().mapToInt(d -> d.getItem().getCount()).sum();
    }

    private static ItemStack rawOreFor(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        String path = id.getPath().replace("deepslate_", "").replace("nether_", "").replace("_ore", "");
        String rawId = switch (path) {
            case "iron" -> "raw_iron";
            case "copper" -> "raw_copper";
            case "gold" -> "raw_gold";
            default -> "raw_" + path;
        };
        Identifier itemId = Identifier.fromNamespaceAndPath("minecraft", rawId);
        if (BuiltInRegistries.ITEM.containsKey(itemId)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack firstTagItem(ServerLevel level, String namespace, String path) {
        var tag = ItemTags.create(Identifier.fromNamespaceAndPath(namespace, path));
        var holders = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                .get(tag);
        if (holders.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = holders.get(level.getRandom().nextInt(holders.size())).value();
        return item.getDefaultInstance();
    }

    private static ItemStack smeltedResult(ServerLevel level, ItemStack input) {
        return level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).stream()
                .filter(r -> r.value().getIngredients().stream().anyMatch(ing -> ing.test(input)))
                .findFirst()
                .map(r -> r.value().getResultItem(level.registryAccess()))
                .map(result -> result.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
    }

    private static void doubleDrops(BlockDropsEvent event) {
        List<ItemEntity> doubled = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemEntity copy = new ItemEntity(drop.level(), drop.getX(), drop.getY(), drop.getZ(),
                    drop.getItem().copy());
            copy.setDefaultPickUpDelay();
            doubled.add(copy);
        }
        event.getDrops().addAll(doubled);
    }

    // --- stone ------------------------------------------------------------------

    private static void handleStone(BlockDropsEvent event, ServerLevel level, OreVaultTeamData data,
                                    ServerPlayer breaker, Block block, BlockPos pos) {
        int tier = data.nodeTier("stone_memory");
        boolean curse = breaker != null && data.isTradeoffActiveFor("stone_curse", breaker.getUUID());
        if (tier <= 0 && !curse) {
            return;
        }
        PlayerStats stats = breaker != null ? data.statsFor(breaker.getUUID()) : null;
        if (stats != null) {
            stats.addStone(1);
            stats.addBlocks(1);
            stats.trackY(pos.getY());
        }
        // Stone Memory XP per tier.
        int xp = tier;
        if (curse) {
            xp = xp * 3;
            event.getDrops().clear(); // Stone Curse: no items from stone
            event.setDroppedExperience(event.getDroppedExperience() + xp);
        } else {
            event.setDroppedExperience(event.getDroppedExperience() + xp);
            if (tier >= 2 && block == Blocks.STONE && level.getRandom().nextFloat() < 0.06F) {
                addDrop(event, new ItemStack(Items.FLINT), level, pos);
            }
            if (tier >= 4 && block == Blocks.STONE && level.getRandom().nextFloat() < 0.02F) {
                addDrop(event, randomNugget(level), level, pos);
            }
        }
        if (tier >= 3 && block == Blocks.DEEPSLATE) {
            // 0.5 Resonance per deepslate, fractional: 50% chance of 1.
            if (level.getRandom().nextBoolean() && breaker != null) {
                ResonanceSystem.addResonance(level.getServer(), data.teamId(), 1, breaker.getUUID());
            }
        }
        if (tier >= 5 && block == Blocks.STONE && level.getRandom().nextFloat() < 0.005F && breaker != null) {
            // Rare Vault Echo-equivalent Resonance burst.
            long burst = 25 + level.getRandom().nextInt(16);
            ResonanceSystem.addResonance(level.getServer(), data.teamId(), burst, breaker.getUUID());
            level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    20, 0.5, 0.5, 0.5, 0.02);
            if (stats != null) {
                stats.addEcho();
            }
        }
    }

    private static void addDrop(BlockDropsEvent event, ItemStack stack, ServerLevel level, BlockPos pos) {
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setDefaultPickUpDelay();
        event.getDrops().add(entity);
    }

    private static ItemStack randomNugget(ServerLevel level) {
        Item item = switch (level.getRandom().nextInt(3)) {
            case 0 -> Items.IRON_NUGGET;
            case 1 -> Items.GOLD_NUGGET;
            default -> Items.COPPER_INGOT;
        };
        return new ItemStack(item);
    }

    // --- vein dynamics -------------------------------------------------------------

    private static boolean isTicketLoaded(ServerLevel level, BlockPos pos) {
        return level.getForcedChunks().contains(new net.minecraft.world.level.ChunkPos(pos));
    }

    /** Volatile Veins: chance the remaining connected vein vanishes. */
    private static void rollVolatileVeins(ServerLevel level, OreVaultTeamData data, ServerPlayer breaker,
                                          Block block, BlockPos broken) {
        PlayerStats stats = data.statsFor(breaker.getUUID());
        if (stats.volatileSafeWindow()) {
            stats.registerSafeBlock();
            return;
        }
        int safety = data.nodeTier("ultimine_safety");
        double chance = 0.02 - safety * 0.01;
        if (level.getRandom().nextDouble() >= chance) {
            return;
        }
        stats.registerVolatileTrigger();
        stats.addVolatileTrigger();
        int removed = vanishVein(level, block, broken, 64);
        OreVault.LOGGER.debug("Ore Vault: Volatile Veins consumed {} blocks for {}", removed, breaker.getGameProfile().getName());
    }

    /** Volatile Veins: chance the remaining connected vein vanishes. Public for Ultimine hooks. */
    public static int vanishVein(ServerLevel level, Block block, BlockPos start, int cap) {
        List<BlockPos> vein = new ArrayList<>();
        List<BlockPos> frontier = new ArrayList<>();
        frontier.add(start);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        while (!frontier.isEmpty() && vein.size() < cap) {
            BlockPos current = frontier.remove(frontier.size() - 1);
            if (!seen.add(current.asLong())) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            if (!state.is(block)) {
                continue;
            }
            vein.add(current);
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!seen.contains(next.asLong()) && level.getBlockState(next).is(block)) {
                    frontier.add(next);
                }
            }
        }
        for (BlockPos pos : vein) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.2, 0.2, 0.2, 0.01);
        }
        if (!vein.isEmpty()) {
            level.playSound(null, vein.get(0), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.6F);
        }
        return vein.size();
    }

    /** Fires when the broken ore's vein is fully mined: Twin Veins + Vault Echo. */
    private static void onVeinCompletion(ServerLevel level, OreVaultTeamData data, ServerPlayer breaker,
                                         Block block, BlockPos broken) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(broken.relative(dir)).is(block)) {
                return; // vein still connected
            }
        }
        int twins = data.nodeTier("twin_veins");
        if (twins > 0) {
            float chance = switch (twins) {
                case 1 -> 0.01F;
                case 2 -> 0.05F;
                default -> 0.10F;
            };
            if (level.getRandom().nextFloat() < chance) {
                spawnTwinVein(level, block, broken);
                data.statsFor(breaker.getUUID()).addTwinVein();
            }
        }
        int echo = data.nodeTier("vault_echo");
        if (echo > 0) {
            long burst = switch (echo) {
                case 1 -> 25;
                case 2 -> 35;
                default -> 50;
            };
            ResonanceSystem.addResonance(level.getServer(), data.teamId(), burst, breaker.getUUID());
            data.statsFor(breaker.getUUID()).addEcho();
            level.sendParticles(ParticleTypes.GLOW, broken.getX() + 0.5, broken.getY() + 0.5, broken.getZ() + 0.5,
                    25, 0.5, 0.5, 0.5, 0.03);
            level.playSound(null, broken, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.7F, 1.4F);
        }
    }

    private static void spawnTwinVein(ServerLevel level, Block block, BlockPos origin) {
        int size = 4 + level.getRandom().nextInt(5);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        pos.set(origin);
        for (int i = 0; i < size * 3; i++) {
            pos.setWithOffset(pos, level.getRandom().nextInt(3) - 1, level.getRandom().nextInt(3) - 1, level.getRandom().nextInt(3) - 1);
            BlockPos check = pos.immutable();
            BlockState current = level.getBlockState(check);
            if (current.is(Blocks.STONE) || current.is(Blocks.DEEPSLATE) || current.is(Blocks.TUFF)) {
                level.setBlock(check, block.defaultBlockState(), Block.UPDATE_NONE);
                size--;
                level.sendParticles(ParticleTypes.ENCHANT, check.getX() + 0.5, check.getY() + 0.5, check.getZ() + 0.5,
                        2, 0.2, 0.2, 0.2, 0.02);
                if (size <= 0) {
                    break;
                }
            }
        }
        level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.6F);
    }
}

