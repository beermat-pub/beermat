# The web version

> Status: **plan**, decided by Claude on 2026-10-28 while Dan was away; approved by Dan on 2026-10-29.

A version in the browser, at `beermat.pub/try`, for people to try Beermat without installing anything: the same editor and pages, opening the examples, saving to the computer. It is a taster: the desktop app stays the real thing (the MCP server, the command line, files on disk).

## Approach: the same code, compiled to WebAssembly

Compose Multiplatform runs in the browser through Kotlin/Wasm, drawing with the same Skia we use on the desktop. So the editor, the page and the math layout can be the same code. What stands in the way is that the modules are Kotlin/JVM and use a few Java libraries:

| Module | Uses from Java | In the browser |
|---|---|---|
| engine | `java.math` (decimals in display), regexes, `Math` | Kotlin's own `Regex` and math work as they are; replace the few `BigDecimal` uses |
| symbolic | `BigInteger` (exact rationals) | a small multiplatform big-integer library (Apache-2.0) or our own |
| format | SnakeYAML (frontmatter, plots), `MessageDigest` (the results hash) | a multiplatform YAML library (kaml, Apache-2.0) or a small parser for our subset; a tiny SHA-256 |
| export | nothing special | as is |
| app | AWT (clipboard, file dialogs, the window), `HttpServer` (MCP) | browser equivalents; no MCP server in the browser |
| xmcd, cli, mcp | JDK XML, files, stdio | desktop only |

## Steps

1. Make `engine`, `symbolic`, `format` and `export` **multiplatform** (JVM and Wasm targets), moving the Java uses behind small `expect`/`actual` functions. Same tests, run on both targets. This is most of the work, and it is worth it anyway: the engine could then also run in a browser page of the docs, or in a VS Code extension.
2. Split the app into a common UI module (editor, page, painter, toolbar) and two shells: desktop (window, menus, files, MCP) and web (canvas, browser storage, upload and download).
3. The web shell: examples to open, the sheet kept in the browser's storage, Open and Save as file upload and download, a banner "the desktop app does more: download".
4. Host it on GitHub Pages under `beermat.pub/try`, built by the release workflow, so it always matches the latest release.

## What the web version leaves out

Asked for by Dan on 2026-10-29: whatever the browser can't do still shows, greyed out, with a note "Available in the desktop version", linking to the download page. Never hidden, so a user learns what the desktop app adds.

| Left out | Why | Where it shows |
|---|---|---|
| SymPy keywords (`factor`, `series`, general `solve`, Laplace…) | No Python in the browser | The `→` result: "needs SymPy: available in the desktop version" ([symbolic.md](symbolic.md#bundled-sympy)) |
| Python functions (`use "file.py"`) | No Python | The statement's error |
| The MCP server (an AI assistant on the live sheet) | No local server in a browser | Greyed out in the menu, with the note |
| `.xmcd` import, the command line | Desktop only (JDK XML, files) | Greyed out in the File menu, with the note |
| Files on disk | The browser's own storage instead; Open and Save as upload and download | — |

## Effort and order

About as large as a milestone: step 1 is the bulk (mechanical but wide), steps 2 and 3 are moderate. It is **part of 1.0** (Dan, 2026-10-29): the 1.0.0 release ships the desktop versions and the web version together. The website also shows a short video of each key feature.

## Alternatives considered

- **A server running the desktop engine**, the browser drawing only: two programs to keep in step, and a server to pay for and secure. No.
- **Streaming the desktop app** (a remote desktop in the browser): heavy, costly, laggy. No.
- **Rewriting the UI for the web** (HTML and JavaScript): two editors to maintain. No.
