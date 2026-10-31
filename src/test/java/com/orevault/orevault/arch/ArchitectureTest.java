package com.orevault.orevault.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Runs ADR 0001's rules over the main classes. A rule whose package has no classes yet is left to its fixture. */
class ArchitectureTest {
    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ArchitectureRules.BASE);

    @Test
    void importsTheMainClasses() {
        assertFalse(MAIN.isEmpty(), "ArchUnit imported no classes; it may not read this class file version");
    }

    @TestFactory
    Stream<DynamicTest> rulesHoldOverTheMainClasses() {
        return ArchitectureRules.all().stream()
                .filter(rule -> rule.scope() == null || !MAIN.that(resideInAPackage(rule.scope())).isEmpty())
                .map(rule -> dynamicTest(rule.name(), () -> rule.rule().check(MAIN)));
    }
}
