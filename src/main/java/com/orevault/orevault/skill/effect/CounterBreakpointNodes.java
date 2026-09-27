package com.orevault.orevault.skill.effect;

import java.util.List;
import java.util.UUID;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.event.NodeRewards;
import com.orevault.orevault.event.VaultBreakContext;
import com.orevault.orevault.event.ResonancePipeline;
import com.orevault.orevault.session.VaultSessions;
import com.orevault.orevault.session.VaultTrip;
import com.orevault.orevault.skill.MiningSpeedModifiers;
import com.orevault.orevault.skill.NodeCosts;
import com.orevault.orevault.skill.NodeEffects;
import com.orevault.orevault.skill.ResonanceBonuses;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultChunkGenerator.SkillSnapshot;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The six §6.1 breakpoint nodes: something a player has already done crosses a threshold and the
 * Vault pays them a nicety for it (#140).
 *
 * <p>Deep Habit, Long Delve, Stonecutter's Patience, Calloused Hands, Highwater Mark and Kindred
 * Rock. None of them touch generation and none of them change what an ore drops; every one is a
 * counter read and a small reward. The arithmetic is all in {@link NodeEffects}, where it is unit
 * tested — this class is the wiring, and it deliberately contains no formulas of its own.</p>
 *
 * <h2>Where each one attaches</h2>
 *
 * <ul>
 * <li><b>Resonance</b> — Deep Habit, Calloused Hands and Kindred Rock, through
 *     {@link ResonancePipeline}, so they compose by §11's rule rather than by call order.</li>
 * <li><b>Per-break reward</b> — Stonecutter's Patience (vanilla XP) and Highwater Mark (durability),
 *     through {@link NodeRewards}.</li>
 * <li><b>Mining speed</b> — Long Delve and Kindred Rock, through {@code PlayerEvent.BreakSpeed} and
 *     the order fixed in {@link MiningSpeedModifiers}.</li>
 * <li><b>Hunger</b> — Long Delve, by refunding exhaustion on the player tick. See
 *     {@link #onPlayerTick}.</li>
 * </ul>
 */
public final class CounterBreakpointNodes {

    private static final String DEEP_HABIT = "deep_habit";
    private static final String LONG_DELVE = "long_delve";
    private static final String STONECUTTERS_PATIENCE = "stonecutters_patience";
    private static final String CALLOUSED_HANDS = "calloused_hands";
    private static final String HIGHWATER_MARK = "highwater_mark";
    private static final String KINDRED_ROCK = "kindred_rock";

    /** Armour slots Highwater Mark tier 2 spills into, in the order it fills them. */
    private static final List<EquipmentSlot> ARMOUR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private CounterBreakpointNodes() {
    }

    /**
     * Registers the pipeline handlers. Call from the mod constructor.
     *
     * <p>The event listeners on this class go on {@code NeoForge.EVENT_BUS} separately — they are
     * gameplay events, and a listener on the mod bus would simply never fire.</p>
     */
    public static void registerEffects() {
        ResonancePipeline.register(CounterBreakpointNodes::deepHabit);
        ResonancePipeline.register(CounterBreakpointNodes::callousedHands);
        ResonancePipeline.register(CounterBreakpointNodes::kindredRockResonance);
        NodeRewards.register(CounterBreakpointNodes::stonecuttersPatience);
        NodeRewards.register(CounterBreakpointNodes::highwaterMark);
    }

    // =====================================================================
    // Resonance contributions
    // =====================================================================

    /** Deep Habit: +5% per 1,000 blocks broken on this trip, to +25%, gone on leaving (§6.1). */
    private static void deepHabit(
            VaultBreakContext context,
            PlayerStats stats,
            VaultTrip trip,
            ResonanceBonuses out) {
        if (trip == null || context.resonanceTier(DEEP_HABIT) <= 0) {
            return;
        }
        out.add(NodeEffects.deepHabitBonus(trip.blocksBroken()));
    }

    /** Calloused Hands: a logarithmic bonus from lifetime blocks broken (§6.1). */
    private static void callousedHands(
            VaultBreakContext context,
            PlayerStats stats,
            VaultTrip trip,
            ResonanceBonuses out) {
        out.add(NodeEffects.callousedHandsBonus(
                context.resonanceTier(CALLOUSED_HANDS), stats.getTotalBlocksBroken()));
    }

    /** Kindred Rock: +15% Resonance from an ore type this player has mined 1,000 of (§6.1). */
    private static void kindredRockResonance(
            VaultBreakContext context,
            PlayerStats stats,
            VaultTrip trip,
            ResonanceBonuses out) {
        if (isAttuned(context.resonanceTier(KINDRED_ROCK), stats, context.state())) {
            out.add(NodeCosts.KINDRED_ROCK_RESONANCE_BONUS);
        }
    }

    // =====================================================================
    // Per-break rewards
    // =====================================================================

    /** Stonecutter's Patience: bonus vanilla XP per ore, earned by lifetime stone broken (§6.1). */
    private static void stonecuttersPatience(
            VaultBreakContext context,
            ServerPlayer player,
            PlayerStats stats,
            VaultTrip trip) {
        if (!context.isOre()) {
            return;
        }
        int xp = NodeEffects.stonecuttersPatienceXp(
                context.resonanceTier(STONECUTTERS_PATIENCE), stats.getStoneBroken());
        if (xp > 0) {
            // Additive to the ore's own vanilla XP, which is never suppressed in a Vault
            // (§4.2, and the 2026-09-03 design log entry that put it there).
            player.giveExperiencePoints(xp);
        }
    }

    /**
     * Highwater Mark: each ore repairs the tool that broke it, by more the deeper the player's
     * all-time record; tier 2 spills onto worn armour when the tool needs nothing (§6.1).
     *
     * <p>Consumes no XP, so it stacks with Mending rather than competing with it.</p>
     */
    private static void highwaterMark(
            VaultBreakContext context,
            ServerPlayer player,
            PlayerStats stats,
            VaultTrip trip) {
        if (!context.isOre()) {
            return;
        }
        int tier = context.resonanceTier(HIGHWATER_MARK);
        int repair = NodeEffects.highwaterMarkRepair(tier, stats.getDeepestY());
        if (repair <= 0) {
            return;
        }

        int spare = repairStack(context.tool(), repair);
        if (spare > 0 && NodeEffects.highwaterMarkRepairsArmour(tier)) {
            repairArmour(player, spare);
        }
    }

    /**
     * Heals {@code amount} durability off one stack.
     *
     * @return the part of {@code amount} the stack had no room for — the whole of it for an item
     *         that cannot be damaged or is already pristine, which is what tier 2 passes on
     */
    private static int repairStack(ItemStack stack, int amount) {
        if (stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) {
            return amount;
        }
        int damage = stack.getDamageValue();
        int applied = Math.min(amount, damage);
        stack.setDamageValue(damage - applied);
        return amount - applied;
    }

    /**
     * Spreads {@code amount} across the damaged armour the player is wearing.
     *
     * <p>Split rather than piled onto the first piece: §6.1 says "split across damaged pieces", and
     * the point of the tier is that a full set slowly comes back, not that a helmet does.</p>
     */
    private static void repairArmour(ServerPlayer player, int amount) {
        List<ItemStack> damaged = ARMOUR_SLOTS.stream()
                .map(player::getItemBySlot)
                .filter(stack -> !stack.isEmpty() && stack.isDamageableItem() && stack.isDamaged())
                .toList();
        if (damaged.isEmpty()) {
            return;
        }
        // Integer division leaves a remainder of at most (pieces - 1), which the first pieces take
        // one each. Dropping it instead would make a 1-point repair with 4 pieces worn do nothing.
        int each = amount / damaged.size();
        int remainder = amount % damaged.size();
        for (int i = 0; i < damaged.size(); i++) {
            int share = each + (i < remainder ? 1 : 0);
            if (share > 0) {
                repairStack(damaged.get(i), share);
            }
        }
    }

    // =====================================================================
    // Mining speed
    // =====================================================================

    /**
     * Long Delve's Delve stacks and Kindred Rock's attunement, applied to one block's break speed.
     *
     * <p>Fires on both sides — the client predicts break progress — so the modifier has to be
     * computed from state the client also has. It is not: Delve stacks and the team's tier map live
     * on the server. The consequence is a cosmetic mismatch in the cracking animation, not a
     * desync: the server decides when the block breaks. Fixing it properly needs the stacks synced
     * to the client, which belongs with the Tome's readout for them rather than here.</p>
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !VaultDimensions.isVaultDimension(player.level())) {
            return;
        }

        SkillSnapshot skills = snapshotFor(player);
        MiningSpeedModifiers modifiers = new MiningSpeedModifiers();

        int longDelveTier = skills.resonanceTier(LONG_DELVE);
        VaultTrip trip = VaultSessions.trip(player);
        if (longDelveTier > 0 && trip != null) {
            int stacks = NodeEffects.longDelveStacks(longDelveTier, trip.activeMinutes());
            modifiers.addBonus(NodeEffects.longDelveMiningSpeedBonus(stacks));
        }

        // Tier 2 is what adds speed; tier 1 pays only Resonance (§6.1).
        int kindredRockTier = skills.resonanceTier(KINDRED_ROCK);
        if (kindredRockTier >= 2) {
            PlayerStats stats = VaultSessions.statsFor(player);
            if (stats != null && isAttuned(kindredRockTier, stats, event.getState())) {
                modifiers.addBonus(NodeCosts.KINDRED_ROCK_BREAK_SPEED_BONUS);
            }
        }

        if (!modifiers.isEmpty()) {
            event.setNewSpeed(modifiers.apply(event.getNewSpeed()));
        }
    }

    // =====================================================================
    // Hunger
    // =====================================================================

    /**
     * Long Delve's hunger reduction: −25% drain per Delve stack.
     *
     * <p>There is no event for exhaustion and no public accessor for it, so this watches the value
     * and refunds the part of each rise the node pays for. The access transformer entry on
     * {@code FoodData.exhaustionLevel} is there for this and says so.</p>
     *
     * <p><b>At the tier-2 cap of four stacks the reduction reaches 100% and hunger stops entirely
     * inside the Vault.</b> That is what §6.1's "−25% per stack" multiplies out to, so it is
     * implemented as written rather than quietly reinterpreted — but it is the kind of number that
     * reads fine in a table and plays differently, and it wants a playtest before 1.0.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VaultTrip trip = VaultSessions.trip(player);
        if (trip == null) {
            return; // not in a Vault; vanilla hunger applies
        }

        int tier = snapshotFor(player).resonanceTier(LONG_DELVE);
        int stacks = tier > 0 ? NodeEffects.longDelveStacks(tier, trip.activeMinutes()) : 0;
        double reduction = Math.min(1.0, stacks * NodeCosts.LONG_DELVE_HUNGER_REDUCTION_PER_STACK);

        float exhaustion = player.getFoodData().exhaustionLevel;
        float refund = trip.refundExhaustion(exhaustion, reduction);
        if (refund > 0.0f) {
            player.getFoodData().exhaustionLevel = exhaustion - refund;
        }
    }

    // =====================================================================
    // Shared
    // =====================================================================

    /** Whether the player has mined enough of this block's type for Kindred Rock to pay (§6.1). */
    private static boolean isAttuned(int tier, PlayerStats stats, BlockState state) {
        if (tier <= 0) {
            return false;
        }
        Block block = state.getBlock();
        // Ancient debris is exempt from every yield node, Kindred Rock named among them (§6.1).
        if (block == Blocks.ANCIENT_DEBRIS) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return NodeEffects.kindredRockAttuned(tier, stats.getOresMined().getOrDefault(id, 0));
    }

    private static SkillSnapshot snapshotFor(ServerPlayer player) {
        UUID teamId = TeamHelper.getTeamId(player);
        return VaultDimensions.skillSnapshot(teamId);
    }
}
