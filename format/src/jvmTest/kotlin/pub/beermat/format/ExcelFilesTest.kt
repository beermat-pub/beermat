package pub.beermat.format

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** READEXCEL, on a hand-made .xlsx and a POI-made .xls, beside the sheet. */
class ExcelFilesTest {

    private val dir: File = Files.createTempDirectory("beermat-excel").toFile().apply { deleteOnExit() }

    /** The smallest .xlsx Excel writes: a workbook of two sheets, shared strings, cached formula values. */
    private fun xlsx(name: String) = File(dir, name).also { f ->
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(path: String, text: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(text.trimIndent().toByteArray())
                z.closeEntry()
            }
            val ns = """xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships""""
            put("xl/workbook.xml", """<workbook $ns><sheets><sheet name="Loads" sheetId="1" r:id="rId1"/><sheet name="Other" sheetId="2" r:id="rId2"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>""")
            put("xl/sharedStrings.xml", """<sst $ns><si><t>Case</t></si><si><t>q</t></si><si><t>Dead</t></si></sst>""")
            put(
                "xl/worksheets/sheet1.xml",
                """<worksheet $ns><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>4.5</v></c></row>
                <row r="3"><c r="B3"><f>B2*2</f><v>9</v></c></row>
                </sheetData></worksheet>""",
            )
            put("xl/worksheets/sheet2.xml", """<worksheet $ns><sheetData><row r="1"><c r="A1"><v>42</v></c></row></sheetData></worksheet>""")
        }
    }

    private fun eval(statement: String): String {
        val sheet = WorksheetText.read("```calc\n$statement\n```\n")
        val calc = Calculation.of(sheet, dir)
        return when (val a = calc.lines.single().annotation) {
            is Annotation.Result -> a.text
            is Annotation.Error -> "error: ${a.message}"
            null -> "nothing"
        }
    }

    @Test
    fun `an xlsx, by range and by sheet`() {
        xlsx("loads.xlsx")
        assertEquals("[\"Case\", \"q\"; \"Dead\", 4.5; 0, 9]", eval("READEXCEL(\"loads.xlsx\") = ?"))
        assertEquals("[4.5; 9]", eval("READEXCEL(\"loads.xlsx\", \"Loads!B2:B3\") = ?"))
        assertEquals("42", eval("READEXCEL(\"loads.xlsx\", \"Other!A1\") = ?"))
        assertTrue("no sheet named" in eval("READEXCEL(\"loads.xlsx\", \"Nope!A1\") = ?"))
    }

    @Test
    fun `a classic xls`() {
        HSSFWorkbook().use { wb ->
            val s = wb.createSheet("Data")
            s.createRow(0).apply { createCell(0).setCellValue(1.5); createCell(1).setCellValue("kN") }
            s.createRow(1).apply { createCell(0).cellFormula = "A1*2" }
            wb.creationHelper.createFormulaEvaluator().evaluateAll()
            File(dir, "old.xls").outputStream().use(wb::write)
        }
        assertEquals("[1.5; 3]", eval("READEXCEL(\"old.xls\", \"A1:A2\") = ?"))
        assertTrue("not found" in eval("READEXCEL(\"missing.xlsx\") = ?"))
    }
}
