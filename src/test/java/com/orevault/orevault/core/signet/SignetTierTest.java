package com.orevault.orevault.core.signet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The §3.3 table, and the tooltip built from it (old #122, old #125). */
class SignetTierTest {
    private static final String PURPOSE = "tooltip.orevault.signet.purpose";
    private static final String OPENS_PORTAL = "tooltip.orevault.signet.opens_portal";
    private static final String ENTRY_POINT = "tooltip.orevault.signet.entry_point";
    private static final String ENTRY_POINTS = "tooltip.orevault.signet.entry_points";
    private static final String INSTANT_TRAVEL = "tooltip.orevault.signet.instant_travel";
    private static final String RESET_VAULT = "tooltip.orevault.signet.reset_vault";
    private static final String UPGRADE = "tooltip.orevault.signet.upgrade";

    private static List<String> keys(SignetTier tier) {
        return tier.tooltipLines().stream().map(SignetTier.TooltipLine::key).toList();
    }

    @Test
    void entryPointCapacityFollowsTheSpecTable() {
        assertEquals(0, SignetTier.WORN.entryPointCapacity());
        assertEquals(1, SignetTier.HEIRLOOM.entryPointCapacity());
        assertEquals(3, SignetTier.FABLED.entryPointCapacity());
        assertEquals(3, SignetTier.PRIMORDIAL.entryPointCapacity());
    }

    @Test
    void instantTravelStartsAtFabled() {
        assertFalse(SignetTier.WORN.hasInstantTravel());
        assertFalse(SignetTier.HEIRLOOM.hasInstantTravel());
        assertTrue(SignetTier.FABLED.hasInstantTravel());
        assertTrue(SignetTier.PRIMORDIAL.hasInstantTravel());
    }

    @Test
    void onlyPrimordialCanResetTheVault() {
        assertFalse(SignetTier.WORN.canResetVault());
        assertFalse(SignetTier.HEIRLOOM.canResetVault());
        assertFalse(SignetTier.FABLED.canResetVault());
        assertTrue(SignetTier.PRIMORDIAL.canResetVault());
    }

    @Test
    void wornTooltipSaysWhatItIsForThenItsOneCapabilityThenTheHint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, UPGRADE), keys(SignetTier.WORN));
    }

    @Test
    void heirloomAddsTheSingleEntryPoint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, UPGRADE), keys(SignetTier.HEIRLOOM));
    }

    @Test
    void fabledRaisesTheEntryPointLineAndAddsInstantTravel() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINTS, INSTANT_TRAVEL, UPGRADE), keys(SignetTier.FABLED));
    }

    @Test
    void primordialListsEveryCapabilityAndHasNoUpgradeHint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT), keys(SignetTier.PRIMORDIAL));
    }

    @Test
    void theEntryPointsLineCarriesTheCapacity() {
        SignetTier.TooltipLine line = SignetTier.FABLED.tooltipLines().stream()
                .filter(candidate -> candidate.key().equals(ENTRY_POINTS)).findFirst().orElseThrow();
        assertEquals(List.of(SignetTier.FABLED.entryPointCapacity()), line.args());
    }

    @ParameterizedTest
    @EnumSource(SignetTier.class)
    void onlyTheUpgradeLineIsAHint(SignetTier tier) {
        tier.tooltipLines().forEach(line -> assertEquals(line.key().equals(UPGRADE), line.hint(), line.key()));
    }

    @Test
    void everyTooltipKeyIsExactlyWhatTheTiersShow() {
        Set<String> shown = Arrays.stream(SignetTier.values())
                .flatMap(tier -> keys(tier).stream())
                .collect(Collectors.toSet());
        assertEquals(shown, Set.copyOf(SignetTier.everyTooltipKey()));
    }

    @Test
    void everyTooltipKeyListsEachKeyOnce() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT, UPGRADE),
                SignetTier.everyTooltipKey());
    }
}
