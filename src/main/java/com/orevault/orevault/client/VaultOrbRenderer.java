package com.orevault.orevault.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orevault.orevault.entity.VaultOrbEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderer for Resonance/Animus orbs: a pulsing glowing quad tinted per orb type
 * (Resonance = cyan, Animus = red). Follows the 26.1 render-state pipeline.
 */
@OnlyIn(Dist.CLIENT)
public class VaultOrbRenderer extends EntityRenderer<VaultOrbEntity, VaultOrbRenderer.OrbRenderState> {
    private final RenderType renderType;
    private final float red, green, blue;

    public VaultOrbRenderer(EntityRendererProvider.Context context, Identifier texture,
                            float red, float green, float blue) {
        super(context);
        this.renderType = RenderTypes.entityTranslucentCullItemTarget(texture);
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(VaultOrbEntity entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public void submit(OrbRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        float pulse = (Mth.sin(state.ageInTicks / 2.0F) + 1.0F) * 0.5F;
        int r = (int) (red * (180 + 75 * pulse));
        int g = (int) (green * (180 + 75 * pulse));
        int b = (int) (blue * (180 + 75 * pulse));
        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.3F, 0.3F, 0.3F);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            vertex(buffer, pose, -0.5F, -0.25F, r, g, b, 0.0F, 1.0F, state.lightCoords);
            vertex(buffer, pose, 0.5F, -0.25F, r, g, b, 1.0F, 1.0F, state.lightCoords);
            vertex(buffer, pose, 0.5F, 0.75F, r, g, b, 1.0F, 0.0F, state.lightCoords);
            vertex(buffer, pose, -0.5F, 0.75F, r, g, b, 0.0F, 0.0F, state.lightCoords);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y,
                               int r, int g, int b, float u, float v, int lightCoords) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, 200)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public OrbRenderState createRenderState() {
        return new OrbRenderState();
    }

    public static class OrbRenderState extends EntityRenderState {
    }
}
