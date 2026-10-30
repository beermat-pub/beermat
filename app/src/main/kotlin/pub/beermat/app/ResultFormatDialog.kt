package pub.beermat.app

import pub.beermat.app.page.Session
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

/**
 * Double-clicking a result (or Format → Result…): its rounding, when it turns to an exponent,
 * and its unit, the Result Format dialog (docs/editor.md#result-format).
 */
object ResultFormatDialog {

    sealed interface Answer {
        data class Apply(val settings: Session.ResultSettings) : Answer
        /** Back to the sheet's format (the unit is kept). */
        data object SheetFormat : Answer
    }

    /** Shows the dialog; null when cancelled. Asks again while the unit doesn't parse. */
    fun show(current: Session.ResultSettings): Answer? {
        val decimals = JRadioButton("Decimal places", !current.significant)
        val significant = JRadioButton("Significant figures", current.significant)
        ButtonGroup().apply { add(decimals); add(significant) }
        val digits = JSpinner(SpinnerNumberModel(current.digits, 0, 15, 1))
        val zeros = JCheckBox("Show trailing zeros", current.trailingZeros)
        val threshold = JSpinner(SpinnerNumberModel(current.exponentialThreshold, 1, 15, 1))
        val unit = JTextField(current.unit, 14)
        fun sync() {
            // Significant figures start at 1, and always keep their trailing zeros.
            (digits.model as SpinnerNumberModel).minimum = if (significant.isSelected) 1 else 0
            if (significant.isSelected && (digits.value as Int) < 1) digits.value = 1
            zeros.isEnabled = decimals.isSelected
        }
        decimals.addActionListener { sync() }
        significant.addActionListener { sync() }
        sync()

        val panel = JPanel(GridBagLayout())
        var row = 0
        fun add(label: String?, field: java.awt.Component) {
            val c = GridBagConstraints().apply { gridy = row++; insets = Insets(3, 3, 3, 3); anchor = GridBagConstraints.WEST }
            if (label != null) panel.add(JLabel(label), c.apply { gridx = 0 })
            panel.add(field, GridBagConstraints().apply { gridx = 1; gridy = c.gridy; insets = c.insets; anchor = GridBagConstraints.WEST })
        }
        add("Rounding", decimals)
        add(null, significant)
        add("Number", digits)
        add(null, zeros)
        add("Exponent from 10^", threshold)
        add("Unit", unit)
        add(null, JLabel("Blank for automatic SI units; for example kN*m"))

        val options = arrayOf("OK", "Sheet's format", "Cancel")
        while (true) {
            val choice = JOptionPane.showOptionDialog(null, panel, "Result format", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0])
            when (choice) {
                0 -> {
                    val text = unit.text.trim()
                    val problem = text.takeIf { it.isNotEmpty() }?.let {
                        try {
                            Parser.parseExpression(it)
                            null
                        } catch (e: ParseException) {
                            e.message
                        }
                    }
                    if (problem != null) {
                        JOptionPane.showMessageDialog(null, "The unit doesn't read as math: $problem", "Result format", JOptionPane.WARNING_MESSAGE)
                        continue
                    }
                    return Answer.Apply(Session.ResultSettings(significant.isSelected, digits.value as Int, zeros.isSelected && decimals.isSelected, threshold.value as Int, text))
                }
                1 -> return Answer.SheetFormat
                else -> return null
            }
        }
    }

    /** Opens the dialog on [statement] of [region] and applies what the user chose. */
    fun edit(session: Session, region: Int, statement: Int) {
        val current = session.resultSettings(region, statement)
        when (val answer = show(current)) {
            is Answer.Apply -> session.setResultSettings(region, statement, answer.settings)
            Answer.SheetFormat -> session.setResultSettings(region, statement, null)
            null -> {}
        }
    }
}
