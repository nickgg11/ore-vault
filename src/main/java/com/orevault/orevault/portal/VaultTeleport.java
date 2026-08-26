package com.orevault.orevault.portal;

import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Overworld -> Vault and Vault -> Overworld routing for players (design spec section 3.2).
 */
public final class VaultTeleport {
    public static final String RETURN_TAG = "orevault_return";
    public static final String ENTRY_TAG = "orevault_entry";

    private VaultTeleport() {
    }

    public static boolean isInVault(Player player) {
        return TeamHelper.isVaultDimension(player.level());
    }

    /** Best igniter tier the player carries (0 = none). */
    public static int bestIgniterTier(Player player) {
        int best = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.VAULT_IGNITER.get())) {
                best = Math.max(best, 1);
            } else if (stack.is(ModItems.ATTUNED_VAULT_IGNITER.get())) {
                best = Math.max(best, 2);
            } else if (stack.is(ModItems.RESONANT_VAULT_IGNITER.get())) {
                best = Math.max(best, 3);
            } else if (stack.is(ModItems.SOVEREIGN_VAULT_IGNITER.get())) {
                best = Math.max(best, 4);
            }
        }
        return best;
    }

    /** Called by the portal block: computes the destination transition or null. */
    public static TeleportTransition computeTransition(ServerLevel currentLevel, Player player, BlockPos portalPos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        if (serverPlayer.isOnPortalCooldown()) {
            return null;
        }
        if (isInVault(serverPlayer)) {
            // Vault -> Overworld
            ServerLevel overworld = currentLevel.getServer().overworld();
            Vec3 dest = readReturnPos(serverPlayer);
            BlockPos destPos = BlockPos.containing(dest);
            if (!overworld.isLoaded(destPos)) {
                dest = Vec3.atCenterOf(overworld.getSharedSpawnPos());
            }
            return new TeleportTransition(overworld, dest, Vec3.ZERO, serverPlayer.getYRot(), serverPlayer.getXRot(),
                    TeleportTransition.PLAY_PORTAL_SOUND);
        }
        // Overworld -> Vault
        UUID teamId = TeamHelper.teamIdFor(serverPlayer);
        if (teamId == null) {
            return null;
        }
        ServerLevel vault = VaultDimensions.ensureVault(currentLevel.getServer(), teamId);
        if (vault == null) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("orevault.msg.no_team"), true);
            return null;
        }
        // Save return position
        writeReturnPos(serverPlayer, serverPlayer.position());
        Vec3 dest = readEntryPos(serverPlayer, vault);
        return new TeleportTransition(vault, dest, Vec3.ZERO, serverPlayer.getYRot(), serverPlayer.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND);
    }

    /** Applies arrival effects based on the best igniter tier in the player's inventory. */
    public static void applyArrivalEffects(ServerPlayer player, ServerLevel vault) {
        int tier = bestIgniterTier(player);
        switch (tier) {
            case 2 -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 5 * 20, 0, false, true));
            case 3 -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, 10 * 20, 0, false, true));
            case 4 -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, 15 * 20, 1, false, true));
            default -> {
            }
        }
        if (tier >= 2) {
            vault.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 1.0F);
        }
    }

    private static Vec3 readReturnPos(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        CompoundTag pos = tag.getCompoundOrEmpty(RETURN_TAG);
        if (!pos.isEmpty()) {
            return new Vec3(pos.getIntOr("x", 0) + 0.5, pos.getIntOr("y", 0) + 0.1, pos.getIntOr("z", 0) + 0.5);
        }
        ServerLevel overworld = player.level().getServer().overworld();
        return Vec3.atCenterOf(overworld.getSharedSpawnPos());
    }

    private static void writeReturnPos(ServerPlayer player, Vec3 pos) {
        CompoundTag tag = player.getPersistentData();
        CompoundTag returnPos = new CompoundTag();
        returnPos.putInt("x", (int) Math.floor(pos.x));
        returnPos.putInt("y", (int) Math.floor(pos.y));
        returnPos.putInt("z", (int) Math.floor(pos.z));
        tag.put(RETURN_TAG, returnPos);
    }

    private static Vec3 readEntryPos(ServerPlayer player, ServerLevel vault) {
        CompoundTag tag = player.getPersistentData();
        CompoundTag pos = tag.getCompoundOrEmpty(ENTRY_TAG);
        if (!pos.isEmpty()) {
            BlockPos blockPos = new BlockPos(pos.getIntOr("x", 0), pos.getIntOr("y", 0), pos.getIntOr("z", 0));
            if (vault.isLoaded(blockPos)) {
                return new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);
            }
        }
        // Fallback: mirrored XZ at Y=64
        BlockPos mirror = new BlockPos(player.blockPosition().getX(), 64, player.blockPosition().getZ());
        return new Vec3(mirror.getX() + 0.5, 64.1, mirror.getZ() + 0.5);
    }

    /** Tier 3+ igniter: set a personal entry point inside the Vault. */
    public static void setEntryPoint(ServerPlayer player, BlockPos clicked) {
        CompoundTag tag = player.getPersistentData();
        CompoundTag pos = new CompoundTag();
        pos.putInt("x", clicked.getX());
        pos.putInt("y", clicked.getY());
        pos.putInt("z", clicked.getZ());
        tag.put(ENTRY_TAG, pos);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("orevault.msg.entry_set",
                clicked.getX(), clicked.getY(), clicked.getZ()), false);
        player.level().playSound(null, clicked, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.5F, 1.5F);
    }
}
