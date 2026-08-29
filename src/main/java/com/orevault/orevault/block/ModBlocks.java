package com.orevault.orevault.block;

import com.orevault.orevault.OreVault;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central block registry for Ore Vault. The single {@link #BLOCKS} register is
 * extended in place by later tasks ([27] disturbed zone, [29] anchor).
 *
 * <p>Blocks are registered via {@code registerBlock}, which sets the registry
 * id on the {@link BlockBehaviour.Properties} before construction — required
 * since 26.1 (blocks constructed without an id throw
 * {@code NullPointerException: Block id not set} and roll back the whole
 * registry event).</p>
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreVault.MODID);

    /** Portal frame block (§3.2). */
    public static final DeferredBlock<VaultFrameBlock> VAULT_FRAME = BLOCKS.registerBlock(
            "vault_frame",
            VaultFrameBlock::new,
            properties -> properties
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
    );

    /** Portal interior blocks, one per igniter tier (§3.2, #84): green/blue/purple/orange. */
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_COMMON = portalBlock("vault_portal_common");
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_UNCOMMON = portalBlock("vault_portal_uncommon");
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_RARE = portalBlock("vault_portal_rare");
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL_LEGENDARY = portalBlock("vault_portal_legendary");

    /** Unbreakable, no-collision interior block; colour comes from client-side tinting (§3.2). */
    private static DeferredBlock<VaultPortalBlock> portalBlock(String name) {
        return BLOCKS.registerBlock(
                name,
                VaultPortalBlock::new,
                properties -> properties
                        .noCollision()
                        .strength(-1.0F, 3600000.0F)
                        .lightLevel(state -> 11)
        );
    }

    /** Portal block for an igniter tier (#84): 1=common (green) … 4=legendary (orange). */
    public static DeferredBlock<VaultPortalBlock> portalForTier(int tier) {
        return switch (tier) {
            case 2 -> VAULT_PORTAL_UNCOMMON;
            case 3 -> VAULT_PORTAL_RARE;
            case 4 -> VAULT_PORTAL_LEGENDARY;
            default -> VAULT_PORTAL_COMMON;
        };
    }

    /**
     * Inverse of {@link #portalForTier}: the igniter tier a portal block
     * represents, or 0 if the state is not a portal block. Lets the return
     * portal upgrade itself without storing a separate high-water mark — the
     * placed blocks already record which tier built them (#86).
     */
    public static int tierOfPortal(BlockState state) {
        if (state.is(VAULT_PORTAL_LEGENDARY)) {
            return 4;
        }
        if (state.is(VAULT_PORTAL_RARE)) {
            return 3;
        }
        if (state.is(VAULT_PORTAL_UNCOMMON)) {
            return 2;
        }
        return state.is(VAULT_PORTAL_COMMON) ? 1 : 0;
    }

    private ModBlocks() {
    }
}
