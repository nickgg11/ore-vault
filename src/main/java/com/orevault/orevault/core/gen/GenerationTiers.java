package com.orevault.orevault.core.gen;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tiers of a team's owned generation nodes, as the snapshot builder hands them to {@link ClipFactors#of} (ADR 0003,
 * ADR 0009 "Counts per rarity, and the floor clip"; Architect, ARCH-E4-16). No generation node exists before E9, so every
 * caller passes {@link #NONE} today; the shape is here now so that E9-S3 fills it without changing every caller. Memory
 * only, like the snapshot: never persisted, so it carries no version.
 *
 * @param tiers node id to owned tier, dormant mod-hidden nodes left out (§6); sorted, so iteration never depends on a hash
 */
public record GenerationTiers(Map<String, Integer> tiers) {
    /** No generation node owned: the baseline. */
    public static final GenerationTiers NONE = new GenerationTiers(Map.of());

    public GenerationTiers {
        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 1) {
                throw new IllegalArgumentException("Node " + entry.getKey() + " at tier " + entry.getValue());
            }
        }
        tiers = Collections.unmodifiableMap(new TreeMap<>(tiers));
    }

    public boolean isEmpty() {
        return tiers.isEmpty();
    }
}
