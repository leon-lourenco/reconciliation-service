package com.cardbilling.reconciliation.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

/**
 * The hexagonal structure, checked rather than claimed. A build with a broken rule here fails the
 * same way a broken test does - which is the point: "we follow hexagonal architecture" is a
 * statement a reader can verify from a green build instead of taking on trust.
 *
 * <p>The rules are the ones in card-billing-modernization's ARCHITECTURE.md, applied to this
 * service.
 */
@AnalyzeClasses(packages = HexagonalArchitectureTest.SERVICE_PACKAGE,
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    static final String SERVICE_PACKAGE = "com.cardbilling.reconciliation";

    private static final String DOMAIN = SERVICE_PACKAGE + ".domain..";
    private static final String APPLICATION = SERVICE_PACKAGE + ".application..";
    private static final String INFRASTRUCTURE = SERVICE_PACKAGE + ".infrastructure..";

    @ArchTest
    static final ArchRule layersAreRespected = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("domain").definedBy(DOMAIN)
            .layer("application").definedBy(APPLICATION)
            .layer("infrastructure").definedBy(INFRASTRUCTURE)
            .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("infrastructure")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "infrastructure");

    @ArchTest
    static final ArchRule domainDependsOnNothingElseInThisService = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE)
            .because("the domain is the middle of the hexagon - it depends on nothing outside itself");

    /**
     * The rule that makes plain-Java domain types worth the mapping code: no Spring, no JPA, no
     * framework at all, so the matching rule can be exercised without a container or a database.
     */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "com.fasterxml..", "com.opencsv..",
                    "io.github.resilience4j..", "io.swagger..")
            .because("domain objects are plain Java - persistence and web mapping live in infrastructure");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "com.opencsv..", "io.swagger..")
            .because("use cases are wired by infrastructure, not annotated by it - see UseCaseConfiguration");

    @ArchTest
    static final ArchRule applicationDoesNotReachIntoInfrastructure = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .because("application talks to infrastructure only through its own ports");

    @ArchTest
    static final ArchRule controllersDoNotTouchPersistenceOrTheBillingClient = noClasses()
            .that().resideInAPackage(SERVICE_PACKAGE + ".infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    SERVICE_PACKAGE + ".infrastructure.persistence..",
                    SERVICE_PACKAGE + ".infrastructure.client..")
            .because("a controller calls a use case, never a repository or another service's client");

    @ArchTest
    static final ArchRule onlyJpaEntitiesAreNamedEntity = classes()
            .that().haveSimpleNameEndingWith("Entity")
            .and().resideOutsideOfPackage(DOMAIN)
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .because("a class named *Entity that is not mapped means the model and its mapping have drifted");

    @ArchTest
    static final ArchRule jpaEntitiesAreNamedEntityAndLiveInPersistence = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().haveSimpleNameEndingWith("Entity")
            .andShould().resideInAPackage(SERVICE_PACKAGE + ".infrastructure.persistence..")
            .because("persistence mapping belongs in one place, and is named so it is obvious");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage(SERVICE_PACKAGE + ".application.port..")
            .and().haveSimpleNameEndingWith("Port")
            .should().beInterfaces()
            .because("a port is what infrastructure implements, not a class application already decided on");

    @ArchTest
    static final ArchRule repositoriesAreOnlyReachedThroughPorts = noClasses()
            .that().resideOutsideOfPackages(
                    SERVICE_PACKAGE + ".infrastructure.persistence..",
                    SERVICE_PACKAGE + ".infrastructure.config..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")
            .because("Spring Data types stop at the persistence adapter");
}
