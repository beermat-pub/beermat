// .xmcd import (docs/xmcd.md). No UI dependencies.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":engine"))
    implementation(libs.poi)
    // Excel components become tables (docs/tables.md#import-from-mcdx-and-xmcd), written with format's Table.
    implementation(project(":format"))
    testImplementation(project(":format"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The local-only test reads the private samples folder at the repository root.
    workingDir = rootDir
    // Symbolic statements in imported sheets go to the bundled SymPy, as in the app.
    dependsOn(":symbolic:preparePython")
    systemProperty("beermat.python", rootDir.resolve("symbolic/build/app-resources/common/python").path)
}

// Local tools that use private worksheets, kept out of the repository, when present.
if (file("survey.gradle.kts").exists()) apply(from = "survey.gradle.kts")
