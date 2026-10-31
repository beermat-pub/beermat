# Evaluation

> Status: **draft**, describing what the engine does today (milestone 3 in progress). Items marked † are guesses that **need checking against imported worksheets**.

## Order

- A sheet is evaluated in order: each statement sees the definitions above it. A later definition of the same name replaces the earlier one from that point on.
- **Global definitions** (`::=`) are evaluated first, top to bottom, in a separate pass. Each global sees only the globals above it (and the built-ins). Every ordinary statement then sees all the globals.
- **A global is really global** (Dan, 2026-10-29): an ordinary definition (`:=`) of a name the sheet defines globally is an error, wherever it is: "x is defined globally (≡); it can't be redefined". So is a second global of the same name. (Letting an ordinary definition shadow a global from where it appeared † made a global mean different things in different places.) A local in a program (`<-`) and a function's parameter may still use the name, inside their own scope.

## Values

- **Numbers** are complex, stored in SI base units with a dimension (see [units.md](units.md)). Real results stay exactly real.
- **Booleans** are the numbers 1 and 0. Any non-zero number is true.
- **Strings** are values. They can be compared but not used in arithmetic.
- **Functions** are values: `h := f` makes `h` the same function.

**Precision** (Dan, 2026-10-28): numbers are IEEE doubles (`Double`, about 15–16 significant figures) everywhere, real and complex. No arbitrary precision: engineering inputs are never known that well. Only unit exponents (`Rational`) and the symbolic engine (exact rationals, then `float`) work exactly.

## Functions

- `f(x, y) := body` defines a function; the body is evaluated only when the function is called.
- **Free names in the body resolve where the function was defined.** After `a := 2`, `f(x) := a*x`, `a := 3`, `f(1)` is 2.
- The function can call itself, so recursion works. More than 1000 nested calls is an error.
- `if(cond, a, b)` evaluates only the branch it picks, so `fact(n) := if(n < 1, 1, n*fact(n - 1))` terminates.

## Operators

- Division by zero is an error, not ∞.
- `(-8)^(1/3)` is the principal complex root `1 + 1.732i`, not `-2` (confirmed by Dan, 2026-10-29). `nthroot(-8, 3)` is `-2`.
- `and` and `or` short-circuit: `0 and 1/0` is 0.
- **Comparisons have a tolerance: the built-in variable `TOL`** (default 10⁻³, the frontmatter `tolerance`). Like `ORIGIN`, it can be redefined in the sheet (`TOL := 10^-6`) from that point on. Numbers within a relative `TOL` of the larger magnitude are equal, so `0.1 + 0.2 == 0.3` is 1. `<`, `<=`, `>` and `>=` use it too, consistently. An infinity (`∞`, `-∞`) equals only itself: `5 != -∞` is 1 (a sheet marks gaps in a table with `-∞` and tests for them). † Whether imported sheets expect `TOL` applied relatively or absolutely is to be checked.
- Comparisons chain: `0 < x < 1`. Complex numbers can be tested with `==` and `!=` but not ordered.
- `atan2(x, y)` is the angle of the point (x, y): the argument order is the reverse of most programming languages.

## Arrays

- A matrix is written `[1, 2; 3, 4]`; a column vector `[1; 2; 3]`. Elements can be any value, and each number keeps its own unit.
- **Indices** count from `ORIGIN` (default 0; the frontmatter `origin`, or `ORIGIN := 1` in the sheet). `v[i]` takes one index on a vector (a row or a column), `M[i, j]` two. `M^<j>` is column j, as a column vector.
- **A number over a vector** is element by element (the results saved in Dan's imported sheets show it): `1/[2; 4]` is `[0.5; 0.25]`. A vector over a vector of the same shape is element by element too (2026-10-29). Over a matrix it is still an error: multiply by its inverse.
- **Names defined together** (2026-10-29): `[a; b] := v` gives each name the element of v in its place (v must have the same shape); `find([x; y])` takes the unknowns that way too. **A column** `M^<j> := v` sets column j, growing the matrix as elements do. Both work with `<-` in programs.
- **A bare 0 goes with any unit** in limits: `int(f, x, 0, L)`, `root(f, x, 0, 2 m)`, `0, 1 m..5 m`.
- **Element definitions** `v[i] := x` and `M[i, j] := x` make a copy of the array with that element set. The array grows as needed, and new elements are zeros in the unit of `x`. Defining an element of an undefined name starts a new array.
- **Arithmetic:**
  - `+` and `-` work element by element on arrays of the same size. A scalar is added to every element (confirmed by Dan).
  - `*`: a scalar scales every element; two column vectors of the same length give their dot product; otherwise it is the matrix product. A product that comes out 1×1 (a row times a column) is that number, not a 1×1 matrix. Units are checked element by element, so a sum of products with mismatched units is an error.
  - `/` divides every element by a scalar. Dividing by a matrix is an error: multiply by `M^-1`.
  - `M^n` for a square matrix and a whole number n; `M^-1` is the inverse, `M^0` the identity. `M^T` is the transpose. An array that isn't square takes a power element by element: `[2, 3]^2` is `[4, 9]` (Dan, 2026-10-29). Older `.xmcd` files (`math20`) computed a row vector's power differently; see [xmcd.md](xmcd.md#known-differences).
  - `|M|` is the determinant of a square matrix, or the length of a vector.
- **Inverse and determinant with units.** They are computed on the numbers, by Gaussian elimination with partial pivoting, and the units are worked out separately: element (i, j) must have the units of `r_i * c_j` for some row units r and column units c, as in a stiffness matrix. The inverse then has the units `1/(c_i * r_j)`. A matrix that doesn't fit this pattern has no meaningful inverse, and gets an error saying which element breaks it.
- **Functions:** `rows`, `cols`, `length`, `last` (the last index, from `ORIGIN`), `identity(n)`, `augment(A, B, …)` (side by side), `stack(A, B, …)` (on top of each other), `submatrix(M, ir, jr, ic, jc)` (inclusive, from `ORIGIN`), `tr` (trace), `sum(v)` (the vector sum Σv), `mean`, `max`/`min`, which look inside arrays (with complex values, the real and imaginary parts separately †), and `matrix(m, n, f)`, an m × n matrix whose element (i, j) is f(i, j), with i and j counting from 0 whatever `ORIGIN` is.
- **Scalar built-ins apply element by element to an array** (Dan, 2026-10-29): `sqrt(v)`, `sin(v)`, `ln(M)` give an array of the same shape, without `vec()`. This covers the built-ins that take one number (elementary, trig, rounding). Those that already mean something for an array keep that meaning (`|v|` is a vector's length, `|M|` a determinant; `max`, `mean`, `rows`). `vec()` is still needed for operators (`vec(a*b)` multiplies element by element) and for user functions.
- **Results** show in linear syntax: `[1, 2; 3, 4]*m` when every element has the same unit, otherwise each element with its own: `[3000 N; 2000 N*m]`.

## Ranges

- `i := 0..10` defines a **range variable**: the whole numbers 0 to 10. `10..0` counts down. `x := 0, 0.1..1` steps by `second - first`, stopping at or before the last value; the values are `first + k*step`, so they don't drift. A range with units needs a step: `x := 0 m, 0.1 m..L`.
- A range is only ever the whole value of a definition (and, later, of a `for` loop).
- **A statement that uses a range variable is evaluated once per value**:
  - `i^2 = ?` shows a column of results;
  - `v[i] := i^2` defines one element per value, in order, so an element can use the ones before it (`x[i + 1] := x[i] + h`);
  - with several range variables, every combination: `M[i, j] := i + j`, the first variable varying slowest.
- Defining a plain name from a range variable (`y := i^2`) is an error that suggests an index (confirmed by Dan).
- A range variable is used one value at a time. Anywhere else, such as inside a function body that refers to it, it is an error.
- **Sums and products:** `sum(expr, i, a, b)` is Σ for i from a to b in whole steps (0 if b < a); `sum(expr, i)` is the range sum over the range variable i; `sum(v)` is the vector sum. `prod` is the same with products (1 when empty). The sum's variable belongs to the sum, so `sum(v[i], i) = ?` shows one number.
- **`vec(expr)`** (vectorize) applies the operators and function calls written inside it element by element: `vec(v*w)` is the element-wise product, `vec(sqrt(v))` the square root of each element. Arrays must be the same size; a scalar goes with every element. Transpose and columns still act on the whole array, and the bodies of user functions called inside are not vectorized themselves.
- Ranges, arrays and iterations are limited to a million values, to stop a typo from hanging the sheet.

## Programs

- A program runs its lines in order. Its value is the value of the last line executed, or of a `return` (which leaves the whole program at once). A line that sets an element, `v[n] <- x`, has the whole array as its value (the results saved in imported sheets show it). Setting an element of a name the program hasn't assigned yet (nor its function's parameters) starts a new array, rather than changing a copy of the sheet's: `C := program` with `C[n] <- …` inside builds C afresh. But a name the program reads before it first assigns it is the sheet's, copied: `S[k] <- S[k]*1000` changes a copy of the sheet's S. The results saved in imported sheets show both. A program that runs no line with a value is an error.
- **Locals.** `a <- x` defines a local variable, visible to the lines below it for the rest of the program, including after a loop it was set in. Names from the sheet (and a function's parameters) can be read; assigning to one makes a local copy, so the sheet's value is never changed. `v[i] <- x` sets an element of a local array, growing it as `v[i] := x` does.
- **`if` and `otherwise`.** An `if` line runs its body when its condition holds; the program then carries on with the next line (it doesn't stop). `otherwise` runs when none of the `if` lines in the run directly above it ran. `-x if x < 0` / `x otherwise` is the classic absolute value.
- **Loops.** `for k in 1..n` runs over a range (so `1..0` counts down, 1 then 0: a known trap), `for x in v` over the elements of an array (row by row), or once for a single value. `while cond` repeats while the condition holds. `break` leaves the innermost loop, `continue` skips to its next turn. A `while` loop that runs more than ten million times is stopped with an error.
- **`try a on error b`** is `a`, or `b` if evaluating `a` fails. It works anywhere, not only in programs.
- A program uses range variables from the sheet one value at a time, like a function body: referring to one inside it is an error.

## Errors

- An error is attached to the smallest sub-expression responsible (an undefined name, the zero divisor, the unit placeholder), for highlighting.
- When a definition fails, uses of the name further down say so: "y has an error: x has an error: units do not match: L vs T", rather than "x is not defined".

## Calculus

`diff` and `int` are the derivative and integral operators, drawn as d/dx and ∫. Evaluated with `=`, they are numeric; with `->`, symbolic ([symbolic.md](symbolic.md)).

- **`diff(f, x)`**, **`diff(f, x, n)`** (n from 1 to 5): the derivative of f with respect to x **at x's current value**, so x must be defined. Central differences refined by Richardson extrapolation (Ridders' method); higher orders differentiate the order below. With a range variable x, the statement gives the derivative at each value of x.
- **`int(f, x, a, b)`**: the definite integral over x from a to b, by adaptive Gauss–Kronrod quadrature (7 and 15 points), which copes with integrable singularities at the ends (`int(1/sqrt(x), x, 0, 1)`). It binds x, like `sum`. Limits must be finite (or infinite). A limit may be ∞ or -∞: the interval is mapped onto a finite one (x = a + t/(1 − t)), which Gauss–Kronrod handles without touching its ends.
- **Units follow**: d/dx divides by x's unit, ∫ … dx multiplies by it (`int(k*x, x, 0 m, 0.1 m)` with k in N/m is in J). An expression whose units change with x is an error. Values must be real for now.

## Solving

`root` is the equation solver (decided with Dan on 2026-10-27: the nth root is `nthroot(x, n)`, so `root` keeps its meaning from `.xmcd` files and imported sheets keep working).

- **`root(f, x)`**: a value of x where f is 0, starting from x's current value as the guess. First secant steps from the guess: quick, and they reach a root in a narrow band (k in ω²/g = k·tanh(k·d) at low ω, solved at every point of an integral). If they don't converge, it widens outwards from the guess on both sides, testing a few points inside each widening so that the root nearest the guess is found first (a wide step could pass two roots unseen), then closes in with Brent's method.
- **`root(f, x, a, b)`**: a root between a and b, where f must change sign. It binds x, like `int`.
- The result has x's unit; f may have any unit. Points where f is complex are skipped while searching.

## Data

Functions for fitting curves through data, with the names and arguments `.xmcd` files use. Data vectors `vx` and `vy` are real, of the same length, and each holds one unit throughout (`vx` in s, `vy` in m); `vx` must be in ascending order where it is interpolated. Results carry the units that follow (`slope` in m/s).

- **`polyroots(v)`**: the roots of `v0 + v1 x + v2 x² + …`, as a column, complex where they are (`polyroots([1; 0; 1])` is `[-1i; 1i]`). Coefficients may carry units if every term has the same; the roots then have the units of `v0/v1`. Found all at once by the Aberth–Ehrlich iteration and sorted by real part, then imaginary part †. With real coefficients, a root whose imaginary part is rounding noise is shown real. Multiple roots are accurate only to about 10⁻⁵ (for a triple root), as with any method.
- **`linterp(vx, vy, x)`**: straight lines between the points; beyond the ends, the first or last line carries on.
- **Splines:** `cspline(vx, vy)`, `pspline(vx, vy)` and `lspline(vx, vy)` return the vector `vs` that **`interp(vs, vx, vy, x)`** takes, for a cubic spline whose ends are cubic, parabolic or straight. A cubic end makes the first two intervals one cubic ("not-a-knot", so `cspline` is exact for a cubic), a parabolic end has the same curvature as its neighbour, and a straight end none (the natural spline). Beyond the ends, the end interval's cubic carries on.
- **`regress(vx, vy, k)`**: the least-squares polynomial of degree k; `interp(regress(vx, vy, k), vx, vy, x)` evaluates it.
- **What `cspline` and `regress` return:**, the first three elements say what follows, and the rest are numbers. For `regress` they are `3, 3, k`, then the coefficients `a0, a1, …, ak` in their units (so `submatrix(r, 3, k + 3, 0, 0)` with `ORIGIN = 0` takes the coefficients). For splines they are `0, 3, ends` (0 straight, 1 parabolic, 2 cubic), then the second derivatives at the points, in the units of `vy/vx²`. † The spline header values are a guess; only `interp` reads them.
- **`linfit(vx, vy, F)`**: the least-squares coefficients for a sum of given functions: `F(x)` returns a vector of terms, as `F(x) := [1; x; exp(-x)]`, and the result is a column of one coefficient for each term. Computed by QR on the terms, scaled to the same size.
- **`slope(vx, vy)`**, **`intercept(vx, vy)`**: the least-squares straight line. **`corr(vx, vy)`**: Pearson's correlation coefficient.
- **Statistics**, which look inside arrays like `mean`: `median`; `var` and `stdev` for a whole population (dividing by n), `Var` and `Stdev` for a sample (by n − 1).

Not yet: `genfit` (nonlinear fits), `loess`, `expfit` and the other named fits, 2D splines (a matrix `vx`), `interp` on a vector of x.

## Solve blocks

Given … Find, written:

```calc
x := 1
y := 1
given
x^2 + y^2 == 4
x - y == 1
s := find(x, y) = ?
```

- **`given`** on its own line starts a solve block (in the editor: type `given`, then Space; it is drawn bold). A variable named `given` on its own is written `\it given`.
- **Constraints** are the comparisons below it (`==`, `<`, `<=`, `>`, `>=`), each evaluated with the definitions above it. The block ends with the statement that calls `find` or `minerr`; a comparison after that is not a constraint.
- **`find(x, y, …)`** returns the unknowns' values that satisfy every constraint (a column for several unknowns), starting from their current values as guesses. It reports an error when it can't satisfy them all (each within 10⁻⁶ of the size of its sides).
- **`minerr(x, y, …)`** returns the closest values even then: the least-squares fit.
- **How:** Levenberg–Marquardt on the constraints' residuals in SI units, the unknowns scaled by their guesses, each step kept within a trust region that starts at about the guesses' size and doubles while steps succeed (so a first step can't fly across a pole to another family of solutions: a Weibull fit from k = 1 stays at k > 0, as imported sheets' saved results have it; 2026-10-31); when that doesn't reach a solution (it can stop where the residuals have gone flat), unbounded steps from the guesses are tried too, and the closer result kept. Inequalities are penalties, made heavier while one is still broken; a chain holds every link (`0 < L < L_max` is `0 < L` and `L < L_max`; read as `(0 < L) < L_max` it would always hold, 2026-10-31). A step onto a point where the constraints can't be evaluated (a pole of Γ) is shortened, not an error. A guess whose definition failed reports that error, not a missing guess. It is local: the guesses choose the solution, and a guess on the wrong side of a turning point may not reach it.
- Units: each unknown keeps its guess's unit; the two sides of a constraint must have the same units.

## More functions

Added on 2026-10-29, chosen by how many of Dan's 2,204 worksheets use each (`Library.kt`); the names and arguments `.xmcd` files use.

- **Rounding to a multiple:** `Floor(x, y)`, `Ceil(x, y)`, `Round(x, y)`, `Trunc(x, y)` (x and y in the same units: `Floor(2.7 m, 0.5 m)` is 2.5 m); `trunc(x)`, `sign(x)`, `signum(z)`.
- **Trigonometry:** `sec`, `csc`, `cot` (complex too), `sech`, `csch`, `coth`, `asinh`, `acosh`, `atanh`, `acot`, `asec`, `acsc`, `acoth`.
- **Special functions:** `erf`, `erfc` (a series near 0, a continued fraction further out, both to about 15 figures), `Γ(x)` (also `gamma`), `lnΓ`.
- **The normal distribution:** `dnorm(x, μ, σ)`, `pnorm(x, μ, σ)`, `qnorm(p, μ, σ)` (the inverse), `cnorm(x)`; x, μ and σ may carry units.
- **Random numbers:** `rnd(x)`, `runif(m, a, b)`, `rnorm(m, μ, σ)`. The generator starts from the same seed at every evaluation, so a sheet shows the same numbers each time it recalculates (at every keystroke, here).
- **Histograms:** `hist(intervals, data)` gives the counts; `histogram(intervals, data)` the bins' middles beside their counts. `intervals` is a number of equal bins between the data's ends, or a vector of edges; a value on an inner edge counts in the bin above.
- **Lookup:** `match(z, A[, mode])` (the positions of z in A, from `ORIGIN`: a vector of indices, or of `[row; column]` for a matrix; modes `"eq"`, `"near"`, `"lt"`, `"gt"`, `"leq"`, `"geq"`), `vlookup(z, A, c)`, `hlookup(z, A, r)`, `lookup(z, A, B)`.
- **Arrays:** `sort(v)`, `reverse(v)` (a matrix's rows), `csort(A, j)` and `rsort(A, i)` (by a column or a row), `linspace(a, b, n)`, `logspace(a, b, n)`, `cross(u, v)` (the plain form of `×`), `conj(z)`.
- **Strings:** `concat(s, …)`, `strlen`, `substr(s, n, m)`, `search(s, sub, start)` (−1 when not found), `num2str`, `str2num`, `error(s)` (fails with that message, for a program's checks), `IsString`, `IsArray`, `IsScalar`.
- **Debugging:** `trace(…)` and `pause(…)`, the tools for a trace window in `.xmcd` sheets, give their first argument back and do nothing else, so imported programs that call them still run.  `time(z)` is the time now in seconds (z ignored), for timing a long calculation.
- **Units of a value:** `SIUnitsOf(x)` and `UnitsOf(x)` are 1 of x's unit in SI (`SIUnitsOf(3 kN)` is 1 N), so `x/SIUnitsOf(x)` is the plain number.
- **Units** added from `.xmcd` sheets: `tonne`, `tonnef`, `mph`, `joule`, `volt`, `mH`, `GN`, `ton` (2000 lb), `kph`.
- Next: Excel components that run their formulas (with POI).

## Numerics

Added on 2026-10-29 (`Numerics.kt`, and `Minimize` in the evaluator):

- **Eigenvalues:** `eigenvals(M)` by the shifted QR algorithm (complex where they are; real for a real symmetric matrix), sorted by real part, in the matrix's units (every element must have the same). `eigenvecs(M)`, the unit eigenvectors as columns in the same order, and `eigenvec(M, λ)`, by inverse iteration; each vector's largest element is made real and positive (the sign convention of the original results †).
- **Fourier transforms** †: `cfft`/`icfft` and `fft`/`ifft` (real data of 2^m values, giving the n/2 + 1 coefficients) scale by 1/√n with +i in the forward exponent; the capitalised `CFFT`/`ICFFT`, `FFT`/`IFFT` scale the forward transform by 1/n with −i. A radix-2 FFT for powers of 2, otherwise the direct sum. The exact conventions are to check against an imported sheet.
- **ODE solvers:** `rkfixed(y, x1, x2, n, D)` (fourth-order Runge–Kutta, n equal steps) and `Rkadapt(y, x1, x2, n, D)` (the same n output points, with Dormand–Prince 5(4) steps between them to about 10⁻¹⁰ relative error). `D(x, y)` gives the derivatives as a vector (or a number for one equation). The result's first column is x, then one column per unknown. **Units** work: y's elements and x carry theirs, and each derivative must come out in y's unit per x's unit (checked).
- **`Minimize(f, x, y, …)` and `Maximize(…)`**: the values of the variables (from their current values as guesses) where f(x, y, …) is least or greatest, by Nelder–Mead on variables scaled by the guesses, restarted until it stops improving. Below a `given`, its constraints hold too, as penalties that grow until they are met (an error if they can't be). It is local: the guesses choose the minimum found. A trial point where f can't be evaluated (a complex value, outside its domain) counts as a bad point, not an error.

## Data files

Added on 2026-10-29 (`DataFiles.kt`), with the names `.xmcd` files use:

- **`READPRN("file")`**: a matrix of the numbers in a text file, in columns separated by spaces, tabs, commas or semicolons; lines that aren't numbers (a header) are skipped, and every line of numbers must have as many values.
- **`READEXCEL("file"[, range])`**: an Excel workbook's values as Excel last calculated them (formulas aren't run), from the first sheet's used cells or a range such as `"Loads!B2:B10"`: numbers, and text where a cell holds text; empty cells are 0. A `.xlsx` is read directly; a classic `.xls` with Apache POI. It lives in the `format` module, since the engine stays free of JVM libraries for the web version.
- **`READCSV("file")`** and **`READFILE("file", "csv")`**: a CSV file, cells that are numbers as numbers and the rest as text (quoted cells may hold commas); **`READFILE("file", "delimited")`** the same with spaces, tabs or commas between cells.
- **Where:** a name is relative to the sheet's folder (or absolute). A sheet not saved yet has no folder, and says so. The CLI, the MCP tools and the app all pass the folder in (`Calculation.of(sheet, folder)`).
- Files are read again at every evaluation, so a sheet follows its data. The values carry no units: multiply a column by its unit (`q := READPRN("loads.txt")^<1>*1 kN/m`).
- A sheet can read any file the user can: worth knowing before opening a sheet from someone else. Writing files (`WRITEPRN`) is not supported, since the sheet evaluates at every keystroke.

## Differential equations

`Odesolve`, the solve block for ordinary differential equations (added 2026-10-29, `OdeSolve.kt`):

```calc
given
y''(t) + 0.1*y'(t) + y(t) == 0
y(0) == 1
y'(0) == 0
y := Odesolve(t, 10)
```

- **Below `given`**, an equation holds at the variable (`y''(t) + y(t) == 0`, using `t`), and an initial condition at a number (`y(0) == 1`, `y'(0) == 0`). A derivative is written with primes, `y'(t)`, `y''(t)`, or as `diff(y(t), t[, n])`, which is how `.xmcd` files store it.
- **`Odesolve(t, b[, steps])`** gives the unknown as a function of t over the range from the initial point to b (1000 steps unless given), so `y(5) = ?` works. **A system:** `[x; v] := Odesolve([x; v], t, 4 s)`, with one equation per unknown, defines each of them.
- **How:** each unknown and its derivatives below the highest make a first-order system; at each step the highest derivatives are found from the equations by Newton's method (so they needn't be solved by hand, and needn't appear linearly), and the system is integrated with Dormand–Prince 5(4) steps, as `Rkadapt`. The functions interpolate between the points with cubic Hermite polynomials, from the values and slopes there.
- **Units:** the initial values give each unknown its unit and the initial point gives t's; each condition on a derivative needs the unknown's unit per t's unit to that power.
- **Not yet:** conditions at two points (boundary values, solved by shooting); stiff systems; the options (fixed or adaptive steps, `Radau` and `Stiffb`).
