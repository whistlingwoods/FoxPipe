import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.kapt) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.jetbrains.kotlin.parcelize) apply false
    alias(libs.plugins.sonarqube) apply false
}

val pipePipeProjects = listOf(
    project(":extractor"),
    project(":timeago-parser"),
)

configure(pipePipeProjects) {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "com.github.TeamNewPipe"
    version = "v4.8.1"

    extensions.extraProperties["nanojsonVersion"] = "1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751"
    extensions.extraProperties["spotbugsVersion"] = "4.6.0"
    extensions.extraProperties["junitVersion"] = "5.8.2"
    extensions.extraProperties["checkstyleVersion"] = "9.3"

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
