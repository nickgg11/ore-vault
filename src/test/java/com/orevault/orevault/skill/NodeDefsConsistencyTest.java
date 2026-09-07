package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;

/**
 * Structural invariants over the whole node registry.
 *
 * <p>{@link NodeDefs} is a transcription of §6.1, and transcriptions go wrong quietly: a prereq
 * pointing at a node id that was renamed, a fork option whose parent moved cluster, a keystone
 * left outside Mastery. None of those throw — they produce a node that can never be bought, or a
 * rule the spec states and the code does not keep. These are the checks that turn each of those
 * into a build failure instead of a playtest report.</p>
 */
class NodeDefsConsistencyTest {

    @Test
    void everyPrerequisiteNamesARealNode() {
        List<String> broken = new ArrayList<>();
        for (NodeDef def : NodeDefs.all()) {
            for (Prereq prereq : def.prereqs()) {
                NodeDef target = NodeDefs.get(prereq.nodeId());
                if (target == null) {
                    broken.add(def.id() + " -> " + prereq.nodeId() + " (no such node)");
                } else if (prereq.minTier() > target.maxTier()) {
                    broken.add(def.id() + " -> " + prereq.nodeId() + " T" + prereq.minTier()
                            + " but that node only has " + target.maxTier() + " tiers");
                }
            }
        }
        assertEquals(List.of(), broken, "unbuyable nodes: prerequisites that can never be met");
    }

    @Test
    void prerequisitesStayInsideTheirOwnTree() {
        for (NodeDef def : NodeDefs.all()) {
            for (Prereq prereq : def.prereqs()) {
                assertSame(def.tree(), NodeDefs.get(prereq.nodeId()).tree(),
                        def.id() + " requires a node from the other tree");
            }
        }
    }

    @Test
    void everyForkOptionHasAParentThatDeclaresTheSameFork() {
        for (NodeDef option : NodeDefs.all()) {
            if (option.nodeClass() != NodeClass.FORK_OPTION) {
                continue;
            }
            NodeDef parent = NodeDefs.get(option.forkParentId());
            assertNotNull(parent, option.id() + " has no parent");
            assertSame(NodeClass.FORK_PARENT, parent.nodeClass(),
                    option.id() + " points at " + parent.id() + ", which is not a fork parent");
            assertEquals(parent.forkGroup(), option.forkGroup(),
                    option.id() + " and its parent disagree about which fork they are");
            assertSame(parent.cluster(), option.cluster(),
                    option.id() + " is drawn under a parent in a different cluster");
        }
    }

    @Test
    void everyForkParentHasAtLeastTwoOptions() {
        for (NodeDef parent : NodeDefs.all()) {
            if (parent.nodeClass() != NodeClass.FORK_PARENT) {
                continue;
            }
            assertTrue(NodeDefs.forkOptions(parent.id()).size() >= 2,
                    parent.id() + " is a fork with fewer than two choices, which is not a fork");
        }
    }

    @Test
    void keystonesLiveOnlyInMastery() {
        for (NodeDef def : NodeDefs.all()) {
            if (def.nodeClass() == NodeClass.KEYSTONE) {
                assertSame(Cluster.MASTERY, def.cluster(),
                        def.id() + " is a keystone outside Mastery; it should be a PACT (§6 notation)");
            }
        }
    }

    @Test
    void pactsLiveOnlyOutsideMastery() {
        for (NodeDef def : NodeDefs.all()) {
            if (def.nodeClass() == NodeClass.PACT) {
                assertFalse(def.cluster() == Cluster.MASTERY,
                        def.id() + " is a pact inside Mastery; a pact behind the Mastery gate is a keystone");
            }
        }
    }

    @Test
    void masteryHoldsNothingButKeystones() {
        for (NodeDef def : NodeDefs.getByCluster(Cluster.MASTERY)) {
            assertSame(NodeClass.KEYSTONE, def.nodeClass(), def.id() + " is in Mastery but is not a keystone");
        }
    }

    @Test
    void exclusivityIsMutual() {
        for (NodeDef def : NodeDefs.all()) {
            if (!def.isExclusive()) {
                continue;
            }
            NodeDef partner = NodeDefs.get(def.exclusiveWith());
            assertNotNull(partner, def.id() + " excludes a node that does not exist");
            assertEquals(def.id(), partner.exclusiveWith(),
                    def.id() + " excludes " + partner.id() + " but not the other way round");
        }
    }

    @Test
    void levelRequirementsNeverFallAsTiersRise() {
        for (NodeDef def : NodeDefs.all()) {
            int[] reqs = def.levelReqs();
            for (int i = 1; i < reqs.length; i++) {
                assertTrue(reqs[i] >= reqs[i - 1],
                        def.id() + " tier " + (i + 1) + " is available earlier than tier " + i);
            }
        }
    }

    @Test
    void anchorGatesRiseDownTheTree() {
        List<Cluster> run = List.of(Cluster.PROSPECTING, Cluster.EXCAVATION, Cluster.ASSAY,
                Cluster.METALLURGY, Cluster.CLAIM, Cluster.DEEP_LORE, Cluster.MASTERY);
        for (int i = 1; i < run.size(); i++) {
            assertTrue(NodeDefs.anchorGate(run.get(i)) > NodeDefs.anchorGate(run.get(i - 1)),
                    run.get(i) + " does not open later than " + run.get(i - 1));
        }
    }

    @Test
    void everyGateIsReachableWithTheTreesOwnPoints() {
        int total = NodeDefs.totalTreeCost(Tree.RESONANCE);
        for (Cluster cluster : Cluster.values()) {
            assertTrue(NodeDefs.anchorGate(cluster) < total,
                    cluster + " needs more points than the whole tree contains");
        }
    }

    /**
     * A cluster whose own nodes are the only way to reach its gate can never open. Every gated
     * cluster must be fundable from the clusters that open before it.
     */
    @Test
    void everyGateIsReachableFromEarlierClustersAlone() {
        List<Cluster> run = List.of(Cluster.PROSPECTING, Cluster.EXCAVATION, Cluster.ASSAY,
                Cluster.METALLURGY, Cluster.CLAIM, Cluster.DEEP_LORE, Cluster.MASTERY);
        int available = 0;
        for (Cluster cluster : run) {
            assertTrue(available >= NodeDefs.anchorGate(cluster),
                    cluster + " needs " + NodeDefs.anchorGate(cluster) + " points but only " + available
                            + " are buyable before it opens");
            for (NodeDef def : NodeDefs.getByCluster(cluster)) {
                for (int cost : def.costs()) {
                    available += cost;
                }
            }
        }
    }

    @Test
    void growthNodesAreSinglePointSingleTier() {
        for (NodeDef def : NodeDefs.all()) {
            if (def.nodeClass() != NodeClass.GROWTH) {
                continue;
            }
            assertEquals(1, def.maxTier(), def.id() + ": a growth node that tiers is not self-retiring");
            assertEquals(1, def.costs()[0], def.id() + ": growth nodes are the cheap early pick, 1 point");
        }
    }

    @Test
    void ultimineNodesLiveInBroadCut() {
        for (NodeDef def : NodeDefs.all()) {
            if (def.ultimineOnly()) {
                assertSame(Cluster.BROAD_CUT, def.cluster(),
                        def.id() + " is Ultimine-only but outside the cluster that disappears with it");
            }
        }
    }

    @Test
    void nodeIdsAreSnakeCase() {
        for (NodeDef def : NodeDefs.all()) {
            assertTrue(def.id().matches("[a-z0-9]+(_[a-z0-9]+)*"),
                    def.id() + " is not a stable snake_case id; ids are persisted in save data");
        }
    }
}
