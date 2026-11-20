package com.orevault.orevault.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code orevault-server.toml} (§10). A value joins this class in the ticket that first reads it, never before:
 * the old build had four values nothing read (rule 7, design log 2026-09-03).
 */
public final class OreVaultServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_COMMANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_CLASSIFICATION_OVERRIDES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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

    /** Read once, by the ore classifier at server start (§10, §11); empty if the config has not loaded. */
    public static List<String> oreClassificationOverrides() {
        return SPEC.isLoaded() ? List.copyOf(ORE_CLASSIFICATION_OVERRIDES.get()) : List.of();
    }
}
