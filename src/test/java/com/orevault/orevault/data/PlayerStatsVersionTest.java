package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

/**
 * Tests the §11 SavedData {@code dataVersion} contract for {@link PlayerStats},
 * which is versioned independently of its owning {@link OreVaultTeamData}
 * because it is serialized as a nested tag and grows on its own schedule.
 */
class PlayerStatsVersionTest {

    /** Stats with a representative value across the stat, pity and toggle groups. */
    private static PlayerStats populated() {
        PlayerStats stats = new PlayerStats();
        stats.recordOreMined("minecraft:iron_ore", -12);
        stats.recordStoneBroken(-30);
        stats.addResonance(640L);
        stats.recordVaultEchoTrigger();
        stats.setVolatileVeinsTriggerStreak(3);
        stats.setVolatileVeinsSafeWindow(true);
        stats.toggleTradeoff("tithe");
        return stats;
    }

    @Test
    void savesStampTheCurrentDataVersion() {
        CompoundTag tag = populated().toNbt();

        assertEquals(PlayerStats.CURRENT_DATA_VERSION, tag.getIntOr("data_version", -1));
    }

    @Test
    void currentDataVersionIsAtLeastOne() {
        assertTrue(PlayerStats.CURRENT_DATA_VERSION >= 1,
                "version 0 is reserved for unversioned legacy saves");
    }

    @Test
    void roundTripPreservesEveryField() {
        PlayerStats loaded = PlayerStats.fromNbt(populated().toNbt());

        assertEquals(1, loaded.getOresMined().get("minecraft:iron_ore"));
        assertEquals(1, loaded.getStoneBroken());
        assertEquals(640L, loaded.getResonanceLifetime());
        assertEquals(1, loaded.getVaultEchoTriggers());
        assertEquals(3, loaded.getVolatileVeinsTriggerStreak());
        assertTrue(loaded.isVolatileVeinsSafeWindow());
        assertTrue(loaded.isTradeoffActive("tithe"));
    }

    @Test
    void unversionedLegacyTagLoadsEveryFieldIntact() {
        CompoundTag legacy = populated().toNbt();
        legacy.remove("data_version");

        PlayerStats loaded = PlayerStats.fromNbt(legacy);

        assertEquals(1, loaded.getStoneBroken());
        assertEquals(640L, loaded.getResonanceLifetime());
        assertEquals(3, loaded.getVolatileVeinsTriggerStreak());
        assertTrue(loaded.isTradeoffActive("tithe"));
    }

    @Test
    void migratedLegacyTagIsRewrittenAtTheCurrentVersion() {
        CompoundTag legacy = populated().toNbt();
        legacy.remove("data_version");

        CompoundTag resaved = PlayerStats.fromNbt(legacy).toNbt();

        assertEquals(PlayerStats.CURRENT_DATA_VERSION, resaved.getIntOr("data_version", -1));
    }

    @Test
    void tagFromAFutureVersionStillLoads() {
        CompoundTag future = populated().toNbt();
        future.putInt("data_version", PlayerStats.CURRENT_DATA_VERSION + 1);

        PlayerStats loaded = PlayerStats.fromNbt(future);

        assertEquals(640L, loaded.getResonanceLifetime());
    }
}
