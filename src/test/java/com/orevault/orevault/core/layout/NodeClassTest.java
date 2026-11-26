package com.orevault.orevault.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The {@code class} tokens of {@code docs/tree-layout-format.md} (Nodes table), exactly and in its order. */
class NodeClassTest {
    @Test
    void tokensAreTheFormatDocsList() {
        assertEquals(List.of("start", "small", "travel", "notable", "keystone", "pact", "growth", "fork_parent"),
                Arrays.stream(NodeClass.values()).map(NodeClass::token).toList());
    }

    @Test
    void fromTokenRoundTrips() {
        for (NodeClass c : NodeClass.values()) {
            assertEquals(Optional.of(c), NodeClass.fromToken(c.token()));
        }
    }

    @Test
    void fromTokenRefusesUnknownAndWrongCase() {
        assertTrue(NodeClass.fromToken("Small").isEmpty());
        assertTrue(NodeClass.fromToken("SMALL").isEmpty());
        assertTrue(NodeClass.fromToken("fork parent").isEmpty());
        assertTrue(NodeClass.fromToken("FORK_PARENT").isEmpty());
        assertTrue(NodeClass.fromToken("").isEmpty());
        assertTrue(NodeClass.fromToken("option").isEmpty());
    }
}
