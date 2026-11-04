---
title: Units
description: Typing units, showing results in the unit you want, and the mistakes units catch.
---

Every number in Beermat can carry a unit, and the units travel through the calculation with it.

```beermat
F := 2.5 kN
A := 25 mm*40 mm
σ := F/A = ?
```

## Typing units

A unit goes straight after its number: `6 m`, `12 kN/m`, `8360 cm^4`. In the editor you needn't type the space: `6m` becomes 6 m. A unit is just a name multiplied by the number, so compound units are written as you would on paper: `kN*m`, `m/s^2`, `kg/m^3`.

The [units reference](../../reference/units/) lists every built-in unit: SI with its common prefixes, US customary units, and a few others (`bar`, `t` for the tonne, `L` for the litre).

## Showing a result in another unit

With no unit asked for, Beermat picks one from SI, with the prefix that keeps the number readable:

```beermat
v := 90 km/hr
t := 2 s
a := v/t = ?
```

To see a result in a unit of your own, type the unit after the `=`. It goes into the placeholder at the end of the result and stays there:

```beermat
σ = ? psi
v = ?
E_day := 3 kW*8 hr = ? kW*hr
E_day = ? MJ
```

Any unit of the right kind works; the calculation inside doesn't change, only what is shown.

## The mistakes units catch

Adding a length to a time is almost always a typo or a missing term. Beermat refuses it and says why:

```beermat errors
x := 3 m + 2 s
```

The letters are the dimensions: L length, M mass, T time, I current, Θ temperature, N amount of substance, J luminous intensity. A wrong unit in the placeholder is caught the same way:

```beermat errors
σ = ? kN
```

Things of the same kind mix freely: `3 m + 250 mm` is `3.25 m`.

## Gravity, grams and other clashes

A few unit names are also common variable names. Beermat settles them like this:

- `g` is standard gravity, 9.80665 m/s². The gram is the upright g, a different name: type `g`, then **Ctrl+I**, which turns a plain (italic) name upright. In a file it is written `\rm g` ([name styles](../../reference/syntax/#name-styles)). `kg` and `mg` are as usual.
- `L` is the litre, `t` the tonne, `m` the metre, `s` the second.

You can still use these names for your own variables: `L := 6 m` makes `L` the span from there on. But a unit you redefine is no longer that unit below it. After `m := 5`, `3 m` is 15, not three metres. If a result suddenly looks wrong by a large factor, look for a variable named like a unit.

```beermat
m := 2 kg
w := m*g = ?
```

## Angles

Angles are plain numbers in radians, and `deg` is π/180:

```beermat
θ := 30 deg
sin(θ) = ?
θ = ?
θ = ? deg
```

To make angles a dimension of their own, so that a rotation can't be added to a length, see [Angles and money as dimensions](../../guides/dimensions/).

## Temperatures

`°C` and `°F` are scales: `20 °C` is the temperature 293.15 K. A difference of temperatures is in kelvin, or `Δ°C`:

```beermat
T := 20 °C
T = ?
T = ? °C
T = ? °F
ΔT := 30 °C - 20 °C = ?
ΔT = ? Δ°C
```

Inside a compound unit, `°C` is a degree of difference: a conductivity in `W/(m*°C)` means what it says.

## Your own units

A unit of your own is a definition like any other, usable below it:

```beermat
bag := 25 kg
cement := 12 bag = ?
```

## Taking the unit off

To use a value where a plain number is needed (in a lookup table, or a formula from a code that assumes units), divide by the unit:

```beermat new
v := 12 kN/m
v/(1 kN/m) = ?
```
