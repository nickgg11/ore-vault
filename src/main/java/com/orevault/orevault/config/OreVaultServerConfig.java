package com.orevault.orevault.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration (§10), generated as {@code orevault-server.toml}.
 * Only values with server performance implications are configurable; everything
 * else is derived from the skill tree.
 */
public final class OreVaultServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // [resonance]
    private static final ModConfigSpec.IntValue TARGET_PLAY_HOURS;
    private static final ModConfigSpec.DoubleValue CURVE_DIVISOR;

    // [chunk_loading]
    private static final ModConfigSpec.BooleanValue VAULT_PRESENCE_ENABLED;
    private static final ModConfigSpec.IntValue MAX_LOADED_CHUNKS_PER_TEAM;

    // [ore_classification]
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_CLASSIFICATION_OVERRIDES;

    // [disturbed_zones]
    private static final ModConfigSpec.IntValue MAX_ZONES_PER_TEAM;

    // [reset]
    private static final ModConfigSpec.BooleanValue ALLOW_BACKUP_ON_RESET;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("resonance");
        TARGET_PLAY_HOURS = BUILDER
                .comment("Target hours of play to make the full Resonance tree purchasable (level 30).",
                        "Read once at server start; changing it requires a restart.")
                .defineInRange("target_play_hours", 100, 1, 10000);
        CURVE_DIVISOR = BUILDER
                .comment("Divides the total Resonance required across the whole curve.",
                        "2.0 = half the grind; 0.5 = double it. The shape of the curve is unchanged,",
                        "so every level requirement in the skill tree keeps its intended pacing.",
                        "Read once at server start; changing it requires a restart.")
                .defineInRange("curve_divisor", 1.0, 0.01, 100.0);
        BUILDER.pop();

        BUILDER.push("chunk_loading");
        VAULT_PRESENCE_ENABLED = BUILDER
                .comment("Whether the Vault Presence skill nodes are enabled.",
                        "Set to false to disable cross-dimension chunk loading entirely.")
                .define("vault_presence_enabled", true);
        MAX_LOADED_CHUNKS_PER_TEAM = BUILDER
                .comment("Hard ceiling on simultaneous loaded chunks per team, regardless of node level.",
                        "Set to 0 for unlimited (not recommended).")
                .defineInRange("max_loaded_chunks_per_team", 32, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("ore_classification");
        ORE_CLASSIFICATION_OVERRIDES = BUILDER
                .comment("Override the automatic rarity classification for specific ore blocks.",
                        "Format: \"modid:block_id=common|uncommon|rare\"",
                        "Example: \"minecraft:diamond_ore=rare\"")
                .defineListAllowEmpty("overrides", List.of(), () -> "", OreVaultServerConfig::validateOverride);
        BUILDER.pop();

        BUILDER.push("disturbed_zones");
        MAX_ZONES_PER_TEAM = BUILDER
                .comment("Maximum number of Disturbed Zone blocks placeable per team.")
                .defineInRange("max_zones_per_team", 10, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("reset");
        ALLOW_BACKUP_ON_RESET = BUILDER
                .comment("Whether team members can take backups before resetting.",
                        "Disable if disk space is a concern.")
                .define("allow_backup_on_reset", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private OreVaultServerConfig() {
    }

    /** Target hours to make the whole Resonance tree purchasable (§4.3 step 4). */
    public static int targetPlayHours() {
        return TARGET_PLAY_HOURS.get();
    }

    /** Scales the whole Resonance grind without changing the curve's shape (§4.3 step 6). */
    public static double curveDivisor() {
        return CURVE_DIVISOR.get();
    }

    public static boolean vaultPresenceEnabled() {
        return VAULT_PRESENCE_ENABLED.get();
    }

    public static int maxLoadedChunksPerTeam() {
        return MAX_LOADED_CHUNKS_PER_TEAM.get();
    }

    public static int maxZonesPerTeam() {
        return MAX_ZONES_PER_TEAM.get();
    }

    public static boolean allowBackupOnReset() {
        return ALLOW_BACKUP_ON_RESET.get();
    }

    /** Parsed classification overrides: block id → rarity ({@code common|uncommon|rare}). */
    public static Map<String, String> oreClassificationOverrides() {
        Map<String, String> result = new HashMap<>();
        for (String entry : ORE_CLASSIFICATION_OVERRIDES.get()) {
            int eq = entry.indexOf('=');
            if (eq > 0 && eq < entry.length() - 1) {
                result.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        return result;
    }

    private static boolean validateOverride(final Object obj) {
        if (!(obj instanceof String entry)) {
            return false;
        }
        int eq = entry.indexOf('=');
        if (eq <= 0 || eq == entry.length() - 1) {
            return false;
        }
        String rarity = entry.substring(eq + 1);
        return rarity.equals("common") || rarity.equals("uncommon") || rarity.equals("rare");
    }
}
