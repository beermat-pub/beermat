---
title: Solving equations
description: Find the value that makes an equation true, or the values that satisfy several.
---

## One equation, one unknown

How long can the beam from [the beam example](../../start/beam/) span before it deflects more than L/360? Write the deflection as a function of the span, guess a span, and ask `root` for the span where the difference is zero:

```text
q := 12 kN/m
E := 210 GPa
I := 8360 cm^4
δ(L) := 5*q*L^4/(384*E*I)
L := 5 m
L_max := root(δ(L) - L/360, L) = 6.783 m
δ(L_max) = 18.842 mm
L_max/360 = 18.842 mm
```

`root(f, x)` starts from x's current value, the guess, and looks for a value of x where f is zero. The result has x's unit.

An equation can have several roots; the guess decides which one you get:

```text
x := 1
root(x^2 - 2, x) = 1.414
x := -1
root(x^2 - 2, x) = -1.414
```

To be sure of the interval, give it instead of a guess: `root(f, x, a, b)` finds a root between a and b, where f must change sign.

```text
root(δ(L) - L/360, L, 1 m, 20 m) = 6.783 m
```

For the roots of a polynomial, all at once, see `polyroots` in [Solve equations](../../guides/solve/#polynomials).

## Several equations: a solve block

Two numbers whose sum is 5 m and whose product is 6 m²:

```text
a := 1 m
b := 4 m
given
a + b == 5 m
a*b == 6 m^2
sol := find(a, b) = [2; 3]*m
```

1. Give each unknown a guess.
2. Type `given` and a space. Below it, write the conditions, using the bold equals **Ctrl+=** (written `==`), or `<`, `>`, `≤`, `≥`.
3. Finish with `find(a, b)`: the values that meet every condition, as a column.

To name the results one by one, put the names in a vector on the left:

```text
[a_1; b_1] := find(a, b)
a_1 = 2 m
```

Like `root`, `find` starts from the guesses, so they choose the solution: with `b := 1 m` too, it can't tell the two answers apart and reports that it found no solution. Inequalities help to steer it:

```text
c := 1
given
c^2 == 2
c > 0
find(c) = 1.414
```

When the conditions can't all be met, `minerr` gives the values that come closest, where `find` would report an error. For optimising (`Minimize`, `Maximize`), see [Solve equations](../../guides/solve/#the-best-values).
