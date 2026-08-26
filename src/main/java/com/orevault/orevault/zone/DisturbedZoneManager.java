package com.orevault.orevault.zone;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.entity.AnimusOrbEntity;
import com.orevault.orevault.skill.NodeCosts;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Disturbed Zone spawning (design spec section 7). Zones are spherical areas around
 * placed Disturbed Zone blocks; only vanilla mobs spawn, scaled by the Animus tree nodes.
 * Zone mobs are tagged so kills award Animus and node effects apply.
 */
public final class DisturbedZoneManager {
    public static final String ZONE_TEAM_TAG = "orevault_zone_team";
    private static final int BASE_SPAWN_INTERVAL = 80;
    private static final int BASE_RADIUS = 16;

    // per-level tick counters
    private static final Map<UUID, Integer> TICK_COUNTERS = new HashMap<>();

    private DisturbedZoneManager() {
    }

    public static boolean onZonePlaced(ServerLevel level, BlockPos pos) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return false;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        if (!data.hasNode("disturbed_zone_unlock")) {
            // Should not happen (recipe gated), but guard anyway.
            level.destroyBlock(pos, true);
            return false;
        }
        if (data.zonePositions().size() >= OreVaultServerConfig.MAX_ZONES_PER_TEAM.get()) {
            level.destroyBlock(pos, true);
            return false;
        }
        data.addZone(pos);
        return true;
    }

    public static void onZoneRemoved(ServerLevel level, BlockPos pos) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        data.removeZone(pos);
    }

    public static boolean isZoneMob(Mob mob) {
        return mob.getPersistentData().contains(ZONE_TEAM_TAG);
    }

    public static UUID zoneTeamOf(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag.contains(ZONE_TEAM_TAG) ? UUID.fromString(tag.getStringOr(ZONE_TEAM_TAG, "")) : null;
    }

    public static void tick(ServerLevel level) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        if (data.zonePositions().isEmpty()) {
            return;
        }
        int tick = TICK_COUNTERS.merge(teamId, 1, Integer::sum);
        if (tick % BASE_SPAWN_INTERVAL != 0) {
            return;
        }
        // Any player in the dimension? (Only spawn near active players.)
        boolean playersNearby = !level.players().isEmpty();
        if (!playersNearby) {
            return;
        }

        int frequencyTier = data.nodeTier("zone_frequency");
        int radius = zoneRadius(data);
        // Sweep: unregister zones whose block was removed (26.1 has no Block#onRemove).
        for (BlockPos zone : new java.util.ArrayList<>(data.zonePositions())) {
            if (!level.isLoaded(zone) || !level.getBlockState(zone).is(com.orevault.orevault.block.ModBlocks.DISTURBED_ZONE)) {
                data.removeZone(zone);
                continue;
            }
            int attempts = spawnAttempts(frequencyTier);
            for (int i = 0; i < attempts; i++) {
                trySpawnPack(level, data, zone, radius);
            }
        }
    }

    private static int spawnAttempts(int frequencyTier) {
        // Zone Frequency: +25/50/80/120% spawn frequency.
        return switch (frequencyTier) {
            case 1 -> levelCheck(1.25);
            case 2 -> levelCheck(1.5);
            case 3 -> levelCheck(1.8);
            case 4 -> 2;
            default -> 1;
        };
    }

    private static int levelCheck(double v) {
        return Math.random() < v - 1 ? 2 : 1;
    }

    private static int zoneRadius(OreVaultTeamData data) {
        return switch (data.nodeTier("zone_radius")) {
            case 1 -> 32;
            case 2 -> 48;
            case 3 -> 64;
            default -> BASE_RADIUS;
        };
    }

    private static void trySpawnPack(ServerLevel level, OreVaultTeamData data, BlockPos zone, int radius) {
        int diversity = data.nodeTier("mob_diversity");
        EntityType<?> mobType = pickMob(level.getRandom(), diversity);
        if (mobType == null) {
            return;
        }
        int packSize = 1 + data.nodeTier("zone_pack_size");
        int spawned = 0;
        for (int attempt = 0; attempt < packSize * 4 && spawned < packSize; attempt++) {
            int dx = level.getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = level.getRandom().nextInt(radius * 2 + 1) - radius;
            int dy = level.getRandom().nextInt(16) - 8;
            BlockPos candidate = zone.offset(dx, dy, dz);
            if (dx * dx + dz * dz > radius * radius || candidate.getY() < level.getMinY() || candidate.getY() >= level.getMaxY()) {
                continue;
            }
            // Find ground.
            BlockPos ground = candidate;
            while (ground.getY() > level.getMinY() && level.getBlockState(ground).isAir()) {
                ground = ground.below();
            }
            if (ground.getY() <= level.getMinY() || !level.getBlockState(ground.above()).isAir()) {
                continue;
            }
            if (level.getMaxLocalRawBrightness(ground.above()) > 7) {
                continue; // Disturbed Zones are dark-spawning only
            }
            Mob mob = mobType.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(ground.above().getX() + 0.5, ground.above().getY(), ground.above().getZ() + 0.5,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            mob.getPersistentData().putString(ZONE_TEAM_TAG, data.teamId().toString());
            if (mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, null)) {
                level.addFreshEntity(mob);
                spawned++;
            }
        }
    }

    private static EntityType<?> pickMob(net.minecraft.util.RandomSource random, int diversity) {
        // Mob Diversity: 1 undead, 2 arthropod, 3 illager, 4 rare. Base (0): undead basics.
        java.util.List<EntityType<?>> pool = new java.util.ArrayList<>();
        pool.add(EntityType.ZOMBIE);
        pool.add(EntityType.SKELETON);
        if (diversity >= 1) {
            pool.add(EntityType.WITHER_SKELETON);
        }
        if (diversity >= 2) {
            pool.add(EntityType.SPIDER);
            pool.add(EntityType.CAVE_SPIDER);
            pool.add(EntityType.SILVERFISH);
        }
        if (diversity >= 3) {
            pool.add(EntityType.VINDICATOR);
            pool.add(EntityType.EVOKER);
            pool.add(EntityType.PILLAGER);
        }
        if (diversity >= 4) {
            pool.add(EntityType.WITCH);
            pool.add(EntityType.ENDERMAN);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Called from the death handler: computes Animus for a zone mob and spawns orbs.
     */
    public static void onZoneMobKilled(ServerLevel level, UUID teamId, Mob mob, net.minecraft.world.entity.player.Player killer) {
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        long base = animusFor(mob.getType());
        double amplifier = 1 + switch (data.nodeTier("animus_amplifier")) {
            case 1 -> 0.25;
            case 2 -> 0.50;
            case 3 -> 0.80;
            default -> 0;
        };
        long amount = Math.max(1, Math.round(base * amplifier));
        AnimusOrbEntity.spawn(level, mob.position().add(0, 0.5, 0), amount, teamId);
    }

    private static long animusFor(EntityType<?> type) {
        if (type == EntityType.WITHER_SKELETON || type == EntityType.BLAZE || type == EntityType.EVOKER) {
            return 20;
        }
        if (type == EntityType.CREEPER || type == EntityType.WITCH || type == EntityType.ENDERMAN) {
            return 8;
        }
        return 3;
    }
}



