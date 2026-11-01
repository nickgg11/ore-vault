package com.orevault.orevault.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.team.TeamHelper.TeamChange;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.ServerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * {@code TeamHelper} against the real FTB Teams manager, with the player offline. A mock {@code ServerPlayer} cannot be
 * used: {@code placeNewPlayer} raises the login event, FTB Library then sends a payload the mock connection never
 * negotiated, and the test dies before any of this code runs. So FTB Teams is told of a player with no
 * {@code ServerPlayer}, which it supports, and every event here is one it posted. What an online player adds is a
 * playtest step: E3-S8's PR lists it.
 */
final class TeamTests {
    private TeamTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static TeamManagerImpl manager() {
        return TeamManagerImpl.INSTANCE;
    }

    /** §9: an unknown player is their own team of one, and a team nobody is in is not size 0. */
    static void anUnknownIdIsATeamOfOne(GameTestHelper helper) {
        UUID stranger = UUID.randomUUID();
        check(helper, "team id", stranger, TeamHelper.teamId(stranger));
        check(helper, "size", 1, TeamHelper.size(stranger));
        check(helper, "members", Set.of(), TeamHelper.members(stranger));
        check(helper, "online", 0, TeamHelper.onlineMembers(stranger).size());
        helper.succeed();
    }

    static void aSoloPlayerIsTheirOwnTeam(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        manager().playerLoggedIn(null, id, "orevault-solo");
        check(helper, "team id", id, TeamHelper.teamId(id));
        check(helper, "members", Set.of(id), TeamHelper.members(id));
        check(helper, "size", 1, TeamHelper.size(id));
        helper.succeed();
    }

    /** Joining a party and leaving it as the last member, which is the only disband there is (§9). */
    static void aPartyChangesTheTeamAndItsLastMemberDisbandsIt(GameTestHelper helper) {
        List<TeamChange> changes = new ArrayList<>();
        List<UUID> disbands = new ArrayList<>();
        TeamHelper.onTeamChange(changes::add);
        TeamHelper.onDisband(disbands::add);

        UUID id = UUID.randomUUID();
        manager().playerLoggedIn(null, id, "orevault-party");
        check(helper, "first login", List.of(new TeamChange(id, null, id)), of(changes, id));

        try {
            PartyTeam party = manager().createParty(id, null, "orevault-test-" + id, "", null);
            UUID partyId = party.getId();
            check(helper, "joined", new TeamChange(id, id, partyId), of(changes, id).getLast());
            check(helper, "team id in a party", partyId, TeamHelper.teamId(id));
            check(helper, "party members", Set.of(id), TeamHelper.members(partyId));
            // The personal team stays, with nobody in it: FTB Teams' own road to a size of 0.
            check(helper, "personal team members", Set.of(), TeamHelper.members(id));
            check(helper, "personal team size", 1, TeamHelper.size(id));

            party.leave(id);
            check(helper, "left", new TeamChange(id, partyId, id), of(changes, id).getLast());
            check(helper, "disbanded", List.of(partyId), disbands);
            check(helper, "team id after", id, TeamHelper.teamId(id));

            // A server team's delete posts the same event, and no Vault is keyed on it: not a disband (§9).
            CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack();
            ServerTeam serverTeam = (ServerTeam) manager().createServerTeam(source, "orevault-server-" + id, "", null);
            serverTeam.delete(source);
            check(helper, "after a server team's delete", List.of(partyId), disbands);
        } catch (CommandSyntaxException e) {
            helper.fail(Component.literal(e.getMessage()));
        }
        helper.succeed();
    }

    private static List<TeamChange> of(List<TeamChange> changes, UUID playerId) {
        return changes.stream().filter(change -> change.playerId().equals(playerId)).toList();
    }
}
