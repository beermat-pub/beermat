# Plots

> Status: **draft**, describing milestone 6: X-Y plots. Polar, contour and 3D plots come later.

A plot is a ```` ```plot ```` fence whose body is YAML; the expressions inside it use the [linear syntax](syntax.md). Like any region it has attributes: `{#p1 x=320 y=100 w=480 h=260}`. `w` and `h` are its size in points (default 420 × 260).

```plot {#p1 w=480 h=260}
x: 0 m .. L
x_unit: m
samples: 200
traces:
  - y: M(x)
    unit: kN*m
    label: Bending moment
  - y: V(x)
    unit: kN
    label: Shear
    axis: right
    style: { line: dash, color: red, marker: none }
axes:
  x: { min: 0, max: 6, grid: true, label: "x [m]" }
  y: { grid: true }
  y2: { }
```

## Inserting

`@` on an empty spot of the page, Insert → X-Y plot, or the `∿` button on the Insert palette puts a working example at the crosshair (sin x from 0 to 2π), opened as its YAML to change. Clicking a plot opens its YAML again; leaving it (Escape, or clicking elsewhere) draws it. A form for the settings may come later; the YAML is what the file holds.

## Evaluation

- A plot is evaluated where it sits in the sheet: it sees the definitions above it, like a statement there.
- **The x axis** names a variable and what it runs over:
  - `x: 0 m .. L`: the variable `x` from 0 m to L, sampled evenly at `samples` points (default 200), so curves are smooth;
  - `x: 0, 0.5..3`: a stepped range, at exactly those values;
  - `x: d`: the elements of a vector of data;
  - another variable name: `x: t = 0 s .. 10 s`.
- **Each trace's `y`** is evaluated at every x value with the variable bound to it. A point that fails (a division by zero, say) leaves a gap in the curve. If `y` doesn't use the variable and gives a vector as long as the x values, it is plotted point by point against them (data against data).
- **Units.** `x_unit` and each trace's `unit` set the display unit (checked against the values' dimension); without one, the automatic unit of [results.md](results.md) is used. The axis labels say which: `x [m]`, `Bending moment [kN*m]`.
- Traces with `axis: right` go on a second y axis with its own unit.
- `axes.*.min` and `max` are in the axis's display unit; without them the range fits the data, rounded out to the ticks.
- `style`: `line` is `solid`, `dash`, `dot` or `none`; `marker` is `none`, `circle` or `square`; `color` is a name (`blue`, `red`, `green`, `orange`, `purple`, `black`, `gray`) or `#rrggbb`, else from the palette.
- **`title`** (2026-10-29): a line above the plot, in bold.
- **Log axes** (2026-10-29): `log: true` on `axes.x`, `y` or `y2`. Ticks fall on the decades (1, 10, 100 …; with two decades or less also 2 and 5 times them; with many, every second or third decade), and lighter grid lines on 2 … 9 within each decade. Points at 0 or below are left out (a gap). A sampled x range (`x: 1 Hz .. 100 kHz`) on a log axis is spaced evenly in log, so each decade gets its share of the points. Limits must be above 0.
- **Tick spacing** (2026-10-29): `step: 0.5` on an axis puts the ticks that far apart, in its display unit (at most 200 ticks); the automatic ends round out to it.
- **Parametric curves** (2026-10-29): a trace with its own `x` draws (x, y) at each value of the plot's variable: with `x: t = 0 .. 2*π`, the trace `{ x: a*cos(t), y: b*sin(t) }` is an ellipse. The x axis takes its unit and label from the first trace's x; every trace's x must have the same units. `examples/plots.bbm` has one, and a log-log plot.

Problems (a missing `x`, a trace that fails everywhere, a unit of the wrong dimension) are shown under the plot and reported by `beermat check`.

## Not yet

- Exporting plots in Markdown and LaTeX (a placeholder line is written for now).
- Several x variables in one plot; equal scales on both axes (an ellipse is drawn as wide as the frame); a title or label in math rather than plain text.
