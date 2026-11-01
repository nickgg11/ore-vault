package com.orevault.orevault.datagen;

import com.mojang.math.Quadrant;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.frame.FrameCorner;
import com.orevault.orevault.core.frame.FrameNeighbours;
import com.orevault.orevault.core.frame.FrameNeighbours.Face;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModItems;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.generators.template.ElementBuilder;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
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
        private static final TextureSlot RIM_TREAD = TextureSlot.create("tread");
        private static final TextureSlot RIM_RISER = TextureSlot.create("riser");

        Models(PackOutput output) {
            super(output, OreVault.MOD_ID);
        }

        /**
         * One model per block state, a cube unless the state rounds off a corner; six properties is one more than a
         * {@code PropertyDispatch} takes.
         */
        private static void frame(BlockModelGenerators blockModels) {
            Block frame = ModBlocks.VAULT_FRAME.get();
            MultiPartGenerator states = MultiPartGenerator.multiPart(frame);
            for (FrameNeighbours neighbours : FrameNeighbours.all()) {
                Identifier model = FrameCorner.of(neighbours)
                        .map(corner -> cornerModel(frame, corner, blockModels))
                        .orElseGet(() -> ModelTemplates.CUBE.createWithSuffix(frame, neighbours.modelSuffix(),
                                frameTextures(frame, neighbours), blockModels.modelOutput));
                states.with(new ConditionBuilder()
                                .term(BlockStateProperties.NORTH, neighbours.north())
                                .term(BlockStateProperties.EAST, neighbours.east())
                                .term(BlockStateProperties.SOUTH, neighbours.south())
                                .term(BlockStateProperties.WEST, neighbours.west())
                                .term(BlockStateProperties.UP, neighbours.up())
                                .term(BlockStateProperties.DOWN, neighbours.down()),
                        BlockModelGenerators.plainVariant(model));
            }
            blockModels.blockStateOutput.accept(states);
        }

        /** The boxes of the curve (§3.2). A face in the ring's plane keeps the slot a cube's face has in this state. */
        private static Identifier cornerModel(Block frame, FrameCorner corner, BlockModelGenerators blockModels) {
            ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                    .parent(Identifier.withDefaultNamespace("block/block"))
                    .requiredTextureSlot(TextureSlot.PARTICLE)
                    .requiredTextureSlot(RIM_TREAD)
                    .requiredTextureSlot(RIM_RISER);
            corner.elements().getFirst().faces().stream()
                    .filter(face -> face.surface() == FrameCorner.Surface.PLANE)
                    .forEach(face -> template.requiredTextureSlot(faceSlot(face.face())));
            corner.elements().forEach(element -> template.element(builder -> cornerElement(builder, element)));
            FrameNeighbours neighbours = corner.neighbours();
            TextureMapping textures = frameTextures(frame, neighbours)
                    .put(RIM_TREAD, TextureMapping.getBlockTexture(frame, corner.treadTexture()))
                    .put(RIM_RISER, TextureMapping.getBlockTexture(frame, corner.riserTexture()));
            return template.build().createWithSuffix(frame, neighbours.modelSuffix(), textures, blockModels.modelOutput);
        }

        private static void cornerElement(ElementBuilder builder, FrameCorner.Element element) {
            FrameCorner.Box box = element.box();
            builder.from(box.fromX(), box.fromY(), box.fromZ()).to(box.toX(), box.toY(), box.toZ());
            for (FrameCorner.ElementFace face : element.faces()) {
                Direction direction = direction(face.face());
                builder.face(direction, faceBuilder -> {
                    faceBuilder.texture(switch (face.surface()) {
                        case PLANE -> faceSlot(face.face());
                        case TREAD -> RIM_TREAD;
                        case RISER -> RIM_RISER;
                    }).uvs(face.u0(), face.v0(), face.u1(), face.v1());
                    if (face.rotation() != 0) {
                        faceBuilder.rotation(quadrant(face.rotation()));
                    }
                    if (face.culled()) {
                        faceBuilder.cullface(direction);
                    }
                });
            }
        }

        private static Quadrant quadrant(int degrees) {
            return switch (degrees) {
                case 90 -> Quadrant.R90;
                case 180 -> Quadrant.R180;
                case 270 -> Quadrant.R270;
                default -> Quadrant.R0;
            };
        }

        private static TextureSlot faceSlot(Face face) {
            return switch (face) {
                case NORTH -> TextureSlot.NORTH;
                case SOUTH -> TextureSlot.SOUTH;
                case EAST -> TextureSlot.EAST;
                case WEST -> TextureSlot.WEST;
                case UP -> TextureSlot.UP;
                case DOWN -> TextureSlot.DOWN;
            };
        }

        private static Direction direction(Face face) {
            return switch (face) {
                case NORTH -> Direction.NORTH;
                case SOUTH -> Direction.SOUTH;
                case EAST -> Direction.EAST;
                case WEST -> Direction.WEST;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
        }

        private static TextureMapping frameTextures(Block frame, FrameNeighbours neighbours) {
            return new TextureMapping()
                    .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(frame))
                    .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.NORTH)))
                    .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.SOUTH)))
                    .put(TextureSlot.EAST, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.EAST)))
                    .put(TextureSlot.WEST, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.WEST)))
                    .put(TextureSlot.UP, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.UP)))
                    .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.DOWN)));
        }

        // The super method generates vanilla's models and then fails on Ore Vault's id filter.
        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
            frame(blockModels);
            itemModels.generateFlatItem(ModItems.WORN_SIGNET.get(), ModelTemplates.FLAT_ITEM);
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
                    .requires(Items.STONE_BRICKS, 8)
                    .requires(Items.LAPIS_LAZULI)
                    .unlockedBy("has_stone_bricks", has(Items.STONE_BRICKS))
                    .save(output);
            shaped(RecipeCategory.TOOLS, ModItems.WORN_SIGNET.get())
                    .define('I', Items.IRON_INGOT)
                    .define('R', Items.REDSTONE)
                    .pattern("IR")
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
