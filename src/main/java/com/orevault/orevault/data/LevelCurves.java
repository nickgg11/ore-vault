package com.orevault.orevault.data;

import com.orevault.orevault.core.curve.LevelCurve;
import java.util.Optional;

/**
 * The level curve computed at server start (§4.3 step 7), held for the award (E5-S4, #274). Main-thread only. Static, so
 * the root listener clears it at {@code ServerStoppedEvent} and a second world in one client derives its own from its own
 * config.
 */
public final class LevelCurves {
    private static LevelCurve current;

    private LevelCurves() {
    }

    public static void publish(LevelCurve curve) {
        current = curve;
    }

    /** Empty before the server has started and after it stopped. */
    public static Optional<LevelCurve> current() {
        return Optional.ofNullable(current);
    }

    public static void clear() {
        current = null;
    }
}
