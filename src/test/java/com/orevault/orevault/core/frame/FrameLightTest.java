package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orevault.orevault.core.frame.FrameLight.PortalView;
import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** §3.2 Frame colour, around a frame block at the origin. */
class FrameLightTest {
    private record Key(int x, int y, int z, Axis axis) {
    }

    private static final class World implements PortalView {
        private final Map<Key, Integer> tiers = new HashMap<>();

        World portal(int x, int y, int z, Axis axis, int tier) {
            tiers.put(new Key(x, y, z, axis), tier);
            return this;
        }

        @Override
        public int tierAt(int x, int y, int z, Axis axis) {
            return tiers.getOrDefault(new Key(x, y, z, axis), 0);
        }
    }

    /** The eight cells around the origin in one axis's plane. */
    private static List<Key> plane(Axis axis) {
        List<Key> cells = new ArrayList<>();
        for (int along = -1; along <= 1; along++) {
            for (int y = -1; y <= 1; y++) {
                if (along != 0 || y != 0) {
                    cells.add(axis == Axis.X ? new Key(along, y, 0, axis) : new Key(0, y, along, axis));
                }
            }
        }
        return cells;
    }

    static List<Key> everyOffset() {
        List<Key> all = new ArrayList<>(plane(Axis.X));
        all.addAll(plane(Axis.Z));
        return all;
    }

    @Test
    void nothingAroundIsGrey() {
        assertEquals(0, FrameLight.tierAround(0, 0, 0, new World()));
    }

    @ParameterizedTest
    @MethodSource("everyOffset")
    void eachOffsetLights(Key cell) {
        World world = new World().portal(cell.x(), cell.y(), cell.z(), cell.axis(), 3);
        assertEquals(3, FrameLight.tierAround(0, 0, 0, world));
    }

    @ParameterizedTest
    @MethodSource("everyOffset")
    void aCellOfTheOtherAxisDoesNotLight(Key cell) {
        // The two cells straight above and below sit in both planes, and count in both.
        if (cell.x() == 0 && cell.z() == 0) {
            return;
        }
        Axis other = cell.axis() == Axis.X ? Axis.Z : Axis.X;
        World world = new World().portal(cell.x(), cell.y(), cell.z(), other, 3);
        assertEquals(0, FrameLight.tierAround(0, 0, 0, world));
    }

    @Test
    void thereAreFourteenPositions() {
        Set<List<Integer>> positions = new HashSet<>();
        PortalView recording = (x, y, z, axis) -> {
            positions.add(List.of(x, y, z));
            return 0;
        };
        FrameLight.tierAround(5, 70, -3, recording);
        assertEquals(14, positions.size());
    }

    @Test
    void itReadsAroundTheBlockItWasAsked() {
        World world = new World().portal(11, 70, 20, Axis.X, 2);
        assertEquals(2, FrameLight.tierAround(10, 71, 20, world));
        assertEquals(0, FrameLight.tierAround(0, 0, 0, world));
    }

    @Test
    void cellsBehindOrInFrontDoNotLight() {
        World world = new World().portal(0, 0, 1, Axis.X, 4).portal(1, 0, 0, Axis.Z, 4).portal(2, 0, 0, Axis.X, 4);
        assertEquals(0, FrameLight.tierAround(0, 0, 0, world));
    }

    @Test
    void theHighestTierWinsOnEitherAxis() {
        assertEquals(4, FrameLight.tierAround(0, 0, 0, new World().portal(1, 0, 0, Axis.X, 2).portal(0, 0, 1, Axis.Z, 4)));
        assertEquals(4, FrameLight.tierAround(0, 0, 0, new World().portal(1, 0, 0, Axis.X, 4).portal(0, 0, 1, Axis.Z, 2)));
    }

    /** Read in both orders, so the last cell read cannot be the one that wins. */
    @Test
    void theHighestTierWinsWithinOnePlane() {
        assertEquals(4, FrameLight.tierAround(0, 0, 0, new World().portal(-1, 0, 0, Axis.X, 4).portal(1, 0, 0, Axis.X, 2)));
        assertEquals(4, FrameLight.tierAround(0, 0, 0, new World().portal(-1, 0, 0, Axis.X, 2).portal(1, 0, 0, Axis.X, 4)));
        assertEquals(4, FrameLight.tierAround(0, 0, 0, new World().portal(0, 0, -1, Axis.Z, 4).portal(0, 0, 1, Axis.Z, 2)));
        assertEquals(4, FrameLight.tierAround(0, 0, 0, new World().portal(0, 0, -1, Axis.Z, 2).portal(0, 0, 1, Axis.Z, 4)));
    }

    @Test
    void runesUseTheirOwnTierOne() {
        assertEquals(0xFF74767C, FrameLight.runeColor(0));
        assertEquals(0xFF148031, FrameLight.runeColor(1));
        assertEquals(0xFF316CFF, FrameLight.runeColor(2));
        assertEquals(0xFFA038D0, FrameLight.runeColor(3));
        assertEquals(0xFFFF8000, FrameLight.runeColor(4));
    }

    @Test
    void theStripUsesTheLadder() {
        assertEquals(0xFF0D7026, FrameLight.portalColor(1));
        assertEquals(0xFF316CFF, FrameLight.portalColor(2));
        assertEquals(0xFFA038D0, FrameLight.portalColor(3));
        assertEquals(0xFFFF8000, FrameLight.portalColor(4));
    }
}
