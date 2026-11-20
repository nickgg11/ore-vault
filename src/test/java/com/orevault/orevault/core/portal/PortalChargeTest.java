package com.orevault.orevault.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** §3.2: the charge sound and overlay, read from the local player's portal intensity (#407). */
class PortalChargeTest {
    /** The client's step while inside, from {@code LocalPlayer#handlePortalTransitionEffect}: 80 ticks to full. */
    private static final float IN = 0.0125F;

    @Test
    void frameRisesWithProgressAcrossAllEight() {
        assertEquals(0, PortalCharge.frame(0.0F));
        assertEquals(0, PortalCharge.frame(0.124F));
        assertEquals(1, PortalCharge.frame(0.125F));
        assertEquals(3, PortalCharge.frame(0.49F));
        assertEquals(4, PortalCharge.frame(0.5F));
        assertEquals(7, PortalCharge.frame(0.9F));
    }

    /** Full charge would be frame 8, one past the strip. */
    @Test
    void frameStaysInsideTheStrip() {
        assertEquals(7, PortalCharge.frame(1.0F));
        assertEquals(7, PortalCharge.frame(1.5F));
        assertEquals(0, PortalCharge.frame(-0.1F));
    }

    @Test
    void frameTopIsTheFrameTimesItsHeight() {
        assertEquals(0, PortalCharge.frameTop(0.0F));
        assertEquals(4 * 360, PortalCharge.frameTop(0.5F));
        assertEquals(7 * 360, PortalCharge.frameTop(1.0F));
        assertEquals(PortalCharge.FRAMES * PortalCharge.FRAME_HEIGHT, PortalCharge.TEXTURE_HEIGHT);
    }

    @Test
    void alphaRunsFromAThirdToNearlyOpaque() {
        assertEquals(0.35F, PortalCharge.alpha(0.0F), 1e-6F);
        assertEquals(0.625F, PortalCharge.alpha(0.5F), 1e-6F);
        assertEquals(0.90F, PortalCharge.alpha(1.0F), 1e-6F);
        assertEquals(0.90F, PortalCharge.alpha(2.0F), 1e-6F);
    }

    @Test
    void chargingWhileTheIntensityRises() {
        assertTrue(PortalCharge.charging(true, 0.0F, IN));
        assertTrue(PortalCharge.charging(true, 0.5F, 0.5F + IN));
    }

    /** At full the intensity stops moving while the player still stands in the portal waiting for travel. */
    @Test
    void chargingWhileHeldAtFull() {
        assertTrue(PortalCharge.charging(true, 1.0F, 1.0F));
    }

    /** Stepping out, the client takes 0.05 off a tick. */
    @Test
    void notChargingOnceTheIntensityFalls() {
        assertFalse(PortalCharge.charging(true, 0.5F, 0.45F));
        assertFalse(PortalCharge.charging(true, 1.0F, 0.95F));
    }

    @Test
    void notChargingAtRest() {
        assertFalse(PortalCharge.charging(true, 0.0F, 0.0F));
        assertFalse(PortalCharge.charging(true, 0.5F, 0.5F));
    }

    /** A nether portal keeps its own sound. */
    @Test
    void notChargingInAnotherPortal() {
        assertFalse(PortalCharge.charging(false, 0.0F, IN));
        assertFalse(PortalCharge.charging(false, 1.0F, 1.0F));
    }
}
