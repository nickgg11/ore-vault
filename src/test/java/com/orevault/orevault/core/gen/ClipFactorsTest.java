package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The 40% floor's mechanism (§3.1, ADR 0009 "Counts per rarity, and the floor clip"). No generation node exists before
 * E9, so the full-stack request cannot be computed yet; these run the mechanism on synthetic requests, the deepslate band's
 * densest layer asking for 90%, 100% and 132% ore (QA-E4-2, triage "Floor test"). Since each ore sits at its own height
 * (ADR 0009, amended 2026-11-21), {@code densest} is per layer, from the same profiles the placer draws from. The full-stack test moves to the T9 ticket that pins
 * Vein Shaping and Stone Reduction.
 */
class ClipFactorsTest {
    private static final UUID TEAM = UUID.fromString("0e4c1a9b-77d2-4f10-8a3e-5b6c7d8e9f01");
    private static final int CHUNKS = 9;
    private static final Map<Band, OrePicker> PICKERS = OrePickers.forVariant(VaultVariant.EXPANDED, VanillaOres.candidates());

    @Test
    void withNoGenerationNodeBothFactorsAreOne() {
        for (VaultVariant variant : VaultVariant.values()) {
            Map<Band, OrePicker> pickers = OrePickers.forVariant(variant, VanillaOres.candidates());
            assertEquals(ClipFactors.NONE, ClipFactors.of(OreBaseline.BASE, variant, pickers, GenerationTiers.NONE), variant.id());
            assertEquals(ClipFactors.NONE, ClipFactors.of(OreBaseline.BASE, variant, Map.of(), GenerationTiers.NONE), variant.id());
        }
    }

    /**
     * The nominal request is the closed form spread by height (ADR 0009, amended 2026-11-21): its layers sum to §3.1's 3%
     * and 6% band density, and it is densest where the ores peak, not flat. The densest layers are
     * {@code docs/research/vanilla-ore/heights.txt}'s: 5.19% at Y=2 in the base stone band, 16.28% at Y=-2 in deepslate,
     * both far from the cap.
     */
    @Test
    void theBaselineAsksForItsOwnDensity() {
        for (VaultVariant variant : VaultVariant.values()) {
            Map<Band, OrePicker> pickers = OrePickers.forVariant(variant, VanillaOres.candidates());
            List<VeinRequest> requests = ClipFactors.nominalRequests(OreBaseline.BASE, variant, GenerationTiers.NONE);
            for (Band band : Band.values()) {
                if (variant.band(band).isEmpty()) {
                    assertEquals(0, ClipFactors.densest(variant, band, requests, pickers));
                    assertEquals(0, ClipFactors.byLayer(variant, band, requests, pickers).length);
                    continue;
                }
                double[] byLayer = ClipFactors.byLayer(variant, band, requests, pickers);
                double densest = ClipFactors.densest(variant, band, requests, pickers);
                String where = variant.id() + " " + band;
                assertEquals(OreBaseline.BASE.density(band, variant), LayerSpread.sum(byLayer) / byLayer.length, 1e-12, where);
                assertEquals(Arrays.stream(byLayer).max().orElseThrow(), densest, 0, where);
                assertTrue(densest > OreBaseline.BASE.density(band, variant) && densest < ClipFactors.CEILING, where);
            }
        }
        List<VeinRequest> base = ClipFactors.nominalRequests(OreBaseline.BASE, VaultVariant.BASE, GenerationTiers.NONE);
        Map<Band, OrePicker> basePickers = OrePickers.forVariant(VaultVariant.BASE, VanillaOres.candidates());
        double[] stone = ClipFactors.byLayer(VaultVariant.BASE, Band.STONE, base, basePickers);
        assertEquals(0.051946, ClipFactors.densest(VaultVariant.BASE, Band.STONE, base, basePickers), 0.0002);
        assertEquals(2 - 1, indexOfMax(stone), "the base stone band is densest at Y=2");
        List<VeinRequest> expanded = ClipFactors.nominalRequests(OreBaseline.BASE, VaultVariant.EXPANDED, GenerationTiers.NONE);
        double[] deepslate = ClipFactors.byLayer(VaultVariant.EXPANDED, Band.DEEPSLATE, expanded, PICKERS);
        assertEquals(0.162814, ClipFactors.densest(VaultVariant.EXPANDED, Band.DEEPSLATE, expanded, PICKERS), 0.0005);
        assertEquals(-2 - (-63), indexOfMax(deepslate), "deepslate is densest at Y=-2");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.9, 1.0, 1.32})
    void aBandAskingPastTheCapIsScaledToIt(double asked) {
        OreBaseline greedy = deepslateAsking(asked);
        ClipFactors clip = ClipFactors.of(greedy, VaultVariant.EXPANDED, PICKERS, GenerationTiers.NONE);
        assertEquals(ClipFactors.CEILING / asked, clip.deepslate(), 1e-9);
        assertEquals(1.0, clip.stone());
    }

    /** One factor for every rarity in the band, so the clipped requests keep the mix exactly (§3.1). */
    @ParameterizedTest
    @ValueSource(doubles = {0.9, 1.0, 1.32})
    void theClipKeepsTheMix(double asked) {
        OreBaseline greedy = deepslateAsking(asked);
        ClipFactors clip = ClipFactors.of(greedy, VaultVariant.EXPANDED, PICKERS, GenerationTiers.NONE);
        Map<Rarity, Double> unclipped = counts(VeinPlacer.baselineRequests(greedy, VaultVariant.EXPANDED, PICKERS, ClipFactors.NONE));
        Map<Rarity, Double> clipped = counts(VeinPlacer.baselineRequests(greedy, VaultVariant.EXPANDED, PICKERS, clip));
        for (Rarity rarity : Rarity.values()) {
            assertEquals(unclipped.get(rarity) * clip.deepslate(), clipped.get(rarity), 1e-9, rarity.toString());
        }
        // The clipped closed form, edge factor divided out, puts its densest layer on the cap.
        BandBounds band = VaultVariant.EXPANDED.band(Band.DEEPSLATE).orElseThrow();
        double[] byLayer = new double[band.layers()];
        for (VeinRequest request : VeinPlacer.baselineRequests(greedy, VaultVariant.EXPANDED, PICKERS, clip)) {
            if (request.band() == Band.DEEPSLATE) {
                double edge = greedy.edgeFactor(Band.DEEPSLATE, request.rarity(), PICKERS.get(Band.DEEPSLATE));
                double[] placed = LayerSpread.placedByLayer(request, PICKERS.get(Band.DEEPSLATE), band);
                for (int i = 0; i < byLayer.length; i++) {
                    byLayer[i] += placed[i] / edge / 256.0;
                }
            }
        }
        assertEquals(ClipFactors.CEILING, Arrays.stream(byLayer).max().orElseThrow(), 1e-9,
                "the clipped closed form's densest layer sits on the cap");
    }

    /**
     * The placer over 81 chunks with the clip applied: every deepslate slab at or under 60% within S12's 5% relative, and
     * the hard stop never fired (QA-E4-2, S3-QA-1). With the headroom (S3-RV-1) a correct clip never reaches the stop. The
     * slab assertion alone cannot catch a broken clip, since overlap keeps placed ore near 45% whatever is requested;
     * {@link #withoutTheClipTheStopFires} and the request-level tests are what can. The largest per-chunk request over the
     * ceiling is printed for S3-BAL-1: the headroom must stay at least five times its spread above 1.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.9, 1.0, 1.32})
    void everySlabStaysUnderTheCap(double asked) {
        OreBaseline greedy = deepslateAsking(asked);
        ClipFactors clip = ClipFactors.of(greedy, VaultVariant.EXPANDED, PICKERS, GenerationTiers.NONE);
        Map<Band, OrePicker> pickers = PICKERS;
        BandBounds deepslate = VaultVariant.EXPANDED.band(Band.DEEPSLATE).orElseThrow();
        int slabCount = deepslate.layers() / 16;
        long[] slabs = new long[slabCount];
        int stopped = 0;
        int chunks = 0;
        double largest = 0;
        double sum = 0;
        double squares = 0;
        for (int x = -CHUNKS / 2; x <= CHUNKS / 2; x++) {
            for (int z = -CHUNKS / 2; z <= CHUNKS / 2; z++) {
                VeinPlacer.Placement placement = VeinPlacer.placeOre(greedy, VaultVariant.EXPANDED, pickers, clip,
                        ChunkSeed.of(TEAM, 0, x, z, GenSalts.BASELINE_ORE));
                chunks++;
                stopped += placement.hardStopped() ? 1 : 0;
                double ratio = placement.requestedDensity(Band.DEEPSLATE, VaultVariant.EXPANDED) / ClipFactors.CEILING;
                largest = Math.max(largest, ratio);
                sum += ratio;
                squares += ratio * ratio;
                for (VeinExtent vein : placement.veins()) {
                    if (vein.band() == Band.DEEPSLATE) {
                        VeinPlacerTest.forEachPosition(vein, (px, py, pz) ->
                                slabs[Math.min(slabCount - 1, (py - deepslate.bottomY()) / 16)]++);
                    }
                }
            }
        }
        double mean = sum / chunks;
        double sigma = Math.sqrt(Math.max(0, squares / chunks - mean * mean));
        System.out.println("Deepslate asking " + asked + ": clip " + clip.deepslate() + ", request over the ceiling mean " + mean
                + ", sigma " + sigma + ", largest " + largest + "; hard stop in " + stopped + " of " + chunks + " chunks");
        for (int slab = 0; slab < slabCount; slab++) {
            int layers = slab == slabCount - 1 ? deepslate.layers() - 16 * slab : 16;
            double measured = slabs[slab] / (256.0 * chunks * layers);
            System.out.println("  slab " + slab + ": " + measured);
            assertTrue(measured <= ClipFactors.CEILING * 1.05, "slab " + slab + " at " + measured);
        }
        assertEquals(0, stopped, "the hard stop fired under a correct clip");
        assertTrue(largest < ClipFactors.HARD_STOP_HEADROOM, "largest request ratio " + largest);
    }

    /**
     * S3-QA-1 (b): the same chunks with no clip, the band's average asking 90% or 132% against a stop at 66%, and every
     * chunk stops. The stop counts the band's total, so this scales by the band's average, not its densest layer: a band
     * whose densest layer asks 90% averages only about 34% and never reaches the stop.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.9, 1.32})
    void withoutTheClipTheStopFires(double asked) {
        OreBaseline greedy = deepslateAveraging(asked);
        Map<Band, OrePicker> pickers = PICKERS;
        int stopped = 0;
        for (int x = -CHUNKS / 2; x <= CHUNKS / 2; x++) {
            for (int z = -CHUNKS / 2; z <= CHUNKS / 2; z++) {
                VeinPlacer.Placement placement = VeinPlacer.placeOre(greedy, VaultVariant.EXPANDED, pickers, ClipFactors.NONE,
                        ChunkSeed.of(TEAM, 0, x, z, GenSalts.BASELINE_ORE));
                stopped += placement.hardStopped() ? 1 : 0;
                assertTrue(placement.requestedDensity(Band.DEEPSLATE, VaultVariant.EXPANDED)
                        <= ClipFactors.CEILING * ClipFactors.HARD_STOP_HEADROOM);
            }
        }
        assertEquals(CHUNKS * CHUNKS, stopped);
    }

    /**
     * {@code densest} is the densest layer, not the band's average (BAL-3): a request concentrated into the band's lowest
     * 30 layers on top of the band-wide veins sets the factor by those layers. Requests add layer by layer, and a region
     * request's blocks stay within {@link VeinShape#KERNEL_REACH} of its region.
     */
    @Test
    void aConcentratedRegionSetsTheFactor() {
        BandBounds deepslate = VaultVariant.EXPANDED.band(Band.DEEPSLATE).orElseThrow();
        List<VeinRequest> nominal = ClipFactors.nominalRequests(OreBaseline.BASE, VaultVariant.EXPANDED, GenerationTiers.NONE);
        double perVein = VeinShape.meanBlocks(12);
        double extra = 0.70;
        VeinRequest region = new VeinRequest(Band.DEEPSLATE, Rarity.COMMON, extra * 256 * 30 / perVein, new double[] {12},
                deepslate.bottomY(), deepslate.bottomY() + 29, false);
        List<VeinRequest> requests = new ArrayList<>(nominal);
        requests.add(region);

        double[] alone = ClipFactors.byLayer(VaultVariant.EXPANDED, Band.DEEPSLATE, nominal, PICKERS);
        double[] added = ClipFactors.byLayer(VaultVariant.EXPANDED, Band.DEEPSLATE, List.of(region), PICKERS);
        double[] both = ClipFactors.byLayer(VaultVariant.EXPANDED, Band.DEEPSLATE, requests, PICKERS);
        for (int i = 0; i < both.length; i++) {
            assertEquals(alone[i] + added[i], both[i], 1e-12, "layer " + i);
            if (i > 29 + VeinShape.KERNEL_REACH) {
                assertEquals(0, added[i], "layer " + i + " is out of the region's reach");
            }
        }
        assertEquals(extra * 30, LayerSpread.sum(added), 1e-9, "the band holds the region's whole closed form");

        double densest = ClipFactors.densest(VaultVariant.EXPANDED, Band.DEEPSLATE, requests, PICKERS);
        assertTrue(densest > ClipFactors.CEILING, "densest " + densest);
        assertEquals(ClipFactors.CEILING / densest,
                ClipFactors.fromRequests(VaultVariant.EXPANDED, requests, PICKERS).deepslate(), 1e-12);
        double average = LayerSpread.sum(both) / deepslate.layers();
        assertTrue(average < ClipFactors.CEILING, "the band average, " + average + ", would not have clipped at all");
    }

    /** A fractional size counts as its two neighbours, as the placer draws it (BAL-E4-10). */
    @Test
    void aFractionalSizeIsBetweenItsNeighbours() {
        VeinRequest request = new VeinRequest(Band.STONE, Rarity.COMMON, 1, new double[] {9.25}, 1, 95, false);
        assertEquals(0.75 * VeinShape.meanBlocks(9) + 0.25 * VeinShape.meanBlocks(10), ClipFactors.meanBlocks(request), 1e-12);
    }

    @Test
    void anUnknownNodeChangesNothingYet() {
        GenerationTiers someNode = new GenerationTiers(Map.of("orevault:not_a_generation_node", 3));
        assertEquals(ClipFactors.NONE, ClipFactors.of(OreBaseline.BASE, VaultVariant.EXPANDED, PICKERS, someNode));
    }

    /** The baseline with every deepslate count scaled so the band's densest layer asks for {@code asked}. */
    static OreBaseline deepslateAsking(double asked) {
        double densest = ClipFactors.densest(VaultVariant.EXPANDED, Band.DEEPSLATE,
                ClipFactors.nominalRequests(OreBaseline.BASE, VaultVariant.EXPANDED, GenerationTiers.NONE), PICKERS);
        OreBaseline scaled = deepslateScaled(asked / densest);
        assertEquals(asked, ClipFactors.densest(VaultVariant.EXPANDED, Band.DEEPSLATE,
                ClipFactors.nominalRequests(scaled, VaultVariant.EXPANDED, GenerationTiers.NONE), PICKERS), 1e-9);
        return scaled;
    }

    /** The baseline with every deepslate count scaled so the band's average closed form asks for {@code asked}. */
    static OreBaseline deepslateAveraging(double asked) {
        OreBaseline scaled = deepslateScaled(asked / OreBaseline.BASE.density(Band.DEEPSLATE, VaultVariant.EXPANDED));
        assertEquals(asked, scaled.density(Band.DEEPSLATE, VaultVariant.EXPANDED), 1e-9);
        return scaled;
    }

    private static OreBaseline deepslateScaled(double scale) {
        Map<Rarity, OreBaseline.Veins> deep = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            OreBaseline.Veins veins = OreBaseline.BASE.veins(Band.DEEPSLATE, rarity);
            deep.put(rarity, new OreBaseline.Veins(veins.perChunk() * scale, veins.minSize(), veins.maxSize()));
        }
        return new OreBaseline(Map.of(Band.STONE, OreBaseline.BASE.ore().get(Band.STONE), Band.DEEPSLATE, deep),
                OreBaseline.BASE.gravel(), OreBaseline.BASE.clay());
    }

    private static int indexOfMax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static Map<Rarity, Double> counts(List<VeinRequest> requests) {
        Map<Rarity, Double> counts = new EnumMap<>(Rarity.class);
        for (VeinRequest request : requests) {
            if (request.band() == Band.DEEPSLATE) {
                counts.put(request.rarity(), request.perChunk());
            }
        }
        return counts;
    }
}
