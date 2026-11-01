package com.orevault.orevault.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code orevault-server.toml} (§10). A value joins this class in the ticket that first reads it, never before:
 * the old build had four values nothing read (rule 7, design log 2026-09-03).
 */
public final class OreVaultServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_COMMANDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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
}
