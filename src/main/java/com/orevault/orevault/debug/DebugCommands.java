package com.orevault.orevault.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.vault.VaultDimensions;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The debug subcommands of {@code /orevault} (S44; §10); {@code density} is {@link DensityCommand} (E4-S4). The rest of S44 reads state that does not exist yet and lands
 * with it. All of this is removed at T14.
 *
 * <p>The guard sits on each subcommand, never the root: {@code reset vote} and {@code archive} live under the same
 * root and must work with this flag off (#205, amended 2026-10-27). The operator check and the flag answer different
 * questions, so both apply. {@code team} carries the guard on its own node because every subcommand S44 puts under it
 * (dump, wipe, grant) is debug; a player-facing {@code team} subcommand would move the guard to the leaves. The flag
 * arrives as a supplier because {@code debug} may not import {@code config} (ADR 0001); the root package hands it over,
 * and hands over the orb spawn the same way, because {@code debug} may not import {@code mining} either.
 */
public final class DebugCommands {
    private static final Logger LOG = LoggerFactory.getLogger(DebugCommands.class);
    private static final String TAG = "[orevault-debug] ";

    private DebugCommands() {
    }

    /** {@code ResonanceOrbEntity#spawn}, handed over by the root package; false when nothing was spawned. */
    @FunctionalInterface
    public interface OrbSpawner {
        boolean spawn(ServerLevel level, Vec3 pos, UUID team, long valueThousandths);
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, BooleanSupplier enabled, OrbSpawner orbs) {
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
        root.then(Commands.literal("vault").requires(allowed)
                .then(Commands.literal("create")
                        .then(Commands.argument("team", UuidArgument.uuid())
                                .executes(context -> createVault(context, VaultVariant.BASE))
                                .then(Commands.literal("base").executes(context -> createVault(context, VaultVariant.BASE)))
                                .then(Commands.literal("expanded")
                                        .executes(context -> createVault(context, VaultVariant.EXPANDED))))));
        root.then(Commands.literal("team").requires(allowed)
                .then(Commands.literal("dump")
                        .then(Commands.argument("team", UuidArgument.uuid())
                                .executes(context -> dumpTeam(context.getSource(), UuidArgument.getUuid(context, "team")))))
                .then(Commands.literal("resonance")
                        .then(Commands.literal("add")
                                .then(Commands.argument("team", UuidArgument.uuid())
                                        .then(Commands.argument("thousandths", LongArgumentType.longArg(0L))
                                                .executes(context -> addResonance(context.getSource(),
                                                        UuidArgument.getUuid(context, "team"),
                                                        LongArgumentType.getLong(context, "thousandths"))))))));
        root.then(DensityCommand.node(allowed));
        root.then(Commands.literal("orb").requires(allowed)
                .then(Commands.literal("spawn")
                        .then(Commands.argument("thousandths", LongArgumentType.longArg(0L))
                                .executes(context -> spawnOrb(context.getSource(), orbs,
                                        LongArgumentType.getLong(context, "thousandths"),
                                        TeamHelper.teamId(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("team", UuidArgument.uuid())
                                        .executes(context -> spawnOrb(context.getSource(), orbs,
                                                LongArgumentType.getLong(context, "thousandths"),
                                                UuidArgument.getUuid(context, "team")))))));
    }

    /**
     * A Resonance orb at the source's position, for seeing orbs in a client without a Vault or an ore (QA, #260). A
     * {@code /summon} makes an orb of value 0, which is discarded on its first tick; this sets the value and the team.
     * The team defaults to the caller's, so a member collects it at once; name another team to see one lie still. A value
     * of 0 is refused, as the spawn refuses it (Balancer).
     */
    private static int spawnOrb(CommandSourceStack source, OrbSpawner orbs, long thousandths, UUID team) {
        Vec3 pos = source.getPosition();
        String where = String.format(Locale.ROOT, "%.2f %.2f %.2f", pos.x, pos.y, pos.z);
        if (!orbs.spawn(source.getLevel(), pos, team, thousandths)) {
            source.sendFailure(Component.literal(TAG + "orb spawn team=" + team + " value=" + thousandths
                    + " refused: an orb needs a value above 0"));
            return 0;
        }
        print(source, "orb spawn team=" + team + " value=" + thousandths + " at " + where + " in "
                + source.getLevel().dimension().identifier());
        return 1;
    }

    /**
     * S44's team dump, read-only: it goes through {@link OreVaultTeamData#read}, which creates nothing, so dumping a team
     * with no file leaves it with none (S18). The restart check compares it before and after a stop (ADR 0005 section 6).
     * E5-S5 (#275) extends it.
     */
    private static int dumpTeam(CommandSourceStack source, UUID teamId) {
        MinecraftServer server = source.getServer();
        TeamDump.Record record = OreVaultTeamData.isUnreadable(server, teamId) ? TeamDump.Record.UNREADABLE
                : OreVaultTeamData.hasRecord(server, teamId) ? TeamDump.Record.OK : TeamDump.Record.NONE;
        print(source, new TeamDump(teamId, record, OreVaultTeamData.read(server, teamId)).line());
        return 1;
    }

    /**
     * Interim, for the restart check (#241): adds raw thousandths to a team's pool through the seam the award path will
     * call, {@link OreVaultTeamData#addResonance} (#264). It only raises the pool and never touches {@code level_reached}, so no stored
     * field is lowered (§11). E5-S8's (#279) {@code grant} replaces it once the award exists, because a grant must pay every
     * level it crosses. The line carries the fixed token {@code resonance add} so the pacing parser leaves it out.
     *
     * <p>If the team file cannot be read and cannot be copied aside, {@code forWrite} hands back data that is never saved
     * (ADR 0005 section 2), so the reply shows a pool that is lost at the next start. The dump's {@code record=unreadable}
     * is how a session sees that state; the restart lists reject it.
     */
    private static int addResonance(CommandSourceStack source, UUID teamId, long thousandths) {
        long pool = OreVaultTeamData.forWrite(source.getServer(), teamId).addResonance(thousandths);
        print(source, TeamDump.added(teamId, thousandths, pool));
        return 1;
    }

    /**
     * S44: a team's Vault by command, so QA's scripted restart and E4-S4 need no client. The second way a Vault is made,
     * beside the portal (ArchUnit named rule 7). A Vault whose folder exists keeps the type in its file, whatever is asked,
     * and the line says which it has.
     */
    private static int createVault(CommandContext<CommandSourceStack> context, VaultVariant forANewVault) {
        CommandSourceStack source = context.getSource();
        UUID teamId = UuidArgument.getUuid(context, "team");
        Optional<ServerLevel> vault = VaultDimensions.findOrCreate(source.getServer(), teamId, forANewVault);
        if (vault.isEmpty()) {
            source.sendFailure(Component.literal(TAG + "vault team=" + teamId + " refused: its type file cannot be read; "
                    + "the server log names it"));
            return 0;
        }
        print(source, "vault team=" + teamId + " dim=" + vault.get().dimension().identifier() + " type="
                + VaultDimensions.variant(teamId).map(VaultVariant::id).orElse("?"));
        return 1;
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
