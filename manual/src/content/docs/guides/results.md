---
title: Format results
description: Decimal places or significant figures, exponents, prefixes and units, for one region or the whole sheet.
---

How a result is shown never changes the calculation: a result shown as `1235` still carries 1234.567 into everything below it.

## One region

**Double-click a result** (or **Format → Result…** while editing a statement). The dialog sets:

- **Rounding**: decimal places or significant figures, how many, and, with decimals, whether to keep trailing zeros;
- **Exponent from 10^n**: the size from which numbers are shown with an exponent;
- **Unit**: the unit the result is shown in. Leave it blank for an automatic SI unit.

The rounding applies to every statement in that region; the unit only to the statement you clicked. **Sheet's format** takes the region back to the sheet's rounding.

## The whole sheet

The sheet's defaults are [settings](../../reference/settings/) at the top of the file:

```yaml
---
result:
  significant: 3
---
```

With that setting:

```text
a := 1234.567
a = 1235
b := 2.5 = 2.50
c := 0.0123456 = 0.0123
F := 54000 N = 54.0 kN
```

## Decimal places or significant figures

- **Decimal places** are the default, 3 of them: `1234.567`, `2.5`, `0.333`. Trailing zeros are dropped unless you ask for them.
- **Significant figures** keep trailing zeros, since they are significant: `2.50`. Digits left of the decimal point are never rounded away, so 1234.567 to 3 figures is `1235`. Tick **strict** (`strict: true`) to round them too: `1230`.

## Exponents

Numbers of a million or more, or smaller than a millionth, are shown with an exponent: 1.23 × 10⁶. Change the threshold with `exponential_threshold` (3 shows 1234.567 as 1.235 × 10³). A small number that would round to 0 gets an exponent instead.

## Units and prefixes

With no unit asked for, the result is in SI, with an engineering prefix that keeps the number between 1 and 1000: `54 kN*m`, `210 GPa`, `12 mm`. To see plain SI units instead, set `result: {prefixes: false}` for the sheet, or `prefixes=false` on one region. A moment is shown in `N*m`, never as an energy in J; type `J` in the placeholder when you mean energy.

Type any unit of the right kind in the result's placeholder to show it in that unit.
