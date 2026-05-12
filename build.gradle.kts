import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.dependencycheck)
}

allprojects {
    group = "dev.vitals"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)
    apply(plugin = rootProject.libs.plugins.errorprone.get().pluginId)

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    val libsCatalog = rootProject.libs

    dependencies {
        "compileOnly"(libsCatalog.jspecify)
        "testCompileOnly"(libsCatalog.jspecify)

        "testImplementation"(platform(libsCatalog.junit.bom))
        "testImplementation"(libsCatalog.junit.jupiter)
        "testRuntimeOnly"(libsCatalog.junit.launcher)
        "testImplementation"(libsCatalog.assertj.core)

        "errorprone"(libsCatalog.errorprone.core)
        "errorprone"(libsCatalog.nullaway.core)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
        options.errorprone {
            disableWarningsInGeneratedCode.set(true)
            option("NullAway:AnnotatedPackages", "dev.vitals")
            error("NullAway")
            // Severity.Error is intentional: the sealed-interface idiom mirrors the spec
            // and qualified usage removes any ambiguity with java.lang.Error.
            disable("JavaLangClash")
        }
    }

    // Error Prone is noisy on test sources; relax there.
    tasks.named<JavaCompile>("compileTestJava").configure {
        options.errorprone {
            disable("NullAway")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = false
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            palantirJavaFormat("2.50.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.20.1"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        configProperties = mapOf(
            "suppressionFile" to rootProject.file("config/checkstyle/suppressions.xml").absolutePath,
        )
        isIgnoreFailures = false
        maxWarnings = 0
    }

    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    tasks.named<JacocoReport>("jacocoTestReport").configure {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    // 80% line coverage gate on core + rules modules.
    val coveredModules = setOf(
        "vitals-core",
        "vitals-rules-jpa",
        "vitals-rules-spring",
        "vitals-rules-kafka",
        "vitals-rules-redis",
        "vitals-rules-jvm",
    )
    if (project.name in coveredModules) {
        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification").configure {
            dependsOn(tasks.named("test"))
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        minimum = "0.80".toBigDecimal()
                    }
                }
            }
        }
        tasks.named("check").configure {
            dependsOn(tasks.named("jacocoTestCoverageVerification"))
        }
    }

    tasks.named("check").configure {
        dependsOn(tasks.named("jacocoTestReport"))
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    suppressionFile = file("config/owasp-suppressions.xml").takeIf { it.exists() }?.absolutePath
    analyzers.assemblyEnabled = false
}
