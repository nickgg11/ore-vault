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
 * <p>The pool and the level only go up in play. So the type offers an add to the pool and no setter for either: a general
 * setter is the method a later caller lowers the pool with by mistake. The award raises {@code level_reached} (#274). The
 * debug wipe (#275) is the one path that lowers either field (§11).
 *
 * @param resonancePool lifetime Resonance in thousandths, never reset by a level-up (§4.3, §11; ADR 0005 section 3).
 *     Fixed-point so that 20 payments of 0.05 sum to exactly 1; an amount is rounded once, half up, where it
 *     enters the pool, by its caller
 * @param levelReached the highest level the team has had (§11). Stored on purpose, because a {@code curve_divisor} change
 *     must never take a level back
 * @param vaultGeneration the reset count, part of every chunk seed (§3.1)
 * @param disbandedAt overworld game time of the disband, empty for a team not disbanded
 */
public record TeamFields(long resonancePool, int levelReached, int vaultGeneration, OptionalLong disbandedAt) {
    /** The pool's unit (ADR 0005 section 3): the team dump divides by this, and readouts will. */
    public static final long THOUSANDTHS_PER_RESONANCE = 1_000L;
    public static final TeamFields DEFAULTS = new TeamFields(0L, 0, 0, OptionalLong.empty());

    private static final int CURRENT_VERSION = 1;
    private static final String DATA_VERSION = "data_version";
    private static final String TEAM_ID = "team_id";
    private static final String RESONANCE_POOL = "resonance_pool";
    private static final String LEVEL_REACHED = "level_reached";
    private static final String VAULT_GENERATION = "vault_generation";
    private static final String DISBANDED_AT = "disbanded_at";

    /** Nothing in memory holds a negative pool or level; the reader clamps an edited file's negative before it gets here. */
    public TeamFields {
        if (resonancePool < 0L || levelReached < 0) {
            throw new IllegalArgumentException("negative pool " + resonancePool + " or level " + levelReached);
        }
    }

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
        // getLongOr and getIntOr read any non-numeric tag as the default. A negative only comes from an NBT edit and means
        // nothing for either field, so it reads as the default too, and the constructor's check cannot throw here.
        long pool = Math.max(DEFAULTS.resonancePool, current.getLongOr(RESONANCE_POOL, DEFAULTS.resonancePool));
        int level = Math.max(DEFAULTS.levelReached, current.getIntOr(LEVEL_REACHED, DEFAULTS.levelReached));
        return new TeamFields(pool, level, current.getIntOr(VAULT_GENERATION, DEFAULTS.vaultGeneration),
                current.getLong(DISBANDED_AT).map(OptionalLong::of).orElse(DEFAULTS.disbandedAt));
    }

    /** The team id goes in for people reading the file; the file's own id is the one that counts. */
    public CompoundTag write(UUID teamId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        tag.putString(TEAM_ID, teamId.toString());
        tag.putLong(RESONANCE_POOL, resonancePool);
        tag.putInt(LEVEL_REACHED, levelReached);
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
        return new TeamFields(resonancePool, levelReached, vaultGeneration, OptionalLong.of(gameTime));
    }

    /**
     * The pool after an amount enters it, already rounded to thousandths and scaled by its caller (ADR 0005 section 3). It
     * stops at the top of a long rather than throwing: a hand-edited pool near the ceiling must not crash the server at the
     * next payment, which runs inside an entity tick (Architect, #241).
     *
     * <p>A negative amount throws instead: it can only be a bug in the caller, which a test should catch loudly. An
     * overflow can only come from an edited file, which no test reaches, so it saturates.
     *
     * @throws IllegalArgumentException for a negative amount: the pool is never lowered
     */
    public TeamFields plusResonance(long thousandths) {
        if (thousandths < 0L) {
            throw new IllegalArgumentException("Resonance is never taken from the pool: " + thousandths);
        }
        long pool = thousandths > Long.MAX_VALUE - resonancePool ? Long.MAX_VALUE : resonancePool + thousandths;
        return new TeamFields(pool, levelReached, vaultGeneration, disbandedAt);
    }
}
