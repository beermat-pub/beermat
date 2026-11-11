---
title: Export
description: Copy equations as LaTeX or MathML, plots as images, and the whole sheet as Word, PDF, Markdown or LaTeX.
---

## One equation

While editing a statement, **Edit → Copy as LaTeX** or **Edit → Copy as MathML** copies it, with its result:

- **LaTeX**, wrapped in `$$…$$`, for LaTeX documents and Markdown that shows math (`M_{\mathrm{max}} := \frac{q \cdot L^{2}}{8} = 54\ \mathrm{kN} \cdot \mathrm{m}`).
- **MathML**: paste it into Word with **Ctrl+V** as plain text (or Paste Special → Unformatted text) and Word turns it into an equation you can edit.
- To go the other way, typing or pasting LaTeX to make math, see [Type math in LaTeX](../latex/).

**Ctrl+C** while editing a statement copies its text form, the [linear syntax](../../reference/syntax/) (`M_max := q*L^2/8 = ? kN*m`), to paste into a chat or an email. Pasted at the crosshair with **Ctrl+V**, text in the linear syntax becomes math again.

## A plot or a picture

Right-click a plot or a picture and choose **Copy as image**: a sharp PNG to paste into a document, an email or a chat.

## The whole sheet

| As | How |
|---|---|
| Word | **File → Export to Word…** (in the browser, the **Word** button): the text with its headings and formatting, every statement an equation you can edit in Word, with its result; tables, plots and pictures |
| PDF | **File → Export to PDF…**: the pages as they look, with selectable text |
| Markdown | **File → Export to Markdown…**: the text, each statement as display math with its result, plots as pictures inside the file |
| Markdown, LaTeX or Word | `beermat export sheet.bbm --to md`, `--to latex`, or `--to docx --out sheet.docx` from a [terminal](../../reference/command-line/) |

A Markdown export is one file: plots go inside it as pictures, not in a folder beside it. The `beermat` command can't draw plots, so its Markdown and Word documents have a line where each plot goes, and its LaTeX leaves plots out.

### In Word

A Word document has no free-form page, so the regions follow one another in reading order: top to bottom, and left to right on a line. Headings use Word's own heading styles, so the navigation pane and a table of contents work. A collapsed area stays out, as when printing. An error is written in red under its equation.

The `.bbm` file itself is Markdown too, readable as it is: see [Why a worksheet is a Markdown file](../../explanations/markdown/).
