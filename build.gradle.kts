plugins {
    base
    // Declared but not applied here: the SBOM is generated per published module
    // (see below), because a root-level SBOM would try to resolve the
    // benchmark module's configurations across project boundaries, which
    // Gradle 9 forbids — and because what a consumer wants described is the
    // artefact they depend on, not this repository's build tooling.
    alias(libs.plugins.cyclonedx) apply false
}

// Captured from the version catalogue here, in the root script scope, so the
// `subprojects` block below does not depend on catalogue accessors being
// visible from a subproject receiver.
val jacocoToolVersion = libs.versions.jacoco.get()
val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitPlatformLauncher = libs.junit.platform.launcher
val assertjCore = libs.assertj.core

// Common Java conventions.
//
// This is cross-project configuration rather than a set of buildSrc convention
// plugins. The trade-off: precompiled script plugins in buildSrc cannot see the
// version catalogue without a workaround, and for a seven-module build the
// clarity of one readable file wins. Revisit if the build grows.
allprojects {
    // R-B3: a group ID the publisher controls. io.github.<user> is verified on
    // Maven Central by GitHub account ownership alone, which is why it is the
    // default here — io.zengin4j would require proving control of zengin4j.io.
    //
    // The Java packages stay io.zengin4j.* regardless: a Maven coordinate and a
    // package name are separate things, and repackaging to match would break
    // every import for no benefit.
    group = providers.gradleProperty("zengin4j.group").getOrElse("io.github.drag0sd0g")
    version = providers.gradleProperty("zengin4j.version").getOrElse("0.1.0-SNAPSHOT")
}

// Modules that go to Maven Central. Deliberately not "all of them": four of the
// seven are skeletons whose epics have not landed, and publishing an empty jar
// claims a name while disappointing anyone who downloads it (R-B11 —
// completeness is not a release gate, but neither is squatting).
//
// zengin4j-codegen builds the descriptors and never ships; benchmarks measure
// and never ship.
val publishedModules = setOf("zengin4j-core", "zengin4j-testkit")

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        // R-M7: Java 21 baseline. Compiled with --release so that a JDK 21 or 25
        // build produces identical, 21-compatible bytecode.
        options.release = 21
        options.encoding = "UTF-8"
        // -serial is excluded because every exception in the hierarchy is
        // Serializable by inheritance and none is designed to be serialised.
        options.compilerArgs.add("-Xlint:all,-serial")
    }

    tasks.withType<Javadoc>().configureEach {
        // A module whose only source is module-info.java has nothing to
        // document. JDK 21's javadoc calls that an error; JDK 25's does not.
        // The §7 skeleton modules have exactly that shape until their epic
        // fills them in, so skip rather than fail — and keep the JDK 21 leg of
        // the CI matrix meaningful.
        onlyIf { source.files.any { file -> file.name != "module-info.java" } }

        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            charSet = "UTF-8"
            docEncoding = "UTF-8"
            memberLevel = JavadocMemberLevel.PROTECTED
            // doclint's syntax, reference, html and accessibility groups fail
            // the build, which is what catches a @param naming a component
            // that no longer exists.
            //
            // The `missing` group is excluded. It cannot be scoped: on JDK 25
            // javadoc rejects the -Xdoclint access qualifier, and the group
            // then fires on every private field of every exception class,
            // because Throwable is Serializable and those fields form its
            // serialized form. Seventeen permanent warnings would hide the one
            // that matters, so R-0.12 is enforced in review rather than here.
            addStringOption("Xdoclint:all,-missing", "-quiet")
        }
    }

    tasks.withType<Jar>().configureEach {
        // R-B5: reproducible archives.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Test>().configureEach {
        // Fuzzing runs from its own tasks, so `check` stays fast and
        // deterministic. The two filters are mutually exclusive: a task named
        // `fuzz…` runs nothing but the tag, and every other task skips it.
        //
        // Matched by prefix rather than by equality because each fuzz target
        // needs its own task — see the comment on fuzzTargets in
        // zengin4j-core/build.gradle.kts.
        val fuzzing = name.startsWith("fuzz")
        useJUnitPlatform {
            if (fuzzing) {
                includeTags("fuzz")
            } else {
                excludeTags("fuzz")
            }
        }
        // Fixed-length payment files are byte-oriented; a platform-dependent
        // default charset must never leak into a test result (R-T18).
        defaultCharacterEncoding = "UTF-8"
        // ./gradlew test -Pgolden.regenerate rewrites the committed golden
        // files. A -D on the command line would reach the Gradle JVM, not this
        // one, so it is forwarded explicitly.
        systemProperty("zengin4j.golden.regenerate",
                providers.gradleProperty("golden.regenerate").isPresent)
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = jacocoToolVersion
    }

    // ------------------------------------------------------------- publishing
    //
    // R-B4: signed artefacts with sources and javadoc jars. R-B5's reproducible
    // archives are configured above and apply to all three.
    //
    // Applied only to the modules that actually publish, so `./gradlew publish`
    // cannot accidentally push a skeleton.
    if (name in publishedModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")
        // R-B6: one SBOM per published artefact, attached to the release.
        apply(plugin = "org.cyclonedx.bom")
        tasks.named("cyclonedxBom") {
            // Runtime only. The default sweeps in every configuration, which
            // means an SBOM for zengin4j-core would list Jazzer, ArchUnit and
            // AssertJ — telling anyone who reads it that a library with no
            // runtime dependencies ships a fuzzer. An SBOM that overstates what
            // an artefact depends on is worse than none: it is the document
            // people scan for exactly this.
            withGroovyBuilder {
                setProperty("includeConfigs", listOf("runtimeClasspath"))
            }
        }

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name = project.name
                        description = when (project.name) {
                            "zengin4j-core" ->
                                "Reader, writer and format descriptors for Japanese Zengin " +
                                    "fixed-length bank file formats. Zero runtime dependencies."
                            else ->
                                "Synthetic fixtures and deterministic generators for testing " +
                                    "against Zengin fixed-length bank file formats."
                        }
                        url = "https://github.com/drag0sd0g/zengin4j"
                        inceptionYear = "2026"
                        licenses {
                            license {
                                name = "The Apache License, Version 2.0"
                                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                                distribution = "repo"
                            }
                        }
                        developers {
                            developer {
                                id = "drag0sd0g"
                                url = "https://github.com/drag0sd0g"
                            }
                        }
                        scm {
                            connection = "scm:git:https://github.com/drag0sd0g/zengin4j.git"
                            developerConnection = "scm:git:ssh://git@github.com/drag0sd0g/zengin4j.git"
                            url = "https://github.com/drag0sd0g/zengin4j"
                        }
                        issueManagement {
                            system = "GitHub Issues"
                            url = "https://github.com/drag0sd0g/zengin4j/issues"
                        }
                    }
                }
            }
            // The remote repository exists only when the release workflow says
            // so. Without -PcentralPublish there is nowhere remote to publish
            // to, so no sequence of Gradle tasks on a developer machine can
            // reach Maven Central — the release path is the workflow, and the
            // workflow is gated on a protected environment.
            //
            // Maven Central coordinates are permanent. A build that makes an
            // accidental release merely unlikely is not good enough; this makes
            // it unreachable.
            if (providers.gradleProperty("centralPublish").isPresent) {
                repositories {
                    maven {
                        name = "central"
                        val releases =
                            "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                        val snapshots = "https://central.sonatype.com/repository/maven-snapshots/"
                        url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshots else releases)
                        credentials {
                            username = providers.environmentVariable("CENTRAL_USERNAME").orNull
                            password = providers.environmentVariable("CENTRAL_PASSWORD").orNull
                        }
                    }
                }
            }
        }

        extensions.configure<SigningExtension> {
            // Signing material comes from the environment in CI and from
            // ~/.gradle/gradle.properties locally. Absent both, publishing to a
            // local repository still works — which is what makes it testable
            // without handing a laptop a release key.
            val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
            isRequired = signingKey != null && !version.toString().endsWith("SNAPSHOT")
            if (signingKey != null) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications["maven"])
            }
        }
    }

    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testImplementation"(assertjCore)
        "testRuntimeOnly"(junitPlatformLauncher)
    }
}
