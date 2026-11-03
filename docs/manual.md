# The manual

> Status: **step 1 done** (2026-11-02, for 1.0.0-dev2): the Starlight project in `manual/`, built in CI and published to beermat.pub/docs by releases; a shell of a few pages (start, install, first worksheet, the command line, What's new); Help → Manual and F1 open it. **Step 2 done** (2026-11-03, for 1.0.0-dev3): the reference pages generated from the code (functions, units, settings, keys) and F1 on a function. **Step 4 drafted** (2026-11-03, for Dan's review): every page in the contents below except "Use your own Python" and Word export, with examples checked by `beermat eval` and written as `text` blocks until step 3's fence draws them. The rest of the plan below is to do: Dan's idea (2026-11-02) is to write it on the released version, recording the first videos as we go.

The user's manual, for 1.0: a documentation website at **beermat.pub/docs**, written in Markdown in this repository, built by the release workflow, and opened from the app's Help menu. **Public-facing**: no other product is named or compared; pages may say that Beermat imports `.xmcd` files.

## Decisions

- **A website, not a PDF.** It is searchable, reads on a phone, links to itself, and changes with every release. A page prints well enough for anyone who wants paper.
- **Built with [Starlight](https://starlight.astro.build)** (MIT, on Astro, MIT): search (Pagefind), dark mode, sidebar, "Edit this page" links to GitHub, translations ready for later. Math in prose through KaTeX (MIT). Node is needed only to build it, never by the app.
- **What it covers: only what the version does.** No future features in the manual: a reader should never meet a page for something they can't use. What's coming goes in a public **`ROADMAP.md`** on GitHub (the plan, without the internal notes and without naming other products), linked from the manual's footer and the website. What changed goes in **What's new** in the manual, from `CHANGELOG.md`.
- **One manual per release.** `beermat.pub/docs` is the latest release's; the app opens the pages of its own version (an older version's manual stays at `beermat.pub/docs/v1.0`, and so on). An offline copy inside the app comes in 1.x.
- **Plain English**, short pages, an example first and then the explanation. British spelling, as in these docs.

## Where it lives

- The source is in this repository, in `manual/` (the Starlight project; pages in `manual/src/content/docs/`), so a feature and its page change in the same commit. `docs/` stays the internal design spec.
- The release workflow builds it and pushes the HTML to the website's repository (`beermat-pub/beermat.pub`, under `docs/`), with a deploy key. One Pages site serves the domain, so the manual is part of it.
- Pull requests build it too (no deploy), so a broken page or example fails CI.

## What generates itself

Anything that is a list in the code is generated from the code, so the manual can't fall behind.

- **Reference pages**: every built-in function, operator, unit, keyword, setting and keystroke. Built (2026-11-03):
  - Each built-in has a doc entry in the engine (`FunctionDocs`: signature, one sentence, an example, and its group, one page each), and `FunctionDocsTest` fails if one is missing or an example doesn't evaluate. The page shows the example with its result written in by the evaluator, so it can't be wrong.
  - Units come from `units.bbm` (its sections and the text under each heading, `BuiltinUnits`), settings from `Settings.reference` in the format module (a test checks the sheet reads each), keys from the toolbar's palettes (`Commands`) and a list of the general keys.
  - `ManualReferenceTest` (app module) writes them to `manual/src/content/docs/reference/` with `./gradlew :app:test --tests '*ManualReference*' -Pbeermat.writeReference=true`. The pages are committed, and the test fails when the code changes and they aren't written again, so CI keeps them in step (rather than a Gradle task in the manual's Node build, which would need Java there).
  - Each function has an anchor of its own, `#fn-name` (headings' own ids would clash between `floor` and `Floor`).
- **Pictures of math and plots**, drawn by Beermat itself: a ```` ```beermat ```` fence in a page holds a small sheet, and a Gradle task renders it to SVG with the app's painter (as PDF export does) and puts the image in its place. The same task evaluates it, so an example that gives an error fails the build: every example in the manual works.
- **"Open this example"**: each example sheet is also published as a `.bbm` file; a button opens it in the web version (`beermat.pub/try?open=…`), ready to edit, and a link downloads it for the desktop app.

## In the app and for AI

- **Help → Manual** opens the manual for the installed version. **F1** on a function, unit or keyword opens its reference page; anywhere else, the page for what's under the cursor (a plot, a table) or the start page.
- The MCP `guide` tool can return manual pages, so an AI assistant learns Beermat from the same text as its user ([ai-guide.md](ai-guide.md) stays the guide written for assistants).

## Contents

Four parts (the usual split of tutorials, how-to guides, reference and explanation). The topics were checked against an established worksheet program's user guide, used only for the list of subjects a worksheet manual needs, never for its text: its chapters on workspace, math, arrays, text, worksheets, calculating, units, functions, solving, pictures, 2D and 3D plots and symbolics all have a home below (3D plots once they exist); programming, data and import, which it left to the Help, are here too.

**Getting started**
- Install (Windows, Microsoft Store, macOS, Linux, portable), or try it in the browser
- Your first worksheet: definitions, results, units, in five minutes
- A beam calculation from start to finish (the website's example, grown)

**Tutorials**
- Units: typing, converting, catching mistakes
- Arrays and ranges: a table of results
- A small program
- Plots
- Solving equations
- Symbolic algebra

**How-to guides**
- Format results (decimals, significant figures, units, prefixes)
- Lay out pages: regions, page breaks, areas, headers and footers; print and make a PDF
- Text, headings and pictures
- Tables of inputs and results
- Solve an equation, a system, an optimisation (`root`, solve blocks, `find`, `minerr`)
- Fit and interpolate data; read CSV and Excel files
- Differential equations
- Use functions from a Python file
- Export: LaTeX, Word (MathML), Markdown, copy as image
- Open `.xmcd` files
- Use an AI assistant with a worksheet (Claude Code and other MCP clients; the command line)
- Angles and currency as dimensions; custom units

**Reference** (mostly generated)
- The math syntax (from [syntax.md](syntax.md), rewritten for users)
- Operators, keystrokes and toolbar palettes
- Functions, by group
- Units, by quantity
- Worksheet settings (the frontmatter)
- Results: rounding and display rules
- Error messages, each with what usually causes it and the fix
- The file format
- The `beermat` command line
- The MCP tools

**Explanations**
- How a sheet is evaluated (order, globals, what a function sees)
- Units are values
- Why a worksheet is a Markdown file (version control, GitHub, AI)
- Numbers: precision and tolerance (`TOL`)

**What's new** (from `CHANGELOG.md`) · **Licence and terms** · **Support Beermat**

## Steps

1. Done: the Starlight project in `manual/` (`npm run dev` to work on it, `npm run build`; `prepare.mjs` first copies the icons from `branding/` and writes What's new from `CHANGELOG.md`, leaving out versions not released yet, so neither is kept twice), the build in CI, publishing to beermat.pub/docs on release, the first pages (only written pages are in the sidebar, rather than "to be written" ones), `ROADMAP.md`.
2. Done (2026-11-03): doc entries for every built-in, and the generated reference pages.
3. The ```` ```beermat ```` fence: rendered to SVG, evaluated, published as `.bbm`, the "Open this example" button.
4. The pages, written as each feature settles, all before the first 1.0 beta: Getting started first, then the reference's hand-written parts, then tutorials and how-tos.
5. Help → Manual and F1 in the app. Done for the start page (2026-11-02: Help → Manual, F1, and a Manual button in the web version's top bar); F1 on a function's name, or anywhere in a call of one, opens its entry (2026-11-03; `Session.helpPage`; the web version's Manual button does the same), and Help → Keyboard shortcuts the keys page; units and keywords open the start page for now. The app opens beermat.pub/docs, the newest manual, until there are versions of it.
6. (1.x) The offline copy in the app; translations, with the app's.
