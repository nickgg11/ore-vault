package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.igniter.IgniterTier;
import com.orevault.orevault.vault.VaultIgniterItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registrations. Each item also needs its {@code items/} definition, model and creative tab entry. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreVault.MOD_ID);

    public static final DeferredItem<BlockItem> VAULT_FRAME = ITEMS.registerSimpleBlockItem(ModBlocks.VAULT_FRAME);
    public static final DeferredItem<VaultIgniterItem> CRUDE_VAULT_IGNITER = ITEMS.registerItem("crude_vault_igniter",
            properties -> new VaultIgniterItem(properties, IgniterTier.CRUDE));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
