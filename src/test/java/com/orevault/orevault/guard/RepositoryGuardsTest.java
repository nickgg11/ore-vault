package com.orevault.orevault.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryGuardsTest {
    private static final Path ROOT = Path.of(System.getProperty("orevault.projectDir"));

    @ParameterizedTest
    @ValueSource(strings = {
            "ResourceLocation id;",
            "void render(GuiGraphics graphics) {}",
            "graphics.drawString(font, text, 0, 0, 0);",
            "source.hasPermission(2)",
            "import org.jetbrains.annotations.Nullable;",
            "import net.neoforged.neoforge.common.util.Nullable;",
            "import javax.annotation.Nullable;",
            "@org.jetbrains.annotations.Nullable String name;",
            "Identifier.fromNamespaceAndPath(\"forge\", \"ores\")",
    })
    void javaGuardsCatchThePlantedViolation(String planted) {
        assertFalse(RepositoryGuards.checkJava("A.java", planted).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GuiGraphicsExtractor graphics;",
            "import org.jspecify.annotations.Nullable;",
            "Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)",
            "theme.drawString(graphics, text, 0, 0);",
            "Theme.drawString(graphics, text, 0, 0);",
    })
    void javaGuardsPassTheCurrentNames(String text) {
        assertEquals(List.of(), RepositoryGuards.checkJava("A.java", text));
    }

    @Test
    void forgeTagsAreCaught() {
        assertFalse(RepositoryGuards.checkJson("a.json", "{\"values\": [\"#forge:ores\"]}").isEmpty());
        assertFalse(RepositoryGuards.checkJson("a.json", "{\"tag\": \"forge:ingots\"}").isEmpty());
        assertEquals(List.of(), RepositoryGuards.checkJson("a.json", "{\"values\": [\"#c:ores\"]}"));
    }

    @Test
    void mineableTagInTheModNamespaceIsCaught() {
        assertFalse(RepositoryGuards.checkPath("src/main/resources/data/orevault/tags/block/mineable/pickaxe.json").isEmpty());
        assertFalse(RepositoryGuards.checkPath("src/main/resources/data/orevault/tags/blocks/mineable/pickaxe.json").isEmpty());
        assertFalse(RepositoryGuards.checkPath("src/main/resources/data/forge/tags/block/ores.json").isEmpty());
        assertEquals(List.of(), RepositoryGuards.checkPath("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));
    }

    @Test
    void committedJdkPinIsCaught() {
        assertFalse(RepositoryGuards.checkBuildFile("gradle.properties", "org.gradle.java.installations.paths=E:/jdk").isEmpty());
    }

    @Test
    void accessTransformerLineNeedsACommentDirectlyAbove() {
        assertFalse(RepositoryGuards.checkAccessTransformer("at.cfg", "public a.B c").isEmpty());
        assertEquals(2, RepositoryGuards.checkAccessTransformer("at.cfg", "# E3-S6a\n\npublic a.B c\npublic a.B d").size());
        assertEquals(List.of(), RepositoryGuards.checkAccessTransformer("at.cfg", "# E3-S6a (#237): why\npublic a.B c\n"));
    }

    @Test
    void theRepositoryPassesEveryGuard() throws IOException {
        List<String> found = new ArrayList<>();
        List<Path> scanned = new ArrayList<>();
        for (String tree : List.of("src/main", "src/gametest")) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(tree))) {
                files.filter(Files::isRegularFile).forEach(scanned::add);
            }
        }
        for (Path file : scanned) {
            String path = ROOT.relativize(file).toString().replace('\\', '/');
            found.addAll(RepositoryGuards.checkPath(path));
            if (path.endsWith(".java")) {
                found.addAll(RepositoryGuards.checkJava(path, Files.readString(file)));
            } else if (path.endsWith(".json")) {
                found.addAll(RepositoryGuards.checkJson(path, Files.readString(file)));
            } else if (path.endsWith("accesstransformer.cfg")) {
                found.addAll(RepositoryGuards.checkAccessTransformer(path, Files.readString(file)));
            }
        }
        for (String name : List.of("gradle.properties", "build.gradle", "settings.gradle", "gradle/wrapper/gradle-wrapper.properties")) {
            found.addAll(RepositoryGuards.checkBuildFile(name, Files.readString(ROOT.resolve(name))));
        }
        assertTrue(scanned.size() > 1, "scanned " + scanned);
        assertEquals(List.of(), found);
    }
}
