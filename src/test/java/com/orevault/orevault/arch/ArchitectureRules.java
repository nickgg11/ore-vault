package com.orevault.orevault.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * ADR 0001's rules: one per row of the package tables, then the named rules. Named rule 7 (Reviewer gate 2)
 * lands with E3-S6a (#237), where its classes exist. The rules never use {@code allowEmptyShould}: a rule
 * whose package has no classes yet runs only against its fixture (S43).
 */
final class ArchitectureRules {
    static final String BASE = "com.orevault.orevault";

    private static final String[] MC = {"net.minecraft..", "net.neoforged..", "com.mojang..", "org.slf4j.."};
    private static final String CORE = BASE + ".core..";
    private static final String DATA = BASE + ".data";
    private static final String ORE = BASE + ".ore";
    private static final String TEAM = BASE + ".team";
    private static final String VAULT = BASE + ".vault";
    private static final String WORLDGEN = BASE + ".worldgen";
    private static final String INDEX = BASE + ".worldgen.index";
    private static final String REGISTRY = BASE + ".registry";
    private static final String MINING = BASE + ".mining";
    private static final String NETWORK = BASE + ".network";

    /**
     * One rule and the planted classes its fixture check must see named.
     *
     * @param scope the package pattern the rule needs classes in, or null if it holds over every class
     */
    record Rule(String name, @Nullable String scope, ArchRule rule, List<String> planted) {
    }

    private ArchitectureRules() {
    }

    static List<Rule> all() {
        List<Rule> rules = new ArrayList<>();
        rules.addAll(pureRows());
        rules.addAll(adapterRows());
        rules.addAll(namedRules());
        return rules;
    }

    private static List<Rule> pureRows() {
        return List.of(
                pureRow("ore", "CoreOre"),
                pureRow("layout", "CoreLayout", "com.google.gson.."),
                pureRow("tree", "CoreTree", core("layout")),
                pureRow("curve", "CoreCurve", core("gen"), core("ore")),
                pureRow("gen", "CoreGen", core("ore"), core("tree")),
                pureRow("mining", "CoreMining", core("ore"), core("tree"), core("vein")),
                pureRow("vein", "CoreVein", core("gen")),
                pureRow("session", "CoreSession"),
                pureRow("reset", "CoreReset"),
                pureRow("wear", "CoreWear", core("tree")),
                pureRow("key", "CoreKey"));
    }

    private static List<Rule> adapterRows() {
        String[] sharedRow = {CORE, DATA, ORE, TEAM, VAULT, WORLDGEN, INDEX, REGISTRY, NETWORK};
        return List.of(
                row(BASE, "Root", withMc(CORE, DATA, ORE, TEAM, VAULT, WORLDGEN, INDEX, REGISTRY, MINING, NETWORK,
                        BASE + ".mixin", BASE + ".chunkload", BASE + ".reset", BASE + ".config", BASE + ".debug",
                        BASE + ".integration..")),
                row(REGISTRY, "Registry", withMc(CORE, VAULT, MINING)),
                row(TEAM, "Team", withMc("dev.ftb.mods.ftbteams..", CORE, DATA, VAULT)),
                row(DATA, "Data", withMc(CORE)),
                row(ORE, "Ore", withMc(CORE)),
                row(VAULT, "Vault", withMc(CORE, DATA, WORLDGEN, INDEX, TEAM, REGISTRY)),
                row(WORLDGEN, "Worldgen", withMc(CORE, ORE, INDEX)),
                row(INDEX, "WorldgenIndex", withMc(core("vein"))),
                row(MINING, "Mining", withMc(CORE, DATA, ORE, TEAM, REGISTRY, INDEX)),
                row(NETWORK, "Network", withMc(CORE, DATA, TEAM, VAULT, BASE + ".reset", INDEX)),
                row(BASE + ".mixin", "Mixin", withMc(core("wear"))),
                row(BASE + ".chunkload", "Chunkload", withMc(sharedRow)),
                row(BASE + ".reset", "Reset", withMc(sharedRow)),
                row(BASE + ".config", "Config", withMc(sharedRow)),
                row(BASE + ".debug", "Debug", withMc(sharedRow)),
                row(BASE + ".integration.ultimine", "IntegrationUltimine",
                        withMc("dev.ftb.mods.ftbultimine..", "dev.ftb.mods.ftblibrary..", CORE, DATA, MINING)),
                row(BASE + ".integration.processing", "IntegrationProcessing", withMc(CORE)));
    }

    private static List<Rule> namedRules() {
        DescribedPredicate<JavaClass> savedDataAccess = resideInAnyPackage(
                BASE + ".data..", BASE + ".team..", "net.minecraft.world.level.saveddata..", "dev.ftb..")
                .or(name("net.minecraft.world.level.storage.SavedDataStorage")
                        .forSubtype())
                .as("team data or SavedData");
        return List.of(
                new Rule("1: worldgen reaches no team data or SavedData", BASE + ".worldgen..",
                        noClasses().that().resideInAPackage(BASE + ".worldgen..")
                                .should().transitivelyDependOnClassesThat(savedDataAccess),
                        List.of("PlantedWorldgenImportsData", "PlantedWorldgenTransitive", "PlantedIndexImportsStorage")),
                new Rule("1: core.gen reaches no team data or SavedData", BASE + ".core.gen..",
                        noClasses().that().resideInAPackage(BASE + ".core.gen..")
                                .should().transitivelyDependOnClassesThat(savedDataAccess),
                        List.of("PlantedGenImportsSavedData")),
                new Rule("2: only team imports FTB Teams", null,
                        noClasses().that().resideOutsideOfPackage(TEAM)
                                .should().dependOnClassesThat().resideInAPackage("dev.ftb.mods.ftbteams.."),
                        List.of("PlantedVaultImportsTeams")),
                new Rule("3: nothing imports Mekanism", null,
                        noClasses().should().dependOnClassesThat(
                                nameMatching("(?i).*mekanism.*").forSubtype().as("named after Mekanism")),
                        List.of("PlantedVaultImportsMekanism")),
                new Rule("4: only integration.ultimine imports FTB Ultimine", null,
                        noClasses().that().resideOutsideOfPackage(BASE + ".integration.ultimine")
                                .should().dependOnClassesThat().resideInAPackage("dev.ftb.mods.ftbultimine.."),
                        List.of("PlantedVaultImportsUltimine")),
                new Rule("5: no common code reaches client code", null,
                        noClasses().that().resideOutsideOfPackage(BASE + ".client..")
                                .should().dependOnClassesThat().resideInAnyPackage(BASE + ".client..",
                                        "net.minecraft.client..", "com.mojang.blaze3d..",
                                        "net.neoforged.neoforge.client.."),
                        List.of("PlantedMiningImportsClient")),
                new Rule("6: core imports no Minecraft", CORE,
                        noClasses().that().resideInAPackage(CORE)
                                .should().dependOnClassesThat().resideInAnyPackage(
                                        "net.minecraft..", "net.neoforged..", "com.mojang.."),
                        List.of("PlantedCoreImportsMinecraft")),
                new Rule("6: the core packages have no cycles", CORE,
                        slices().matching(BASE + ".core.(*)..").should().beFreeOfCycles(),
                        List.of("PlantedCycleA", "PlantedCycleB")),
                new Rule("8: mixin reaches only core.wear", BASE + ".mixin",
                        noClasses().that().resideInAPackage(BASE + ".mixin")
                                .should().dependOnClassesThat(resideInAnyPackage(BASE + "..")
                                        .and(DescribedPredicate.not(resideInAnyPackage(core("wear"), BASE + ".mixin")))
                                        .as("an Ore Vault class outside core.wear")),
                        List.of("PlantedMixinReachesData")));
    }

    private static String core(String name) {
        return BASE + ".core." + name;
    }

    private static String[] withMc(String... allowed) {
        return Stream.concat(Stream.of(MC), Stream.of(allowed)).toArray(String[]::new);
    }

    private static Rule pureRow(String name, String planted, String... allowed) {
        return row(core(name), planted, allowed);
    }

    private static Rule row(String pkg, String planted, String[] allowed) {
        String[] packages = Stream.concat(Stream.of("java..", "org.jspecify..", pkg), Stream.of(allowed))
                .toArray(String[]::new);
        return new Rule("row " + pkg, pkg,
                classes().that().resideInAPackage(pkg)
                        .should().onlyDependOnClassesThat().resideInAnyPackage(packages),
                List.of("Planted" + planted + "Row"));
    }
}
