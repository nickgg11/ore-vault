package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** §3.2 Parts. The layout rows come from the Designer's {@code door_part()}, which Nick's yes to the render settled. */
class DoorPartTest {
    /** Rows from the sill, by height, as the approved render computes them. */
    private static final Map<Integer, List<Integer>> STRAP_ROWS = new LinkedHashMap<>();

    static {
        STRAP_ROWS.put(3, List.of(0, 2));
        STRAP_ROWS.put(4, List.of(0, 3));
        STRAP_ROWS.put(5, List.of(0, 4));
        STRAP_ROWS.put(6, List.of(0, 5));
        STRAP_ROWS.put(7, List.of(0, 6));
        STRAP_ROWS.put(8, List.of(0, 7));
        STRAP_ROWS.put(9, List.of(0, 4, 8));
        STRAP_ROWS.put(10, List.of(0, 4, 9));
        STRAP_ROWS.put(11, List.of(0, 5, 10));
        STRAP_ROWS.put(12, List.of(0, 6, 11));
        STRAP_ROWS.put(13, List.of(0, 6, 12));
        STRAP_ROWS.put(14, List.of(0, 4, 9, 13));
        STRAP_ROWS.put(15, List.of(0, 5, 9, 14));
        STRAP_ROWS.put(16, List.of(0, 5, 10, 15));
        STRAP_ROWS.put(17, List.of(0, 5, 11, 16));
        STRAP_ROWS.put(18, List.of(0, 6, 11, 17));
        STRAP_ROWS.put(19, List.of(0, 4, 9, 14, 18));
        STRAP_ROWS.put(20, List.of(0, 5, 10, 14, 19));
        STRAP_ROWS.put(21, List.of(0, 5, 10, 15, 20));
    }

    /** Save data from the first door (ADR 0010): changing one of these is a migration, not an edit. */
    @Test
    void idsAreGolden() {
        Map<DoorPart, Integer> golden = new LinkedHashMap<>();
        golden.put(DoorPart.FIELD_LEFT, 0);
        golden.put(DoorPart.FIELD_RIGHT, 1);
        golden.put(DoorPart.SEAM_LEFT, 2);
        golden.put(DoorPart.SEAM_RIGHT, 3);
        golden.put(DoorPart.SEAM_CENTRE, 4);
        golden.put(DoorPart.STRAP_LEFT, 5);
        golden.put(DoorPart.STRAP_RIGHT, 6);
        golden.put(DoorPart.STRAP_SEAM_LEFT, 7);
        golden.put(DoorPart.STRAP_SEAM_RIGHT, 8);
        golden.put(DoorPart.STRAP_SEAM_CENTRE, 9);
        golden.put(DoorPart.SEAT_LEFT, 10);
        golden.put(DoorPart.SEAT_RIGHT, 11);
        golden.put(DoorPart.SEAT_CENTRE, 12);
        assertEquals(DoorPart.values().length, golden.size());
        golden.forEach((part, id) -> {
            assertEquals(id, part.id(), part.name());
            assertSame(part, DoorPart.fromId(id));
        });
    }

    @Test
    void anEvenDoorSplitsDownTheMiddle() {
        // 4 by 5: straps on rows 0 and 4, the seat on row 1, directly above the bottom strap.
        assertEquals(DoorPart.STRAP_LEFT, DoorPart.of(0, 0, 4, 5));
        assertEquals(DoorPart.STRAP_SEAM_LEFT, DoorPart.of(1, 0, 4, 5));
        assertEquals(DoorPart.STRAP_SEAM_RIGHT, DoorPart.of(2, 0, 4, 5));
        assertEquals(DoorPart.STRAP_RIGHT, DoorPart.of(3, 0, 4, 5));
        assertEquals(DoorPart.FIELD_LEFT, DoorPart.of(0, 2, 4, 5));
        assertEquals(DoorPart.SEAM_LEFT, DoorPart.of(1, 2, 4, 5));
        assertEquals(DoorPart.SEAM_RIGHT, DoorPart.of(2, 2, 4, 5));
        assertEquals(DoorPart.FIELD_RIGHT, DoorPart.of(3, 2, 4, 5));
        assertEquals(DoorPart.SEAT_LEFT, DoorPart.of(1, 1, 4, 5));
        assertEquals(DoorPart.SEAT_RIGHT, DoorPart.of(2, 1, 4, 5));
    }

    @Test
    void anOddDoorHasACentreColumn() {
        assertEquals(DoorPart.STRAP_SEAM_CENTRE, DoorPart.of(1, 0, 3, 5));
        assertEquals(DoorPart.SEAM_CENTRE, DoorPart.of(1, 2, 3, 5));
        assertEquals(DoorPart.SEAT_CENTRE, DoorPart.of(1, 1, 3, 5));
        assertEquals(DoorPart.FIELD_LEFT, DoorPart.of(0, 2, 3, 5));
        assertEquals(DoorPart.FIELD_RIGHT, DoorPart.of(2, 2, 3, 5));
    }

    @Test
    void strapsAndSeatFollowTheRenderAtEveryHeight() {
        STRAP_ROWS.forEach((height, rows) -> {
            int seat = 1;
            for (int row = 0; row < height; row++) {
                boolean strapped = DoorPart.of(0, row, 5, height) == DoorPart.STRAP_LEFT;
                assertEquals(rows.contains(row), strapped, "height " + height + " row " + row);
                assertEquals(row == seat, DoorPart.of(2, row, 5, height) == DoorPart.SEAT_CENTRE,
                        "height " + height + " row " + row);
            }
        });
    }

    /** One seat at every size, directly above the bottom strap. */
    @Test
    void aDoorHasExactlyOneSeatAtEverySize() {
        for (int width = VaultPortalShape.MIN_WIDTH; width <= VaultPortalShape.MAX_SIZE; width++) {
            for (int height = VaultPortalShape.MIN_HEIGHT; height <= VaultPortalShape.MAX_SIZE; height++) {
                int seats = 0;
                for (int column = 0; column < width; column++) {
                    for (int row = 0; row < height; row++) {
                        DoorPart part = DoorPart.of(column, row, width, height);
                        seats += part == DoorPart.SEAT_LEFT || part == DoorPart.SEAT_CENTRE ? 1 : 0;
                    }
                }
                assertEquals(1, seats, width + " by " + height);
            }
        }
    }

    /** The art contract: nine tiles and the flips cover all thirteen parts. */
    @Test
    void tilesAndFlipsAreTheArtContract() {
        Set<String> tiles = new HashSet<>();
        for (DoorPart part : DoorPart.values()) {
            tiles.add(part.tile());
        }
        assertEquals(Set.of("door_field", "door_seam", "door_seam_c", "door_strap", "door_strap_seam", "door_strap_seam_c",
                "door_seat_l", "door_seat_r", "door_seat_c"), tiles);
        assertTrue(DoorPart.FIELD_RIGHT.mirrored());
        assertTrue(DoorPart.STRAP_RIGHT.mirrored());
        assertTrue(DoorPart.SEAM_RIGHT.mirrored());
        assertTrue(DoorPart.STRAP_SEAM_RIGHT.mirrored());
        assertFalse(DoorPart.FIELD_LEFT.mirrored());
        assertFalse(DoorPart.SEAT_RIGHT.mirrored());
        assertFalse(DoorPart.SEAT_CENTRE.mirrored());
        assertFalse(DoorPart.SEAM_CENTRE.mirrored());
        assertFalse(DoorPart.STRAP_SEAM_CENTRE.mirrored());
    }

    /** Seen from behind the columns run the other way, so each block shows its mirror's face. */
    @Test
    void theBackOfAPartIsItsMirrorAcrossTheSeam() {
        for (int width = VaultPortalShape.MIN_WIDTH; width <= VaultPortalShape.MAX_SIZE; width++) {
            for (int column = 0; column < width; column++) {
                for (int row = 0; row < 7; row++) {
                    assertEquals(DoorPart.of(width - 1 - column, row, width, 7), DoorPart.of(column, row, width, 7).back(),
                            "column " + column + " of " + width + " row " + row);
                }
            }
        }
    }
}
