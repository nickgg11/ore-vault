package com.orevault.orevault.vault;

import com.orevault.orevault.core.key.VaultKeys;
import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.VaultTypeFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Moving a Vault's folder out of the dimensions folder, and the start-of-server pass's step for a folder of an older
 * layout (ADR 0007 "The start-of-server pass", step 4; ADR 0009, amended 2026-11-21). Plain paths and two log sinks, no
 * server, so the file work is unit-tested on a temp folder and the caller in {@link VaultDimensions} stays a call.
 *
 * <p>It runs at {@code ServerStartedEvent}, before the restore registers any Vault, so no level holds the folder's region
 * files open: nothing is moved while the server runs (§3.1, S19). The disband and deferred-reset steps (2 and 3) join here
 * with E11; this class has the shared move they will call.
 */
public final class VaultFolders {
    private VaultFolders() {
    }

    /**
     * Step 4: every Vault folder whose type file reads an older layout, absent included, is moved alone, not the team file,
     * to {@code <world>/orevault_archive/<teamUuid>/layout-<n>-<time>/}, with one WARN naming both paths. A folder with no
     * type file, or one that cannot be read, is left for the restore, which refuses it and logs it as before, so this logs
     * nothing for it. A move that fails is one ERROR, and the folder stays: the restore then refuses it as a layout it
     * cannot load.
     *
     * @param time the archive folder's time stamp, the overworld's game time at the start
     * @return the teams whose folder was archived, for the restore's count and login case 1's message
     */
    public static Set<UUID> archiveOldLayouts(Path worldRoot, long time, Consumer<String> warn, Consumer<String> error) {
        Set<UUID> archived = new LinkedHashSet<>();
        Path vaults = DataPaths.vaultsFolder(worldRoot);
        if (!Files.isDirectory(vaults)) {
            return archived;
        }
        List<Path> folders;
        try (Stream<Path> entries = Files.list(vaults)) {
            folders = entries.filter(Files::isDirectory).sorted().toList();
        } catch (IOException | UncheckedIOException e) {
            error.accept("Cannot list " + vaults + " for Vaults of an older layout; none is archived: " + e);
            return archived;
        }
        for (Path folder : folders) {
            Optional<UUID> teamId = VaultKeys.teamIdFromDirectoryName(folder.getFileName().toString());
            Path typeFile = folder.resolve(VaultKeys.VAULT_TYPE_FILE);
            if (teamId.isEmpty() || !Files.isRegularFile(typeFile)) {
                continue;
            }
            Optional<VaultTypeFile.Type> type = VaultTypeFile.readType(typeFile, line -> { }, line -> { });
            if (type.isEmpty() || type.get().layout() >= VaultTypeFile.CURRENT_LAYOUT) {
                continue;
            }
            int layout = type.get().layout();
            Path target = DataPaths.layoutArchive(worldRoot, teamId.get(), layout, time);
            if (Files.exists(target)) {
                error.accept("Vault folder " + folder + " is layout " + layout + " but " + target + " already exists; the folder "
                        + "is left in place and the restore refuses it");
                continue;
            }
            try {
                move(folder, target);
            } catch (IOException | UncheckedIOException e) {
                error.accept("Vault folder " + folder + " is layout " + layout + " and could not be moved to " + target
                        + "; it is left in place and the restore refuses it: " + e);
                continue;
            }
            warn.accept("Vault folder " + folder + " was generated under layout " + layout + " (stone to Y=245), older than "
                    + "this build's layout " + VaultTypeFile.CURRENT_LAYOUT + "; archived to " + target + ". The team's data "
                    + "stays, and its next portal entry makes a fresh Vault");
            archived.add(teamId.get());
        }
        return archived;
    }

    /**
     * Moves {@code source} to {@code target}, which must not exist, making its parents. A rename when both are on one file
     * store; otherwise a copy, every file checked byte for byte against its source, then the source deleted. A copy that
     * does not match is removed again and the source kept.
     */
    public static void move(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            throw new IOException(target + " already exists");
        }
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AtomicMoveNotSupportedException e) {
            // Another file store: copy, check, delete.
        }
        try {
            copyTree(source, target);
            verify(source, target);
        } catch (IOException | RuntimeException e) {
            deleteTree(target);
            throw e;
        }
        deleteTree(source);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** The same relative files on both sides, each with the same bytes. */
    static void verify(Path source, Path target) throws IOException {
        List<Path> sourceFiles = files(source);
        List<Path> targetFiles = files(target);
        if (!sourceFiles.equals(targetFiles)) {
            throw new IOException("The copy at " + target + " does not hold the files of " + source);
        }
        for (Path relative : sourceFiles) {
            if (Files.mismatch(source.resolve(relative.toString()), target.resolve(relative.toString())) != -1L) {
                throw new IOException("The copy of " + source.resolve(relative.toString()) + " differs from its source");
            }
        }
    }

    private static List<Path> files(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                files.add(root.relativize(path));
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
