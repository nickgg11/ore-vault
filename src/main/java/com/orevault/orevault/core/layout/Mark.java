package com.orevault.orevault.core.layout;

import java.util.Optional;

/**
 * A mark on a node as the layout file writes it (§6 Node classes and marks; {@code docs/tree-layout-format.md}, Nodes):
 * a rule added to a node of any class. In {@code core.layout} for the same reason as {@link NodeClass}. Whether a
 * {@link #TRADEOFF} is per player or a team vote is code in {@code core.tree}, not layout.
 */
public enum Mark {
    TRADEOFF("tradeoff"),
    EXCLUSIVE("exclusive"),
    REQUIRES("requires");

    private final String token;

    Mark(String token) {
        this.token = token;
    }

    /** The token in the layout file's {@code marks} array. */
    public String token() {
        return token;
    }

    /** The mark whose token is exactly {@code token}, case-sensitive; empty for anything else. */
    public static Optional<Mark> fromToken(String token) {
        for (Mark m : values()) {
            if (m.token.equals(token)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
