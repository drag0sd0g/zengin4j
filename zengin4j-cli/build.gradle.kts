description = "Command line interface: validate / inspect / convert / generate. Epic 5."

dependencies {
    implementation(project(":zengin4j-iso20022"))
    implementation(project(":zengin4j-testkit"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.cli")
    }
}
