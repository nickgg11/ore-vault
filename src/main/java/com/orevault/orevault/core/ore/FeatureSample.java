package com.orevault.orevault.core.ore;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * What the classifier's adapter read from one overworld placed feature, as plain numbers (ADR 0009 steps 2 to 7), so the
 * rest of the classification is pure and has a golden test. The adapter sampled the count and height providers from the
 * feature's own seed ({@link SamplingSeed}); everything here is already an expectation.
 *
 * @param featureId the placed feature's id
 * @param targets each block the feature places and which bands' filler its rule test matches
 * @param veinsPerChunk expected veins a chunk: the mean count, times {@code 1/chance} for a rarity filter, times the share
 *     of height draws inside the overworld
 * @param blocksPerVein mean blocks a vein places in solid rock (step 7)
 * @param biomeShare the share of the overworld's biomes whose underground-ores step lists the feature, over 0
 * @param sourceFloorY the overworld Y of {@code originMass[0]}
 * @param originMass the kept height draws per overworld layer, upward; any scale
 */
public record FeatureSample(String featureId, List<Target> targets, double veinsPerChunk, double blocksPerVein,
        double biomeShare, int sourceFloorY, double[] originMass) {
    /** ADR 0009 step 3: a feature more than half of the overworld's biomes list is all-biome. */
    public static final double ALL_BIOME_SHARE = 0.5;

    /**
     * One block a feature places.
     *
     * @param blockId the block's id
     * @param stoneBand whether the target's rule test matches stone's default state, so it is what the stone band places
     * @param deepslateBand the same for deepslate
     */
    public record Target(String blockId, boolean stoneBand, boolean deepslateBand) {
        public Target {
            Objects.requireNonNull(blockId);
        }
    }

    public FeatureSample {
        Objects.requireNonNull(featureId);
        targets = List.copyOf(targets);
        if (!(veinsPerChunk >= 0) || Double.isInfinite(veinsPerChunk) || !(blocksPerVein >= 0)
                || Double.isInfinite(blocksPerVein)) {
            throw new IllegalArgumentException(featureId + ": " + veinsPerChunk + " veins of " + blocksPerVein);
        }
        if (!(biomeShare > 0 && biomeShare <= 1)) {
            throw new IllegalArgumentException(featureId + " is listed by a share of " + biomeShare);
        }
        originMass = originMass.clone();
        double total = 0;
        for (double mass : originMass) {
            if (!(mass >= 0) || Double.isInfinite(mass)) {
                throw new IllegalArgumentException(featureId + " has a negative or non-finite origin mass");
            }
            total += mass;
        }
        if (veinsPerChunk > 0 && total <= 0) {
            throw new IllegalArgumentException(featureId + " has veins but no origin inside the overworld");
        }
    }

    @Override
    public double[] originMass() {
        return originMass.clone();
    }

    /** Veins times blocks a vein: the feature's part of step 8's sum. */
    public double expectedBlocks() {
        return veinsPerChunk * blocksPerVein;
    }

    public boolean allBiome() {
        return biomeShare > ALL_BIOME_SHARE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FeatureSample that && featureId.equals(that.featureId) && targets.equals(that.targets)
                && Double.compare(veinsPerChunk, that.veinsPerChunk) == 0
                && Double.compare(blocksPerVein, that.blocksPerVein) == 0
                && Double.compare(biomeShare, that.biomeShare) == 0 && sourceFloorY == that.sourceFloorY
                && Arrays.equals(originMass, that.originMass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureId, targets, veinsPerChunk, blocksPerVein, biomeShare, sourceFloorY)
                * 31 + Arrays.hashCode(originMass);
    }

    @Override
    public String toString() {
        return "FeatureSample[" + featureId + ", " + veinsPerChunk + " veins of " + blocksPerVein + ", share "
                + biomeShare + "]";
    }
}
