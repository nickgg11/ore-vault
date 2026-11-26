package com.orevault.orevault.mining;

import com.orevault.orevault.core.mining.OrbRules;
import com.orevault.orevault.team.TeamHelper;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A team-owned orb that pays a team pool, never player XP (§4.2, §11 Resonance Orb Entity; sketch #260). It is modelled on
 * {@code ExperienceOrb} rather than extending it: vanilla's {@code playerTouch} pays whoever touches it and its follow
 * chases any player, so a subclass would be one missed override from paying whoever walks past. Nothing here reads or
 * writes player XP or {@code takeXpDelay} (§3.4).
 *
 * <p>Movement is the server's alone. The client cannot know team membership, so it only interpolates what the server
 * sends, which is why the type uses {@code updateInterval} 2. "Member" is {@link OrbRules#isMember} against
 * {@link TeamHelper#teamId}, and only a real {@link ServerPlayer} is ever one: a {@link FakePlayer}'s team is a phantom.
 *
 * <p>{@link OrbFields} is the source of truth for team, value, {@code orb_count} and age. {@code DATA_VALUE} mirrors the
 * value for the renderer and is set wherever the fields are.
 */
public abstract class VaultOrbEntity extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultOrbEntity.class);
    private static final EntityDataAccessor<Long> DATA_VALUE =
            SynchedEntityData.defineId(VaultOrbEntity.class, EntityDataSerializers.LONG);
    /** The one compound the fields are saved under (ADR 0005 section 10, amended 2026-11-25). */
    private static final String SAVE_KEY = "orb";

    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private OrbFields fields = OrbFields.DEFAULTS;

    protected VaultOrbEntity(EntityType<? extends VaultOrbEntity> type, Level level) {
        super(type, level);
    }

    /** Pays the orb's whole value to its team. Server thread only; the collector is a member and never a fake player. */
    protected abstract void pay(ServerPlayer collector, UUID team, long valueThousandths, int orbCount);

    /** ARGB tint at an age in ticks, partial ticks included. */
    public abstract int tint(float ageInTicks);

    /** The radius members are attracted within. Resonance Magnetism raises it (#347). */
    protected double attractionRadius() {
        return OrbRules.BASE_RADIUS;
    }

    public final OrbFields fields() {
        return fields;
    }

    /** Sets the fields and the synced value together, so the two never disagree. */
    protected final void setFields(OrbFields newFields) {
        fields = newFields;
        entityData.set(DATA_VALUE, newFields.value());
    }

    /** The value in thousandths as synced to clients: the one figure the renderer may read. */
    public final long syncedValue() {
        return entityData.get(DATA_VALUE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VALUE, 0L);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    /**
     * Vanilla's orb tick on the server, with the follow and the merge swapped for team-aware ones; on the client only the
     * interpolation, because a client prediction would pull orbs toward players who cannot collect them (§11).
     */
    @Override
    public void tick() {
        if (level().isClientSide()) {
            interpolation.interpolate();
            return;
        }
        if (fields.value() <= 0L) {
            // A summoned orb, or an edited save: it carries nothing, so it is not kept (Balancer, #260).
            discard();
            return;
        }
        super.tick();
        boolean colliding = !level().noCollision(getBoundingBox());
        if (getFluidInteraction().isEyeInFluidMatching(this, (_, type, _) -> type.getIsWaterLike())) {
            setUnderwaterMovement();
        } else if (!colliding) {
            applyGravity();
        }
        if (level().getFluidState(blockPosition()).is(FluidTags.LAVA)) {
            setDeltaMovement((random.nextFloat() - random.nextFloat()) * 0.2F, 0.2F,
                    (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
        if (tickCount % OrbRules.SCAN_PERIOD == 1) {
            scanForMerges();
        }
        Player following = followNearbyMember();
        if (following == null && colliding) {
            boolean nextColliding = !level().noCollision(getBoundingBox().move(getDeltaMovement()));
            if (nextColliding) {
                moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0, getZ());
                needsSync = true;
            }
        }
        double fallSpeed = getDeltaMovement().y;
        move(MoverType.SELF, getDeltaMovement());
        applyEffectsFromBlocks();
        float friction = 0.98F;
        if (onGround()) {
            BlockPos below = getBlockPosBelowThatAffectsMyMovement();
            friction = level().getBlockState(below).getFriction(level(), below, this) * 0.98F;
        }
        setDeltaMovement(getDeltaMovement().scale(friction));
        if (verticalCollisionBelow && fallSpeed < -getGravity()) {
            setDeltaMovement(new Vec3(getDeltaMovement().x, -fallSpeed * 0.4, getDeltaMovement().z));
        }
        int age = fields.age() + 1;
        setFields(fields.withAge(age));
        if (age >= OrbRules.LIFETIME) {
            discard();
        }
    }

    /**
     * Rescanned every tick rather than cached, so a member who leaves the team stops pulling the next tick. Vanilla's
     * {@code XpOrbTargetingEvent} is not posted (it takes an {@code ExperienceOrb}), so XP magnets do not move ours.
     */
    private @Nullable Player followNearbyMember() {
        UUID team = fields.team().orElse(null);
        if (team == null) {
            return null;
        }
        double radius = attractionRadius();
        Player target = level().getNearestPlayer(getX(), getY(), getZ(), radius, entity -> entity instanceof ServerPlayer player
                && !(entity instanceof FakePlayer) && !player.isSpectator() && !player.isDeadOrDying()
                && OrbRules.isMember(team, TeamHelper.teamId(player)));
        if (target != null) {
            Vec3 delta = new Vec3(target.getX() - getX(), target.getY() + target.getEyeHeight() / 2.0 - getY(),
                    target.getZ() - getZ());
            double pull = OrbRules.pull(delta.length(), radius);
            setDeltaMovement(getDeltaMovement().add(delta.normalize().scale(pull)));
        }
        return target;
    }

    /** Vanilla's merge minus the equal-value rule: values sum, so the sprite grows with the total. */
    private void scanForMerges() {
        UUID team = fields.team().orElse(null);
        if (team == null) {
            return;
        }
        for (VaultOrbEntity other : level().getEntities(EntityTypeTest.forClass(VaultOrbEntity.class),
                getBoundingBox().inflate(OrbRules.MERGE_REACH),
                orb -> orb != this && orb.getType() == getType() && OrbRules.canMerge(team, orb.fields.team().orElse(null)))) {
            if (isRemoved()) {
                return;
            }
            if (other.isRemoved()) {
                continue;
            }
            OrbRules.Merged merged = OrbRules.merge(fields.value(), fields.orbCount(), fields.age(),
                    other.fields.value(), other.fields.orbCount(), other.fields.age());
            setFields(fields.with(merged.value(), merged.orbCount(), merged.age()));
            other.discard();
        }
    }

    private void setUnderwaterMovement() {
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(movement.x * 0.99F, Math.min(movement.y + 5.0E-4F, 0.06F), movement.z * 0.99F);
    }

    /** Vanilla's, for an orb spawned inside a block. */
    protected final void unstuckIfPossible(double maxDistance) {
        Vec3 center = position().add(0.0, getBbHeight() / 2.0, 0.0);
        VoxelShape allowedCenters = Shapes.create(AABB.ofSize(center, maxDistance, maxDistance, maxDistance));
        level().findFreePosition(this, allowedCenters, center, getBbWidth(), getBbHeight(), getBbWidth())
                .ifPresent(pos -> setPos(pos.add(0.0, -getBbHeight() / 2.0, 0.0)));
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return getOnPos(0.999999F);
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    /**
     * The whole orb pays in one touch, to a member only. No take packet: {@code LivingEntity#take} sends one only for
     * items, arrows and XP orbs, and sending it ourselves makes the client play the item sound. So there is no fly-in
     * animation, and the server plays vanilla's XP pickup sound with vanilla's volume and pitch (§4.2, §11).
     */
    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide() || isRemoved() || !(player instanceof ServerPlayer collector)
                || player instanceof FakePlayer) {
            return;
        }
        UUID team = fields.team().orElse(null);
        if (team == null || !OrbRules.isMember(team, TeamHelper.teamId(collector))) {
            return;
        }
        pay(collector, team, fields.value(), fields.orbCount());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1F,
                (random.nextFloat() - random.nextFloat()) * 0.35F + 0.9F);
        discard();
    }

    /** A lost orb is lost progression, so nothing damages it (#260, old #116). Lava still bounces it, as vanilla. */
    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public final boolean hurtClient(DamageSource source) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(SAVE_KEY, CompoundTag.CODEC, fields.write());
    }

    /** Through {@link #setFields}, so a loaded orb's synced value is its saved one. An absent compound reads as defaults. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setFields(OrbFields.read(input.read(SAVE_KEY, CompoundTag.CODEC).orElseGet(CompoundTag::new), LOGGER::warn));
    }
}
