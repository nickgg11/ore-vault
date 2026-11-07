package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shipped files against the numbers in Java, as rule 8 means them to be checked. That the game accepts the files is
 * the gametests' part: a file these tests like can still fail a 26.1 codec.
 */
class VaultDataFilesTest {
    private static final Path DATA = Path.of(System.getProperty("orevault.projectDir"), "src/main/resources/data/orevault");

    private static JsonObject json(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject dimensionType(VaultVariant variant) throws IOException {
        return json(DATA.resolve("dimension_type/" + variant.path() + ".json"));
    }

    @Test
    void eachDimensionTypeHasTheHeightsInJava() throws IOException {
        for (VaultVariant variant : VaultVariant.values()) {
            JsonObject type = dimensionType(variant);
            assertEquals(variant.minY(), type.get("min_y").getAsInt(), variant.id());
            assertEquals(variant.height(), type.get("height").getAsInt(), variant.id());
            assertEquals(variant.height(), type.get("logical_height").getAsInt(), variant.id());
        }
    }

    /** §3.1's shared fields. The two types differ only in how deep they go. */
    @Test
    void theTwoTypesDifferOnlyInTheirHeights() throws IOException {
        JsonObject base = dimensionType(VaultVariant.BASE);
        JsonObject expanded = dimensionType(VaultVariant.EXPANDED);
        for (String key : List.of("min_y", "height", "logical_height")) {
            base.remove(key);
            expanded.remove(key);
        }
        assertEquals(base, expanded);
    }

    @Test
    void theRulesOfSectionThreeOneAreSet() throws IOException {
        JsonObject type = dimensionType(VaultVariant.BASE);
        assertEquals(1.0f, type.get("ambient_light").getAsFloat());
        assertTrue(type.get("has_skylight").getAsBoolean());
        assertTrue(type.get("has_fixed_time").getAsBoolean());
        assertFalse(type.has("timelines"));
        assertEquals(0, type.get("monster_spawn_light_level").getAsInt());
        assertEquals(0, type.get("monster_spawn_block_light_limit").getAsInt());
        JsonObject attributes = type.getAsJsonObject("attributes");
        assertEquals(1.0f, attributes.get("minecraft:visual/sky_light_factor").getAsFloat());
        assertTrue(attributes.has("minecraft:visual/ambient_light_color"), "its default is opaque black");
        assertFalse(attributes.get("minecraft:gameplay/can_start_raid").getAsBoolean());
        assertFalse(attributes.get("minecraft:gameplay/respawn_anchor_works").getAsBoolean());
        JsonObject bed = attributes.getAsJsonObject("minecraft:gameplay/bed_rule");
        assertEquals("never", bed.get("can_sleep").getAsString());
        assertEquals("never", bed.get("can_set_spawn").getAsString());
        assertFalse(bed.has("explodes"), "Nick chose no explosion on 2026-10-27");
    }

    @Test
    void theSkyAndFogAreOld83sInBothPlaces() throws IOException {
        JsonObject type = dimensionType(VaultVariant.BASE).getAsJsonObject("attributes");
        JsonObject biome = json(DATA.resolve("worldgen/biome/vault.json")).getAsJsonObject("attributes");
        for (JsonObject attributes : List.of(type, biome)) {
            assertEquals("#78a7ff", attributes.get("minecraft:visual/sky_color").getAsString());
            assertEquals("#c0d8ff", attributes.get("minecraft:visual/fog_color").getAsString());
        }
    }

    @Test
    void theBiomeListsNoSpawnersAndNoCosts() throws IOException {
        JsonObject biome = json(DATA.resolve("worldgen/biome/vault.json"));
        assertEquals(0, biome.getAsJsonObject("spawn_costs").size());
        JsonObject spawners = biome.getAsJsonObject("spawners");
        assertEquals(8, spawners.size());
        for (String category : spawners.keySet()) {
            assertEquals(0, spawners.getAsJsonArray(category).size(), category);
        }
    }

    /** A biome modifier aimed at a broad tag is how most mods add spawns (§3.1). */
    @Test
    void noTagOfOursNamesTheBiome() throws IOException {
        Path tags = DATA.resolveSibling("minecraft/tags/worldgen/biome");
        if (Files.exists(tags)) {
            try (var files = Files.list(tags)) {
                assertEquals(List.of(), files.toList());
            }
        }
    }

    /** Same values in the shipped file and in the fallback, so a player cannot tell which one loaded. */
    @Test
    void eachLayerFileEqualsTheBuiltInStack() throws IOException {
        for (VaultVariant variant : VaultVariant.values()) {
            List<String> errors = new ArrayList<>();
            Path file = DATA.resolve("worldgen/vault_layers/" + variant.path() + ".json");
            try (Reader reader = Files.newBufferedReader(file)) {
                assertEquals(LayerStack.builtIn(variant), LayerStack.fromJson(reader, variant, id -> true, errors::add));
            }
            assertEquals(List.of(), errors, variant.id());
        }
    }
}
