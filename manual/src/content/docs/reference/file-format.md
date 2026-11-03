---
title: The file format
description: What a .bbm worksheet file holds, for reading and writing it by hand or by program.
---

A worksheet is a Markdown file, saved as `.bbm` (or `.bbm.md`, which Markdown viewers such as GitHub show without any setup). It reads as a tidy document in any Markdown viewer, and can be written by hand, by a script or by an AI assistant: Beermat fills in what is missing the next time it saves.

````markdown
---
beermat: 1
title: Beam check
result:
  significant: 3
results: 7d4621da81717985
---

# Beam check

<!-- #t1 x=40 y=40 w=500 -->
Simply supported beam, uniform load **q** over span **L**.

```calc {#r1 x=40 y=100}
L := 6 m
q := 12 kN/m
```

```calc {#r2 x=40 y=160}
M_max := q*L^2/8 = ? kN*m   #= 54.0 kN*m
```

```plot {#p1 x=320 y=100 w=300 h=200}
x: 0 m .. L
traces:
  - y: q*x*(L - x)/2
    unit: kN*m
```
````

## Settings

The YAML block between `---` lines at the top holds the sheet's [settings](../settings/). All are optional.

## Regions

| Region | Written as |
|---|---|
| Math | A ```` ```calc ```` fence, one statement per line in the [linear syntax](../syntax/); a program's lines are indented below its first |
| Text | Plain Markdown, after a `<!-- #t1 … -->` marker (a heading needs none) |
| Plot | A ```` ```plot ```` fence holding the plot's settings in YAML ([Plots](../../tutorials/plots/)) |
| Table | A ```` ```table ```` fence holding a Markdown table ([Tables](../../guides/tables/)) |
| Picture | `![alt][i1]` after a `<!-- #i1 … -->` marker, with the picture's data in a line `[i1]: data:image/png;base64,…` at the end of the file |

Each region's attributes go in braces after the fence's name, or in the marker:

| Attribute | Means |
|---|---|
| `#r1` | The region's id (`r` math, `t` text, `p` plot, `i` picture). Beermat adds one if it is missing |
| `x=40 y=100` | Its position on the page, in points (1/72 inch) from the top-left of the page area |
| `w=300 h=200` | Its size, for text, plots, tables and pictures |
| `disabled` | Not worked out |
| `decimals=2`, `significant=3`, `prefixes=false`, … | The region's own [result format](../../guides/results/) |
| `name=Loads` | A table's name |

Without a position, a region goes below the one before it, so a file written with no positions at all becomes one tidy column.

A page break is `<!-- pagebreak y=700 -->`. An area is everything between `<!-- #a1 area y=720 title="Load cases" collapsed -->` and `<!-- /area -->`.

## Results

Each result is kept at the end of its line, after `#=`, and each error after `#!`:

```calc
M_max := q*L^2/8 = ? kN*m   #= 54 kN*m
y := L + 2 s                #! units do not match: L vs T
```

So the file reads as a finished calculation, even where Beermat isn't installed. The statement itself is never changed: the unit you asked for stays after the `?`.

The `results:` line in the settings is a fingerprint of everything else in the file. If anything changes outside Beermat (by hand, by a script, by a merge), the fingerprint no longer matches, and every kept result is worked out again rather than trusted. [`beermat check`](../command-line/) reports results that are out of date; `beermat eval --write` brings them up to date.

Don't write `#=` lines yourself: Beermat writes them.

## Order and form

- Beermat saves regions in the order they are worked out (top to bottom, then left to right), so reading the file from the top follows the calculation.
- Saving an unchanged sheet gives an identical file: the spacing and order are always the same, so changes show clearly in a diff.
- The file is UTF-8 text with LF line endings.
