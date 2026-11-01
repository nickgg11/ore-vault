package com.orevault.orevault.gametest;

import com.orevault.orevault.registry.ModItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** §3.3: Tier 1 is iron and redstone side by side, and the shaped recipe holds them to that. */
final class VaultKeyTests {
    private static final List<ItemStack> IRON_AND_REDSTONE =
            List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE));

    private VaultKeyTests() {
    }

    /** The stack size is set in the item's constructor, so a later tier inherits this. */
    static void stacksToOne(GameTestHelper helper) {
        int maxStackSize = ModItems.CRUDE_VAULT_KEY.toStack().getMaxStackSize();
        if (maxStackSize != 1) {
            helper.fail(Component.literal("stacks to " + maxStackSize));
        }
        helper.succeed();
    }

    static void recipeTakesIronAndRedstoneSideBySide(GameTestHelper helper) {
        ItemStack sideBySide = craft(helper, CraftingInput.of(2, 1, IRON_AND_REDSTONE));
        if (!sideBySide.is(ModItems.CRUDE_VAULT_KEY.get()) || sideBySide.getCount() != 1) {
            helper.fail(Component.literal("side by side crafted " + sideBySide));
        }
        ItemStack stacked = craft(helper, CraftingInput.of(1, 2, IRON_AND_REDSTONE));
        if (!stacked.isEmpty()) {
            helper.fail(Component.literal("stacked crafted " + stacked));
        }
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }
}
