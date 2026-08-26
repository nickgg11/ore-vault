package com.orevault.orevault.entity;

import com.orevault.orevault.animus.AnimusSystem;
import com.orevault.orevault.data.OreVaultTeamData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Animus orb: red/dark red. Adds to the team Animus pool on collection. No magnetism node
 * exists in the Animus tree, so the base radius applies.
 */
public class AnimusOrbEntity extends VaultOrbEntity {
    public AnimusOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected String magnetismNodeId() {
        return ""; // no radius node in the Animus tree
    }

    @Override
    protected boolean isHoarderActive(OreVaultTeamData data) {
        return false;
    }

    @Override
    protected long collect(ServerLevel level, ServerPlayer collector, int value) {
        AnimusSystem.addAnimus(level.getServer(), teamId(), value, collector.getUUID());
        return value;
    }

    /** Spawns a burst of orbs totalling the given Animus. */
    public static void spawn(ServerLevel level, Vec3 pos, long animus, UUID teamId) {
        long remaining = animus;
        while (remaining > 0) {
            int value = (int) Math.min(remaining, 10);
            remaining -= value;
            AnimusOrbEntity orb = ModEntities.ANIMUS_ORB.get().create(level);
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

