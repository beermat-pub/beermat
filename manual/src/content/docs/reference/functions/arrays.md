---
title: Arrays
description: The built-in functions for arrays.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

<a id="fn-rows"></a>

## `rows(A)`

The number of rows of A (0 for a number).

```text
rows([1, 2, 3; 4, 5, 6]) = 2
```

<a id="fn-cols"></a>

## `cols(A)`

The number of columns of A.

```text
cols([1, 2, 3; 4, 5, 6]) = 3
```

<a id="fn-length"></a>

## `length(v)`

The number of elements of the vector v.

```text
length([4; 5; 6; 7]) = 4
```

<a id="fn-last"></a>

## `last(v)`

The index of the last element of v, counting from ORIGIN.

```text
last([4; 5; 6; 7]) = 3
```

<a id="fn-matrix"></a>

## `matrix(m, n, f)`

An m×n matrix whose element (i, j) is f(i, j), i and j counting from 0.

```text
f(i, j) := i + 10*j
matrix(2, 3, f) = [0, 10, 20; 1, 11, 21]
```

<a id="fn-identity"></a>

## `identity(n)`

The n×n identity matrix.

```text
identity(3) = [1, 0, 0; 0, 1, 0; 0, 0, 1]
```

<a id="fn-augment"></a>

## `augment(A, B, …)`

The matrices side by side; they need the same number of rows.

```text
augment([1; 2], [3; 4]) = [1, 3; 2, 4]
```

<a id="fn-stack"></a>

## `stack(A, B, …)`

The matrices one above the other; they need the same number of columns.

```text
stack([1, 2], [3, 4]) = [1, 2; 3, 4]
```

<a id="fn-submatrix"></a>

## `submatrix(A, ir, jr, ic, jc)`

The part of A from row ir to jr and column ic to jc, counting from ORIGIN.

```text
M := [1, 2, 3; 4, 5, 6; 7, 8, 9]
submatrix(M, 1, 2, 0, 1) = [4, 5; 7, 8]
```

<a id="fn-tr"></a>

## `tr(M)`

The trace of the square matrix M, the sum of its diagonal.

```text
tr([1, 2; 3, 4]) = 5
```

<a id="fn-reverse"></a>

## `reverse(v)`

The elements of v in reverse order (the rows of a matrix).

```text
reverse([1; 2; 3]) = [3; 2; 1]
```

<a id="fn-sort"></a>

## `sort(v)`

The elements of v in ascending order.

```text
sort([3; 1; 2]) = [1; 2; 3]
```

<a id="fn-csort"></a>

## `csort(A, j)`

The rows of A sorted by column j.

```text
csort([3, 30; 1, 10; 2, 20], 0) = [1, 10; 2, 20; 3, 30]
```

<a id="fn-rsort"></a>

## `rsort(A, i)`

The columns of A sorted by row i.

```text
rsort([3, 1, 2; 30, 10, 20], 0) = [1, 2, 3; 10, 20, 30]
```

<a id="fn-linspace"></a>

## `linspace(a, b, n)`

n values evenly spaced from a to b, as a column.

```text
linspace(0 m, 1 m, 5) = [0; 0.25; 0.5; 0.75; 1]*m
```

<a id="fn-logspace"></a>

## `logspace(a, b, n)`

n values from a to b, evenly spaced on a log scale.

```text
logspace(1, 1000, 4) = [1; 10; 100; 1000]
```

<a id="fn-cross"></a>

## `cross(u, v)`

The cross product of the three-element vectors u and v.

```text
cross([1; 0; 0], [0; 1; 0]) = [0; 0; 1]
```

<a id="fn-eigenvals"></a>

## `eigenvals(M)`

The eigenvalues of the square matrix M, as a column.

```text
eigenvals([2, 0; 0, 3]) = [2; 3]
```

<a id="fn-eigenvecs"></a>

## `eigenvecs(M)`

The eigenvectors of M, one per column, in the order of eigenvals(M), each of length 1.

```text
eigenvecs([2, 1; 1, 2]) = [0.707, 0.707; -0.707, 0.707]
```

<a id="fn-eigenvec"></a>

## `eigenvec(M, λ)`

The eigenvector of M for the eigenvalue λ, of length 1.

```text
eigenvec([2, 1; 1, 2], 3) = [0.707; 0.707]
```

<a id="fn-match"></a>

## `match(z, A[, mode])`

The positions in A of the value z, or with mode "near", "lt", "gt", "leq" or "geq" of the nearest value, the one just below or just above.

```text
match(5, [2; 5; 7]) = [1]
match(6, [2; 5; 7], "lt") = [1]
```

<a id="fn-vlookup"></a>

## `vlookup(z, A, j)`

The elements of column j of A in the rows whose first element is z.

```text
vlookup(2, [1, 10; 2, 20; 3, 30], 1) = [20]
```

<a id="fn-hlookup"></a>

## `hlookup(z, A, i)`

The elements of row i of A in the columns whose first element is z.

```text
hlookup(2, [1, 2, 3; 10, 20, 30], 1) = [20]
```

<a id="fn-lookup"></a>

## `lookup(z, A, B)`

The elements of B in the places where A holds z.

```text
lookup(2, [1; 2; 3], [10; 20; 30]) = [20]
```

<a id="fn-cells"></a>

## `cells(table, "B2:C9")`

A range of a named table's grid, written as in a spreadsheet, the header being row 1.

<a id="fn-IsArray"></a>

## `IsArray(x)`

1 if x is an array, 0 if not.

```text
IsArray([1; 2]) = 1
```

<a id="fn-IsScalar"></a>

## `IsScalar(x)`

1 if x is a number, 0 if not.

```text
IsScalar(3 m) = 1
```
