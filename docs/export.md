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

<a id="latex-input"></a>**LaTeX input** goes the other way (2026-11-11, [editor.md](editor.md#latex)): `LatexInput` reads LaTeX math into a statement. It turns the LaTeX into the linear syntax and parses that; braces that only group go through a marker function taken out afterwards, so `\frac{a+b}{2}` adds no parentheses of its own, and `Printer.canonical` adds those the structure needs. Its test: every statement in a list of 50 (definitions, units, matrices, indices, sums, integrals, derivatives, symbolic keywords, styles) goes out through `Latex` and comes back as the same tree. What doesn't come back: programs (an error); an index by one name (`v_{i}` reads as the name `v_i`, as most LaTeX means it); a bare `x = y` constraint (read as a definition); and redundant parentheses that the export leaves out (`e^{-x}` for `e^(-x)`).

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

## Word

> Status: **built** (2026-11-03), milestone 25 (Dan asked on 2026-10-30; brought into 1.0 on 2026-11-03).

File → Export → Word writes a `.docx` of the whole worksheet, for drafting reports and papers:

- **Text** with its Markdown formatting: headings, bold, italic, lists, links.
- **Math** as native Word equations (Office Math, OMML), built from the expression tree as the MathML export is, so they can be edited in Word. Results are written after their `=` as on the page, in their display units.
- **Plots and pictures** as images (PNG at print resolution), each at its size on the page.
- Regions in reading order, one paragraph each; side-by-side regions follow one another (Word has no free-form page).

Written directly (a `.docx` is a zip of XML files), with no other program needed. `beermat export --to docx` does the same from the command line.

How it is built:

- `Docx` in the export module (common code, so the web version exports too): the regions in reading order (`Regions.of`), collapsed areas left out as when printing, page breaks kept, the page A4 or letter with ¾ inch margins as the sheet's. Text: headings (Word's Heading 1–3, so the navigation pane and a table of contents work), paragraphs, bulleted and numbered lists (each numbered list from 1), quotes, code, **bold**, *italic*, `code` and links. A `_` inside a word (`M_max`) is not emphasis.
- `Omml`, beside `MathMl` and built the same way from the tree: fractions, powers, subscripts, roots, Σ and ∫ with their limits, matrices in brackets, programs (a left-aligned column with the bar), `≔` as one symbol (Word spaces `:` and `=` apart otherwise). Variables italic, units and built-in functions upright, as on the page; every run in Cambria Math. An error goes in a red paragraph after its equation (the style "Calculation Error").
- Tables are Word tables (style "Calculation Table"), the header row repeated on each page, computed cells as their values.
- Plots and pictures come from a painter the shell passes in (`WordPictures` in the ui module, for the desktop and the web): a plot drawn by `PlotPainter` as on the page, at 200 dpi, with a small margin for tick labels; a PNG, JPEG or GIF picture as it is; SVG and WebP drawn to a PNG. Each at its size on the page, scaled down to the text's width if wider.
- The zip is our own (`Zip`, entries stored, CRC-32), as the web version has no `java.util.zip`.
- The command line has no painter (it has no Skia), so there embedded PNG, JPEG and GIF pictures go in, and a plot is a line saying the app's Export to Word draws it.
- Checked by `DocxTest` (every part well-formed, CRCs read back by the JDK's zip reader, equations, text, lists, tables, page breaks, collapsed areas, pictures) and `WordPicturesTest`, and by hand (2026-11-03) in Word 16 itself, run hidden: the examples open, every statement is an equation Word knows (`OMaths`), and the PDFs Word made from them look as on the page.
- File → Export to Word… on the desktop, a Word button on the web version's bar.
