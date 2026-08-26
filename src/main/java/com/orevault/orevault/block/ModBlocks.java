package com.orevault.orevault.block;

import com.orevault.orevault.OreVault;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreVault.MODID);

    public static final DeferredBlock<VaultFrameBlock> VAULT_FRAME = BLOCKS.register("vault_frame", () ->
            new VaultFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL = BLOCKS.register("vault_portal", () ->
            new VaultPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .lightLevel(state -> 11)
                    .sound(SoundType.GLASS)));

    public static final DeferredBlock<DisturbedZoneBlock> DISTURBED_ZONE = BLOCKS.register("disturbed_zone", () ->
            new DisturbedZoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)
                    .sound(SoundType.STONE)));

    public static final DeferredBlock<VaultAnchorBlock> VAULT_ANCHOR = BLOCKS.register("vault_anchor", () ->
            new VaultAnchorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(25.0F, 600.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> RESONANCE_CRYSTAL_BLOCK = BLOCKS.register("resonance_crystal_block", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0F, 9.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)));

    private ModBlocks() {
    }
}
