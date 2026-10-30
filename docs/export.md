# Export

> Status: **draft**, describing milestone 7. Plots and PDF are not exported yet.

All export code is in the UI-free `export` module.

## One statement: LaTeX and MathML

In the app, right-click a statement: **Copy as LaTeX** (wrapped in `$$…$$`), **Copy as MathML**, or **Copy as text** (the linear syntax).

- **LaTeX** needs only plain LaTeX math with `amssymb` (for `\blacksquare`, the empty placeholder), and renders in Markdown viewers that support math: `M_{\mathrm{max}} := \frac{q \cdot L^{2}}{8} = 54\ \mathrm{kN} \cdot \mathrm{m}`.
- **MathML** is presentation MathML in a `<math display="block">` element. Pasting it into Word as text turns it into an editable equation. (Putting it on the clipboard in Word's own HTML format would let a plain Ctrl+V do the same; that is for later.)

Conventions:

- Variables are italic, as in the app. Multi-letter variable names are `\mathit{…}` in LaTeX and `mathvariant="italic"` in MathML. Built-in functions are upright: `\sin`, `\mathrm{rows}`.
- **Units are upright.** A name counts as a unit if it is a built-in unit *and the sheet doesn't define it*: in a sheet with `L := 6 m`, `L` is a length, not litres. In a result's unit part, every name is a unit.
- Literal subscripts are upright: `M_{\mathrm{max}}`. Array indices are not: `v_{i}`.
- The structure follows the 2D view ([layout.md](layout.md)): fractions, radicals, `\sum` with limits, `bmatrix` (square brackets), `\mathsf{T}` for the transpose, `\overrightarrow` for `vec`, and programs as a left-barred `array` with bold keywords.

## A whole worksheet: Markdown and LaTeX

Printing and PDF are in the app (File → Print, Export to PDF; [editor.md](editor.md#printing)).

`beermat export sheet.bbm [--to md|latex] [--out file]` (see [cli.md](cli.md)):

- **Markdown** (`--to md`, the default): the text as written, without region markers; each statement as a `$$…$$` block with its result; errors as an italic line under the equation; plots as a placeholder line (for now: see below).
- **LaTeX** (`--to latex`): a standalone `article` document; headings become `\section*` and so on, `**bold**` and `*italic*` become `\textbf` and `\textit`, each statement is a `\[ … \]` display.

## Plots and pictures

Decided by Dan on 2026-10-29: an exported sheet is **one file**, with no folder of images beside it, as the `.bbm` keeps its pictures inside ([file-format.md](file-format.md#images)).

- **In Markdown**, a plot is an SVG drawn by the same painter as the page (Skia's SVG canvas, as the PDF uses), embedded as a `data:image/svg+xml;base64,…` image; pictures go in as they are stored. Being vector, plots stay sharp at any size. (GitHub's viewer doesn't show `data:` images †, as noted for `.bbm` files.)
- **In LaTeX**, the same SVG can't be included by plain LaTeX, so plots there wait for a later step (a PDF figure beside the file is the usual way).
- **Copy as image**: right-click a plot (or a picture, or a selection of regions) → Copy as image puts a PNG on the clipboard, at twice screen resolution, to paste into Word, an email or a chat. Copying math stays LaTeX and MathML, which paste as equations.

**Built on 2026-10-29:** File → Export to Markdown… in the app, and the MCP `export` tool when the app serves it, embed each plot as its SVG (`PlotImages`, text as outlines, on white). The `beermat` command line has no drawing engine, so its Markdown keeps the placeholder line for plots. **Copy as image** is on the right-click menu of a plot or a picture (a selection of several regions is still to come).
