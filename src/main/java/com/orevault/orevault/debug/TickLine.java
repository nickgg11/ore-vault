package com.orevault.orevault.debug;

/**
 * One line of the Vault tick logger (E3-S11, #245), the evidence QA's headless restart check reads that a restored Vault
 * ticks (ADR 0002 "NeoForge bumps", QA-38), and that a removed one has stopped.
 */
record TickLine(String dimension, int serverTick) {
    /** Once a second. */
    static final int EVERY_TICKS = 20;

    static boolean due(int serverTick) {
        return serverTick % EVERY_TICKS == 0;
    }

    String line() {
        return "tick " + dimension + " server_tick=" + serverTick;
    }
}
