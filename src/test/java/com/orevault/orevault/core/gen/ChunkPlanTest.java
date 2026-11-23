package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A chunk comes out the same whenever, and in whatever order, it is first generated (§3.1, ADR 0003; QA-E4-13): two
 * chunk orders, a shuffled ore table, and other Vaults' chunks interleaved give the same plans.
 */
class ChunkPlanTest {
    private static final UUID TEAM = UUID.fromString("9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private static final UUID OTHER = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final int SIDE = 5;

    private static Map<Long, ChunkPlan> plans(VaultVariant variant, Map<Band, OrePicker> pickers, List<int[]> order, boolean interleave) {
        GenSnapshot snapshot = GenSnapshot.of(variant, 2, pickers, GenerationTiers.NONE);
        Map<Long, ChunkPlan> plans = new HashMap<>();
        for (int[] chunk : order) {
            plans.put(key(chunk), ChunkPlan.of(OreBaseline.BASE, variant, pickers, snapshot, TEAM, chunk[0], chunk[1]));
            if (interleave) {
                ChunkPlan.of(OreBaseline.BASE, variant, pickers, GenSnapshot.of(variant, 0, pickers, GenerationTiers.NONE), OTHER, chunk[1], chunk[0]);
            }
        }
        return plans;
    }

    private static List<int[]> rowOrder() {
        List<int[]> order = new ArrayList<>();
        for (int x = -SIDE / 2; x <= SIDE / 2; x++) {
            for (int z = -SIDE / 2; z <= SIDE / 2; z++) {
                order.add(new int[] {x, z});
            }
        }
        return order;
    }

    private static long key(int[] chunk) {
        return ((long) chunk[0] << 32) | (chunk[1] & 0xFFFFFFFFL);
    }

    @Test
    void twoChunkOrdersGiveTheSamePlans() {
        for (VaultVariant variant : VaultVariant.values()) {
            Map<Band, OrePicker> pickers = OrePickers.forVariant(variant, VanillaOres.candidates());
            List<int[]> shuffled = new ArrayList<>(rowOrder());
            Collections.reverse(shuffled);
            Collections.shuffle(shuffled, new Random(7));
            assertEquals(plans(variant, pickers, rowOrder(), false), plans(variant, pickers, shuffled, true), variant.id());
        }
    }

    /** DEV-E4-5, RV-E4-6: the picker is built from a table in whatever order the classifier's map gave it. */
    @Test
    void aShuffledTableGivesTheSamePlans() {
        List<OrePicker.Candidate> shuffled = new ArrayList<>(VanillaOres.candidates());
        Collections.shuffle(shuffled, new Random(42));
        for (VaultVariant variant : VaultVariant.values()) {
            assertEquals(plans(variant, OrePickers.forVariant(variant, VanillaOres.candidates()), rowOrder(), false),
                    plans(variant, OrePickers.forVariant(variant, shuffled), rowOrder(), false), variant.id());
        }
    }

    /** Each part from its own salt, so the plan is exactly the two placers' output for the chunk's seeds (ADR 0003). */
    @Test
    void thePlanIsThePlacersWithTheirOwnSalts() {
        VaultVariant variant = VaultVariant.EXPANDED;
        Map<Band, OrePicker> pickers = OrePickers.forVariant(variant, VanillaOres.candidates());
        GenSnapshot snapshot = GenSnapshot.of(variant, 3, pickers, GenerationTiers.NONE);
        ChunkPlan plan = ChunkPlan.of(OreBaseline.BASE, variant, pickers, snapshot, TEAM, -4, 9);
        VeinPlacer.Placement ore = VeinPlacer.placeOre(OreBaseline.BASE, variant, pickers, ClipFactors.NONE,
                ChunkSeed.of(TEAM, 3, -4, 9, GenSalts.BASELINE_ORE));
        assertEquals(ore, plan.ore());
        assertEquals(BlobPlacer.place(OreBaseline.BASE, variant, ore.veins(), ChunkSeed.of(TEAM, 3, -4, 9, GenSalts.GRAVEL),
                ChunkSeed.of(TEAM, 3, -4, 9, GenSalts.CLAY)), plan.blobs());
        assertEquals(ore.veins().stream().mapToLong(VeinExtent::blocks).sum(), plan.oreBlocks());
    }

    /** §3.1: the reset count is in the seed, so a reset gives fresh veins. */
    @Test
    void aResetGivesFreshVeins() {
        VaultVariant variant = VaultVariant.BASE;
        Map<Band, OrePicker> pickers = OrePickers.forVariant(variant, VanillaOres.candidates());
        assertNotEquals(ChunkPlan.of(OreBaseline.BASE, variant, pickers, GenSnapshot.of(variant, 0, pickers, GenerationTiers.NONE), TEAM, 0, 0),
                ChunkPlan.of(OreBaseline.BASE, variant, pickers, GenSnapshot.of(variant, 1, pickers, GenerationTiers.NONE), TEAM, 0, 0));
    }

    /** The snapshot's clip reaches the placer: a clipped deepslate band places fewer deepslate veins. */
    @Test
    void theSnapshotsClipReachesThePlacer() {
        VaultVariant variant = VaultVariant.EXPANDED;
        Map<Band, OrePicker> pickers = OrePickers.forVariant(variant, VanillaOres.candidates());
        ChunkPlan open = ChunkPlan.of(OreBaseline.BASE, variant, pickers, new GenSnapshot(variant, 0, ClipFactors.NONE), TEAM, 1, 1);
        ChunkPlan clipped = ChunkPlan.of(OreBaseline.BASE, variant, pickers, new GenSnapshot(variant, 0, new ClipFactors(1, 0.5)), TEAM, 1, 1);
        assertTrue(deepslateVeins(clipped) < 0.6 * deepslateVeins(open), deepslateVeins(clipped) + " against " + deepslateVeins(open));
    }

    private static long deepslateVeins(ChunkPlan plan) {
        return plan.ore().veins().stream().filter(v -> v.band() == Band.DEEPSLATE).count();
    }

    /** A pack with no classified ore: no veins, still gravel and clay. */
    @Test
    void noPickersPlaceGravelAndClayOnly() {
        ChunkPlan plan = ChunkPlan.of(OreBaseline.BASE, VaultVariant.BASE, Map.of(), GenSnapshot.of(VaultVariant.BASE, 0, Map.of(), GenerationTiers.NONE), TEAM, 0, 0);
        assertTrue(plan.ore().veins().isEmpty());
        assertFalse(plan.ore().hardStopped());
        assertTrue(plan.blobs().gravel().length > 0);
    }

    @Test
    void aSnapshotOfTheOtherVariantIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ChunkPlan.of(OreBaseline.BASE, VaultVariant.BASE, Map.of(),
                GenSnapshot.of(VaultVariant.EXPANDED, 0, Map.of(), GenerationTiers.NONE), TEAM, 0, 0));
    }
}
