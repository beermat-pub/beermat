---
title: Fit and interpolate data; read files
description: Read data from CSV, text and Excel files, fit lines and curves, and interpolate.
---

## Read a file

```text
D := READCSV("spring.csv")
D = ["x", "F"; 0, 0; 2.1, 50; 3.9, 100; 6.2, 150; 7.9, 200]
P := READPRN("spring.csv")
P = [0, 0; 2.1, 50; 3.9, 100; 6.2, 150; 7.9, 200]
```

| Function | Reads |
|---|---|
| `READPRN("file")` | A text file of numbers in columns (spaces, tabs, commas or semicolons); lines that aren't numbers, such as a header, are skipped |
| `READCSV("file")` | A CSV file: numbers as numbers, the rest as text |
| `READFILE("file", "csv")`, `READFILE("file", "delimited")` | The same, or with spaces, tabs or commas between cells |
| `READEXCEL("file", "Sheet1!B2:B10")` | An Excel workbook's values (`.xlsx` or `.xls`): the first sheet's cells, or a range |

- The file name is relative to the sheet's folder, so save the sheet first.
- The file is read again whenever the sheet is worked out, so the sheet follows its data.
- Values come without units. Multiply a column by its unit: `x := P^<0>*1 mm`.

Reading files is in the desktop app; the browser can't reach your files.

## Fit a straight line

```text
x := P^<0>*1 mm
F := P^<1>*1 N
k := slope(x, F) = 25.083 N/mm
corr(x, F) = 0.999
```

`intercept(x, F)` gives the other end of the line.

## Interpolate

```text
linterp(x, F, 5 mm) = 123.913 N
vs := cspline(x, F)
interp(vs, x, F, 5 mm) = 124.404 N
```

- `linterp` draws straight lines between the points.
- `cspline`, `pspline` and `lspline` make a smooth curve through the points (with cubic, parabolic or straight ends); `interp` reads it at a point.
- The x values must be in ascending order.

## Fit a curve

- `regress(vx, vy, k)`: the least-squares polynomial of degree k, read with `interp(regress(vx, vy, k), vx, vy, x)`.
- `linfit(vx, vy, F)`: the best coefficients for a sum of terms you choose, given as a function returning a vector: `F(t) := [1; t; t^2]`.
- For a model of your own, a [solve block with `minerr`](../solve/#several-equations) fits any formula.

## Statistics

```text
mean(F) = 100 N
median(F) = 100 N
Stdev(F) = 79.057 N
```

`stdev` and `var` are for a whole population (dividing by n), `Stdev` and `Var` for a sample (by n − 1). The [statistics functions](../../reference/functions/statistics/) include the normal distribution, histograms and random numbers.
