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
