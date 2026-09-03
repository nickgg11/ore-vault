package com.orevault.orevault.entity;

import java.util.UUID;

import com.orevault.orevault.resonance.ResonanceSystem;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The orb an ore break pays out in (§4.2). Absorption credits the breaker's
 * team Resonance pool — never the absorbing player's experience.
 *
 * <p>Colour is blue/cyan per §11; the renderer that draws it is [40], so until
 * then it renders as nothing in-world and only the pickup is observable.</p>
 */
public class ResonanceOrbEntity extends VaultOrbEntity {

    /** Blue/cyan (§11), read by the renderer in [40]. */
    public static final int TINT = 0x3FC7E8;

    public ResonanceOrbEntity(EntityType<? extends ResonanceOrbEntity> type, Level level) {
        super(type, level);
    }

    public ResonanceOrbEntity(Level level, Vec3 pos, UUID teamId, int value) {
        super(ModEntities.RESONANCE_ORB.get(), level, pos, teamId, value);
    }

    /** Spawns an orb worth {@code value} Resonance for {@code teamId}. */
    public static ResonanceOrbEntity award(ServerLevel level, Vec3 pos, UUID teamId, int value) {
        ResonanceOrbEntity orb = new ResonanceOrbEntity(level, pos, teamId, value);
        level.addFreshEntity(orb);
        return orb;
    }

    @Override
    protected void onAbsorbed(ServerPlayer player, UUID teamId, int value) {
        // The team is credited, not the player: a solo player is a team of one
        // (§2), so this is the same call either way and there is no player-XP path.
        ResonanceSystem.addResonance(player.level().getServer(), teamId, value);
    }

    @Override
    protected void playPickupSound(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.1F, 0.5F * ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.8F));
    }
}
