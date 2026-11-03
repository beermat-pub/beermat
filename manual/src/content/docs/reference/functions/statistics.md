---
title: Statistics and data
description: The built-in functions for statistics and data.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

<a id="fn-mean"></a>

## `mean(a, b, …)`

The mean of all the numbers given.

```text
mean([1; 2; 3; 6]) = 3
```

<a id="fn-median"></a>

## `median(a, b, …)`

The median of all the numbers given.

```text
median([1; 2; 3; 6]) = 2.5
```

<a id="fn-var"></a>

## `var(a, b, …)`

The variance of the numbers as a whole population (dividing by n).

```text
var([1; 2; 3; 6]) = 3.5
```

<a id="fn-Var"></a>

## `Var(a, b, …)`

The variance of the numbers as a sample (dividing by n − 1).

```text
Var([1; 2; 3; 6]) = 4.667
```

<a id="fn-stdev"></a>

## `stdev(a, b, …)`

The standard deviation of a whole population.

```text
stdev([1; 2; 3; 6]) = 1.871
```

<a id="fn-Stdev"></a>

## `Stdev(a, b, …)`

The standard deviation of a sample.

```text
Stdev([1; 2; 3; 6]) = 2.16
```

<a id="fn-corr"></a>

## `corr(vx, vy)`

The correlation coefficient of the two vectors.

```text
corr([1; 2; 3], [2; 4; 7]) = 0.993
```

<a id="fn-slope"></a>

## `slope(vx, vy)`

The slope of the least-squares straight line through the points.

```text
slope([1; 2; 3], [2; 4; 7]) = 2.5
```

<a id="fn-intercept"></a>

## `intercept(vx, vy)`

Where the least-squares straight line crosses x = 0.

```text
intercept([1; 2; 3], [2; 4; 7]) = -0.667
```

<a id="fn-linterp"></a>

## `linterp(vx, vy, x)`

The value at x of the straight lines between the points (vx, vy); beyond the ends, the end line carries on.

```text
linterp([0 m; 2 m], [10 kN; 20 kN], 0.5 m) = 12.5 kN
```

<a id="fn-cspline"></a>

## `cspline(vx, vy)`

A cubic spline through the points, with cubic ends, for interp.

```text
vx := [0; 1; 2; 3]
vy := vx^3
interp(cspline(vx, vy), vx, vy, 1.5) = 3.375
```

<a id="fn-pspline"></a>

## `pspline(vx, vy)`

A cubic spline with parabolic ends, for interp.

```text
vx := [0; 1; 2; 3]
vy := [0; 1; 4; 9]
interp(pspline(vx, vy), vx, vy, 1.5) = 2.25
```

<a id="fn-lspline"></a>

## `lspline(vx, vy)`

A cubic spline with straight ends (the natural spline), for interp.

```text
vx := [0; 1; 2; 3]
vy := [0; 1; 4; 9]
interp(lspline(vx, vy), vx, vy, 1.5) = 2.2
```

<a id="fn-interp"></a>

## `interp(vs, vx, vy, x)`

The value at x of a spline from cspline, pspline or lspline, or of a polynomial from regress.

```text
vx := [0; 1; 2; 3]
vy := [1; 3; 5; 7]
interp(regress(vx, vy, 1), vx, vy, 10) = 21
```

<a id="fn-regress"></a>

## `regress(vx, vy, k)`

The least-squares polynomial of degree k through the points, for interp.

```text
vx := [0; 1; 2; 3]
vy := [1; 2; 5; 10]
interp(regress(vx, vy, 2), vx, vy, 4) = 17
```

<a id="fn-linfit"></a>

## `linfit(vx, vy, F)`

The least-squares coefficients of a sum of terms: F(x) returns a vector of the terms.

```text
F(x) := [1; x]
linfit([0; 1; 2], [1; 3; 5], F) = [1; 2]
```

<a id="fn-dnorm"></a>

## `dnorm(x, μ, σ)`

The probability density of the normal distribution at x.

```text
dnorm(0, 0, 1) = 0.399
```

<a id="fn-pnorm"></a>

## `pnorm(x, μ, σ)`

The probability that a normal variable is below x.

```text
pnorm(1.96, 0, 1) = 0.975
```

<a id="fn-cnorm"></a>

## `cnorm(x)`

The probability that a standard normal variable is below x.

```text
cnorm(1.645) = 0.95
```

<a id="fn-qnorm"></a>

## `qnorm(p, μ, σ)`

The x below which a normal variable falls with probability p.

```text
qnorm(0.95, 0, 1) = 1.645
```

<a id="fn-rnd"></a>

## `rnd(x)`

A random number between 0 and x, the same at every recalculation.

```text
rnd(1) = 0.731
```

<a id="fn-runif"></a>

## `runif(m, a, b)`

m random numbers between a and b, as a column.

```text
runif(3, 0, 10) = [7.309; 4.101; 2.077]
```

<a id="fn-rnorm"></a>

## `rnorm(m, μ, σ)`

m random numbers from the normal distribution, as a column.

```text
rnorm(3, 0, 1) = [1.562; -0.608; -1.091]
```

<a id="fn-hist"></a>

## `hist(intervals, data)`

How many of the data fall in each interval: intervals is a vector of their ends, or the number of equal intervals.

```text
hist([0; 1; 2; 3], [0.5; 1.5; 1.7; 2.2]) = [1; 2; 1]
```

<a id="fn-histogram"></a>

## `histogram(intervals, data)`

As hist, beside the middle of each interval: a column of middles, then one of counts.

```text
histogram([0; 1; 2; 3], [0.5; 1.5; 1.7; 2.2]) = [0.5, 1; 1.5, 2; 2.5, 1]
```

<a id="fn-fft"></a>

## `fft(v)`

The Fourier transform of the real vector v (2ⁿ elements), its first half and the middle, scaled by 1/√n.

```text
fft([1; 0; 0; 0]) = [0.5; 0.5; 0.5]
```

<a id="fn-ifft"></a>

## `ifft(c)`

The inverse of fft: the real vector whose transform is c.

```text
ifft(fft([1; 2; 3; 4])) = [1; 2; 3; 4]
```

<a id="fn-FFT"></a>

## `FFT(v)`

As fft, scaled by 1/n and with the opposite sign in the exponent.

```text
FFT([1; 0; 0; 0]) = [0.25; 0.25; 0.25]
```

<a id="fn-IFFT"></a>

## `IFFT(c)`

The inverse of FFT.

```text
IFFT(FFT([1; 2; 3; 4])) = [1; 2; 3; 4]
```

<a id="fn-cfft"></a>

## `cfft(A)`

The Fourier transform of a complex vector or matrix of any size, scaled by 1/√n.

```text
cfft([1; 1i]) = [0.707 + 0.707i; 0.707 - 0.707i]
```

<a id="fn-icfft"></a>

## `icfft(A)`

The inverse of cfft.

```text
icfft(cfft([1; 1i])) = [1; 1i]
```

<a id="fn-CFFT"></a>

## `CFFT(A)`

As cfft, scaled by 1/n and with the opposite sign in the exponent.

```text
CFFT([1; 1i]) = [0.5 + 0.5i; 0.5 - 0.5i]
```

<a id="fn-ICFFT"></a>

## `ICFFT(A)`

The inverse of CFFT.

```text
ICFFT(CFFT([1; 1i])) = [1; 1i]
```

<a id="fn-READPRN"></a>

## `READPRN("file")`

The numbers in a text file, in columns separated by spaces, tabs or commas; lines that aren't numbers are skipped.

<a id="fn-READCSV"></a>

## `READCSV("file")`

A comma-separated file as a matrix, numbers and text kept apart.

<a id="fn-READFILE"></a>

## `READFILE("file", type)`

A data file, type being "delimited" (spaces, tabs or commas) or "csv".

<a id="fn-READEXCEL"></a>

## `READEXCEL("file"[, "Sheet1!A1:C10"])`

The cells of an Excel workbook (.xlsx or .xls), all of its first sheet or a range; in the desktop app.
