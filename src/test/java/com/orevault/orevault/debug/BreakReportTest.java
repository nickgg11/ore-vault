package com.orevault.orevault.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BreakReportTest {
    private static String line(boolean destroyed, boolean gone) {
        return new BreakReport("orevault:vault_ab", 1, -2, 3, "minecraft:stone", false, destroyed, gone).line();
    }

    @Test
    void saysWhereWhatAndThatItIsGone() {
        assertEquals("break orevault:vault_ab 1 -2 3 minecraft:stone: destroyBlock=true gone=true", line(true, true));
    }

    /** A cancelled break: the restart check reads this as the Vault refusing, not as a pass. */
    @Test
    void saysWhenTheBlockIsStillThere() {
        assertEquals("break orevault:vault_ab 1 -2 3 minecraft:stone: destroyBlock=false gone=false", line(false, false));
    }

    /** True from {@code destroyBlock} is the game mode's opinion; the line reports the world's as well. */
    @Test
    void keepsTheTwoAnswersApart() {
        assertEquals("break orevault:vault_ab 1 -2 3 minecraft:stone: destroyBlock=true gone=false", line(true, false));
    }

    /** An empty position proves nothing about the level, so a typo in a script must not read as a pass. */
    @Test
    void anEmptyPositionSaysSo() {
        assertEquals("break minecraft:overworld 0 64 0 minecraft:air: nothing to break",
                new BreakReport("minecraft:overworld", 0, 64, 0, "minecraft:air", true, false, false).line());
    }
}
