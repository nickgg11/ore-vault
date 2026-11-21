package com.orevault.orevault.core.gen;

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
 */
public record GenSnapshot(VaultVariant variant, int vaultGeneration, ClipFactors clip) {
    public GenSnapshot {
        Objects.requireNonNull(variant);
        Objects.requireNonNull(clip);
    }

    /**
     * The one way the snapshot is built outside tests of the record itself, so the clip factors always come from the
     * baseline and the team's generation tiers and never from a hand-written value (Developer, DEV-E4-12).
     */
    public static GenSnapshot of(VaultVariant variant, int vaultGeneration, GenerationTiers tiers) {
        return new GenSnapshot(variant, vaultGeneration, ClipFactors.of(OreBaseline.BASE, variant, tiers));
    }
}
