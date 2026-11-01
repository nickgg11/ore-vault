package com.orevault.orevault.core.igniter;

import java.util.ArrayList;
import java.util.List;

/**
 * The igniter ladder of §3.3. Callers ask what a tier can do and never which tier it is: instant travel has
 * already moved tiers once (old #122), and the tooltip is built here from the same accessors so it cannot go on
 * claiming the old answer (old #125). All four tiers answer now, though only {@link #CRUDE} has an item yet.
 */
public enum IgniterTier {
    CRUDE(0, false, false, true),
    ATTUNED(1, false, false, true),
    RESONANT(3, true, false, true),
    SOVEREIGN(3, true, true, false);

    private static final String PREFIX = "tooltip.orevault.igniter.";
    private static final String PURPOSE = PREFIX + "purpose";
    private static final String OPENS_PORTAL = PREFIX + "opens_portal";
    private static final String ENTRY_POINT = PREFIX + "entry_point";
    private static final String ENTRY_POINTS = PREFIX + "entry_points";
    private static final String INSTANT_TRAVEL = PREFIX + "instant_travel";
    private static final String RESET_VAULT = PREFIX + "reset_vault";
    private static final String UPGRADE = PREFIX + "upgrade";

    private final int entryPointCapacity;
    private final boolean instantTravel;
    private final boolean resetVault;
    private final boolean upgradable;

    IgniterTier(int entryPointCapacity, boolean instantTravel, boolean resetVault, boolean upgradable) {
        this.entryPointCapacity = entryPointCapacity;
        this.instantTravel = instantTravel;
        this.resetVault = resetVault;
        this.upgradable = upgradable;
    }

    /**
     * One line of the tooltip.
     *
     * @param key the lang key; tiers share keys, so one capability has one string
     * @param args the values for the key's placeholders
     * @param hint true for the upgrade hint, which is drawn fainter than a capability
     */
    public record TooltipLine(String key, List<Integer> args, boolean hint) {
        private static TooltipLine of(String key) {
            return new TooltipLine(key, List.of(), false);
        }
    }

    public int entryPointCapacity() {
        return entryPointCapacity;
    }

    public boolean hasInstantTravel() {
        return instantTravel;
    }

    public boolean canResetVault() {
        return resetVault;
    }

    /**
     * What the item is for, then this tier's capabilities with those it inherits, then the upgrade hint. The
     * hint is last so it reads as a next step, not as a limit on the item in hand (old #125).
     */
    public List<TooltipLine> tooltipLines() {
        List<TooltipLine> lines = new ArrayList<>(List.of(TooltipLine.of(PURPOSE), TooltipLine.of(OPENS_PORTAL)));
        if (entryPointCapacity == 1) {
            lines.add(TooltipLine.of(ENTRY_POINT));
        } else if (entryPointCapacity > 1) {
            lines.add(new TooltipLine(ENTRY_POINTS, List.of(entryPointCapacity), false));
        }
        if (hasInstantTravel()) {
            lines.add(TooltipLine.of(INSTANT_TRAVEL));
        }
        if (canResetVault()) {
            lines.add(TooltipLine.of(RESET_VAULT));
        }
        if (upgradable) {
            lines.add(new TooltipLine(UPGRADE, List.of(), true));
        }
        return List.copyOf(lines);
    }

    /** Every key any tier can show, for the lang coverage check: tiers 2 to 4 have no item to reach theirs yet. */
    public static List<String> everyTooltipKey() {
        return List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT, UPGRADE);
    }
}
