package com.orevault.orevault.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.VaultFrameBlock;
import com.orevault.orevault.block.VaultPortalBlock;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.event.OreDropHandler;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Playtest instrumentation. Everything here is temporary and none of it is
 * gameplay.
 *
 * <p>It began as passive logging for #82 ("blocks unbreakable in team-created
 * Vaults"): three listeners at {@code EventPriority.LOWEST} on
 * {@code LeftClickBlock}, {@code PlayerEvent.BreakSpeed} and
 * {@code BreakBlockEvent}. Both #82 and #89 are root-caused and closed, and the
 * listeners were firing on every left-click in a Vault, so they are gone —
 * a diagnostic that outlives its bug is just noise in the log you need next.</p>
 *
 * <p>What remains is on demand rather than always-on:</p>
 * <ul>
 *   <li>{@link #onResonanceGained} prints what each orb paid, because until the
 *       Tome ([35]) there is no readout of the pool at all and a working build
 *       looks exactly like a broken one while mining.</li>
 *   <li>{@code /orevault diag [pos]} prints break state for a block: spawn
 *       protection, world border, may-interact, gamemode, destroy progress, and
 *       the server tick the dimension was created at.</li>
 *   <li>{@code /orevault testore [radius] [ore]} fills a cube with ore so the
 *       Resonance loop can be exercised in a minute.</li>
 * </ul>
 *
 * <p>Both commands are gated on {@code [debug] enable_debug_commands} <em>and</em>
 * operator permission. The config gate is what makes the class safe to ship in a
 * pre-1.0 jar; it is not a substitute for deleting it. This whole file should
 * leave the tree before 1.0.</p>
 */
public final class VaultDiag {

    /** Log tag so the pack's {@code latest.log} can be grepped for diagnostics. */
    public static final String TAG = "[orevault-diag]";

    /** Vault dimensions created after server startup, keyed by creation server tick (#82). */
    private static final Map<ResourceKey<Level>, Integer> CREATED_AT_TICK = new ConcurrentHashMap<>();

    /** Cube half-width filled by {@code /orevault testore} when none is given. */
    private static final int DEFAULT_TEST_RADIUS = 6;

    /** Ceiling on the same. A radius of 32 is a 65³ cube — 274k setBlock calls. */
    private static final int MAX_TEST_RADIUS = 32;

    private VaultDiag() {
    }

    /** Records that a vault dimension was created at the given server tick (#82 diagnostics). */
    public static void markCreated(ResourceKey<Level> key, int serverTick) {
        CREATED_AT_TICK.put(key, serverTick);
    }

    // ----- Resonance gain readout -----

    /**
     * Prints what an orb just paid, to the player who collected it.
     *
     * <p>Until the Tome lands there is no readout of the pool at all: an orb
     * drifts over, disappears, and the only visible feedback is the level-up
     * overlay, which fires once every few hundred breaks. That makes a working
     * build and a broken one look identical while mining, which is exactly the
     * false negative this exists to prevent.</p>
     *
     * <p>The line quotes the pool <em>after</em> team scaling rather than the
     * orb's face value, because those differ on any team larger than one and the
     * difference is the part worth watching (§4.2).</p>
     */
    public static void onResonanceGained(ServerPlayer player, UUID teamId, int orbValue, ResonanceSystem.LevelUp levelUp) {
        if (!OreVaultServerConfig.logResonanceGain()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.getOrCreate(server.overworld(), teamId);
        int percent = (int) Math.round(ResonanceSystem.progressToNextLevel(data, ResonanceSystem.curve()) * 100);

        StringBuilder line = new StringBuilder(String.format(
                "+%d Resonance → pool %d · level %d (%d%% to %d)",
                orbValue, data.getResonancePool(), levelUp.newLevel(), percent, levelUp.newLevel() + 1));
        if (levelUp.leveledUp()) {
            line.append(String.format(" · LEVEL UP, +%d skill points (%d unspent)",
                    levelUp.pointsAwarded(), data.getResonanceSkillPoints()));
        }
        player.sendSystemMessage(Component.literal(TAG + " " + line));
    }

    // ----- commands -----

    /**
     * Operator <em>and</em> the config flag, applied to the {@code /orevault}
     * root so every subcommand inherits it and none can be added without it.
     *
     * <p>Two independent gates because they answer different questions. The flag
     * says whether these commands exist on this server at all; the permission
     * says who may run them where they do. An op on a server that has not opted
     * in still gets nothing, which is the point — {@code testore} rewrites
     * terrain, and shipping it live-by-default in a pre-1.0 jar is how someone
     * loses a Vault they cared about.</p>
     *
     * <p>26.1 replaced the int permission level with a {@code PermissionSet};
     * {@code LEVEL_GAMEMASTERS} is the old 2.</p>
     */
    private static Predicate<CommandSourceStack> debugCommandsAllowed() {
        return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS)
                .and(source -> OreVaultServerConfig.enableDebugCommands());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("orevault")
                        .requires(debugCommandsAllowed())
                        .then(literal("diag")
                                .executes(ctx -> run(ctx.getSource(), null))
                                .then(argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                        .then(literal("testore")
                                .executes(ctx -> fillTestOre(ctx.getSource(), DEFAULT_TEST_RADIUS, Blocks.COAL_ORE))
                                .then(argument("radius", IntegerArgumentType.integer(1, MAX_TEST_RADIUS))
                                        .executes(ctx -> fillTestOre(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius"), Blocks.COAL_ORE))
                                        .then(argument("ore", BlockStateArgument.block(event.getBuildContext()))
                                                .executes(ctx -> fillTestOre(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        BlockStateArgument.getBlock(ctx, "ore").getState().getBlock())))))
        );
    }

    // ----- /orevault testore [radius] [ore] -----

    /**
     * Fills a cube around the player with an ore, so the Resonance loop can be
     * exercised in a minute instead of an hour.
     *
     * <p>A command rather than a worldgen switch on purpose. Dense generation
     * would only affect chunks not yet generated, so testing it means abandoning
     * the Vault you are standing in and walking until the terrain changes. This
     * works where you already are, and deleting the method is the whole
     * revert.</p>
     *
     * <p>It reports the block's <em>live</em> classification and what that pays,
     * because the classifier resolves rarity from placed features at server
     * start — so which vanilla ore counts as rare is a property of the pack, not
     * something that can be assumed here.</p>
     */
    private static int fillTestOre(CommandSourceStack source, int radius, Block ore) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!VaultDimensions.isVaultDimension(level)) {
            source.sendFailure(Component.literal("Not in a Vault dimension"));
            return 0;
        }

        BlockState state = ore.defaultBlockState();
        BlockPos centre = player.blockPosition();
        int filled = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            if (!isSafeToOverwrite(level, pos, centre)) {
                continue;
            }
            level.setBlock(pos.immutable(), state, Block.UPDATE_ALL);
            filled++;
        }

        String classification = OreClassifier.isClassifiedOre(state)
                ? OreClassifier.getRarity(state) + ", " + OreDropHandler.baseResonance(OreClassifier.getRarity(state))
                        + " Resonance each"
                : "NOT a classified ore — this will pay nothing";
        int total = filled;
        source.sendSuccess(() -> Component.literal(String.format(
                "%s filled %d block(s) with %s (%s)",
                TAG, total, ore.getName().getString(), classification)), false);
        OreVault.LOGGER.info("{} testore radius={} block={} filled={} ({})",
                TAG, radius, ore, filled, classification);
        return 1;
    }

    /**
     * Keeps the command from bricking the Vault it is run in: bedrock is the
     * floor, the portal is the way out, and burying the player in ore would
     * suffocate them.
     */
    private static boolean isSafeToOverwrite(ServerLevel level, BlockPos pos, BlockPos centre) {
        if (pos.getX() == centre.getX() && pos.getZ() == centre.getZ()
                && pos.getY() >= centre.getY() && pos.getY() <= centre.getY() + 1) {
            return false; // the two blocks the player occupies
        }
        BlockState existing = level.getBlockState(pos);
        // Matched by type, not by holder: there are four tier-coloured portal
        // blocks and naming one of them would leave the other three fillable.
        return !existing.isAir()
                && !existing.is(Blocks.BEDROCK)
                && !(existing.getBlock() instanceof VaultPortalBlock)
                && !(existing.getBlock() instanceof VaultFrameBlock);
    }

    private static int run(CommandSourceStack source, @Nullable BlockPos explicitPos) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = explicitPos != null ? explicitPos : aimedBlock(player);
        for (String line : diagLines(level, pos, player)) {
            source.sendSuccess(() -> Component.literal(line), false);
            OreVault.LOGGER.info("{} {}", TAG, line);
        }
        return 1;
    }

    private static BlockPos aimedBlock(ServerPlayer player) {
        HitResult hit = player.pick(5.0, 0.0F, false);
        return hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : player.blockPosition();
    }

    private static List<String> diagLines(ServerLevel level, BlockPos pos, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        BlockState state = level.getBlockState(pos);
        List<String> lines = new ArrayList<>();
        lines.add("dim=" + level.dimension().identifier()
                + " createdAtTick=" + CREATED_AT_TICK.getOrDefault(level.dimension(), -1)
                + " skyLight=" + level.dimensionType().hasSkyLight()
                + " fixedTime=" + level.dimensionType().hasFixedTime());
        lines.add("pos=" + pos + " block=" + state
                + " destroyProgress=" + state.getDestroyProgress(player, level, pos)
                + " itemDestroySpeed=" + player.getMainHandItem().getDestroySpeed(state)
                + " canHarvest=" + state.canHarvestBlock(level, pos, player));
        lines.add("mayInteract=" + level.mayInteract(player, pos)
                + " spawnProtection=" + server.isUnderSpawnProtection(level, pos, player)
                + " borderWithin=" + level.getWorldBorder().isWithinBounds(pos)
                + " borderSize=" + level.getWorldBorder().getSize());
        lines.add("gamemode=" + player.gameMode.getGameModeForPlayer()
                + " mayBuild=" + player.getAbilities().mayBuild
                + " instabuild=" + player.getAbilities().instabuild
                + " blockActionRestricted=" + player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer()));
        lines.add("team=" + TeamHelper.getTeam(player).map(team -> team.getTeamId().toString()).orElse("NONE")
                + " mainHand=" + player.getMainHandItem());
        return lines;
    }
}
