package com.orevault.orevault.core.gen;

import java.util.Arrays;
import java.util.Optional;

/**
 * The two dimension types of §3.1. The heights are duplicated from {@code data/orevault/dimension_type/*.json} on purpose
 * (rule 8): the layer config is validated against these numbers, so a file and the code that disagree fail loudly, and a
 * unit test compares them. Change both or neither. The ids are save data in every Vault's type file.
 */
public enum VaultVariant {
    /** Bedrock at Y=0, no deepslate band. */
    BASE("ore_vault", 0, 320, 1),
    /** Bedrock at Y=-64, which opens the deepslate band Y=-63..-1 (§6.1 Vault Expansion), with stone from Y=0. */
    EXPANDED("ore_vault_expanded", -64, 384, 0);

    private static final String NAMESPACE = "orevault:";
    private static final int STONE_TOP_Y = 245;

    private final String path;
    private final int minY;
    private final int height;
    private final int stoneBottomY;

    VaultVariant(String path, int minY, int height, int stoneBottomY) {
        this.path = path;
        this.minY = minY;
        this.height = height;
        this.stoneBottomY = stoneBottomY;
    }

    /** The dimension type's id, as the Vault type file stores it. */
    public String id() {
        return NAMESPACE + path;
    }

    /** The id's path, which also names the variant's layer file. */
    public String path() {
        return path;
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    /** The lowest layer of the stone band, which is every stone layer of this type's stack (ADR 0009). */
    public int stoneBottomY() {
        return stoneBottomY;
    }

    /** The highest layer of the stone band, the same in both types. */
    public int stoneTopY() {
        return STONE_TOP_Y;
    }

    public static Optional<VaultVariant> byId(String id) {
        return Arrays.stream(values()).filter(variant -> variant.id().equals(id)).findFirst();
    }
}
