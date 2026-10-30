# Tables

> Status: **steps 1 to 3 built**: the fence, evaluation, display, export, MCP (2026-10-28); the grid editor (2026-10-29). Not yet: the formatting attributes, ✓/✗ checks, per-column number formats, expressions drawn as math in cells, `.mcdx` import (waiting on samples). Agreed with Dan: our own table region, no embedded Excel.

A table region shows inputs and results as a formatted grid: the neat input/output presentation that embedded spreadsheets give worksheets, without a spreadsheet inside. It is not a spreadsheet: there are no A1 cell formulas. A computed cell is a Beermat expression.

## In the file

A ```` ```table ```` fence holding a Markdown pipe table, so it renders as a table on GitHub and reads naturally to people and to AI tools:

````markdown
```table {#tb1 x=40 y=300 name=Loads}
| Case        | q [kN/m] | L [m] | M [kN*m]        |
|-------------|---------:|------:|----------------:|
| Dead        | 4.5      | 6     | = q*L^2/8       |
| Imposed     | 7.5      | 6     | = q*L^2/8       |
| Total       | = sum(q) |       | = sum(M)        |
```
````

- **Header row**: column names, each with an optional unit in brackets (`q [kN/m]`). A column's name is a valid Beermat name, or a label (`Case`) when it is text.
- **Cells**: a number (in the column's unit), text, or an expression starting with `=`. In a row, an expression sees that row's values by column name (`q*L^2/8`); in the last rows it may use a whole column as a vector (`sum(q)`).
- **Alignment** from the Markdown separator row (`---:` right, `:---:` centre).
- **Results are cached** like statements: `#= …` can't go inside a Markdown table, so a computed cell's last value is stored as `= q*L^2/8 → 16.875`, re-evaluated and rewritten on save (the `→ value` part is output, like `#=`).

## Cell references

Decided with Dan on 2026-10-29 (option A), so Excel's world moves into tables whole:

- **In a cell's expression, a cell has its Excel name:** `= B3*B4`, `= sum(B2:B6)`, `$B$5` too. **Row 1 is the header row**, as in Excel, so the first body row is row 2, and column A is the first column. `= A1` is the first column's label, as text.
- Column names keep working beside them: in a row, a column's name is that row's value (`= q*L^2/8`); in the cell's own column, the column's other rows (`= sum(q)`).
- Inside a table, a name such as `B5` always means the cell, even where the sheet defines a variable of that name.
- Cells are evaluated in the order their references need, not row by row; a cell that comes back to itself is a **circular reference**, reported with its cell name.
- **Out to the sheet:** a named table (`name=Pipe`) defines, besides its columns (`q_Pipe`), its whole grid as `Pipe`, header as row 1, and **`cells(Pipe, "B2:B21")`** takes a range from it (one cell's value, or a matrix): output ranges, written as a function so the math syntax needs nothing new.
- **Inserting or deleting** rows and columns in the grid editor rewrites the references, as Excel does; a reference to a deleted cell becomes `#REF`, an error. (References inside `cells(Pipe, "…")` strings in the sheet are not rewritten.)

## In the sheet

- With `name=Loads`, the table **defines** each named column as a vector with the table's name as subscript: `q_Loads`, `M_Loads`, for the statements below it. Every row is in the vector, a totals row too (keep totals in their own table if that matters). Bare column names are not defined, to avoid clashes (decided by Claude, for review).
- A table **without a name** is display only: an output table whose cells are expressions on the sheet's variables (`= M_max`, `= σ_all`).
- Tables evaluate in sheet order like any region.

## Formatting ("embellishment")

Attributes on the fence, nothing hidden in the table text:

- `header=bold|shaded`, `borders=all|outer|rows|none`, `stripes` (alternate row shading), `width=…`;
- per column, in the header: `q [kN/m]{.3}` for 3 decimals, `{sig=3}`, `{%}`;
- cell highlight for checks: an expression cell that is a comparison (`= σ <= σ_all`) shows ✓ or ✗, green or red.

## Editing

In the app, a table region edits as a grid: Tab and arrows between cells, Enter for a new row, typing `=` starts an expression (edited with the math editor in the cell, so expressions look like math), right-click for insert/delete row and column, and paste from Excel (tab-separated text) fills cells. The grid editor is the one sizeable piece of work. [GridCraft](https://github.com/storytold/gridcraft) (Dan's ArtCraft project, MIT or Apache-2.0, so compatible) is a full spreadsheet in Rust on egui: its code can't run inside our Compose app, but its behaviour, keystrokes, table styles and `.xlsx` reading are the reference to follow (with attribution where we port logic).

**Built on 2026-10-29** (`app/page/TableEditor`, the edits in `format/TableEdits`):

- **A click on a cell** puts the cell cursor there (a blue outline); a click on the table's problems line opens its Markdown.
- **Moving:** arrows; Tab and Shift+Tab along the row and on to the next; Enter down. Enter or Tab past the last row adds a row. Home and End go to the row's ends.
- **Typing replaces the cell**, as in a spreadsheet; **F2 or a double-click** edits what it holds, with a caret (arrows, Home, End). Enter, Tab or an arrow writes it and moves; Escape drops the typing, and a second Escape leaves the table. Delete or Backspace clears a cell. Each change is one undo step.
- **A cell holds its linear text:** a number, a word, or `= expression`; the header cell is the column's name and unit (`q [kN/m]`).
- **Right-click:** insert a row above or below, delete a row, insert a column left or right (named `c1`, `c2` …, right-aligned), delete a column, or edit the table as its Markdown.
- **Paste** (`Ctrl+V`) of tab-separated text, as Excel and other spreadsheets copy cells, fills cells from the cursor, adding rows and columns as needed; `Ctrl+C` copies the cell's text.
- Not yet: expressions drawn as math while typing (the math editor in the cell), selecting several cells, dragging column widths.

## Import from `.mcdx` and `.xmcd`

To confirm with the samples. What we expect:

- Prime keeps an Excel component's workbook inside the `.mcdx` package (a zip), with the mapping of **inputs** (sheet variables → cell ranges) and **outputs** (cell ranges → sheet variables).
- We read the workbook ourselves (an `.xlsx` is zipped XML; no library needed for values and formulas) and make a table:
  - input cells become cells bound to the variables (`= q`);
  - output ranges become columns, with their last values;
  - simple formulas (arithmetic, SUM, AVERAGE, MIN, MAX, IF, references within the row or column) translate to expressions;
  - anything else keeps its last value, with an import warning naming the cell.
- Formatting that matters (number formats, bold, fills, borders) maps to the attributes above where it can.
- Excel components (OLE objects in `.xmcd`): **built** on 2026-10-29: the workbook inside is read with POI and becomes a table as above, with input cells bound to the sheet's values ([xmcd.md](xmcd.md)). Formulas using other rows' cells keep their last values for now.

## Steps

1. The `table` fence in the format module (read, write, round trip), evaluated in the engine through the format module, with `--png` rendering. Tests.
2. Display in the app (Skia grid, formats, ✓/✗), export to Markdown (it already is one) and LaTeX (`tabular`).
3. The grid editor in the app: **built** 2026-10-29 (below).
4. `.mcdx` import of Excel components (after the samples).
