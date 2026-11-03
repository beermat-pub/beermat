---
title: Units are values
description: Why a unit is just a named value, and what follows from that.
---

In Beermat, a unit is not a label stuck to a number. It is a value with a name: `kN` is 1000 newtons, `mm` is 0.001 metres. Writing `12 kN/m` multiplies 12 by the value `kN` and divides by `m`, as on paper.

## What this gives you

- **Conversions are just arithmetic.** `2 ft + 3 in` is a length, shown in whatever unit you ask for: `685.8 mm`.
- **Checks come for free.** Every value knows its dimensions (how much length, mass, time… it has), and adding things of different dimensions is an error. A missing term or a wrong exponent shows up at once.
- **Results find their own units.** `q*L^2/8` with q in kN/m and L in m is a moment, so it is shown in kN·m, without your saying so.
- **Your own units are definitions.** `bag := 25 kg` makes a unit, used like any other.

## How values are kept

Inside, every value is kept in SI base units: `3 mm` is 0.003 with the dimension of a length. The unit you see is chosen only when a result is shown, so changing it never changes the calculation.

## What it asks of you

Because units are names, a variable can hide one. After `m := 5`, `m` is no longer the metre for the statements below, so `3 m` is 15. The built-in names that clash most are `m`, `s`, `g` (gravity), `L` (litre), `t` (tonne), `A`, `C`, `F`, `N`, `S`, `T`, `V` and `W`. Defining one as a variable is fine when you don't need the unit below it; choose another name when you do.

A value with units can't go where a plain number is needed (`sin`, `ln`, an exponent, a Python function without annotations): divide by its unit first, `v/(1 kN/m)`.

Angles are plain numbers by default, with `rad` = 1 and `deg` = π/180. A sheet can make angles, and money, dimensions of their own: [Angles and money](../../guides/dimensions/).
