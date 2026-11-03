package pub.beermat.app

import pub.beermat.symbolic.Packages
import pub.beermat.symbolic.Python
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Tools → Python packages… (docs/symbolic.md#the-users-own-packages): installs and removes the
 * packages a sheet's Python files import, with the bundled pip, into the user's own folder. pip
 * runs on a thread of its own, its output shown as it finishes.
 */
object PythonPackages {

    fun show() {
        val home = Python.home() ?: run {
            JOptionPane.showMessageDialog(null, "This copy of Beermat has no Python.", "Python packages", JOptionPane.WARNING_MESSAGE)
            return
        }
        val dialog = JDialog(null as java.awt.Frame?, "Python packages", true)
        val list = DefaultListModel<String>()
        val names = JList(list)
        val field = JTextField(24)
        val install = JButton("Install")
        val remove = JButton("Remove")
        val again = JButton("Install again")
        val log = JTextArea(8, 50).apply { isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 11) }
        val folder = JLabel()
        val buttons = listOf(install, remove, again, field)

        fun refresh() = Thread {
            val installed = Packages.installed(home)
            val missing = Packages.missing(home)
            val where = Packages.folder(home)
            SwingUtilities.invokeLater {
                list.clear()
                installed.forEach { list.addElement("${it.name} ${it.version}") }
                folder.text = "<html>Installed in ${where.path}, which updates of Beermat leave alone.</html>"
                again.isVisible = missing.isNotEmpty()
                again.text = "Install again: ${missing.joinToString(", ")}"
            }
        }.apply { isDaemon = true }.start()

        /** Runs pip with the buttons off, then shows what it said. */
        fun pip(what: String, job: () -> Packages.Outcome) {
            buttons.forEach { it.isEnabled = false }
            log.text = "$what…\n"
            Thread {
                val o = runCatching(job).getOrElse { Packages.Outcome(false, it.message ?: it.toString()) }
                SwingUtilities.invokeLater {
                    log.text = (if (o.ok) "" else "It didn't work:\n") + o.output.lines().takeLast(40).joinToString("\n")
                    buttons.forEach { it.isEnabled = true }
                    refresh()
                }
            }.apply { isDaemon = true }.start()
        }

        val ask = { field.text.trim().split(Regex("[\\s,]+")).filter(String::isNotEmpty) }
        install.addActionListener { ask().takeIf { it.isNotEmpty() }?.let { r -> pip("Installing ${r.joinToString(", ")}") { Packages.install(home, r) } } }
        field.addActionListener { install.doClick() }
        remove.addActionListener { names.selectedValue?.substringBefore(' ')?.let { n -> pip("Removing $n") { Packages.remove(home, n) } } }
        again.addActionListener { Packages.missing(home).let { m -> pip("Installing ${m.joinToString(", ")}") { Packages.install(home, m) } } }

        val top = JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Package:")); add(field); add(install) }
        val middle = JPanel(BorderLayout(0, 6)).apply {
            add(JLabel("The packages your sheets' Python files can import, beyond SymPy and Python's own:"), BorderLayout.NORTH)
            add(JScrollPane(names).apply { preferredSize = Dimension(420, 140) }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(remove); add(again) }, BorderLayout.SOUTH)
        }
        val bottom = JPanel(BorderLayout(0, 6)).apply {
            add(folder, BorderLayout.NORTH)
            add(JScrollPane(log), BorderLayout.CENTER)
        }
        dialog.contentPane = JPanel(BorderLayout(0, 10)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(top, BorderLayout.NORTH)
            add(middle, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
        again.isVisible = false
        refresh()
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }
}
