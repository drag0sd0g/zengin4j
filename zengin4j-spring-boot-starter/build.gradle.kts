description = "Spring Boot auto-configuration, metrics and health indicators. Epic 8."

dependencies {
    api(project(":zengin4j-iso20022"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.zengin4j.spring.boot.starter")
    }
}
