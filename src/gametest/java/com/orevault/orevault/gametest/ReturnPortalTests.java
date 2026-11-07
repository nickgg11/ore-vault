package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.core.frame.ReturnPortal;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.vault.VaultDimensions;
import com.orevault.orevault.vault.VaultPortalBlock;
import com.orevault.orevault.vault.VaultTravel;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * E3-S6b (#244): the return portal at the team anchor and the safe arrival, through {@link VaultTravel#intoVault}, the step
 * after the portal block's fake-player refusal (as {@code VaultLifecycleTests}). Each test makes its own team's Vault. The
 * trip is read for where it goes and never taken: the portal is built before the teleport, as §3.2's order has it.
 */
final class ReturnPortalTests {
    /** The base stack's grass is at Y=250, so the ring's bottom row and the anchor's feet are at Y=251. */
    private static final int BASE = 251;
    /** Where the test stands its entry portal, relative to the test's origin. */
    private static final BlockPos ENTRY = new BlockPos(1, 1, 1);
    /** Flags 18, as a portal is filled: the lone entry block schedules no dissolve. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ReturnPortalTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    /** A trip in from a portal of this tier standing in the test's own area; the Vault's return portal is built by it. */
    private static TeleportTransition enter(GameTestHelper helper, UUID team, SignetTier tier) {
        ServerLevel overworld = helper.getLevel();
        BlockPos entry = helper.absolutePos(ENTRY);
        overworld.setBlock(entry, ModBlocks.portal(tier).defaultBlockState(), QUIET);
        FakePlayer player = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "orevault-returner"));
        player.setPos(Vec3.atBottomCenterOf(entry));
        TeleportTransition trip = VaultTravel.intoVault(player, team, overworld, entry);
        if (trip == null) {
            throw new IllegalStateException("no trip into the Vault of " + team);
        }
        return trip;
    }

    private static BlockPos at(ReturnPortal.Place place) {
        return new BlockPos(place.cell().x(), place.cell().y(), place.cell().z());
    }

    /** Every ring position holds what it should: frames on the edge, cells of {@code tier} on axis X in the opening. */
    private static void checkRing(GameTestHelper helper, ServerLevel vault, SignetTier tier) {
        for (ReturnPortal.Place place : ReturnPortal.layout(BASE)) {
            BlockState state = vault.getBlockState(at(place));
            if (place.role() == ReturnPortal.Role.CELL) {
                check(helper, "cell " + at(place), ModBlocks.portal(tier), state.getBlock());
                check(helper, "axis " + at(place), Direction.Axis.X, state.getValue(BlockStateProperties.HORIZONTAL_AXIS));
            } else {
                check(helper, "frame " + at(place), true, state.is(ModBlocks.VAULT_FRAME));
            }
        }
    }

    /** Counts of portal, door and frame blocks in a box well round the anchor. */
    private record Census(int portals, int doors, int frames) {
        static Census of(ServerLevel vault) {
            int portals = 0;
            int doors = 0;
            int frames = 0;
            for (BlockPos pos : BlockPos.betweenClosed(-6, BASE - 2, -6, 7, BASE + 12, 9)) {
                BlockState state = vault.getBlockState(pos);
                portals += state.getBlock() instanceof VaultPortalBlock ? 1 : 0;
                doors += state.is(ModBlocks.VAULT_DOOR) ? 1 : 0;
                frames += state.is(ModBlocks.VAULT_FRAME) ? 1 : 0;
            }
            return new Census(portals, doors, frames);
        }
    }

    /** §3.2: the first trip in builds the 4×5 ring at the anchor in the entry portal's tier, cells first, so no door. */
    static void builtOnTheFirstEntry(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        TeleportTransition trip = enter(helper, team, SignetTier.HEIRLOOM);
        ServerLevel vault = trip.newLevel();
        checkRing(helper, vault, SignetTier.HEIRLOOM);
        check(helper, "census", new Census(6, 0, 14), Census.of(vault));
        check(helper, "arrival", new Vec3(0.5, BASE, 0.5), trip.position());
        check(helper, "facing the portal", 0.0F, trip.yRot());
        check(helper, "in the team's Vault", VaultDimensions.dimensionKey(team), vault.dimension());
        helper.succeed();
    }

    /** §3.2: exactly one per Vault. A second trip in builds nothing more. */
    static void oneAndIdempotent(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        ServerLevel vault = enter(helper, team, SignetTier.WORN).newLevel();
        enter(helper, team, SignetTier.WORN);
        enter(helper, team, SignetTier.WORN);
        check(helper, "census", new Census(6, 0, 14), Census.of(vault));
        checkRing(helper, vault, SignetTier.WORN);
        helper.succeed();
    }

    /** §3.2 and old #109: the tier is read back from the cells, raised by a higher signet and never lowered. */
    static void upgradesAndNeverDowngrades(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        ServerLevel vault = enter(helper, team, SignetTier.FABLED).newLevel();
        checkRing(helper, vault, SignetTier.FABLED);
        enter(helper, team, SignetTier.WORN);
        checkRing(helper, vault, SignetTier.FABLED);
        enter(helper, team, SignetTier.PRIMORDIAL);
        checkRing(helper, vault, SignetTier.PRIMORDIAL);
        check(helper, "census", new Census(6, 0, 14), Census.of(vault));
        helper.succeed();
    }

    /** Nick: "so they can't get stuck". A dissolved return portal is rebuilt on its own frames at the next trip in. */
    static void rebuiltAfterADissolve(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        ServerLevel vault = enter(helper, team, SignetTier.WORN).newLevel();
        for (ReturnPortal.Place place : ReturnPortal.layout(BASE)) {
            if (place.role() == ReturnPortal.Role.CELL) {
                vault.setBlock(at(place), Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
        vault.setBlock(new BlockPos(ReturnPortal.FIRST_X, BASE + 2, ReturnPortal.PLANE_Z), Blocks.AIR.defaultBlockState(), QUIET);
        check(helper, "dissolved", new Census(0, 0, 13), Census.of(vault));

        enter(helper, team, SignetTier.WORN);

        checkRing(helper, vault, SignetTier.WORN);
        check(helper, "census", new Census(6, 0, 14), Census.of(vault));
        helper.succeed();
    }

    /** Old #77: a pillar built over the anchor is stood on, not arrived inside. */
    static void arrivalStandsOnAPillarOverTheAnchor(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        ServerLevel vault = enter(helper, team, SignetTier.WORN).newLevel();
        for (int y = BASE; y < BASE + 3; y++) {
            vault.setBlock(new BlockPos(0, y, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        check(helper, "on the pillar", new Vec3(0.5, BASE + 3, 0.5), enter(helper, team, SignetTier.WORN).position());
        helper.succeed();
    }
}
