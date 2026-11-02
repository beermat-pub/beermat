# Worksheet file format (`.bbm`)

> Status: **draft**.

## Extension

- **`.bbm`** ("Back of a Beer Mat") is the default for new worksheets.
- **`.bbm.md`** is also accepted, and Save keeps whichever extension the file already has. Use it when a worksheet should render as Markdown with no setup.
- The contents are identical either way. To get Markdown preview for `.bbm` files:
  - **VS Code:** set `"files.associations": { "*.bbm": "markdown" }`.
  - **GitHub:** `*.bbm linguist-language=Markdown` in `.gitattributes` gives Markdown highlighting, but GitHub only *renders* files by their extension, so a `.bbm` file shows as highlighted source. Use `.bbm.md` where GitHub rendering matters.
- `.bbm` was once used by Deluxe Paint (Amiga/DOS) for brush bitmaps. That use is long obsolete, so there's no practical clash.

## Goals

1. **It reads as a document in any Markdown viewer** (natively for `.bbm.md`; for `.bbm`, once the viewer associates the extension with Markdown). On GitHub or in VS Code preview, a worksheet looks like a tidy calculation report.
2. **It is diffable.** One statement per line, stable region IDs, deterministic ordering and formatting.
3. **It can be written by hand or by an LLM.** Positions and IDs are optional; the app fills them in on the next save.
4. **It is lossless.** Everything the app shows is stored here (except things it can recompute).

## Overview

````markdown
---
beermat: 1
title: Beam check
units: SI
origin: 0
result:
  decimals: 3
---

# Beam check

<!-- #t1 x=40 y=40 w=500 -->
Simply supported beam, uniform load **q** over span **L**.

```calc {#r1 x=40 y=100}
L := 6 m
q := 12 kN/m
```

```calc {#r2 x=40 y=160}
M_max := q*L^2/8 = ? kN*m
```

```plot {#p1 x=320 y=100 w=300 h=200}
x: 0 m .. L
traces:
  - y: M(x)
    unit: kN*m
```
````

## Encoding

- UTF-8, LF line endings, final newline. On reading, CRLF line endings and a leading byte-order mark are accepted; Beermat writes neither.
- The app writes the file in a canonical form: ordering, attribute order and indentation are deterministic, so re-saving an unchanged worksheet produces an identical file.

## Frontmatter

YAML between `---` lines at the top. All keys are optional.

| Key | Default | Meaning |
|---|---|---|
| `beermat` | `1` | Format version |
| `title` | file name | Worksheet title |
| `units` | `SI` | Unit system for displaying results: `SI`, `USCS`, `CGS`, `MKS`, or `none` |
| `angle` | `none` | `dimension` makes angles a dimension, `rad` its unit ([units.md](units.md#angle-and-currency-as-dimensions)); Format → Units |
| `angle_display` | `deg` | With `angle: dimension`, an angle no unit was asked for shows in `deg` or `rad` |
| `single_page_width` | `false` | `true`: printing and PDF leave out what is past the right margin, rather than printing it on extra pages ([editor.md](editor.md#printing)) |
| `currency` | none | A currency dimension whose unit has this name (`AUD`, `€`); Format → Units |
| `angle` | `dimensionless` | `dimension` makes angle a dimension of its own ([units.md](units.md#angle-and-currency-as-dimensions)) |
| `angle_display` | `deg` | With `angle: dimension`: how angle results show, `deg` or `rad` |
| `currency` | — | A currency name (`AUD`) turns on the currency dimension, with that unit |
| `result.prefixes` | `true` | Engineering prefixes on results with no unit asked for (`54 kN·m`); `false` turns them off |
| `origin` | `0` | Array ORIGIN |
| `result.decimals` | `3` | Decimal places shown (the default way of rounding results) |
| `result.significant` | — | Significant figures shown instead of decimal places; when set, `result.decimals` is ignored |
| `result.strict` | `false` | With `significant`: also round digits left of the decimal point (1234.567 to 3 figures: `1230`, not `1235`) |
| `result.trailing_zeros` | `false` | With `decimals`: pad to the full number of places. (With `significant`, trailing zeros are always shown: `2.50`.) |
| `result.exponential_threshold` | `6` | Numbers of 10^n or more, or below 10^-n, are shown with an exponent |
| `result.zero_threshold` | `15` | Values below 10^-n display as 0 |
| `result.complex_threshold` | `10` | A real or imaginary part smaller than 10^-n of the other part is hidden |
| `tolerance` | `1e-3` | Initial value of `TOL`, used by solvers and by comparisons (`==`, `<`, …) |
| `page` | `A4` | Page size: `A4` or `letter`, with 54 pt (¾ in) margins ([editor.md](editor.md)) |
| `title` | none | The sheet's title (an imported sheet gets its file name); `{title}` in a header or footer |
| `header`, `footer` | none | Text in the top and bottom margins of every page, on screen and in print: `header: Calculation 12` (centred), or `header: {left: "{title}", centre: …, right: "Page {page} of {pages}"}`. Fields: `{page}`, `{pages}`, `{title}`, `{date}` (today, as 2026-10-29). No pictures or fonts |
| `multiplication` | `dot` | How `*` is drawn: `dot` (a·b) or `space` (a b); one operator can differ ([syntax.md](syntax.md#multiplication-style)) |
Custom units are not a setting: they are ordinary definitions in the sheet (`kip := 1000 lbf`), made or redefined anywhere and usable below where they are defined, like any variable (Dan, 2026-10-29; [units.md](units.md#custom-units)).

## Regions

Every region carries an **attribute set** in braces: `{#id key=value …}`, inspired by Pandoc attributes.

| Attribute | Meaning |
|---|---|
| `#id` | Stable region ID (`r…` for math, `t…` for text, `p…` for plots, `i…` for images). Assigned by the app if missing |
| `x`, `y` | Position of the region's top-left corner, in points (1/72 in) from the top-left of the page area |
| `w`, `h` | Size (text, plots, images). Math regions size themselves |
| `disabled` | Region is not evaluated ("Disable evaluation") |
| `decimals=…`, `significant=…`, `strict`, … | Per-region overrides of the `result.*` settings |
| `highlight=…` | Background colour |

### Math regions: ```` ```calc ````

A fenced block whose info string starts with `calc`. (Not `math`, because GitHub renders ```` ```math ```` fences as LaTeX.)

The content is one or more statements in the [linear syntax](syntax.md), one per line (program blocks span indented lines).

A line indented further than the statement above it continues that statement: that is how a program's lines belong to its `f(x) :=`. A blank line ends a statement. A program's annotation (usually an error) goes on its first line.

**A fence is one *block*.** Its statements are stacked vertically from the block's position, and the block moves as a unit. When the user drags one statement away in the editor, the app splits the block into two fences. So the most compact form is preferred: consecutive definitions in one column live in one fence, and anything positioned independently gets its own fence.

### Text regions

Plain Markdown, introduced by an HTML comment carrying the attributes:

```markdown
<!-- #t1 x=40 y=40 w=500 -->
Any **Markdown** here, until the next region marker, fence or heading.
```

The HTML comment is invisible on GitHub, so the text reads naturally.

- **Headings** (`#`, `##`, …) are text regions too. A heading with no marker gets full width and auto-flow placement.
- Inline math in text uses `` `calc: x = ?` `` (Dan, 2026-10-29; built in 1.x). `.xmcd` files can embed math regions in text.

### Plot regions: ```` ```plot ````

The body is YAML; the expressions inside it use the linear syntax.

```plot {#p1 x=320 y=100 w=300 h=200}
x: 0 m .. L                # x-axis variable or range
x_unit: m
traces:
  - y: M(x)
    unit: kN*m
    label: Bending moment
    style: { line: solid, color: auto, marker: none }
  - y: V(x)
    unit: kN
    axis: right
axes:
  x: { min: auto, max: auto, grid: true, label: "x [m]" }
  y: { min: auto, max: auto, grid: true }
```

The plot schema and how plots are evaluated: [plots.md](plots.md).

### Images

**One file, images included** (Dan, 2026-10-28: a worksheet is one file to send or attach, as an `.xmcd` was). An image is stored in the file as text, a base64 `data:` URI, as HTML pages embed pictures. So the region reads cleanly, the region holds a Markdown reference and the data goes at the end of the file, after everything else:

```markdown
<!-- #i1 x=40 y=400 w=200 h=150 -->
![Beam diagram][i1]

…the rest of the sheet…

[i1]: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA…
```

- The reference is the region's id. The app writes the data as one long line per image, so the body of the sheet stays readable and a diff only shows the image's line when the image changes.
- PNG and JPEG keep their bytes; SVG is stored the same way (base64), so no Markdown reader mistakes it for markup.
- A hand-written sheet may link to a file instead (`![Beam diagram](beam.png)`, relative to the sheet); the app shows it, and offers to embed it.
- The cost: base64 is a third bigger than the image, so the app warns above a few megabytes, and some Markdown viewers (GitHub's among them, †) don't show `data:` images, only their alt text.

**Built on 2026-10-29:**

- **In the app:** Insert → Picture… (PNG, JPEG, GIF, WebP or SVG; it asks first above 3 MB), or paste a picture copied from another program (`Ctrl+V`). It goes at the crosshair, at its natural size (96 pixels to the inch) or narrowed to fit the page, with `w` and `h` written. A click selects it, to drag or delete (`Delete`). A file that can't be read shows as a grey box with its alt text.
- **Reading:** a marked region whose only content is `![alt][ref]` (with a `[ref]: data:…` line anywhere) or `![alt](path)` is a picture region (`ImageRegion`); a `data:` definition no picture refers to stays in the text. Writing puts the data lines last, in the order of the pictures.
- **Printing and PDF** draw pictures as on screen; the PDF embeds each one once, with its transparency.
- **The MCP tools** never send the data to the assistant: `read_worksheet` shows `[i1]: data:image/png;base64,… (12 KB, not shown)`, and when that line comes back in `update_region` the data is put back.
- **Resizing:** a selected picture has a handle on its bottom-right corner; dragging it resizes the picture, keeping its shape (Shift: any shape). Plots resize the same way.
- **Not yet:** offering to embed a linked file; pictures in the LaTeX export; pictures from `.xmcd` files.

### Page breaks and areas

```markdown
<!-- pagebreak y=700 -->

<!-- #a1 area y=720 title="Load cases" collapsed -->
… regions …
<!-- /area y=900 -->
```

- A page break starts a new page at its `y` (`Ctrl+Enter` in the editor).
- An area (a collapsible part of the sheet) holds the regions between its two markers. They are evaluated as usual; `collapsed` hides them, and what is below moves up ([editor.md](editor.md)).

## Ordering and positions

- **Positions are authoritative.** Evaluation order is by the region's top edge, then its left edge (verify how ties work).
- **The file is always saved in evaluation order**, so reading the file top to bottom matches how the sheet calculates.
- **Missing positions auto-flow.** A region without `x`/`y` is placed at the left margin, below the previous region plus a gap. A hand-written file with no coordinates at all becomes a single tidy column.
- **Hand-edited files whose order and positions disagree:** positions win, a warning is shown, and the file is re-sorted on the next save.
- **Until the layout engine exists (milestone 4)**, the CLI evaluates regions in file order: it cannot yet tell where an auto-flowed region ends up.

## Results in the file

**Decision: results are cached in the file**, so a worksheet reads as a finished calculation on GitHub and to an AI that reads it without running Beermat (`.xmcd` files store results too). The cache is never trusted when anything it could depend on has changed.

- **In the app**, editing a region invalidates the cached results of every region that depends on it, through the same dependency graph that drives incremental recalculation.
- **In the file**, edits made outside Beermat (by hand, by Claude, by a merge) must also be caught:
  - A cached result follows its statement as a trailing annotation, in the unit it is displayed in:
    ```calc
    M_max := q*L^2/8 = ? kN*m   #= 54 kN*m
    x = ?                       #= 3 m
    ```
    The `?` and the sticky unit stay as they are, so the statement itself never changes. That way an automatically chosen unit can't be mistaken for one the user typed.
  - The frontmatter holds `results: <hash>`, a hash of the file's inputs: the whole file with the `#=` annotations and this line removed. If the hash doesn't match on load, **all** cached results are treated as stale and recomputed. That over-invalidates, but it is simple and always safe, and evaluation is cheap.
  - The hash is the first 16 hex digits of the SHA-256 of the UTF-8 file text, after line endings are normalised to LF, the `results:` line is removed, and every `#=`/`#!` annotation is removed together with the spaces before it.
  - `beermat check` reports stale results (with a failing exit code, for CI or a pre-commit hook), and `beermat eval --write` refreshes them. A file with no `results:` key and no annotations has no cache, so there is nothing to be stale.
- **Errors are cached too**, as `#! message`, so a reader sees why a line has no result:
  ```calc
  y := x + 2 m   #! units do not match: L·M vs L
  ```
- Annotations within one fence are aligned in a column, three spaces after the longest statement that has one.
- `#=` and `#!` are only annotations at the end of a line in a `calc` fence (outside a string). They are not part of the linear syntax, and a statement is never changed when its results are written.

## Things not representable yet

To be designed as features arrive:

- areas (collapsible, lockable)
- solve blocks (`Given` … `Find`)
- references to other worksheets
- headers and footers
- per-region fonts and styles

Likely shape: Pandoc-style fenced divs (`::: area {#a1 collapsed}` … `:::`).
