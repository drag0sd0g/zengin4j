description = "Build-time generator for record classes and format documentation. NOT PUBLISHED."

dependencies {
    implementation(project(":zengin4j-core"))

    // Build-time only, and therefore fine: R-M1 constrains what
    // zengin4j-core requires at runtime, not what the build uses to produce
    // it. Reading the descriptors here rather than in core is what lets core
    // ship with no parser at all — see docs/adr/0016.
    implementation(libs.snakeyaml)
}

// Not a consumable artifact: no sources jar, no javadoc jar, no JPMS descriptor.
tasks.named<Jar>("sourcesJar") { enabled = false }
tasks.named<Jar>("javadocJar") { enabled = false }
tasks.javadoc { enabled = false }

// The descriptors are build inputs, not runtime resources: they are compiled
// into core as Java, so they deliberately do not sit under src/main/resources
// and are not packaged into the jar (ADR-0016).
val formatsDir = rootProject.layout.projectDirectory.dir("zengin4j-core/formats")
val javaOutDir = rootProject.layout.projectDirectory.dir("zengin4j-core/src/main/java")
val docsOutDir = rootProject.layout.projectDirectory.dir("docs/formats")

// The transliteration substitutions, on the same footing as the descriptors:
// build input, compiled to Java, never packaged (R-K9, ADR-0016).
val kanaDir = rootProject.layout.projectDirectory.dir("zengin4j-core/kana")

fun codegenTask(
    taskName: String,
    mode: String,
    taskDescription: String,
    outputDir: Directory?,
) = tasks.register<JavaExec>(taskName) {
    group = "zengin4j codegen"
    description = taskDescription
    mainClass = "io.zengin4j.codegen.CodegenMain"
    classpath = sourceSets["main"].runtimeClasspath
    args(
        "--mode", mode,
        "--formats", formatsDir.asFile.absolutePath,
        "--java-out", javaOutDir.asFile.absolutePath,
        "--docs-out", docsOutDir.asFile.absolutePath,
        "--kana", kanaDir.asFile.absolutePath,
    )
    inputs.dir(formatsDir).withPropertyName("descriptors")
    inputs.dir(kanaDir).withPropertyName("kanaSubstitutions")
    if (outputDir != null) {
        outputs.dir(outputDir).withPropertyName("generated")
    } else {
        outputs.upToDateWhen { false }
    }
}

// Rewrites the committed generated sources and documentation. Run this after
// editing any descriptor, then commit the result (R-M8).
val generateFormatSources = codegenTask(
    "generateFormatSources",
    "generate",
    "Regenerates committed record classes and format documentation from the descriptors.",
    null,
)

// R-F1: Σ field lengths == recordLength for every record of every format,
// checked as a build failure rather than a runtime surprise.
val verifyFormatDescriptors = codegenTask(
    "verifyFormatDescriptors",
    "verify",
    "Fails the build if any format descriptor is internally inconsistent.",
    null,
)

// R-M8: the committed output must match what the descriptors currently
// produce, so a hand-edit of generated code cannot survive review.
val checkGeneratedSources = codegenTask(
    "checkGeneratedSources",
    "check",
    "Fails the build if the committed generated sources are stale or hand-edited.",
    null,
)

tasks.check {
    dependsOn(verifyFormatDescriptors, checkGeneratedSources)
}
