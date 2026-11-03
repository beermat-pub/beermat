# The VS Code extension

> Status: **step 2 built** (2026-11-03; Dan brought the extension into 1.0 that day): the custom editor, the web build inside it, the document kept in step both ways, a `.vsix` on each release. **Steps 3 and 4 built** the same day (the AI tools on the open sheet, below): SymPy, Python functions (with consent), `READEXCEL` and `.xmcd` import through the installed `beermat`; data files and linked pictures read by the extension. Python functions, data files and `.xmcd` import, then steps 4 and 5, to do. Plan agreed with Dan on 2026-11-01 (milestone 24).

The whole Beermat editor inside VS Code: a `.bbm` file opens on its page, with the toolbar and the same keys as the desktop app, beside the code and the chat it belongs with.

## Approach: the web version, hosted by VS Code

VS Code lets an extension draw a file's editor itself, in a web view (a *custom editor*). The web version ([web.md](web.md)) is the app compiled to WebAssembly and drawn on a canvas, so the extension hosts that same build: no second editor to maintain. Its web shell is written with two hosts in mind, the browser and VS Code, which differ only in where files and settings come from.

- **The file stays the text.** The document VS Code holds is the `.bbm` Markdown. The page edits it through VS Code, so saving, undo across editors, git diffs, "Reopen With → Text Editor" and other extensions (Claude Code among them) all see the same text, and an edit made in the text shows on the page at once.
- **What the browser lacks comes from the desktop install.** SymPy, Python functions (`use "file.py"`), data files on disk and `.xmcd` import need more than a web view. When `beermat` (the command line, part of every desktop install) is found, the extension runs it as a helper process for those; without it they are greyed out as on the web, with a link to the download page.
- **MCP on the open sheet.** The extension serves the same tools as the app ([mcp.md](mcp.md)) on the sheet open in VS Code, so an AI assistant in VS Code works on the live page, its edits showing at once and undoing with Ctrl+Z.
- **Theme.** The page stays paper-white, as printed; the toolbar and status bar follow VS Code's colours.

## Steps

1. The web version first (milestone 20), its shell split so the host is swappable.
2. The extension: the custom editor for `*.bbm` and `*.bbm.md`, the web build inside it, the document kept in step both ways.
3. The helper: find `beermat`, run it for symbolics, Python, files and import; grey out what is missing.
4. MCP from the extension.
5. Published on the VS Code Marketplace and Open VSX, built by the release workflow.

## Step 2 in detail

- **One build.** The extension's page is the web version's build (`web/build/dist/…`, in the `.vsix` as `media/`); `main` sees `acquireVsCodeApi` and shows `VsCodeApp` instead of the browser page: no top bar, the toolbar, and a status bar with Manual, Text (the Markdown beside the page), Word and the zoom.
- **The extension** (`vscode/extension.js`, plain JavaScript, no dependencies, so nothing to install or compile): a `CustomTextEditorProvider` for `*.bbm` and `*.bbm.md` (VS Code's default for them; `.bbm` is Markdown in the text editor), "Open as Text" and "Open on the Beermat Page" in the editor's title bar. Its web view loads the page with a `<base>` at the extension's `media/`, and a Content Security Policy that allows only the extension's own files (and WebAssembly).
- **Messages.** Page → extension: `ready`; `edit` (the whole text); `save`; `open` (a link: a web view can't open one); `text`; `word` (an exported `.docx`, base64, saved where the user says); `ask` (a question). Extension → page: `load` (the document's text, at the start and after every change to it); `answer`.
- **The document is the truth.** An `edit` is applied as the part that changed (the common start and end left alone, so undo and the diff gutter show the real change), one at a time, with the file's `\r\n` kept. Edits go once typing pauses (0.4 s) and before a save, so each is a whole step to undo. The page compares an incoming `load` with what it sent, so its own edits coming back change nothing; anything else (the text editor, undo, git, an AI assistant) replaces the sheet, the crosshair staying. A file not written the way the page writes (ids, positions, the results hash) isn't changed by opening it: the page's own text of what came in counts as already sent.
- **Undo is VS Code's**: Ctrl+Z and Ctrl+Y pass through the page (`Host.undoesItself` false) to the editor's undo, on the document, which comes back as a `load`. One history for the page and the text.
- **Questions** (the matrix size, Ctrl+M) are asked by the extension (`showInputBox`, `showQuickPick`) and answered later, since a web view has no `prompt()`: `Host.askMatrix` takes a callback for that (the desktop and the browser answer at once).
- **Packing**: `./gradlew :web:vsix` writes `web/build/vsix/beermat-<version>.vsix` (the manifest written by Gradle, no `vsce`); the Release workflow attaches it to each release. `code --install-extension beermat-<version>.vsix` installs it.
- **Checked** (2026-11-03): the page in headless Edge with a stand-in for VS Code (the document sent, typing sent back as one edit after the pause, clicks sending nothing, an outside change shown); the `.vsix` installed by VS Code's own command line into a scratch folder; the edit logic by `vscode/test/edit.test.js` (Node, in CI). Not yet seen in a real VS Code window: Dan to try.

## Step 3 in detail

- **SymPy** (built 2026-11-03): the page runs the same `SymPyBackend` as the desktop (moved to common code, with `Json`), over a `Bridge`: one JSON request, one answer, as `bridge.py` takes them. On the desktop the bridge is the bundled Python (`SymPy`); in VS Code it is `VsCodeBridge`, set as `Helper.bridge` when the extension says it found `beermat`.
- **A page can't wait**, so `VsCodeBridge` answers from what it has been told: a request met for the first time goes to the extension, and the statement says "SymPy is working on this…"; the answer is kept, and the sheet worked out again, meeting it this time. While requests are out the page doesn't send its text, so "working" never reaches the file; until the user changes something, answers only complete the text the page started from, so opening a sheet changes nothing.
- **The helper** is `beermat bridge` (the command line: standard input to the bundled Python's `bridge.py` and back, one line each), started by the extension at the first request and kept for every sheet; found as the `beermat.command` setting or `beermat` on the PATH (`--version` asked once). It answers symbolic requests only: Python functions (`op`) are refused until the extension can ask the user, as the app does.
- Checked: `vscode/test/helper.test.js` (the helper against a built `beermat`, with `BEERMAT_COMMAND` set; skipped in CI), and the page in headless Edge with the extension's helper behind a stand-in, the integral and `factor` answered by SymPy.
- **Data files and linked pictures** (built 2026-11-03): the extension sends the sheet's folder (`folder`), and the page's `RemoteFiles` (the engine's WebAssembly files) asks for a file the first time it is met (`file`, answered by `filed` with base64), saying it is being read meanwhile, as for SymPy. Text formats only (`READCSV`, `READPRN`, `READFILE`); `READEXCEL` needs the desktop. Files are read again when the sheet comes again from VS Code. `Pictures` no longer remembers a file that wasn't there, so one saved later appears (on the desktop too).
- **Workspace trust**: in VS Code's Restricted Mode the extension reads no files for a sheet and starts no helper (`capabilities.untrustedWorkspaces`: limited).
- **Python functions** (built 2026-11-03): `PythonModules` moved to common code (with `fileName`, `canonicalPath`, `fileVersion` per platform; in the page the version is the file's contents' hash, read through `RemoteFiles`), over the same bridge. The page asks the extension for consent (`consent`, answered by `consented`) the first time it meets a file and says it is waiting meanwhile. The extension asks as the desktop app does (a modal warning with the path), keeps a yes for that file and its contents in VS Code's global state (a changed file asks again) and a no until VS Code closes, never in Restricted Mode; and it checks that key again for **every** Python request before passing it to `beermat bridge --allow-python`, so a page can't run a file the user hasn't allowed. The helper runs them in the user's own Python when one is set, as the app does. `PythonConsent.modules` is now looked up at each evaluation (it was made once, so the page's helper, and the app's own Python chosen while it ran, were missed).
- Checked: `vscode/test/python.test.js` (consent and the per-request check, with VS Code faked; in CI) and the page in headless Edge with the real helper behind a stand-in: `bt.moment(12 kN/m, 6 m) = 54 kN·m` from a `.py` beside the sheet.
- **`READEXCEL`** (built 2026-11-03): the page sends `{"excel": path, "range": …}` over the bridge; `beermat bridge` reads the workbook with POI (`ExcelFiles.read`) and answers the cells as `bridge.py` writes values (numbers, `{"s": …}`, `{"m": rows}`). Not a Python request, so no consent; the helper runs only in a trusted workspace.
- **`.xmcd` import** (built 2026-11-03): **Beermat: Import .xmcd Worksheet…** (and the Explorer's right-click on an `.xmcd`) runs `beermat import`, beside it as a `.bbm` (or where the user says when that exists), opens it on its page and lists what didn't come across. On Windows the helper's command line quotes paths.
- Step 3 is complete.

## Step 4 in detail

Built 2026-11-03.

- **The same tools as the app**, on the same transport (MCP's streamable HTTP, `POST /mcp`), served by the extension at `http://127.0.0.1:7342/mcp` (`beermat.mcpPort`; the app has 7341, so both can run). It starts with the first sheet opened in a trusted workspace, listens on 127.0.0.1 only, and refuses a request with an `Origin` that isn't localhost, as the app does. **Beermat: Connect Claude Code** copies `claude mcp add --transport http beermat-vscode http://127.0.0.1:7342/mcp`.
- **The tools run in `beermat mcp --host`** (the `mcp` module's `Mcp`, with a `Documents` that asks the host): one JSON object per line each way. The extension sends `{"mcp": message}` and gets `{"reply": response}`; while a tool works it asks `{"read": path}` (answered with the text and folder) or `{"write": path, "text": …}`. One MCP message at a time.
- **Live documents**: no path is the active Beermat page (the last one in front); a path is the document open in VS Code for that file, else the file on disk. Reads see unsaved changes; writes are edits to the document (the part that changed), so they show on the page at once, are unsaved until saved, and undo with Ctrl+Z.
- Checked by `vscode/test/mcp.test.js`: refusing a web page's request (in CI), and, with a built `beermat`, a session that reads the live sheet and inserts a statement, which arrives in the document with its result.

## Alternatives considered

- **Text with live results** (a language extension running `beermat eval`, results shown after each `= ?`, errors as squiggles, math drawn in the Markdown preview): quicker, but not the full editor. It may still come as a small extra for those who prefer the text.
- **The desktop app's window inside VS Code**: not possible; an extension draws only in web views.
