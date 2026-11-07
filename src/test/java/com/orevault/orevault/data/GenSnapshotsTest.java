package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.VaultVariant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ADR 0003 "Holders and the supplier", the contract ADR 0002 creation step 3 relies on. */
class GenSnapshotsTest {
    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @AfterEach
    void clear() {
        GenSnapshots.clear();
    }

    @Test
    void theSupplierAnswersTheFilledSnapshot() {
        Supplier<GenSnapshot> supplier = GenSnapshots.fill(TEAM, VaultVariant.BASE, 3);
        assertEquals(new GenSnapshot(VaultVariant.BASE, 3), supplier.get());
        assertEquals(Optional.of(new GenSnapshot(VaultVariant.BASE, 3)), GenSnapshots.current(TEAM));
    }

    /** A generator still draining across a reset keeps its own snapshot, so its variant check cannot trip. */
    @Test
    void aSecondFillLeavesTheFirstSupplierAlone() {
        Supplier<GenSnapshot> old = GenSnapshots.fill(TEAM, VaultVariant.BASE, 0);
        Supplier<GenSnapshot> fresh = GenSnapshots.fill(TEAM, VaultVariant.EXPANDED, 1);
        assertEquals(new GenSnapshot(VaultVariant.BASE, 0), old.get());
        assertEquals(new GenSnapshot(VaultVariant.EXPANDED, 1), fresh.get());
    }

    @Test
    void aRebuildKeepsTheVariantAndReadsTheGeneration() {
        Supplier<GenSnapshot> supplier = GenSnapshots.fill(TEAM, VaultVariant.EXPANDED, 0);
        GenSnapshots.rebuild(TEAM, () -> 4);
        assertEquals(new GenSnapshot(VaultVariant.EXPANDED, 4), supplier.get());
    }

    @Test
    void aRebuildReachesOnlyTheCurrentHolder() {
        Supplier<GenSnapshot> old = GenSnapshots.fill(TEAM, VaultVariant.BASE, 0);
        GenSnapshots.fill(TEAM, VaultVariant.BASE, 0);
        GenSnapshots.rebuild(TEAM, () -> 9);
        assertEquals(new GenSnapshot(VaultVariant.BASE, 0), old.get());
    }

    @Test
    void aTeamWithNoHolderHasNothingToRebuildAndReadsNothing() {
        GenSnapshots.rebuild(TEAM, () -> {
            throw new AssertionError("read team data for a team with no holder");
        });
        assertEquals(Optional.empty(), GenSnapshots.current(TEAM));
    }

    @Test
    void clearingForgetsEveryTeam() {
        GenSnapshots.fill(TEAM, VaultVariant.BASE, 0);
        GenSnapshots.clear();
        assertTrue(GenSnapshots.current(TEAM).isEmpty());
    }
}
