package com.orevault.orevault.core.igniter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The §3.3 table, and the tooltip built from it (old #122, old #125). */
class IgniterTierTest {
    private static final String PURPOSE = "tooltip.orevault.igniter.purpose";
    private static final String OPENS_PORTAL = "tooltip.orevault.igniter.opens_portal";
    private static final String ENTRY_POINT = "tooltip.orevault.igniter.entry_point";
    private static final String ENTRY_POINTS = "tooltip.orevault.igniter.entry_points";
    private static final String INSTANT_TRAVEL = "tooltip.orevault.igniter.instant_travel";
    private static final String RESET_VAULT = "tooltip.orevault.igniter.reset_vault";
    private static final String UPGRADE = "tooltip.orevault.igniter.upgrade";

    private static List<String> keys(IgniterTier tier) {
        return tier.tooltipLines().stream().map(IgniterTier.TooltipLine::key).toList();
    }

    @Test
    void entryPointCapacityFollowsTheSpecTable() {
        assertEquals(0, IgniterTier.CRUDE.entryPointCapacity());
        assertEquals(1, IgniterTier.ATTUNED.entryPointCapacity());
        assertEquals(3, IgniterTier.RESONANT.entryPointCapacity());
        assertEquals(3, IgniterTier.SOVEREIGN.entryPointCapacity());
    }

    @Test
    void instantTravelStartsAtResonant() {
        assertFalse(IgniterTier.CRUDE.hasInstantTravel());
        assertFalse(IgniterTier.ATTUNED.hasInstantTravel());
        assertTrue(IgniterTier.RESONANT.hasInstantTravel());
        assertTrue(IgniterTier.SOVEREIGN.hasInstantTravel());
    }

    @Test
    void onlySovereignCanResetTheVault() {
        assertFalse(IgniterTier.CRUDE.canResetVault());
        assertFalse(IgniterTier.ATTUNED.canResetVault());
        assertFalse(IgniterTier.RESONANT.canResetVault());
        assertTrue(IgniterTier.SOVEREIGN.canResetVault());
    }

    @Test
    void crudeTooltipSaysWhatItIsForThenItsOneCapabilityThenTheHint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, UPGRADE), keys(IgniterTier.CRUDE));
    }

    @Test
    void attunedAddsTheSingleEntryPoint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, UPGRADE), keys(IgniterTier.ATTUNED));
    }

    @Test
    void resonantRaisesTheEntryPointLineAndAddsInstantTravel() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINTS, INSTANT_TRAVEL, UPGRADE), keys(IgniterTier.RESONANT));
    }

    @Test
    void sovereignListsEveryCapabilityAndHasNoUpgradeHint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT), keys(IgniterTier.SOVEREIGN));
    }

    @Test
    void theEntryPointsLineCarriesTheCapacity() {
        IgniterTier.TooltipLine line = IgniterTier.RESONANT.tooltipLines().stream()
                .filter(candidate -> candidate.key().equals(ENTRY_POINTS)).findFirst().orElseThrow();
        assertEquals(List.of(IgniterTier.RESONANT.entryPointCapacity()), line.args());
    }

    @ParameterizedTest
    @EnumSource(IgniterTier.class)
    void onlyTheUpgradeLineIsAHint(IgniterTier tier) {
        tier.tooltipLines().forEach(line -> assertEquals(line.key().equals(UPGRADE), line.hint(), line.key()));
    }

    @Test
    void everyTooltipKeyListsEachKeyOnce() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT, UPGRADE),
                IgniterTier.everyTooltipKey());
    }
}
