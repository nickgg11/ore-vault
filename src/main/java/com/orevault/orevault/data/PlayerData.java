package com.orevault.orevault.data;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/**
 * The player attachment {@code orevault:player} and its save shape (§3.2, §11 SavedData Pattern, ADR 0005 section 5), in a
 * plain class so the keys, the defaults and the migration run in JUnit. Later stories add {@code tome_granted}, the entry
 * points and the rest of ADR 0005's table here, each reading its default from a tag written without it.
 *
 * <p>The reader never throws. NeoForge drops a whole attachment whose read fails, before anything of ours sees the tag, so
 * the attachment is registered over the raw compound and this reader does the work (ADR 0005 section 6).
 *
 * @param returnPos where the player entered a Vault from, saved only after that teleport succeeded (§3.2)
 * @param inVault the Vault the player is in, which the login check reads (ADR 0002)
 */
public record PlayerData(Optional<ReturnPos> returnPos, Optional<InVault> inVault) {
    public static final PlayerData DEFAULTS = new PlayerData(Optional.empty(), Optional.empty());

    private static final int CURRENT_VERSION = 1;
    private static final String DATA_VERSION = "data_version";
    private static final String RETURN_POS = "return_pos";
    private static final String IN_VAULT = "in_vault";
    private static final String DIM = "dim";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String TEAM = "team";
    private static final String GENERATION = "generation";

    /** A block position in a named dimension; the dimension is an id string, parsed where it is used. */
    public record ReturnPos(String dim, int x, int y, int z) {
    }

    /** The Vault's team and that team's {@code vault_generation} at entry. The dimension key follows from the team. */
    public record InVault(UUID team, int generation) {
    }

    /** @param warn takes one line for a tag from a newer build */
    public static PlayerData read(CompoundTag tag, Consumer<String> warn) {
        int stored = tag.getIntOr(DATA_VERSION, 1);
        if (stored > CURRENT_VERSION) {
            warn.accept("Player data is version " + stored + ", newer than this build's " + CURRENT_VERSION
                    + "; reading what it knows");
        }
        CompoundTag current = migrate(tag.copy(), stored);
        return new PlayerData(readReturnPos(current), readInVault(current));
    }

    private static Optional<ReturnPos> readReturnPos(CompoundTag tag) {
        return tag.getCompound(RETURN_POS).flatMap(pos -> pos.getString(DIM).flatMap(dim -> pos.getInt(X).flatMap(x ->
                pos.getInt(Y).flatMap(y -> pos.getInt(Z).map(z -> new ReturnPos(dim, x, y, z))))));
    }

    private static Optional<InVault> readInVault(CompoundTag tag) {
        return tag.getCompound(IN_VAULT).flatMap(inVault -> inVault.getString(TEAM).flatMap(PlayerData::uuid)
                .flatMap(team -> inVault.getInt(GENERATION).map(generation -> new InVault(team, generation))));
    }

    private static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        returnPos.ifPresent(pos -> {
            CompoundTag written = new CompoundTag();
            written.putString(DIM, pos.dim());
            written.putInt(X, pos.x());
            written.putInt(Y, pos.y());
            written.putInt(Z, pos.z());
            tag.put(RETURN_POS, written);
        });
        inVault.ifPresent(vault -> {
            CompoundTag written = new CompoundTag();
            written.putString(TEAM, vault.team().toString());
            written.putInt(GENERATION, vault.generation());
            tag.put(IN_VAULT, written);
        });
        return tag;
    }

    /** A fall-through chain, as {@link TeamFields#migrate}. Version 1 is the first, so there is no step yet. */
    static CompoundTag migrate(CompoundTag tag, int storedVersion) {
        return tag;
    }

    public PlayerData withReturnPos(ReturnPos pos) {
        return new PlayerData(Optional.of(pos), inVault);
    }

    public PlayerData withInVault(InVault vault) {
        return new PlayerData(returnPos, Optional.of(vault));
    }

    public PlayerData withoutInVault() {
        return new PlayerData(returnPos, Optional.empty());
    }
}
