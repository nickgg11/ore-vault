package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** §11 Drop Pipeline: Consume, Fortune and Quantity contribute; the pipeline applies the total once. */
class DropOutcomeTest {
    @Test
    void aFreshOutcomeChangesNothing() {
        DropOutcome outcome = new DropOutcome();
        assertFalse(outcome.consumed());
        assertEquals(0, outcome.fortune());
        assertEquals(1.0, outcome.quantity());
    }

    @Test
    void consumeSticks() {
        DropOutcome outcome = new DropOutcome();
        outcome.consume();
        outcome.consume();
        assertTrue(outcome.consumed());
    }

    /** One re-roll however many nodes raise Fortune: their levels sum. */
    @Test
    void fortuneContributionsSum() {
        DropOutcome outcome = new DropOutcome();
        outcome.addFortune(1);
        outcome.addFortune(2);
        assertEquals(3, outcome.fortune());
    }

    /** Quantity multipliers compose multiplicatively because they are multiplied together. */
    @Test
    void quantityContributionsMultiply() {
        DropOutcome outcome = new DropOutcome();
        outcome.multiplyQuantity(2.0);
        outcome.multiplyQuantity(1.25);
        assertEquals(2.5, outcome.quantity(), 1e-12);
    }
}
