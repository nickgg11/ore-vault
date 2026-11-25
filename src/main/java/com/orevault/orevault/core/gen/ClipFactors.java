package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The 40% stone floor's two factors (§3.1, ADR 0009 "Counts per rarity, and the floor clip"): every rarity's count in a
 * band is multiplied by its band's factor, {@code min(1, 0.60 / densest)}, so the mix never shifts. {@code densest} is the
 * highest ore density requested in any one layer of the band, not the band's average: requests are summed layer by layer,
 * so a node that concentrates ore into the band's lowest 30 layers on top of the band-wide veins sets the factor by those
 * layers (Balancer, BAL-3). Computed on the main thread by {@link #of} and carried in the generation snapshot (ADR 0003),
 * so the generator never computes it from team state. With no generation nodes both are 1.
 *
 * <p>Each request is spread over the layers as height now spreads it (ADR 0009, amended 2026-11-21): its veins' ores and
 * box middles from the band's profiles, their blocks about the middle from the generated kernel, cut at the band's edges,
 * and the whole scaled to the request's closed form, count times mean blocks ({@link LayerSpread#placedByLayer}). That is
 * what the placer places once the count carries the edge factor, which only makes up the cut blocks. So the baseline
 * itself is densest where its ores peak, not flat. On vanilla, at Y=100 (heights.py's B100 rows, confirmed by the Balancer
 * 2026-11-23), the densest single layer is 5.19% at Y=2 of the base stone band and 16.28% at Y=-2 of deepslate, far from
 * 60%; {@code heights.txt}'s best two-layer tunnels are Y2..3 at about 5.2% and Y-4..-3 at about 15.5%.

 * <p>Since the clip reads the densest layer, it fires once the band-wide request reaches about 0.60 over the densest-to-
 * average ratio: 2.62 in deepslate and about 1.7 in stone, so about 23% and 35% band-wide, not 60% (Balancer, B-2). That
 * is ADR 0009's rule, recorded in its Consequences; what it means for the full tree is T9's (#320). A per-chunk outcome such as a Vein Singularity chunk is one more request set to compute this
 * over; it joins with its node (T9).
 *
 * @param stone the stone band's factor
 * @param deepslate the deepslate band's factor, carried but never read in the base type
 */
public record ClipFactors(double stone, double deepslate) {
    /** §3.1: the Vault is never more than 60% ore by volume. The placer's hard stop counts against the same figure. */
    public static final double CEILING = 0.60;

    /**
     * How far past {@link #CEILING} the placer's hard stop sits (S3-BAL-1). The stop counts the band's total requested
     * blocks. For a request flat through its band, where the densest layer is the band's average, a correct clip puts the
     * mean in-band request exactly on the ceiling, and the spread of vein sizes is about 1.4% of it in a fully clipped
     * deepslate band (derived), so a stop on the ceiling itself would refuse the rare veins placed last in about half of
     * clipped chunks. For such a flat request, at 1.10 the stop is about seven standard deviations out: it fires only when a
     * clip is wrong by more than 10%, and placed ore after overlap stays at most about 48%. These figures hold for a flat
     * request only. The clip reads the densest layer (ADR 0009, amended 2026-11-21), and the baseline is not flat: a
     * correctly clipped deepslate band averages at most about 23%, so the stop catches only a clip wrong by about 2.9 times
     * there (Balancer, B-3). Whether the stop becomes per slab is T9's. If the clip test's printed spread is above 2%, this
     * becomes 1 + 5 sigma.
     */
    public static final double HARD_STOP_HEADROOM = 1.10;

    public static final ClipFactors NONE = new ClipFactors(1, 1);

    public ClipFactors {
        if (!(stone > 0 && stone <= 1 && deepslate > 0 && deepslate <= 1)) {
            throw new IllegalArgumentException("Clip factors " + stone + ", " + deepslate);
        }
    }

    public double of(Band band) {
        return band == Band.STONE ? stone : deepslate;
    }

    /**
     * The factors for a team: the baseline's requests with the team's generation nodes applied (ARCH-E4-16). No generation
     * node exists before E9, so {@code tiers} changes nothing yet; each node joins here as a change to the requests or as
     * extra requests (ADR 0009 Consequences), and an owned node this method does not know changes nothing. A pack with no
     * classified ore has no pickers and places no ore, so nothing clips.
     *
     * @param pickers the variant's published pickers, one per band, or none at all
     */
    public static ClipFactors of(OreBaseline baseline, VaultVariant variant, Map<Band, OrePicker> pickers, GenerationTiers tiers) {
        if (pickers.isEmpty()) {
            return NONE;
        }
        return fromRequests(variant, nominalRequests(baseline, variant, tiers), pickers);
    }

    /**
     * One request per band the variant has and per rarity, over the whole band, at the baseline count: no edge factor and
     * no clip, which is what {@link #fromRequests} wants.
     */
    static List<VeinRequest> nominalRequests(OreBaseline baseline, VaultVariant variant, GenerationTiers tiers) {
        List<VeinRequest> requests = new ArrayList<>();
        for (Band band : Band.values()) {
            variant.band(band).ifPresent(bounds -> {
                for (Rarity rarity : Rarity.values()) {
                    OreBaseline.Veins veins = baseline.veins(band, rarity);
                    double[] sizes = new double[veins.maxSize() - veins.minSize() + 1];
                    for (int i = 0; i < sizes.length; i++) {
                        sizes[i] = veins.minSize() + i;
                    }
                    requests.add(new VeinRequest(band, rarity, veins.perChunk(), sizes, bounds.bottomY(), bounds.topY(), false));
                }
            });
        }
        return requests;
    }

    /**
     * The factors for a set of requests: per band, the densest layer's summed density sets {@code min(1, 0.60 / densest)}.
     * A band the variant does not have keeps 1.
     */
    public static ClipFactors fromRequests(VaultVariant variant, List<VeinRequest> requests, Map<Band, OrePicker> pickers) {
        return new ClipFactors(factor(variant, Band.STONE, requests, pickers), factor(variant, Band.DEEPSLATE, requests, pickers));
    }

    /**
     * Every layer's summed request density in the band, index 0 at the band's floor, in closed form before overlap; an
     * empty array for a band the variant lacks.
     *
     * @throws IllegalArgumentException when a band with requests has no picker
     */
    public static double[] byLayer(VaultVariant variant, Band band, List<VeinRequest> requests, Map<Band, OrePicker> pickers) {
        BandBounds bounds = variant.band(band).orElse(null);
        if (bounds == null) {
            return new double[0];
        }
        double[] byLayer = new double[bounds.layers()];
        for (VeinRequest request : requests) {
            if (request.band() != band) {
                continue;
            }
            OrePicker picker = pickers.get(band);
            if (picker == null) {
                throw new IllegalArgumentException("No ore picker for the " + band + " band");
            }
            double[] placed = LayerSpread.placedByLayer(request, picker, bounds);
            for (int i = 0; i < byLayer.length; i++) {
                byLayer[i] += placed[i] / 256.0;
            }
        }
        return byLayer;
    }

    /** The highest summed request density of any layer of the band, in closed form; 0 for a band the variant lacks. */
    public static double densest(VaultVariant variant, Band band, List<VeinRequest> requests, Map<Band, OrePicker> pickers) {
        double densest = 0;
        for (double density : byLayer(variant, band, requests, pickers)) {
            densest = Math.max(densest, density);
        }
        return densest;
    }

    private static double factor(VaultVariant variant, Band band, List<VeinRequest> requests, Map<Band, OrePicker> pickers) {
        double densest = densest(variant, band, requests, pickers);
        return densest > CEILING ? CEILING / densest : 1.0;
    }

    /**
     * Mean blocks a vein of the request places: its sizes drawn uniformly, a fractional size as its two neighbours with the
     * probabilities the placer uses, so the mean is linear between table rows.
     */
    static double meanBlocks(VeinRequest request) {
        double sum = 0;
        for (double size : request.sizes()) {
            int whole = (int) Math.floor(size);
            double fraction = size - whole;
            sum += fraction == 0 ? VeinShape.meanBlocks(whole)
                    : (1 - fraction) * VeinShape.meanBlocks(whole) + fraction * VeinShape.meanBlocks(whole + 1);
        }
        return sum / request.sizeCount();
    }
}
