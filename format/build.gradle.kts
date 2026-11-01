// The .bbm worksheet file: reading, writing, the result cache, and running a sheet.
// No UI dependencies. Kotlin Multiplatform (docs/web.md): JVM and WebAssembly; Excel reading on the JVM only.

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":engine"))
            implementation(project(":symbolic"))
            // Frontmatter and plot specs: the multiplatform port of SnakeYAML Engine (Apache-2.0).
            implementation(libs.snakeyaml.engine.kmp)
        }
        jvmMain.dependencies {
            // READEXCEL reads classic .xls workbooks with POI (Apache-2.0); .xlsx is read directly.
            implementation(libs.poi)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
