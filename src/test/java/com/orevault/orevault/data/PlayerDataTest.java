package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.data.PlayerData.InVault;
import com.orevault.orevault.data.PlayerData.ReturnPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** The player attachment's save shape (ADR 0005 sections 5 and 6, S17): keys, defaults, and that no read throws. */
class PlayerDataTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final ReturnPos HOME = new ReturnPos("minecraft:overworld", 10, 64, -3);
    private static final InVault INSIDE = new InVault(TEAM, 2);

    private final List<String> warnings = new ArrayList<>();

    private PlayerData read(CompoundTag tag) {
        return PlayerData.read(tag, warnings::add);
    }

    @Test
    void theGoldenKeySetIsPinned() {
        CompoundTag tag = new PlayerData(Optional.of(HOME), Optional.of(INSIDE)).write();
        assertEquals(Set.of("data_version", "return_pos", "in_vault"), tag.keySet());
        assertEquals(Set.of("dim", "x", "y", "z"), tag.getCompoundOrEmpty("return_pos").keySet());
        assertEquals(Set.of("team", "generation"), tag.getCompoundOrEmpty("in_vault").keySet());
    }

    @Test
    void theDefaultsWriteOnlyTheVersion() {
        assertEquals(Set.of("data_version"), PlayerData.DEFAULTS.write().keySet());
        assertEquals(1, PlayerData.DEFAULTS.write().getIntOr("data_version", 0));
    }

    @Test
    void theDefaultsHoldNoReturnPositionAndNoVault() {
        assertEquals(Optional.empty(), PlayerData.DEFAULTS.returnPos());
        assertEquals(Optional.empty(), PlayerData.DEFAULTS.inVault());
    }

    @Test
    void aWrittenTagReadsBack() {
        PlayerData data = new PlayerData(Optional.of(HOME), Optional.of(INSIDE));
        assertEquals(data, read(data.write()));
        assertEquals(List.of(), warnings);
    }

    @Test
    void theTeamIsWrittenHyphenated() {
        CompoundTag tag = PlayerData.DEFAULTS.withInVault(INSIDE).write();
        assertEquals(TEAM.toString(), tag.getCompoundOrEmpty("in_vault").getStringOr("team", ""));
    }

    /** Version 1 as the first build writes it, built key by key, so a change of a key's type fails here. */
    private static CompoundTag versionOneFixture() {
        CompoundTag pos = new CompoundTag();
        pos.putString("dim", "minecraft:the_nether");
        pos.putInt("x", -40);
        pos.putInt("y", 70);
        pos.putInt("z", 12);
        CompoundTag inVault = new CompoundTag();
        inVault.putString("team", TEAM.toString());
        inVault.putInt("generation", 0);
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", 1);
        tag.put("return_pos", pos);
        tag.put("in_vault", inVault);
        return tag;
    }

    @Test
    void theVersionOneFixtureReadsAndWritesTheSameTag() {
        CompoundTag fixture = versionOneFixture();
        PlayerData data = read(fixture);
        assertEquals(new PlayerData(Optional.of(new ReturnPos("minecraft:the_nether", -40, 70, 12)),
                Optional.of(new InVault(TEAM, 0))), data);
        assertEquals(fixture, data.write());
    }

    @Test
    void aTagWithoutInVaultReadsNotInAVault() {
        CompoundTag fixture = versionOneFixture();
        fixture.remove("in_vault");
        PlayerData data = read(fixture);
        assertEquals(Optional.empty(), data.inVault());
        assertTrue(data.returnPos().isPresent());
    }

    @Test
    void aTagWithoutReturnPosReadsNoReturnPosition() {
        CompoundTag fixture = versionOneFixture();
        fixture.remove("return_pos");
        assertEquals(Optional.empty(), read(fixture).returnPos());
    }

    @Test
    void aTagWithoutAVersionReadsAsVersionOne() {
        CompoundTag fixture = versionOneFixture();
        fixture.remove("data_version");
        assertEquals(read(versionOneFixture()), read(fixture));
        assertEquals(List.of(), warnings);
    }

    @Test
    void anEmptyTagReadsEveryDefault() {
        assertEquals(PlayerData.DEFAULTS, read(new CompoundTag()));
    }

    @Test
    void wrongTypesReadTheirDefaultsWithoutThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.put("data_version", StringTag.valueOf("one"));
        tag.put("return_pos", IntTag.valueOf(3));
        tag.put("in_vault", new ListTag());
        assertEquals(PlayerData.DEFAULTS, read(tag));
    }

    @Test
    void aReturnPositionMissingACoordinateReadsAbsent() {
        CompoundTag fixture = versionOneFixture();
        fixture.getCompoundOrEmpty("return_pos").remove("y");
        assertEquals(Optional.empty(), read(fixture).returnPos());
    }

    @Test
    void aReturnPositionWithAStringCoordinateReadsAbsent() {
        CompoundTag fixture = versionOneFixture();
        fixture.getCompoundOrEmpty("return_pos").putString("x", "ten");
        assertEquals(Optional.empty(), read(fixture).returnPos());
    }

    @Test
    void anInVaultNamingNoUuidReadsAbsent() {
        CompoundTag fixture = versionOneFixture();
        fixture.getCompoundOrEmpty("in_vault").putString("team", "not-a-uuid");
        assertEquals(Optional.empty(), read(fixture).inVault());
    }

    @Test
    void anInVaultWithoutAGenerationReadsAbsent() {
        CompoundTag fixture = versionOneFixture();
        fixture.getCompoundOrEmpty("in_vault").remove("generation");
        assertEquals(Optional.empty(), read(fixture).inVault());
    }

    @Test
    void aNewerTagIsReadBestEffortWithOneWarning() {
        CompoundTag fixture = versionOneFixture();
        fixture.putInt("data_version", 7);
        fixture.putString("a_later_key", "kept until the next save");
        PlayerData data = read(fixture);
        assertTrue(data.returnPos().isPresent());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("7"), warnings.get(0));
        assertFalse(data.write().contains("a_later_key"));
    }

    @Test
    void migratingTwiceChangesNothing() {
        CompoundTag once = PlayerData.migrate(versionOneFixture(), 1);
        assertEquals(once, PlayerData.migrate(once.copy(), 1));
    }

    @Test
    void theReadLeavesItsTagAlone() {
        CompoundTag fixture = versionOneFixture();
        CompoundTag before = fixture.copy();
        read(fixture);
        assertEquals(before, fixture);
    }

    @Test
    void leavingAVaultKeepsTheReturnPosition() {
        PlayerData inside = new PlayerData(Optional.of(HOME), Optional.of(INSIDE));
        assertEquals(new PlayerData(Optional.of(HOME), Optional.empty()), inside.withoutInVault());
    }

    @Test
    void aNewReturnPositionKeepsTheVault() {
        ReturnPos elsewhere = new ReturnPos("minecraft:overworld", 1, 2, 3);
        PlayerData data = PlayerData.DEFAULTS.withInVault(INSIDE).withReturnPos(elsewhere);
        assertEquals(new PlayerData(Optional.of(elsewhere), Optional.of(INSIDE)), data);
    }
}
