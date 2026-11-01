package com.orevault.orevault.debug;

/**
 * What {@code /orevault break} found, as one line a restart script can read (S44, QA-38).
 *
 * @param empty whether the position held air, so there was nothing to break and nothing is proved
 * @param destroyed what {@code destroyBlock} returned
 * @param gone whether the block has left the world
 */
record BreakReport(String dimension, int x, int y, int z, String block, boolean empty, boolean destroyed, boolean gone) {
    String line() {
        String where = "break " + dimension + " " + x + " " + y + " " + z + " " + block + ": ";
        return where + (empty ? "nothing to break" : "destroyBlock=" + destroyed + " gone=" + gone);
    }
}
