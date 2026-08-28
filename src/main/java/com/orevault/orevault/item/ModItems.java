package com.orevault.orevault.item;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.ModBlocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central item registry for Ore Vault. Extended in place by later tasks
 * ([33] tome, [65] soul harvest).
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreVault.MODID);

    /** Igniter tiers (§3.3): one item per tier, behaviour driven by {@code VaultIgniterItem.Tier}. */
    public static final DeferredHolder<Item, VaultIgniterItem> VAULT_IGNITER =
            ITEMS.register("vault_igniter", () -> new VaultIgniterItem(VaultIgniterItem.Tier.CRUDE));
    public static final DeferredHolder<Item, VaultIgniterItem> ATTUNED_VAULT_IGNITER =
            ITEMS.register("attuned_vault_igniter", () -> new VaultIgniterItem(VaultIgniterItem.Tier.ATTUNED));
    public static final DeferredHolder<Item, VaultIgniterItem> RESONANT_VAULT_IGNITER =
            ITEMS.register("resonant_vault_igniter", () -> new VaultIgniterItem(VaultIgniterItem.Tier.RESONANT));
    public static final DeferredHolder<Item, VaultIgniterItem> SOVEREIGN_VAULT_IGNITER =
            ITEMS.register("sovereign_vault_igniter", () -> new VaultIgniterItem(VaultIgniterItem.Tier.SOVEREIGN));

    /**
     * Item form of the Vault Frame block. Formally part of [68] (models/textures),
     * but registered here so the portal can actually be built in survival before
     * the content phase.
     */
    public static final DeferredHolder<Item, BlockItem> VAULT_FRAME_ITEM =
            ITEMS.register("vault_frame", () -> new BlockItem(ModBlocks.VAULT_FRAME.get(), new Item.Properties()));

    private ModItems() {
    }
}
