package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ADR 0009 "Which ore a vein is", with the Epic 4 triage's per-layer profiles (ruling 4), read at the overworld's own Y
 * since 2026-11-21 (ADR 0009 amendment): no linear map and no fold.
 */
class HeightProfileTest {
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

    /** One to one: the mass at overworld Y is the mass at Vault Y, and nothing outside the overworld. */
    @Test
    void massIsReadAtItsOwnY() {
        HeightProfile profile = new HeightProfile(-64, new double[] {1, 0, 3});
        assertEquals(0.25, profile.massAt(-64), 1e-12);
        assertEquals(0.0, profile.massAt(-63), 1e-12);
        assertEquals(0.75, profile.massAt(-62), 1e-12);
        assertEquals(0.0, profile.massAt(-65), 1e-12);
        assertEquals(0.0, profile.massAt(-61), 1e-12);
    }

    /** A band's layers hold exactly the profile's mass there; what lies outside is dropped, not folded in (§3.1). */
    @Test
    void aBandKeepsOnlyItsOwnMass() {
        for (OrePicker.Candidate ore : VanillaOres.candidates()) {
            double[] base = ore.profile().layers(1, 95);
            double inside = 0;
            for (int y = 1; y <= 95; y++) {
                inside += ore.profile().massAt(y);
                assertEquals(ore.profile().massAt(y), base[y - 1], 1e-15, ore.groupId() + " Y=" + y);
            }
            assertEquals(inside, sum(base), 1e-12, ore.groupId());
        }
    }

    /** The expanded type's bands share vanilla's Y-63..95, and the rest of the overworld is outside both. */
    @Test
    void theExpandedBandsAreVanillasOwnLayers() {
        for (OrePicker.Candidate ore : VanillaOres.candidates()) {
            double all = sum(ore.profile().layers(-64, 319));
            double deepslate = sum(ore.profile().layers(-63, -1));
            double stone = sum(ore.profile().layers(0, 95));
            assertEquals(1.0, all, 1e-9, ore.groupId());
            assertEquals(1.0 - ore.profile().massAt(-64) - sum(ore.profile().layers(96, 319)), deepslate + stone, 1e-9,
                    ore.groupId());
        }
    }

    /** §3.1: diamond, redstone and gold sit low; most of coal, iron and emerald lies above Y=95 and is not the band's. */
    @Test
    void vanillasOresSitWhereVanillaPutsThem() {
        java.util.Map<String, HeightProfile> profiles = new java.util.HashMap<>();
        VanillaOres.candidates().forEach(ore -> profiles.put(ore.groupId(), ore.profile()));
        assertEquals(0.0, sum(profiles.get("minecraft:diamond_ore").layers(17, 95)), 1e-12);
        assertEquals(0.0, sum(profiles.get("minecraft:redstone_ore").layers(16, 95)), 1e-12);
        assertEquals(0.0, sum(profiles.get("minecraft:gold_ore").layers(33, 95)), 1e-12);
        assertTrue(sum(profiles.get("minecraft:coal_ore").layers(96, 319)) > 0.5);
        assertTrue(sum(profiles.get("minecraft:iron_ore").layers(96, 319)) > 0.5);
        assertTrue(sum(profiles.get("minecraft:emerald_ore").layers(96, 319)) > 0.5);
    }

    @Test
    void anEmptyRangeIsRefused() {
        HeightProfile profile = new HeightProfile(0, new double[] {1});
        assertThrows(IllegalArgumentException.class, () -> profile.layers(5, 4));
    }

    /** §3.1: the base stone band's slabs are Y1..16 to Y49..64 and Y65..95 merged; expanded Y0..15 to Y80..95; deepslate's. */
    @Test
    void theTopSlabIsMerged() {
        assertEquals(3, HeightProfile.slabOf(64, 1, 95));
        assertEquals(4, HeightProfile.slabOf(65, 1, 95));
        assertEquals(4, HeightProfile.slabOf(95, 1, 95));
        assertEquals(4, HeightProfile.slabOf(79, 0, 95));
        assertEquals(5, HeightProfile.slabOf(80, 0, 95));
        assertEquals(5, HeightProfile.slabOf(95, 0, 95));
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
