package com.orevault.orevault.debug;

import java.util.List;

/**
 * What {@code /orevault diag} prints. Old #82 looked like a protection mod denying breaks and was a level that never
 * ticked (#89), which nothing denies. {@code serverTick} and {@code levelGameTime} tell those apart: over two runs,
 * the server tick moving while the level's game time stands still means the level is not being ticked.
 */
record BreakDiagnosis(
        String dimension,
        int serverTick,
        long levelGameTime,
        String pos,
        String block,
        float destroyProgress,
        boolean mayInteract,
        boolean spawnProtected,
        boolean insideBorder,
        double borderSize,
        String gameMode) {
    List<String> lines() {
        return List.of(
                "dim=" + dimension + " serverTick=" + serverTick + " levelGameTime=" + levelGameTime,
                "pos=" + pos + " block=" + block + " destroyProgress=" + destroyProgress,
                "mayInteract=" + mayInteract + " spawnProtection=" + spawnProtected
                        + " insideBorder=" + insideBorder + " borderSize=" + borderSize,
                "gameMode=" + gameMode);
    }
}
