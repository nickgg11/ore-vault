package com.orevault.orevault.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Playtest diagnostics for #82 ("blocks unbreakable in team-created Vaults").
 *
 * <p>Passive instrumentation on the game event bus logs — at
 * {@link EventPriority#LOWEST}, so after every other listener — the three
 * events that gate block breaking inside any vault dimension:
 * {@code LeftClickBlock} (cancelled by claim/protection mods),
 * {@code PlayerEvent.BreakSpeed} (a mod zeroing the break speed freezes the
 * crack progress forever), and {@code BreakBlockEvent} (26.1's renamed
 * {@code BlockEvent.BreakEvent}). All lines are tagged {@value #TAG} so the
 * pack's {@code logs/latest.log} can be grepped.</p>
 *
 * <p>The {@code /orevault diag [pos]} command prints the same state
 * synchronously for the aimed-at (or given) block: spawn protection, world
 * border, may-interact, gamemode, computed destroy progress and item destroy
 * speed — plus the server tick at which the dimension was created (startup
 * dimensions were created at tick 0, team-created ones mid-session).</p>
 *
 * <p>Temporary: removed once #82 is root-caused.</p>
 */
public final class VaultDiag {

    /** Log tag so the pack's {@code latest.log} can be grepped for diagnostics. */
    public static final String TAG = "[orevault-diag]";

    /** Vault dimensions created after server startup, keyed by creation server tick (#82). */
    private static final Map<ResourceKey<Level>, Integer> CREATED_AT_TICK = new ConcurrentHashMap<>();

    private VaultDiag() {
    }

    /** Records that a vault dimension was created at the given server tick (#82 diagnostics). */
    public static void markCreated(ResourceKey<Level> key, int serverTick) {
        CREATED_AT_TICK.put(key, serverTick);
    }

    // ----- passive instrumentation -----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && VaultDimensions.isVaultDimension(level)) {
            OreVault.LOGGER.info("{} LEFT_CLICK dim={} pos={} player={} canceled={} useBlock={}",
                    TAG, level.dimension().identifier(), event.getPos(),
                    event.getEntity().getName().getString(), event.isCanceled(), event.getUseBlock());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().level() instanceof ServerLevel level && VaultDimensions.isVaultDimension(level)) {
            OreVault.LOGGER.info("{} BREAK_SPEED dim={} pos={} block={} original={} new={} canceled={} player={}",
                    TAG, level.dimension().identifier(),
                    event.getPosition().map(BlockPos::toString).orElse("unknown"),
                    event.getState(), event.getOriginalSpeed(), event.getNewSpeed(), event.isCanceled(),
                    event.getEntity().getName().getString());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakBlock(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && VaultDimensions.isVaultDimension(level)) {
            Player player = event.getPlayer();
            OreVault.LOGGER.info("{} BREAK_BLOCK dim={} pos={} block={} canceled={} notifyClient={} player={} mayInteract={} spawnProtection={}",
                    TAG, level.dimension().identifier(), event.getPos(), event.getState(),
                    event.isCanceled(), event.shouldNotifyClient(), player.getName().getString(),
                    level.mayInteract(player, event.getPos()),
                    level.getServer().isUnderSpawnProtection(level, event.getPos(), player));
        }
    }

    // ----- /orevault diag [pos] -----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("orevault").then(literal("diag")
                        .executes(ctx -> run(ctx.getSource(), null))
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
        );
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
