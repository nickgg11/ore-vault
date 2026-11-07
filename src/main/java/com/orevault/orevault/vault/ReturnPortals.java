package com.orevault.orevault.vault;

import com.orevault.orevault.core.frame.ReturnPortal;
import com.orevault.orevault.core.frame.ReturnPortal.Plan;
import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The world half of {@link ReturnPortal} (§3.2): reads a Vault for it and writes its plan, cells before frames (ADR 0010).
 * Server thread only. Nick, from the portal playtest: "Maybe we should spawn the portal in by default just so they can't
 * get stuck." Every trip in, and the login placement at the anchor after a reset, makes sure it stands.
 */
public final class ReturnPortals {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReturnPortals.class);

    private ReturnPortals() {
    }

    /**
     * Builds the return portal if it is not standing, and raises its tier to {@code entering} if that is higher. Generates
     * the two chunks it stands in.
     *
     * @param entering the tier of the portal the player came through; {@link SignetTier#WORN} when there was none
     * @return whether a return portal stands once this returns
     */
    public static boolean ensure(ServerLevel vault, SignetTier entering) {
        int lastX = ReturnPortal.FIRST_X + ReturnPortal.WIDTH - 1;
        vault.getChunk(ReturnPortal.FIRST_X >> 4, ReturnPortal.PLANE_Z >> 4);
        vault.getChunk(lastX >> 4, ReturnPortal.PLANE_Z >> 4);
        int top = vault.getMinY();
        for (int x = ReturnPortal.FIRST_X; x <= lastX; x++) {
            top = Math.max(top, vault.getHeight(Heightmap.Types.MOTION_BLOCKING, x, ReturnPortal.PLANE_Z));
        }
        ReturnPortal.Blocks blocks = blocks(vault);
        int baseY = ReturnPortal.baseY(blocks, Math.min(top, vault.getMaxY()), vault.getMinY());
        if (baseY + ReturnPortal.HEIGHT - 1 > vault.getMaxY()) {
            LOGGER.warn("The return portal of {} has no room: its ground at Y={} is too close to the build limit",
                    vault.dimension().identifier(), baseY - 1);
            return false;
        }
        Plan plan = ReturnPortal.plan(blocks, baseY, entering(entering));
        if (plan.blocked()) {
            LOGGER.warn("The return portal of {} at Y={} is blocked: something other than a frame or an opening is in its ring",
                    vault.dimension().identifier(), baseY);
            return false;
        }
        if (plan.changesNothing()) {
            return true;
        }
        BlockState cell = ModBlocks.portal(SignetTier.values()[plan.rank() - 1]).defaultBlockState()
                .setValue(VaultOpenings.AXIS, Direction.Axis.X);
        plan.cells().forEach(at -> vault.setBlock(pos(at), cell, VaultOpenings.FILL_FLAGS));
        for (Cell at : plan.frames()) {
            BlockPos pos = pos(at);
            vault.setBlock(pos, ModBlocks.VAULT_FRAME.get().connectedState(vault, pos), Block.UPDATE_ALL);
        }
        LOGGER.info("Return portal of {} at Y={}, tier {}: wrote {} cells and {} frames", vault.dimension().identifier(), baseY,
                plan.rank(), plan.cells().size(), plan.frames().size());
        return true;
    }

    /** The tier of the portal a player stood in, or {@link SignetTier#WORN} for anything else. */
    static SignetTier tierAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof VaultPortalBlock portal ? portal.signetTier() : SignetTier.WORN;
    }

    private static ReturnPortal.Entering entering(SignetTier tier) {
        return new ReturnPortal.Entering() {
            @Override
            public int rank() {
                return tier.rank();
            }

            @Override
            public boolean mayPress(int portalRank) {
                return tier.mayPress(portalRank);
            }
        };
    }

    private static ReturnPortal.Blocks blocks(ServerLevel vault) {
        return new ReturnPortal.Blocks() {
            @Override
            public Kind kindAt(int x, int y, int z) {
                BlockState state = vault.getBlockState(new BlockPos(x, y, z));
                if (state.is(ModBlocks.VAULT_FRAME)) {
                    return Kind.FRAME;
                }
                if (state.is(ModTags.VAULT_OPENINGS)) {
                    return state.getValue(VaultOpenings.AXIS) == Direction.Axis.X ? Kind.PORTAL : Kind.OTHER;
                }
                return state.canBeReplaced() ? Kind.AIR : Kind.OTHER;
            }

            @Override
            public int rankAt(int x, int y, int z) {
                return vault.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof VaultPortalBlock portal ? portal.tier() : 0;
            }
        };
    }

    private static BlockPos pos(Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
