package com.orevault.orevault.mining;

import com.orevault.orevault.core.curve.TeamScaling;
import com.orevault.orevault.core.mining.GainLine;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one path into the team pool (§4.2). The level-up and the sync land here with #274 and the Hoarder multiplier with
 * #347. Main thread only: an orb pays inside the collecting player's server tick.
 */
public final class ResonanceAward {
    private static final Logger LOG = LoggerFactory.getLogger(ResonanceAward.class);
    private static volatile BooleanSupplier logGains = () -> false;

    private ResonanceAward() {
    }

    /**
     * Hands over the reader of {@code debug.log_resonance_gain} (§10), read on each pickup so a change takes effect at
     * once. A supplier because {@code mining} may not import {@code config} (ADR 0001). Called once from the mod constructor.
     */
    public static void logGainsWhen(BooleanSupplier enabled) {
        logGains = enabled;
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
     * @param orbCount the orbs merged into this one, for the gain log
     */
    public static void collect(ServerPlayer collector, UUID team, long valueThousandths, int orbCount) {
        MinecraftServer server = collector.level().getServer();
        int teamSize = TeamHelper.size(team);
        long scaled = OreVaultTeamData.read(server, team).disbandedAt().isPresent()
                ? 0L
                : TeamScaling.poolGainThousandths(valueThousandths, teamSize);
        if (scaled != 0L) {
            OreVaultTeamData.forWrite(server, team).addResonance(scaled);
        }
        if (logGains.getAsBoolean()) {
            LOG.info("{}", new GainLine(team, collector.getUUID(), orbCount, valueThousandths, teamSize, scaled).line());
        }
    }
}
