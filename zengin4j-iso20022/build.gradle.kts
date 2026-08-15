description = "ISO 20022 mapping. The only module permitted an XML dependency (R-M3). Epic 7."

dependencies {
    api(project(":zengin4j-validation"))
    api(project(":zengin4j-core"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.iso20022")
    }
}
