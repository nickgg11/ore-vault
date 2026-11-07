package com.orevault.orevault.gametest;

import com.orevault.orevault.OreVault;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Pins every {@code orevault} registry id. Ids are save data: a renamed block id loses every placed block at the
 * next save, and a constant cannot show that registration used it, so this walks the live registries instead
 * (ADR 0005 section 6, amended 2026-11-01). The registries are the ones ADR 0005 names. The lists are literal and
 * read no constant on purpose: a rename, a removal and an unlisted new id all fail here, and the fix for a new id
 * is to add it, while a rename also needs {@code DeferredRegister#addAlias} or a migration.
 */
final class FrozenIdsTests {
    private static final Map<ResourceKey<? extends Registry<?>>, List<String>> FROZEN = Map.of(
            Registries.BLOCK, List.of(
                    "vault_door",
                    "vault_frame",
                    "vault_portal_common",
                    "vault_portal_legendary",
                    "vault_portal_rare",
                    "vault_portal_uncommon"),
            Registries.ITEM, List.of(
                    "vault_frame",
                    "worn_signet"),
            Registries.ENTITY_TYPE, List.of(),
            Registries.DATA_COMPONENT_TYPE, List.of(),
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, List.of(
                    "player"),
            Registries.BIOME, List.of(
                    "vault"),
            Registries.DIMENSION_TYPE, List.of(
                    "ore_vault",
                    "ore_vault_expanded"));

    private FrozenIdsTests() {
    }

    static void everyIdIsListed(GameTestHelper helper) {
        Set<String> problems = new TreeSet<>();
        FROZEN.forEach((registryKey, expected) -> {
            Registry<?> registry = helper.getLevel().registryAccess().lookupOrThrow(registryKey);
            Set<String> live = new TreeSet<>();
            for (Identifier id : registry.keySet()) {
                if (id.getNamespace().equals(OreVault.MOD_ID)) {
                    live.add(id.getPath());
                }
            }
            String name = registryKey.identifier().toString();
            for (String id : expected) {
                if (!live.contains(id)) {
                    problems.add(name + " is missing " + OreVault.MOD_ID + ":" + id);
                }
            }
            for (String id : live) {
                if (!expected.contains(id)) {
                    problems.add(name + " has unlisted " + OreVault.MOD_ID + ":" + id);
                }
            }
        });
        if (!problems.isEmpty()) {
            helper.fail(Component.literal(String.join("; ", problems)));
        }
        helper.succeed();
    }
}
