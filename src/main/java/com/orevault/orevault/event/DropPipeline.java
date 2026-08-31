package com.orevault.orevault.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.orevault.orevault.OreVault;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The single ordered path every node that modifies a Vault ore drop runs
 * through (§11).
 *
 * <p>Nine nodes change what an ore break drops, and the order they apply in
 * changes the answer. Without one contract they would each patch the same
 * handler and fight: does Greedy Seams double before or after Ore Doubling,
 * does Smelter's Intuition smelt the doubled output, does Tithe consume before
 * Fortune rolls. This defines the order once; each node ticket then registers
 * into a stage rather than adding a listener.</p>
 *
 * <h2>Why stages contribute rather than mutate</h2>
 *
 * <p>Fortune and Quantity handlers do not touch the drop list. They contribute
 * to {@link Outcome} and the pipeline applies the total once. That is what makes
 * the guarantees structural instead of conventional: multipliers compose
 * multiplicatively because they are multiplied together, not because every node
 * author remembered to; and Fortune re-rolls the loot table exactly once no
 * matter how many nodes raise it. Transform and Bonus handlers do edit the list,
 * because there is nothing to compose — they replace and append.</p>
 *
 * <h2>Why Fortune runs before Quantity</h2>
 *
 * <p>The original §11 order ran Quantity first while also specifying that
 * Fortune "discards the existing drop list and re-rolls". Those are
 * incompatible: the re-roll throws away everything Quantity just added, so Ore
 * Doubling and Greedy Seams would silently do nothing whenever a Fortune node
 * was also unlocked. Running Fortune first also answers the other two ordering
 * questions cleanly — Tithe short-circuits before Fortune ever rolls, and
 * Transform still follows Quantity so Smelter's smelts the doubled output.</p>
 */
public final class DropPipeline {

    /** Ordered stages; declaration order is execution order. */
    public enum Stage {
        /** Block consumed outright — Tithe, Brittle Stone. Short-circuits the rest. */
        CONSUME,
        /** Effective Fortune bonus — Vein Fortune, Vault's Purity. Re-rolls the loot table. */
        FORTUNE,
        /** Output multipliers — Ore Doubling, Greedy Seams, Automated Extraction. */
        QUANTITY,
        /** Replacements in place — Smelter's Intuition, Runic Attunement, dust substitution. */
        TRANSFORM,
        /** Extra drops appended — Stone Memory, Ancient Knowledge, Stonecaller, Vault Echo. */
        BONUS
    }

    /** A node's contribution to one stage. */
    @FunctionalInterface
    public interface DropStage {
        void apply(VaultBreakContext context, Outcome outcome, List<ItemEntity> drops);
    }

    /**
     * What the pipeline decided, accumulated across stages and read afterwards
     * by the Resonance award.
     */
    public static final class Outcome {

        private boolean consumed;
        private boolean consumedByTithe;
        private int bonusFortune;
        private double quantityMultiplier = 1.0;

        /**
         * Consumes the block: nothing drops and no later stage runs.
         *
         * @param byTithe true for Tithe specifically, which pays 1.75× Resonance
         *                for the consumed block (§4.2). Brittle Stone consumes
         *                without the bonus.
         */
        public void consume(boolean byTithe) {
            this.consumed = true;
            this.consumedByTithe |= byTithe;
        }

        /** Raises effective Fortune for the single re-roll. */
        public void addFortune(int levels) {
            if (levels > 0) {
                bonusFortune += levels;
            }
        }

        /** Multiplies the output. Composes with every other Quantity node. */
        public void multiplyQuantity(double factor) {
            if (factor > 0) {
                quantityMultiplier *= factor;
            }
        }

        public boolean consumed() {
            return consumed;
        }

        /** Read by the Resonance award for the §4.2 1.75× Tithe modifier. */
        public boolean consumedByTithe() {
            return consumedByTithe;
        }

        public int bonusFortune() {
            return bonusFortune;
        }

        public double quantityMultiplier() {
            return quantityMultiplier;
        }
    }

    private static final Map<Stage, List<DropStage>> STAGES = new EnumMap<>(Stage.class);

    static {
        for (Stage stage : Stage.values()) {
            STAGES.put(stage, new ArrayList<>());
        }
    }

    private DropPipeline() {
    }

    /** Registers a node's handler into a stage. Call during mod construction. */
    public static void register(Stage stage, DropStage handler) {
        STAGES.get(stage).add(handler);
    }

    // ----- the single listener -----

    /**
     * The only {@code BlockDropsEvent} listener in the mod. It resolves the
     * context, runs the pipeline, and hands both to the Resonance award.
     *
     * <p>Order matters in one specific way: the pipeline runs <b>before</b> the
     * award, because Tithe's stage-1 consume multiplies that block's Resonance
     * by 1.75 and the award has to know.</p>
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        VaultBreakContext context = VaultBreakContext.of(event);
        if (context == null) {
            return; // not a Vault break
        }

        Outcome outcome = run(context, event.getDrops());
        if (outcome.consumed()) {
            event.getDrops().clear();
        }
        OreDropHandler.awardResonance(context, outcome);
    }

    /** Runs every stage in order, applying the accumulated totals. */
    static Outcome run(VaultBreakContext context, List<ItemEntity> drops) {
        Outcome outcome = new Outcome();

        runStage(Stage.CONSUME, context, outcome, drops);
        if (outcome.consumed()) {
            return outcome; // nothing else can act on a block that no longer drops
        }

        runStage(Stage.FORTUNE, context, outcome, drops);
        applyFortune(context, outcome, drops);

        runStage(Stage.QUANTITY, context, outcome, drops);
        applyQuantity(outcome, drops);

        runStage(Stage.TRANSFORM, context, outcome, drops);
        runStage(Stage.BONUS, context, outcome, drops);
        return outcome;
    }

    private static void runStage(Stage stage, VaultBreakContext context, Outcome outcome, List<ItemEntity> drops) {
        for (DropStage handler : STAGES.get(stage)) {
            handler.apply(context, outcome, drops);
        }
    }

    // ----- stage application -----

    /**
     * Re-rolls the loot table with a Fortune-boosted copy of the tool.
     *
     * <p>{@code BlockDropsEvent} fires after {@code Block#getDrops} has already
     * rolled, so a Fortune bonus cannot be added to the list — the roll has to
     * happen again. Two constraints follow. The re-roll is skipped entirely when
     * the bonus is zero or the block dropped nothing — the first is the common
     * case and, more importantly, is what keeps drops that <em>other mods</em>
     * appended to this event from being discarded; the second would be wasted
     * work. And the new stacks are wrapped back into item entities carrying
     * the position and motion of the ones they replace, so a re-rolled drop does
     * not visibly behave differently from an ordinary one.</p>
     */
    private static void applyFortune(VaultBreakContext context, Outcome outcome, List<ItemEntity> drops) {
        if (outcome.bonusFortune() <= 0 || drops.isEmpty()) {
            // Nothing dropped means there is no item entity to carry position and
            // motion over from, so {@link #replaceDrops} would throw the re-roll
            // away — see the note there. Bailing here skips the loot roll too.
            return;
        }

        Holder<Enchantment> fortune = context.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);

        ItemStack tool = context.tool().copy();
        int existing = EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
        tool.enchant(fortune, existing + outcome.bonusFortune());

        List<ItemStack> rerolled = Block.getDrops(
                context.state(), context.level(), context.pos(), null, context.breaker(), tool);

        replaceDrops(drops, rerolled);
    }

    /**
     * Scales the drop list by the composed multiplier. The fractional part is a
     * probability rather than a rounding, so a 1.5× node pays 1 or 2 per break
     * and averages 1.5 instead of always paying the same rounded number.
     */
    private static void applyQuantity(Outcome outcome, List<ItemEntity> drops) {
        double multiplier = outcome.quantityMultiplier();
        if (multiplier == 1.0 || drops.isEmpty()) {
            return;
        }
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            double exact = stack.getCount() * multiplier;
            int whole = (int) exact;
            if (entity.level().getRandom().nextDouble() < exact - whole) {
                whole++;
            }
            stack.setCount(Math.max(0, Math.min(whole, stack.getMaxStackSize())));
        }
        drops.removeIf(entity -> entity.getItem().isEmpty());
    }

    /** Swaps the drop list's contents for {@code stacks}, reusing the existing motion. */
    private static void replaceDrops(List<ItemEntity> drops, List<ItemStack> stacks) {
        ItemEntity template = drops.isEmpty() ? null : drops.get(0);
        if (template == null) {
            // Nothing dropped originally, so there is no motion to carry over and
            // no entity to clone. A re-roll cannot invent one; the Bonus stage is
            // where drops get added from nothing.
            return;
        }

        List<ItemEntity> replacement = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity entity = new ItemEntity(
                    template.level(), template.getX(), template.getY(), template.getZ(), stack);
            entity.setDeltaMovement(template.getDeltaMovement());
            entity.setDefaultPickUpDelay();
            replacement.add(entity);
        }
        drops.clear();
        drops.addAll(replacement);
    }

    /** Test seam: drops every registered stage handler. */
    static void clearForTest() {
        STAGES.values().forEach(List::clear);
    }

    /** Logs the registered stage counts; called once at startup for diagnostics. */
    public static void logRegistrations() {
        StringBuilder summary = new StringBuilder();
        for (Stage stage : Stage.values()) {
            summary.append(stage).append('=').append(STAGES.get(stage).size()).append(' ');
        }
        OreVault.LOGGER.debug("Drop pipeline stages: {}", summary.toString().trim());
    }
}
