---
title: Numbers and rounding
description: The built-in functions for numbers and rounding.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

<a id="fn-sqrt"></a>

## `sqrt(x)`

The square root of x, with its units halved.

```text
sqrt(16 m^2) = 4 m
```

<a id="fn-nthroot"></a>

## `nthroot(x, n)`

The real nth root of x; an odd root of a negative number is negative.

```text
nthroot(-27, 3) = -3
```

<a id="fn-abs"></a>

## `abs(x)`

The absolute value of x, or the magnitude of a complex number. Also written |x|.

```text
abs(-3 kN) = 3 kN
```

<a id="fn-Re"></a>

## `Re(z)`

The real part of z.

```text
Re(3 + 4i) = 3
```

<a id="fn-Im"></a>

## `Im(z)`

The imaginary part of z.

```text
Im(3 + 4i) = 4
```

<a id="fn-arg"></a>

## `arg(z)`

The angle of z in the complex plane, between −π and π.

```text
arg(1i) = 1.571
```

<a id="fn-conj"></a>

## `conj(z)`

The complex conjugate of z, element by element in an array.

```text
conj(3 + 4i) = 3 - 4i
```

<a id="fn-sign"></a>

## `sign(x)`

−1, 0 or 1, as x is negative, zero or positive.

```text
sign(-2.5) = -1
```

<a id="fn-signum"></a>

## `signum(z)`

z divided by its magnitude, or 0 when z is 0.

```text
signum(3 + 4i) = 0.6 + 0.8i
```

<a id="fn-exp"></a>

## `exp(x)`

e to the power x.

```text
exp(1) = 2.718
```

<a id="fn-ln"></a>

## `ln(x)`

The natural logarithm of x.

```text
ln(e^2) = 2
```

<a id="fn-log"></a>

## `log(x[, b])`

The logarithm of x to base 10, or to base b.

```text
log(1000) = 3
log(8, 2) = 3
```

<a id="fn-floor"></a>

## `floor(x)`

The largest integer not above x.

```text
floor(-2.5) = -3
```

<a id="fn-ceil"></a>

## `ceil(x)`

The smallest integer not below x.

```text
ceil(2.1) = 3
```

<a id="fn-round"></a>

## `round(x[, n])`

x rounded to n decimal places, or to an integer.

```text
round(3.14159, 2) = 3.14
```

<a id="fn-trunc"></a>

## `trunc(x)`

The integer part of x, towards zero.

```text
trunc(-2.7) = -2
```

<a id="fn-Floor"></a>

## `Floor(x, y)`

The largest multiple of y not above x; x and y may have units, the same.

```text
Floor(2.7 m, 0.5 m) = 2.5 m
```

<a id="fn-Ceil"></a>

## `Ceil(x, y)`

The smallest multiple of y not below x.

```text
Ceil(2.7 m, 0.5 m) = 3 m
```

<a id="fn-Round"></a>

## `Round(x, y)`

x rounded to the nearest multiple of y.

```text
Round(17 mm, 5 mm) = 15 mm
```

<a id="fn-Trunc"></a>

## `Trunc(x, y)`

The multiple of y nearest x towards zero.

```text
Trunc(-2.7, 0.5) = -2.5
```

<a id="fn-mod"></a>

## `mod(x, y)`

The remainder of x divided by y, with the sign of x.

```text
mod(17, 5) = 2
```

<a id="fn-max"></a>

## `max(a, b, …)`

The largest of the numbers given, arrays included element by element.

```text
max(3 m, 450 mm, [1 m; 5 m]) = 5 m
```

<a id="fn-min"></a>

## `min(a, b, …)`

The smallest of the numbers given.

```text
min(3, 1, [4; 2]) = 1
```

<a id="fn-Γ"></a>

## `Γ(x)`

The gamma function: Γ(n) is (n − 1)! for a whole number n.

```text
Γ(5) = 24
```

<a id="fn-gamma"></a>

## `gamma(x)`

The gamma function, as Γ(x).

```text
gamma(0.5) = 1.772
```

<a id="fn-lnΓ"></a>

## `lnΓ(x)`

The natural logarithm of the gamma function, for large x.

```text
lnΓ(100) = 359.134
```

<a id="fn-erf"></a>

## `erf(x)`

The error function.

```text
erf(1) = 0.843
```

<a id="fn-erfc"></a>

## `erfc(x)`

The complementary error function, 1 − erf(x).

```text
erfc(1) = 0.157
```
