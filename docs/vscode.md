# The VS Code extension

> Status: **step 2 built** (2026-11-03; Dan brought the extension into 1.0 that day): the custom editor, the web build inside it, the document kept in step both ways, a `.vsix` on each release. Steps 3 to 5 to do. Plan agreed with Dan on 2026-11-01 (milestone 24).

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

## Alternatives considered

- **Text with live results** (a language extension running `beermat eval`, results shown after each `= ?`, errors as squiggles, math drawn in the Markdown preview): quicker, but not the full editor. It may still come as a small extra for those who prefer the text.
- **The desktop app's window inside VS Code**: not possible; an extension draws only in web views.
