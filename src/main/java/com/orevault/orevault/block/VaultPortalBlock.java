package com.orevault.orevault.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.item.VaultIgniterItem;
import com.orevault.orevault.portal.VaultPortalShape;
import com.orevault.orevault.portal.VaultTeleport;
import com.orevault.orevault.team.TeamHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ore Vault portal block — the interior fill of a portal frame (§3.2).
 *
 * <ul>
 * <li>No collision, hardness -1.0 (unbreakable), blast resistance 3,600,000,
 * light level 11.</li>
 * <li>{@code HORIZONTAL_AXIS} state for orientation.</li>
 * <li>{@link #updateShape} breaks to air when the frame around it is damaged
 * (re-validates the whole frame, so broken corners dissolve it too).</li>
 * <li>Implements 26.1's {@link Portal} interface: players standing inside get
 * the vanilla nether-style charge-up ({@link #getPortalTransitionTime}), the
 * wavy "confusion" screen overlay ({@link #getLocalTransition}) and a portal
 * travel sound, then {@link #getPortalDestination} routes through
 * {@link VaultTeleport}. Tier-4 igniter holders skip the wait entirely.</li>
 * <li>Requires an FTB team (§3.1): teamless players get a hint instead of
 * portal charge-up.</li>
 * </ul>
 */
public class VaultPortalBlock extends Block implements Portal {

    public static final MapCodec<VaultPortalBlock> CODEC = simpleCodec(VaultPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** Thin vertical slab in the centre of the block, rotated per axis (mirrors NetherPortalBlock). */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));

    public VaultPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public MapCodec<VaultPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    /**
     * Portal frame integrity check (§3.2): on any change to a horizontal
     * neighbour off the portal plane, the whole frame is re-validated (via
     * {@link VaultPortalShape#isValidFrameContaining}) and the portal dissolves
     * if it is no longer complete. Re-validating the entire frame — rather
     * than just the changed neighbour — is what catches corner blocks, which
     * are diagonal to every portal block (with the frame's own
     * {@code onRemove} scan as the final safety net).
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random
    ) {
        Direction.Axis updateAxis = directionToNeighbour.getAxis();
        Direction.Axis axis = state.getValue(AXIS);
        boolean samePlane = axis == updateAxis || !updateAxis.isHorizontal();
        return !samePlane && !VaultPortalShape.isValidFrameContaining(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    /**
     * Portal entry (§3.2): the client call only creates the local portal
     * processor for the warp overlay; all gating is server side.
     * <ul>
     * <li>Tier 4 igniter (§3.3): instant direct teleport, no wait, no cooldown.</li>
     * <li>No FTB team (§3.1): hint message, rate-limited via the portal cooldown.</li>
     * <li>Otherwise: the vanilla {@link Portal} flow — wait, overlay, travel
     * sound, then {@link #getPortalDestination}.</li>
     * </ul>
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level.isClientSide()) {
            // Client side: create the local portal processor — the wavy
            // CONFUSION warp overlay is rendered from it (#81). Vanilla's
            // NetherPortalBlock does this on both sides; skipping it left the
            // portal with no visual warp effect.
            entity.setAsInsidePortal(this, pos);
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (VaultIgniterItem.highestTierLevel(player) >= 4) {
            if (!player.isOnPortalCooldown()) {
                VaultTeleport.handlePortal(player);
            }
            return;
        }
        if (TeamHelper.getTeam(player).isEmpty()) {
            if (!player.isOnPortalCooldown()) {
                player.sendSystemMessage(Component.translatable("message.orevault.team_required"));
                player.setPortalCooldown(40); // re-used as a message rate limiter
            }
            return;
        }
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    /** Nether-style charge-up while standing inside the portal, in ticks (§3.2). */
    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return VaultTeleport.PORTAL_WAIT_TICKS;
    }

    /** Routes the portal trip through {@link VaultTeleport} (overworld ⇄ vault). */
    @Override
    public TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        return VaultTeleport.createTransition(currentLevel, entity, portalEntryPos);
    }

    /** The vanilla wavy screen overlay while the portal charges (nether-style). */
    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    /** Unbreakable interior block: never pick-blockable (matches vanilla portal behaviour). */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
