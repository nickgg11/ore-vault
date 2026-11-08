package com.orevault.orevault.client;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.frame.FrameLight;
import com.orevault.orevault.core.portal.PortalCharge;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PortalProcessor;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * Our charging overlay in a Vault portal (§3.2; Nick, 2026-11-08; #407): a stepped veil closing in from the screen edges,
 * one of the 8 frames of a near-white strip picked by charge progress and tinted to the portal's tier. It replaced the
 * vanilla portal texture tinted per tier (Nick, 2026-11-07).
 *
 * <p>26.1 has no hook for the overlay itself: {@code Gui#extractPortalOverlay} is private and always draws the nether
 * portal's sprite, inside the {@code CAMERA_OVERLAYS} layer, whenever the local player's {@code portalEffectIntensity} is
 * above zero. So around that one layer, and only while the player is charging in a Vault portal, the intensity is set to
 * zero for the layer's draw and put back after it, and this draws ours. Nothing else reads the field in that window: the
 * screen wobble is drawn before the GUI and stays vanilla, and the field moves only in the player's tick. A nether portal
 * is left as vanilla draws it.
 */
final class PortalOverlay {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "textures/misc/portal_overlay.png");

    /** The tier of the Vault portal the overlay is for, 0 for none. Kept while the overlay fades after stepping out. */
    private static int tier;
    private static boolean hidden;
    private static float intensity;
    private static float previousIntensity;

    private PortalOverlay() {
    }

    static void before(RenderGuiLayerEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.isCanceled() || !event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            return;
        }
        int now = vaultTier(player.portalProcess);
        tier = player.portalProcess != null ? now : player.portalEffectIntensity > 0.0F ? tier : 0;
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

    /** The rank of the Vault portal this processor is for, 0 for none or another portal. */
    static int vaultTier(@Nullable PortalProcessor process) {
        if (process != null) {
            for (SignetTier signet : SignetTier.values()) {
                if (process.isSamePortal(ModBlocks.portal(signet))) {
                    return signet.rank();
                }
            }
        }
        return 0;
    }

    /** The frame for this progress, stretched to the screen as vanilla stretches its sprite. */
    private static void draw(GuiGraphicsExtractor graphics, float progress) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0.0F, PortalCharge.frameTop(progress),
                graphics.guiWidth(), graphics.guiHeight(), PortalCharge.FRAME_WIDTH, PortalCharge.FRAME_HEIGHT,
                PortalCharge.FRAME_WIDTH, PortalCharge.TEXTURE_HEIGHT,
                ARGB.color(PortalCharge.alpha(progress), FrameLight.portalColor(tier)));
    }
}
