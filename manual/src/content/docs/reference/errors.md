---
title: Error messages
description: The common error messages, what usually causes each, and how to fix it.
---

An error shows in red at the part of the statement that caused it. The command line prints it after `#!`. Every statement that uses a broken definition says so, naming where the trouble started:

```text
a := b + 1          b is not defined
c := a + 1          a has an error: b is not defined
```

Fix the first one, and the rest follow.

## Names

**`b is not defined`**

Nothing above defines b. The sheet is worked out top to bottom, then left to right, so a definition below or to the right of its use doesn't count yet: move it up. Check the spelling, the case (`F` is not `f`), the subscript, and the style (a bold **F** is a different name from F).

**`x is defined globally (≡); it can't be redefined`**

A global definition (`≡`, typed `~`) holds for the whole sheet, so it can't be defined again with `:=`. Use another name, or make the global an ordinary definition.

**A result is wrong by a large factor**

Look for a variable named like a unit. After `m := 2 kg`, `3 m` is 6 kg, not three metres, for everything below. Rename the variable (`M`, `m_1`, `mass`).

## Units

**`units do not match: L vs T`**

Two things of different kinds are added, subtracted or compared, here a length and a time. The letters are the dimensions: L length, M mass, T time, I current, Θ temperature, N amount of substance, J luminous intensity (and ∠ angle, ¤ money, when a sheet makes them dimensions). Usually a term or a unit is missing.

**`units do not match: the result is L, the unit is L·M·T^-2`**

The unit typed in the result's placeholder is of another kind than the result: here a length asked to show in kN. Change the unit, or check the formula.

**`sin: the argument must be dimensionless or an angle, not L`**

Functions such as `sin`, `exp` and `ln` need a plain number. Divide by a unit, or check the formula.

**`an exponent must be dimensionless, not L`**

A power must be a plain number: `x^2`, not `x^(2 m)`.

## Typing

**`missing operator before '3': write '*'`**

Two numbers side by side. Only a name may follow a number without `*` (`2 m`, `2 x`).

**`missing ')'`**

A bracket was opened and not closed.

**`expected '?' after '=' (write 'x = ?' to evaluate, or '==' to compare)`**

In a file, `=` shows a result and must be followed by `?`. To compare, use `==` (Ctrl+= in the app).

**`this definition needs a name`**

The left of `:=` is still empty.

## Arrays and ranges

**`i is a range variable: it can define the elements of an array (v[i] := …), not a single value`**

A range variable stands for each of its values in turn, so `y := i^2` has no single value. Define the elements instead: `y[i] := i^2`.

**`row 2 is out of range: there are 2, numbered from ORIGIN = 0`**

Indices count from ORIGIN, 0 unless the sheet sets it. The last element of a vector of 2 is `v[1]`.

**`cannot multiply a 2×2 matrix by a 1×3 matrix: the columns of the first must match the rows of the second`**

A matrix product needs matching sizes. For an element-by-element product, use `vec(a*b)`. A transpose (`^T`) may be what's missing.

**`sin: expected a number, not a 3×1 matrix`**

Most functions take one number. Wrap the call in `vec( )` to apply it to every element: `vec(sin(v))`.

## Calculations

**`division by zero`**

Dividing by 0 is an error, not infinity. `try … on error …` can catch it where it is expected.

**`f takes 1 argument(s), not 2`**

The call has a different number of arguments from the function's definition.

**`too many nested calls (is the recursion infinite?)`**

A function calls itself without end. Check that it has a case that stops: `fact(n) := if(n < 1, 1, n*fact(n - 1))`.

**`the while loop ran more than 10000000 times (does its condition ever become false?)`**

The loop's condition never changes. Check what the loop changes.

**`an operand must be a number, not a string`**

Text can be compared (`==`) but not used in arithmetic. `concat` joins texts, `num2str` and `str2num` convert.

## Solving

**`root: no root found near the guess (give limits: root(f, x, a, b))`**

There may be no real root, or it is far from the guess. Plot the function to see where it crosses zero, then give limits on either side of it.

**`find: no solution found from these guesses (try other guesses, or minerr for the closest values)`**

The conditions can't all be met from where the search started. Try other guesses (not all equal, if the problem is symmetrical), or `minerr` to see how close it gets.

## Python

**`beam_tools.py runs Python code, which hasn't been allowed yet`**

A sheet's Python file runs only once you allow it. The app asks; on the command line, add `--allow-python`.

**`add: a takes a plain number; give it a unit in the .py file (a: "m"), or divide by one at the call`**

The function's argument has no unit in its annotation, but was given a value with one. See [Python](../../guides/python/#units).
