package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    static void hasTheSpecProperties(GameTestHelper helper) {
        Block frame = ModBlocks.VAULT_FRAME.get();
        if (frame.defaultMapColor() != MapColor.STONE) {
            helper.fail(Component.literal("not the stone map colour"));
        }
        if (frame.defaultDestroyTime() != 1.5F) {
            helper.fail(Component.literal("hardness " + frame.defaultDestroyTime()));
        }
        if (frame.getExplosionResistance() != 6.0F) {
            helper.fail(Component.literal("blast resistance " + frame.getExplosionResistance()));
        }
        if (frame.defaultBlockState().getSoundType() != SoundType.STONE) {
            helper.fail(Component.literal("not the stone sound"));
        }
        helper.succeed();
    }

    /** A run of three, then its middle broken: each end follows, and stone beside a frame connects to nothing. */
    static void connectsOnlyToFrames(GameTestHelper helper) {
        // The test area is one block wide with barriers around it, so the run stands clear of them.
        BlockPos west = new BlockPos(3, 1, 3);
        BlockPos middle = west.east();
        BlockPos east = middle.east();
        for (BlockPos pos : List.of(west, middle, east)) {
            helper.placeBlock(pos, ModBlocks.VAULT_FRAME.get(), Direction.NORTH);
        }
        helper.setBlock(middle.north(), Blocks.STONE);
        assertConnections(helper, west, Set.of(Direction.EAST));
        assertConnections(helper, middle, Set.of(Direction.EAST, Direction.WEST));
        assertConnections(helper, east, Set.of(Direction.WEST));
        helper.setBlock(middle, Blocks.AIR);
        assertConnections(helper, west, Set.of());
        assertConnections(helper, east, Set.of());
        helper.succeed();
    }

    /**
     * A ring's top-left block rounds off and the end of the bar beside it stays a cube. The shape changes nothing for
     * the pickaxe: mining goes by the block, not the shape.
     */
    static void roundedCornerHasItsShapeAndStillDropsItself(GameTestHelper helper) {
        BlockPos corner = new BlockPos(3, 2, 3);
        BlockPos east = corner.east();
        BlockPos below = corner.below();
        for (BlockPos pos : List.of(corner, east, below)) {
            helper.placeBlock(pos, ModBlocks.VAULT_FRAME.get(), Direction.NORTH);
        }
        assertConnections(helper, corner, Set.of(Direction.EAST, Direction.DOWN));
        if (!Shapes.joinIsNotEmpty(Shapes.block(), outline(helper, corner), BooleanOp.ONLY_FIRST)) {
            helper.fail(Component.literal("the rounded corner's outline fills the block"));
        }
        if (helper.getBlockState(corner).isSolidRender()) {
            helper.fail(Component.literal("the rounded corner would hide a neighbour's face behind its wedge"));
        }
        BlockState rounded = helper.getBlockState(corner);
        BlockPos absolute = helper.absolutePos(corner);
        if (!Shapes.joinIsNotEmpty(Shapes.block(), rounded.getCollisionShape(helper.getLevel(), absolute),
                BooleanOp.ONLY_FIRST)) {
            helper.fail(Component.literal("the rounded corner's collision fills the block"));
        }
        if (rounded.isRedstoneConductor(helper.getLevel(), absolute)) {
            helper.fail(Component.literal("the rounded corner conducts redstone"));
        }
        if (rounded.isSuffocating(helper.getLevel(), absolute)) {
            helper.fail(Component.literal("the rounded corner suffocates"));
        }
        if (rounded.isPathfindable(PathComputationType.LAND)) {
            helper.fail(Component.literal("mobs would path into the rounded corner"));
        }
        if (outline(helper, east) != Shapes.block()) {
            helper.fail(Component.literal("the end of a bar is not a full cube"));
        }
        breakHolding(helper, corner, new ItemStack(Items.WOODEN_PICKAXE));
        helper.assertItemEntityCountIs(ModItems.VAULT_FRAME.get(), corner, DROP_RADIUS, 1);
        helper.succeed();
    }

    /** Shapeless, so the lapis may sit anywhere. One frame per craft is §3.2's yield (Nb). */
    static void recipeYieldsExactlyOne(GameTestHelper helper) {
        for (int lapisSlot : new int[] {4, 0}) {
            CraftingInput input = bricksAround(lapisSlot);
            ItemStack result = helper.getLevel().getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                    .map(holder -> holder.value().assemble(input))
                    .orElse(ItemStack.EMPTY);
            if (!result.is(ModItems.VAULT_FRAME.get()) || result.getCount() != 1) {
                helper.fail(Component.literal("lapis in slot " + lapisSlot + " crafted " + result));
            }
        }
        helper.succeed();
    }

    private static void breakFrameHolding(GameTestHelper helper, ItemStack held) {
        helper.setBlock(FRAME, ModBlocks.VAULT_FRAME.get());
        breakHolding(helper, FRAME, held);
    }

    private static void breakHolding(GameTestHelper helper, BlockPos pos, ItemStack held) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), BREAKER);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.gameMode.destroyBlock(helper.absolutePos(pos));
        // Without this, a break that never happened would pass the no-drop test.
        helper.assertBlockPresent(Blocks.AIR, pos);
    }

    private static VoxelShape outline(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos), CollisionContext.empty());
    }

    private static void assertConnections(GameTestHelper helper, BlockPos pos, Set<Direction> expected) {
        BlockState state = helper.getBlockState(pos);
        for (Direction direction : Direction.values()) {
            if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction)) != expected.contains(direction)) {
                helper.fail(Component.literal(pos + " " + direction + " wrong in " + state));
            }
        }
    }

    private static CraftingInput bricksAround(int lapisSlot) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            slots.add(new ItemStack(slot == lapisSlot ? Items.LAPIS_LAZULI : Items.STONE_BRICKS));
        }
        return CraftingInput.of(3, 3, slots);
    }
}
