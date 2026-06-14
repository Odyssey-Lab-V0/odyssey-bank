package com.banking.iam.api;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests — automatically enforce DDD layer boundaries at build time.
 *
 * These tests FAIL the build if any class violates the architecture rules.
 * This prevents accidental leakage of infrastructure concerns into the domain,
 * or controllers bypassing the application service layer.
 *
 * Run with: mvn test
 */
class DddArchitectureTest {

    private static final String BASE = "com.banking.iam";

    @Test
    void domain_must_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".infrastructure..");

        rule.check(new ClassFileImporter().importPackages(BASE));
    }

    @Test
    void domain_must_not_depend_on_spring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");

        rule.check(new ClassFileImporter().importPackages(BASE));
    }

    @Test
    void api_must_not_access_domain_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".api..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".domain..");

        rule.check(new ClassFileImporter().importPackages(BASE));
    }

    @Test
    void layered_architecture_is_respected() {
        layeredArchitecture()
                .consideringAllDependencies()
                .layer("API").definedBy(BASE + ".api..")
                .layer("Application").definedBy(BASE + ".application..")
                .layer("Domain").definedBy(BASE + ".domain..")
                .layer("Infrastructure").definedBy(BASE + ".infrastructure..")
                .whereLayer("API").mayOnlyAccessLayers("Application")
                .whereLayer("Application").mayOnlyAccessLayers("Domain", "Infrastructure")
                .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain")
                .whereLayer("Domain").mayNotAccessAnyLayer()
                .check(new ClassFileImporter().importPackages(BASE));
    }
}
