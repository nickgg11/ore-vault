package com.orevault.orevault.integration;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.event.OreDropHandler;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.team.TeamHelper;
import dev.ftb.mods.ftbultimine.api.blockbreaking.BlockBreakHandler;
import dev.ftb.mods.ftbultimine.api.neoforge.FTBUltimineEvent;
import dev.ftb.mods.ftbultimine.api.shape.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FTB Ultimine integration (soft dependency — this class only loads when Ultimine is
 * present). Hooks the block-break handler so Volatile Veins rolls once per Ultimine
 * operation, Ultimine Safety reduces the chance, and Ultimine Gambit raises it in exchange
 * for a Resonance bonus on safe operations.
 */
public final class FtbUltimineIntegration {
    private FtbUltimineIntegration() {
    }

    /** Guarded by ModList — only call when ftbultimine is loaded. */
    public static void register(IEventBus bus) {
        bus.addListener(FtbUltimineIntegration::onRegisterBreakHandlers);
    }

    private static void onRegisterBreakHandlers(FTBUltimineEvent.RegisterBlockBreakHandler event) {
        event.getEventData().consumer().accept(HANDLER);
    }

    private record OpState(long lastOpTick, boolean vanished, int oreBreaks) {
    }

    private static final Map<UUID, OpState> OP_STATES = new HashMap<>();

    private static final BlockBreakHandler HANDLER = new BlockBreakHandler() {
        @Override
        public Result breakBlock(Player player, BlockPos pos, BlockState state, Shape shape, BlockHitResult hitResult) {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !TeamHelper.isVaultDimension(player.level())) {
                return Result.PASS;
            }
            UUID teamId = TeamHelper.teamIdFor(serverPlayer);
            if (teamId == null) {
                return Result.PASS;
            }
            OreVaultTeamData data = OreVaultTeamData.get(serverplayer.getServer(), teamId);
            if (!data.isTradeoffActiveFor("volatile_veins", player.getUUID())) {
                return Result.PASS;
            }
            if (!OreClassifier.isOre(state.getBlock())) {
                return Result.PASS;
            }

            // Once per operation: roll the disappearance chance.
            OpState op = OP_STATES.get(player.getUUID());
            boolean newOperation = op == null || player.tickCount - op.lastOpTick() > 3;
            if (newOperation) {
                double chance = 0.02 - data.nodeTier("ultimine_safety") * 0.01;
                if (data.isTradeoffActiveFor("ultimine_gambit", player.getUUID())) {
                    chance += 0.05; // riskier Ultimine (effective +1 block)
                }
                boolean vanished = player.level().getRandom().nextDouble() < chance;
                op = new OpState(player.tickCount, vanished, 0);
                OP_STATES.put(player.getUUID(), op);
            }

            OpState current = new OpState(player.tickCount, op.vanished(), op.oreBreaks() + 1);
            OP_STATES.put(player.getUUID(), current);

            if (current.vanished() && current.oreBreaks() == 1) {
                // Disappearance: the connected vein vanishes, no drops from it.
                var stats = data.statsFor(player.getUUID());
                stats.registerVolatileTrigger();
                stats.addVolatileTrigger();
                OreDropHandler.vanishVein((net.minecraft.server.level.ServerLevel) player.level(),
                        state.getBlock(), pos, 64);
            }
            return Result.PASS; // let Ultimine continue breaking normally
        }

        @Override
        public void postBreak(Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !TeamHelper.isVaultDimension(player.level())) {
                return;
            }
            UUID teamId = TeamHelper.teamIdFor(serverPlayer);
            if (teamId == null) {
                return;
            }
            OpState op = OP_STATES.remove(player.getUUID());
            if (op == null) {
                return;
            }
            OreVaultTeamData data = OreVaultTeamData.get(serverplayer.getServer(), teamId);
            if (!op.vanished() && op.oreBreaks() > 0
                    && data.isTradeoffActiveFor("ultimine_gambit", player.getUUID())) {
                // 20% Resonance bonus on safe Ultimine operations.
                long bonus = Math.max(1, op.oreBreaks() * 2L / 5L);
                ResonanceSystem.addResonance(serverplayer.getServer(), teamId, bonus, player.getUUID());
            }
        }
    };
}

