package com.example.activation;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.example.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDependOnServiceInterfacesOnly = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..service.impl..");

    @ArchTest
    static final ArchRule servicesDoNotKnowTheWebLayer = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..handler..");

    @ArchTest
    static final ArchRule implementationsAreOnlyReachedThroughInterfaces = noClasses()
            .that().resideOutsideOfPackage("..service.impl..")
            .should().dependOnClassesThat().resideInAPackage("..service.impl..");

    @ArchTest
    static final ArchRule modelDoesNotDependOnOtherLayers = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..repository..", "..dto..", "..mapper..", "..handler..");
}
