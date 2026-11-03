package pub.beermat.engine.eval

/**
 * A built-in function's entry in the manual's reference (docs/manual.md#what-generates-itself):
 * how it is called, one sentence on what it does, and an example, a few lines of a sheet whose
 * last line ends in `= ?`. The manual's generator evaluates the example and writes the result in;
 * a test checks that every built-in has an entry and that every example evaluates.
 */
data class FunctionDoc(val name: String, val signature: String, val group: FunctionGroup, val summary: String, val example: List<String>) {
    /** Its place in the manual, below beermat.pub/docs/ (F1 opens it). */
    val page: String get() = "reference/functions/${group.slug}/#fn-$name"
}

/** The reference's pages of functions, in the manual's order: [slug] is the page's file name. */
enum class FunctionGroup(val title: String, val slug: String) {
    NUMBERS("Numbers and rounding", "numbers"),
    TRIGONOMETRY("Trigonometry", "trigonometry"),
    ARRAYS("Arrays", "arrays"),
    CALCULUS("Sums, calculus and solving", "calculus"),
    STATISTICS("Statistics and data", "statistics"),
    TEXT("Strings, units and programs", "strings"),
}

object FunctionDocs {

    private fun doc(group: FunctionGroup, name: String, signature: String, summary: String, vararg example: String) =
        FunctionDoc(name, signature, group, summary, example.toList())

    private val N = FunctionGroup.NUMBERS
    private val T = FunctionGroup.TRIGONOMETRY
    private val A = FunctionGroup.ARRAYS
    private val C = FunctionGroup.CALCULUS
    private val S = FunctionGroup.STATISTICS
    private val X = FunctionGroup.TEXT

    val all: List<FunctionDoc> = listOf(
        // Numbers and rounding.
        doc(N, "sqrt", "sqrt(x)", "The square root of x, with its units halved.", "sqrt(16 m^2) = ?"),
        doc(N, "nthroot", "nthroot(x, n)", "The real nth root of x; an odd root of a negative number is negative.", "nthroot(-27, 3) = ?"),
        doc(N, "abs", "abs(x)", "The absolute value of x, or the magnitude of a complex number. Also written |x|.", "abs(-3 kN) = ?"),
        doc(N, "Re", "Re(z)", "The real part of z.", "Re(3 + 4i) = ?"),
        doc(N, "Im", "Im(z)", "The imaginary part of z.", "Im(3 + 4i) = ?"),
        doc(N, "arg", "arg(z)", "The angle of z in the complex plane, between −π and π.", "arg(1i) = ?"),
        doc(N, "conj", "conj(z)", "The complex conjugate of z, element by element in an array.", "conj(3 + 4i) = ?"),
        doc(N, "sign", "sign(x)", "−1, 0 or 1, as x is negative, zero or positive.", "sign(-2.5) = ?"),
        doc(N, "signum", "signum(z)", "z divided by its magnitude, or 0 when z is 0.", "signum(3 + 4i) = ?"),
        doc(N, "exp", "exp(x)", "e to the power x.", "exp(1) = ?"),
        doc(N, "ln", "ln(x)", "The natural logarithm of x.", "ln(e^2) = ?"),
        doc(N, "log", "log(x[, b])", "The logarithm of x to base 10, or to base b.", "log(1000) = ?", "log(8, 2) = ?"),
        doc(N, "floor", "floor(x)", "The largest integer not above x.", "floor(-2.5) = ?"),
        doc(N, "ceil", "ceil(x)", "The smallest integer not below x.", "ceil(2.1) = ?"),
        doc(N, "round", "round(x[, n])", "x rounded to n decimal places, or to an integer.", "round(3.14159, 2) = ?"),
        doc(N, "trunc", "trunc(x)", "The integer part of x, towards zero.", "trunc(-2.7) = ?"),
        doc(N, "Floor", "Floor(x, y)", "The largest multiple of y not above x; x and y may have units, the same.", "Floor(2.7 m, 0.5 m) = ?"),
        doc(N, "Ceil", "Ceil(x, y)", "The smallest multiple of y not below x.", "Ceil(2.7 m, 0.5 m) = ?"),
        doc(N, "Round", "Round(x, y)", "x rounded to the nearest multiple of y.", "Round(17 mm, 5 mm) = ?"),
        doc(N, "Trunc", "Trunc(x, y)", "The multiple of y nearest x towards zero.", "Trunc(-2.7, 0.5) = ?"),
        doc(N, "mod", "mod(x, y)", "The remainder of x divided by y, with the sign of x.", "mod(17, 5) = ?"),
        doc(N, "max", "max(a, b, …)", "The largest of the numbers given, arrays included element by element.", "max(3 m, 450 mm, [1 m; 5 m]) = ?"),
        doc(N, "min", "min(a, b, …)", "The smallest of the numbers given.", "min(3, 1, [4; 2]) = ?"),
        doc(N, "Γ", "Γ(x)", "The gamma function: Γ(n) is (n − 1)! for a whole number n.", "Γ(5) = ?"),
        doc(N, "gamma", "gamma(x)", "The gamma function, as Γ(x).", "gamma(0.5) = ?"),
        doc(N, "lnΓ", "lnΓ(x)", "The natural logarithm of the gamma function, for large x.", "lnΓ(100) = ?"),
        doc(N, "erf", "erf(x)", "The error function.", "erf(1) = ?"),
        doc(N, "erfc", "erfc(x)", "The complementary error function, 1 − erf(x).", "erfc(1) = ?"),

        // Trigonometry.
        doc(T, "sin", "sin(x)", "The sine of x, an angle in radians, or with a unit such as deg.", "sin(30 deg) = ?"),
        doc(T, "cos", "cos(x)", "The cosine of x.", "cos(60 deg) = ?"),
        doc(T, "tan", "tan(x)", "The tangent of x.", "tan(45 deg) = ?"),
        doc(T, "sec", "sec(x)", "The secant of x, 1/cos(x).", "sec(π/3) = ?"),
        doc(T, "csc", "csc(x)", "The cosecant of x, 1/sin(x).", "csc(30 deg) = ?"),
        doc(T, "cot", "cot(x)", "The cotangent of x, 1/tan(x).", "cot(45 deg) = ?"),
        doc(T, "asin", "asin(x)", "The angle whose sine is x, between −π/2 and π/2.", "asin(0.5) = ? deg"),
        doc(T, "acos", "acos(x)", "The angle whose cosine is x, between 0 and π.", "acos(0.5) = ? deg"),
        doc(T, "atan", "atan(x)", "The angle whose tangent is x, between −π/2 and π/2.", "atan(1) = ? deg"),
        doc(T, "atan2", "atan2(x, y)", "The angle of the point (x, y) from the x axis, between −π and π; x and y may have units, the same.", "atan2(-1 m, 1 m) = ? deg"),
        doc(T, "acot", "acot(x)", "The angle whose cotangent is x, between 0 and π.", "acot(1) = ? deg"),
        doc(T, "asec", "asec(x)", "The angle whose secant is x.", "asec(2) = ? deg"),
        doc(T, "acsc", "acsc(x)", "The angle whose cosecant is x.", "acsc(2) = ? deg"),
        doc(T, "sinh", "sinh(x)", "The hyperbolic sine of x.", "sinh(1) = ?"),
        doc(T, "cosh", "cosh(x)", "The hyperbolic cosine of x.", "cosh(1) = ?"),
        doc(T, "tanh", "tanh(x)", "The hyperbolic tangent of x.", "tanh(1) = ?"),
        doc(T, "sech", "sech(x)", "The hyperbolic secant of x, 1/cosh(x).", "sech(1) = ?"),
        doc(T, "csch", "csch(x)", "The hyperbolic cosecant of x, 1/sinh(x).", "csch(1) = ?"),
        doc(T, "coth", "coth(x)", "The hyperbolic cotangent of x, 1/tanh(x).", "coth(1) = ?"),
        doc(T, "asinh", "asinh(x)", "The inverse hyperbolic sine of x.", "asinh(1) = ?"),
        doc(T, "acosh", "acosh(x)", "The inverse hyperbolic cosine of x, for x of 1 or more.", "acosh(2) = ?"),
        doc(T, "atanh", "atanh(x)", "The inverse hyperbolic tangent of x, for x between −1 and 1.", "atanh(0.5) = ?"),
        doc(T, "acoth", "acoth(x)", "The inverse hyperbolic cotangent of x, for x beyond −1 and 1.", "acoth(2) = ?"),

        // Arrays.
        doc(A, "rows", "rows(A)", "The number of rows of A (0 for a number).", "rows([1, 2, 3; 4, 5, 6]) = ?"),
        doc(A, "cols", "cols(A)", "The number of columns of A.", "cols([1, 2, 3; 4, 5, 6]) = ?"),
        doc(A, "length", "length(v)", "The number of elements of the vector v.", "length([4; 5; 6; 7]) = ?"),
        doc(A, "last", "last(v)", "The index of the last element of v, counting from ORIGIN.", "last([4; 5; 6; 7]) = ?"),
        doc(A, "matrix", "matrix(m, n, f)", "An m×n matrix whose element (i, j) is f(i, j), i and j counting from 0.", "f(i, j) := i + 10*j", "matrix(2, 3, f) = ?"),
        doc(A, "identity", "identity(n)", "The n×n identity matrix.", "identity(3) = ?"),
        doc(A, "augment", "augment(A, B, …)", "The matrices side by side; they need the same number of rows.", "augment([1; 2], [3; 4]) = ?"),
        doc(A, "stack", "stack(A, B, …)", "The matrices one above the other; they need the same number of columns.", "stack([1, 2], [3, 4]) = ?"),
        doc(A, "submatrix", "submatrix(A, ir, jr, ic, jc)", "The part of A from row ir to jr and column ic to jc, counting from ORIGIN.", "M := [1, 2, 3; 4, 5, 6; 7, 8, 9]", "submatrix(M, 1, 2, 0, 1) = ?"),
        doc(A, "tr", "tr(M)", "The trace of the square matrix M, the sum of its diagonal.", "tr([1, 2; 3, 4]) = ?"),
        doc(A, "reverse", "reverse(v)", "The elements of v in reverse order (the rows of a matrix).", "reverse([1; 2; 3]) = ?"),
        doc(A, "sort", "sort(v)", "The elements of v in ascending order.", "sort([3; 1; 2]) = ?"),
        doc(A, "csort", "csort(A, j)", "The rows of A sorted by column j.", "csort([3, 30; 1, 10; 2, 20], 0) = ?"),
        doc(A, "rsort", "rsort(A, i)", "The columns of A sorted by row i.", "rsort([3, 1, 2; 30, 10, 20], 0) = ?"),
        doc(A, "linspace", "linspace(a, b, n)", "n values evenly spaced from a to b, as a column.", "linspace(0 m, 1 m, 5) = ?"),
        doc(A, "logspace", "logspace(a, b, n)", "n values from a to b, evenly spaced on a log scale.", "logspace(1, 1000, 4) = ?"),
        doc(A, "cross", "cross(u, v)", "The cross product of the three-element vectors u and v.", "cross([1; 0; 0], [0; 1; 0]) = ?"),
        doc(A, "eigenvals", "eigenvals(M)", "The eigenvalues of the square matrix M, as a column.", "eigenvals([2, 0; 0, 3]) = ?"),
        doc(A, "eigenvecs", "eigenvecs(M)", "The eigenvectors of M, one per column, in the order of eigenvals(M), each of length 1.", "eigenvecs([2, 1; 1, 2]) = ?"),
        doc(A, "eigenvec", "eigenvec(M, λ)", "The eigenvector of M for the eigenvalue λ, of length 1.", "eigenvec([2, 1; 1, 2], 3) = ?"),
        doc(A, "match", "match(z, A[, mode])", "The positions in A of the value z, or with mode \"near\", \"lt\", \"gt\", \"leq\" or \"geq\" of the nearest value, the one just below or just above.", "match(5, [2; 5; 7]) = ?", "match(6, [2; 5; 7], \"lt\") = ?"),
        doc(A, "vlookup", "vlookup(z, A, j)", "The elements of column j of A in the rows whose first element is z.", "vlookup(2, [1, 10; 2, 20; 3, 30], 1) = ?"),
        doc(A, "hlookup", "hlookup(z, A, i)", "The elements of row i of A in the columns whose first element is z.", "hlookup(2, [1, 2, 3; 10, 20, 30], 1) = ?"),
        doc(A, "lookup", "lookup(z, A, B)", "The elements of B in the places where A holds z.", "lookup(2, [1; 2; 3], [10; 20; 30]) = ?"),
        doc(A, "cells", "cells(table, \"B2:C9\")", "A range of a named table's grid, written as in a spreadsheet, the header being row 1."),
        doc(A, "IsArray", "IsArray(x)", "1 if x is an array, 0 if not.", "IsArray([1; 2]) = ?"),
        doc(A, "IsScalar", "IsScalar(x)", "1 if x is a number, 0 if not.", "IsScalar(3 m) = ?"),

        // Sums, calculus and solving.
        doc(C, "sum", "sum(v), sum(f, i), sum(f, i, a, b)", "The sum of the elements of v, or of f over the range variable i, or over i from a to b. Also written with Σ.", "sum(i^2, i, 1, 4) = ?"),
        doc(C, "prod", "prod(v), prod(f, i), prod(f, i, a, b)", "The product of the elements of v, or of f over i. Also written with Π.", "prod(k, k, 1, 5) = ?"),
        doc(C, "vec", "vec(expression)", "The expression evaluated element by element over its arrays (the vectorize arrow).", "v := [1; 2; 3]", "vec(v*v) = ?"),
        doc(C, "diff", "diff(f, x[, n])", "The derivative of f with respect to x (the nth derivative, up to 5) at x's current value.", "x := 2", "diff(x^3, x) = ?"),
        doc(C, "int", "int(f, x, a, b)", "The definite integral of f over x from a to b; either limit may be ∞.", "int(x^2, x, 0, 3) = ?"),
        doc(C, "root", "root(f, x[, a, b])", "A value of x where f is zero: from x's current value as a guess, or between a and b where f changes sign.", "root(x^2 - 2, x, 0, 2) = ?"),
        doc(C, "polyroots", "polyroots(v)", "The roots of the polynomial v₀ + v₁x + v₂x² + …, as a column, complex where they are.", "polyroots([-6; 1; 1]) = ?"),
        doc(C, "find", "find(x, y, …)", "Below `given`, the values of the unknowns that satisfy its constraints, starting from their current values.", "x := 1", "given", "x^2 == 2", "find(x) = ?"),
        doc(C, "minerr", "minerr(x, y, …)", "Below `given`, the values that come closest to satisfying its constraints, when they can't all hold.", "x := 1", "given", "x == 2", "x == 3", "minerr(x) = ?"),
        doc(C, "Minimize", "Minimize(f, x, y, …)", "The values of the variables, from their current values, where f(x, y, …) is least; below `given`, within its constraints.", "f(x) := (x - 3)^2 + 1", "x := 0", "Minimize(f, x) = ?"),
        doc(C, "Maximize", "Maximize(f, x, y, …)", "The values of the variables where f(x, y, …) is greatest.", "f(x) := 4 - (x - 1)^2", "x := 0", "Maximize(f, x) = ?"),
        doc(C, "Odesolve", "Odesolve(t, b[, steps])", "Below `given`, the solution of its differential equation and initial conditions as a function of t, up to b.", "given", "y'(t) == -y(t)", "y(0) == 1", "y := Odesolve(t, 2)", "y(1) = ?"),
        doc(C, "rkfixed", "rkfixed(y, x1, x2, n, D)", "The solution of y' = D(x, y) from x1 to x2 in n equal steps (fourth-order Runge–Kutta): a column of x, then one per element of y.", "D(x, y) := -y", "rkfixed(1, 0, 1, 4, D) = ?"),
        doc(C, "Rkadapt", "Rkadapt(y, x1, x2, n, D)", "As rkfixed, at the same n points, with adaptive steps between them for accuracy.", "D(x, y) := -y", "Rkadapt(1, 0, 1, 2, D) = ?"),
        doc(C, "rkadapt", "rkadapt(y, x1, x2, n, D)", "The same as Rkadapt.", "D(x, y) := -y", "rkadapt(1, 0, 1, 2, D) = ?"),

        // Statistics and data.
        doc(S, "mean", "mean(a, b, …)", "The mean of all the numbers given.", "mean([1; 2; 3; 6]) = ?"),
        doc(S, "median", "median(a, b, …)", "The median of all the numbers given.", "median([1; 2; 3; 6]) = ?"),
        doc(S, "var", "var(a, b, …)", "The variance of the numbers as a whole population (dividing by n).", "var([1; 2; 3; 6]) = ?"),
        doc(S, "Var", "Var(a, b, …)", "The variance of the numbers as a sample (dividing by n − 1).", "Var([1; 2; 3; 6]) = ?"),
        doc(S, "stdev", "stdev(a, b, …)", "The standard deviation of a whole population.", "stdev([1; 2; 3; 6]) = ?"),
        doc(S, "Stdev", "Stdev(a, b, …)", "The standard deviation of a sample.", "Stdev([1; 2; 3; 6]) = ?"),
        doc(S, "corr", "corr(vx, vy)", "The correlation coefficient of the two vectors.", "corr([1; 2; 3], [2; 4; 7]) = ?"),
        doc(S, "slope", "slope(vx, vy)", "The slope of the least-squares straight line through the points.", "slope([1; 2; 3], [2; 4; 7]) = ?"),
        doc(S, "intercept", "intercept(vx, vy)", "Where the least-squares straight line crosses x = 0.", "intercept([1; 2; 3], [2; 4; 7]) = ?"),
        doc(S, "linterp", "linterp(vx, vy, x)", "The value at x of the straight lines between the points (vx, vy); beyond the ends, the end line carries on.", "linterp([0 m; 2 m], [10 kN; 20 kN], 0.5 m) = ?"),
        doc(S, "cspline", "cspline(vx, vy)", "A cubic spline through the points, with cubic ends, for interp.", "vx := [0; 1; 2; 3]", "vy := vx^3", "interp(cspline(vx, vy), vx, vy, 1.5) = ?"),
        doc(S, "pspline", "pspline(vx, vy)", "A cubic spline with parabolic ends, for interp.", "vx := [0; 1; 2; 3]", "vy := [0; 1; 4; 9]", "interp(pspline(vx, vy), vx, vy, 1.5) = ?"),
        doc(S, "lspline", "lspline(vx, vy)", "A cubic spline with straight ends (the natural spline), for interp.", "vx := [0; 1; 2; 3]", "vy := [0; 1; 4; 9]", "interp(lspline(vx, vy), vx, vy, 1.5) = ?"),
        doc(S, "interp", "interp(vs, vx, vy, x)", "The value at x of a spline from cspline, pspline or lspline, or of a polynomial from regress.", "vx := [0; 1; 2; 3]", "vy := [1; 3; 5; 7]", "interp(regress(vx, vy, 1), vx, vy, 10) = ?"),
        doc(S, "regress", "regress(vx, vy, k)", "The least-squares polynomial of degree k through the points, for interp.", "vx := [0; 1; 2; 3]", "vy := [1; 2; 5; 10]", "interp(regress(vx, vy, 2), vx, vy, 4) = ?"),
        doc(S, "linfit", "linfit(vx, vy, F)", "The least-squares coefficients of a sum of terms: F(x) returns a vector of the terms.", "F(x) := [1; x]", "linfit([0; 1; 2], [1; 3; 5], F) = ?"),
        doc(S, "dnorm", "dnorm(x, μ, σ)", "The probability density of the normal distribution at x.", "dnorm(0, 0, 1) = ?"),
        doc(S, "pnorm", "pnorm(x, μ, σ)", "The probability that a normal variable is below x.", "pnorm(1.96, 0, 1) = ?"),
        doc(S, "cnorm", "cnorm(x)", "The probability that a standard normal variable is below x.", "cnorm(1.645) = ?"),
        doc(S, "qnorm", "qnorm(p, μ, σ)", "The x below which a normal variable falls with probability p.", "qnorm(0.95, 0, 1) = ?"),
        doc(S, "rnd", "rnd(x)", "A random number between 0 and x, the same at every recalculation.", "rnd(1) = ?"),
        doc(S, "runif", "runif(m, a, b)", "m random numbers between a and b, as a column.", "runif(3, 0, 10) = ?"),
        doc(S, "rnorm", "rnorm(m, μ, σ)", "m random numbers from the normal distribution, as a column.", "rnorm(3, 0, 1) = ?"),
        doc(S, "hist", "hist(intervals, data)", "How many of the data fall in each interval: intervals is a vector of their ends, or the number of equal intervals.", "hist([0; 1; 2; 3], [0.5; 1.5; 1.7; 2.2]) = ?"),
        doc(S, "histogram", "histogram(intervals, data)", "As hist, beside the middle of each interval: a column of middles, then one of counts.", "histogram([0; 1; 2; 3], [0.5; 1.5; 1.7; 2.2]) = ?"),
        doc(S, "fft", "fft(v)", "The Fourier transform of the real vector v (2ⁿ elements), its first half and the middle, scaled by 1/√n.", "fft([1; 0; 0; 0]) = ?"),
        doc(S, "ifft", "ifft(c)", "The inverse of fft: the real vector whose transform is c.", "ifft(fft([1; 2; 3; 4])) = ?"),
        doc(S, "FFT", "FFT(v)", "As fft, scaled by 1/n and with the opposite sign in the exponent.", "FFT([1; 0; 0; 0]) = ?"),
        doc(S, "IFFT", "IFFT(c)", "The inverse of FFT.", "IFFT(FFT([1; 2; 3; 4])) = ?"),
        doc(S, "cfft", "cfft(A)", "The Fourier transform of a complex vector or matrix of any size, scaled by 1/√n.", "cfft([1; 1i]) = ?"),
        doc(S, "icfft", "icfft(A)", "The inverse of cfft.", "icfft(cfft([1; 1i])) = ?"),
        doc(S, "CFFT", "CFFT(A)", "As cfft, scaled by 1/n and with the opposite sign in the exponent.", "CFFT([1; 1i]) = ?"),
        doc(S, "ICFFT", "ICFFT(A)", "The inverse of CFFT.", "ICFFT(CFFT([1; 1i])) = ?"),
        doc(S, "READPRN", "READPRN(\"file\")", "The numbers in a text file, in columns separated by spaces, tabs or commas; lines that aren't numbers are skipped."),
        doc(S, "READCSV", "READCSV(\"file\")", "A comma-separated file as a matrix, numbers and text kept apart."),
        doc(S, "READFILE", "READFILE(\"file\", type)", "A data file, type being \"delimited\" (spaces, tabs or commas) or \"csv\"."),
        doc(S, "READEXCEL", "READEXCEL(\"file\"[, \"Sheet1!A1:C10\"])", "The cells of an Excel workbook (.xlsx or .xls), all of its first sheet or a range; in the desktop app."),

        // Strings, units and programs.
        doc(X, "concat", "concat(s, t, …)", "The strings joined.", "concat(\"beam \", \"B1\") = ?"),
        doc(X, "strlen", "strlen(s)", "The number of characters in s.", "strlen(\"beam\") = ?"),
        doc(X, "substr", "substr(s, m, n)", "n characters of s from position m (counting from 0).", "substr(\"beam B1\", 5, 2) = ?"),
        doc(X, "search", "search(s, t, m)", "The position of t in s, looking from position m, or −1 if it isn't there.", "search(\"beam B1\", \"B\", 0) = ?"),
        doc(X, "num2str", "num2str(x)", "The number x as a string.", "num2str(2.5) = ?"),
        doc(X, "str2num", "str2num(s)", "The number written in the string s.", "str2num(\"2.5e3\") = ?"),
        doc(X, "IsString", "IsString(x)", "1 if x is a string, 0 if not.", "IsString(\"a\") = ?"),
        doc(X, "error", "error(s)", "Stops with the message s: in a program, for a value that can't be used."),
        doc(X, "if", "if(c, a, b)", "a if c is true (not zero), otherwise b; only the one chosen is evaluated.", "if(2 > 1, \"yes\", \"no\") = ?"),
        doc(X, "SIUnitsOf", "SIUnitsOf(x)", "One of x's unit in SI, so that x/SIUnitsOf(x) is the plain number.", "SIUnitsOf(3 kN) = ?"),
        doc(X, "UnitsOf", "UnitsOf(x)", "The same as SIUnitsOf.", "F := 3 kN", "F/UnitsOf(F) = ?"),
        doc(X, "time", "time(z)", "The time now in seconds (z is not used), for timing a long calculation."),
        doc(X, "trace", "trace(x, …)", "Returns x, for compatibility with imported sheets.", "trace(2) = ?"),
        doc(X, "pause", "pause(x, …)", "Returns x, for compatibility with imported sheets.", "pause(2) = ?"),
        doc(X, "use", "use \"file.py\"", "The functions of a Python file, as a module: `bt := use \"beam.py\"`, then `bt.f(x)`; in the desktop app."),
    )

    val byName: Map<String, FunctionDoc> = all.associateBy { it.name }
}
