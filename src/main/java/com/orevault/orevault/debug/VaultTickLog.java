package com.orevault.orevault.debug;

import com.orevault.orevault.vault.VaultDimensions;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs each Vault's key at INFO once a second while the server ticks it (E3-S11, #245; ADR 0002). A level added after the
 * first tick without {@code markWorldsDirty()} sits in the world map and never ticks (old #82, #89), and nothing else in the
 * log shows the difference. QA's restart check reads these lines after a restart, and the level-unload spike read them to see
 * a removed Vault stop.
 *
 * <p>Only while {@code enable_debug_commands} is on, which the restart check turns on for its run: one line a second for
 * every Vault is noise on a real server. The flag arrives as a supplier because {@code debug} may not import {@code config}
 * (ADR 0001). Removed with the rest of the debug tools at T14.
 */
public final class VaultTickLog {
    private static final Logger LOG = LoggerFactory.getLogger(VaultTickLog.class);
    private static final String TAG = "[orevault-debug] ";

    private VaultTickLog() {
    }

    /** Called once from the mod constructor. A gameplay event, so the game bus (rule 3). */
    public static void register(BooleanSupplier enabled) {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level && VaultDimensions.teamOf(level.dimension()).isPresent()) {
                int tick = level.getServer().getTickCount();
                if (TickLine.due(tick) && enabled.getAsBoolean()) {
                    LOG.info("{}{}", TAG, new TickLine(level.dimension().identifier().toString(), tick).line());
                }
            }
        });
    }
}
