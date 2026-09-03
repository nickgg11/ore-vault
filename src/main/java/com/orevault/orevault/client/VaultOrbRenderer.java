package com.orevault.orevault.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orevault.orevault.entity.VaultOrbEntity;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws Vault orbs (§11): the vanilla orb sprite sheet, sized by value, tinted
 * per orb type — blue/cyan for Resonance.
 *
 * <p>Reusing the vanilla texture is deliberate rather than a shortcut. §11 says
 * an orb should read as an orb and differ by colour, and a Vault floor will
 * usually have both kinds on it at once; matching the silhouette exactly and
 * changing only the hue is what makes "that one is Resonance" legible at a
 * glance. It also means no new texture is needed before the art task lands.</p>
 *
 * <p>The tint comes off the entity rather than being baked in here, so the
 * Animus orb gets its red for free when it arrives post-1.0.</p>
 */
public class VaultOrbRenderer extends EntityRenderer<VaultOrbEntity, VaultOrbRenderState> {

    private static final Identifier ORB_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentCullItemTarget(ORB_TEXTURE);

    /** Sprite cell size and sheet width, matching the vanilla orb sheet. */
    private static final int CELL = 16;
    private static final float SHEET = 64.0F;

    /** How far the tint is allowed to pulse, so an orb shimmers rather than sits flat. */
    private static final float PULSE_DEPTH = 0.25F;

    public VaultOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(VaultOrbEntity entity, BlockPos blockPos) {
        // Orbs self-illuminate, as vanilla's do; a Vault is fully lit anyway, but
        // this keeps them readable against a dark deepslate band.
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public VaultOrbRenderState createRenderState() {
        return new VaultOrbRenderState();
    }

    @Override
    public void extractRenderState(VaultOrbEntity entity, VaultOrbRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.icon = entity.getIcon();
        state.tint = entity.getTint();
    }

    @Override
    public void submit(VaultOrbRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        poseStack.pushPose();

        int icon = state.icon;
        float u0 = (icon % 4 * CELL) / SHEET;
        float u1 = (icon % 4 * CELL + CELL) / SHEET;
        float v0 = (icon / 4 * CELL) / SHEET;
        float v1 = (icon / 4 * CELL + CELL) / SHEET;

        // Pulse the tint around its base colour on the same period vanilla uses,
        // so a Resonance orb has the same "alive" feel as the XP orbs beside it.
        float phase = Mth.sin(state.ageInTicks / 2.0F);
        float brightness = 1.0F - PULSE_DEPTH + PULSE_DEPTH * (phase + 1.0F) * 0.5F;
        int red = channel(state.tint >> 16, brightness);
        int green = channel(state.tint >> 8, brightness);
        int blue = channel(state.tint, brightness);

        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.3F, 0.3F, 0.3F);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, -0.5F, -0.25F, red, green, blue, u0, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, -0.25F, red, green, blue, u1, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, 0.75F, red, green, blue, u1, v0, state.lightCoords);
            vertex(buffer, pose, -0.5F, 0.75F, red, green, blue, u0, v0, state.lightCoords);
        });

        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static int channel(int shifted, float brightness) {
        return Mth.clamp(Math.round((shifted & 0xFF) * brightness), 0, 255);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y,
                               int red, int green, int blue, float u, float v, int lightCoords) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(red, green, blue, 128)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
