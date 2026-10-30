// The .bbm worksheet file: reading, writing, the result cache, and running a sheet.
// No UI dependencies.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":engine"))
    implementation(project(":symbolic"))
    implementation(libs.snakeyaml.engine)
    // READEXCEL reads classic .xls workbooks with POI (Apache-2.0); .xlsx is read directly.
    implementation(libs.poi)
    testImplementation(libs.poi)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
