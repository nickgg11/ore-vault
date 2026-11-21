package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.HeightProfile;
import com.orevault.orevault.core.ore.Rarity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Placed ore in one Vault, counted by band, rarity, 16-layer slab and ore, and judged against S12 (§3.1 "How the baseline
 * is checked"; E4-S4, #254). The {@code /orevault density} command reads blocks the generator really wrote and hands the
 * counts here as plain numbers, so the arithmetic and the verdicts are unit-tested and the adapter stays a loop (ADR 0001;
 * Epic 4 triage, ARCH-E4-14). Removed at T14 with the command.
 *
 * <p>What each verdict is measured against:
 * <ul>
 *   <li>Band density, within 5% relative of {@link #expectedDensity}: {@link OreBaseline}'s closed form less the overlap
 *       between veins, {@code density(band, variant) × (1 − overlap)}. The overlap is the measured figure §3.1 gives under
 *       "Veins per chunk", about 1.7% of the stone band's blocks and 3.3% of deepslate's. The edge factor does not appear:
 *       the placer raises each rarity's count by it, and cutting the overhanging veins at the band's floor and top takes
 *       exactly as much back, so it cancels (ADR 0009 "Height"; S3 review ruling 3). It is printed so the line says so.
 *       §3.1's stated 3% and 6% are printed beside it, without a verdict of their own: the expected value is within 0.7%
 *       of each, far inside 5%.</li>
 *   <li>Rarity shares, by placed blocks and by the rarity of the ore placed, within 2 points of {@link OreBaseline#share}
 *       (68.0/25.0/7.0 stone, 59.7/30.2/10.1 deepslate). Overlap moves the placed mix toward the rarities placed later by
 *       under a point at the baseline (ADR 0009, amended for E4-S3), so the shares are not corrected for it.</li>
 *   <li>Every slab, within 10% relative of its band's measured density. Slabs are counted up from the band's floor with
 *       the partial top slab merged into the one below, through {@link HeightProfile#slabOf}, the same function the
 *       identity pick uses, so the command and the generator cannot disagree on a slab's bounds.</li>
 * </ul>
 * Identity shares (each ore's share of its rarity's placed blocks, per band) are printed with no verdict: S12 sets none,
 * and §3.1's identity figures are about veins and stated as "about".
 */
public final class DensityTally {
    /** §3.1 "Veins per chunk": blocks lost to overlap between veins, by the measured placement. */
    public static final double STONE_OVERLAP = 0.017;
    public static final double DEEPSLATE_OVERLAP = 0.033;
    /** S12. */
    public static final double DENSITY_TOLERANCE = 0.05;
    public static final double SHARE_TOLERANCE = 0.02;
    public static final double SLAB_TOLERANCE = 0.10;

    private static final int BLOCKS_PER_LAYER = 256;

    private final OreBaseline baseline;
    private final VaultVariant variant;
    private final int chunks;
    /** Per band the variant has: blocks by slab, then by rarity ordinal. */
    private final Map<Band, long[][]> counts = new EnumMap<>(Band.class);
    /** Per band and rarity: blocks by ore group id, sorted for a stable print. */
    private final Map<Band, Map<Rarity, Map<String, Long>>> identities = new EnumMap<>(Band.class);

    /**
     * @param baseline what the Vault was generated from
     * @param variant the Vault's dimension type, which fixes its bands
     * @param chunks how many whole chunks the counts come from
     */
    public DensityTally(OreBaseline baseline, VaultVariant variant, int chunks) {
        if (chunks < 1) {
            throw new IllegalArgumentException(chunks + " chunks");
        }
        this.baseline = baseline;
        this.variant = variant;
        this.chunks = chunks;
        for (Band band : Band.values()) {
            variant.band(band).ifPresent(bounds -> {
                counts.put(band, new long[slabCount(bounds)][Rarity.values().length]);
                Map<Rarity, Map<String, Long>> byRarity = new EnumMap<>(Rarity.class);
                for (Rarity rarity : Rarity.values()) {
                    byRarity.put(rarity, new TreeMap<>());
                }
                identities.put(band, byRarity);
            });
        }
    }

    /** The band a layer lies in for this dimension type, or empty for bedrock, the surface and the air. */
    public static Optional<Band> bandAt(VaultVariant variant, int y) {
        for (Band band : Band.values()) {
            Optional<BandBounds> bounds = variant.band(band);
            if (bounds.isPresent() && y >= bounds.get().bottomY() && y <= bounds.get().topY()) {
                return Optional.of(band);
            }
        }
        return Optional.empty();
    }

    /**
     * Counts ore found at one layer.
     *
     * @param groupId the ore group the block belongs to ({@code OreTable.Entry#groupId})
     * @param blocks how many of its blocks were found there
     * @throws IllegalArgumentException for a layer outside the band, or a band this type does not have
     */
    public void add(Band band, int layerY, Rarity rarity, String groupId, long blocks) {
        BandBounds bounds = bounds(band);
        if (layerY < bounds.bottomY() || layerY > bounds.topY()) {
            throw new IllegalArgumentException("Y=" + layerY + " is outside the " + name(band) + " band of " + variant.id());
        }
        counts.get(band)[HeightProfile.slabOf(layerY, bounds.bottomY(), bounds.topY())][rarity.ordinal()] += blocks;
        identities.get(band).get(rarity).merge(groupId, blocks, Long::sum);
    }

    /** The bands this type has, stone first. */
    public List<Band> bands() {
        return List.copyOf(counts.keySet());
    }

    public VaultVariant variant() {
        return variant;
    }

    public int chunks() {
        return chunks;
    }

    public long blocks(Band band) {
        long sum = 0;
        for (long[] slab : slabs(band)) {
            for (long count : slab) {
                sum += count;
            }
        }
        return sum;
    }

    public long blocks(Band band, Rarity rarity) {
        long sum = 0;
        for (long[] slab : slabs(band)) {
            sum += slab[rarity.ordinal()];
        }
        return sum;
    }

    /** Every block of the band over the chunks counted. */
    public long volume(Band band) {
        return (long) BLOCKS_PER_LAYER * chunks * bounds(band).layers();
    }

    public double density(Band band) {
        return blocks(band) / (double) volume(band);
    }

    public static double overlap(Band band) {
        return band == Band.STONE ? STONE_OVERLAP : DEEPSLATE_OVERLAP;
    }

    /** The closed form less the overlap; see the class comment. */
    public double expectedDensity(Band band) {
        return baseline.density(band, variant) * (1 - overlap(band));
    }

    /** The band's edge factor, the rarities' averaged by their closed-form blocks: printed, and cancelled by the cut. */
    public double edgeFactor(Band band) {
        double weighted = 0;
        for (Rarity rarity : Rarity.values()) {
            weighted += baseline.blocksPerChunk(band, rarity) * baseline.edgeFactor(band, rarity, variant);
        }
        return weighted / baseline.blocksPerChunk(band);
    }

    public boolean densityPasses(Band band) {
        double expected = expectedDensity(band);
        return Math.abs(density(band) - expected) <= DENSITY_TOLERANCE * expected;
    }

    /** A rarity's share of the band's placed ore; 0 when the band has none. */
    public double share(Band band, Rarity rarity) {
        long total = blocks(band);
        return total == 0 ? 0 : blocks(band, rarity) / (double) total;
    }

    public double expectedShare(Band band, Rarity rarity) {
        return baseline.share(band, rarity);
    }

    public boolean sharePasses(Band band, Rarity rarity) {
        return Math.abs(share(band, rarity) - expectedShare(band, rarity)) <= SHARE_TOLERANCE;
    }

    public int slabCount(Band band) {
        return slabs(band).length;
    }

    /** A slab's layers, the merged top slab included. */
    public BandBounds slabBounds(Band band, int slab) {
        BandBounds bounds = bounds(band);
        int count = slabCount(band);
        if (slab < 0 || slab >= count) {
            throw new IllegalArgumentException("No slab " + slab + " in the " + name(band) + " band");
        }
        int bottom = bounds.bottomY() + slab * HeightProfile.SLAB;
        int top = slab == count - 1 ? bounds.topY() : bottom + HeightProfile.SLAB - 1;
        return new BandBounds(bottom, top);
    }

    public long slabBlocks(Band band, int slab) {
        long sum = 0;
        for (long count : slabs(band)[slab]) {
            sum += count;
        }
        return sum;
    }

    public double slabDensity(Band band, int slab) {
        return slabBlocks(band, slab) / ((double) BLOCKS_PER_LAYER * chunks * slabBounds(band, slab).layers());
    }

    public boolean slabPasses(Band band, int slab) {
        double bandDensity = density(band);
        return Math.abs(slabDensity(band, slab) - bandDensity) <= SLAB_TOLERANCE * bandDensity;
    }

    /** Placed blocks of each ore of one rarity in one band, by group id. */
    public Map<String, Long> identities(Band band, Rarity rarity) {
        return Collections.unmodifiableMap(new TreeMap<>(identityMap(band).get(rarity)));
    }

    /** Every S12 check that failed, named as the verdict line names it; empty when all pass. */
    public List<String> failures() {
        List<String> failures = new ArrayList<>();
        for (Band band : bands()) {
            if (!densityPasses(band)) {
                failures.add(name(band) + " density");
            }
            for (Rarity rarity : Rarity.values()) {
                if (!sharePasses(band, rarity)) {
                    failures.add(name(band) + " " + name(rarity) + " share");
                }
            }
            for (int slab = 0; slab < slabCount(band); slab++) {
                if (!slabPasses(band, slab)) {
                    failures.add(name(band) + " slab " + range(slabBounds(band, slab)));
                }
            }
        }
        return failures;
    }

    public boolean passes() {
        return failures().isEmpty();
    }

    /** The command's output, in English: this is debug output for QA's PR quote and the server log (S44, T14). */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "density %s over %d chunks; S12: each band within %s relative, shares within %s"
                + " points, every slab within %s of its band", variant.id(), chunks, percent(DENSITY_TOLERANCE),
                points(SHARE_TOLERANCE), percent(SLAB_TOLERANCE)));
        for (Band band : bands()) {
            BandBounds bounds = bounds(band);
            double expected = expectedDensity(band);
            double stated = stated(band);
            lines.add(String.format(Locale.ROOT, "%s Y%s: %d ore in %d blocks = %.4f%%; expected %.4f%% = closed form %.4f%%"
                    + " less %.1f%% overlap (edge factor %.4f on the counts, taken back by the band cut); %+.2f%%: %s; "
                    + "§3.1 states %s: %+.2f%%",
                    name(band), range(bounds), blocks(band), volume(band), density(band) * 100, expected * 100,
                    baseline.density(band, variant) * 100, overlap(band) * 100, edgeFactor(band),
                    relative(density(band), expected) * 100, verdict(densityPasses(band)), percent(stated),
                    relative(density(band), stated) * 100));
            List<String> shares = new ArrayList<>();
            for (Rarity rarity : Rarity.values()) {
                shares.add(String.format(Locale.ROOT, "%s %.2f%% (expected %.2f%%, %+.2f points) %s", name(rarity),
                        share(band, rarity) * 100, expectedShare(band, rarity) * 100,
                        (share(band, rarity) - expectedShare(band, rarity)) * 100, verdict(sharePasses(band, rarity))));
            }
            lines.add(name(band) + " shares by placed blocks: " + String.join("; ", shares));
            for (int slab = 0; slab < slabCount(band); slab++) {
                lines.add(String.format(Locale.ROOT, "%s slab Y%s: %.4f%% (%+.2f%% of the band) %s", name(band),
                        range(slabBounds(band, slab)), slabDensity(band, slab) * 100,
                        relative(slabDensity(band, slab), density(band)) * 100, verdict(slabPasses(band, slab))));
            }
            for (Rarity rarity : Rarity.values()) {
                Map<String, Long> ores = identityMap(band).get(rarity);
                long total = blocks(band, rarity);
                List<String> parts = new ArrayList<>();
                ores.forEach((group, count) -> parts.add(String.format(Locale.ROOT, "%s %.1f%%", group, count * 100.0 / total)));
                lines.add(name(band) + " identity, " + name(rarity) + ": " + (parts.isEmpty() ? "none" : String.join(", ", parts)));
            }
        }
        List<String> failures = failures();
        lines.add(failures.isEmpty() ? "verdict: PASS" : "verdict: FAIL (" + String.join(", ", failures) + ")");
        return lines;
    }

    /** Every count in a fixed order, so two tallies of the same blocks compare equal (the gametest's check). */
    public String counts() {
        StringBuilder out = new StringBuilder(variant.id()).append(" chunks=").append(chunks);
        for (Band band : bands()) {
            long[][] slabs = slabs(band);
            for (int slab = 0; slab < slabs.length; slab++) {
                out.append('\n').append(name(band)).append(" slab ").append(slab);
                for (Rarity rarity : Rarity.values()) {
                    out.append(' ').append(name(rarity)).append('=').append(slabs[slab][rarity.ordinal()]);
                }
            }
            for (Rarity rarity : Rarity.values()) {
                out.append('\n').append(name(band)).append(' ').append(name(rarity)).append(' ').append(identityMap(band).get(rarity));
            }
        }
        return out.toString();
    }

    private long[][] slabs(Band band) {
        long[][] slabs = counts.get(band);
        if (slabs == null) {
            throw new IllegalArgumentException(variant.id() + " has no " + name(band) + " band");
        }
        return slabs;
    }

    private Map<Rarity, Map<String, Long>> identityMap(Band band) {
        slabs(band);
        return identities.get(band);
    }

    private BandBounds bounds(Band band) {
        return variant.band(band).orElseThrow(
                () -> new IllegalArgumentException(variant.id() + " has no " + name(band) + " band"));
    }

    /** Through the one rule the identity pick uses: 16-layer slabs, the partial top one merged. */
    private static int slabCount(BandBounds bounds) {
        return HeightProfile.slabOf(bounds.topY(), bounds.bottomY(), bounds.topY()) + 1;
    }

    /** §3.1's stated densities, printed beside the expected value. */
    private static double stated(Band band) {
        return band == Band.STONE ? 0.03 : 0.06;
    }

    private static double relative(double measured, double against) {
        return against == 0 ? 0 : measured / against - 1;
    }

    private static String verdict(boolean passes) {
        return passes ? "PASS" : "FAIL";
    }

    private static String range(BandBounds bounds) {
        return bounds.bottomY() + ".." + bounds.topY();
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%s%%", trim(fraction * 100));
    }

    private static String points(double fraction) {
        return trim(fraction * 100);
    }

    /** {@code 0.05 * 100} is 5.000000000000001 in a double; four places and no trailing zeros print it as 5. */
    private static String trim(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String name(Band band) {
        return band.name().toLowerCase(Locale.ROOT);
    }

    private static String name(Rarity rarity) {
        return rarity.name().toLowerCase(Locale.ROOT);
    }
}
