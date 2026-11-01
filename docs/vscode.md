# The VS Code extension

> Status: **plan**, agreed with Dan on 2026-11-01 (milestone 24, after the web version).

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

## Alternatives considered

- **Text with live results** (a language extension running `beermat eval`, results shown after each `= ?`, errors as squiggles, math drawn in the Markdown preview): quicker, but not the full editor. It may still come as a small extra for those who prefer the text.
- **The desktop app's window inside VS Code**: not possible; an extension draws only in web views.
