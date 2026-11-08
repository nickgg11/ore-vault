package com.orevault.orevault.client;

import com.orevault.orevault.core.portal.PortalCharge;
import com.orevault.orevault.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jspecify.annotations.Nullable;

/**
 * Our charge sound in a Vault portal, in place of the vanilla trigger (§3.2; Nick, 2026-11-08; #407). The 4-second drone
 * matches the 80-tick charge; it starts on the tick the local player starts charging and fades out over a few ticks if
 * they step out before travel. Like the trigger it is the local player's own, so it plays at the listener, not the portal.
 *
 * <p>The vanilla trigger comes from {@code LocalPlayer#handlePortalTransitionEffect} when the intensity leaves zero in any
 * portal with the confusion transition, so it is dropped in {@link PlaySoundEvent} while the processor names a Vault portal.
 * The portal travel sound stays vanilla.
 */
final class PortalChargeSound extends AbstractTickableSoundInstance {
    /** Volume lost a tick after stepping out: four ticks to silence, so the cut does not click. */
    private static final float FADE_STEP = 0.25F;

    /** Whether the local player was charging at the end of the last client tick, so a sound starts only on the edge. */
    private static boolean wasCharging;

    /** The drone now playing, so a pause or a quick re-entry never layers a second one over it. */
    private static @Nullable PortalChargeSound live;

    private final LocalPlayer owner;
    private boolean fading;

    private PortalChargeSound(LocalPlayer owner) {
        super(ModSounds.PORTAL_CHARGE.value(), SoundSource.AMBIENT, RandomSource.create());
        this.owner = owner;
        this.looping = false;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean charging = player != null && charging(player);
        if (charging && !wasCharging) {
            boolean playing = live != null && minecraft.getSoundManager().isActive(live);
            if (playing && live.fading) {
                minecraft.getSoundManager().stop(live);
                playing = false;
            }
            if (!playing) {
                live = new PortalChargeSound(player);
                minecraft.getSoundManager().play(live);
            }
        }
        wasCharging = charging;
    }

    static void dropVanillaTrigger(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        LocalPlayer player = Minecraft.getInstance().player;
        if (sound != null && player != null && sound.getIdentifier().equals(SoundEvents.PORTAL_TRIGGER.location())
                && PortalOverlay.vaultTier(player.portalProcess) > 0) {
            event.setSound(null);
        }
    }

    private static boolean charging(LocalPlayer player) {
        return PortalCharge.charging(PortalOverlay.vaultTier(player.portalProcess) > 0, player.oPortalEffectIntensity,
                player.portalEffectIntensity);
    }

    /**
     * Fades once the owner stops charging, and once the client has a new player (travel, respawn, a command move), so a
     * drone restarted over a partial charge does not carry into the next dimension.
     */
    @Override
    public void tick() {
        LocalPlayer current = Minecraft.getInstance().player;
        if (current == null) {
            stop();
            return;
        }
        if (current != owner || !charging(owner)) {
            fading = true;
        }
        if (fading) {
            volume -= FADE_STEP;
            if (volume <= 0.0F) {
                stop();
            }
        }
    }
}
