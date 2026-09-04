package com.orevault.orevault.event;

import com.orevault.orevault.entity.ResonanceOrbEntity;
import com.orevault.orevault.ore.OreClassifier;

import net.minecraft.world.phys.Vec3;

/**
 * Turns a Vault ore break into a Resonance orb (§4.2).
 *
 * <p>This registers no listener of its own. {@link DropPipeline} owns the single
 * {@code BlockDropsEvent} listener, resolves the {@link VaultBreakContext}, runs
 * the pipeline and then calls in here — resolving "is this a Vault, is this an
 * ore, who broke it" twice would guarantee the two answers eventually
 * disagree.</p>
 */
public final class OreDropHandler {

    /**
     * Base Resonance per §4.2. Rare takes the midpoint of its 10–15 band; the
     * spread belongs to the node that introduces variance, not to the base rate.
     */
    private static final int COMMON_RESONANCE = 2;
    private static final int UNCOMMON_RESONANCE = 5;
    private static final int RARE_RESONANCE = 12;

    /** Tithe pays 1.75× for the block it consumed (§4.2). */
    private static final double TITHE_MULTIPLIER = 1.75;

    private OreDropHandler() {
    }

    /** Spawns the orb for a completed break, if the break earns one. */
    static void awardResonance(VaultBreakContext context, DropPipeline.Outcome outcome) {
        if (!context.isOre()) {
            return;
        }

        // The machine rule (§4.2, v1.1): anything not broken by a player awards
        // zero Resonance, under every node, with no exceptions. This is not a
        // balance tunable and must not become config or node-modifiable — a
        // single quarry outproduces a player by orders of magnitude, and paying
        // it would collapse a 100-hour progression into an evening on any tech
        // pack. Note the deliberate asymmetry: the drop pipeline has already run
        // for this block, and the vein index will still count it. Mining by
        // machine progresses the world; it does not progress the tree.
        if (context.machineBroken()) {
            return;
        }

        double value = baseResonance(context.rarity());
        if (outcome.consumedByTithe()) {
            value *= TITHE_MULTIPLIER;
        }

        int awarded = (int) Math.round(value);
        if (awarded <= 0) {
            return;
        }

        ResonanceOrbEntity.award(
                context.level(), Vec3.atCenterOf(context.pos()), context.teamId(), awarded);
    }

    /**
     * The §4.2 base rate for a rarity, before Tithe and before team scaling.
     *
     * <p>Public because it is a readout as well as a rule: the playtest command
     * quotes it when filling a test area, and the Tome's node tooltips will want
     * the same number rather than restating it.</p>
     */
    public static int baseResonance(OreClassifier.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> COMMON_RESONANCE;
            case UNCOMMON -> UNCOMMON_RESONANCE;
            case RARE -> RARE_RESONANCE;
        };
    }
}
