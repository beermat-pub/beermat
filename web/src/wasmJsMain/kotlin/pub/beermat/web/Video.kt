package pub.beermat.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pub.beermat.app.Commands
import pub.beermat.app.MATH_SIZE
import pub.beermat.app.SheetView
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.Session

/**
 * The page for recording the feature videos (docs/web.md#the-video-page): `/try?video`, the toolbar and the
 * page alone, at 175 % so math reads on a phone (or the zoom in `?video=125`, for a sheet that needs room), an empty sheet that the browser doesn't keep. The
 * recorder (`tools/record-video.mjs`) types into it and sets, through `window.beermatVideo`, the key
 * just pressed (a badge at the bottom right, so the video teaches the shortcuts) and the caption.
 * With `&square=240`, both are drawn inside the square from x 240 to 960, the badge above the caption,
 * so that a square crop of the video keeps them (the LinkedIn cuts).
 */
@Composable
internal fun VideoApp() {
    var ready by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var key by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf<String?>(null) }
    // The page draws again at every frame, so that a still moment is recorded as long as it lasts: the
    // browser gives a canvas's video a frame only when it changes.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { tick++ } }
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    LaunchedEffect(Unit) {
        loadFonts(resolver)
        ready = true
        exposeVideo({ key = it.ifEmpty { null } }, { caption = it.ifEmpty { null } })
    }
    if (!ready) return
    val session = remember { Session("", MathLayout(SkiaMath, MATH_SIZE)) }
    val square = remember { videoSquare() }
    // Where the badge and the caption go: the whole page, or the square.
    val frame = if (square > 0) Modifier.fillMaxHeight().width(720.dp).offset(x = square.dp) else Modifier.fillMaxSize()
    Box(Modifier.fillMaxSize()) {
        SheetView(
            session, revision, Commands.palettes, videoZoom().takeIf { it > 0 } ?: 175, WebHost,
            onChange = { revision++ },
            onSave = {},
            onZoom = {},
            statusBar = {},
        )
        key?.let { k ->
            Box(frame.padding(bottom = if (square > 0) 96.dp else 28.dp, end = 28.dp), contentAlignment = Alignment.BottomEnd) {
                BasicText(
                    k,
                    Modifier.background(Color(0xE62B2118), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                    style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        // One pixel, all but invisible, that changes at every frame.
        Box(Modifier.size(1.dp).background(Color(0f, 0f, 0f, if (tick % 2 == 0) 0.01f else 0.02f)))
        caption?.let { c ->
            Box(frame.padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
                BasicText(
                    c,
                    Modifier.background(Color(0xE6F2B33D), RoundedCornerShape(10.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
                    style = TextStyle(color = Color(0xFF2B2118), fontSize = 24.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
