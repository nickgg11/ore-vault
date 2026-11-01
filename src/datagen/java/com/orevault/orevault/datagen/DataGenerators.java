package com.orevault.orevault.datagen;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.registry.ModBlocks;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Wires the mod's data providers. They live in a source set of their own so none of this ships in the jar, and
 * the output is committed under {@code src/generated/resources}; hand-written JSON drifted from the registries
 * and put the mineable tag in the wrong namespace (old #87). Each block and item story fills these in.
 */
@EventBusSubscriber(modid = OreVault.MOD_ID)
public final class DataGenerators {
    private DataGenerators() {
    }

    @SubscribeEvent
    static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(Models::new);
        event.createProvider(ModBlockTags::new);
        event.createProvider(Recipes.Runner::new);
        event.createProvider(DataGenerators::lootTables);
    }

    private static LootTableProvider lootTables(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        return new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)), lookup);
    }

    private static final class Models extends ModelProvider {
        Models(PackOutput output) {
            super(output, OreVault.MOD_ID);
        }

        // The super method generates vanilla's models and then fails on Ore Vault's id filter.
        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
            blockModels.createTrivialCube(ModBlocks.VAULT_FRAME.get());
        }
    }

    private static final class ModBlockTags extends BlockTagsProvider {
        ModBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, OreVault.MOD_ID);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.VAULT_FRAME.get());
        }
    }

    private static final class BlockLoot extends BlockLootSubProvider {
        BlockLoot(HolderLookup.Provider lookup) {
            super(Set.of(), FeatureFlags.DEFAULT_FLAGS, lookup);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.VAULT_FRAME.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return BuiltInRegistries.BLOCK.listElements()
                    .filter(block -> block.key().identifier().getNamespace().equals(OreVault.MOD_ID))
                    .<Block>map(Holder::value)
                    .toList();
        }
    }

    private static final class Recipes extends RecipeProvider {
        Recipes(HolderLookup.Provider lookup, RecipeOutput output) {
            super(lookup, output);
        }

        @Override
        protected void buildRecipes() {
            shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.VAULT_FRAME.get())
                    .requires(Items.IRON_INGOT, 8)
                    .requires(Items.REDSTONE)
                    .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                    .save(output);
        }

        static final class Runner extends RecipeProvider.Runner {
            Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
                super(output, lookup);
            }

            @Override
            protected RecipeProvider createRecipeProvider(HolderLookup.Provider lookup, RecipeOutput output) {
                return new Recipes(lookup, output);
            }

            @Override
            public String getName() {
                return "Ore Vault recipes";
            }
        }
    }
}
