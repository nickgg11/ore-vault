package com.orevault.orevault.core.mining;

import com.orevault.orevault.core.ore.Rarity;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What one block break is, worked out once before any drop stage runs (§11 Drop Pipeline, §3.4): who broke it, whose
 * Vault it is in, whether the breaker is on that Vault's team, and the block's ore rarity. The team comes from the
 * dimension, never from the breaker, so a machine break in a Vault still knows whose Vault it is (old #92).
 *
 * @param source hand or machine (§3.4)
 * @param vaultTeam the team whose Vault the block was in, or null outside every Vault
 * @param member the breaker is a current member of {@code vaultTeam}; asked only for a hand break, because a fake
 *     player's team is a phantom id (§9)
 * @param rarity the block's classified rarity, or null for stone, deepslate and every block the classifier could not read:
 *     there is no default rarity (§4.2, old PR #117)
 */
public record BreakContext(BreakSource source, @Nullable UUID vaultTeam, boolean member, @Nullable Rarity rarity) {
    private static final double THOUSANDTHS = 1_000.0;

    public BreakContext {
        if (member && (vaultTeam == null || source != BreakSource.HAND)) {
            throw new IllegalArgumentException("membership is asked only for a hand break in a Vault");
        }
    }

    public boolean inVault() {
        return vaultTeam != null;
    }

    /**
     * Only a classified ore broken by hand in a Vault by a member of that Vault's team pays (§3.4, §4.2). Stone,
     * unclassified blocks, machine breaks and a non-member's breaks pay and record nothing.
     */
    public boolean paysResonance() {
        return source == BreakSource.HAND && vaultTeam != null && member && rarity != null;
    }

    /**
     * The orb's value before any node and before team scaling, in thousandths: 2,000, 5,000 or 12,500 by rarity (§4.2),
     * or 0 when the break pays nothing. {@code ResonanceBonuses} applies on top of this with E9-S2 (#306).
     */
    public long awardThousandths() {
        return paysResonance() ? Math.round(rarity.baseResonance() * THOUSANDTHS) : 0L;
    }
}
