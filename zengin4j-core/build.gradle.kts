description = "Zengin fixed-length codec. Zero runtime dependencies (R-M1)."

dependencies {
    // R-M1 / P3: this list must stay empty for api, implementation and
    // runtimeOnly. If you are about to add one, read §7 first.
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jazzer.junit)
}

// R-T9: fuzzing, on its own tasks.
//
// Kept out of `check` deliberately. Fuzzing is a nightly job — it is slow, and
// it is non-deterministic by design, which is the opposite of what a
// per-commit gate should be. INV-3 is covered on every build by the seeded
// property in InvariantProperties; this is the deeper pass.
//
//     ./gradlew :zengin4j-core:fuzz          replay every corpus — fast, deterministic
//     ./gradlew :zengin4j-core:fuzzAll       mutate every target, each in its own JVM
//     ./gradlew :zengin4j-core:fuzzReading   mutate one target
//
// **One target per JVM when mutating.** libFuzzer terminates the process when a
// target's time budget expires, so a second target in the same JVM never runs
// and Gradle — still expecting it — fails with a missing results file rather
// than with anything that names the cause. Hence a task per target rather than
// a flag on one task: the broken combination is not expressible.
//
// Jazzer's JUnit integration switches on the JAZZER_FUZZ environment variable,
// not a system property. Per-run duration comes from @FuzzTest(maxDuration).
//
// Adding a @FuzzTest means adding it here. FuzzTargetsAreWiredTest fails the
// build if that is forgotten, so a target cannot go silently un-fuzzed.
val fuzzTargets = mapOf(
    "Reading" to "io.zengin4j.core.ReaderFuzzTest.readingNeverMisbehaves",
    "RoundTrip" to "io.zengin4j.core.ReaderFuzzTest.anythingReadableIsWritable",
)

fun Test.fuzzTaskDefaults() {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    // Tag filtering comes from the root build, which routes the `fuzz` tag
    // here and excludes it everywhere else.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "failed")
    }
}

// Replay is in `check`: it is deterministic, takes about two seconds, and a
// crash reproducer that only runs nightly lets the regression it was committed
// to prevent reach main first. Mutating runs stay nightly.
val fuzzReplay = tasks.register<Test>("fuzz") {
    fuzzTaskDefaults()
    description = "Replays the committed fuzzing corpora (R-T9). Deterministic; part of check."
}

val mutatingFuzzTasks = fuzzTargets.map { (name, target) ->
    tasks.register<Test>("fuzz$name") {
        fuzzTaskDefaults()
        description = "Fuzzes $target for the duration it declares."
        environment("JAZZER_FUZZ", "1")
        // Trailing wildcard: a @FuzzTest reports as `name(byte[])`, so an exact
        // method match finds nothing and Gradle fails with "no tests found".
        filter { includeTestsMatching("$target*") }
    }
}

tasks.register("fuzzAll") {
    group = "verification"
    description = "Fuzzes every target, each in its own JVM (R-T9)."
    dependsOn(mutatingFuzzTasks)
}

tasks.named<Test>("test") {
    // So the guard test can compare the wiring above against the annotations.
    systemProperty("zengin4j.fuzz.targets", fuzzTargets.values.sorted().joinToString(","))
}

tasks.jar {
    manifest {
        // R-M6: JPMS descriptor plus Automatic-Module-Name as a fallback for
        // consumers still on the class path.
        attributes("Automatic-Module-Name" to "io.zengin4j.core")
    }
}

// R-T16: enforced coverage floor on core.
//
// The generated record classes are excluded from the counters. They are
// machine-emitted from the descriptors and are covered by dedicated
// offset/round-trip tests; including ~60 trivial accessors in the denominator
// would measure the generator, not the hand-written code the gate exists to
// protect. JaCoCo also filters them automatically via @Generated.
val coverageExclusions = listOf("io/zengin4j/core/model/generated/**")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }),
    )
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }),
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, tasks.jacocoTestReport, fuzzReplay)
}

// R-T15: mutation testing, opt-in.
//
// Deliberately not wired into `check`: PIT re-runs the suite once per surviving
// mutant, which is minutes rather than the second `check` currently takes.
// Run it before a release and when changing anything in the codec.
//
//     ./gradlew :zengin4j-core:pitest
//
// Driven through PIT's command line rather than info.solidsoft.pitest, whose
// newest release (1.15.0) reads reporting.baseDir and so cannot apply on
// Gradle 9. A JavaExec task needs no plugin and pins the versions in the
// catalogue like everything else.
val pitest: Configuration = configurations.create("pitest")

dependencies {
    pitest(libs.pitest.command.line)
    pitest(libs.pitest.junit5.plugin)
}

tasks.register<JavaExec>("pitest") {
    group = "verification"
    description = "Runs mutation testing over core (R-T15). Slow; not part of check."
    dependsOn(tasks.testClasses)
    mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
    classpath = pitest + sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
    args(
        "--reportDir", layout.buildDirectory.dir("reports/pitest").get().asFile.absolutePath,
        "--targetClasses", "io.zengin4j.core.*",
        "--targetTests", "io.zengin4j.core.*",
        "--sourceDirs", layout.projectDirectory.dir("src/main/java").asFile.absolutePath,
        "--outputFormats", "HTML,XML",
        // ArchitectureTest and FuzzTargetsAreWiredTest assert on the class path
        // and the build's own wiring rather than on behaviour: they pass under
        // every mutant and kill none, and the latter reads a system property
        // only the `test` task sets, so PIT's JVM fails it outright.
        // ReaderFuzzTest is excluded for the reason it is tagged `fuzz` — its
        // duration is a wall clock, not a test count.
        "--excludedTestClasses", "io.zengin4j.core.ArchitectureTest"
                + ",io.zengin4j.core.FuzzTargetsAreWiredTest"
                + ",io.zengin4j.core.ReaderFuzzTest",
        "--mutationThreshold", "80",
        "--timestampedReports", "false",
        "--testPlugin", "junit5",
        "--threads", Runtime.getRuntime().availableProcessors().toString(),
    )

    // --targetClasses matches everything on the class path, and the test classes
    // are on it. Mutating a test is meaningless — nothing asserts on a test's own
    // logic, so every such mutant survives — and it dilutes the score in
    // proportion to how much test code exists, which is exactly backwards.
    //
    // The exclusion is derived from the compiled test output rather than from a
    // naming convention, so a generator or fixture that is not named *Test is
    // excluded too, and a new one needs no change here.
    //
    // Generated model code is excluded for the reason it is excluded from the
    // coverage counters: it measures the generator, not the codec.
    val testClassesDirs = sourceSets["test"].output.classesDirs
    argumentProviders.add(CommandLineArgumentProvider {
        val excluded = mutableListOf("io.zengin4j.core.model.generated.*")
        testClassesDirs.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .mapTo(excluded) {
                    it.relativeTo(root).invariantSeparatorsPath
                        .removeSuffix(".class")
                        .replace('/', '.')
                }
        }
        listOf("--excludedClasses", excluded.sorted().joinToString(","))
    })
}
