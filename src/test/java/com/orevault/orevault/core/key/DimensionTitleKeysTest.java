package com.orevault.orevault.core.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DimensionTitleKeysTest {
    private static final UUID TEAM = UUID.fromString("2a717c8f-118e-3474-bc2d-d61b924015ff");

    @Test
    void aVaultsTitleKeyFallsBackToTheSharedOne() {
        String key = "travelerstitles.orevault." + VaultKeys.dimensionPath(TEAM);
        assertEquals("travelerstitles.orevault.vault", DimensionTitleKeys.fallback(key));
    }

    @Test
    void theSharedKeyIsTheOneInTheLangFile() {
        assertEquals("travelerstitles.orevault.vault", DimensionTitleKeys.TITLE);
    }

    @Test
    void theColourKeyAndOtherKeysAreLeftAlone() {
        String vault = "travelerstitles.orevault." + VaultKeys.dimensionPath(TEAM);
        assertNull(DimensionTitleKeys.fallback(vault + ".color"));
        assertNull(DimensionTitleKeys.fallback("travelerstitles.minecraft.overworld"));
        assertNull(DimensionTitleKeys.fallback("travelerstitles.orevault.vault"));
        assertNull(DimensionTitleKeys.fallback("travelerstitles.orevault.vault_123"));
        assertNull(DimensionTitleKeys.fallback("travelerstitles.orevault.VAULT_2A717C8F118E3474BC2DD61B924015FF"));
        assertNull(DimensionTitleKeys.fallback("block.orevault.vault_frame"));
    }
}
