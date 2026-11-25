package com.orevault.orevault.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.ChunkPlan;
import com.orevault.orevault.core.gen.DensityTally;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.gen.VeinExtent;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.GenSnapshots;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.VaultTypeFile;
import com.orevault.orevault.debug.DensityCommand;
import com.orevault.orevault.ore.ClassifiedOres;
import com.orevault.orevault.vault.VaultDimensions;
import com.orevault.orevault.worldgen.VaultChunkGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The commands through the real dispatcher (S44, #205). The flag is set in memory only and put back before a test
 * returns, and nothing else in the batch reads it.
 */
final class DebugCommandTests {
    private static final BlockPos TARGET = new BlockPos(0, 1, 0);

    private DebugCommandTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static CommandSourceStack source(GameTestHelper helper, PermissionSet permissions) {
        return helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPermission(permissions);
    }

    /** Sends a source's output to a list, so a test reads what a console or a player would be shown. */
    private static CommandSource recordingTo(List<String> lines) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                lines.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }

    private static int run(CommandDispatcher<CommandSourceStack> dispatcher, String command, CommandSourceStack source,
            GameTestHelper helper) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException e) {
            helper.fail(Component.literal(command + " did not run: " + e.getMessage()));
            return 0;
        }
    }

    private static void withDebugCommands(Runnable test) {
        OreVaultServerConfig.ENABLE_DEBUG_COMMANDS.set(true);
        try {
            test.run();
        } finally {
            OreVaultServerConfig.ENABLE_DEBUG_COMMANDS.set(false);
        }
    }

    /** §10: the root stays for everyone with the flag off; a subcommand needs the flag and the operator level both. */
    static void theRootIsAlwaysThereAndTheSubcommandsAreGated(GameTestHelper helper) {
        check(helper, "the server config loaded", true, OreVaultServerConfig.SPEC.isLoaded());
        CommandSourceStack gamemaster = source(helper, LevelBasedPermissionSet.GAMEMASTER);
        CommandSourceStack player = source(helper, PermissionSet.NO_PERMISSIONS);
        CommandNode<CommandSourceStack> root = dispatcher(helper).getRoot().getChild(OreVault.MOD_ID);

        check(helper, "flag off: root for a player", true, root.canUse(player));
        for (String subcommand : List.of("diag", "break", "vault", "density", "team")) {
            CommandNode<CommandSourceStack> node = root.getChild(subcommand);
            check(helper, "flag off: " + subcommand + " for a gamemaster", false, node.canUse(gamemaster));
            withDebugCommands(() -> {
                check(helper, "flag on: " + subcommand + " for a gamemaster", true, node.canUse(gamemaster));
                check(helper, "flag on: " + subcommand + " for a player", false, node.canUse(player));
                check(helper, "flag on: root for a player", true, root.canUse(player));
            });
            check(helper, "flag back off: " + subcommand, false, node.canUse(gamemaster));
        }
        helper.succeed();
    }

    static void breakRemovesTheBlockAndSaysSo(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "orevault break " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source, helper)));

        helper.assertBlockPresent(Blocks.AIR, TARGET);
        check(helper, "printed", List.of("[orevault-debug] break minecraft:overworld " + pos.getX() + " " + pos.getY()
                + " " + pos.getZ() + " minecraft:stone: destroyBlock=true gone=true"), lines);
        helper.succeed();
    }

    /** A wrong position in a restart script fails rather than reading as a block that went. */
    static void breakOnAirFails(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.AIR);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 0, run(dispatcher(helper),
                "orevault break " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source, helper)));

        check(helper, "printed", 1, lines.size());
        check(helper, "says why", true, lines.getFirst().endsWith("nothing to break"));
        helper.succeed();
    }

    /**
     * QA's restart check as it will run (QA-38): from a console, through {@code execute in}, at a position in a chunk
     * nothing has loaded. Bedrock because it stands at this height whatever the seed generates.
     */
    static void breakLoadsTheChunkItNeeds(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos pos = new BlockPos(5000, 0, 5000);
        check(helper, "chunk loaded before the command", false, nether.hasChunkAt(pos));
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.OWNER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "execute in minecraft:the_nether run orevault break 5000 0 5000", source, helper)));

        check(helper, "printed", List.of("[orevault-debug] break minecraft:the_nether 5000 0 5000 minecraft:bedrock: "
                + "destroyBlock=true gone=true"), lines);
        helper.succeed();
    }

    /** A fake player, because a mock one cannot join with FTB Library loaded (see {@code TeamTests}). */
    static void diagPrintsTheBreakState(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "orevault-diag"));
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER)
                .withEntity(player)
                .withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "orevault diag " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), source, helper)));

        String printed = String.join("\n", lines);
        for (String field : List.of("serverTick=" + helper.getLevel().getServer().getTickCount(), "levelGameTime=",
                "block=minecraft:stone", "destroyProgress=", "mayInteract=", "spawnProtection=", "insideBorder=true",
                "borderSize=", "gameMode=survival")) {
            check(helper, "printed " + field, true, printed.contains(field));
        }
        helper.succeed();
    }

    /**
     * Under {@code execute in} the source's level is not the player's. The diagnosis is of the player's break, so the
     * position has to be checked in the level it is read from: this one is loaded there and not in the Nether.
     */
    static void diagReadsThePlayersLevelUnderExecuteIn(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos pos = helper.absolutePos(TARGET);
        List<String> lines = new ArrayList<>();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "orevault-diag"));
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER)
                .withEntity(player)
                .withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 1, run(dispatcher(helper),
                "execute in minecraft:the_nether run orevault diag " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                source, helper)));

        String printed = String.join("\n", lines);
        check(helper, "the player's level", true, printed.contains("dim=minecraft:overworld"));
        check(helper, "the block there", true, printed.contains("block=minecraft:stone"));
        helper.succeed();
    }

    /**
     * S44's create command, then QA's break through {@code execute in} in the Vault it made. A second create asking for the
     * other type keeps the folder's (ADR 0002 creation step 2).
     */
    static void vaultCreateMakesAVaultTheBreakWorksIn(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        String dim = VaultLifecycleTests.dimension(team);
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.OWNER).withSource(recordingTo(lines));

        withDebugCommands(() -> {
            check(helper, "create", 1, run(dispatcher(helper), "orevault vault create " + team + " expanded", source, helper));
            check(helper, "create again", 1, run(dispatcher(helper), "orevault vault create " + team + " base", source, helper));
            check(helper, "break", 1, run(dispatcher(helper), "execute in " + dim + " run orevault break 0 100 0", source, helper));
        });

        String created = "[orevault-debug] vault team=" + team + " dim=" + dim + " type=orevault:ore_vault_expanded";
        check(helper, "printed", List.of(created, created,
                "[orevault-debug] break " + dim + " 0 100 0 minecraft:grass_block: destroyBlock=true gone=true"), lines);
        helper.succeed();
    }

    /** The task's case: {@code /orevault break} in a Vault registered by restore, as the restart script runs it. */
    static void breakWorksInARestoredVault(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        UUID team = UUID.randomUUID();
        VaultTypeFile.write(DataPaths.vaultFolder(server, team), VaultVariant.BASE);
        check(helper, "restored", true, VaultDimensions.restoreOne(server, team).restored());
        // Since E4-S3 the Vault has ore, gravel and clay, and the team is random, so Y=1 may not be stone: make it stone.
        ServerLevel vault = Objects.requireNonNull(server.getLevel(VaultDimensions.dimensionKey(team)));
        vault.setBlock(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        String dim = VaultLifecycleTests.dimension(team);
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.OWNER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "break", 1,
                run(dispatcher(helper), "execute in " + dim + " run orevault break 0 1 0", source, helper)));

        check(helper, "printed", List.of("[orevault-debug] break " + dim + " 0 1 0 minecraft:stone: destroyBlock=true gone=true"),
                lines);
        helper.succeed();
    }

    /** E4-S4: a level whose generator is not a Vault's is refused, with the way to run it from the console. */
    static void densityRefusesALevelThatIsNotAVault(GameTestHelper helper) {
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.GAMEMASTER).withSource(recordingTo(lines));

        withDebugCommands(() -> check(helper, "result", 0, run(dispatcher(helper), "orevault density 0", source, helper)));

        check(helper, "printed", List.of("[orevault-debug] density: minecraft:overworld is not a Vault. From the console, run "
                + "it in one with: execute in orevault:vault_<team> run orevault density"), lines);
        helper.succeed();
    }

    /**
     * E4-S4, kept to one chunk so it is cheap: in a fresh expanded Vault, from the console through {@code execute in}, at a
     * chunk nothing has loaded, the command generates the chunk on its first run and not on its second, prints the same
     * counts both times (it changes nothing), and its tally holds exactly the ore of the pure plan for that chunk, by band,
     * rarity, slab and ore. A count that missed a section, read the wrong band, mapped a deepslate ore to another group or
     * put a layer in the wrong slab fails here; whether 81 chunks meet S12 is QA's dedicated-server run.
     */
    static void densityTalliesWhatThePlanPlaced(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID team = UUID.randomUUID();
        String dim = VaultLifecycleTests.dimension(team);
        int chunkX = 40;
        int chunkZ = -30;
        String density = "execute in " + dim + " positioned " + (chunkX * 16 + 8) + " 100 " + (chunkZ * 16 + 8)
                + " run orevault density 0";
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.OWNER).withSource(recordingTo(lines));

        withDebugCommands(() -> {
            check(helper, "create", 1, run(dispatcher(helper), "orevault vault create " + team + " expanded", source, helper));
            check(helper, "first count", 1, run(dispatcher(helper), density, source, helper));
            check(helper, "second count", 1, run(dispatcher(helper), density, source, helper));
            // Loading one chunk generates its neighbours, so a 3x3 run over untouched ground must still call all nine its own.
            check(helper, "square count", 1, run(dispatcher(helper), "execute in " + dim + " positioned " + (chunkX * 16 + 8)
                    + " 100 " + ((chunkZ + 20) * 16 + 8) + " run orevault density 1", source, helper));
        });
        check(helper, "a 3x3 run over new ground generated all nine", true, lines.contains(
                "[orevault-debug] density chunks: 9 counted, 9 generated by this run, 0 generated earlier this session, 0 not generated this session"));

        String chunks = "[orevault-debug] density chunks: 1 counted, ";
        check(helper, "first run generated the chunk", true,
                lines.contains(chunks + "1 generated by this run, 0 generated earlier this session, 0 not generated this session"));
        check(helper, "second run generated nothing", true,
                lines.contains(chunks + "0 generated by this run, 1 generated earlier this session, 0 not generated this session"));
        List<String> bandLines = lines.stream().filter(line -> line.contains(" ore in ")).toList();
        check(helper, "band lines, two bands three times", 6, bandLines.size());
        check(helper, "the second run counts the same", bandLines.subList(0, 2), bandLines.subList(2, 4));
        check(helper, "no warning", false, lines.stream().anyMatch(line -> line.contains("WARNING")));

        ServerLevel vault = Objects.requireNonNull(server.getLevel(VaultDimensions.dimensionKey(team)));
        VaultChunkGenerator generator = (VaultChunkGenerator) vault.getChunkSource().getGenerator();
        DensityTally counted = DensityCommand.count(vault, generator, chunkX, chunkZ, 0).tally();

        ClassifiedOres published = VaultChunkGenerator.publishedOres();
        GenSnapshot snapshot = GenSnapshots.current(team).orElseThrow();
        ChunkPlan plan = ChunkPlan.of(OreBaseline.BASE, VaultVariant.EXPANDED, published.pickers(VaultVariant.EXPANDED), snapshot,
                team, chunkX, chunkZ);
        DensityTally planned = new DensityTally(OreBaseline.BASE, VaultVariant.EXPANDED, published.pickers(VaultVariant.EXPANDED), 1);
        for (VeinExtent vein : plan.ore().veins()) {
            OreTable.Entry entry = Objects.requireNonNull(published.table().lookup(vein.oreGroup()), vein.oreGroup());
            for (int y = 0; y < vein.sizeY(); y++) {
                for (int z = 0; z < vein.sizeZ(); z++) {
                    for (int x = 0; x < vein.sizeX(); x++) {
                        if (vein.isSet(x, y, z)) {
                            planned.add(vein.band(), vein.minY() + y, entry.rarity(), entry.groupId(), 1);
                        }
                    }
                }
            }
        }
        check(helper, "the plan has ore in both bands", true,
                planned.blocks(Band.STONE) > 0 && planned.blocks(Band.DEEPSLATE) > 0);
        check(helper, "every planned block counted", plan.oreBlocks(), counted.blocks(Band.STONE) + counted.blocks(Band.DEEPSLATE));
        check(helper, "the tally against the plan", planned.counts(), counted.counts());
        helper.succeed();
    }

    /**
     * S44's dump and the interim add (#241), as the restart check runs them: a dump of a team with no file says so and
     * leaves it with none (S18); an add creates the record, and the dump then shows the sum in both units.
     */
    static void teamDumpAndResonanceAdd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID team = UUID.randomUUID();
        List<String> lines = new ArrayList<>();
        CommandSourceStack source = source(helper, LevelBasedPermissionSet.OWNER).withSource(recordingTo(lines));

        withDebugCommands(() -> {
            check(helper, "dump", 1, run(dispatcher(helper), "orevault team dump " + team, source, helper));
            check(helper, "no record after a dump", false, OreVaultTeamData.hasRecord(server, team));
            server.getDataStorage().saveAndJoin();
            check(helper, "no file after a dump", false, Files.exists(DataPaths.teamFile(server, team)));
            check(helper, "add", 1, run(dispatcher(helper), "orevault team resonance add " + team + " 12500", source, helper));
            check(helper, "add", 1, run(dispatcher(helper), "orevault team resonance add " + team + " 2800", source, helper));
            check(helper, "dump", 1, run(dispatcher(helper), "orevault team dump " + team, source, helper));
        });

        String tag = "[orevault-debug] ";
        check(helper, "printed", List.of(
                tag + "team " + team + " record=none resonance_pool=0 (0) level_reached=0 vault_generation=0 disbanded_at=none",
                tag + "resonance add team=" + team + " added=12500 (12.5) resonance_pool=12500 (12.5)",
                tag + "resonance add team=" + team + " added=2800 (2.8) resonance_pool=15300 (15.3)",
                tag + "team " + team + " record=ok resonance_pool=15300 (15.3) level_reached=0 vault_generation=0 disbanded_at=none"),
                lines);
        helper.succeed();
    }
}
