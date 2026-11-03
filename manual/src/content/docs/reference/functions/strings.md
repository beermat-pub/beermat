---
title: Strings, units and programs
description: The built-in functions for strings, units and programs.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

<a id="fn-concat"></a>

## `concat(s, t, …)`

The strings joined.

```text
concat("beam ", "B1") = "beam B1"
```

<a id="fn-strlen"></a>

## `strlen(s)`

The number of characters in s.

```text
strlen("beam") = 4
```

<a id="fn-substr"></a>

## `substr(s, m, n)`

n characters of s from position m (counting from 0).

```text
substr("beam B1", 5, 2) = "B1"
```

<a id="fn-search"></a>

## `search(s, t, m)`

The position of t in s, looking from position m, or −1 if it isn't there.

```text
search("beam B1", "B", 0) = 5
```

<a id="fn-num2str"></a>

## `num2str(x)`

The number x as a string.

```text
num2str(2.5) = "2.5"
```

<a id="fn-str2num"></a>

## `str2num(s)`

The number written in the string s.

```text
str2num("2.5e3") = 2500
```

<a id="fn-IsString"></a>

## `IsString(x)`

1 if x is a string, 0 if not.

```text
IsString("a") = 1
```

<a id="fn-error"></a>

## `error(s)`

Stops with the message s: in a program, for a value that can't be used.

<a id="fn-if"></a>

## `if(c, a, b)`

a if c is true (not zero), otherwise b; only the one chosen is evaluated.

```text
if(2 > 1, "yes", "no") = "yes"
```

<a id="fn-SIUnitsOf"></a>

## `SIUnitsOf(x)`

One of x's unit in SI, so that x/SIUnitsOf(x) is the plain number.

```text
SIUnitsOf(3 kN) = 1 N
```

<a id="fn-UnitsOf"></a>

## `UnitsOf(x)`

The same as SIUnitsOf.

```text
F := 3 kN
F/UnitsOf(F) = 3000
```

<a id="fn-time"></a>

## `time(z)`

The time now in seconds (z is not used), for timing a long calculation.

<a id="fn-trace"></a>

## `trace(x, …)`

Returns x, for compatibility with imported sheets.

```text
trace(2) = 2
```

<a id="fn-pause"></a>

## `pause(x, …)`

Returns x, for compatibility with imported sheets.

```text
pause(2) = 2
```

<a id="fn-use"></a>

## `use "file.py"`

The functions of a Python file, as a module: `bt := use "beam.py"`, then `bt.f(x)`; in the desktop app.
