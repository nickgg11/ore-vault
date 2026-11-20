package com.orevault.orevault.vault;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

/**
 * Tells the client's door renderer that a portal cell was set in the client's level (§3.2, E3-S13, #401). A lit portal
 * draws no model, so the client has to learn of every new cell, and the chunk renderer's own event is not enough: a
 * renderer replacement such as Sodium may not fire it, or not on the main thread. {@code Level#markAndNotifyBlock} calls
 * the new block's {@code onBlockStateChange} on both sides, packets included, and this is where the portal block passes
 * it on.
 *
 * <p>Common code, so it names no client class (rule 4): the client entry point installs the listener. On a dedicated
 * server nothing is installed, and only a client level is passed on.
 */
public final class PortalCellChanges {
    private static volatile @Nullable Consumer<BlockPos> listener;

    private PortalCellChanges() {
    }

    public static void listen(Consumer<BlockPos> clientListener) {
        listener = clientListener;
    }

    static void changed(LevelReader level, BlockPos pos) {
        Consumer<BlockPos> current = listener;
        if (current != null && level.isClientSide()) {
            current.accept(pos.immutable());
        }
    }
}
