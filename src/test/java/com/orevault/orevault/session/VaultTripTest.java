package com.orevault.orevault.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orevault.orevault.skill.NodeCosts;

import org.junit.jupiter.api.Test;

/**
 * The per-trip counters Deep Habit, Long Delve and Apprentice's Ledger all read (§6.1).
 *
 * <p>Three nodes sharing one record is the whole point — three separate counters would eventually
 * disagree about when a trip started. What has to hold is that the record counts what a player did
 * on this visit and nothing else: it starts at zero, it does not pay for standing still, and it is
 * never carried across a trip boundary.</p>
 */
class VaultTripTest {

    private static final long IDLE_TICKS = NodeCosts.LONG_DELVE_IDLE_SECONDS * 20L;
    private static final long MINUTE_TICKS = 20L * 60L;

    /** Runs {@code ticks} of player ticks from {@code from}, breaking nothing. */
    private static void idle(VaultTrip trip, long from, long ticks) {
        for (long t = 0; t < ticks; t++) {
            trip.onTick(from + t);
        }
    }

    /** Runs {@code ticks} of player ticks from {@code from}, breaking a block every second. */
    private static void mine(VaultTrip trip, long from, long ticks) {
        for (long t = 0; t < ticks; t++) {
            long now = from + t;
            if (t % 20 == 0) {
                trip.recordBlockBroken(now);
            }
            trip.onTick(now);
        }
    }

    // ----- what a fresh trip knows -----

    @Test
    void aFreshTripHasNothingOnIt() {
        VaultTrip trip = new VaultTrip(1_000L);

        assertEquals(0L, trip.blocksBroken());
        assertEquals(0L, trip.oresBroken());
        assertEquals(0L, trip.activeMinutes());
    }

    @Test
    void anOreCountsAsABlockBrokenToo() {
        VaultTrip trip = new VaultTrip(0L);

        trip.recordOreBroken(5L);

        assertEquals(1L, trip.blocksBroken());
        assertEquals(1L, trip.oresBroken());
    }

    // ----- the AFK guard (§6.1 Long Delve) -----

    @Test
    void standingStillEarnsNoDelveTime() {
        VaultTrip trip = new VaultTrip(0L);

        idle(trip, 0L, MINUTE_TICKS * 30);

        assertEquals(0L, trip.activeMinutes());
    }

    @Test
    void miningSteadilyEarnsAMinutePerMinute() {
        VaultTrip trip = new VaultTrip(0L);

        mine(trip, 0L, MINUTE_TICKS * 21);

        assertEquals(21L, trip.activeMinutes());
    }

    @Test
    void timeKeepsAccruingThroughAPauseShorterThanTheGuard() {
        // A player walking between veins is not AFK. The guard is 60 seconds; a 30-second
        // pause must not cost them the stack they were most of the way toward.
        VaultTrip trip = new VaultTrip(0L);

        trip.recordBlockBroken(0L);
        idle(trip, 1L, IDLE_TICKS / 2);

        assertEquals(IDLE_TICKS / 2, trip.activeTicks());
    }

    @Test
    void timeStopsAccruingOnceTheGuardLapses() {
        VaultTrip trip = new VaultTrip(0L);

        trip.recordBlockBroken(0L);
        idle(trip, 1L, IDLE_TICKS * 10);

        // Every tick within the guard counts, and not one after it.
        assertEquals(IDLE_TICKS, trip.activeTicks());
    }

    @Test
    void breakingAgainRestartsTheGuardWithoutRestartingTheTrip() {
        VaultTrip trip = new VaultTrip(0L);

        trip.recordBlockBroken(0L);
        idle(trip, 1L, IDLE_TICKS * 5);
        long afterTheLapse = trip.activeTicks();

        trip.recordBlockBroken(IDLE_TICKS * 6);
        idle(trip, IDLE_TICKS * 6 + 1, 100);

        assertEquals(afterTheLapse + 100, trip.activeTicks());
        assertEquals(2L, trip.blocksBroken());
    }

    @Test
    void tickingBeforeTheFirstBreakEarnsNothing() {
        // The guard is "broke a block in the last 60 seconds", and a player who has broken
        // nothing has not. Without this, the first minute of every trip is free.
        VaultTrip trip = new VaultTrip(0L);

        idle(trip, 0L, IDLE_TICKS - 1);

        assertEquals(0L, trip.activeTicks());
    }

    // ----- the stat flush -----

    @Test
    void wallClockTicksDrainOnceAndOnlyOnce() {
        // PlayerStats#timeInVaultTicks is written in batches so the SavedData is not
        // dirtied every tick. Draining twice would double a player's recorded time.
        VaultTrip trip = new VaultTrip(0L);

        idle(trip, 0L, 45);

        assertEquals(45L, trip.drainUnrecordedTicks());
        assertEquals(0L, trip.drainUnrecordedTicks());
    }

    // ----- Long Delve's hunger reduction -----

    @Test
    void theFirstExhaustionReadingIsOnlyABaseline() {
        // There is nothing to compare a first reading against. Refunding it would hand the
        // player back everything they burned before they walked in.
        VaultTrip trip = new VaultTrip(0L);

        assertEquals(0.0f, trip.refundExhaustion(3.5f, 1.0), 1.0e-6f);
    }

    @Test
    void halfOfARiseIsRefundedAtHalfReduction() {
        VaultTrip trip = new VaultTrip(0L);
        trip.refundExhaustion(0.0f, 0.5);

        assertEquals(0.05f, trip.refundExhaustion(0.1f, 0.5), 1.0e-6f);
    }

    @Test
    void afullReductionRefundsTheWholeRise() {
        VaultTrip trip = new VaultTrip(0L);
        trip.refundExhaustion(0.0f, 1.0);

        assertEquals(0.1f, trip.refundExhaustion(0.1f, 1.0), 1.0e-6f);
    }

    @Test
    void aFallInExhaustionIsNotChargedFor() {
        // Vanilla spends exhaustion in 4-point chunks, so the value drops as well as climbs.
        // Treating a drop as a negative rise would take hunger off the player for eating.
        VaultTrip trip = new VaultTrip(0L);
        trip.refundExhaustion(5.0f, 1.0);

        assertEquals(0.0f, trip.refundExhaustion(1.0f, 1.0), 1.0e-6f);
    }

    @Test
    void theBaselineKeepsUpWhileTheNodeIsUnbought() {
        // Called every tick with a reduction of zero, so that buying the node mid-trip does not
        // refund the whole trip's hunger in one tick.
        VaultTrip trip = new VaultTrip(0L);
        trip.refundExhaustion(0.0f, 0.0);
        trip.refundExhaustion(3.0f, 0.0);

        assertEquals(0.5f, trip.refundExhaustion(4.0f, 0.5), 1.0e-6f);
    }

    @Test
    void refundingTracksTheValueThePlayerIsLeftWith() {
        // The refund is subtracted from the player's exhaustion by the caller, so the next
        // tick's rise has to be measured from the reduced value, not the one that was read.
        VaultTrip trip = new VaultTrip(0L);
        trip.refundExhaustion(0.0f, 1.0);
        trip.refundExhaustion(1.0f, 1.0); // player is left at 0.0

        assertEquals(0.5f, trip.refundExhaustion(0.5f, 1.0), 1.0e-6f);
    }

    @Test
    void wallClockTimeCountsWhileAfkEvenThoughDelveTimeDoesNot() {
        // Time in the Vault is time in the Vault — the AFK guard belongs to Long Delve,
        // not to the stat. Conflating them would under-report every player's playtime.
        VaultTrip trip = new VaultTrip(0L);

        idle(trip, 0L, 200);

        assertEquals(200L, trip.drainUnrecordedTicks());
        assertEquals(0L, trip.activeTicks());
    }
}
