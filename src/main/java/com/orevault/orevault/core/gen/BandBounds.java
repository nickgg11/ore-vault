package com.orevault.orevault.core.gen;

/**
 * One band's layers in one dimension type, both ends inclusive (§3.1, ADR 0009 "One pure record").
 *
 * @param bottomY the band's floor
 * @param topY the band's top
 */
public record BandBounds(int bottomY, int topY) {
    public BandBounds {
        if (topY < bottomY) {
            throw new IllegalArgumentException("A band from Y=" + bottomY + " to Y=" + topY);
        }
    }

    /** The band's height, {@code H} in ADR 0009's edge factor. */
    public int layers() {
        return topY - bottomY + 1;
    }
}
