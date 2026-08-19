package io.zengin4j.iso20022;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/// The module rules of §7, for the module that was always going to be the
/// exception.
///
/// R-M3 permits this module an XML dependency — it is the only one that may
/// have one — and it turns out not to need it: `java.xml` is in the JDK,
/// so the reader, the writer and the optional schema validation all come from
/// the platform (ADR-0031). These rules keep that true, because "the module
/// allowed a dependency" is exactly the module where one appears later without
/// anybody weighing it.
@AnalyzeClasses(packages = "io.zengin4j.iso20022",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /// Nothing but the JDK and the other zengin4j modules.
    ///
    /// R-M1 constrains `core`, not this module. Holding this module to
    /// the same standard is a choice rather than a requirement — but a published
    /// artefact with no dependencies is easier to adopt than one with a binding
    /// framework, and there is nothing here a binding framework would do better.
    @ArchTest
    static final ArchRule nothingButTheJdkAndTheOtherModules = noClasses()
            .should().dependOnClassesThat().resideOutsideOfPackages(
                    "io.zengin4j..", "java..", "javax.xml..", "")
            .because("this module reads and writes XML against java.xml and needs no "
                    + "binding framework (ADR-0031)");

    /// NG5, P7, and the one place it could plausibly go wrong.
    ///
    /// An XML parser resolving a schema location, or a validator fetching an
    /// import, would reach the network from inside a payment conversion. The
    /// parser disables external entities and DTDs; this makes sure nothing else
    /// grows a socket.
    @ArchTest
    static final ArchRule neverTouchesTheNetwork = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("java.net..", "javax.net..")
            .because("this library reads files and writes files; it never opens a socket "
                    + "(NG5, P7) — including to fetch a schema");

    /// R-M5: the dependency direction is one-way, and this module is above core.
    @ArchTest
    static final ArchRule doesNotDependOnTheModulesAboveIt = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.zengin4j.cli..", "io.zengin4j.testkit..")
            .because("the CLI depends on this module, not the other way round (§7)");

    /// R-O4: no logging framework here either; loss is reported, not logged.
    @ArchTest
    static final ArchRule usesNoLoggingFramework = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "java.util.logging..", "org.slf4j..")
            .because("what a conversion loses goes in the loss report, where a caller has to "
                    + "look at it, not in a log where they do not (R-O4)");

    /// R-T4, R-0.10: no static mutable state, so nothing changes under a converter.
    @ArchTest
    static final ArchRule noStaticMutableState = fields()
            .that().areStatic()
            .should().beFinal()
            .because("static mutable state has no place in something shared between threads "
                    + "(R-T4)");

    /// R-0.11: immutability by default means no setters anywhere.
    @ArchTest
    static final ArchRule noSetters = noMethods()
            .should().haveNameMatching("set[A-Z].*")
            .because("every public type is immutable (R-0.11)");

    /// The message model does not know how it is transported.
    ///
    /// `pain001` describes a message; `envelope` describes how the
    /// profile concatenates one. Keeping the arrow one-way is what would let a
    /// second message be added without touching the envelope, and a second
    /// envelope without touching the messages.
    @ArchTest
    static final ArchRule theMessageModelDoesNotDependOnTheEnvelope = noClasses()
            .that().resideInAPackage("io.zengin4j.iso20022.pain001")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.zengin4j.iso20022.api..", "io.zengin4j.iso20022.mapping..")
            .because("a message model must be usable without the mapper that fills it");

    /// Declared rows are data; they must not know what reads them.
    @ArchTest
    static final ArchRule mappingRowsDoNotDependOnTheMapper = noClasses()
            .that().resideInAPackage("io.zengin4j.iso20022.mapping")
            .should().dependOnClassesThat().resideInAPackage("io.zengin4j.iso20022.api..")
            .because("mapping rows are a declaration, and a declaration that imports its "
                    + "interpreter is no longer one");

    /// R-0.9, R-E2: no checked exceptions escape the public API.
    @ArchTest
    static final ArchRule noPublicMethodDeclaresACheckedException = noMethods()
            .that().arePublic()
            .should().declareThrowableOfType(java.io.IOException.class)
            .because("I/O failures surface as ZenginIOException, unchecked (R-E2)");
}
