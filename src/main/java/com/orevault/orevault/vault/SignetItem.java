package com.orevault.orevault.vault;

import com.orevault.orevault.core.signet.SignetTier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    /** Only a sound says a press was refused: like a nether portal, the frame opens or it does not (§3.2). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!VaultOpenings.takesASignet(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean opened = VaultOpenings.press(level, context.getClickedPos(), tier);
        level.playSound(null, context.getClickedPos(), opened ? SoundEvents.PORTAL_TRIGGER : SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
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
