package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.Rarity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** §3.4 and §4.2: which ore breaks pay, and how much before any node. */
class BreakContextTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    private static BreakContext memberHand(Rarity rarity) {
        return new BreakContext(BreakSource.HAND, TEAM, true, rarity);
    }

    @Test
    void aMembersHandBreakOfAClassifiedOrePaysItsRarity() {
        assertEquals(2_000L, memberHand(Rarity.COMMON).awardThousandths());
        assertEquals(5_000L, memberHand(Rarity.UNCOMMON).awardThousandths());
        assertEquals(12_500L, memberHand(Rarity.RARE).awardThousandths());
        assertTrue(memberHand(Rarity.RARE).paysResonance());
    }

    /** Stone, deepslate and an ore the classifier could not read have no rarity, and there is no default (old PR #117). */
    @Test
    void anUnclassifiedBlockPaysNothing() {
        BreakContext stone = new BreakContext(BreakSource.HAND, TEAM, true, null);
        assertFalse(stone.paysResonance());
        assertEquals(0L, stone.awardThousandths());
    }

    /** Planted break: paying a machine break collapses the curve (§3.4). */
    @Test
    void aMachineBreakPaysNothing() {
        BreakContext machine = new BreakContext(BreakSource.MACHINE, TEAM, false, Rarity.RARE);
        assertFalse(machine.paysResonance());
        assertEquals(0L, machine.awardThousandths());
    }

    @Test
    void aNonMemberPaysNothing() {
        BreakContext stranger = new BreakContext(BreakSource.HAND, TEAM, false, Rarity.RARE);
        assertFalse(stranger.paysResonance());
        assertEquals(0L, stranger.awardThousandths());
    }

    @Test
    void outsideAVaultNothingPays() {
        BreakContext overworld = new BreakContext(BreakSource.HAND, null, false, Rarity.RARE);
        assertFalse(overworld.inVault());
        assertFalse(overworld.paysResonance());
        assertEquals(0L, overworld.awardThousandths());
    }

    /** Membership means nothing without a Vault: a context that says so is refused rather than paid. */
    @Test
    void aMemberWithNoVaultIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new BreakContext(BreakSource.HAND, null, true, Rarity.RARE));
    }

    /** Membership is only asked for a hand break: a fake player's team is a phantom id (§9). */
    @Test
    void aMachineIsNeverAMember() {
        assertThrows(IllegalArgumentException.class, () -> new BreakContext(BreakSource.MACHINE, TEAM, true, Rarity.RARE));
    }
}
