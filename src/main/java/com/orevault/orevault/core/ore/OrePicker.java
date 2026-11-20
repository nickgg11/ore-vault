package com.orevault.orevault.core.ore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which ore a vein is, once its rarity and its layer are known, for one band of one dimension type (§3.1 "Where each
 * ore sits"; ADR 0009 "Which ore a vein is"; Nick, question 4, 2026-10-31: "as written"). The draw is in proportion to
 * each ore's weight times its mapped height profile in the layer's 16-layer slab. Where no ore of the rarity has mass in
 * that slab, the nearest slab in the band that has some is used, the lower on a tie. Nothing reads the band-wide mix on
 * its own.
 *
 * <p>Where a pack has no ore of a rarity at all, that rarity's veins keep their count and size and take their ore from
 * the nearest rarity that has ores, so density does not change (Epic 4 triage ruling 5; #234 "density stays right in a
 * pack with no rare ore"). Resonance per ore drops, about 15% with no rare ore.
 *
 * <p>Built once on the main thread; immutable afterwards, so the generator's worker threads only read it. Every
 * fallback is resolved here, and a pick is one binary search over one {@code nextDouble}, so a pack with forty ores moves
 * which ore a vein is but never where any vein is (ADR 0003). Candidates are sorted by group id, so the table does not
 * depend on the order the classifier met them in.
 */
public final class OrePicker {
    /**
     * One ore the pick can return.
     *
     * @param groupId the stone-band block's id, the string every record stores for the ore (ADR 0009 "The ore group's id")
     * @param rarity its §4.2 class
     * @param weight expected blocks a chunk in the overworld times its features' biome share (ADR 0009)
     * @param profile where it sits in the overworld
     */
    public record Candidate(String groupId, Rarity rarity, double weight, HeightProfile profile) {
        public Candidate {
            Objects.requireNonNull(groupId);
            Objects.requireNonNull(rarity);
            Objects.requireNonNull(profile);
            if (!(weight > 0)) {
                throw new IllegalArgumentException(groupId + " has weight " + weight);
            }
        }
    }

    private static final Map<Rarity, List<Rarity>> NEAREST = Map.of(
            Rarity.COMMON, List.of(Rarity.UNCOMMON, Rarity.RARE),
            Rarity.UNCOMMON, List.of(Rarity.COMMON, Rarity.RARE),
            Rarity.RARE, List.of(Rarity.UNCOMMON, Rarity.COMMON));

    private final int bandFloorY;
    private final int bandTopY;
    /** Per rarity, per slab: the ore ids and their cumulative weights, fallbacks already resolved. */
    private final Map<Rarity, Slab[]> slabs;

    private record Slab(String[] ids, double[] cumulative) {
    }

    /**
     * @param candidates the classified ores
     * @param columnFloorY the Vault column the overworld maps onto, its floor (Y-63)
     * @param columnTopY its top (Y=245)
     * @param bandFloorY this band's floor
     * @param bandTopY this band's top
     * @param foldBelow whether mass below the band counts in its lowest slab (the base type's stone band)
     */
    public OrePicker(List<Candidate> candidates, int columnFloorY, int columnTopY, int bandFloorY, int bandTopY, boolean foldBelow) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No ore to pick from: a Vault would generate empty");
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Candidate::groupId));
        this.bandFloorY = bandFloorY;
        this.bandTopY = bandTopY;
        int slabCount = HeightProfile.slabOf(bandTopY, bandFloorY, bandTopY) + 1;

        Map<Rarity, Slab[]> own = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            List<Candidate> ofRarity = sorted.stream().filter(c -> c.rarity() == rarity).toList();
            if (ofRarity.isEmpty()) {
                continue;
            }
            double[][] mass = new double[ofRarity.size()][];
            for (int i = 0; i < ofRarity.size(); i++) {
                Candidate c = ofRarity.get(i);
                mass[i] = c.profile().slabs(columnFloorY, columnTopY, bandFloorY, bandTopY, foldBelow);
                for (int s = 0; s < slabCount; s++) {
                    mass[i][s] *= c.weight();
                }
            }
            own.put(rarity, resolveSlabs(ofRarity, mass, slabCount));
        }
        Map<Rarity, Slab[]> resolved = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            Slab[] table = own.get(rarity);
            if (table == null) {
                for (Rarity nearest : NEAREST.get(rarity)) {
                    table = own.get(nearest);
                    if (table != null) {
                        break;
                    }
                }
            }
            resolved.put(rarity, table);
        }
        this.slabs = resolved;
    }

    /** Whether the pack has any ore of this rarity of its own, for the classifier's warning. */
    public static boolean hasRarity(List<Candidate> candidates, Rarity rarity) {
        return candidates.stream().anyMatch(c -> c.rarity() == rarity);
    }

    /**
     * The ore of a vein of {@code rarity} whose layer is {@code layerY}, from one uniform draw in [0, 1).
     */
    public String pick(Rarity rarity, int layerY, double draw) {
        Slab slab = slabs.get(rarity)[HeightProfile.slabOf(layerY, bandFloorY, bandTopY)];
        double target = draw * slab.cumulative()[slab.cumulative().length - 1];
        int low = 0;
        int high = slab.cumulative().length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (slab.cumulative()[mid] > target) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return slab.ids()[low];
    }

    /** Each slab's own table, or the nearest slab's with mass, the lower on a tie. */
    private static Slab[] resolveSlabs(List<Candidate> ores, double[][] mass, int slabCount) {
        Slab[] own = new Slab[slabCount];
        for (int s = 0; s < slabCount; s++) {
            List<String> ids = new ArrayList<>();
            List<Double> cumulative = new ArrayList<>();
            double sum = 0;
            for (int i = 0; i < ores.size(); i++) {
                if (mass[i][s] > 0) {
                    sum += mass[i][s];
                    ids.add(ores.get(i).groupId());
                    cumulative.add(sum);
                }
            }
            if (!ids.isEmpty()) {
                own[s] = new Slab(ids.toArray(String[]::new), cumulative.stream().mapToDouble(Double::doubleValue).toArray());
            }
        }
        Slab[] resolved = new Slab[slabCount];
        for (int s = 0; s < slabCount; s++) {
            for (int distance = 0; distance < slabCount && resolved[s] == null; distance++) {
                if (s - distance >= 0 && own[s - distance] != null) {
                    resolved[s] = own[s - distance];
                } else if (s + distance < slabCount && own[s + distance] != null) {
                    resolved[s] = own[s + distance];
                }
            }
            if (resolved[s] == null) {
                // No ore of this rarity maps into the band at all: every slab draws by weight alone.
                double sum = 0;
                double[] cumulative = new double[ores.size()];
                for (int i = 0; i < ores.size(); i++) {
                    sum += ores.get(i).weight();
                    cumulative[i] = sum;
                }
                resolved[s] = new Slab(ores.stream().map(Candidate::groupId).toArray(String[]::new), cumulative);
            }
        }
        return resolved;
    }
}
