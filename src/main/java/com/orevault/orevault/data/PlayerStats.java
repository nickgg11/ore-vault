package com.orevault.orevault.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player statistics tracked by the Ore Memory tab. Stored inside the team's SavedData.
 */
public final class PlayerStats {
    public static final Codec<PlayerStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ores", Map.of()).forGetter(p -> p.oresMined),
            Codec.LONG.optionalFieldOf("stone", 0L).forGetter(p -> p.stoneBroken),
            Codec.LONG.optionalFieldOf("blocks", 0L).forGetter(p -> p.totalBlocksBroken),
            Codec.INT.optionalFieldOf("deepest", 255).forGetter(p -> p.deepestY),
            Codec.LONG.optionalFieldOf("time", 0L).forGetter(p -> p.timeInVaultTicks),
            Codec.LONG.optionalFieldOf("res", 0L).forGetter(p -> p.resonanceLifetime),
            Codec.LONG.optionalFieldOf("res_session", 0L).forGetter(p -> p.resonanceSession),
            Codec.LONG.optionalFieldOf("echoes", 0L).forGetter(p -> p.vaultEchoTriggers),
            Codec.LONG.optionalFieldOf("twins", 0L).forGetter(p -> p.twinVeinTriggers),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("kills", Map.of()).forGetter(p -> p.mobsKilled),
            Codec.LONG.optionalFieldOf("animus", 0L).forGetter(p -> p.animusLifetime),
            Codec.LONG.optionalFieldOf("animus_session", 0L).forGetter(p -> p.animusSession),
            Codec.LONG.optionalFieldOf("volatile", 0L).forGetter(p -> p.volatileVeinsTriggers),
            Codec.LONG.optionalFieldOf("pity", 0L).forGetter(p -> p.volatileVeinsPityActivations),
            Codec.LONG.optionalFieldOf("chunks", 0L).forGetter(p -> p.chunksVisited)
    ).apply(i, PlayerStats::new));

    private final Map<String, Long> oresMined = new HashMap<>();
    private long stoneBroken;
    private long totalBlocksBroken;
    private int deepestY = Integer.MAX_VALUE;
    private long timeInVaultTicks;
    private long resonanceLifetime;
    private long resonanceSession;
    private long vaultEchoTriggers;
    private long twinVeinTriggers;
    private final Map<String, Long> mobsKilled = new HashMap<>();
    private long animusLifetime;
    private long animusSession;
    private long volatileVeinsTriggers;
    private long volatileVeinsPityActivations;
    private long chunksVisited;

    // Volatile Veins pity system (transient, resets on logout)
    private transient int volatileTriggerStreak;
    private transient boolean volatileSafeWindow;
    private transient int volatileSafeBlocksRemaining;

    public PlayerStats() {
    }

    public PlayerStats(Map<String, Long> ores, long stone, long blocks, int deepest, long time,
                       long res, long resSession, long echoes, long twins, Map<String, Long> kills,
                       long animus, long animusSession, long volatileTriggers, long pity, long chunks) {
        this.oresMined.putAll(ores);
        this.stoneBroken = stone;
        this.totalBlocksBroken = blocks;
        this.deepestY = deepest;
        this.timeInVaultTicks = time;
        this.resonanceLifetime = res;
        this.resonanceSession = resSession;
        this.vaultEchoTriggers = echoes;
        this.twinVeinTriggers = twins;
        this.mobsKilled.putAll(kills);
        this.animusLifetime = animus;
        this.animusSession = animusSession;
        this.volatileVeinsTriggers = volatileTriggers;
        this.volatileVeinsPityActivations = pity;
        this.chunksVisited = chunks;
    }

    // --- accessors / mutators -------------------------------------------------

    public Map<String, Long> oresMined() {
        return oresMined;
    }

    public void addOre(String oreKey, long amount) {
        oresMined.merge(oreKey, amount, Long::sum);
    }

    public long stoneBroken() {
        return stoneBroken;
    }

    public void addStone(long n) {
        stoneBroken += n;
    }

    public long totalBlocksBroken() {
        return totalBlocksBroken;
    }

    public void addBlocks(long n) {
        totalBlocksBroken += n;
    }

    public int deepestY() {
        return deepestY == Integer.MAX_VALUE ? -1 : deepestY;
    }

    public void trackY(int y) {
        if (y < deepestY) {
            deepestY = y;
        }
    }

    public long timeInVaultTicks() {
        return timeInVaultTicks;
    }

    public void addVaultTime(long ticks) {
        timeInVaultTicks += ticks;
    }

    public long resonanceLifetime() {
        return resonanceLifetime;
    }

    public void addResonance(long n) {
        resonanceLifetime += n;
        resonanceSession += n;
    }

    public long resonanceSession() {
        return resonanceSession;
    }

    public void resetSessionCounters() {
        resonanceSession = 0;
        animusSession = 0;
    }

    public long vaultEchoTriggers() {
        return vaultEchoTriggers;
    }

    public void addEcho() {
        vaultEchoTriggers++;
    }

    public long twinVeinTriggers() {
        return twinVeinTriggers;
    }

    public void addTwinVein() {
        twinVeinTriggers++;
    }

    public Map<String, Long> mobsKilled() {
        return mobsKilled;
    }

    public void addMob(String mobKey, long amount) {
        mobsKilled.merge(mobKey, amount, Long::sum);
    }

    public long animusLifetime() {
        return animusLifetime;
    }

    public void addAnimus(long n) {
        animusLifetime += n;
        animusSession += n;
    }

    public long animusSession() {
        return animusSession;
    }

    public long volatileVeinsTriggers() {
        return volatileVeinsTriggers;
    }

    public void addVolatileTrigger() {
        volatileVeinsTriggers++;
    }

    public long volatileVeinsPityActivations() {
        return volatileVeinsPityActivations;
    }

    public void addPityActivation() {
        volatileVeinsPityActivations++;
    }

    public long chunksVisited() {
        return chunksVisited;
    }

    public void addChunkVisit() {
        chunksVisited++;
    }

    // Volatile Veins pity state (transient)
    public int volatileTriggerStreak() {
        return volatileTriggerStreak;
    }

    public boolean volatileSafeWindow() {
        return volatileSafeWindow;
    }

    public int volatileSafeBlocksRemaining() {
        return volatileSafeBlocksRemaining;
    }

    public void registerVolatileTrigger() {
        volatileTriggerStreak++;
        if (volatileTriggerStreak >= 3) {
            volatileSafeWindow = true;
            volatileSafeBlocksRemaining = 10;
            volatileTriggerStreak = 0;
            addPityActivation();
        }
    }

    public void registerSafeBlock() {
        if (volatileSafeWindow && volatileSafeBlocksRemaining > 0) {
            volatileSafeBlocksRemaining--;
            if (volatileSafeBlocksRemaining == 0) {
                volatileSafeWindow = false;
            }
        }
    }

    public void onLogout() {
        volatileTriggerStreak = 0;
        volatileSafeWindow = false;
        volatileSafeBlocksRemaining = 0;
    }

    // --- NBT serialisation (legacy simple path; SavedData uses codecs) -------

    public CompoundTag writeNbt() {
        CompoundTag tag = new CompoundTag();
        CompoundTag ores = new CompoundTag();
        oresMined.forEach((k, v) -> ores.putLong(k, v));
        tag.put("ores", ores);
        tag.putLong("stone", stoneBroken);
        tag.putLong("blocks", totalBlocksBroken);
        tag.putInt("deepest", deepestY == Integer.MAX_VALUE ? 255 : deepestY);
        tag.putLong("time", timeInVaultTicks);
        tag.putLong("res", resonanceLifetime);
        tag.putLong("res_session", resonanceSession);
        tag.putLong("echoes", vaultEchoTriggers);
        tag.putLong("twins", twinVeinTriggers);
        CompoundTag kills = new CompoundTag();
        mobsKilled.forEach((k, v) -> kills.putLong(k, v));
        tag.put("kills", kills);
        tag.putLong("animus", animusLifetime);
        tag.putLong("animus_session", animusSession);
        tag.putLong("volatile", volatileVeinsTriggers);
        tag.putLong("pity", volatileVeinsPityActivations);
        tag.putLong("chunks", chunksVisited);
        return tag;
    }

    public static PlayerStats readNbt(CompoundTag tag) {
        PlayerStats stats = new PlayerStats();
        CompoundTag ores = tag.getCompoundOrEmpty("ores");
        for (String key : ores.keySet()) {
            stats.oresMined.put(key, ores.getLongOr(key, 0L));
        }
        stats.stoneBroken = tag.getLongOr("stone", 0L);
        stats.totalBlocksBroken = tag.getLongOr("blocks", 0L);
        stats.deepestY = tag.getIntOr("deepest", 0);
        stats.timeInVaultTicks = tag.getLongOr("time", 0L);
        stats.resonanceLifetime = tag.getLongOr("res", 0L);
        stats.resonanceSession = tag.getLongOr("res_session", 0L);
        stats.vaultEchoTriggers = tag.getLongOr("echoes", 0L);
        stats.twinVeinTriggers = tag.getLongOr("twins", 0L);
        CompoundTag kills = tag.getCompoundOrEmpty("kills");
        for (String key : kills.keySet()) {
            stats.mobsKilled.put(key, kills.getLongOr(key, 0L));
        }
        stats.animusLifetime = tag.getLongOr("animus", 0L);
        stats.animusSession = tag.getLongOr("animus_session", 0L);
        stats.volatileVeinsTriggers = tag.getLongOr("volatile", 0L);
        stats.volatileVeinsPityActivations = tag.getLongOr("pity", 0L);
        stats.chunksVisited = tag.getLongOr("chunks", 0L);
        return stats;
    }
}

