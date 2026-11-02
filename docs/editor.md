# The structural editor

> Status: **built** (milestone 5, steps 5a to 5c), as decided with Dan on 2026-10-27 (he accepted every proposal below). What is not built yet, and the choices made while building, are at the end.

The editor works on the expression tree, never on text: keystrokes insert or wrap nodes around the insertion cursor. The file is written from the tree with the printer, so what the editor makes always parses back ([syntax.md](syntax.md)).

## The insertion cursor

- The cursor **selects a node of the tree** (shown underlined, a blue "L") and stands on its **left or right side** (the vertical bar).
- Inside a name, a number or a string, the cursor is a caret between characters.
- **Space** widens the selection to the parent node. **Insert** flips the side.
- **Arrow keys** move between operands: Left and Right go to the neighbouring leaf, Up and Down to the leaf above or below (a numerator, a denominator, a superscript).
- **Tab** jumps to the next placeholder (■); Shift+Tab to the previous one. With none left, Tab goes on to the next math region, and Shift+Tab to the one before (Dan, 2026-11-01); from the crosshair, Tab goes to the first region after it.
- **Backspace** deletes the character before the caret. On an empty leaf it leaves a placeholder; on a placeholder it removes the operator and keeps the other operand (rather than leaving an empty operator slot). **Delete** does the same forwards.
- **Escape** leaves the region.

## Typing

| Keys | Makes | Text |
|---|---|---|
| a letter or digit | a name or a number; a letter straight after a number starts an implicit product with a unit (`3m` → 3 m, and `20°C`, `°` counting as a letter; the Greek palette has a `°` button) | `3 m` |
| `+` `-` `*` `/` `^` | the operator, applied to the selection (on its right side: the selection is the left operand) | `a + ?` |
| `:` | definition (only at the top of a statement) | `x := ?` |
| `~` | global definition | `x ::= ?` |
| `=` | evaluation; after a definition's value, definition and evaluation | `x = ?` |
| `<` `>`, then `=` | ≤ and ≥; `!` then `=` gives ≠ | `<=` |
| `Ctrl+=` `Ctrl+3` `Ctrl+9` `Ctrl+0` | ==, ≠, ≤, ≥ | |
| `Ctrl+8` | cross product | |
| `(` | parentheses around the selection; `'` too | `(a)` |
| `(` straight after a name | a function call | `f(?)` |
| `[` | an index | `v[?]` |
| `.` after a name | a literal subscript | `M_max` |
| `\` | square root; `Ctrl+\` the nth root | `sqrt(?)` |
| `\|` | absolute value | `\|?\|` |
| `;` | a range | `1..?` |
| `,` | the next argument of a call, or the second value of a range | |
| `"` | a string (in an empty slot); in an empty spot of the page, a text region | |
| `@` | in an empty spot of the page, an X-Y plot (a working example, opened as its YAML; also Insert → X-Y plot and the Insert palette) | |
| `Ctrl+G` | the Greek letter for the character before the caret | `α` |
| `Ctrl+B`, `Ctrl+I` | the current name bold, or italic (plain); with neither, upright | `\bf F` |
| `Ctrl+Shift+K` | special text mode: every key goes into the name (`*`, `%`, `/`, spaces…) until `Ctrl+Shift+K` again; on an empty slot it starts a name. The status bar says when it is on; the toolbar's Style palette has it too | `` `θ*` `` |
| `.` with nothing after it | an empty subscript: a different variable that looks just like the plain one | `x_` |
| `Ctrl+/` | a division on the line, drawn ÷ (typing `÷` or `∕` does the same, in its own style) | `a÷b` |
| `Ctrl+M` | a matrix (rows and columns asked for); in a matrix, Insert adds rows below the cursor's cell and columns to its right, Delete removes them starting at it † | `[?, ?; ?, ?]` |
| `.` after a module's name | its functions: a list opens, narrowed as you type; ↑ ↓ pick, Enter or Tab put it in, Esc closes; the mouse resting on `bt.f` shows its signature and docstring ([symbolic.md](symbolic.md#python-functions-in-a-sheet)) | `bt.moment` |
| `Ctrl+6`, `Ctrl+1`, `Ctrl+-` | column, transpose, vectorize | |
| `$` / `Ctrl+Shift+4` | Σ over a range variable / Σ with limits | `sum(?, ?)` |
| `#` / `Ctrl+Shift+3` | Π, the same way | `prod(?, ?)` |
| `]` | in a definition's value: a program, or a new program line | |
| `{` | local assignment | `<-` |
| `}` | a program `if`; on an empty line the cursor starts in the value, drawn first | `? if ?` |
| a keyword, then Space | in an empty slot: `if`, `for`, `while`, `otherwise`, `return`, `break`, `continue`, `try` become their structure | |

**Multiplication style:** right-click on a multiplication for Dot, Thin space or As the sheet; Format → Multiplication sets the sheet's default ([syntax.md](syntax.md#multiplication-style)).

**Division style:** right-click on a division for Stacked, Inline ÷ or Inline /; going on the line adds the parentheses a fraction didn't need (`(x + 1)÷2`). The right-click menu is the innermost multiplication's or division's around the cursor ([syntax.md](syntax.md#division-style)).

**Operators on an empty spot** bring their empty operands: `*` gives `■·■`, `/` a fraction, `<` `■ < ■`, `!` `■!`, `[` `■[■]`, the cursor in the first slot (Tab to the next). `:` gives `■ := ■`, the cursor on the name.

**Programs start anywhere a value can:** `]`, `}`, `{` (after a name) and the program keywords also work outside a program, in a definition's value or on an empty spot. Since a program is always a definition's value, on an empty spot they make `■ := program`, with the name to fill in (an empty name is allowed while editing: `? := …` defines nothing and says "this definition needs a name"). The toolbar's keyword buttons do the same; on a program line that already says something, they add their line below it.


## Toolbar

For those who prefer buttons: every command is on a toolbar below the menus, grouped in palettes (Calculator, Comparisons, Matrix, Calculus, Programming, Symbolic and solving, Greek, Style, Insert). Hovering over a button shows its name and its keystroke, so the toolbar also teaches the keys. A button does exactly what its keystroke does (one table, `app/Commands.kt`, drives both), and never takes the focus from the sheet. A palette wraps to the next row as a whole, its divider after it, so a row may end with a divider but never starts with one.

**Choosing what shows** (Dan, 2026-10-28): View → Toolbar → **Show the toolbar** hides or shows it all; below it, a checkbox per palette shows only the ones ticked, for a thinner bar. Each palette wraps to the next row as a whole. Both are remembered between runs (Java preferences, node `pub/beermat`), as the user's choice rather than the sheet's.

## Zoom

View → Zoom: 50%, 75%, 100%, 125%, 150% or 200% (Dan's list), or `Ctrl`+mouse wheel to step through them; the status bar shows the level. The zoom is the user's, remembered between runs, not stored in the sheet (not per worksheet †: a sheet opened from someone else shouldn't change how big things look on this screen). A page wider than the window scrolls sideways; both scrollbars show.

## The window

It opens 1000 pixels wide (at 100% display scaling) and as tall as a page allows, centred and wholly inside the screen's work area, so every edge and corner (none behind the taskbar) can be dragged to resize it.

## Regions on the page

- The page is free-form: **click anywhere** to put the red **+** crosshair there, and type. The **arrow keys** move the crosshair a grid step (6 pt), and Enter moves it down a line (18 pt), pushing every region from there down with it, to make room (Dan, issue #5, 2026-11-02: a few Enters open up space). Backspace takes out the blank line above the crosshair, and Delete the one below it, moving everything from there down up a line; where a region is in that line, nothing moves (and there's no beep, Dan asked). With regions selected, the arrows move them instead, a step at a time (Dan, 2026-11-01). Regions snap to a grid (6 pt) and can be **dragged**.
- **Selecting regions:** drag on empty paper for a rectangle, which works as in AutoCAD (Dan's choice): dragged **left to right** (a solid blue rectangle) it selects the regions wholly inside it; dragged **right to left** (a dashed green one) it selects every region it touches. Selected regions show in a dashed blue box. `Ctrl+click` adds a region to the selection or takes it out. Dragging any selected region moves them all, keeping their layout; `Delete` removes them (one undo step); `Esc` or a click elsewhere ends the selection. Page breaks can be selected and moved too; an area's lines can't (a click on one folds the area).
- **A new sheet:** started without a file, the app opens a blank sheet, "Untitled", saved where the user chooses at the first `Ctrl+S`. File → New (`Ctrl+N`), Open… (`Ctrl+O`), Save As…; closing, opening or starting another sheet with unsaved changes asks whether to save them.
- Positions are stored in the file (`x=`, `y=`, in points). **Evaluation order is top to bottom, then left to right**, counting a math region from the baseline of its first line, not its top (so a fraction reaching up beside a definition comes after it; baselines within 3 points are one line; 2026-10-29), and the file is saved in that order ([file-format.md](file-format.md)).
- **Resizing pictures and plots** (2026-10-29): select one (a click selects a picture; a plot by rectangle or `Ctrl+click`) and drag the handle on its bottom-right corner; a picture keeps its shape unless Shift is held. The size is written as `w` and `h`.
- **Overlapping regions** are flagged; "Separate regions" (`Ctrl+Shift+S`) pushes them apart vertically.
- **Pages.** The page is A4 by default (frontmatter `page: A4 | letter`), with margins, a right-margin line and page breaks:
  - a region that would be cut by a page boundary moves to the next page, and everything below it moves down with it (only on screen and in print: the file keeps its own coordinates);
  - a region taller than a page (a long program) breaks between its lines instead;
  - the user can insert a page break (`Ctrl+Enter`), stored as `<!-- pagebreak y=… -->`.
- **Areas** (collapsible areas) hide a part of the sheet: `<!-- #a1 area y=… title="…" collapsed -->` … `<!-- /area -->`. Everything between the markers is evaluated as usual; a collapsed area shows as one line with a ▸, and what is below it moves up. Clicking the line expands it.

## Result format

Built on 2026-10-29: the Result Format dialog. **Double-click a result** (or Format → Result… on the statement being edited) to set:

- **Rounding:** decimal places or significant figures, how many, and (with decimals) whether to show trailing zeros;
- **Exponent from 10^n:** where numbers turn to an exponent ([results.md](results.md));
- **Unit:** the unit placeholder, in linear syntax (`kN*m`); blank for automatic SI units.

The rounding is stored on the region, as attributes that differ from the sheet's (`{#r1 significant=3}`, [file-format.md](file-format.md#attributes)), so it applies to every statement in that region; the unit goes into that statement. **Sheet's format** removes the region's own rounding and keeps the unit. One undo step. Not yet: setting the sheet's own default from the dialog (a "Set as default"); it is the frontmatter's `result:` for now.

## Printing

Built on 2026-10-29.

- **File → Print…** (`Ctrl+P`) shows the system's print dialog and prints the pages as they are on screen, on paper the size of the sheet's pages (A4 or letter), each page drawn at 300 dpi. The sheet keeps its own margins, so the printer is given the whole page.
- **File → Export to PDF…** writes the pages to a PDF, beside the sheet and with its name by default. It is vector, so it is sharp at any zoom and small, from about 70 KB a page. The PDF's title is the sheet's name.
- What prints is what the page shows, without the screen's marks: no crosshair, cursor, selection, right-margin line, page-break lines or overlap flags. Collapsed areas stay collapsed; area lines print †. Errors print in red.
- **How:** `PagePainter.paintPage` draws one page; for the PDF it draws onto Skia's SVG canvas with text turned into outlines, and `Pdf` turns that SVG (paths, rectangles, circles, clips, opacity) into PDF operators and writes the file itself, with no library. So the PDF looks exactly like the screen, fonts included.
- **Selectable text** (2026-10-29): over the outlines, each page carries its text again, invisible (render mode 3), each character where it is drawn and as wide as its advance, as scanned documents carry their recognised text. Its fonts are Type 3 fonts whose glyphs draw nothing, with `ToUnicode` maps, so selecting, copying and searching give the characters drawn (𝑀, α, kN). No font is embedded, and the page looks the same. A fraction or a power reads as its parts, line by line.
- Without a window: `beermat-app sheet.bbm --pdf out.pdf`. The `beermat` command line can't make PDFs yet, since it has no drawing engine; nor can the MCP `export` tool.
- Tests read each example's PDF back with PDFBox (a test-only dependency) and check the pages; `./gradlew :app:test --tests '*PdfTest*' -Pbeermat.pdfOut=dir` keeps the PDFs and a PNG of every page, to look at.

## Behaviour

- **Automatic recalculation** after every edit; `F9` recalculates when automatic recalculation is off (`Ctrl+F9` toggles it, Tools menu).
- **Saving** writes the tree back to the file (`Ctrl+S`); results are cached as usual.
- **Changes on disk** (Claude through MCP, or another editor) reload the sheet when there are no unsaved changes; otherwise the app asks.
- **Copy and paste.** A region copies as its linear text, so it can go to Claude or a chat and back; pasting linear text makes a region.
- **Undo** (`Ctrl+Z`, `Ctrl+Y`) per keystroke, grouping consecutive characters of one name.
- **Bugs cost one action, never the sheet.** Every edit is checked before it is applied (the statement must still be a valid tree that can be written, with the cursor on one of its nodes; otherwise the key does nothing). And every key, click and command runs guarded: if one throws, the sheet goes back to just before it, the status bar says so in red, and the details go to `~/.beermat/errors.log`. A test (`FuzzTest`) types random keys, clicks and commands on real sheets and checks that nothing throws and the sheet still draws; `BEERMAT_FUZZ_RUNS=3000` runs a longer hunt.

## Steps

- **5a:** editing one math region with the insertion cursor and the core keys, written to the file.
- **5b:** regions on the page: crosshair, dragging, text regions, pages and page breaks, areas.
- **5c:** programs, matrices, and the rarer keys.

## Not built yet

- `Ctrl+F9` (automatic recalculation off): recalculation is always automatic for now; `F9` recalculates.
- `Ctrl+8` (cross product): the engine has no cross product yet.
- `Ctrl+.` (symbolic evaluation): milestone 10.
- Inline math in text regions.

## Choices made while building (approved by Dan, 2026-10-29)

- **New math is one statement per region.** Typing at the crosshair makes a new region; Enter leaves it and puts the crosshair below. Regions loaded from a file keep their several statements, and each statement is edited on its own.
- **A region is dragged from anywhere on it** (not only by its border); a click without movement edits it.
- **Text regions are edited as their Markdown**, in a plain text field over the region, and drawn formatted when left (Escape or Ctrl+Enter, or clicking elsewhere). Plots are edited the same way (their YAML), and so is a statement that doesn't parse.
- **The first save places every region**: regions without a position get the one they were drawn at, and text gains its `<!-- #t1 x=… y=… -->` marker. Ids are assigned then too.
- **`!` then `=`** is ≠ inside an expression, but at the top of a statement it is an evaluation (`5! = ?`).
- **`]` on a definition makes a program with two empty lines**.
- **Up and Down** move to the nearest leaf above or below, by position on the page.
- The page is drawn at 12 pt math and 11 pt text (Segoe UI, or the system's sans-serif).
