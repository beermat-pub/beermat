---
title: Functions
description: Every built-in function, from A to Z.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

The built-in functions, by group:

- [Numbers and rounding](numbers/)
- [Trigonometry](trigonometry/)
- [Arrays](arrays/)
- [Sums, calculus and solving](calculus/)
- [Statistics and data](statistics/)
- [Strings, units and programs](strings/)

And all of them from A to Z:

| Function | What it does |
|---|---|
| [`abs`](numbers/#fn-abs) | The absolute value of x, or the magnitude of a complex number. Also written \|x\|. |
| [`acos`](trigonometry/#fn-acos) | The angle whose cosine is x, between 0 and π. |
| [`acosh`](trigonometry/#fn-acosh) | The inverse hyperbolic cosine of x, for x of 1 or more. |
| [`acot`](trigonometry/#fn-acot) | The angle whose cotangent is x, between 0 and π. |
| [`acoth`](trigonometry/#fn-acoth) | The inverse hyperbolic cotangent of x, for x beyond −1 and 1. |
| [`acsc`](trigonometry/#fn-acsc) | The angle whose cosecant is x. |
| [`arg`](numbers/#fn-arg) | The angle of z in the complex plane, between −π and π. |
| [`asec`](trigonometry/#fn-asec) | The angle whose secant is x. |
| [`asin`](trigonometry/#fn-asin) | The angle whose sine is x, between −π/2 and π/2. |
| [`asinh`](trigonometry/#fn-asinh) | The inverse hyperbolic sine of x. |
| [`atan`](trigonometry/#fn-atan) | The angle whose tangent is x, between −π/2 and π/2. |
| [`atan2`](trigonometry/#fn-atan2) | The angle of the point (x, y) from the x axis, between −π and π; x and y may have units, the same. |
| [`atanh`](trigonometry/#fn-atanh) | The inverse hyperbolic tangent of x, for x between −1 and 1. |
| [`augment`](arrays/#fn-augment) | The matrices side by side; they need the same number of rows. |
| [`Ceil`](numbers/#fn-Ceil) | The smallest multiple of y not below x. |
| [`ceil`](numbers/#fn-ceil) | The smallest integer not below x. |
| [`cells`](arrays/#fn-cells) | A range of a named table's grid, written as in a spreadsheet, the header being row 1. |
| [`CFFT`](statistics/#fn-CFFT) | As cfft, scaled by 1/n and with the opposite sign in the exponent. |
| [`cfft`](statistics/#fn-cfft) | The Fourier transform of a complex vector or matrix of any size, scaled by 1/√n. |
| [`cnorm`](statistics/#fn-cnorm) | The probability that a standard normal variable is below x. |
| [`cols`](arrays/#fn-cols) | The number of columns of A. |
| [`concat`](strings/#fn-concat) | The strings joined. |
| [`conj`](numbers/#fn-conj) | The complex conjugate of z, element by element in an array. |
| [`corr`](statistics/#fn-corr) | The correlation coefficient of the two vectors. |
| [`cos`](trigonometry/#fn-cos) | The cosine of x. |
| [`cosh`](trigonometry/#fn-cosh) | The hyperbolic cosine of x. |
| [`cot`](trigonometry/#fn-cot) | The cotangent of x, 1/tan(x). |
| [`coth`](trigonometry/#fn-coth) | The hyperbolic cotangent of x, 1/tanh(x). |
| [`cross`](arrays/#fn-cross) | The cross product of the three-element vectors u and v. |
| [`csc`](trigonometry/#fn-csc) | The cosecant of x, 1/sin(x). |
| [`csch`](trigonometry/#fn-csch) | The hyperbolic cosecant of x, 1/sinh(x). |
| [`csort`](arrays/#fn-csort) | The rows of A sorted by column j. |
| [`cspline`](statistics/#fn-cspline) | A cubic spline through the points, with cubic ends, for interp. |
| [`diff`](calculus/#fn-diff) | The derivative of f with respect to x (the nth derivative, up to 5) at x's current value. |
| [`dnorm`](statistics/#fn-dnorm) | The probability density of the normal distribution at x. |
| [`eigenvals`](arrays/#fn-eigenvals) | The eigenvalues of the square matrix M, as a column. |
| [`eigenvec`](arrays/#fn-eigenvec) | The eigenvector of M for the eigenvalue λ, of length 1. |
| [`eigenvecs`](arrays/#fn-eigenvecs) | The eigenvectors of M, one per column, in the order of eigenvals(M), each of length 1. |
| [`erf`](numbers/#fn-erf) | The error function. |
| [`erfc`](numbers/#fn-erfc) | The complementary error function, 1 − erf(x). |
| [`error`](strings/#fn-error) | Stops with the message s: in a program, for a value that can't be used. |
| [`exp`](numbers/#fn-exp) | e to the power x. |
| [`FFT`](statistics/#fn-FFT) | As fft, scaled by 1/n and with the opposite sign in the exponent. |
| [`fft`](statistics/#fn-fft) | The Fourier transform of the real vector v (2ⁿ elements), its first half and the middle, scaled by 1/√n. |
| [`find`](calculus/#fn-find) | Below `given`, the values of the unknowns that satisfy its constraints, starting from their current values. |
| [`Floor`](numbers/#fn-Floor) | The largest multiple of y not above x; x and y may have units, the same. |
| [`floor`](numbers/#fn-floor) | The largest integer not above x. |
| [`gamma`](numbers/#fn-gamma) | The gamma function, as Γ(x). |
| [`hist`](statistics/#fn-hist) | How many of the data fall in each interval: intervals is a vector of their ends, or the number of equal intervals. |
| [`histogram`](statistics/#fn-histogram) | As hist, beside the middle of each interval: a column of middles, then one of counts. |
| [`hlookup`](arrays/#fn-hlookup) | The elements of row i of A in the columns whose first element is z. |
| [`ICFFT`](statistics/#fn-ICFFT) | The inverse of CFFT. |
| [`icfft`](statistics/#fn-icfft) | The inverse of cfft. |
| [`identity`](arrays/#fn-identity) | The n×n identity matrix. |
| [`if`](strings/#fn-if) | a if c is true (not zero), otherwise b; only the one chosen is evaluated. |
| [`IFFT`](statistics/#fn-IFFT) | The inverse of FFT. |
| [`ifft`](statistics/#fn-ifft) | The inverse of fft: the real vector whose transform is c. |
| [`Im`](numbers/#fn-Im) | The imaginary part of z. |
| [`int`](calculus/#fn-int) | The definite integral of f over x from a to b; either limit may be ∞. |
| [`intercept`](statistics/#fn-intercept) | Where the least-squares straight line crosses x = 0. |
| [`interp`](statistics/#fn-interp) | The value at x of a spline from cspline, pspline or lspline, or of a polynomial from regress. |
| [`IsArray`](arrays/#fn-IsArray) | 1 if x is an array, 0 if not. |
| [`IsScalar`](arrays/#fn-IsScalar) | 1 if x is a number, 0 if not. |
| [`IsString`](strings/#fn-IsString) | 1 if x is a string, 0 if not. |
| [`last`](arrays/#fn-last) | The index of the last element of v, counting from ORIGIN. |
| [`length`](arrays/#fn-length) | The number of elements of the vector v. |
| [`linfit`](statistics/#fn-linfit) | The least-squares coefficients of a sum of terms: F(x) returns a vector of the terms. |
| [`linspace`](arrays/#fn-linspace) | n values evenly spaced from a to b, as a column. |
| [`linterp`](statistics/#fn-linterp) | The value at x of the straight lines between the points (vx, vy); beyond the ends, the end line carries on. |
| [`ln`](numbers/#fn-ln) | The natural logarithm of x. |
| [`lnΓ`](numbers/#fn-lnΓ) | The natural logarithm of the gamma function, for large x. |
| [`log`](numbers/#fn-log) | The logarithm of x to base 10, or to base b. |
| [`logspace`](arrays/#fn-logspace) | n values from a to b, evenly spaced on a log scale. |
| [`lookup`](arrays/#fn-lookup) | The elements of B in the places where A holds z. |
| [`lspline`](statistics/#fn-lspline) | A cubic spline with straight ends (the natural spline), for interp. |
| [`match`](arrays/#fn-match) | The positions in A of the value z, or with mode "near", "lt", "gt", "leq" or "geq" of the nearest value, the one just below or just above. |
| [`matrix`](arrays/#fn-matrix) | An m×n matrix whose element (i, j) is f(i, j), i and j counting from 0. |
| [`max`](numbers/#fn-max) | The largest of the numbers given, arrays included element by element. |
| [`Maximize`](calculus/#fn-Maximize) | The values of the variables where f(x, y, …) is greatest. |
| [`mean`](statistics/#fn-mean) | The mean of all the numbers given. |
| [`median`](statistics/#fn-median) | The median of all the numbers given. |
| [`min`](numbers/#fn-min) | The smallest of the numbers given. |
| [`minerr`](calculus/#fn-minerr) | Below `given`, the values that come closest to satisfying its constraints, when they can't all hold. |
| [`Minimize`](calculus/#fn-Minimize) | The values of the variables, from their current values, where f(x, y, …) is least; below `given`, within its constraints. |
| [`mod`](numbers/#fn-mod) | The remainder of x divided by y, with the sign of x. |
| [`nthroot`](numbers/#fn-nthroot) | The real nth root of x; an odd root of a negative number is negative. |
| [`num2str`](strings/#fn-num2str) | The number x as a string. |
| [`Odesolve`](calculus/#fn-Odesolve) | Below `given`, the solution of its differential equation and initial conditions as a function of t, up to b. |
| [`pause`](strings/#fn-pause) | Returns x, for compatibility with imported sheets. |
| [`pnorm`](statistics/#fn-pnorm) | The probability that a normal variable is below x. |
| [`polyroots`](calculus/#fn-polyroots) | The roots of the polynomial v₀ + v₁x + v₂x² + …, as a column, complex where they are. |
| [`prod`](calculus/#fn-prod) | The product of the elements of v, or of f over i. Also written with Π. |
| [`pspline`](statistics/#fn-pspline) | A cubic spline with parabolic ends, for interp. |
| [`qnorm`](statistics/#fn-qnorm) | The x below which a normal variable falls with probability p. |
| [`Re`](numbers/#fn-Re) | The real part of z. |
| [`READCSV`](statistics/#fn-READCSV) | A comma-separated file as a matrix, numbers and text kept apart. |
| [`READEXCEL`](statistics/#fn-READEXCEL) | The cells of an Excel workbook (.xlsx or .xls), all of its first sheet or a range; in the desktop app. |
| [`READFILE`](statistics/#fn-READFILE) | A data file, type being "delimited" (spaces, tabs or commas) or "csv". |
| [`READPRN`](statistics/#fn-READPRN) | The numbers in a text file, in columns separated by spaces, tabs or commas; lines that aren't numbers are skipped. |
| [`regress`](statistics/#fn-regress) | The least-squares polynomial of degree k through the points, for interp. |
| [`reverse`](arrays/#fn-reverse) | The elements of v in reverse order (the rows of a matrix). |
| [`Rkadapt`](calculus/#fn-Rkadapt) | As rkfixed, at the same n points, with adaptive steps between them for accuracy. |
| [`rkadapt`](calculus/#fn-rkadapt) | The same as Rkadapt. |
| [`rkfixed`](calculus/#fn-rkfixed) | The solution of y' = D(x, y) from x1 to x2 in n equal steps (fourth-order Runge–Kutta): a column of x, then one per element of y. |
| [`rnd`](statistics/#fn-rnd) | A random number between 0 and x, the same at every recalculation. |
| [`rnorm`](statistics/#fn-rnorm) | m random numbers from the normal distribution, as a column. |
| [`root`](calculus/#fn-root) | A value of x where f is zero: from x's current value as a guess, or between a and b where f changes sign. |
| [`Round`](numbers/#fn-Round) | x rounded to the nearest multiple of y. |
| [`round`](numbers/#fn-round) | x rounded to n decimal places, or to an integer. |
| [`rows`](arrays/#fn-rows) | The number of rows of A (0 for a number). |
| [`rsort`](arrays/#fn-rsort) | The columns of A sorted by row i. |
| [`runif`](statistics/#fn-runif) | m random numbers between a and b, as a column. |
| [`search`](strings/#fn-search) | The position of t in s, looking from position m, or −1 if it isn't there. |
| [`sec`](trigonometry/#fn-sec) | The secant of x, 1/cos(x). |
| [`sech`](trigonometry/#fn-sech) | The hyperbolic secant of x, 1/cosh(x). |
| [`sign`](numbers/#fn-sign) | −1, 0 or 1, as x is negative, zero or positive. |
| [`signum`](numbers/#fn-signum) | z divided by its magnitude, or 0 when z is 0. |
| [`sin`](trigonometry/#fn-sin) | The sine of x, an angle in radians, or with a unit such as deg. |
| [`sinh`](trigonometry/#fn-sinh) | The hyperbolic sine of x. |
| [`SIUnitsOf`](strings/#fn-SIUnitsOf) | One of x's unit in SI, so that x/SIUnitsOf(x) is the plain number. |
| [`slope`](statistics/#fn-slope) | The slope of the least-squares straight line through the points. |
| [`sort`](arrays/#fn-sort) | The elements of v in ascending order. |
| [`sqrt`](numbers/#fn-sqrt) | The square root of x, with its units halved. |
| [`stack`](arrays/#fn-stack) | The matrices one above the other; they need the same number of columns. |
| [`Stdev`](statistics/#fn-Stdev) | The standard deviation of a sample. |
| [`stdev`](statistics/#fn-stdev) | The standard deviation of a whole population. |
| [`str2num`](strings/#fn-str2num) | The number written in the string s. |
| [`strlen`](strings/#fn-strlen) | The number of characters in s. |
| [`submatrix`](arrays/#fn-submatrix) | The part of A from row ir to jr and column ic to jc, counting from ORIGIN. |
| [`substr`](strings/#fn-substr) | n characters of s from position m (counting from 0). |
| [`sum`](calculus/#fn-sum) | The sum of the elements of v, or of f over the range variable i, or over i from a to b. Also written with Σ. |
| [`tan`](trigonometry/#fn-tan) | The tangent of x. |
| [`tanh`](trigonometry/#fn-tanh) | The hyperbolic tangent of x. |
| [`time`](strings/#fn-time) | The time now in seconds (z is not used), for timing a long calculation. |
| [`tr`](arrays/#fn-tr) | The trace of the square matrix M, the sum of its diagonal. |
| [`trace`](strings/#fn-trace) | Returns x, for compatibility with imported sheets. |
| [`Trunc`](numbers/#fn-Trunc) | The multiple of y nearest x towards zero. |
| [`trunc`](numbers/#fn-trunc) | The integer part of x, towards zero. |
| [`UnitsOf`](strings/#fn-UnitsOf) | The same as SIUnitsOf. |
| [`use`](strings/#fn-use) | The functions of a Python file, as a module: `bt := use "beam.py"`, then `bt.f(x)`; in the desktop app. |
| [`Var`](statistics/#fn-Var) | The variance of the numbers as a sample (dividing by n − 1). |
| [`var`](statistics/#fn-var) | The variance of the numbers as a whole population (dividing by n). |
| [`vec`](calculus/#fn-vec) | The expression evaluated element by element over its arrays (the vectorize arrow). |
| [`vlookup`](arrays/#fn-vlookup) | The elements of column j of A in the rows whose first element is z. |
| [`Γ`](numbers/#fn-Γ) | The gamma function: Γ(n) is (n − 1)! for a whole number n. |
