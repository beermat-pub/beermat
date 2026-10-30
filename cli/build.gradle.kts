// The `beermat` command-line tool (docs/cli.md).

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":export"))
    implementation(project(":xmcd"))
    implementation(project(":symbolic"))
    implementation(project(":mcp"))
    implementation(libs.snakeyaml.engine)
    testImplementation(kotlin("test"))
}

application {
    mainClass = "pub.beermat.cli.MainKt"
    applicationName = "beermat"
}

// The bundled Python with SymPy (docs/symbolic.md#bundled-sympy) goes in `python/` beside `lib/`,
// where `Python.home()` finds it; so the distribution is built for the system it runs on.
evaluationDependsOn(":symbolic")
distributions {
    main {
        contents {
            from(project(":symbolic").tasks.named("preparePython")) {
                // The distribution doesn't keep the interpreter's executable bit by itself.
                filesMatching("python/bin/*") { permissions { unix("rwxr-xr-x") } }
            }
        }
    }
}

// Only the zip is released; a tar would be another 90 MB at every build.
tasks.distTar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}
