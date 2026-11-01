package com.orevault.orevault.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The debug subcommands of {@code /orevault} (S44; §10). The rest of S44 reads state that does not exist yet and lands
 * with it. All of this is removed at T14.
 *
 * <p>The guard sits on each subcommand, never the root: {@code reset vote} and {@code archive} live under the same
 * root and must work with this flag off (#205, amended 2026-10-27). The operator check and the flag answer different
 * questions, so both apply. The flag arrives as a supplier because {@code debug} may not import {@code config}
 * (ADR 0001); the root package hands it over.
 */
public final class DebugCommands {
    private static final Logger LOG = LoggerFactory.getLogger(DebugCommands.class);
    private static final String TAG = "[orevault-debug] ";

    private DebugCommands() {
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, BooleanSupplier enabled) {
        Predicate<CommandSourceStack> allowed = Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS)
                .and(source -> enabled.getAsBoolean());
        root.then(Commands.literal("diag").requires(allowed)
                .executes(context -> diag(context, false))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> diag(context, true))));
        root.then(Commands.literal("break").requires(allowed)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> breakBlock(context.getSource(),
                                BlockPosArgument.getBlockPos(context, "pos")))));
    }

    /** Everything is read from the player's level, the position's loaded check included: it diagnoses their break. */
    private static int diag(CommandContext<CommandSourceStack> context, boolean explicitPos) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos pos = explicitPos ? loadedPos(context, level) : aimedBlock(player);
        MinecraftServer server = level.getServer();
        BlockState state = level.getBlockState(pos);
        BreakDiagnosis diagnosis = new BreakDiagnosis(
                level.dimension().identifier().toString(),
                server.getTickCount(),
                level.getGameTime(),
                pos.toString(),
                blockId(state),
                state.getDestroyProgress(player, level, pos),
                level.mayInteract(player, pos),
                server.isUnderSpawnProtection(level, pos, player),
                level.getWorldBorder().isWithinBounds(pos),
                level.getWorldBorder().getSize(),
                player.gameMode.getGameModeForPlayer().getName());
        diagnosis.lines().forEach(line -> print(source, line));
        return 1;
    }

    /**
     * {@code BlockPosArgument.getLoadedBlockPos(context, level, name)} takes a level and then checks the source's
     * (read 2026-11-01, 26.1.2.95), so under {@code execute in} it answers for the wrong one.
     */
    private static BlockPos loadedPos(CommandContext<CommandSourceStack> context, ServerLevel level)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        if (!level.hasChunkAt(pos)) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
        if (!level.isInWorldBounds(pos)) {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
        return pos;
    }

    private static BlockPos aimedBlock(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos()
                : player.blockPosition();
    }

    /**
     * Breaks through a fake player because a gametest's player lives one server lifetime, and QA's restart check runs
     * this from the console before anyone connects (QA-38): {@code execute in orevault:vault_<id> run orevault break ...}.
     * The position is deliberately not the loaded form of the argument: nothing has loaded that chunk yet, and reading
     * the block here loads it.
     */
    private static int breakBlock(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockState before = level.getBlockState(pos);
        boolean destroyed = !before.isAir() && FakePlayerFactory.getMinecraft(level).gameMode.destroyBlock(pos);
        boolean gone = !before.isAir() && !level.getBlockState(pos).is(before.getBlock());
        String line = new BreakReport(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(),
                blockId(before), before.isAir(), destroyed, gone).line();
        if (gone) {
            print(source, line);
            return 1;
        }
        source.sendFailure(Component.literal(TAG + line));
        LOG.info("{}{}", TAG, line);
        return 0;
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static void print(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(TAG + line), false);
        LOG.info("{}{}", TAG, line);
    }
}
