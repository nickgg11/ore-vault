package com.orevault.orevault.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The {@code marks} tokens of {@code docs/tree-layout-format.md} (Nodes table), exactly and in its order. */
class MarkTest {
    @Test
    void tokensAreTheFormatDocsList() {
        assertEquals(List.of("tradeoff", "exclusive", "requires"),
                Arrays.stream(Mark.values()).map(Mark::token).toList());
    }

    @Test
    void fromTokenRoundTrips() {
        for (Mark m : Mark.values()) {
            assertEquals(Optional.of(m), Mark.fromToken(m.token()));
        }
    }

    @Test
    void fromTokenRefusesUnknownAndWrongCase() {
        assertTrue(Mark.fromToken("Tradeoff").isEmpty());
        assertTrue(Mark.fromToken("EXCLUSIVE").isEmpty());
        assertTrue(Mark.fromToken("exclusive_group").isEmpty());
        assertTrue(Mark.fromToken("forOption").isEmpty());
        assertTrue(Mark.fromToken("").isEmpty());
    }
}
