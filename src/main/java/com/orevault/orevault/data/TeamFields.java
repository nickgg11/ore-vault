package com.orevault.orevault.data;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/**
 * Team data's fields and their save shape (§11 SavedData Pattern, ADR 0005 section 3), in a plain class so the keys, the
 * defaults and the migration run in JUnit. This is the shell of E3-S9; later stories add their keys here, each reading its
 * default from a tag written without it.
 *
 * <p>The reader never throws, for any input: {@code SavedDataStorage} turns a throw into a missing file, and the next save
 * would then write an empty team over the real one. A Vault's dimension type is not here. It is a fact about the terrain,
 * so it is in the Vault's folder ({@link VaultTypeFile}).
 *
 * @param vaultGeneration the reset count, part of every chunk seed (§3.1)
 * @param disbandedAt overworld game time of the disband, empty for a team not disbanded
 */
public record TeamFields(int vaultGeneration, OptionalLong disbandedAt) {
    public static final TeamFields DEFAULTS = new TeamFields(0, OptionalLong.empty());

    private static final int CURRENT_VERSION = 1;
    private static final String DATA_VERSION = "data_version";
    private static final String TEAM_ID = "team_id";
    private static final String VAULT_GENERATION = "vault_generation";
    private static final String DISBANDED_AT = "disbanded_at";

    /** @param warn takes one line each for a tag from a newer build and for a {@code team_id} that is not the file's */
    public static TeamFields read(CompoundTag tag, UUID fileTeamId, Consumer<String> warn) {
        int stored = tag.getIntOr(DATA_VERSION, 1);
        if (stored > CURRENT_VERSION) {
            warn.accept("Team data of " + fileTeamId + " is version " + stored + ", newer than this build's "
                    + CURRENT_VERSION + "; reading what it knows");
        }
        CompoundTag current = migrate(tag.copy(), stored);
        tag.getString(TEAM_ID).filter(id -> !id.equals(fileTeamId.toString())).ifPresent(id ->
                warn.accept("Team data of " + fileTeamId + " says it belongs to " + id + "; the file's own id wins"));
        return new TeamFields(current.getIntOr(VAULT_GENERATION, DEFAULTS.vaultGeneration),
                current.getLong(DISBANDED_AT).map(OptionalLong::of).orElse(DEFAULTS.disbandedAt));
    }

    /** The team id goes in for people reading the file; the file's own id is the one that counts. */
    public CompoundTag write(UUID teamId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        tag.putString(TEAM_ID, teamId.toString());
        tag.putInt(VAULT_GENERATION, vaultGeneration);
        disbandedAt.ifPresent(time -> tag.putLong(DISBANDED_AT, time));
        return tag;
    }

    /**
     * Brings a tag to the current shape. A fall-through chain: each case upgrades one version in place, keeps frozen copies
     * of what it translates from, and changes nothing the second time. Version 1 is the first, so there is no step yet.
     */
    static CompoundTag migrate(CompoundTag tag, int storedVersion) {
        return tag;
    }

    public TeamFields withDisbandedAt(long gameTime) {
        return new TeamFields(vaultGeneration, OptionalLong.of(gameTime));
    }
}
