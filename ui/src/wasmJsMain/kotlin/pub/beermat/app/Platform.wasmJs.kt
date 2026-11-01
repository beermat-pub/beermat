package pub.beermat.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

internal actual fun logError(e: Throwable) = e.printStackTrace()

// The browser shell fetches the font and sets SkiaMath.fontBytes before the first page is drawn.
internal actual fun defaultFontBytes(): ByteArray? = null

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = block()

// Compose has no tooltip area in the browser: the tip opens in a popup under the content once the
// mouse has rested on it as long as the desktop waits (350 ms), and closes when it leaves or clicks.
@Composable
internal actual fun WithTip(tip: @Composable () -> Unit, content: @Composable () -> Unit) {
    var hovering by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    LaunchedEffect(hovering) {
        shown = false
        if (hovering) {
            delay(350)
            shown = true
        }
    }
    Box(
        Modifier.onSizeChanged { height = it.height }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    when (awaitPointerEvent().type) {
                        PointerEventType.Enter -> hovering = true
                        PointerEventType.Exit, PointerEventType.Press -> hovering = false
                    }
                }
            }
        },
    ) {
        content()
        if (shown) Popup(offset = IntOffset(0, height + 4), properties = PopupProperties(focusable = false)) { tip() }
    }
}

internal actual val typedOnKeyDown: Boolean = true
