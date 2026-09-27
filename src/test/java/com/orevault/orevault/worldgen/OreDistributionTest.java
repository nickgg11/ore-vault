package com.orevault.orevault.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.orevault.orevault.ore.OreClassifier.Rarity;

import org.junit.jupiter.api.Test;

/**
 * The §3.1 baseline distribution: how much ore a Vault generates before a single skill point is
 * spent, and where it sits.
 *
 * <p>This is the layer worth testing, because the failure modes are all silent. A budget that
 * overshoots breaches the 40% stone floor the spec calls a hard rule; a rarity mix that drifts from
 * the shares {@code ResonanceSystem} assumes invalidates the whole level curve without anything
 * looking wrong; and a depth mapping that inverts puts diamond at the surface, which nobody notices
 * until a playtest.</p>
 */
class OreDistributionTest {

    private static final double EPSILON = 1.0e-9;

    /** 16 x 16 x 245, the base type's stone band. */
    private static final int BASE_STONE_VOLUME = 16 * 16 * 245;

    // ----- the budget -----

    @Test
    void theBudgetIsTheDensityShareOfTheBand() {
        assertEquals(
                (int) (BASE_STONE_VOLUME * 0.03),
                OreDistribution.oreBudget(BASE_STONE_VOLUME, 0.03));
    }

    @Test
    void anEmptyBandGetsNoBudget() {
        assertEquals(0, OreDistribution.oreBudget(0, 0.03));
    }

    @Test
    void theBudgetCannotBreachTheFortyPercentStoneFloor() {
        // §3.1 states the floor as a hard rule rather than a balance value, so it is clamped here
        // and not left to whatever density a future node stacks up to.
        int budget = OreDistribution.oreBudget(BASE_STONE_VOLUME, 0.95);

        assertEquals((int) (BASE_STONE_VOLUME * VaultChunkGenerator.MAX_ORE_FRACTION), budget);
    }

    @Test
    void aNegativeDensityIsTreatedAsNone() {
        assertEquals(0, OreDistribution.oreBudget(BASE_STONE_VOLUME, -1.0));
    }

    @Test
    void theDeepslateBandIsRicherThanTheStoneBand() {
        // §6.1: the expanded type's deepslate band "carries the highest ore density in the mod".
        assertTrue(OreDistribution.DEEPSLATE_DENSITY > OreDistribution.STONE_DENSITY);
    }

    // ----- the rarity mix -----

    @Test
    void theSharesAreTheOnesTheLevelCurveAssumes() {
        // ResonanceSystem derives the whole curve from these three numbers. If the generator and
        // the curve disagree about how often a rare ore turns up, the curve is wrong and nothing
        // in play says so.
        assertEquals(1.0,
                OreDistribution.COMMON_SHARE + OreDistribution.UNCOMMON_SHARE + OreDistribution.RARE_SHARE,
                EPSILON);
    }

    @Test
    void theRollLandsInEachBandInShareOrder() {
        assertEquals(Rarity.COMMON, OreDistribution.rollRarity(0.0));
        assertEquals(Rarity.COMMON, OreDistribution.rollRarity(0.69));
        assertEquals(Rarity.UNCOMMON, OreDistribution.rollRarity(0.70));
        assertEquals(Rarity.UNCOMMON, OreDistribution.rollRarity(0.94));
        assertEquals(Rarity.RARE, OreDistribution.rollRarity(0.95));
        assertEquals(Rarity.RARE, OreDistribution.rollRarity(0.999));
    }

    @Test
    void aRollOfExactlyOneIsStillRareRatherThanAnError() {
        // RandomSource#nextDouble is exclusive of 1.0, but a caller passing it should not throw.
        assertEquals(Rarity.RARE, OreDistribution.rollRarity(1.0));
    }

    @Test
    void theRollReproducesTheSharesOverManySamples() {
        int[] counts = new int[Rarity.values().length];
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            counts[OreDistribution.rollRarity(i / (double) samples).ordinal()]++;
        }

        assertEquals(OreDistribution.COMMON_SHARE, counts[Rarity.COMMON.ordinal()] / (double) samples, 0.001);
        assertEquals(OreDistribution.UNCOMMON_SHARE, counts[Rarity.UNCOMMON.ordinal()] / (double) samples, 0.001);
        assertEquals(OreDistribution.RARE_SHARE, counts[Rarity.RARE.ordinal()] / (double) samples, 0.001);
    }

    // ----- vein size -----

    @Test
    void commonVeinsAreBiggerThanRareOnes() {
        assertTrue(OreDistribution.veinSize(Rarity.COMMON, 1.0) > OreDistribution.veinSize(Rarity.RARE, 1.0));
    }

    @Test
    void veinSizeStaysInsideItsBandAtBothEndsOfTheRoll() {
        for (Rarity rarity : Rarity.values()) {
            int low = OreDistribution.veinSize(rarity, 0.0);
            int high = OreDistribution.veinSize(rarity, 0.9999);
            assertTrue(low >= 1, rarity + " floor");
            assertTrue(high >= low, rarity + " range");
        }
    }

    // ----- depth -----

    @Test
    void anOreThatGeneratesLowInTheOverworldGeneratesLowInTheVault() {
        // Diamond: -64..16 in the overworld, so its midpoint sits near the bottom of the range.
        double diamond = OreDistribution.normalizedDepth(-64, 16);

        assertTrue(diamond < 0.25, "diamond mapped to " + diamond);
    }

    @Test
    void anOreThatGeneratesHighInTheOverworldGeneratesHighInTheVault() {
        double coal = OreDistribution.normalizedDepth(0, 320);

        assertTrue(coal > 0.5, "coal mapped to " + coal);
    }

    @Test
    void normalizedDepthIsClampedToTheUnitRange() {
        assertEquals(0.0, OreDistribution.normalizedDepth(-500, -400), EPSILON);
        assertEquals(1.0, OreDistribution.normalizedDepth(400, 500), EPSILON);
    }

    @Test
    void aDeepOreLandsAtTheBottomOfTheColumnAndAShallowOneAtTheTop() {
        int bottom = 1;
        int top = 246;

        assertTrue(OreDistribution.preferredY(0.0, bottom, top) <= bottom + 1);
        assertTrue(OreDistribution.preferredY(1.0, bottom, top) >= top - 2);
    }

    @Test
    void aPreferredHeightOutsideABandIsClampedIntoIt() {
        // The stone band of the base type starts at Y=1, so diamond's preference for Y=-30 has
        // nowhere to go but the bottom of the band. Clamping rather than skipping is what keeps
        // deep ore present in a dimension that has no deepslate at all.
        assertEquals(1, OreDistribution.clampToBand(-30, 1, 246));
        assertEquals(245, OreDistribution.clampToBand(9999, 1, 246));
        assertEquals(100, OreDistribution.clampToBand(100, 1, 246));
    }

    @Test
    void aBandOneBlockTallStillPlacesInsideItself() {
        assertEquals(5, OreDistribution.clampToBand(-99, 5, 6));
    }

    // ----- determinism -----

    @Test
    void aChunkRollsFromTheSameSeedEveryTime() {
        // Vault chunks generate lazily over months and a Vault is regenerated on reset (#93). A
        // chunk that came out differently depending on when it was first visited would change a
        // team's map under them.
        UUID team = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals(
                OreDistribution.chunkSeed(team, 12, -7),
                OreDistribution.chunkSeed(team, 12, -7));
    }

    @Test
    void neighbouringChunksRollDifferently() {
        UUID team = UUID.fromString("11111111-2222-3333-4444-555555555555");
        long here = OreDistribution.chunkSeed(team, 0, 0);

        assertTrue(here != OreDistribution.chunkSeed(team, 1, 0));
        assertTrue(here != OreDistribution.chunkSeed(team, 0, 1));
    }

    @Test
    void twoTeamsGetDifferentVaults() {
        UUID first = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID second = UUID.fromString("66666666-7777-8888-9999-000000000000");

        assertTrue(OreDistribution.chunkSeed(first, 4, 4) != OreDistribution.chunkSeed(second, 4, 4));
    }
}
