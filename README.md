# Beermat

**Back-of-a-beer-mat calculations, done properly.**

Beermat is an engineering worksheet. You click anywhere on the page and type math that looks like math. It evaluates top to bottom, handles units properly, and supports programming inside functions. On top of that, worksheets are plain text files you can diff, read on GitHub, export to LaTeX or Word, and hand to an AI assistant.

> **Status:** developer preview: [try it in your browser](https://beermat.pub/try) or [download it](https://beermat.pub/download), read the [manual](https://beermat.pub/docs/), and report what you find in [Issues](https://github.com/beermat-pub/beermat/issues). What's coming: [ROADMAP.md](ROADMAP.md).

## What a worksheet looks like

A Beermat worksheet (`.bbm`) is Markdown with calculation blocks:

````markdown
# Beam check

```calc
L := 6 m
q := 12 kN/m
M_max := q*L^2/8 = ? kN*m
```
````

The app shows this as 2D math, with `M_max = 54 kN·m` computed live.

## Documentation

- [docs/editor.md](docs/editor.md): typing math on the page
- [docs/syntax.md](docs/syntax.md): the math syntax
- [docs/file-format.md](docs/file-format.md): the `.bbm` file format
- [docs/units.md](docs/units.md): units
- [docs/evaluation.md](docs/evaluation.md): how a worksheet is evaluated, and the built-in functions
- [docs/cli.md](docs/cli.md): the `beermat` command line
- [docs/mcp.md](docs/mcp.md): the MCP server, for AI assistants

## Support

Beermat is free and open source. If it saves you time, you can support its development through [GitHub Sponsors](https://github.com/sponsors/dlcarneiro) or [Ko-fi](https://ko-fi.com/dcarneiro). Questions: [hello@beermat.pub](mailto:hello@beermat.pub).

## Licence

[MIT](LICENSE). Beermat is provided as is, without warranty of any kind. It is a calculation aid: you are responsible for checking its results and for any decisions or designs based on them. See [terms and privacy](https://beermat.pub/terms).
