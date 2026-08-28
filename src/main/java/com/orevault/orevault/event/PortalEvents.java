package com.orevault.orevault.event;

import com.orevault.orevault.block.ModBlocks;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Portal protection: the Ore Vault Portal block is unbreakable (§3.2,
 * hardness -1). Survival mining already respects that, but creative mode
 * bypasses block hardness, so the break is cancelled here for everyone —
 * with one escape hatch: a creative player holding sneak may remove portal
 * blocks, so stuck portals can still be cleaned up during building.
 */
public final class PortalEvents {

    private PortalEvents() {
    }

    @SubscribeEvent
    public static void onBreakBlock(BreakBlockEvent event) {
        if (!event.getState().is(ModBlocks.VAULT_PORTAL)) {
            return;
        }
        Player player = event.getPlayer();
        boolean creativeOverride = player.getAbilities().instabuild && player.isShiftKeyDown();
        if (!creativeOverride) {
            event.setCanceled(true);
        }
    }
}
