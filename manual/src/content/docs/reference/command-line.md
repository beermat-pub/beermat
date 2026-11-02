---
title: The beermat command
description: Run, check, export and import worksheets from a terminal.
---

The `beermat` command works on worksheets without opening the app: for scripts, build servers and AI assistants. It comes with the desktop app ([Install](../../start/install/#the-beermat-command)).

## `beermat eval <file>`

Works out the sheet and prints every line with a result or an error:

```
12: M_max := q*L^2/8 = ? kN*m   #= 54 kN*m
14: y := x + 2 m                #! units do not match: L·M vs L
```

- `--write` saves the fresh results into the file.
- `--json` prints the same as JSON.
- `--allow-python` lets the sheet run the Python files it uses.

## `beermat check <file>`

Lists the problems in a sheet, one per line, without changing it: errors, and results saved in the file that are out of date. Prints `ok` when there is nothing to report.

## `beermat export <file> [--to md|latex] [--out <file>]`

Writes the sheet as Markdown (the default) or as a LaTeX document.

## `beermat import <file.xmcd> [--out <file.bbm>]`

Converts an `.xmcd` worksheet to a `.bbm` one, and lists what couldn't be brought across exactly.

## `beermat mcp`

Lets AI assistants that speak MCP, such as Claude Code, read and work out worksheets.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success, nothing to report |
| 1 | The sheet has errors, or (`check`) out-of-date results |
| 2 | Bad arguments, or the file can't be read or written |
