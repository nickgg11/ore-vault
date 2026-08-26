package com.orevault.orevault.entity;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.skill.SkillTree;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Base class for Resonance and Animus orbs. Behavior mirrors vanilla XP orbs, except the
 * value accrues to the team pool on collection and attraction is team-scoped with a radius
 * controlled by skill nodes.
 */
public abstract class VaultOrbEntity extends Entity {
    protected static final EntityDataAccessor<Integer> DATA_VALUE =
            SynchedEntityData.defineId(VaultOrbEntity.class, EntityDataSerializers.INT);

    private UUID teamId = new UUID(0, 0);
    private int age;
    private int radiusRefreshCooldown;
    private double cachedRadius = 4.0;

    public VaultOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID teamId() {
        return teamId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VALUE, 0);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    public int getValue() {
        return entityData.get(DATA_VALUE);
    }

    public void setValue(int value) {
        entityData.set(DATA_VALUE, value);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Base collection radius when no radius nodes are purchased (Resonance Magnetism raises it). */
    protected double baseFollowRadius() {
        return 4.0;
    }

    /** Node-dependent follow radius; Hoarder's Instinct reduces it to 0 (manual collection). */
    protected double followRadius() {
        return baseFollowRadius();
    }

    protected double multiplierForCollector(ServerPlayer collector) {
        return 1.0;
    }

    /** Called server-side when a team member collects the orb; returns the amount credited. */
    protected abstract long collect(ServerLevel level, ServerPlayer collector, int value);

    protected abstract boolean isHoarderActive(OreVaultTeamData data);

    @Override
    public void tick() {
        super.tick();
        if (radiusRefreshCooldown-- <= 0) {
            radiusRefreshCooldown = 20;
            cachedRadius = computeRadius();
        }
        if (!this.level().isClientSide()) {
            followTeamMember();
        }
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.verticalCollisionBelow && this.getDeltaMovement().y < -this.getGravity()) {
            this.setDeltaMovement(new Vec3(this.getDeltaMovement().x, -this.getDeltaMovement().y * 0.4, this.getDeltaMovement().z));
        }
        this.age++;
        if (this.age >= 6000) {
            this.discard();
        }
    }

    private double computeRadius() {
        if (this.level() instanceof ServerLevel serverLevel) {
            OreVaultTeamData data = OreVaultTeamData.get(serverLevel.getServer(), teamId);
            if (isHoarderActive(data)) {
                return 0.0;
            }
            int tier = data.nodeTier(magnetismNodeId());
            return switch (tier) {
                case 1 -> 8.0;
                case 2 -> 16.0;
                case 3 -> 24.0;
                default -> baseFollowRadius();
            };
        }
        return baseFollowRadius();
    }

    protected abstract String magnetismNodeId();

    private void followTeamMember() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (cachedRadius <= 0) {
            return; // Hoarder's Instinct: only manual collection
        }
        ServerPlayer target = null;
        double best = cachedRadius * cachedRadius;
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isSpectator() && !player.isDeadOrDying()
                    && teamId.equals(TeamHelper.teamIdFor(player))) {
                double d = player.distanceToSqr(this);
                if (d < best) {
                    best = d;
                    target = player;
                }
            }
        }
        if (target != null) {
            Vec3 delta = new Vec3(
                    target.getX() - this.getX(),
                    target.getY() + target.getEyeHeight() / 2.0 - this.getY(),
                    target.getZ() - this.getZ()
            );
            double length = delta.lengthSqr();
            if (length < 0.75) {
                absorb(serverLevel, target);
                return;
            }
            double power = 1.0 - Math.sqrt(length) / cachedRadius;
            this.setDeltaMovement(this.getDeltaMovement().add(delta.normalize().scale(power * power * 0.1)));
        }
    }

    @Override
    public void touch(Entity entity) {
        super.touch(entity);
        if (!this.level().isClientSide() && entity instanceof ServerPlayer player
                && teamId.equals(TeamHelper.teamIdFor(player)) && !player.isSpectator()) {
            absorb((ServerLevel) this.level(), player);
        }
    }

    private void absorb(ServerLevel level, ServerPlayer player) {
        long credited = collect(level, player, getValue());
        if (credited > 0) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Team")) {
            teamId = tag.getUUID("Team");
        }
        setValue(tag.getIntOr("Value", 0));
        age = tag.getIntOr("Age", 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Team", teamId.toString());
        tag.putInt("Value", getValue());
        tag.putInt("Age", age);
    }
}
