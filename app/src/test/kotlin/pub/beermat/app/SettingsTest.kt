package pub.beermat.app

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {

    @Test
    fun `zooming steps through the levels and stops at the ends`() {
        assertEquals(125, Settings.step(100, 1))
        assertEquals(75, Settings.step(100, -1))
        assertEquals(200, Settings.step(200, 1))
        assertEquals(50, Settings.step(50, -1))
        assertEquals(125, Settings.step(110, 1), "an unknown level counts as 100%")
    }
}
