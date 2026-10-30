package com.example.activation;

import com.example.activation.domain.ActivationRequestRepository;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.example.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnNothingElseInTheApp = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infrastructure..", "..config..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..", "..config..");

    @ArchTest
    static final ArchRule apiGoesThroughApplicationLayer = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .orShould().dependOnClassesThat().areAssignableTo(ActivationRequestRepository.class);

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnApi = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..api..");
}
