package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** The mod's block tags (§3.2). The files are generated, under {@code data/orevault/tags/block/}. */
public final class ModTags {
    /** Something you can travel through. Everything that recognises a portal block reads this. */
    public static final TagKey<Block> VAULT_PORTALS = block("vault_portals");

    /** What may fill a valid opening: the sealed door and the portals. */
    public static final TagKey<Block> VAULT_OPENINGS = block("vault_openings");

    private ModTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(OreVault.MOD_ID, path));
    }
}
