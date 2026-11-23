package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import java.util.Map;
import java.util.Objects;

/**
 * What the chunk generator may read about a team (§11, ADR 0003): an immutable value built on the main thread and read
 * off it, so that nothing on the generation thread ever touches {@code SavedData} (rule 1). It holds what placement reads
 * today. The node tiers, fork choices, tradeoffs and stamp join it with the stories that read them; it is memory only, so
 * adding a field costs no migration.
 *
 * @param variant the dimension type; the generator is built per variant and checks that the two agree
 * @param vaultGeneration the reset count, part of every seed
 * @param clip the 40% floor's factors (§3.1), computed by {@link ClipFactors#of} when the snapshot is built
 * @param edges each band's and rarity's edge factor, computed by {@link EdgeFactors#of} when the snapshot is built, so the
 *     worker never recomputes it per chunk (R-6)
 */
public record GenSnapshot(VaultVariant variant, int vaultGeneration, ClipFactors clip, EdgeFactors edges) {
    public GenSnapshot {
        Objects.requireNonNull(variant);
        Objects.requireNonNull(clip);
        Objects.requireNonNull(edges);
    }

    /**
     * The one way the snapshot is built outside tests of the record itself, so the clip factors always come from the
     * baseline, the pack's ore profiles and the team's generation tiers, never from a hand-written value (Developer,
     * DEV-E4-12). The clip reads where each ore sits (ADR 0009, amended 2026-11-21), so a snapshot depends on the
     * classification, which ADR 0007's start order already runs first.
     *
     * @param pickers the variant's published pickers, or none for a pack with no classified ore
     */
    public static GenSnapshot of(VaultVariant variant, int vaultGeneration, Map<Band, OrePicker> pickers, GenerationTiers tiers) {
        return new GenSnapshot(variant, vaultGeneration, ClipFactors.of(OreBaseline.BASE, variant, pickers, tiers),
                EdgeFactors.of(OreBaseline.BASE, variant, pickers));
    }
}
