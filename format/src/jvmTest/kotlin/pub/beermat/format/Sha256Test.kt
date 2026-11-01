package pub.beermat.format

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** [Sha256] gives what `MessageDigest` gave before it, so the results hash of every saved sheet stays the same. */
class Sha256Test {

    @Test
    fun `the same as MessageDigest`() {
        val random = Random(1)
        // Every length around the 64-byte blocks, where the padding changes, and longer ones.
        for (size in (0..200) + listOf(1000, 4095, 4096, 100_000)) {
            val bytes = random.nextBytes(size)
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(bytes), Sha256.digest(bytes), "$size bytes")
        }
    }
}
