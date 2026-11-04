---
title: Solve equations
description: root, polyroots, solve blocks with find and minerr, and Minimize and Maximize.
---

[Solving equations](../../tutorials/solving/) walks through the first two; this page is the summary.

## One equation

| Write | Gives |
|---|---|
| `root(f, x)` | A value of x where f is 0, starting from x's current value |
| `root(f, x, a, b)` | A root between a and b (f must change sign there) |

```beermat
x := 1
root(x^3 - 2*x - 5, x) = ?
root(cos(x) - x, x, 0, 1) = ?
```

If `root` reports that it found no root near the guess, give limits, or a better guess. It looks for real roots only.

## Polynomials

`polyroots(v)` gives every root of v₀ + v₁x + v₂x² + …, complex ones too:

```beermat
polyroots([-6; 11; -6; 1]) = ?
polyroots([1; 0; 1]) = ?
```

## Several equations

```beermat
a := 1 m
b := 4 m
given
a + b == 5 m
a*b == 6 m^2
find(a, b) = ?
```

- Every unknown needs a guess above the block, with its unit.
- Below `given` (type `given` and a space) go the conditions: `==` (**Ctrl+=**), `<`, `>`, `<=`, `>=`. A chain such as `0 m < L < 10 m` is two conditions.
- The block ends at the statement that calls `find`, `minerr`, `Minimize` or `Maximize`.
- `[a_1; b_1] := find(a, b)` names each result.

**When there is no exact solution**, `minerr` gives the values that come closest (a least-squares fit), where `find` reports an error:

```beermat
x_d := [1; 2; 3; 4]
y_d := [2.1; 3.9; 6.2; 7.8]
a := 1
b := 0
given
a*x_d[0] + b == y_d[0]
a*x_d[1] + b == y_d[1]
a*x_d[2] + b == y_d[2]
a*x_d[3] + b == y_d[3]
minerr(a, b) = ?
```

## The best values

`Minimize(f, x, y, …)` and `Maximize(f, x, y, …)` give the values of the variables where the function f is least or greatest, starting from their current values. Below a `given`, the conditions hold too:

```beermat new
f(x) := (x - 2)^2 + 1
x := 0
Minimize(f, x) = ?
```

```beermat
w := 1 m
h := 1 m
A(w, h) := w*h
given
2*w + 2*h == 4 m
Maximize(A, w, h) = ?
```

## Good guesses

All of these search from the guesses, so the guesses choose the answer when there are several. A guess right between two answers, or exactly on a symmetry of the problem, can leave the search stuck. If a result is surprising, plot the function first and guess near the answer you want.

[Functions for solving](../../reference/functions/calculus/) has the full list.
