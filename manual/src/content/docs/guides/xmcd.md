---
title: Open .xmcd files
description: Convert .xmcd worksheets to Beermat worksheets, and what to check afterwards.
---

Beermat imports `.xmcd` worksheets (the XML worksheet format). The import makes a new `.bbm` file and leaves the original as it was.

## Convert a file

In a terminal, with the [`beermat` command](../../start/install/#the-beermat-command):

```text
beermat import design.xmcd
```

This writes `design.bbm` beside it (or choose the name with `--out other.bbm`), and lists anything it couldn't bring across. Then open `design.bbm` in Beermat.

To convert a whole folder (in PowerShell on Windows):

```text
Get-ChildItem *.xmcd | ForEach-Object { beermat import $_.FullName }
```

## What comes across

- Math, with names, subscripts and Greek letters as they were, and how each multiplication was drawn.
- Text, with headings, bold and italic.
- Programs, solve blocks, ranges, arrays, derivatives, integrals and sums.
- Symbolic evaluations.
- Areas, collapsed or not.
- The sheet's ORIGIN and tolerance.
- Controls (check boxes, lists, text boxes) and data tables, as their current values.
- Spreadsheet components, as [tables](../tables/): their input cells take the sheet's values, and formulas that use only their own row or whole columns run. Other formulas keep the values last calculated, with a warning naming the cell.
- Plots and pictures, as the pictures last drawn of them.

## What to check

After importing, run [`beermat check`](../../reference/command-line/) on the new file, or look for red errors on the page:

- **Plots** are pictures, so they don't follow changed inputs. Make a new [plot](../../tutorials/plots/) where you need a live one.
- **Spreadsheet formulas** that use other rows' cells keep their last values; the warning names the cell.
- **A row vector raised to a power** is worked out element by element (`[1, 2, 3]^2` is `[1, 4, 9]`). Older files may have computed it differently.
- **`root`** may find a different root than the one saved in the file, both valid: give it limits to choose.
- **References to other worksheets** aren't imported; the warning says which.
