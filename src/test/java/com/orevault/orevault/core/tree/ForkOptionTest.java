package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** §6.1 Forks: the option ids frozen at #208, their parents and their tier counts (rows of each option's table). */
class ForkOptionTest {
    @Test
    void idsParentsAndTiersAsSection61() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("abundance", "vein_shaping:2");
        expected.put("vein_singularity", "vein_shaping:2");
        expected.put("stratified", "vein_shaping:2");
        expected.put("common_focus", "ore_attunement:3");
        expected.put("uncommon_focus", "ore_attunement:3");
        expected.put("rare_focus", "ore_attunement:3");
        expected.put("ore_doubling", "ore_working:6");
        expected.put("smelters_intuition", "ore_working:3");
        expected.put("resonance_magnetism", "resonant_draw:3");
        expected.put("hoarders_instinct", "resonant_draw:3");
        Map<String, String> actual = new LinkedHashMap<>();
        for (ForkOption o : ForkOption.values()) {
            actual.put(o.id(), o.parent().id() + ":" + o.tiers());
        }
        assertEquals(expected, actual);
    }

    @Test
    void idsAreUniqueLowerSnakeAndShortEnough() {
        Set<String> seen = new HashSet<>();
        for (ForkOption o : ForkOption.values()) {
            assertTrue(seen.add(o.id()), "duplicate " + o.id());
            assertTrue(SkillIdTest.LOWER_SNAKE.matcher(o.id()).matches(), o.id());
            assertTrue(o.id().length() <= SkillIdTest.MAX_ID, o.id());
        }
    }

    @Test
    void noIdIsReservedStartOrASkillId() {
        for (ForkOption o : ForkOption.values()) {
            assertFalse(ReservedIds.isReserved(o.id()), o.id());
            assertFalse(o.id().equals("start"));
            assertTrue(SkillId.fromId(o.id()).isEmpty(), o.id() + " equals a skill id");
        }
    }

    @Test
    void parentsAreTheFourForkParentsEachWithAtLeastTwoOptions() {
        Set<SkillId> parents = Arrays.stream(ForkOption.values()).map(ForkOption::parent)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SkillId.class)));
        assertEquals(EnumSet.of(SkillId.VEIN_SHAPING, SkillId.ORE_ATTUNEMENT, SkillId.ORE_WORKING,
                SkillId.RESONANT_DRAW), parents);
        for (SkillId parent : parents) {
            assertTrue(ForkOption.of(parent).size() >= 2, parent.id());
        }
    }

    @Test
    void ofListsAParentsOptionsAndNothingForOtherSkills() {
        assertEquals(List.of(ForkOption.ORE_DOUBLING, ForkOption.SMELTERS_INTUITION),
                ForkOption.of(SkillId.ORE_WORKING));
        assertTrue(ForkOption.of(SkillId.VEIN_EXPANSION).isEmpty());
    }

    @Test
    void fromIdRoundTripsAndRefusesOthers() {
        for (ForkOption o : ForkOption.values()) {
            assertEquals(Optional.of(o), ForkOption.fromId(o.id()));
        }
        assertTrue(ForkOption.fromId("motherlode").isEmpty());
        assertTrue(ForkOption.fromId("ORE_DOUBLING").isEmpty());
        assertTrue(ForkOption.fromId("ore_working").isEmpty());
    }
}
