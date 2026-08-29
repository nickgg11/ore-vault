package com.orevault.orevault.worldgen;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.debug.VaultDiag;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.team.TeamHelper;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Per-team Vault dimension management (§3.1, §11): one {@code orevault:vault_}
 * dimension per FTB team, registered dynamically and created on demand the
 * first time the team activates a portal.
 *
 * <p>At server start every existing team gets its dimension; teams created
 * later are handled via {@link FTBTeamsEvent.TeamCreated}. Dimension deletion
 * on team disband is a documented TODO that lands in {@code [31]} VaultReset.</p>
 *
 * <p>Because chunk generation runs on a background executor while SavedData is
 * main-thread-only, this class maintains a thread-safe {@link
 * VaultChunkGenerator.SkillSnapshot} map refreshed on the main thread and
 * handed to each team's generator as a {@code Supplier}.</p>
 */
public final class VaultDimensions {

    private static final ResourceKey<DimensionType> BASE_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(OreVault.MODID, "ore_vault"));
    private static final ResourceKey<Biome> VAULT_BIOME =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(OreVault.MODID, "vault"));

    /** World height of the base dimension type (matches {@code ore_vault.json}). */
    private static final int BASE_MIN_Y = -64;
    private static final int BASE_HEIGHT = 384;

    private static final Map<UUID, ResourceKey<Level>> TEAM_DIMENSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, VaultChunkGenerator.SkillSnapshot> SKILL_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, VaultChunkGenerator> GENERATORS = new ConcurrentHashMap<>();

    /** Layer stack for the base dimension type, loaded on first dimension creation (#76). */
    private static volatile VaultLayerConfig BASE_LAYER_CONFIG;

    private VaultDimensions() {
    }

    /** Dimension registry key for a team: {@code orevault:vault_<teamId-no-hyphens>} (§3.1). */
    public static ResourceKey<Level> dimensionKey(UUID teamId) {
        return ResourceKey.create(Registries.DIMENSION, TeamHelper.dimensionKey(teamId));
    }

    /** True if the given level is any team's Ore Vault dimension (§3.1 naming scheme). */
    public static boolean isVaultDimension(Level level) {
        Identifier id = level.dimension().identifier();
        return OreVault.MODID.equals(id.getNamespace()) && id.getPath().startsWith("vault_");
    }

    /**
     * Default entry Y for the base dimension: the bottom of the configured air
     * layer (#76), falling back to the pre-config height while no server has
     * loaded the layer stack yet.
     */
    public static int defaultEntryY() {
        VaultLayerConfig config = BASE_LAYER_CONFIG;
        if (config != null && config.firstAirY() >= 0) {
            return config.firstAirY();
        }
        return BASE_MIN_Y + BASE_HEIGHT - VaultLayerConfig.DEFAULT_AIR_THICKNESS;
    }

    /**
     * Ensures the given team's Vault dimension exists, creating it on first use.
     * Safe to call repeatedly; idempotent per team.
     */
    public static ResourceKey<Level> findOrCreate(UUID teamId) {
        ResourceKey<Level> existing = TEAM_DIMENSIONS.get(teamId);
        if (existing != null) {
            return existing;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("VaultDimensions.findOrCreate called outside of a running server");
        }
        return ensureTeamDimension(server, teamId);
    }

    // ----- lifecycle -----

    /** Server start: classify ores (§11) and register a dimension for every existing team. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        OreClassifier.rebuild(server.registryAccess(), OreVaultServerConfig.oreClassificationOverrides());
        for (Team team : TeamHelper.manager().getTeams()) {
            ensureTeamDimension(server, team.getTeamId());
        }
    }

    /** New team created mid-session: create its dimension on demand. */
    @SubscribeEvent
    public static void onTeamCreated(FTBTeamsEvent.TeamCreated event) {
        MinecraftServer server = TeamHelper.manager().getServer();
        ensureTeamDimension(server, event.getEventData().team().getTeamId());
    }

    // ----- dimension creation -----

    /**
     * Registers the team's {@link LevelStem} in the live dimension registry and
     * constructs the {@link ServerLevel}, mirroring what
     * {@code MinecraftServer#createLevels} does for non-overworld dimensions.
     *
     * <p>TODO [31]: dimension deletion on team disband (unregister LevelStem,
     * remove the level, delete {@code <world>/dimensions/orevault/vault_...}).</p>
     */
    private static ResourceKey<Level> ensureTeamDimension(MinecraftServer server, UUID teamId) {
        ResourceKey<Level> key = dimensionKey(teamId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            TEAM_DIMENSIONS.put(teamId, key);
            return key;
        }

        refreshSkillSnapshot(server, teamId);

        Holder.Reference<DimensionType> type = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BASE_DIMENSION_TYPE);
        Holder.Reference<Biome> biome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(VAULT_BIOME);
        VaultLayerConfig layerConfig = VaultLayerConfig.load(server, BASE_DIMENSION_TYPE, BASE_MIN_Y, BASE_HEIGHT);
        BASE_LAYER_CONFIG = layerConfig;
        VaultChunkGenerator generator = new VaultChunkGenerator(
                teamId,
                biome,
                BASE_MIN_Y,
                BASE_HEIGHT,
                () -> SKILL_SNAPSHOTS.getOrDefault(teamId, VaultChunkGenerator.SkillSnapshot.EMPTY),
                layerConfig
        );
        LevelStem stem = new LevelStem(type, generator);

        // The server's registry access is frozen; reach the live registry and
        // briefly unfreeze to register the new stem (§11 "registry desync" gotcha).
        if (!(server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM) instanceof MappedRegistry<?> registry)) {
            throw new IllegalStateException("LEVEL_STEM registry is not a MappedRegistry; cannot register " + key.identifier());
        }
        @SuppressWarnings("unchecked")
        MappedRegistry<LevelStem> stems = (MappedRegistry<LevelStem>) registry;
        stems.unfreeze(false);
        stems.register(ResourceKey.create(Registries.LEVEL_STEM, key.identifier()), stem, RegistrationInfo.BUILT_IN);
        stems.freeze();

        DerivedLevelData derivedLevelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        ServerLevel level = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                server.storageSource,
                derivedLevelData,
                key,
                stem,
                false,
                BiomeManager.obfuscateSeed(server.overworld().getSeed()),
                List.of(),
                false
        );
        server.forgeGetWorldMap().put(key, level);
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));

        GENERATORS.put(teamId, generator);
        TEAM_DIMENSIONS.put(teamId, key);
        VaultDiag.markCreated(key, server.getTickCount());
        OreVault.LOGGER.info("Created Ore Vault dimension {} for team {} at server tick {}", key.identifier(), teamId, server.getTickCount());
        return key;
    }

    /**
     * Refreshes the main-thread skill snapshot for a team so its generator
     * always sees current skill state at chunk generation time. Call on the
     * server thread whenever nodes change or a dimension is created.
     */
    public static void refreshSkillSnapshot(MinecraftServer server, UUID teamId) {
        OreVaultTeamData data = OreVaultTeamData.get(server.overworld(), teamId);
        SKILL_SNAPSHOTS.put(teamId, data == null ? VaultChunkGenerator.SkillSnapshot.EMPTY : VaultChunkGenerator.SkillSnapshot.of(data));
    }
}
