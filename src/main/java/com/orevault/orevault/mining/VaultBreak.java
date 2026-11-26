package com.orevault.orevault.mining;

import com.orevault.orevault.core.mining.BreakContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jspecify.annotations.Nullable;

/**
 * One block break as every drop stage sees it: the event, the context worked out once (§3.4, §11), and the player when
 * it was a hand break. A stage reads {@code context.source()} and never asks again whether the breaker is a machine.
 *
 * <p>The event's tool is a copy taken before the tool was damaged; a stage that changes the held tool uses the hand
 * breaker's live main-hand stack (old PR #150's Highwater Mark bug).
 *
 * @param handBreaker the breaker when {@code context.source()} is {@code HAND}, otherwise null
 */
public record VaultBreak(BlockDropsEvent event, BreakContext context, @Nullable ServerPlayer handBreaker) {
}
