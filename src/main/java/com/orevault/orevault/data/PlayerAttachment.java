package com.orevault.orevault.data;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.OreVault;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player attachment {@code orevault:player} (§3.2, ADR 0005 section 5). Its id is save data from the first release.
 *
 * <p>Registered over the raw compound with {@code MapCodec.assumeMapUnsafe}, so {@link PlayerData#read} sees the whole tag
 * and cannot fail: a codec that failed would make NeoForge drop the attachment, {@code return_pos} with it, before any
 * migration ran (ADR 0005 section 6). {@code serialize} must come before {@code copyOnDeath}, or the builder throws.
 *
 * <p>The holder is here, beside the shape, and not in {@code registry}: {@code registry} may not import {@code data}
 * (ADR 0001), and {@code worldgen.index} keeps its own attachment the same way.
 */
public final class PlayerAttachment {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAttachment.class);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OreVault.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerData>> PLAYER = ATTACHMENTS.register("player",
            () -> AttachmentType.builder(() -> PlayerData.DEFAULTS)
                    .serialize(MapCodec.assumeMapUnsafe(CompoundTag.CODEC.xmap(
                            tag -> PlayerData.read(tag, LOGGER::warn), PlayerData::write)))
                    .copyOnDeath()
                    .build());

    private PlayerAttachment() {
    }

    /** On the mod bus, from the mod constructor (rule 3). */
    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static PlayerData get(ServerPlayer player) {
        return player.getData(PLAYER);
    }

    public static void update(ServerPlayer player, UnaryOperator<PlayerData> change) {
        player.setData(PLAYER, change.apply(get(player)));
    }
}
