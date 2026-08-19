package io.zengin4j.core;

import module java.base;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/// The module rules of §7, enforced rather than documented (R-M5).
@AnalyzeClasses(packages = "io.zengin4j.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /// R-M1, P3: zero runtime dependencies. This is the property that makes the
    /// artifact adoptable where dependencies are reviewed, and the only way to
    /// keep it is to check it. The empty package admits primitives and arrays.
    @ArchTest
    static final ArchRule coreDependsOnNothingButTheJdk = noClasses()
            .should().dependOnClassesThat().resideOutsideOfPackages("io.zengin4j..", "java..", "")
            .because("zengin4j-core must have zero runtime dependencies (R-M1)");

    /// R-M5: the dependency direction is one-way, and core is at the bottom of it.
    @ArchTest
    static final ArchRule coreDependsOnNoOtherZengin4jModule = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.zengin4j.validation..", "io.zengin4j.iso20022..",
                    "io.zengin4j.cli..", "io.zengin4j.testkit..")
            .because("core sits below every other module (§7)");

    /// NG1, NG5, P7: this library is a codec. It never opens a socket.
    @ArchTest
    static final ArchRule coreNeverTouchesTheNetwork = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("java.net..", "javax.net..")
            .because("network transport is out of scope and this library never opens a socket (NG5, P7)");

    /// R-O4: no logging framework in core; warnings go to a listener the caller supplies.
    @ArchTest
    static final ArchRule coreUsesNoLoggingFramework = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("java.util.logging..", "org.slf4j..")
            .because("core reports through ZenginWarning, not a logging framework (R-O4)");

    /// R-T4, R-0.10: no static mutable state, so nothing can change under a reader.
    @ArchTest
    static final ArchRule noStaticMutableState = fields()
            .that().areStatic()
            .should().beFinal()
            .because("static mutable state has no place in a codec shared between threads (R-T4)");

    /// R-0.11: immutability by default means no setters anywhere.
    @ArchTest
    static final ArchRule noSetters = noMethods()
            .should().haveNameMatching("set[A-Z].*")
            .because("every public type is immutable (R-0.11)");

    /// The model describes records; it does not read them.
    @ArchTest
    static final ArchRule theModelDoesNotDependOnTheReaders = noClasses()
            .that().resideInAPackage("io.zengin4j.core.model")
            .should().dependOnClassesThat().resideInAPackage("io.zengin4j.core.codec")
            .because("the record model must be usable without the codec that produced it");

    /// Descriptors are data; they must not know what is built from them.
    @ArchTest
    static final ArchRule descriptorsDoNotDependOnTheModel = noClasses()
            .that().resideInAPackage("io.zengin4j.core.format..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.zengin4j.core.model..", "io.zengin4j.core.codec..")
            .because("format descriptors are data and must not know what is built from them");

    /// R-0.9, R-E2: no checked exceptions escape the public API.
    @ArchTest
    static final ArchRule noPublicMethodDeclaresACheckedException = noMethods()
            .that().arePublic()
            .should().declareThrowableOfType(IOException.class)
            .because("the public API has no checked exceptions (R-0.9, R-E2)");
}
