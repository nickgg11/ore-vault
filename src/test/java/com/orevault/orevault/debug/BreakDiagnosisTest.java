package com.orevault.orevault.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BreakDiagnosisTest {
    /** Each value here differs from the rest, so a line printing the wrong field fails this. */
    @Test
    void printsEveryFieldOldIssue82NeededToTellAStalledBreakFromADeniedOne() {
        BreakDiagnosis diagnosis = new BreakDiagnosis("orevault:vault_ab", 1200, 340L, "BlockPos{x=1, y=2, z=3}",
                "minecraft:stone", 0.25F, true, false, true, 59999968.0, "survival");
        assertEquals(List.of(
                "dim=orevault:vault_ab serverTick=1200 levelGameTime=340",
                "pos=BlockPos{x=1, y=2, z=3} block=minecraft:stone destroyProgress=0.25",
                "mayInteract=true spawnProtection=false insideBorder=true borderSize=5.9999968E7",
                "gameMode=survival"), diagnosis.lines());
    }
}
