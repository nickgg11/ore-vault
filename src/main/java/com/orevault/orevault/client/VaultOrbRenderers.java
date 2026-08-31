package com.orevault.orevault.client;

import com.orevault.orevault.entity.ModEntities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Entity renderer bindings for the Vault orbs (client only).
 *
 * <p>This exists because a registered entity type with no renderer is a hard
 * client crash, not a warning: {@code Minecraft} self-tests the renderer table
 * on startup and throws when an entry is missing. Registering the orb without
 * this would have taken down {@code runClient} on launch while leaving a
 * dedicated server perfectly happy — the asymmetry §CLAUDE.md's side-safety rule
 * is about.</p>
 *
 * <p>Both orb types share {@link VaultOrbRenderer}, which takes its tint from
 * the entity (§11). That is the whole reason the tint lives on the entity rather
 * than in the renderer: the Animus orb registers here and needs nothing else.</p>
 */
public final class VaultOrbRenderers {

    private VaultOrbRenderers() {
    }

    /** Registers the renderer bindings on the mod event bus (client only). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VaultOrbRenderers::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RESONANCE_ORB.get(), VaultOrbRenderer::new);
    }
}
