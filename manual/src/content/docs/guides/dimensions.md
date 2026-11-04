---
title: Angles and money as dimensions; your own units
description: Make Beermat check angles and currency as it checks lengths and forces, and define units of your own.
---

## Angles as a dimension

By default, an angle is a plain number in radians. For sheets where it matters (a rotational stiffness is not a moment), turn on **Format → Units → Angles as a dimension**, or write at the top of the file:

```yaml
---
angle: dimension
---
```

Then `rad` is a unit like the metre, and angles are checked:

```beermat errors angle=dimension
θ := 30 deg
θ = ? deg
asin(0.5) = ? deg
k_r := 20 kN*m/(0.01 rad) = ? MN*m/rad
x := 2 m + θ
```

- `sin`, `cos` and `tan` take an angle (or a plain number, read as radians); `asin`, `acos`, `atan` and `atan2` give one.
- An angle result is shown in degrees unless you ask for another unit; `angle_display: rad` shows radians instead.
- Formulas where the radian quietly disappears need it written. An arc length is `r*θ/rad`, and an angular frequency `2*π*f*rad`:

```beermat
r := 2 m
s := r*θ/rad = ?
f := 50 Hz
ω := 2*π*f*rad = ? rad/s
```

## Money as a dimension

Name your currency, with **Format → Units → Currency…**, or at the top of the file:

```yaml
---
currency: AUD
---
```

That name becomes a unit, and money is checked like anything else:

```beermat errors currency=AUD
cost := 120 AUD/m^3
V := 4 m^3
cost*V = ?
USD := 1.52 AUD
p := 100 USD = ?
y := 3 AUD + 2 m
```

Other currencies are definitions with your own rate; Beermat fetches no exchange rates. Money never takes prefixes (no "kAUD").

## Your own units

A unit is a value with a name, so a unit of your own is a definition:

```beermat
bag := 25 kg
cement := 12 bag = ?
```

It can be used below where it is defined, like any variable, and in a result's placeholder (`= ? bag`).
