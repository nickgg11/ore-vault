package com.orevault.orevault.worldgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.debug.VaultDiag;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.team.TeamHelper;

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
import net.minecraft.world.level.storage.LevelResource;
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
 * <p><strong>Creation is lazy; restoration is not.</strong> No dimension is
 * created for a team merely because the team exists — FTB Teams auto-creates a
 * single-member team for every player who logs in, so that cost one
 * {@link ServerLevel} per player account the server had ever seen. Only
 * {@link #findOrCreate} on the portal path creates a <em>new</em> Vault.
 * Vaults that already exist on disk are re-registered at server start, before
 * any player connects, because a player may have logged out inside one.
 * Dimension deletion on team disband is a documented TODO that lands in
 * {@code [77]} (issue #93).</p>
 *
 * <p>Because chunk generation runs on a background executor while SavedData is
 * main-thread-only, this class maintains a thread-safe {@link
 * VaultChunkGenerator.SkillSnapshot} map refreshed on the main thread and
 * handed to each team's generator as a {@code Supplier}.</p>
 */
public final class VaultDimensions {

    /**
     * A Vault dimension type and the world height that must match its JSON
     * (§3.1). The two variants differ only in how far down the world goes
     * before bedrock; everything else — lighting, fixed time, no natural
     * spawning — is identical.
     *
     * <p>The height values are duplicated from the JSON rather than read back
     * from the registry because {@link VaultLayerConfig#load} validates the
     * layer stack against them, and a mismatch should fail loudly at creation
     * rather than generate a subtly wrong world.</p>
     */
    public enum Variant {
        /** {@code ore_vault}: bedrock at Y=0, no deepslate band. */
        BASE("ore_vault", 0, 320),
        /** {@code ore_vault_expanded}: bedrock at Y=-64, unlocking Y=-63..-1 (§6.1 Vault Expansion). */
        EXPANDED("ore_vault_expanded", -64, 384);

        private final ResourceKey<DimensionType> key;
        private final int minY;
        private final int height;

        Variant(String path, int minY, int height) {
            this.key = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(OreVault.MODID, path));
            this.minY = minY;
            this.height = height;
        }

        public ResourceKey<DimensionType> key() {
            return key;
        }

        public int minY() {
            return minY;
        }

        public int height() {
            return height;
        }
    }

    private static final ResourceKey<Biome> VAULT_BIOME =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(OreVault.MODID, "vault"));

    private static final Map<UUID, ResourceKey<Level>> TEAM_DIMENSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, VaultChunkGenerator.SkillSnapshot> SKILL_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, VaultChunkGenerator> GENERATORS = new ConcurrentHashMap<>();

    /** Which variant each team's Vault was created under. */
    private static final Map<UUID, Variant> TEAM_VARIANTS = new ConcurrentHashMap<>();

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
     * The team a Vault dimension belongs to, or {@code null} if the level is not
     * a Vault. Inverse of {@link #dimensionKey}, parsed from the level's own id
     * rather than looked up in {@link #TEAM_DIMENSIONS} so it answers correctly
     * for a Vault whose team has no entry yet.
     *
     * <p>Break handling derives the owning team this way rather than from the
     * breaking player, because a machine has no player and because the world was
     * generated against the owner's skill tree — the drops belong to whoever the
     * dimension belongs to.</p>
     */
    public static @Nullable UUID teamIdFor(Level level) {
        if (!isVaultDimension(level)) {
            return null;
        }
        String raw = level.dimension().identifier().getPath().substring("vault_".length());
        if (raw.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(new StringBuilder(raw)
                    .insert(20, '-').insert(16, '-').insert(12, '-').insert(8, '-')
                    .toString());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * The team's current skill snapshot, refreshed on the main thread.
     *
     * <p>Break handling reads this rather than {@code OreVaultTeamData} directly.
     * Both would be correct on the main thread, but two views of skill state is
     * exactly how they drift — the snapshot is the one source, and worldgen
     * already depends on it.</p>
     */
    public static VaultChunkGenerator.SkillSnapshot skillSnapshot(UUID teamId) {
        return SKILL_SNAPSHOTS.getOrDefault(teamId, VaultChunkGenerator.SkillSnapshot.EMPTY);
    }

    /**
     * Default entry Y for the base dimension: the bottom of the configured air
     * layer (#76), falling back to the pre-config height while no server has
     * loaded the layer stack yet. Both variants share the same surface — they
     * differ below Y=0, not above it — so one value serves for either.
     */
    public static int defaultEntryY() {
        VaultLayerConfig config = BASE_LAYER_CONFIG;
        if (config != null && config.firstAirY() >= 0) {
            return config.firstAirY();
        }
        return Variant.BASE.minY() + Variant.BASE.height() - VaultLayerConfig.DEFAULT_AIR_THICKNESS;
    }

    /** The variant a team's Vault was created under; {@link Variant#BASE} until one exists. */
    public static Variant variantFor(UUID teamId) {
        return TEAM_VARIANTS.getOrDefault(teamId, Variant.BASE);
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

    /**
     * Server start: classify ores (§11) and restore every Vault that already
     * exists on disk.
     *
     * <p>New dimensions are created lazily, on the first portal trip by a team
     * member (§3.1) — <em>not</em> for every registered team. FTB Teams
     * auto-creates a single-member team for every player who logs in, so
     * creating one there meant a full {@link ServerLevel} — chunk map, storage,
     * region directory — per player account the server had ever seen, for a
     * dimension most of them never enter.</p>
     *
     * <p>A Vault that already has a directory on disk is a different matter: it
     * has been entered, its cost is already paid, and a player may be standing
     * in it right now. Those <strong>must</strong> be registered before anyone
     * connects. A player whose saved dimension does not resolve to a live level
     * is placed in the Overworld by vanilla — but keeps their saved
     * coordinates, because {@code ServerPlayer.SavedPosition} holds the
     * dimension and the position as independent optionals. Someone who logged
     * out standing on the Vault surface at Y=251 therefore reappears at Y=251
     * in the Overworld, in open sky, and falls to their death.</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        OreClassifier.rebuild(server.registryAccess(), OreVaultServerConfig.oreClassificationOverrides());
        restoreExistingDimensions(server);
    }

    /**
     * Registers every Vault that already has a directory under
     * {@code <world>/dimensions/orevault/}. Teams that have never opened a
     * portal have no directory and stay uncreated.
     *
     * <p>Restored Vaults come back as {@link Variant#BASE}. That is correct
     * today because nothing can produce an expanded Vault yet — the Vault
     * Expansion keystone and the reset that applies it both land in
     * {@code [77]} (issue #93). When they do, the chosen variant has to be
     * persisted on the team's SavedData and read here, or an expanded Vault
     * would silently come back with bedrock at Y=0 and its deepslate band
     * unreachable.</p>
     */
    private static void restoreExistingDimensions(MinecraftServer server) {
        Path dimensionsDir = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(OreVault.MODID);
        if (!Files.isDirectory(dimensionsDir)) {
            return;
        }
        int restored = 0;
        try (Stream<Path> entries = Files.list(dimensionsDir)) {
            for (Path dir : entries.filter(Files::isDirectory).toList()) {
                UUID teamId = teamIdFromDirectoryName(dir.getFileName().toString());
                if (teamId == null) {
                    OreVault.LOGGER.warn("Ignoring unrecognised Ore Vault dimension directory {}", dir);
                    continue;
                }
                try {
                    ensureTeamDimension(server, teamId);
                    restored++;
                } catch (RuntimeException e) {
                    OreVault.LOGGER.error("Failed to restore Ore Vault dimension for team {}", teamId, e);
                }
            }
        } catch (IOException e) {
            OreVault.LOGGER.error("Failed to scan {} for existing Ore Vault dimensions", dimensionsDir, e);
            return;
        }
        if (restored > 0) {
            OreVault.LOGGER.info("Restored {} existing Ore Vault dimension(s) from disk", restored);
        }
    }

    /**
     * Reverses {@link TeamHelper#dimensionKey}: {@code vault_<32 hex chars>}
     * back to a team UUID, or {@code null} if the name is not one of ours.
     */
    private static @Nullable UUID teamIdFromDirectoryName(String name) {
        String prefix = "vault_";
        if (!name.startsWith(prefix)) {
            return null;
        }
        String hex = name.substring(prefix.length());
        if (hex.length() != 32) {
            return null;
        }
        try {
            // Re-insert the hyphens right-to-left so the earlier indices stay valid.
            return UUID.fromString(new StringBuilder(hex)
                    .insert(20, '-')
                    .insert(16, '-')
                    .insert(12, '-')
                    .insert(8, '-')
                    .toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ----- dimension creation -----

    /**
     * Registers the team's {@link LevelStem} in the live dimension registry and
     * constructs the {@link ServerLevel}, mirroring what
     * {@code MinecraftServer#createLevels} does for non-overworld dimensions.
     *
     * <p>TODO [77] (issue #93): dimension deletion on team disband (unregister LevelStem,
     * remove the level, delete {@code <world>/dimensions/orevault/vault_...}).</p>
     */
    private static ResourceKey<Level> ensureTeamDimension(MinecraftServer server, UUID teamId) {
        return ensureTeamDimension(server, teamId, variantFor(teamId));
    }

    /**
     * As {@link #ensureTeamDimension(MinecraftServer, UUID)}, but pins the
     * dimension type variant. Only the reset path ({@code [77]}, issue #93)
     * passes anything other than the team's current variant — a Vault's depth
     * can change exactly once, when Vault Expansion forces a regeneration
     * (§3.5 step 5).
     */
    private static ResourceKey<Level> ensureTeamDimension(MinecraftServer server, UUID teamId, Variant variant) {
        ResourceKey<Level> key = dimensionKey(teamId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            TEAM_DIMENSIONS.put(teamId, key);
            return key;
        }

        refreshSkillSnapshot(server, teamId);

        Holder.Reference<DimensionType> type = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(variant.key());
        Holder.Reference<Biome> biome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(VAULT_BIOME);
        VaultLayerConfig layerConfig = VaultLayerConfig.load(server, variant.key(), variant.minY(), variant.height());
        if (variant == Variant.BASE) {
            BASE_LAYER_CONFIG = layerConfig;
        }
        VaultChunkGenerator generator = new VaultChunkGenerator(
                teamId,
                biome,
                variant.minY(),
                variant.height(),
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
        // MUST follow the world-map insert (#82/#89). MinecraftServer ticks levels
        // from a cached ServerLevel[] rebuilt by getWorldArray() only when this
        // marker changes; without the call a level added after the first server
        // tick sits in the map but is never ticked. The failure mode is
        // misleading: packet handlers are synchronous, so block placement works
        // and the first left-click produces exactly one BreakSpeed event, then
        // ServerPlayerGameMode.tick() never runs, destroy progress never
        // advances and BREAK_BLOCK never fires. Nothing is cancelled, and
        // relogging "fixes" it because the level is then rebuilt by
        // MinecraftServer#createLevels before the cache exists.
        server.markWorldsDirty();
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));

        GENERATORS.put(teamId, generator);
        TEAM_DIMENSIONS.put(teamId, key);
        TEAM_VARIANTS.put(teamId, variant);
        VaultDiag.markCreated(key, server.getTickCount());
        OreVault.LOGGER.info(
                "Created Ore Vault dimension {} ({}, Y={}..{}) for team {} at server tick {}",
                key.identifier(),
                variant.key().identifier(),
                variant.minY(),
                variant.minY() + variant.height() - 1,
                teamId,
                server.getTickCount()
        );
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
