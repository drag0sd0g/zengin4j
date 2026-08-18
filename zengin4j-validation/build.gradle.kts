description = "Structured validation with byte-level findings. Depends only on core (R-M2). Epic 4."

dependencies {
    api(project(":zengin4j-core"))

    // Testkit is a published artifact rather than a test fixture (R-M4), so
    // using it here is the same thing a consumer does.
    testImplementation(project(":zengin4j-testkit"))

    // Checks the hand-written JSON and SARIF writers by parsing their output
    // with a real parser. Test-scoped: R-M2 keeps it out of the artifact, and
    // checkPomHasNoDependencies would catch it if it leaked.
    testImplementation(libs.jackson.databind)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.validation")
    }
}

// RuleReferenceTest reads two files outside this project, so Gradle cannot infer
// that editing them should re-run the tests. Without this the drift check is
// skipped as UP-TO-DATE in exactly the case it exists for: documentation changed
// and code did not.
//
// ModuleDescriptorTest also reads across modules, and needs nothing here: core
// and testkit are dependencies of this task, so editing either one's sources
// rebuilds its jar and invalidates these tests already.
tasks.test {
    inputs.file(rootProject.file("docs/validation-rules.md"))
            .withPropertyName("ruleReference")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("README.md"))
            .withPropertyName("readme")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    // ModuleDescriptorTest checks every module's descriptor, including one this
    // module does not depend on, so Gradle cannot infer that editing it matters.
    inputs.dir(rootProject.file("zengin4j-iso20022/src/main/java"))
            .withPropertyName("iso20022Sources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
}

// R-T16 sets its coverage floor on `core`. Validation gets one too, because a
// rule nobody exercises is a rule nobody knows is wrong — but a lower branch
// floor, honestly stated rather than quietly matched.
//
// The difference is structural, not laxity. Half the branches here are
// "no calendar supplied", "no reference data supplied", "this format has no
// such field" — the optionality R-V5 and R-V6 require. Each needs a contrived
// descriptor to reach, and a test that builds one proves the guard compiles
// rather than that any rule works. The floor is set where the meaningful
// branches are covered, and it ratchets: raise it when it is comfortably
// exceeded, never lower it to make a build pass.
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

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, tasks.jacocoTestReport)
}
