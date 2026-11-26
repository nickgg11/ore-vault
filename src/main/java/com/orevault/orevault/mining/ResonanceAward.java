package com.orevault.orevault.mining;

import com.orevault.orevault.core.curve.TeamScaling;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one path into the team pool (§4.2). The level-up and the sync land here with #274, the gain log with #264 and the
 * Hoarder multiplier with #347. Main thread only: an orb pays inside the collecting player's server tick.
 */
public final class ResonanceAward {
    private ResonanceAward() {
    }

    /**
     * Pays a team. A disbanded team is paid nothing. A team with no file yet is paid, and its file created: that is every
     * team's first orb (ADR 0005 section 10, amended 2026-11-25).
     *
     * <p>The amount is scaled by team size ({@link TeamScaling#poolGainThousandths}), which rounds once, half up, in
     * integers: 1 thousandth on a team of three is 0.4 and adds nothing, on a team of two it is 0.55 and adds 1. An amount
     * that rounds to 0 writes nothing.
     *
     * @param collector the member who touched the orb; never a fake player
     * @param valueThousandths the orb's value before team scaling, never negative
     */
    public static void collect(ServerPlayer collector, UUID team, long valueThousandths) {
        MinecraftServer server = collector.level().getServer();
        if (OreVaultTeamData.read(server, team).disbandedAt().isPresent()) {
            return;
        }
        long scaled = TeamScaling.poolGainThousandths(valueThousandths, TeamHelper.size(team));
        if (scaled == 0L) {
            return;
        }
        OreVaultTeamData.forWrite(server, team).addResonance(scaled);
    }
}
