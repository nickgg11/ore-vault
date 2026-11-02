package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** §3.1's stacks, block by block, and the fallback: a bad file costs an ERROR, never a broken Vault. */
class LayerStackTest {
    private static final Predicate<String> ANY_BLOCK = id -> true;
    private static final String GOOD_BASE = """
            {"layers": [
              {"block": "minecraft:bedrock", "thickness": 1},
              {"block": "minecraft:cobblestone", "thickness": 245},
              {"block": "minecraft:dirt", "thickness": 4},
              {"block": "minecraft:grass_block", "thickness": 1},
              {"block": "minecraft:air", "thickness": 69}
            ]}""";

    private static String at(VaultVariant variant, int y) {
        return LayerStack.builtIn(variant).column().get(y - variant.minY());
    }

    private static LayerStack load(String json, Predicate<String> blockExists, List<String> errors) {
        return LayerStack.fromJson(new StringReader(json), VaultVariant.BASE, blockExists, errors::add);
    }

    @Test
    void theBaseStackIsBedrockStoneDirtGrassAir() {
        VaultVariant base = VaultVariant.BASE;
        assertEquals("minecraft:bedrock", at(base, 0));
        assertEquals("minecraft:stone", at(base, 1));
        assertEquals("minecraft:stone", at(base, 245));
        assertEquals("minecraft:dirt", at(base, 246));
        assertEquals("minecraft:dirt", at(base, 249));
        assertEquals("minecraft:grass_block", at(base, 250));
        assertEquals("minecraft:air", at(base, 251));
        assertEquals("minecraft:air", at(base, 319));
    }

    @Test
    void theExpandedStackAddsBedrockAndDeepslateBelowZero() {
        VaultVariant expanded = VaultVariant.EXPANDED;
        assertEquals("minecraft:bedrock", at(expanded, -64));
        assertEquals("minecraft:deepslate", at(expanded, -63));
        assertEquals("minecraft:deepslate", at(expanded, -1));
        assertEquals("minecraft:stone", at(expanded, 0));
        assertEquals("minecraft:stone", at(expanded, 245));
        assertEquals("minecraft:dirt", at(expanded, 246));
        assertEquals("minecraft:grass_block", at(expanded, 250));
        assertEquals("minecraft:air", at(expanded, 251));
        assertEquals("minecraft:air", at(expanded, 319));
    }

    @Test
    void eachBuiltInStackFillsItsWholeHeight() {
        for (VaultVariant variant : VaultVariant.values()) {
            assertEquals(variant.height(), LayerStack.builtIn(variant).column().size(), variant.id());
        }
    }

    @Test
    void theColumnHasSixtyNineBlocksOfOpenAir() {
        long air = LayerStack.builtIn(VaultVariant.BASE).column().stream().filter(LayerStack.AIR::equals).count();
        assertEquals(69, air);
    }

    @Test
    void theFirstAirIndexIsTheBlockAboveTheGrass() {
        assertEquals(251, LayerStack.builtIn(VaultVariant.BASE).firstAirIndex());
        assertEquals(251 + 64, LayerStack.builtIn(VaultVariant.EXPANDED).firstAirIndex());
    }

    @Test
    void aStackWithNoAirHasNoFirstAirIndex() {
        LayerStack solid = new LayerStack(List.of(new LayerStack.Layer("minecraft:stone", 16)));
        assertEquals(-1, solid.firstAirIndex());
    }

    @Test
    void aGoodFileIsUsedAsWritten() {
        List<String> errors = new ArrayList<>();
        LayerStack stack = load(GOOD_BASE, ANY_BLOCK, errors);
        assertEquals("minecraft:cobblestone", stack.column().get(1));
        assertEquals(List.of(), errors);
    }

    @Test
    void malformedJsonFallsBackAndSaysSo() {
        List<String> errors = new ArrayList<>();
        assertEquals(LayerStack.builtIn(VaultVariant.BASE), load("{not json", ANY_BLOCK, errors));
        assertEquals(1, errors.size(), errors.toString());
    }

    @Test
    void aFileWithNoLayersKeyFallsBack() {
        List<String> errors = new ArrayList<>();
        assertEquals(LayerStack.builtIn(VaultVariant.BASE), load("{}", ANY_BLOCK, errors));
        assertEquals(1, errors.size(), errors.toString());
    }

    @Test
    void aLayerWithoutAThicknessFallsBack() {
        List<String> errors = new ArrayList<>();
        String json = "{\"layers\": [{\"block\": \"minecraft:stone\"}]}";
        assertEquals(LayerStack.builtIn(VaultVariant.BASE), load(json, ANY_BLOCK, errors));
        assertEquals(1, errors.size(), errors.toString());
    }

    /** The stack still sums to the height, so only the thickness can refuse this one. */
    @Test
    void aThicknessOfZeroFallsBack() {
        List<String> errors = new ArrayList<>();
        String json = GOOD_BASE.replace("\"minecraft:bedrock\", \"thickness\": 1", "\"minecraft:bedrock\", \"thickness\": 0")
                .replace("\"thickness\": 245", "\"thickness\": 246");
        assertEquals(LayerStack.builtIn(VaultVariant.BASE), load(json, ANY_BLOCK, errors));
        assertEquals(1, errors.size(), errors.toString());
    }

    /** Rule 8: the stack against the heights in Java is what makes a wrong file loud. */
    @Test
    void aStackOfTheWrongHeightFallsBackAndNamesBothHeights() {
        List<String> errors = new ArrayList<>();
        String json = GOOD_BASE.replace("\"thickness\": 69", "\"thickness\": 70");
        assertEquals(LayerStack.builtIn(VaultVariant.BASE), load(json, ANY_BLOCK, errors));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains("321") && errors.getFirst().contains("320"), errors.getFirst());
    }

    @Test
    void anUnknownBlockFallsBackAndNamesIt() {
        List<String> errors = new ArrayList<>();
        Predicate<String> noCobble = id -> !id.equals("minecraft:cobblestone");
        assertEquals(LayerStack.builtIn(VaultVariant.BASE), load(GOOD_BASE, noCobble, errors));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains("minecraft:cobblestone"), errors.getFirst());
    }

    @Test
    void theColumnCannotBeChangedByItsReader() {
        LayerStack stack = LayerStack.builtIn(VaultVariant.BASE);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> stack.column().set(0, "x"));
    }
}
