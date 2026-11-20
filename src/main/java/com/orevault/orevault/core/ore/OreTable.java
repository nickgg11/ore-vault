package com.orevault.orevault.core.ore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The session's ore classification (§4.2, §11 Ore Rarity Classification, ADR 0009 steps 3, 4, 8 and 9): which blocks are
 * ores, each ore's rarity, where it sits and how much it weighs in the identity pick. Built once on the main thread at
 * server start from the adapter's {@link FeatureSample}s and never changed, so it can be published to the generator's
 * worker threads through one {@code volatile} write (ADR 0009 "Publication"; old PR #153). Entries are sorted by group id
 * and every sum runs over features sorted by id, so the table, and the pick built from it, do not depend on the order the
 * adapter met biomes or features in.
 *
 * <p>{@link #lookup} answers with the ore's entry or {@code null}: there is no default rarity. Stone, deepslate, gravel,
 * clay and a {@code #c:ores} block no overworld feature places have none, which is old PR #117's fix made the shape of
 * the API, so the stone-pays-5 bug cannot come back through a default.
 */
public final class OreTable {
    /**
     * One classified ore.
     *
     * @param groupId the stone-band block's id, the string every record stores for the ore (ADR 0009 "The ore group's id")
     * @param rarity its class, after any override
     * @param classifiedAs what §4.2's rule gave it, or {@code null} for an ore only an override names
     * @param stoneBlock what the stone band places
     * @param deepslateBlock what the deepslate band places; the stone block for a single-variant ore
     * @param blocks every block of the group, sorted
     * @param features the placed features step 3 used for it, sorted
     * @param expectedBlocks expected blocks a chunk in the overworld over those features (step 8)
     * @param meanBlocksPerVein those blocks over their veins; 0 with no feature
     * @param weight expected blocks with each biome-only feature at its biome share, the identity pick's weight
     * @param profile where it sits in the overworld, block-weighted
     */
    public record Entry(String groupId, Rarity rarity, @Nullable Rarity classifiedAs, String stoneBlock,
            String deepslateBlock, List<String> blocks, List<String> features, double expectedBlocks,
            double meanBlocksPerVein, double weight, HeightProfile profile) {
        public Entry {
            Objects.requireNonNull(groupId);
            Objects.requireNonNull(rarity);
            Objects.requireNonNull(profile);
            blocks = List.copyOf(blocks);
            features = List.copyOf(features);
        }
    }

    private final List<Entry> entries;
    private final Map<String, Entry> byBlock;
    private final List<String> unclassified;
    private final List<String> notes;
    private final List<OrePicker.Candidate> candidates;

    private OreTable(List<Entry> entries, List<String> unclassified, List<String> notes) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Entry::groupId));
        this.entries = List.copyOf(sorted);
        Map<String, Entry> index = new HashMap<>();
        for (Entry entry : this.entries) {
            for (String block : entry.blocks()) {
                index.put(block, entry);
            }
        }
        this.byBlock = Map.copyOf(index);
        this.unclassified = List.copyOf(unclassified);
        this.notes = List.copyOf(notes);
        this.candidates = this.entries.stream()
                .map(entry -> new OrePicker.Candidate(entry.groupId(), entry.rarity(), entry.weight(), entry.profile()))
                .toList();
    }

    /**
     * Steps 3, 4, 8 and 9, then the overrides.
     *
     * @param samples one per placed feature, in any order
     * @param oreTag the block ids in {@code #c:ores}; a target outside it is not an ore
     * @param overrides the admin's, already validated against the same tag
     * @param sourceFloorY the overworld floor every sample's origin mass starts at
     * @param sourceLayers the overworld's height, every sample's origin mass length
     */
    public static OreTable build(List<FeatureSample> samples, Set<String> oreTag, Overrides overrides, int sourceFloorY,
            int sourceLayers) {
        List<FeatureSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparing(FeatureSample::featureId));
        for (int i = 0; i < sorted.size(); i++) {
            FeatureSample sample = sorted.get(i);
            if (i > 0 && sorted.get(i - 1).featureId().equals(sample.featureId())) {
                throw new IllegalArgumentException("Feature " + sample.featureId() + " sampled twice");
            }
            if (sample.sourceFloorY() != sourceFloorY || sample.originMass().length != sourceLayers) {
                throw new IllegalArgumentException(sample.featureId() + " was sampled over another overworld range");
            }
        }

        // Step 4: blocks targeted by the same feature are one ore. Union-find over the tag's blocks.
        Map<String, String> parent = new TreeMap<>();
        Map<String, List<FeatureSample>> featuresByRoot = new TreeMap<>();
        List<FeatureSample> usable = new ArrayList<>();
        for (FeatureSample sample : sorted) {
            List<String> ores = oreTargets(sample, oreTag);
            if (sample.expectedBlocks() <= 0 || ores.isEmpty()) {
                continue;
            }
            usable.add(sample);
            for (String block : ores) {
                parent.putIfAbsent(block, block);
                union(parent, ores.get(0), block);
            }
        }
        for (FeatureSample sample : usable) {
            featuresByRoot.computeIfAbsent(find(parent, oreTargets(sample, oreTag).get(0)), k -> new ArrayList<>()).add(sample);
        }
        Map<String, SortedSet<String>> members = new TreeMap<>();
        for (String block : parent.keySet()) {
            members.computeIfAbsent(find(parent, block), k -> new TreeSet<>()).add(block);
        }

        List<Draft> drafts = new ArrayList<>();
        Map<String, Draft> draftByBlock = new HashMap<>();
        for (Map.Entry<String, List<FeatureSample>> group : featuresByRoot.entrySet()) {
            Draft draft = Draft.of(members.get(group.getKey()), group.getValue(), oreTag, sourceLayers);
            drafts.add(draft);
            draft.blocks.forEach(block -> draftByBlock.put(block, draft));
        }

        // Step 8's overrides, after classification. One override moves the whole group; two that disagree move nothing.
        List<String> notes = new ArrayList<>();
        Map<Draft, Set<Rarity>> wanted = new IdentityHashMap<>();
        Map<String, Rarity> withoutFeature = new TreeMap<>();
        for (Map.Entry<String, Rarity> override : overrides.byBlock().entrySet()) {
            Draft draft = draftByBlock.get(override.getKey());
            if (draft != null) {
                wanted.computeIfAbsent(draft, k -> EnumSet.noneOf(Rarity.class)).add(override.getValue());
            } else if (oreTag.contains(override.getKey())) {
                withoutFeature.put(override.getKey(), override.getValue());
            } else {
                notes.add("Override for " + override.getKey() + " names a block outside #c:ores; it does not apply");
            }
        }
        for (Draft draft : drafts) {
            Set<Rarity> rarities = wanted.get(draft);
            if (rarities == null) {
                continue;
            }
            if (rarities.size() == 1) {
                draft.rarity = rarities.iterator().next();
            } else {
                notes.add("Overrides give " + draft.groupId + " the rarities " + rarities + "; none applies and it stays "
                        + draft.classifiedAs);
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (Draft draft : drafts) {
            entries.add(draft.entry());
        }
        // An override naming an ore with no feature data: a flat profile and the median weight of its rarity (step 8).
        for (Map.Entry<String, Rarity> override : withoutFeature.entrySet()) {
            String block = override.getKey();
            entries.add(new Entry(block, override.getValue(), null, block, block, List.of(block), List.of(), 0, 0,
                    medianWeight(drafts, override.getValue()), HeightProfile.flat(sourceFloorY, sourceLayers)));
        }

        Set<String> classified = new TreeSet<>();
        entries.forEach(entry -> classified.addAll(entry.blocks()));
        List<String> unclassified = oreTag.stream().filter(block -> !classified.contains(block)).sorted().toList();
        return new OreTable(entries, unclassified, notes);
    }

    private static List<String> oreTargets(FeatureSample sample, Set<String> oreTag) {
        return sample.targets().stream().map(FeatureSample.Target::blockId).filter(oreTag::contains).distinct().sorted()
                .toList();
    }

    private static String find(Map<String, String> parent, String block) {
        String root = block;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        return root;
    }

    /** The lesser id becomes the root, so the result does not depend on the order of the unions. */
    private static void union(Map<String, String> parent, String a, String b) {
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (rootA.compareTo(rootB) < 0) {
            parent.put(rootB, rootA);
        } else if (rootB.compareTo(rootA) < 0) {
            parent.put(rootA, rootB);
        }
    }

    /** The median weight of the feature-backed ores of this rarity, else of all of them, else 1. */
    private static double medianWeight(List<Draft> drafts, Rarity rarity) {
        List<Double> weights = drafts.stream().filter(d -> d.rarity == rarity).map(d -> d.weight).sorted().toList();
        if (weights.isEmpty()) {
            weights = drafts.stream().map(d -> d.weight).sorted().toList();
        }
        if (weights.isEmpty()) {
            return 1.0;
        }
        int middle = weights.size() / 2;
        return weights.size() % 2 == 1 ? weights.get(middle) : (weights.get(middle - 1) + weights.get(middle)) / 2;
    }

    /** One group while overrides are applied. */
    private static final class Draft {
        private final String groupId;
        private final String deepslateBlock;
        private final List<String> blocks;
        private final List<String> features;
        private final double expectedBlocks;
        private final double meanBlocksPerVein;
        private final double weight;
        private final HeightProfile profile;
        private final Rarity classifiedAs;
        private Rarity rarity;

        private Draft(String groupId, String deepslateBlock, List<String> blocks, List<String> features,
                double expectedBlocks, double meanBlocksPerVein, double weight, HeightProfile profile) {
            this.groupId = groupId;
            this.deepslateBlock = deepslateBlock;
            this.blocks = blocks;
            this.features = features;
            this.expectedBlocks = expectedBlocks;
            this.meanBlocksPerVein = meanBlocksPerVein;
            this.weight = weight;
            this.profile = profile;
            this.classifiedAs = RarityRule.classify(expectedBlocks, meanBlocksPerVein);
            this.rarity = classifiedAs;
        }

        /** Step 3, then step 8's sums, over features already sorted by id. */
        static Draft of(SortedSet<String> members, List<FeatureSample> all, Set<String> oreTag, int sourceLayers) {
            List<FeatureSample> used = all.stream().filter(FeatureSample::allBiome).toList();
            if (used.isEmpty()) {
                used = all;
            }
            double blocks = 0;
            double veins = 0;
            double weight = 0;
            double[] mass = new double[sourceLayers];
            for (FeatureSample sample : used) {
                double expected = sample.expectedBlocks();
                blocks += expected;
                veins += sample.veinsPerChunk();
                weight += expected * (sample.allBiome() ? 1.0 : sample.biomeShare());
                double[] origins = sample.originMass();
                double total = 0;
                for (double origin : origins) {
                    total += origin;
                }
                for (int i = 0; i < sourceLayers; i++) {
                    mass[i] += expected * origins[i] / total;
                }
            }
            // The bands' blocks come from every feature of the group, used or not: a rule test says where a block goes.
            SortedSet<String> stone = new TreeSet<>();
            SortedSet<String> deepslate = new TreeSet<>();
            for (FeatureSample sample : all) {
                for (FeatureSample.Target target : sample.targets()) {
                    if (oreTag.contains(target.blockId()) && target.stoneBand()) {
                        stone.add(target.blockId());
                    }
                    if (oreTag.contains(target.blockId()) && target.deepslateBand()) {
                        deepslate.add(target.blockId());
                    }
                }
            }
            String groupId = !stone.isEmpty() ? stone.first() : !deepslate.isEmpty() ? deepslate.first() : members.first();
            String deepslateBlock = deepslate.isEmpty() ? groupId : deepslate.first();
            return new Draft(groupId, deepslateBlock, List.copyOf(members),
                    used.stream().map(FeatureSample::featureId).toList(), blocks, blocks / veins, weight,
                    new HeightProfile(used.get(0).sourceFloorY(), mass));
        }

        Entry entry() {
            return new Entry(groupId, rarity, classifiedAs, groupId, deepslateBlock, blocks, features, expectedBlocks,
                    meanBlocksPerVein, weight, profile);
        }
    }

    /**
     * Epic 4 triage ruling 6, as amended in round 2: a server overworld that leaves some rarity with no ore, or whose biome
     * source has a single biome (superflat, skyblock), is classified against the {@code minecraft:normal} preset's
     * overworld instead, which replaces this table.
     */
    public static boolean needsNormalPreset(OreTable classified, int possibleBiomes) {
        return possibleBiomes <= 1 || !classified.emptyRarities().isEmpty();
    }

    /** Every classified ore, sorted by group id. */
    public List<Entry> entries() {
        return entries;
    }

    /** The block's ore, either variant, or {@code null}: no default rarity (old PR #117). */
    public @Nullable Entry lookup(String blockId) {
        return byBlock.get(blockId);
    }

    /** The identity pick's input, sorted by group id (ADR 0009 "Which ore a vein is"). */
    public List<OrePicker.Candidate> candidates() {
        return candidates;
    }

    /** The rarities no ore has, for the classifier's WARN (triage ruling 5) and ruling 6's fallback. */
    public Set<Rarity> emptyRarities() {
        Set<Rarity> empty = EnumSet.allOf(Rarity.class);
        entries.forEach(entry -> empty.remove(entry.rarity()));
        return empty;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** {@code #c:ores} members with no entry, sorted: not placed, and pay nothing (step 9). */
    public List<String> unclassified() {
        return unclassified;
    }

    /** Overrides that could not apply, for a WARN each. */
    public List<String> notes() {
        return notes;
    }
}
