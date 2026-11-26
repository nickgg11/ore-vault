package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The agreement check between the layout's ids and Java's (§11 Tree Layout Data; old #135). Small literal sets go through
 * the package-private overload; the public method is checked against the full enums. The run over the shipped
 * {@code resonance.json} is owed to #239 (Manager ruling 3, #208).
 */
class TreeIdsTest {
    private static final Set<String> KNOWN_SKILLS = Set.of("alpha", "beta", "fork");
    private static final Map<String, String> KNOWN_OPTIONS = Map.of("one", "fork", "two", "fork");

    private static List<String> check(List<String> nodes, List<String> skills, Map<String, List<String>> options) {
        return TreeIds.problems(nodes, skills, options, KNOWN_SKILLS, KNOWN_OPTIONS);
    }

    private static Map<String, List<String>> cleanOptions() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("fork", List.of("one", "two"));
        return m;
    }

    private static final List<String> NODES = List.of("start", "alpha", "beta_1", "beta_2", "fork_1");
    private static final List<String> SKILLS = List.of("alpha", "beta", "fork");

    private static void assertOneMentioning(List<String> problems, String... ids) {
        assertEquals(1, problems.size(), problems.toString());
        for (String id : ids) {
            assertTrue(problems.get(0).contains(id), problems.get(0) + " does not name " + id);
        }
    }

    @Test
    void cleanSetsHaveNoProblems() {
        assertEquals(List.of(), check(NODES, SKILLS, cleanOptions()));
    }

    @Test
    void theFullEnumsAgreeWithThemselves() {
        List<String> skills = Arrays.stream(SkillId.values()).map(SkillId::id).toList();
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (ForkOption o : ForkOption.values()) {
            options.computeIfAbsent(o.parent().id(), k -> new ArrayList<>()).add(o.id());
        }
        List<String> nodes = new ArrayList<>(skills);
        nodes.add("start");
        assertEquals(List.of(), TreeIds.problems(nodes, skills, options));
    }

    @Test
    void aLayoutSkillWithNoConstants() {
        assertOneMentioning(check(NODES, List.of("alpha", "beta", "fork", "gamma"), cleanOptions()), "gamma",
                "no constants");
    }

    @Test
    void aConstantWithNoNode() {
        assertOneMentioning(check(NODES, List.of("alpha", "fork"), cleanOptions()), "beta", "no node");
    }

    @Test
    void twoOffendersOfOneKindAreBothReported() {
        List<String> problems = check(NODES, List.of("fork"), cleanOptions());
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("alpha") || problems.get(1).contains("alpha"));
        assertTrue(problems.get(0).contains("beta") || problems.get(1).contains("beta"));
    }

    @Test
    void aReservedNodeId() {
        List<String> nodes = new ArrayList<>(NODES);
        nodes.add("motherlode");
        assertOneMentioning(check(nodes, SKILLS, cleanOptions()), "motherlode", "reserved");
    }

    @Test
    void aReservedSkillId() {
        List<String> problems = check(NODES, List.of("alpha", "beta", "fork", "ore_sense"), cleanOptions());
        assertTrue(problems.stream().anyMatch(p -> p.contains("ore_sense") && p.contains("reserved")),
                problems.toString());
    }

    @Test
    void aReservedOptionId() {
        Map<String, List<String>> options = cleanOptions();
        options.put("fork", List.of("one", "two", "efficient_miner"));
        List<String> problems = check(NODES, SKILLS, options);
        assertTrue(problems.stream().anyMatch(p -> p.contains("efficient_miner") && p.contains("reserved")),
                problems.toString());
    }

    @Test
    void aForkParentJavaDoesNotKnow() {
        Map<String, List<String>> options = cleanOptions();
        options.put("beta", List.of());
        assertOneMentioning(check(NODES, SKILLS, options), "beta", "not a fork parent");
    }

    @Test
    void anUnknownOption() {
        Map<String, List<String>> options = cleanOptions();
        options.put("fork", List.of("one", "two", "three"));
        assertOneMentioning(check(NODES, SKILLS, options), "three", "unknown");
    }

    @Test
    void anOptionUnderTheWrongParent() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("fork", List.of("one"));
        options.put("alpha", List.of("two"));
        List<String> problems = check(NODES, SKILLS, options);
        assertTrue(problems.stream().anyMatch(p -> p.contains("two") && p.contains("belongs to fork")),
                problems.toString());
    }

    @Test
    void anOptionMissingFromTheLayout() {
        Map<String, List<String>> options = cleanOptions();
        options.put("fork", List.of("one"));
        assertOneMentioning(check(NODES, SKILLS, options), "two", "missing from the layout");
    }

    @Test
    void twoMissingOptionsAreBothReported() {
        assertEquals(2, check(NODES, SKILLS, Map.of()).size());
    }

    @Test
    void anOptionEqualToASkillOrNodeId() {
        Set<String> knownSkills = Set.of("alpha", "beta", "fork", "one");
        List<String> problems = TreeIds.problems(List.of("start", "alpha", "beta_1", "fork_1", "one"),
                List.of("alpha", "beta", "fork", "one"), cleanOptions(), knownSkills, KNOWN_OPTIONS);
        assertTrue(problems.stream().anyMatch(p -> p.contains("one") && p.contains("equals a skill or node id")),
                problems.toString());
    }
}
