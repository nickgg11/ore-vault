package com.orevault.orevault.event;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.portal.VaultTeleport;
import com.orevault.orevault.reset.VaultReset;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;
import com.orevault.orevault.zone.DisturbedZoneManager;
import com.orevault.orevault.chunk.VaultChunkLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NeoForge game-bus event handlers: server init, block drops, player ticks (hunger,
 * purity/blessing, fever, time tracking), dimension-change arrival effects, zone spawning.
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        OreClassifier.init(server);
        VaultDimensions.onServerStarted(server);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        OreDropHandler.onBlockDrops(event);
    }

    @SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        MobDeathHandler.onLivingDeath(event);
    }

    @SubscribeEvent
    public static void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        MobDeathHandler.onLivingDrops(event);
    }

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        VaultReset.tick(event.getServer());
    }

    /**
     * Ore Sense: passive Fortune levels on ore mining inside the Vault, stacking additively
     * with tool enchantments.
     */
    @SubscribeEvent
    public static void onGetEnchantmentLevel(net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent event) {
        if (event.isTargetting(net.minecraft.world.item.enchantment.Enchantments.FORTUNE)) {
            // The event fires during mining; the breaking player was recorded by the break handler.
            ServerPlayer miner = OreDropHandler.ACTIVE_MINER.get();
            if (miner == null || !TeamHelper.isVaultDimension(miner.level())) {
                return;
            }
            OreVaultTeamData data = teamDataFor(miner);
            if (data == null) {
                return;
            }
            int tier = data.nodeTier("ore_sense");
            if (tier > 0) {
                int current = event.getEnchantments().getLevel(event.getTargetEnchant());
                event.getEnchantments().set(event.getTargetEnchant(), current + tier);
            }
        }
    }

    @SubscribeEvent
    public static void onBreakBlock(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && TeamHelper.isVaultDimension(player.level())) {
            OreDropHandler.ACTIVE_MINER.set(player);
        }
    }

    /**
     * Efficient Miner T4: no starvation damage while inside the Vault.
     */
    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel
                && TeamHelper.isVaultDimension(player.level())
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.STARVE)) {
            OreVaultTeamData data = teamDataFor(player);
            if (data != null && data.nodeTier("efficient_miner") >= 4) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && TeamHelper.isVaultDimension(serverLevel)) {
            DisturbedZoneManager.tick(serverLevel);
            if (serverLevel.getGameTime() % 100L == 0L) {
                VaultChunkLoader.sweep(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTo().equals(player.level().dimension())) {
            if (TeamHelper.isVaultDimension(player.level())) {
                VaultTeleport.applyArrivalEffects(player, (ServerLevel) player.level());
                // Vault's Purity strips potion effects on entry.
                OreVaultTeamData data = teamDataFor(player);
                if (data != null && data.hasNode("vaults_purity")) {
                    player.removeAllEffects();
                }
            }
            HungerTracker.reset(player.getUUID());
            EffectTracker.reset(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!TeamHelper.isVaultDimension(level)) {
            HungerTracker.reset(player.getUUID());
            EffectTracker.reset(player.getUUID());
            return;
        }
        OreVaultTeamData data = teamDataFor(player);
        if (data == null) {
            return;
        }
        PlayerStats stats = data.statsFor(player.getUUID());
        stats.addVaultTime(1);
        trackChunkVisit(stats, player);

        applyHungerNodes(data, player);
        applyEffectNodes(data, player);
        applyFever(data, player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OreVaultTeamData data = teamDataFor(player);
            if (data != null) {
                data.statsFor(player.getUUID()).onLogout();
            }
            HungerTracker.reset(player.getUUID());
            EffectTracker.reset(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OreVaultTeamData data = teamDataFor(player);
            if (data != null) {
                data.statsFor(player.getUUID()).resetSessionCounters();
            }
        }
    }

    private static OreVaultTeamData teamDataFor(ServerPlayer player) {
        UUID teamId = TeamHelper.teamIdFor(player);
        if (teamId == null) {
            return null;
        }
        return OreVaultTeamData.get(player.getServer(), teamId);
    }

    private static final Map<UUID, ChunkPos> LAST_CHUNK = new HashMap<>();

    private static void trackChunkVisit(PlayerStats stats, ServerPlayer player) {
        ChunkPos current = player.chunkPosition();
        ChunkPos last = LAST_CHUNK.put(player.getUUID(), current);
        if (last == null || !last.equals(current)) {
            stats.addChunkVisit();
        }
    }

    // --- hunger / effect node tick effects ------------------------------------------

    private static void applyHungerNodes(OreVaultTeamData data, ServerPlayer player) {
        int tier = data.nodeTier("efficient_miner");
        if (tier <= 0) {
            return;
        }
        FoodData food = player.getFoodData();
        HungerTracker.State state = HungerTracker.state(player.getUUID());
        double reduction = switch (tier) {
            case 1 -> 0.20;
            case 2 -> 0.45;
            case 3 -> 0.65;
            case 4 -> 0.85;
            default -> 1.00;
        };
        if (tier >= 5) {
            // Frozen completely.
            if (state.lastHunger >= 0) {
                food.setFoodLevel(state.lastHunger);
                food.setSaturation(state.lastSaturation);
                food.setExhaustion(state.lastExhaustion);
            }
            state.lastHunger = food.getFoodLevel();
            state.lastSaturation = food.getSaturationLevel();
            state.lastExhaustion = food.getExhaustionLevel();
            return;
        }
        // Refund a fraction of the exhaustion added since last tick.
        double current = food.getExhaustionLevel();
        if (state.lastExhaustion >= 0 && current > state.lastExhaustion) {
            double added = current - state.lastExhaustion;
            food.setExhaustion((float) Math.max(0, current - added * reduction));
        }
        state.lastExhaustion = food.getExhaustionLevel();

        if (tier >= 2) {
            // Food restores 20% more saturation: compensate after eating (saturation increased).
            double sat = food.getSaturationLevel();
            if (state.lastSaturation >= 0 && sat > state.lastSaturation) {
                double gained = sat - state.lastSaturation;
                food.setSaturation((float) Math.min(20, sat + gained * 0.2));
            }
            state.lastSaturation = food.getSaturationLevel();
        }
        if (tier >= 3 && state.lastSaturation >= 0 && food.getSaturationLevel() > state.lastSaturation) {
            // Eating grants brief Regeneration I.
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 4 * 20, 0, false, false));
        }
    }

    private static void applyEffectNodes(OreVaultTeamData data, ServerPlayer player) {
        if (data.hasNode("vaults_purity")) {
            // Purity: no potion effects inside the Vault.
            if (!player.getActiveEffects().isEmpty()) {
                player.removeAllEffects();
            }
        } else if (data.hasNode("vaults_blessing")) {
            // Blessing: effects tick down at half speed while inside (50% longer).
            EffectTracker.State state = EffectTracker.state(player.getUUID());
            state.tickParity ^= true;
            if (state.tickParity) {
                for (MobEffectInstance effect : player.getActiveEffects()) {
                    if (!effect.isInfiniteDuration() && effect.getDuration() > 1) {
                        effect.update(new MobEffectInstance(effect.getEffect(), effect.getDuration() + 1,
                                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()));
                    }
                }
            }
        }
    }

    private static void applyFever(OreVaultTeamData data, ServerPlayer player) {
        if (data.isTradeoffActiveFor("vault_fever", player.getUUID())) {
            if (player.tickCount % 60 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 61, 1, false, false));
            }
            FoodData food = player.getFoodData();
            double current = food.getExhaustionLevel();
            HungerTracker.State state = HungerTracker.state(player.getUUID());
            if (state.lastExhaustion >= 0 && current > state.lastExhaustion) {
                food.setExhaustion((float) (current + (current - state.lastExhaustion) * 0.5));
            }
            state.lastExhaustion = food.getExhaustionLevel();
        }
    }

    static final class HungerTracker {
        record State(double lastExhaustion, float lastSaturation, int lastHunger) {
        }

        private static final Map<UUID, State> STATES = new HashMap<>();

        static State state(UUID player) {
            return STATES.computeIfAbsent(player, u -> new State(-1, -1, -1));
        }

        static void reset(UUID player) {
            STATES.remove(player);
        }
    }

    static final class EffectTracker {
        static final class State {
            boolean tickParity;
        }

        private static final Map<UUID, State> STATES = new HashMap<>();

        static State state(UUID player) {
            return STATES.computeIfAbsent(player, u -> new State());
        }

        static void reset(UUID player) {
            STATES.remove(player);
        }
    }
}

