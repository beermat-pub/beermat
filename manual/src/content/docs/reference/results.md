---
title: How results are shown
description: The rules for rounding results and choosing their units.
---

Showing a result never changes it: what is carried into the statements below is the full value, about 15 significant figures. To change how results look, see [Format results](../../guides/results/); the settings are in [Worksheet settings](../settings/).

## The unit

1. **The unit you typed** in the result's placeholder, if any. It must be of the right kind, or the result is an error.
2. Otherwise **an SI unit**, chosen in this order:
   - a named unit of exactly that kind: N, Pa, W, J, C, V, Ω, F, H, T, Wb, S. A moment, force times length, is shown as `N*m`, never as J;
   - a named unit with m or s: `N/m`, `N*m^2`, `Pa*s`, `W/m^2`;
   - a named unit squared or cubed, or one over a named unit: `N^2`, `1/N`, `m/N`;
   - base units: `m/s^2`, `kg/m^3`.

   Hz, Bq, Gy and Sv are never chosen on their own, since other things share their units.
3. Then an **engineering prefix**, so the number is between 1 and 1000: `54 kN*m`, `210 GPa`, `12 mm`, `7.85 t/m^3`, `250 \rm g`.
   - Only steps of a thousand (no cm), only prefixed units Beermat knows, and only on the first unit (`kN/m`, not `N/mm`).
   - Never on time, temperature, angles, US units, or plain numbers. Zero keeps the plain unit.
   - The prefix of an array is chosen for the whole array, from its largest element.
   - Turned off by `result: {prefixes: false}`.
4. A result without units shows none.

## The number

| Setting | Default | Example |
|---|---|---|
| Decimal places (`decimals`) | 3 | 1234.567, 2.5, 0.333 |
| Trailing zeros (`trailing_zeros`) | off | with it, 2.500 |
| Significant figures (`significant`) | off | to 3: 2.50, 0.0123, 1235 |
| Strict (`strict`), with significant figures | off | to 3: 1230 |
| Exponent from 10^n (`exponential_threshold`) | 6 | 1.23 × 10⁶ |

- Rounding is half away from zero, on the number as written in decimal: 2.675 to two places is 2.68.
- With significant figures, digits left of the decimal point stay unless **strict** is on.
- A number of 10^n or more, or below 10^−n, is shown with an exponent, and so is a small number that would otherwise round to 0.
- A value smaller than 10⁻¹⁵ is shown as 0; in a complex number, a part smaller than 10⁻¹⁰ of the other is hidden.

## Arrays and text

An array whose elements share a unit shows it once, after it: `[0; 40.5; 54]*kN*m`. Elements with units of their own each show theirs: `[10 kN; 2 kN*m]`. An array of more than 20 rows or columns is shown 20 at a time, and scrolls.

In a file and on the command line, a result is written in the [linear syntax](../syntax/), so it can be read back: `54 kN*m`, `9.807 m/s^2`, `5/s`, `(3 + 4i)*m`.
