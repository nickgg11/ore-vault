package com.orevault.orevault.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code orevault-server.toml} (§10). A value joins this class in the ticket that first reads it, never before:
 * the old build had four values nothing read (rule 7, design log 2026-09-03).
 */
public final class OreVaultServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue TARGET_PLAY_HOURS;
    public static final ModConfigSpec.DoubleValue CURVE_DIVISOR;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_COMMANDS;
    public static final ModConfigSpec.BooleanValue LOG_RESONANCE_GAIN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_CLASSIFICATION_OVERRIDES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.translation("orevault.configuration.resonance").push("resonance");
        // Ranges from old #24, confirmed by the Balancer for E5-S1 (#240). Both are read once at server start, because
        // moving a threshold mid-session would move it under teams that had already passed it (§4.3 step 3).
        TARGET_PLAY_HOURS = builder
                .comment("Target hours of play to reach Resonance level 30.",
                        "Read once at server start; takes effect on the next world load.")
                .translation("orevault.configuration.resonance.target_play_hours")
                .worldRestart()
                .defineInRange("target_play_hours", 100.0, 1.0, 10_000.0);
        CURVE_DIVISOR = builder
                .comment("Divides the total Resonance required across the whole curve.",
                        "2.0 = half the grind; 0.5 = double it. The shape of the curve is unchanged,",
                        "so every level requirement in the skill tree keeps its intended pacing.",
                        "Read once at server start; takes effect on the next world load.")
                .translation("orevault.configuration.resonance.curve_divisor")
                .worldRestart()
                .defineInRange("curve_divisor", 1.0, 0.01, 100.0);
        builder.pop();
        builder.translation("orevault.configuration.ore_classification").push("ore_classification");
        // The list check is only "a string": NeoForge resets the whole list when one element fails it, so each entry is
        // validated by core.ore.Overrides instead, and a bad one is logged and skipped on its own (§10).
        ORE_CLASSIFICATION_OVERRIDES = builder
                .comment("Override the automatic rarity classification for specific ore blocks.",
                        "A list of \"modid:block_id=common|uncommon|rare\" strings, for example",
                        "\"minecraft:diamond_ore=rare\". Each entry is validated: a malformed entry, an unknown rarity or a",
                        "block outside the #c:ores tag is rejected with a warning in the server log.",
                        "Read once at server start; takes effect on the next world load.")
                .translation("orevault.configuration.ore_classification.overrides")
                .worldRestart()
                .defineListAllowEmpty("overrides", List.of(), () -> "", value -> value instanceof String);
        builder.pop();
        builder.translation("orevault.configuration.debug").push("debug");
        ENABLE_DEBUG_COMMANDS = builder
                .comment("Whether the debug subcommands of /orevault are usable. Off by default.",
                        "Operators only, on top of this flag: the flag says whether the commands exist on this",
                        "server, the permission says who may run them.",
                        "Takes effect on save: the command tree is re-sent to everyone online, so no relog.",
                        "The whole [debug] block is removed before 1.0.")
                .translation("orevault.configuration.debug.enable_debug_commands")
                .define("enable_debug_commands", false);
        LOG_RESONANCE_GAIN = builder
                .comment("Whether each Resonance orb pickup is logged at INFO: team, collector, orbs merged, the orb's",
                        "value and what the pool received after team scaling. Off by default.",
                        "Takes effect on save: it is read on each pickup.",
                        "The whole [debug] block is removed before 1.0.")
                .translation("orevault.configuration.debug.log_resonance_gain")
                .define("log_resonance_gain", false);
        builder.pop();
        SPEC = builder.build();
    }

    private OreVaultServerConfig() {
    }

    /**
     * Guarded on {@link ModConfigSpec#isLoaded()} because this is read inside a Brigadier {@code requires}
     * predicate, which can run before the server config loads: {@code get()} throws then, and a throwing predicate
     * breaks dispatch for everything under {@code /orevault} (old PR #119).
     */
    public static boolean enableDebugCommands() {
        return SPEC.isLoaded() && ENABLE_DEBUG_COMMANDS.getAsBoolean();
    }

    /** Read on each orb pickup by {@code ResonanceAward} (§10); off if the config has not loaded. */
    public static boolean logResonanceGain() {
        return SPEC.isLoaded() && LOG_RESONANCE_GAIN.getAsBoolean();
    }

    /** Read once, by the level curve at server start (§4.3 step 3); the default if the config has not loaded. */
    public static double targetPlayHours() {
        return SPEC.isLoaded() ? TARGET_PLAY_HOURS.getAsDouble() : TARGET_PLAY_HOURS.getDefault();
    }

    /** Read once, by the level curve at server start (§4.3 step 5); the default if the config has not loaded. */
    public static double curveDivisor() {
        return SPEC.isLoaded() ? CURVE_DIVISOR.getAsDouble() : CURVE_DIVISOR.getDefault();
    }

    /** Read once, by the ore classifier at server start (§10, §11); empty if the config has not loaded. */
    public static List<String> oreClassificationOverrides() {
        return SPEC.isLoaded() ? List.copyOf(ORE_CLASSIFICATION_OVERRIDES.get()) : List.of();
    }
}
