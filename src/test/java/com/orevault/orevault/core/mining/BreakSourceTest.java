package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** §3.4 "What machine-broken means": a hand break needs all three checks; everything else is a machine (S21, old #26). */
class BreakSourceTest {
    @Test
    void aListedRealServerPlayerIsAHandBreak() {
        assertEquals(BreakSource.HAND, BreakSource.of(true, false, true));
    }

    /** No breaker at all, or an entity that is not a server player (TNT, a mob): machine. */
    @Test
    void noServerPlayerIsAMachine() {
        assertEquals(BreakSource.MACHINE, BreakSource.of(false, false, false));
    }

    @Test
    void aFakePlayerIsAMachineEvenWhenListed() {
        assertEquals(BreakSource.MACHINE, BreakSource.of(true, true, true));
        assertEquals(BreakSource.MACHINE, BreakSource.of(true, true, false));
    }

    /** Planted break: dropping the player-list check pays a mod's fake player that does not override isFakePlayer(). */
    @Test
    void anUnlistedServerPlayerIsAMachine() {
        assertEquals(BreakSource.MACHINE, BreakSource.of(true, false, false));
    }
}
