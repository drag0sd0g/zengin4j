plugins {
    base
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
    group = "io.zengin4j"
    version = "0.1.0-SNAPSHOT"
}

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

    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testImplementation"(assertjCore)
        "testRuntimeOnly"(junitPlatformLauncher)
    }
}
