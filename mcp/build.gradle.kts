// The MCP server's protocol and tools (docs/mcp.md): served on stdin/stdout by `beermat mcp`,
// and over local HTTP by the app, for the sheet open in it. No UI dependencies.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":export"))
    implementation(project(":symbolic"))
    implementation(libs.snakeyaml.engine)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// The guide for AI sessions (docs/ai-guide.md) is served by the `guide` tool: one source.
tasks.processResources {
    from(rootProject.file("docs/ai-guide.md")) { into("pub/beermat/mcp") }
}
