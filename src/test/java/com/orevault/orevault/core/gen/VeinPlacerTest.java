package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pure placer against §3.1 and S12 over 81 chunks (ADR 0009 Tests): each band's density within 5% relative, the
 * rarity shares by placed blocks within 2 points, every 16-layer slab within 10% of its band's density. The density
 * command (E4-S4) checks the same against blocks the generator really wrote.
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

    /** Pinned from the first run on Nick's PC. */
    private static final int PINNED_FINGERPRINT = 0;

    /** ADR 0009: the fractional roll's mean over 10,000 seeds is c within three standard errors. */
    @Test
    void theFractionalRollMeetsItsMean() {
        double mean = 141.37;
        double sum = 0;
        for (int seed = 0; seed < 10_000; seed++) {
            sum += VeinPlacer.rollCount(mean, GenRandom.of(seed));
        }
        double fraction = mean - Math.floor(mean);
        double standardError = Math.sqrt(fraction * (1 - fraction) / 10_000);
        assertEquals(mean, sum / 10_000, 3 * standardError);
    }

    /** The safety net: a request past 60% is refused at the ceiling, and no band is ever placed over it. */
    @Test
    void theHardStopHoldsTheCeiling() {
        BandBounds stone = VaultVariant.BASE.band(Band.STONE).orElseThrow();
        List<VeinRequest> flood = List.of(new VeinRequest(Band.STONE, Rarity.COMMON, 20_000, new double[] {12}, stone.bottomY(), stone.topY(), false));
        VeinPlacer.Placement placement = VeinPlacer.place(flood, VaultVariant.BASE, pickers(VaultVariant.BASE, VanillaOres.candidates()), 1L);
        assertTrue(placement.hardStopped());
        long placed = placement.veins().stream().mapToLong(VeinExtent::blocks).sum();
        assertTrue(placed <= 0.60 * 256 * stone.layers(), "placed " + placed);
    }

    private static Tally run(VaultVariant variant, List<OrePicker.Candidate> ores, int offset) {
        Tally tally = new Tally(variant);
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
        final Map<Band, long[]> bySlab = new EnumMap<>(Band.class);
        int chunks;

        Tally(VaultVariant variant) {
            this.variant = variant;
            for (Band band : Band.values()) {
                Map<Rarity, Long> zero = new EnumMap<>(Rarity.class);
                for (Rarity rarity : Rarity.values()) {
                    zero.put(rarity, 0L);
                }
                byRarity.put(band, zero);
                variant.band(band).ifPresent(b -> bySlab.put(band, new long[slabCount(b)]));
            }
        }

        void add(VeinPlacer.Placement placement) {
            chunks++;
            for (VeinExtent vein : placement.veins()) {
                Rarity rarity = VanillaOres.rarityOf(vein.oreGroup());
                byRarity.get(vein.band()).merge(rarity, (long) vein.blocks(), Long::sum);
                BandBounds bounds = variant.band(vein.band()).orElseThrow();
                long[] slabs = bySlab.get(vein.band());
                forEachPosition(vein, (x, y, z) -> slabs[slabOf(y, bounds)]++);
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

        void assertSlabs(Band band) {
            BandBounds bounds = variant.band(band).orElseThrow();
            double density = total(band) / (256.0 * chunks * bounds.layers());
            long[] slabs = bySlab.get(band);
            for (int slab = 0; slab < slabs.length; slab++) {
                int layers = slab == slabs.length - 1 ? bounds.layers() - 16 * slab : 16;
                double measured = slabs[slab] / (256.0 * chunks * layers);
                assertTrue(Math.abs(measured - density) <= 0.10 * density,
                        band + " slab " + slab + " at " + measured + " against the band's " + density + " (10% relative)");
            }
        }

        static int slabCount(BandBounds bounds) {
            return Math.max(1, bounds.layers() / 16);
        }

        static int slabOf(int y, BandBounds bounds) {
            return Math.min(slabCount(bounds) - 1, (y - bounds.bottomY()) / 16);
        }
    }

}
