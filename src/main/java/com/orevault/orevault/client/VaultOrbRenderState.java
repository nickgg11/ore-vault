package com.orevault.orevault.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Per-frame snapshot the orb renderer draws from (§11).
 *
 * <p>Extraction runs on the render thread against the entity; drawing runs later
 * against this. Only the two things that differ between orbs are carried over —
 * the sprite index the value maps to, and the tint that says which kind of orb
 * it is — so the Animus orb needs no new state class.</p>
 */
public class VaultOrbRenderState extends EntityRenderState {

    /** Sprite index on the vanilla orb sheet; see {@code VaultOrbEntity#getIcon}. */
    public int icon;

    /** Packed RGB tint. */
    public int tint;
}
