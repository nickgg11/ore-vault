package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.TeamFields;
import com.orevault.orevault.data.VaultTypeFile;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Team data against the real server storage and the real FTB Teams path (§11 SavedData Pattern, ADR 0005). The shape's
 * rules are unit tests over {@code TeamFields}; these check what needs a running server. A gametest is one server lifetime
 * and {@code SavedDataStorage#get} cannot drop a cached entry, so the restart check is E3-S10's script.
 */
final class TeamDataTests {
    private static final byte[] GARBAGE = {1, 2, 3, 4, 5, 6, 7, 8};

    private TeamDataTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static TeamManagerImpl manager() {
        return TeamManagerImpl.INSTANCE;
    }

    private static PartyTeam partyOf(UUID member) throws CommandSyntaxException {
        manager().playerLoggedIn(null, member, "orevault-data");
        return manager().createParty(member, null, "orevault-data-" + member, "", null);
    }

    /** The codec the storage itself uses, from a tag and back to the same tag. */
    static void theCodecRoundTrips(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        Tag tag = new TeamFields(3, OptionalLong.of(77L)).write(team);
        Codec<OreVaultTeamData> codec = OreVaultTeamData.codec(team);
        OreVaultTeamData decoded = codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
        check(helper, "fields", new TeamFields(3, OptionalLong.of(77L)), decoded.fields());
        check(helper, "tag", tag, codec.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow());
        helper.succeed();
    }

    /** ADR 0005: {@code <world>/data/orevault/team_<hex>.dat}, the server's storage and not the overworld's. */
    static void aWriteLandsInTheServersStorage(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        OreVaultTeamData.forWrite(server, team).markDisbanded(5L);
        server.getDataStorage().saveAndJoin();

        Path file = DataPaths.teamFile(server, team);
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            check(helper, "the file's data compound", 5L, root.getCompoundOrEmpty("data").getLongOr("disbanded_at", -1L));
        } catch (IOException e) {
            helper.fail(Component.literal("no team file at " + file + ": " + e));
        }
        check(helper, "the overworld's own storage", null, server.overworld().getDataStorage().get(OreVaultTeamData.type(team)));
        check(helper, "a read after the write", OptionalLong.of(5L), OreVaultTeamData.read(server, team).disbandedAt());
        helper.succeed();
    }

    /** A read creates nothing, so a player who logs in and never mines has no file (ADR 0005 section 2). */
    static void aReadWritesNothing(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        check(helper, "defaults", TeamFields.DEFAULTS, OreVaultTeamData.read(server, team));
        check(helper, "record", false, OreVaultTeamData.hasRecord(server, team));
        check(helper, "cached", null, server.getDataStorage().get(OreVaultTeamData.type(team)));
        server.getDataStorage().saveAndJoin();
        check(helper, "file", false, Files.exists(DataPaths.teamFile(server, team)));
        helper.succeed();
    }

    /**
     * S18, through the real subscriber: FTB Teams posts a team change with a null previous team when it makes a personal
     * team, and another on a party join. Neither is a reason to write.
     */
    static void aFirstLoginAndAPartyJoinWriteNothing(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = server(helper);
        UUID player = UUID.randomUUID();
        UUID party = partyOf(player).getId();
        check(helper, "personal team", false, OreVaultTeamData.hasRecord(server, player));
        check(helper, "party", false, OreVaultTeamData.hasRecord(server, party));
        helper.succeed();
    }

    /** §3.2: FTB Teams answers a fake player with a phantom id, so nothing may be recorded for one. */
    static void aFakePlayerIsRefusedBeforeAnythingIsWritten(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID id = UUID.randomUUID();
        ServerPlayer fake = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "orevault-fake"));
        check(helper, "accessor", Optional.empty(), OreVaultTeamData.forWrite(fake, id));
        check(helper, "record", false, OreVaultTeamData.hasRecord(server, id));
        helper.succeed();
    }

    static void aRealPlayerIsWrittenThrough(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        UUID id = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(id, "orevault-real"),
                ClientInformation.createDefault());
        check(helper, "accessor", true, OreVaultTeamData.forWrite(player, id).isPresent());
        check(helper, "record", true, OreVaultTeamData.hasRecord(server, id));
        helper.succeed();
    }

    /** ADR 0007's Disband step 1, through FTB Teams' own path: set and saved at once. */
    static void aDisbandSetsDisbandedAtAndSaves(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = server(helper);
        UUID player = UUID.randomUUID();
        PartyTeam party = partyOf(player);
        UUID partyId = party.getId();
        OreVaultTeamData.forWrite(server, partyId);
        long now = server.overworld().getGameTime();

        party.leave(player);

        check(helper, "disbanded_at", OptionalLong.of(now), OreVaultTeamData.read(server, partyId).disbandedAt());
        check(helper, "saved", true, Files.exists(DataPaths.teamFile(server, partyId)));
        helper.succeed();
    }

    /** "If the team never wrote data and has no Vault folder, there is nothing to archive and nothing is written." */
    static void aDisbandWithNothingToArchiveWritesNothing(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = server(helper);
        UUID player = UUID.randomUUID();
        PartyTeam party = partyOf(player);
        UUID partyId = party.getId();

        party.leave(player);

        check(helper, "record", false, OreVaultTeamData.hasRecord(server, partyId));
        helper.succeed();
    }

    /** A team that entered and never recorded anything has a folder and no file, and that folder must still be archived. */
    static void aDisbandWithOnlyAVaultFolderIsRecorded(GameTestHelper helper)
            throws CommandSyntaxException, IOException {
        MinecraftServer server = server(helper);
        UUID player = UUID.randomUUID();
        PartyTeam party = partyOf(player);
        UUID partyId = party.getId();
        VaultTypeFile.write(DataPaths.vaultFolder(server, partyId), VaultVariant.BASE);

        party.leave(player);

        check(helper, "disbanded", true, OreVaultTeamData.read(server, partyId).disbandedAt().isPresent());
        helper.succeed();
    }

    /** ADR 0005 section 2: the bytes are kept beside the file before anything can overwrite them. */
    static void anUnreadableFileIsCopiedAsideBeforeTheFirstWrite(GameTestHelper helper) throws IOException {
        MinecraftServer server = server(helper);
        UUID team = UUID.randomUUID();
        Path file = DataPaths.teamFile(server, team);
        Files.createDirectories(file.getParent());
        Files.write(file, GARBAGE);
        long now = server.overworld().getGameTime();
        Path copy = file.resolveSibling(file.getFileName() + ".unreadable-" + now);

        check(helper, "a read keeps the defaults", TeamFields.DEFAULTS, OreVaultTeamData.read(server, team));
        check(helper, "a read copies nothing", false, Files.exists(copy));

        OreVaultTeamData.forWrite(server, team).markDisbanded(9L);

        check(helper, "the copy's bytes", true, Files.exists(copy) && Arrays.equals(GARBAGE, Files.readAllBytes(copy)));
        server.getDataStorage().saveAndJoin();
        check(helper, "the new file", 9L, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
                .getCompoundOrEmpty("data").getLongOr("disbanded_at", -1L));
        helper.succeed();
    }
}
