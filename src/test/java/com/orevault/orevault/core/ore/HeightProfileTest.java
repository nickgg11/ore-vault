package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** ADR 0009 "Which ore a vein is", with the Epic 4 triage's per-layer profiles and Vault-side slabs (rulings 3 and 4). */
class HeightProfileTest {
    /** The Vault column both dimension types map the overworld onto, and the bands, as {@code VaultVariant} gives them. */
    private static final int COLUMN_FLOOR = -63;
    private static final int COLUMN_TOP = 245;

    @Test
    void theMassesAreNormalized() {
        HeightProfile profile = new HeightProfile(-64, new double[] {2, 6});
        assertEquals(0.25, profile.masses()[0], 1e-12);
        assertEquals(0.75, profile.masses()[1], 1e-12);
    }

    @Test
    void aProfileWithNoMassIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new HeightProfile(-64, new double[3]));
    }

    /** The base type's mass below Y=1 lands in its lowest stone slab, so nothing is lost (QA-29's band-floor case). */
    @Test
    void theBaseStoneBandKeepsAllTheMass() {
        for (OrePicker.Candidate ore : VanillaOres.candidates()) {
            double[] slabs = ore.profile().slabs(COLUMN_FLOOR, COLUMN_TOP, 1, 245, true);
            assertEquals(1.0, sum(slabs), 1e-9, ore.groupId());
        }
    }

    /** In the expanded type the deepslate and stone bands share the mass between them. */
    @Test
    void theExpandedBandsShareTheMass() {
        for (OrePicker.Candidate ore : VanillaOres.candidates()) {
            double deepslate = sum(ore.profile().slabs(COLUMN_FLOOR, COLUMN_TOP, -63, -1, false));
            double stone = sum(ore.profile().slabs(COLUMN_FLOOR, COLUMN_TOP, 0, 245, false));
            assertEquals(1.0, deepslate + stone, 1e-9, ore.groupId());
        }
    }

    /** Architect, round 2: a range end inside a slab still conserves mass, split by exact overlap. */
    @Test
    void aRangeEndInsideASlabIsSplitExactly() {
        double[] masses = new double[100];
        for (int i = 0; i < 40; i++) {
            masses[i] = 1;
        }
        double[] slabs = new HeightProfile(0, masses).slabs(0, 99, 0, 99, false);
        assertEquals(1.0, sum(slabs), 1e-12);
        assertEquals(16 / 40.0, slabs[0], 1e-12);
        assertEquals(16 / 40.0, slabs[1], 1e-12);
        assertEquals(8 / 40.0, slabs[2], 1e-12);
        assertEquals(0.0, slabs[3], 1e-12);
    }

    /** §3.1: the stone band's top slab is Y225..245 in the base type, Y224..245 in the expanded one; deepslate's Y-31..-1. */
    @Test
    void theTopSlabIsMerged() {
        assertEquals(13, HeightProfile.slabOf(224, 1, 245));
        assertEquals(14, HeightProfile.slabOf(225, 1, 245));
        assertEquals(14, HeightProfile.slabOf(245, 1, 245));
        assertEquals(14, HeightProfile.slabOf(224, 0, 245));
        assertEquals(13, HeightProfile.slabOf(223, 0, 245));
        assertEquals(1, HeightProfile.slabOf(-32, -63, -1));
        assertEquals(2, HeightProfile.slabOf(-31, -63, -1));
        assertEquals(2, HeightProfile.slabOf(-1, -63, -1));
        assertEquals(0, HeightProfile.slabOf(-200, -63, -1));
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }
}
