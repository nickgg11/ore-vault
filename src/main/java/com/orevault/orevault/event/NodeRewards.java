package com.orevault.orevault.event;

import java.util.ArrayList;
import java.util.List;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.session.VaultSessions;
import com.orevault.orevault.session.VaultTrip;

import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Node effects that pay a break in something other than drops or Resonance (§6.1).
 *
 * <p>Not everything a node gives out is an item or a number in the pool. Stonecutter's Patience pays
 * vanilla XP, Highwater Mark repairs the tool in your hand, and later nodes pay hunger, health and
 * status effects. None of those compose with each other, so unlike {@link DropPipeline} and
 * {@link ResonancePipeline} there is nothing to accumulate — this is a list of things that happen,
 * and it exists so they happen in one place with the §3.4 machine rule applied once.</p>
 *
 * <p>Runs after the drop pipeline and the Resonance award, so a reward can read what the break
 * actually produced.</p>
 */
public final class NodeRewards {

    /** One node's side effect on a player's own break. */
    @FunctionalInterface
    public interface Reward {
        void apply(VaultBreakContext context, ServerPlayer player, PlayerStats stats, @Nullable VaultTrip trip);
    }

    private static final List<Reward> REWARDS = new ArrayList<>();

    private NodeRewards() {
    }

    /** Registers a node's reward. Call during mod construction. */
    public static void register(Reward reward) {
        REWARDS.add(reward);
    }

    /** Runs every registered reward for one player break. */
    static void onBreak(VaultBreakContext context) {
        if (REWARDS.isEmpty()) {
            return;
        }
        ServerPlayer player = context.player();
        if (player == null || context.machineBroken()) {
            return; // §3.4
        }
        PlayerStats stats = VaultSessions.statsFor(player);
        if (stats == null) {
            return;
        }
        VaultTrip trip = VaultSessions.trip(player);
        for (Reward reward : REWARDS) {
            reward.apply(context, player, stats, trip);
        }
    }

    /** Test hook, matching {@code DropPipeline#clearForTest}. */
    static void clearForTest() {
        REWARDS.clear();
    }
}
