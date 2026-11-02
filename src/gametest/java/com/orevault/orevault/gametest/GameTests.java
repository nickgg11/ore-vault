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
    private static final ResourceKey<Consumer<GameTestHelper>> FRAME_CONNECTS = function("vault/frame_connects");
    private static final ResourceKey<Consumer<GameTestHelper>> FRAME_ROUNDED = function("vault/frame_rounded");
    private static final ResourceKey<Consumer<GameTestHelper>> SIGNET_STACK_SIZE = function("vault/signet_stack_size");
    private static final ResourceKey<Consumer<GameTestHelper>> SIGNET_RECIPE = function("vault/signet_recipe");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_UNKNOWN = function("team/unknown_is_one");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_SOLO = function("team/solo");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_PARTY = function("team/party_and_disband");
    private static final ResourceKey<Consumer<GameTestHelper>> DEBUG_GATE = function("debug/gate");
    private static final ResourceKey<Consumer<GameTestHelper>> DEBUG_BREAK = function("debug/break_removes_the_block");
    private static final ResourceKey<Consumer<GameTestHelper>> DEBUG_BREAK_EMPTY = function("debug/break_on_air_fails");
    private static final ResourceKey<Consumer<GameTestHelper>> DEBUG_BREAK_UNLOADED = function("debug/break_loads_the_chunk");
    private static final ResourceKey<Consumer<GameTestHelper>> DEBUG_DIAG_LEVEL = function("debug/diag_reads_the_players_level");
    private static final ResourceKey<Consumer<GameTestHelper>> DEBUG_DIAG = function("debug/diag_prints_the_break_state");

    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SEALS = function("portal/seals_on_the_last_block");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SEALS_Z = function("portal/seals_along_z");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_PISTON = function("portal/piston_completes_a_ring");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_FAKE_PLAYER = function("portal/placement_and_press_by_a_player");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SIGNET_PASSES = function("portal/signet_passes_on_other_blocks");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_OBSTRUCTED = function("portal/obstructed_ring_gets_no_door");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_RESEAL = function("portal/placing_again_seals_an_empty_ring");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_LIVE_GUARD = function("portal/corner_added_later_leaves_a_live_portal");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_CROSSING_SEALED = function("portal/crossing_rings_sealed_by_one_block");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_CROSSING_REFUSED = function("portal/crossing_rings_refuse_the_other_axis");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SWAP = function("portal/press_on_a_door_swaps_it");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_TIERS = function("portal/lower_signet_refused_higher_upgrades");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_ORPHAN = function("portal/door_with_no_ring_refuses");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SIDE_DOOR = function("portal/side_break_dissolves_a_door");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SIDE_PORTAL = function("portal/side_break_dissolves_a_portal");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_CORNER = function("portal/corner_break_leaves_it_standing");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_HOLE_DOOR = function("portal/hole_in_a_door_dissolves_the_rest");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_HOLE_PORTAL = function("portal/hole_in_a_portal_dissolves_the_rest");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SHARED_SURVIVES = function("portal/shared_bar_survives_one_dissolve");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_SHARED_BOTH = function("portal/breaking_a_shared_bar_dissolves_both");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_WATER = function("portal/water_leaves_a_portal_alone");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_CREATIVE = function("portal/creative_break_refused_unless_sneaking");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_ENTITY = function("portal/only_a_player_starts_a_charge");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_PROPERTIES = function("portal/block_properties");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_NO_LOAD = function("portal/scan_never_loads_a_chunk");
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_DISSOLVE_WATER = function("portal/dissolve_lets_water_in");

    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_CODEC = function("team_data/codec_round_trips");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_STORAGE = function("team_data/write_lands_in_the_servers_storage");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_READ = function("team_data/read_writes_nothing");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_LOGIN = function("team_data/first_login_and_party_join_write_nothing");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_FAKE = function("team_data/fake_player_refused");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_REAL = function("team_data/real_player_written_through");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_DISBAND = function("team_data/disband_sets_disbanded_at");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_DISBAND_EMPTY = function("team_data/disband_with_nothing_writes_nothing");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_DISBAND_FOLDER = function("team_data/disband_with_only_a_folder_is_recorded");
    private static final ResourceKey<Consumer<GameTestHelper>> TEAM_DATA_UNREADABLE = function("team_data/unreadable_file_copied_aside");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_BASE = function("worldgen/fills_the_base_stack");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_EXPANDED = function("worldgen/fills_the_expanded_stack");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_ONE_READ = function("worldgen/reads_the_snapshot_once_a_chunk");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_VARIANT = function("worldgen/refuses_the_other_variant");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_NO_MOBS = function("worldgen/no_mobs_at_for_every_category");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_SEED = function("worldgen/seed_from_team_and_chunk");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_HEIGHTS = function("worldgen/height_questions");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_CODEC = function("worldgen/codec_is_a_unit");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_DIMENSION_TYPES = function("worldgen/dimension_types_load");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_BIOME = function("worldgen/biome_loads_with_no_spawns");
    private static final ResourceKey<Consumer<GameTestHelper>> WORLDGEN_LAYERS = function("worldgen/layer_files_load");

    private GameTests() {
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OreVault.MOD_ID, path);
    }

    /** A test that throws fails with what it threw, so a test around file or reflection calls need not catch. */
    private static Consumer<GameTestHelper> unchecked(ThrowingTest test) {
        return helper -> {
            try {
                test.run(helper);
            } catch (Exception e) {
                helper.fail(Component.literal(e.toString()));
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingTest {
        void run(GameTestHelper helper) throws Exception;
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
            registry.register(FRAME_CONNECTS, VaultFrameTests::connectsOnlyToFrames);
            registry.register(FRAME_ROUNDED, VaultFrameTests::roundedCornerHasItsShapeAndStillDropsItself);
            registry.register(SIGNET_STACK_SIZE, SignetTests::stacksToOne);
            registry.register(SIGNET_RECIPE, SignetTests::recipeTakesIronAndRedstoneSideBySide);
            registry.register(TEAM_UNKNOWN, TeamTests::anUnknownIdIsATeamOfOne);
            registry.register(TEAM_SOLO, TeamTests::aSoloPlayerIsTheirOwnTeam);
            registry.register(TEAM_PARTY, TeamTests::aPartyChangesTheTeamAndItsLastMemberDisbandsIt);
            registry.register(DEBUG_GATE, DebugCommandTests::theRootIsAlwaysThereAndTheSubcommandsAreGated);
            registry.register(DEBUG_BREAK, DebugCommandTests::breakRemovesTheBlockAndSaysSo);
            registry.register(DEBUG_BREAK_EMPTY, DebugCommandTests::breakOnAirFails);
            registry.register(DEBUG_BREAK_UNLOADED, DebugCommandTests::breakLoadsTheChunkItNeeds);
            registry.register(DEBUG_DIAG_LEVEL, DebugCommandTests::diagReadsThePlayersLevelUnderExecuteIn);
            registry.register(DEBUG_DIAG, DebugCommandTests::diagPrintsTheBreakState);
            registry.register(PORTAL_SEALS, PortalTests::sealsOnTheLastBlock);
            registry.register(PORTAL_SEALS_Z, PortalTests::sealsAlongZ);
            registry.register(PORTAL_PISTON, PortalTests::aPistonCompletesARing);
            registry.register(PORTAL_FAKE_PLAYER, PortalTests::placementAndThePressThroughAFakePlayer);
            registry.register(PORTAL_SIGNET_PASSES, PortalTests::aSignetPassesOnOtherBlocks);
            registry.register(PORTAL_OBSTRUCTED, PortalTests::anObstructedRingGetsNoDoor);
            registry.register(PORTAL_RESEAL, PortalTests::placingAFrameAgainSealsAnEmptyRing);
            registry.register(PORTAL_LIVE_GUARD, PortalTests::aCornerAddedLaterLeavesALivePortal);
            registry.register(PORTAL_CROSSING_SEALED, PortalTests::crossingRingsAreSealedByOneBlock);
            registry.register(PORTAL_CROSSING_REFUSED, PortalTests::crossingRingsRefuseTheOtherAxis);
            registry.register(PORTAL_SWAP, PortalTests::aPressOnADoorSwapsItForThePortal);
            registry.register(PORTAL_TIERS, PortalTests::aLowerSignetIsRefusedAndAHigherUpgrades);
            registry.register(PORTAL_ORPHAN, PortalTests::aDoorWithNoRingRefuses);
            registry.register(PORTAL_SIDE_DOOR, PortalTests::aSideBreakDissolvesADoor);
            registry.register(PORTAL_SIDE_PORTAL, PortalTests::aSideBreakDissolvesAPortal);
            registry.register(PORTAL_CORNER, PortalTests::aCornerBreakLeavesItStanding);
            registry.register(PORTAL_HOLE_DOOR, PortalTests::aHoleInADoorDissolvesTheRest);
            registry.register(PORTAL_HOLE_PORTAL, PortalTests::aHoleInAPortalDissolvesTheRest);
            registry.register(PORTAL_SHARED_SURVIVES, PortalTests::aSharedBarSurvivesOnePortalDissolving);
            registry.register(PORTAL_SHARED_BOTH, PortalTests::breakingASharedBarDissolvesBoth);
            registry.register(PORTAL_WATER, PortalTests::waterLeavesAPortalAlone);
            registry.register(PORTAL_CREATIVE, PortalTests::creativeBreakingIsRefusedUnlessSneaking);
            registry.register(PORTAL_ENTITY, PortalTests::onlyAPlayerStartsACharge);
            registry.register(PORTAL_PROPERTIES, PortalTests::theBlocksHaveTheSpecProperties);
            registry.register(PORTAL_NO_LOAD, PortalTests::theScanNeverLoadsAChunk);
            registry.register(PORTAL_DISSOLVE_WATER, PortalTests::aDissolveLetsWaterIn);
            registry.register(TEAM_DATA_CODEC, TeamDataTests::theCodecRoundTrips);
            registry.register(TEAM_DATA_STORAGE, TeamDataTests::aWriteLandsInTheServersStorage);
            registry.register(TEAM_DATA_READ, TeamDataTests::aReadWritesNothing);
            registry.register(TEAM_DATA_LOGIN, unchecked(TeamDataTests::aFirstLoginAndAPartyJoinWriteNothing));
            registry.register(TEAM_DATA_FAKE, TeamDataTests::aFakePlayerIsRefusedBeforeAnythingIsWritten);
            registry.register(TEAM_DATA_REAL, TeamDataTests::aRealPlayerIsWrittenThrough);
            registry.register(TEAM_DATA_DISBAND, unchecked(TeamDataTests::aDisbandSetsDisbandedAtAndSaves));
            registry.register(TEAM_DATA_DISBAND_EMPTY, unchecked(TeamDataTests::aDisbandWithNothingToArchiveWritesNothing));
            registry.register(TEAM_DATA_DISBAND_FOLDER, unchecked(TeamDataTests::aDisbandWithOnlyAVaultFolderIsRecorded));
            registry.register(TEAM_DATA_UNREADABLE, unchecked(TeamDataTests::anUnreadableFileIsCopiedAsideBeforeTheFirstWrite));
            registry.register(WORLDGEN_BASE, WorldgenTests::fillsTheBaseStack);
            registry.register(WORLDGEN_EXPANDED, WorldgenTests::fillsTheExpandedStack);
            registry.register(WORLDGEN_ONE_READ, WorldgenTests::readsTheSnapshotOnceAChunk);
            registry.register(WORLDGEN_VARIANT, WorldgenTests::refusesASnapshotOfTheOtherVariant);
            registry.register(WORLDGEN_NO_MOBS, WorldgenTests::anyBiomesSpawnsAreRefused);
            registry.register(WORLDGEN_SEED, WorldgenTests::theSeedComesFromTheTeamAndTheChunk);
            registry.register(WORLDGEN_HEIGHTS, WorldgenTests::answersTheHeightQuestions);
            registry.register(WORLDGEN_CODEC, unchecked(WorldgenTests::theCodecIsAUnitOfTheGenerator));
            registry.register(WORLDGEN_DIMENSION_TYPES, WorldgenTests::theDimensionTypesLoadWithTheHeightsInJava);
            registry.register(WORLDGEN_BIOME, WorldgenTests::theBiomeLoadsWithNoSpawnsAndInNoBroadTag);
            registry.register(WORLDGEN_LAYERS, WorldgenTests::theLayerFilesLoadFromTheDataPack);
        });
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        for (ResourceKey<Consumer<GameTestHelper>> test : List.of(
                PASSES, LANG_COVERAGE, FRAME_PICKAXE_DROP, FRAME_HAND_NO_DROP, FRAME_RECIPE, FRAME_PROPERTIES,
                FRAME_CONNECTS, FRAME_ROUNDED, SIGNET_RECIPE, SIGNET_STACK_SIZE, TEAM_UNKNOWN, TEAM_SOLO, TEAM_PARTY,
                DEBUG_GATE, DEBUG_BREAK, DEBUG_BREAK_EMPTY, DEBUG_BREAK_UNLOADED, DEBUG_DIAG, DEBUG_DIAG_LEVEL,
                PORTAL_SEALS, PORTAL_SEALS_Z, PORTAL_PISTON, PORTAL_FAKE_PLAYER, PORTAL_SIGNET_PASSES,
                PORTAL_OBSTRUCTED, PORTAL_RESEAL, PORTAL_LIVE_GUARD, PORTAL_CROSSING_SEALED,
                PORTAL_CROSSING_REFUSED, PORTAL_SWAP, PORTAL_TIERS, PORTAL_ORPHAN, PORTAL_SIDE_DOOR,
                PORTAL_SIDE_PORTAL, PORTAL_CORNER, PORTAL_HOLE_DOOR, PORTAL_HOLE_PORTAL,
                PORTAL_SHARED_SURVIVES, PORTAL_SHARED_BOTH, PORTAL_WATER, PORTAL_CREATIVE, PORTAL_ENTITY,
                PORTAL_PROPERTIES, PORTAL_NO_LOAD, PORTAL_DISSOLVE_WATER,
                TEAM_DATA_CODEC, TEAM_DATA_STORAGE, TEAM_DATA_READ, TEAM_DATA_LOGIN, TEAM_DATA_FAKE,
                TEAM_DATA_REAL, TEAM_DATA_DISBAND, TEAM_DATA_DISBAND_EMPTY, TEAM_DATA_DISBAND_FOLDER,
                TEAM_DATA_UNREADABLE, WORLDGEN_BASE, WORLDGEN_EXPANDED, WORLDGEN_ONE_READ, WORLDGEN_VARIANT,
                WORLDGEN_NO_MOBS, WORLDGEN_SEED, WORLDGEN_HEIGHTS, WORLDGEN_CODEC, WORLDGEN_DIMENSION_TYPES,
                WORLDGEN_BIOME, WORLDGEN_LAYERS)) {
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
