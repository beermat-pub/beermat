# 2D math layout

> Status: **draft**, describing milestone 4 (a read-only view). Items marked † are guesses to verify.

The app draws the expression tree as 2D math with a small TeX-like box model (`ui/src/commonMain/kotlin/pub/beermat/app/math`, in the `ui` module that the desktop app and the web version share). Layout is separate from drawing: `MathLayout` builds boxes using a `Metrics` interface, so it is tested with fake metrics, and `SkiaMath` measures and paints with Skia.

## Font

STIX Two Math, bundled in `ui/src/commonMain/composeResources/files/fonts` with its licence, the SIL Open Font License 1.1. In the browser, which has no fonts of its own, the web version also brings Liberation Sans and Liberation Mono (OFL 1.1) for text and tables ([web.md](web.md#step-3-in-detail)). The OFL allows bundling the font with software under any licence; the font itself stays under the OFL (approved by Dan: fonts under the OFL are fine).

Variables are italic, multi-letter names too (drawn with Unicode's mathematical italic letters, which the font has). Units, numbers, built-in functions (`sin`, `rows`) and literal subscripts (`M_max`) are upright. A name counts as a unit as in [export.md](export.md): a built-in unit the sheet doesn't define; in a result's unit part every name is a unit. Gravity `g` is the exception: a physical constant, drawn italic, so it can't be mistaken for the gram, the upright `\rm g`. Program keywords (`if`, `for`, `try`, `on error`, …) are bold.

Decided with Dan: these are the defaults. A name can also be upright (`\rm x`, drawn upright) or bold (`\bf F`, drawn upright in the font's maths bold letters), each a different variable ([syntax.md](syntax.md) §3). Export: `\mathrm`, `\mathbf` (`\boldsymbol` for Greek) and MathML `mathvariant`.

## What each node looks like

| Linear | Drawn as |
|---|---|
| `a*b` | a · b (implicit multiplication `3 m`: a thin space) |
| `a/b` | a stacked fraction; nested fractions keep the full size |
| `x^2`, `M^T`, `M^<j>` | superscripts: 2, T, ⟨j⟩ |
| `M_max`, `v[i]` | subscripts |
| `sqrt(x)`, `nthroot(x, n)` | a radical sign, with the index n |
| `sum(e, k, a, b)`, `prod(…)` | ∑ or ∏ with the limits below and above; `sum(e, i)` with i below; `sum(v)` with none |
| `vec(e)` | an arrow over e |
| `\|x\|` | bars stretched to fit |
| `[1, 2; 3, 4]` | a grid in square brackets |
| `1.2e-3` | 1.2 × 10⁻³ |
| `==`, `!=`, `<=`, `>=` | a bold =, ≠, ≤, ≥ |
| `and`, `or`, `xor`, `not` | ∧, ∨, ⊕, ¬ |
| `:=`, `::=` | := and ≡ |
| programs | a vertical bar, one line per row, centred on the definition; `for` and `while` bodies indented with their own bar; an `if` or `otherwise` body with several lines as a nested bar to the left of the keyword |

Parentheses that only the linear form needs are dropped where the 2D structure already groups: around a numerator or denominator, an exponent, a radicand, an index, and a limit. Other parentheses the user wrote are kept.

Delimiters around a single line of text are the font's glyphs; around anything taller (fractions, matrices) they are drawn as strokes stretched to fit, centred on the math axis.

## Results

A result is drawn from its two parts in linear syntax (`ResultText.Parts`): the number (or matrix) and the unit, each parsed back and laid out, so `54 kN*m` shows as 54 kN·m and a matrix result as a grid followed by its unit. Errors are shown in red under the statement. The evaluator reports which variables a message quotes (`EvalException.names`), and those are drawn as in the sheet: in "*M*<sub>max</sub> has an error: *q* is not defined" both names are math, while the text form keeps the linear syntax (`M_max has an error: q is not defined`).

## The window (milestone 5)

`beermat-app [file.bbm]` (for now `./gradlew :app:run --args="file.bbm"`) shows the sheet as pages, with every region at its position, and edits it ([editor.md](editor.md)). The page model is `app/page`: `SheetLayout` evaluates and places the regions (auto-flowing those without a position) and paginates them, `PagePainter` draws them with Skia, and `Session` holds the editing state, so all of it is tested without a window. Math is 12 pt, text 11 pt; the screen shows 96 pixels per 72 points.

- `--png out.png` renders the pages to an image without a window;
- `--script "click 100 50; type x:3m«enter»"` (or `--script @steps.txt`) edits first, for checking the editor by eye;
- `--selftest keys.txt` opens the window and posts the keys to its own event queue, as a keyboard would: it checks the real key handling. With `--snapshot out.png`, once the keys are typed the app saves the sheet as it then stands as a PNG and closes, so the check needs no one watching the screen.
