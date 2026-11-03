package pub.beermat.app

import pub.beermat.app.page.Session
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/** The sheet view's [Host] in the desktop app: the system clipboard and Swing dialogs. */
object DesktopHost : Host {

    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    override fun copyText(text: String) = clipboard.setContents(StringSelection(text), null)

    override fun copyImage(png: ByteArray) {
        val image = ImageIO.read(png.inputStream())
        val transferable = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = image
        }
        clipboard.setContents(transferable, null)
    }

    // Text first: a program that copies both (a spreadsheet) means its cells.
    override fun paste(): Pasted? = when {
        clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) -> (clipboard.getData(DataFlavor.stringFlavor) as? String)?.let(Pasted::Text)
        clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) -> (clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image)?.let { Pasted.Picture(png(it)) }
        else -> null
    }

    /** A picture from the clipboard, as PNG bytes. */
    private fun png(image: java.awt.Image): ByteArray {
        val buffered = image as? BufferedImage ?: BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
            it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        }
        return ByteArrayOutputStream().also { ImageIO.write(buffered, "png", it) }.toByteArray()
    }

    override fun beep() = Toolkit.getDefaultToolkit().beep()

    /** Rows and columns, as `3, 1` (a column of 3) or `2 2`. */
    override fun askMatrix(answer: (Int, Int) -> Unit) {
        val text = JOptionPane.showInputDialog(null, "Rows and columns:", "Insert matrix", JOptionPane.QUESTION_MESSAGE, null, null, "3, 1") as? String ?: return
        val n = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
        if (n.size == 2) answer(n[0], n[1])
    }

    override fun askText(question: String, initial: String, answer: (String) -> Unit) {
        (JOptionPane.showInputDialog(null, question, "Beermat", JOptionPane.QUESTION_MESSAGE, null, null, initial) as? String)?.let(answer)
    }

    override fun askMatrixResize(answer: (Int, Int, Boolean) -> Unit) {
        val field = JTextField("1, 0", 8)
        val panel = JPanel(BorderLayout(0, 6)).apply {
            add(JLabel("Rows and columns, after the cursor's cell (Insert) or from it (Delete):"), BorderLayout.NORTH)
            add(field, BorderLayout.CENTER)
        }
        val options = arrayOf("Insert", "Delete", "Cancel")
        val choice = JOptionPane.showOptionDialog(null, panel, "Resize matrix", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0])
        if (choice !in 0..1) return
        val n = Regex("""\d+""").findAll(field.text).map { it.value.toInt() }.toList()
        if (n.size == 2) answer(n[0], n[1], choice == 0)
    }

    override fun editResultFormat(session: Session, region: Int, statement: Int) = ResultFormatDialog.edit(session, region, statement)
}
