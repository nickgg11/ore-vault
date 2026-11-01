package com.orevault.orevault.gametest;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.igniter.IgniterTier;
import com.orevault.orevault.registry.ModCreativeTabs;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Fails on any translation key this mod registers with no {@code en_us} value. The old tree shipped 49 nodes
 * showing raw keys, and the coverage check arrived too late to matter, so this grows with each registration
 * (lessons doc; §13 Polish). Node keys have a test of their own (E8-S6a, #214).
 */
final class LangCoverage {
    private static final String EN_US = "/assets/orevault/lang/en_us.json";

    private LangCoverage() {
    }

    static void everyKeyHasAValue(GameTestHelper helper) {
        JsonObject lang = readLang();
        Set<String> missing = new TreeSet<>();
        for (String key : registeredKeys()) {
            if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            helper.fail(Component.literal("no en_us value for " + missing));
        }
        helper.succeed();
    }

    private static List<String> registeredKeys() {
        List<String> keys = new ArrayList<>();
        ownedEntries(BuiltInRegistries.BLOCK).forEach(block -> keys.add(block.getDescriptionId()));
        ownedEntries(BuiltInRegistries.ITEM).forEach(item -> keys.add(item.getDescriptionId()));
        ownedEntries(BuiltInRegistries.ENTITY_TYPE).forEach(type -> keys.add(type.getDescriptionId()));
        keys.add(ModCreativeTabs.TITLE_KEY);
        keys.addAll(IgniterTier.everyTooltipKey());
        ModConfigs.getModConfigs(OreVault.MOD_ID).stream()
                .map(ModConfig::getSpec)
                .filter(ModConfigSpec.class::isInstance)
                .map(ModConfigSpec.class::cast)
                .forEach(spec -> addConfigKeys(spec, spec.getSpec(), List.of(), keys));
        return keys;
    }

    private static <T> List<T> ownedEntries(Registry<T> registry) {
        return registry.keySet().stream()
                .filter(id -> id.getNamespace().equals(OreVault.MOD_ID))
                .map(registry::getValue)
                .toList();
    }

    /** Follows {@code ConfigurationScreen}: a value or section with no key of its own falls back to its name. */
    private static void addConfigKeys(ModConfigSpec spec, UnmodifiableConfig config, List<String> path, List<String> keys) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String fallback = OreVault.MOD_ID + ".configuration." + entry.getKey();
            if (entry.getValue() instanceof UnmodifiableConfig section) {
                List<String> sectionPath = new ArrayList<>(path);
                sectionPath.add(entry.getKey());
                String key = spec.getLevelTranslationKey(sectionPath);
                keys.add(key != null ? key : fallback);
                addConfigKeys(spec, section, sectionPath, keys);
            } else if (entry.getValue() instanceof ModConfigSpec.ValueSpec value) {
                keys.add(value.getTranslationKey() != null ? value.getTranslationKey() : fallback);
            }
        }
    }

    private static JsonObject readLang() {
        try (InputStream stream = LangCoverage.class.getResourceAsStream(EN_US);
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + EN_US, e);
        }
    }
}
