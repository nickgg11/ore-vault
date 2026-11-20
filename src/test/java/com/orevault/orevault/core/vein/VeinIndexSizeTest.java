package com.orevault.orevault.core.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.BandBounds;
import com.orevault.orevault.core.gen.ChunkSeed;
import com.orevault.orevault.core.gen.GenSalts;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.gen.OrePickers;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.gen.VeinExtent;
import com.orevault.orevault.core.gen.VeinPlacer;
import com.orevault.orevault.core.gen.VeinRequest;
import com.orevault.orevault.core.gen.VeinShape;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The vein index's size gate, before E4-S3 (#247): ADR 0004 "Pending measurement", as the Epic 4 triage ruled (ruling 1).
 * The gate is the 1.0 tree's heaviest generation stack, pinned now from §6.1 and §11 so it does not wait for T9
 * (Balancer, BAL-E4-1): Vein Proliferation 5 times Abundance as ×4.4 on every count; Vein Expansion 5 with Volatile
 * Veins and Abundance's -20% as ×1.8 blocks a vein; Deep Veins 2 doubling the deepslate count in its lowest 30 layers,
 * with the deepslate clip at 0.6092 (0.60 / 0.985); 12 Guide Veins of configured size 24; and Stone Reduction as every
 * box grown by one layer on every side. Expanded type, 81 chunks, the maximum deciding: at most 73,728 bytes. The
 * Balancer's estimate is about 40.5 KB. Two further setups are printed, not gated, so the headroom is on record.
 */
class VeinIndexSizeTest {
    private static final UUID TEAM = UUID.fromString("0a1b2c3d-4e5f-4061-8728-394a5b6c7d8e");
    private static final VaultVariant EXPANDED = VaultVariant.EXPANDED;
    private static final double PROLIFERATION_AND_ABUNDANCE = 4.4;
    private static final double BLOCKS_A_VEIN = 1.8;
    private static final double DEEPSLATE_CLIP = 0.60 / 0.985;
    private static final int DEEP_VEINS_LAYERS = 30;
    private static final int GUIDE_VEINS = 12;
    private static final int GUIDE_SIZE = 24;

    @Test
    void theArithmeticFollowsTheEncoding() {
        long[] bits = new long[1];
        bits[0] = 1L;
        VeinExtent a = new VeinExtent(0, "minecraft:iron_ore", Band.STONE, 0, 10, 0, 4, 4, 4, bits, false);
        VeinExtent b = new VeinExtent(1, "minecraft:iron_ore", Band.STONE, 4, 10, 0, 1, 1, 1, bits, false);
        // Two headers, 65 bits concatenated into two longs, and one palette id of 18 bytes.
        assertEquals(2 * 8 + 2 * 8 + 18, VeinIndexSize.bytes(List.of(a, b)));
    }

    @Test
    void theHeaviestOneDotZeroStackFitsTheComparator() {
        Map<Band, OrePicker> pickers = OrePickers.forVariant(EXPANDED, VanillaOres.candidates());
        List<VeinRequest> stack = heaviestStack();
        long max = 0;
        long maxUngrown = 0;
        double sum = 0;
        int veins = 0;
        int hardStops = 0;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                VeinPlacer.Placement placement = VeinPlacer.place(stack, EXPANDED, pickers, ChunkSeed.of(TEAM, 0, x, z, GenSalts.BASELINE_ORE));
                long grown = grownBytes(placement.veins());
                max = Math.max(max, grown);
                maxUngrown = Math.max(maxUngrown, VeinIndexSize.bytes(placement.veins()));
                sum += grown;
                veins += placement.veins().size();
                hardStops += placement.hardStopped() ? 1 : 0;
            }
        }
        System.out.printf(Locale.ROOT, "Vein index, heaviest 1.0 stack, 81 chunks: mean %.0f bytes, max %d (%.1f%% of %d); "
                        + "max without Stone Reduction's growth %d; %.0f veins a chunk; hard stops %d%n",
                sum / 81, max, 100.0 * max / VeinIndexSize.COMPARATOR_BYTES, VeinIndexSize.COMPARATOR_BYTES, maxUngrown, veins / 81.0, hardStops);
        printInformational();
        assertTrue(max <= VeinIndexSize.COMPARATOR_BYTES, "max " + max + " bytes over the comparator; back to the Manager before E4-S3");
    }

    private static List<VeinRequest> heaviestStack() {
        OreBaseline base = OreBaseline.BASE;
        List<VeinRequest> requests = new ArrayList<>();
        BandBounds stone = EXPANDED.band(Band.STONE).orElseThrow();
        BandBounds deepslate = EXPANDED.band(Band.DEEPSLATE).orElseThrow();
        for (Rarity rarity : Rarity.values()) {
            double count = base.veins(Band.STONE, rarity).perChunk() * PROLIFERATION_AND_ABUNDANCE * base.edgeFactor(Band.STONE, rarity, EXPANDED);
            requests.add(new VeinRequest(Band.STONE, rarity, count, scaledSizes(base.veins(Band.STONE, rarity), BLOCKS_A_VEIN), stone.bottomY(), stone.topY(), false));
        }
        requests.add(new VeinRequest(Band.STONE, Rarity.COMMON, GUIDE_VEINS, new double[] {GUIDE_SIZE}, stone.bottomY(), stone.topY(), true));
        for (Rarity rarity : Rarity.values()) {
            double count = base.veins(Band.DEEPSLATE, rarity).perChunk() * PROLIFERATION_AND_ABUNDANCE * DEEPSLATE_CLIP
                    * base.edgeFactor(Band.DEEPSLATE, rarity, EXPANDED);
            double[] sizes = scaledSizes(base.veins(Band.DEEPSLATE, rarity), BLOCKS_A_VEIN);
            requests.add(new VeinRequest(Band.DEEPSLATE, rarity, count, sizes, deepslate.bottomY(), deepslate.topY(), false));
            requests.add(new VeinRequest(Band.DEEPSLATE, rarity, count * DEEP_VEINS_LAYERS / deepslate.layers(), sizes,
                    deepslate.bottomY(), deepslate.bottomY() + DEEP_VEINS_LAYERS - 1, false));
        }
        return requests;
    }

    /** Not a gate: both bands at the 60% ceiling, at baseline sizes and at Abundance's -20% alone (BAL-E4-1, ARCH-E4-3). */
    private static void printInformational() {
        Map<Band, OrePicker> pickers = OrePickers.forVariant(EXPANDED, VanillaOres.candidates());
        for (double blocks : new double[] {1.0, 0.8}) {
            List<VeinRequest> requests = new ArrayList<>();
            for (Band band : Band.values()) {
                BandBounds bounds = EXPANDED.band(band).orElseThrow();
                double factor = 0.60 / (OreBaseline.BASE.density(band, EXPANDED) * blocks);
                for (Rarity rarity : Rarity.values()) {
                    OreBaseline.Veins veins = OreBaseline.BASE.veins(band, rarity);
                    requests.add(new VeinRequest(band, rarity, veins.perChunk() * factor * OreBaseline.BASE.edgeFactor(band, rarity, EXPANDED),
                            scaledSizes(veins, blocks), bounds.bottomY(), bounds.topY(), false));
                }
            }
            long max = 0;
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    max = Math.max(max, VeinIndexSize.bytes(VeinPlacer.place(requests, EXPANDED, pickers, ChunkSeed.of(TEAM, 1, x, z, GenSalts.BASELINE_ORE)).veins()));
                }
            }
            System.out.printf(Locale.ROOT, "Vein index, both bands at 60%%, blocks a vein x%.1f, 9 chunks: max %d (%.1f%%)%n",
                    blocks, max, 100.0 * max / VeinIndexSize.COMPARATOR_BYTES);
        }
    }

    private static double[] scaledSizes(OreBaseline.Veins veins, double blocks) {
        double[] sizes = new double[veins.maxSize() - veins.minSize() + 1];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = blocks == 1.0 ? veins.minSize() + i : VeinShape.sizeForMeanBlocks(blocks * VeinShape.meanBlocks(veins.minSize() + i));
        }
        return sizes;
    }

    /** Stone Reduction's upper bound: every box one layer larger on every side (BAL-E4-1). */
    private static long grownBytes(List<VeinExtent> veins) {
        long bits = 0;
        Set<String> palette = new LinkedHashSet<>();
        for (VeinExtent vein : veins) {
            bits += (long) (vein.sizeX() + 2) * (vein.sizeY() + 2) * (vein.sizeZ() + 2);
            palette.add(vein.oreGroup());
        }
        return VeinIndexSize.bytes(veins.size(), bits, palette);
    }
}
