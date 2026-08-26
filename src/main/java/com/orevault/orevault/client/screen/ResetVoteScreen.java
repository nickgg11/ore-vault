package com.orevault.orevault.client.screen;

import com.orevault.orevault.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.UUID;

/**
 * Reset voting dialog shown to every online team member while a vote is active.
 */
public class ResetVoteScreen extends Screen {
    private CompoundTag state;
    private boolean voted;

    public ResetVoteScreen(CompoundTag state) {
        super(Component.translatable("orevault.screen.reset_vote"));
        this.state = state;
    }

    public void update(CompoundTag state) {
        this.state = state;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2 + 20;
        addRenderableWidget(Button.builder(Component.translatable("orevault.reset.approve"), b -> send(true))
                .bounds(cx - 105, cy, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("orevault.reset.reject"), b -> send(false))
                .bounds(cx + 5, cy, 100, 20).build());
    }

    private void send(boolean approve) {
        ClientPacketDistributor.sendToServer(new ModNetwork.ResetVotePayload(approve));
        voted = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        graphics.fill(cx - 130, cy - 60, cx + 130, cy + 60, 0xE0101010);
        graphics.outline(cx - 130, cy - 60, cx + 130, cy + 60, 0xFF886633);
        graphics.centeredText(font, title, cx, cy - 48, 0xFFFFFFFF);

        boolean passed = state.getBooleanOr("passed", false);
        int countdown = state.getIntOr("countdown", 0);
        int required = state.getIntOr("required", 0);
        CompoundTag votes = state.getCompoundOrEmpty("votes");
        long yes = 0;
        for (String key : votes.keySet()) {
            if (votes.getBooleanOr(key, false)) {
                yes++;
            }
        }
        if (passed) {
            graphics.centeredText(font, Component.translatable("orevault.reset.passed", yes, required), cx, cy - 30, 0xFF66FF66);
            if (countdown > 0) {
                graphics.centeredText(font, Component.translatable("orevault.reset.countdown", countdown / 20 + 1), cx, cy - 12, 0xFFFFAA00);
            }
        } else {
            graphics.centeredText(font, Component.translatable("orevault.reset.progress", yes, required), cx, cy - 30, 0xFFFFFFFF);
            graphics.centeredText(font, Component.translatable("orevault.reset.vote_prompt"), cx, cy - 12, 0xFFAAAAAA);
        }
        if (voted) {
            graphics.centeredText(font, Component.translatable("orevault.reset.voted"), cx, cy + 8, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

