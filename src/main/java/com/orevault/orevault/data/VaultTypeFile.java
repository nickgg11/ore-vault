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
 *
 * <p>{@code layout} says which layer stack the folder's chunks were generated under (ADR 0009, amended 2026-11-21; ADR
 * 0005 section 3a). Layout 1 is the stack of 2026-10, stone to Y=245 and grass at Y=250; layout 2, {@link #CURRENT_LAYOUT},
 * is the surface at Y=100. The dimension type did not change, so an old folder would load, and new chunks would generate
 * 150 blocks lower beside its old ones: a layout-1 folder is archived at server start ({@code VaultFolders}), and one still
 * here is refused. An absent key reads as 1, since every file written before the key existed was layout 1. A layout above
 * the current one is a newer build's folder and is refused like an unknown type. Adding the key needs no version step:
 * nothing was renamed or removed, and the absent case is the default.
 */
public final class VaultTypeFile {
    private static final int CURRENT_VERSION = 1;
    private static final String DATA_VERSION = "data_version";
    private static final String VARIANT = "variant";
    private static final String LAYOUT = "layout";
    /** The layer stack this build generates: the surface at Y=100 (ADR 0009, amended 2026-11-21). */
    public static final int CURRENT_LAYOUT = VaultKeys.CURRENT_LAYOUT;
    /** What a file written before {@code layout} existed holds: the stack of 2026-10, stone to Y=245. */
    public static final int FIRST_LAYOUT = VaultKeys.FIRST_LAYOUT;
    private static final String TEMP_FILE = VaultKeys.VAULT_TYPE_FILE + ".tmp";

    private VaultTypeFile() {
    }

    /** What a type file records: the dimension type, and the layer stack its chunks were generated under. */
    public record Type(VaultVariant variant, int layout) {
    }

    static CompoundTag toTag(VaultVariant variant) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        tag.putString(VARIANT, variant.id());
        tag.putInt(LAYOUT, CURRENT_LAYOUT);
        return tag;
    }

    /** A fall-through chain, as {@link TeamFields#migrate}. Version 1 is the first, so there is no step yet. */
    static CompoundTag migrate(CompoundTag tag, int storedVersion) {
        return tag;
    }

    /**
     * The type in {@code file} a level can be loaded under, or empty after one line to {@code error} naming the path. A
     * folder of an older layout is refused here: the start-of-server pass archives it, and one still here is a move that
     * failed.
     */
    public static Optional<VaultVariant> read(Path file, Consumer<String> warn, Consumer<String> error) {
        Optional<Type> type = readType(file, warn, error);
        if (type.isPresent() && type.get().layout() < CURRENT_LAYOUT) {
            error.accept("Vault type file " + file + " is layout " + type.get().layout() + ", generated under an older layer "
                    + "stack than this build's layout " + CURRENT_LAYOUT + "; the start-of-server pass archives such a folder, "
                    + "and this one is still here, so its move failed (the ERROR from the pass names why)");
            return Optional.empty();
        }
        return type.map(Type::variant);
    }

    /**
     * The type and layout in {@code file}, of any layout this build knows, or empty after one line to {@code error} naming
     * the path: unreadable, an unknown type, or a layout that is damaged or newer than {@link #CURRENT_LAYOUT}.
     */
    public static Optional<Type> readType(Path file, Consumer<String> warn, Consumer<String> error) {
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
        CompoundTag migrated = migrate(tag, stored);
        String id = migrated.getStringOr(VARIANT, "");
        Optional<VaultVariant> variant = VaultVariant.byId(id);
        if (variant.isEmpty()) {
            error.accept("Vault type file " + file + " names no known type: '" + id + "'");
            return Optional.empty();
        }
        int layout;
        if (!migrated.contains(LAYOUT)) {
            layout = FIRST_LAYOUT;
        } else {
            Optional<Integer> stated = migrated.getInt(LAYOUT);
            if (stated.isEmpty() || stated.get() < FIRST_LAYOUT) {
                error.accept("Vault type file " + file + " has a damaged layout: " + migrated.get(LAYOUT));
                return Optional.empty();
            }
            layout = stated.get();
        }
        if (layout > CURRENT_LAYOUT) {
            error.accept("Vault type file " + file + " is layout " + layout + ", newer than this build's layout "
                    + CURRENT_LAYOUT + ": a folder from a newer version of the mod");
            return Optional.empty();
        }
        return Optional.of(new Type(variant.get(), layout));
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
