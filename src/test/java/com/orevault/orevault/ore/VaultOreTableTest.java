package com.orevault.orevault.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.orevault.orevault.ore.OreClassifier.Rarity;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * The ore set the generator picks from (§3.1).
 *
 * <p>Two properties matter and neither is visible in play until a world is already wrong. Order has
 * to be stable, because a team's Vault is generated from a per-chunk seed and an unstable iteration
 * order would generate a different world on every server start. And a pack whose ore tag is thinner
 * than vanilla's has to still generate a full Vault rather than a sparse one.</p>
 *
 * <p>{@code BlockState} is left null throughout: nothing here dereferences it, and building real
 * states needs a bootstrapped registry this source set does not have.</p>
 */
class VaultOreTableTest {

    private static VaultOreTable.Entry entry(String path, Rarity rarity) {
        return new VaultOreTable.Entry(
                Identifier.fromNamespaceAndPath("test", path), null, rarity, 0.5);
    }

    @Test
    void anEmptyTableIsEmpty() {
        assertTrue(VaultOreTable.EMPTY.isEmpty());
        assertEquals(0, VaultOreTable.EMPTY.size());
        assertTrue(VaultOreTable.EMPTY.of(Rarity.COMMON).isEmpty());
    }

    @Test
    void entriesAreGroupedByRarity() {
        VaultOreTable table = new VaultOreTable(List.of(
                entry("coal", Rarity.COMMON),
                entry("iron", Rarity.COMMON),
                entry("gold", Rarity.UNCOMMON),
                entry("diamond", Rarity.RARE)));

        assertEquals(2, table.of(Rarity.COMMON).size());
        assertEquals(1, table.of(Rarity.UNCOMMON).size());
        assertEquals(1, table.of(Rarity.RARE).size());
        assertEquals(4, table.size());
        assertFalse(table.isEmpty());
    }

    @Test
    void orderIsSortedByIdSoAVaultGeneratesTheSameWayTwice() {
        // Chunk generation picks an ore by index from a seeded roll. If the order moved between
        // server starts, so would every vein in every chunk not yet generated.
        VaultOreTable table = new VaultOreTable(List.of(
                entry("zinc", Rarity.COMMON),
                entry("aluminium", Rarity.COMMON),
                entry("mithril", Rarity.COMMON)));

        assertEquals(
                List.of("test:aluminium", "test:mithril", "test:zinc"),
                table.of(Rarity.COMMON).stream().map(e -> e.id().toString()).toList());
    }

    @Test
    void theSameInputGivesTheSameOrderWhateverItArrivesIn() {
        VaultOreTable first = new VaultOreTable(List.of(
                entry("b", Rarity.RARE), entry("a", Rarity.RARE), entry("c", Rarity.RARE)));
        VaultOreTable second = new VaultOreTable(List.of(
                entry("c", Rarity.RARE), entry("b", Rarity.RARE), entry("a", Rarity.RARE)));

        assertEquals(
                first.of(Rarity.RARE).stream().map(e -> e.id().toString()).toList(),
                second.of(Rarity.RARE).stream().map(e -> e.id().toString()).toList());
    }

    // ----- the fallback -----

    @Test
    void aMissingRarityFallsBackToOneThePackActuallyHas() {
        // A pack with no rare ore is not a reason to generate nothing when the roll comes up rare.
        // Without the fallback its Vaults would run 5% short of the density the curve assumes.
        VaultOreTable table = new VaultOreTable(List.of(entry("coal", Rarity.COMMON)));

        assertEquals("test:coal", table.ofOrFallback(Rarity.RARE).get(0).id().toString());
        assertEquals("test:coal", table.ofOrFallback(Rarity.UNCOMMON).get(0).id().toString());
    }

    @Test
    void theFallbackPrefersTheRarityThatWasAskedFor() {
        VaultOreTable table = new VaultOreTable(List.of(
                entry("coal", Rarity.COMMON), entry("diamond", Rarity.RARE)));

        assertEquals("test:diamond", table.ofOrFallback(Rarity.RARE).get(0).id().toString());
    }

    @Test
    void anEmptyTableFallsBackToNothingRatherThanThrowing() {
        assertTrue(VaultOreTable.EMPTY.ofOrFallback(Rarity.RARE).isEmpty());
    }
}
