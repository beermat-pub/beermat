package pub.beermat.xmcd

import org.apache.poi.poifs.filesystem.POIFSFileSystem
import pub.beermat.engine.syntax.Parser
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Controls read from synthetic items, laid out as `.xmcd` files have them (docs/xmcd.md#import-milestone-8). */
class ControlComponentTest {

    /** An OLE file whose `Contents` stream holds [contents]. */
    private fun ole(contents: ByteArray): ByteArray = POIFSFileSystem().use { fs ->
        fs.createDocument(contents.inputStream(), "Contents")
        ByteArrayOutputStream().also { fs.writeFilesystem(it) }.toByteArray()
    }

    /** An item as an `.xmcd` file writes one: some bytes of its own, then the OLE files. */
    private fun item(vararg oles: ByteArray) = ByteArray(40) + oles.fold(ByteArray(0)) { a, b -> a + b }

    private fun utf16(s: String) = s.toByteArray(Charsets.UTF_16LE)

    @Test
    fun `web page controls`() {
        val list = ControlComponent.read(item(ole("<HTML><SELECT><OPTION value=1>one<OPTION selected value=2.5>two</SELECT></HTML>".toByteArray())))!!
        assertEquals("list box" to "2.5", list.kind to list.value)
        val box = ControlComponent.read(item(ole(utf16("<HTML><INPUT type=checkbox checked></HTML>"))))!!
        assertEquals("check box" to "1", box.kind to box.value)
    }

    @Test
    fun `a data table, column by column after its marker`() {
        val marker = byteArrayOf(0x54, 0x8c.toByte(), 0xc9.toByte(), 0x31, 0x63, 0xd0.toByte(), 0x11, 0xa6.toByte(), 0x8d.toByte(), 0x00, 0xa0.toByte(), 0x24, 0xcf.toByte(), 0x05, 0xd1.toByte())
        // Two columns, three rows: the last column's index, the last row's, and the count.
        val header = listOf(1, 1, 8, 2, 0, 0, 1, 2, 6)
        val values = listOf(1.0, 2.0, 3.0, 4.0, -5.5, 6.0)
        val b = ByteBuffer.allocate(20 + marker.size + 36 + 48 + 8).order(ByteOrder.LITTLE_ENDIAN)
        b.put(ByteArray(20)).put(marker)
        header.forEach { b.putInt(it) }
        values.forEach { b.putDouble(it) }
        val table = ControlComponent.read(b.array(), kind = "a6cfc4d6-3a88-437d-94de-30d1a591cf29")!!
        assertEquals("data table" to "[1, 4; 2, (-5.5); 3, 6]", table.kind to table.value)
        // Older files write no clsid-buddy for a data table.
        assertEquals(table.value, ControlComponent.read(b.array(), kind = "")!!.value)
        Parser.parseExpression(table.value)
        // Another component that keeps a matrix the same way isn't read as a table.
        assertNull(ControlComponent.read(b.array(), kind = "577d93c8-ea65-4d05-b631-401168b146e4"))
    }

    @Test
    fun `scripted controls`() {
        // A text box: its text in its state, after FF FE FF and a length.
        val textScript = ole(utf16("TextBox Sub TextBoxEvent_Exec(Inputs,Outputs) Outputs(0).Value = TextBox.Text End Sub"))
        val state = ole(byteArrayOf(2, 0, 10, 0) + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 3) + utf16("0.5") + ByteArray(8))
        assertEquals("0.5", ControlComponent.read(item(textScript, state))!!.value)
        // A list box that takes its selection from its input when the sheet loads: that input.
        val listScript = ole("ListBox.CurSel = Inputs(0).Value  Outputs(0).Value = ListBox.CurSel".toByteArray())
        val list = ControlComponent.read(item(listScript, ole(ByteArray(16))), listOf(Parser.parseExpression("choice + 1")))!!
        assertEquals("list box" to "choice + 1", list.kind to list.value)
    }
}
