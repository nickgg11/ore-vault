package com.orevault.orevault.skill;

import net.neoforged.fml.ModList;

/**
 * Runtime soft-dependency detection, cached once per boot. Must not reference any
 * optional mod's classes.
 */
public final class SoftDeps {
    private static final boolean ULTIMINE = ModList.get().isLoaded("ftbultimine");
    private static final boolean MEKANISM = ModList.get().isLoaded("mekanism");

    private SoftDeps() {
    }

    public static boolean isUltimineLoaded() {
        return ULTIMINE;
    }

    public static boolean isMekanismLoaded() {
        return MEKANISM;
    }
}
