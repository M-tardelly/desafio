package com.example.activation;

import com.example.activation.utils.LogBuilder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.slf4j.Logger;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regras de arquitetura verificadas no build: dependências entre camadas e uso do {@code LogBuilder}.
 *
 * @since 2026-10-29
 */
@AnalyzeClasses(packages = "com.example.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final Set<String> LOG_METHODS = Set.of("trace", "debug", "info", "warn", "error");

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

    @ArchTest
    static final ArchRule logsGoThroughLogBuilder = noClasses()
            .that().doNotBelongToAnyOf(LogBuilder.class)
            .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>("call a log method of org.slf4j.Logger") {
                @Override
                public boolean test(JavaMethodCall call) {
                    return call.getTargetOwner().isAssignableTo(Logger.class) && LOG_METHODS.contains(call.getName());
                }
            })
            .because("os logs devem seguir o padrão do LogBuilder");
}
