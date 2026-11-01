package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.frame.FrameNeighbours.Face;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Which of the 17 textures each face of a Vault Frame shows (§3.2). */
class FrameNeighboursTest {
    private static final Set<String> TEXTURES = Set.of("", "_u", "_d", "_l", "_r", "_ud", "_lr", "_dr", "_dl", "_ur", "_ul",
            "_udl", "_udr", "_ulr", "_dlr", "_udlr", "_thin");

    private static final FrameNeighbours NONE = new FrameNeighbours(false, false, false, false, false, false);

    private static FrameNeighbours with(String sides) {
        return new FrameNeighbours(sides.contains("n"), sides.contains("e"), sides.contains("s"), sides.contains("w"),
                sides.contains("u"), sides.contains("d"));
    }

    private static void assertFaces(FrameNeighbours block, String north, String south, String east, String west, String up,
            String down) {
        assertEquals(north, block.textureSuffix(Face.NORTH), "north");
        assertEquals(south, block.textureSuffix(Face.SOUTH), "south");
        assertEquals(east, block.textureSuffix(Face.EAST), "east");
        assertEquals(west, block.textureSuffix(Face.WEST), "west");
        assertEquals(up, block.textureSuffix(Face.UP), "up");
        assertEquals(down, block.textureSuffix(Face.DOWN), "down");
    }

    @Test
    void aLoneBlockShowsTheSingleTextureEverywhere() {
        assertFaces(NONE, "", "", "", "", "", "");
    }

    @Test
    void aRunAlongXSwapsLeftAndRightBetweenFrontAndBack() {
        assertFaces(with("e"), "_l", "_r", "_thin", "_thin", "_thin", "_thin");
    }

    @Test
    void aRunAlongZSwapsLeftAndRightBetweenFrontAndBack() {
        assertFaces(with("s"), "_thin", "_thin", "_l", "_r", "_thin", "_thin");
    }

    @Test
    void theMiddleOfAHorizontalRunIsThinAboveAndBelow() {
        assertFaces(with("ew"), "_lr", "_lr", "_thin", "_thin", "_thin", "_thin");
    }

    @Test
    void aColumnKeepsThePlaitOnAllFourSidesBecauseNothingSaysWhichIsTheFront() {
        assertFaces(with("ud"), "_ud", "_ud", "_ud", "_ud", "_thin", "_thin");
    }

    @Test
    void theTopOfAColumnEndsThePlaitDownward() {
        assertFaces(with("d"), "_d", "_d", "_d", "_d", "_thin", "_thin");
    }

    @Test
    void aCornerOfAnXFramePatternsItsFrontAndBackOnly() {
        assertFaces(with("ed"), "_dl", "_dr", "_thin", "_thin", "_thin", "_thin");
    }

    @Test
    void aCornerOfAZFramePatternsItsFrontAndBackOnly() {
        assertFaces(with("sd"), "_thin", "_thin", "_dl", "_dr", "_thin", "_thin");
    }

    @Test
    void aJunctionInAWallShowsTheCrossPiece() {
        assertFaces(with("ewud"), "_udlr", "_udlr", "_thin", "_thin", "_thin", "_thin");
    }

    @Test
    void aFlatLayerPatternsItsTopAndUndersideAndThinsItsEdges() {
        assertFaces(with("ne"), "_thin", "_thin", "_thin", "_thin", "_ur", "_dr");
    }

    @Test
    void neighboursOnAllThreeAxesLeaveEveryFaceToItsOwnSides() {
        assertFaces(with("neu"), "_ul", "_ur", "_ur", "_ul", "_ur", "_dr");
    }

    @Test
    void theSixtyFourStatesReachEveryTextureAndNoOther() {
        Set<String> shown = new TreeSet<>();
        for (FrameNeighbours block : FrameNeighbours.all()) {
            for (Face face : Face.values()) {
                shown.add(block.textureSuffix(face));
            }
        }
        assertEquals(64, FrameNeighbours.all().size());
        assertEquals(new TreeSet<>(TEXTURES), shown);
    }

    @Test
    void everySuffixHasATextureFile() {
        Path folder = Path.of(System.getProperty("orevault.projectDir"), "src/main/resources/assets/orevault/textures/block");
        for (String suffix : TEXTURES) {
            assertTrue(Files.exists(folder.resolve("vault_frame" + suffix + ".png")), suffix);
        }
    }

    @Test
    void modelNamesAreUniqueAndTheLoneBlockKeepsTheBaseName() {
        Set<String> names = new TreeSet<>();
        FrameNeighbours.all().forEach(block -> names.add(block.modelSuffix()));
        assertEquals(64, names.size());
        assertEquals("", NONE.modelSuffix());
        assertEquals("_neud", with("neud").modelSuffix());
    }
}
