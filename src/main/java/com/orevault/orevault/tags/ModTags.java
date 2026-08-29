package com.orevault.orevault.tags;

import com.orevault.orevault.OreVault;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Custom block tags. The portal interior exists in four tier variants
 * (#84), so everything that recognises "a portal block" checks
 * {@link #VAULT_PORTALS} instead of a single block.
 */
public final class ModTags {

    public static final class Blocks {

        /** All four tier-coloured Ore Vault Portal interior blocks (§3.2, #84). */
        public static final TagKey<Block> VAULT_PORTALS = TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(OreVault.MODID, "vault_portals")
        );

        private Blocks() {
        }
    }

    private ModTags() {
    }
}
