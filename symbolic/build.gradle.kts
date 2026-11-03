import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject

// Symbolic evaluation (milestone 10, docs/symbolic.md): a small engine of our own behind the
// engine's SymbolicBackend interface, and the bundled Python with SymPy for the rest.
// Kotlin Multiplatform (docs/web.md): our own engine everywhere; Python and SymPy on the JVM only.

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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

/**
 * Python with SymPy for this machine (docs/symbolic.md#bundled-sympy): python-build-standalone,
 * SymPy installed with its own pip, trimmed, and compiled with `unchecked-hash` `.pyc` files, so they
 * stay valid when an installer changes the files' dates (and Python never writes into the app's folder).
 * `tar` unpacks it, keeping the symlinks and executable bits that a Gradle copy would lose.
 */
abstract class PreparePython : DefaultTask() {
    @get:Input abstract val release: Property<String>
    @get:Input abstract val python: Property<String>
    @get:Input abstract val packages: ListProperty<String>
    @get:Input abstract val platform: Property<String>
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:Internal abstract val downloads: DirectoryProperty

    @get:Inject abstract val exec: ExecOperations
    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun prepare() {
        val name = "cpython-${python.get()}+${release.get()}-${platform.get()}-install_only_stripped.tar.gz"
        val archive = downloads.get().file(name).asFile
        if (!archive.exists()) {
            archive.parentFile.mkdirs()
            val url = "https://github.com/astral-sh/python-build-standalone/releases/download/${release.get()}/${name.replace("+", "%2B")}"
            logger.lifecycle("Downloading $url")
            val partial = File(archive.path + ".part")
            URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            partial.renameTo(archive)
        }
        val out = output.get().asFile
        files.delete { delete(out) }
        out.mkdirs()
        exec.exec { commandLine("tar", "-xzf", archive.absolutePath, "-C", out.absolutePath) }

        val home = File(out, "python")
        val windows = platform.get().contains("windows")
        if (!windows) singleFiles(home)
        val exe = if (windows) File(home, "python.exe") else File(home, "bin/python3")
        val lib = if (windows) File(home, "Lib") else File(home, "lib/python${python.get().substringBeforeLast('.')}")
        exec.exec { commandLine(exe.path, "-I", "-m", "pip", "install", "--quiet", "--no-compile", "--disable-pip-version-check", "--no-warn-script-location", *packages.get().toTypedArray()) }

        // What we never use: Tcl/Tk, IDLE, the test suites, headers. pip stays, for the user's own
        // packages (docs/symbolic.md#the-users-own-packages), run as `python -m pip`.
        val unused = listOf("tkinter", "idlelib", "turtledemo", "test", "ensurepip", "pydoc_data", "site-packages/bin")
            .map { File(lib, it) } +
            if (windows) listOf("tcl", "include", "libs", "Scripts", "share", "DLLs/_tkinter.pyd", "DLLs/tcl86t.dll", "DLLs/tk86t.dll").map { File(home, it) }
            else listOf("include", "share", "lib/pkgconfig", "lib/tcl9", "lib/tk9", "lib/tcl8", "lib/tk8", "lib/tcl8.6", "lib/tk8.6", "lib/itcl4.3.2", "lib/thread3.0.1").map { File(home, it) } +
                File(lib, "lib-dynload").listFiles { f -> f.name.startsWith("_tkinter") }.orEmpty() +
                File(home, "lib").listFiles { f -> f.name.matches(Regex("lib(tcl|tk).*")) }.orEmpty() +
                File(home, "bin").listFiles { f -> f.name.matches(Regex("(idle|pydoc|pip|isympy).*|.*-config")) }.orEmpty()
        val tests = File(lib, "site-packages").walkTopDown().filter { it.isDirectory && it.name == "tests" }.toList()
        files.delete { delete(unused + tests) }

        exec.exec { commandLine(exe.path, "-I", "-m", "compileall", "-q", "-j", "0", "--invalidation-mode", "unchecked-hash", lib.path) }
    }

    /**
     * The app bundles this folder with a Gradle copy, which turns each symlink into a copy of its
     * file: `bin/python` and `bin/python3` would be two more 31 MB interpreters. So the interpreter
     * becomes the one real `bin/python3`, the other links go, and so does `libpython`, which is only
     * for embedding (the interpreter is linked statically).
     */
    private fun singleFiles(home: File) {
        val bin = File(home, "bin")
        val real = File(bin, "python${python.get().substringBeforeLast('.')}")
        val python3 = File(bin, "python3")
        Files.delete(python3.toPath())
        check(real.renameTo(python3)) { "could not rename $real" }
        val links = home.walkTopDown().filter { Files.isSymbolicLink(it.toPath()) }.toList()
        files.delete { delete(links + File(home, "lib").listFiles { f -> f.name.startsWith("libpython") }.orEmpty()) }
    }
}

/** The python-build-standalone name of this machine. CI builds each system on its own runner. */
fun hostPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = when (System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> error("no bundled Python for ${System.getProperty("os.arch")}")
    }
    return when {
        os.startsWith("windows") -> "$arch-pc-windows-msvc"
        os.startsWith("mac") -> "$arch-apple-darwin"
        else -> "$arch-unknown-linux-gnu"
    }
}

val preparePython = tasks.register<PreparePython>("preparePython") {
    group = "beermat"
    description = "Prepares build/app-resources/common/python: Python with SymPy for this machine"
    release = libs.versions.python.standalone
    python = libs.versions.python.asProvider()
    packages = listOf("sympy==${libs.versions.sympy.get()}", "mpmath==${libs.versions.mpmath.get()}")
    platform = hostPlatform()
    // Laid out as Compose wants app resources (`common/` for every system): the app bundles it from there.
    output = layout.buildDirectory.dir("app-resources/common")
    downloads = rootProject.layout.projectDirectory.dir(".gradle/python-downloads")
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    dependsOn(preparePython)
    systemProperty("beermat.python", layout.buildDirectory.dir("app-resources/common/python").get().asFile.path)
    // The user's own packages (Packages) in a folder of the tests', never the real one.
    systemProperty("beermat.packages", layout.buildDirectory.dir("test-packages").get().asFile.path)
}
