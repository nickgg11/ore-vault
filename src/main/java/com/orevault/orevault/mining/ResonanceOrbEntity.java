package com.orevault.orevault.mining;

import com.orevault.orevault.core.mining.ResonanceColor;
import com.orevault.orevault.registry.ModEntities;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Resonance orb (§4.2, §11 Resonance Orb Entity). {@link #spawn} is the one way the mod makes one; the drop pipeline
 * calls it per break (#264).
 */
public final class ResonanceOrbEntity extends VaultOrbEntity {
    public ResonanceOrbEntity(EntityType<? extends ResonanceOrbEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Spawns an orb with vanilla's scatter. Main thread only.
     *
     * @param team the Vault's team, from the dimension, not the breaker
     * @param valueThousandths the per-break amount after bonuses, before team scaling
     * @return the orb, or empty for a value of 0 or less, which spawns nothing (Balancer, #260)
     */
    public static Optional<ResonanceOrbEntity> spawn(ServerLevel level, Vec3 pos, UUID team, long valueThousandths) {
        if (valueThousandths <= 0L) {
            return Optional.empty();
        }
        ResonanceOrbEntity orb = new ResonanceOrbEntity(ModEntities.RESONANCE_ORB.get(), level);
        orb.setPos(pos);
        orb.setYRot(level.getRandom().nextFloat() * 360.0F);
        orb.setDeltaMovement((level.getRandom().nextDouble() * 0.2 - 0.1) * 2.0, level.getRandom().nextDouble() * 0.2 * 2.0,
                (level.getRandom().nextDouble() * 0.2 - 0.1) * 2.0);
        orb.setFields(new OrbFields(Optional.of(team), valueThousandths, 1, 0));
        if (!level.noCollision(orb.getBoundingBox())) {
            orb.unstuckIfPossible(orb.getBoundingBox().getSize());
        }
        return level.addFreshEntity(orb) ? Optional.of(orb) : Optional.empty();
    }

    @Override
    protected void pay(ServerPlayer collector, UUID team, long valueThousandths, int orbCount) {
        ResonanceAward.collect(collector, team, valueThousandths);
    }

    @Override
    public int tint(float ageInTicks) {
        return ResonanceColor.pulse(ageInTicks);
    }
}
