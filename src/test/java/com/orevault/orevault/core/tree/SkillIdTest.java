package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The form of every {@link SkillId#id()}: the saved and wired form (ADR 0005, ADR 0006), never a reserved id (§6.1). */
class SkillIdTest {
    /** Lower snake case: starts with a letter, no doubled or trailing underscore, at most 64 characters (ADR 0006). */
    static final Pattern LOWER_SNAKE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    static final int MAX_ID = 64;

    @Test
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (SkillId s : SkillId.values()) {
            assertTrue(seen.add(s.id()), "duplicate id " + s.id());
        }
    }

    @Test
    void idsAreLowerSnakeCaseAndShortEnough() {
        for (SkillId s : SkillId.values()) {
            assertTrue(LOWER_SNAKE.matcher(s.id()).matches(), s.id());
            assertTrue(s.id().length() <= MAX_ID, s.id());
        }
    }

    @Test
    void noIdIsReservedOrStart() {
        for (SkillId s : SkillId.values()) {
            assertFalse(ReservedIds.isReserved(s.id()), s.id() + " is reserved");
            assertFalse(s.id().equals("start"), "start is the format's token, not a skill");
        }
    }

    @Test
    void fromIdRoundTrips() {
        for (SkillId s : SkillId.values()) {
            assertEquals(Optional.of(s), SkillId.fromId(s.id()));
        }
    }

    @Test
    void fromIdRefusesReservedUnknownAndConstantNames() {
        for (String reserved : ReservedIds.ALL) {
            assertTrue(SkillId.fromId(reserved).isEmpty(), reserved);
        }
        assertTrue(SkillId.fromId("start").isEmpty());
        assertTrue(SkillId.fromId("ore_xp").isEmpty());
        assertTrue(SkillId.fromId("VEIN_EXPANSION").isEmpty(), "name() is not the id");
        assertTrue(SkillId.fromId("").isEmpty());
    }
}
