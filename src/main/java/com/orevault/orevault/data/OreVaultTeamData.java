package com.orevault.orevault.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.skill.NodeCosts;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.SkillTree;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Team-scoped SavedData holding all shared progression state (§11): Resonance and
 * Animus pools, levels, skill-point counts, the two skill trees, per-player stats,
 * and the chunk-loading ticket count.
 *
 * <p>One instance exists per FTB team, keyed {@code orevault:team_<teamId>} in the
 * overworld's {@code SavedDataStorage}. Serialization is codec-based (MC 26.1
 * {@code SavedDataType}) and delegates to manual NBT via {@link CompoundTag#CODEC}.</p>
 *
 * <p>All mutators call {@link #setDirty()} so state is flushed on save. If a caller
 * mutates a {@link PlayerStats} returned by {@link #getOrCreatePlayerStats(UUID)}
 * directly, it must call {@link #setDirty()} afterwards.</p>
 */
public final class OreVaultTeamData extends SavedData {

    public static final Codec<OreVaultTeamData> CODEC =
            CompoundTag.CODEC.xmap(OreVaultTeamData::fromNbt, OreVaultTeamData::toNbt);

    /**
     * Schema version for the persisted team tag (§11).
     *
     * <p>Bump this whenever the stored shape changes — a renamed key, a changed
     * unit, a restructured sub-tag — and add the corresponding step to
     * {@link #migrate(CompoundTag, int)}. Version {@code 0} is reserved for the
     * unversioned saves written before versioning existed.</p>
     *
     * <p>This class grows through every remaining phase, so the cost of not
     * having this is a schema change becoming a wipe rather than a migration.</p>
     */
    public static final int CURRENT_DATA_VERSION = 1;

    private static final String DATA_VERSION_KEY = "data_version";

    private final UUID teamId;
    private final SkillTree resonanceTree = new SkillTree(Tree.RESONANCE);
    private final SkillTree animusTree = new SkillTree(Tree.ANIMUS);
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();

    private long resonancePool;
    private long animusPool;
    private int resonanceLevel;
    private int animusLevel;
    private int resonanceSkillPoints;
    private int animusSkillPoints;
    private int chunkLoadingTickets;
    private long freeRespecUntilGameTime;

    public OreVaultTeamData(UUID teamId) {
        this.teamId = teamId;
    }

    // ----- identity -----

    public UUID teamId() {
        return teamId;
    }

    public SkillTree resonanceTree() {
        return resonanceTree;
    }

    public SkillTree animusTree() {
        return animusTree;
    }

    // ----- pools / levels / points -----

    public long getResonancePool() {
        return resonancePool;
    }

    public long getAnimusPool() {
        return animusPool;
    }

    public int getResonanceLevel() {
        return resonanceLevel;
    }

    public int getAnimusLevel() {
        return animusLevel;
    }

    public int getResonanceSkillPoints() {
        return resonanceSkillPoints;
    }

    public int getAnimusSkillPoints() {
        return animusSkillPoints;
    }

    public int getChunkLoadingTickets() {
        return chunkLoadingTickets;
    }

    /** Overworld game time at which the free-respec window closes (0 = never opened). */
    public long getFreeRespecUntilGameTime() {
        return freeRespecUntilGameTime;
    }

    /** Whether node refunds are currently free (§3.5, §4.4). */
    public boolean isFreeRespecActive(long gameTime) {
        return gameTime < freeRespecUntilGameTime;
    }

    // ----- mutators (mark dirty) -----

    public void addResonancePool(long amount) {
        this.resonancePool += amount;
        setDirty();
    }

    public void addAnimusPool(long amount) {
        this.animusPool += amount;
        setDirty();
    }

    public void setResonanceLevel(int level) {
        this.resonanceLevel = level;
        setDirty();
    }

    public void setAnimusLevel(int level) {
        this.animusLevel = level;
        setDirty();
    }

    public void addResonanceSkillPoints(int points) {
        this.resonanceSkillPoints += points;
        setDirty();
    }

    public void addAnimusSkillPoints(int points) {
        this.animusSkillPoints += points;
        setDirty();
    }

    public void setChunkLoadingTickets(int tickets) {
        this.chunkLoadingTickets = tickets;
        setDirty();
    }

    /**
     * Opens the 10-minute free-respec window (§3.5). Called by the dimension
     * reset; a fresh Vault is the natural moment to rebuild a mining strategy.
     *
     * @param gameTime current overworld game time, in ticks
     */
    public void startFreeRespecWindow(long gameTime) {
        this.freeRespecUntilGameTime = gameTime + NodeCosts.FREE_RESPEC_WINDOW_TICKS;
        setDirty();
    }

    /** Unlocks the next tier on the Resonance tree; marks dirty only on success. */
    public SkillTree.UnlockResult unlockResonanceNode(String nodeId, int teamLevel, int availableSkillPoints) {
        return unlockNode(resonanceTree, nodeId, teamLevel, availableSkillPoints);
    }

    /** Unlocks the next tier on the Animus tree; marks dirty only on success. */
    public SkillTree.UnlockResult unlockAnimusNode(String nodeId, int teamLevel, int availableSkillPoints) {
        return unlockNode(animusTree, nodeId, teamLevel, availableSkillPoints);
    }

    /**
     * Refunds the highest unlocked Resonance tier; returns the XP cost or -1.
     * The cost is 0 while the free-respec window is open (§4.4).
     */
    public int refundResonanceNode(String nodeId, long gameTime) {
        return refundNode(resonanceTree, nodeId, gameTime);
    }

    /**
     * Refunds the highest unlocked Animus tier; returns the XP cost or -1.
     * The cost is 0 while the free-respec window is open (§4.4).
     */
    public int refundAnimusNode(String nodeId, long gameTime) {
        return refundNode(animusTree, nodeId, gameTime);
    }

    // ----- player stats -----

    /** Returns the player's stats, creating an empty entry if absent. */
    public PlayerStats getOrCreatePlayerStats(UUID playerId) {
        PlayerStats stats = playerStats.get(playerId);
        if (stats == null) {
            stats = new PlayerStats();
            playerStats.put(playerId, stats);
            setDirty();
        }
        return stats;
    }

    /** Returns the player's stats, or {@code null} if never tracked. */
    public PlayerStats getPlayerStats(UUID playerId) {
        return playerStats.get(playerId);
    }

    public Map<UUID, PlayerStats> getAllPlayerStats() {
        return Map.copyOf(playerStats);
    }

    // ----- lookup -----

    /** SavedData type for the given team; instances are equal by id. */
    public static SavedDataType<OreVaultTeamData> type(UUID teamId) {
        return new SavedDataType<>(
                Identifier.fromNamespaceAndPath(OreVault.MODID, "team_" + teamId),
                level -> new OreVaultTeamData(teamId),
                level -> CODEC);
    }

    /** Loads or creates the team's data from the overworld SavedData storage. */
    public static OreVaultTeamData getOrCreate(ServerLevel overworld, UUID teamId) {
        return overworld.getDataStorage().computeIfAbsent(type(teamId));
    }

    /** Loads the team's data without creating it; {@code null} if absent. */
    public static OreVaultTeamData get(ServerLevel overworld, UUID teamId) {
        return overworld.getDataStorage().get(type(teamId));
    }

    // ----- NBT -----

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
        tag.putString("team_id", teamId.toString());
        tag.putLong("resonance_pool", resonancePool);
        tag.putLong("animus_pool", animusPool);
        tag.putInt("resonance_level", resonanceLevel);
        tag.putInt("animus_level", animusLevel);
        tag.putInt("resonance_skill_points", resonanceSkillPoints);
        tag.putInt("animus_skill_points", animusSkillPoints);
        tag.putInt("chunk_loading_tickets", chunkLoadingTickets);
        tag.putLong("free_respec_until", freeRespecUntilGameTime);
        tag.put("resonance_nodes", tiersToNbt(resonanceTree.getUnlockedTiers()));
        tag.put("animus_nodes", tiersToNbt(animusTree.getUnlockedTiers()));
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        tag.put("player_stats", players);
        return tag;
    }

    public static OreVaultTeamData fromNbt(CompoundTag raw) {
        CompoundTag tag = migrate(raw, raw.getIntOr(DATA_VERSION_KEY, 0));
        String teamIdString = tag.getStringOr("team_id", "");
        if (teamIdString.isEmpty()) {
            throw new IllegalArgumentException("OreVaultTeamData is missing team_id");
        }
        OreVaultTeamData data = new OreVaultTeamData(UUID.fromString(teamIdString));
        data.resonancePool = tag.getLongOr("resonance_pool", 0L);
        data.animusPool = tag.getLongOr("animus_pool", 0L);
        data.resonanceLevel = tag.getIntOr("resonance_level", 0);
        data.animusLevel = tag.getIntOr("animus_level", 0);
        data.resonanceSkillPoints = tag.getIntOr("resonance_skill_points", 0);
        data.animusSkillPoints = tag.getIntOr("animus_skill_points", 0);
        data.chunkLoadingTickets = tag.getIntOr("chunk_loading_tickets", 0);
        data.freeRespecUntilGameTime = tag.getLongOr("free_respec_until", 0L);
        tiersFromNbt(tag.getCompoundOrEmpty("resonance_nodes"), data.resonanceTree);
        tiersFromNbt(tag.getCompoundOrEmpty("animus_nodes"), data.animusTree);
        CompoundTag players = tag.getCompoundOrEmpty("player_stats");
        for (String key : players.keySet()) {
            data.playerStats.put(UUID.fromString(key), PlayerStats.fromNbt(players.getCompoundOrEmpty(key)));
        }
        return data;
    }

    /**
     * Upgrades a stored tag to {@link #CURRENT_DATA_VERSION} before any field is
     * read, so {@code fromNbt} only ever deals with the current shape.
     *
     * <p>Add one step per version bump, each upgrading {@code out} in place and
     * falling through to the next, so a save from any older version migrates in
     * a single pass.</p>
     */
    private static CompoundTag migrate(CompoundTag tag, int storedVersion) {
        if (storedVersion == CURRENT_DATA_VERSION) {
            return tag;
        }
        if (storedVersion > CURRENT_DATA_VERSION) {
            // Written by a newer build than this one. Read it best-effort rather
            // than refusing: unknown keys are ignored and missing keys fall back
            // to defaults, so a player who downgrades keeps their progression
            // instead of having the team wiped.
            OreVault.LOGGER.warn(
                    "Ore Vault team data is version {} but this build understands {}; loading best-effort."
                            + " Progression written by the newer build may be dropped on next save.",
                    storedVersion, CURRENT_DATA_VERSION);
            return tag;
        }
        CompoundTag out = tag.copy();
        // v0 (unversioned, written before #104) -> v1: no structural change. The
        // version stamp is the only addition, so there is nothing to rewrite.
        OreVault.LOGGER.info("Migrating Ore Vault team data from version {} to {}", storedVersion, CURRENT_DATA_VERSION);
        return out;
    }

    private static CompoundTag tiersToNbt(Map<String, Integer> tiers) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            out.putInt(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void tiersFromNbt(CompoundTag tag, SkillTree tree) {
        for (String key : tag.keySet()) {
            tree.setUnlockedTier(key, tag.getIntOr(key, 0));
        }
    }

    private SkillTree.UnlockResult unlockNode(SkillTree tree, String nodeId, int teamLevel, int availableSkillPoints) {
        SkillTree.UnlockResult result = tree.unlock(nodeId, teamLevel, availableSkillPoints);
        if (result == SkillTree.UnlockResult.OK) {
            setDirty();
        }
        return result;
    }

    private int refundNode(SkillTree tree, String nodeId, long gameTime) {
        int cost = tree.refund(nodeId, isFreeRespecActive(gameTime));
        if (cost >= 0) {
            setDirty();
        }
        return cost;
    }
}
