package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's creative tab; an item outside it cannot be found in creative or JEI (lessons doc). */
public final class ModCreativeTabs {
    public static final String TITLE_KEY = "itemGroup.orevault";

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OreVault.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORE_VAULT = TABS.register("ore_vault",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable(TITLE_KEY))
                    .icon(() -> ModItems.VAULT_FRAME.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.VAULT_FRAME.get());
                        output.accept(ModItems.CRUDE_VAULT_IGNITER.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
