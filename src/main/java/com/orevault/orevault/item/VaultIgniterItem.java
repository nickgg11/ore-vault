package com.orevault.orevault.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.portal.VaultPortalShape;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Vault Igniter (§3.3): one class drives all four tiers.
 *
 * <p><b>Every tier grants a persistent capability, never a stat.</b> The
 * igniter is the player's key, and what it carries is access. The four tiers
 * used to hand out short potion effects on arrival — Speed I for 5s, Haste I
 * for 10s, Haste II for 15s — which were worth nothing: Vault Fever grants
 * permanent Haste II inside the Vault and Efficient Miner owns the hunger axis,
 * so a fifteen-second buff on entry was noise next to either (#100).</p>
 *
 * <table border="1">
 * <caption>Tier capabilities (§3.3)</caption>
 * <tr><th>Tier</th><th>Capability</th></tr>
 * <tr><td>1 Crude</td><td>Opens the portal. Nothing else.</td></tr>
 * <tr><td>2 Attuned</td><td>One personal entry point</td></tr>
 * <tr><td>3 Resonant</td><td>Instant travel (no charge, no cooldown); three entry points</td></tr>
 * <tr><td>4 Sovereign</td><td>Unlocks the Vault Reset button</td></tr>
 * </table>
 *
 * <p>Capabilities are asked for by name — {@link #entryPointCapacity(Player)},
 * {@link #hasInstantTravel(Player)}, {@link #canResetVault(Player)} — rather
 * than by comparing tier numbers at each call site. The tier a capability
 * unlocks has already moved once (instant travel from 4 to 3, entry points from
 * 3 to 2), and scattered {@code >= 3} checks are how one of those moves gets
 * missed.</p>
 */
public class VaultIgniterItem extends Item {

    /** The four igniter tiers (§3.3). */
    public enum Tier {
        CRUDE(1, 20, 0),
        ATTUNED(2, 14, 1),
        RESONANT(3, 14, 3),
        SOVEREIGN(4, 14, 3);

        private final int level;
        private final int activationTicks;
        private final int entryPoints;

        Tier(int level, int activationTicks, int entryPoints) {
            this.level = level;
            this.activationTicks = activationTicks;
            this.entryPoints = entryPoints;
        }

        public int level() {
            return level;
        }

        /** Progressive portal-fill duration; tier 2+ is 30% faster than the 20-tick standard. */
        public int activationTicks() {
            return activationTicks;
        }

        /** How many personal entry points this tier may store (§3.3). */
        public int entryPoints() {
            return entryPoints;
        }

        /** Tier 3+: skips the portal charge and the re-entry cooldown (§3.3). */
        public boolean instantTravel() {
            return level >= 3;
        }

        /** Tier 4: gates the Vault Reset button (§3.3). Consumed by the reset ticket. */
        public boolean unlocksReset() {
            return level >= 4;
        }
    }

    /**
     * Per-player persistent-data key for personal entry points (§3.3).
     *
     * <p>The stored shape is {@code {points: [{x,y,z}, ...], selected: int}}.
     * It used to be a bare {@code {x,y,z}} for the single tier-3 point, and
     * player persistent data is a save file, so {@link #entryPoints} still reads
     * that older shape as a one-element list. Dropping it would silently lose a
     * waypoint every existing player had set.</p>
     */
    public static final String ENTRY_POINT_TAG = "orevault_entry";

    private final Tier tier;

    public VaultIgniterItem(Item.Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    /** The igniter tier of the given stack, or {@code null} if it is not an igniter. */
    @Nullable
    public static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof VaultIgniterItem igniter ? igniter.tier : null;
    }

    /** Highest igniter tier carried by the player (main inventory + offhand); 0 if none. */
    public static int highestTierLevel(Player player) {
        Tier best = highestTier(player);
        return best == null ? 0 : best.level();
    }

    /** Highest igniter the player is carrying, or {@code null} if none. */
    public static @Nullable Tier highestTier(Player player) {
        Tier highest = null;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            highest = better(highest, tierOf(stack));
        }
        return better(highest, tierOf(player.getOffhandItem()));
    }

    private static @Nullable Tier better(@Nullable Tier current, @Nullable Tier candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.level() > current.level() ? candidate : current;
    }

    // ----- capabilities (§3.3) -----

    /** How many entry points the player's best igniter allows; 0 means the feature is locked. */
    public static int entryPointCapacity(Player player) {
        Tier best = highestTier(player);
        return best == null ? 0 : best.entryPoints();
    }

    /** Whether the player skips the portal charge and the re-entry cooldown (tier 3+). */
    public static boolean hasInstantTravel(Player player) {
        Tier best = highestTier(player);
        return best != null && best.instantTravel();
    }

    /**
     * Whether the player may trigger a Vault reset (tier 4).
     *
     * <p>Nothing calls this yet — the reset flow is Phase 8 (#93–#96). It lives
     * here so the gate is defined alongside the other capabilities rather than
     * being reinvented as a tier comparison when that ticket lands.</p>
     */
    public static boolean canResetVault(Player player) {
        Tier best = highestTier(player);
        return best != null && best.unlocksReset();
    }

    // ----- interaction -----

    /**
     * Right-click on a block (§3.2, §3.3):
     * <ul>
     * <li>On a Vault Frame: scan and fill the portal (failure sound if invalid).</li>
     * <li>Inside the Vault, tier 2+: store this block as an entry point.</li>
     * </ul>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockState(pos).is(ModBlocks.VAULT_FRAME)) {
            Optional<VaultPortalShape> shape = VaultPortalShape.find(level, pos);
            if (shape.isPresent()) {
                VaultPortalShape portal = shape.get();
                level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (level instanceof ServerLevel serverLevel) {
                    portal.fillAnimated(serverLevel, tier.activationTicks(), tier.level() >= 2, tier.level());
                } else {
                    portal.fill(level, tier.level());
                }
            } else {
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        if (tier.entryPoints() > 0 && player != null && !level.isClientSide()
                && VaultDimensions.isVaultDimension(level)) {
            storeEntryPoint(player, pos, tier.entryPoints());
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    /**
     * Right-click in the air: cycles which stored entry point is the arrival
     * target, for tiers that hold more than one.
     *
     * <p>This is the interim selector. §3.3 asks for a waypoint list UI, which
     * needs the network channel and screen work in Phase 4 ([32]/[34]) — until
     * those exist, cycling with a message naming the destination is the whole of
     * what can be built, and it makes the three slots usable rather than
     * stored-and-unreachable.</p>
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || tier.entryPoints() <= 1) {
            return InteractionResult.PASS;
        }
        List<BlockPos> points = entryPoints(player);
        if (points.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.orevault.entry_point_none"));
            return InteractionResult.SUCCESS_SERVER;
        }

        int next = (selectedIndex(player) + 1) % points.size();
        setSelectedIndex(player, next);
        BlockPos target = points.get(next);
        player.sendSystemMessage(Component.translatable("message.orevault.entry_point_selected",
                next + 1, points.size(), target.getX(), target.getY(), target.getZ()));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS_SERVER;
    }

    // ----- entry point storage -----

    /**
     * Adds an entry point, or overwrites the selected one once the tier's slots
     * are full.
     *
     * <p>Filling before overwriting is the behaviour that needs no explanation:
     * a tier-3 player's first three clicks each add a waypoint, and only after
     * that does a click replace something. Overwriting the <em>selected</em>
     * slot rather than the oldest means the slot a player just cycled to is the
     * one they replace, so the cycle doubles as "which one am I editing".</p>
     */
    public static void storeEntryPoint(Player player, BlockPos pos, int capacity) {
        List<BlockPos> points = new ArrayList<>(entryPoints(player));
        int slot;
        if (points.size() < capacity) {
            points.add(pos);
            slot = points.size() - 1;
        } else {
            slot = Math.min(selectedIndex(player), capacity - 1);
            points.set(slot, pos);
        }
        writeEntryPoints(player, points, slot);

        Component message = capacity == 1
                ? Component.translatable("message.orevault.entry_point_set")
                : Component.translatable("message.orevault.entry_point_stored", slot + 1, capacity);
        player.sendSystemMessage(message);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /**
     * The player's stored entry points, oldest first.
     *
     * <p>Reads the legacy bare {@code {x,y,z}} shape as a single point; see
     * {@link #ENTRY_POINT_TAG}.</p>
     */
    public static List<BlockPos> entryPoints(Player player) {
        Optional<CompoundTag> root = player.getPersistentData().getCompound(ENTRY_POINT_TAG);
        if (root.isEmpty()) {
            return List.of();
        }
        CompoundTag tag = root.get();

        Optional<ListTag> stored = tag.getList("points");
        if (stored.isEmpty()) {
            // Pre-#100 save: one point held directly on the root tag.
            return readPos(tag).map(List::of).orElse(List.of());
        }

        List<BlockPos> points = new ArrayList<>();
        for (Tag element : stored.get()) {
            if (element instanceof CompoundTag entry) {
                readPos(entry).ifPresent(points::add);
            }
        }
        return points;
    }

    /** The entry point the player currently arrives at, if any. */
    public static Optional<BlockPos> selectedEntryPoint(Player player) {
        List<BlockPos> points = entryPoints(player);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        // Clamped rather than trusted: the tier that stored the index may be
        // gone from the player's inventory, or the list may have shrunk.
        return Optional.of(points.get(Math.min(selectedIndex(player), points.size() - 1)));
    }

    private static int selectedIndex(Player player) {
        return player.getPersistentData().getCompound(ENTRY_POINT_TAG)
                .flatMap(tag -> tag.getInt("selected"))
                .filter(index -> index >= 0)
                .orElse(0);
    }

    private static void setSelectedIndex(Player player, int index) {
        writeEntryPoints(player, entryPoints(player), index);
    }

    private static void writeEntryPoints(Player player, List<BlockPos> points, int selected) {
        ListTag list = new ListTag();
        for (BlockPos point : points) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", point.getX());
            entry.putInt("y", point.getY());
            entry.putInt("z", point.getZ());
            list.add(entry);
        }
        CompoundTag root = new CompoundTag();
        root.put("points", list);
        root.putInt("selected", Math.max(0, selected));
        player.getPersistentData().put(ENTRY_POINT_TAG, root);
    }

    private static Optional<BlockPos> readPos(CompoundTag tag) {
        Optional<Integer> x = tag.getInt("x");
        Optional<Integer> y = tag.getInt("y");
        Optional<Integer> z = tag.getInt("z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(x.get(), y.get(), z.get()));
    }
}
