---
title: Symbolic algebra
description: Simplify, expand, differentiate, integrate and solve with letters, not numbers.
---

With `=`, Beermat works out a number. With the symbolic arrow `→`, it works on the expression itself and gives an expression back.

```beermat
x/2 + x/3 -> ?
diff(x*sin(x), x) -> ?
int(x*sin(x), x) -> ?
```

Type the expression, then **Ctrl+.** for the arrow. In a file the arrow is written `->`, ending in `-> ?` where the result goes.

## Keywords

A keyword between the expression and the result says what to do. **Ctrl+Shift+.** adds one, or type it after the arrow:

```beermat
(x + 1)^2 -> expand -> ?
x^2 - 1 -> factor -> ?
x^2 - 4 -> solve(x) -> ?
a*x^2 + b*x + c -> solve(x) -> ?
sin(x) -> series(x, 6) -> ?
π -> float(4) -> ?
```

Keywords chain from left to right, each working on the one before:

```beermat
x^2 + 1 -> substitute(x == y + 1) -> expand -> ?
sqrt(x^2) -> assume(x > 0) -> simplify -> ?
```

## Calculus

```beermat
int(x^2, x, 0, a) -> ?
lim(sin(x)/x, x, 0) -> ?
sum(1/k^2, k, 1, ∞) -> ?
1/(s^2 + 1) -> invlaplace(s) -> ?
```

## Systems

```beermat
[x + y == 3; x - y == -1] -> solve(x, y) -> ?
```

Each row of the result is one solution.

## What the sheet has defined

Names defined above are put in: numbers by their value, and functions by their formula. Names that aren't defined stay letters.

```beermat
w := 3
w^2 + z -> ?
g_1(u) := u^2
diff(g_1(t), t) -> ?
```

A value with units goes in with its units, and they are checked:

```beermat
q := 12 kN/m
L := 6 m
q*L^2/8 -> ?
```

## The keywords

| Keyword | Does |
|---|---|
| `simplify` (or no keyword) | Simplifies |
| `expand`, `factor`, `collect(x)` | Multiplies out; factors; gathers the powers of x |
| `solve(x)`, `solve(x, y)` | Solves an equation (`a == b`, or an expression equal to 0), or a system |
| `substitute(x == v)` | Puts v in place of x |
| `series(x, n)`, `series(x == a, n)` | The series in x (about a) up to below degree n |
| `parfrac(x)`, `coeffs(x)` | Partial fractions; the coefficients of a polynomial, from the constant up |
| `float`, `float(n)` | A decimal number, to n significant figures |
| `laplace(t)`, `invlaplace(s)`, `fourier(t)`, `invfourier(ω)`, `ztrans(n)` | Transforms |
| `assume(x > 0)`, `assume(x, real)` | Assumptions for the keywords after it |

`diff`, `int`, `lim`, `sum` and `prod` work with the arrow too, as above.

:::note[In the browser]
The web version does the simpler keywords (simplify, expand, diff, substitute, float and solving to degree 2). The desktop app does all of them.
:::
