package pub.beermat.export

import pub.beermat.engine.HostFile
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.format.Annotation
import pub.beermat.format.AreaEnd
import pub.beermat.format.AreaStart
import pub.beermat.format.Calculation
import pub.beermat.format.Column
import pub.beermat.format.ImageRegion
import pub.beermat.format.LineOutcome
import pub.beermat.format.MathRegion
import pub.beermat.format.PageBreak
import pub.beermat.format.PlotBlock
import pub.beermat.format.PlotOutcome
import pub.beermat.format.PlotRegion
import pub.beermat.format.Regions
import pub.beermat.format.Table
import pub.beermat.format.TableBlock
import pub.beermat.format.TableOutcome
import pub.beermat.format.TableRegion
import pub.beermat.format.TextRegion
import pub.beermat.format.Worksheet
import pub.beermat.format.identityMap

/**
 * A whole worksheet as a Word document (docs/export.md#word): text with its Markdown formatting,
 * each statement an editable equation ([Omml]) with its result, tables as Word tables, plots and
 * pictures as images. Regions follow one another in reading order: Word has no free-form page.
 */
object Docx {

    /** An image to put in the document: PNG, JPEG or GIF bytes, and its size on the page in points. */
    class Picture(val bytes: ByteArray, val type: String, val width: Float, val height: Float)

    /**
     * The sheet as a `.docx`. [picture] draws a plot ([PlotOutcome]) or a picture ([ImageRegion]) as an
     * image (the app's painter, in the app and the web version); without it (the command line), embedded
     * PNG, JPEG and GIF pictures go in as they are, and plots and other pictures are noted in the text.
     * [folder] is where the sheet's data files are read from.
     */
    fun write(sheet: Worksheet, picture: ((Any) -> Picture?)? = null, folder: HostFile? = null): ByteArray =
        Writer(sheet, picture, folder).document()

    /** An embedded picture as it is, when Word can show it. */
    fun embedded(item: Any): Picture? {
        val region = item as? ImageRegion ?: return null
        val bytes = region.bytes() ?: return null
        val (type, size) = Images.size(bytes) ?: return null
        val w = region.attributes["w"]?.toFloatOrNull()
        val h = region.attributes["h"]?.toFloatOrNull()
        // Pixels at 96 per inch, as on the page, when the region has no size of its own.
        return Picture(bytes, type, w ?: size.first * 0.75f, h ?: size.second * 0.75f)
    }

    private class Writer(val sheet: Worksheet, val painter: ((Any) -> Picture?)?, folder: HostFile?) {
        val calc = Calculation.of(sheet, folder)
        val omml = Omml(Units.of(calc.lines.mapNotNull { it.parsed }), calc.settings.multiplication)
        val body = StringBuilder()
        val media = mutableListOf<Pair<String, Picture>>()
        val links = mutableListOf<String>()
        val numbered = mutableListOf<Int>()
        val page = Page.of(sheet.frontmatter)

        fun document(): ByteArray {
            val outcomes = identityMap<Any, LineOutcome>().apply { calc.lines.forEach { put(it.line, it) } }
            val regions = Regions.of(sheet)
            // Plots and tables pair with their outcomes in order.
            val plots = identityMap<Any, PlotOutcome>().apply {
                regions.filterIsInstance<PlotRegion>().zip(sheet.parts.filterIsInstance<PlotBlock>()).forEach { (r, b) ->
                    calc.plots.firstOrNull { it.block === b }?.let { put(r, it) }
                }
            }
            val tables = identityMap<Any, TableOutcome>().apply {
                regions.filterIsInstance<TableRegion>().zip(calc.tables).forEach { (r, t) -> put(r, t) }
            }
            var hidden = 0
            for (r in regions) {
                // A collapsed area is left out, as when printing.
                if (r is AreaStart && (hidden > 0 || r.collapsed)) {
                    hidden++
                    continue
                }
                if (r is AreaEnd && hidden > 0) {
                    hidden--
                    continue
                }
                if (hidden > 0) continue
                when (r) {
                    is TextRegion -> text(r.lines)
                    is MathRegion -> for (line in r.lines) outcomes[line]?.let(::equation)
                    is PlotRegion -> plots[r]?.let { p -> image(p, "Plot", p.block.attributes["w"]?.toFloatOrNull() ?: 420f) } ?: note("(A plot that could not be drawn.)")
                    is TableRegion -> tables[r]?.let(::table)
                    is ImageRegion -> image(r, r.alt, null)
                    is PageBreak -> body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                    is AreaStart -> r.title.takeIf { it.isNotBlank() }?.let { paragraph(inline(it, bold = true)) }
                    is AreaEnd -> {}
                }
            }
            return pack()
        }

        fun equation(o: LineOutcome) {
            val st = o.parsed ?: return
            // A symbolic result is an expression, cached as its text.
            val result = o.shown?.let { Result.of(ResultText.parts(it, o.format)) }
                ?: (o.annotation as? Annotation.Result)?.takeIf { st is Symbolic }?.let { Result.of(ResultText.Parts(o.symbolic ?: it.text, null)) }
            body.append("<w:p><m:oMathPara><m:oMathParaPr><m:jc m:val=\"left\"/></m:oMathParaPr>${omml.math(st, result)}</m:oMathPara></w:p>")
            (o.annotation as? Annotation.Error)?.let { body.append("<w:p><w:pPr><w:pStyle w:val=\"BeermatError\"/></w:pPr>${run("Error: ${it.message}")}</w:p>") }
        }

        fun image(item: Any, alt: String, defaultWidth: Float?) {
            val p = (painter ?: ::embedded)(item) ?: return note(
                when {
                    item is ImageRegion -> "(A picture that could not be included: ${item.alt.ifBlank { "no description" }}.)"
                    painter == null -> "(A plot: the app's Export to Word draws it.)"
                    else -> "(A plot that could not be drawn.)"
                },
            )
            val k = media.size + 1
            media += "image$k.${p.type}" to p
            // No wider than the text.
            val scale = minOf(1f, page.textWidth / p.width)
            val cx = (p.width * scale * EMU).toLong()
            val cy = (p.height * scale * EMU).toLong()
            val name = xml(alt.ifBlank { "Picture $k" })
            body.append(
                "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
                    "<wp:docPr id=\"$k\" name=\"Picture $k\" descr=\"$name\"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
                    "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>" +
                    "<pic:nvPicPr><pic:cNvPr id=\"$k\" name=\"image$k.${p.type}\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                    "<pic:blipFill><a:blip r:embed=\"rImage$k\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                    "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
                    "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>",
            )
        }

        /** A table as a Word table: the header in bold, computed cells as their values. */
        fun table(t: TableOutcome) {
            val cols = t.table.columns
            fun cell(text: String, align: Column.Align, bold: Boolean) =
                "<w:tc><w:p><w:pPr><w:spacing w:after=\"0\"/><w:jc w:val=\"${jc(align)}\"/></w:pPr>${run(text, bold = bold)}</w:p></w:tc>"
            body.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"BeermatTable\"/><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr><w:tblGrid>")
            cols.forEach { _ -> body.append("<w:gridCol/>") }
            body.append("</w:tblGrid><w:tr><w:trPr><w:tblHeader/></w:trPr>")
            cols.forEach { body.append(cell(it.header, it.align, true)) }
            body.append("</w:tr>")
            t.table.rows.forEachIndexed { r, row ->
                body.append("<w:tr>")
                row.forEachIndexed { c, value ->
                    val shown = t.shown[r to c] ?: if (Table.expression(value) != null) "" else value
                    body.append(cell(shown, cols.getOrNull(c)?.align ?: Column.Align.LEFT, false))
                }
                body.append("</w:tr>")
            }
            // Word wants a paragraph between a table and what follows.
            body.append("</w:tbl><w:p/>")
        }

        fun jc(a: Column.Align) = when (a) {
            Column.Align.LEFT -> "left"
            Column.Align.CENTRE -> "center"
            Column.Align.RIGHT -> "right"
        }

        /** Markdown text: headings, paragraphs, lists, quotes and code, with bold, italic, code and links inside. */
        fun text(lines: List<String>) {
            val paragraph = mutableListOf<String>()
            var fence: String? = null
            var list: Int? = null
            fun flush() {
                if (paragraph.isEmpty()) return
                paragraph(paragraph.joinToString(" ") { it.trim() }.let { inline(it) })
                paragraph.clear()
            }
            for (raw in lines) {
                val t = raw.trim()
                if (fence != null) {
                    if (t.startsWith(fence)) fence = null else paragraph(run(raw, code = true), "BeermatCode")
                    continue
                }
                if (t.startsWith("<!--") && t.endsWith("-->")) continue
                val heading = Regex("""(#{1,6})\s+(.*)""").matchEntire(t)
                val bullet = Regex("""[-*+]\s+(.*)""").matchEntire(t)
                val number = Regex("""\d+[.)]\s+(.*)""").matchEntire(t)
                when {
                    t.startsWith("```") || t.startsWith("~~~") -> {
                        flush()
                        fence = t.takeWhile { it == t[0] }
                    }
                    t.isEmpty() -> {
                        flush()
                        list = null
                    }
                    heading != null -> {
                        flush()
                        paragraph(inline(heading.groupValues[2].trimEnd('#', ' ')), "Heading${minOf(heading.groupValues[1].length, 3)}")
                    }
                    bullet != null -> {
                        flush()
                        paragraph(inline(bullet.groupValues[1]), "ListParagraph", numbering = BULLETS)
                    }
                    number != null -> {
                        flush()
                        val id = list ?: (NUMBERS_FROM + numbered.size).also { numbered += it; list = it }
                        paragraph(inline(number.groupValues[1]), "ListParagraph", numbering = id)
                    }
                    t.startsWith(">") -> {
                        flush()
                        paragraph(inline(t.removePrefix(">").trim()), "Quote")
                    }
                    else -> paragraph += raw
                }
            }
            flush()
        }

        fun note(s: String) = paragraph(run(s, italic = true))

        fun paragraph(runs: String, style: String? = null, numbering: Int? = null) {
            val props = (style?.let { "<w:pStyle w:val=\"$it\"/>" } ?: "") +
                (numbering?.let { "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"$it\"/></w:numPr>" } ?: "")
            body.append("<w:p>").append(if (props.isEmpty()) "" else "<w:pPr>$props</w:pPr>").append(runs).append("</w:p>")
        }

        /** `**bold**`, `*italic*` (or `_italic_`), `` `code` `` and `[links](url)` as Word runs. */
        fun inline(s: String, bold: Boolean = false): String {
            val out = StringBuilder()
            var k = 0
            val plain = StringBuilder()
            fun flush() {
                if (plain.isNotEmpty()) out.append(run(plain.toString(), bold = bold))
                plain.clear()
            }
            while (k < s.length) {
                val rest = s.substring(k)
                val strong = Regex("""^\*\*(.+?)\*\*""").find(rest)
                // `_` inside a word (`M_max`) is not emphasis.
                val em = Regex("""^\*(.+?)\*|^_(.+?)_(?![\p{L}\p{N}])""").find(rest)?.takeIf { k == 0 || !s[k - 1].isLetterOrDigit() || it.value[0] == '*' }
                val code = Regex("""^`([^`]+)`""").find(rest)
                val link = Regex("""^\[([^\]]+)]\(([^)\s]+)\)""").find(rest)
                when {
                    rest.startsWith("\\") && rest.length > 1 -> {
                        plain.append(rest[1])
                        k += 2
                    }
                    strong != null -> {
                        flush()
                        out.append(run(strong.groupValues[1], bold = true))
                        k += strong.value.length
                    }
                    em != null -> {
                        flush()
                        out.append(run(em.groupValues[1] + em.groupValues[2], bold = bold, italic = true))
                        k += em.value.length
                    }
                    code != null -> {
                        flush()
                        out.append(run(code.groupValues[1], code = true))
                        k += code.value.length
                    }
                    link != null -> {
                        flush()
                        links += link.groupValues[2]
                        out.append("<w:hyperlink r:id=\"rLink${links.size}\">${run(link.groupValues[1], style = "Hyperlink")}</w:hyperlink>")
                        k += link.value.length
                    }
                    else -> {
                        plain.append(s[k])
                        k++
                    }
                }
            }
            flush()
            return out.toString()
        }

        fun run(s: String, bold: Boolean = false, italic: Boolean = false, code: Boolean = false, style: String? = null): String {
            val props = (style?.let { "<w:rStyle w:val=\"$it\"/>" } ?: "") +
                (if (code) "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>" else "") +
                (if (bold) "<w:b/>" else "") + (if (italic) "<w:i/>" else "")
            return "<w:r>${if (props.isEmpty()) "" else "<w:rPr>$props</w:rPr>"}<w:t xml:space=\"preserve\">${xml(s)}</w:t></w:r>"
        }

        fun pack(): ByteArray {
            val zip = Zip()
            zip.add("[Content_Types].xml", contentTypes())
            zip.add("_rels/.rels", XML + "<Relationships xmlns=\"$RELS\">" +
                "<Relationship Id=\"rDoc\" Type=\"$OFFICE/officeDocument\" Target=\"word/document.xml\"/>" +
                "<Relationship Id=\"rCore\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                "</Relationships>")
            zip.add("docProps/core.xml", XML + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>${xml(calc.settings.title ?: "")}</dc:title><dc:creator>Beermat</dc:creator></cp:coreProperties>")
            zip.add("word/document.xml", XML + "<w:document $NAMESPACES><w:body>$body${page.section()}</w:body></w:document>")
            zip.add("word/_rels/document.xml.rels", XML + "<Relationships xmlns=\"$RELS\">" +
                "<Relationship Id=\"rStyles\" Type=\"$OFFICE/styles\" Target=\"styles.xml\"/>" +
                "<Relationship Id=\"rNumbering\" Type=\"$OFFICE/numbering\" Target=\"numbering.xml\"/>" +
                media.mapIndexed { k, (name, _) -> "<Relationship Id=\"rImage${k + 1}\" Type=\"$OFFICE/image\" Target=\"media/$name\"/>" }.joinToString("") +
                links.mapIndexed { k, url -> "<Relationship Id=\"rLink${k + 1}\" Type=\"$OFFICE/hyperlink\" Target=\"${xml(url)}\" TargetMode=\"External\"/>" }.joinToString("") +
                "</Relationships>")
            zip.add("word/styles.xml", STYLES)
            zip.add("word/numbering.xml", numbering())
            media.forEach { (name, p) -> zip.add("word/media/$name", p.bytes) }
            return zip.bytes()
        }

        fun contentTypes() = XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/><Default Extension=\"gif\" ContentType=\"image/gif\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
            "</Types>"

        /** Bullets, and one numbering per numbered list, each starting at 1. */
        fun numbering(): String {
            fun level(format: String, text: String) =
                "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"$format\"/><w:lvlText w:val=\"$text\"/><w:lvlJc w:val=\"left\"/>" +
                    "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl>"
            return XML + "<w:numbering $NAMESPACES>" +
                "<w:abstractNum w:abstractNumId=\"1\">${level("bullet", "•")}</w:abstractNum>" +
                "<w:abstractNum w:abstractNumId=\"2\">${level("decimal", "%1.")}</w:abstractNum>" +
                "<w:num w:numId=\"$BULLETS\"><w:abstractNumId w:val=\"1\"/></w:num>" +
                numbered.joinToString("") { "<w:num w:numId=\"$it\"><w:abstractNumId w:val=\"2\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>" } +
                "</w:numbering>"
        }
    }

    /** The page: A4 or letter, with ¾ inch margins, as the sheet's. */
    private class Page(val width: Int, val height: Int) {
        /** The text's width, in points. */
        val textWidth: Float get() = (width - 2 * MARGIN) / 20f

        fun section() = "<w:sectPr><w:pgSz w:w=\"$width\" w:h=\"$height\"/>" +
            "<w:pgMar w:top=\"$MARGIN\" w:right=\"$MARGIN\" w:bottom=\"$MARGIN\" w:left=\"$MARGIN\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr>"

        companion object {
            /** ¾ inch, in twentieths of a point. */
            const val MARGIN = 1080

            fun of(frontmatter: List<String>?): Page {
                val name = frontmatter?.firstNotNullOfOrNull { Regex("""page:\s*(\S+)""").matchEntire(it.trim())?.groupValues?.get(1) }
                return if (name?.lowercase() == "letter") Page(12240, 15840) else Page(11906, 16838)
            }
        }
    }

    /** English Metric Units per point. */
    private const val EMU = 12700f

    private const val BULLETS = 1
    private const val NUMBERS_FROM = 2

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NAMESPACES =
        "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\" " +
            "xmlns:r=\"$OFFICE\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""

    private val STYLES = XML + "<w:styles $NAMESPACES>" +
        "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Calibri\"/>" +
        "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"en-GB\"/></w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
        heading(1, 32) + heading(2, 26) + heading(3, 24) +
        "<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:after=\"60\"/><w:ind w:left=\"720\"/></w:pPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Quote\"><w:name w:val=\"Quote\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:ind w:left=\"567\"/></w:pPr><w:rPr><w:i/><w:color w:val=\"555555\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:customStyle=\"1\" w:styleId=\"BeermatCode\"><w:name w:val=\"Code\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:after=\"0\"/></w:pPr><w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"20\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:customStyle=\"1\" w:styleId=\"BeermatError\"><w:name w:val=\"Calculation Error\"/><w:basedOn w:val=\"Normal\"/><w:rPr><w:i/><w:color w:val=\"C62828\"/></w:rPr></w:style>" +
        "<w:style w:type=\"character\" w:styleId=\"Hyperlink\"><w:name w:val=\"Hyperlink\"/><w:rPr><w:color w:val=\"0563C1\"/><w:u w:val=\"single\"/></w:rPr></w:style>" +
        "<w:style w:type=\"table\" w:customStyle=\"1\" w:styleId=\"BeermatTable\"><w:name w:val=\"Calculation Table\"/><w:tblPr><w:tblBorders>" +
        listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") { "<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A0A0\"/>" } +
        "</w:tblBorders><w:tblCellMar><w:left w:w=\"100\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr></w:style>" +
        "</w:styles>"

    private fun heading(level: Int, size: Int) =
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading$level\"><w:name w:val=\"heading $level\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
            "<w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"${level - 1}\"/></w:pPr>" +
            "<w:rPr><w:b/><w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr></w:style>"

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** The type and pixel size of PNG, JPEG and GIF data, from their headers. */
internal object Images {
    fun size(b: ByteArray): Pair<String, Pair<Int, Int>>? {
        fun u(k: Int) = b[k].toInt() and 0xFF
        fun be16(k: Int) = (u(k) shl 8) or u(k + 1)
        fun be32(k: Int) = (be16(k) shl 16) or be16(k + 2)
        return when {
            b.size > 24 && u(0) == 0x89 && b[1] == 'P'.code.toByte() -> "png" to (be32(16) to be32(20))
            b.size > 10 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() -> "gif" to ((u(6) or (u(7) shl 8)) to (u(8) or (u(9) shl 8)))
            b.size > 4 && u(0) == 0xFF && u(1) == 0xD8 -> {
                var k = 2
                while (k + 9 < b.size) {
                    if (u(k) != 0xFF) return null
                    val marker = u(k + 1)
                    // The start-of-frame markers hold the size; others are skipped by their length.
                    if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return "jpeg" to (be16(k + 7) to be16(k + 5))
                    k += 2 + be16(k + 2)
                }
                null
            }
            else -> null
        }
    }
}
