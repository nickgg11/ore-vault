package com.orevault.orevault.item;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central item registry for Ore Vault. Extended in place by later tasks
 * ([33] tome, [65] soul harvest).
 *
 * <p>Items are registered via {@code registerItem}/{@code registerSimpleBlockItem},
 * which set the registry id on the {@link net.minecraft.world.item.Item.Properties}
 * before construction — required since 26.1 (items constructed without an id
 * throw {@code NullPointerException: Item id not set}, same as blocks).</p>
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OreVault.MODID);

    /** Igniter tiers (§3.3): one item per tier, behaviour driven by {@code VaultIgniterItem.Tier}. */
    public static final DeferredItem<VaultIgniterItem> VAULT_IGNITER =
            ITEMS.registerItem("vault_igniter", properties -> new VaultIgniterItem(properties, VaultIgniterItem.Tier.CRUDE));
    public static final DeferredItem<VaultIgniterItem> ATTUNED_VAULT_IGNITER =
            ITEMS.registerItem("attuned_vault_igniter", properties -> new VaultIgniterItem(properties, VaultIgniterItem.Tier.ATTUNED));
    public static final DeferredItem<VaultIgniterItem> RESONANT_VAULT_IGNITER =
            ITEMS.registerItem("resonant_vault_igniter", properties -> new VaultIgniterItem(properties, VaultIgniterItem.Tier.RESONANT));
    public static final DeferredItem<VaultIgniterItem> SOVEREIGN_VAULT_IGNITER =
            ITEMS.registerItem("sovereign_vault_igniter", properties -> new VaultIgniterItem(properties, VaultIgniterItem.Tier.SOVEREIGN));

    /**
     * Item form of the Vault Frame block. Formally part of [68] (models/textures),
     * but registered here so the portal can actually be built in survival before
     * the content phase.
     */
    public static final DeferredItem<BlockItem> VAULT_FRAME_ITEM =
            ITEMS.registerSimpleBlockItem("vault_frame", ModBlocks.VAULT_FRAME);

    // ----- creative tab -----

    /**
     * Ore Vault creative tab. Without tab membership items are invisible in
     * the creative menu and JEI (26.1 search tab only indexes tab contents),
     * so this is required for playability even before the content phase.
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OreVault.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OREVAULT_TAB = CREATIVE_TABS.register(
            "orevault",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.orevault"))
                    .icon(() -> new ItemStack(VAULT_IGNITER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(VAULT_FRAME_ITEM.get());
                        output.accept(VAULT_IGNITER.get());
                        output.accept(ATTUNED_VAULT_IGNITER.get());
                        output.accept(RESONANT_VAULT_IGNITER.get());
                        output.accept(SOVEREIGN_VAULT_IGNITER.get());
                    })
                    .build()
    );

    private ModItems() {
    }
}
