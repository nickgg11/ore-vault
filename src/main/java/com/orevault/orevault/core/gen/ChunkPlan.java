package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a Vault chunk holds beyond its layer stack, as values (§3.1, ADR 0004 "Placement returns extents"): the ore
 * veins, then gravel and clay. The generator writes exactly this and nothing else, so it stays a zero-logic adapter, and
 * the determinism test runs here in JUnit: a chunk's plan is a function of the team, the snapshot, the chunk and the
 * pickers, so it comes out the same whatever order chunks are generated in (§3.1, ADR 0003).
 *
 * @param ore the veins, from the seed of {@link GenSalts#BASELINE_ORE}
 * @param blobs gravel and clay, from the seeds of {@link GenSalts#GRAVEL} and {@link GenSalts#CLAY}
 */
public record ChunkPlan(VeinPlacer.Placement ore, BlobPlacer.Placement blobs) {
    private static final VeinPlacer.Placement NO_ORE = new VeinPlacer.Placement(List.of(), false);

    public ChunkPlan {
        Objects.requireNonNull(ore);
        Objects.requireNonNull(blobs);
    }

    /**
     * One chunk's plan.
     *
     * @param pickers one per band of the variant, or none at all for a pack with no classified ore, which places gravel and
     *     clay only (the classifier has logged an ERROR then)
     */
    public static ChunkPlan of(OreBaseline baseline, VaultVariant variant, Map<Band, OrePicker> pickers, GenSnapshot snapshot,
            UUID teamId, int chunkX, int chunkZ) {
        if (snapshot.variant() != variant) {
            throw new IllegalArgumentException("A plan for " + variant.id() + " from a snapshot of " + snapshot.variant().id());
        }
        VeinPlacer.Placement ore = pickers.isEmpty() ? NO_ORE
                : VeinPlacer.placeOre(baseline, variant, pickers, snapshot.clip(),
                        ChunkSeed.of(teamId, snapshot.vaultGeneration(), chunkX, chunkZ, GenSalts.BASELINE_ORE));
        BlobPlacer.Placement blobs = BlobPlacer.place(baseline, variant, ore.veins(),
                ChunkSeed.of(teamId, snapshot.vaultGeneration(), chunkX, chunkZ, GenSalts.GRAVEL),
                ChunkSeed.of(teamId, snapshot.vaultGeneration(), chunkX, chunkZ, GenSalts.CLAY));
        return new ChunkPlan(ore, blobs);
    }

    /** Ore blocks the plan places, after later veins took earlier veins' positions. */
    public long oreBlocks() {
        return ore.veins().stream().mapToLong(VeinExtent::blocks).sum();
    }
}
