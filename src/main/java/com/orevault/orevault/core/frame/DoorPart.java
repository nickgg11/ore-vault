package com.orevault.orevault.core.frame;

/**
 * Which tile of the two-leaf sealed door a cell shows (§3.2). Each cell takes the part for its place in the opening, so
 * a 21 by 21 door is one door with more bands and not a tiled wall.
 *
 * <p>{@link #id()} is what the {@code part} block state holds, and it is save data from the first door (ADR 0010): the
 * ids are written out here, not taken from the ordinal, so reordering the list cannot move a saved door. This class
 * cannot import {@code StringRepresentable}, which is why the state is an integer and not an enum property.
 */
public enum DoorPart {
    FIELD_LEFT(0, "door_field", false),
    FIELD_RIGHT(1, "door_field", true),
    SEAM_LEFT(2, "door_seam", false),
    SEAM_RIGHT(3, "door_seam", true),
    SEAM_CENTRE(4, "door_seam_c", false),
    STRAP_LEFT(5, "door_strap", false),
    STRAP_RIGHT(6, "door_strap", true),
    STRAP_SEAM_LEFT(7, "door_strap_seam", false),
    STRAP_SEAM_RIGHT(8, "door_strap_seam", true),
    STRAP_SEAM_CENTRE(9, "door_strap_seam_c", false),
    SEAT_LEFT(10, "door_seat_l", false),
    SEAT_RIGHT(11, "door_seat_r", false),
    SEAT_CENTRE(12, "door_seat_c", false);

    /** The highest id, which is the top of the {@code part} property's range. */
    public static final int MAX_ID = 12;

    /** Blocks between straps, more or less. The first and last rows always have one, so the door has a top and a bottom. */
    private static final double RUN_BETWEEN_STRAPS = 5.0;

    /** The row directly above the bottom strap, at every size (Nick, 2026-11-07; it was min(height / 2, 2)). */
    private static final int SEAT_ROW = 1;

    private final int id;
    private final String tile;
    private final boolean mirrored;

    DoorPart(int id, String tile, boolean mirrored) {
        this.id = id;
        this.tile = tile;
        this.mirrored = mirrored;
    }

    /**
     * The part at a cell of an opening.
     *
     * @param column counted along the opening from 0
     * @param row counted up from the sill from 0
     * @param width the opening's width
     * @param height the opening's height
     */
    public static DoorPart of(int column, int row, int width, int height) {
        boolean seat = row == SEAT_ROW;
        boolean strapped = isStrapRow(row, height);
        if (width % 2 == 1 && column == width / 2) {
            return seat ? SEAT_CENTRE : strapped ? STRAP_SEAM_CENTRE : SEAM_CENTRE;
        }
        if (width % 2 == 0 && column == width / 2 - 1) {
            return seat ? SEAT_LEFT : strapped ? STRAP_SEAM_LEFT : SEAM_LEFT;
        }
        if (width % 2 == 0 && column == width / 2) {
            return seat ? SEAT_RIGHT : strapped ? STRAP_SEAM_RIGHT : SEAM_RIGHT;
        }
        boolean left = column * 2 < width - 1;
        if (strapped) {
            return left ? STRAP_LEFT : STRAP_RIGHT;
        }
        return left ? FIELD_LEFT : FIELD_RIGHT;
    }

    /** {@code Math.rint} rounds a half to even, as the render this door was approved on does: 9 rows put a strap on row 4. */
    private static boolean isStrapRow(int row, int height) {
        int top = height - 1;
        int bands = Math.max(1, (int) Math.rint(top / RUN_BETWEEN_STRAPS));
        for (int band = 0; band <= bands; band++) {
            if ((int) Math.rint(band * (double) top / bands) == row) {
                return true;
            }
        }
        return false;
    }

    /** The part with this id. */
    public static DoorPart fromId(int id) {
        for (DoorPart part : values()) {
            if (part.id == id) {
                return part;
            }
        }
        throw new IllegalArgumentException("no door part " + id);
    }

    /** What the {@code part} block state holds. */
    public int id() {
        return id;
    }

    /** The texture, after {@code vault_}. */
    public String tile() {
        return tile;
    }

    /** Whether {@link #tile()} is flipped left to right. The right leaf is the left one mirrored, not a new texture. */
    public boolean mirrored() {
        return mirrored;
    }

    /**
     * What this cell shows from behind. Columns run the other way from there, so a block in the left leaf is on a viewer's
     * right, and showing it the same tile would put the outer edge on the seam.
     */
    public DoorPart back() {
        return switch (this) {
            case FIELD_LEFT -> FIELD_RIGHT;
            case FIELD_RIGHT -> FIELD_LEFT;
            case SEAM_LEFT -> SEAM_RIGHT;
            case SEAM_RIGHT -> SEAM_LEFT;
            case STRAP_LEFT -> STRAP_RIGHT;
            case STRAP_RIGHT -> STRAP_LEFT;
            case STRAP_SEAM_LEFT -> STRAP_SEAM_RIGHT;
            case STRAP_SEAM_RIGHT -> STRAP_SEAM_LEFT;
            case SEAT_LEFT -> SEAT_RIGHT;
            case SEAT_RIGHT -> SEAT_LEFT;
            case SEAM_CENTRE, STRAP_SEAM_CENTRE, SEAT_CENTRE -> this;
        };
    }
}
