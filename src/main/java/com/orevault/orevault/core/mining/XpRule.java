package com.orevault.orevault.core.mining;

/**
 * The one rule for a block's vanilla XP: nodes add to it and nothing lowers it (§3.4 Vanilla XP is untouched, §11 Drop
 * Pipeline). A player who mines in the Vault must not end up behind one who mined the same ore outside. The adapter that
 * sets the event's value calls only this.
 */
public final class XpRule {
    private XpRule() {
    }

    /**
     * The XP after a bonus: {@code current} plus {@code bonus}, where a negative bonus counts as 0 and the sum saturates
     * at {@link Integer#MAX_VALUE}. Never below {@code current}.
     */
    public static int raise(int current, int bonus) {
        if (bonus <= 0) {
            return current;
        }
        return bonus > Integer.MAX_VALUE - current ? Integer.MAX_VALUE : current + bonus;
    }
}
