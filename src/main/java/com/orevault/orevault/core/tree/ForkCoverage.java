package com.orevault.orevault.core.tree;

import java.util.List;
import java.util.Optional;

/**
 * §6.1 Forks: "an option must cover every tier its parent has". A parent tier with nothing behind it in the chosen option
 * is a paid step that does nothing, which is the gap Hoarder's Instinct once had (old #102).
 *
 * <p>The parent nodes an option must cover are counted by each node's layout {@code forOption}, never by what is visible
 * at runtime: a node with no {@code forOption} counts for every option, one with {@code forOption} counts only for that
 * option. So Ore Working's tag-gated nodes 4 to 6, which carry {@code forOption: ore_doubling}, make Ore Doubling cover
 * six and Smelter's Intuition three, and empty Mekanism tags on a server cannot break the check. The sanctioned exception
 * falls out of {@code forOption}; it is not a special case here. The layout test that only {@code ore_working} nodes carry
 * {@code forOption} is #212's.
 */
public final class ForkCoverage {
    private ForkCoverage() {
    }

    /**
     * How many of a parent's nodes count for {@code option}, given each parent node's layout {@code forOption} option id
     * (empty when the node has none). Counts by layout, not by runtime visibility.
     */
    public static int parentNodesForOption(ForkOption option, List<Optional<String>> forOptionOfEachParentNode) {
        int count = 0;
        for (Optional<String> forOption : forOptionOfEachParentNode) {
            if (forOption.isEmpty() || forOption.get().equals(option.id())) {
                count++;
            }
        }
        return count;
    }

    /**
     * One message when {@code option}'s tier count is not exactly {@code parentNodesForOption}, the parent nodes counted
     * for it by layout {@code forOption} ({@link #parentNodesForOption}); empty when they are equal. Equality, not "at
     * least": an option with more tiers than nodes has rows no team can reach.
     */
    public static List<String> gaps(ForkOption option, int parentNodesForOption) {
        if (option.tiers() == parentNodesForOption) {
            return List.of();
        }
        return List.of("fork option " + option.id() + " has " + option.tiers() + " tiers but its parent "
                + option.parent().id() + " has " + parentNodesForOption + " nodes for it (§6.1 Forks)");
    }
}
