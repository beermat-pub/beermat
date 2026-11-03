@file:OptIn(ExperimentalWasmJsInterop::class)

package pub.beermat.web

import kotlin.io.encoding.Base64

// What the page asks of the browser, each a few lines of JavaScript.

/** The sheet kept between visits, in the browser's own storage; null on a first visit. */
internal fun stored(key: String): String? = js("window.localStorage.getItem(key)")

internal fun store(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")

/** Offers [text] as a file named [name] to save, as a download. */
internal fun download(name: String, text: String): Unit = js(
    """{
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([text], { type: 'text/markdown' }));
        a.download = name;
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    }""",
)

/** Offers [base64] bytes of the type [mime] as a file named [name] to save, as a download. */
internal fun downloadBytes(name: String, mime: String, base64: String): Unit = js(
    """{
        const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0));
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([bytes], { type: mime }));
        a.download = name;
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    }""",
)

/** Asks for a worksheet on the computer, and gives its name and text when one is chosen. */
internal fun upload(onFile: (String, String) -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.bbm,.md';
        input.onchange = () => { const f = input.files[0]; if (f) f.text().then(t => onFile(f.name, t)); };
        input.click();
    }""",
)

/** Asks for a picture on the computer, and gives its name, type and bytes (base64) when one is chosen. */
internal fun uploadPicture(onPicture: (String, String, String) -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/png,image/jpeg,image/gif,image/webp,image/svg+xml';
        input.onchange = () => {
            const f = input.files[0];
            if (!f) return;
            const reader = new FileReader();
            reader.onload = () => onPicture(f.name, f.type, reader.result.substring(reader.result.indexOf(',') + 1));
            reader.readAsDataURL(f);
        };
        input.click();
    }""",
)

internal fun copyToClipboard(text: String): Unit = js("navigator.clipboard.writeText(text).catch(() => {})")

private fun copyPngToClipboard(base64: String): Unit = js(
    """fetch('data:image/png;base64,' + base64).then(r => r.blob())
        .then(b => navigator.clipboard.write([new ClipboardItem({ 'image/png': b })])).catch(() => {})""",
)

internal fun copyPng(png: ByteArray) = copyPngToClipboard(Base64.encode(png))

/** The browser's own question box: what was typed, or null when cancelled. */
internal fun prompt(message: String, initial: String): String? = js("window.prompt(message, initial)")

internal fun confirm(message: String): Boolean = js("window.confirm(message)")

internal fun alert(message: String): Unit = js("window.alert(message)")

internal fun openPage(url: String): Unit = js("window.open(url, '_blank', 'noopener')")

/** Leaves the page for [url], in the same tab (the sheet stays in the browser's storage). */
internal fun goTo(url: String): Unit = js("window.location.href = url")

/** Calls [onText] with the text of every paste on the page (Ctrl+V): the browser gives it only there. */
internal fun onPaste(onText: (String) -> Unit): Unit = js(
    """document.addEventListener('paste', e => {
        const t = e.clipboardData && e.clipboardData.getData('text/plain');
        if (t) { onText(t); e.preventDefault(); }
    })""",
)

// VS Code's web view (docs/vscode.md): the page talks to the extension in messages.

/** Whether the page runs in VS Code's web view rather than a browser tab. */
internal fun inVsCode(): Boolean = js("typeof acquireVsCodeApi === 'function'")

/** Sends the extension a message: its [type], and [text] and [id] where the type has them. */
internal fun postToVsCode(type: String, text: String, id: Int): Unit = js(
    """{
        window.beermatVsCode = window.beermatVsCode || acquireVsCodeApi();
        window.beermatVsCode.postMessage({ type: type, text: text, id: id });
    }""",
)

/** Calls [onMessage] with each message from the extension: its type, text, id, and the yes-or-no of an answer. */
internal fun onVsCodeMessage(onMessage: (String, String, Int, Boolean) -> Unit): Unit = js(
    """window.addEventListener('message', e => {
        const m = e.data || {};
        onMessage(String(m.type || ''), String(m.text == null ? '' : m.text), Number(m.id || 0), !!m.yes);
    })""",
)
