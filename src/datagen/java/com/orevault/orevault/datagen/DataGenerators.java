package com.orevault.orevault.datagen;

import com.mojang.math.Quadrant;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.frame.DoorPart;
import com.orevault.orevault.core.frame.FrameCorner;
import com.orevault.orevault.core.frame.FrameLight;
import com.orevault.orevault.core.frame.FrameNeighbours;
import com.orevault.orevault.core.frame.FrameNeighbours.Face;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModItems;
import com.orevault.orevault.registry.ModTags;
import com.orevault.orevault.vault.VaultDoorBlock;
import com.orevault.orevault.vault.VaultOpenings;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
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
import net.neoforged.neoforge.client.model.generators.template.FaceBuilder;
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
        private static final TextureSlot RIM_TREAD_STRAND = TextureSlot.create("tread_strand");
        private static final TextureSlot RIM_RISER_STRAND = TextureSlot.create("riser_strand");
        private static final TextureSlot DOOR_FRONT = TextureSlot.create("front");
        private static final TextureSlot DOOR_BACK = TextureSlot.create("back");
        private static final TextureSlot DOOR_EDGE = TextureSlot.create("edge");
        private static final TextureSlot PORTAL = TextureSlot.create("portal");
        private static final Map<Face, TextureSlot> STRAND_SLOTS = strandSlots();
        private static final String STRAND = "_strand";
        private static final int STRAND_TINT = 0;
        private static final int SLAB_FRONT = 6;
        private static final int SLAB_BACK = 10;

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
            ModelTemplate cube = frameCube();
            for (FrameNeighbours neighbours : FrameNeighbours.all()) {
                Identifier model = FrameCorner.of(neighbours)
                        .map(corner -> cornerModel(frame, corner, blockModels))
                        .orElseGet(() -> cube.createWithSuffix(frame, neighbours.modelSuffix(),
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
            // The strands would draw white with no tint for index 0; the block model names the lone block.
            blockModels.registerSimpleTintedItemModel(frame, ModelLocationUtils.getModelLocation(frame),
                    ItemModelUtils.constantTint(FrameLight.DORMANT));
        }

        private static Map<Face, TextureSlot> strandSlots() {
            Map<Face, TextureSlot> slots = new EnumMap<>(Face.class);
            for (Face face : Face.values()) {
                slots.put(face, TextureSlot.create(face.name().toLowerCase(Locale.ROOT) + STRAND));
            }
            return slots;
        }

        /** A cube with the strands as a second, tinted layer over it, as {@code grass_block} has its side overlay. */
        private static ModelTemplate frameCube() {
            ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                    .parent(Identifier.withDefaultNamespace("block/block"))
                    .requiredTextureSlot(TextureSlot.PARTICLE);
            for (Face face : Face.values()) {
                template.requiredTextureSlot(faceSlot(face)).requiredTextureSlot(STRAND_SLOTS.get(face));
            }
            template.element(builder -> cubeLayer(builder, false));
            template.element(builder -> cubeLayer(builder, true));
            return template.build();
        }

        private static void cubeLayer(ElementBuilder builder, boolean strands) {
            builder.from(0, 0, 0).to(16, 16, 16);
            for (Face face : Face.values()) {
                Direction direction = direction(face);
                builder.face(direction, faceBuilder -> {
                    faceBuilder.texture(strands ? STRAND_SLOTS.get(face) : faceSlot(face)).cullface(direction);
                    if (strands) {
                        faceBuilder.tintindex(STRAND_TINT);
                    }
                });
            }
        }

        /** The boxes of the curve (§3.2). A face in the ring's plane keeps the slot a cube's face has in this state. */
        private static Identifier cornerModel(Block frame, FrameCorner corner, BlockModelGenerators blockModels) {
            ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                    .parent(Identifier.withDefaultNamespace("block/block"))
                    .requiredTextureSlot(TextureSlot.PARTICLE)
                    .requiredTextureSlot(RIM_TREAD)
                    .requiredTextureSlot(RIM_RISER);
            template.requiredTextureSlot(RIM_TREAD_STRAND).requiredTextureSlot(RIM_RISER_STRAND);
            corner.elements().getFirst().faces().stream()
                    .filter(face -> face.surface() == FrameCorner.Surface.PLANE)
                    .forEach(face -> template.requiredTextureSlot(faceSlot(face.face()))
                            .requiredTextureSlot(STRAND_SLOTS.get(face.face())));
            corner.elements().forEach(element -> template.element(builder -> cornerElement(builder, element, false)));
            corner.elements().forEach(element -> template.element(builder -> cornerElement(builder, element, true)));
            FrameNeighbours neighbours = corner.neighbours();
            TextureMapping textures = frameTextures(frame, neighbours)
                    .put(RIM_TREAD, TextureMapping.getBlockTexture(frame, corner.treadTexture()))
                    .put(RIM_RISER, TextureMapping.getBlockTexture(frame, corner.riserTexture()))
                    .put(RIM_TREAD_STRAND, TextureMapping.getBlockTexture(frame, corner.treadTexture() + STRAND))
                    .put(RIM_RISER_STRAND, TextureMapping.getBlockTexture(frame, corner.riserTexture() + STRAND));
            return template.build().createWithSuffix(frame, neighbours.modelSuffix(), textures, blockModels.modelOutput);
        }

        private static void cornerElement(ElementBuilder builder, FrameCorner.Element element, boolean strands) {
            FrameCorner.Box box = element.box();
            builder.from(box.fromX(), box.fromY(), box.fromZ()).to(box.toX(), box.toY(), box.toZ());
            for (FrameCorner.ElementFace face : element.faces()) {
                Direction direction = direction(face.face());
                builder.face(direction, faceBuilder -> {
                    faceBuilder.texture(switch (face.surface()) {
                        case PLANE -> strands ? STRAND_SLOTS.get(face.face()) : faceSlot(face.face());
                        case TREAD -> strands ? RIM_TREAD_STRAND : RIM_TREAD;
                        case RISER -> strands ? RIM_RISER_STRAND : RIM_RISER;
                    }).uvs(face.u0(), face.v0(), face.u1(), face.v1());
                    if (strands) {
                        faceBuilder.tintindex(STRAND_TINT);
                    }
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
            TextureMapping textures = new TextureMapping();
            for (Face face : Face.values()) {
                textures.put(STRAND_SLOTS.get(face),
                        TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(face) + STRAND));
            }
            return textures
                    .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(frame))
                    .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.NORTH)))
                    .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.SOUTH)))
                    .put(TextureSlot.EAST, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.EAST)))
                    .put(TextureSlot.WEST, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.WEST)))
                    .put(TextureSlot.UP, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.UP)))
                    .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(frame, neighbours.textureSuffix(Face.DOWN)));
        }

        /**
         * One model per part. Column 0 is on a viewer's left from the south face and, on the Z axis, from the west one, which
         * is where a quarter turn takes it. The back shows {@link DoorPart#back()}, so the door reads from both sides.
         */
        private static void door(BlockModelGenerators blockModels) {
            Block door = ModBlocks.VAULT_DOOR.get();
            Map<Integer, Identifier> models = new HashMap<>();
            for (DoorPart part : DoorPart.values()) {
                models.put(part.id(), doorModel(door, part, blockModels));
            }
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(door).with(
                    PropertyDispatch.initial(VaultOpenings.AXIS, VaultDoorBlock.PART)
                            .generate((axis, id) -> onAxis(axis, models.get(id)))));
        }

        private static MultiVariant onAxis(Direction.Axis axis, Identifier model) {
            MultiVariant variant = BlockModelGenerators.plainVariant(model);
            return axis == Direction.Axis.X ? variant : variant.with(BlockModelGenerators.Y_ROT_90);
        }

        private static Identifier doorModel(Block door, DoorPart part, BlockModelGenerators blockModels) {
            ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                    .parent(Identifier.withDefaultNamespace("block/block"))
                    .requiredTextureSlot(TextureSlot.PARTICLE)
                    .requiredTextureSlot(DOOR_FRONT)
                    .requiredTextureSlot(DOOR_BACK)
                    .requiredTextureSlot(DOOR_EDGE);
            template.element(builder -> {
                builder.from(0, 0, SLAB_FRONT).to(16, 16, SLAB_BACK);
                leaf(builder, Direction.SOUTH, DOOR_FRONT, part);
                leaf(builder, Direction.NORTH, DOOR_BACK, part.back());
                builder.face(Direction.WEST, face -> edge(face, Direction.WEST));
                builder.face(Direction.EAST, face -> edge(face, Direction.EAST));
                builder.face(Direction.UP, face -> edge(face, Direction.UP).rotation(Quadrant.R90));
                builder.face(Direction.DOWN, face -> edge(face, Direction.DOWN).rotation(Quadrant.R90));
            });
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.PARTICLE, doorTile(door, DoorPart.FIELD_LEFT))
                    .put(DOOR_FRONT, doorTile(door, part))
                    .put(DOOR_BACK, doorTile(door, part.back()))
                    .put(DOOR_EDGE, TextureMapping.getBlockTexture(door, "_edge"));
            return template.build().createWithSuffix(door, "_" + part.name().toLowerCase(Locale.ROOT), textures,
                    blockModels.modelOutput);
        }

        private static void leaf(ElementBuilder builder, Direction side, TextureSlot slot, DoorPart part) {
            builder.face(side, face -> {
                face.texture(slot);
                if (part.mirrored()) {
                    face.uvs(16, 0, 0, 16);
                } else {
                    face.uvs(0, 0, 16, 16);
                }
            });
        }

        private static FaceBuilder edge(FaceBuilder face, Direction side) {
            return face.texture(DOOR_EDGE).uvs(SLAB_FRONT, 0, SLAB_BACK, 16).cullface(side);
        }

        /** {@code door_field} is {@code vault_door_field}. */
        private static Material doorTile(Block door, DoorPart part) {
            return TextureMapping.getBlockTexture(door, part.tile().substring("door".length()));
        }

        /** One model for all four tiers: two faces, no shade, tinted. The strip is the only texture with partial alpha. */
        private static void portals(BlockModelGenerators blockModels) {
            Material strip = new Material(Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "block/vault_portal"));
            Identifier model = Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "block/vault_portal");
            ExtendedModelTemplateBuilder.builder()
                    .requiredTextureSlot(TextureSlot.PARTICLE)
                    .requiredTextureSlot(PORTAL)
                    .element(builder -> {
                        builder.from(0, 0, SLAB_FRONT).to(16, 16, SLAB_BACK).shade(false);
                        for (Direction side : List.of(Direction.NORTH, Direction.SOUTH)) {
                            builder.face(side, face -> face.texture(PORTAL).uvs(0, 0, 16, 16).tintindex(0));
                        }
                    })
                    .build()
                    .create(model, new TextureMapping().put(TextureSlot.PARTICLE, strip).put(PORTAL, strip),
                            blockModels.modelOutput);
            for (Block portal : ModBlocks.portals()) {
                blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(portal).with(
                        PropertyDispatch.initial(VaultOpenings.AXIS)
                                .generate(axis -> onAxis(axis, model))));
            }
        }

        // The super method generates vanilla's models and then fails on Ore Vault's id filter.
        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
            frame(blockModels);
            door(blockModels);
            portals(blockModels);
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
            Block[] portals = ModBlocks.portals().toArray(Block[]::new);
            tag(ModTags.VAULT_PORTALS).add(portals);
            tag(ModTags.VAULT_OPENINGS).add(ModBlocks.VAULT_DOOR.get()).addTag(ModTags.VAULT_PORTALS);
            // A wither and the dragon ignore hardness, and read only these (ADR 0010).
            tag(BlockTags.WITHER_IMMUNE).add(ModBlocks.VAULT_DOOR.get()).add(portals);
            tag(BlockTags.DRAGON_IMMUNE).add(ModBlocks.VAULT_DOOR.get()).add(portals);
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
