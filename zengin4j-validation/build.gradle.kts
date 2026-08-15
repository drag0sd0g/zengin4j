description = "Structured validation with byte-level findings. Depends only on core (R-M2). Epic 4."

dependencies {
    api(project(":zengin4j-core"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.validation")
    }
}
