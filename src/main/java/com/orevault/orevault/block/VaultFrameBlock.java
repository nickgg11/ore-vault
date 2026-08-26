package com.orevault.orevault.block;

import com.orevault.orevault.item.VaultIgniterItem;
import com.orevault.orevault.portal.VaultPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.core.Direction;

/**
 * Vault Frame block: 8 iron + 1 redstone. Right-click with any Vault Igniter triggers the
 * portal shape scan and fills the interior with portal blocks.
 */
public class VaultFrameBlock extends Block {
    public VaultFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof VaultIgniterItem igniter) {
            if (level instanceof ServerLevel serverLevel) {
                VaultPortalShape.Result shape = VaultPortalShape.scan(serverLevel, pos);
                if (shape.valid()) {
                    Direction longDir = shape.axis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
                    BlockState portal = ModBlocks.VAULT_PORTAL.get().defaultBlockState()
                            .setValue(VaultPortalBlock.AXIS, shape.axis());
                    for (int w = 1; w < shape.width() - 1; w++) {
                        for (int h = 0; h < shape.height(); h++) {
                            BlockPos interior = shape.minCorner().relative(longDir, w).above(h);
                            serverLevel.setBlockAndUpdate(interior, portal);
                        }
                    }
                    serverLevel.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
                            serverLevel.getRandom().nextFloat() * 0.4F + 0.8F);
                    igniter.onPortalIgnited(stack, player, serverLevel, pos);
                    return InteractionResult.SUCCESS;
                }
                serverLevel.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}

