package com.orevault.orevault.core.portal;

/**
 * The charge in a Vault portal, as the client's sound and overlay read it (§3.2; Nick, 2026-11-08; #407). Progress is
 * the local player's portal intensity, which rises 1/80 a tick inside a portal and falls 1/20 a tick after stepping out.
 *
 * <p>The overlay texture is 8 frames of 640x360 stacked in one 640x2880 strip; the frame is progress times 8, so the
 * veil closes in as the charge fills, and the alpha is the Designer's 0.35 + 0.55 times progress
 * ({@code docs/previews/e3-s15/README.md}).
 */
public final class PortalCharge {
    public static final int FRAMES = 8;
    public static final int FRAME_WIDTH = 640;
    public static final int FRAME_HEIGHT = 360;
    public static final int TEXTURE_HEIGHT = FRAMES * FRAME_HEIGHT;

    private static final float ALPHA_AT_REST = 0.35F;
    private static final float ALPHA_RANGE = 0.55F;

    private PortalCharge() {
    }

    /** The frame for this progress, 0 to 7: full charge would be 8, one past the strip. */
    public static int frame(float progress) {
        return Math.clamp((int) Math.floor(progress * FRAMES), 0, FRAMES - 1);
    }

    /** The frame's top row in the strip. */
    public static int frameTop(float progress) {
        return frame(progress) * FRAME_HEIGHT;
    }

    public static float alpha(float progress) {
        return ALPHA_AT_REST + ALPHA_RANGE * Math.clamp(progress, 0.0F, 1.0F);
    }

    /**
     * Whether the player is charging: in a Vault portal with the intensity rising, or held at full while travel is due.
     * A falling intensity means they stepped out, since the client only lowers it outside a portal.
     *
     * @param inVaultPortal the player's portal processor names a Vault portal
     */
    public static boolean charging(boolean inVaultPortal, float previous, float current) {
        return inVaultPortal && (current > previous || current >= 1.0F);
    }
}
