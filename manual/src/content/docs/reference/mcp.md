---
title: MCP tools
description: The tools Beermat gives an AI assistant through MCP.
---

These are the tools an AI assistant gets when it is connected to Beermat ([Use an AI assistant](../../guides/ai/)). The assistant chooses when to call them; this list is for knowing what it can do.

| Tool | Does |
|---|---|
| `guide` | A short guide to worksheets, the syntax and these tools. Beermat asks every assistant to read it first |
| `read_worksheet` | Every region in order, with each statement's fresh result or error |
| `evaluate` | Works out an expression in the sheet's context, after all its statements or after a given region, without changing anything: `q*L^2/8 = ? kN*m`, or `diff(x^3, x) -> ?` |
| `check` | Errors, plot problems and out-of-date results, as [`beermat check`](../command-line/) reports them |
| `update_region` | Replaces a region's content |
| `insert_region` | Adds a math, plot, table or text region after another one, or at the end |
| `delete_region` | Removes a region |
| `export` | The sheet as Markdown with display math, or as a LaTeX document |

- Every tool takes `path`, the worksheet's file. In the app, leaving it out means the sheet that is open.
- A region is named by its id (`r1`) or by its kind and place (`calc 2`, `plot 1`, `text 3`), as `read_worksheet` lists them.
- The editing tools work the sheet out again and return it, so the assistant sees the results of its change straight away.
- Pictures are never sent to the assistant: it sees a line saying a picture is there, and its size.
- Python files in a sheet don't run for an assistant on its own: in the app you allow them yourself, as usual.
