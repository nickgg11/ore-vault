package com.orevault.orevault.entity;

import java.util.UUID;

import com.orevault.orevault.team.TeamHelper;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Base for the floating orbs a Vault pays out in (§4.2, §5.1, §11).
 *
 * <p>Modelled on {@code ExperienceOrb} rather than extending it. The two
 * behaviours that matter here are exactly the two that vanilla hard-codes:
 * {@code playerTouch} grants the toucher experience, and the follow logic
 * chases the nearest player of any allegiance. An orb belongs to a <b>team</b>
 * and pays a <b>team pool</b>, so both had to be replaced outright; subclassing
 * would have left the vanilla versions one missed override away from granting
 * XP to whoever walked past.</p>
 *
 * <p>The base class knows nothing about what an orb is worth to a team — it
 * carries a value and a team id, floats to a member of that team, and hands
 * both to {@link #onAbsorbed} on contact. That keeps the Animus subclass a drop-in
 * later (post-1.0, see the Animus epic) with no change here.</p>
 *
 * <p><b>Movement is server-authoritative.</b> Vanilla runs the follow logic on
 * both sides and lets the client predict, which it can do because "nearest
 * player" is knowable on the client. Team membership is not — the client has no
 * team manager — so a client-side prediction would pull orbs toward players who
 * cannot collect them. The client interpolates the positions the server sends
 * instead, which is why the entity type uses a short update interval.</p>
 */
public abstract class VaultOrbEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_VALUE =
            SynchedEntityData.defineId(VaultOrbEntity.class, EntityDataSerializers.INT);

    /** Ticks an orb survives before despawning; matches vanilla XP orbs. */
    private static final int LIFETIME = 6000;

    /**
     * Base attraction radius in blocks (§11). Resonance Magnetism raises it to
     * 16 and then 24; Hoarder's Instinct sets it to zero and makes orbs
     * permanent. Both are Phase 6 node tickets, so this is a plain constant
     * until {@link #attractionRadius()} has a skill snapshot to read.
     */
    protected static final double BASE_ATTRACTION_RADIUS = 8.0;

    private final InterpolationHandler interpolation = new InterpolationHandler(this);

    /** Team the orb pays; resolved when it is spawned and persisted with it. */
    private @Nullable UUID teamId;

    private int age;

    private @Nullable Player followingPlayer;

    protected VaultOrbEntity(EntityType<? extends VaultOrbEntity> type, Level level) {
        super(type, level);
    }

    protected VaultOrbEntity(EntityType<? extends VaultOrbEntity> type, Level level, Vec3 pos, UUID teamId, int value) {
        this(type, level);
        this.teamId = teamId;
        setPos(pos);
        setValue(value);
        if (!level.isClientSide()) {
            setYRot(random.nextFloat() * 360.0F);
            setDeltaMovement(
                    (random.nextDouble() * 0.2 - 0.1) * 2.0,
                    random.nextDouble() * 0.2 * 2.0,
                    (random.nextDouble() * 0.2 - 0.1) * 2.0);
        }
    }

    // ----- absorption -----

    /**
     * Called when a member of the orb's team touches it. Implementations credit
     * the team pool; the orb is discarded either way.
     */
    protected abstract void onAbsorbed(ServerPlayer player, UUID teamId, int value);

    /**
     * Sound played on pickup. Vanilla XP orbs are the reference point (§4.2), so
     * a subclass only overrides this to sound different, not to sound like
     * something else entirely.
     */
    protected abstract void playPickupSound(ServerPlayer player);

    @Override
    public void playerTouch(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || teamId == null) {
            return;
        }
        // §11: any member of the owning team collects, first to arrive. A player
        // from another team walks through it.
        if (!teamId.equals(TeamHelper.getTeamId(serverPlayer))) {
            return;
        }
        if (player.takeXpDelay != 0) {
            return;
        }
        player.takeXpDelay = 2;
        player.take(this, 1);
        playPickupSound(serverPlayer);
        onAbsorbed(serverPlayer, teamId, getValue());
        discard();
    }

    // ----- state -----

    public int getValue() {
        return entityData.get(DATA_VALUE);
    }

    protected void setValue(int value) {
        entityData.set(DATA_VALUE, value);
    }

    /** Owning team, or {@code null} on the client and before the orb is placed. */
    public @Nullable UUID getTeamId() {
        return teamId;
    }

    /**
     * Sprite index on the vanilla orb sheet, which is how an orb shows its worth
     * at a glance. Vanilla's thresholds are reused deliberately rather than
     * rescaled for Resonance: the §4.2 base rates land across the first four
     * sizes as it is — a common ore at 2 is the smallest orb, uncommon at 5 the
     * next, rare at 12 the next, a Tithed rare at 21 the next — and matching
     * vanilla means the size reads the same way players already expect.
     */
    public int getIcon() {
        int value = getValue();
        if (value >= 2477) {
            return 10;
        } else if (value >= 1237) {
            return 9;
        } else if (value >= 617) {
            return 8;
        } else if (value >= 307) {
            return 7;
        } else if (value >= 149) {
            return 6;
        } else if (value >= 73) {
            return 5;
        } else if (value >= 37) {
            return 4;
        } else if (value >= 17) {
            return 3;
        } else if (value >= 7) {
            return 2;
        }
        return value >= 3 ? 1 : 0;
    }

    /** Packed RGB the renderer tints this orb with (§11). */
    public abstract int getTint();

    /** Blocks from which this orb is drawn to a team member. */
    protected double attractionRadius() {
        return BASE_ATTRACTION_RADIUS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // Value is synced because the renderer sizes the orb by it; the team id
        // is not, because only the server ever decides who may collect.
        entityData.define(DATA_VALUE, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Value", getValue());
        output.putInt("Age", age);
        if (teamId != null) {
            output.putString("TeamId", teamId.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setValue(input.getIntOr("Value", 0));
        age = input.getIntOr("Age", 0);
        teamId = input.getString("TeamId").map(UUID::fromString).orElse(null);
    }

    // ----- movement -----

    @Override
    public void tick() {
        interpolation.interpolate();
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            return; // the client shows what the server sent; see the class javadoc
        }

        applyGravity();
        followNearbyTeamMember();
        move(MoverType.SELF, getDeltaMovement());

        float friction = 0.98F;
        if (onGround()) {
            friction = level().getBlockState(getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
        }
        setDeltaMovement(getDeltaMovement().scale(friction));

        age++;
        if (age >= LIFETIME) {
            discard();
        }
    }

    /**
     * Drifts toward the nearest collectable team member, re-targeting when the
     * current one moves out of range. The acceleration curve is vanilla's, so an
     * orb feels the same as the XP orbs it sits next to.
     */
    private void followNearbyTeamMember() {
        double radius = attractionRadius();
        if (radius <= 0) {
            return; // Hoarder's Instinct: orbs stay put (Phase 6)
        }

        if (followingPlayer == null
                || followingPlayer.isRemoved()
                || followingPlayer.distanceToSqr(this) > radius * radius) {
            followingPlayer = level().getNearestPlayer(getX(), getY(), getZ(), radius, this::canCollect);
        }
        if (followingPlayer == null) {
            return;
        }

        Vec3 delta = new Vec3(
                followingPlayer.getX() - getX(),
                followingPlayer.getY() + followingPlayer.getEyeHeight() / 2.0 - getY(),
                followingPlayer.getZ() - getZ());
        double pull = 1.0 - Math.sqrt(delta.lengthSqr()) / radius;
        setDeltaMovement(getDeltaMovement().add(delta.normalize().scale(pull * pull * 0.1)));
    }

    private boolean canCollect(Entity entity) {
        return entity instanceof ServerPlayer player
                && !player.isSpectator()
                && !player.isDeadOrDying()
                && teamId != null
                && teamId.equals(TeamHelper.getTeamId(player));
    }

    // ----- entity boilerplate -----

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public final boolean hurtClient(DamageSource source) {
        return false;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // Vanilla XP orbs have health and can be destroyed. These carry team
        // progression, so losing one to a stray arrow or a splash of lava would
        // be a silent, unexplainable loss.
        return false;
    }

    @Override
    protected void doWaterSplashEffect() {
    }
}
