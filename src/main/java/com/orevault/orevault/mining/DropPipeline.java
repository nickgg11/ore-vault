package com.orevault.orevault.mining;

import com.orevault.orevault.core.key.VaultKeys;
import com.orevault.orevault.core.mining.BreakContext;
import com.orevault.orevault.core.mining.BreakSource;
import com.orevault.orevault.core.mining.DropOutcome;
import com.orevault.orevault.core.mining.DropStage;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.ore.ClassifiedOres;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.team.TeamHelper;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jspecify.annotations.Nullable;

/**
 * The mod's one {@link BlockDropsEvent} listener (§11 Drop Pipeline, `CLAUDE.md` rule 9). Every node that changes what a break drops
 * registers into one of the six {@link DropStage}s instead of adding a listener, because the order the nodes apply in
 * changes the result, and two listeners have no order.
 *
 * <p>Each break's {@link BreakContext} is worked out once, before any stage, so the stages, the award, the stats and the
 * travel counters read one answer to "whose Vault, which team, hand or machine, what ore" (old #26, #92, #117).
 *
 * <p>No node registers yet: the stages are a fixed contract with nothing in them. The base ore orb is the one built-in
 * step, at the start of {@link DropStage#BONUS} (stage 5 holds "XP and orbs"), so a Consume node such as Tithe (E9-S31)
 * runs before it and the award can read what Consume did. The Fortune re-roll, the {@code ItemEntity} re-wrapping and the
 * guard against a nonsense contribution come with the first Fortune node, E9-S26 (#330).
 *
 * <p>The listener does not receive a cancelled event: a mod that cancelled the drops cancelled the XP with them, and a
 * break the world did not complete pays nothing. FTB Ultimine cancels its original {@code BreakBlockEvent} and breaks
 * each block again as the player, so each block of a chain arrives here once, as a hand break (§9). Main thread only:
 * NeoForge posts this event from the block's removal on the server thread.
 */
public final class DropPipeline {
    /** A node's part in one stage. Consume, Fortune and Quantity write only to the outcome; the others edit the drops. */
    @FunctionalInterface
    public interface StageHandler {
        void apply(VaultBreak brk, DropOutcome outcome);
    }

    private static final Map<DropStage, List<StageHandler>> STAGES = new EnumMap<>(DropStage.class);

    static {
        for (DropStage stage : DropStage.values()) {
            STAGES.put(stage, new CopyOnWriteArrayList<>());
        }
    }

    private DropPipeline() {
    }

    /**
     * Called once from the mod constructor. A gameplay event, so the game bus ({@code CLAUDE.md} rule 3); cancelled events
     * are not received. At {@code LOWEST} so a protection mod that cancels the drops at any priority does so before the orb
     * spawns: otherwise a cancel at {@code LOW} would come after a paid orb.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockDropsEvent.class, DropPipeline::onBlockDrops);
    }

    /** Adds a node to a stage, at mod init. Within a stage, nodes run in the order they were added. */
    public static void addStage(DropStage stage, StageHandler handler) {
        STAGES.get(stage).add(handler);
    }

    private static void onBlockDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        Identifier dimension = level.dimension().identifier();
        Optional<UUID> vaultTeam = VaultKeys.teamOfDimension(dimension.getNamespace(), dimension.getPath());
        if (vaultTeam.isEmpty()) {
            return; // Harvester's Lot, the one change outside a Vault (§11), joins here with E9.
        }
        ServerPlayer handBreaker = handBreaker(level, event.getBreaker());
        BreakContext context = context(vaultTeam.get(), handBreaker, event.getState());
        run(new VaultBreak(event, context, handBreaker));
    }

    /**
     * The breaker when the break was by hand, otherwise null (§3.4): a {@code ServerPlayer} that is not a fake player and is
     * the instance the player list holds for its UUID.
     */
    private static @Nullable ServerPlayer handBreaker(ServerLevel level, @Nullable Entity breaker) {
        boolean serverPlayer = breaker instanceof ServerPlayer;
        ServerPlayer player = serverPlayer ? (ServerPlayer) breaker : null;
        boolean fake = player != null && player.isFakePlayer();
        boolean listed = player != null && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
        return BreakSource.of(serverPlayer, fake, listed) == BreakSource.HAND ? player : null;
    }

    private static BreakContext context(UUID vaultTeam, @Nullable ServerPlayer handBreaker, BlockState state) {
        BreakSource source = handBreaker == null ? BreakSource.MACHINE : BreakSource.HAND;
        // Asked only for a hand break: a fake player's team is a phantom id (§9).
        boolean member = handBreaker != null && TeamHelper.teamId(handBreaker).equals(vaultTeam);
        return new BreakContext(source, vaultTeam, member, rarity(state));
    }

    /** The block's classified rarity, or null: stone, deepslate, an unreadable ore, or no table published (§4.2). */
    private static @Nullable Rarity rarity(BlockState state) {
        ClassifiedOres ores = OreClassifier.published();
        if (ores == null) {
            return null;
        }
        OreTable.Entry entry = ores.table().lookup(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        return entry == null ? null : entry.rarity();
    }

    private static void run(VaultBreak brk) {
        DropOutcome outcome = new DropOutcome();
        runStage(DropStage.CONSUME, brk, outcome);
        if (outcome.consumed()) {
            brk.event().getDrops().clear(); // items only: the XP stays (§3.4)
        }
        boolean ancientDebris = brk.event().getState().is(Blocks.ANCIENT_DEBRIS);
        for (DropStage stage : DropStage.afterConsume(outcome.consumed(), ancientDebris)) {
            if (stage == DropStage.BONUS) {
                spawnOreOrb(brk);
            }
            runStage(stage, brk, outcome);
        }
    }

    private static void runStage(DropStage stage, VaultBreak brk, DropOutcome outcome) {
        for (StageHandler handler : STAGES.get(stage)) {
            handler.apply(brk, outcome);
        }
    }

    /** §4.2: 2, 5 or 12.5 at the block's centre, where vanilla pops the block's XP; nothing for a break that pays nothing. */
    private static void spawnOreOrb(VaultBreak brk) {
        BreakContext context = brk.context();
        long value = context.awardThousandths();
        if (value > 0L) {
            ResonanceOrbEntity.spawn(brk.event().getLevel(), Vec3.atCenterOf(brk.event().getPos()), context.vaultTeam(), value);
        }
    }
}
