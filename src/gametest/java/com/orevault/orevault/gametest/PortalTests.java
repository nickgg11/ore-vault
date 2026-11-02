package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.frame.DoorPart;
import com.orevault.orevault.core.frame.VaultPortalShape;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModItems;
import com.orevault.orevault.registry.ModTags;
import com.orevault.orevault.vault.VaultDoorBlock;
import com.orevault.orevault.vault.VaultOpenings;
import com.orevault.orevault.vault.VaultPortalBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * §3.2, ADR 0010: the sealed door, the press and the dissolve. The test area is one block with its neighbours five blocks
 * off and all tests in a batch run at once, so a ring wider than that would stand in another test's. Each test takes a
 * height band of its own instead, well above the barriers.
 */
@EventBusSubscriber(modid = OreVault.MOD_ID)
final class PortalTests {
    private static final int FIRST_BAND = 8;
    private static final int BAND_HEIGHT = 9;
    private static final int BESIDE = 3;

    private static final GameProfile PLAYER =
            new GameProfile(UUID.fromString("7f1d9a52-6b2e-4b9c-8c11-0f6d2a4e7b31"), "orevault-presser");
    private static final GameProfile BUILDER =
            new GameProfile(UUID.fromString("2c8e4b17-9a3d-4f06-b5de-71c2f0a9e644"), "orevault-creative");

    /** Where each sound played, so a test reads only its own. The server thread writes and reads it. */
    private static final Map<BlockPos, Set<SoundEvent>> SOUNDS = new ConcurrentHashMap<>();

    private PortalTests() {
    }

    @SubscribeEvent
    static void onSound(PlayLevelSoundEvent.AtPosition event) {
        SOUNDS.computeIfAbsent(BlockPos.containing(event.getPosition()), pos -> ConcurrentHashMap.newKeySet())
                .add(event.getSound().value());
    }

    /**
     * A ring on one axis, by its opening.
     *
     * @param first the opening's lowest cell, column 0, relative to the test
     * @param along the way the columns run
     */
    private record Ring(GameTestHelper helper, BlockPos first, Direction along, int width, int height) {
        static Ring in(GameTestHelper helper, int band, Direction along, int width, int height) {
            return new Ring(helper, new BlockPos(BESIDE, FIRST_BAND + band * BAND_HEIGHT, BESIDE), along, width, height);
        }

        /** The block at this column and row. Column -1 and {@code width}, and row -1 and {@code height}, are the bars. */
        BlockPos at(int column, int row) {
            return first.relative(along, column).above(row);
        }

        List<BlockPos> cells() {
            List<BlockPos> cells = new ArrayList<>();
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    cells.add(at(column, row));
                }
            }
            return cells;
        }

        /** The four sides, the last of them the top bar's last block, which is what completes the ring. */
        List<BlockPos> sides() {
            List<BlockPos> sides = new ArrayList<>();
            for (int row = 0; row < height; row++) {
                sides.add(at(-1, row));
                sides.add(at(width, row));
            }
            for (int column = 0; column < width; column++) {
                sides.add(at(column, -1));
            }
            for (int column = 0; column < width; column++) {
                sides.add(at(column, height));
            }
            return sides;
        }

        /** Everything but {@link #lastSide()}, so a test can watch the ring complete. */
        void buildAllButOne() {
            List<BlockPos> sides = sides();
            sides.subList(0, sides.size() - 1).forEach(pos -> helper.setBlock(pos, ModBlocks.VAULT_FRAME.get()));
        }

        BlockPos lastSide() {
            return sides().getLast();
        }

        void build() {
            buildAllButOne();
            helper.setBlock(lastSide(), ModBlocks.VAULT_FRAME.get());
        }

        List<BlockPos> corners() {
            return List.of(at(-1, -1), at(width, -1), at(-1, height), at(width, height));
        }

        void assertAll(Block expected) {
            cells().forEach(cell -> helper.assertBlockPresent(expected, cell));
        }

        boolean allAre(Block block) {
            return cells().stream().allMatch(cell -> helper.getBlockState(cell).is(block));
        }

        Direction.Axis axis() {
            return along.getAxis();
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(Component.literal(message));
        }
    }

    private static ServerPlayer player(GameTestHelper helper, GameProfile profile) {
        return FakePlayerFactory.get(helper.getLevel(), profile);
    }

    private static InteractionResult use(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        ServerPlayer player = player(helper, PLAYER);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(clicked);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** The Worn Signet, the only one with an item yet, by the path a player's click takes. */
    private static void press(GameTestHelper helper, BlockPos clicked) {
        use(helper, new ItemStack(ModItems.WORN_SIGNET.get()), clicked, Direction.UP);
    }

    private static void assertSoundAt(GameTestHelper helper, BlockPos pos, SoundEvent sound) {
        Set<SoundEvent> heard = SOUNDS.getOrDefault(helper.absolutePos(pos), Set.of());
        check(helper, heard.contains(sound), "expected " + sound + " at " + pos + ", heard " + heard);
    }

    private static void breakWithAPickaxe(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = player(helper, PLAYER);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(pos));
        helper.assertBlockPresent(Blocks.AIR, pos);
    }

    private static void succeedWhenGone(GameTestHelper helper, Ring ring) {
        helper.succeedWhen(() -> ring.assertAll(Blocks.AIR));
    }

    // ---- sealing ---------------------------------------------------------------------------------------------------

    static void sealsOnTheLastBlock(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 0, Direction.EAST, 2, 3);
        ring.buildAllButOne();
        ring.assertAll(Blocks.AIR);
        helper.setBlock(ring.lastSide(), ModBlocks.VAULT_FRAME.get());
        assertDoor(helper, ring);
        helper.succeed();
    }

    /** Odd width, so the centre column, on the axis the model reaches by a quarter turn. */
    static void sealsAlongZ(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 1, Direction.SOUTH, 3, 4);
        ring.build();
        assertDoor(helper, ring);
        helper.succeed();
    }

    private static void assertDoor(GameTestHelper helper, Ring ring) {
        for (int column = 0; column < ring.width(); column++) {
            for (int row = 0; row < ring.height(); row++) {
                BlockState state = helper.getBlockState(ring.at(column, row));
                DoorPart part = DoorPart.of(column, row, ring.width(), ring.height());
                check(helper, state.is(ModBlocks.VAULT_DOOR.get()), column + "," + row + " is " + state);
                check(helper, state.getValue(VaultOpenings.AXIS) == ring.axis(), "wrong axis in " + state);
                check(helper, state.getValue(VaultDoorBlock.PART) == part.id(), "wanted " + part + " in " + state);
            }
        }
    }

    /** The frame arrives by {@code setBlock(pos, state, 67)}, with no player and no snapshot. */
    static void aPistonCompletesARing(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 2, Direction.EAST, 2, 3);
        ring.buildAllButOne();
        BlockPos gap = ring.lastSide();
        helper.setBlock(gap.above(), ModBlocks.VAULT_FRAME.get());
        helper.setBlock(gap.above(2), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.DOWN));
        helper.setBlock(gap.above(3), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.VAULT_FRAME.get(), gap);
            assertDoor(helper, ring);
        });
    }

    /**
     * Player placement captures snapshots, which skips {@code onPlace} in {@code setBlock}, and then
     * {@code CommonHooks#onPlaceItemIntoWorld} calls it itself. Then the signet, on the frame, by the same path.
     */
    static void placementAndThePressThroughAFakePlayer(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 3, Direction.EAST, 2, 3);
        ring.buildAllButOne();
        InteractionResult placed = use(helper, new ItemStack(ModItems.VAULT_FRAME.get()), ring.at(0, ring.height()), Direction.EAST);
        check(helper, placed.consumesAction(), "placing returned " + placed);
        helper.assertBlockPresent(ModBlocks.VAULT_FRAME.get(), ring.lastSide());
        assertDoor(helper, ring);
        InteractionResult pressed = use(helper, new ItemStack(ModItems.WORN_SIGNET.get()), ring.lastSide(), Direction.SOUTH);
        check(helper, pressed.consumesAction(), "pressing returned " + pressed);
        ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        assertSoundAt(helper, ring.lastSide(), SoundEvents.PORTAL_TRIGGER);
        helper.succeed();
    }

    /** The signet ignores everything else, so a block with its own use keeps it. */
    static void aSignetPassesOnOtherBlocks(GameTestHelper helper) {
        BlockPos stone = new BlockPos(BESIDE, FIRST_BAND + 21 * BAND_HEIGHT, BESIDE);
        helper.setBlock(stone, Blocks.STONE);
        InteractionResult result = use(helper, new ItemStack(ModItems.WORN_SIGNET.get()), stone, Direction.UP);
        check(helper, result == InteractionResult.PASS, "on stone it returned " + result);
        helper.succeed();
    }

    /**
     * No door, the press refused with the failure sound, the opening cleared with nothing noticing, the press accepted,
     * since the door is only presentation.
     */
    static void anObstructedRingGetsNoDoor(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 4, Direction.EAST, 2, 3);
        BlockPos stone = ring.at(1, 1);
        helper.setBlock(stone, Blocks.STONE);
        ring.build();
        helper.assertBlockPresent(Blocks.AIR, ring.at(0, 0));
        press(helper, ring.at(-1, 0));
        helper.assertBlockPresent(Blocks.STONE, stone);
        helper.assertBlockPresent(Blocks.AIR, ring.at(0, 0));
        assertSoundAt(helper, ring.at(-1, 0), SoundEvents.FIRE_EXTINGUISH);
        helper.setBlock(stone, Blocks.AIR);
        helper.assertBlockPresent(Blocks.AIR, ring.at(0, 0));
        press(helper, ring.at(-1, 0));
        ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        helper.succeed();
    }

    /** A ring from before this change, a generated one and a pillared-up one all come down to this. */
    static void placingAFrameAgainSealsAnEmptyRing(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 5, Direction.EAST, 2, 3);
        BlockPos stone = ring.at(0, 0);
        helper.setBlock(stone, Blocks.STONE);
        ring.build();
        helper.setBlock(stone, Blocks.AIR);
        ring.assertAll(Blocks.AIR);
        BlockState connected = helper.getBlockState(ring.lastSide());
        helper.setBlock(ring.lastSide(), connected.setValue(PipeBlock.UP, true));
        ring.assertAll(Blocks.AIR);
        helper.setBlock(ring.lastSide(), Blocks.AIR);
        helper.setBlock(ring.lastSide(), ModBlocks.VAULT_FRAME.get());
        assertDoor(helper, ring);
        helper.succeed();
    }

    /** The corner's scan finds the ring, and only all-air stops it putting a door over the portal. */
    static void aCornerAddedLaterLeavesALivePortal(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 6, Direction.EAST, 2, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        ring.corners().forEach(corner -> helper.setBlock(corner, ModBlocks.VAULT_FRAME.get()));
        ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        helper.succeed();
    }

    /** The placed block finishes both, and the first door written fills the column they share. */
    static void crossingRingsAreSealedByOneBlock(GameTestHelper helper) {
        Ring[] rings = crossing(helper, 7);
        helper.setBlock(shared(rings), ModBlocks.VAULT_FRAME.get());
        assertDoor(helper, rings[0]);
        helper.assertBlockPresent(Blocks.AIR, rings[1].at(0, 0));
        helper.assertBlockPresent(Blocks.AIR, rings[1].at(2, 2));
        helper.succeed();
    }

    /** The other ring's door is not this press's to replace, and the ring the door belongs to still opens. */
    static void crossingRingsRefuseTheOtherAxis(GameTestHelper helper) {
        Ring[] rings = crossing(helper, 8);
        helper.setBlock(shared(rings), ModBlocks.VAULT_FRAME.get());
        press(helper, rings[1].at(-1, 0));
        assertDoor(helper, rings[0]);
        helper.assertBlockPresent(Blocks.AIR, rings[1].at(0, 0));
        assertSoundAt(helper, rings[1].at(-1, 0), SoundEvents.FIRE_EXTINGUISH);
        press(helper, rings[0].at(-1, 0));
        rings[0].assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        helper.succeed();
    }

    /** Two 3 by 3 openings, one on each axis, sharing their centre column, and missing only the top of that column. */
    private static Ring[] crossing(GameTestHelper helper, int band) {
        Ring alongX = Ring.in(helper, band, Direction.EAST, 3, 3);
        Ring alongZ = new Ring(helper, alongX.at(1, 0).north(), Direction.SOUTH, 3, 3);
        Ring[] rings = {alongX, alongZ};
        for (Ring ring : rings) {
            ring.sides().stream()
                    .filter(side -> !side.equals(shared(rings)))
                    .forEach(side -> helper.setBlock(side, ModBlocks.VAULT_FRAME.get()));
        }
        return rings;
    }

    private static BlockPos shared(Ring[] rings) {
        return rings[0].at(1, rings[0].height());
    }

    // ---- pressing -----------------------------------------------------------------------------------------------------

    static void aPressOnADoorSwapsItForThePortal(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 9, Direction.SOUTH, 2, 3);
        ring.build();
        press(helper, ring.at(1, 2));
        ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        ring.cells().forEach(cell -> check(helper,
                helper.getBlockState(cell).getValue(VaultOpenings.AXIS) == Direction.Axis.Z, "wrong axis at " + cell));
        helper.succeed();
    }

    /** §3.2, Nick: a higher signet upgrades, a lower one is refused with the failure sound, and the same one is no change. */
    static void aLowerSignetIsRefusedAndAHigherUpgrades(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 10, Direction.EAST, 2, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
        BlockPos portal = helper.absolutePos(ring.at(0, 0));
        check(helper, VaultOpenings.press(helper.getLevel(), portal, SignetTier.FABLED), "the upgrade was refused");
        ring.assertAll(ModBlocks.VAULT_PORTAL_RARE.get());
        press(helper, ring.at(0, 0));
        ring.assertAll(ModBlocks.VAULT_PORTAL_RARE.get());
        assertSoundAt(helper, ring.at(0, 0), SoundEvents.FIRE_EXTINGUISH);
        check(helper, !VaultOpenings.press(helper.getLevel(), helper.absolutePos(ring.at(-1, 0)), SignetTier.HEIRLOOM),
                "a lower signet on the frame was accepted");
        check(helper, VaultOpenings.press(helper.getLevel(), portal, SignetTier.FABLED), "the same tier was refused");
        ring.assertAll(ModBlocks.VAULT_PORTAL_RARE.get());
        helper.succeed();
    }

    static void aDoorWithNoRingRefuses(GameTestHelper helper) {
        BlockPos alone = Ring.in(helper, 11, Direction.EAST, 2, 3).at(0, 0);
        helper.setBlock(alone, ModBlocks.VAULT_DOOR.get());
        press(helper, alone);
        helper.assertBlockPresent(ModBlocks.VAULT_DOOR.get(), alone);
        assertSoundAt(helper, alone, SoundEvents.FIRE_EXTINGUISH);
        helper.succeed();
    }

    // ---- dissolving ------------------------------------------------------------------------------------------------

    static void aSideBreakDissolvesADoor(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 12, Direction.EAST, 3, 3);
        ring.build();
        breakWithAPickaxe(helper, ring.at(-1, 1));
        succeedWhenGone(helper, ring);
    }

    static void aSideBreakDissolvesAPortal(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 13, Direction.SOUTH, 3, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        breakWithAPickaxe(helper, ring.at(1, 3));
        succeedWhenGone(helper, ring);
    }

    /** Corners are optional, so nothing is wrong. It waits long enough for a scheduled dissolve to have run. */
    static void aCornerBreakLeavesItStanding(GameTestHelper helper) {
        Ring door = Ring.in(helper, 14, Direction.EAST, 2, 3);
        Ring portal = Ring.in(helper, 15, Direction.EAST, 2, 3);
        door.build();
        portal.build();
        door.corners().forEach(corner -> helper.setBlock(corner, ModBlocks.VAULT_FRAME.get()));
        portal.corners().forEach(corner -> helper.setBlock(corner, ModBlocks.VAULT_FRAME.get()));
        press(helper, portal.at(0, 0));
        breakWithAPickaxe(helper, door.corners().getFirst());
        breakWithAPickaxe(helper, portal.corners().getLast());
        helper.runAfterDelay(5, () -> {
            door.assertAll(ModBlocks.VAULT_DOOR.get());
            portal.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
            helper.succeed();
        });
    }

    /** The scanner accepts air, so the ring still scans as valid, and the tick has to treat the hole as invalid itself. */
    static void aHoleInADoorDissolvesTheRest(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 16, Direction.EAST, 2, 3);
        ring.build();
        helper.setBlock(ring.at(0, 1), Blocks.AIR);
        succeedWhenGone(helper, ring);
    }

    static void aHoleInAPortalDissolvesTheRest(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 17, Direction.EAST, 2, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        helper.setBlock(ring.at(1, 2), Blocks.AIR);
        succeedWhenGone(helper, ring);
    }

    /** Two rings sharing a bar: one dissolving leaves the other portal and the bar they share. */
    static void aSharedBarSurvivesOnePortalDissolving(GameTestHelper helper) {
        Ring left = Ring.in(helper, 18, Direction.EAST, 2, 3);
        Ring right = new Ring(helper, left.at(3, 0), Direction.EAST, 2, 3);
        left.build();
        right.build();
        press(helper, left.at(0, 0));
        press(helper, right.at(0, 0));
        BlockPos shared = left.at(2, 1);
        breakWithAPickaxe(helper, left.at(-1, 1));
        helper.succeedWhen(() -> {
            left.assertAll(Blocks.AIR);
            right.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
            helper.assertBlockPresent(ModBlocks.VAULT_FRAME.get(), shared);
        });
    }

    static void breakingASharedBarDissolvesBoth(GameTestHelper helper) {
        Ring left = Ring.in(helper, 19, Direction.EAST, 2, 3);
        Ring right = new Ring(helper, left.at(3, 0), Direction.EAST, 2, 3);
        left.build();
        right.build();
        press(helper, right.at(0, 0));
        breakWithAPickaxe(helper, left.at(2, 1));
        helper.succeedWhen(() -> {
            left.assertAll(Blocks.AIR);
            right.assertAll(Blocks.AIR);
        });
    }

    // ---- the blocks ----------------------------------------------------------------------------------------------------

    /** A flowing source, in a stone pocket with the portal as its only way out; then a bucket into a cell. */
    static void waterLeavesAPortalAlone(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 20, Direction.EAST, 2, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        BlockPos pocket = ring.at(0, 0).south();
        for (BlockPos wall : List.of(pocket.south(), pocket.east(), pocket.west(), pocket.below(), pocket.above())) {
            helper.setBlock(wall, Blocks.STONE);
        }
        helper.setBlock(pocket, Blocks.WATER);
        BucketItem bucket = (BucketItem) Items.WATER_BUCKET;
        boolean emptied = bucket.emptyContents(null, helper.getLevel(), helper.absolutePos(ring.at(1, 1)), null);
        check(helper, !emptied, "the bucket emptied into a portal cell");
        helper.runAfterDelay(10, () -> {
            ring.assertAll(ModBlocks.VAULT_PORTAL_COMMON.get());
            helper.succeed();
        });
    }

    /** Old PR #108: the cells go with {@code UPDATE_ALL}, so water held back by a portal hears it and flows in. */
    static void aDissolveLetsWaterIn(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 24, Direction.EAST, 2, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        BlockPos pocket = ring.at(0, 0).south();
        for (BlockPos wall : List.of(pocket.south(), pocket.east(), pocket.west(), pocket.below(), pocket.above())) {
            helper.setBlock(wall, Blocks.STONE);
        }
        helper.setBlock(pocket, Blocks.WATER);
        helper.runAfterDelay(7, () -> {
            helper.assertBlockPresent(ModBlocks.VAULT_PORTAL_COMMON.get(), ring.at(0, 0));
            breakWithAPickaxe(helper, ring.at(0, -1));
            helper.succeedWhenBlockPresent(Blocks.WATER, ring.at(0, 0));
        });
    }

    /** Hardness stops survival mining; creative ignores it, unless the event refuses, and sneaking is the way out. */
    static void creativeBreakingIsRefusedUnlessSneaking(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 22, Direction.EAST, 2, 3);
        ring.build();
        ServerPlayer creative = player(helper, BUILDER);
        creative.setGameMode(GameType.CREATIVE);
        creative.setShiftKeyDown(false);
        BlockPos door = ring.at(0, 0);
        creative.gameMode.destroyBlock(helper.absolutePos(door));
        ring.assertAll(ModBlocks.VAULT_DOOR.get());
        creative.setShiftKeyDown(true);
        creative.gameMode.destroyBlock(helper.absolutePos(door));
        creative.setShiftKeyDown(false);
        helper.assertBlockPresent(Blocks.AIR, door);
        helper.succeed();
    }

    /** {@code entityInside} on the server here; the client half is the same line and a playtest step. */
    static void anEntityInAPortalStartsItsCharge(GameTestHelper helper) {
        Ring ring = Ring.in(helper, 23, Direction.EAST, 2, 3);
        ring.build();
        press(helper, ring.at(0, 0));
        // A ring can sit in the chunk beside the forced one, which ticks blocks and not entities, so this asks directly.
        Pig pig = helper.spawn(EntityType.PIG, ring.at(0, 0));
        pig.applyEffectsFromBlocks(pig.position(), pig.position());
        check(helper, pig.portalProcess != null, "no portal process");
        pig.discard();
        helper.succeed();
    }

    static void theBlocksHaveTheSpecProperties(GameTestHelper helper) {
        check(helper, ModBlocks.VAULT_FRAME.get().getStateDefinition().getPossibleStates().size() == 64, "frame states");
        Block door = ModBlocks.VAULT_DOOR.get();
        check(helper, door.getStateDefinition().getPossibleStates().size() == 26, "door states");
        check(helper, !door.defaultBlockState().is(ModTags.VAULT_PORTALS), "the door is in the portal tag");
        check(helper, door.defaultBlockState().is(ModTags.VAULT_OPENINGS), "the door is not an opening");
        check(helper, !door.defaultBlockState().hasBlockEntity(), "the door has a block entity");
        check(helper, door.defaultBlockState().getLightEmission() == 0, "the door gives light");
        check(helper, !door.defaultBlockState().getCollisionShape(helper.getLevel(), BlockPos.ZERO).isEmpty(), "door collision");
        check(helper, door.getLootTable().isEmpty(), "the door has a loot table");
        checkUnbreakableBlock(helper, door);
        List<VaultPortalBlock> portals = ModBlocks.portals();
        check(helper, portals.size() == 4, portals + " portals");
        for (int index = 0; index < portals.size(); index++) {
            VaultPortalBlock portal = portals.get(index);
            BlockState state = portal.defaultBlockState();
            check(helper, portal.tier() == index + 1, portal + " is tier " + portal.tier());
            check(helper, state.is(ModTags.VAULT_PORTALS) && state.is(ModTags.VAULT_OPENINGS), portal + " tags");
            check(helper, state.getLightEmission() == 11, portal + " light");
            check(helper, state.getCollisionShape(helper.getLevel(), BlockPos.ZERO).isEmpty(), portal + " has collision");
            check(helper, portal.getLootTable().isEmpty(), portal + " has a loot table");
            checkUnbreakableBlock(helper, portal);
        }
        helper.succeed();
    }

    private static void checkUnbreakableBlock(GameTestHelper helper, Block block) {
        BlockState state = block.defaultBlockState();
        check(helper, block.defaultDestroyTime() == -1.0F, block + " hardness");
        check(helper, block.getExplosionResistance() == 3_600_000.0F, block + " blast resistance");
        check(helper, state.getPistonPushReaction() == PushReaction.BLOCK, block + " push reaction");
        check(helper, state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE), block + " immunity");
        check(helper, !state.isAir() && thinOnItsAxis(helper, state), block + " shape");
    }

    /** 4 px thick, across the axis it runs along. */
    private static boolean thinOnItsAxis(GameTestHelper helper, BlockState state) {
        var alongX = state.setValue(VaultOpenings.AXIS, Direction.Axis.X)
                .getShape(helper.getLevel(), BlockPos.ZERO, CollisionContext.empty()).bounds();
        var alongZ = state.setValue(VaultOpenings.AXIS, Direction.Axis.Z)
                .getShape(helper.getLevel(), BlockPos.ZERO, CollisionContext.empty()).bounds();
        return alongX.getZsize() == 0.25 && alongX.getXsize() == 1.0 && alongZ.getXsize() == 0.25 && alongZ.getZsize() == 1.0;
    }

    /** The answer the unit test pins on {@link VaultPortalShape.View#loadedOnly}, here on the real adapter. */
    static void theScanNeverLoadsAChunk(GameTestHelper helper) {
        int far = 1 << 20;
        var chunks = helper.getLevel().getChunkSource();
        check(helper, !chunks.hasChunk(far >> 4, far >> 4), "the far chunk is loaded before the test");
        Kind kind = VaultOpenings.view(helper.getLevel()).kindAt(far, 64, far);
        check(helper, kind == Kind.OTHER, "an unloaded position answered " + kind);
        check(helper, !chunks.hasChunk(far >> 4, far >> 4), "the view loaded the chunk");
        helper.succeed();
    }
}
