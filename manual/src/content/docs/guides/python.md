---
title: Use functions from a Python file
description: Call your own Python functions from a sheet, with units, and install packages such as NumPy for them.
---

A sheet can call the functions of a Python file beside it. Python comes with the desktop app, so there is nothing else to install.

## A Python file

`beam_tools.py`, in the same folder as the sheet:

```python
def moment(q: "kN/m", L: "m") -> "kN*m":
    """The largest bending moment of a simply supported beam."""
    return q * L**2 / 8

def add(a, b):
    return a + b
```

## Using it

```text
bt := use "beam_tools.py"
M_max := bt.moment(12 kN/m, 6 m) = 54 kN*m
bt.add(2, 3) = 5
```

- `use "file.py"` gives the file's functions as a module, held in a variable (`bt`). The path is relative to the sheet.
- `bt.moment` is the function `moment`. Typing `bt.` lists the functions, narrowed as you type; resting the mouse on `bt.moment` shows its signature and docstring.
- The file is read again when it changes, so you can edit it beside the sheet.

## Units

Annotate the arguments and the result with their units, as strings. Beermat checks each argument's unit, passes the plain number in that unit (12 and 6 above), and puts the unit on the result.

Without annotations, the arguments and the result are plain numbers. A value with a unit passed to such a function is an error:

```text
bt.add(2 m, 3)      add: a takes a plain number; give it a unit in the .py file (a: "m"), or divide by one at the call
```

Arrays go in and out as lists (of lists), and strings as strings. An error in the Python code shows as the statement's error, with the line in the `.py` file.

## Allowing the file to run

A Python file can do anything a program can, so the first time a sheet uses one, Beermat shows its path and asks before running it. The answer is remembered for that file; if the file changes, it asks again. On the [command line](../../reference/command-line/), add `--allow-python`.

## Packages

The bundled Python has its standard library and SymPy. For more, such as NumPy or SciPy, use **Tools → Python packages…**: type the package's name and choose **Install**. No terminal or administrator rights are needed.

```python
import numpy as np

def norm(x, y):
    return float(np.linalg.norm([x, y]))
```

- Packages go in a folder of your own (shown in the dialog), which updates of Beermat leave alone.
- When an update brings a newer Python, the dialog offers **Install again** for the packages you had.
- From a terminal: `beermat packages install numpy` (also `list`, `remove` and `reinstall`).

Python functions are in the desktop app; the browser has no Python.
