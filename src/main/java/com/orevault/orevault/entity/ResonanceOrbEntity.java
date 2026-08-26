package com.orevault.orevault.entity;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.skill.NodeDefs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Resonance orb: blue/cyan. Adds to the team Resonance pool on collection.
 */
public class ResonanceOrbEntity extends VaultOrbEntity {
    public ResonanceOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected String magnetismNodeId() {
        return "resonance_magnetism";
    }

    @Override
    protected boolean isHoarderActive(OreVaultTeamData data) {
        return data.hasNode("hoarders_instinct");
    }

    @Override
    protected double multiplierForCollector(ServerPlayer collector) {
        // Hoarder's Instinct: 2x Resonance on manual collection.
        if (collector != null) {
            OreVaultTeamData data = OreVaultTeamData.get(collector.getServer(), teamId());
            if (data.hasNode("hoarders_instinct")) {
                return 2.0;
            }
        }
        return 1.0;
    }

    @Override
    protected long collect(ServerLevel level, ServerPlayer collector, int value) {
        double mult = multiplierForCollector(collector);
        long amount = Math.max(1, Math.round(value * mult));
        ResonanceSystem.addResonance(level.getServer(), teamId(), amount, collector.getUUID());
        return amount;
    }

    /** Spawns a burst of orbs totalling the given Resonance. */
    public static void spawn(ServerLevel level, Vec3 pos, long resonance, UUID teamId) {
        long remaining = resonance;
        while (remaining > 0) {
            int value = (int) Math.min(remaining, 10);
            remaining -= value;
            ResonanceOrbEntity orb = ModEntities.RESONANCE_ORB.get().create(level);
            if (orb == null) {
                continue;
            }
            orb.setTeamId(teamId);
            orb.setValue(value);
            orb.setPos(pos);
            orb.setDeltaMovement(new Vec3(
                    (level.getRandom().nextDouble() * 0.2 - 0.1) * 2.0,
                    level.getRandom().nextDouble() * 0.2 * 2.0,
                    (level.getRandom().nextDouble() * 0.2 - 0.1) * 2.0));
            level.addFreshEntity(orb);
        }
    }
}

