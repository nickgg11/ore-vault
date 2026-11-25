package com.orevault.orevault.data;

import com.orevault.orevault.core.curve.LevelCurve;

/**
 * The level curve computed at server start (§4.3 step 7), held for the award (E5-S4, #274) and the progress sync.
 * Main-thread only. Static, so the root listener clears it at {@code ServerStoppedEvent} and a second world in one client
 * derives its own from its own config.
 *
 * <p>The curve is published before the tick loop runs, and a failure to derive it stops the server, so a read before
 * {@code ServerStartedEvent} or after the stop is a bug. {@link #require} throws then, rather than handing the award an
 * empty value it could skip, which would drop ore payments with nothing in the log.
 */
public final class LevelCurves {
    private static LevelCurve current;

    private LevelCurves() {
    }

    public static void publish(LevelCurve curve) {
        current = curve;
    }

    /** @throws IllegalStateException before the server has started or after it stopped */
    public static LevelCurve require() {
        if (current == null) {
            throw new IllegalStateException("Level curve not published");
        }
        return current;
    }

    /** For a caller that must tell "not started" apart, such as a debug command; the award uses {@link #require}. */
    public static boolean isPublished() {
        return current != null;
    }

    public static void clear() {
        current = null;
    }
}
