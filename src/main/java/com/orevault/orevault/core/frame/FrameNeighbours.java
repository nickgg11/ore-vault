package com.orevault.orevault.core.frame;

import java.util.ArrayList;
import java.util.List;

/**
 * Which sides of a Vault Frame touch another Vault Frame, and the texture that gives each face (§3.2). A texture
 * is named by the sides that connect as seen from outside the face, so the mapping below follows vanilla's default
 * UVs ({@code FaceBakery#defaultFaceUV}): left and right swap between opposite faces, which keeps a run correct
 * from behind without mirrored UVs.
 *
 * <p>{@code _thin} marks the edge of a frame one block thick, meaning a face whose normal lies in the frame's plane.
 * Two axes with neighbours name the plane. One horizontal axis is taken as an upright frame, the plane holding that
 * axis and the vertical. A column cannot tell its front from its reveal, so only its top and bottom are thin. A lone
 * block has no plane and neighbours on all three axes are not flat, so neither is ever thin.
 */
public record FrameNeighbours(boolean north, boolean east, boolean south, boolean west, boolean up, boolean down) {
    private static final String THIN = "_thin";
    private static final int STATES = 64;

    /** The six faces of the cube. */
    public enum Face {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
    }

    /** Every combination of neighbours, so a generator can write one model for each block state. */
    public static List<FrameNeighbours> all() {
        List<FrameNeighbours> all = new ArrayList<>(STATES);
        for (int bits = 0; bits < STATES; bits++) {
            all.add(new FrameNeighbours((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0,
                    (bits & 16) != 0, (bits & 32) != 0));
        }
        return List.copyOf(all);
    }

    /** The part of the texture name after {@code vault_frame}. */
    public String textureSuffix(Face face) {
        if (isThin(face)) {
            return THIN;
        }
        return switch (face) {
            case NORTH -> sides(up, down, east, west);
            case SOUTH -> sides(up, down, west, east);
            case EAST -> sides(up, down, south, north);
            case WEST -> sides(up, down, north, south);
            case UP -> sides(north, south, west, east);
            case DOWN -> sides(south, north, west, east);
        };
    }

    /** The part of the model name after {@code vault_frame}; a lone block is the base model the item uses. */
    public String modelSuffix() {
        String letters = (north ? "n" : "") + (east ? "e" : "") + (south ? "s" : "") + (west ? "w" : "")
                + (up ? "u" : "") + (down ? "d" : "");
        return letters.isEmpty() ? "" : "_" + letters;
    }

    private boolean isThin(Face face) {
        int axes = (eastWest() ? 1 : 0) + (northSouth() ? 1 : 0) + (vertical() ? 1 : 0);
        if (axes == 0 || axes == 3) {
            return false;
        }
        return switch (face) {
            case NORTH, SOUTH -> northSouth();
            case EAST, WEST -> eastWest();
            case UP, DOWN -> vertical() || axes == 1;
        };
    }

    private boolean eastWest() {
        return east || west;
    }

    private boolean northSouth() {
        return north || south;
    }

    private boolean vertical() {
        return up || down;
    }

    private static String sides(boolean textureUp, boolean textureDown, boolean textureLeft, boolean textureRight) {
        String letters = (textureUp ? "u" : "") + (textureDown ? "d" : "") + (textureLeft ? "l" : "")
                + (textureRight ? "r" : "");
        return letters.isEmpty() ? "" : "_" + letters;
    }
}
