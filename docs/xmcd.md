# `.xmcd` notes

> Status: **draft**. Milestone 8 (import) is implemented: `beermat import file.xmcd` (module `xmcd`), and File → Open in the desktop app (2026-11-03), which opens an `.xmcd` file converted, as a new sheet to save as the `.bbm` beside it (Save As when that exists already), listing what didn't come across; the same for an `.xmcd` file given to the app (Open With). The notes below came from a real private worksheet; committed tests use a small synthetic fixture (`xmcd/src/test/resources/synthetic.xmcd`).

Samples examined so far: one private worksheet (schema `worksheet30`, ~270 regions, no units). The newer `.xmcd` files share the 3.0 schema.

## Confirmed

- **Operators:** `<ml:apply>` with the operator element first, then the operands (`<ml:apply><ml:plus/>a b</ml:apply>`). This matches our `Apply(op, args)`.
- **Function calls:** `<ml:apply>` with an `<ml:id>` first and the arguments wrapped in `<ml:sequence>`. This matches our `Call(fn, args)`.
- **Parentheses** are explicit `<ml:parens>` nodes, as our `Parens` is.
- **Literal subscripts** are an attribute: `<ml:id subscript="max">M</ml:id>`, as our `Id(name, subscript)` is.
- **Number literals** are stored as text: `<ml:real>1.963E-05</ml:real>`, `<ml:real>-1</ml:real>`. A negative literal maps to `NEG(Num)`.
- **Results are stored** in the file: `<ml:eval>` holds the expression and a `<result>` element.

## Differences to resolve before import

- **Multiplication style.** `<ml:mult style="…"/>` (`auto-select`, `dot`, `x`, `no-space`, or absent) and the sheet's `<operators multiplication="…">` are imported since 2026-10-28 ([syntax.md](syntax.md#multiplication-style)).
- **Identifiers:** subscripts with `.` (`int_Ω.fo`), names with `∇` (`∇H`) or `*` (`θ*`) are kept as they were since 2026-10-28 ([syntax.md](syntax.md#more-characters-in-names)); `subscript=""` stays an empty subscript, a different name ([syntax.md](syntax.md#more-characters-in-names)).
- **Other element types:** every element met in the samples so far (`indexer`, `matrix`, `range`, `program` and its lines, `tryCatch`, `lambda`, `boundVars`, `derivative`, `integral`, `summation`, `transpose`, `matcol`) now has a node in our tree, except `symEval` (symbolic regions) and `provenance` (where a region was copied from, dropped).

## Import (milestone 8)

- **Areas:** a **collapsed** area stores its regions relative to itself, and what follows moves up over them; an expanded one stores them where they are (889 of 890 and 201 of 202 cases in Dan's files). The import rebuilds them as Beermat areas (`<!-- #a1 area … collapsed -->` … `<!-- /area -->`): a collapsed area's regions go below its line, and everything after it moves down by their height, so Beermat shows the page.
- **Excel components become tables** (2026-10-29, as agreed with Dan: [tables.md](tables.md#import-from-mcdx-and-xmcd)). The workbook's used cells make a `table` region at the component's place (the first row as the header when its filled cells are all text, else the columns' letters). Input cells take the sheet's values (`= D/mm`; an input written as a matrix fills its cells element by element, another vector as `v[ORIGIN + k]`). Formulas that use only their own row's cells or whole columns become expressions (`B3*C3^2/8` as `q*L^2/8`, `SUM(B2:B6)` as `sum(q)`); the rest keep the value Excel last calculated, with a warning naming the cell. Outputs are defined beside the table: from a whole numeric column (`q_Excel1`), so they follow it, otherwise from their values. Most of Dan's components are "Parameter | Value | Unit" lists whose formulas use other rows' cells, which tables can't express yet (cell references, a proposal for later).
- **Controls** (2026-10-29): list boxes, check boxes, radio buttons and text boxes are small web pages; the component's item holds their HTML in a `Contents` stream (plain bytes or UTF-16), with the state in it (`<OPTION value=LOW selected>`, `checked`). Each imports as its picture and its current value defined beside it: the selected option's value (a number where it reads as one: `α_A := 0.95`, else text: `SC := "LOW"`), 1 or 0 for a check box, the checked radio button's value, a text box's text. Not live: a control on Beermat's page is an optional feature for later.
- **Data tables and scripted controls** (2026-10-29): the data table (clsid-buddy `a6cfc4d6…`, or none at all in the oldest files) keeps its matrix in its OLE file's `Contents` stream (read through POI: the stream's sectors needn't be in order in the item), after a fixed marker (most of the GUID 31c98c54-d063-11d0-a68d-00a024cf05d1) and a header whose first fields are 1, 1, 8, 2: nine 32-bit numbers, the last three being the last column's index, the last row's and the count, then the doubles column by column (checked against the tables' pictures and the results saved in imported sheets). It imports as its matrix. Scripted controls (`01350056…` text box, `01350052…` list box) keep a script and a state, each in an OLE file: a text box's text is in its state (FF FE FF, a length byte, UTF-16); a list box saves no selection, but the usual script sets it from the input when the sheet loads, so it imports as that input. Push buttons (`01350041…`) run scripts that write files: not imported. A matrix drawn as a picture (`8442da2d…`) defines nothing and shows as its picture.
- **The output ranges** in the component's `Contents` stream are plain bytes in one family of Excel components and UTF-16 in the other (`01350091…`, the most common); both are read.
- **How a component is stored:** the binary item holds two OLE compound files, the component's own (a `Contents` stream listing the input cells, then the output ranges, such as `b2:b21`) and the workbook (a classic `.xls`, read with Apache POI). Each output is defined, in a math region beside the component's picture, from the values Excel last calculated in its range: a number or string for one cell, a matrix for a range. The formulas aren't run yet, so changed inputs don't reach the outputs; the note under the picture says so.
- **Regions** are taken in evaluation order: by the baseline (`align-y`, or the top in files without it), then left; so a fraction reaching up beside a definition comes after it. Each is written as a fence or text marker, with `x` and `y` from the region's `left` and `top`. `disable-calc` becomes the `disabled` attribute. Areas are flattened, their regions included (an area is a `<region>` holding an `<area>` of regions).
- **Worksheet options:** the sheet's own `ORIGIN` and `TOL` (`<builtInVariables array-origin="1" convergence-tolerance=…>`) become the frontmatter's `origin:` and `tolerance:` when they aren't the defaults (found by comparing with the results saved in imported sheets: 33 of 300 sheets count from 1).
- **Versions:** `math30`/`worksheet30` (the newest files), and `math20`, `math10` from older ones, which are read the same way.
- **Text regions** become Markdown: `Heading 1..3` styles become `#`..`###`, `<b>` and `<i>` become `**` and `*`, and math inside text becomes `` `code` `` in linear syntax.
- **Math** maps element for element:
  - `define`, `globalDefine` (`::=`), `eval` (the cached `<result>` is dropped and recomputed; `unitOverride` is the unit placeholder), a definition whose value is an `eval` as `x := … = ?`, `function` + `boundVars` as `f(x, y)`, a `matrix` of names as `[a; b] := …` and `matcol` as `M^<j> := …`;
  - `provenance` (where a region was copied from) is unwrapped wherever it appears; `Given` alone in a region is `given`; the solver elements `Find`, `Minerr` are `find`, `minerr` (the others keep their names, `Minimize`, `Odesolve` …, until the engine has them); `vectorSum` is `sum(v)`, `determinant` is `|M|`;
  - `apply` with an operator (`plus`, `minus`, `neg`, `mult`, `div`, `pow`, comparisons, `and`/`or`/`xor`/`not`, `absval`, `factorial`, `transpose`, `matcol`, `sqrt`, `nthRoot`, `indexer`, `vectorize`), or a function call with its arguments in a `sequence`;
  - `matrix` (stored **column by column**), `range` (a `sequence` first for a stepped range), `parens`, `str`, `real` (a leading `-` becomes negation);
  - `summation`, `product`, `integral`, `derivative` (a `lambda` for the variable and body, `bounds` for limits) as `sum(body, i, a, b)`, `prod(…)`, `int(…)`, `diff(body, x)`;
  - `program`, `localDefine`, `ifThen`, `otherwise`, `for`, `while`, `return`, `break`, `continue`; `tryCatch` as `try a on error b`, in the file's order (attempt, fallback).
- **Names are kept as they were**, with backticks in the text where a name needs them (`` `θ*`_max ``); `subscript=""` is kept (an empty subscript, a different name). Where a sheet defines the plain name and uses the empty-subscript one (it happens), those uses import as not defined. Backticks and line breaks, which a name can't hold, are removed.
- **Programs anywhere**, with the word `program` in their place ([syntax.md](syntax.md#a-program-inside-an-expression)); **symbolic evaluation** `→` with its keywords (`ml:command`: a keyword alone, or a `sequence` of the keyword and its arguments).
- **Plots, pictures and components** come in as the picture last drawn of them (each region's `rendering`, a PNG among the binary items), at their place and size, as picture regions. The source file keeps a plot's settings in its own binary serialisation (both in `math20` and `math30` files), so a live plot needs that format decoded: later. A component also gets a note naming the variables it defines.
- **Not imported yet** (reported as warnings, imported as `?` or a placeholder line): components (Excel tables and file readers, about half of Dan's files: the placeholder names the variables they define, which the sheet below then lacks), plots, pictures, symbolic evaluation `→` (kept as the expression), a program inside an expression (our syntax only allows one as a definition's value), and anything unknown.
- The private sample imports completely: 103 math regions, all parse back. Most remaining evaluation errors are missing features (`int`, `diff`, symbolic results).

## Known differences

Kept on purpose (2026-10-29), found by comparing imported sheets with the results saved in them:

- **A row vector to a power, in older sheets (`math20`).** These squared a row r = [a₁, a₂, a₃] as [a₁² + a₂² + a₃², a₁a₂ + a₂a₃, a₁a₃], its autocorrelation: `[1, 2, 3]^2` gave `[14, 8, 3]`. Beermat squares element by element, `[1, 4, 9]`, which is what such a formula means when each element is a separate case (Dan, 2026-10-29). Imported sheets that square a row vector show different numbers there, and in what follows from them.
- **A root among several.** `root(f, x)` may find another root than the one saved in the sheet from the same guess, both valid: the original steps can't be reproduced.

- **Sheets that reference another worksheet** (Insert Reference; Later, if ever: Dan, 2026-10-30). The import warns and leaves the reference out.

## Known gaps at 1.0

Left for 1.x (Dan, 2026-11-10: 1.0 goes ahead with them; about 98% of the results compared over his sheets agree):

- **Excel components' formulas** run only when they use their own row's cells or whole columns; the rest keep the value Excel last calculated, with a warning naming the cell, so changed inputs don't reach them.
- **Names defined in another order**: where a sheet's regions evaluate in an order that differs from the one Beermat reads them in (page position), a name used before its definition shows as not defined.
- **Some components and buttons aren't read** (file readers and controls beyond those listed above): their placeholder names the variables they define, which the sheet below then lacks.

## Still needed

- A sample that **uses units**, to see how units and the unit placeholder are stored.
