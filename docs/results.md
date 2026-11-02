# Showing results

> Status: **draft**. Items marked † are guesses to verify.

Display never changes the calculation: a result shown as `1230` still carries `1234.567` into everything that uses it.

## Unit

The unit typed in the placeholder if there is one. Otherwise, in SI (see [units.md](units.md#display)):

1. a named derived unit whose dimension matches exactly (N, Pa, W, C, V, Ω, F, H, T, Wb, S), except that **N·m is shown as `N*m`, not `J`**: moments are far more common than energies in engineering, and the dimension cannot tell them apart (type `= ? J` for an energy);
2. a derived unit times or over `m` or `s`, squared at most, when that is no longer than the base-unit form: `N/m` (not `kg/s^2`), `N*m^2`, `Pa*s`, `W/m^2`, `N*s`, `V/m`;
3. a derived unit squared or cubed: `N^2` (the determinant of a stiffness matrix);
4. the reciprocal of a named form: `1/N`, `m/N`, `1/(N*m)` (the elements of a flexibility matrix);
5. a product of base units: `m/s^2`, `kg/m^3`, `kg*m^2`.

Forms 2 to 4 are only used when the base-unit form has at least two factors.

Units that share a dimension with something else (Hz and Bq with 1/s; Gy and Sv with m²/s²) are never chosen automatically.

Then the first unit of that form gets an **engineering prefix** ([units.md](units.md#display), rule 3; `result.prefixes`, on by default): `54 kN*m`, `12 kN/m`, `210 GPa`, `12 mm`, `1500 mm^2`, `7.85 t/m^3`, `250 \rm g`. In detail (built 2026-11-02):

- The prefix is the largest one in the unit table that leaves the number at 1 or more; a number smaller than every prefixed form takes the smallest one (`0.5 N`, there being no `mN`). Past the largest, the largest (`5000 GPa`), then the exponent as usual.
- A power of the first unit takes the prefix to that power (`mm^2`); a fractional power, a reciprocal (`5000/s`), time (`s`), temperature (`K`) and dimensionless numbers never get one.
- An array is prefixed by its largest element, all elements in the one unit (`[0.5; 2.5]*kN`); arrays whose elements have their own units prefix each one (`[3 kN; 2 kN*m]`).
- Zero keeps the plain unit.
- It can be set for a sheet (`result: {prefixes: false}`) or for one region (`{prefixes=false}`), like the other `result.*` settings.

## Number

The examples in this section are below the exponential threshold (see below).

Two ways of rounding, set per worksheet (frontmatter `result.*`, see [file-format.md](file-format.md#frontmatter)) and per region (in the app, by double-clicking a result: [editor.md](editor.md#result-format)):

- **Decimal places** (the default): `result.decimals: 3` shows `1234.567`, `2.5`, `0.333`. Trailing zeros are dropped unless `result.trailing_zeros` is on.
- **Significant figures**: `result.significant: 3` shows `2.50`, `0.0123`, `10.0`, keeping trailing zeros because they are significant.
  - By default, digits left of the decimal point are never rounded away: 1234.567 to 3 figures shows `1235`. To 4 figures, `1235`; to 5, `1234.6`.
  - With `result.strict: true`, they are: 1234.567 to 3 figures shows `1230`.

Rounding is half away from zero, applied to the shortest decimal form of the number (so 2.675 to 2 decimals is `2.68`, as a person would expect, not `2.67` as binary floating point would give).

**Exponents.** Numbers of 10^n or more, or below 10^-n, are shown with an exponent, where n is `result.exponential_threshold` (default 6; 3 would turn 1234.567 into 1.235 × 10³). A non-zero number that would round to 0 also gets an exponent, so a strain of 0.000001 at 3 decimals shows as `1e-6`, not `0`. In text the form is `1.235e3`; the editor draws 1.235 × 10³. The rounding setting applies to the mantissa.

**Thresholds**: a value below 10^-15 shows as 0 (`zero_threshold`); in a complex number, a part smaller than 10^-10 of the other part is hidden (`complex_threshold`; confirmed by Dan, 2026-10-29). These only change what is shown, not the value, so they are separate from `TOL`, which is about comparing values.

## Text form

Used by the CLI and in the file's result cache (`#= …`): the number, a space, and the unit in linear syntax, e.g. `54 kN*m`, `11.54 mm`, `9.807 m/s^2`. The text always parses back as an expression, so two forms differ: a unit that starts with `1/` is joined as a division (`5/s`, `0.5/N`, not `5 1/s`), and a complex number or an array is joined with `*` (`(3 + 4i)*m`, `[1; 2]*m`), because only a plain number can be followed directly by a unit. A sticky unit is written exactly as the user typed it.

Arrays: `[1, 2; 3, 4]*m` when every element has the same unit, otherwise each element with its own (`[3000 N; 2000 N*m]`).
