package com.orevault.orevault.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Tome of the Deep Seam (§8): the book a player spends skill points from.
 *
 * <p>Every player is given one on first join and it is the only way into the
 * skill tree, so it is closer to a permanent UI key than to an item.</p>
 *
 * <h2>The screen is installed, not referenced</h2>
 *
 * <p>Opening a screen is client-only work, and this class is on the common
 * path — a direct reference to a {@code Screen} here would kill a dedicated
 * server at class-load. So the client installs an opener at startup through
 * {@link #setScreenOpener} and this class holds nothing but a
 * {@link Consumer}. Same shape as {@code ModNetwork}'s client handler, and for
 * the same reason.</p>
 *
 * <p>Until the Tome screen lands ([34], #35) nothing installs one, and a
 * right-click says so rather than doing nothing — an item that appears inert is
 * indistinguishable from a broken one.</p>
 */
public class TomeItem extends Item {

    /** Installed by the client at startup; {@code null} until [34] (#35) exists. */
    private static volatile @Nullable Consumer<Player> screenOpener;

    public TomeItem(Item.Properties properties) {
        super(properties);
    }

    /** Installs the client-side screen opener. Called only from client code. */
    public static void setScreenOpener(Consumer<Player> opener) {
        screenOpener = opener;
    }

    // ----- opening -----

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Nothing happens from the offhand, which is half of why it does not
        // belong there; inventoryTick enforces the other half.
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }

        Consumer<Player> opener = screenOpener;
        if (opener == null) {
            player.sendSystemMessage(
                    Component.translatable("message.orevault.tome_not_ready").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        opener.accept(player);
        return InteractionResult.SUCCESS;
    }

    // ----- keeping it out of the offhand (§8) -----

    /**
     * Refuses the offhand slot.
     *
     * <p>The vanilla default already answers false here, because an ordinary
     * item's equipment slot is the main hand. It is stated anyway: this is a
     * rule of the item, not an accident of what {@code getEquipmentSlotForItem}
     * happens to return, and someone giving the Tome an equipment slot later
     * should have to delete this line deliberately.</p>
     */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        return slot != EquipmentSlot.OFFHAND && super.canEquip(stack, slot, entity);
    }

    /**
     * Evicts the Tome from the offhand if it gets there anyway.
     *
     * <p>{@link #canEquip} covers the equip path, but a player can drag a stack
     * into the offhand slot in the inventory screen and no vanilla hook rejects
     * that. This is the enforcement that actually holds: server-side, so a
     * client cannot skip it, and it moves the book rather than deleting it.</p>
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        if (slot != EquipmentSlot.OFFHAND || !(owner instanceof Player player)) {
            return;
        }
        ItemStack moved = stack.copy();
        stack.setCount(0);
        if (!player.getInventory().add(moved)) {
            player.drop(moved, false);
        }
    }

    // ----- tooltip -----

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.orevault.tome.open").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.orevault.tome.shared").withStyle(ChatFormatting.DARK_GRAY));
    }
}
