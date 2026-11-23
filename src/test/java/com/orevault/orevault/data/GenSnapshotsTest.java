package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.ClipFactors;
import com.orevault.orevault.core.gen.EdgeFactors;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.gen.OrePickers;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** ADR 0003 "Holders and the supplier", the contract ADR 0002 creation step 3 relies on. */
class GenSnapshotsTest {
    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    /**
     * With no generation node both clip factors are 1 (§3.1): the baseline's densest layer, about 5% in stone and 16% in
     * deepslate, is far under 60%, so the filled snapshot carries {@link ClipFactors#NONE}.
     */
    private static GenSnapshot baseline(VaultVariant variant, int vaultGeneration) {
        return new GenSnapshot(variant, vaultGeneration, ClipFactors.NONE, EdgeFactors.of(OreBaseline.BASE, variant, pickers(variant)));
    }

    private static Map<Band, OrePicker> pickers(VaultVariant variant) {
        return OrePickers.forVariant(variant, VanillaOres.candidates());
    }

    @AfterEach
    void clear() {
        GenSnapshots.clear();
    }

    @Test
    void theSupplierAnswersTheFilledSnapshot() {
        Supplier<GenSnapshot> supplier = GenSnapshots.fill(TEAM, VaultVariant.BASE, pickers(VaultVariant.BASE), 3);
        assertEquals(baseline(VaultVariant.BASE, 3), supplier.get());
        assertEquals(Optional.of(baseline(VaultVariant.BASE, 3)), GenSnapshots.current(TEAM));
    }

    /** A generator still draining across a reset keeps its own snapshot, so its variant check cannot trip. */
    @Test
    void aSecondFillLeavesTheFirstSupplierAlone() {
        Supplier<GenSnapshot> old = GenSnapshots.fill(TEAM, VaultVariant.BASE, pickers(VaultVariant.BASE), 0);
        Supplier<GenSnapshot> fresh = GenSnapshots.fill(TEAM, VaultVariant.EXPANDED, pickers(VaultVariant.EXPANDED), 1);
        assertEquals(baseline(VaultVariant.BASE, 0), old.get());
        assertEquals(baseline(VaultVariant.EXPANDED, 1), fresh.get());
    }

    @Test
    void aRebuildKeepsTheVariantAndReadsTheGeneration() {
        Supplier<GenSnapshot> supplier = GenSnapshots.fill(TEAM, VaultVariant.EXPANDED, pickers(VaultVariant.EXPANDED), 0);
        GenSnapshots.rebuild(TEAM, () -> 4);
        assertEquals(baseline(VaultVariant.EXPANDED, 4), supplier.get());
    }

    @Test
    void aRebuildReachesOnlyTheCurrentHolder() {
        Supplier<GenSnapshot> old = GenSnapshots.fill(TEAM, VaultVariant.BASE, pickers(VaultVariant.BASE), 0);
        GenSnapshots.fill(TEAM, VaultVariant.BASE, pickers(VaultVariant.BASE), 0);
        GenSnapshots.rebuild(TEAM, () -> 9);
        assertEquals(baseline(VaultVariant.BASE, 0), old.get());
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
        GenSnapshots.fill(TEAM, VaultVariant.BASE, pickers(VaultVariant.BASE), 0);
        GenSnapshots.clear();
        assertTrue(GenSnapshots.current(TEAM).isEmpty());
    }
}
