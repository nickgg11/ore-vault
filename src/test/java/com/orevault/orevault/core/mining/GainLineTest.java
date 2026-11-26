package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** §10 {@code debug.log_resonance_gain}: one line per orb pickup, in a fixed shape a log parser can read. */
class GainLineTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID PLAYER = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");

    @Test
    void theLineIsFrozen() {
        assertEquals("[orevault-debug] resonance gain team=01234567-89ab-cdef-0123-456789abcdef "
                        + "collector=fedcba98-7654-3210-fedc-ba9876543210 orbs=3 value=12.500 team_size=2 pooled=13.750",
                new GainLine(TEAM, PLAYER, 3, 12_500L, 2, 13_750L).line());
    }

    @Test
    void thousandthsKeepTheirLeadingZeros() {
        assertEquals("[orevault-debug] resonance gain team=01234567-89ab-cdef-0123-456789abcdef "
                        + "collector=fedcba98-7654-3210-fedc-ba9876543210 orbs=1 value=0.050 team_size=3 pooled=0.000",
                new GainLine(TEAM, PLAYER, 1, 50L, 3, 0L).line());
    }
}
