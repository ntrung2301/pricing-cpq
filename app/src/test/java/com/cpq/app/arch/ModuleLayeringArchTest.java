package com.cpq.app.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module-layering hygiene test per ADR-005 (revised by D-013: this is
 * NOT a domain-keyword scan — automotive/any other domain is pure
 * config/data per ADR-001, so there is no "pack.*" package to forbid).
 *
 * <p>ADR-005 rule 1: "only app may depend on every core-* module; each
 * other core-* module only depends on the core-* module(s) it actually
 * needs". ArchUnit inspects compiled bytecode, not the Gradle module
 * graph directly, so the rule is expressed here as two package-level
 * checks on the {@code app} test classpath (which can see every module's
 * compiled classes, since {@code app} is the only module that depends on
 * all of them):
 *
 * <ol>
 *   <li>{@link #coreModulesMustBeFreeOfCycles()} — core-* modules may now
 *       depend on each other (S1-T6 introduced the first real edge,
 *       {@code core-api -> core-security}, needed for
 *       {@code @PreAuthorize} per ADR-006), but never in a cycle: a
 *       one-directional dependency graph between core-* modules is fine,
 *       two modules depending on each other is not. This replaces the
 *       original, stricter {@code notDependOnEachOther()} check (which
 *       only held while no {@code core-*} module needed another), per
 *       this class's own original plan to relax it "as real cross-module
 *       dependencies are introduced in later sprints".</li>
 *   <li>{@link #coreModulesMustNotDependOnApp()} — a core-* module must
 *       never depend back on the composition root.</li>
 * </ol>
 *
 * <p>Together with {@link com.cpq.app.CompositionRoot} (which, by
 * implementing every core-* module's {@code ModuleMarker}, is proof that
 * {@code com.cpq.app} is in fact the one package that imports across all
 * of them), these two rules are the closest ArchUnit equivalent of "only
 * app may depend on every core-*".
 */
class ModuleLayeringArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.cpq");
    }

    @Test
    void coreModulesMustBeFreeOfCycles() {
        ArchRule rule = slices().matching("com.cpq.core.(*)..").should().beFreeOfCycles();
        rule.check(classes);
    }

    @Test
    void coreModulesMustNotDependOnApp() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("com.cpq.core..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("com.cpq.app..");
        rule.check(classes);
    }
}
