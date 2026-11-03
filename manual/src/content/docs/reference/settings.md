---
title: Worksheet settings
description: The settings at the top of a worksheet file.
---

<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->

A worksheet's settings are at the top of its file, between `---` lines, as `key: value` (YAML). Every one is optional, and most are set from the Format menu. A `result.` setting is written below `result:`:

```yaml
---
title: Beam B1
result:
  significant: 3
header: {right: "Page {page} of {pages}"}
---
```

| Setting | Default | What it does |
|---|---|---|
| `title` | the file's name | The worksheet's title, `{title}` in a header or footer. |
| `page` | `A4` | The page size, `A4` or `letter`, with ¾ inch margins. |
| `header` | none | Text in the top margin of every page: `header: Calculation 12` (centred), or `header: {left: "{title}", centre: …, right: "Page {page} of {pages}"}`. The fields are `{page}`, `{pages}`, `{title}` and `{date}`. |
| `footer` | none | Text in the bottom margin of every page, as `header`. |
| `single_page_width` | `false` | `true`: printing and PDF export leave out what is past the right margin, rather than printing it on extra pages (File → Print single page width). |
| `result.decimals` | `3` | Decimal places shown in results. |
| `result.significant` | none | Significant figures shown instead of decimal places. |
| `result.strict` | `false` | With `significant`: round digits left of the decimal point too (1234.567 to 3 figures is `1230`, not `1235`). |
| `result.trailing_zeros` | `false` | With `decimals`: pad results to the full number of places. |
| `result.exponential_threshold` | `6` | Numbers of 10ⁿ or more, or below 10⁻ⁿ, are shown with an exponent. |
| `result.zero_threshold` | `15` | Values below 10⁻ⁿ show as 0. |
| `result.complex_threshold` | `10` | A real or imaginary part smaller than 10⁻ⁿ of the other is hidden. |
| `result.prefixes` | `true` | Engineering prefixes on results with no unit asked for (`54 kN·m`); `false` turns them off. |
| `multiplication` | `dot` | How `*` is drawn: `dot` (a·b) or `space` (a b) (Format → Multiplication). |
| `origin` | `0` | The first index of arrays, the starting value of `ORIGIN`. |
| `tolerance` | `1e-3` | The starting value of `TOL`, used by the solvers and by comparisons (`==`, `<`, …). |
| `angle` | `none` | `dimension` makes angle a dimension of its own, with `rad` its unit (Format → Units). |
| `angle_display` | `deg` | With `angle: dimension`, how an angle shows when no unit is asked for: `deg` or `rad`. |
| `currency` | none | A currency dimension whose unit has this name, such as `AUD` or `€` (Format → Units). |
| `beermat` | `1` | The version of the file format, written by Beermat. |
| `results` |  | A fingerprint of the sheet when its results were saved, written by Beermat, so that results left over from an older version of the sheet aren't shown. |
