package com.orevault.orevault.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.key.VaultKeys;
import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.VaultTypeFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR 0007's start-of-server pass, step 4 (2026-11-21), on a temp world: a Vault folder of layout 1 moves to the archive
 * with its bytes, one WARN naming both paths, and nothing else is touched (ADR 0002 restore fixture case (g)).
 */
class VaultFoldersTest {
    private static final UUID OLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CURRENT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID DAMAGED = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID NO_TYPE = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final byte[] REGION = {9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final long TIME = 4242L;

    @TempDir
    Path world;

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private Path folder(UUID team) {
        return DataPaths.vaultFolder(world, team);
    }

    /** A folder as a build before {@code layout} wrote it, with one region file. */
    private void oldFolder(UUID team) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", 1);
        tag.putString("variant", VaultVariant.BASE.id());
        Files.createDirectories(folder(team).resolve("region"));
        NbtIo.writeCompressed(tag, folder(team).resolve(VaultKeys.VAULT_TYPE_FILE));
        Files.write(folder(team).resolve("region").resolve("r.0.0.mca"), REGION);
    }

    private Set<UUID> archive() {
        return VaultFolders.archiveOldLayouts(world, TIME, warnings::add, errors::add);
    }

    @Test
    void aLayoutOneFolderMovesToTheArchiveWithItsBytes() throws IOException {
        oldFolder(OLD);
        byte[] typeBytes = Files.readAllBytes(folder(OLD).resolve(VaultKeys.VAULT_TYPE_FILE));

        assertEquals(Set.of(OLD), archive());

        Path target = world.resolve("orevault_archive").resolve(OLD.toString()).resolve("layout-1-4242");
        assertFalse(Files.exists(folder(OLD)));
        assertArrayEquals(typeBytes, Files.readAllBytes(target.resolve(VaultKeys.VAULT_TYPE_FILE)));
        assertArrayEquals(REGION, Files.readAllBytes(target.resolve("region").resolve("r.0.0.mca")));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains(folder(OLD).toString()) && warnings.getFirst().contains(target.toString()),
                warnings.getFirst());
        assertEquals(List.of(), errors);
    }

    /** A current folder, a damaged type file and a folder with no type file are the restore's, and the pass says nothing. */
    @Test
    void everyOtherFolderIsLeftForTheRestore() throws IOException {
        VaultTypeFile.write(folder(CURRENT), VaultVariant.EXPANDED);
        Files.createDirectories(folder(DAMAGED));
        Files.write(folder(DAMAGED).resolve(VaultKeys.VAULT_TYPE_FILE), REGION);
        Files.createDirectories(folder(NO_TYPE).resolve("region"));
        Files.createDirectories(DataPaths.vaultsFolder(world).resolve("vault_zzz"));

        assertEquals(Set.of(), archive());

        assertTrue(Files.exists(folder(CURRENT).resolve(VaultKeys.VAULT_TYPE_FILE)));
        assertArrayEquals(REGION, Files.readAllBytes(folder(DAMAGED).resolve(VaultKeys.VAULT_TYPE_FILE)));
        assertTrue(Files.isDirectory(folder(NO_TYPE).resolve("region")));
        assertFalse(Files.exists(world.resolve("orevault_archive")));
        assertEquals(List.of(), warnings);
        assertEquals(List.of(), errors);
    }

    @Test
    void onlyTheOldFolderMovesBesideTheOthers() throws IOException {
        oldFolder(OLD);
        VaultTypeFile.write(folder(CURRENT), VaultVariant.BASE);
        assertEquals(Set.of(OLD), archive());
        assertTrue(Files.exists(folder(CURRENT)));
    }

    /** A target already there is never overwritten: one ERROR, and the folder stays for the restore to refuse. */
    @Test
    void anArchiveAlreadyThereLeavesTheFolderWithAnError() throws IOException {
        oldFolder(OLD);
        Path target = DataPaths.layoutArchive(world, OLD, 1, TIME);
        Files.createDirectories(target);

        assertEquals(Set.of(), archive());

        assertTrue(Files.exists(folder(OLD).resolve("region").resolve("r.0.0.mca")));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().contains(folder(OLD).toString()), errors.getFirst());
        assertEquals(List.of(), warnings);
    }

    @Test
    void noVaultsFolderIsNothingToDo() {
        assertEquals(Set.of(), archive());
        assertEquals(List.of(), errors);
    }

    @Test
    void aMoveRefusesATargetThatExists() throws IOException {
        Path source = world.resolve("a");
        Path target = world.resolve("b");
        Files.createDirectories(source);
        Files.createDirectories(target);
        assertThrows(IOException.class, () -> VaultFolders.move(source, target));
        assertTrue(Files.exists(source));
    }

    /** The copy path's check: the same files with the same bytes, or the move does not delete its source. */
    @Test
    void theCheckFindsAMissingOrChangedFile() throws IOException {
        Path source = world.resolve("source");
        Path copy = world.resolve("copy");
        Files.createDirectories(source.resolve("region"));
        Files.createDirectories(copy.resolve("region"));
        Files.write(source.resolve("region").resolve("r.0.0.mca"), REGION);
        Files.write(copy.resolve("region").resolve("r.0.0.mca"), REGION);
        VaultFolders.verify(source, copy);

        Files.write(copy.resolve("region").resolve("r.0.0.mca"), new byte[] {9, 8, 7, 6, 5, 4, 3, 2, 0});
        assertThrows(IOException.class, () -> VaultFolders.verify(source, copy));

        Files.write(copy.resolve("region").resolve("r.0.0.mca"), REGION);
        Files.write(source.resolve("extra.dat"), REGION);
        assertThrows(IOException.class, () -> VaultFolders.verify(source, copy));
    }
}
