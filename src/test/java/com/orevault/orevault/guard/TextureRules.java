package com.orevault.orevault.guard;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The block and item texture rules ({@code CLAUDE.md} Textures). The mod logo sits outside
 * {@code textures/} and tree icons under {@code textures/gui/}, so neither reaches these rules; the icon sizes
 * arrive with E8-S2 (#256).
 */
final class TextureRules {
    static final int SIZE = 16;
    /** The one texture allowed partial alpha (E3-S3, #181). */
    static final String PORTAL_STRIP = "assets/orevault/textures/block/ore_vault_portal.png";

    private TextureRules() {
    }

    static boolean applies(String path) {
        return path.startsWith("assets/orevault/textures/block/") || path.startsWith("assets/orevault/textures/item/");
    }

    static List<String> check(String path, BufferedImage image, boolean hasMcmeta) {
        List<String> found = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != SIZE) {
            found.add(path + ": " + width + " wide, not " + SIZE);
        } else if (height % SIZE != 0) {
            found.add(path + ": " + height + " high, not a multiple of " + SIZE);
        } else if (height != SIZE && !hasMcmeta) {
            found.add(path + ": animation strip with no .mcmeta");
        }
        if (!path.equals(PORTAL_STRIP) && hasPartialAlpha(image)) {
            found.add(path + ": partial alpha");
        }
        return found;
    }

    private static boolean hasPartialAlpha(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) {
                    return true;
                }
            }
        }
        return false;
    }
}
