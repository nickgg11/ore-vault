package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.VaultVariant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one record of what terrain a Vault folder holds (ADR 0005 section 3a). Every other shape has a default for an
 * absent key; this one refuses, because a wrong guess costs the deepslate band.
 */
class VaultTypeFileTest {
    private static final byte[] GARBAGE = {1, 2, 3, 4, 5, 6, 7, 8};

    @TempDir
    Path temp;

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private Path folder() {
        return temp.resolve("vault_0123");
    }

    private Path typeFile() {
        return folder().resolve("orevault_vault.dat");
    }

    private Path tempFile() {
        return folder().resolve("orevault_vault.dat.tmp");
    }

    private Optional<VaultVariant> read() {
        return VaultTypeFile.read(typeFile(), warnings::add, errors::add);
    }

    private Optional<VaultVariant> openOrCreate(VaultVariant forANewVault) {
        return VaultTypeFile.openOrCreate(folder(), () -> forANewVault, warnings::add, errors::add);
    }

    private void writeTag(CompoundTag tag) throws IOException {
        Files.createDirectories(folder());
        NbtIo.writeCompressed(tag, typeFile());
    }

    private List<String> folderFiles() throws IOException {
        try (Stream<Path> files = Files.list(folder())) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void theGoldenKeySetIsPinned() {
        assertEquals(Set.of("data_version", "variant"), VaultTypeFile.toTag(VaultVariant.BASE).keySet());
    }

    @Test
    void theRootIsTheCompoundItselfWithNoDataWrapper() throws IOException {
        VaultTypeFile.write(folder(), VaultVariant.EXPANDED);
        CompoundTag root = NbtIo.readCompressed(typeFile(), NbtAccounter.unlimitedHeap());
        assertEquals(1, root.getIntOr("data_version", 0));
        assertEquals("orevault:ore_vault_expanded", root.getStringOr("variant", ""));
    }

    @Test
    void eachVariantRoundTrips() throws IOException {
        for (VaultVariant variant : VaultVariant.values()) {
            VaultTypeFile.write(folder(), variant);
            assertEquals(Optional.of(variant), read());
        }
        assertEquals(List.of(), errors);
    }

    @Test
    void aWriteLeavesNoTempFile() throws IOException {
        VaultTypeFile.write(folder(), VaultVariant.BASE);
        assertEquals(List.of("orevault_vault.dat"), folderFiles());
    }

    @Test
    void aTagWithNoVersionReadsAsVersionOne() throws IOException {
        CompoundTag tag = VaultTypeFile.toTag(VaultVariant.EXPANDED);
        tag.remove("data_version");
        writeTag(tag);
        assertEquals(Optional.of(VaultVariant.EXPANDED), read());
        assertEquals(List.of(), warnings);
    }

    @Test
    void aNewerVersionWithAKnownVariantIsUsedWithOneWarn() throws IOException {
        CompoundTag tag = VaultTypeFile.toTag(VaultVariant.EXPANDED);
        tag.putInt("data_version", 99);
        writeTag(tag);
        assertEquals(Optional.of(VaultVariant.EXPANDED), read());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("99"), warnings.getFirst());
    }

    @Test
    void migratingTwiceChangesNothing() {
        CompoundTag tag = VaultTypeFile.toTag(VaultVariant.BASE);
        CompoundTag once = VaultTypeFile.migrate(tag.copy(), 1);
        assertEquals(tag, once);
        assertEquals(once, VaultTypeFile.migrate(once.copy(), 1));
    }

    @Test
    void aMissingFileIsARefusalNamingThePath() throws IOException {
        Files.createDirectories(folder());
        assertEquals(Optional.empty(), read());
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains(typeFile().toString()), errors.getFirst());
    }

    @Test
    void garbageBytesAreARefusalNamingThePath() throws IOException {
        Files.createDirectories(folder());
        Files.write(typeFile(), GARBAGE);
        assertEquals(Optional.empty(), read());
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains(typeFile().toString()), errors.getFirst());
    }

    @Test
    void anEmptyFileIsARefusal() throws IOException {
        Files.createDirectories(folder());
        Files.write(typeFile(), new byte[0]);
        assertEquals(Optional.empty(), read());
        assertEquals(1, errors.size(), errors.toString());
    }

    @Test
    void anUnknownVariantIsARefusal() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", 1);
        tag.putString("variant", "orevault:nonsense");
        writeTag(tag);
        assertEquals(Optional.empty(), read());
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains("orevault:nonsense"), errors.getFirst());
    }

    /** Absent reads as a refusal, never a default: base for an expanded Vault deletes its deepslate band. */
    @Test
    void anAbsentVariantIsARefusalAndNotBase() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", 1);
        writeTag(tag);
        assertEquals(Optional.empty(), read());
        assertEquals(1, errors.size(), errors.toString());
    }

    @Test
    void aVariantOfTheWrongTypeIsARefusal() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("variant", 1);
        writeTag(tag);
        assertEquals(Optional.empty(), read());
        assertEquals(1, errors.size(), errors.toString());
    }

    @Test
    void noFolderIsANewVaultAndWritesTheFile() throws IOException {
        assertEquals(Optional.of(VaultVariant.EXPANDED), openOrCreate(VaultVariant.EXPANDED));
        assertEquals(Optional.of(VaultVariant.EXPANDED), read());
        assertEquals(List.of(), errors);
    }

    @Test
    void aFolderHoldingOnlyTheTempFileIsANewVault() throws IOException {
        Files.createDirectories(folder());
        Files.write(tempFile(), GARBAGE);
        assertEquals(Optional.of(VaultVariant.BASE), openOrCreate(VaultVariant.BASE));
        assertEquals(List.of("orevault_vault.dat"), folderFiles());
    }

    /** The other place a cut creation can stop: the folder made, the temp file not yet. No chunk is in it. */
    @Test
    void anEmptyFolderIsANewVault() throws IOException {
        Files.createDirectories(folder());
        assertEquals(Optional.of(VaultVariant.BASE), openOrCreate(VaultVariant.BASE));
        assertEquals(List.of("orevault_vault.dat"), folderFiles());
    }

    @Test
    void anExistingFolderKeepsItsTypeWhateverTheTeamNowOwns() throws IOException {
        VaultTypeFile.write(folder(), VaultVariant.BASE);
        assertEquals(Optional.of(VaultVariant.BASE), openOrCreate(VaultVariant.EXPANDED));
        assertEquals(Optional.of(VaultVariant.BASE), read());
    }

    @Test
    void aFolderWithRegionsAndNoTypeFileIsRefusedAndNothingIsWritten() throws IOException {
        Files.createDirectories(folder().resolve("region"));
        assertEquals(Optional.empty(), openOrCreate(VaultVariant.BASE));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals(List.of("region"), folderFiles());
    }

    @Test
    void aDamagedTypeFileKeepsItsBytes() throws IOException {
        Files.createDirectories(folder());
        Files.write(typeFile(), GARBAGE);
        assertEquals(Optional.empty(), openOrCreate(VaultVariant.BASE));
        assertArrayEquals(GARBAGE, Files.readAllBytes(typeFile()));
    }

    /** A temp file beside anything else is no cut creation, so it is left for an admin to read. */
    @Test
    void aTempFileBesideRegionsIsLeftAlone() throws IOException {
        Files.createDirectories(folder().resolve("region"));
        Files.write(tempFile(), GARBAGE);
        assertEquals(Optional.empty(), openOrCreate(VaultVariant.BASE));
        assertTrue(Files.exists(tempFile()));
        assertFalse(Files.exists(typeFile()));
    }

    /** Nothing caches a refusal: an admin who puts the file back has the Vault at the next attempt. */
    @Test
    void puttingTheFileBackOpensTheVault() throws IOException {
        Files.createDirectories(folder().resolve("region"));
        assertEquals(Optional.empty(), openOrCreate(VaultVariant.BASE));
        VaultTypeFile.write(folder(), VaultVariant.EXPANDED);
        assertEquals(Optional.of(VaultVariant.EXPANDED), openOrCreate(VaultVariant.BASE));
    }
}
