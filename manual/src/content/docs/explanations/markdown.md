---
title: Why a worksheet is a Markdown file
description: What a plain-text worksheet gives you: reading it anywhere, version control, and AI assistants.
---

A Beermat worksheet is a text file you can open in any editor. The math is in the [linear syntax](../../reference/syntax/), the notes are Markdown, and each result is written after its line. This choice gives a few things a closed file can't.

## It can be read without Beermat

Open a `.bbm` file in a text editor, or name it `.bbm.md` and look at it on GitHub, and it reads as a calculation: headings, notes, each equation with its result. A colleague without Beermat can still check the numbers, and in twenty years the file will still be readable.

## It works with version control

Each statement is one line, regions keep their ids, and saving an unchanged sheet gives an identical file. So Git, and any other version control, shows exactly what changed between two versions: a changed load, a new check. Sheets can be reviewed, merged and kept with the drawings or the code they belong to.

The results kept in the file are protected against edits made elsewhere. If anything in the file changes outside Beermat, they are all worked out again rather than trusted ([The file format](../../reference/file-format/#results)). `beermat check` in a build or a pre-commit hook catches a sheet whose results are out of date.

## AI assistants can read and write it

An AI assistant reads a worksheet as easily as you do, and can write one in the same syntax. With the [`beermat` command](../../reference/command-line/), it can work a sheet out and check it, and through [MCP](../../guides/ai/) it can work on the sheet open in the app, with you watching. Nothing in Beermat sends your sheets anywhere: the assistant you choose does the work.

## One file

Pictures are stored inside the file, at its end, so a worksheet is always one file to send or attach.
