package com.orevault.orevault.portal;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.item.VaultIgniterItem;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;

import dev.ftb.mods.ftbteams.api.Team;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Teleportation routing between the Overworld and the team's Vault (§3.2).
 *
 * <p>Two entry paths exist:
 * <ul>
 * <li>{@link #createTransition} — the 26.1 {@code Portal} interface path used
 * by {@code VaultPortalBlock}: after the vanilla portal wait, the returned
 * {@link TeleportTransition} performs the trip (with portal travel sound).
 * Overworld → Vault: saves the return position (outside the portal plane, on
 * the approach side), finds/creates the team's dimension, ensures the exit
 * portal at the vault's default entry point, and lands on the mirrored XZ
 * standing on the surface — or at the player's custom entry point for tier 3+.
 * Vault → Overworld: returns to the saved position, falling back to world
 * spawn.</li>
 * <li>{@link #handlePortal} — the tier-4 instant path (§3.3): direct teleport,
 * no portal wait and no cooldown.</li>
 * </ul>
 *
 * <p>Arrival effects on entering the Vault: Speed I (tier 2, 5s), Haste I
 * (tier 3, 10s), Haste II (tier 4, 15s). Arrivals in both directions show an
 * action-bar overlay message (#78). The re-entry cooldown is the vanilla
 * portal cooldown (80 ticks, §3.2); tier 4 skips it.</p>
 */
public final class VaultTeleport {

    /** Persistent-data key for the saved return position (§3.2): NBT x/y/z ints. */
    public static final String RETURN_TAG = "orevault_return";
    /** Portal charge-up time while standing inside, in ticks (§3.2). */
    public static final int PORTAL_WAIT_TICKS = 80;
    /** Re-entry cooldown after a portal trip in ticks (§3.2); tier 4 skips it (§3.3). */
    public static final int PORTAL_COOLDOWN_TICKS = 80;

    private VaultTeleport() {
    }

    /** Direct teleport used by the tier-4 instant path (§3.3). */
    public static void handlePortal(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        if (VaultDimensions.isVaultDimension(player.level())) {
            teleportBack(player);
        } else {
            teleportToVault(player);
        }
    }

    /**
     * Portal-interface entry point (§3.2): builds the {@link TeleportTransition}
     * for the waiting portal flow in {@code VaultPortalBlock}, or {@code null}
     * if the trip is impossible. The team check normally happens in the block;
     * this is the safety net.
     */
    public static @Nullable TeleportTransition createTransition(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }
        if (VaultDimensions.isVaultDimension(currentLevel)) {
            return returnTransition(player);
        }
        return toVaultTransition(player, currentLevel, portalEntryPos);
    }

    // ----- Overworld -> Vault -----

    private static @Nullable TeleportTransition toVaultTransition(ServerPlayer player, ServerLevel currentLevel, BlockPos portalEntryPos) {
        Optional<Team> team = TeamHelper.getTeam(player);
        if (team.isEmpty()) {
            return null;
        }
        saveReturnPosition(player, currentLevel, portalEntryPos);

        MinecraftServer server = currentLevel.getServer();
        ResourceKey<Level> key = VaultDimensions.findOrCreate(team.get().getTeamId());
        ServerLevel vault = server.getLevel(key);
        if (vault == null) {
            OreVault.LOGGER.error("Vault dimension {} missing after findOrCreate", key.identifier());
            return null;
        }

        int tier = VaultIgniterItem.highestTierLevel(player);
        BlockPos defaultAnchor = safeSurface(vault, player.blockPosition());
        Optional<BlockPos> custom = tier >= 3 ? entryPoint(player) : Optional.empty();
        BlockPos target = custom.map(pos -> findSafeFooting(vault, pos)).orElse(defaultAnchor);

        // The team's exit portal lives at the vault's default entry point (§3.2);
        // (re)build it whenever the area is still clear.
        Direction.Axis axis = currentLevel.getBlockState(portalEntryPos)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        VaultPortalShape.ensureReturnPortal(vault, defaultAnchor, axis);

        int finalTier = tier;
        TeleportTransition.PostTeleportTransition post = TeleportTransition.PLAY_PORTAL_SOUND.then(entity -> {
            if (entity instanceof ServerPlayer p) {
                p.setPortalCooldown(finalTier >= 4 ? 0 : PORTAL_COOLDOWN_TICKS);
                applyArrivalEffects(p, finalTier);
                p.sendOverlayMessage(Component.translatable("message.orevault.vault_arrival"));
            }
        });
        return new TeleportTransition(
                vault,
                new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                post
        );
    }

    /**
     * Safe landing spot at the mirrored XZ (#77): reads the MOTION_BLOCKING
     * heightmap (generating the chunk if needed) and scans down from the
     * surface for a 2-block air pocket, falling back to the default entry
     * height. Also used by {@link VaultPortalShape#ensureReturnPortal} to sit
     * the exit portal on the actual surface.
     */
    public static BlockPos safeSurface(ServerLevel level, BlockPos at) {
        int x = at.getX();
        int z = at.getZ();
        level.getChunk(x >> 4, z >> 4); // ensure generated so the heightmap is populated
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int minY = level.getMinY() + 1;
        int maxY = Math.min(surface, level.getMaxY() - 2);
        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                return feet;
            }
        }
        return new BlockPos(x, VaultDimensions.defaultEntryY(), z);
    }

    // ----- Vault -> Overworld -----

    private static TeleportTransition returnTransition(ServerPlayer player) {
        ServerLevel vault = (ServerLevel) player.level();
        ServerLevel overworld = vault.getServer().overworld();
        Optional<BlockPos> saved = returnPos(player);
        Vec3 dest;
        if (saved.isPresent()) {
            BlockPos pos = saved.get();
            dest = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        } else {
            BlockPos spawn = overworld.getRespawnData().globalPos().pos();
            dest = new Vec3(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
        }
        return new TeleportTransition(
                overworld,
                dest,
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND.then(entity -> {
                    if (entity instanceof ServerPlayer p) {
                        p.sendOverlayMessage(Component.translatable("message.orevault.overworld_return"));
                    }
                })
        );
    }

    // ----- direct (tier-4) paths -----

    private static void teleportToVault(ServerPlayer player) {
        MinecraftServer server = server(player);
        if (server == null) {
            return;
        }
        ServerLevel current = (ServerLevel) player.level();
        saveReturnPosition(player, current, player.blockPosition());

        Optional<Team> team = TeamHelper.getTeam(player);
        if (team.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.orevault.team_required"));
            return;
        }
        ResourceKey<Level> key = VaultDimensions.findOrCreate(team.get().getTeamId());
        ServerLevel vault = server.getLevel(key);
        if (vault == null) {
            OreVault.LOGGER.error("Vault dimension {} missing after findOrCreate", key.identifier());
            return;
        }

        int tier = VaultIgniterItem.highestTierLevel(player);
        BlockPos defaultAnchor = safeSurface(vault, player.blockPosition());
        Optional<BlockPos> custom = tier >= 3 ? entryPoint(player) : Optional.empty();
        BlockPos target = custom.map(pos -> findSafeFooting(vault, pos)).orElse(defaultAnchor);

        Direction.Axis axis = current.getBlockState(player.blockPosition())
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        VaultPortalShape.ensureReturnPortal(vault, defaultAnchor, axis);

        teleport(player, vault, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        applyArrivalEffects(player, tier); // no cooldown: tier 4 (§3.3)
        player.sendOverlayMessage(Component.translatable("message.orevault.vault_arrival"));
    }

    private static void teleportBack(ServerPlayer player) {
        MinecraftServer server = server(player);
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        Optional<BlockPos> saved = returnPos(player);
        double x;
        double y;
        double z;
        if (saved.isPresent()) {
            x = saved.get().getX() + 0.5;
            y = saved.get().getY();
            z = saved.get().getZ() + 0.5;
        } else {
            BlockPos spawn = overworld.getRespawnData().globalPos().pos();
            x = spawn.getX() + 0.5;
            y = spawn.getY() + 1;
            z = spawn.getZ() + 0.5;
        }
        teleport(player, overworld, x, y, z);
        player.sendOverlayMessage(Component.translatable("message.orevault.overworld_return"));
    }

    // ----- helpers -----

    /** The server hosting this player, or {@code null} on the client. */
    private static @Nullable MinecraftServer server(ServerPlayer player) {
        return player.level() instanceof ServerLevel serverLevel ? serverLevel.getServer() : null;
    }

    private static void teleport(ServerPlayer player, ServerLevel target, double x, double y, double z) {
        player.teleportTo(
                target,
                x,
                y,
                z,
                Relative.union(Relative.DELTA, Relative.ROTATION),
                player.getYRot(),
                player.getXRot(),
                true
        );
    }

    /**
     * Saves the return position outside the portal plane on the approach side.
     * Walking from the touched block along the approach direction until the
     * first non-portal block guarantees the saved spot lies outside BOTH portal
     * planes, so returning never instantly re-triggers the portal.
     */
    private static void saveReturnPosition(ServerPlayer player, Level level, BlockPos portalPos) {
        Direction.Axis axis = level.getBlockState(portalPos)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        Direction exitDirection = exitDirection(player, axis);

        BlockPos exit = portalPos;
        for (int steps = 0; steps < 4 && level.getBlockState(exit.relative(exitDirection)).is(ModBlocks.VAULT_PORTAL); steps++) {
            exit = exit.relative(exitDirection);
        }
        exit = exit.relative(exitDirection);

        CompoundTag tag = new CompoundTag();
        tag.putInt("x", exit.getX());
        tag.putInt("y", portalPos.getY());
        tag.putInt("z", exit.getZ());
        player.getPersistentData().put(RETURN_TAG, tag);
    }

    /** The horizontal direction out of the portal on the side the player approached from. */
    private static Direction exitDirection(ServerPlayer player, Direction.Axis axis) {
        Direction.Axis normalAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Vec3 motion = player.getDeltaMovement();
        double normalMotion = normalAxis == Direction.Axis.X ? motion.x() : motion.z();
        int sign;
        if (Math.abs(normalMotion) > 0.01) {
            sign = normalMotion > 0 ? -1 : 1; // exit behind the direction of travel
        } else {
            Direction facing = player.getDirection();
            double facingComponent = normalAxis == Direction.Axis.X ? facing.getStepX() : facing.getStepZ();
            sign = facingComponent > 0 ? -1 : 1;
        }
        return Direction.fromAxisAndDirection(
                normalAxis,
                sign > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE
        );
    }

    /** The saved return position from {@link #RETURN_TAG}, if present and well-formed. */
    private static Optional<BlockPos> returnPos(ServerPlayer player) {
        Optional<CompoundTag> opt = player.getPersistentData().getCompound(RETURN_TAG);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = opt.get();
        Optional<Integer> x = tag.getInt("x");
        Optional<Integer> y = tag.getInt("y");
        Optional<Integer> z = tag.getInt("z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(x.get(), y.get(), z.get()));
    }

    /** The player's custom Vault entry point (§3.3, tier 3+), if stored. */
    private static Optional<BlockPos> entryPoint(ServerPlayer player) {
        Optional<CompoundTag> opt = player.getPersistentData().getCompound(VaultIgniterItem.ENTRY_POINT_TAG);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = opt.get();
        Optional<Integer> x = tag.getInt("x");
        Optional<Integer> y = tag.getInt("y");
        Optional<Integer> z = tag.getInt("z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(x.get(), y.get(), z.get()));
    }

    /**
     * Spawn footing for a custom entry point: stand on top of the stored
     * block, scanning upward for a 2-block air pocket; carves one if none
     * exists nearby.
     */
    private static BlockPos findSafeFooting(ServerLevel vault, BlockPos entry) {
        for (int y = entry.getY() + 1; y <= entry.getY() + 6; y++) {
            if (vault.getBlockState(new BlockPos(entry.getX(), y, entry.getZ())).isAir()
                    && vault.getBlockState(new BlockPos(entry.getX(), y + 1, entry.getZ())).isAir()) {
                return new BlockPos(entry.getX(), y, entry.getZ());
            }
        }
        vault.setBlock(new BlockPos(entry.getX(), entry.getY() + 1, entry.getZ()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        vault.setBlock(new BlockPos(entry.getX(), entry.getY() + 2, entry.getZ()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        return new BlockPos(entry.getX(), entry.getY() + 1, entry.getZ());
    }

    /** Arrival effects on entering the Vault, by igniter tier (§3.3). */
    private static void applyArrivalEffects(ServerPlayer player, int tier) {
        switch (tier) {
            case 2 -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 5 * 20, 0));
            case 3 -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, 10 * 20, 0));
            case 4 -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, 15 * 20, 1));
            default -> {
                // Tier 1: no arrival effect.
            }
        }
    }
}
