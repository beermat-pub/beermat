package pub.beermat.app

import pub.beermat.engine.Version
import java.net.HttpURLConnection
import java.net.URI
import java.util.prefs.Preferences

/**
 * The update check (docs/release.md#update-check): at startup, one request to GitHub for the latest
 * release; a newer one shows quietly in the status bar. Nothing about the user or the sheet is sent,
 * and any failure (offline, private repository, rate limit) is silent.
 */
object Updates {
    /**
     * The releases, newest first. Not `releases/latest`, which skips pre-releases and so answered 404
     * while every release was a preview (issue #8).
     */
    const val LATEST = "https://api.github.com/repos/beermat-pub/beermat/releases?per_page=30"

    data class Release(val version: String, val url: String)

    /**
     * The version live on the Microsoft Store, a file on `main` changed when a submission is published:
     * the Store build checks it rather than GitHub's releases, which reach users days before the Store.
     */
    const val STORE_LATEST = "https://raw.githubusercontent.com/beermat-pub/beermat/main/packaging/windows/store-version.txt"

    /** The Microsoft Store build's Store ID, or `pending` before it has one (packaging/windows/msix.ps1). */
    private val storeId: String? = System.getProperty("beermat.store")

    /** Whether this is the Microsoft Store build: it checks [STORE_LATEST], and a new version links to its Store page. */
    val store: Boolean = storeId != null

    /** The app's page on the Microsoft Store, or a search for it without a Store ID. */
    fun storePage(id: String?): String =
        if (id != null && Regex("[0-9A-Z]{12}").matches(id)) "https://apps.microsoft.com/detail/$id" else "https://apps.microsoft.com/search?query=Beermat"

    /** The setting, remembered between runs (Help → Check for updates at startup). */
    var atStartup: Boolean
        get() = runCatching { prefs().getBoolean("checkForUpdates", true) }.getOrDefault(true)
        set(value) {
            runCatching { prefs().putBoolean("checkForUpdates", value) }
        }

    private fun prefs() = Preferences.userRoot().node("pub/beermat")

    /** The latest release; throws when GitHub can't be reached or doesn't answer with one. */
    fun latest(): Release {
        // BEERMAT_UPDATE_URL points the check elsewhere, to try it before a release exists.
        val connection = URI(System.getenv("BEERMAT_UPDATE_URL") ?: if (store) STORE_LATEST else LATEST).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Beermat/${Version.current}")
        try {
            check(connection.responseCode == 200) { "GitHub answered ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().readText()
            return (if (store) storeRelease(body, storeId) else newest(body, Version.current)) ?: error("no release in GitHub's answer")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The newest release in GitHub's JSON (a list of releases, or one), by version, linking to its page.
     * Pre-releases count only when [current] is one itself: someone trying a preview hears of the next
     * preview, and someone on a final version only of final versions.
     */
    fun newest(json: String, current: String): Release? {
        val includePre = '-' in current
        // Each release has one tag_name and one prerelease field, in that order (its assets have neither).
        val tags = Regex("\"tag_name\"\\s*:\\s*\"([^\"]*)\"").findAll(json).map { it.groupValues[1] }.toList()
        val pre = Regex("\"prerelease\"\\s*:\\s*(true|false)").findAll(json).map { it.groupValues[1] == "true" }.toList()
        return tags.withIndex()
            .filter { (i, tag) -> includePre || (!pre.getOrElse(i) { false } && '-' !in tag) }
            .map { it.value }
            .maxWithOrNull { a, b -> compare(a, b) }
            ?.let { Release(it.removePrefix("v"), "https://github.com/beermat-pub/beermat/releases/tag/$it") }
    }

    /** The version in [STORE_LATEST] (its first line that isn't a `#` comment), linking to the Store page. */
    fun storeRelease(text: String, id: String?): Release? =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.takeIf { Regex("\\d+\\.\\d+\\.\\d+").matches(it) }
            ?.let { Release(it, storePage(id)) }

    /** Whether [candidate] is a later version than [current], by semantic versioning (a pre-release comes before its release). */
    fun newer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val (coreA, preA) = split(a)
        val (coreB, preB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val c = coreA.getOrElse(i) { 0 }.compareTo(coreB.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        if (preA == null || preB == null) return (if (preA == null) 1 else 0) - (if (preB == null) 1 else 0)
        val idsA = preA.split('.')
        val idsB = preB.split('.')
        for (i in 0 until minOf(idsA.size, idsB.size)) {
            val x = idsA[i]
            val y = idsB[i]
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            val c = when {
                nx != null && ny != null -> nx.compareTo(ny)
                nx != null -> -1
                ny != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return idsA.size.compareTo(idsB.size)
    }

    private fun split(version: String): Pair<List<Int>, String?> {
        val v = version.removePrefix("v").substringBefore('+')
        val core = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        return core to (if ('-' in v) v.substringAfter('-') else null)
    }
}
