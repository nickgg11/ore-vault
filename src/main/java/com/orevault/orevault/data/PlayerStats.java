package com.orevault.orevault.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Per-player statistics tracked inside the Vault (§8), the per-player Volatile
 * Veins pity state (§11), and the per-player tradeoff toggle state (§6.1:
 * "toggle state is saved per-player").
 *
 * <p>Serializes to/from NBT via {@link #toNbt()} / {@link #fromNbt(CompoundTag)}.
 * Missing keys decode to zero/empty, so older saves stay forward-compatible.</p>
 */
public final class PlayerStats {

    // ----- ore / block stats (§8) -----
    private final Map<String, Integer> oresMined = new HashMap<>();
    private int stoneBroken;
    private int totalBlocksBroken;
    private int deepestY; // 0 = no block broken yet
    private int chunksExplored;
    private long timeInVaultTicks;

    // ----- Resonance stats (§8) -----
    private long resonanceLifetime;
    private long resonanceSession;
    private int vaultEchoTriggers;
    private int twinVeinsTriggers;

    // ----- mob stats (§8) -----
    private final Map<String, Integer> mobsKilled = new HashMap<>();
    private long animusLifetime;
    private long animusSession;

    // ----- Volatile Veins (§8 counters + §11 pity state) -----
    private int volatileVeinsTriggers;
    private int volatileVeinsPityActivations;
    private int volatileVeinsTriggerStreak;
    private boolean volatileVeinsSafeWindow;
    private int volatileVeinsSafeBlocksRemaining;

    // ----- per-player tradeoff toggles (§6.1) -----
    private final Set<String> activeTradeoffs = new HashSet<>();

    // ----- getters -----

    public Map<String, Integer> getOresMined() {
        return Map.copyOf(oresMined);
    }

    public int getStoneBroken() {
        return stoneBroken;
    }

    public int getTotalBlocksBroken() {
        return totalBlocksBroken;
    }

    public int getDeepestY() {
        return deepestY;
    }

    public int getChunksExplored() {
        return chunksExplored;
    }

    public long getTimeInVaultTicks() {
        return timeInVaultTicks;
    }

    public long getResonanceLifetime() {
        return resonanceLifetime;
    }

    public long getResonanceSession() {
        return resonanceSession;
    }

    public int getVaultEchoTriggers() {
        return vaultEchoTriggers;
    }

    public int getTwinVeinsTriggers() {
        return twinVeinsTriggers;
    }

    public Map<String, Integer> getMobsKilled() {
        return Map.copyOf(mobsKilled);
    }

    public int getTotalMobsKilled() {
        int total = 0;
        for (int count : mobsKilled.values()) {
            total += count;
        }
        return total;
    }

    public long getAnimusLifetime() {
        return animusLifetime;
    }

    public long getAnimusSession() {
        return animusSession;
    }

    public int getVolatileVeinsTriggers() {
        return volatileVeinsTriggers;
    }

    public int getVolatileVeinsPityActivations() {
        return volatileVeinsPityActivations;
    }

    public int getVolatileVeinsTriggerStreak() {
        return volatileVeinsTriggerStreak;
    }

    public boolean isVolatileVeinsSafeWindow() {
        return volatileVeinsSafeWindow;
    }

    public int getVolatileVeinsSafeBlocksRemaining() {
        return volatileVeinsSafeBlocksRemaining;
    }

    public Set<String> getActiveTradeoffs() {
        return Set.copyOf(activeTradeoffs);
    }

    // ----- recording helpers -----

    /** Records one ore block mined (also counts toward total blocks broken). */
    public void recordOreMined(String oreId, int y) {
        oresMined.merge(oreId, 1, Integer::sum);
        totalBlocksBroken++;
        updateDeepestY(y);
    }

    /** Records one stone/deepslate block broken (also counts toward total blocks broken). */
    public void recordStoneBroken(int y) {
        stoneBroken++;
        totalBlocksBroken++;
        updateDeepestY(y);
    }

    /** Records one non-ore, non-stone block broken. */
    public void recordBlockBroken(int y) {
        totalBlocksBroken++;
        updateDeepestY(y);
    }

    public void addChunksExplored(int count) {
        chunksExplored += count;
    }

    public void addTimeInVault(long ticks) {
        timeInVaultTicks += ticks;
    }

    public void addResonance(long amount) {
        resonanceLifetime += amount;
        resonanceSession += amount;
    }

    public void addAnimus(long amount) {
        animusLifetime += amount;
        animusSession += amount;
    }

    public void recordVaultEchoTrigger() {
        vaultEchoTriggers++;
    }

    public void recordTwinVeinsTrigger() {
        twinVeinsTriggers++;
    }

    public void recordMobKill(String mobId) {
        mobsKilled.merge(mobId, 1, Integer::sum);
    }

    public void recordVolatileVeinsTrigger() {
        volatileVeinsTriggers++;
    }

    public void recordVolatileVeinsPityActivation() {
        volatileVeinsPityActivations++;
    }

    // ----- Volatile Veins pity state (manipulated by the node-effect task) -----

    public void setVolatileVeinsTriggerStreak(int streak) {
        this.volatileVeinsTriggerStreak = streak;
    }

    public void setVolatileVeinsSafeWindow(boolean safe) {
        this.volatileVeinsSafeWindow = safe;
    }

    public void setVolatileVeinsSafeBlocksRemaining(int blocks) {
        this.volatileVeinsSafeBlocksRemaining = blocks;
    }

    // ----- per-player tradeoff toggles (§6.1) -----

    public boolean isTradeoffActive(String nodeId) {
        return activeTradeoffs.contains(nodeId);
    }

    /** Toggles a tradeoff on/off; returns the new active state. */
    public boolean toggleTradeoff(String nodeId) {
        if (!activeTradeoffs.remove(nodeId)) {
            activeTradeoffs.add(nodeId);
            return true;
        }
        return false;
    }

    // ----- session reset -----

    /** Resets session-scoped counters (call on player logout / session end). */
    public void resetSession() {
        resonanceSession = 0;
        animusSession = 0;
    }

    // ----- serialization -----

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stone_broken", stoneBroken);
        tag.putInt("total_blocks_broken", totalBlocksBroken);
        tag.putInt("deepest_y", deepestY);
        tag.putInt("chunks_explored", chunksExplored);
        tag.putLong("time_in_vault_ticks", timeInVaultTicks);
        tag.putLong("resonance_lifetime", resonanceLifetime);
        tag.putLong("resonance_session", resonanceSession);
        tag.putInt("vault_echo_triggers", vaultEchoTriggers);
        tag.putInt("twin_veins_triggers", twinVeinsTriggers);
        tag.putLong("animus_lifetime", animusLifetime);
        tag.putLong("animus_session", animusSession);
        tag.putInt("volatile_veins_triggers", volatileVeinsTriggers);
        tag.putInt("volatile_veins_pity_activations", volatileVeinsPityActivations);
        tag.putInt("volatile_veins_trigger_streak", volatileVeinsTriggerStreak);
        tag.putBoolean("volatile_veins_safe_window", volatileVeinsSafeWindow);
        tag.putInt("volatile_veins_safe_blocks_remaining", volatileVeinsSafeBlocksRemaining);
        tag.put("ores_mined", intMapToNbt(oresMined));
        tag.put("mobs_killed", intMapToNbt(mobsKilled));
        ListTag tradeoffs = new ListTag();
        for (String id : activeTradeoffs) {
            tradeoffs.add(StringTag.valueOf(id));
        }
        tag.put("active_tradeoffs", tradeoffs);
        return tag;
    }

    public static PlayerStats fromNbt(CompoundTag tag) {
        PlayerStats stats = new PlayerStats();
        stats.stoneBroken = tag.getIntOr("stone_broken", 0);
        stats.totalBlocksBroken = tag.getIntOr("total_blocks_broken", 0);
        stats.deepestY = tag.getIntOr("deepest_y", 0);
        stats.chunksExplored = tag.getIntOr("chunks_explored", 0);
        stats.timeInVaultTicks = tag.getLongOr("time_in_vault_ticks", 0L);
        stats.resonanceLifetime = tag.getLongOr("resonance_lifetime", 0L);
        stats.resonanceSession = tag.getLongOr("resonance_session", 0L);
        stats.vaultEchoTriggers = tag.getIntOr("vault_echo_triggers", 0);
        stats.twinVeinsTriggers = tag.getIntOr("twin_veins_triggers", 0);
        stats.animusLifetime = tag.getLongOr("animus_lifetime", 0L);
        stats.animusSession = tag.getLongOr("animus_session", 0L);
        stats.volatileVeinsTriggers = tag.getIntOr("volatile_veins_triggers", 0);
        stats.volatileVeinsPityActivations = tag.getIntOr("volatile_veins_pity_activations", 0);
        stats.volatileVeinsTriggerStreak = tag.getIntOr("volatile_veins_trigger_streak", 0);
        stats.volatileVeinsSafeWindow = tag.getBooleanOr("volatile_veins_safe_window", false);
        stats.volatileVeinsSafeBlocksRemaining = tag.getIntOr("volatile_veins_safe_blocks_remaining", 0);
        intMapFromNbt(tag.getCompoundOrEmpty("ores_mined"), stats.oresMined);
        intMapFromNbt(tag.getCompoundOrEmpty("mobs_killed"), stats.mobsKilled);
        for (Tag element : tag.getListOrEmpty("active_tradeoffs")) {
            element.asString().ifPresent(stats.activeTradeoffs::add);
        }
        return stats;
    }

    private static CompoundTag intMapToNbt(Map<String, Integer> map) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            out.putInt(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void intMapFromNbt(CompoundTag tag, Map<String, Integer> into) {
        for (String key : tag.keySet()) {
            into.put(key, tag.getIntOr(key, 0));
        }
    }

    private void updateDeepestY(int y) {
        if (deepestY == 0 || y < deepestY) {
            deepestY = y;
        }
    }
}
