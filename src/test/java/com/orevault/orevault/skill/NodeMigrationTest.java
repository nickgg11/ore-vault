package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.orevault.orevault.skill.NodeDef.Tree;

/**
 * The §6.1 node removals and renames, applied to a real team's saved tiers.
 *
 * <p>These are player save files. A rename that drops a tier silently deletes hours of
 * progression, and a removal that does not hand the points back charges a team for a node this
 * build will not let them buy. Both are invisible in play until someone counts their points, so
 * they are asserted here instead.</p>
 */
class NodeMigrationTest {

    private static Map<String, Integer> tiers(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void oreSenseBecomesVeinFortuneAtTheSameTier() {
        NodeMigration.Result result = NodeMigration.apply(tiers("ore_sense", 3));

        assertEquals(3, result.tiers().get("vein_fortune"));
        assertFalse(result.tiers().containsKey("ore_sense"));
        assertEquals(0, result.pointsRefunded(), "a rename costs the team nothing and gives nothing");
    }

    @Test
    void efficientMinerBecomesMinersConstitutionAtTheSameTier() {
        NodeMigration.Result result = NodeMigration.apply(tiers("efficient_miner", 5));

        assertEquals(5, result.tiers().get("miners_constitution"));
        assertFalse(result.tiers().containsKey("efficient_miner"));
    }

    @Test
    void removedNodesHandBackExactlyWhatTheyCost() {
        // The three Ore Boosts at full tier: 1+1+2, 1+2+2, 2+2+3 under the costs they had.
        NodeMigration.Result result = NodeMigration.apply(
                tiers("common_ore_boost", 3, "uncommon_ore_boost", 3, "rare_ore_boost", 3));

        assertEquals(4 + 5 + 7, result.pointsRefunded());
        assertTrue(result.tiers().isEmpty());
    }

    @Test
    void aPartlyBoughtRemovedNodeRefundsOnlyTheTiersPaidFor() {
        NodeMigration.Result result = NodeMigration.apply(tiers("rare_ore_boost", 2));

        assertEquals(2 + 2, result.pointsRefunded());
    }

    @Test
    void motherlodeRefundsRatherThanBecomingVeinSingularity() {
        NodeMigration.Result result = NodeMigration.apply(tiers("motherlode", 2));

        assertEquals(3 + 4, result.pointsRefunded());
        assertFalse(result.tiers().containsKey("vein_singularity"),
                "Vein Singularity is a free option under a parent this team never paid for");
    }

    @Test
    void disturbedZoneUnlockIsRefundedWithTheAnimusDeferral() {
        NodeMigration.Result result = NodeMigration.apply(tiers("disturbed_zone_unlock", 1));

        assertEquals(1, result.pointsRefunded());
        assertTrue(result.tiers().isEmpty());
    }

    @Test
    void nodesThisBuildStillDefinesArePassedThroughUntouched() {
        Map<String, Integer> stored = tiers("vein_expansion", 5, "stone_memory", 2, "tithe", 1);

        NodeMigration.Result result = NodeMigration.apply(stored);

        assertEquals(stored, result.tiers());
        assertEquals(0, result.pointsRefunded());
    }

    @Test
    void anUnrecognisedIdIsDroppedWithoutARefund() {
        // A node from a fork of the mod, or a build newer than this one. There is no cost table
        // for it, so inventing a refund would hand out points the team never spent.
        NodeMigration.Result result = NodeMigration.apply(tiers("something_from_elsewhere", 2));

        assertTrue(result.tiers().isEmpty());
        assertEquals(0, result.pointsRefunded());
    }

    @Test
    void aRenameNeverProducesATierTheNodeCannotHold() {
        NodeMigration.Result result = NodeMigration.apply(tiers("ore_sense", 99));

        assertEquals(NodeDefs.get("vein_fortune").maxTier(), result.tiers().get("vein_fortune"));
    }

    @Test
    void migratingTwiceChangesNothingTheSecondTime() {
        NodeMigration.Result once = NodeMigration.apply(
                tiers("ore_sense", 3, "rare_ore_boost", 3, "vein_expansion", 1));
        NodeMigration.Result twice = NodeMigration.apply(once.tiers());

        assertEquals(once.tiers(), twice.tiers());
        assertEquals(0, twice.pointsRefunded(), "a second pass must not pay the refund again");
    }

    @Test
    void everySurvivingIdIsANodeThisBuildDefines() {
        NodeMigration.Result result = NodeMigration.apply(
                tiers("ore_sense", 3, "efficient_miner", 4, "motherlode", 1, "vein_expansion", 2));

        for (String id : result.tiers().keySet()) {
            assertEquals(Tree.RESONANCE, NodeDefs.get(id).tree(), id + " survived migration but is not a node");
        }
    }
}
