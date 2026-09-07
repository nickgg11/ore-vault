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

        /**
         * Natural stone for {@code PlayerStats#stoneBroken} and Stonecutter's Patience (§6.1).
         *
         * <p>Ships containing {@code #minecraft:base_stone_overworld} — stone, granite, diorite,
         * andesite, tuff and deepslate — and {@code #c:stones}, so modded stone that follows either
         * convention counts with no per-mod work here. A pack can extend the tag in a datapack for
         * anything that follows neither, which is the whole point of counting against a tag rather
         * than a hard-coded list in a kitchen-sink context.</p>
         */
        public static final TagKey<Block> VAULT_STONE = TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(OreVault.MODID, "vault_stone")
        );

        private Blocks() {
        }
    }

    private ModTags() {
    }
}
