package com.orevault.orevault.portal;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.item.VaultIgniterItem;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Teleportation routing between the Overworld and the team's Vault (§3.2).
 *
 * <p>Overworld → Vault: saves the return position (one block outside the
 * portal plane, on the approach side, so returning never re-triggers the
 * portal), finds/creates the team's dimension via {@link VaultDimensions},
 * and teleports to the mirrored XZ at Y=64 — or to the player's custom entry
 * point for tier 3+ igniters. Vault → Overworld: returns to the saved
 * position, falling back to the world spawn.</p>
 *
 * <p>Cooldown is the vanilla 80-tick {@code portalCooldown}; tier 4 skips it.
 * Arrival effects on entering the Vault: Speed I (tier 2, 5s), Haste I
 * (tier 3, 10s), Haste II (tier 4, 15s).</p>
 *
 * <p><b>Entry chamber (design note):</b> the Vault world is solid stone
 * (§3.1, no caves), so the default Y=64 spawn point is carved into a 5×5×4
 * air chamber on first entry — without it the player would suffocate. Carving
 * happens on the server thread via {@code setBlock}, which generates the
 * chunk if needed.</p>
 */
public final class VaultTeleport {

    /** Persistent-data key for the saved return position (§3.2): NBT x/y/z ints. */
    public static final String RETURN_TAG = "orevault_return";
    /** Vanilla portal cooldown in ticks (§3.2); tier 4 skips it (§3.3). */
    public static final int PORTAL_COOLDOWN_TICKS = 80;
    /** Default entry Y in the Vault (§3.2 pseudocode). */
    public static final int DEFAULT_ENTRY_Y = 64;
    /** Entry chamber footprint (square side length) carved at the default spawn. */
    public static final int ENTRY_CHAMBER_FOOTPRINT = 5;
    /** Entry chamber height in blocks, from {@link #DEFAULT_ENTRY_Y} upward. */
    public static final int ENTRY_CHAMBER_HEIGHT = 4;

    private VaultTeleport() {
    }

    /** Portal entry point (§3.2): routes between Overworld and Vault. */
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

    // ----- Overworld -> Vault -----

    private static void teleportToVault(ServerPlayer player) {
        MinecraftServer server = server(player);
        if (server == null) {
            return;
        }
        saveReturnPosition(player);

        UUID teamId = TeamHelper.getTeamId(player);
        ResourceKey<Level> key = VaultDimensions.findOrCreate(teamId);
        ServerLevel vault = server.getLevel(key);
        if (vault == null) {
            OreVault.LOGGER.error("Vault dimension {} missing after findOrCreate", key.identifier());
            return;
        }

        int tier = VaultIgniterItem.highestTierLevel(player);
        Optional<BlockPos> custom = tier >= 3 ? entryPoint(player) : Optional.empty();
        BlockPos target = custom.map(pos -> findSafeFooting(vault, pos))
                .orElseGet(() -> defaultEntry(player, vault));
        teleport(player, vault, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        if (tier < 4) {
            player.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
        }
        applyArrivalEffects(player, tier);
    }

    /** Mirrored XZ at {@link #DEFAULT_ENTRY_Y} (§3.2), carving the entry chamber on first use. */
    private static BlockPos defaultEntry(ServerPlayer player, ServerLevel vault) {
        BlockPos target = new BlockPos(player.blockPosition().getX(), DEFAULT_ENTRY_Y, player.blockPosition().getZ());
        carveEntryChamber(vault, target);
        return target;
    }

    // ----- Vault -> Overworld -----

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

        if (VaultIgniterItem.highestTierLevel(player) < 4) {
            player.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
        }
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
     * Saves the return position one block outside the portal plane on the
     * approach side. Standing outside the plane after the return trip means
     * the portal never instantly re-triggers, even with the tier-4 no-cooldown.
     */
    private static void saveReturnPosition(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        Direction.Axis axis = player.level()
                .getBlockState(pos)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        BlockPos exit = pos.relative(exitDirection(player, axis));

        CompoundTag tag = new CompoundTag();
        tag.putInt("x", exit.getX());
        tag.putInt("y", pos.getY());
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

    /**
     * Carves the default spawn chamber (the Vault is solid stone, §3.1, so
     * Y=64 would otherwise suffocate the player). Idempotent.
     */
    private static void carveEntryChamber(ServerLevel vault, BlockPos target) {
        int half = ENTRY_CHAMBER_FOOTPRINT / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int y = 0; y < ENTRY_CHAMBER_HEIGHT; y++) {
                    pos.set(target.getX() + dx, DEFAULT_ENTRY_Y + y, target.getZ() + dz);
                    if (!vault.getBlockState(pos).isAir()) {
                        vault.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
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
