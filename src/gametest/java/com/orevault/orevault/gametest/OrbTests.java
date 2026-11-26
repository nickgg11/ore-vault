package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.orevault.orevault.core.curve.TeamScaling;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.mining.OrbFields;
import com.orevault.orevault.mining.ResonanceOrbEntity;
import com.orevault.orevault.mining.VaultOrbEntity;
import com.orevault.orevault.registry.ModEntities;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Resonance orb in a real level (§4.2, §11 Resonance Orb Entity; sketch #260). A test player is a real
 * {@link ServerPlayer}, not a fake one, because the orb refuses fake players; it has a mock connection so the server can
 * pair it with the orb, and it does not tick, so it never touches anything itself: collection in these tests is always a
 * direct {@code playerTouch}, and attraction is the orb's pull alone. A player FTB Teams does not know is their own team
 * of one ({@code TeamHelper}), so an orb whose team is the player's UUID is the player's.
 */
final class OrbTests {
    /**
     * The orbs' block. The empty structure is one block walled in barrier, so each test clears its own floor and air first
     * ({@link #arena}); an orb spawned in a barrier is pushed out sideways and leaves the test.
     */
    private static final BlockPos ORB_AT = new BlockPos(0, 1, 0);
    private static final long VALUE = 12_500L;

    private OrbTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** Stone at y 0 and air above, from one block behind the orbs to three past them along x. */
    private static void arena(GameTestHelper helper) {
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** The bottom centre of the block {@code dx} along x from the orbs' block, in the arena. */
    private static Vec3 at(GameTestHelper helper, int dx) {
        return Vec3.atBottomCenterOf(helper.absolutePos(ORB_AT.offset(dx, 0, 0)));
    }

    /** A real, non-fake player standing still at {@code pos}, with a mock connection (see the class comment). */
    private static ServerPlayer player(GameTestHelper helper, Vec3 pos) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "orevault-orb");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation()) {
            @Override
            public void tick() {
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
        player.setPos(pos);
        level.addNewPlayer(player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    /** An orb at rest: the spawn's scatter is cleared so a distance measures the pull and nothing else. */
    private static ResonanceOrbEntity orb(GameTestHelper helper, Vec3 pos, UUID team, long value) {
        ResonanceOrbEntity orb = ResonanceOrbEntity.spawn(helper.getLevel(), pos, team, value)
                .orElseThrow(() -> new IllegalStateException("no orb spawned"));
        orb.setDeltaMovement(Vec3.ZERO);
        return orb;
    }

    private static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- attraction and pickup -------------------------------------------------------------------------------------

    /** A non-member two blocks away for 40 ticks: no pull, a touch does nothing, and the orb's team gets no file. */
    static void aNonMemberNeitherAttractsNorCollects(GameTestHelper helper) {
        arena(helper);
        ServerPlayer stranger = player(helper, at(helper, 2));
        UUID team = UUID.randomUUID();
        ResonanceOrbEntity orb = orb(helper, at(helper, 0), team, VALUE);
        double start = horizontal(orb, stranger);
        helper.runAfterDelay(40, () -> {
            double end = horizontal(orb, stranger);
            if (Math.abs(end - start) > 0.01) {
                remove(stranger);
                helper.fail(Component.literal("a non-member moved the orb from " + start + " to " + end + "; orb at " + orb.position()
                        + ", player at " + stranger.position() + ", floor " + helper.getLevel().getBlockState(orb.blockPosition().below())));
                return;
            }
            orb.playerTouch(stranger);
            check(helper, "alive after a non-member's touch", false, orb.isRemoved());
            check(helper, "the orb's team has no record", false, OreVaultTeamData.hasRecord(server(helper), team));
            remove(stranger);
            orb.discard();
            helper.succeed();
        });
    }

    /** The member counterpart: the pull alone closes the distance within 40 ticks, then a touch pays the scaled value. */
    static void aMemberAttractsAndCollects(GameTestHelper helper) {
        arena(helper);
        ServerPlayer member = player(helper, at(helper, 2));
        UUID team = member.getUUID();
        ResonanceOrbEntity orb = orb(helper, at(helper, 0), team, VALUE);
        double start = horizontal(orb, member);
        helper.runAfterDelay(40, () -> {
            double end = horizontal(orb, member);
            if (end > start - 0.5) {
                remove(member);
                helper.fail(Component.literal("the member's pull moved the orb only from " + start + " to " + end));
                return;
            }
            orb.playerTouch(member);
            check(helper, "removed after the member's touch", true, orb.isRemoved());
            check(helper, "pool", TeamScaling.poolGainThousandths(VALUE, 1),
                    OreVaultTeamData.read(server(helper), team).resonancePool());
            remove(member);
            helper.succeed();
        });
    }

    /** ADR 0005 section 10 as amended: a disbanded team's orb and a team-less orb pay nothing, and nothing throws. */
    static void aDisbandedOrTeamlessOrbPaysNothing(GameTestHelper helper) {
        arena(helper);
        MinecraftServer server = server(helper);
        ServerPlayer member = player(helper, at(helper, 2));
        UUID team = member.getUUID();
        OreVaultTeamData data = OreVaultTeamData.forWrite(server, team);
        data.addResonance(1_000L);
        data.markDisbanded(server.overworld().getGameTime());

        ResonanceOrbEntity disbanded = orb(helper, at(helper, 0), team, VALUE);
        disbanded.playerTouch(member);
        check(helper, "disbanded: pool unchanged", 1_000L, OreVaultTeamData.read(server, team).resonancePool());
        check(helper, "disbanded: consumed", true, disbanded.isRemoved());

        ResonanceOrbEntity teamless = orb(helper, at(helper, 0), team, VALUE);
        load(teamless, saveWithFields(teamless, new OrbFields(Optional.empty(), VALUE, 1, 0)));
        teamless.playerTouch(member);
        check(helper, "team-less: pool unchanged", 1_000L, OreVaultTeamData.read(server, team).resonancePool());
        check(helper, "team-less: not consumed", false, teamless.isRemoved());
        teamless.discard();
        remove(member);
        helper.succeed();
    }

    // ---- save data -------------------------------------------------------------------------------------------------

    private static CompoundTag save(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static void load(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), tag));
    }

    private static CompoundTag saveWithFields(Entity entity, OrbFields fields) {
        CompoundTag tag = save(entity);
        tag.put("orb", fields.write());
        return tag;
    }

    /** The entity's own save and load, nested under {@code orb}; a tag without {@code orb} loads at the defaults. */
    static void theEntityTagRoundTrips(GameTestHelper helper) {
        OrbFields fields = new OrbFields(Optional.of(UUID.randomUUID()), VALUE, 3, 100);
        ResonanceOrbEntity saved = new ResonanceOrbEntity(ModEntities.RESONANCE_ORB.get(), helper.getLevel());
        load(saved, saveWithFields(saved, fields));
        CompoundTag tag = save(saved);
        check(helper, "nested under orb", fields.write(), tag.getCompoundOrEmpty("orb"));

        ResonanceOrbEntity loaded = new ResonanceOrbEntity(ModEntities.RESONANCE_ORB.get(), helper.getLevel());
        load(loaded, tag);
        check(helper, "fields", fields, loaded.fields());
        check(helper, "synced value pushed on load", VALUE, loaded.syncedValue());

        CompoundTag old = save(saved);
        old.remove("orb");
        ResonanceOrbEntity bare = new ResonanceOrbEntity(ModEntities.RESONANCE_ORB.get(), helper.getLevel());
        load(bare, old);
        check(helper, "without orb", OrbFields.DEFAULTS, bare.fields());
        helper.succeed();
    }

    /** Balancer, #260: a loaded orb of value 0 (a {@code /summon}, an edited save) is discarded on its first tick. */
    static void aZeroValueOrbIsDiscardedOnItsFirstTick(GameTestHelper helper) {
        arena(helper);
        ResonanceOrbEntity orb = new ResonanceOrbEntity(ModEntities.RESONANCE_ORB.get(), helper.getLevel());
        load(orb, saveWithFields(orb, new OrbFields(Optional.of(UUID.randomUUID()), 0L, 1, 0)));
        orb.setPos(at(helper, 0));
        helper.getLevel().addFreshEntity(orb);
        check(helper, "spawn refuses 0", Optional.empty(),
                ResonanceOrbEntity.spawn(helper.getLevel(), at(helper, 0), UUID.randomUUID(), 0L));
        helper.runAfterDelay(2, () -> {
            check(helper, "discarded", true, orb.isRemoved());
            helper.succeed();
        });
    }

    // ---- damage, merge, type ---------------------------------------------------------------------------------------

    static void nothingDamagesIt(GameTestHelper helper) {
        arena(helper);
        ServerLevel level = helper.getLevel();
        ResonanceOrbEntity orb = orb(helper, at(helper, 0), UUID.randomUUID(), VALUE);
        List<DamageSource> sources = List.of(level.damageSources().generic(), level.damageSources().lava(),
                level.damageSources().explosion(null, null), level.damageSources().inFire());
        for (DamageSource source : sources) {
            check(helper, "hurt by " + source.getMsgId(), false, orb.hurtServer(level, source, 1000.0F));
        }
        check(helper, "attackable", false, orb.isAttackable());
        check(helper, "fire immune", true, orb.fireImmune());
        check(helper, "alive", false, orb.isRemoved());
        orb.discard();
        helper.succeed();
    }

    /** Two same-team orbs merge within a scan, value and {@code orb_count} conserved; another team's orb stays apart. */
    static void sameTeamOrbsMergeAndConserveValue(GameTestHelper helper) {
        arena(helper);
        UUID team = UUID.randomUUID();
        ResonanceOrbEntity a = orb(helper, at(helper, 0), team, VALUE);
        ResonanceOrbEntity b = orb(helper, at(helper, 0), team, 2_000L);
        ResonanceOrbEntity stranger = orb(helper, at(helper, 0), UUID.randomUUID(), 5_000L);
        helper.runAfterDelay(21, () -> {
            List<VaultOrbEntity> left = helper.getLevel().getEntitiesOfClass(VaultOrbEntity.class,
                    new AABB(helper.absolutePos(ORB_AT)).inflate(2.0), orb -> !orb.isRemoved());
            List<VaultOrbEntity> ours = left.stream().filter(orb -> orb.fields().team().equals(Optional.of(team))).toList();
            check(helper, "one orb of the team", 1, ours.size());
            if (ours.size() == 1) {
                check(helper, "value conserved", VALUE + 2_000L, ours.getFirst().fields().value());
                check(helper, "synced value", VALUE + 2_000L, ours.getFirst().syncedValue());
                check(helper, "orb_count summed", 2, ours.getFirst().fields().orbCount());
            }
            check(helper, "exactly one of the pair removed", true, a.isRemoved() != b.isRemoved());
            check(helper, "the other team's orb kept apart", false, stranger.isRemoved());
            check(helper, "the other team's value", 5_000L, stranger.fields().value());
            left.forEach(Entity::discard);
            helper.succeed();
        });
    }

    /** §11: the client only interpolates, so the server must send positions often. */
    static void theTypeUpdatesEveryTwoTicks(GameTestHelper helper) {
        check(helper, "updateInterval", 2, ModEntities.RESONANCE_ORB.get().updateInterval());
        check(helper, "clientTrackingRange", 6, ModEntities.RESONANCE_ORB.get().clientTrackingRange());
        check(helper, "fireImmune", true, ModEntities.RESONANCE_ORB.get().fireImmune());
        helper.succeed();
    }
}
