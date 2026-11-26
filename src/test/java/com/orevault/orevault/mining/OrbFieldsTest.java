package com.orevault.orevault.mining;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** The orb's save shape (ADR 0005 section 10, amended for #260): the keys, their defaults, and that nothing throws. */
class OrbFieldsTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    /** Values that cannot be confused with each other, so a swapped key fails. */
    private static final OrbFields FULL = new OrbFields(Optional.of(TEAM), 12_500L, 3, 100);

    private final List<String> warnings = new ArrayList<>();

    private OrbFields read(CompoundTag tag) {
        return OrbFields.read(tag, warnings::add);
    }

    private static CompoundTag without(String key) {
        CompoundTag tag = FULL.write();
        tag.remove(key);
        return tag;
    }

    @Test
    void theGoldenKeySetIsPinned() {
        assertEquals(Set.of("data_version", "team", "value", "orb_count", "age"), FULL.write().keySet());
    }

    @Test
    void anOrbWithNoTeamWritesNoTeam() {
        assertEquals(Set.of("data_version", "value", "orb_count", "age"), OrbFields.DEFAULTS.write().keySet());
    }

    @Test
    void theShapeStartsAtVersionOne() {
        assertEquals(1, FULL.write().getIntOr("data_version", 0));
        assertEquals(1, OrbFields.DATA_VERSION);
    }

    @Test
    void theTeamIsWrittenHyphenated() {
        assertEquals(Optional.of(TEAM.toString()), FULL.write().getString("team"));
    }

    @Test
    void itRoundTrips() {
        assertEquals(FULL, read(FULL.write()));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void anEmptyTagReadsAsTheDefaults() {
        assertEquals(OrbFields.DEFAULTS, read(new CompoundTag()));
        assertEquals(new OrbFields(Optional.empty(), 0L, 1, 0), OrbFields.DEFAULTS);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void eachKeyAbsentReadsItsDefault() {
        assertEquals(new OrbFields(Optional.of(TEAM), 12_500L, 3, 100), read(without("data_version")));
        assertEquals(new OrbFields(Optional.empty(), 12_500L, 3, 100), read(without("team")));
        assertEquals(new OrbFields(Optional.of(TEAM), 0L, 3, 100), read(without("value")));
        assertEquals(new OrbFields(Optional.of(TEAM), 12_500L, 1, 100), read(without("orb_count")));
        assertEquals(new OrbFields(Optional.of(TEAM), 12_500L, 3, 0), read(without("age")));
    }

    @Test
    void aMalformedTeamIsNoTeamAndNeverAThrow() {
        CompoundTag tag = FULL.write();
        tag.putString("team", "not-a-uuid");
        OrbFields fields = assertDoesNotThrow(() -> read(tag));
        assertEquals(Optional.empty(), fields.team());
    }

    /** Planted break: keeping a negative value would pay a negative amount, which the pool refuses by throwing. */
    @Test
    void aNegativeValueReadsAsZero() {
        CompoundTag tag = FULL.write();
        tag.putLong("value", -5L);
        assertEquals(0L, read(tag).value());
    }

    @Test
    void anOrbCountBelowOneReadsAsOne() {
        CompoundTag tag = FULL.write();
        tag.putInt("orb_count", 0);
        assertEquals(1, read(tag).orbCount());
        tag.putInt("orb_count", -4);
        assertEquals(1, read(tag).orbCount());
    }

    @Test
    void theAgeIsClampedToTheLifetime() {
        CompoundTag tag = FULL.write();
        tag.putInt("age", -1);
        assertEquals(0, read(tag).age());
        tag.putInt("age", 1_000_000);
        assertEquals(6000, read(tag).age());
    }

    @Test
    void wrongTypedKeysReadAsDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.put("team", StringTag.valueOf(""));
        tag.putString("value", "lots");
        tag.putString("orb_count", "three");
        tag.putString("age", "old");
        tag.putInt("team_extra", 9);
        assertEquals(OrbFields.DEFAULTS, assertDoesNotThrow(() -> read(tag)));
    }

    @Test
    void aNewerVersionLoadsWhatItKnowsAndWarnsOnce() {
        CompoundTag tag = FULL.write();
        tag.putInt("data_version", 2);
        assertEquals(FULL, read(tag));
        assertEquals(1, warnings.size());
    }

    @Test
    void theRecordRefusesNothingTheReaderCanProduce() {
        assertDoesNotThrow(() -> new OrbFields(Optional.empty(), 0L, 1, 0));
    }

    @Test
    void withersReturnNewFields() {
        assertEquals(new OrbFields(Optional.of(TEAM), 12_500L, 3, 101), FULL.withAge(101));
        assertEquals(new OrbFields(Optional.of(TEAM), 2_000L, 7, 5), FULL.with(2_000L, 7, 5));
    }
}
