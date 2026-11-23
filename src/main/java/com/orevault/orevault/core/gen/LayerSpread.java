package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;

/**
 * Where a request's veins put their blocks, layer by layer, in closed form (§3.1 "Where each ore sits" and "How the
 * baseline is checked"; ADR 0009, amended 2026-11-21, D2). A vein's ore and box middle come from the
 * {@link OrePicker.Table} the placer draws from, and its blocks about the middle from {@link VeinShape#layerKernel}, the
 * generated table of the same port the placer rolls; only blocks inside the band are kept, as the placer keeps them.
 *
 * <p>The edge factor ({@link OreBaseline#edgeFactor}), the floor clip's {@code densest} ({@link ClipFactors}) and the
 * density check's expected slabs ({@link DensityTally}) all read this one class, so the generator, the clip and the check
 * cannot disagree on where ore sits.
 */
public final class LayerSpread {
    private LayerSpread() {
    }

    /**
     * Mean blocks one vein of the request places at each offset from its box middle, index {@code offset +}
     * {@link VeinShape#KERNEL_REACH}: its sizes drawn uniformly, a fractional size as its two neighbours with the
     * probabilities the placer draws them with.
     */
    public static double[] kernel(VeinRequest request) {
        double[] kernel = new double[2 * VeinShape.KERNEL_REACH + 1];
        for (double size : request.sizes()) {
            int whole = (int) Math.floor(size);
            double fraction = size - whole;
            add(kernel, VeinShape.layerKernel(whole), 1 - fraction);
            if (fraction > 0) {
                add(kernel, VeinShape.layerKernel(whole + 1), fraction);
            }
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= request.sizeCount();
        }
        return kernel;
    }

    /** The same for a band's baseline veins, sizes uniform over the configured range. */
    public static double[] kernel(OreBaseline.Veins veins) {
        double[] kernel = new double[2 * VeinShape.KERNEL_REACH + 1];
        for (int size = veins.minSize(); size <= veins.maxSize(); size++) {
            add(kernel, VeinShape.layerKernel(size), 1);
        }
        int sizes = veins.maxSize() - veins.minSize() + 1;
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sizes;
        }
        return kernel;
    }

    /**
     * Mean blocks one vein drawn from {@code table} places at each layer of the band, index 0 at the band's floor: each
     * ore's share, times its middle distribution, times the kernel about each middle, blocks outside the band dropped. Its
     * sum over the kernel's sum is the fraction of a vein's blocks the band keeps.
     */
    public static double[] perVein(OrePicker.Table table, double[] kernel, BandBounds band) {
        double[] layers = new double[band.layers()];
        int reach = VeinShape.KERNEL_REACH;
        for (int ore = 0; ore < table.size(); ore++) {
            double share = table.share(ore);
            double[] middle = table.middle(ore);
            for (int m = 0; m < middle.length; m++) {
                if (middle[m] == 0) {
                    continue;
                }
                double weight = share * middle[m];
                int middleY = table.regionBottomY() + m;
                for (int offset = -reach; offset <= reach; offset++) {
                    int y = middleY + offset;
                    if (y >= band.bottomY() && y <= band.topY()) {
                        layers[y - band.bottomY()] += weight * kernel[offset + reach];
                    }
                }
            }
        }
        return layers;
    }

    /** The fraction of a vein's blocks the band keeps: {@link #perVein} summed over the kernel summed; 1 for an empty kernel. */
    public static double kept(double[] perVein, double[] kernel) {
        double all = sum(kernel);
        return all == 0 ? 1 : sum(perVein) / all;
    }

    /**
     * A request's expected placed blocks a chunk at each layer of its band, index 0 at the band's floor, before overlap
     * between veins: its closed form, {@code perChunk} times mean blocks a vein, spread as {@link #perVein} spreads it and
     * scaled so the band holds the whole closed form. That is what the placer places once the count carries the edge
     * factor, which makes up the blocks cut at the band's edges ({@link OreBaseline#edgeFactor}); for a request whose count
     * carries none, it errs high, which is the safe side for the floor clip.
     *
     * @throws IllegalArgumentException for a region outside the band
     */
    public static double[] placedByLayer(VeinRequest request, OrePicker picker, BandBounds band) {
        OrePicker.Table table = picker.table(request.rarity(), request.regionBottomY(), request.regionTopY());
        double[] layers = perVein(table, kernel(request), band);
        double inBand = sum(layers);
        double closedForm = request.perChunk() * ClipFactors.meanBlocks(request);
        for (int i = 0; i < layers.length; i++) {
            layers[i] = inBand == 0 ? 0 : layers[i] * closedForm / inBand;
        }
        return layers;
    }

    static double sum(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    private static void add(double[] into, double[] row, double weight) {
        for (int i = 0; i < into.length; i++) {
            into[i] += weight * row[i];
        }
    }
}
