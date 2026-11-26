package com.orevault.orevault.mining;

import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Planted violation of named rule 9; see ArchitectureFixtureTest. */
final class PlantedMiningSetsXp {
    void planted(BlockDropsEvent event) {
        event.setDroppedExperience(0);
    }
}
