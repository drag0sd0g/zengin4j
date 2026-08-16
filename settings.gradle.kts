rootProject.name = "zengin4j"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

// Module graph per specification §7. Dependency direction is enforced by
// build-script wiring here and by ArchUnit rules in zengin4j-core (R-M5).
include(
    "zengin4j-core",
    "zengin4j-codegen",
    "zengin4j-validation",
    "zengin4j-iso20022",
    "zengin4j-testkit",
    "zengin4j-cli",
    "zengin4j-spring-boot-starter",
)

// Not a published module. Benchmarks live outside the module graph because
// nothing depends on them and a measurement harness is not part of the
// library (R-P4).
include("benchmarks")
