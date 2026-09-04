package com.orevault.orevault.client;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.client.screen.TomeScreen;
import com.orevault.orevault.item.TomeItem;
import com.orevault.orevault.network.ModNetwork;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client entrypoint: everything that only exists on a client is registered
 * from here (§11).
 *
 * <p>{@code @Mod(dist = Dist.CLIENT)} means this class is never constructed —
 * never even loaded — on a dedicated server. That is a stronger guarantee than
 * the {@code FMLEnvironment.getDist().isClient()} check these registrations used
 * to sit behind in {@link OreVault}: a runtime check still requires the enclosing
 * class to resolve every type it mentions, so one careless edit inside the branch
 * could still crash a server at class-load. Here the JVM never sees the class at
 * all, and no unit test in this tree can catch the difference, so the structural
 * version is worth the extra file.</p>
 *
 * <p>The Tome's screen is installed here as a callback rather than registered:
 * it has no menu and no container, so it opens straight from the item's
 * right-click with nothing to hand to {@code RegisterMenuScreensEvent}. The
 * reset vote screen ([38]) will land the same way.</p>
 */
@Mod(value = OreVault.MODID, dist = Dist.CLIENT)
public final class OreVaultClient {

    public OreVaultClient(IEventBus modEventBus, ModContainer modContainer) {
        // Block tint sources for the four tier-coloured portal blocks.
        VaultPortalColors.register(modEventBus);

        // Entity renderers: the Resonance orb, and the Animus orb post-1.0.
        VaultOrbRenderers.register(modEventBus);

        // The Config button on this mod's page in the mods list.
        VaultConfigScreen.register(modContainer);

        // Client packet handling. ModNetwork registers the payloads from common
        // code and cannot name a client class, so it holds this callback instead.
        ModNetwork.setClientHandler(ClientPacketHandlers::handle);

        // The Tome's screen, installed the same way and for the same reason:
        // TomeItem is a common-path Item and cannot name a Screen ([34]).
        TomeItem.setScreenOpener(player -> Minecraft.getInstance().setScreen(new TomeScreen()));

        NeoForge.EVENT_BUS.addListener(OreVaultClient::onLoggingOut);
    }

    /**
     * Drops the synced team state on disconnect, so the next world does not open
     * the Tome showing the previous server's Resonance until its first sync
     * lands.
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPacketHandlers.clear();
    }
}
