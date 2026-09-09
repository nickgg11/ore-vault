package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every node has a name and a description in {@code en_us.json} (#147).
 *
 * <p>Half the tree did not. Names survived it because the Tome falls back to
 * {@link NodeDef#name()}, but a description has nothing to fall back to, so a
 * node registered without one showed the player the literal string
 * {@code node.orevault.whatever.desc} in its tooltip. That is invisible from the
 * code and from the lang file alike — it only shows up on hover, one node at a
 * time — and 30 nodes arrived in a single pass (#137), which is how it got past
 * a playtest.</p>
 *
 * <p>Deliberately a substring check on the raw file rather than a JSON parse.
 * The test source set cannot load Minecraft classes and has no JSON library of
 * its own, and what is being asserted is that the key is present, which the text
 * answers exactly.</p>
 */
class NodeLangTest {

    private static String lang;

    @BeforeAll
    static void loadLang() throws IOException {
        try (InputStream in = NodeLangTest.class.getResourceAsStream(
                "/assets/orevault/lang/en_us.json")) {
            assertTrue(in != null, "en_us.json is not on the test classpath");
            lang = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyNodeHasADescription() {
        assertTrue(NodeDefs.all().size() > 50, "the registry is empty, so this test proves nothing");
        List<String> missing = new ArrayList<>();
        for (NodeDef def : NodeDefs.all()) {
            if (!lang.contains("\"node.orevault." + def.id() + ".desc\"")) {
                missing.add(def.id());
            }
        }
        assertTrue(missing.isEmpty(),
                missing.size() + " node(s) would show a raw lang key on hover: " + missing);
    }

    @Test
    void everyNodeHasAName() {
        List<String> missing = new ArrayList<>();
        for (NodeDef def : NodeDefs.all()) {
            if (!lang.contains("\"node.orevault." + def.id() + "\"")) {
                missing.add(def.id());
            }
        }
        assertTrue(missing.isEmpty(), "no lang name for: " + missing);
    }

    /**
     * A description says what the node does, in numbers a player can act on (#148).
     *
     * <p>The words banned here are the ones that were standing in for a number that
     * had never been chosen: "moderate geode frequency", "occasionally drops flint",
     * "sparse ancient debris", "1-3% (balance TBD)". Each read as a description and
     * was really an open design question, and a player could not tell the difference.
     * They are banned rather than merely discouraged because the failure is silent —
     * the tooltip renders, it just does not answer anything.</p>
     *
     * <p>Qualifying a real number is fine and not what this catches: "about +7% at
     * 100,000 blocks" and "roughly one per 24" both pass, because the number is
     * there to be qualified.</p>
     */
    @Test
    void noDescriptionStandsInForANumberItNeverChose() {
        List<String> hedges = List.of(
                "moderate", "brief", "occasionally", "several", "sparse", "small chance",
                "a small amount", "rare chance", "high frequency", "tbd", "somewhat",
                "various", "a portion of", "strong shift", "a burst of vanilla xp");
        List<String> offenders = new ArrayList<>();
        for (NodeDef def : NodeDefs.all()) {
            String text = description(def.id()).toLowerCase(Locale.ROOT);
            for (String hedge : hedges) {
                if (text.contains(hedge)) {
                    offenders.add(def.id() + " says \"" + hedge + "\"");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "description(s) describing an effect in adjectives instead of numbers: " + offenders);
    }

    /**
     * A one-line flourish is not a description.
     *
     * <p>"The mirror image." was the whole of what Resonant Overload told a player,
     * and it is 17 characters. The floor is crude on purpose — it cannot judge whether
     * a description is any good, only that something was written where a sentence about
     * the effect belongs.</p>
     */
    @Test
    void noDescriptionIsAFlourishInsteadOfAnEffect() {
        List<String> tooShort = new ArrayList<>();
        for (NodeDef def : NodeDefs.all()) {
            String text = description(def.id());
            if (text.length() < 40) {
                tooShort.add(def.id() + " (" + text.length() + " chars)");
            }
        }
        assertTrue(tooShort.isEmpty(), "description(s) too short to state an effect: " + tooShort);
    }

    /** The description text for a node, read straight out of the lang file. */
    private static String description(String nodeId) {
        String key = "\"node.orevault." + nodeId + ".desc\": \"";
        int start = lang.indexOf(key);
        assertTrue(start >= 0, "no description for " + nodeId);
        start += key.length();
        int end = start;
        while (lang.charAt(end) != '"' || lang.charAt(end - 1) == '\\') {
            end++;
        }
        return lang.substring(start, end);
    }

    /**
     * A key for a node that no longer exists is a name nobody will ever read and
     * a hint that a removal was only half done. #137 deleted five nodes and left
     * ten keys behind.
     */
    @Test
    void noLangKeyNamesANodeThatIsGone() {
        List<String> live = new ArrayList<>();
        for (NodeDef def : NodeDefs.all()) {
            live.add(def.id());
        }
        List<String> stale = new ArrayList<>();
        int from = 0;
        String marker = "\"node.orevault.";
        while ((from = lang.indexOf(marker, from)) >= 0) {
            int end = lang.indexOf('"', from + marker.length());
            String key = lang.substring(from + marker.length(), end);
            String id = key.endsWith(".desc") ? key.substring(0, key.length() - ".desc".length()) : key;
            if (!live.contains(id) && !stale.contains(id)) {
                stale.add(id);
            }
            from = end;
        }
        assertTrue(stale.isEmpty(), "lang keys for nodes that are not in the registry: " + stale);
    }
}
