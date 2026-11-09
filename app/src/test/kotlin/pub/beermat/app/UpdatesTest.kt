package pub.beermat.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {

    @Test
    fun `versions compare by semantic versioning`() {
        assertTrue(Updates.newer("1.2.0", "1.1.9"))
        assertTrue(Updates.newer("1.10.0", "1.9.0"))
        assertTrue(Updates.newer("v2.0.0", "1.9.9"))
        assertFalse(Updates.newer("1.2.0", "1.2.0"))
        assertFalse(Updates.newer("1.1.0", "1.2.0"))
    }

    @Test
    fun `a pre-release comes before its release`() {
        assertTrue(Updates.newer("1.0.0", "1.0.0-beta.2"))
        assertTrue(Updates.newer("1.0.0", "1.0.0-dev"))
        assertTrue(Updates.newer("1.0.0-beta.10", "1.0.0-beta.2"))
        assertFalse(Updates.newer("1.0.0-beta.1", "1.0.0"))
        // A build between releases is ahead of the one before it.
        assertFalse(Updates.newer("1.0.0", "1.1.0-dev"))
    }

    private fun release(tag: String, pre: Boolean) =
        """{"html_url":"https://github.com/beermat-pub/beermat/releases/tag/$tag","author":{"html_url":"x"},"tag_name":"$tag","prerelease":$pre,"assets":[{"name":"a.zip"}]}"""

    @Test
    fun `the newest release is read from GitHub's list`() {
        val json = "[" + listOf(release("v1.0.0-dev2", true), release("v1.0.0-dev3", true), release("v1.0.0-dev1", true)).joinToString(",") + "]"
        assertEquals(Updates.Release("1.0.0-dev3", "https://github.com/beermat-pub/beermat/releases/tag/v1.0.0-dev3"), Updates.newest(json, "1.0.0-dev2"))
        assertNull(Updates.newest("""{"message":"Not Found"}""", "1.0.0-dev3"))
        assertNull(Updates.newest("[]", "1.0.0"))
    }

    @Test
    fun `pre-releases are offered only to someone running one`() {
        val json = "[" + listOf(release("v1.1.0-beta.1", true), release("v1.0.0", false)).joinToString(",") + "]"
        assertEquals("1.0.0", Updates.newest(json, "1.0.0")?.version)
        assertEquals("1.1.0-beta.1", Updates.newest(json, "1.0.0-dev3")?.version)
    }

    @Test
    fun `the Store build reads the Store's version and links to its page`() {
        val text = "# The version live on the Microsoft Store.\n\n1.2.0\n"
        assertEquals(Updates.Release("1.2.0", "https://apps.microsoft.com/detail/9NABCDEFGH12"), Updates.storeRelease(text, "9NABCDEFGH12"))
        assertEquals("https://apps.microsoft.com/search?query=Beermat", Updates.storeRelease(text, "pending")?.url)
        assertNull(Updates.storeRelease("404: Not Found", null))
    }
}
