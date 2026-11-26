package com.orevault.orevault.mining;

import com.orevault.orevault.core.mining.XpRule;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The only caller of {@link BlockDropsEvent#setDroppedExperience} in the mod (§3.4, §11 Drop Pipeline; an ArchUnit rule
 * holds it). The event already carries the vanilla amount, after Silk Touch, when the first listener runs
 * ({@code docs/research/neoforge-26.1-sources.md}, "BlockDropsEvent experience accessors"), so a node can only add to it
 * through {@link XpRule#raise}, which never lowers it. Nothing cancels the event either: cancelling drops the XP too.
 */
public final class DroppedXp {
    private DroppedXp() {
    }

    /** Adds {@code bonus} vanilla XP to what the block drops. A bonus of 0 or less changes nothing. */
    public static void add(BlockDropsEvent event, int bonus) {
        int current = event.getDroppedExperience();
        int raised = XpRule.raise(current, bonus);
        if (raised != current) {
            event.setDroppedExperience(raised);
        }
    }
}
