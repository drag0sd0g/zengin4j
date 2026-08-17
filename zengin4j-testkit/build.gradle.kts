description = "Synthetic fixtures and deterministic generators. Published, not test-scoped (R-M4)."

dependencies {
    // R-M4: downstream consumers put this on their own test class path, so
    // core is exposed as api.
    api(project(":zengin4j-core"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.testkit")
    }
}

// The testkit gained real logic in Epic 5 — four format fixtures and a
// registry — and it is a published artifact (R-M4). A published module with
// logic and no coverage floor is the inconsistency this closes. Set where it
// comfortably sits today; it ratchets up, never down.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, tasks.jacocoTestReport)
}
