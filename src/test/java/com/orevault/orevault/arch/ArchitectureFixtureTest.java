package com.orevault.orevault.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Each rule must fail on its planted violations under {@code src/test/java} (S43). The report has to name the
 * planted class: a rule that fails because its package name is mistyped matches nothing and proves nothing.
 */
class ArchitectureFixtureTest {
    private static final JavaClasses FIXTURES = new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .importPackages(ArchitectureRules.BASE);

    @TestFactory
    Stream<DynamicTest> everyRuleFailsOnItsPlantedViolation() {
        return ArchitectureRules.all().stream().map(rule -> dynamicTest(rule.name(), () -> {
            EvaluationResult result = rule.rule().evaluate(FIXTURES);
            assertTrue(result.hasViolation(), "the rule passed");
            String report = result.getFailureReport().toString();
            for (String planted : rule.planted()) {
                assertTrue(report.contains(planted), "no mention of " + planted + " in " + report);
            }
        }));
    }

    /** {@link ArchitectureTest} leaves a rule to its fixture by scope, so a mistyped scope would retire the rule. */
    @TestFactory
    Stream<DynamicTest> everyScopeMatchesAClass() {
        return ArchitectureRules.all().stream().filter(rule -> rule.scope() != null).map(rule -> dynamicTest(rule.name(),
                () -> assertFalse(FIXTURES.that(resideInAPackage(rule.scope())).isEmpty(), rule.scope())));
    }

    /** Holding a {@code ServerLevel} reaches {@code SavedDataStorage} only through Minecraft's own classes. */
    @Test
    void ruleOneAllowsAGeneratorThatHoldsALevel() {
        ArchitectureRules.Rule ruleOne = ArchitectureRules.all().stream()
                .filter(rule -> rule.name().startsWith("1: worldgen")).findFirst().orElseThrow();
        String report = ruleOne.rule().evaluate(FIXTURES).getFailureReport().toString();
        assertFalse(report.contains("AllowedWorldgenHoldsLevel"), report);
    }
}
