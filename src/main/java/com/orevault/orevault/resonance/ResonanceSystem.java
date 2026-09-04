package com.orevault.orevault.resonance;

import java.util.UUID;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.skill.LevelCurve;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.NodeDefs;
import com.orevault.orevault.skill.TeamScaling;
import com.orevault.orevault.team.TeamHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.jspecify.annotations.Nullable;

/**
 * Owns the team Resonance pool: accumulation, level-ups and skill-point awards
 * (§4.2, §4.3).
 *
 * <p>The division of labour is deliberate. {@link LevelCurve} owns the
 * threshold maths and is tree-agnostic; {@link TeamScaling} owns the §4.2 team
 * formula; this class owns pool state and the award side-effects. Nothing here
 * re-derives a threshold or a team multiplier — a second copy of either is how
 * the pre-v1.1 curve ended up contradicting the level requirements it was
 * supposed to pace.</p>
 *
 * <p>The curve is computed once at server start, because both of its config
 * inputs are read once there. Recomputing it mid-session would move every
 * threshold under teams that had already passed them.</p>
 */
public final class ResonanceSystem {

    /**
     * Ore blocks a solo player breaks in an hour of active mining inside a
     * Vault.
     *
     * <p>This is a measurement of how fast someone mines, not a preference, so
     * it is a constant rather than config: an admin has no way to know the right
     * value and a wrong one silently distorts the entire curve. The number a
     * server owner actually wants is {@code curve_divisor}, which scales the
     * grind without touching the shape.</p>
     *
     * <p>Derived from a sustained rate of roughly ten ore blocks a minute — an
     * enchanted pickaxe in a dimension generated at up to
     * {@code VaultChunkGenerator.MAX_ORE_FRACTION} ore, with tunnelling and
     * travel included rather than assuming continuous breaking. It is the one
     * number here that wants correcting against a real playtest; until vein
     * placement lands ([44]/[45], #45) there is nothing better to derive it
     * from.</p>
     */
    private static final int SOLO_ORES_PER_HOUR = 600;

    /**
     * Expected Resonance from one ore, weighted by how often each rarity turns
     * up. Values are the §4.2 base rates (rare takes the midpoint of its 10–15
     * band); the shares assume a Vault is mostly common ore, which is what the
     * classifier's count-and-height rule produces for a vanilla ore set.
     */
    private static final double WEIGHTED_RESONANCE_PER_ORE =
            0.70 * 2.0      // common — iron, copper, coal
            + 0.25 * 5.0    // uncommon — gold, lapis, redstone
            + 0.05 * 12.5;  // rare — diamond, emerald

    /** Solo Resonance per hour, feeding §4.3 step 5. */
    static final double SOLO_RESONANCE_PER_HOUR = SOLO_ORES_PER_HOUR * WEIGHTED_RESONANCE_PER_ORE;

    private static volatile @Nullable LevelCurve curve;

    private ResonanceSystem() {
    }

    /** Outcome of a single gain: what the team crossed, and what it was paid. */
    public record LevelUp(int previousLevel, int newLevel, int pointsAwarded) {

        public boolean leveledUp() {
            return newLevel > previousLevel;
        }
    }

    // ----- lifecycle -----

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        curve = LevelCurve.compute(
                NodeDefs.totalTreeCost(Tree.RESONANCE),
                SOLO_RESONANCE_PER_HOUR,
                OreVaultServerConfig.targetPlayHours(),
                OreVaultServerConfig.curveDivisor());
        OreVault.LOGGER.info(
                "Resonance curve: {} levels, {} points each, {} total Resonance over {}h (divisor {})",
                curve.levelCount(), curve.pointsPerLevel(), curve.totalCost(),
                OreVaultServerConfig.targetPlayHours(), OreVaultServerConfig.curveDivisor());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // A single-player client can start a second integrated server in the same
        // JVM with different config; holding the old curve would silently pace it.
        curve = null;
    }

    /**
     * The curve for this server session.
     *
     * @throws IllegalStateException before {@link #onServerStarted} has run
     */
    public static LevelCurve curve() {
        LevelCurve current = curve;
        if (current == null) {
            throw new IllegalStateException("Resonance curve requested before server start");
        }
        return current;
    }

    // ----- readouts (the Tome's progress bar, [35]) -----

    /** Current Resonance level, derived from the pool rather than the cached field. */
    public static int levelOf(OreVaultTeamData data, LevelCurve curve) {
        return curve.levelFor(data.getResonancePool());
    }

    /** Progress (0..1) toward the next level; 1.0 once the cap is reached. */
    public static double progressToNextLevel(OreVaultTeamData data, LevelCurve curve) {
        return curve.progressWithinLevel(data.getResonancePool());
    }

    // ----- gain -----

    /**
     * Adds Resonance to a team's pool, awarding every level it crosses.
     *
     * @param summedMemberGains raw gain before §4.2 team scaling
     */
    public static LevelUp addResonance(MinecraftServer server, UUID teamId, double summedMemberGains) {
        ServerLevel overworld = server.overworld();
        OreVaultTeamData data = OreVaultTeamData.getOrCreate(overworld, teamId);
        LevelUp result = applyGain(data, curve(), summedMemberGains, TeamHelper.teamSize(teamId));
        if (result.leveledUp()) {
            announce(teamId, result);
        }
        // The pool moved, so the Tome's header is now stale on every open client
        // ([34]). Pushed on every gain rather than only on a level-up: the
        // progress bar is the part players watch while mining.
        ModNetwork.syncTeam(server, teamId);
        return result;
    }

    /**
     * Applies a gain to team data and returns what it earned.
     *
     * <p>Split from {@link #addResonance} so the award rules are unit-testable:
     * every Minecraft-side concern (which team, who is online, what they see)
     * lives in the caller.</p>
     *
     * <p>Gains are rounded to the nearest whole point on the way into the pool,
     * which is a long. Rounding to nearest is unbiased across many breaks, and
     * the only fractional source in the design — Stone Memory's 0.5 per stone
     * block (§4.2) — accumulates on the orb-spawning side rather than here, so
     * no fractional remainder needs persisting.</p>
     */
    public static LevelUp applyGain(OreVaultTeamData data, LevelCurve curve, double summedMemberGains, int teamSize) {
        // Level is derived from the pool rather than read from the stored field, which
        // is a cache for display. It also gives the right behaviour when an admin
        // changes curve_divisor between sessions: the team re-levels to match the new
        // curve, and points already awarded are never clawed back.
        int previousLevel = curve.levelFor(data.getResonancePool());

        data.addResonancePool(Math.round(TeamScaling.teamPoolGain(summedMemberGains, teamSize)));

        int newLevel = curve.levelFor(data.getResonancePool());
        if (newLevel <= previousLevel) {
            return new LevelUp(previousLevel, previousLevel, 0);
        }

        // Every level crossed is paid, not just one: a single Vault Echo burst can
        // span several thresholds, and paying one would quietly strand the rest.
        int points = (newLevel - previousLevel) * curve.pointsPerLevel();
        data.setResonanceLevel(newLevel);
        data.addResonanceSkillPoints(points);
        return new LevelUp(previousLevel, newLevel, points);
    }

    /**
     * Tells the team it levelled up.
     *
     * <p>An overlay message, not the §4.3 toast: a real toast is client-side and
     * needs the network channel from [32] (#33). Swap this for a packet when
     * that lands.</p>
     */
    private static void announce(UUID teamId, LevelUp result) {
        Component message = Component.translatable(
                "message.orevault.resonance_level_up", result.newLevel(), result.pointsAwarded());
        for (ServerPlayer player : TeamHelper.getOnlineTeamMembers(teamId)) {
            player.sendOverlayMessage(message);
        }
    }
}
