package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.Rarity;
import java.util.ArrayList;
import java.util.List;

/**
 * The 40% stone floor's two factors (§3.1, ADR 0009 "Counts per rarity, and the floor clip"): every rarity's count in a
 * band is multiplied by its band's factor, {@code min(1, 0.60 / densest)}, so the mix never shifts. {@code densest} is the
 * highest ore density requested in any one region of the band, not the band's average: requests are summed layer by
 * layer, so a node that concentrates ore into the band's lowest 30 layers on top of the band-wide veins sets the factor by
 * those layers (Balancer, BAL-3). Computed on the main thread by {@link #of} and carried in the generation snapshot (ADR
 * 0003), so the generator never computes it from team state. With no generation nodes both are 1.
 *
 * <p>Densities are the closed form, count times mean blocks over the region's volume, before the edge factor: the edge
 * factor only makes up blocks cut at the band's edges, so placed blocks match the closed form, and the cap is on placed
 * blocks. This is exact for a request spread through its whole band. A request over part of a band spills its overhang
 * into the neighbouring layers of the band, so its densest layer comes out under the closed form and the factor is
 * conservative there (S3-AR-3). A per-chunk outcome such as a Vein Singularity chunk is one more request set to compute
 * this over; it joins with its node (T9).
 *
 * @param stone the stone band's factor
 * @param deepslate the deepslate band's factor, carried but never read in the base type
 */
public record ClipFactors(double stone, double deepslate) {
    /** §3.1: the Vault is never more than 60% ore by volume. The placer's hard stop counts against the same figure. */
    public static final double CEILING = 0.60;

    /**
     * How far past {@link #CEILING} the placer's hard stop sits (S3-BAL-1). A correct clip puts the mean in-band request
     * exactly on the ceiling, and the spread of vein sizes is about 1.4% of it in a fully clipped deepslate band (derived),
     * so a stop on the ceiling itself would refuse the rare veins placed last in about half of clipped chunks. At 1.10 the
     * stop is about seven standard deviations out: it fires only when a clip is wrong by more than 10%, and placed ore after
     * overlap stays at most about 48%. If the clip test's printed spread is above 2%, this becomes 1 + 5 sigma.
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
     * extra requests (ADR 0009 Consequences), and an owned node this method does not know changes nothing.
     */
    public static ClipFactors of(OreBaseline baseline, VaultVariant variant, GenerationTiers tiers) {
        return fromRequests(variant, nominalRequests(baseline, variant, tiers));
    }

    /**
     * One request per band the variant has and per rarity, spread through the whole band, at the baseline count: no edge
     * factor and no clip, which is what {@link #fromRequests} wants.
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
     * The factors for a set of requests, each read as its closed-form density over its own region: per band, the densest
     * layer's summed density sets {@code min(1, 0.60 / densest)}. A band the variant does not have keeps 1.
     */
    public static ClipFactors fromRequests(VaultVariant variant, List<VeinRequest> requests) {
        return new ClipFactors(factor(variant, Band.STONE, requests), factor(variant, Band.DEEPSLATE, requests));
    }

    /** The highest summed request density of any layer of the band, in closed form; 0 for a band the variant lacks. */
    public static double densest(VaultVariant variant, Band band, List<VeinRequest> requests) {
        BandBounds bounds = variant.band(band).orElse(null);
        if (bounds == null) {
            return 0;
        }
        double[] byLayer = new double[bounds.layers()];
        for (VeinRequest request : requests) {
            if (request.band() != band) {
                continue;
            }
            int regionLayers = request.regionTopY() - request.regionBottomY() + 1;
            double density = request.perChunk() * meanBlocks(request) / (256.0 * regionLayers);
            for (int y = Math.max(request.regionBottomY(), bounds.bottomY()); y <= Math.min(request.regionTopY(), bounds.topY()); y++) {
                byLayer[y - bounds.bottomY()] += density;
            }
        }
        double densest = 0;
        for (double density : byLayer) {
            densest = Math.max(densest, density);
        }
        return densest;
    }

    private static double factor(VaultVariant variant, Band band, List<VeinRequest> requests) {
        double densest = densest(variant, band, requests);
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
