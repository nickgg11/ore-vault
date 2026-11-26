package com.orevault.orevault.core.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One constant per fork option of §6.1 Forks: a petal, not a node, which decides what its parent's tiers do. Petals and
 * their positions are layout ({@code forks[]} in {@code docs/tree-layout-format.md}); Java holds what effect code and
 * saves need: the option id, its parent skill and its tier count, the rows of its §6.1 table.
 *
 * <p>The ids are frozen from #208: the save's {@code fork_choice} maps a parent's skill id to one of these (ADR 0005),
 * and {@code ChooseForkOption} and {@code Forks} carry them as strings (ADR 0006). They follow {@link SkillId}'s
 * derivation from the §6.1 heading.
 *
 * <p><b>Never persist or send {@link #ordinal()} or {@link #name()}.</b> Saves and the wire use {@link #id()} only.
 */
public enum ForkOption {
    ABUNDANCE("abundance", SkillId.VEIN_SHAPING, 2),
    VEIN_SINGULARITY("vein_singularity", SkillId.VEIN_SHAPING, 2),
    STRATIFIED("stratified", SkillId.VEIN_SHAPING, 2),
    COMMON_FOCUS("common_focus", SkillId.ORE_ATTUNEMENT, 3),
    UNCOMMON_FOCUS("uncommon_focus", SkillId.ORE_ATTUNEMENT, 3),
    RARE_FOCUS("rare_focus", SkillId.ORE_ATTUNEMENT, 3),
    /** Six tiers: Ore Working's nodes 4 to 6 are shown only under this option ({@code forOption}, §6.1 Ore Working). */
    ORE_DOUBLING("ore_doubling", SkillId.ORE_WORKING, 6),
    SMELTERS_INTUITION("smelters_intuition", SkillId.ORE_WORKING, 3),
    RESONANCE_MAGNETISM("resonance_magnetism", SkillId.RESONANT_DRAW, 3),
    HOARDERS_INSTINCT("hoarders_instinct", SkillId.RESONANT_DRAW, 3);

    private static final Map<String, ForkOption> BY_ID = new HashMap<>();

    static {
        for (ForkOption o : values()) {
            BY_ID.put(o.id, o);
        }
    }

    private final String id;
    private final SkillId parent;
    private final int tiers;

    ForkOption(String id, SkillId parent, int tiers) {
        this.id = id;
        this.parent = parent;
        this.tiers = tiers;
    }

    /** The saved and wired form. */
    public String id() {
        return id;
    }

    /** The fork parent skill; its id is also the fork's id (the key of {@code fork_choice}). */
    public SkillId parent() {
        return parent;
    }

    /** Rows in this option's §6.1 table: the parent nodes it must cover ({@link ForkCoverage}). */
    public int tiers() {
        return tiers;
    }

    /** The option whose {@link #id()} is exactly {@code id}; empty otherwise. */
    public static Optional<ForkOption> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** The options of {@code parent} in declaration order; empty for a skill that is not a fork parent. */
    public static List<ForkOption> of(SkillId parent) {
        List<ForkOption> out = new ArrayList<>();
        for (ForkOption o : values()) {
            if (o.parent == parent) {
                out.add(o);
            }
        }
        return List.copyOf(out);
    }
}
