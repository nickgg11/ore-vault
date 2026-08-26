package com.orevault.orevault.animus;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.skill.SkillTree;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Team Animus pool management: parallel to Resonance, driven by mob kills in Disturbed
 * Zones (design spec section 5).
 */
public final class AnimusSystem {
    private AnimusSystem() {
    }

    public static void addAnimus(MinecraftServer server, UUID teamId, long rawAmount, UUID sourcePlayer) {
        if (rawAmount <= 0 || teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(server, teamId);
        data.addAnimus(rawAmount);
        if (sourcePlayer != null) {
            PlayerStats stats = data.statsFor(sourcePlayer);
            stats.addAnimus(rawAmount);
        }
        int gained = SkillTree.applyAnimusLevels(data);
        if (gained > 0) {
            announceLevelUp(server, teamId, data.animusLevel(), gained);
        }
    }

    private static void announceLevelUp(MinecraftServer server, UUID teamId, int newLevel, int pointsGained) {
        TeamHelper.teamById(teamId).ifPresent(team -> {
            Component title = Component.translatable("orevault.toast.animus_level.title");
            Component msg = Component.translatable("orevault.toast.animus_level.msg", newLevel, pointsGained);
            for (var player : team.getOnlineMembers()) {
                ModNetwork.showToast(player, title, msg);
            }
        });
    }
}
