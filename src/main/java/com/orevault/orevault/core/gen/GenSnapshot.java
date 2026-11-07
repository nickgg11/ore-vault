package com.orevault.orevault.core.gen;

/**
 * What the chunk generator may read about a team (§11, ADR 0003): an immutable value built on the main thread and read
 * off it, so that nothing on the generation thread ever touches {@code SavedData} (rule 1). It holds what placement reads
 * today. The node tiers, fork choices, tradeoffs, clip factors and stamp join it with the stories that read them; it is
 * memory only, so adding a field costs no migration.
 *
 * @param variant the dimension type; the generator is built per variant and checks that the two agree
 * @param vaultGeneration the reset count, part of every seed
 */
public record GenSnapshot(VaultVariant variant, int vaultGeneration) {
}
