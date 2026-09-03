package com.orevault.orevault.event;

import java.util.UUID;

import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.ore.OreClassifier.Rarity;
import com.orevault.orevault.worldgen.VaultChunkGenerator;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import org.jspecify.annotations.Nullable;

/**
 * Everything a Vault block break resolves to, worked out once (§11).
 *
 * <p>The drop pipeline and the Resonance award both need the same answers —
 * which Vault, whose team, what rarity, player or machine — and both would
 * otherwise compute them from the same event independently. Two derivations of
 * "is this a machine" is how the two end up disagreeing about a block, so this
 * is built once by {@link DropPipeline} and handed to everything downstream.</p>
 *
 * @param machineBroken true when nothing, or nothing player-shaped, broke the
 *                      block. Machine breaks still run the pipeline and still
 *                      progress the world; they award no Resonance (§4.2).
 * @param rarity        {@code null} when the block is not a classified ore
 */
public record VaultBreakContext(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        ItemStack tool,
        @Nullable Entity breaker,
        @Nullable ServerPlayer player,
        boolean machineBroken,
        UUID teamId,
        VaultChunkGenerator.SkillSnapshot skills,
        @Nullable Rarity rarity) {

    /**
     * Resolves a break inside a Vault, or {@code null} when the event is not one
     * this mod acts on — outside a Vault, or in a Vault whose team cannot be
     * determined.
     */
    static @Nullable VaultBreakContext of(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        if (!VaultDimensions.isVaultDimension(level)) {
            return null;
        }
        UUID teamId = VaultDimensions.teamIdFor(level);
        if (teamId == null) {
            return null;
        }

        Entity breaker = event.getBreaker();
        ServerPlayer player = breaker instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        // A fake player is still a Player, and is how most mod machines break
        // blocks. Treating it as a machine is deliberate but not detectable here;
        // it needs the runtime mod detection from the SoftDeps ticket.
        boolean machineBroken = !(breaker instanceof Player);

        return new VaultBreakContext(
                level,
                event.getPos(),
                event.getState(),
                event.getTool(),
                breaker,
                player,
                machineBroken,
                teamId,
                VaultDimensions.skillSnapshot(teamId),
                OreClassifier.isClassifiedOre(event.getState()) ? OreClassifier.getRarity(event.getState()) : null);
    }

    /** True when the broken block is a classified ore. */
    public boolean isOre() {
        return rarity != null;
    }

    /** True when the owning team has invested in the named Resonance node. */
    public boolean hasResonanceNode(String nodeId) {
        return skills.resonanceNodes().contains(nodeId);
    }
}
