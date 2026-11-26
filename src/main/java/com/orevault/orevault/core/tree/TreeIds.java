package com.orevault.orevault.core.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The agreement check between the layout file's ids and Java's (§11 Tree Layout Data; old #135, "ids resolve"). The layout
 * is the one source of nodes; Java holds the skill and option ids effects and saves are keyed by. If the two drift, a
 * skill has a node with no effect or an effect with no node, so this reports every offender, not only the first.
 *
 * <p>It takes plain collections so it needs no layout reader. At #208 it is test-only. Running it over the shipped
 * {@code resonance.json}, and a start-time call so a mismatched jar fails at start, are owed to #239: the provisional
 * file at #212 lacks most skills and would fail every dev run (Manager ruling, #208).
 */
public final class TreeIds {
    private TreeIds() {
    }

    /**
     * Problems between the layout's ids and {@link SkillId} and {@link ForkOption}; empty when they agree.
     *
     * @param nodeIds every node id in the layout, the start included
     * @param skillIds every distinct {@code skill} in the layout
     * @param optionsByParent each fork's parent skill id to its option ids, from the layout's {@code forks}
     */
    public static List<String> problems(Collection<String> nodeIds, Collection<String> skillIds,
            Map<String, ? extends Collection<String>> optionsByParent) {
        Set<String> knownSkills = new HashSet<>();
        for (SkillId s : SkillId.values()) {
            knownSkills.add(s.id());
        }
        Map<String, String> knownOptions = new LinkedHashMap<>();
        for (ForkOption o : ForkOption.values()) {
            knownOptions.put(o.id(), o.parent().id());
        }
        return problems(nodeIds, skillIds, optionsByParent, knownSkills, knownOptions);
    }

    /** As the public method, against given known skill ids and option-to-parent ids, so tests can use small sets. */
    static List<String> problems(Collection<String> nodeIds, Collection<String> skillIds,
            Map<String, ? extends Collection<String>> optionsByParent, Collection<String> knownSkillIds,
            Map<String, String> knownOptionParents) {
        List<String> out = new ArrayList<>();
        Set<String> layoutSkills = new HashSet<>(skillIds);
        for (String skill : skillIds) {
            if (ReservedIds.isReserved(skill)) {
                out.add("skill id " + skill + " is reserved (§6.1)");
            } else if (!knownSkillIds.contains(skill)) {
                out.add("layout skill " + skill + " has no constants (no SkillId)");
            }
        }
        for (String known : knownSkillIds) {
            if (!layoutSkills.contains(known)) {
                out.add("SkillId " + known + " has no node in the layout");
            }
        }
        for (String node : nodeIds) {
            if (ReservedIds.isReserved(node)) {
                out.add("node id " + node + " is reserved (§6.1)");
            }
        }
        Set<String> parentsInJava = new HashSet<>(knownOptionParents.values());
        Set<String> nodeAndSkillIds = new HashSet<>(nodeIds);
        nodeAndSkillIds.addAll(skillIds);
        Set<String> layoutOptions = new HashSet<>();
        for (Map.Entry<String, ? extends Collection<String>> fork : optionsByParent.entrySet()) {
            String parent = fork.getKey();
            if (!parentsInJava.contains(parent)) {
                out.add("fork " + parent + " is not a fork parent of any ForkOption");
            }
            for (String option : fork.getValue()) {
                layoutOptions.add(option);
                if (nodeAndSkillIds.contains(option)) {
                    out.add("option id " + option + " equals a skill or node id");
                }
                String javaParent = knownOptionParents.get(option);
                if (ReservedIds.isReserved(option)) {
                    out.add("option id " + option + " is reserved (§6.1)");
                } else if (javaParent == null) {
                    out.add("option " + option + " under fork " + parent + " is unknown (no ForkOption)");
                } else if (!javaParent.equals(parent)) {
                    out.add("option " + option + " is under fork " + parent + " but belongs to fork " + javaParent);
                }
            }
        }
        for (Map.Entry<String, String> known : knownOptionParents.entrySet()) {
            if (!layoutOptions.contains(known.getKey())) {
                out.add("ForkOption " + known.getKey() + " of fork " + known.getValue()
                        + " is missing from the layout");
            }
        }
        return List.copyOf(out);
    }
}
