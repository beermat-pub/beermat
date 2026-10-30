// Export: LaTeX, MathML and Markdown (docs/export.md). No UI dependencies.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":format"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
