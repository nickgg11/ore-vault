package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.orevault.orevault.skill.NodeCosts;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

/**
 * Tests the §11 SavedData {@code dataVersion} contract: every save is stamped
 * with the current version, and a stored tag from an older version is migrated
 * forward on load rather than being read with the wrong shape.
 */
class OreVaultTeamDataVersionTest {

    private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    /** Game time the free-respec window is opened at; the stored value is this plus the window length. */
    private static final long RESPEC_WINDOW_OPENED_AT = 500L;

    /** Team data with a representative value in every persisted field. */
    private static OreVaultTeamData populated() {
        OreVaultTeamData data = new OreVaultTeamData(TEAM_ID);
        data.addResonancePool(1234L);
        data.setResonanceLevel(7);
        data.addResonanceSkillPoints(21);
        data.setChunkLoadingTickets(4);
        data.startFreeRespecWindow(RESPEC_WINDOW_OPENED_AT);
        data.resonanceTree().setUnlockedTier("vein_expansion", 3);
        data.getOrCreatePlayerStats(TEAM_ID).recordStoneBroken(-10);
        return data;
    }

    @Test
    void savesStampTheCurrentDataVersion() {
        CompoundTag tag = populated().toNbt();

        assertEquals(OreVaultTeamData.CURRENT_DATA_VERSION, tag.getIntOr("data_version", -1));
    }

    @Test
    void currentDataVersionIsAtLeastOne() {
        assertTrue(OreVaultTeamData.CURRENT_DATA_VERSION >= 1,
                "version 0 is reserved for unversioned legacy saves");
    }

    @Test
    void roundTripPreservesEveryField() {
        OreVaultTeamData loaded = OreVaultTeamData.fromNbt(populated().toNbt());

        assertEquals(TEAM_ID, loaded.teamId());
        assertEquals(1234L, loaded.getResonancePool());
        assertEquals(7, loaded.getResonanceLevel());
        assertEquals(21, loaded.getResonanceSkillPoints());
        assertEquals(4, loaded.getChunkLoadingTickets());
        assertEquals(RESPEC_WINDOW_OPENED_AT + NodeCosts.FREE_RESPEC_WINDOW_TICKS,
                loaded.getFreeRespecUntilGameTime());
        assertEquals(3, loaded.resonanceTree().unlockedTier("vein_expansion"));
        assertEquals(1, loaded.getPlayerStats(TEAM_ID).getStoneBroken());
    }

    /**
     * Saves written before versioning existed carry no {@code data_version} key.
     * They must migrate forward intact, not be discarded or misread.
     */
    @Test
    void unversionedLegacyTagLoadsEveryFieldIntact() {
        CompoundTag legacy = populated().toNbt();
        legacy.remove("data_version");

        OreVaultTeamData loaded = OreVaultTeamData.fromNbt(legacy);

        assertEquals(1234L, loaded.getResonancePool());
        assertEquals(7, loaded.getResonanceLevel());
        assertEquals(21, loaded.getResonanceSkillPoints());
        assertEquals(3, loaded.resonanceTree().unlockedTier("vein_expansion"));
        assertEquals(1, loaded.getPlayerStats(TEAM_ID).getStoneBroken());
    }

    @Test
    void migratedLegacyTagIsRewrittenAtTheCurrentVersion() {
        CompoundTag legacy = populated().toNbt();
        legacy.remove("data_version");

        CompoundTag resaved = OreVaultTeamData.fromNbt(legacy).toNbt();

        assertEquals(OreVaultTeamData.CURRENT_DATA_VERSION, resaved.getIntOr("data_version", -1));
    }

    /**
     * A world opened with an older build of the mod must not hard-fail; load
     * best-effort so the player keeps their progression rather than losing it.
     */
    @Test
    void tagFromAFutureVersionStillLoads() {
        CompoundTag future = populated().toNbt();
        future.putInt("data_version", OreVaultTeamData.CURRENT_DATA_VERSION + 1);

        OreVaultTeamData loaded = OreVaultTeamData.fromNbt(future);

        assertEquals(1234L, loaded.getResonancePool());
        assertEquals(7, loaded.getResonanceLevel());
    }
}
