package com.orevault.orevault.vault;

import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** The player's way into the Vault (§3.3): pressed into a frame, door or portal, it opens that portal (§3.2). */
public final class SignetItem extends Item {
    private final SignetTier tier;

    public SignetItem(Properties properties, SignetTier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    /** Our open sound for a press that opens (Nick, 2026-11-08; #407). Only a sound says a press was refused: like a nether portal, the frame opens or it does not (§3.2). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!VaultOpenings.takesASignet(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            boolean opened = VaultOpenings.press(server, context.getClickedPos(), tier);
            if (opened) {
                server.playSound(null, context.getClickedPos(), ModSounds.PORTAL_OPEN.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                server.playSound(null, context.getClickedPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        for (SignetTier.TooltipLine line : tier.tooltipLines()) {
            builder.accept(Component.translatable(line.key(), line.args().toArray())
                    .withStyle(line.hint() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
        }
    }
}
