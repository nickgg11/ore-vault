package com.orevault.orevault.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orevault.orevault.OreVault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-team persistent state: Resonance/Animus pools, levels, skill points, unlocked nodes,
 * per-player tradeoff toggles, player stats, anchor and zone positions.
 * <p>
 * Stored on the {@link MinecraftServer#getDataStorage()} (global storage) so it survives
 * dimension resets and team Vault deletion alike.
 */
public final class OreVaultTeamData extends SavedData {
    public static final String PREFIX = "orevault_team_";

    public static SavedDataType<OreVaultTeamData> type(UUID teamId) {
        return new SavedDataType<>(
                Identifier.fromNamespaceAndPath(OreVault.MODID, "teams/" + PREFIX + teamId),
                () -> new OreVaultTeamData(teamId),
                CompoundTag.CODEC.xmap(tag -> load(teamId, tag), data -> data.save(new CompoundTag())),
                null
        );
    }

    private final UUID teamId;

    // Resonance
    private long resonancePool;
    private int resonanceLevel;
    private int resonanceSkillPoints;

    // Animus
    private long animusPool;
    private int animusLevel;
    private int animusSkillPoints;

    // Node state: nodeId -> tier unlocked (both trees share the map; node ids are unique)
    private final Map<String, Integer> unlockedNodes = new HashMap<>();
    // Tradeoff toggles: nodeId -> (playerUuid -> active)
    private final Map<String, Map<UUID, Boolean>> tradeoffToggles = new HashMap<>();

    // Player stats
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();

    // World structure
    private boolean vaultCreated;
    private boolean vaultExpanded; // Vault Expansion keystone purchased & applied (post-reset)
    private final List<BlockPos> anchorPositions = new ArrayList<>();
    private final List<BlockPos> zonePositions = new ArrayList<>();

    // Team aggregate stats
    private long chunksGenerated;
    private long totalBlocksGenerated;

    public OreVaultTeamData(UUID teamId) {
        this.teamId = teamId;
    }

    public static OreVaultTeamData get(MinecraftServer server, UUID teamId) {
        return server.getDataStorage().computeIfAbsent(type(teamId));
    }

    public UUID teamId() {
        return teamId;
    }

    // --- Resonance -------------------------------------------------------------

    public long resonancePool() {
        return resonancePool;
    }

    public void addResonance(long amount) {
        resonancePool += amount;
        setDirty();
    }

    public int resonanceLevel() {
        return resonanceLevel;
    }

    public void setResonanceLevel(int level) {
        resonanceLevel = level;
        setDirty();
    }

    public int resonanceSkillPoints() {
        return resonanceSkillPoints;
    }

    public void setResonanceSkillPoints(int points) {
        resonanceSkillPoints = points;
        setDirty();
    }

    public void addResonanceSkillPoints(int points) {
        resonanceSkillPoints += points;
        setDirty();
    }

    // --- Animus ----------------------------------------------------------------

    public long animusPool() {
        return animusPool;
    }

    public void addAnimus(long amount) {
        animusPool += amount;
        setDirty();
    }

    public int animusLevel() {
        return animusLevel;
    }

    public void setAnimusLevel(int level) {
        animusLevel = level;
        setDirty();
    }

    public int animusSkillPoints() {
        return animusSkillPoints;
    }

    public void setAnimusSkillPoints(int points) {
        animusSkillPoints = points;
        setDirty();
    }

    public void addAnimusSkillPoints(int points) {
        animusSkillPoints += points;
        setDirty();
    }

    // --- Nodes -----------------------------------------------------------------

    public Map<String, Integer> unlockedNodes() {
        return unlockedNodes;
    }

    public int nodeTier(String nodeId) {
        return unlockedNodes.getOrDefault(nodeId, 0);
    }

    public boolean hasNode(String nodeId) {
        return unlockedNodes.containsKey(nodeId);
    }

    public void unlockNode(String nodeId, int tier) {
        unlockedNodes.put(nodeId, tier);
        setDirty();
    }

    public void removeNode(String nodeId) {
        unlockedNodes.remove(nodeId);
        setDirty();
    }

    public int totalSkillPointsInvested(String treeId) {
        return unlockedNodes.entrySet().stream()
                .filter(e -> com.orevault.orevault.skill.NodeDefs.byId(e.getKey())
                        .map(n -> n.treeId().equals(treeId))
                        .orElse(false))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public Map<String, Map<UUID, Boolean>> tradeoffToggles() {
        return tradeoffToggles;
    }

    public boolean isTradeoffActiveFor(String nodeId, UUID playerId) {
        return tradeoffToggles.getOrDefault(nodeId, Map.of()).getOrDefault(playerId, false);
    }

    public boolean isTradeoffActiveForAnyone(String nodeId) {
        return tradeoffToggles.getOrDefault(nodeId, Map.of()).values().stream().anyMatch(Boolean::booleanValue);
    }

    public void setTradeoff(String nodeId, UUID playerId, boolean active) {
        tradeoffToggles.computeIfAbsent(nodeId, k -> new HashMap<>()).put(playerId, active);
        setDirty();
    }

    // --- Stats -----------------------------------------------------------------

    public Map<UUID, PlayerStats> playerStats() {
        return playerStats;
    }

    public PlayerStats statsFor(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, u -> {
            setDirty();
            return new PlayerStats();
        });
    }

    // --- World structure --------------------------------------------------------

    public boolean vaultCreated() {
        return vaultCreated;
    }

    public void setVaultCreated(boolean created) {
        vaultCreated = created;
        setDirty();
    }

    public boolean vaultExpanded() {
        return vaultExpanded;
    }

    public void setVaultExpanded(boolean expanded) {
        vaultExpanded = expanded;
        setDirty();
    }

    public List<BlockPos> anchorPositions() {
        return anchorPositions;
    }

    public void addAnchor(BlockPos pos) {
        if (!anchorPositions.contains(pos)) {
            anchorPositions.add(pos);
            setDirty();
        }
    }

    public void removeAnchor(BlockPos pos) {
        if (anchorPositions.remove(pos)) {
            setDirty();
        }
    }

    public List<BlockPos> zonePositions() {
        return zonePositions;
    }

    public void addZone(BlockPos pos) {
        if (!zonePositions.contains(pos)) {
            zonePositions.add(pos);
            setDirty();
        }
    }

    public void removeZone(BlockPos pos) {
        if (zonePositions.remove(pos)) {
            setDirty();
        }
    }

    public long chunksGenerated() {
        return chunksGenerated;
    }

    public void addChunksGenerated(int n) {
        chunksGenerated += n;
        setDirty();
    }

    public long totalBlocksGenerated() {
        return totalBlocksGenerated;
    }

    public void addBlocksGenerated(long n) {
        totalBlocksGenerated += n;
        setDirty();
    }

    // --- SavedData --------------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        tag.putString("teamId", teamId.toString());
        tag.putLong("resonancePool", resonancePool);
        tag.putInt("resonanceLevel", resonanceLevel);
        tag.putInt("resonanceSkillPoints", resonanceSkillPoints);
        tag.putLong("animusPool", animusPool);
        tag.putInt("animusLevel", animusLevel);
        tag.putInt("animusSkillPoints", animusSkillPoints);

        CompoundTag nodes = new CompoundTag();
        unlockedNodes.forEach((id, tier) -> nodes.putInt(id, tier));
        tag.put("unlockedNodes", nodes);

        CompoundTag toggles = new CompoundTag();
        tradeoffToggles.forEach((nodeId, players) -> {
            CompoundTag playerTag = new CompoundTag();
            players.forEach((uuid, active) -> playerTag.putBoolean(uuid.toString(), active));
            toggles.put(nodeId, playerTag);
        });
        tag.put("tradeoffToggles", toggles);

        CompoundTag stats = new CompoundTag();
        playerStats.forEach((uuid, ps) -> stats.put(uuid.toString(), ps.writeNbt()));
        tag.put("playerStats", stats);

        tag.putBoolean("vaultCreated", vaultCreated);
        tag.putBoolean("vaultExpanded", vaultExpanded);

        CompoundTag anchors = new CompoundTag();
        for (int i = 0; i < anchorPositions.size(); i++) {
            anchors.putLong("p" + i, anchorPositions.get(i).asLong());
        }
        tag.put("anchors", anchors);

        CompoundTag zones = new CompoundTag();
        for (int i = 0; i < zonePositions.size(); i++) {
            zones.putLong("p" + i, zonePositions.get(i).asLong());
        }
        tag.put("zones", zones);

        tag.putLong("chunksGenerated", chunksGenerated);
        tag.putLong("totalBlocksGenerated", totalBlocksGenerated);
        return tag;
    }

    public static OreVaultTeamData load(UUID teamId, CompoundTag tag) {
        OreVaultTeamData data = new OreVaultTeamData(teamId);
        data.resonancePool = tag.getLongOr("resonancePool", 0L);
        data.resonanceLevel = tag.getIntOr("resonanceLevel", 0);
        data.resonanceSkillPoints = tag.getIntOr("resonanceSkillPoints", 0);
        data.animusPool = tag.getLongOr("animusPool", 0L);
        data.animusLevel = tag.getIntOr("animusLevel", 0);
        data.animusSkillPoints = tag.getIntOr("animusSkillPoints", 0);

        CompoundTag nodes = tag.getCompoundOrEmpty("unlockedNodes");
        for (String key : nodes.keySet()) {
            data.unlockedNodes.put(key, nodes.getIntOr(key, 0));
        }

        CompoundTag toggles = tag.getCompoundOrEmpty("tradeoffToggles");
        for (String nodeId : toggles.keySet()) {
            CompoundTag playerTag = toggles.getCompoundOrEmpty(nodeId);
            Map<UUID, Boolean> map = new HashMap<>();
            for (String uuidKey : playerTag.keySet()) {
                map.put(UUID.fromString(uuidKey), playerTag.getBooleanOr(uuidKey, false));
            }
            data.tradeoffToggles.put(nodeId, map);
        }

        CompoundTag stats = tag.getCompoundOrEmpty("playerStats");
        for (String uuidKey : stats.keySet()) {
            data.playerStats.put(UUID.fromString(uuidKey), PlayerStats.readNbt(stats.getCompoundOrEmpty(uuidKey)));
        }

        data.vaultCreated = tag.getBooleanOr("vaultCreated", false);
        data.vaultExpanded = tag.getBooleanOr("vaultExpanded", false);

        CompoundTag anchors = tag.getCompoundOrEmpty("anchors");
        for (String key : anchors.keySet()) {
            data.anchorPositions.add(BlockPos.of(anchors.getLongOr(key, 0L)));
        }
        CompoundTag zones = tag.getCompoundOrEmpty("zones");
        for (String key : zones.keySet()) {
            data.zonePositions.add(BlockPos.of(zones.getLongOr(key, 0L)));
        }

        data.chunksGenerated = tag.getLongOr("chunksGenerated", 0L);
        data.totalBlocksGenerated = tag.getLongOr("totalBlocksGenerated", 0L);
        return data;
    }
}

