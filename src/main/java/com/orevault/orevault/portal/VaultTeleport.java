package com.orevault.orevault.portal;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.item.VaultIgniterItem;
import com.orevault.orevault.tags.ModTags;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * standing on the surface — or at the player's selected entry point (tier 2+).
 * Vault → Overworld: returns to the saved position, falling back to world
 * spawn.</li>
 * <li>{@link #handlePortal} — the tier-3+ instant path (§3.3): direct teleport,
 * no portal wait and no cooldown.</li>
 * </ul>
 *
 * <p>No potion effects are applied on arrival. The tiers grant persistent
 * capabilities instead of stats (§3.3, #100); see {@code VaultIgniterItem}.
 * Arrivals in both directions show an action-bar overlay message (#78). The
 * re-entry cooldown is the vanilla portal cooldown (80 ticks, §3.2); tier 3+
 * skips it.</p>
 */
public final class VaultTeleport {

    /** Persistent-data key for the saved return position (§3.2): NBT x/y/z ints. */
    public static final String RETURN_TAG = "orevault_return";
    /** Portal charge-up time while standing inside, in ticks (§3.2). */
    public static final int PORTAL_WAIT_TICKS = 80;
    /** Re-entry cooldown after a portal trip in ticks (§3.2); tier 3+ skips it (§3.3). */
    public static final int PORTAL_COOLDOWN_TICKS = 80;

    private VaultTeleport() {
    }

    /** Direct teleport used by the tier-3+ instant path (§3.3). */
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
        MinecraftServer server = currentLevel.getServer();
        ResourceKey<Level> key = VaultDimensions.findOrCreate(TeamHelper.getTeamId(player));
        ServerLevel vault = server.getLevel(key);
        if (vault == null) {
            OreVault.LOGGER.error("Vault dimension {} missing after findOrCreate", key.identifier());
            return null;
        }

        // Saved only once the trip is known to be possible (#100). Saving before
        // the checks overwrote a good return position on a failed trip, so a
        // player who bounced off a broken vault lost their way home instead of
        // simply going nowhere.
        saveReturnPosition(player, currentLevel, portalEntryPos);

        int tier = VaultIgniterItem.highestTierLevel(player);
        BlockPos anchor = vaultAnchor(vault);
        Optional<BlockPos> custom = VaultIgniterItem.entryPointCapacity(player) > 0
                ? VaultIgniterItem.selectedEntryPoint(player)
                : Optional.empty();
        BlockPos target = custom.map(pos -> findSafeFooting(vault, pos)).orElse(anchor);

        // The team's one return portal lives at the fixed anchor (§3.2);
        // (re)build it whenever the area is still clear, upgrading its tier.
        Direction.Axis axis = currentLevel.getBlockState(portalEntryPos)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        VaultPortalShape.ensureReturnPortal(vault, anchor, axis, tier);

        boolean instant = VaultIgniterItem.hasInstantTravel(player);
        TeleportTransition.PostTeleportTransition post = TeleportTransition.PLAY_PORTAL_SOUND.then(entity -> {
            if (entity instanceof ServerPlayer p) {
                p.setPortalCooldown(instant ? 0 : PORTAL_COOLDOWN_TICKS);
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
     * The team's fixed arrival point: the Vault surface at X=0, Z=0 (§3.2).
     *
     * <p>Every trip into a Vault lands on this same block regardless of where
     * in the Overworld the portal stood. Arrival deliberately does not mirror
     * Overworld coordinates — doing so built a separate return portal for each
     * entry location and littered the Vault with them.</p>
     */
    public static BlockPos vaultAnchor(ServerLevel vault) {
        return safeSurface(vault, new BlockPos(0, vault.getMaxY(), 0));
    }

    /**
     * Safe landing spot at the given XZ (#77): reads the MOTION_BLOCKING
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

    // ----- direct (tier-3+) paths -----

    private static void teleportToVault(ServerPlayer player) {
        MinecraftServer server = server(player);
        if (server == null) {
            return;
        }
        ServerLevel current = (ServerLevel) player.level();

        ResourceKey<Level> key = VaultDimensions.findOrCreate(TeamHelper.getTeamId(player));
        ServerLevel vault = server.getLevel(key);
        if (vault == null) {
            OreVault.LOGGER.error("Vault dimension {} missing after findOrCreate", key.identifier());
            return;
        }

        // After the checks, for the same reason as toVaultTransition (#100).
        saveReturnPosition(player, current, player.blockPosition());

        int tier = VaultIgniterItem.highestTierLevel(player);
        BlockPos anchor = vaultAnchor(vault);
        Optional<BlockPos> custom = VaultIgniterItem.entryPointCapacity(player) > 0
                ? VaultIgniterItem.selectedEntryPoint(player)
                : Optional.empty();
        BlockPos target = custom.map(pos -> findSafeFooting(vault, pos)).orElse(anchor);

        Direction.Axis axis = current.getBlockState(player.blockPosition())
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        VaultPortalShape.ensureReturnPortal(vault, anchor, axis, tier);

        teleport(player, vault, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
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
        for (int steps = 0; steps < 4 && level.getBlockState(exit.relative(exitDirection)).is(ModTags.Blocks.VAULT_PORTALS); steps++) {
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

}
