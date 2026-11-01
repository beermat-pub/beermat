package pub.beermat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pub.beermat.app.page.Session
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser

/**
 * The Result Format dialog (docs/editor.md#result-format) in Compose: a result's rounding, when it
 * turns to an exponent, and its unit. The web version shows it; the desktop app still has its Swing one.
 * [onApply] gets the settings, or null for the sheet's format (the unit is kept).
 */
@Composable
fun ResultFormatPanel(current: Session.ResultSettings, onApply: (Session.ResultSettings?) -> Unit, onCancel: () -> Unit) {
    var significant by remember { mutableStateOf(current.significant) }
    var digits by remember { mutableIntStateOf(current.digits) }
    var zeros by remember { mutableStateOf(current.trailingZeros) }
    var threshold by remember { mutableIntStateOf(current.exponentialThreshold) }
    var unit by remember { mutableStateOf(current.unit) }
    var problem by remember { mutableStateOf<String?>(null) }
    val text = TextStyle(fontSize = 13.sp, color = Color(0xFF222222))
    val link = TextStyle(fontSize = 13.sp, color = Color(0xFF1E5BD8))

    @Composable
    fun Choice(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
        BasicText((if (on) "◉ " else "○ ") + label, Modifier.let { if (enabled) it.clickable(onClick = onClick) else it }.padding(vertical = 3.dp), style = if (enabled) text else text.copy(color = Color(0xFFAAAAAA)))

    @Composable
    fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(150.dp), style = text)
        BasicText(" − ", Modifier.clickable { onChange((value - 1).coerceIn(range)) }.border(1.dp, Color(0xFFBBBBBB)).padding(horizontal = 6.dp), style = text)
        BasicText("$value", Modifier.width(36.dp).padding(horizontal = 10.dp), style = text)
        BasicText(" + ", Modifier.clickable { onChange((value + 1).coerceIn(range)) }.border(1.dp, Color(0xFFBBBBBB)).padding(horizontal = 6.dp), style = text)
    }

    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.background(Color.White).border(1.dp, Color(0xFFBBBBBB)).padding(16.dp)) {
            BasicText("Result format", style = text.copy(fontSize = 15.sp))
            Spacer(Modifier.height(10.dp))
            Choice("Decimal places", !significant) { significant = false }
            // Significant figures start at 1, and always keep their trailing zeros.
            Choice("Significant figures", significant) { significant = true; digits = maxOf(digits, 1) }
            Spacer(Modifier.height(6.dp))
            Stepper("Number", digits, (if (significant) 1 else 0)..15) { digits = it }
            Choice("Show trailing zeros", zeros && !significant, enabled = !significant) { zeros = !zeros }
            Stepper("Exponent from 10^", threshold, 1..15) { threshold = it }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Unit", Modifier.width(150.dp), style = text)
                BasicTextField(unit, { unit = it; problem = null }, Modifier.width(140.dp).border(1.dp, Color(0xFFBBBBBB)).padding(4.dp), textStyle = text, singleLine = true)
            }
            BasicText("Blank for automatic SI units; for example kN*m", style = text.copy(fontSize = 11.sp, color = Color(0xFF777777)))
            problem?.let { BasicText("The unit doesn't read as math: $it", style = text.copy(color = Color(0xFFC62828))) }
            Spacer(Modifier.height(12.dp))
            Row {
                BasicText("OK", Modifier.clickable {
                    val u = unit.trim()
                    problem = u.takeIf { it.isNotEmpty() }?.let { runCatching { Parser.parseExpression(it) }.exceptionOrNull() as? ParseException }?.message
                    if (problem == null) onApply(Session.ResultSettings(significant, digits, zeros && !significant, threshold, u))
                }.padding(6.dp), style = link)
                BasicText("Sheet's format", Modifier.clickable { onApply(null) }.padding(6.dp), style = link)
                BasicText("Cancel", Modifier.clickable(onClick = onCancel).padding(6.dp), style = link)
            }
        }
    }
}
