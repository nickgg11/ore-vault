package com.orevault.orevault.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Vault Igniter — four tiers with escalating benefits (design spec section 3.3):
 * <ul>
 *   <li>T1 Crude: standard activation.</li>
 *   <li>T2 Attuned: particle burst on activation, Speed I on entry, 30% faster activation.</li>
 *   <li>T3 Resonant: Haste I on entry, sets a personal Vault entry point.</li>
 *   <li>T4 Sovereign: Haste II on entry, instant teleport with no cooldown, unlocks the reset button.</li>
 * </ul>
 */
public class VaultIgniterItem extends Item {
    private final int tier;

    public VaultIgniterItem(Properties properties, int tier, Rarity rarity) {
        super(properties.rarity(rarity).stacksTo(1));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** Called by the frame block after a successful portal ignition. */
    public void onPortalIgnited(ItemStack stack, Player player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (tier >= 2) {
            // Attuned+: particle burst
            for (int i = 0; i < 40; i++) {
                double x = pos.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 3.0;
                double y = pos.getY() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 3.0;
                double z = pos.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 3.0;
                level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.0, 0.05, 0.0, 0.15);
            }
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.8F, 1.3F);
        }
    }
}

