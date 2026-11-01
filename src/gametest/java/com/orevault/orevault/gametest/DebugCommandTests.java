package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The commands through the real dispatcher (S44, #205). The flag is set in memory only and put back before a test
 * returns, and nothing else in the batch reads it.
 */
final class DebugCommandTests {
    private static final BlockPos TARGET = new BlockPos(0, 1, 0);

    private DebugCommandTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static CommandSourceStack source(GameTestHelper helper, PermissionSet permissions) {
        return helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPermission(permissions);
    }

    /** Sends a source's output to a list, so a test reads what a console or a player would be shown. */
    private static CommandSource recordingTo(List<String> lines) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                lines.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }

    private static int run(CommandDispatcher<CommandSourceStack> dispatcher, String command, CommandSourceStack source,
            GameTestHelper helper) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException e) {
            helper.fail(Component.literal(command + " did not run: " + e.getMessage()));
            return 0;
        }
    }

    private static void withDebugCommands(Runnable test) {
        OreVaultServerConfig.ENABLE_DEBUG_COMMANDS.set(true);
        try {
            test.run();
        } finally {
            OreVaultServerConfig.ENABLE_DEBUG_COMMANDS.set(false);
        }
    }

    /** §10: the root stays for everyone with the flag off; a subcommand needs the flag and the operator level both. */
    static void theRootIsAlwaysThereAndTheSubcommandsAreGated(GameTestHelper helper) {
        check(helper, "the server config loaded", true, OreVaultServerConfig.SPEC.isLoaded());
        CommandSourceStack gamemaster = source(helper, LevelBasedPermissionSet.GAMEMASTER);
        CommandSourceStack player = source(helper, PermissionSet.NO_PERMISSIONS);
        CommandNode<CommandSourceStack> root = dispatcher(helper).getRoot().getChild(OreVault.MOD_ID);

        check(helper, "flag off: root for a player", true, root.canUse(player));
        for (String subcommand : List.of("diag", "break")) {
            CommandNode<CommandSourceStack> node = root.getChild(subcommand);
            check(helper, "flag off: " + subcommand + " for a gamemaster", false, node.canUse(gamemaster));
            withDebugCommands(() -> {
                check(helper, "flag on: " + subcommand + " for a gamemaster", true, node.canUse(gamemaster));
                check(helper, "flag on: " + subcommand + " for a player", false, node.canUse(player));
                check(helper, "flag on: root for a player", true, root.canUse(player));
            });
            check(helper, "flag back off: " + subcommand, false, node.canUse(gamemaster));
        }
        helper.succeed();
    }

    static void breakRemovesTheBlockAndSaysSo(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "orevault break " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source, helper)));

        helper.assertBlockPresent(Blocks.AIR, TARGET);
        check(helper, "printed", List.of("[orevault-debug] break minecraft:overworld " + pos.getX() + " " + pos.getY()
                + " " + pos.getZ() + " minecraft:stone: destroyBlock=true gone=true"), lines);
        helper.succeed();
    }

    /** A wrong position in a restart script fails rather than reading as a block that went. */
    static void breakOnAirFails(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.AIR);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 0, run(dispatcher(helper),
                "orevault break " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source, helper)));

        check(helper, "printed", 1, lines.size());
        check(helper, "says why", true, lines.getFirst().endsWith("nothing to break"));
        helper.succeed();
    }

    /**
     * QA's restart check as it will run (QA-38): from a console, through {@code execute in}, at a position in a chunk
     * nothing has loaded. Bedrock because it stands at this height whatever the seed generates.
     */
    static void breakLoadsTheChunkItNeeds(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos pos = new BlockPos(5000, 0, 5000);
        check(helper, "chunk loaded before the command", false, nether.hasChunkAt(pos));
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.OWNER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "execute in minecraft:the_nether run orevault break 5000 0 5000", source, helper)));

        check(helper, "printed", List.of("[orevault-debug] break minecraft:the_nether 5000 0 5000 minecraft:bedrock: "
                + "destroyBlock=true gone=true"), lines);
        helper.succeed();
    }

    /** A fake player, because a mock one cannot join with FTB Library loaded (see {@code TeamTests}). */
    static void diagPrintsTheBreakState(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "orevault-diag"));
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER)
                .withEntity(player)
                .withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "orevault diag " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source, helper)));

        String printed = String.join("\n", lines);
        for (String field : List.of("serverTick=" + helper.getLevel().getServer().getTickCount(), "levelGameTime=",
                "block=minecraft:stone", "destroyProgress=", "mayInteract=", "spawnProtection=", "insideBorder=true",
                "borderSize=", "gameMode=survival")) {
            check(helper, "printed " + field, true, printed.contains(field));
        }
        helper.succeed();
    }
}
