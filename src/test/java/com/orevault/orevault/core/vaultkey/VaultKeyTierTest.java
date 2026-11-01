package com.orevault.orevault.core.vaultkey;

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
class VaultKeyTierTest {
    private static final String PURPOSE = "tooltip.orevault.vault_key.purpose";
    private static final String OPENS_PORTAL = "tooltip.orevault.vault_key.opens_portal";
    private static final String ENTRY_POINT = "tooltip.orevault.vault_key.entry_point";
    private static final String ENTRY_POINTS = "tooltip.orevault.vault_key.entry_points";
    private static final String INSTANT_TRAVEL = "tooltip.orevault.vault_key.instant_travel";
    private static final String RESET_VAULT = "tooltip.orevault.vault_key.reset_vault";
    private static final String UPGRADE = "tooltip.orevault.vault_key.upgrade";

    private static List<String> keys(VaultKeyTier tier) {
        return tier.tooltipLines().stream().map(VaultKeyTier.TooltipLine::key).toList();
    }

    @Test
    void entryPointCapacityFollowsTheSpecTable() {
        assertEquals(0, VaultKeyTier.CRUDE.entryPointCapacity());
        assertEquals(1, VaultKeyTier.ATTUNED.entryPointCapacity());
        assertEquals(3, VaultKeyTier.RESONANT.entryPointCapacity());
        assertEquals(3, VaultKeyTier.SOVEREIGN.entryPointCapacity());
    }

    @Test
    void instantTravelStartsAtResonant() {
        assertFalse(VaultKeyTier.CRUDE.hasInstantTravel());
        assertFalse(VaultKeyTier.ATTUNED.hasInstantTravel());
        assertTrue(VaultKeyTier.RESONANT.hasInstantTravel());
        assertTrue(VaultKeyTier.SOVEREIGN.hasInstantTravel());
    }

    @Test
    void onlySovereignCanResetTheVault() {
        assertFalse(VaultKeyTier.CRUDE.canResetVault());
        assertFalse(VaultKeyTier.ATTUNED.canResetVault());
        assertFalse(VaultKeyTier.RESONANT.canResetVault());
        assertTrue(VaultKeyTier.SOVEREIGN.canResetVault());
    }

    @Test
    void crudeTooltipSaysWhatItIsForThenItsOneCapabilityThenTheHint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, UPGRADE), keys(VaultKeyTier.CRUDE));
    }

    @Test
    void attunedAddsTheSingleEntryPoint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, UPGRADE), keys(VaultKeyTier.ATTUNED));
    }

    @Test
    void resonantRaisesTheEntryPointLineAndAddsInstantTravel() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINTS, INSTANT_TRAVEL, UPGRADE), keys(VaultKeyTier.RESONANT));
    }

    @Test
    void sovereignListsEveryCapabilityAndHasNoUpgradeHint() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT), keys(VaultKeyTier.SOVEREIGN));
    }

    @Test
    void theEntryPointsLineCarriesTheCapacity() {
        VaultKeyTier.TooltipLine line = VaultKeyTier.RESONANT.tooltipLines().stream()
                .filter(candidate -> candidate.key().equals(ENTRY_POINTS)).findFirst().orElseThrow();
        assertEquals(List.of(VaultKeyTier.RESONANT.entryPointCapacity()), line.args());
    }

    @ParameterizedTest
    @EnumSource(VaultKeyTier.class)
    void onlyTheUpgradeLineIsAHint(VaultKeyTier tier) {
        tier.tooltipLines().forEach(line -> assertEquals(line.key().equals(UPGRADE), line.hint(), line.key()));
    }

    @Test
    void everyTooltipKeyIsExactlyWhatTheTiersShow() {
        Set<String> shown = Arrays.stream(VaultKeyTier.values())
                .flatMap(tier -> keys(tier).stream())
                .collect(Collectors.toSet());
        assertEquals(shown, Set.copyOf(VaultKeyTier.everyTooltipKey()));
    }

    @Test
    void everyTooltipKeyListsEachKeyOnce() {
        assertEquals(List.of(PURPOSE, OPENS_PORTAL, ENTRY_POINT, ENTRY_POINTS, INSTANT_TRAVEL, RESET_VAULT, UPGRADE),
                VaultKeyTier.everyTooltipKey());
    }
}
