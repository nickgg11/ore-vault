package com.orevault.orevault.debug;

import com.orevault.orevault.data.TeamFields;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/**
 * What {@code /orevault team dump} prints, as one line the restart check reads (S44; ADR 0005 section 6, restart checks
 * through the dump; #241). E5-S5 (#275) extends it. The pool is printed in its stored thousandths and in Resonance, so an
 * NBT editor's number and a readout's number can both be matched.
 *
 * @param record whether the team has a file, and whether it could be read: both a team with no file and one whose file is
 *     damaged read as the defaults, and the line must not let a damaged file pass for a fresh team
 */
record TeamDump(UUID team, Record record, TeamFields fields) {
    enum Record {
        NONE, OK, UNREADABLE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    String line() {
        return "team " + team + " record=" + record.id()
                + " resonance_pool=" + fields.resonancePool() + " (" + resonance(fields.resonancePool()) + ")"
                + " level_reached=" + fields.levelReached()
                + " vault_generation=" + fields.vaultGeneration()
                + " disbanded_at=" + (fields.disbandedAt().isPresent() ? fields.disbandedAt().getAsLong() : "none");
    }

    /** Thousandths as Resonance, exactly: through a decimal, never a double. The division by 1,000 has no remainder. */
    static String resonance(long thousandths) {
        return BigDecimal.valueOf(thousandths)
                .divide(BigDecimal.valueOf(TeamFields.THOUSANDTHS_PER_RESONANCE), 3, RoundingMode.UNNECESSARY)
                .stripTrailingZeros().toPlainString();
    }

    /** The reply and log line of {@code /orevault team resonance add}; its fixed token lets the pacing parser skip it. */
    static String added(UUID team, long added, long pool) {
        return "resonance add team=" + team + " added=" + added + " (" + resonance(added) + ") resonance_pool=" + pool
                + " (" + resonance(pool) + ")";
    }
}
