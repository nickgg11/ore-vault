package com.orevault.orevault.core.mining;

import java.util.Locale;
import java.util.UUID;

/**
 * The line {@code debug.log_resonance_gain} writes at INFO on each orb pickup (§10), in one fixed shape so a log can be
 * read back by a script. Values are Resonance with three decimals, the thousandths the pool stores. Removed with the
 * rest of the {@code [debug]} block at T14.
 *
 * @param orbs the orbs merged into the one collected ({@code orb_count})
 * @param valueThousandths the orb's value, before team scaling
 * @param pooledThousandths what the pool received after team scaling (§4.2); 0 for a disbanded team
 */
public record GainLine(UUID team, UUID collector, int orbs, long valueThousandths, int teamSize, long pooledThousandths) {
    private static final String TAG = "[orevault-debug] ";

    public String line() {
        return TAG + "resonance gain team=" + team + " collector=" + collector + " orbs=" + orbs + " value="
                + resonance(valueThousandths) + " team_size=" + teamSize + " pooled=" + resonance(pooledThousandths);
    }

    private static String resonance(long thousandths) {
        return Long.toString(thousandths / 1_000) + "." + String.format(Locale.ROOT, "%03d", thousandths % 1_000);
    }
}
