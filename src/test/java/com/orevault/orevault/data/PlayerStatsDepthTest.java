package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@code PlayerStats#deepestY} is a lifetime record that must never rise (§5.1).
 *
 * <p>Highwater Mark and Shallow Grace both read it, and both are permanent once earned: the first
 * pays more the deeper the record, the second stops paying forever once the record passes Y=100.
 * A record that can rise means the first silently loses value and the second comes back from the
 * dead, and neither is visible in play until someone notices their repair got worse.</p>
 */
class PlayerStatsDepthTest {

    @Test
    void theFirstBreakSetsTheRecord() {
        PlayerStats stats = new PlayerStats();

        stats.recordBlockBroken(42);

        assertEquals(42, stats.getDeepestY());
    }

    @Test
    void aDeeperBreakLowersTheRecord() {
        PlayerStats stats = new PlayerStats();

        stats.recordBlockBroken(42);
        stats.recordBlockBroken(-12);

        assertEquals(-12, stats.getDeepestY());
    }

    @Test
    void aShallowerBreakLeavesTheRecordAlone() {
        PlayerStats stats = new PlayerStats();

        stats.recordBlockBroken(-12);
        stats.recordBlockBroken(200);

        assertEquals(-12, stats.getDeepestY());
    }

    @Test
    void aRecordOfExactlyZeroIsARecordAndNotAnEmptyField() {
        // Zero is also the "no record yet" value of the stored field, so a record set at
        // Y=0 used to read as absent and the next break at any height replaced it.
        PlayerStats stats = new PlayerStats();

        stats.recordBlockBroken(0);
        stats.recordBlockBroken(120);

        assertEquals(0, stats.getDeepestY());
    }

    @Test
    void oreAndStoneBreaksSetTheRecordToo() {
        PlayerStats ore = new PlayerStats();
        PlayerStats stone = new PlayerStats();

        ore.recordOreMined("minecraft:diamond_ore", -40);
        stone.recordStoneBroken(-40);

        assertEquals(-40, ore.getDeepestY());
        assertEquals(-40, stone.getDeepestY());
    }

    @Test
    void aRecordOfZeroSurvivesASaveAndReload() {
        PlayerStats stats = new PlayerStats();
        stats.recordBlockBroken(0);

        PlayerStats reloaded = PlayerStats.fromNbt(stats.toNbt());
        reloaded.recordBlockBroken(120);

        assertEquals(0, reloaded.getDeepestY());
    }
}
