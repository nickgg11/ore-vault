package com.orevault.orevault.core.ore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which ore a vein is and where its middle sits, once its rarity is known, for one band of one dimension type (§3.1
 * "Where each ore sits"; ADR 0009, amended 2026-11-21: each ore picks its own height, Nick after his playtest of #422).
 * A vein's ore is drawn first, from the rarity's ores in proportion to each ore's weight times its profile mass inside
 * the band, read at the overworld's own Y. Its box middle layer is drawn second, from that ore's profile inside the band,
 * by inverse cumulative mass per layer. An ore with no mass in a band is never picked there. Where no ore of the rarity has
 * mass in the band, though the pack has some, the ore is drawn by weight alone and its middle evenly through the band.
 *
 * <p>A request over part of a band (Deep Veins' lowest 30 layers, Stratified's bands, Guide Vein's veins; {@code VeinRequest})
 * follows the same rule inside its own region: the ore by weight times its mass inside the region, the middle from that
 * ore's profile cut to the region and renormalised, an ore with no mass there drawing its middle evenly in the region
 * (Manager, 2026-11-21). {@link #table(Rarity, int, int)} builds that table; the band-wide ones are built once here.
 *
 * <p>Where a pack has no ore of a rarity at all, that rarity's veins keep their count and size and take their ore from
 * the nearest rarity that has ores, so density does not change (Epic 4 triage ruling 5; #234 "density stays right in a
 * pack with no rare ore"). Resonance per ore drops, about 15% with no rare ore.
 *
 * <p>Built once on the main thread; immutable afterwards, so the generator's worker threads only read it. A pick is two
 * binary searches over two {@code nextDouble}s the placer always draws, so the same pack and the same chunk always give the
 * same veins. Candidates are sorted by group id, so the tables do not depend on the order the classifier met them in.
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
    /** Per rarity, the ores its veins draw from, the nearest rarity's for a rarity the pack lacks; sorted by group id. */
    private final Map<Rarity, List<Candidate>> pools;
    /** Per rarity, the band-wide table. */
    private final Map<Rarity, Table> bandTables;

    /**
     * @param candidates the classified ores
     * @param bandFloorY this band's floor
     * @param bandTopY this band's top
     */
    public OrePicker(List<Candidate> candidates, int bandFloorY, int bandTopY) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No ore to pick from: a Vault would generate empty");
        }
        if (bandTopY < bandFloorY) {
            throw new IllegalArgumentException("An empty band Y" + bandFloorY + ".." + bandTopY);
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Candidate::groupId));
        this.bandFloorY = bandFloorY;
        this.bandTopY = bandTopY;

        Map<Rarity, List<Candidate>> own = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            List<Candidate> ofRarity = sorted.stream().filter(c -> c.rarity() == rarity).toList();
            if (!ofRarity.isEmpty()) {
                own.put(rarity, ofRarity);
            }
        }
        Map<Rarity, List<Candidate>> resolved = new EnumMap<>(Rarity.class);
        Map<Rarity, Table> tables = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            List<Candidate> pool = own.get(rarity);
            if (pool == null) {
                for (Rarity nearest : NEAREST.get(rarity)) {
                    pool = own.get(nearest);
                    if (pool != null) {
                        break;
                    }
                }
            }
            List<Candidate> resolvedPool = Objects.requireNonNull(pool);
            resolved.put(rarity, resolvedPool);
            tables.put(rarity, new Table(resolvedPool, bandFloorY, bandTopY));
        }
        this.pools = Collections.unmodifiableMap(resolved);
        this.bandTables = Collections.unmodifiableMap(tables);
    }

    /** Whether the pack has any ore of this rarity of its own, for the classifier's warning. */
    public static boolean hasRarity(List<Candidate> candidates, Rarity rarity) {
        return candidates.stream().anyMatch(c -> c.rarity() == rarity);
    }

    public int bandFloorY() {
        return bandFloorY;
    }

    public int bandTopY() {
        return bandTopY;
    }

    /** The band-wide table of a rarity: the baseline's veins and every request spread through the whole band. */
    public Table table(Rarity rarity) {
        return bandTables.get(rarity);
    }

    /**
     * The table of a request over {@code regionBottomY..regionTopY}, inside the band. The band-wide table when the region
     * is the band; otherwise built here, so a caller that rolls many veins of one request builds it once.
     *
     * @throws IllegalArgumentException for a region outside the band
     */
    public Table table(Rarity rarity, int regionBottomY, int regionTopY) {
        if (regionBottomY < bandFloorY || regionTopY > bandTopY || regionTopY < regionBottomY) {
            throw new IllegalArgumentException("Region Y" + regionBottomY + ".." + regionTopY + " is not inside the band Y"
                    + bandFloorY + ".." + bandTopY);
        }
        if (regionBottomY == bandFloorY && regionTopY == bandTopY) {
            return table(rarity);
        }
        return new Table(pools.get(rarity), regionBottomY, regionTopY);
    }

    /**
     * One rarity's draw over one region: the ores it can return with their pick shares, and per ore the distribution of a
     * vein's box middle over the region's layers. Immutable. The shares and the middle distributions are what the edge
     * factor, the floor clip and the density check's expected slabs read, so they and the placer read one source (§3.1 "How
     * the baseline is checked").
     */
    public static final class Table {
        private final int regionBottomY;
        private final int regionTopY;
        private final String[] ids;
        /** Cumulative pick weight, by ore. */
        private final double[] oreCumulative;
        /** Per ore, the lowest layer with mass, as an offset from {@code regionBottomY}. */
        private final int[] firstLayer;
        /** Per ore, the cumulative middle mass from its first layer to its last layer with mass. */
        private final double[][] middleCumulative;

        Table(List<Candidate> pool, int regionBottomY, int regionTopY) {
            this.regionBottomY = regionBottomY;
            this.regionTopY = regionTopY;
            int layers = regionTopY - regionBottomY + 1;
            List<double[]> masses = new ArrayList<>(pool.size());
            double total = 0;
            for (Candidate candidate : pool) {
                double[] mass = candidate.profile().layers(regionBottomY, regionTopY);
                masses.add(mass);
                total += candidate.weight() * sum(mass);
            }
            List<String> chosen = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            List<double[]> middles = new ArrayList<>();
            for (int i = 0; i < pool.size(); i++) {
                double[] mass = masses.get(i);
                double inRegion = sum(mass);
                if (total > 0 && inRegion > 0) {
                    chosen.add(pool.get(i).groupId());
                    weights.add(pool.get(i).weight() * inRegion);
                    middles.add(mass);
                } else if (total <= 0) {
                    // No ore of the rarity has mass here: by weight alone, the middle evenly through the region.
                    double[] even = new double[layers];
                    Arrays.fill(even, 1.0);
                    chosen.add(pool.get(i).groupId());
                    weights.add(pool.get(i).weight());
                    middles.add(even);
                }
            }
            this.ids = chosen.toArray(String[]::new);
            this.oreCumulative = new double[ids.length];
            this.firstLayer = new int[ids.length];
            this.middleCumulative = new double[ids.length][];
            double running = 0;
            for (int i = 0; i < ids.length; i++) {
                running += weights.get(i);
                oreCumulative[i] = running;
                double[] mass = middles.get(i);
                int first = 0;
                while (mass[first] <= 0) {
                    first++;
                }
                int last = mass.length - 1;
                while (mass[last] <= 0) {
                    last--;
                }
                double[] cumulative = new double[last - first + 1];
                double sum = 0;
                for (int layer = first; layer <= last; layer++) {
                    sum += mass[layer];
                    cumulative[layer - first] = sum;
                }
                firstLayer[i] = first;
                middleCumulative[i] = cumulative;
            }
        }

        public int regionBottomY() {
            return regionBottomY;
        }

        public int regionTopY() {
            return regionTopY;
        }

        /** How many ores the draw can return. */
        public int size() {
            return ids.length;
        }

        /** The group id of the {@code ore}th ore, in group-id order. */
        public String id(int ore) {
            return ids[ore];
        }

        /** The probability one vein is this ore. */
        public double share(int ore) {
            double total = oreCumulative[oreCumulative.length - 1];
            return (oreCumulative[ore] - (ore == 0 ? 0 : oreCumulative[ore - 1])) / total;
        }

        /**
         * The probability a vein of this ore has its box middle at each layer of the region, index 0 at
         * {@link #regionBottomY()}; sums to 1.
         */
        public double[] middle(int ore) {
            double[] cumulative = middleCumulative[ore];
            double total = cumulative[cumulative.length - 1];
            double[] middle = new double[regionTopY - regionBottomY + 1];
            for (int i = 0; i < cumulative.length; i++) {
                middle[firstLayer[ore] + i] = (cumulative[i] - (i == 0 ? 0 : cumulative[i - 1])) / total;
            }
            return middle;
        }

        /** The ore of one vein, from one uniform draw in [0, 1). */
        public int pickOre(double draw) {
            return search(oreCumulative, draw);
        }

        /** The box middle layer of one vein of the {@code ore}th ore, from one uniform draw in [0, 1). */
        public int pickMiddle(int ore, double draw) {
            return regionBottomY + firstLayer[ore] + search(middleCumulative[ore], draw);
        }

        /**
         * The first index whose cumulative value is above {@code draw} times the total. Both ends of every cumulative array
         * hold mass, so the last index is a valid answer when rounding puts the target on the total.
         */
        private static int search(double[] cumulative, double draw) {
            double target = draw * cumulative[cumulative.length - 1];
            int low = 0;
            int high = cumulative.length - 1;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (cumulative[mid] > target) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            return low;
        }

        private static double sum(double[] values) {
            double sum = 0;
            for (double value : values) {
                sum += value;
            }
            return sum;
        }
    }
}
