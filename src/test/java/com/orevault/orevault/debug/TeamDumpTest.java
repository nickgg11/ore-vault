package com.orevault.orevault.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orevault.orevault.data.TeamFields;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The lines the restart check reads (S44, #241); its lists match on them, so they are pinned here. */
class TeamDumpTest {
    private static final UUID TEAM = UUID.fromString("0000000b-0000-4000-8000-00000000000b");

    @Test
    void printsEveryStoredFieldWithThePoolInBothUnits() {
        TeamFields fields = new TeamFields(15_300L, 2, 1, OptionalLong.empty());
        assertEquals("team 0000000b-0000-4000-8000-00000000000b record=ok resonance_pool=15300 (15.3) level_reached=2 "
                + "vault_generation=1 disbanded_at=none", new TeamDump(TEAM, TeamDump.Record.OK, fields).line());
    }

    @Test
    void printsADisbandTime() {
        TeamFields fields = new TeamFields(0L, 0, 0, OptionalLong.of(3_000_000_000L));
        assertEquals("team 0000000b-0000-4000-8000-00000000000b record=ok resonance_pool=0 (0) level_reached=0 "
                + "vault_generation=0 disbanded_at=3000000000", new TeamDump(TEAM, TeamDump.Record.OK, fields).line());
    }

    /**
     * A team with no file and a team whose file cannot be read both read as the defaults; the line keeps them apart so a
     * damaged file never reads as a fresh team (Architect, #241 C2a).
     */
    @Test
    void saysWhetherTheTeamHasARecord() {
        assertEquals("team 0000000b-0000-4000-8000-00000000000b record=none resonance_pool=0 (0) level_reached=0 "
                + "vault_generation=0 disbanded_at=none", new TeamDump(TEAM, TeamDump.Record.NONE, TeamFields.DEFAULTS).line());
        assertEquals("team 0000000b-0000-4000-8000-00000000000b record=unreadable resonance_pool=0 (0) level_reached=0 "
                + "vault_generation=0 disbanded_at=none",
                new TeamDump(TEAM, TeamDump.Record.UNREADABLE, TeamFields.DEFAULTS).line());
    }

    /** Thousandths shown as Resonance exactly, never through a double. */
    @Test
    void showsThousandthsAsResonanceExactly() {
        assertEquals("0.001", TeamDump.resonance(1L));
        assertEquals("12.5", TeamDump.resonance(12_500L));
        assertEquals("2.8", TeamDump.resonance(2_800L));
        assertEquals("1000", TeamDump.resonance(1_000_000L));
        assertEquals("9223372036854775.807", TeamDump.resonance(Long.MAX_VALUE));
    }

    /** The fixed token {@code resonance add} lets the pacing log parser leave these out (Architect, #241 C2d). */
    @Test
    void anAddSaysWhatWasAddedAndThePoolAfter() {
        assertEquals("resonance add team=0000000b-0000-4000-8000-00000000000b added=12500 (12.5) resonance_pool=15300 (15.3)",
                TeamDump.added(TEAM, 12_500L, 15_300L));
    }
}
