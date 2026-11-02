# Linear math syntax

> Status: **draft**. This is the most load-bearing document in the project: the file format, CLI, MCP tools and editor all go through it.

The linear syntax is the plain-text form of a math statement. It is designed to be:

- **readable** by people and LLMs without the app,
- **typeable** by hand (ASCII-only must always be possible),
- **lossless**: every AST round-trips through text exactly (`parse(print(ast)) == ast`),
- **close to familiar worksheet keystrokes** wherever that doesn't hurt readability.

The editor never shows this syntax; it shows 2D math. The syntax is what gets stored and exchanged.

---

## 1. Statements

Each statement is one line (program blocks continue over indented lines, see §8).

| Kind | Syntax | Shown as | Keystroke |
|---|---|---|---|
| Definition | `x := 3 m` | x := 3 m | `:` |
| Global definition | `g ::= 9.81 m/s^2` | g ≡ 9.81 m/s² | `~` |
| Evaluation | `x = ?` | x = 3 m | `=` |
| Evaluation in a unit | `x = ? mm` | x = 3000 mm | `=` then type in the unit placeholder |
| Definition + evaluation | `M := q*L^2/8 = ? kN*m` | M := q·L²/8 = 54 kN·m | |
| Symbolic evaluation | `diff(x^3, x) -> ?` | d/dx x³ → 3·x² | `Ctrl+.` |
| Symbolic with keyword | `x^2 - 4 -> solve(x) -> ?` | x² − 4 solve, x → (−2, 2) | |
| Expression only | `x + y` | x + y | (an unevaluated expression, e.g. still being typed) |

`?` is a **placeholder**: an empty slot. In an evaluation it marks where the result goes; anywhere else it's an incomplete expression (an error, drawn as a black ■). A definition's name can be empty too, `? := 2`, as the editor makes it when `:` or a program starts on an empty spot; it defines nothing and shows "this definition needs a name".

Global definitions (`::=`) are evaluated in a first pass over the whole sheet before normal definitions.

## 2. Literals

| Literal | Examples | Notes |
|---|---|---|
| Real | `3`, `3.5`, `.5`, `1.2e-3`, `1.2E+3` | |
| Imaginary | `2i`, `2j`, `1i` | A bare `i` is an identifier; the imaginary unit is written `1i` |
| Hex/octal/binary | `0x1F`, `0o17`, `0b101`, `0b101.01` | Prefixes, as in Python, Kotlin and most languages (Dan, 2026-10-29), rather than the suffixes of `.xmcd` files (`1Fh`, `17o`, `101b`, their `base` attribute), which the import turns into these. A fraction is allowed. Results still show in decimal. Built 2026-10-29 |
| String | `"hello"` | Escapes: `\"`, `\\`, `\n` |
| Matrix | `[1, 2; 3, 4]` | `,` separates columns, `;` separates rows |
| Column vector | `[1; 2; 3]` | |
| Row vector | `[1, 2, 3]` | |

## 3. Identifiers

- Letters, digits, `_` and `'` (prime, as in `f'`). Must start with a letter.
- **Unicode letters are allowed and preferred for Greek:** `α := 30 deg`. The file stores Unicode.
- **ASCII escapes for Greek:** `\alpha`, `\Delta`, … parse to the Unicode letter and are never written back. This keeps ASCII-only typing possible without making `alpha` a reserved word. The escape ends where the name ends, so `\Deltax` is `Δx` (no Greek name is a prefix of another, so this is never ambiguous).
- **Literal subscript** (typed `.`): `M_max` displays as M with a "max" subscript. Everything after the first `_` is the subscript. `x_1` is an identifier, not an index. The tree stores the subscript as a separate field of the identifier, as `.xmcd`'s `ml:id` does.
- Case-sensitive: `F` and `f` are different.
- **Styles.** A name is plain (drawn italic), upright or bold, and each style is a different variable, as case is: `F`, `\rm F` and `\bf F` can hold three values. The prefix is followed by a space and applies to that one name, subscript included (`\bf F_max`, `\bf \alpha`). Messages write names in this form, `\bf F is not defined`; the app draws every name a message quotes as it is drawn in the sheet (see [layout.md](layout.md)).
  - Built-in names (units, functions, `π`, `ORIGIN`) are plain, with one exception: the gram is `\rm g`, because plain `g` is gravity ([units.md](units.md)). So `\rm m` is a variable, not the metre.
  - A marked name is never a keyword, so `\bf if` and `\bf and` are variables.
  - **`\it` marks a plain name** (`\it x` is just `x`), for a variable named like a keyword: `\it and := 2`. `and`, `or`, `xor`, `not` and `try` always need it. Program words (`if`, `otherwise`, `for`, `while`, `return`, `break`, `continue`) are keywords only in some places, and `if` is also the built-in `if(c, a, b)`, so they need it only where they would be read as a keyword: the printer writes `\it break` at the start of a program line, and `\it if` always works.
  - The tree stores the style as a field of the identifier. † How `.xmcd` stores math styles is to be found from a sample, for import.
- **Built-in units are ordinary identifiers** in a built-in scope (`m`, `kN`, `s`, `deg`). A user may shadow one (`m := 5`). The checker warns when that happens, because it is a classic trap.

### More characters in names

Decided by Dan on 2026-10-28: every name a `.xmcd` file uses comes through as it was, never renamed.

- **`∇`, `∂` and `°` count as letters**, so `∇H`, `∂u` and `°C` are plain names (`°` since 2026-10-29, for the temperature scales: [units.md](units.md#temperature)).
- **A subscript may contain `.`** between its characters: `int_Ω.fo`. A `.` with no name character after it ends the subscript (`a_b*.5`). In the editor, `.` typed inside a subscript stays a `.`.
- **Anything else goes in backticks:** `` `θ*` ``, `` `x%` ``, `` `a/b` `` ("special text mode" names, typed in the editor with `Ctrl+Shift+K`). The backticks quote the name, and a subscript still follows `_` (`` `θ*`_max ``, `` M_`a+b` ``). A quoted name is never a keyword (`` `and` ``). The printer adds backticks only when a name needs them, and the app and exports never show them.
- `%` stays out of plain names, kept for a percent unit. **`∞` is a name of its own**, the built-in infinity (a constant, like `π`): `int(e^(-x^2), x, -∞, ∞) -> ?`. It is written in backticks only with a subscript or a style (`` `∞`_x ``).
- **An empty subscript** (`x_`, typed `x.` and nothing after) is a different name from `x`, drawn exactly the same: an old trick for a second variable that looks like the first (Dan, 2026-10-28: kept). Only the text (`x_`) and messages (`x_ is not defined`) tell them apart. The import keeps `subscript=""` as it is.
## 4. Operators

Listed from lowest to highest precedence.

| Prec. | Operator | Syntax | Meaning |
|---|---|---|---|
| 1 | Statement | `:=` `::=` `= ?` `-> ?` | Not true operators; only valid at statement level |
| 2 | Error handling | `try a on error b` | `on error`, written with `try` so it reads in order: try this, or else that. `try` is a reserved word. Nests: `try a on error try b on error c` |
| 3 | Logical or / xor | `a or b`, `a xor b` | |
| 4 | Logical and | `a and b` | |
| 5 | Logical not | `not a` | |
| 6 | Comparison | `==` `!=` `<` `<=` `>` `>=` | `==` is the bold equals (`Ctrl+=`). Chains: `0 < x < 1` (a chain is a comparison whose left operand is an unparenthesised comparison; `(0 < x) < 1` is not a chain) |
| 7 | Range | `a..b`, `a, b..c` | Only as the whole value of a definition (`i := 0..10`) or a `for` loop |
| 8 | Additive | `+` `-` | |
| 9 | Multiplicative | `*` `/` `×` (cross product) | Implicit multiplication, see §5 |
| 10 | Unary | `-a` `+a` | |
| 11 | Power | `a^b` | Right-associative; `-x^2` = `-(x^2)` |
| 12 | Postfix | `a!`, `M^T` (transpose), `M^<j>` (column j) | `^T` is always a transpose: a power of a variable named T is written `x^(T)`. `M^<j>` is a superscript, as drawn, so it can't be read as a comparison. `M^-1` is an ordinary power |
| 13 | Access | `v[i]`, `M[i, j]`, `f(x)` | |
| 14 | Atom | literal, identifier, `( … )`, `\|a\|`, `[ … ]` | `\|a\|` is absolute value, magnitude or determinant |

Notes:

- `Ctrl+-` vectorize is written `vec(expr)`. It displays as an arrow over the expression.
- Division is drawn as a stacked fraction by default; `÷` and `∕` are inline divisions ([division style](#division-style)).
- `×` (or its ASCII form `cross(a, b)`) is the cross product. Plain `*` between vectors is the dot product.

## 5. Implicit multiplication (juxtaposition)

Units are variables multiplied with numbers, and a multiplication can be drawn with a thin space. One rule covers both:

> **An operand followed by a name (with or without a space after a number) is multiplied by it.**

```
L := 6 m              → 6 · m
q := 12 kN/m          → (12 · kN) / m   = 12 · (kN/m)
v := 2.5e3 mm/s       → (2.5e3 · mm) / s
A := 2x               → 2 · x
M := q L^2 / 8        → (q · L^2) / 8
```

A name needs a space before it after another name (`q L`; `qL` is the one name `qL`). Only a name may follow: `q 2` and `2 3` are errors. Juxtaposition has the same precedence as `*`, so `1/2 m` is `(1/2)·m` and `q L^2` is `q·(L^2)`. Since 2026-10-28 (multiplication style, below), `q L` is a multiplication drawn with a thin space; before, it was an error.

The tree records whether a multiplication was implicit (or written `·`), and the printer reproduces it: implicit as a space (`12 kN/m`, `q L`), `·` as `·`, explicit as `*`. The printer never needs to know which identifiers are units.

### Multiplication style

How a multiplication is **drawn**: `q·L` with a centred dot, or `q L` with a thin space. Decided by Dan on 2026-10-28:, a default for the sheet, and right-click on one operator to change just that one.

- **The sheet's default** is in the frontmatter, `multiplication: dot` (the default, not written) or `multiplication: space`, set from Format → Multiplication.
- **One operator's own style** is written with its own character: `*` follows the sheet's default, `·` (or `⋅`) is always a dot, and a space before a name (`q L`, `q L^2`) is always a thin space. So any operand followed by a name is a multiplication, not just a number followed by a unit (§5).
- **Right-click on a multiplication** (the innermost one around the point clicked): Dot, Thin space, or As the sheet. A thin space needs a name on the right, since `q 2` would not read back; it is greyed out otherwise.
- **Exports follow** the same choice: `\cdot` or `\,` in LaTeX, `·` or a thin `<mspace>` in MathML.
- **Not offered:** a cross style (`x`) for plain multiplication, which reads as a cross product (`×` is the cross product here), and a "no space" style (`qL` touching), which reads as the one name `qL`. The `.xmcd` import draws the first as a dot and the second as a thin space; the sheet's own setting (`<operators multiplication="…">`) becomes `multiplication: space` for a thin or no space, a dot otherwise.

### Division style

Decided by Dan on 2026-10-29: stacked by default, and inline as an option, chosen like the multiplication style. All three are the same division; only the drawing differs.

- **`/` is a stacked fraction** (a over b), always.
- **`÷` is an inline division drawn as ÷** (a ÷ b), and **`∕`** (U+2215, the division slash) **an inline division drawn as a slash** (a/b, on one line). `÷` and `∕` have the precedence of `/`.
- **Right-click on a division**: Stacked, Inline ÷ or Inline /. Typing `/` makes a stacked fraction and `Ctrl+/` an inline one (÷) †.
- **No sheet default**, unlike multiplication: a fraction is the common case, and the inline forms are for the odd one that should stay on the line. (One can be added later if sheets want it.)
- **Exports follow**: `\frac`, `\div` or `/` in LaTeX; `<mfrac>`, `÷` or `/` in MathML. The `.xmcd` import maps inline division to `÷` (`<ml:div inline="true"/>`).
- Built on 2026-11-02: the tree keeps the style (`Apply.division`), the printer writes `÷` and `∕` without spaces, as it does `/` (`(a + b)÷c`), and a division on the line is drawn with the parentheses its operands need.

## 6. Functions

```
f(x) := x^2 + 1
g(x, y) := sqrt(x^2 + y^2)
h := f              (functions are values)
```

Built-in operators that display as special notation are written as functions in the linear syntax:

| Linear | Displays as |
|---|---|
| `sqrt(x)` | √x |
| `nthroot(x, n)` | ⁿ√x (`root(f, x)` is the equation solver: [evaluation.md](evaluation.md#solving)) |
| `sum(expr, i, a, b)` | Σ from i=a to b |
| `sum(expr, i)` | range sum Σᵢ (i must be a range variable) |
| `prod(expr, i, a, b)` | Π |
| `int(expr, x, a, b)` | ∫ₐᵇ expr dx |
| `int(expr, x)` | indefinite (symbolic only) |
| `diff(expr, x)` | d/dx expr |
| `diff(expr, x, n)` | dⁿ/dxⁿ expr |
| `lim(expr, x, a)` | limit, with `lim(expr, x, a, "+")` / `"-"` for one-sided |
| `vec(expr)` | vectorize arrow |
| `abs(x)` / `\|x\|` | \|x\| |

Argument order follows the common CAS convention (body first, then variable, then bounds), which makes the symbolic bridge simpler.

<a id="modules"></a>Functions from a Python file are reached with `.`: `bt := use "beam_tools.py"`, then `bt.f(x)` ([symbolic.md](symbolic.md#python-functions-in-a-sheet)). `.` after a name with no subscript, followed by a name, is this access (the `MEMBER` operator, binding as tightly as a call); after a subscript it stays part of the subscript, so a module in a subscripted name is written `(bt_1).f`, and the printer adds those parentheses. `use "file.py"` is a call of `use` on a string, written without parentheses (`use("file.py")` reads the same and prints back as `use "file.py"`). Names starting with `_` can't follow the dot, so a module's private functions are out of reach.

In the tree these are ordinary function calls; the layout engine recognises the names and draws √, Σ, ∫ and so on. `|x|` is the exception: it is an operator node of its own, so `|x|` and `abs(x)` both survive saving as typed.

## 7. Ranges and indexing

```
i := 0..10                 integers 0, 1, …, 10
x := 0, 0.1..1             first, second..last  (step 0.1)
v[i] := i^2                defines vector elements over the range
M[1, 2]                    element
M^<2>                      column 2
```

- A range is only ever the whole value of a definition or `for` loop; `,` and `..` are not operators anywhere else. Semantics: [evaluation.md](evaluation.md#ranges).
- `ORIGIN` (default 0) is a worksheet setting (frontmatter `origin: 0`) and can be redefined with `ORIGIN := 1`.
- Index expressions use `[ ]`, so `M_max` (literal subscript) and `M[max]` (index by a variable) are distinct.

## 8. Program blocks

A program is introduced by `:=` at the end of a line; its body is on the following lines, indented by 2 spaces per level. Each indented line is one program line (one row of the vertical program bar).

```
f(x) :=
  if x < 0
    return -x
  otherwise
    x
```

```
fib(n) :=
  a <- 0
  b <- 1
  for k in 1..n
    t <- a + b
    a <- b
    b <- t
  a
```

| Construct | Syntax |
|---|---|
| Local assignment | `a <- expr` (also `←`), or an element: `v[i] <- expr`. `<-` is always this token, so write `x < -1` (with spaces) for a comparison |
| Local function | `f(x, y) <- expr`: a function inside the program; its free names are the program's as they stand at that line (as in `f(i, j) <- i*Δt`, then `matrix(n, 1, f)`) |
| Conditional | `if cond` + indented body; `otherwise` + indented body |
| Inline conditional | `line if cond` and `line otherwise`, where the line is an expression, an assignment, `return …`, `break` or `continue` (a one-line `if`: `break if k > 10`) |
| For loop | `for k in range-or-vector`, or `for k ∈ …`. `in` is the loop keyword only right after `for k`; everywhere else it is the inch |
| While loop | `while cond` |
| Control flow | `break`, `continue`, `return expr` |
| Error handling | `try expr on error expr`: an operator with the lowest precedence, usable anywhere |

The value of a program is the value of its last executed line.

Programs can also appear on the right of a variable definition (`x :=` followed by an indented body).

Rules for the text form:

- Indentation is by spaces (two per level is the canonical form; any consistent amount parses). Tabs are an error.
- A program can't contain a blank line: a blank line ends the statement.
- A line starting with `if` is a block only when lines are indented below it; otherwise it is an expression line, which can call the function `if(c, a, b)`.
- `return`, `break`, `continue`, `otherwise`, `for` and `while` start their special lines only at the start of a program line. An expression line that starts with a variable of one of those names marks it as a name: `\it break` (§3; parentheses, `(break)`, also work).
- In the tree, an inline `x if c` and a block `if c` with the single line `x` are the same node (as in `.xmcd`, where both are `ml:ifThen`); the printer writes a one-line body inline.
- Parse errors inside a program say which line: "(program line 2, column 5)", counting from the first indented line.

### A program inside an expression

Decided with Dan on 2026-10-29, after 553 of his worksheets turned out to have one: a program can sit anywhere in a statement's expression, not only as a definition's whole value. In the text the word **`program`** stands where it goes, and its lines are indented below the statement as usual:

```
M := program*kN*m = ? kN*m
  a <- q*L^2
  a/8
program = ?
  x <- 1
  x + 2
```

One program per statement (the indented lines can only belong to one). A definition whose whole value is a program keeps the plain form, `f(x) :=` with the lines below. In the 2D view the program is drawn in place, with its bar.

**Inside a program too** (2026-10-29, 283 of Dan's sheets have one): a program line written on one line (an expression, `a <- …`, `return …`, or `… if c`) may hold the word `program`, and the lines indented below it are that program:

```
f(x) :=
  a <- program*2
    b <- x + 1
    b^2
  a + 1
```

## 8½. Solve blocks

A line `given` starts a solve block; the comparisons below it are its constraints, solved by `find(x, y)` or `minerr(x, y)` ([evaluation.md](evaluation.md#solve-blocks)).

```
x := 1
given
x^2 == 2
r := find(x) = ?
```

## 9. Symbolic

```
(x + 1)^2 -> expand -> ?
x^2 - 4 -> solve(x) -> ?
a*x^2 + b*x + c -> solve(x) -> ?
F(s) := laplace(f(t), t, s) -> ?
```

Keywords chain left to right with `->` (or `→`). The final `-> ?` is the result placeholder. The keywords of `.xmcd` files are the starting point; what works so far is in [symbolic.md](symbolic.md). A definition with a symbolic value (the last example) is not supported yet.

## 10. Comments

None inside math: notes go in text regions (Dan, 2026-10-29). Comments in math, perhaps only in programs, can be added later if users ask for them. In a file, `#=` and `#!` at the end of a line are taken: they hold cached results and errors ([file-format.md](file-format.md#results-in-the-file)).

## 11. Editor keystrokes → syntax

How typing in the 2D editor maps onto the AST (and therefore the text). The full list of keystrokes, as built, is in [editor.md](editor.md).

| Keystroke | Inserts | Linear form |
|---|---|---|
| `:` | definition | `:=` |
| `~` | global definition | `::=` |
| `=` | evaluation (at the end of a definition: definition + evaluation) | `= ?` |
| `Ctrl+=` | Boolean equals | `==` |
| `Ctrl+.` | symbolic evaluation | `-> ?` |
| `/` | stacked fraction | `/` |
| `^` | exponent | `^` |
| `\` | square root | `sqrt( )` |
| `Ctrl+\` | nth root | `root( , )` |
| `\|` | absolute value / determinant | `\| \|` |
| `[` | index | `v[ ]` |
| `.` (after an identifier) | literal subscript | `_` |
| `'` | parentheses around the selection | `( )` |
| `;` | range | `..` |
| `Ctrl+G` | Greek letter for the previous character | `α` |
| `Ctrl+M` | insert matrix (dialog) | `[ … ]` |
| `Ctrl+6` | matrix column | `M^< >` |
| `Ctrl+1` | transpose | `^T` |
| `Ctrl+-` | vectorize | `vec( )` |
| `Ctrl+Shift+4` | summation | `sum( , , , )` |
| `?` | derivative | `diff( , )` |
| `&` | definite integral | `int( , , , )` |
| `]` | add program line | new indented line |
| `{` | local assignment | `<-` |
| `}` | program `if` | `if` |
| `Space` | widen the insertion cursor's selection | (navigation) |
| `Insert` | flip the insertion cursor between left and right side | (navigation) |

## 12. What the tree preserves

Saving a worksheet must not rewrite what the user typed. So, beyond the meaning, the AST keeps:

- **Number literals as typed.** `.5`, `1.2E+3` and `0x1F` keep their spelling; the value is computed from the text.
- **Parentheses.** `(a + b)` is a parenthesis node, so redundant brackets survive. (`.xmcd` has the same: `ml:parens`.)
- **Implicit vs explicit multiplication** (see §5).

Whitespace is not preserved: the printer uses one canonical spacing.

## 13. Full example

```calc
L := 6 m
q := 12 kN/m
E := 210 GPa
I := 8356 cm^4

M_max := q*L^2/8 = ? kN*m
δ_max := 5*q*L^4/(384*E*I) = ? mm

check(δ, L) :=
  "OK" if δ <= L/250
  "FAIL" otherwise
check(δ_max, L) = ?
```
