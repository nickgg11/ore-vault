package com.orevault.orevault.event;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.session.VaultSessions;
import com.orevault.orevault.session.VaultTrip;
import com.orevault.orevault.tags.ModTags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/**
 * Writes the §5.1 counters every breakpoint node reads.
 *
 * <p>{@code PlayerStats} has had a field and a recording method per counter since #7 and nothing has
 * ever called one, which is why six of the §6.1 nodes would have read zero forever.</p>
 *
 * <h2>Why this hangs off the drop pipeline rather than a break event</h2>
 *
 * <p>{@code BlockDropsEvent} is already the mod's single break listener and {@link VaultBreakContext}
 * has already answered which Vault, whose team, ore or not, and player or machine. Listening to
 * {@code BreakBlockEvent} as well would mean a second derivation of all four — and the comment on
 * {@code VaultBreakContext} exists because two derivations of "is this a machine" eventually
 * disagree about a block.</p>
 *
 * <p>The consequence, stated so it is a decision and not a surprise: a creative-mode break does not
 * reach {@code Block#dropResources} and so is not counted. That is the behaviour worth having anyway
 * — a creative player is not making progress — but it follows from the hook, so it is written down.</p>
 *
 * <p>§3.4: nothing a machine broke moves a player's stat. The block still counted for the world and
 * still ran the pipeline; it did not count for anybody's record.</p>
 */
public final class VaultStatRecorder {

    private VaultStatRecorder() {
    }

    /**
     * Records one Vault break against the breaker's lifetime stats and current trip.
     *
     * <p>Runs before the Resonance award, so the break being counted is included in the counters the
     * award's own bonuses read. That ordering matters to exactly one node — Deep Habit's thousandth
     * block pays on itself — and matching the spec's "blocks broken in a single trip" is the reading
     * that needs no footnote in a tooltip.</p>
     */
    static void record(VaultBreakContext context) {
        ServerPlayer player = context.player();
        if (player == null || context.machineBroken()) {
            return; // §3.4: machine breaks progress the world, not a player's record
        }

        PlayerStats stats = VaultSessions.statsFor(player);
        if (stats == null) {
            return;
        }

        int y = context.pos().getY();
        long gameTime = context.level().getGameTime();
        VaultTrip trip = VaultSessions.trip(player);

        if (context.isOre()) {
            stats.recordOreMined(BuiltInRegistries.BLOCK.getKey(context.state().getBlock()).toString(), y);
            if (trip != null) {
                trip.recordOreBroken(gameTime);
            }
            return;
        }

        // Stone is the tag, not a block list, so modded stone following either of the two
        // conventions #orevault:vault_stone includes counts with no per-mod work (§6.1).
        if (context.state().is(ModTags.Blocks.VAULT_STONE)) {
            stats.recordStoneBroken(y);
        } else {
            stats.recordBlockBroken(y);
        }
        if (trip != null) {
            trip.recordBlockBroken(gameTime);
        }
    }
}
