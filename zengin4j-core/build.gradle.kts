description = "Zengin fixed-length codec. Zero runtime dependencies (R-M1)."

dependencies {
    // R-M1 / P3: this list must stay empty for api, implementation and
    // runtimeOnly. If you are about to add one, read §7 first.
    testImplementation(libs.archunit.junit5)
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
    dependsOn(tasks.jacocoTestCoverageVerification, tasks.jacocoTestReport)
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
        // Generated code is excluded for the same reason it is excluded from the
        // coverage counters: it measures the generator, not the codec.
        "--excludedClasses", "io.zengin4j.core.model.generated.*",
        "--excludedTestClasses", "io.zengin4j.core.ArchitectureTest",
        "--mutationThreshold", "80",
        "--timestampedReports", "false",
        "--testPlugin", "junit5",
        "--threads", Runtime.getRuntime().availableProcessors().toString(),
    )
}
