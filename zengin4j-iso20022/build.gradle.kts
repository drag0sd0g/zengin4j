description = "Bidirectional ISO 20022 mapping for the ZEDI profile, with mandatory loss reporting."

dependencies {
    api(project(":zengin4j-validation"))
    api(project(":zengin4j-core"))

    testImplementation(project(":zengin4j-testkit"))

    // Checks the hand-written JSON and the hand-written XML by parsing them with
    // a real parser. Test-scoped: this module publishes with no dependencies,
    // and a leak would show up in its POM.
    testImplementation(libs.jackson.databind)

    // R-M5: the module rules are enforced, not documented.
    testImplementation(libs.archunit.junit5)

    // R-T9: the envelope reader scans untrusted bytes for a cut point, which is
    // exactly the shape of parser coverage-guided fuzzing is good at.
    testImplementation(libs.jazzer.junit)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.iso20022")
    }
}

// The mapping reference is generated from the same YAML the mapper is driven by,
// so editing the YAML without regenerating must fail here rather than pass
// silently. Same reasoning as the descriptor drift check in zengin4j-codegen.
tasks.test {
    inputs.file(rootProject.file("docs/mapping.md"))
            .withPropertyName("mappingReference")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("zengin4j-iso20022/mappings"))
            .withPropertyName("mappingDeclarations")
            .withPathSensitivity(PathSensitivity.RELATIVE)
}

// R-T9: fuzzing, on its own tasks, wired exactly as core's is — one target per
// JVM when mutating, because libFuzzer terminates the process when a target's
// budget expires and a second target in the same JVM would never run.
//
//     ./gradlew :zengin4j-iso20022:fuzz            replay every corpus, in check
//     ./gradlew :zengin4j-iso20022:fuzzAll         mutate every target
//     ./gradlew :zengin4j-iso20022:fuzzSplitting   mutate one target
val fuzzTargets = mapOf(
    "Splitting" to "io.zengin4j.iso20022.envelope.EnvelopeFuzzTest.splittingNeverMisbehaves",
    "RoundTrip" to
        "io.zengin4j.iso20022.envelope.EnvelopeFuzzTest.anythingReadableIsWrittenBackUnchanged",
)

fun Test.fuzzTaskDefaults() {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "failed")
    }
}

val fuzzReplay = tasks.register<Test>("fuzz") {
    fuzzTaskDefaults()
    description = "Replays the committed fuzzing corpora (R-T9). Deterministic; part of check."
}

val mutatingFuzzTasks = fuzzTargets.map { (name, target) ->
    tasks.register<Test>("fuzz$name") {
        fuzzTaskDefaults()
        description = "Fuzzes $target for the duration it declares."
        environment("JAZZER_FUZZ", "1")
        filter { includeTestsMatching("$target*") }
    }
}

tasks.register("fuzzAll") {
    group = "verification"
    description = "Fuzzes every target, each in its own JVM (R-T9)."
    dependsOn(mutatingFuzzTasks)
}

// R-I21 asks for XSD validation in CI. The official ISO 20022 schemas are not
// redistributed here — see ADR-0031 — so this task validates only when it is
// pointed at a copy the user has obtained, and says clearly what it did.
//
// It is deliberately not wired into `check`: a gate that silently passes when
// its input is missing is worse than one that is absent, because it reads like
// coverage nobody has.
val validateAgainstXsd by tasks.registering(Test::class) {
    group = "verification"
    description = "Validates generated pain.001 output against official XSDs (-Pxsd.dir=...)."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("xsd") }
    systemProperty("zengin4j.xsd.dir",
            providers.gradleProperty("xsd.dir").getOrElse(""))
    outputs.upToDateWhen { false }
}

// Coverage floors, on the same ratchet as the other modules: raise when
// comfortably exceeded, never lower to make a build pass. Set slightly below
// core's because the XML reader carries a genuine tail of defensive branches —
// malformed input this module must survive but that no honest fixture produces.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// R-M3 permits this module an XML dependency. It does not have one, and the
// POM is where a consumer finds out — so the claim in the POM's own description
// is checked rather than asserted. Sibling modules are expected; anything else
// is a binding framework that arrived without anybody weighing it (ADR-0031).
val checkPomHasNoThirdPartyDependencies by tasks.registering {
    group = "verification"
    description = "Fails if the published POM declares anything but sibling modules (ADR-0031)."
    dependsOn("generatePomFileForMavenPublication")

    val pom = layout.buildDirectory.file("publications/maven/pom-default.xml")
    inputs.file(pom)
    outputs.upToDateWhen { false }

    doLast {
        val text = pom.get().asFile.readText()
        val foreign = Regex("<groupId>(.*?)</groupId>").findAll(text)
            .map { it.groupValues[1] }
            .drop(1)
            .filter { it != project.group.toString() }
            .toList()
        if (foreign.isNotEmpty()) {
            throw GradleException(
                "zengin4j-iso20022's published POM declares third-party dependencies " +
                    "(${foreign.joinToString(", ")}). The module reads and writes XML against " +
                    "java.xml and its POM description says so — see ADR-0031. If a dependency " +
                    "is genuinely needed, supersede that ADR and update the description first."
            )
        }
        logger.lifecycle("published POM declares no third-party dependencies (ADR-0031)")
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, tasks.jacocoTestReport, fuzzReplay,
        checkPomHasNoThirdPartyDependencies)
}
