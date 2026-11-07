package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.key.VaultKeys;
import com.orevault.orevault.core.session.LoginCheck.Outcome;
import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerAttachment;
import com.orevault.orevault.data.PlayerData;
import com.orevault.orevault.data.PlayerData.InVault;
import com.orevault.orevault.data.PlayerData.ReturnPos;
import com.orevault.orevault.data.VaultTypeFile;
import com.orevault.orevault.vault.VaultDimensions;
import com.orevault.orevault.vault.VaultDimensions.Restore;
import com.orevault.orevault.vault.VaultPlayerEvents;
import com.orevault.orevault.vault.VaultTravel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * E3-S6a (#237): creating, restoring and entering a team's Vault, and the player attachment, in one server lifetime. What
 * needs a restart is QA's scripted stop and start (E3-S10, #200). Every test uses its own random team id, so the Vaults it
 * makes are its own, and restore is driven per team ({@code restoreOne}) rather than over the folder another test is
 * writing.
 *
 * <p>The players are {@link FakePlayer}s built per test. A mock player cannot join with FTB Library loaded
 * ({@code TeamTests}), and a fake player's connection swallows what a teleport sends. The portal path itself refuses a fake
 * player, so the travel tests call {@link VaultTravel#intoVault} with a team id, the step after that refusal.
 */
@EventBusSubscriber(modid = OreVault.MOD_ID)
final class VaultLifecycleTests {
    private static final byte[] GARBAGE = {1, 2, 3, 4, 5, 6, 7, 8};
    /** The base stack's grass is at Y=250, so the anchor stands at Y=251. */
    private static final int BASE_SURFACE = 251;

    /** Every level that has ticked since the server started; the tick loop is the only writer. */
    private static final Set<ResourceKey<Level>> TICKED = ConcurrentHashMap.newKeySet();
    /** Players whose next trip to another dimension is cancelled, as a mod would cancel it. */
    private static final Set<UUID> BOUNCED = ConcurrentHashMap.newKeySet();

    private VaultLifecycleTests() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        TICKED.add(event.getLevel().dimension());
    }

    @SubscribeEvent
    static void onTravel(EntityTravelToDimensionEvent event) {
        if (BOUNCED.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static ServerLevel createVault(GameTestHelper helper, UUID team) {
        return VaultDimensions.findOrCreate(server(helper), team).orElseThrow(() -> new IllegalStateException("no Vault for " + team));
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    /** A fake player added to a level by a teleport is taken out again, so it cannot hold a later test's Vault open. */
    private static void discard(ServerPlayer player) {
        player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static int levelCount(MinecraftServer server) {
        int count = 0;
        for (ServerLevel ignored : server.getAllLevels()) {
            count++;
        }
        return count;
    }

    // ---- creation --------------------------------------------------------------------------------------------------

    /** §3.1, S14: a login reaches no creation. A fake player, as above; FTB Teams makes it a team as it would anyone. */
    static void aLoginCreatesNoVault(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        FakePlayer player = player(helper.getLevel(), "orevault-login");
        int before = levelCount(server);

        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));

        check(helper, "levels", before, levelCount(server));
        check(helper, "Vault level", null, server.getLevel(VaultDimensions.dimensionKey(player.getUUID())));
        check(helper, "Vault folder", false, Files.exists(DataPaths.vaultFolder(server, player.getUUID())));
        helper.succeed();
    }

    /**
     * Old #82 and #89: a level added after the first tick must tick, or breaking never finishes. The tick logger is the
     * evidence that it ticks; the fake player's break shows a block can be removed in it.
     */
    static void aVaultCreatedAfterTheFirstTickTicks(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        check(helper, "past the first tick", true, server.getTickCount() > 0);
        ServerLevel vault = createVault(helper, team);
        check(helper, "registered", vault, server.getLevel(VaultDimensions.dimensionKey(team)));
        check(helper, "not yet ticked", false, TICKED.contains(vault.dimension()));
        BlockPos grass = new BlockPos(0, BASE_SURFACE - 1, 0);
        check(helper, "anchor", new Vec3(0.5, BASE_SURFACE, 0.5), VaultTravel.anchor(vault));
        check(helper, "grass under the anchor", true, vault.getBlockState(grass).is(Blocks.GRASS_BLOCK));
        FakePlayerFactory.get(vault, new GameProfile(UUID.randomUUID(), "orevault-breaker")).gameMode.destroyBlock(grass);
        check(helper, "broken", true, vault.getBlockState(grass).isAir());
        helper.succeedWhen(() -> {
            if (!TICKED.contains(vault.dimension())) {
                helper.fail(Component.literal("the Vault has not ticked"));
            }
        });
    }

    /** ADR 0002 creation step 1, and step 2's type file written before the level, with nothing in team data (S18). */
    static void findOrCreateIsIdempotentAndWritesOnlyTheTypeFile(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        ServerLevel first = createVault(helper, team);
        int count = levelCount(server);
        check(helper, "same level", first, createVault(helper, team));
        check(helper, "levels", count, levelCount(server));
        check(helper, "type", Optional.of(VaultVariant.BASE), VaultTypeFile.read(DataPaths.vaultTypeFile(server, team),
                line -> { }, line -> helper.fail(Component.literal(line))));
        check(helper, "team data", false, OreVaultTeamData.hasRecord(server, team));
        check(helper, "in the variant map", Optional.of(VaultVariant.BASE), VaultDimensions.variant(team));
        helper.succeed();
    }

    /** ADR 0002 creation step 2: a damaged type file refuses the Vault and nothing is written into its folder. */
    static void aDamagedTypeFileRefusesTheVault(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        Path typeFile = DataPaths.vaultTypeFile(server, team);
        Files.createDirectories(typeFile.getParent());
        Files.write(typeFile, GARBAGE);

        check(helper, "portal path", Optional.empty(), VaultDimensions.findOrCreate(server, team));
        check(helper, "level", null, server.getLevel(VaultDimensions.dimensionKey(team)));
        check(helper, "bytes", true, Arrays.equals(GARBAGE, Files.readAllBytes(typeFile)));
        try (var files = Files.list(typeFile.getParent())) {
            check(helper, "folder", 1L, files.count());
        }
        Restore restore = VaultDimensions.restoreOne(server, team);
        check(helper, "restored", false, restore.restored());
        helper.succeed();
    }

    // ---- restore ---------------------------------------------------------------------------------------------------

    /** The type comes from the folder (ADR 0002, round 2): an expanded folder restores expanded, bedrock at Y=-64. */
    static void restoreReadsTheTypeFromTheFolder(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        VaultTypeFile.write(DataPaths.vaultFolder(server, team), VaultVariant.EXPANDED);

        Restore restore = VaultDimensions.restoreOne(server, team);

        check(helper, "restored", new Restore(team, true, ""), restore);
        ServerLevel vault = server.getLevel(VaultDimensions.dimensionKey(team));
        check(helper, "registered", true, vault != null);
        check(helper, "variant", Optional.of(VaultVariant.EXPANDED), VaultDimensions.variant(team));
        check(helper, "floor", -64, vault.getMinY());
        helper.succeed();
    }

    /** S19: a disbanded team's Vault waits for the archive at the next start and is not registered. */
    static void restoreSkipsADisbandedTeam(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        VaultTypeFile.write(DataPaths.vaultFolder(server, team), VaultVariant.BASE);
        OreVaultTeamData.forWrite(server, team).markDisbanded(server.overworld().getGameTime());

        check(helper, "restored", false, VaultDimensions.restoreOne(server, team).restored());
        check(helper, "level", null, server.getLevel(VaultDimensions.dimensionKey(team)));
        helper.succeed();
    }

    /** ADR 0002 restore step 4: a damaged team file costs progress, never the terrain, and is not touched. */
    static void restoreKeepsTheVaultOfAnUnreadableTeam(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        VaultTypeFile.write(DataPaths.vaultFolder(server, team), VaultVariant.BASE);
        Path teamFile = DataPaths.teamFile(server, team);
        Files.createDirectories(teamFile.getParent());
        Files.write(teamFile, GARBAGE);

        check(helper, "unreadable", true, OreVaultTeamData.isUnreadable(server, team));
        check(helper, "restored", true, VaultDimensions.restoreOne(server, team).restored());
        check(helper, "team file", true, Arrays.equals(GARBAGE, Files.readAllBytes(teamFile)));
        helper.succeed();
    }

    static void anAbsentTeamFileIsNotUnreadable(GameTestHelper helper) {
        check(helper, "unreadable", false, OreVaultTeamData.isUnreadable(server(helper), UUID.randomUUID()));
        helper.succeed();
    }

    // ---- travel ----------------------------------------------------------------------------------------------------

    /** §3.2: the return position is saved by the teleport's post step, so only once the player is in the Vault. */
    static void theReturnPositionIsSavedAfterTheTeleport(GameTestHelper helper) {
        ServerLevel overworld = server(helper).overworld();
        UUID team = UUID.randomUUID();
        FakePlayer player = player(overworld, "orevault-traveller");
        player.setPos(100.5, 80, -20.5);
        TeleportTransition trip = VaultTravel.intoVault(player, team, overworld, BlockPos.containing(player.position()));
        check(helper, "a destination", true, trip != null);
        check(helper, "nothing saved before the trip", Optional.empty(), PlayerAttachment.get(player).returnPos());

        boolean moved = player.teleport(trip) != null;

        check(helper, "moved", true, moved);
        check(helper, "in the Vault", VaultDimensions.dimensionKey(team), player.level().dimension());
        // A fake player's connection drops the position a teleport sends, so the trip is read for where it goes.
        check(helper, "to the anchor", new Vec3(0.5, BASE_SURFACE, 0.5), trip.position());
        ReturnPos saved = PlayerAttachment.get(player).returnPos().orElse(null);
        check(helper, "saved in", "minecraft:overworld", saved == null ? null : saved.dim());
        check(helper, "saved y", 80, saved == null ? null : saved.y());
        check(helper, "in_vault", Optional.of(new InVault(team, 0)), PlayerAttachment.get(player).inVault());
        discard(player);
        helper.succeed();
    }

    /** The AC: a bounced teleport leaves the return position it had. */
    static void aBouncedTeleportKeepsTheOldReturnPosition(GameTestHelper helper) {
        ServerLevel overworld = server(helper).overworld();
        FakePlayer player = player(overworld, "orevault-bounced");
        ReturnPos old = new ReturnPos("minecraft:overworld", 7, 70, 7);
        PlayerAttachment.update(player, data -> data.withReturnPos(old));
        TeleportTransition trip = VaultTravel.intoVault(player, UUID.randomUUID(), overworld, BlockPos.ZERO);

        BOUNCED.add(player.getUUID());
        boolean moved;
        try {
            moved = player.teleport(trip) != null;
        } finally {
            BOUNCED.remove(player.getUUID());
        }

        check(helper, "moved", false, moved);
        check(helper, "still in", Level.OVERWORLD, player.level().dimension());
        check(helper, "return_pos", Optional.of(old), PlayerAttachment.get(player).returnPos());
        check(helper, "in_vault", Optional.empty(), PlayerAttachment.get(player).inVault());
        helper.succeed();
    }

    /** §3.2: home is the return position; without one, or with one in a level that is gone, world spawn. */
    static void homeIsTheReturnPositionOrWorldSpawn(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        FakePlayer player = player(server.overworld(), "orevault-home");
        check(helper, "no return_pos", server.findRespawnDimension(), VaultTravel.home(player, TeleportTransition.DO_NOTHING).newLevel());

        PlayerAttachment.update(player, data -> data.withReturnPos(new ReturnPos("minecraft:the_nether", 3, 64, 4)));
        TeleportTransition nether = VaultTravel.home(player, TeleportTransition.DO_NOTHING);
        check(helper, "level", Level.NETHER, nether.newLevel().dimension());
        check(helper, "position", new Vec3(3.5, 64, 4.5), nether.position());

        PlayerAttachment.update(player, data -> data.withReturnPos(new ReturnPos("orevault:nowhere", 3, 64, 4)));
        check(helper, "gone", server.findRespawnDimension(), VaultTravel.home(player, TeleportTransition.DO_NOTHING).newLevel());
        String vault = VaultDimensions.dimensionKey(UUID.randomUUID()).identifier().toString();
        PlayerAttachment.update(player, data -> data.withReturnPos(new ReturnPos(vault, 3, 64, 4)));
        check(helper, "in a Vault", server.findRespawnDimension(), VaultTravel.home(player, TeleportTransition.DO_NOTHING).newLevel());
        helper.succeed();
    }

    /** §3.2: FTB Teams has no team for a fake player, so a portal takes it nowhere and makes no Vault. */
    static void aFakePlayerGoesNowhere(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "orevault-machine"));
        check(helper, "destination", null, VaultTravel.portalDestination(helper.getLevel(), player, BlockPos.ZERO));
        check(helper, "Vault", null, server.getLevel(VaultDimensions.dimensionKey(player.getUUID())));
        check(helper, "folder", false, Files.exists(DataPaths.vaultFolder(server, player.getUUID())));
        helper.succeed();
    }

    // ---- the player attachment -------------------------------------------------------------------------------------

    private static CompoundTag save(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(output);
        return output.buildResult();
    }

    private static void load(ServerPlayer player, CompoundTag tag) {
        player.load(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), tag));
    }

    /** ADR 0005 section 6's wrapper, run for the first time: the attachment through the entity's own save and load. */
    static void theAttachmentRoundTripsThroughTheEntitySave(GameTestHelper helper) {
        PlayerData data = new PlayerData(Optional.of(new ReturnPos("minecraft:overworld", 1, 2, 3)),
                Optional.of(new InVault(UUID.randomUUID(), 4)));
        FakePlayer saved = player(helper.getLevel(), "orevault-saved");
        PlayerAttachment.update(saved, ignored -> data);
        CompoundTag tag = save(saved);
        CompoundTag attachment = tag.getCompoundOrEmpty("neoforge:attachments").getCompoundOrEmpty("orevault:player");
        check(helper, "saved under its id", data.write(), attachment);

        FakePlayer loaded = player(helper.getLevel(), "orevault-loaded");
        load(loaded, tag);
        check(helper, "loaded", data, PlayerAttachment.get(loaded));
        helper.succeed();
    }

    /** S17: a tag written without {@code in_vault} loads through the real wrapper, keeping the rest. */
    static void aTagWithoutInVaultLoads(GameTestHelper helper) {
        FakePlayer saved = player(helper.getLevel(), "orevault-old-tag");
        ReturnPos home = new ReturnPos("minecraft:overworld", 9, 65, 9);
        PlayerAttachment.update(saved, data -> data.withReturnPos(home).withInVault(new InVault(UUID.randomUUID(), 0)));
        CompoundTag tag = save(saved);
        tag.getCompoundOrEmpty("neoforge:attachments").getCompoundOrEmpty("orevault:player").remove("in_vault");

        FakePlayer loaded = player(helper.getLevel(), "orevault-new-build");
        load(loaded, tag);
        check(helper, "loaded", new PlayerData(Optional.of(home), Optional.empty()), PlayerAttachment.get(loaded));
        helper.succeed();
    }

    /**
     * {@code copyOnDeath}: the death respawn's clone, {@code restoreFrom} with {@code restoreAll} false, which posts the
     * {@code PlayerEvent.Clone} NeoForge copies attachments from. A fake player cannot respawn through
     * {@code PlayerList#respawn}, so this is the step of it that copies; the whole respawn is a playtest step.
     */
    static void theAttachmentSurvivesDeath(GameTestHelper helper) {
        PlayerData data = PlayerData.DEFAULTS.withReturnPos(new ReturnPos("minecraft:overworld", 5, 66, 5));
        FakePlayer dead = player(helper.getLevel(), "orevault-dead");
        PlayerAttachment.update(dead, ignored -> data);
        FakePlayer respawned = player(helper.getLevel(), "orevault-respawned");

        respawned.restoreFrom(dead, false);

        check(helper, "copied", data, PlayerAttachment.get(respawned));
        helper.succeed();
    }

    /**
     * The AC: a player who dies inside and respawns in the Overworld is not moved by the login check. The clone copies
     * {@code in_vault}; the respawn event, which is all {@code PlayerList#respawn} posts, clears it.
     */
    static void aDeathInsideClearsInVaultAtRespawn(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        ServerLevel vault = createVault(helper, team);
        FakePlayer dead = player(vault, "orevault-died-inside");
        VaultPlayerEvents.recordWhereTheyAre(dead);
        check(helper, "in_vault inside", Optional.of(new InVault(team, 0)), PlayerAttachment.get(dead).inVault());
        FakePlayer respawned = player(server.overworld(), "orevault-respawned-outside");
        respawned.restoreFrom(dead, false);
        check(helper, "copied by the clone", Optional.of(new InVault(team, 0)), PlayerAttachment.get(respawned).inVault());

        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(respawned, false));

        check(helper, "cleared", Optional.empty(), PlayerAttachment.get(respawned).inVault());
        check(helper, "login check", Outcome.STAY, VaultPlayerEvents.checkAtLogin(respawned));
        helper.succeed();
    }

    /** S14, login case 1: in_vault names a Vault that is not loaded, so vanilla put them in the Overworld; sent home. */
    static void aLoginRecordedInAVaultThatDidNotLoadGoesHome(GameTestHelper helper) {
        ServerLevel overworld = server(helper).overworld();
        FakePlayer player = player(overworld, "orevault-stranded");
        player.setPos(0.5, 251, 0.5);
        ReturnPos home = new ReturnPos("minecraft:overworld", 12, 70, -8);
        PlayerAttachment.update(player, data -> data.withReturnPos(home).withInVault(new InVault(UUID.randomUUID(), 0)));

        check(helper, "outcome", Outcome.SEND_HOME, VaultPlayerEvents.checkAtLogin(player));

        // The move itself is dropped by a fake player's connection; where home is, the home test reads.
        check(helper, "home", new Vec3(12.5, 70, -7.5), VaultTravel.home(player, TeleportTransition.DO_NOTHING).position());
        check(helper, "in_vault", Optional.empty(), PlayerAttachment.get(player).inVault());
        check(helper, "return_pos kept", Optional.of(home), PlayerAttachment.get(player).returnPos());
        helper.succeed();
    }

    /** Login case 2: a player standing in a Vault whose team is not theirs is sent out (RV-8). */
    static void aLoginInsideAnotherTeamsVaultGoesHome(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        ServerLevel vault = createVault(helper, team);
        FakePlayer player = player(vault, "orevault-visitor");
        player.setPos(0.5, BASE_SURFACE, 0.5);
        PlayerAttachment.update(player, data -> data.withInVault(new InVault(team, 0)));

        check(helper, "outcome", Outcome.SEND_HOME_NOT_THEIR_TEAM, VaultPlayerEvents.checkAtLogin(player));

        check(helper, "left the Vault", false, player.level().dimension().equals(vault.dimension()));
        check(helper, "in_vault", Optional.empty(), PlayerAttachment.get(player).inVault());
        discard(player);
        helper.succeed();
    }

    // ---- the debug command -----------------------------------------------------------------------------------------

    /** The key a debug line names, for the restart script: {@code orevault:vault_<hex>}. */
    static String dimension(UUID team) {
        return OreVault.MOD_ID + ":" + VaultKeys.dimensionPath(team);
    }
}
