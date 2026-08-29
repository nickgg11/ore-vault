package com.orevault.orevault.client;

import com.orevault.orevault.entity.ModEntities;

import net.minecraft.client.renderer.entity.NoopRenderer;
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
 * <p>{@link NoopRenderer} is a placeholder. The real renderer — a tinted orb
 * sized by value, blue/cyan per §11 — is [40], so until that lands orbs are
 * collectable but invisible.</p>
 */
public final class VaultOrbRenderers {

    private VaultOrbRenderers() {
    }

    /** Registers the renderer bindings on the mod event bus (client only). */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VaultOrbRenderers::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RESONANCE_ORB.get(), NoopRenderer::new);
    }
}
