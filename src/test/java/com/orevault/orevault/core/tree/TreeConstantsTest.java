package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Every tree constant pinned (§4.3, §4.4). A change here is a balance change and needs the Balancer. */
class TreeConstantsTest {
    @Test
    void levelAndPoints() {
        assertEquals(30, TreeConstants.LEVEL_CAP);
        assertEquals(5, TreeConstants.POINTS_PER_LEVEL);
    }

    @Test
    void refundPricesOfSection44() {
        assertEquals(3, TreeConstants.REFUND_XP_PER_NODE);
        assertEquals(12, TreeConstants.REFUND_XP_PACT);
        assertEquals(30, TreeConstants.REFUND_XP_KEYSTONE);
    }

    @Test
    void windowsInGameTicks() {
        assertEquals(1_200, TreeConstants.UNDO_WINDOW_TICKS, "60 seconds");
        assertEquals(12_000, TreeConstants.FREE_RESPEC_WINDOW_TICKS, "10 minutes");
    }
}
