---
title: Sums, calculus and solving
description: The built-in functions for sums, calculus and solving.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

<a id="fn-sum"></a>

## `sum(v), sum(f, i), sum(f, i, a, b)`

The sum of the elements of v, or of f over the range variable i, or over i from a to b. Also written with Σ.

```text
sum(i^2, i, 1, 4) = 30
```

<a id="fn-prod"></a>

## `prod(v), prod(f, i), prod(f, i, a, b)`

The product of the elements of v, or of f over i. Also written with Π.

```text
prod(k, k, 1, 5) = 120
```

<a id="fn-vec"></a>

## `vec(expression)`

The expression evaluated element by element over its arrays (the vectorize arrow).

```text
v := [1; 2; 3]
vec(v*v) = [1; 4; 9]
```

<a id="fn-diff"></a>

## `diff(f, x[, n])`

The derivative of f with respect to x (the nth derivative, up to 5) at x's current value.

```text
x := 2
diff(x^3, x) = 12
```

<a id="fn-int"></a>

## `int(f, x, a, b)`

The definite integral of f over x from a to b; either limit may be ∞.

```text
int(x^2, x, 0, 3) = 9
```

<a id="fn-root"></a>

## `root(f, x[, a, b])`

A value of x where f is zero: from x's current value as a guess, or between a and b where f changes sign.

```text
root(x^2 - 2, x, 0, 2) = 1.414
```

<a id="fn-polyroots"></a>

## `polyroots(v)`

The roots of the polynomial v₀ + v₁x + v₂x² + …, as a column, complex where they are.

```text
polyroots([-6; 1; 1]) = [-3; 2]
```

<a id="fn-find"></a>

## `find(x, y, …)`

Below `given`, the values of the unknowns that satisfy its constraints, starting from their current values.

```text
x := 1
given
x^2 == 2
find(x) = 1.414
```

<a id="fn-minerr"></a>

## `minerr(x, y, …)`

Below `given`, the values that come closest to satisfying its constraints, when they can't all hold.

```text
x := 1
given
x == 2
x == 3
minerr(x) = 2.5
```

<a id="fn-Minimize"></a>

## `Minimize(f, x, y, …)`

The values of the variables, from their current values, where f(x, y, …) is least; below `given`, within its constraints.

```text
f(x) := (x - 3)^2 + 1
x := 0
Minimize(f, x) = 3
```

<a id="fn-Maximize"></a>

## `Maximize(f, x, y, …)`

The values of the variables where f(x, y, …) is greatest.

```text
f(x) := 4 - (x - 1)^2
x := 0
Maximize(f, x) = 1
```

<a id="fn-Odesolve"></a>

## `Odesolve(t, b[, steps])`

Below `given`, the solution of its differential equation and initial conditions as a function of t, up to b.

```text
given
y'(t) == -y(t)
y(0) == 1
y := Odesolve(t, 2)
y(1) = 0.368
```

<a id="fn-rkfixed"></a>

## `rkfixed(y, x1, x2, n, D)`

The solution of y' = D(x, y) from x1 to x2 in n equal steps (fourth-order Runge–Kutta): a column of x, then one per element of y.

```text
D(x, y) := -y
rkfixed(1, 0, 1, 4, D) = [0, 1; 0.25, 0.779; 0.5, 0.607; 0.75, 0.472; 1, 0.368]
```

<a id="fn-Rkadapt"></a>

## `Rkadapt(y, x1, x2, n, D)`

As rkfixed, at the same n points, with adaptive steps between them for accuracy.

```text
D(x, y) := -y
Rkadapt(1, 0, 1, 2, D) = [0, 1; 0.5, 0.607; 1, 0.368]
```

<a id="fn-rkadapt"></a>

## `rkadapt(y, x1, x2, n, D)`

The same as Rkadapt.

```text
D(x, y) := -y
rkadapt(1, 0, 1, 2, D) = [0, 1; 0.5, 0.607; 1, 0.368]
```
