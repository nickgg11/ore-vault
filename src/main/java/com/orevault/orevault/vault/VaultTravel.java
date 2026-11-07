package com.orevault.orevault.vault;

import com.orevault.orevault.core.frame.PortalExit;
import com.orevault.orevault.data.PlayerAttachment;
import com.orevault.orevault.data.PlayerData.ReturnPos;
import com.orevault.orevault.registry.ModTags;
import com.orevault.orevault.team.TeamHelper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Travel between the Overworld and a team's Vault (§3.2 Teleportation Logic). Entry saves {@code return_pos} only once the
 * teleport has succeeded, in the transition's post step, which {@code ServerPlayer#teleport} runs only after the move; a
 * trip a mod cancels, or one vanilla refuses, leaves the old one (lessons: the first build saved it first and lost a
 * player's way home on a bounced trip).
 *
 * <p>The arrival here is the anchor column at X=0, Z=0 on the generated surface. The return portal, the safe-arrival search
 * and the overlays are E3-S6b's (#244).
 */
public final class VaultTravel {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultTravel.class);
    static final String VAULT_DAMAGED = "message.orevault.vault_damaged";
    static final String SENT_HOME_DISBANDED = "message.orevault.sent_home.disbanded";
    static final String SENT_HOME_NOT_THEIR_TEAM = "message.orevault.sent_home.not_their_team";

    private VaultTravel() {
    }

    /** Every chat line this class sends, for the lang coverage gametest. */
    public static List<String> messageKeys() {
        return List.of(VAULT_DAMAGED, SENT_HOME_DISBANDED, SENT_HOME_NOT_THEIR_TEAM);
    }

    /**
     * The portal block's destination. A fake player goes nowhere: FTB Teams has no team for one, and its phantom id would
     * get a Vault of its own (§3.2).
     */
    public static @Nullable TeleportTransition portalDestination(ServerLevel current, Entity entity, BlockPos portalEntryPos) {
        if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return null;
        }
        if (VaultDimensions.teamOf(current.dimension()).isPresent()) {
            return home(player, TeleportTransition.PLAY_PORTAL_SOUND);
        }
        return intoVault(player, TeamHelper.teamId(player), current, portalEntryPos);
    }

    /**
     * Into the team's Vault, made on its first entry (§3.1). Null, with a chat line, when its folder's type cannot be read:
     * the server log names the file (ADR 0002 creation step 2).
     */
    public static @Nullable TeleportTransition intoVault(ServerPlayer player, UUID teamId, ServerLevel from, BlockPos portalEntryPos) {
        MinecraftServer server = from.getServer();
        Optional<ServerLevel> vault;
        try {
            vault = VaultDimensions.findOrCreate(server, teamId);
        } catch (RuntimeException e) {
            LOGGER.error("Opening the Vault of team {} failed", teamId, e);
            vault = Optional.empty();
        }
        if (vault.isEmpty()) {
            player.sendSystemMessage(Component.translatable(VAULT_DAMAGED));
            return null;
        }
        ReturnPos noted = returnPosition(player, from, portalEntryPos);
        return new TeleportTransition(vault.get(), anchor(vault.get()), Vec3.ZERO, player.getYRot(), player.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND.then(arrived -> {
                    if (arrived instanceof ServerPlayer moved) {
                        PlayerAttachment.update(moved, data -> data.withReturnPos(noted));
                    }
                }));
    }

    /**
     * Back to {@code return_pos}, or to world spawn when there is none or its level is gone (§3.2). A return position in a
     * Vault is never used: one cannot be saved, so it would be a damaged file.
     */
    public static TeleportTransition home(ServerPlayer player, TeleportTransition.PostTeleportTransition after) {
        MinecraftServer server = player.level().getServer();
        return PlayerAttachment.get(player).returnPos().flatMap(pos -> levelOf(server, pos.dim())
                        .map(level -> new TeleportTransition(level, new Vec3(pos.x() + 0.5, pos.y(), pos.z() + 0.5), Vec3.ZERO,
                                player.getYRot(), player.getXRot(), after)))
                .orElseGet(() -> TeleportTransition.createDefault(player, after));
    }

    private static Optional<ServerLevel> levelOf(MinecraftServer server, String dimension) {
        return Optional.ofNullable(Identifier.tryParse(dimension))
                .map(id -> ResourceKey.create(Registries.DIMENSION, id))
                .filter(key -> VaultDimensions.teamOf(key).isEmpty())
                .map(server::getLevel);
    }

    /**
     * Out of a Vault that is no longer the player's to be in, by the login check or a team change (ADR 0002). Clears
     * {@code in_vault} once the teleport went through: a login move inside the Overworld changes no dimension, so nothing
     * else would.
     *
     * @param reason a lang key for the chat line, or null for none
     * @return whether the player was moved
     */
    public static boolean sendHome(ServerPlayer player, @Nullable String reason) {
        boolean moved = player.teleport(home(player, TeleportTransition.DO_NOTHING)) != null;
        if (moved) {
            PlayerAttachment.update(player, data -> data.withoutInVault());
            if (reason != null) {
                player.sendSystemMessage(Component.translatable(reason));
            }
        }
        return moved;
    }

    /** Login case 3: to the team anchor of the Vault they are in, which was reset while they were offline. */
    static boolean toAnchor(ServerPlayer player, ServerLevel vault) {
        return player.teleport(new TeleportTransition(vault, anchor(vault), Vec3.ZERO, player.getYRot(), player.getXRot(),
                TeleportTransition.DO_NOTHING)) != null;
    }

    /** The team anchor (X=0, Z=0), standing on the generated surface. Loads that chunk, generating it on first entry. */
    public static Vec3 anchor(ServerLevel vault) {
        vault.getChunk(0, 0);
        return new Vec3(0.5, vault.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5);
    }

    /** §3.2: walked out along the normal on the side the player came from, past the last portal cell. */
    static ReturnPos returnPosition(ServerPlayer player, ServerLevel from, BlockPos portalEntryPos) {
        Direction.Axis plane = from.getBlockState(portalEntryPos).getOptionalValue(VaultOpenings.AXIS).orElse(Direction.Axis.X);
        Direction.Axis normal = plane == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        int side = PortalExit.side(player.getDeltaMovement().get(normal),
                normal == Direction.Axis.X ? player.getDirection().getStepX() : player.getDirection().getStepZ());
        Direction out = Direction.fromAxisAndDirection(normal,
                side > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        BlockPos start = player.blockPosition();
        BlockPos exit = start.relative(out, PortalExit.stepsOut(steps ->
                from.getBlockState(start.relative(out, steps)).is(ModTags.VAULT_PORTALS)));
        return new ReturnPos(from.dimension().identifier().toString(), exit.getX(), exit.getY(), exit.getZ());
    }
}
