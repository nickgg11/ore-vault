package com.orevault.orevault.resonance;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.skill.SkillTree;
import com.orevault.orevault.team.TeamHelper;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Team Resonance pool management (design spec section 4): adds gains scaled by team size
 * with diminishing returns, applies the dynamic level curve, awards skill points and shows
 * a toast to online members on level-up.
 */
public final class ResonanceSystem {
    private ResonanceSystem() {
    }

    public static double effectiveTeamMultiplier(int teamSize) {
        return 1 + (teamSize - 1) * 0.7;
    }

    public static int teamSize(MinecraftServer server, UUID teamId) {
        return TeamHelper.teamById(teamId).map(t -> Math.max(1, t.getMembers().size())).orElse(1);
    }

    /**
     * Adds Resonance to the team pool (applying the team-size multiplier) and credits the
     * source player's lifetime/session stats. Awards skill points via the dynamic curve.
     */
    public static void addResonance(MinecraftServer server, UUID teamId, long rawAmount, UUID sourcePlayer) {
        if (rawAmount <= 0 || teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(server, teamId);
        long scaled = Math.max(1, Math.round(rawAmount * effectiveTeamMultiplier(teamSize(server, teamId))));
        data.addResonance(scaled);
        if (sourcePlayer != null) {
            PlayerStats stats = data.statsFor(sourcePlayer);
            stats.addResonance(scaled);
        }
        int gained = SkillTree.applyResonanceLevels(data);
        if (gained > 0) {
            announceLevelUp(server, teamId, data.resonanceLevel(), gained);
        }
    }

    private static void announceLevelUp(MinecraftServer server, UUID teamId, int newLevel, int pointsGained) {
        TeamHelper.teamById(teamId).ifPresent(team -> {
            Component title = Component.translatable("orevault.toast.resonance_level.title");
            Component msg = Component.translatable("orevault.toast.resonance_level.msg", newLevel, pointsGained);
            for (var player : team.getOnlineMembers()) {
                ModNetwork.showToast(player, title, msg);
            }
        });
    }
}
