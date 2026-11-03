---
title: Arrays and ranges
description: Vectors and matrices, and a table of results from a range variable.
---

## A table of results

A **range variable** takes a run of whole numbers. A statement that uses it is worked out once for each value:

```text
L := 6 m
q := 12 kN/m
i := 0..4
x[i] := i*L/4
M_x[i] := q*x[i]*(L - x[i])/2
x = [0; 1.5; 3; 4.5; 6]*m
M_x = [0; 40.5; 54; 40.5; 0]*kN*m
```

Type `;` for the two dots of a range (`i:0;4`), and `[` for an index (`x[i`). On the page, `x[i]` is drawn as x with a subscript i, and the results as columns.

- `x[i] := …` defines one element of the vector x for each value of i.
- A range can step by any amount: give the first two values, then the last. `s := 0 m, 1.5 m..L` is 0, 1.5, 3, 4.5 and 6 m.
- A range variable stands for one value at a time, so `y := i^2` is an error: define elements (`y[i] := i^2`) instead.

## Vectors and matrices

**Ctrl+M** asks for the number of rows and columns and makes an empty matrix to fill in. In a file, `,` separates columns and `;` rows:

```text
v := [3; 4; 12]
M := [2, 1; 1, 3]
v[0] = 3
|v| = 13
M^-1 = [0.6, -0.2; -0.2, 0.4]
|M| = 5
M^T = [2, 1; 1, 3]
```

- Indices start at 0. To count from 1, define `ORIGIN := 1` above, or set `origin: 1` for the whole sheet.
- `|v|` is a vector's length, and `|M|` a square matrix's determinant. `M^-1` is the inverse and `M^T` the transpose (**Ctrl+1**).
- `M^<j>` is column j of M (**Ctrl+6**).

Each element keeps its own unit, so a vector can hold a force and a moment:

```text
F := [10 kN; 2 kN*m]
F = [10 kN; 2 kN*m]
```

## Working on every element

Arithmetic between arrays follows the rules of matrices: `M*v` is a matrix product, and two vectors multiplied give their dot product. To work element by element instead, wrap the expression in `vec( )` (**Ctrl+-**, drawn as an arrow over it):

```text
vec(v*v) = [9; 16; 144]
vec(sqrt(v)) = [1.732; 2; 3.464]
```

## Sums

```text
sum(v) = 19
sum(i^2, i) = 30
sum(k^2, k, 1, 4) = 30
max(v) = 12
rows(M_x) = 5
```

`sum(v)` adds a vector's elements; `sum(expr, i)` adds over a range variable (**$**); `sum(expr, k, a, b)` from a to b (**Ctrl+Shift+4**). `prod` is the same for products.

The [array functions](../../reference/functions/arrays/) include `rows`, `cols`, `augment`, `stack`, `submatrix`, `sort`, `eigenvals` and more.

## Big arrays

An array with more than 20 rows or columns is shown as a table of 20 at a time, with its row and column numbers. Scroll it with the mouse wheel over it (Shift and the wheel to go sideways).
