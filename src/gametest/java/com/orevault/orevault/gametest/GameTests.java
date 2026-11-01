package com.orevault.orevault.gametest;

import com.orevault.orevault.OreVault;
import java.util.List;
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
    private static final ResourceKey<Consumer<GameTestHelper>> FRAME_PICKAXE_DROP = function("vault/frame_pickaxe_drop");
    private static final ResourceKey<Consumer<GameTestHelper>> FRAME_HAND_NO_DROP = function("vault/frame_hand_no_drop");
    private static final ResourceKey<Consumer<GameTestHelper>> FRAME_RECIPE = function("vault/frame_recipe");
    private static final ResourceKey<Consumer<GameTestHelper>> FRAME_PROPERTIES = function("vault/frame_properties");
    private static final ResourceKey<Consumer<GameTestHelper>> SIGNET_STACK_SIZE = function("vault/signet_stack_size");
    private static final ResourceKey<Consumer<GameTestHelper>> SIGNET_RECIPE = function("vault/signet_recipe");

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
            registry.register(FRAME_PICKAXE_DROP, VaultFrameTests::dropsItselfToAPickaxe);
            registry.register(FRAME_HAND_NO_DROP, VaultFrameTests::dropsNothingByHand);
            registry.register(FRAME_RECIPE, VaultFrameTests::recipeYieldsExactlyOne);
            registry.register(FRAME_PROPERTIES, VaultFrameTests::hasTheSpecProperties);
            registry.register(SIGNET_STACK_SIZE, SignetTests::stacksToOne);
            registry.register(SIGNET_RECIPE, SignetTests::recipeTakesIronAndRedstoneSideBySide);
        });
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        for (ResourceKey<Consumer<GameTestHelper>> test : List.of(
                PASSES, LANG_COVERAGE, FRAME_PICKAXE_DROP, FRAME_HAND_NO_DROP, FRAME_RECIPE, FRAME_PROPERTIES,
                SIGNET_RECIPE, SIGNET_STACK_SIZE)) {
            event.registerTest(test.identifier(), new FunctionGameTestInstance(test, data(environment, false)));
        }
        event.registerTest(DELIBERATE_FAILURE.identifier(),
                new FunctionGameTestInstance(DELIBERATE_FAILURE, data(environment, true)));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> data(
            Holder<TestEnvironmentDefinition<?>> environment, boolean manualOnly) {
        return new TestData<>(environment, EMPTY_STRUCTURE, MAX_TICKS, 0, true, Rotation.NONE, manualOnly, 1, 1, false, 0);
    }
}
