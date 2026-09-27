package com.orevault.orevault.event;

import java.util.ArrayList;
import java.util.List;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.session.VaultTrip;
import com.orevault.orevault.skill.ResonanceBonuses;

import org.jspecify.annotations.Nullable;

/**
 * The single place a node adds to an ore break's Resonance (§11).
 *
 * <p>The Resonance-side twin of {@link DropPipeline}, and it exists for the same reason: without one
 * registry every node that pays "+X% Resonance" patches {@code OreDropHandler} directly and the
 * order they compose in becomes whatever the last author assumed. Handlers contribute to a
 * {@link ResonanceBonuses}, which states how the contributions combine and applies them once.</p>
 *
 * <p>There are no stages here, unlike the drop pipeline. Additive contributions commute, so ordering
 * carries no meaning and inventing stages would imply it did.</p>
 */
public final class ResonancePipeline {

    /**
     * One node's contribution to a break's Resonance.
     *
     * @param stats the breaker's lifetime record — never null; a machine break never reaches here
     * @param trip  the breaker's current trip, or null if they somehow broke a block in a Vault
     *              without one (a stale dimension change). Per-trip nodes contribute nothing then
     *              rather than starting a trip mid-delve.
     */
    @FunctionalInterface
    public interface Source {
        void contribute(VaultBreakContext context, PlayerStats stats, @Nullable VaultTrip trip, ResonanceBonuses out);
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    private ResonancePipeline() {
    }

    /** Registers a node's contribution. Call during mod construction. */
    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** Sums every registered node's contribution for one break. */
    public static ResonanceBonuses run(
            VaultBreakContext context, PlayerStats stats, @Nullable VaultTrip trip) {
        ResonanceBonuses bonuses = new ResonanceBonuses();
        for (Source source : SOURCES) {
            source.contribute(context, stats, trip, bonuses);
        }
        return bonuses;
    }

    /** Test hook, matching {@code DropPipeline#clearForTest}. */
    static void clearForTest() {
        SOURCES.clear();
    }
}
