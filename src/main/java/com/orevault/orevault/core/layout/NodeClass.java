package com.orevault.orevault.core.layout;

import java.util.Optional;

/**
 * A node's class as the layout file writes it (§6 Node classes and marks; {@code docs/tree-layout-format.md}, Nodes).
 * Class decides how a node is drawn (§8). It lives in {@code core.layout}, not {@code core.tree}, because the layout
 * parser (#212) must refuse an unknown class and may not import {@code core.tree} (ADR 0001).
 *
 * <p>{@link #START} is a token of the format, not one of §6's seven classes: the start node has no skill and no effect.
 * Fork options are petals, not nodes, and have no class.
 */
public enum NodeClass {
    /** The format's start node; not a §6 class. */
    START("start"),
    SMALL("small"),
    TRAVEL("travel"),
    NOTABLE("notable"),
    KEYSTONE("keystone"),
    PACT("pact"),
    GROWTH("growth"),
    FORK_PARENT("fork_parent");

    private final String token;

    NodeClass(String token) {
        this.token = token;
    }

    /** The token in the layout file's {@code class} field. */
    public String token() {
        return token;
    }

    /** The class whose token is exactly {@code token}, case-sensitive; empty for anything else. */
    public static Optional<NodeClass> fromToken(String token) {
        for (NodeClass c : values()) {
            if (c.token.equals(token)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
