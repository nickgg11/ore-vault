package com.orevault.orevault.gametest;

import com.orevault.orevault.OreVault;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Every gametest of the mod is registered here, in a source set of its own so none of it ships in the jar.
 * {@code harness/deliberate_failure} stays as manual-only: it proved a failure is reported and fails the run
 * (E1-S2), and setting {@code manualOnly} to false repeats that proof.
 */
@EventBusSubscriber(modid = OreVault.MOD_ID)
public final class GameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int MAX_TICKS = 20;

    private static final ResourceKey<Consumer<GameTestHelper>> PASSES = function("harness/passes");
    private static final ResourceKey<Consumer<GameTestHelper>> DELIBERATE_FAILURE = function("harness/deliberate_failure");
    private static final ResourceKey<Consumer<GameTestHelper>> LANG_COVERAGE = function("lang/coverage");

    private GameTests() {
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OreVault.MOD_ID, path);
    }

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            registry.register(PASSES, GameTestHelper::succeed);
            registry.register(DELIBERATE_FAILURE, helper -> helper.fail(Component.literal("deliberate failure")));
            registry.register(LANG_COVERAGE, LangCoverage::everyKeyHasAValue);
        });
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        event.registerTest(PASSES.identifier(), new FunctionGameTestInstance(PASSES, data(environment, false)));
        event.registerTest(LANG_COVERAGE.identifier(), new FunctionGameTestInstance(LANG_COVERAGE, data(environment, false)));
        event.registerTest(DELIBERATE_FAILURE.identifier(),
                new FunctionGameTestInstance(DELIBERATE_FAILURE, data(environment, true)));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> data(
            Holder<TestEnvironmentDefinition<?>> environment, boolean manualOnly) {
        return new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, 0, true, Rotation.NONE, manualOnly, 1, 1, false, 0);
    }
}
