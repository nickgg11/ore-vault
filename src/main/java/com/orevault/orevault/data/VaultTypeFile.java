package com.orevault.orevault.data;

import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.key.VaultKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * The Vault's dimension type, in its own folder (ADR 0005 section 3a): raw NBT, written once when the folder is made and
 * never again, and read before the level exists, so it is not {@code SavedData}. It is the only record of which terrain a
 * folder holds, so unlike every other shape it has no default. A file that is missing, damaged or names an unknown type
 * refuses the Vault, because base read for an expanded Vault deletes its deepslate band. Nothing caches a refusal, so an
 * admin who puts the file back has the Vault at the next attempt.
 */
public final class VaultTypeFile {
    private static final int CURRENT_VERSION = 1;
    private static final String DATA_VERSION = "data_version";
    private static final String VARIANT = "variant";
    private static final String TEMP_FILE = VaultKeys.VAULT_TYPE_FILE + ".tmp";

    private VaultTypeFile() {
    }

    static CompoundTag toTag(VaultVariant variant) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        tag.putString(VARIANT, variant.id());
        return tag;
    }

    /** A fall-through chain, as {@link TeamFields#migrate}. Version 1 is the first, so there is no step yet. */
    static CompoundTag migrate(CompoundTag tag, int storedVersion) {
        return tag;
    }

    /** The type in {@code file}, or empty after one line to {@code error} naming the path. */
    public static Optional<VaultVariant> read(Path file, Consumer<String> warn, Consumer<String> error) {
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            error.accept("Vault type file " + file + " cannot be read: " + e);
            return Optional.empty();
        }
        int stored = tag.getIntOr(DATA_VERSION, 1);
        if (stored > CURRENT_VERSION) {
            warn.accept("Vault type file " + file + " is version " + stored + ", newer than this build's "
                    + CURRENT_VERSION + "; reading what it knows");
        }
        String id = migrate(tag, stored).getStringOr(VARIANT, "");
        Optional<VaultVariant> variant = VaultVariant.byId(id);
        if (variant.isEmpty()) {
            error.accept("Vault type file " + file + " names no known type: '" + id + "'");
        }
        return variant;
    }

    /** To a temp file, then renamed onto the name, so a cut write never leaves a half file under it. */
    public static void write(Path folder, VaultVariant variant) throws IOException {
        Files.createDirectories(folder);
        Path temp = folder.resolve(TEMP_FILE);
        NbtIo.writeCompressed(toTag(variant), temp);
        Files.move(temp, folder.resolve(VaultKeys.VAULT_TYPE_FILE), StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * ADR 0002 creation step 2. A folder that exists keeps its type, whatever the team owns now, and nothing is written
     * into it. No folder is a new Vault, and so is what a creation cut short leaves behind: an empty folder, or one
     * holding nothing but the temp file, neither of which has a chunk in it. {@link #write} replaces that temp file.
     *
     * @param forANewVault the type to write, asked only when there is no Vault here yet
     * @return the type, or empty after one line to {@code error} naming the path
     */
    public static Optional<VaultVariant> openOrCreate(
            Path folder, Supplier<VaultVariant> forANewVault, Consumer<String> warn, Consumer<String> error) {
        return openOrCreate(folder, forANewVault, warn, error, Files::list);
    }

    /** Lists a folder. A seam for the one failure a test cannot provoke, a disk that fails part way through a listing. */
    interface FolderListing {
        Stream<Path> open(Path folder) throws IOException;
    }

    /** {@code Files.list} throws {@link UncheckedIOException} for a failure while iterating, so that is a refusal too. */
    static Optional<VaultVariant> openOrCreate(
            Path folder, Supplier<VaultVariant> forANewVault, Consumer<String> warn, Consumer<String> error, FolderListing listing) {
        try {
            if (!holdsAVault(folder, listing)) {
                VaultVariant variant = forANewVault.get();
                write(folder, variant);
                return Optional.of(variant);
            }
        } catch (IOException | UncheckedIOException e) {
            error.accept("Vault folder " + folder + " cannot be read or written: " + e);
            return Optional.empty();
        }
        return read(folder.resolve(VaultKeys.VAULT_TYPE_FILE), warn, error);
    }

    private static boolean holdsAVault(Path folder, FolderListing listing) throws IOException {
        if (!Files.exists(folder)) {
            return false;
        }
        try (Stream<Path> files = listing.open(folder)) {
            List<String> names = files.map(file -> file.getFileName().toString()).toList();
            return !names.isEmpty() && !names.equals(List.of(TEMP_FILE));
        }
    }
}
