# Changelog

Each version's section here becomes the notes of its release on GitHub ([docs/release.md](docs/release.md)).

## 1.0.0-dev3 (not released yet)

- **Making room on the page**: with the crosshair on the page, Enter pushes everything below it down a line; Backspace and Delete take a blank line out above or below it.
- **The web version** says what needs the desktop app, and where to get it, rather than naming what's inside it.
- **The manual**: the logo leads back to beermat.pub.
- **Big arrays are tables**: an array with more than 20 rows or columns, a result or one written in the sheet, shows 20 at a time with row and column numbers; the mouse wheel over it scrolls it. **Fixed**: `.xmcd` sheets with big input tables stopped the desktop app ("Can't represent a width … in Constraints").
- **Fixed**: regions past the page's right edge couldn't be scrolled to.
- **The system's own file dialogs**: on Windows, Open, Save As, Insert Picture and Export now use the modern Explorer dialog.
- **A word and a space make text**: typing a word and then a space at the crosshair turns the new region into a text region, and the typing goes on there.

## 1.0.0-dev2 (developer preview)

The third preview: everything a first public version needs, put out the way 1.0 will be, with the [download page](https://beermat.pub/download), the [manual](https://beermat.pub/docs/) and [Beermat in the browser](https://beermat.pub/try). Please try it and report what you find.

- **The `beermat` command comes with the desktop app**: no separate download and no Java to install. On Windows the installer adds it to the PATH; on macOS and Linux, Tools → Install the beermat command.
- **Fixed**: the arrow keys moved the crosshair (and selected regions) without the page showing it until the next edit, in the browser and on the desktop.
- **Results with engineering prefixes**: with no unit asked for, `54 kN·m` rather than `54000 N·m`, `210 GPa`, `12 mm`. A sheet that prefers the plain units sets `result: {prefixes: false}`.
- **Divisions on the line**: `Ctrl+/` (or typing `÷`) gives a ÷ b, and right-click on any division chooses Stacked, Inline ÷ or Inline /. Imported `.xmcd` sheets keep their inline divisions.
- **Angles and money as dimensions**, for sheets that want them checked: `angle: dimension` makes `rad` a unit (a rotation stiffness is `kN·m/rad`, not a moment; `asin` gives degrees), and `currency: AUD` makes money a unit (`120 AUD/m^3 · 4 m^3 = 480 AUD`). Both are off by default; Format → Units turns them on.
- **A global (≡) is defined once**: redefining it with `:=`, or a second `≡` of the same name, is now an error, so a global means the same everywhere in the sheet.
- **The manual** has begun, at [beermat.pub/docs](https://beermat.pub/docs/): installing, your first worksheet, the command line. Help → Manual or F1 opens it, and so does the web version's Manual button.
- **The website** has a [download page](https://beermat.pub/download) for the newest version, and links to the web version and the manual. What's coming is in [ROADMAP.md](ROADMAP.md).
- **The web version**: the name in the top bar and a beermat.pub button lead back to the website; "Get the desktop app" opens the download page; the browser tab has Beermat's icon.

## 1.0.0-dev1 (developer preview)

The second preview, and the first with **Beermat in the browser**: [beermat.pub/try](https://beermat.pub/try), nothing to install. Please try it and report what you find.

- **The web version**: the same page, toolbar and keys as the desktop app. Open the examples, open and save sheets from your computer; your sheet stays in the browser until you save it. Printing, PDF, `.xmcd` import, SymPy, Python functions and the AI assistant (MCP) are in the desktop versions, shown greyed in the browser.
- **Much faster on big sheets**: sheets that fill arrays in loops, or define many local variables, run in seconds where they took hours.
- **Solve blocks** keep every part of a chained constraint (`0 < L < L_max`), and stay near the guesses, so a fit no longer jumps to a solution on the other side of a pole.
- **Units**: a zero matches any unit in a sum, and a plain 0 in an array of lengths is 0 m.
- **The keyboard**: the arrow keys move the crosshair (or the selected regions), Enter moves it down a line, and Tab goes on to the next region once no placeholder is left.
- **Fixed**: a symbolic result could show the answer to an earlier statement after the sheet was edited while SymPy worked; the toolbar no longer starts a wrapped row with a divider; the status bar says "AI/MCP"; the installer's header shows only the logo.

## 1.0.0-dev0 (developer preview)

The first build to try: everything below under 1.0.0 is in it, along with what came since. It is a preview, so expect rough edges, and please report what you find.

New since the plan below was written:

- **Python functions in a sheet**: `bt := use "beam_tools.py"`, then `bt.moment(12 kN/m, 6 m)`. Units come from the function's annotations (`q: "kN/m"`). Typing `bt.` lists the functions, and hovering shows their signature and docstring. A sheet asks before it runs a Python file.
- **Units in symbolic results**: `q*L^2/8 → 54000 N·m`, with the units checked.
- **Tables** with Excel-style cell references (`B5`, `B2:B6`), and a grid editor that pastes from Excel.
- **Printing and PDF export**, with selectable, searchable text; **headers and footers** with page numbers.
- **Pictures** in the sheet: insert, paste, resize.
- **Data and numerics**: curve fitting and interpolation, reading data files (CSV, text, Excel), eigenvalues, FFTs, ODE solvers, optimisation (`Minimize`, `Maximize`), temperatures in °C and °F.
- **Numbers in other bases**: `0x1F`, `0o17`, `0b101`.
- **Matrices** can be resized after they are made (`Ctrl+M` inside one).
- **Importing `.xmcd` worksheets** is much more complete: controls, data tables and spreadsheet components come in as their values, and results are checked against the values saved in the files.

## 1.0.0 (not released yet)

The first public version.

- **Math that looks like math**: type on a free-form page and get real fractions, roots, sums, matrices and programs, with an insertion cursor that works on the structure, not on text. A toolbar for those who prefer buttons.
- **Real units** everywhere, checked and converted: `12 kN/m · (6 m)²/8 = 54 kN·m`.
- **Arrays, ranges and programs**: loops, conditions, local variables, `try … on error`.
- **Solving**: `root(f, x)`, solve blocks (`given` … `find`), numeric derivatives and integrals.
- **Symbolic evaluation**: simplify, expand, differentiate, integrate, limits, series, factor, solve (systems too), Laplace, Fourier and z-transforms, with SymPy built in (desktop versions).
- **X-Y plots** with units and a second axis.
- **Pages**: A4 or letter, page breaks, collapsible areas.
- **Files you can read**: worksheets are Markdown, readable on GitHub and diffable, with results cached in the file.
- **Export** to Markdown and LaTeX; copy equations as LaTeX or as MathML for Word.
- **Import** of `.xmcd` worksheets.
- **Portable Windows version**: a zip to run from any folder, nothing installed.
- **AI-ready**: an MCP server, in the app and from the command line, so Claude and other AI assistants can read, check and edit worksheets.
- **The `beermat` command line**: evaluate, check, export and import from scripts.
- **Update check**: a quiet note in the status bar when a new version is out (Help → Check for updates at startup turns it off).
