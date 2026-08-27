package com.orevault.orevault.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import com.orevault.orevault.ore.OreClassifier.Rarity;

import org.junit.jupiter.api.Test;

/** Pure-logic tests for the §11 rarity thresholds and §10 override precedence. */
class OreClassifierTest {

    @Test
    void rareWhenFewVeinsAndShallowMaxHeight() {
        assertEquals(Rarity.RARE, OreClassifier.classify(4, -64, 32));
        assertEquals(Rarity.RARE, OreClassifier.classify(1, 0, 16));
        assertEquals(Rarity.RARE, OreClassifier.classify(3, -64, 32));
    }

    @Test
    void commonWhenManyVeinsAcrossWideHeightRange() {
        assertEquals(Rarity.COMMON, OreClassifier.classify(15, -64, 64)); // range exactly 128
        assertEquals(Rarity.COMMON, OreClassifier.classify(90, -64, 320));
        assertEquals(Rarity.COMMON, OreClassifier.classify(20, 0, 200));
    }

    @Test
    void uncommonForEverythingElse() {
        assertEquals(Rarity.UNCOMMON, OreClassifier.classify(5, -64, 32)); // too many veins for rare
        assertEquals(Rarity.UNCOMMON, OreClassifier.classify(14, -64, 64)); // too few veins for common
        assertEquals(Rarity.UNCOMMON, OreClassifier.classify(15, -64, 63)); // range 127 < 128
        assertEquals(Rarity.UNCOMMON, OreClassifier.classify(4, 33, 64)); // too deep for rare
    }

    @Test
    void overridesWinOverAutomaticClassification() {
        Map<String, String> overrides = Map.of(
                "minecraft:diamond_ore", "rare",
                "minecraft:iron_ore", "common",
                "minecraft:coal_ore", "banana"
        );

        assertEquals(Rarity.RARE, OreClassifier.applyOverride(Rarity.UNCOMMON, "minecraft:diamond_ore", overrides));
        assertEquals(Rarity.COMMON, OreClassifier.applyOverride(Rarity.RARE, "minecraft:iron_ore", overrides));
        assertEquals(Rarity.UNCOMMON, OreClassifier.applyOverride(Rarity.UNCOMMON, "minecraft:copper_ore", overrides));
    }

    @Test
    void invalidOverrideValuesFallBackToAutomatic() {
        Map<String, String> overrides = Map.of("minecraft:coal_ore", "banana");
        assertEquals(Rarity.UNCOMMON, OreClassifier.applyOverride(Rarity.UNCOMMON, "minecraft:coal_ore", overrides));
        assertEquals(Rarity.RARE, OreClassifier.applyOverride(Rarity.RARE, "minecraft:coal_ore", overrides));
    }
}
