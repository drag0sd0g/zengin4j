plugins {
    application
}

// R-P1, R-P4: a committed JMH harness, so a published throughput number can be
// reproduced rather than believed.
//
//     ./gradlew :benchmarks:jmh                    every benchmark
//     ./gradlew :benchmarks:jmh --args='Streaming' one of them, by regex
//
// This module is deliberately outside the published module graph: nothing
// depends on it, it ships nowhere, and it is the one place in the build allowed
// to care about wall-clock time.

dependencies {
    implementation(project(":zengin4j-core"))
    implementation(project(":zengin4j-testkit"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}

application {
    mainClass = "org.openjdk.jmh.Main"
}

// The root build's javadoc and coverage conventions apply to published modules.
// A benchmark has no API to document and no coverage floor to meet.
tasks.withType<Javadoc>().configureEach {
    enabled = false
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs the JMH benchmarks (R-P1). Slow by design; never part of check."
    mainClass = "org.openjdk.jmh.Main"
    classpath = sourceSets["main"].runtimeClasspath
    // Recorded in benchmarks/README.md alongside any published number (P9).
    jvmArgs("-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch")
    args = (findProperty("jmhArgs") as String?)?.split(" ") ?: listOf()
}

// R-P2: constant memory on the streaming path, whatever the file size.
//
// A 1 GB file streamed under a 64 MB heap. The heap is the assertion: if
// anything on the read path retained per-record state, this would not survive
// the first few million records, and the failure would be an OutOfMemoryError
// rather than a number to interpret.
tasks.register<JavaExec>("constantMemory") {
    group = "verification"
    description = "Streams a 1 GB file under a constrained heap (R-P2)."
    mainClass = "io.zengin4j.benchmarks.ConstantMemoryCheck"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx64m", "-XX:+HeapDumpOnOutOfMemoryError")
    args = listOf(providers.gradleProperty("bytes").getOrElse((1024L * 1024 * 1024).toString()))
}
