package com.orevault.orevault.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** E3-S11 (#245): the line QA's restart check and the unload spike read to tell that a Vault ticks (ADR 0002, QA-38). */
class TickLineTest {
    @Test
    void namesTheLevelAndTheServerTick() {
        assertEquals("tick orevault:vault_ab server_tick=40", new TickLine("orevault:vault_ab", 40).line());
    }

    @Test
    void isDueOnceASecond() {
        assertTrue(TickLine.due(0));
        assertFalse(TickLine.due(1));
        assertFalse(TickLine.due(10));
        assertFalse(TickLine.due(19));
        assertTrue(TickLine.due(20));
        assertTrue(TickLine.due(400));
        assertFalse(TickLine.due(401));
    }
}
