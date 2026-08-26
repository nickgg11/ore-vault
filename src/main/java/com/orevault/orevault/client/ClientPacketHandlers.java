package com.orevault.orevault.client;

import com.orevault.orevault.client.screen.ResetVoteScreen;
import com.orevault.orevault.client.screen.TomeScreen;
import com.orevault.orevault.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * Client-side handling of Ore Vault payloads.
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void handleTeamData(ModNetwork.TeamDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.open()) {
            mc.setScreen(new TomeScreen(payload.data()));
        } else if (mc.screen instanceof TomeScreen screen) {
            screen.updateData(payload.data());
        }
    }

    public static void handleVoteSync(CompoundTag state) {
        Minecraft mc = Minecraft.getInstance();
        boolean active = state.getBooleanOr("active", false);
        if (active) {
            if (mc.screen instanceof ResetVoteScreen screen) {
                screen.update(state);
            } else {
                mc.setScreen(new ResetVoteScreen(state));
            }
        } else if (mc.screen instanceof ResetVoteScreen screen) {
            screen.onClose();
        }
    }
}

