---
title: Numbers, precision and tolerance
description: How precise results are, and how comparisons treat numbers that are nearly equal.
---

## Precision

Numbers are kept as standard double-precision floating point, about 15 to 16 significant figures, real and complex alike. That is far more than any engineering input is known to, so rounding inside a calculation never matters in practice; what you see is rounded only for display ([How results are shown](../../reference/results/)).

A few things follow from floating point:

- `0.1 + 0.2` is not exactly 0.3 inside, but comparisons allow for it (below).
- Very large and very small results are still correct, and shown with an exponent.
- Division by zero is an error, not infinity.

The symbolic arrow `→` works exactly, with fractions: `x/2 + x/3 → 5*x/6`. `float` turns an exact result into a decimal.

## Tolerance

Comparisons (`==`, `<`, `<=`, `>`, `>=`, `!=`) treat numbers within a small relative tolerance as equal. The tolerance is the built-in variable `TOL`, 10⁻³ by default:

```beermat
0.1 + 0.2 == 0.3 = ?
1.0005 == 1 = ?
1.002 == 1 = ?
TOL := 10^-6
1.0005 == 1 = ?
```

Change it for the whole sheet with the `tolerance` [setting](../../reference/settings/), or from one point on with `TOL := …`.

## Roots of negative numbers

`(-8)^(1/3)` is the principal complex cube root, `1 + 1.732i`, as in most mathematics. For the real root, use `nthroot`:

```beermat
(-8)^(1/3) = ?
nthroot(-8, 3) = ?
```
