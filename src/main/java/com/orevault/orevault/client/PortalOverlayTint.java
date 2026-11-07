package com.orevault.orevault.client;

import com.orevault.orevault.core.frame.FrameLight;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PortalProcessor;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The charging overlay in a Vault portal, in that portal's tier colour (Nick, 2026-11-07; our own overlay is #407).
 *
 * <p>26.1 has no hook for the overlay itself: {@code Gui#extractPortalOverlay} is private and always draws the nether
 * portal's sprite, inside the {@code CAMERA_OVERLAYS} layer, whenever the local player's {@code portalEffectIntensity} is
 * above zero. So around that one layer, and only while the player is charging in a Vault portal, the intensity is set to
 * zero for the layer's draw and put back after it, and this draws the portal's own near-white sprite tinted by its tier,
 * with vanilla's alpha curve. Nothing else reads the field in that window: the screen wobble is drawn before the GUI, and
 * the field moves only in the player's tick. A nether portal is left as vanilla draws it.
 */
final class PortalOverlayTint {
    /** The tier of the Vault portal the overlay is for, 0 for none. Kept while the overlay fades after stepping out. */
    private static int tier;
    private static boolean hidden;
    private static float intensity;
    private static float previousIntensity;

    private PortalOverlayTint() {
    }

    static void before(RenderGuiLayerEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.isCanceled() || !event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            return;
        }
        tier = tierOf(player.portalProcess, player.portalEffectIntensity, tier);
        if (tier == 0 || (player.portalEffectIntensity <= 0.0F && player.oPortalEffectIntensity <= 0.0F)) {
            return;
        }
        intensity = player.portalEffectIntensity;
        previousIntensity = player.oPortalEffectIntensity;
        player.portalEffectIntensity = 0.0F;
        player.oPortalEffectIntensity = 0.0F;
        hidden = true;
    }

    static void after(RenderGuiLayerEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!hidden || player == null || !event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            return;
        }
        player.portalEffectIntensity = intensity;
        player.oPortalEffectIntensity = previousIntensity;
        hidden = false;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        draw(event.getGuiGraphics(), Mth.lerp(partialTick, previousIntensity, intensity));
    }

    /** The processor names the portal while the player is in it; after they step out, the overlay fades in the last tier. */
    private static int tierOf(PortalProcessor process, float intensity, int last) {
        if (process != null) {
            for (SignetTier signet : SignetTier.values()) {
                if (process.isSamePortal(ModBlocks.portal(signet))) {
                    return signet.rank();
                }
            }
            return 0;
        }
        return intensity > 0.0F ? last : 0;
    }

    /** Vanilla's curve from {@code Gui#extractPortalOverlay}, with this portal's sprite and colour. */
    private static void draw(GuiGraphicsExtractor graphics, float strength) {
        float alpha = strength;
        if (alpha < 1.0F) {
            alpha *= alpha;
            alpha *= alpha;
            alpha = alpha * 0.8F + 0.2F;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .getParticleMaterial(ModBlocks.portal(SignetTier.values()[tier - 1]).defaultBlockState()).sprite();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                ARGB.color(alpha, FrameLight.portalColor(tier)));
    }
}
