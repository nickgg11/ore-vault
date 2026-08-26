package com.orevault.orevault.config;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side configuration (orevault-server.toml). Intentionally minimal per the design
 * spec — admins only configure things with server performance implications.
 */
public final class OreVaultServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // [chunk_loading]
    public static final ModConfigSpec.BooleanValue VAULT_PRESENCE_ENABLED = BUILDER
            .comment("Whether the Vault Presence skill nodes are enabled. Set to false to disable cross-dimension chunk loading entirely.")
            .define("chunk_loading.vault_presence_enabled", true);
    public static final ModConfigSpec.IntValue MAX_LOADED_CHUNKS_PER_TEAM = BUILDER
            .comment("Hard ceiling on simultaneous loaded chunks per team, regardless of node level. Set to 0 for unlimited (not recommended).")
            .defineInRange("chunk_loading.max_loaded_chunks_per_team", 32, 0, Integer.MAX_VALUE);

    // [ore_classification]
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_CLASSIFICATION_OVERRIDES = BUILDER
            .comment("Override the automatic rarity classification for specific ore blocks.",
                    "Format: \"modid:block_id=common|uncommon|rare\"",
                    "Example: \"minecraft:diamond_ore=rare\"")
            .defineListAllowEmpty("ore_classification.overrides", ArrayList::new, e -> e instanceof String s && s.contains("="));

    // [disturbed_zones]
    public static final ModConfigSpec.IntValue MAX_ZONES_PER_TEAM = BUILDER
            .comment("Maximum number of Disturbed Zone blocks placeable per team.")
            .defineInRange("disturbed_zones.max_zones_per_team", 10, 1, Integer.MAX_VALUE);

    // [reset]
    public static final ModConfigSpec.BooleanValue ALLOW_BACKUP_ON_RESET = BUILDER
            .comment("Whether team members can take backups before resetting. Disable if disk space is a concern.")
            .define("reset.allow_backup_on_reset", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static List<ClassOverride> parsedOverrides = List.of();

    public record ClassOverride(String blockId, OreClass oreClass) {
        public static ClassOverride parse(String entry) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid ore classification override: " + entry);
            }
            return new ClassOverride(parts[0].trim(), OreClass.valueOf(parts[1].trim().toUpperCase()));
        }
    }

    public enum OreClass {
        COMMON, UNCOMMON, RARE
    }

    public static void onLoad(ModConfigEvent event) {
        parsedOverrides = ORE_CLASSIFICATION_OVERRIDES.get().stream()
                .map(s -> {
                    try {
                        return ClassOverride.parse(s);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException("Bad orevault config entry: " + s, e);
                    }
                })
                .toList();
    }

    public static List<ClassOverride> classificationOverrides() {
        return parsedOverrides;
    }

    private OreVaultServerConfig() {
    }
}

