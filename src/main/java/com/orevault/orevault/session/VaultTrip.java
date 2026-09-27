package com.orevault.orevault.session;

import com.orevault.orevault.skill.NodeCosts;

/**
 * What one player has done on one visit to a Vault (§6.1).
 *
 * <p>Deep Habit counts blocks broken on this trip, Long Delve counts unbroken minutes spent on it,
 * and Apprentice's Ledger counts the trip's first ore. Three nodes, one record: three separate
 * counters would eventually disagree about when the trip started, and the first symptom of that is
 * a bonus that survives a hearth out.</p>
 *
 * <p>Deliberately <b>not</b> {@code SavedData}. A trip does not survive a logout — log out inside a
 * Vault and back in and you are on a new trip. That is the simpler rule, and the one that cannot be
 * farmed by relogging to bank a Delve stack.</p>
 *
 * <p>No Minecraft imports: the caller supplies the game time, so the AFK guard and the minute
 * arithmetic are testable without a world. {@link VaultSessions} owns every instance and is the
 * only thing that ticks one.</p>
 */
public final class VaultTrip {

    /** Ticks a block break keeps Delve time accruing for (§6.1 Long Delve's AFK guard). */
    private static final long IDLE_GUARD_TICKS = NodeCosts.LONG_DELVE_IDLE_SECONDS * 20L;
    private static final long TICKS_PER_MINUTE = 20L * 60L;

    /** Sentinel for "has broken nothing yet", so the guard is closed at the start of a trip. */
    private static final long NEVER = Long.MIN_VALUE;

    private final long startGameTime;

    private long blocksBroken;
    private long oresBroken;
    private long lastBreakGameTime = NEVER;

    /** Ticks spent inside the guard — the number Long Delve's stacks come from. */
    private long activeTicks;

    /**
     * Wall-clock ticks not yet written to {@code PlayerStats#timeInVaultTicks}.
     *
     * <p>Batched because the stat lives in {@code SavedData}: dirtying a team's save file once a
     * tick per member, forever, to move a counter nobody reads in real time is not a trade worth
     * making.</p>
     */
    private long unrecordedTicks;

    /**
     * The player's exhaustion as of the last tick, or NaN before the first one.
     *
     * <p>Long Delve reduces hunger drain, and there is no event for exhaustion and no vanilla
     * accessor for it — so the only way to take a percentage off a rise is to know what it rose
     * from. That baseline is per-trip state like everything else here.</p>
     */
    private float lastSeenExhaustion = Float.NaN;

    public VaultTrip(long startGameTime) {
        this.startGameTime = startGameTime;
    }

    /** Game time the trip began, for the diag readout. */
    public long startGameTime() {
        return startGameTime;
    }

    // ----- recording -----

    /** A block the player broke inside the Vault. Opens the AFK guard for the next 60 seconds. */
    public void recordBlockBroken(long gameTime) {
        blocksBroken++;
        lastBreakGameTime = gameTime;
    }

    /** An ore the player broke: counts as a block broken as well, matching {@code PlayerStats}. */
    public void recordOreBroken(long gameTime) {
        oresBroken++;
        recordBlockBroken(gameTime);
    }

    /**
     * One player tick inside the Vault.
     *
     * <p>Wall-clock time always counts; Delve time counts only inside the guard. Conflating the two
     * would either pay for standing still or under-report the player's time in the dimension, and
     * there is a node reading each of them.</p>
     */
    public void onTick(long gameTime) {
        unrecordedTicks++;
        if (lastBreakGameTime != NEVER && gameTime - lastBreakGameTime <= IDLE_GUARD_TICKS) {
            activeTicks++;
        }
    }

    // ----- readouts -----

    public long blocksBroken() {
        return blocksBroken;
    }

    public long oresBroken() {
        return oresBroken;
    }

    /** Ticks that counted toward Long Delve; exposed for the tests and the diag readout. */
    public long activeTicks() {
        return activeTicks;
    }

    /** Whole unbroken minutes earned, which is what {@code NodeEffects#longDelveStacks} takes. */
    public long activeMinutes() {
        return activeTicks / TICKS_PER_MINUTE;
    }

    /**
     * How much of this tick's rise in exhaustion Long Delve pays for, updating the baseline.
     *
     * <p>Called every tick whether the node is bought or not, with {@code reduction} at zero when it
     * is not, so the baseline never goes stale. A stale baseline would hand the player a refund for
     * every point of hunger they burned while the node was off.</p>
     *
     * <p>Only a rise is refunded. Vanilla's food tick spends exhaustion in 4-point chunks, so the
     * value falls as well as climbs, and treating a fall as a negative rise would charge the player
     * for eating.</p>
     *
     * @param current   the player's exhaustion right now
     * @param reduction fraction of the rise to refund, 0..1
     * @return the amount to subtract from {@code current}, never more than the rise itself
     */
    public float refundExhaustion(float current, double reduction) {
        if (Float.isNaN(lastSeenExhaustion)) {
            lastSeenExhaustion = current;
            return 0.0f;
        }
        float rise = current - lastSeenExhaustion;
        if (rise <= 0.0f || reduction <= 0.0) {
            lastSeenExhaustion = current;
            return 0.0f;
        }
        float refund = (float) (rise * Math.min(1.0, reduction));
        lastSeenExhaustion = current - refund;
        return refund;
    }

    /** Hands over the wall-clock ticks owed to {@code PlayerStats} and clears the tally. */
    public long drainUnrecordedTicks() {
        long owed = unrecordedTicks;
        unrecordedTicks = 0;
        return owed;
    }
}
