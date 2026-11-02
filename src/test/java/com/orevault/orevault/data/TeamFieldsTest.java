package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Team data's save shape (ADR 0005 sections 3 and 6, S17): the keys, their defaults, and that nothing throws. */
class TeamFieldsTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    private final List<String> warnings = new ArrayList<>();

    private TeamFields read(CompoundTag tag) {
        return TeamFields.read(tag, TEAM, warnings::add);
    }

    @Test
    void theGoldenKeySetIsPinned() {
        CompoundTag tag = new TeamFields(2, OptionalLong.of(99L)).write(TEAM);
        assertEquals(Set.of("data_version", "team_id", "vault_generation", "disbanded_at"), tag.keySet());
    }

    @Test
    void aTeamNotDisbandedWritesNoDisbandedAt() {
        assertFalse(TeamFields.DEFAULTS.write(TEAM).contains("disbanded_at"));
    }

    @Test
    void theShapeStartsAtVersionOne() {
        assertEquals(1, TeamFields.DEFAULTS.write(TEAM).getIntOr("data_version", 0));
    }

    @Test
    void theTeamIdIsWrittenHyphenated() {
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", TeamFields.DEFAULTS.write(TEAM).getStringOr("team_id", ""));
    }

    @Test
    void aWrittenTagReadsBack() {
        TeamFields fields = new TeamFields(4, OptionalLong.of(1_728_000L));
        assertEquals(fields, read(fields.write(TEAM)));
        assertEquals(List.of(), warnings);
    }

    @Test
    void anEmptyTagReadsEveryDefault() {
        assertEquals(TeamFields.DEFAULTS, read(new CompoundTag()));
        assertEquals(List.of(), warnings);
    }

    @Test
    void theDefaultsAreGenerationZeroAndNotDisbanded() {
        assertEquals(0, TeamFields.DEFAULTS.vaultGeneration());
        assertEquals(OptionalLong.empty(), TeamFields.DEFAULTS.disbandedAt());
    }

    @Test
    void aTagWithoutTheGenerationReadsZero() {
        CompoundTag tag = new TeamFields(4, OptionalLong.of(5L)).write(TEAM);
        tag.remove("vault_generation");
        assertEquals(new TeamFields(0, OptionalLong.of(5L)), read(tag));
    }

    @Test
    void aTagWithoutDisbandedAtIsNotDisbanded() {
        CompoundTag tag = new TeamFields(4, OptionalLong.of(5L)).write(TEAM);
        tag.remove("disbanded_at");
        assertEquals(new TeamFields(4, OptionalLong.empty()), read(tag));
    }

    /** A disband at game time zero is a disband: absence means not disbanded, not a value of 0. */
    @Test
    void disbandedAtZeroStaysSet() {
        TeamFields fields = new TeamFields(0, OptionalLong.of(0L));
        assertEquals(fields, read(fields.write(TEAM)));
    }

    @Test
    void aTagWithoutAVersionReadsAsVersionOne() {
        CompoundTag tag = new TeamFields(3, OptionalLong.empty()).write(TEAM);
        tag.remove("data_version");
        assertEquals(new TeamFields(3, OptionalLong.empty()), read(tag));
        assertEquals(List.of(), warnings);
    }

    @Test
    void aGenerationOfTheWrongTypeReadsItsDefault() {
        CompoundTag tag = new CompoundTag();
        tag.putString("vault_generation", "three");
        assertEquals(TeamFields.DEFAULTS, read(tag));
    }

    @Test
    void aDisbandedAtOfTheWrongTypeReadsNotDisbanded() {
        CompoundTag tag = new CompoundTag();
        tag.putString("disbanded_at", "yesterday");
        assertEquals(TeamFields.DEFAULTS, read(tag));
    }

    @Test
    void aVersionOfTheWrongTypeReadsAsVersionOne() {
        CompoundTag tag = new TeamFields(3, OptionalLong.empty()).write(TEAM);
        tag.putString("data_version", "new");
        assertEquals(new TeamFields(3, OptionalLong.empty()), read(tag));
    }

    @Test
    void aNewerVersionReadsKnownKeysAndWarnsOnce() {
        CompoundTag tag = new TeamFields(3, OptionalLong.of(7L)).write(TEAM);
        tag.putInt("data_version", 99);
        tag.putString("a_key_from_the_future", "kept out");
        assertEquals(new TeamFields(3, OptionalLong.of(7L)), read(tag));
        assertEquals(1, warnings.size(), warnings.toString());
        String warning = warnings.getFirst();
        assertTrue(warning.contains("99") && warning.contains("1") && warning.contains(TEAM.toString()), warning);
    }

    @Test
    void anUnknownKeyIsDroppedOnTheNextSave() {
        CompoundTag tag = TeamFields.DEFAULTS.write(TEAM);
        tag.putString("a_key_from_the_future", "kept out");
        assertFalse(read(tag).write(TEAM).contains("a_key_from_the_future"));
    }

    @Test
    void theFilesOwnIdWinsAndAMismatchWarns() {
        UUID other = UUID.fromString("ffffffff-89ab-cdef-0123-456789abcdef");
        CompoundTag tag = TeamFields.DEFAULTS.write(other);
        assertEquals(TEAM.toString(), read(tag).write(TEAM).getStringOr("team_id", ""));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains(other.toString()), warnings.getFirst());
    }

    @Test
    void anAbsentTeamIdDoesNotWarn() {
        CompoundTag tag = TeamFields.DEFAULTS.write(TEAM);
        tag.remove("team_id");
        read(tag);
        assertEquals(List.of(), warnings);
    }

    /** ADR 0005 section 6: a step changes nothing the second time. At version 1 there is no step to run. */
    @Test
    void migratingTwiceChangesNothing() {
        CompoundTag tag = new TeamFields(3, OptionalLong.of(7L)).write(TEAM);
        CompoundTag once = TeamFields.migrate(tag.copy(), 1);
        assertEquals(tag, once);
        assertEquals(once, TeamFields.migrate(once.copy(), 1));
    }

    @Test
    void markingDisbandedKeepsTheGeneration() {
        assertEquals(new TeamFields(2, OptionalLong.of(50L)), new TeamFields(2, OptionalLong.empty()).withDisbandedAt(50L));
    }
}
