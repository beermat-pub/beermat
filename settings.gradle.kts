rootProject.name = "beermat"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Modules are added as their milestone arrives.
include("engine", "symbolic", "format", "export", "xmcd", "mcp", "cli", "ui", "app", "web")
