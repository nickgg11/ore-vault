package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * §3.2. The break goes through {@code ServerPlayerGameMode#destroyBlock}, not {@code Block#getDrops}: the loot
 * table has no tool condition, so "by hand it drops nothing" only holds on the path a survival player takes.
 */
final class VaultFrameTests {
    private static final BlockPos FRAME = new BlockPos(0, 1, 0);
    private static final double DROP_RADIUS = 2.0;
    private static final GameProfile BREAKER =
            new GameProfile(UUID.fromString("0a7e5c1c-3f0b-4f6e-9f7a-1d3b2c4e5f60"), "orevault-breaker");

    private VaultFrameTests() {
    }

    /** The weakest pickaxe, because §3.2 says any pickaxe. */
    static void dropsItselfToAPickaxe(GameTestHelper helper) {
        breakFrameHolding(helper, new ItemStack(Items.WOODEN_PICKAXE));
        helper.assertItemEntityCountIs(ModItems.VAULT_FRAME.get(), FRAME, DROP_RADIUS, 1);
        helper.succeed();
    }

    static void dropsNothingByHand(GameTestHelper helper) {
        breakFrameHolding(helper, ItemStack.EMPTY);
        helper.assertItemEntityCountIs(ModItems.VAULT_FRAME.get(), FRAME, DROP_RADIUS, 0);
        helper.succeed();
    }

    /** Shapeless, so the redstone may sit anywhere. One frame per craft is §3.2's yield (Nb). */
    static void recipeYieldsExactlyOne(GameTestHelper helper) {
        for (int redstoneSlot : new int[] {4, 0}) {
            CraftingInput input = ironAround(redstoneSlot);
            ItemStack result = helper.getLevel().getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                    .map(holder -> holder.value().assemble(input))
                    .orElse(ItemStack.EMPTY);
            if (!result.is(ModItems.VAULT_FRAME.get()) || result.getCount() != 1) {
                helper.fail(Component.literal("redstone in slot " + redstoneSlot + " crafted " + result));
            }
        }
        helper.succeed();
    }

    private static void breakFrameHolding(GameTestHelper helper, ItemStack held) {
        helper.setBlock(FRAME, ModBlocks.VAULT_FRAME.get());
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), BREAKER);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.gameMode.destroyBlock(helper.absolutePos(FRAME));
        // Without this, a break that never happened would pass the no-drop test.
        helper.assertBlockPresent(Blocks.AIR, FRAME);
    }

    private static CraftingInput ironAround(int redstoneSlot) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            slots.add(new ItemStack(slot == redstoneSlot ? Items.REDSTONE : Items.IRON_INGOT));
        }
        return CraftingInput.of(3, 3, slots);
    }
}
