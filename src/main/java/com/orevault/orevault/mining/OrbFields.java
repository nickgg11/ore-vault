package com.orevault.orevault.mining;

import com.orevault.orevault.core.mining.OrbRules;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/**
 * A Vault orb's saved fields and their shape (ADR 0005 section 10, amended 2026-11-25 for #260), in a plain class so the
 * keys, the defaults and the migration run in JUnit. The entity holds one of these as the source of truth and writes it
 * nested under one {@code orb} compound: a {@code ValueInput} cannot be built in a unit test, a {@code CompoundTag} can.
 *
 * <p>An orb on the ground is save data. The reader never throws for any input: an entity whose load throws is lost with
 * whatever it carried.
 *
 * @param team the owning team; empty follows nobody, pays nobody and ages out
 * @param value thousandths of a Resonance before team scaling, never negative; 0 is discarded on the first tick
 * @param orbCount how many orbs merged into this one, at least 1: Hoarder's Instinct's N (§6.1)
 * @param age ticks lived, 0 to {@link OrbRules#LIFETIME}
 */
public record OrbFields(Optional<UUID> team, long value, int orbCount, int age) {
    public static final int DATA_VERSION = 1;
    public static final OrbFields DEFAULTS = new OrbFields(Optional.empty(), 0L, 1, 0);

    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_TEAM = "team";
    private static final String KEY_VALUE = "value";
    private static final String KEY_ORB_COUNT = "orb_count";
    private static final String KEY_AGE = "age";

    /** @param warn takes one line for a tag from a newer build */
    public static OrbFields read(CompoundTag tag, Consumer<String> warn) {
        int stored = tag.getIntOr(KEY_DATA_VERSION, DATA_VERSION);
        if (stored > DATA_VERSION) {
            warn.accept("A Vault orb is saved at version " + stored + ", newer than this build's " + DATA_VERSION
                    + "; reading what it knows");
        }
        CompoundTag current = migrate(tag.copy(), stored);
        // getLongOr and getIntOr read a tag of another type as the default; a value outside the range reads as the
        // nearest one inside it.
        long value = Math.max(0L, current.getLongOr(KEY_VALUE, DEFAULTS.value));
        int orbCount = Math.max(1, current.getIntOr(KEY_ORB_COUNT, DEFAULTS.orbCount));
        int age = Math.clamp(current.getIntOr(KEY_AGE, DEFAULTS.age), 0, OrbRules.LIFETIME);
        return new OrbFields(current.getString(KEY_TEAM).flatMap(OrbFields::parseTeam), value, orbCount, age);
    }

    private static Optional<UUID> parseTeam(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_DATA_VERSION, DATA_VERSION);
        team.ifPresent(id -> tag.putString(KEY_TEAM, id.toString()));
        tag.putLong(KEY_VALUE, value);
        tag.putInt(KEY_ORB_COUNT, orbCount);
        tag.putInt(KEY_AGE, age);
        return tag;
    }

    /**
     * Brings a tag to the current shape. A fall-through chain, as {@code TeamFields#migrate}: each case upgrades one version
     * in place. Version 1 is the first, so there is no step yet.
     */
    static CompoundTag migrate(CompoundTag tag, int storedVersion) {
        return tag;
    }

    public OrbFields withAge(int newAge) {
        return new OrbFields(team, value, orbCount, newAge);
    }

    public OrbFields with(long newValue, int newOrbCount, int newAge) {
        return new OrbFields(team, newValue, newOrbCount, newAge);
    }
}
