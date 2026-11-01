package com.orevault.orevault.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

/** Nothing loads a config in JUnit, so {@code SPEC} here is in the state a {@code requires} predicate can meet at boot. */
class OreVaultServerConfigTest {
    /** Old #119: a throwing {@code requires} predicate breaks every subcommand under the root. */
    @Test
    void anUnloadedConfigReadsAsDisabledRatherThanThrowing() {
        assertFalse(OreVaultServerConfig.SPEC.isLoaded(), "this test needs the unloaded state");
        assertFalse(OreVaultServerConfig.enableDebugCommands());
    }

    @Test
    void debugCommandsAreOffByDefault() {
        assertEquals(false, OreVaultServerConfig.ENABLE_DEBUG_COMMANDS.getDefault());
    }

    /** §10: this value takes effect at once, so {@code worldRestart()} would be a false warning on the screen. */
    @Test
    void debugCommandsNeedNoRestart() {
        ModConfigSpec.ValueSpec value = OreVaultServerConfig.SPEC.getSpec().get(List.of("debug", "enable_debug_commands"));
        assertEquals(ModConfigSpec.RestartType.NONE, value.restartType());
    }

    /** Old #121: the screen shows a guessed key when one is left to NeoForge's derivation. */
    @Test
    void everyValueAndSectionHasAnExplicitTranslationKey() {
        List<String> unnamed = new ArrayList<>();
        collectUnnamed(OreVaultServerConfig.SPEC.getSpec(), List.of(), unnamed);
        assertTrue(unnamed.isEmpty(), "no explicit key on " + unnamed);
    }

    private static void collectUnnamed(UnmodifiableConfig config, List<String> path, List<String> unnamed) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            List<String> entryPath = new ArrayList<>(path);
            entryPath.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig section) {
                if (OreVaultServerConfig.SPEC.getLevelTranslationKey(entryPath) == null) {
                    unnamed.add(String.join(".", entryPath));
                }
                collectUnnamed(section, entryPath, unnamed);
            } else if (entry.getValue() instanceof ModConfigSpec.ValueSpec value && value.getTranslationKey() == null) {
                unnamed.add(String.join(".", entryPath));
            }
        }
    }
}
