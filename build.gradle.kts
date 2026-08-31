import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            if (providers.gradleProperty("kixyu.compose.reports").orNull == "true") {
                reportsDestination.set(layout.buildDirectory.dir("reports/compose-compiler"))
                metricsDestination.set(layout.buildDirectory.dir("reports/compose-compiler"))
            }
        }
        tasks.configureEach {
            if (name.startsWith("compile") && name.endsWith("Kotlin")) {
                // Compose compiler report destinations are not part of Kotlin's incremental task
                // fingerprint. Force the explicitly requested report run so an up-to-date compile
                // cannot silently leave modules without metrics.
                outputs.upToDateWhen {
                    providers.gradleProperty("kixyu.compose.reports").orNull != "true"
                }
            }
        }
    }
}

tasks.register("composePerformanceReport") {
    group = "verification"
    description = "Generates Compose compiler stability and recomposition metrics for release sources."
    dependsOn(
        ":app:compileReleaseKotlin",
        ":core:core-designsystem:compileReleaseKotlin",
        ":core:core-reader-engine:compileReleaseKotlin",
        ":core:core-ui:compileReleaseKotlin",
        ":feature:feature-home:compileReleaseKotlin",
        ":feature:feature-library:compileReleaseKotlin",
        ":feature:feature-reader:compileReleaseKotlin",
        ":feature:feature-settings:compileReleaseKotlin",
    )
    doFirst {
        check(providers.gradleProperty("kixyu.compose.reports").orNull == "true") {
            "Run with -Pkixyu.compose.reports=true so compiler metrics are enabled."
        }
    }
}

tasks.register("verifyAccessibilityResources") {
    group = "verification"
    description = "Rejects hard-coded Compose content descriptions in production Kotlin sources."
    val productionSources = fileTree(rootDir) {
        include("**/src/main/**/*.kt")
        exclude("**/build/**")
    }
    inputs.files(productionSources)
    doLast {
        val directContentDescription = Regex("contentDescription\\s*=\\s*\"")
        val positionalIconDescription = Regex("Icon\\([^\\n]*,\\s*\"")
        val violations = productionSources.files.sorted().flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (
                    directContentDescription.containsMatchIn(line) ||
                    positionalIconDescription.containsMatchIn(line)
                ) {
                    "${source.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        check(violations.isEmpty()) {
            "Hard-coded accessibility text must use stringResource:\n${violations.joinToString("\n")}"
        }
    }
}
