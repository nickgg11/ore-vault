package com.orevault.orevault.worldgen;

import java.util.UUID;

import com.orevault.orevault.ore.OreClassifier.Rarity;

/**
 * The §3.1 baseline: how much ore a Vault generates with no skill points spent, and where it sits.
 *
 * <p>Split out of {@link VaultChunkGenerator} because every number here is a balance decision that
 * is invisible in play until it is already wrong, and because the generator itself runs off-thread
 * in a world and cannot be tested. Nothing in this class imports a Minecraft type.</p>
 *
 * <h2>The density</h2>
 *
 * <p>3% of the stone band, roughly six times the vanilla overworld. Stone still dominates and
 * finding a good vein is still an event; the skill tree is what makes a Vault rich, working up
 * toward the 40% stone floor that caps it (§3.1). It is a constant rather than config for the same
 * reason {@link VaultChunkGenerator#MAX_ORE_FRACTION} is — a server owner has no way to know the
 * right value, and a wrong one silently distorts the level curve calibrated against it.</p>
 *
 * <h2>The mix</h2>
 *
 * <p>70% common, 25% uncommon, 5% rare, by block count. These are not a fresh judgement:
 * {@code ResonanceSystem} derives the entire §4.3 level curve from exactly these shares, so the
 * generator reads them from here and so does the curve. Generating a different mix than the curve
 * assumes would misprice every level with nothing in play to show for it.</p>
 *
 * <h2>The depth</h2>
 *
 * <p>An ore's height in a Vault is its height in the overworld, normalized. The classifier already
 * reads each ore's real placement range to decide its rarity, so the same data answers where it
 * belongs — and it answers for modded ore with no per-mod work, which a table of hand-picked bands
 * could never do. Diamond generates low because diamond generates low, not because this file says
 * so.</p>
 */
public final class OreDistribution {

    /** Fraction of the stone band that becomes ore at zero skill points. */
    public static final double STONE_DENSITY = 0.03;

    /**
     * Fraction of the deepslate band that becomes ore.
     *
     * <p>Double the stone band, because §6.1 sells the expanded Vault's deepslate as "the highest
     * ore density in the mod" and Vault Expansion costs a keystone to reach. A band that read the
     * same as the stone above it would make the keystone pay in headroom only.</p>
     */
    public static final double DEEPSLATE_DENSITY = 0.06;

    public static final double COMMON_SHARE = 0.70;
    public static final double UNCOMMON_SHARE = 0.25;
    public static final double RARE_SHARE = 0.05;

    /** Inclusive vein-size bounds per rarity: rarer ore comes in smaller veins. */
    private static final int[] VEIN_SIZE_MIN = {6, 4, 2};
    private static final int[] VEIN_SIZE_MAX = {12, 8, 5};

    /** The overworld's build range, which every ore's placement range is measured against. */
    private static final int OVERWORLD_MIN_Y = -64;
    private static final int OVERWORLD_MAX_Y = 320;

    private OreDistribution() {
    }

    /**
     * The seed one chunk's ore is rolled from.
     *
     * <p>Derived from the team and the chunk coordinates, deliberately <em>not</em> from the world
     * seed. A Vault is regenerated on reset (#93) and its chunks are generated lazily over months;
     * a chunk has to come out the same whenever it is first visited and whichever server start
     * visits it, or a team's map changes under them when a chunk finally loads.</p>
     */
    public static long chunkSeed(UUID teamId, int chunkX, int chunkZ) {
        long seed = teamId.hashCode();
        seed = seed * 31 + chunkX;
        seed = seed * 31 + chunkZ;
        return seed;
    }

    /**
     * Ore blocks to place in a band of {@code volume} blocks.
     *
     * <p>Clamped to {@link VaultChunkGenerator#MAX_ORE_FRACTION} so the 40% stone floor holds
     * whatever density is asked for. §3.1 states that floor as a hard rule rather than a balance
     * value, which means it belongs in the arithmetic and not in a caller's discipline.</p>
     */
    public static int oreBudget(int volume, double density) {
        if (volume <= 0 || density <= 0) {
            return 0;
        }
        double capped = Math.min(density, VaultChunkGenerator.MAX_ORE_FRACTION);
        return (int) (volume * capped);
    }

    /**
     * Picks a rarity from a uniform roll in {@code [0, 1)}.
     *
     * <p>Taking a roll rather than a {@code RandomSource} is what makes the shares testable: the
     * caller owns the determinism, this owns the boundaries.</p>
     */
    public static Rarity rollRarity(double roll) {
        if (roll < COMMON_SHARE) {
            return Rarity.COMMON;
        }
        if (roll < COMMON_SHARE + UNCOMMON_SHARE) {
            return Rarity.UNCOMMON;
        }
        return Rarity.RARE;
    }

    /** Blocks in one vein of the given rarity, from a uniform roll in {@code [0, 1)}. */
    public static int veinSize(Rarity rarity, double roll) {
        int min = VEIN_SIZE_MIN[rarity.ordinal()];
        int max = VEIN_SIZE_MAX[rarity.ordinal()];
        int span = max - min + 1;
        int offset = (int) (Math.min(Math.max(roll, 0.0), 0.999999) * span);
        return min + offset;
    }

    /**
     * Where an ore belongs in a Vault, as a fraction of the mineralizable column: 0 at the bedrock,
     * 1 at the surface.
     *
     * <p>Taken from the midpoint of the ore's own overworld placement range. The midpoint rather
     * than the minimum, because an ore that spans the whole world (iron, copper) should sit in the
     * middle of a Vault rather than at the bottom of it alongside diamond.</p>
     *
     * @param oreMinY the ore's lowest overworld generation height, from the classifier's metrics
     * @param oreMaxY the ore's highest
     */
    public static double normalizedDepth(int oreMinY, int oreMaxY) {
        double midpoint = (oreMinY + oreMaxY) / 2.0;
        double fraction = (midpoint - OVERWORLD_MIN_Y) / (double) (OVERWORLD_MAX_Y - OVERWORLD_MIN_Y);
        return Math.min(1.0, Math.max(0.0, fraction));
    }

    /**
     * The height an ore prefers inside a column running {@code bottom} (inclusive) to {@code top}
     * (exclusive).
     */
    public static int preferredY(double normalizedDepth, int bottom, int top) {
        int span = Math.max(1, top - bottom - 1);
        return bottom + (int) Math.round(normalizedDepth * span);
    }

    /**
     * Forces a height into a band it may have missed.
     *
     * <p>Deep ore in the base dimension type has nowhere to go: its stone band starts at Y=1 and
     * diamond wants Y=-30. Clamping rather than skipping is what keeps deep ore present in a Vault
     * that has no deepslate at all — it piles up at the bottom of the band, which is both the right
     * place for it and what a player expects.</p>
     *
     * @param top exclusive
     */
    public static int clampToBand(int y, int bottom, int top) {
        return Math.min(Math.max(y, bottom), top - 1);
    }
}
