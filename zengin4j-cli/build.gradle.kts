description = "Command line interface: validate / inspect / convert / dryrun / generate / diff / explain."

dependencies {
    implementation(project(":zengin4j-core"))
    implementation(project(":zengin4j-validation"))
    implementation(project(":zengin4j-iso20022"))
    // Not test-scoped: `zengin generate` is a shipped command, so the fixture
    // generator is part of this application's runtime (R-M4).
    implementation(project(":zengin4j-testkit"))

    // The argument parser. A runtime dependency here and nowhere else — this
    // module is an application, not a library, and is not published, so nothing
    // downstream inherits it. See ADR-0024.
    implementation(libs.picocli)

    // Generates META-INF/native-image/**/{reflect,resource}-config.json from the
    // @Command annotations at compile time. Hand-written GraalVM config goes
    // stale the first time an option is renamed; generated config cannot.
    annotationProcessor(libs.picocli.codegen)

    testImplementation(libs.jackson.databind)
}

val mainClass = "io.zengin4j.cli.Zengin"

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.cli")
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Tells picocli-codegen which project the generated config belongs to, so
    // the files land under a path that cannot collide with another jar's.
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
    // picocli-codegen deliberately does not *claim* the annotations it reads,
    // so that other processors still see them — which makes -Xlint:processing
    // warn on every compile about a processor that is working correctly. One
    // permanent warning teaches people to ignore the output, so it goes.
    options.compilerArgs.add("-Xlint:-processing")
}

// ------------------------------------------------------------- the shaded jar
//
// Hand-rolled rather than via a shadow plugin. There is exactly one dependency
// to bundle, relocation would be wrong (nothing downstream consumes this), and
// the plugin would add a build dependency to save about ten lines. If this ever
// needs relocation or service-file merging, take the plugin — that is the point
// at which it earns its place.
val shadedJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds the self-contained zengin CLI jar."

    archiveClassifier = "all"
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    // A module descriptor in an uber-jar describes a module that does not exist:
    // the contents of several modules are merged into one artifact. The regular
    // jar keeps its own module-info for anyone using this on the module path.
    exclude("module-info.class", "META-INF/versions/*/module-info.class")
    // Signatures do not survive repackaging, and a stale one makes the jar
    // refuse to load rather than merely fail a check.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to mainClass,
            "Implementation-Title" to "zengin4j",
            "Implementation-Version" to project.version.toString(),
        )
    }
}

tasks.assemble {
    dependsOn(shadedJar)
}

// ---------------------------------------------------------- the native image
//
// Optional, per §5.6, and deliberately not wired into `check`: requiring a
// GraalVM on every contributor's machine to run the tests would be a poor
// trade for a build whose test suite runs in seconds. The configuration the
// image needs is generated on every compile regardless, so this task works
// whenever a suitable JDK is present and says plainly what is missing when not.
val nativeImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds a native binary from the shaded jar (requires GraalVM)."

    dependsOn(shadedJar)
    val output = layout.buildDirectory.file("native/zengin")
    val jar = shadedJar.flatMap { it.archiveFile }
    // `File`, not `java.io.File`: in a Gradle Kotlin script `java` resolves to
    // the JavaPluginExtension accessor, so the qualified name does not compile.
    val nativeImageBinary = providers.systemProperty("java.home")
        .map { File(it, "bin/native-image") }

    onlyIf {
        val present = nativeImageBinary.get().canExecute()
        if (!present) {
            logger.lifecycle(
                "Skipping nativeImage: no native-image at ${nativeImageBinary.get()}. " +
                    "Run with a GraalVM JDK, or use the shaded jar from :zengin4j-cli:shadedJar."
            )
        }
        present
    }

    outputs.file(output)
    commandLine(
        nativeImageBinary.map { it.absolutePath }.get(),
        "-jar", jar.get().asFile.absolutePath,
        "--no-fallback",
        // The message bundles and the holiday CSV are read as resources, and a
        // native image includes no resource it has not been told about.
        "-H:+ReportExceptionStackTraces",
        output.get().asFile.absolutePath,
    )
}

// The CLI's own coverage floor. Lower than core's, and for a reason worth
// stating: a third of this module is usage text, exit-code plumbing and the
// "your terminal cannot encode this" fallbacks, which are reached by a person
// running the tool rather than by a test. The rules that decide anything —
// exit codes, masking, output shape — are covered.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, tasks.jacocoTestReport)
}

// CliReferenceTest reads a file outside this project, so Gradle cannot infer
// that editing it should re-run the tests — without this the drift check is
// skipped as UP-TO-DATE in exactly the case it exists for.
tasks.test {
    inputs.file(rootProject.file("docs/cli.md"))
            .withPropertyName("cliReference")
            .withPathSensitivity(PathSensitivity.RELATIVE)
}
