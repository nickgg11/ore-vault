package com.orevault.orevault.item;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mod items, including the four Vault Igniter tiers (design spec section 3.3).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreVault.MODID);

    public static final DeferredItem<VaultIgniterItem> VAULT_IGNITER =
            ITEMS.register("vault_igniter", p -> new VaultIgniterItem(p, 1, Rarity.COMMON));
    public static final DeferredItem<VaultIgniterItem> ATTUNED_VAULT_IGNITER =
            ITEMS.register("attuned_vault_igniter", p -> new VaultIgniterItem(p, 2, Rarity.UNCOMMON));
    public static final DeferredItem<VaultIgniterItem> RESONANT_VAULT_IGNITER =
            ITEMS.register("resonant_vault_igniter", p -> new VaultIgniterItem(p, 3, Rarity.RARE));
    public static final DeferredItem<VaultIgniterItem> SOVEREIGN_VAULT_IGNITER =
            ITEMS.register("sovereign_vault_igniter", p -> new VaultIgniterItem(p, 4, Rarity.EPIC));

    public static final DeferredItem<TomeItem> TOME_OF_THE_DEEP_SEAM =
            ITEMS.register("tome_of_the_deep_seam", p -> new TomeItem(p.stacksTo(1)));
    public static final DeferredItem<Item> RESONANCE_CRYSTAL =
            ITEMS.register("resonance_crystal", p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> SOUL_HARVEST =
            ITEMS.register("soul_harvest", p -> new Item(p.rarity(Rarity.RARE)));

    // Block items
    public static final DeferredItem<BlockItem> VAULT_FRAME_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.VAULT_FRAME);
    public static final DeferredItem<BlockItem> DISTURBED_ZONE_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.DISTURBED_ZONE);
    public static final DeferredItem<BlockItem> VAULT_ANCHOR_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.VAULT_ANCHOR);
    public static final DeferredItem<BlockItem> RESONANCE_CRYSTAL_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_CRYSTAL_BLOCK);

    private ModItems() {
    }
}
