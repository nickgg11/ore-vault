package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.vault.VaultDoorBlock;
import com.orevault.orevault.vault.VaultFrameBlock;
import com.orevault.orevault.vault.VaultPortalBlock;
import java.util.List;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registrations. {@code registerBlock} sets the id on the properties; without it the block throws inside
 * {@code RegisterEvent}, the registry rolls back and other mods log "unbound value" errors (old #17).
 */
public final class ModBlocks {
    private static final float UNBREAKABLE = -1.0F;
    private static final float BLAST_PROOF = 3_600_000.0F;
    private static final int PORTAL_LIGHT = 11;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreVault.MOD_ID);

    public static final DeferredBlock<VaultFrameBlock> VAULT_FRAME = BLOCKS.registerBlock("vault_frame", VaultFrameBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<VaultDoorBlock> VAULT_DOOR = BLOCKS.registerBlock("vault_door", VaultDoorBlock::new,
            properties -> properties
                    .mapColor(MapColor.WOOD)
                    .strength(UNBREAKABLE, BLAST_PROOF)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable());

    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_COMMON = portal("vault_portal_common", SignetTier.WORN);
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_UNCOMMON =
            portal("vault_portal_uncommon", SignetTier.HEIRLOOM);
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_RARE = portal("vault_portal_rare", SignetTier.FABLED);
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_LEGENDARY =
            portal("vault_portal_legendary", SignetTier.PRIMORDIAL);

    private ModBlocks() {
    }

    private static DeferredBlock<VaultPortalBlock> portal(String name, SignetTier tier) {
        return BLOCKS.registerBlock(name, properties -> new VaultPortalBlock(properties, tier),
                properties -> properties
                        .noCollision()
                        .strength(UNBREAKABLE, BLAST_PROOF)
                        .sound(SoundType.GLASS)
                        .lightLevel(state -> PORTAL_LIGHT)
                        .pushReaction(PushReaction.BLOCK)
                        .noLootTable());
    }

    /** The portal a signet of this tier opens. */
    public static VaultPortalBlock portal(SignetTier tier) {
        return switch (tier) {
            case WORN -> VAULT_PORTAL_COMMON.get();
            case HEIRLOOM -> VAULT_PORTAL_UNCOMMON.get();
            case FABLED -> VAULT_PORTAL_RARE.get();
            case PRIMORDIAL -> VAULT_PORTAL_LEGENDARY.get();
        };
    }

    /** The four portal blocks, Tier 1 first. */
    public static List<VaultPortalBlock> portals() {
        return List.of(SignetTier.values()).stream().map(ModBlocks::portal).toList();
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
