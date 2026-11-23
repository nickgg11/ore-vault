package com.orevault.orevault.data;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.GenerationTiers;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.OrePicker;
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
    private static final Map<UUID, Holder> HOLDERS = new HashMap<>();

    /**
     * One team's holder and the pickers its snapshots are built from. The pickers are the published classification's for
     * the holder's variant, immutable and the same for the whole session, so a rebuild reads them here rather than from the
     * classifier, which {@code data} does not import (ADR 0001).
     */
    private record Holder(AtomicReference<GenSnapshot> snapshot, Map<Band, OrePicker> pickers) {
    }

    private GenSnapshots() {
    }

    /**
     * ADR 0002 creation step 3: a new holder for the team, filled before its generator is built. Writes no team data.
     *
     * @param pickers the variant's pickers from the classifier's published record, which the caller reads (it throws when
     *     nothing is published, as {@code VaultChunkGenerator.forVault} does); none for a pack with no classified ore
     */
    public static Supplier<GenSnapshot> fill(MinecraftServer server, UUID teamId, VaultVariant variant, Map<Band, OrePicker> pickers) {
        return fill(teamId, variant, pickers, OreVaultTeamData.read(server, teamId).vaultGeneration());
    }

    static Supplier<GenSnapshot> fill(UUID teamId, VaultVariant variant, Map<Band, OrePicker> pickers, int vaultGeneration) {
        AtomicReference<GenSnapshot> snapshot = new AtomicReference<>(snapshot(variant, pickers, vaultGeneration));
        HOLDERS.put(teamId, new Holder(snapshot, Map.copyOf(pickers)));
        return snapshot::get;
    }

    /** One of ADR 0003's rebuild triggers. A team with no holder has nothing generating, so nothing is read. */
    public static void rebuild(MinecraftServer server, UUID teamId) {
        rebuild(teamId, () -> OreVaultTeamData.read(server, teamId).vaultGeneration());
    }

    static void rebuild(UUID teamId, IntSupplier vaultGeneration) {
        Holder holder = HOLDERS.get(teamId);
        if (holder != null) {
            holder.snapshot().set(snapshot(holder.snapshot().get().variant(), holder.pickers(), vaultGeneration.getAsInt()));
        }
    }

    /**
     * Both paths build the snapshot here, with the clip factors from {@code ClipFactors.of} (Architect, ARCH-E4-16), which
     * reads the pickers' profiles. No generation node exists yet, so the tiers are empty; E9-S3 (#307) reads the team's
     * owned generation tiers here.
     */
    private static GenSnapshot snapshot(VaultVariant variant, Map<Band, OrePicker> pickers, int vaultGeneration) {
        return GenSnapshot.of(variant, vaultGeneration, pickers, GenerationTiers.NONE);
    }

    /** The team's current snapshot, for the S44 readout. */
    public static Optional<GenSnapshot> current(UUID teamId) {
        return Optional.ofNullable(HOLDERS.get(teamId)).map(holder -> holder.snapshot().get());
    }

    /** At {@code ServerStoppedEvent}. */
    public static void clear() {
        HOLDERS.clear();
    }
}
