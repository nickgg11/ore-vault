package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.vaultkey.VaultKeyTier;
import com.orevault.orevault.vault.VaultKeyItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registrations. Each item also needs its {@code items/} definition, model and creative tab entry. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreVault.MOD_ID);

    public static final DeferredItem<BlockItem> VAULT_FRAME = ITEMS.registerSimpleBlockItem(ModBlocks.VAULT_FRAME);
    public static final DeferredItem<VaultKeyItem> CRUDE_VAULT_KEY = ITEMS.registerItem("crude_vault_key",
            properties -> new VaultKeyItem(properties, VaultKeyTier.CRUDE));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
