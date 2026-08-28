package com.orevault.orevault.item;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.portal.VaultPortalShape;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Vault Igniter (§3.3): one class drives all four tiers, with tier-specific
 * behaviour keyed off {@link Tier}.
 *
 * <ul>
 * <li>Tier 1 (Crude): standard portal activation.</li>
 * <li>Tier 2 (Attuned): particle burst on activation, 30% faster activation
 * animation (14 ticks vs 20).</li>
 * <li>Tier 3 (Resonant): additionally sets a personal entry point in the
 * Vault via right-click on a block there.</li>
 * <li>Tier 4 (Sovereign): additionally marks the player for instant
 * teleportation and (later, [31]) the reset button.</li>
 * </ul>
 *
 * <p>Arrival effects (Speed/Haste) are applied by {@code VaultTeleport} ([20])
 * based on {@link #highestTierLevel(Player)}.</p>
 */
public class VaultIgniterItem extends Item {

    /** The four igniter tiers (§3.3). */
    public enum Tier {
        CRUDE(1, 20),
        ATTUNED(2, 14),
        RESONANT(3, 14),
        SOVEREIGN(4, 14);

        private final int level;
        private final int activationTicks;

        Tier(int level, int activationTicks) {
            this.level = level;
            this.activationTicks = activationTicks;
        }

        public int level() {
            return level;
        }

        /** Progressive portal-fill duration; tier 2+ is 30% faster than the 20-tick standard. */
        public int activationTicks() {
            return activationTicks;
        }
    }

    /** Per-player persistent-data key for the custom entry point (§3.3, tier 3+). */
    public static final String ENTRY_POINT_TAG = "orevault_entry";

    private final Tier tier;

    public VaultIgniterItem(Tier tier) {
        super(new Item.Properties());
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    /** The igniter tier of the given stack, or {@code null} if it is not an igniter. */
    @Nullable
    public static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof VaultIgniterItem igniter ? igniter.tier : null;
    }

    /** Highest igniter tier carried by the player (main inventory + offhand); 0 if none. */
    public static int highestTierLevel(Player player) {
        int highest = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            Tier tier = tierOf(stack);
            if (tier != null) {
                highest = Math.max(highest, tier.level());
            }
        }
        Tier offhand = tierOf(player.getOffhandItem());
        if (offhand != null) {
            highest = Math.max(highest, offhand.level());
        }
        return highest;
    }

    /**
     * Right-click behaviour (§3.2, §3.3):
     * <ul>
     * <li>On a Vault Frame: scan and fill the portal (failure sound if invalid).</li>
     * <li>Tier 3+, on a block inside the Vault: set the personal entry point.</li>
     * </ul>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockState(pos).is(ModBlocks.VAULT_FRAME)) {
            Optional<VaultPortalShape> shape = VaultPortalShape.find(level, pos);
            if (shape.isPresent()) {
                VaultPortalShape portal = shape.get();
                level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (level instanceof ServerLevel serverLevel) {
                    portal.fillAnimated(serverLevel, tier.activationTicks(), tier.level() >= 2);
                } else {
                    portal.fill(level);
                }
            } else {
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        if (tier.level() >= 3 && player != null && !level.isClientSide() && VaultDimensions.isVaultDimension(level)) {
            setEntryPoint(player, pos);
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    /** Stores the clicked block as the player's personal Vault entry point (§3.3, tier 3+). */
    public static void setEntryPoint(Player player, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        player.getPersistentData().put(ENTRY_POINT_TAG, tag);
        player.sendSystemMessage(Component.literal("Vault entry point set."));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
    }
}
