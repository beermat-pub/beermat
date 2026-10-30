import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The desktop app: Compose Multiplatform. The worksheet editor (docs/editor.md),
// with the math laid out in 2D (docs/layout.md, docs/editor.md).

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":export"))
    implementation(project(":mcp"))
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
    testImplementation(libs.pdfbox)
}

compose.desktop {
    application {
        mainClass = "pub.beermat.app.MainKt"
        nativeDistributions {
            // docs/release.md. Native installers want a plain X.Y.Z: `1.0.0-beta.2` packages as 1.0.0.
            packageName = "Beermat"
            packageVersion = project.version.toString().substringBefore('-')
            description = "Engineering worksheets: math that looks like math, with real units"
            vendor = "beermat.pub"
            copyright = "© Beermat contributors. MIT licence."
            licenseFile.set(rootProject.file("packaging/licence.txt"))
            // From `./gradlew :app:suggestRuntimeModules` (the MCP server needs jdk.httpserver), plus what
            // the update check uses: java.prefs for its setting, jdk.crypto.ec for GitHub's TLS.
            modules("java.instrument", "java.prefs", "jdk.crypto.ec", "jdk.httpserver", "jdk.unsupported")
            // Windows: an Inno Setup installer built on the app image (packaging/windows), not the MSI.
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            fileAssociation("text/markdown", "bbm", "Beermat worksheet", rootProject.file("packaging/icons/beermat.png"))
            // Python with SymPy, as `python/` in the app's resources (docs/symbolic.md#bundled-sympy).
            appResourcesRootDir.set(project(":symbolic").layout.buildDirectory.dir("app-resources"))
            windows {
                iconFile.set(rootProject.file("packaging/icons/beermat.ico"))
                menuGroup = "Beermat"
                upgradeUuid = "0b6a7d45-5a2e-4c1f-9d0e-6f3b8c2a1e57"
            }
            macOS {
                iconFile.set(rootProject.file("packaging/icons/beermat.icns"))
                bundleID = "pub.beermat.app"
                appCategory = "public.app-category.productivity"
            }
            linux {
                iconFile.set(rootProject.file("packaging/icons/beermat.png"))
                packageName = "beermat"
                menuGroup = "Office;Science;Education"
                appCategory = "Science"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // `-Pbeermat.pdfOut=dir` keeps the PDFs (and a PNG of each page) that PdfTest writes, to look at.
    providers.gradleProperty("beermat.pdfOut").orNull?.let { systemProperty("beermat.pdfOut", it) }
    // The bundled Python, for the tests of Python functions in the editor.
    dependsOn(":symbolic:preparePython")
    systemProperty("beermat.python", rootDir.resolve("symbolic/build/app-resources/common/python").path)
}

// Compose registers it late, after this script runs.
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(":symbolic:preparePython")
}

// Compose copies the app resources without their executable bits (with Kotlin's `copyTo`), so the
// bundled interpreter gets its bit back in the app image, and every package is made from that image:
// Compose does so on macOS, and the Linux packages are pointed at it here.
tasks.matching { it.name == "packageDeb" || it.name == "packageRpm" }.configureEach {
    val create = tasks.named<AbstractJPackageTask>("createDistributable")
    dependsOn(create)
    (this as AbstractJPackageTask).appImage.set(create.flatMap { it.destinationDir.dir("Beermat") })
}
tasks.matching { it.name.matches(Regex("create(Release)?Distributable")) }.configureEach {
    val image = (this as AbstractJPackageTask).destinationDir
    doLast {
        image.get().asFile.walkTopDown()
            .filter { it.isFile && it.parentFile.name == "bin" && it.parentFile.parentFile.name == "python" }
            .forEach { it.setExecutable(true, false) }
    }
}

// The icons and installer pictures, from the logo (docs/release.md#branding).
tasks.register<JavaExec>("icons") {
    group = "beermat"
    description = "Makes packaging/icons from branding/logo.svg"
    mainClass = "pub.beermat.app.IconsKt"
    classpath = sourceSets["main"].runtimeClasspath
    args(rootProject.file("packaging/icons").path)
}

// The logo comes from branding/ (one source; see branding/README.md).
tasks.processResources {
    from(rootProject.file("branding")) {
        include("logo.svg", "logo-small.svg")
        into("brand")
    }
}
