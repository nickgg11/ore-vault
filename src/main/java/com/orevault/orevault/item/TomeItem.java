package com.orevault.orevault.item;

import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tome of the Deep Seam — opens the three-tab Ore Vault GUI. Mainhand/inventory only;
 * right-click to open.
 */
public class TomeItem extends Item {
    public TomeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (TeamHelper.teamIdFor(serverPlayer) == null) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("orevault.msg.no_team"), true);
                return InteractionResult.FAIL;
            }
            // Server composes a full team snapshot and sends it; the client opens the screen.
            ModNetwork.sendTeamDataTo(serverPlayer, true);
        }
        return InteractionResult.SUCCESS;
    }
}

