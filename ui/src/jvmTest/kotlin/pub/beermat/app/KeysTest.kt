package pub.beermat.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeysTest {

    @Test
    fun `on a Mac the command key is Ctrl or ⌘, elsewhere Ctrl only`() {
        assertTrue(command(ctrl = false, meta = true, mac = true))
        assertTrue(command(ctrl = true, meta = false, mac = true))
        assertTrue(command(ctrl = true, meta = false, mac = false))
        assertFalse(command(ctrl = false, meta = true, mac = false))
        assertFalse(command(ctrl = false, meta = false, mac = true))
    }

    @Test
    fun `shortcuts are written with ⌘ on a Mac`() {
        assertEquals("Undo    ⌘Z", shortcutText("Undo    Ctrl+Z", mac = true))
        assertEquals("Separate regions    ⇧⌘S", shortcutText("Separate regions    Ctrl+Shift+S", mac = true))
        assertEquals("Undo    Ctrl+Z", shortcutText("Undo    Ctrl+Z", mac = false))
    }
}
