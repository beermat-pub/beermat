# Beermat for VS Code

Engineering worksheets, beside your code. A `.bbm` file opens on its page: real fractions, roots, matrices and units, results worked out as you type, plots, the toolbar and the same keys as the [Beermat](https://beermat.pub) desktop app.

```
M_max := q*L^2/8 = ? kN*m   →   M_max := q·L²/8 = 54 kN·m
```

- **The file stays text.** A worksheet is Markdown, and the page edits that text: saving, undo, git diffs and other extensions (an AI assistant among them) see the same file. A change made in the text shows on the page at once.
- **Open as Text** (the button at the top right) shows the Markdown beside the page; **Open on the Beermat Page** goes back.
- **Units checked** everywhere, results in the unit you ask for, tables, X-Y plots, programs, solve blocks and Export to Word come with it.

**Symbolic algebra** beyond the basics (integrals, factoring, series, solving, transforms) comes from the desktop app when it is installed: the extension finds its `beermat` command (or set **beermat.command**). Data files beside the sheet (`READCSV`, `READPRN`, `READFILE`) and linked pictures are read in a trusted workspace. **Python functions** (`bt := use "beam_tools.py"`) run through it too, after you say the file may run, as the app asks. Excel files, printing and `.xmcd` import need the desktop app itself for now: [beermat.pub/download](https://beermat.pub/download). The manual is at [beermat.pub/docs](https://beermat.pub/docs/).

Beermat is free and open source (MIT). Issues and ideas: [github.com/beermat-pub/beermat](https://github.com/beermat-pub/beermat/issues).
