---
title: Lay out pages, print and make a PDF
description: Regions on the page, page breaks, collapsible areas, headers and footers, printing and PDF.
---

## Regions

Everything on the page is a **region**: a block of math, text, a plot, a table or a picture, placed where you put it.

- **Click** on empty paper to put the red crosshair there, then type.
- **Drag** a region to move it. Regions snap to a 6-point grid.
- **Select several** by dragging a rectangle on empty paper. Dragged left to right, it selects the regions wholly inside it; right to left, every region it touches. **Ctrl+click** adds a region to the selection or takes it out. Drag any selected region to move them all, or press **Delete**.
- The **arrow keys** move the crosshair, or the selected regions, a step at a time.
- **Enter** on the page moves the crosshair down a line and pushes everything below down with it, to make room. **Backspace** and **Delete** take out a blank line above or below the crosshair.
- **Format → Separate regions** (`Ctrl+Shift+S`) moves overlapping regions apart.

The sheet is worked out from top to bottom, then left to right, so put a definition above (or to the left of) where it is used.

## Pages

Pages are A4 by default, with margins and a line at the right margin. For US letter, set `page: letter` at the top of the file. A region that would be cut by the end of a page moves to the next one.

- **Insert → Page break** (`Ctrl+Enter`) starts a new page at the crosshair.
- Regions past the right margin are allowed: the page scrolls sideways to them, and they print on extra pages after their page. **File → Print single page width** leaves them out of print.

## Areas

An area is a part of the sheet that can be folded away: **Insert → Area** puts one at the crosshair. Click its line to fold or unfold it. A folded area is still worked out; it just takes no room on the page, and what is below moves up.

## Headers and footers

Set them at the top of the file:

```yaml
---
title: Beam check
header: Calculation 12
footer:
  left: "{title}"
  right: "Page {page} of {pages}"
---
```

A plain text is centred; `left`, `centre` and `right` place each part. The fields are `{page}`, `{pages}`, `{title}` and `{date}`.

## Print and PDF

- **File → Print…** (`Ctrl+P`) prints the pages as they look on screen, without the crosshair, selection or margin line.
- **File → Export to PDF…** writes them to a PDF, next to the sheet by default. The PDF is vector, so it is sharp at any zoom, and its text can be selected and searched.

Printing and PDF are in the desktop app. In the browser, save the sheet and open it in the desktop app to print it.
