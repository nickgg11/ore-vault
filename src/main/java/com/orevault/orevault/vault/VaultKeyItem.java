package com.orevault.orevault.vault;

import com.orevault.orevault.core.vaultkey.VaultKeyTier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The player's key to the Vault (§3.3). Unlocking a frame arrives with the portal stories (E3-S2, E3-S4). */
public final class VaultKeyItem extends Item {
    private final VaultKeyTier tier;

    public VaultKeyItem(Properties properties, VaultKeyTier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        for (VaultKeyTier.TooltipLine line : tier.tooltipLines()) {
            builder.accept(Component.translatable(line.key(), line.args().toArray())
                    .withStyle(line.hint() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
        }
    }
}
