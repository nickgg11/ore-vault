package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** §3.1's table, and the ids that are save data in every Vault folder's type file (ADR 0005 section 3a). */
class VaultVariantTest {
    @Test
    void theBaseTypeStartsAtZero() {
        assertEquals(0, VaultVariant.BASE.minY());
        assertEquals(320, VaultVariant.BASE.height());
    }

    @Test
    void theExpandedTypeStartsAtMinusSixtyFour() {
        assertEquals(-64, VaultVariant.EXPANDED.minY());
        assertEquals(384, VaultVariant.EXPANDED.height());
    }

    @Test
    void theBaseStoneBandIs245Layers() {
        assertEquals(1, VaultVariant.BASE.stoneBottomY());
        assertEquals(245, VaultVariant.BASE.stoneTopY());
    }

    /** ADR 0009: Y=0 is stone here, so the band has one layer more than the base type's. */
    @Test
    void theExpandedStoneBandStartsAtZero() {
        assertEquals(0, VaultVariant.EXPANDED.stoneBottomY());
        assertEquals(245, VaultVariant.EXPANDED.stoneTopY());
    }

    @Test
    void theIdsAreFrozen() {
        assertEquals("orevault:ore_vault", VaultVariant.BASE.id());
        assertEquals("orevault:ore_vault_expanded", VaultVariant.EXPANDED.id());
    }

    @Test
    void thePathNamesTheLayerFile() {
        assertEquals("ore_vault", VaultVariant.BASE.path());
        assertEquals("ore_vault_expanded", VaultVariant.EXPANDED.path());
    }

    @Test
    void anIdParsesBackToItsVariant() {
        assertEquals(Optional.of(VaultVariant.BASE), VaultVariant.byId("orevault:ore_vault"));
        assertEquals(Optional.of(VaultVariant.EXPANDED), VaultVariant.byId("orevault:ore_vault_expanded"));
    }

    @Test
    void anUnknownIdIsNoVariant() {
        assertEquals(Optional.empty(), VaultVariant.byId("orevault:nonsense"));
    }
}
