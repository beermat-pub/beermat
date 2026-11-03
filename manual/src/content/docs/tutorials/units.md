---
title: Units
description: Typing units, showing results in the unit you want, and the mistakes units catch.
---

Every number in Beermat can carry a unit, and the units travel through the calculation with it.

```text
F := 2.5 kN
A := 25 mm*40 mm
σ := F/A = 2.5 MPa
```

## Typing units

A unit goes straight after its number: `6 m`, `12 kN/m`, `8360 cm^4`. In the editor you needn't type the space: `6m` becomes 6 m. A unit is just a name multiplied by the number, so compound units are written as you would on paper: `kN*m`, `m/s^2`, `kg/m^3`.

The [units reference](../../reference/units/) lists every built-in unit: SI with its common prefixes, US customary units, and a few others (`bar`, `t` for the tonne, `L` for the litre).

## Showing a result in another unit

With no unit asked for, Beermat picks one from SI, with the prefix that keeps the number readable:

```text
v := 90 km/hr
t := 2 s
a := v/t = 12.5 m/s^2
```

To see a result in a unit of your own, type the unit after the `=`. It goes into the placeholder at the end of the result and stays there:

```text
σ = 362.594 psi
v = 25 m/s
E_day := 3 kW*8 hr = 24 kW*hr
E_day = 86.4 MJ
```

Any unit of the right kind works; the calculation inside doesn't change, only what is shown.

## The mistakes units catch

Adding a length to a time is almost always a typo or a missing term. Beermat refuses it and says why:

```text
x := 3 m + 2 s      units do not match: L vs T
```

The letters are the dimensions: L length, M mass, T time, I current, Θ temperature, N amount of substance, J luminous intensity. A wrong unit in the placeholder is caught the same way:

```text
σ = ? kN            units do not match: the result is L^-1·M·T^-2, the unit is L·M·T^-2
```

Things of the same kind mix freely: `3 m + 250 mm` is `3.25 m`.

## Gravity, grams and other clashes

A few unit names are also common variable names. Beermat settles them like this:

- `g` is standard gravity, 9.80665 m/s². The gram is the upright g, a different name: type `g`, then **Ctrl+I**, which turns a plain (italic) name upright. In a file it is written `\rm g` ([name styles](../../reference/syntax/#name-styles)). `kg` and `mg` are as usual.
- `L` is the litre, `t` the tonne, `m` the metre, `s` the second.

You can still use these names for your own variables: `L := 6 m` makes `L` the span from there on. But a unit you redefine is no longer that unit below it. After `m := 5`, `3 m` is 15, not three metres. If a result suddenly looks wrong by a large factor, look for a variable named like a unit.

```text
m := 2 kg
w := m*g = 19.613 N
```

## Angles

Angles are plain numbers in radians, and `deg` is π/180:

```text
θ := 30 deg
sin(θ) = 0.5
θ = 0.524
θ = 30 deg
```

To make angles a dimension of their own, so that a rotation can't be added to a length, see [Angles and money as dimensions](../../guides/dimensions/).

## Temperatures

`°C` and `°F` are scales: `20 °C` is the temperature 293.15 K. A difference of temperatures is in kelvin, or `Δ°C`:

```text
T := 20 °C
T = 293.15 K
T = 20 °C
T = 68 °F
ΔT := 30 °C - 20 °C = 10 K
ΔT = 10 Δ°C
```

Inside a compound unit, `°C` is a degree of difference: a conductivity in `W/(m*°C)` means what it says.

## Your own units

A unit of your own is a definition like any other, usable below it:

```text
bag := 25 kg
cement := 12 bag = 300 kg
```

## Taking the unit off

To use a value where a plain number is needed (in a lookup table, or a formula from a code that assumes units), divide by the unit:

```text
v := 12 kN/m
v/(1 kN/m) = 12
```
