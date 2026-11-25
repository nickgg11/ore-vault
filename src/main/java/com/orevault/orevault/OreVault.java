package com.orevault.orevault;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.orevault.orevault.config.ConfigReload;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.core.curve.LevelCurve;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.ore.Overrides;
import com.orevault.orevault.core.tree.TreeConstants;
import com.orevault.orevault.data.GenSnapshots;
import com.orevault.orevault.data.LevelCurves;
import com.orevault.orevault.data.PlayerAttachment;
import com.orevault.orevault.debug.DebugCommands;
import com.orevault.orevault.debug.VaultTickLog;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModCreativeTabs;
import com.orevault.orevault.registry.ModItems;
import com.orevault.orevault.registry.ModSounds;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.vault.VaultDimensions;
import com.orevault.orevault.vault.VaultOpenings;
import com.orevault.orevault.vault.VaultPlayerEvents;
import com.orevault.orevault.vault.VaultTeamEvents;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's entry point. The constructor is the only place the mod event bus is handed over, so registries
 * and lifecycle listeners are wired here, and gameplay listeners go on {@code NeoForge.EVENT_BUS}.
 */
@Mod(OreVault.MOD_ID)
public final class OreVault {
    public static final String MOD_ID = "orevault";

    private static final Logger LOGGER = LoggerFactory.getLogger(OreVault.class);

    public OreVault(IEventBus modEventBus, ModContainer container) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
        PlayerAttachment.register(modEventBus);
        TeamHelper.register();
        VaultTeamEvents.register();
        VaultPlayerEvents.register();
        VaultTickLog.register(OreVaultServerConfig::enableDebugCommands);
        container.registerConfig(ModConfig.Type.SERVER, OreVaultServerConfig.SPEC);
        modEventBus.addListener(ConfigReload::onReloading);
        NeoForge.EVENT_BUS.addListener(OreVault::registerCommands);
        NeoForge.EVENT_BUS.addListener(VaultOpenings::refuseBreak);
        NeoForge.EVENT_BUS.addListener(OreVault::onServerStarted);
        NeoForge.EVENT_BUS.addListener(OreVault::onServerStopped);
    }

    /** The root carries no guard: everything that is not a debug tool lives under it too, whatever the flag says (§10). */
    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(MOD_ID);
        DebugCommands.attach(root, OreVaultServerConfig::enableDebugCommands);
        event.getDispatcher().register(root);
    }

    /**
     * The start of the server, in ADR 0002's order. It returns before the tick loop runs, so before any login is placed. The
     * steps before the restore join here with their stories: ADR 0007's start-of-server pass, first, which archives Vault
     * folders of an older layout now (E4-S3b, #425) and with E11 hands the restore the resets it finished; the ore classifier (E4-S2, here); the pending generation tradeoffs (E9). Each comes before the
     * restore, because the snapshot the restore fills reads what they leave: the classifier publishes its table before any
     * Vault level is registered, so no Vault chunk can generate before it (ADR 0009 "Publication").
     */
    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Set<UUID> layoutArchived = VaultDimensions.archiveOldLayouts(server);
        classifyOres(server);
        publishLevelCurve();
        VaultDimensions.restoreAll(server, List.of(), layoutArchived);
    }

    /**
     * A feature that cannot be read is passed over inside the classifier, so an exception that reaches here is a bug in
     * the mod. It stops the server rather than starting without a table: restoring Vaults without one would either leave
     * a player who logged out inside a Vault in the Overworld sky (rule 2) or generate chunks with no ore, and generated
     * chunks never change (S2-AR-1, ADR 0009 "Publication").
     */
    private static void classifyOres(MinecraftServer server) {
        try {
            Overrides overrides = OreClassifier.readOverrides(server.registryAccess(),
                    OreVaultServerConfig.oreClassificationOverrides());
            OreClassifier.publish(OreClassifier.classifyServer(server, overrides));
        } catch (RuntimeException e) {
            LOGGER.error("The ore classifier failed; the server stops rather than generate Vaults without an ore table", e);
            throw e;
        }
    }

    /**
     * §4.3 steps 3 to 7: the curve from the baseline record and the two {@code [resonance]} values, computed once. The log
     * line is a cheap check that the server read the config it was given (old PR #116).
     */
    private static void publishLevelCurve() {
        double targetPlayHours = OreVaultServerConfig.targetPlayHours();
        double curveDivisor = OreVaultServerConfig.curveDivisor();
        LevelCurve curve = LevelCurve.derive(OreBaseline.BASE, targetPlayHours, curveDivisor);
        LevelCurves.publish(curve);
        LOGGER.info("Ore Vault level curve: {} levels, {} points each, {} Resonance over {} hours (curve_divisor {}); level 1"
                        + " costs {}, level {} costs {}",
                curve.levelCap(), TreeConstants.POINTS_PER_LEVEL, resonance(curve.totalThousandths()), targetPlayHours,
                curveDivisor, resonance(curve.costThousandths(1)), curve.levelCap(),
                resonance(curve.costThousandths(curve.levelCap())));
    }

    private static String resonance(long thousandths) {
        return BigDecimal.valueOf(thousandths, 3).toPlainString();
    }

    /** A second world in one client must not find this one's entries under the same personal team id (ADR 0002). */
    private static void onServerStopped(ServerStoppedEvent event) {
        VaultDimensions.clear();
        GenSnapshots.clear();
        OreClassifier.clear();
        LevelCurves.clear();
    }
}
