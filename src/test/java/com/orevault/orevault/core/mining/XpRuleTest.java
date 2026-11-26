package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** §3.4 Vanilla XP is untouched: a node only ever adds to the vanilla amount (§11 Drop Pipeline, stages 1 and 5). */
class XpRuleTest {
    @Test
    void aBonusIsAdded() {
        assertEquals(7, XpRule.raise(4, 3));
    }

    @Test
    void aZeroBonusLeavesVanilla() {
        assertEquals(4, XpRule.raise(4, 0));
    }

    /** Planted break: letting a negative bonus through lowers vanilla XP, which §3.4 forbids. */
    @Test
    void aNegativeBonusNeverLowersTheValue() {
        assertEquals(4, XpRule.raise(4, -3));
        assertEquals(0, XpRule.raise(0, Integer.MIN_VALUE));
    }

    @Test
    void theSumSaturatesRatherThanWrapping() {
        assertEquals(Integer.MAX_VALUE, XpRule.raise(Integer.MAX_VALUE - 1, 5));
    }

    /**
     * Never below what came in, for every pair tried. No XP node exists yet (E9), so this is what "every XP node owned
     * shows vanilla XP at least the vanilla amount" can say today: every node goes through this function.
     */
    @Test
    void neverBelowTheIncomingValue() {
        int[] values = {0, 1, 3, 7, 100, Integer.MAX_VALUE};
        int[] bonuses = {Integer.MIN_VALUE, -100, -1, 0, 1, 100, Integer.MAX_VALUE};
        for (int value : values) {
            for (int bonus : bonuses) {
                int raised = XpRule.raise(value, bonus);
                assertTrue(raised >= value, value + " + " + bonus + " gave " + raised);
            }
        }
    }
}
