// The worksheet's user interface, shared by the desktop app and the web version (docs/web.md#step-2-in-detail):
// the editing model, math layout, the page, plots, the commands. Kotlin Multiplatform and Compose (JVM and WebAssembly).

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // The tests draw with Skia and run on the JVM; in the browser they would need its runtime bundled.
            testTask { enabled = false }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":export"))
            // Compose, and Skia with it, which draws the math and the page on both platforms.
            implementation(compose.ui)
        }
        // The tests draw with Skia, which on the JVM needs this system's native part.
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    // The bundled Python, for the tests of Python functions in the editor.
    dependsOn(":symbolic:preparePython")
    systemProperty("beermat.python", rootDir.resolve("symbolic/build/app-resources/common/python").path)
}

// It warns that browser tests would lack Skia's runtime; there are none (above), so it has nothing to check.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }
