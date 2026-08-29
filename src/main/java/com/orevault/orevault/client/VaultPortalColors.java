package com.orevault.orevault.client;

import java.util.List;

import com.orevault.orevault.block.ModBlocks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Client-side tier colours for the four portal interior blocks (#84): the
 * shared near-white portal texture is tinted per igniter tier — common
 * green, uncommon blue, rare purple, legendary orange. Tinting (rather than
 * four texture files) keeps a single source texture and matches the flat
 * translucent portal look.
 */
public final class VaultPortalColors {

    /** Tier tints (#84): green / blue / purple / orange. */
    public static final int COMMON = 0x66FF66;
    public static final int UNCOMMON = 0x5555FF;
    public static final int RARE = 0xCC33FF;
    public static final int LEGENDARY = 0xFF9933;

    private VaultPortalColors() {
    }

    /** Registers the block-tint handlers on the mod event bus (client only). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VaultPortalColors::onRegisterBlockColors);
    }

    private static void onRegisterBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(state -> COMMON), ModBlocks.VAULT_PORTAL_COMMON.get());
        event.register(List.of(state -> UNCOMMON), ModBlocks.VAULT_PORTAL_UNCOMMON.get());
        event.register(List.of(state -> RARE), ModBlocks.VAULT_PORTAL_RARE.get());
        event.register(List.of(state -> LEGENDARY), ModBlocks.VAULT_PORTAL_LEGENDARY.get());
    }
}
