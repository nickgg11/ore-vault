package com.orevault.orevault.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** What {@link VaultOrbRenderer} draws an orb from: the sprite on vanilla's sheet and the tint at this frame. */
public final class VaultOrbRenderState extends EntityRenderState {
    public int icon;
    /** ARGB; the alpha is ignored, the quad is drawn at vanilla's 128. */
    public int tint;
}
