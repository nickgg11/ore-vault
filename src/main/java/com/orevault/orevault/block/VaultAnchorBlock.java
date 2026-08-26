package com.orevault.orevault.block;

import com.orevault.orevault.chunk.VaultChunkLoader;
import com.orevault.orevault.item.VaultIgniterItem;
import com.orevault.orevault.portal.VaultTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Vault Anchor block: chunk loads its chunk while placed (Forge ticket) and doubles as a
 * waypoint — right-click with a Tier 3+ igniter sets it as the personal Vault entry point.
 */
public class VaultAnchorBlock extends Block {
    public VaultAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            VaultChunkLoader.onAnchorPlaced(serverLevel, pos);
        }
    }

    // Removal is detected by VaultChunkLoader's periodic sweep (26.1 removed Block#onRemove).

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof VaultIgniterItem igniter && igniter.tier() >= 3
                && player instanceof ServerPlayer serverPlayer && VaultTeleport.isInVault(serverPlayer)) {
            VaultTeleport.setEntryPoint(serverPlayer, pos.above());
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
