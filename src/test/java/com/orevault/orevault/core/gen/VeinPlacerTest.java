package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.orevault.orevault.core.ore.HeightProfile;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pure placer against §3.1 and S12 over 81 chunks (ADR 0009 Tests): each band's density within 5% relative, the
 * rarity shares by placed blocks within 2 points, and every 16-layer slab within 10% of its own expected density and 4
 * points of its expected shares, the figures {@link DensityTally} computes from the profiles (ADR 0009, amended
 * 2026-11-21: ore density follows height). The density command (E4-S4) checks the same against blocks the generator
 * really wrote.
 */
class VeinPlacerTest {
    private static final UUID TEAM = UUID.fromString("6f1c2b8e-3d4a-4e5f-9a0b-1c2d3e4f5a6b");
    private static final int CHUNKS = 9;

    @Test
    void theBaseTypeHoldsTheBaseline() {
        Tally tally = run(VaultVariant.BASE, VanillaOres.candidates(), 0);
        tally.assertBand(Band.STONE, OreBaseline.BASE.density(Band.STONE, VaultVariant.BASE));
        tally.assertShares(Band.STONE);
        tally.assertSlabs(Band.STONE);
        tally.assertSlabShares(Band.STONE);
    }

    @Test
    void theExpandedTypeHoldsTheBaseline() {
        Tally tally = run(VaultVariant.EXPANDED, VanillaOres.candidates(), 0);
        tally.assertBand(Band.STONE, OreBaseline.BASE.density(Band.STONE, VaultVariant.EXPANDED));
        tally.assertBand(Band.DEEPSLATE, OreBaseline.BASE.density(Band.DEEPSLATE, VaultVariant.EXPANDED));
        tally.assertShares(Band.STONE);
        tally.assertShares(Band.DEEPSLATE);
        tally.assertSlabs(Band.STONE);
        tally.assertSlabs(Band.DEEPSLATE);
        tally.assertSlabShares(Band.STONE);
        tally.assertSlabShares(Band.DEEPSLATE);
    }

    /**
     * ADR 0009 "Height", BAL-15: the band's outermost layers hold what the profiles put there, after the cut and the edge
     * factor. Without the overhang the two layers at each edge come out at about 64% of it, which the 16-layer slab test
     * averages away (seen on Nick's PC 2026-11-20). Since each ore sits at its own height the expected figure is per layer
     * ({@link ClipFactors#byLayer}, less the overlap), not the band's density: deepslate's top layers hold about 16%.
     */
    @Test
    void theEdgeLayersHoldTheBandDensity() {
        for (VaultVariant variant : VaultVariant.values()) {
            Tally tally = run(variant, VanillaOres.candidates(), 0);
            for (Band band : Band.values()) {
                variant.band(band).ifPresent(bounds -> {
                    tally.assertLayers(band, bounds.bottomY(), bounds.bottomY() + 1);
                    tally.assertLayers(band, bounds.topY() - 1, bounds.topY());
                });
            }
        }
    }

    /** #234, ruling 5: no rare ore in the pack, and the density and every slab hold; the mix is not checked (BAL round 2). */
    @Test
    void densityHoldsWithNoRareOre() {
        Tally tally = run(VaultVariant.EXPANDED, VanillaOres.withoutRare(), 0);
        tally.assertBand(Band.STONE, OreBaseline.BASE.density(Band.STONE, VaultVariant.EXPANDED));
        tally.assertBand(Band.DEEPSLATE, OreBaseline.BASE.density(Band.DEEPSLATE, VaultVariant.EXPANDED));
        tally.assertSlabs(Band.STONE);
        tally.assertSlabs(Band.DEEPSLATE);
        assertEquals(0, tally.byRarity.get(Band.STONE).get(Rarity.RARE));
    }

    /** No position is held twice, none is outside its band, and every box stays in the chunk (S20, ADR 0004). */
    @Test
    void extentsAreDisjointAndInsideTheirBands() {
        for (int chunk = 0; chunk < 20; chunk++) {
            VeinPlacer.Placement placement = place(VaultVariant.EXPANDED, VanillaOres.candidates(), chunk, chunk);
            Set<Long> held = new HashSet<>();
            for (VeinExtent vein : placement.veins()) {
                BandBounds bounds = VaultVariant.EXPANDED.band(vein.band()).orElseThrow();
                assertTrue(vein.minY() >= bounds.bottomY() && vein.minY() + vein.sizeY() - 1 <= bounds.topY(), vein.toString());
                assertTrue(vein.minX() + vein.sizeX() <= 16 && vein.minZ() + vein.sizeZ() <= 16, vein.toString());
                assertTrue(vein.blocks() > 0);
                forEachPosition(vein, (x, y, z) -> assertTrue(held.add(((long) y << 8) | (z << 4) | x), "twice at " + x + "," + y + "," + z));
            }
            assertFalse(placement.hardStopped());
        }
    }

    /**
     * §3.1 "Where each ore sits": a vein's middle is drawn from its ore's profile, so none of its blocks sits farther than
     * {@link VeinShape#KERNEL_REACH} from a layer where that ore has mass. Coal, which has none below Y=0, is never in
     * deepslate, and diamond, which has none above Y=16, never above Y=20 in stone.
     */
    @Test
    void noBlockLandsFarFromItsOresProfile() {
        Map<String, HeightProfile> profiles = new HashMap<>();
        for (OrePicker.Candidate candidate : VanillaOres.candidates()) {
            profiles.put(candidate.groupId(), candidate.profile());
        }
        for (VaultVariant variant : VaultVariant.values()) {
            for (int chunk = 0; chunk < 20; chunk++) {
                for (VeinExtent vein : place(variant, VanillaOres.candidates(), chunk, 2 * chunk).veins()) {
                    HeightProfile profile = profiles.get(vein.oreGroup());
                    if (vein.band() == Band.DEEPSLATE) {
                        assertFalse(vein.oreGroup().endsWith(":coal_ore"), vein.toString());
                    }
                    forEachPosition(vein, (x, y, z) -> {
                        boolean near = false;
                        for (int dy = -VeinShape.KERNEL_REACH; dy <= VeinShape.KERNEL_REACH; dy++) {
                            near |= profile.massAt(y + dy) > 0;
                        }
                        assertTrue(near, vein.oreGroup() + " at Y=" + y + ", no mass within reach");
                        if (vein.oreGroup().endsWith(":diamond_ore")) {
                            assertTrue(y <= 16 + VeinShape.KERNEL_REACH, "diamond at Y=" + y);
                        }
                    });
                }
            }
        }
    }

    /** ARCH-E4-4: every extent fits ADR 0004's header fields. */
    @Test
    void everyExtentFitsTheHeader() {
        for (int chunk = 0; chunk < 40; chunk++) {
            for (VeinExtent vein : place(VaultVariant.EXPANDED, VanillaOres.candidates(), chunk, -chunk).veins()) {
                assertTrue(vein.sizeX() >= 1 && vein.sizeX() <= 16 && vein.sizeZ() >= 1 && vein.sizeZ() <= 16, vein.toString());
                assertTrue(vein.sizeY() >= 1 && vein.sizeY() <= 32, vein.toString());
                assertTrue(vein.minY() - VaultVariant.EXPANDED.minY() < 512, vein.toString());
                assertTrue(vein.ordinal() < 1 << 16, vein.toString());
            }
        }
    }

    @Test
    void theSameSeedPlacesTheSameVeins() {
        assertEquals(place(VaultVariant.EXPANDED, VanillaOres.candidates(), 3, 4), place(VaultVariant.EXPANDED, VanillaOres.candidates(), 3, 4));
    }

    /**
     * ARCH-E4-7: the draw sequence is pinned. A change to the order of draws, the salt or the shape changes every Vault
     * chunk ever generated afterwards, so it fails here first. Printed on a mismatch, to pin a deliberate change.
     */
    @Test
    void theDrawSequenceIsPinned() {
        int fingerprint = place(VaultVariant.EXPANDED, VanillaOres.candidates(), 0, 0).veins().hashCode();
        if (fingerprint != PINNED_FINGERPRINT) {
            fail("Placement fingerprint " + fingerprint + ", pinned " + PINNED_FINGERPRINT
                    + ". If the change to the draw sequence is deliberate, pin the new value.");
        }
    }

    /** Pinned 2026-11-23 from the first run on Nick's PC (#425). Was 1853940645, pinned 2026-11-20. */
    private static final int PINNED_FINGERPRINT = 1863178634;

    /** ADR 0009: the fractional roll's mean over 10,000 seeds is c within three standard errors. */
    @Test
    void theFractionalRollMeetsItsMean() {
        double mean = 54.67;
        double sum = 0;
        for (int seed = 0; seed < 10_000; seed++) {
            sum += VeinPlacer.rollCount(mean, GenRandom.of(seed));
        }
        double fraction = mean - Math.floor(mean);
        double standardError = Math.sqrt(fraction * (1 - fraction) / 10_000);
        assertEquals(mean, sum / 10_000, 3 * standardError);
    }

    /**
     * The safety net: a request past 60% times the headroom is refused there, the refusal names its band and rarity, and
     * no band is ever placed over §3.1's 60% (S3-RV-1, S3-RV-4). The request is past the stop by far, so the requested
     * blocks land within one vein of it.
     */
    @Test
    void theHardStopHoldsTheCeiling() {
        BandBounds stone = VaultVariant.BASE.band(Band.STONE).orElseThrow();
        List<VeinRequest> flood = List.of(new VeinRequest(Band.STONE, Rarity.COMMON, 20_000, new double[] {12}, stone.bottomY(), stone.topY(), false));
        VeinPlacer.Placement placement = VeinPlacer.place(flood, VaultVariant.BASE, pickers(VaultVariant.BASE, VanillaOres.candidates()), 1L);
        assertTrue(placement.hardStopped());
        assertEquals(new VeinPlacer.HardStop(Band.STONE, Rarity.COMMON), placement.firstStop());
        double stop = ClipFactors.CEILING * ClipFactors.HARD_STOP_HEADROOM;
        double requested = placement.requestedDensity(Band.STONE, VaultVariant.BASE);
        assertTrue(requested <= stop && requested > stop - 0.001, "requested " + requested + " against the stop at " + stop);
        long placed = placement.veins().stream().mapToLong(VeinExtent::blocks).sum();
        assertTrue(placed <= ClipFactors.CEILING * 256 * stone.layers(), "placed " + placed);
    }

    private static Tally run(VaultVariant variant, List<OrePicker.Candidate> ores, int offset) {
        Tally tally = new Tally(variant, pickers(variant, ores));
        for (int x = -CHUNKS / 2; x <= CHUNKS / 2; x++) {
            for (int z = -CHUNKS / 2; z <= CHUNKS / 2; z++) {
                tally.add(place(variant, ores, x + offset, z));
            }
        }
        return tally;
    }

    private static VeinPlacer.Placement place(VaultVariant variant, List<OrePicker.Candidate> ores, int chunkX, int chunkZ) {
        long seed = ChunkSeed.of(TEAM, 0, chunkX, chunkZ, GenSalts.BASELINE_ORE);
        return VeinPlacer.placeOre(OreBaseline.BASE, variant, pickers(variant, ores), ClipFactors.NONE, seed);
    }

    private static Map<Band, OrePicker> pickers(VaultVariant variant, List<OrePicker.Candidate> ores) {
        return OrePickers.forVariant(variant, ores);
    }

    interface PositionVisitor {
        void visit(int x, int y, int z);
    }

    static void forEachPosition(VeinExtent vein, PositionVisitor visitor) {
        for (int y = 0; y < vein.sizeY(); y++) {
            for (int z = 0; z < vein.sizeZ(); z++) {
                for (int x = 0; x < vein.sizeX(); x++) {
                    if (vein.isSet(x, y, z)) {
                        visitor.visit(vein.minX() + x, vein.minY() + y, vein.minZ() + z);
                    }
                }
            }
        }
    }

    /** Placed ore by band, rarity and slab over the chunks counted. */
    private static final class Tally {
        final VaultVariant variant;
        final Map<Band, Map<Rarity, Long>> byRarity = new EnumMap<>(Band.class);
        final Map<Band, Map<Integer, Long>> byLayer = new EnumMap<>(Band.class);
        final Map<Band, OrePicker> pickers;
        final DensityTally density;
        int chunks;

        Tally(VaultVariant variant, Map<Band, OrePicker> pickers) {
            this.variant = variant;
            this.pickers = pickers;
            this.density = new DensityTally(OreBaseline.BASE, variant, pickers, CHUNKS * CHUNKS);
            for (Band band : Band.values()) {
                Map<Rarity, Long> zero = new EnumMap<>(Rarity.class);
                for (Rarity rarity : Rarity.values()) {
                    zero.put(rarity, 0L);
                }
                byRarity.put(band, zero);
            }
        }

        void add(VeinPlacer.Placement placement) {
            chunks++;
            for (VeinExtent vein : placement.veins()) {
                Rarity rarity = VanillaOres.rarityOf(vein.oreGroup());
                byRarity.get(vein.band()).merge(rarity, (long) vein.blocks(), Long::sum);
                Map<Integer, Long> layers = byLayer.computeIfAbsent(vein.band(), b -> new HashMap<>());
                forEachPosition(vein, (x, y, z) -> layers.merge(y, 1L, Long::sum));
                for (int y = vein.minY(); y < vein.minY() + vein.sizeY(); y++) {
                    long inLayer = 0;
                    for (int z = 0; z < vein.sizeZ(); z++) {
                        for (int x = 0; x < vein.sizeX(); x++) {
                            inLayer += vein.isSet(x, y - vein.minY(), z) ? 1 : 0;
                        }
                    }
                    if (inLayer > 0) {
                        density.add(vein.band(), y, rarity, vein.oreGroup(), inLayer);
                    }
                }
            }
        }

        long total(Band band) {
            return byRarity.get(band).values().stream().mapToLong(Long::longValue).sum();
        }

        void assertBand(Band band, double expected) {
            double measured = total(band) / (256.0 * chunks * variant.band(band).orElseThrow().layers());
            assertTrue(Math.abs(measured - expected) <= 0.05 * expected,
                    band + " density " + measured + " against " + expected + " (S12: 5% relative)");
        }

        /** By the ore actually picked, so a rare vein that took uncommon ore counts as uncommon. */
        void assertShares(Band band) {
            for (Rarity rarity : Rarity.values()) {
                double share = byRarity.get(band).get(rarity) / (double) total(band);
                double expected = OreBaseline.BASE.share(band, rarity);
                assertTrue(Math.abs(share - expected) <= 0.02, band + " " + rarity + " share " + share + " against " + expected);
            }
        }

        /**
         * Layers {@code fromY..toY} against what the profiles put there less the overlap, within 25%: the missing overhang
         * is about -36%.
         */
        void assertLayers(Band band, int fromY, int toY) {
            BandBounds bounds = variant.band(band).orElseThrow();
            double[] expectedByLayer = ClipFactors.byLayer(variant, band,
                    ClipFactors.nominalRequests(OreBaseline.BASE, variant, GenerationTiers.NONE), pickers);
            double expected = 0;
            for (int y = fromY; y <= toY; y++) {
                expected += expectedByLayer[y - bounds.bottomY()] * (1 - DensityTally.overlap(band));
            }
            expected /= toY - fromY + 1;
            double measured = byLayer.getOrDefault(band, Map.of()).entrySet().stream()
                    .filter(e -> e.getKey() >= fromY && e.getKey() <= toY).mapToLong(Map.Entry::getValue).sum()
                    / (256.0 * chunks * (toY - fromY + 1));
            assertTrue(Math.abs(measured - expected) <= 0.25 * expected,
                    variant.id() + " " + band + " Y" + fromY + ".." + toY + " at " + measured + " against " + expected);
        }

        /** Every slab's density against its own expected density, within 10% relative (DensityTally). */
        void assertSlabs(Band band) {
            assertEquals(CHUNKS * CHUNKS, chunks);
            for (int slab = 0; slab < density.slabCount(band); slab++) {
                assertTrue(density.slabPasses(band, slab), band + " slab " + density.slabBounds(band, slab) + " at "
                        + density.slabDensity(band, slab) + " against " + density.expectedSlabDensity(band, slab) + " (10% relative)");
            }
        }

        /** Every slab's rarity shares against its expected shares, within 4 points (DensityTally). */
        void assertSlabShares(Band band) {
            for (int slab = 0; slab < density.slabCount(band); slab++) {
                for (Rarity rarity : Rarity.values()) {
                    assertTrue(density.slabSharePasses(band, slab, rarity), band + " slab " + density.slabBounds(band, slab)
                            + " " + rarity + " share " + density.slabShare(band, slab, rarity) + " against "
                            + density.expectedSlabShare(band, slab, rarity));
                }
            }
        }
    }

}
