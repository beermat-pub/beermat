# Changelog

Each version's section here becomes the notes of its release on GitHub ([docs/release.md](docs/release.md)).

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
