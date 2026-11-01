package com.orevault.orevault.vault;

import com.orevault.orevault.core.signet.SignetTier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The player's way into the Vault (§3.3). Opening a frame arrives with the portal stories (E3-S2, E3-S4). */
public final class SignetItem extends Item {
    private final SignetTier tier;

    public SignetItem(Properties properties, SignetTier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
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
