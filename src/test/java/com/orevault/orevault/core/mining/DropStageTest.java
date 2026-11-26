package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** §11 Drop Pipeline: the six stages in a fixed order, and which of them a break skips. */
class DropStageTest {
    @Test
    void theStagesAreTheSpecsSixInOrder() {
        assertEquals(List.of(DropStage.CONSUME, DropStage.FORTUNE, DropStage.QUANTITY, DropStage.TRANSFORM, DropStage.BONUS,
                DropStage.DELIVER), List.of(DropStage.values()));
    }

    @Test
    void anOrdinaryBreakRunsEveryStageAfterConsume() {
        assertEquals(List.of(DropStage.FORTUNE, DropStage.QUANTITY, DropStage.TRANSFORM, DropStage.BONUS, DropStage.DELIVER),
                DropStage.afterConsume(false, false));
    }

    /** Consume short-circuits before Fortune ever rolls; XP and orbs (Bonus) and the counters (Deliver) still run. */
    @Test
    void aConsumedBlockSkipsFortuneQuantityAndTransform() {
        assertEquals(List.of(DropStage.BONUS, DropStage.DELIVER), DropStage.afterConsume(true, false));
    }

    /** Ancient debris skips stages 2 to 4 and always drops exactly one (Ancient Traces, §6.1). */
    @Test
    void ancientDebrisSkipsStagesTwoToFour() {
        assertEquals(List.of(DropStage.BONUS, DropStage.DELIVER), DropStage.afterConsume(false, true));
    }
}
