# Units

> Status: **draft**.

## Model

- Every numeric value carries a **dimension**: a vector of exponents over the base dimensions
  **L** (length), **M** (mass), **T** (time), **I** (current), **Θ** (temperature), **N** (amount), **J** (luminosity).
  **Angle** and **currency** can be added as two more, per sheet † ([below](#angle-and-currency-as-dimensions)).
- Exponents are **rationals**, so `sqrt(m)` is valid (L^1/2). It is rare but needed for things like fracture toughness (MPa·√m).
- Values are stored internally **in SI base units**. `3 mm` is stored as `0.003` with dimension L.
- **A unit is just a named value.** `kN` is the value `1000` with dimension M·L·T⁻². `3 kN` is ordinary multiplication. This model keeps the evaluator simple: no special unit-handling code paths beyond dimension arithmetic.

## Dimension rules

| Operation | Rule |
|---|---|
| `a + b`, `a - b`, comparisons | Dimensions must match, otherwise "units do not match" |
| `a * b`, `a / b` | Dimensions add / subtract |
| `a ^ n` | If `a` has a dimension, `n` must be dimensionless and real; dimension × n (any real n: `D^1.852` is allowed, its dimension the nearest fraction) |
| `sqrt`, `root` | Divide the exponents |
| `sin`, `exp`, `ln`, … | Argument must be dimensionless (angles count as dimensionless, see below) |
| `abs`, `max`, `min`, `sum` | Preserve the dimension; elements must match, but a bare 0 in `max` and `min` takes the others' unit (`max(0, x)`) |
| Matrices | Each element has its own unit, so `[F; M]` can hold a force and a moment. Arithmetic checks units element by element; see [evaluation.md](evaluation.md#arrays) |
| A bare 0 | A zero matches any dimension: `x > 0` for a length, and `0 + F` is F, so a program can start an array with zeros and collect forces in it (found importing `.xmcd` sheets, 2026-10-29). So does a zero that picked up a unit on the way, `0/M_p + 1` with a moment of 0 (2026-10-31, from saved results †). Only an exact 0: `1 m + 1e-9` is still an error. In arrays, since `.xmcd` arrays shared one unit: a bare 0 put into an element that has a unit keeps that unit (`v[1] <- 0` in an array of lengths is 0 m), a bare 0 written in an array beside elements that share one unit takes it (`[0; T - T_amb]` is a column of temperature differences; 2026-10-31), and the data of `linterp`, `max`, `min` take any 0 as their unit, whatever unit it picked up on the way (0/MN) |

## Display

When a result is evaluated:

1. **If the user typed a unit in the placeholder** (`M = ? kN*m`), the value is divided by that unit and displayed with it. The dimensions must match, or it is an error.
2. **Otherwise** the worksheet's unit system (frontmatter `units:`) picks a unit: a named derived unit, a derived unit combined with `m` or `s` (`N/m`, `Pa*s`), or base units. Moments show as `N*m`, not `J`. The exact rules are in [results.md](results.md#unit).
3. **Engineering prefixes** (Dan, 2026-10-29): with no unit asked for, the unit gets the prefix that puts the number between 1 and 1000: `54000 N·m` shows as `54 kN·m`, `2.1e11 Pa` as `210 GPa`, `0.012 m` as `12 mm`. The frontmatter `result.prefixes: false` turns it off (the `.xmcd` import leaves it on).
   - Only steps of a thousand (no `cm`, `dm`), and only prefixed units in the built-in table, so a result never shows a unit the user couldn't type. If the right one isn't there, the next one that is, or the plain unit with an exponent as before.
   - The prefix goes on the first unit of a compound (`kN·m`, `kN/m`, `MPa`), never on a denominator alone.
   - Mass uses grams' prefixes up to `kg`, then `t` (tonne). Time, angles, temperatures and USCS units never get prefixes; neither do dimensionless results.
   - A unit typed in the placeholder always wins.
4. **Dimensionless results** show no unit.

The unit placeholder is **sticky**: it is stored in the file (`= ? kN*m`), so it survives editing.

## Built-in unit library

Starts with the built-in set that `.xmcd` files use. The table lives in [engine/src/commonMain/resources/pub/beermat/engine/units.bbm](../engine/src/commonMain/resources/pub/beermat/engine/units.bbm) and covers:

- **SI base and derived:** m, kg, s, A, K, mol, cd, N, Pa, J, W, C, V, Ω (`ohm`), F, H, T, Wb, Hz, S, lm, lx, Gy, Sv, Bq, kat.
- **Prefixed forms:** generated from prefixes (`k`, `M`, `G`, `m`, `μ`/`u`, `n`, `c`, `d`, …), but **only for a curated list** (mm, cm, km, kN, MN, GN, kPa, MPa, GPa, kJ, MJ, kW, MW, mA, μm, ns, …), so that every identifier doesn't become a unit and names don't collide with user variables.
- **Time:** min, sec, hr, day, week, yr. `min` and `sec` are also functions (`min(a, b)`, the secant `sec(x)`): a call means the function, unless the sheet redefined the name.
- **Angles:** rad, deg, `°`, rev, gon.
- **USCS:** in, ft, yd, mi, mph, lbf, kip, lb (`lbm`), ton (the short ton, 2000 lb), psi, ksi, ft·lbf, BTU, hp, gal, °F/R.
- **Other:** kph, L (litre), mL, bar, atm, t (tonne), `\rm g` (gram), mg, kgf, g (standard gravity).

**Names that clash with common variables** were decided as follows:

- **`g` is standard gravity**, 9.80665 m/s². **The gram is `\rm g`**: the upright g, a different name ([syntax.md](syntax.md) §3), drawn upright like every unit, so `250 \rm g` reads as 250 g. Gravity is drawn italic, as a physical constant, so the two never look alike. `mg` is the milligram.
- **`L` is the litre.**
- **`t` is the tonne** (`.xmcd` files write `mt`).

Name clashes (`m`, `g`, `L`, `t`, `A`, `C`, `F`, `S`, `T`, `V`, `W`, `s`) are the main usability problem with units-as-variables. The rule (implemented):

> **User definitions shadow units silently in their scope; using a shadowed unit name *in a unit placeholder* still refers to the unit.** The checker warns when a user definition shadows a built-in unit.

## Angles

By default angles are dimensionless (rad = 1): `deg = π/180`. `sin(30 deg)` works. Results that are angles show as plain numbers in radians unless the user puts `deg` in the placeholder.

### Angle and currency as dimensions

Decided by Dan on 2026-10-29: both can be turned on per sheet, as extra dimensions beside the seven. Both are off by default, so ordinary sheets work.

**Angle** (`angle: dimension` in the frontmatter; Format → Units):

- `rad` is the base unit of an angle dimension, and `deg`, `°`, `rev`, `gon` are angles. So an angle can't be added to a length, torque (N·m) and energy (J) still match, but a moment and a rotation stiffness (N·m/rad) don't.
- `sin`, `cos`, `tan` take an angle, or a plain number read as radians; `asin`, `acos`, `atan`, `atan2`, `arg` return angles. An angle result shows in `deg` or `rad` as the frontmatter `angle_display` says (`deg` by default).
- The cost, and the reason it's off by default: formulas where the radian silently disappears need it removed by hand. Arc length `s := r θ` is `m·rad`, written `r θ / rad`; `ω := 2 π f` in Hz needs `rad` added. The error message says so.

**Currency** (`currency: AUD` in the frontmatter, any name):

- A currency dimension whose base unit has that name (`AUD`, `€`, `$`). Other currencies are ordinary definitions with the user's rate, `USD := 1.52 AUD`: no exchange rates from the internet.
- Then `cost := 120 AUD/m^3` and `V := 4 m^3` give `cost V = 480 AUD`, and adding money to a length is an error. Prefixes never apply (no "kAUD").
- Without `currency`, money is plain numbers.

## Temperature

Beermat has `K`, `R`, `°C`, `°F` and `Δ°C`, `Δ°F` (2026-10-29). The problem is that a temperature on a scale is affine: an offset, not just a scale factor.

- **`°C` and `°F` are scales**, so a temperature written with them is absolute: `20 °C` is 293.15 K, `-40 °F` is 233.15 K. Whatever multiplies them must be a plain number, or an array of them (`[0; 100] °C`, `T °C` with `T := 25`); a minus sign belongs to the number, since unary minus binds tighter than multiplication.
- **Shown on a scale:** `T = ? °C` shows `T − 273.15 K` in degrees (`68 °F` for 20 °C). Plot axes (`unit: °C`) and table columns (`T [°C]`) work the same way, through `Evaluator.displayUnit`.
- **`°C(20)`**, the scale used as a function, is the same as `20 °C` (and `°F(x)`).
- **`%`** is a built-in unit, a hundredth (`5 %` is 0.05); it is written in backticks in the text (`` 5*`%` ``).
- **Differences** are `Δ°C` (the kelvin) and `Δ°F` (5/9 K, the same size as `R`, the rankine). The difference of two temperatures is in K: `30 °C − 20 °C` is `10 K`, or `= ? Δ°C`.
- **Inside a product, quotient or power, even after a number**, `°C` is one degree as well: `100*°C/(1.85*km)` is a gradient of 100 degrees per 1.85 km, as Dan's sheets computed it (checked against the results saved in imported sheets, 2026-10-29). A function's arguments start afresh, so `f(20 °C)` passes 293.15 K.
- **`°C` anywhere else is one degree** (changed 2026-10-29, after importing 2,204 of Dan's `.xmcd` files: 260 of them write `α := 12.7·10⁻⁶ °C⁻¹` or `W/(m·°C)`, which their saved results evidently read this way). So `°C^-1` is `1/K`, and `2 m °C` (after a quantity with units, not a plain number) is `2 m·K`. Only `number °C` is on the scale. The first rule, an error pointing to `Δ°C`, would have broken all those sheets. A sum like `20 °C + 20 °C` is just 586.3 K, with no warning: nothing in the engine has warnings yet.
- `°` counts as a letter in names ([syntax.md](syntax.md#more-characters-in-names)), so `°C`, `Δ°C` and `20°C` need no backticks. A sheet that defines its own `°C` gets an ordinary variable; older sheets write `°C := Δ°C` (or `°C ::= K`), making it a plain degree everywhere, as it was before there were scales, a result placeholder in `°C` included (`= ? °C` then shows the plain degree; any other unit name in a placeholder still means the built-in unit).
- Each scale's zero is in code (`Builtins.temperatureZeros`), one degree of it in the unit table.
- † How a `.xmcd` file names these units is still to check, with a sample that uses them.

## Custom units

```
kip := 1000 lbf
kN/m^2 is just an expression
```

A custom unit is just a definition (Dan, 2026-10-29): it can be made or redefined anywhere in the sheet, and is usable below where it is defined, like any variable. Not a frontmatter setting. *(Open: mark definitions as "units" so they render in the unit style and can appear in display simplification?)*

## Implementation notes

- `Rational`: an exact `Int` fraction that throws on overflow. `Rational.approximate` turns a computed exponent (`0.333…`) back into `1/3`; a value with units raised to any other real power (`D^1.852`, as empirical formulas do) gets the closest fraction with a denominator up to 10000 in its dimension, while the value uses the exponent as it is (2026-10-30).
- `Dimension`: immutable, with 7 `Rational` exponents.
- `Quantity`: `(value: Complex, dimension: Dimension)` in `pub.beermat.engine.units`. Real values take a plain `Double` path so they stay exactly real. Matrices will carry a single `Dimension`.
- The unit table is data, not code, so it is easy to extend and audit. It is a resource written **in the linear syntax** (`N := kg*m/s^2`, `kN := 1000 N`) and evaluated once at startup by the engine itself. Only the seven SI base units are defined in code.
- Every rule above gets a unit test before the evaluator uses it.
