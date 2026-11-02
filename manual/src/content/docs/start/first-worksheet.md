---
title: Your first worksheet
description: Definitions, results and units in five minutes.
---

A worksheet is a page. Click anywhere on it and type: what you type becomes math at that spot.

## Define a value

Click on the page and type:

```
L:6m
```

The colon becomes the definition sign, so the page shows **L := 6 m**. Press **Enter** to finish.

Below it, define a load:

```
q:12kN/m
```

## Work something out

Below both, type:

```
M:q*L^2/8=
```

Beermat shows **M := q·L²/8 = 54000 N·m**: a definition, and its result after the `=`. The result has units, worked out from the units you typed.

## Show it in other units

Type the unit you want right after the `=`. Below, type:

```
M=kN*m
```

The page shows **M = 54 kN·m**. A unit that doesn't fit (`kN` alone, for a moment) shows an error instead of a wrong number.

## Change a value

Click on `6 m` and make it `8 m`. Every result below it changes at once: the page is worked out from top to bottom, then left to right.

## Save it

**File → Save** writes a `.bbm` file. It is plain text (Markdown), so you can read it in any editor, keep it in Git, or hand it to an AI assistant.
