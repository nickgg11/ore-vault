package com.orevault.orevault.vault;

import com.orevault.orevault.core.session.LoginCheck;
import com.orevault.orevault.core.session.LoginCheck.Facts;
import com.orevault.orevault.core.session.LoginCheck.Outcome;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerAttachment;
import com.orevault.orevault.data.PlayerData;
import com.orevault.orevault.data.PlayerData.InVault;
import com.orevault.orevault.data.TeamFields;
import com.orevault.orevault.team.TeamHelper;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps {@code in_vault} true to where the player is, and runs ADR 0002's login check on it (§11 Dimension-per-Team
 * Registration). Set on entering a Vault and cleared on leaving, watched through the dimension change and the respawn,
 * never through the portal code: a player also leaves by dying and by command. A death exit fires no dimension change
 * ({@code PlayerList#respawn} posts only the respawn event), so the respawn listener clears it, and the login check never
 * moves a player who died inside and respawned outside.
 */
public final class VaultPlayerEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultPlayerEvents.class);

    private VaultPlayerEvents() {
    }

    /** Called once from the mod constructor. Gameplay events, so the game bus (rule 3). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(VaultPlayerEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(VaultPlayerEvents::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(VaultPlayerEvents::onRespawn);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAtLogin(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordWhereTheyAre(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordWhereTheyAre(player);
        }
    }

    /**
     * ADR 0002's login check, then {@code in_vault} brought up to date. {@code PlayerLoggedInEvent} fires at the end of
     * {@code placeNewPlayer}, before the player's first tick, so a player vanilla put at Vault height in the Overworld is
     * moved before they can fall (old #89). Writes no team data.
     */
    public static Outcome checkAtLogin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Optional<InVault> recorded = PlayerAttachment.get(player).inVault();
        Optional<UUID> levelTeam = VaultDimensions.teamOf(player.level().dimension());
        TeamFields levelTeamFields = levelTeam.map(team -> OreVaultTeamData.read(server, team)).orElse(TeamFields.DEFAULTS);
        Outcome outcome = LoginCheck.decide(new Facts(
                recorded.map(InVault::team).orElse(null), recorded.map(InVault::generation).orElse(0),
                levelTeam.orElse(null), levelTeamFields.disbandedAt().isPresent(), levelTeamFields.vaultGeneration(),
                TeamHelper.teamId(player)));
        boolean moved = switch (outcome) {
            case SEND_HOME -> {
                LOGGER.info("{} logged in recorded as inside the Vault of team {}, which is not where they are ({}); sent to "
                        + "their return position", player.getGameProfile().name(), recorded.map(InVault::team).orElse(null),
                        player.level().dimension().identifier());
                yield VaultTravel.sendHome(player, null);
            }
            case SEND_HOME_DISBANDED -> {
                LOGGER.info("{} logged in inside the Vault of disbanded team {}; sent home", player.getGameProfile().name(),
                        levelTeam.orElse(null));
                yield VaultTravel.sendHome(player, VaultTravel.SENT_HOME_DISBANDED);
            }
            case SEND_HOME_NOT_THEIR_TEAM -> {
                LOGGER.info("{} logged in inside the Vault of team {}, which is not their team; sent home",
                        player.getGameProfile().name(), levelTeam.orElse(null));
                yield VaultTravel.sendHome(player, VaultTravel.SENT_HOME_NOT_THEIR_TEAM);
            }
            case TO_ANCHOR -> {
                LOGGER.info("{} logged in inside the Vault of team {}, reset since they entered; placed at the anchor",
                        player.getGameProfile().name(), levelTeam.orElse(null));
                yield VaultTravel.toAnchor(player, player.level());
            }
            case STAY -> true;
        };
        if (!moved) {
            // in_vault is kept, so the next login tries again rather than leaving them where the check found them.
            LOGGER.warn("{} could not be moved at login ({}); in_vault is kept for the next login", player.getGameProfile().name(),
                    outcome);
            return outcome;
        }
        recordWhereTheyAre(player);
        return outcome;
    }

    /**
     * Sets {@code in_vault} to the Vault the player stands in, with its team's generation, or clears it outside one. An
     * entry already naming this Vault keeps its generation: only entering, or the anchor placement after a reset, is a new
     * entry. Writes no team data.
     */
    public static void recordWhereTheyAre(ServerPlayer player) {
        ServerLevel level = player.level();
        Optional<UUID> team = VaultDimensions.teamOf(level.dimension());
        PlayerData data = PlayerAttachment.get(player);
        if (team.isEmpty()) {
            if (data.inVault().isPresent()) {
                PlayerAttachment.update(player, PlayerData::withoutInVault);
            }
            return;
        }
        int generation = OreVaultTeamData.read(level.getServer(), team.get()).vaultGeneration();
        Optional<InVault> current = data.inVault();
        if (current.isEmpty() || !current.get().team().equals(team.get()) || current.get().generation() < generation) {
            PlayerAttachment.update(player, d -> d.withInVault(new InVault(team.get(), generation)));
        }
    }
}
