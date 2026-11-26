package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * §6.1 Forks, "an option must cover every tier its parent has": an option's tier count equals the parent's nodes counted
 * for it by layout {@code forOption}. Old #102.
 */
class ForkCoverageTest {
    private static final Optional<String> PLAIN = Optional.empty();
    private static final Optional<String> DOUBLING = Optional.of("ore_doubling");
    /** Ore Working as §6.1 lays it out: nodes 1 to 3 plain, 4 to 6 shown only under Ore Doubling. */
    private static final List<Optional<String>> ORE_WORKING = List.of(PLAIN, PLAIN, PLAIN, DOUBLING, DOUBLING, DOUBLING);

    @Test
    void section61CountsPass() {
        for (ForkOption o : ForkOption.of(SkillId.VEIN_SHAPING)) {
            assertEquals(List.of(), ForkCoverage.gaps(o, 2), o.id());
        }
        for (ForkOption o : ForkOption.of(SkillId.ORE_ATTUNEMENT)) {
            assertEquals(List.of(), ForkCoverage.gaps(o, 3), o.id());
        }
        for (ForkOption o : ForkOption.of(SkillId.RESONANT_DRAW)) {
            assertEquals(List.of(), ForkCoverage.gaps(o, 3), o.id());
        }
        assertEquals(List.of(), ForkCoverage.gaps(ForkOption.ORE_DOUBLING, 6));
        assertEquals(List.of(), ForkCoverage.gaps(ForkOption.SMELTERS_INTUITION, 3));
    }

    @Test
    void oreWorkingsExceptionFallsOutOfForOption() {
        assertEquals(6, ForkCoverage.parentNodesForOption(ForkOption.ORE_DOUBLING, ORE_WORKING));
        assertEquals(3, ForkCoverage.parentNodesForOption(ForkOption.SMELTERS_INTUITION, ORE_WORKING));
        assertEquals(List.of(), ForkCoverage.gaps(ForkOption.ORE_DOUBLING,
                ForkCoverage.parentNodesForOption(ForkOption.ORE_DOUBLING, ORE_WORKING)));
        assertEquals(List.of(), ForkCoverage.gaps(ForkOption.SMELTERS_INTUITION,
                ForkCoverage.parentNodesForOption(ForkOption.SMELTERS_INTUITION, ORE_WORKING)));
    }

    @Test
    void aCountOtherThanTheTiersFailsEitherWay() {
        // The historical gap had the shape of a parent with one node more than the option has tiers: Hoarder's
        // Instinct had two tiers under Resonant Draw's three. Here Hoarder's three tiers under four nodes.
        assertEquals(1, ForkCoverage.gaps(ForkOption.HOARDERS_INSTINCT, 4).size(), "a node with no tier behind it");
        assertEquals(1, ForkCoverage.gaps(ForkOption.HOARDERS_INSTINCT, 2).size(), "more tiers than nodes");
    }

    @Test
    void smeltersSeeingSixNodesFails() {
        List<String> gaps = ForkCoverage.gaps(ForkOption.SMELTERS_INTUITION, 6);
        assertEquals(1, gaps.size());
        assertTrue(gaps.get(0).contains("smelters_intuition"), gaps.get(0));
    }

    @Test
    void countIgnoresNodesForOtherOptions() {
        assertEquals(0, ForkCoverage.parentNodesForOption(ForkOption.SMELTERS_INTUITION, List.of(DOUBLING)));
        assertEquals(0, ForkCoverage.parentNodesForOption(ForkOption.ABUNDANCE, Collections.emptyList()));
    }
}
