// Top-level build file. Plugins are declared here and applied only in the modules that use them.

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 brings an older Kotlin Gradle plugin. This pins the current stable Kotlin
        // (see docs/DECISIONS.md, D4).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}
