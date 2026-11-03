# Symbolic evaluation

> Status: **our own engine and SymPy both work** (milestone 10). Our small engine came first (2026-10-27); SymPy, bundled in every desktop version, was decided by Dan and built on 2026-10-28 (below). Still to do: the installers checked with Python on macOS and Linux.

`expr -> ?` evaluates symbolically: the result is an expression, not a number. Keywords between transform it in turn: `(x + 1)^2 -> expand -> ?`. In the editor, `Ctrl+.` makes a statement symbolic and `Ctrl+Shift+.` adds a keyword. The result is cached in the file like any other (`#= x^2 + 2*x + 1`), and drawn as math after an arrow; keywords are drawn bold (`solve, x`).

## Two engines

The engine defines the interface (`SymbolicBackend`); the `symbolic` module answers through `Symbolics`, which asks two engines in turn:

1. **Our own small engine** (`Cas`), MIT, in the same process and instant. It does what the table below lists, and is the only one in the web version.
2. **SymPy** (`SymPyBackend`), for everything else: when our engine meets something it can't do (a keyword, an integral, a limit, `solve` beyond degree 2, complex numbers, `∞`), it says so (`Cas.Beyond`) and the whole statement goes to SymPy. Without SymPy (the web), that is the error, which names the desktop versions rather than SymPy (Dan, issue #6, 2026-11-02): "factor isn't available in the web version: install Beermat on your computer (beermat.pub/download) for all of it".

Our engine works on its own tree of sums, products and powers, kept in a canonical form: flattened, numbers exact (rationals of any size), like terms and like factors collected, in a fixed order (higher powers first). Results are written back as readable expressions: `5*x/6`, `x*cos(x) + sin(x)`, `(-b - sqrt(b^2 - 4*a*c))/(2*a)`. SymPy's results are written back the same way, so both read alike.

| What | Example | Result | Engine |
|---|---|---|---|
| simplify (also with no keyword) | `x/2 + x/3 -> ?` | `5*x/6` | ours (SymPy's `simplify` when the chain goes to SymPy) |
| `expand` | `(a + b)*(c + d) -> expand -> ?` | `a*c + a*d + b*c + b*d` | ours |
| `diff(f, x)`, `diff(f, x, n)` | `diff(x*sin(x), x) -> ?` | `x*cos(x) + sin(x)` | ours |
| `substitute(x == v)` (or `substitute(x, v)`) | `x^2 + 1 -> substitute(x == y) -> ?` | `y^2 + 1` | ours |
| `solve(x)`: `a == b` or an expression equal to 0 | `x^2 - 4 -> solve(x) -> ?` | `[-2; 2]` | ours to degree 2, real roots; SymPy beyond |
| `solve(x, y)` on a vector of equations: a system, one row per solution | `[x + y == 3; x - y == -1] -> solve(x, y) -> ?` | `[1, 2]` | SymPy |
| `float`, `float(n)` (n significant figures, 10 by default) | `π -> float(4) -> ?` | `3.142` | ours |
| `int(f, x)`, `int(f, x, a, b)` | `int(x*sin(x), x) -> ?` | `sin(x) - x*cos(x)` | SymPy |
| `lim(f, x, a)`, `lim(f, x, a, "+")` | `lim(sin(x)/x, x, 0) -> ?` | `1` | SymPy |
| `sum(f, i, a, b)`, `prod(f, i, a, b)` | `sum(1/k^2, k, 1, ∞) -> ?` | `π^2/6` | SymPy |
| `factor` | `x^2 - 1 -> factor -> ?` | `(x - 1)*(x + 1)` | SymPy |
| `collect(x)` | `a*x^2 + b*x^2 + c*x -> collect(x) -> ?` | `c*x + x^2*(a + b)` | SymPy |
| `series(x, n)`, `series(x == a, n)`: terms below degree n (6 by default) | `sin(x) -> series(x, 6) -> ?` | `x^5/120 - x^3/6 + x` | SymPy |
| `parfrac(x)` | `1/(x*(x - 1)) -> parfrac(x) -> ?` | `1/(x - 1) - 1/x` | SymPy |
| `coeffs(x)`: from the constant term up | `(x - 1)*(x - 2) -> coeffs(x) -> ?` | `[2; -3; 1]` | SymPy |
| `laplace(t)`, `laplace(t, s)`; `invlaplace(s)`, `invlaplace(s, t)` (t > 0, so no step function) | `1/(s^2 + 1) -> invlaplace(s) -> ?` | `sin(t)` | SymPy |
| `fourier(t)`, `fourier(t, ω)`; `invfourier(ω)`: the convention ∫ f e^(−iωt) dt | | | SymPy |
| `ztrans(n)`, `ztrans(n, z)` (by summation) | | | SymPy |
| `assume(x > 0)`, `assume(x < 0)`, `assume(x >= 0)`, `assume(x, real)` (or `positive`, `negative`, `integer`): for the keywords after it | `sqrt(x^2) -> assume(x > 0) -> simplify -> ?` | `x` | SymPy |

Derivatives in our engine know sums, products, powers, `exp`, `ln`, `sin`, `cos`, `tan`, their inverses, `sinh` and `cosh`; anything else goes to SymPy. `diff` and `int` are drawn as d/dx and ∫ (an indefinite integral without limits), `lim` with x→a below it. A symbolic statement carries no units, so every name in it is drawn as a variable: `t` and `s` in `laplace(t)` are italic, not tonne and second (in the window and in LaTeX and MathML). The integral's, derivative's, limit's or sum's variable is its own: `x := 5` doesn't put 5 into `int(x, x, 0, 1)`. `∞` is the built-in infinity (a name of its own: [syntax.md](syntax.md#more-characters-in-names)). Complex results read as `-0.707 - 1.22i`. A result SymPy can only give with conditions (a `Piecewise`) is an error that suggests `assume`. Agreed by Dan on 2026-10-28: `∞` as a built-in constant, `invlaplace` for t > 0, `ztrans` without its convergence condition, and `Piecewise` as an error for now.

## What the sheet has defined

Names defined above are put in (confirmed by Dan, 2026-10-29): a number without units by its value (`x := 3` then `x^2 + y -> ?` gives `y + 9`), and a function the sheet defines by its body (`f(u) := u^2` then `diff(f(t), t) -> ?` gives `2*t`). A number with units is put in too (see Units, below). Anything else stays a symbol: an array, a name not defined. A keyword's variables stay names (`solve(x)` after `x := 3` solves for x), and its values are put in (`substitute(x == a)`). Both engines do this the same way.

## Units

Decided by Dan on 2026-10-29 (milestone 18): units go through symbolic evaluation as symbols †, with the numeric engine's checks wherever the units are known. Both engines get the same treatment, in a layer around them (`SymbolicUnits`), so neither handles units itself.

- **In:** a name defined with units is put in as its value in SI times base-unit symbols: `L := 6 m` goes in as `6·m`, `q := 12 kN/m` as `12000·kg·s⁻²`. The unit symbols are upright names (`
m m`), so they never merge with a variable called `m`. A temperature on a scale goes in absolute, as everywhere: `20 °C` is 293.15 K. Units written in the symbolic statement itself stay variables, as before (`t` and `s` in `laplace(t)`).
- **Checked:** before the engine runs, the numeric engine's dimension rules are applied wherever the dimensions are known: a sum or comparison of a length and a time, `sin(6 m)`, `e^(2 s)` are errors (`units do not match`). Names not defined have no known dimension and pass.
- **Out:** a result that is a product with units in it is shown with its unit, chosen as for `=` without a placeholder (docs/units.md#display): `q*L^2/8 -> ?` gives `54000 N*m`, and `L/x -> ?` gives `6/x m`. Units that cancel disappear. A result where units sit inside a sum (`x + 6 m`, with `x` not defined) keeps the unit symbols where they are.

## Not yet

- A definition with a symbolic value (`F(s) := … -> ?`); units inside the body of a function the sheet defines (its names with units stay symbols).
- `invztrans`, `rewrite`, `Piecewise` results (drawn as a program's `if` lines).

## Choosing the fuller backend

Decided by Dan on 2026-10-28: **B**, bundled in every desktop version (see [Bundled SymPy](#bundled-sympy)). The comparison, kept for the record. Our own engine stays in every option: it is fast, MIT, in the same process, and the only one that also runs in the web version. The question is what does the hard part: integration, limits, series, `factor`, general `solve`, Laplace and Fourier transforms.

| | A. Grow our own | B. SymPy, a helper process | C. Symja, a helper process | D. Rings, a library |
|---|---|---|---|---|
| Licence | MIT (ours) | BSD-3: compatible | GPL-3.0: can only ship as a separate program beside ours, with its source offered | Apache-2.0: compatible |
| What it adds | What we write: series (from `diff`), limits, polynomial and table integrals, Laplace by table, `collect`, `coeffs` are reachable; general integration and `solve` are years of work | Nearly every symbolic keyword: `integrate`, `limit`, `series`, `factor`, `apart` (parfrac), `solve`, `laplace_transform`, `fourier_transform`, assumptions | As SymPy, with very strong integration (Rubi rules) | Polynomial algebra only: `factor`, GCD, partial fractions, polynomial systems. No integration, limits or series |
| Size | Nothing | About 70–80 MB more per installer: Python and SymPy († to measure) | Tens of MB of jars (†) | About 10 MB (with the Scala library) |
| Speed | Instant | About 1–2 s the first time (starting Python), then fast; started at the first `→` | About 1 s the first time (a second JVM), then fast | Instant |
| Web version | Yes | No (Pyodide could, later, at a high download cost) | No | No |
| Our work | Most, and it never ends | A translator between our tree and SymPy's, and bundling Python on three systems in CI | The same translator; plus the licence care | Small, for what it covers |
| Risk | Users expecting a full CAS hit the limits | Two languages in the project; a big install | GPL boundary: what counts as "separate" is a judgement, so a lawyer's question before shipping | Covers only a part |

Not compared: REDUCE (BSD, very capable, but an old Lisp system with a small community) and Maxima (GPL, with the same licence question as C).

**Recommendation: B**, with our own engine first. Our engine answers whenever it can (simplify, expand, diff, substitute, float, low-degree solve), and hands anything else to SymPy, kept running after its first use. SymPy covers the full keyword list almost one for one, its licence is clean, and it is the most used open-source CAS, so it will outlive our interest in maintaining one. The cost is size; if that matters, SymPy can be an optional download on the first `→` that needs it, instead of in every installer. In the web version only our own engine's keywords would work, and the others would say so.

## Bundled SymPy

Dan's decision (2026-10-28), replacing the optional extension planned earlier that day: **every desktop version bundles Python and SymPy** (installers, portable zip, Microsoft Store), as long as it costs about 50 MB or less. Only the web version goes without, with our own engine alone. One version for everyone also avoids a download inside the app, which the Store may not allow (policy 10.2.2, †).

**Measured** on Windows (2026-10-28): python-build-standalone 3.13 (`install_only_stripped`) with SymPy 1.14 and mpmath 1.3, trimmed (no Tcl/Tk, IDLE, pip, ensurepip, headers, test suites, SymPy's `tests` folders), compiled to `.pyc` at build time:

| | Size |
|---|---|
| Added to the download (zip or tar.gz) | about 25 MB (56 → about 81 MB for the portable zip) |
| Added once installed | about 81 MB |
| `import sympy`, compiled | 0.45 s (4.8 s if the `.pyc` files had to be written on first use, hence compiling at build time) |

The licences fit: Python under the PSF licence, SymPy and mpmath BSD, python-build-standalone's parts permissive (Tcl/Tk is left out anyway). They go in the app's licence notices.

**How it is built and run:**

- **One Python for all three systems:** python-build-standalone, pinned to a release, downloaded and trimmed by a Gradle task and placed in the app image as `python/`. SymPy is installed into it with its own pip at build time (pip is then removed). Each OS builds its own, on its own CI runner.
- **Symlinks and executable bits on macOS and Linux** (checked on Linux in WSL and in CI, 2026-10-29): `tar` unpacks the archive keeping both. Compose copies app resources with Kotlin's `copyTo`, which drops executable bits, so `app/build.gradle.kts` sets the interpreter's bit again in the app image and makes every package from that image (Compose does so on macOS; `packageDeb` and `packageRpm` are pointed at it). The command line's zip sets it explicitly too. Gradle copies also turn each symlink into a copy of its file: on Linux `bin/python` and `bin/python3` link to a 31 MB interpreter. So the task makes the interpreter the one real `bin/python3`, deletes the other links, and deletes `libpython` (only for embedding; the interpreter is linked statically).
- **The command line** carries the same folder as `python/` beside its `lib/`, where `Python.home()` finds it from the jar's location ([cli.md](cli.md#building)).
- **Started only when needed** (Dan asked, 2026-10-28): never at the app's start. One long-lived Python process starts on the first `→` that needs SymPy (or, later, the first Python function), and stays until the app closes; a sheet without such work never starts it. That first result waits for Python to start and load SymPy, about 0.8 s on Windows (measured 2026-10-28); after that, answers take what SymPy takes. (Starting Python in the background when such a sheet opens wouldn't help: the sheet is evaluated straight away.) It is one process because SymPy's import costs half a second, too slow per evaluation. Requests and answers are JSON lines on stdin/stdout (`bridge.py`; expressions as trees, never as code; names as numbers in a table, so any name travels), read on their own threads; answers are cached by request, since a sheet is evaluated again at every edit. A request that takes over 30 s stops the process with a message, and the next one starts a new one.
- **Isolated:** started with `-I` (no user site, no environment variables, no current directory on the path) and `PYTHONHOME` set to the bundled folder, so a Python installed on the machine can't interfere.
- **The Gradle task** is `:symbolic:preparePython` (versions in `gradle/libs.versions.toml`). It writes `symbolic/build/app-resources/common/python`, which the app bundles as Compose app resources (`app/resources/python` in the image, found through `compose.application.resources.dir`; `pub.beermat.symbolic.Python`). The download is kept in `.gradle/python-downloads`. Tests get the same folder through the `beermat.python` property; nothing needs installing by hand.
- **Only SymPy and mpmath**, no NumPy or SciPy (Dan asked, 2026-10-29): Python serves `→` only, and every numeric result (`=`, `root`, `int`, solve blocks, fits, plots) is computed in Kotlin, so they would add 60–90 MB to each download for nothing a sheet uses. They come back as a question with Python functions in a sheet, below.
- **Without it** (the web version, or a build made without the task): our own engine's keywords work (simplify, expand, diff, substitute, float, solve to degree 2); the others answer "needs SymPy, which this version doesn't include".
- **Steps:** (1) the Gradle task and the bundled folder, **done** (2026-10-28; Windows image 122 → 209 MB, portable zip 56 → 83 MB); (2) the bridge (`SymPy`, `SymPyBackend`, `bridge.py`) and the keywords that use it, **done** (2026-10-28); (3) Python for the command line, and the release workflow checking Python in each package on all three systems, **done** (2026-10-29).

### Python functions in a sheet

With Python in every desktop version, a sheet can use functions from a `.py` file (Dan's idea, 2026-10-28). Desktop only, since the web version has no Python. Shaped by Dan on 2026-10-29. **Built (2026-10-29, step 19a):** `use`, the dot, calls with unit annotations and default arguments, numbers, matrices and strings both ways, Python's errors with their line, the file read again when it changes, and the command line's `--allow-python` (`PythonModules`, `bridge.py`; `PythonConsent` holds who may run what). **Built (step 19b):** the app's consent dialog (`PythonPermission`: a yes is kept in the user's preferences by the file's path and a SHA-256 of its content; a no lasts until the app closes; images and PDFs made from the command line run nothing). **Built (step 19c):** in the editor, `.` after a name that holds a module starts the function's slot (`bt.■`, which saves as `bt.?` until filled); a list of the module's functions opens there, narrowed as you type, with the picked one's signature and docstring below it (arrows, Enter or Tab, Escape); resting the mouse on `bt.f` shows the same as a tip. **Built (2026-11-03):** [the user's own packages](#the-users-own-packages).

```
bt := use "beam_tools.py"
M_max := bt.moment(12 kN/m, 6 m) = ? kN*m
g(x) := bt.deflection(x, 6 m)
```

- **`use "file.py"` is a value, a module**, held in a variable like any other. The path is relative to the sheet. The file is read again when it changes on disk.
- **`bt.f` is the module's function `f`**, a function value like any other: call it (`bt.f(x)`), or wrap it in a sheet function (`g(x) := bt.f(x)`, `h := bt.f`). Only functions are reachable, and names starting with `_` stay private.
- **Why `.`:** it reads as in Python, and nothing else uses it there. In the text, `.` straight after a name with no subscript, followed by a name, is this access (`bt.f`); after a subscript it stays part of the subscript (`int_Ω.fo`, [syntax.md](syntax.md#more-characters-in-names)), so a module held in a subscripted name is written `(bt_1).f`. In the editor, `.` after a name that holds a module starts this access; after any other name it starts a subscript, as before.
- **Completion and hover**, as in VS Code: typing `bt.` opens a list of the module's functions, narrowed as you type (arrows and Enter or Tab to pick, Escape to close). Hovering over `bt.f`, or an entry in the list, shows its signature, argument types and the first lines of its docstring, read by Python's `inspect`.
- **Units through annotations.** A function can annotate its arguments and result with units as strings, `def moment(q: "kN/m", L: "m") -> "kN*m":`. Beermat then checks each argument's dimension, passes it as a plain number in that unit (12 and 6 above), and puts the unit on the result. Without annotations, arguments must be dimensionless numbers and so is the result; a quantity with units passed to such a function is an error that suggests annotating it (or dividing by a unit at the call). Arrays go in and out as lists of lists; strings as strings.
- **Consent:** a sheet that runs code asks once per file, showing the file's path ("This sheet runs beam_tools.py. Run it?"), as Excel asks for macros. The answer is remembered per file and its content hash, so a changed file asks again. Until allowed, calls are an error that says so. The command line asks for `--allow-python` instead; the MCP server never allows it on its own.
- **Errors** in the Python code show as the statement's error, with Python's message and the line in the `.py` file.
- It runs in the same long-lived Python process as SymPy (started on first use), with the user's own packages (below) for anything beyond the standard library and SymPy.

### The user's own packages

Proposed 2026-10-29, after Dan asked whether `pip install` would work and survive updates; approved by Dan the same day. **Built 2026-11-03** (for 1.0.0-dev3); the last point, the user's own Python, below. Before, it couldn't work: pip was removed from the bundle, the interpreter runs isolated (`-I`), and the app folder is read-only in practice and replaced at every update.

- Packages go in a folder of the user's, outside the app: `~/.beermat/python/3.13/site-packages`, which the bridge adds to Python's path after the bundled packages (`bridge.py`, from `BEERMAT_PACKAGES`, which `Python.process` sets, since `-I` ignores the user site). Updates never touch it. A package installed while Python is running is found by the next `use` (the import caches are cleared).
- **Tools → Python packages…** (`PythonPackages`, a Swing dialog like the other small ones) installs and removes them with the bundled pip, now kept for this (13 MB on disk), into that folder (`pip install --target --upgrade`); no terminal, no admin rights. pip's output shows below, and a sheet is calculated again when the dialog closes. The command line does the same: `beermat packages` ([cli.md](cli.md#beermat-packages-listinstall-nameremove-namereinstall)). The code is `Packages` in the symbolic module.
- The folder is per Python minor version, because compiled wheels (NumPy, SciPy) only fit the version they were built for. `~/.beermat/python/packages.txt` keeps the list of what the user asked for, and when an update brings a new Python, the dialog's "Install again" button (or `beermat packages reinstall`) installs the same list again. Removing a package leaves what it brought along, as pip does.
- Checked by `PackagesTest` (a wheel made by the test, so no network: installed, imported by a sheet's Python file, removed), by hand with NumPy from PyPI (2026-11-03), and by the Release workflow, which imports pip in every package.
- The user's own Python, for those who have one set up: [below](#the-users-own-python).

### The user's own Python

Brought into 1.0 by Dan on 2026-11-03 (it was 1.x); built the same day. A setting points Beermat at an interpreter of the user's (a venv, a Conda environment, the system's Python), and the sheets' Python functions run there, with whatever packages it has. For those who already keep a Python with their packages (and their own modules) set up.

- **A setting of the machine, not of the sheet**, kept in `~/.beermat/python/own-python.txt` (the interpreter's path), so a sheet stays portable: the same sheet runs in the bundled Python on a machine without the setting, as long as the packages it imports are there too.
- **Only the Python functions move.** SymPy stays in the bundled Python, so symbolic results don't depend on whichever SymPy (if any) the user's Python has; the functions run in a second process, the same `bridge.py` (which then works without SymPy). Two processes is the cost: about 20 MB of memory, only while a sheet uses Python functions.
- **Run as the user has it set up**: not isolated (`-I` is left out, so `PYTHONPATH`, the user's site-packages and `.pth` files count), no `PYTHONHOME`, and Beermat's own packages folder left out: the packages are entirely the user's, installed with their pip or conda. The bridge script runs from a temporary folder holding only itself, so nothing beside it is imported by mistake.
- **Python 3.9 or newer**, checked when it is chosen (`OwnPython.check` runs it and reads its version); choosing a venv's or Conda environment's folder finds the interpreter inside.
- **Where**: Tools → Python packages… has the choice at the top (Beermat's own Python, or yours with a path and Choose…; the package list is off while yours is in use), and `beermat python <path>` / `beermat python bundled` on the command line ([cli.md](cli.md#python)).
- If the interpreter is gone (a deleted venv), the statements that use a Python file say so ("couldn't start"); the rest of the sheet is unaffected.
- Checked by `OwnPythonTest` (the bundled interpreter run as a user's would be, told apart by `sys.flags.isolated`; a missing interpreter; the version check), and by hand (2026-11-03) with a venv holding NumPy, the symbolic example running in the bundled SymPy at the same time.
