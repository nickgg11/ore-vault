package com.orevault.orevault.core.mining;

/**
 * Who broke a block, in the one sense the mod cares about (§3.4 "What machine-broken means", S21). Computed once per
 * break, and Resonance, the per-block travel stats, Tempered, Ascending Edge and Echoing Edge all read the answer, so the
 * drop pipeline and the award can never disagree about what a machine is (old #26, old #92).
 */
public enum BreakSource {
    /** A real player, by hand. An FTB Ultimine chain is one of these per block (§9). */
    HAND,
    /** A machine, a fake player, an entity that is not a player, or no breaker at all. Never pays Resonance. */
    MACHINE;

    /**
     * @param serverPlayer the breaker is a {@code ServerPlayer}; false for no breaker and for any other entity
     * @param fakePlayer its {@code isFakePlayer()} answers true
     * @param listed it is the very instance the server's player list holds for its UUID, which catches a mod's fake player
     *     that extends {@code ServerPlayer} without overriding {@code isFakePlayer()}
     */
    public static BreakSource of(boolean serverPlayer, boolean fakePlayer, boolean listed) {
        return serverPlayer && !fakePlayer && listed ? HAND : MACHINE;
    }
}
