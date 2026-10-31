package com.orevault.orevault.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureRulesTest {
    private static final Path RESOURCES = Path.of(System.getProperty("orevault.projectDir"), "src/main/resources");
    private static final String BLOCK = "assets/orevault/textures/block/frame.png";

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void aSixteenBySixteenTexturePasses() {
        assertEquals(List.of(), TextureRules.check(BLOCK, image(16, 16), false));
    }

    @Test
    void aPlanted64By64IsCaught() {
        assertFalse(TextureRules.check(BLOCK, image(64, 64), false).isEmpty());
    }

    @Test
    void aStrayFramePixelSizeIsCaught() {
        assertFalse(TextureRules.check(BLOCK, image(16, 20), true).isEmpty());
    }

    @Test
    void anAnimationStripNeedsItsMcmeta() {
        assertFalse(TextureRules.check(BLOCK, image(16, 48), false).isEmpty());
        assertEquals(List.of(), TextureRules.check(BLOCK, image(16, 48), true));
    }

    @Test
    void aPlantedSemiTransparentPixelIsCaught() {
        BufferedImage image = image(16, 16);
        image.setRGB(3, 7, 0x80FFFFFF);
        assertFalse(TextureRules.check(BLOCK, image, false).isEmpty());
    }

    @Test
    void thePortalStripMayHavePartialAlpha() {
        BufferedImage image = image(16, 32);
        image.setRGB(3, 7, 0x80FFFFFF);
        assertEquals(List.of(), TextureRules.check(TextureRules.PORTAL_STRIP, image, true));
    }

    @Test
    void fullyTransparentAndOpaquePixelsPass() {
        BufferedImage image = image(16, 16);
        image.setRGB(0, 0, 0xFF102030);
        assertEquals(List.of(), TextureRules.check(BLOCK, image, false));
    }

    @Test
    void onlyBlockAndItemTexturesAreChecked() {
        assertTrue(TextureRules.applies("assets/orevault/textures/item/igniter.png"));
        assertFalse(TextureRules.applies("logo.png"));
        assertFalse(TextureRules.applies("assets/orevault/textures/gui/sprites/tome/page.png"));
    }

    @Test
    void aSemiTransparentPngSurvivesReadingFromDisk(@TempDir Path dir) throws IOException {
        File file = dir.resolve("planted.png").toFile();
        BufferedImage planted = image(16, 16);
        planted.setRGB(1, 1, 0x80FFFFFF);
        ImageIO.write(planted, "png", file);
        assertFalse(TextureRules.check(BLOCK, ImageIO.read(file), false).isEmpty());
    }

    @Test
    void everyCommittedTexturePasses() throws IOException {
        List<String> found = new ArrayList<>();
        List<Path> pngs;
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            pngs = files.filter(file -> file.toString().endsWith(".png")).toList();
        }
        for (Path png : pngs) {
            String path = RESOURCES.relativize(png).toString().replace(File.separatorChar, '/');
            if (TextureRules.applies(path)) {
                boolean hasMcmeta = Files.exists(png.resolveSibling(png.getFileName() + ".mcmeta"));
                found.addAll(TextureRules.check(path, ImageIO.read(png.toFile()), hasMcmeta));
            }
        }
        assertEquals(List.of(), found);
    }
}
