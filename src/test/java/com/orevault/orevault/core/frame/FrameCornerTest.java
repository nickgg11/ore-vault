package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.frame.FrameCorner.Box;
import com.orevault.orevault.core.frame.FrameCorner.Element;
import com.orevault.orevault.core.frame.FrameCorner.ElementFace;
import com.orevault.orevault.core.frame.FrameCorner.Surface;
import com.orevault.orevault.core.frame.FrameNeighbours.Face;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The geometry of a rounded Vault Frame corner (§3.2). The expected values were read from the Designer's solved data. */
class FrameCornerTest {
    private static final int FULL = 16;
    private static final int BOXES = 11;
    private static final int WEDGE_TEXELS = 63;

    private static FrameNeighbours with(String sides) {
        return new FrameNeighbours(sides.contains("n"), sides.contains("e"), sides.contains("s"), sides.contains("w"),
                sides.contains("u"), sides.contains("d"));
    }

    private static FrameCorner corner(String sides) {
        return FrameCorner.of(with(sides)).orElseThrow();
    }

    private static boolean twoOnPerpendicularAxes(FrameNeighbours block) {
        int x = (block.east() ? 1 : 0) + (block.west() ? 1 : 0);
        int y = (block.up() ? 1 : 0) + (block.down() ? 1 : 0);
        int z = (block.north() ? 1 : 0) + (block.south() ? 1 : 0);
        return x + y + z == 2 && x < 2 && y < 2 && z < 2;
    }

    private static ElementFace surface(Element element, Surface surface) {
        return element.faces().stream().filter(face -> face.surface() == surface).findFirst().orElseThrow();
    }

    private static ElementFace on(Element element, Face side) {
        return element.faces().stream().filter(face -> face.face() == side).findFirst().orElseThrow();
    }

    private static String uv(ElementFace face) {
        return face.u0() + " " + face.v0() + " " + face.u1() + " " + face.v1() + " r" + face.rotation()
                + (face.culled() ? " culled" : "");
    }

    @Test
    void exactlyTheTwoNeighbourStatesOnPerpendicularAxesAreRounded() {
        int rounded = 0;
        for (FrameNeighbours block : FrameNeighbours.all()) {
            assertEquals(twoOnPerpendicularAxes(block), FrameCorner.of(block).isPresent(), block.toString());
            rounded += FrameCorner.of(block).isPresent() ? 1 : 0;
        }
        assertEquals(12, rounded);
    }

    @Test
    void aBarALoneBlockAndAJunctionStayCubes() {
        assertEquals(Optional.empty(), FrameCorner.of(with("")));
        assertEquals(Optional.empty(), FrameCorner.of(with("ew")));
        assertEquals(Optional.empty(), FrameCorner.of(with("ud")));
        assertEquals(Optional.empty(), FrameCorner.of(with("e")));
        assertEquals(Optional.empty(), FrameCorner.of(with("eud")));
    }

    @Test
    void eachCornerAnswersForTheNeighboursItWasAskedAbout() {
        for (FrameCorner corner : FrameCorner.values()) {
            assertEquals(corner, FrameCorner.of(corner.neighbours()).orElseThrow());
        }
    }

    @Test
    void theTopLeftCornerRunsDownTheCurveFromItsTopRow() {
        List<Box> boxes = corner("ed").elements().stream().map(Element::box).toList();
        assertEquals(new Box(13, 15, 0, 16, 16, 16), boxes.getFirst());
        assertEquals(new Box(10, 14, 0, 16, 15, 16), boxes.get(1));
        assertEquals(new Box(0, 0, 0, 16, 3, 16), boxes.getLast());
    }

    @Test
    void theOtherAxesMirrorTheTopLeftCorner() {
        assertEquals(new Box(0, 0, 13, 16, 1, 16), corner("su").elements().getFirst().box());
        assertEquals(new Box(0, 0, 0, 1, 16, 3), corner("ne").elements().getFirst().box());
        assertEquals(new Box(15, 0, 13, 16, 16, 16), corner("sw").elements().getFirst().box());
    }

    @Test
    void everyCornerFillsTheBlockButTheWedgeWithElevenBoxesThatNeverOverlap() {
        for (FrameCorner corner : FrameCorner.values()) {
            Set<List<Integer>> filled = new HashSet<>();
            int volume = 0;
            List<Element> elements = corner.elements();
            assertEquals(BOXES, elements.size(), corner.name());
            for (Element element : elements) {
                Box box = element.box();
                for (int x = box.fromX(); x < box.toX(); x++) {
                    for (int y = box.fromY(); y < box.toY(); y++) {
                        for (int z = box.fromZ(); z < box.toZ(); z++) {
                            filled.add(List.of(x, y, z));
                            volume++;
                        }
                    }
                }
            }
            assertEquals(volume, filled.size(), corner.name() + " overlaps");
            assertEquals(FULL * (FULL * FULL - WEDGE_TEXELS), volume, corner.name());
        }
    }

    @Test
    void theCurveIsSymmetricAboutItsDiagonal() {
        boolean[][] kept = new boolean[FULL][FULL];
        for (Element element : corner("ed").elements()) {
            Box box = element.box();
            for (int x = box.fromX(); x < box.toX(); x++) {
                for (int y = box.fromY(); y < box.toY(); y++) {
                    kept[x][y] = true;
                }
            }
        }
        for (int x = 0; x < FULL; x++) {
            for (int y = 0; y < FULL; y++) {
                assertEquals(kept[x][y], kept[FULL - 1 - y][FULL - 1 - x], x + "," + y);
            }
        }
    }

    @Test
    void bothArmSidesAreWholeSoTheBarsMeetTheCornerWithNoGap() {
        for (FrameCorner corner : FrameCorner.values()) {
            for (Face arm : corner.arms()) {
                int area = corner.elements().stream().mapToInt(element -> areaOnSide(element.box(), arm)).sum();
                assertEquals(FULL * FULL, area, corner.name() + " " + arm);
            }
        }
    }

    private static int areaOnSide(Box box, Face side) {
        int width = box.toX() - box.fromX();
        int height = box.toY() - box.fromY();
        int depth = box.toZ() - box.fromZ();
        return switch (side) {
            case EAST -> box.toX() == FULL ? height * depth : 0;
            case WEST -> box.fromX() == 0 ? height * depth : 0;
            case UP -> box.toY() == FULL ? width * depth : 0;
            case DOWN -> box.fromY() == 0 ? width * depth : 0;
            case SOUTH -> box.toZ() == FULL ? width * height : 0;
            case NORTH -> box.fromZ() == 0 ? width * height : 0;
        };
    }

    @Test
    void theFourFacesAreTheTwoPlaneFacesTheTreadAndTheRiser() {
        for (FrameCorner corner : FrameCorner.values()) {
            for (Element element : corner.elements()) {
                List<Surface> surfaces = element.faces().stream().map(ElementFace::surface).toList();
                assertEquals(List.of(Surface.PLANE, Surface.PLANE, Surface.TREAD, Surface.RISER), surfaces);
            }
        }
    }

    @Test
    void theTreadAndRiserFaceTheTwoExposedSides() {
        Map<String, List<Face>> expected = Map.ofEntries(
                Map.entry("ed", List.of(Face.UP, Face.WEST)),
                Map.entry("wd", List.of(Face.UP, Face.EAST)),
                Map.entry("eu", List.of(Face.DOWN, Face.WEST)),
                Map.entry("wu", List.of(Face.DOWN, Face.EAST)),
                Map.entry("sd", List.of(Face.UP, Face.NORTH)),
                Map.entry("nd", List.of(Face.UP, Face.SOUTH)),
                Map.entry("su", List.of(Face.DOWN, Face.NORTH)),
                Map.entry("nu", List.of(Face.DOWN, Face.SOUTH)),
                Map.entry("ne", List.of(Face.WEST, Face.SOUTH)),
                Map.entry("nw", List.of(Face.EAST, Face.SOUTH)),
                Map.entry("es", List.of(Face.WEST, Face.NORTH)),
                Map.entry("sw", List.of(Face.EAST, Face.NORTH)));
        expected.forEach((sides, faces) -> {
            assertEquals(faces.get(0), corner(sides).tread(), sides);
            assertEquals(faces.get(1), corner(sides).riser(), sides);
        });
    }

    @Test
    void aPlaneFaceKeepsTheDefaultUvOfItsBox() {
        Element top = corner("ed").elements().getFirst();
        assertEquals("13 0 16 1 r0 culled", uv(on(top, Face.SOUTH)));
        assertEquals("0 0 3 1 r0 culled", uv(on(top, Face.NORTH)));
        Element flat = corner("ne").elements().getFirst();
        assertEquals("0 13 1 16 r0 culled", uv(on(flat, Face.DOWN)));
        assertEquals("0 0 1 3 r0 culled", uv(on(flat, Face.UP)));
    }

    /** A wrong UV here only shows as a seam in game, so every state is pinned. */
    @Test
    void theTreadOfTheTopRowHasTheSolvedUvInEveryState() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("ed", "13 0 16 16 r0 culled"),
                Map.entry("wd", "16 0 13 16 r0 culled"),
                Map.entry("eu", "13 0 16 16 r0 culled"),
                Map.entry("wu", "16 0 13 16 r0 culled"),
                Map.entry("sd", "13 16 16 0 r90 culled"),
                Map.entry("nd", "16 16 13 0 r90 culled"),
                Map.entry("su", "16 16 13 0 r90 culled"),
                Map.entry("nu", "13 16 16 0 r90 culled"),
                Map.entry("ne", "16 0 13 16 r0 culled"),
                Map.entry("nw", "13 0 16 16 r0 culled"),
                Map.entry("es", "13 0 16 16 r0 culled"),
                Map.entry("sw", "16 0 13 16 r0 culled"));
        expected.forEach((sides, value) ->
                assertEquals(value, uv(surface(corner(sides).elements().getFirst(), Surface.TREAD)), sides));
    }

    @Test
    void theRiserOfTheThirdRowFromTheEndHasTheSolvedUvInEveryState() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("ed", "0 10 16 13 r0"),
                Map.entry("wd", "16 10 0 13 r0"),
                Map.entry("eu", "16 13 0 10 r0"),
                Map.entry("wu", "0 13 16 10 r0"),
                Map.entry("sd", "16 10 0 13 r0"),
                Map.entry("nd", "0 10 16 13 r0"),
                Map.entry("su", "16 13 0 10 r0"),
                Map.entry("nu", "0 13 16 10 r0"),
                Map.entry("ne", "0 13 16 10 r90"),
                Map.entry("nw", "0 10 16 13 r90"),
                Map.entry("es", "0 10 16 13 r90"),
                Map.entry("sw", "0 13 16 10 r90"));
        expected.forEach((sides, value) ->
                assertEquals(value, uv(surface(corner(sides).elements().get(9), Surface.RISER)), sides));
    }

    @Test
    void theRimCullsOnlyWhereItReachesTheBlockBoundary() {
        for (FrameCorner corner : FrameCorner.values()) {
            List<Element> elements = corner.elements();
            for (int row = 0; row < elements.size(); row++) {
                assertEquals(row == 0, surface(elements.get(row), Surface.TREAD).culled(), corner.name() + " tread " + row);
                assertEquals(row == elements.size() - 1, surface(elements.get(row), Surface.RISER).culled(),
                        corner.name() + " riser " + row);
            }
        }
    }

    @Test
    void theMixedRimIsUsedWhereTheTwoBarsDisagreeAboutTheLitEdge() {
        Set<String> mixed = Set.of("wd", "eu", "sd", "su");
        for (String sides : List.of("ed", "wd", "eu", "wu", "sd", "nd", "su", "nu", "ne", "nw", "es", "sw")) {
            assertEquals(mixed.contains(sides), corner(sides).mixedRim(), sides);
        }
    }

    @Test
    void theRimTexturesExistUnderTheNamesTheCornersAskFor() {
        Path folder = Path.of(System.getProperty("orevault.projectDir"), "src/main/resources/assets/orevault/textures/block");
        for (FrameCorner corner : FrameCorner.values()) {
            assertTrue(Files.exists(folder.resolve("vault_frame" + corner.treadTexture() + ".png")), corner.name());
            assertTrue(Files.exists(folder.resolve("vault_frame" + corner.riserTexture() + ".png")), corner.name());
        }
        assertEquals("_rim_tread_mixed", corner("wd").treadTexture());
        assertEquals("_rim_riser", corner("ed").riserTexture());
    }
}
