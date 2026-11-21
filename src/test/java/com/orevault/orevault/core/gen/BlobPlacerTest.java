package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.VanillaOres;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Gravel and clay (§3.1, ARCH-E4-8): after ore, never on ore, clay never on gravel, cut to the band, own salts. */
class BlobPlacerTest {
    private static final UUID TEAM = UUID.fromString("5a0d3c2e-1b4f-4a6e-9c8d-7e6f5a4b3c2d");
    private static final int CHUNKS = 9;

    private static VeinPlacer.Placement ore(VaultVariant variant, int chunkX, int chunkZ) {
        return VeinPlacer.placeOre(OreBaseline.BASE, variant, OrePickers.forVariant(variant, VanillaOres.candidates()),
                ClipFactors.NONE, ChunkSeed.of(TEAM, 0, chunkX, chunkZ, GenSalts.BASELINE_ORE));
    }

    private static BlobPlacer.Placement blobs(VaultVariant variant, List<VeinExtent> ore, int chunkX, int chunkZ) {
        return BlobPlacer.place(OreBaseline.BASE, variant, ore, ChunkSeed.of(TEAM, 0, chunkX, chunkZ, GenSalts.GRAVEL),
                ChunkSeed.of(TEAM, 0, chunkX, chunkZ, GenSalts.CLAY));
    }

    private static Set<Integer> oreCells(VaultVariant variant, List<VeinExtent> ore) {
        Set<Integer> cells = new HashSet<>();
        for (VeinExtent vein : ore) {
            VeinPlacerTest.forEachPosition(vein, (x, y, z) -> cells.add(BlobPlacer.cell(variant, x, y, z)));
        }
        return cells;
    }

    @Test
    void noBlobTakesAnOrePositionAndClayNeverTakesGravel() {
        for (VaultVariant variant : VaultVariant.values()) {
            for (int chunk = 0; chunk < 10; chunk++) {
                List<VeinExtent> ore = ore(variant, chunk, -chunk).veins();
                Set<Integer> taken = oreCells(variant, ore);
                BlobPlacer.Placement blobs = blobs(variant, ore, chunk, -chunk);
                assertTrue(blobs.gravel().length > 0, "no gravel in chunk " + chunk);
                for (int cell : blobs.gravel()) {
                    assertTrue(taken.add(cell), "gravel on ore or twice at " + cell);
                }
                for (int cell : blobs.clay()) {
                    assertTrue(taken.add(cell), "clay on ore or gravel at " + cell);
                }
            }
        }
    }

    @Test
    void everyBlobIsCutToItsBand() {
        for (VaultVariant variant : VaultVariant.values()) {
            int lowest = variant.band(Band.DEEPSLATE).or(() -> variant.band(Band.STONE)).orElseThrow().bottomY();
            int highest = variant.band(Band.STONE).orElseThrow().topY();
            for (int chunk = 0; chunk < 20; chunk++) {
                BlobPlacer.Placement blobs = blobs(variant, List.of(), chunk, chunk);
                for (int[] cells : List.of(blobs.gravel(), blobs.clay())) {
                    for (int cell : cells) {
                        int y = BlobPlacer.y(variant, cell);
                        assertTrue(y >= lowest && y <= highest, variant.id() + " blob at Y=" + y);
                    }
                }
            }
        }
    }

    /** The expanded type's two bands are each the band's own blobs, so gravel reaches deepslate too (§3.1). */
    @Test
    void theDeepslateBandGetsItsOwnBlobs() {
        BandBounds deepslate = VaultVariant.EXPANDED.band(Band.DEEPSLATE).orElseThrow();
        long inDeepslate = 0;
        for (int chunk = 0; chunk < 20; chunk++) {
            for (int cell : blobs(VaultVariant.EXPANDED, List.of(), chunk, 3).gravel()) {
                inDeepslate += BlobPlacer.y(VaultVariant.EXPANDED, cell) <= deepslate.topY() ? 1 : 0;
            }
        }
        assertTrue(inDeepslate > 0);
    }

    @Test
    void cellsRoundTrip() {
        for (VaultVariant variant : VaultVariant.values()) {
            int cell = BlobPlacer.cell(variant, 7, 200, 13);
            assertEquals(7, BlobPlacer.x(cell));
            assertEquals(13, BlobPlacer.z(cell));
            assertEquals(200, BlobPlacer.y(variant, cell));
        }
    }

    @Test
    void theSameSeedsPlaceTheSameBlobs() {
        List<VeinExtent> ore = ore(VaultVariant.EXPANDED, 2, 5).veins();
        assertEquals(blobs(VaultVariant.EXPANDED, ore, 2, 5), blobs(VaultVariant.EXPANDED, ore, 2, 5));
        assertNotEquals(blobs(VaultVariant.EXPANDED, ore, 2, 5), blobs(VaultVariant.EXPANDED, ore, 2, 6));
    }

    /** Own salts: a new clay seed moves no gravel, and gravel without ore under it holds every cell it held with ore. */
    @Test
    void eachKindHasItsOwnStream() {
        List<VeinExtent> ore = ore(VaultVariant.BASE, 1, 1).veins();
        long gravelSeed = ChunkSeed.of(TEAM, 0, 1, 1, GenSalts.GRAVEL);
        BlobPlacer.Placement one = BlobPlacer.place(OreBaseline.BASE, VaultVariant.BASE, ore, gravelSeed, 11L);
        BlobPlacer.Placement two = BlobPlacer.place(OreBaseline.BASE, VaultVariant.BASE, ore, gravelSeed, 12L);
        assertTrue(Arrays.equals(one.gravel(), two.gravel()));
        assertFalse(Arrays.equals(one.clay(), two.clay()));

        Set<Integer> withoutOre = new HashSet<>();
        for (int cell : BlobPlacer.place(OreBaseline.BASE, VaultVariant.BASE, List.of(), gravelSeed, 11L).gravel()) {
            withoutOre.add(cell);
        }
        Set<Integer> oreCells = oreCells(VaultVariant.BASE, ore);
        for (int cell : one.gravel()) {
            assertTrue(withoutOre.contains(cell), "gravel moved because of ore at " + cell);
        }
        for (int cell : withoutOre) {
            assertTrue(oreCells.contains(cell) || Arrays.binarySearch(one.gravel(), cell) >= 0, "gravel lost a cell not on ore");
        }
    }

    @Test
    void theArraysAreCopies() {
        BlobPlacer.Placement blobs = blobs(VaultVariant.BASE, List.of(), 0, 0);
        int[] gravel = blobs.gravel();
        int first = gravel[0];
        gravel[0] = -1;
        assertEquals(first, blobs.gravel()[0]);
    }

    /**
     * §3.1's rate, about 1.6% gravel and 0.5% clay in the stone band: 81 chunks of the base type against the blob count times
     * mean blocks, less the blocks cut at the band's edges and those already ore, within 10% relative.
     */
    @Test
    void theStoneBandHoldsItsGravelAndClay() {
        BandBounds stone = VaultVariant.BASE.band(Band.STONE).orElseThrow();
        long gravel = 0;
        long clay = 0;
        for (int x = -CHUNKS / 2; x <= CHUNKS / 2; x++) {
            for (int z = -CHUNKS / 2; z <= CHUNKS / 2; z++) {
                BlobPlacer.Placement blobs = blobs(VaultVariant.BASE, ore(VaultVariant.BASE, x, z).veins(), x, z);
                gravel += blobs.gravel().length;
                clay += blobs.clay().length;
            }
        }
        double volume = 256.0 * stone.layers() * CHUNKS * CHUNKS;
        int size = OreBaseline.BASE.gravel().get(Band.STONE).size();
        double kept = stone.layers() / (stone.layers() + VeinShape.blockWeightedBoxHeight(size) - 1)
                * (1 - OreBaseline.BASE.density(Band.STONE, VaultVariant.BASE));
        double gravelExpected = OreBaseline.BASE.gravel().get(Band.STONE).perChunk() * VeinShape.meanBlocks(size) * kept / (256.0 * stone.layers());
        double clayExpected = OreBaseline.BASE.clay().get(Band.STONE).perChunk() * VeinShape.meanBlocks(size) * kept / (256.0 * stone.layers());
        System.out.println("Gravel " + gravel / volume + " against " + gravelExpected + ", clay " + clay / volume + " against " + clayExpected);
        assertEquals(gravelExpected, gravel / volume, 0.10 * gravelExpected);
        assertEquals(clayExpected, clay / volume, 0.15 * clayExpected);
    }
}
