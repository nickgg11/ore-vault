package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own sounds (§3.2; Nick, 2026-11-08; #407). Each id needs an entry in {@code assets/orevault/sounds.json} and
 * its subtitle key in {@code en_us}; the vanilla portal travel sound stays vanilla.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, OreVault.MOD_ID);

    /** Pressing a signet that opens a portal: a glassy tone gliding up a fifth. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = register("block.vault_portal.open");
    /** Standing in a Vault portal: a drone whose beating slows to unison over the 4-second charge. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CHARGE = register("block.vault_portal.charge");

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    /** The subtitle keys {@code sounds.json} names, for the lang coverage test. */
    public static List<String> subtitleKeys() {
        return SOUNDS.getEntries().stream().map(sound -> "subtitles." + OreVault.MOD_ID + "." + sound.getId().getPath()).toList();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(OreVault.MOD_ID, name)));
    }
}
