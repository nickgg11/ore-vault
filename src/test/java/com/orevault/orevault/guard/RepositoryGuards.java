package com.orevault.orevault.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks for failures that produced no error in the first build (lessons doc). Each takes a repository path
 * with forward slashes and the file's text, and returns what it found, so a planted violation can be tested
 * without a planted file.
 */
final class RepositoryGuards {
    private static final List<Pattern> OLD_NAMES = List.of(
            Pattern.compile("\\bResourceLocation\\b"),
            Pattern.compile("\\bGuiGraphics\\b"),
            Pattern.compile("\\bdrawString\\b"),
            Pattern.compile("hasPermission\\(\\s*2\\s*\\)"));
    private static final Pattern WRONG_NULLABLE =
            Pattern.compile("import\\s+(org\\.jetbrains\\.annotations|net\\.neoforged\\.[\\w.]+)\\.Nullable\\s*;");
    private static final Pattern FORGE_TAG = Pattern.compile("\"#?forge:");
    private static final String WRONG_MINEABLE = "data/orevault/tags/block/mineable";
    private static final String JDK_PIN = "org.gradle.java.installations.paths";

    private RepositoryGuards() {
    }

    static List<String> checkJava(String path, String text) {
        List<String> found = new ArrayList<>();
        for (Pattern name : OLD_NAMES) {
            if (name.matcher(text).find()) {
                found.add(path + ": pre-26.1 name " + name.pattern());
            }
        }
        if (WRONG_NULLABLE.matcher(text).find()) {
            found.add(path + ": @Nullable that is not JSpecify's");
        }
        return found;
    }

    static List<String> checkJson(String path, String text) {
        return FORGE_TAG.matcher(text).find() ? List.of(path + ": forge: tag, use c:") : List.of();
    }

    static List<String> checkPath(String path) {
        return path.contains(WRONG_MINEABLE) ? List.of(path + ": tool tags go in data/minecraft") : List.of();
    }

    static List<String> checkBuildFile(String path, String text) {
        return text.contains(JDK_PIN) ? List.of(path + ": machine-local JDK pin") : List.of();
    }

    /** Reviewer gate 7: every access transformer line is preceded by a comment. */
    static List<String> checkAccessTransformer(String path, String text) {
        List<String> found = new ArrayList<>();
        String previous = "";
        int number = 0;
        for (String line : text.split("\\R", -1)) {
            number++;
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !previous.startsWith("#")) {
                found.add(path + ":" + number + ": access transformer line with no comment above it");
            }
            previous = trimmed;
        }
        return found;
    }
}
