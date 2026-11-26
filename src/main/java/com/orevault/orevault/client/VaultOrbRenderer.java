package com.orevault.orevault.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orevault.orevault.core.mining.OrbRules;
import com.orevault.orevault.mining.VaultOrbEntity;
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
 * One renderer for every Vault orb (§11 Resonance Orb Entity): vanilla's {@code ExperienceOrbRenderer} on vanilla's orb
 * sheet, so an orb reads as an orb, sized by value ({@link OrbRules#icon}) and tinted by the entity instead
 * of vanilla's green pulse. The sheet is vanilla's and ships nothing of ours, so the 16x16 texture rule does not apply.
 */
public final class VaultOrbRenderer extends EntityRenderer<VaultOrbEntity, VaultOrbRenderState> {
    private static final Identifier ORB_SHEET = Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentCullItemTarget(ORB_SHEET);
    private static final int ALPHA = 128;

    public VaultOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.15F;
        shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(VaultOrbEntity entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public void submit(VaultOrbRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        int icon = state.icon;
        float u0 = (icon % 4 * 16) / 64.0F;
        float u1 = (icon % 4 * 16 + 16) / 64.0F;
        float v0 = (icon / 4 * 16) / 64.0F;
        float v1 = (icon / 4 * 16 + 16) / 64.0F;
        int r = state.tint >> 16 & 0xFF;
        int g = state.tint >> 8 & 0xFF;
        int b = state.tint & 0xFF;
        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.3F, 0.3F, 0.3F);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, -0.5F, -0.25F, r, g, b, u0, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, -0.25F, r, g, b, u1, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, 0.75F, r, g, b, u1, v0, state.lightCoords);
            vertex(buffer, pose, -0.5F, 0.75F, r, g, b, u0, v0, state.lightCoords);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int r, int g, int b, float u,
            float v, int lightCoords) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public VaultOrbRenderState createRenderState() {
        return new VaultOrbRenderState();
    }

    @Override
    public void extractRenderState(VaultOrbEntity entity, VaultOrbRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.icon = OrbRules.icon(entity.syncedValue());
        state.tint = entity.tint(state.ageInTicks);
    }
}
