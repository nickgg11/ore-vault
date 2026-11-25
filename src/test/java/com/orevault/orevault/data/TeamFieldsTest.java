package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /** A pool and level that cannot be confused with each other or with the generation, so a swapped key fails. */
    private static final TeamFields FULL = new TeamFields(15_300L, 7, 4, OptionalLong.of(1_728_000L));

    @Test
    void theGoldenKeySetIsPinned() {
        CompoundTag tag = FULL.write(TEAM);
        assertEquals(Set.of("data_version", "team_id", "resonance_pool", "level_reached", "vault_generation", "disbanded_at"),
                tag.keySet());
    }

    /** An absent pool and a pool of 0 read alike; both are written so a person reading the file sees them. */
    @Test
    void aFreshTeamWritesItsPoolAndLevel() {
        CompoundTag tag = TeamFields.DEFAULTS.write(TEAM);
        assertEquals(Set.of("data_version", "team_id", "resonance_pool", "level_reached", "vault_generation"), tag.keySet());
    }

    @Test
    void aTeamNotDisbandedWritesNoDisbandedAt() {
        assertFalse(TeamFields.DEFAULTS.write(TEAM).contains("disbanded_at"));
    }

    @Test
    void theShapeStartsAtVersionOne() {
        assertEquals(1, TeamFields.DEFAULTS.write(TEAM).getIntOr("data_version", 0));
    }

    /** ADR 0005 section 6: an added key never bumps the version. */
    @Test
    void thePoolAndLevelLeaveTheVersionAtOne() {
        assertEquals(1, FULL.write(TEAM).getIntOr("data_version", 0));
    }

    @Test
    void theTeamIdIsWrittenHyphenated() {
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", TeamFields.DEFAULTS.write(TEAM).getStringOr("team_id", ""));
    }

    @Test
    void aWrittenTagReadsBack() {
        assertEquals(FULL, read(FULL.write(TEAM)));
        assertEquals(List.of(), warnings);
    }

    @Test
    void anEmptyTagReadsEveryDefault() {
        assertEquals(TeamFields.DEFAULTS, read(new CompoundTag()));
        assertEquals(List.of(), warnings);
    }

    @Test
    void theDefaultsAreZeroAndNotDisbanded() {
        assertEquals(0L, TeamFields.DEFAULTS.resonancePool());
        assertEquals(0, TeamFields.DEFAULTS.levelReached());
        assertEquals(0, TeamFields.DEFAULTS.vaultGeneration());
        assertEquals(OptionalLong.empty(), TeamFields.DEFAULTS.disbandedAt());
    }

    /**
     * Version 1 as E3-S9 (#232) wrote it, before the pool and level existed, built key by key and frozen: the absent-field
     * fixture for both (ADR 0005 section 6). Never edit it to add a key.
     */
    private static CompoundTag e3s9Fixture() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", 1);
        tag.putString("team_id", TEAM.toString());
        tag.putInt("vault_generation", 5);
        return tag;
    }

    @Test
    void theE3S9FixtureReadsAZeroPoolAndLevel() {
        TeamFields fields = read(e3s9Fixture());
        assertEquals(new TeamFields(0L, 0, 5, OptionalLong.empty()), fields);
        assertEquals(List.of(), warnings);
    }

    /** Reading an older tag and saving it adds the two keys at their defaults and changes nothing else. */
    @Test
    void theE3S9FixtureSavesWithTheNewKeysAtTheirDefaults() {
        CompoundTag expected = e3s9Fixture();
        expected.putLong("resonance_pool", 0L);
        expected.putInt("level_reached", 0);
        assertEquals(expected, read(e3s9Fixture()).write(TEAM));
    }

    /** Version 1 as this build writes it, built key by key, so a change of a key's type fails here and not in a world. */
    private static CompoundTag versionOneFixture() {
        CompoundTag tag = e3s9Fixture();
        tag.putLong("resonance_pool", 15_300L);
        tag.putInt("level_reached", 7);
        return tag;
    }

    @Test
    void theVersionOneFixtureWithNoDisbandReadsAndWritesTheSameTag() {
        CompoundTag fixture = versionOneFixture();
        TeamFields fields = read(fixture);
        assertEquals(new TeamFields(15_300L, 7, 5, OptionalLong.empty()), fields);
        assertEquals(fixture, fields.write(TEAM));
    }

    /** A game time past an int's range, so a read or a write narrowed to int cannot pass. */
    @Test
    void theVersionOneFixtureWithADisbandReadsAndWritesTheSameTag() {
        CompoundTag fixture = versionOneFixture();
        fixture.putLong("disbanded_at", 3_000_000_000L);
        TeamFields fields = read(fixture);
        assertEquals(new TeamFields(15_300L, 7, 5, OptionalLong.of(3_000_000_000L)), fields);
        assertEquals(fixture, fields.write(TEAM));
    }

    /** Three billion Resonance in thousandths, far past an int, so a pool narrowed to int on read or write cannot pass. */
    @Test
    void aPoolPastAnIntsRangeRoundTripsAsALong() {
        CompoundTag fixture = versionOneFixture();
        fixture.putLong("resonance_pool", 3_000_000_000_000L);
        TeamFields fields = read(fixture);
        assertEquals(3_000_000_000_000L, fields.resonancePool());
        assertEquals(fixture, fields.write(TEAM));
    }

    @Test
    void aTagWithoutThePoolReadsZero() {
        CompoundTag tag = FULL.write(TEAM);
        tag.remove("resonance_pool");
        assertEquals(new TeamFields(0L, 7, 4, OptionalLong.of(1_728_000L)), read(tag));
    }

    @Test
    void aTagWithoutTheLevelReadsZero() {
        CompoundTag tag = FULL.write(TEAM);
        tag.remove("level_reached");
        assertEquals(new TeamFields(15_300L, 0, 4, OptionalLong.of(1_728_000L)), read(tag));
    }

    /** ADR 0005 section 6 names this case: {@code resonance_pool} as a string loads to the default without throwing. */
    @Test
    void aPoolOfTheWrongTypeReadsZero() {
        CompoundTag tag = FULL.write(TEAM);
        tag.putString("resonance_pool", "15300");
        assertEquals(new TeamFields(0L, 7, 4, OptionalLong.of(1_728_000L)), read(tag));
    }

    @Test
    void aLevelOfTheWrongTypeReadsZero() {
        CompoundTag tag = FULL.write(TEAM);
        tag.putString("level_reached", "seven");
        assertEquals(new TeamFields(15_300L, 0, 4, OptionalLong.of(1_728_000L)), read(tag));
    }

    /** Only an NBT edit makes these. The reader clamps before it constructs, so the constructor's check never throws here. */
    @Test
    void aNegativePoolAndLevelReadZeroWithoutThrowing() {
        CompoundTag tag = FULL.write(TEAM);
        tag.putLong("resonance_pool", -1L);
        tag.putInt("level_reached", -3);
        assertEquals(new TeamFields(0L, 0, 4, OptionalLong.of(1_728_000L)), read(tag));
    }

    @Test
    void noTeamFieldsHoldsANegativePoolOrLevel() {
        assertThrows(IllegalArgumentException.class, () -> new TeamFields(-1L, 0, 0, OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> new TeamFields(0L, -1, 0, OptionalLong.empty()));
    }

    /** ADR 0005 section 3: the pool's unit is thousandths of a Resonance. */
    @Test
    void thePoolCountsThousandths() {
        assertEquals(1_000L, TeamFields.THOUSANDTHS_PER_RESONANCE);
    }

    @Test
    void resonanceAddsToThePoolAndLeavesTheRest() {
        TeamFields after = FULL.plusResonance(12_500L).plusResonance(2_800L);
        assertEquals(new TeamFields(30_600L, 7, 4, OptionalLong.of(1_728_000L)), after);
    }

    /** ADR 0005 section 6's case: 20 payments of 0.05 sum to exactly one Resonance. */
    @Test
    void twentyPaymentsOfATwentiethSumToExactlyOne() {
        TeamFields fields = TeamFields.DEFAULTS;
        for (int i = 0; i < 20; i++) {
            fields = fields.plusResonance(50L);
        }
        assertEquals(TeamFields.THOUSANDTHS_PER_RESONANCE, fields.resonancePool());
    }

    /** The pool never goes down: an add of a negative amount is refused. */
    @Test
    void aNegativeAmountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FULL.plusResonance(-1L));
    }

    /**
     * A hand-edited pool near the top of a long must not crash the server at the next orb: the add stops at the ceiling
     * instead of throwing (Architect, #241 sketch C3).
     */
    @Test
    void anAddAtTheCeilingSaturates() {
        TeamFields nearTop = new TeamFields(Long.MAX_VALUE - 5L, 0, 0, OptionalLong.empty());
        assertEquals(Long.MAX_VALUE, nearTop.plusResonance(10L).resonancePool());
        assertEquals(Long.MAX_VALUE, nearTop.plusResonance(Long.MAX_VALUE).resonancePool());
        assertEquals(Long.MAX_VALUE - 1L, nearTop.plusResonance(4L).resonancePool());
    }

    @Test
    void anAddOfZeroChangesNothing() {
        assertEquals(FULL, FULL.plusResonance(0L));
    }

    @Test
    void aTagWithoutTheGenerationReadsZero() {
        CompoundTag tag = new TeamFields(0L, 0, 4, OptionalLong.of(5L)).write(TEAM);
        tag.remove("vault_generation");
        assertEquals(new TeamFields(0L, 0, 0, OptionalLong.of(5L)), read(tag));
    }

    @Test
    void aTagWithoutDisbandedAtIsNotDisbanded() {
        CompoundTag tag = new TeamFields(0L, 0, 4, OptionalLong.of(5L)).write(TEAM);
        tag.remove("disbanded_at");
        assertEquals(new TeamFields(0L, 0, 4, OptionalLong.empty()), read(tag));
    }

    /** A disband at game time zero is a disband: absence means not disbanded, not a value of 0. */
    @Test
    void disbandedAtZeroStaysSet() {
        TeamFields fields = new TeamFields(0L, 0, 0, OptionalLong.of(0L));
        assertEquals(fields, read(fields.write(TEAM)));
    }

    @Test
    void aTagWithoutAVersionReadsAsVersionOne() {
        CompoundTag tag = new TeamFields(0L, 0, 3, OptionalLong.empty()).write(TEAM);
        tag.remove("data_version");
        assertEquals(new TeamFields(0L, 0, 3, OptionalLong.empty()), read(tag));
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
        CompoundTag tag = new TeamFields(0L, 0, 3, OptionalLong.empty()).write(TEAM);
        tag.putString("data_version", "new");
        assertEquals(new TeamFields(0L, 0, 3, OptionalLong.empty()), read(tag));
    }

    @Test
    void aNewerVersionReadsKnownKeysAndWarnsOnce() {
        CompoundTag tag = new TeamFields(0L, 0, 3, OptionalLong.of(7L)).write(TEAM);
        tag.putInt("data_version", 99);
        tag.putString("a_key_from_the_future", "kept out");
        assertEquals(new TeamFields(0L, 0, 3, OptionalLong.of(7L)), read(tag));
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
        CompoundTag tag = new TeamFields(0L, 0, 3, OptionalLong.of(7L)).write(TEAM);
        CompoundTag once = TeamFields.migrate(tag.copy(), 1);
        assertEquals(tag, once);
        assertEquals(once, TeamFields.migrate(once.copy(), 1));
    }

    @Test
    void markingDisbandedKeepsTheGeneration() {
        assertEquals(new TeamFields(0L, 0, 2, OptionalLong.of(50L)), new TeamFields(0L, 0, 2, OptionalLong.empty()).withDisbandedAt(50L));
    }
}
