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

1. Make `engine`, `symbolic`, `format` and `export` **multiplatform** (JVM and Wasm targets), moving the Java uses behind small `expect`/`actual` functions. Same tests, run on both targets. This is most of the work, and it is worth it anyway: the engine could then also run in a browser page of the docs, or in the VS Code extension ([vscode.md](vscode.md)).
   - **engine: done** (2026-11-01). What the platform gives is in `Platform.kt` (files as `HostFile`, a `java.io.File` on the desktop; stopping; the clock; how deep calls may nest: 1,000 on the JVM, 300 in WebAssembly, whose stack is smaller). Results are rounded by our own `Decimal`, and random numbers come from `SheetRandom`, a copy of `java.util.Random`'s generator; JVM-only tests check both against the Java classes, so sheets show the same digits and the same random values on every platform. The version and the unit table are generated as Kotlin source. One difference found on the way: in WebAssembly `HashMap.putAll` keeps a value equal to the new one, so `Scope` merges with `put`, which a sheet's `°C := K` needs.
   - **symbolic, format, export: done** (2026-11-01). Our own symbolic engine runs everywhere, its exact fractions on `BigInt` (ours, 32-bit limbs and Knuth's long division) instead of `BigInteger`; SymPy and the sheet's Python functions are JVM only, so in the browser what our engine can't do says it needs SymPy. The format module reads YAML with `snakeyaml-engine-kmp` (the multiplatform port of SnakeYAML Engine, Apache-2.0), hashes results with its own SHA-256, and in the browser `READEXCEL` says it needs the desktop version. JVM-only tests check `BigInt` against `BigInteger`, the fractions' doubles against `BigDecimal`, and the hash against `MessageDigest`, so saved sheets keep their results and hashes.
2. Split the app into a common UI module (editor, page, painter, toolbar) and two shells: desktop (window, menus, files, MCP) and web (canvas, browser storage, upload and download). Planned on 2026-11-01 and approved by Dan (see [Step 2 in detail](#step-2-in-detail)).
3. The web shell: examples to open, the sheet kept in the browser's storage, Open and Save as file upload and download, a banner "the desktop app does more: download". **Done** (2026-11-01; [Step 3 in detail](#step-3-in-detail)).
4. Host it on GitHub Pages under `beermat.pub/try`, built by the release workflow, so it always matches the latest release. **Done** in the workflow (2026-11-01; [release.md](release.md#web-version)): the `site` job publishes each tagged release, previews included, once the deploy key is in.

## Step 2 in detail

> Status: **approved** by Dan on 2026-11-01 (printing and PDF in the browser: later). **2a and 2b done** (2026-11-01); step 3, the browser shell, next.

What the app is made of today (`app/`, about 7,000 lines):

| Part | Lines | Desktop-only uses |
|---|---|---|
| `editor/` (the editing model), `math/` (2D layout, Skia drawing) | 1,900 | the font read from the classpath |
| `page/` (session, layout, pages, painter, tables, pictures, text), `plot/` | 2,400 | the sheet's folder as a `java.io.File`, the error log, `IdentityHashMap`, `java.util.Base64` |
| `Commands.kt`, `SheetItems.kt`, `SheetImage.kt` | 300 | one `IdentityHashMap` |
| `Main.kt`: the window, menu bar, file dialogs, clipboard, the sheet view, its keys and mouse, the toolbar, the text editor, the right-click menus, self-test and scripts | 1,060 | most of them |
| `McpHttp.kt`, `Updates.kt`, `Settings.kt`, `PythonPermission.kt`, `ResultFormatDialog.kt`, `Brand.kt`, `Icons.kt`, `print/` | 1,400 | HTTP server, preferences, dialogs, printing, the PDF writer's files |

**The proposal:**

- **A new module, `ui`** (Kotlin Multiplatform and Compose, JVM and WebAssembly): `editor/`, `math/`, `page/`, `plot/`, the commands, and the sheet view taken out of `Main.kt`: the page with its mouse and keys, the toolbar, the text editor, the right-click menus, the Result Format dialog. Its tests move with it and run on both platforms where they can.
- **`app` stays the desktop shell** (JVM): the window and menu bar, file dialogs and watching the file, clipboard, printing and PDF, the MCP server, the update check, preferences, the Python permission dialog, the icon generator, the self-test and scripts. It depends on `ui`.
- **`web`, the browser shell**, comes in step 3, on `ui` the same way.
- **What the shells give the sheet view: one interface, `Host`**, passed in rather than `expect`/`actual`, since the two shells differ in more than a function or two: copy text or an image, open a link, open or save a file and choose a picture (as `suspend` functions, because a browser's file picker answers later), log an error, keep a setting. The desktop's `Host` does what `Main.kt` does today.
- **The font** (STIX Two Math) through Compose resources, which both platforms read; the shell loads it once before the first page is drawn.
- **Desktop-only Compose features** in the sheet view get common equivalents: `onPointerEvent` becomes `pointerInput`; the scrollbars and the toolbar's tooltips, desktop-only today, are given by the shell (the desktop keeps its own; the browser's come in step 3).

**In two steps, each tested and committed on its own:**

- **2a (done):** create `ui`, move `editor/`, `math/`, `page/`, `plot/` and the commands into it, with the few desktop uses replaced: the folder as `HostFile` (the engine now reads a file's bytes through it, for pictures beside the sheet), the error log and a lock as small platform functions in `ui` (the error log moves into `Host` in 2b), `identityMap`, a plain most-recent-last picture cache. The packages keep their names (`pub.beermat.app.page`…), so nothing else changed. The font stays a desktop resource for now, read through `SkiaMath.fontBytes`, which the browser shell will set. `PlotImages` (the plots' SVG for exports) stays in `app`, as it writes through a JVM stream. `ui`'s tests run on the JVM (they draw with Skia); every one passes, and `ui` compiles for WebAssembly.
- **2b (done):** the sheet view is out of `Main.kt`, in `ui`: `SheetView` (the toolbar, the pages with the mouse and the keys, the text editor, the right-click menus), `Keys`, `Scripts`. The shell gives a `Host` (clipboard, beep, the matrix-size and Result Format dialogs) and puts its own banner and status bar in the slots `SheetView` has for them. The desktop's `DesktopHost` keeps the Swing dialogs and the system clipboard; `Main.kt` keeps the window, the menu bar, file dialogs, following the file on disk, and the self-test (536 lines from 1,064). Changed on the way: Ctrl+wheel zoom listens through `pointerInput` (common) instead of the desktop-only `onPointerEvent`; a double click is timed by the pointer events' own clock; the toolbar's tooltips go through a small platform composable, as Compose has no tooltip area in the browser yet. Checked with every test, `--png --script`, and in the real window with `--selftest` and `--snapshot`.
  For step 3, from this step: the three Swing dialogs (matrix size, matrix resize, Result Format, and the large-picture question in Insert → Picture) need Compose versions in the browser; the browser's clipboard answers later (`navigator.clipboard`), so paste there will come from the page's paste event rather than `Host.paste`; and the toolbar's tooltips.

Printing and PDF export stay on the desktop for now; in the browser they show greyed out, like the rest of what it leaves out.

## Step 3 in detail

> Status: **done** (2026-11-01), checked in headless Edge; hosting (step 4) next.

The `web` module (Kotlin/Wasm and Compose, `web/`): one page that runs `SheetView` from `ui`, as the desktop window does.

- **The page:** a top bar with the sheet's name, New, Open… (a file from the computer, uploaded), Save (downloaded as `.bbm`) and Examples (the files in `examples/`, built into the page); then the toolbar, the pages and a status bar saying the sheet stays in this browser until saved. The sheet is kept in the browser's storage at every change and comes back on the next visit.
- **What only the desktop does** shows greyed in the top bar (Print, PDF, Import .xmcd, AI assistant (MCP)); a click offers the download page. "Get the desktop app" links there too (beermat.pub/download, the site's page that lists the newest release's files), and "Manual" opens beermat.pub/docs.
- **`WebHost`:** the clipboard through `navigator.clipboard` (copy; a paste comes from the page's paste event, the only place the browser gives it), the browser's own question boxes for a matrix's size, and for Result Format, for now, a note that the dialog is in the desktop version.
- **Fonts:** the browser gives Skia no fonts of its own, so the page brings them: STIX Two Math (from `ui`'s resources) and Liberation Sans and Mono (SIL Open Font License 1.1, with their licence, in `web`'s resources), registered through `PageFonts` before the first page is drawn. Liberation Sans has Arial's metrics, so text sits as it does on the desktop.
- **Found on the way, fixed for both:** the browser's Skia lacks `Font.measureText`, so the math is measured from its glyphs (the same numbers on the desktop, checked by a test); in the browser a typed character comes with its key-down event, which `Keys` now reads there; and `Session.text()` now gives new regions their ids in the text it writes, so a sheet kept before its first save reads back whole (the MCP server's `read_worksheet` had the same gap).
- **Size:** about 16 MB as served (the Skia runtime 8.6 MB, Beermat 3.4 MB, the fonts 3 MB); browsers cache it after the first visit.
- **Checking it** with no one at the screen: `tools/web-drive.mjs` drives headless Edge over the DevTools protocol: it loads the page, prints its console, clicks, types, reloads, and saves screenshots of the page itself.
- **Added after the first run (2026-11-01):** the toolbar's tips (a popup after 350 ms, as Compose has no tooltip area in the browser); the Result Format dialog in Compose (`ResultFormatPanel`, in `ui`, so the desktop can move to it too), opened by a double click on a result; Picture… in the top bar (a picture from the computer, asking before a large one, as the desktop does); zoom buttons in the status bar (the browser keeps Ctrl+wheel and Ctrl+= for its own zoom). A double click is now told apart however soon the layout changes after the first click (the click is remembered outside the mouse handler, which restarts then), on both platforms. The matrix size is still asked in the browser's question box.

To try it locally: `./gradlew :web:wasmJsBrowserDevelopmentRun` serves it with live reloading; `./gradlew :web:wasmJsBrowserDistribution` builds the files to host (`web/build/dist/wasmJs/productionExecutable`).

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

About as large as a milestone: step 1 is the bulk (mechanical but wide), steps 2 and 3 are moderate. It is **part of 1.0** (Dan, 2026-10-29): the 1.0.0 release ships the desktop versions and the web version together. Brought forward to the **1.0.0-dev1** preview (Dan, 2026-11-01), so the web version can be tried before 1.0. The website also shows a short video of each key feature.

## Alternatives considered

- **A server running the desktop engine**, the browser drawing only: two programs to keep in step, and a server to pay for and secure. No.
- **Streaming the desktop app** (a remote desktop in the browser): heavy, costly, laggy. No.
- **Rewriting the UI for the web** (HTML and JavaScript): two editors to maintain. No.
