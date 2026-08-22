package com.luislipinski.trucklife.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.luislipinski.trucklife",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModularArchitectureTest {

    @ArchTest
    static final ArchRule TOP_LEVEL_MODULES_HAVE_NO_CYCLES = slices()
            .matching("com.luislipinski.trucklife.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule REST_CONTROLLERS_STAY_IN_API_PACKAGES = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..api..");
}
