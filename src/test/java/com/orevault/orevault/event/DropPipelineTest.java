package com.orevault.orevault.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The stage contract from §11, pinned as tests.
 *
 * <p>The three cases below are the three ordering questions the pipeline exists
 * to answer. They run against fake stage handlers rather than the real nodes:
 * none of those nodes are written yet, and when they are, what has to hold is
 * that a Quantity node composes with another Quantity node and that Transform
 * sees what Quantity produced — not what any particular node's factor is.</p>
 *
 * <p>Handlers get a {@code null} context and an empty drop list on purpose. That
 * keeps the ordering assertions independent of a live world, and it works
 * because every stage application short-circuits on an empty list.</p>
 */
class DropPipelineTest {

    private List<String> order;

    @BeforeEach
    void setUp() {
        DropPipeline.clearForTest();
        order = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        DropPipeline.clearForTest();
    }

    /** Records that a stage ran, without contributing anything. */
    private DropPipeline.DropStage marker(String name) {
        return (context, outcome, drops) -> order.add(name);
    }

    @Test
    void quantityNodesComposeMultiplicatively() {
        // Ore Doubling and Greedy Seams both land in QUANTITY. §11 requires the
        // second to build on the first rather than overwrite it.
        DropPipeline.register(DropPipeline.Stage.QUANTITY, (context, outcome, drops) -> outcome.multiplyQuantity(2.0));
        DropPipeline.register(DropPipeline.Stage.QUANTITY, (context, outcome, drops) -> outcome.multiplyQuantity(1.5));

        DropPipeline.Outcome outcome = DropPipeline.run(null, List.of());

        assertEquals(3.0, outcome.quantityMultiplier(), 1.0e-9);
    }

    @Test
    void transformRunsAfterQuantity() {
        // Smelter's Intuition smelts the doubled output, not the original stack.
        DropPipeline.register(DropPipeline.Stage.TRANSFORM, marker("transform"));
        DropPipeline.register(DropPipeline.Stage.QUANTITY, marker("quantity"));

        DropPipeline.run(null, List.of());

        assertEquals(List.of("quantity", "transform"), order);
    }

    @Test
    void consumeShortCircuitsEveryLaterStage() {
        // Tithe consumes the block, so Fortune never rolls and nothing downstream
        // gets a say — there is no drop left to modify.
        DropPipeline.register(DropPipeline.Stage.CONSUME, (context, outcome, drops) -> outcome.consume(true));
        DropPipeline.register(DropPipeline.Stage.FORTUNE, marker("fortune"));
        DropPipeline.register(DropPipeline.Stage.QUANTITY, marker("quantity"));
        DropPipeline.register(DropPipeline.Stage.TRANSFORM, marker("transform"));
        DropPipeline.register(DropPipeline.Stage.BONUS, marker("bonus"));

        DropPipeline.Outcome outcome = DropPipeline.run(null, List.of());

        assertTrue(outcome.consumed());
        assertEquals(List.of(), order);
    }

    @Test
    void everyStageRunsInDeclarationOrderWhenNothingConsumes() {
        for (DropPipeline.Stage stage : DropPipeline.Stage.values()) {
            DropPipeline.register(stage, marker(stage.name()));
        }

        DropPipeline.run(null, List.of());

        assertEquals(List.of("CONSUME", "FORTUNE", "QUANTITY", "TRANSFORM", "BONUS"), order);
    }

    @Test
    void fortuneBonusesFromSeveralNodesAddUp() {
        // Vein Fortune and Vault's Purity both raise effective Fortune, and the
        // pipeline re-rolls the loot table once at the total rather than once per
        // node.
        DropPipeline.register(DropPipeline.Stage.FORTUNE, (context, outcome, drops) -> outcome.addFortune(2));
        DropPipeline.register(DropPipeline.Stage.FORTUNE, (context, outcome, drops) -> outcome.addFortune(1));

        DropPipeline.Outcome outcome = DropPipeline.run(null, List.of());

        assertEquals(3, outcome.bonusFortune());
    }

    @Test
    void brittleStoneConsumesWithoutEarningTheTitheBonus() {
        // Both nodes consume; only Tithe pays 1.75× Resonance for it (§4.2).
        DropPipeline.register(DropPipeline.Stage.CONSUME, (context, outcome, drops) -> outcome.consume(false));

        DropPipeline.Outcome outcome = DropPipeline.run(null, List.of());

        assertTrue(outcome.consumed());
        assertFalse(outcome.consumedByTithe());
    }

    @Test
    void negativeAndZeroContributionsAreIgnored() {
        // A node that computes its way to a nonsense factor must not be able to
        // delete a break's drops or subtract another node's Fortune.
        DropPipeline.register(DropPipeline.Stage.FORTUNE, (context, outcome, drops) -> outcome.addFortune(-5));
        DropPipeline.register(DropPipeline.Stage.QUANTITY, (context, outcome, drops) -> outcome.multiplyQuantity(0.0));

        DropPipeline.Outcome outcome = DropPipeline.run(null, List.of());

        assertEquals(0, outcome.bonusFortune());
        assertEquals(1.0, outcome.quantityMultiplier(), 1.0e-9);
    }
}
