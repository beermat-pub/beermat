# Beermat for AI assistants

You are connected to Beermat, an engineering worksheet: math laid out on a page, with real units, evaluated top to bottom. This guide is everything you need to read, check and edit worksheets well. The MCP `guide` tool returns it.

## The file

A worksheet (`.bbm`) is Markdown:

- math in ```` ```calc ```` fences, one statement per line;
- text as plain Markdown, plots in ```` ```plot ```` fences (YAML);
- `{#r1 x=40 y=100}` after a fence's name (or `<!-- #t1 x=… y=… -->` before text) gives a region's id and position in points. Regions evaluate top to bottom, then left to right.
- Results are cached after each statement: `#= 54 kN*m`, errors as `#! message`. Don't write these yourself: the tools compute them.

## The tools

- `read_worksheet` first: every region with its id or position (`calc 2`), and fresh results and errors.
- `evaluate` to try an expression in the sheet's context without changing anything (`q*L^2/8 = ? kN*m`, or symbolic: `diff(x^3, x) -> ?`).
- `update_region`, `insert_region`, `delete_region` to edit; they return the sheet with fresh results, so check them.
- `check` for errors and stale results; `export` for Markdown or LaTeX.
- In the app (HTTP), `path` can be left out: you work on the open sheet, live. The user sees your edits at once and saves them; Ctrl+Z undoes them. From the CLI (stdio), you edit the file.

## Statements

| Write | Means |
|---|---|
| `L := 6 m` | define (`::=` global, defined before everything else) |
| `L = ?` | evaluate; `L = ? mm` shows it in mm |
| `M := q*L^2/8 = ? kN*m` | define and show |
| `f(x) := x^2` | a function |
| `v[i] := i^2` with `i := 0..10` | an array element per value of a range |
| `expr -> ?`, `expr -> expand -> ?` | symbolic evaluation |
| `given` then comparisons then `s := find(x, y)` | a solve block (guesses: x and y's current values) |

## Expressions

- Operators: `+ - * / ^`, comparisons `== != < <= > >=` (`==` is equality; `=` only evaluates), `and or xor not`, `!` factorial.
- **Units multiply implicitly after a number**: `3 m`, `12 kN/m`, `2.5 kg*g`. Elsewhere write `*`.
- Calls `f(a, b)`; indices `v[i]`, `M[i, j]`; matrices `[1, 2; 3, 4]` (`;` between rows); columns `M^<j>`, transpose `M^T`.
- Ranges `0..10`, `0, 0.5..10`. `vec(expr)` applies element by element.
- Literal subscripts `M_max`. Greek as Unicode (`α`) or `\alpha`.
- `sqrt(x)`, `nthroot(x, n)`, `|x|`, `sum(e, i)`, `sum(e, k, 1, n)`, `prod(…)`.
- `diff(f, x)` and `int(f, x, a, b)`: numeric with `=`, symbolic with `->`. `root(f, x)` solves f = 0 from x's value; `root(f, x, a, b)` between limits.
- `try a on error b`: b if a fails.
- Name styles are different variables: `F`, bold `\bf F`, upright `\rm F`. `\it if` is a variable named like a keyword.
- Units to know: `g` is standard gravity; the gram is `\rm g`. `in` is the inch (except in `for k in`). A sheet may redefine a unit name (`L := 6 m` makes `L` a length).

## Programs

A definition ending in `:=` with indented lines below (2 spaces per level); the value is the last line run:

```calc
peak(v) :=
  k <- 0
  for j in 0..last(v)
    k <- j if v[j] > v[k]
  k
```

`<-` assigns locally; `x if cond`, `x otherwise`, `while cond`, `return x`, `break`, `continue`. No blank lines inside a program.

## Good practice

- Read before you write; keep the user's style, names and units.
- Prefer one statement per line and small regions; show results with `= ?` where the user would want to see them.
- After editing, look at the returned results: an error (`#!`) means something to fix.
- Explain what you changed in plain words; the user may not read the file.
