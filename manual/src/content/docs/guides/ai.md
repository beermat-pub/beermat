---
title: Use an AI assistant with a worksheet
description: Let Claude Code or another AI assistant read, check and edit your worksheets, live in the app or from files.
---

An AI assistant can work on your worksheets with you: read them, check them, try expressions, and add or change regions. It does this through MCP (the Model Context Protocol), an open standard that many assistants speak. Beermat has no AI built in and sends nothing anywhere: the assistant you choose connects to it on your computer.

## In the app, on the open sheet

With a sheet open in the desktop app, choose **Tools → Connect Claude Code**. It copies the command that connects it; run it once in a terminal:

```text
claude mcp add --transport http beermat http://127.0.0.1:7341/mcp
```

Then ask, in Claude Code, for example: *"Read my open Beermat sheet and check the deflection calculation"*, or *"Add a check of the shear stress below the bending check"*.

- The assistant works on the sheet as it is on screen, unsaved changes included.
- Its edits appear at once, unsaved: you see them, and **Ctrl+Z** undoes them. You decide whether to save.
- The connection is to your own computer only (`127.0.0.1`), and web pages can't use it.

## In VS Code

With the Beermat extension, the same tools work on the sheet open in VS Code. Run **Beermat: Connect Claude Code** from the Command Palette: it copies a command like the one above (with port 7342), to run once. The assistant then reads the Beermat page in front, with your unsaved changes, and its edits show on it at once; **Ctrl+Z** undoes them.

## From files

Without the app open, the [`beermat` command](../../reference/command-line/) serves the same tools on worksheet files:

```text
claude mcp add beermat -- beermat mcp
```

The assistant then reads and writes `.bbm` files; if the app has the file open, it follows the changes.

## Other assistants

Any assistant that supports MCP can connect the same way: give it either the command `beermat mcp`, or, while the app is open, the address `http://127.0.0.1:7341/mcp`. That includes local models running on your own machine, through an MCP client that supports them. The first thing Beermat tells an assistant is to read its short guide, so no setup per assistant is needed.

## Without MCP

A worksheet is a plain text file, so any assistant can read one, and write one in the [linear syntax](../../reference/syntax/). `beermat eval sheet.bbm` and `beermat check sheet.bbm` then work it out and report its errors.

The tools an assistant gets are listed in [MCP tools](../../reference/mcp/).
