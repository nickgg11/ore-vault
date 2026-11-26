package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.mining.DroppedXp;
import com.orevault.orevault.mining.ResonanceOrbEntity;
import com.orevault.orevault.vault.VaultDimensions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The drop pipeline's break context and ore award in a real Vault (§3.4, §4.2, §11 Drop Pipeline; E5-S3, #264). A hand
 * break needs a {@code ServerPlayer} the player list holds, and {@code placeNewPlayer} cannot be used with a mock
 * connection ({@code TeamTests}), so {@link #listed} puts the test player in the list's UUID map by reflection and
 * {@link #unlist} takes it out; that map is all {@code PlayerList#getPlayer(UUID)} reads. The player does not tick, so it
 * never picks up an orb or an XP orb. A player FTB Teams does not know is their own team of one, so a Vault made for the
 * player's UUID is their team's.
 */
final class DropTests {
    /** Above the surface (Y=100), so the block is placed into air and nothing else is near it. */
    private static final int Y = 110;

    private DropTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static ServerLevel vault(GameTestHelper helper, UUID team) {
        return VaultDimensions.findOrCreate(server(helper), team)
                .orElseThrow(() -> new IllegalStateException("no Vault for " + team));
    }

    /** A real, non-fake survival player in {@code level} with a mock connection and a diamond pickaxe, not yet listed. */
    private static ServerPlayer player(ServerLevel level, UUID id) {
        GameProfile profile = new GameProfile(id, "orevault-miner");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation()) {
            @Override
            public void tick() {
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.setPos(0.5, Y, -2.5);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(MinecraftServer server) {
        try {
            Field field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>) field.get(server.getPlayerList());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PlayerList#playersByUUID", e);
        }
    }

    private static ServerPlayer listed(ServerLevel level, UUID id) {
        ServerPlayer player = player(level, id);
        byUuid(level.getServer()).put(player.getUUID(), player);
        return player;
    }

    private static void unlist(ServerPlayer player) {
        byUuid(player.level().getServer()).remove(player.getUUID(), player);
    }

    private static BlockPos place(ServerLevel level, int x, Block block) {
        BlockPos pos = new BlockPos(x, Y, 0);
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        return pos;
    }

    private static List<ResonanceOrbEntity> orbsAt(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ResonanceOrbEntity.class, new AABB(pos).inflate(2.0));
    }

    private static int xpAt(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(2.0)).stream()
                .mapToInt(ExperienceOrb::getValue).sum();
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        orbsAt(level, pos).forEach(ResonanceOrbEntity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(2.0)).forEach(ExperienceOrb::discard);
    }

    /** One orb of the expected value and team at the block, or a failure naming what was there. */
    private static void oneOrb(GameTestHelper helper, String what, ServerLevel level, BlockPos pos, UUID team, long value) {
        List<ResonanceOrbEntity> orbs = orbsAt(level, pos);
        check(helper, what + ": orbs", 1, orbs.size());
        if (orbs.size() == 1) {
            check(helper, what + ": value", value, orbs.getFirst().fields().value());
            check(helper, what + ": team", Optional.of(team), orbs.getFirst().fields().team());
        }
    }

    /** §4.2: a member's hand break of a common, uncommon and rare ore spawns one orb of 2, 5 and 12.5 at the block. */
    static void aHandBreakPaysByRarity(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerLevel vault = vault(helper, id);
        ServerPlayer miner = listed(vault, id);
        try {
            BlockPos coal = place(vault, 0, Blocks.COAL_ORE);
            BlockPos lapis = place(vault, 8, Blocks.LAPIS_ORE);
            BlockPos diamond = place(vault, 16, Blocks.DIAMOND_ORE);
            check(helper, "coal broken", true, miner.gameMode.destroyBlock(coal));
            check(helper, "lapis broken", true, miner.gameMode.destroyBlock(lapis));
            check(helper, "diamond broken", true, miner.gameMode.destroyBlock(diamond));
            oneOrb(helper, "coal", vault, coal, miner.getUUID(), 2_000L);
            oneOrb(helper, "lapis", vault, lapis, miner.getUUID(), 5_000L);
            oneOrb(helper, "diamond", vault, diamond, miner.getUUID(), 12_500L);
            clear(vault, coal);
            clear(vault, lapis);
            clear(vault, diamond);
        } finally {
            unlist(miner);
        }
        helper.succeed();
    }

    /** §3.4: the orb is an addition. Diamond ore's vanilla 3 to 7 XP still drops beside it. */
    static void vanillaXpStillDrops(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerLevel vault = vault(helper, id);
        ServerPlayer member = listed(vault, id);
        try {
            BlockPos diamond = place(vault, 0, Blocks.DIAMOND_ORE);
            check(helper, "broken", true, member.gameMode.destroyBlock(diamond));
            oneOrb(helper, "orb beside the XP", vault, diamond, id, 12_500L);
            int xp = xpAt(vault, diamond);
            if (xp < 3 || xp > 7) {
                helper.fail(Component.literal("diamond ore dropped " + xp + " XP, vanilla is 3 to 7"));
            }
            clear(vault, diamond);
        } finally {
            unlist(member);
        }
        helper.succeed();
    }

    /**
     * S21, old #26: a fake player, a real {@code ServerPlayer} the player list does not hold, and a break with no breaker
     * all pay nothing, each on a rare ore in their own Vault whose team is theirs, so only the machine rule can refuse it.
     * Vanilla XP still drops for the fake player's break (§3.4).
     */
    static void machineBreaksPayNothing(GameTestHelper helper) {
        UUID fakeId = UUID.randomUUID();
        ServerLevel fakeVault = vault(helper, fakeId);
        FakePlayer fake = FakePlayerFactory.get(fakeVault, new GameProfile(fakeId, "orevault-quarry"));
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos byFake = place(fakeVault, 0, Blocks.DIAMOND_ORE);
        check(helper, "fake: broken", true, fake.gameMode.destroyBlock(byFake));
        check(helper, "fake: orbs", 0, orbsAt(fakeVault, byFake).size());
        check(helper, "fake: vanilla XP", true, xpAt(fakeVault, byFake) >= 3);
        clear(fakeVault, byFake);

        // A real ServerPlayer whose isFakePlayer() is false but which the player list does not hold: what a mod's fake
        // player that extends ServerPlayer without overriding isFakePlayer() looks like.
        UUID impostorId = UUID.randomUUID();
        ServerLevel unlistedVault = vault(helper, impostorId);
        ServerPlayer impostor = player(unlistedVault, impostorId);
        BlockPos byImpostor = place(unlistedVault, 0, Blocks.DIAMOND_ORE);
        check(helper, "unlisted: broken", true, impostor.gameMode.destroyBlock(byImpostor));
        check(helper, "unlisted: orbs", 0, orbsAt(unlistedVault, byImpostor).size());
        clear(unlistedVault, byImpostor);

        UUID team = UUID.randomUUID();
        ServerLevel nobodysVault = vault(helper, team);
        BlockPos byNobody = place(nobodysVault, 0, Blocks.DIAMOND_ORE);
        check(helper, "no breaker: broken", true, nobodysVault.destroyBlock(byNobody, true));
        check(helper, "no breaker: orbs", 0, orbsAt(nobodysVault, byNobody).size());
        check(helper, "no breaker: no team record", false, OreVaultTeamData.hasRecord(server(helper), team));
        clear(nobodysVault, byNobody);
        helper.succeed();
    }

    /** §3.4, §4.2: a listed real player not on the Vault's team breaks ore there; nothing spawns and nothing is written. */
    static void aNonMemberPaysAndRecordsNothing(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        ServerLevel vault = vault(helper, team);
        ServerPlayer stranger = listed(vault, UUID.randomUUID());
        try {
            BlockPos diamond = place(vault, 0, Blocks.DIAMOND_ORE);
            check(helper, "broken", true, stranger.gameMode.destroyBlock(diamond));
            check(helper, "orbs", 0, orbsAt(vault, diamond).size());
            check(helper, "the Vault's team has no record", false, OreVaultTeamData.hasRecord(server(helper), team));
            check(helper, "the stranger's team has no record", false,
                    OreVaultTeamData.hasRecord(server(helper), stranger.getUUID()));
            clear(vault, diamond);
        } finally {
            unlist(stranger);
        }
        helper.succeed();
    }

    /**
     * §9: the listener does not receive a cancelled event. The same event uncancelled pays, which shows the post reaches
     * the listener; stone, posted the same way, pays nothing (§4.2: no default rarity).
     */
    static void aCancelledEventAndStonePayNothing(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerLevel vault = vault(helper, id);
        ServerPlayer member = listed(vault, id);
        try {
            // Placed, though nothing breaks them, so their chunks are loaded and an orb spawned there would be seen.
            BlockPos cancelledAt = place(vault, 0, Blocks.DIAMOND_ORE);
            BlockDropsEvent cancelled = event(vault, cancelledAt, Blocks.DIAMOND_ORE, member);
            cancelled.setCanceled(true);
            NeoForge.EVENT_BUS.post(cancelled);
            check(helper, "cancelled: orbs", 0, orbsAt(vault, cancelledAt).size());

            BlockPos stoneAt = place(vault, 8, Blocks.STONE);
            NeoForge.EVENT_BUS.post(event(vault, stoneAt, Blocks.STONE, member));
            check(helper, "stone: orbs", 0, orbsAt(vault, stoneAt).size());

            BlockPos paidAt = place(vault, 16, Blocks.DIAMOND_ORE);
            NeoForge.EVENT_BUS.post(event(vault, paidAt, Blocks.DIAMOND_ORE, member));
            oneOrb(helper, "uncancelled", vault, paidAt, member.getUUID(), 12_500L);
            clear(vault, paidAt);
        } finally {
            unlist(member);
        }
        helper.succeed();
    }

    /**
     * §3.4, §11: the one XP helper never lowers a block's XP and adds a bonus on top of vanilla's. No XP node exists yet
     * (they come with E9), so "every XP node owned shows vanilla XP at least the vanilla amount" is this: every node goes
     * through {@link DroppedXp#add}, and a negative bonus leaves the vanilla amount in the real event.
     */
    static void theXpHelperNeverLowersVanilla(GameTestHelper helper) {
        ServerLevel vault = vault(helper, UUID.randomUUID());
        BlockDropsEvent event = event(vault, new BlockPos(0, Y, 0), Blocks.DIAMOND_ORE, null);
        int vanilla = event.getDroppedExperience();
        check(helper, "vanilla in range", true, vanilla >= 3 && vanilla <= 7);
        DroppedXp.add(event, -5);
        check(helper, "after a negative bonus", vanilla, event.getDroppedExperience());
        DroppedXp.add(event, Integer.MIN_VALUE);
        check(helper, "after the lowest int", vanilla, event.getDroppedExperience());
        DroppedXp.add(event, 2);
        check(helper, "after +2", vanilla + 2, event.getDroppedExperience());
        helper.succeed();
    }

    private static BlockDropsEvent event(ServerLevel level, BlockPos pos, Block block, ServerPlayer breaker) {
        return new BlockDropsEvent(level, pos, block.defaultBlockState(), null, new ArrayList<>(), breaker,
                new ItemStack(Items.DIAMOND_PICKAXE));
    }
}
