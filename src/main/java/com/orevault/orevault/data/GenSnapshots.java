package com.orevault.orevault.data;

import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.VaultVariant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/**
 * Each team's generation snapshot holder (§11 Dynamic Chunk Generator, ADR 0003 "Holders and the supplier"). Built here, on
 * the main thread, from team data; read by the generator off it through the supplier {@link #fill} returns, which is the
 * one thing the generator holds of the team (rule 1).
 *
 * <p>The map is main-thread only; a holder is an {@link AtomicReference} because the generator reads it from another
 * thread. {@link #fill} always puts a new holder in the map, never a new value into the old one, so a generator still
 * draining a level that has been replaced keeps the snapshot it was built with and never sees another variant. A rebuild
 * keeps the variant of the holder it updates, since team data does not hold it (ADR 0002 creation step 2). The map is
 * static and a personal team's id is the same in a second world in one client, so the root listener clears it at
 * {@code ServerStoppedEvent}.
 */
public final class GenSnapshots {
    private static final Map<UUID, AtomicReference<GenSnapshot>> HOLDERS = new HashMap<>();

    private GenSnapshots() {
    }

    /** ADR 0002 creation step 3: a new holder for the team, filled before its generator is built. Writes no team data. */
    public static Supplier<GenSnapshot> fill(MinecraftServer server, UUID teamId, VaultVariant variant) {
        return fill(teamId, variant, OreVaultTeamData.read(server, teamId).vaultGeneration());
    }

    static Supplier<GenSnapshot> fill(UUID teamId, VaultVariant variant, int vaultGeneration) {
        AtomicReference<GenSnapshot> holder = new AtomicReference<>(new GenSnapshot(variant, vaultGeneration));
        HOLDERS.put(teamId, holder);
        return holder::get;
    }

    /** One of ADR 0003's rebuild triggers. A team with no holder has nothing generating, so nothing is read. */
    public static void rebuild(MinecraftServer server, UUID teamId) {
        rebuild(teamId, () -> OreVaultTeamData.read(server, teamId).vaultGeneration());
    }

    static void rebuild(UUID teamId, IntSupplier vaultGeneration) {
        AtomicReference<GenSnapshot> holder = HOLDERS.get(teamId);
        if (holder != null) {
            holder.set(new GenSnapshot(holder.get().variant(), vaultGeneration.getAsInt()));
        }
    }

    /** The team's current snapshot, for the S44 readout. */
    public static Optional<GenSnapshot> current(UUID teamId) {
        return Optional.ofNullable(HOLDERS.get(teamId)).map(AtomicReference::get);
    }

    /** At {@code ServerStoppedEvent}. */
    public static void clear() {
        HOLDERS.clear();
    }
}
