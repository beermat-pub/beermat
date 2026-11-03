---
title: Units
description: Every built-in unit, by quantity.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

The seven SI base units are `m`, `kg`, `s`, `A`, `K`, `mol` and `cd`. Every other built-in unit is defined from them, below. A unit is a value like any other, so `kip := 1000 lbf` in a sheet adds one, and a definition of the same name as a unit replaces it in that sheet.

## Length

| Unit | Defined as |
|---|---|
| `mm` | `0.001 m` |
| `cm` | `0.01 m` |
| `km` | `1000 m` |
| `μm` | `1e-6 m` |
| `in` | `0.0254 m` |
| `ft` | `12 in` |
| `yd` | `3 ft` |
| `mi` | `5280 ft` |

## Time and frequency

| Unit | Defined as |
|---|---|
| `ms` | `0.001 s` |
| `min` | `60 s` |
| `sec` | `s` |
| `hr` | `60 min` |
| `mph` | `mi/hr` |
| `kph` | `km/hr` |
| `day` | `24 hr` |
| `week` | `7 day` |
| `yr` | `365.2422 day` |
| `Hz` | `1/s` |
| `kHz` | `1000 Hz` |
| `MHz` | `1e6 Hz` |

## Angle

Angles are dimensionless, unless a sheet makes them a dimension (`angle: dimension`), when `rad` is its unit.

| Unit | Defined as |
|---|---|
| `rad` | `1` |
| `deg` | `π/180` |
| `rev` | `2*π` |
| `gon` | `π/200` |

## Ratios

`%` is a hundredth: `ξ := 5 %`. It is written in backticks in the text, since `%` can't start a plain name.

| Unit | Defined as |
|---|---|
| `` `%` `` | `0.01` |

## Mass

`g` is gravity (below); the gram is the upright `\rm g`, drawn upright like every unit. `mg` is the milligram.

| Unit | Defined as |
|---|---|
| `\rm g` | `0.001 kg` |
| `mg` | `0.000001 kg` |
| `t` | `1000 kg` |
| `tonne` | `t` |
| `lb` | `0.45359237 kg` |
| `ton` | `2000 lb` |

## Temperature

`°C` and `°F` are points on a scale: `20 °C` is 293.15 K, and `= ? °C` shows a temperature on the scale. Here they are one degree; each scale's zero is built in. A temperature difference is `Δ°C` or `Δ°F`, and `R` is the rankine.

| Unit | Defined as |
|---|---|
| `°C` | `K` |
| `°F` | `5/9*K` |
| `Δ°C` | `K` |
| `Δ°F` | `5/9*K` |
| `R` | `5/9*K` |

## Acceleration

| Unit | Defined as |
|---|---|
| `g` | `9.80665 m/s^2` |

## Force

| Unit | Defined as |
|---|---|
| `N` | `kg*m/s^2` |
| `kN` | `1000 N` |
| `MN` | `1e6 N` |
| `GN` | `1e9 N` |
| `kgf` | `kg*g` |
| `tonnef` | `tonne*g` |
| `lbf` | `lb*g` |
| `kip` | `1000 lbf` |

## Pressure and stress

| Unit | Defined as |
|---|---|
| `Pa` | `N/m^2` |
| `kPa` | `1000 Pa` |
| `MPa` | `1e6 Pa` |
| `GPa` | `1e9 Pa` |
| `bar` | `1e5 Pa` |
| `atm` | `101325 Pa` |
| `psi` | `lbf/in^2` |
| `ksi` | `1000 psi` |

## Energy and power

| Unit | Defined as |
|---|---|
| `J` | `N*m` |
| `joule` | `J` |
| `kJ` | `1000 J` |
| `MJ` | `1e6 J` |
| `W` | `J/s` |
| `kW` | `1000 W` |
| `MW` | `1e6 W` |
| `BTU` | `1055.05585262 J` |
| `hp` | `550 ft*lbf/s` |

## Volume

| Unit | Defined as |
|---|---|
| `L` | `0.001 m^3` |
| `mL` | `0.001 L` |
| `gal` | `3.785411784 L` |

## Electrical

| Unit | Defined as |
|---|---|
| `mA` | `0.001 A` |
| `C` | `A*s` |
| `V` | `W/A` |
| `volt` | `V` |
| `kV` | `1000 V` |
| `Ω` | `V/A` |
| `ohm` | `Ω` |
| `S` | `1/Ω` |
| `F` | `C/V` |
| `Wb` | `V*s` |
| `T` | `Wb/m^2` |
| `H` | `Wb/A` |
| `mH` | `0.001*H` |

## Light, radiation, chemistry

| Unit | Defined as |
|---|---|
| `lm` | `cd` |
| `lx` | `lm/m^2` |
| `Bq` | `1/s` |
| `Gy` | `J/kg` |
| `Sv` | `J/kg` |
| `kat` | `mol/s` |
