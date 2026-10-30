// Expression tree, linear-syntax parser and printer, evaluator, units.
// Pure Kotlin/JVM: no UI dependencies, and no dependencies on other project modules.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// The version, as a resource that Version reads (one number for the app, the CLI and the MCP server).
val versionResource = layout.buildDirectory.dir("generated/version")
val writeVersion by tasks.registering {
    val v = project.version.toString()
    val dir = versionResource
    inputs.property("version", v)
    outputs.dir(dir)
    doLast { dir.get().file("pub/beermat/engine/version.txt").asFile.apply { parentFile.mkdirs() }.writeText(v) }
}
sourceSets.main { resources.srcDir(writeVersion) }
