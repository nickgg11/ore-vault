package com.orevault.orevault.client;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.frame.PortalDoor;
import com.orevault.orevault.core.frame.PortalDoor.Swing;
import com.orevault.orevault.core.frame.VaultPortalShape;
import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import com.orevault.orevault.core.key.VaultKeys;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.vault.VaultDoorBlock;
import com.orevault.orevault.vault.VaultOpenings;
import com.orevault.orevault.vault.VaultPortalBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

/**
 * The lit portals this client can see, and each one's door (§3.2, E3-S13, #401). Client only. Every structure here is
 * touched on the main thread only: an event that arrives on another thread is handed over with {@link Minecraft#execute}.
 * Nothing is sent or stored, and the server's portal cells never change.
 *
 * <p>A portal block draws no model ({@code RenderShape.INVISIBLE}); {@link PortalDoorRenderer} draws the door and the
 * portal behind it from this list, so the list must hold every portal cell, whatever renders the chunks. Sections to
 * scan come from three places, none of them needed alone:
 * <ul>
 *   <li>{@link ChunkEvent.Load} on the client level, for every chunk the server sends;</li>
 *   <li>{@code PortalCellChanges}, which the portal block calls from {@code onBlockStateChange} for every cell set in the
 *       client's level, packets included;</li>
 *   <li>{@link AddSectionGeometryEvent}, from the vanilla chunk renderer, if it fires (a replacement such as Sodium may not).</li>
 * </ul>
 * A cell's removal calls nothing on the portal block, so each tick also checks the cells it draws and rescans the sections
 * of any that is gone. A scan asks the section's palette first whether it can hold a portal at all, which costs nothing
 * for almost every section, and reads the 4096 cells only when it can.
 *
 * <p>Openings are regrouped with {@link VaultPortalShape#around} once a tick, and only those that touch a rescanned
 * section, with their frame ring. A cell in no valid opening is drawn as a plain portal: a portal without its door is
 * better than none.
 */
final class PortalDoors {
    /** Portal cells by section, as {@link BlockPos#asLong} values. */
    private static final Long2ObjectMap<long[]> CELLS = new Long2ObjectOpenHashMap<>();
    private static final LongSet DIRTY = new LongOpenHashSet();
    private static final Map<VaultPortalShape, Opening> OPENINGS = new HashMap<>();
    private static List<BlockPos> loose = List.of();
    private static long ticks;
    private static @Nullable ClientLevel seen;

    private PortalDoors() {
    }

    /** One valid opening of portal cells. {@code door} is false for the return portal. */
    static final class Opening {
        final VaultPortalShape shape;
        final int tier;
        final boolean door;
        Swing swing = Swing.closed();
        boolean open;
        int side = 1;

        private Opening(VaultPortalShape shape, int tier, boolean door) {
            this.shape = shape;
            this.tier = tier;
            this.door = door;
        }
    }

    static Iterable<Opening> openings() {
        return OPENINGS.values();
    }

    /** Portal cells in no valid opening, such as a ring that has just been broken. They are drawn as a plain portal. */
    static List<BlockPos> looseCells() {
        return loose;
    }

    /** Ticks and the fraction of the next, so a frame between ticks draws the swing between them. */
    static double time(float partialTick) {
        return ticks + partialTick;
    }

    static void onSectionCompile(AddSectionGeometryEvent event) {
        BlockPos origin = event.getSectionOrigin().immutable();
        Object level = event.getLevel();
        onMainThread(() -> {
            if (level == Minecraft.getInstance().level) {
                DIRTY.add(SectionPos.asLong(origin));
            }
        });
    }

    /** From {@code PortalCellChanges}: a portal cell was set in the client's level. */
    static void cellChanged(BlockPos pos) {
        onMainThread(() -> DIRTY.add(SectionPos.asLong(pos)));
    }

    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) {
            return;
        }
        LevelChunk chunk = event.getChunk();
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        int minSection = chunk.getMinSectionY();
        int sections = chunk.getSectionsCount();
        onMainThread(() -> {
            if (level != Minecraft.getInstance().level) {
                return;
            }
            for (int index = 0; index < sections; index++) {
                DIRTY.add(SectionPos.asLong(chunkX, minSection + index, chunkZ));
            }
        });
    }

    static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level)) {
            return;
        }
        int chunkX = event.getChunk().getPos().x();
        int chunkZ = event.getChunk().getPos().z();
        onMainThread(() -> {
            if (level != Minecraft.getInstance().level) {
                return;
            }
            LongArrayList gone = new LongArrayList();
            for (long key : CELLS.keySet()) {
                if (SectionPos.x(key) == chunkX && SectionPos.z(key) == chunkZ) {
                    gone.add(key);
                }
            }
            DIRTY.addAll(gone);
        });
    }

    static void onLevelUnload(LevelEvent.Unload event) {
        Object level = event.getLevel();
        onMainThread(() -> {
            if (level == seen) {
                clear();
            }
        });
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            if (seen != null) {
                clear();
            }
            return;
        }
        if (seen != level) {
            clear();
            seen = level;
        }
        ticks++;
        checkDrawnCells(level);
        if (!DIRTY.isEmpty()) {
            LongOpenHashSet changed = new LongOpenHashSet(DIRTY);
            DIRTY.clear();
            for (long key : changed) {
                long[] cells = scan(level, key);
                if (cells.length > 0) {
                    CELLS.put(key, cells);
                } else {
                    CELLS.remove(key);
                }
            }
            regroup(level, changed);
        }
        for (Opening opening : OPENINGS.values()) {
            if (!opening.door) {
                continue;
            }
            boolean open = PortalDoor.wantsOpen(opening.open,
                    PortalDoor.distance(opening.shape, player.getX(), player.getY(), player.getZ()), opening.shape.width());
            if (open && opening.swing.isClosedAt(ticks)) {
                opening.side = PortalDoor.side(opening.shape, player.getX(), player.getZ());
            }
            opening.open = open;
            opening.swing = opening.swing.toward(open ? PortalDoor.OPEN_DEGREES : 0, ticks);
        }
    }

    /** A Vault, by the dimension id its folder is named after (ADR 0002). */
    static boolean inVault(ClientLevel level) {
        Identifier id = level.dimension().identifier();
        return id.getNamespace().equals(OreVault.MOD_ID) && VaultKeys.teamIdFromDirectoryName(id.getPath()).isPresent();
    }

    private static void onMainThread(Runnable work) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            work.run();
        } else {
            minecraft.execute(work);
        }
    }

    private static void clear() {
        CELLS.clear();
        DIRTY.clear();
        OPENINGS.clear();
        loose = List.of();
        seen = null;
    }

    /**
     * Removing a cell calls nothing on the portal block, so every drawn cell is read back each tick, a few hundred reads
     * for a large door, and a section holding one that is gone, or changed tier, is rescanned.
     */
    private static void checkDrawnCells(ClientLevel level) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Opening opening : OPENINGS.values()) {
            VaultPortalShape shape = opening.shape;
            for (Cell cell : shape.cells()) {
                BlockState state = level.getBlockState(at.set(cell.x(), cell.y(), cell.z()));
                if (!(state.getBlock() instanceof VaultPortalBlock portal) || portal.tier() != opening.tier) {
                    markSpan(shape);
                    break;
                }
            }
        }
        for (BlockPos pos : loose) {
            if (!(level.getBlockState(pos).getBlock() instanceof VaultPortalBlock)) {
                DIRTY.add(SectionPos.asLong(pos));
            }
        }
    }

    /** Every section an opening's cells lie in. */
    private static void markSpan(VaultPortalShape shape) {
        int maxX = shape.minX() + (shape.axis() == Axis.X ? shape.width() : 1) - 1;
        int maxZ = shape.minZ() + (shape.axis() == Axis.Z ? shape.width() : 1) - 1;
        int maxY = shape.minY() + shape.height() - 1;
        for (int sx = SectionPos.blockToSectionCoord(shape.minX()); sx <= SectionPos.blockToSectionCoord(maxX); sx++) {
            for (int sy = SectionPos.blockToSectionCoord(shape.minY()); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                for (int sz = SectionPos.blockToSectionCoord(shape.minZ()); sz <= SectionPos.blockToSectionCoord(maxZ);
                        sz++) {
                    DIRTY.add(SectionPos.asLong(sx, sy, sz));
                }
            }
        }
    }

    private static long[] scan(ClientLevel level, long key) {
        int sectionX = SectionPos.x(key);
        int sectionY = SectionPos.y(key);
        int sectionZ = SectionPos.z(key);
        if (!level.getChunkSource().hasChunk(sectionX, sectionZ)) {
            return new long[0];
        }
        LevelChunk chunk = level.getChunk(sectionX, sectionZ);
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= chunk.getSectionsCount()) {
            return new long[0];
        }
        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof VaultPortalBlock)) {
            return new long[0];
        }
        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
        LongArrayList found = new LongArrayList();
        for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
                    if (section.getBlockState(x, y, z).getBlock() instanceof VaultPortalBlock) {
                        found.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
        return found.toLongArray();
    }

    /**
     * Regroups only what the rescan can have changed: the openings whose cells or frame ring lie in a changed section, the
     * cells of the changed sections, and the loose cells, which are few. Every other opening keeps its swing untouched.
     */
    private static void regroup(ClientLevel level, LongSet changed) {
        LongArrayList candidates = new LongArrayList();
        Map<VaultPortalShape, Opening> before = new HashMap<>();
        Iterator<Map.Entry<VaultPortalShape, Opening>> it = OPENINGS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<VaultPortalShape, Opening> entry = it.next();
            if (touches(entry.getKey(), changed)) {
                before.put(entry.getKey(), entry.getValue());
                for (Cell cell : entry.getKey().cells()) {
                    candidates.add(BlockPos.asLong(cell.x(), cell.y(), cell.z()));
                }
                it.remove();
            }
        }
        for (BlockPos pos : loose) {
            candidates.add(pos.asLong());
        }
        for (long key : changed) {
            long[] cells = CELLS.get(key);
            if (cells != null) {
                candidates.addElements(candidates.size(), cells);
            }
        }

        boolean inVault = inVault(level);
        LongOpenHashSet done = new LongOpenHashSet();
        for (VaultPortalShape kept : OPENINGS.keySet()) {
            for (Cell cell : kept.cells()) {
                done.add(BlockPos.asLong(cell.x(), cell.y(), cell.z()));
            }
        }
        List<BlockPos> nextLoose = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (long cell : candidates) {
            if (!done.add(cell)) {
                continue;
            }
            BlockState state = level.getBlockState(at.set(cell));
            if (!(state.getBlock() instanceof VaultPortalBlock)) {
                continue;
            }
            Axis axis = state.getValue(VaultOpenings.AXIS) == Direction.Axis.X ? Axis.X : Axis.Z;
            Optional<VaultPortalShape> found = VaultPortalShape.around(view(level, axis), axis,
                    at.getX(), at.getY(), at.getZ());
            int tier = found.map(shape -> tierOf(level, shape)).orElse(0);
            if (tier == 0 || OPENINGS.containsKey(found.get())) {
                nextLoose.add(at.immutable());
                continue;
            }
            VaultPortalShape shape = found.get();
            for (Cell inside : shape.cells()) {
                done.add(BlockPos.asLong(inside.x(), inside.y(), inside.z()));
            }
            Opening old = before.get(shape);
            OPENINGS.put(shape, old != null && old.tier == tier ? old
                    : new Opening(shape, tier, PortalDoor.hasDoor(inVault, shape)));
        }
        loose = List.copyOf(nextLoose);
    }

    /** Whether the opening's cells or its frame ring lie in one of these sections. */
    private static boolean touches(VaultPortalShape shape, LongSet sections) {
        int minX = shape.minX() - (shape.axis() == Axis.X ? 1 : 0);
        int minZ = shape.minZ() - (shape.axis() == Axis.Z ? 1 : 0);
        int maxX = shape.minX() + (shape.axis() == Axis.X ? shape.width() : 0);
        int maxZ = shape.minZ() + (shape.axis() == Axis.Z ? shape.width() : 0);
        int minY = shape.minY() - 1;
        int maxY = shape.minY() + shape.height();
        for (long key : sections) {
            int x0 = SectionPos.sectionToBlockCoord(SectionPos.x(key));
            int y0 = SectionPos.sectionToBlockCoord(SectionPos.y(key));
            int z0 = SectionPos.sectionToBlockCoord(SectionPos.z(key));
            if (maxX >= x0 && minX < x0 + SectionPos.SECTION_SIZE && maxY >= y0 && minY < y0 + SectionPos.SECTION_SIZE
                    && maxZ >= z0 && minZ < z0 + SectionPos.SECTION_SIZE) {
                return true;
            }
        }
        return false;
    }

    /** The highest tier among the cells, or 0 if any is a sealed door: a half-pressed opening gets no drawn door. */
    private static int tierOf(ClientLevel level, VaultPortalShape shape) {
        int tier = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Cell cell : shape.cells()) {
            BlockState state = level.getBlockState(at.set(cell.x(), cell.y(), cell.z()));
            if (!(state.getBlock() instanceof VaultPortalBlock portal)) {
                return 0;
            }
            tier = Math.max(tier, portal.tier());
        }
        return tier;
    }

    /** The scan's view (§3.2), with an opening on the other axis read as in the way, as the press reads it. */
    private static VaultPortalShape.View view(ClientLevel level, Axis axis) {
        Direction.Axis wanted = axis == Axis.X ? Direction.Axis.X : Direction.Axis.Z;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            BlockState state = level.getBlockState(at.set(x, y, z));
            if (state.is(ModBlocks.VAULT_FRAME.get())) {
                return Kind.FRAME;
            }
            if (state.isAir()) {
                return Kind.AIR;
            }
            boolean opening = state.getBlock() instanceof VaultPortalBlock || state.getBlock() instanceof VaultDoorBlock;
            return opening && state.getValue(VaultOpenings.AXIS) == wanted ? Kind.PORTAL : Kind.OTHER;
        };
    }
}
