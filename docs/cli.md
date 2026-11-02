# Command-line tool (`beermat`)

> Status: **draft**, describing milestone 2.

The CLI runs worksheets without the app, for scripts, CI and AI tools such as Claude Code. Output is UTF-8.

## `beermat eval <file> [--write] [--json]`

Evaluates the worksheet and prints every `calc` line that has a result or an error, with its line number and a fresh annotation:

```
12: M_max := q*L^2/8 = ? kN*m   #= 54 kN*m
14: y := x + 2 m                #! units do not match: L·M vs L
```

- `--write` also writes the fresh results back into the file and updates its `results:` hash (see [file-format.md](file-format.md#results-in-the-file)). Nothing but the annotations and that line changes.
- `--allow-python` lets the sheet run the Python files it uses (`bt := use "beam_tools.py"`; [symbolic.md](symbolic.md#python-functions-in-a-sheet)). Without it, a sheet that uses one shows an error there, and runs nothing. `check` and `export` take it too.
- `--json` prints the same as a JSON object instead:
  ```json
  {"file": "beam.bbm", "lines": [
    {"line": 12, "statement": "M_max := q*L^2/8 = ? kN*m", "result": "54 kN*m"},
    {"line": 14, "statement": "y := x + 2 m", "error": "units do not match: L·M vs L"}
  ]}
  ```

## `beermat check <file>`

Reports problems without changing the file, one per line, as `file:line: message`:

- syntax errors and evaluation errors (units, undefined names, …);
- stale results: the `results:` hash doesn't match the file, or a cached result differs from what the sheet now computes;
- frontmatter settings that are invalid or not supported yet.

Prints `ok` when there is nothing to report.

## `beermat export <file> [--to md|latex] [--out <file>]`

Writes the worksheet as Markdown with display math (the default) or as a standalone LaTeX document; to standard output unless `--out` is given. See [export.md](export.md).

## `beermat import <file.xmcd> [--out <file.bbm>]`

Converts an `.xmcd` worksheet to `.bbm` (next to it unless `--out` is given) and lists on standard error what could not be imported faithfully. See [xmcd.md](xmcd.md).

## `beermat mcp`

Serves worksheets to MCP clients such as Claude Code on standard input and output. See [mcp.md](mcp.md).

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success, nothing to report |
| 1 | The sheet has errors, or (`check`) stale results |
| 2 | Bad arguments, or the file can't be read or written |

## Installing

The desktop installers bring the `beermat` command with them (from 1.0.0-dev2; Dan, 2026-11-01): a small script in the app's folder that runs the command line with the app's own Java and Python, so nothing else is needed and the two always match.

| System | The script | On the PATH |
|---|---|---|
| Windows | `bin\beermat.cmd` in the app's folder | the installer's "Add the beermat command to the PATH" (ticked by default; the user's PATH, or the system's for an install for everyone; the uninstaller takes it out). The portable zip has the same `bin` folder, to add by hand |
| macOS | `Beermat.app/Contents/app/bin/beermat` (not beside the `Beermat` launcher: macOS names ignore case) | Tools → Install the beermat command…: a link in `/usr/local/bin`, after macOS asks for the password |
| Linux | `/opt/beermat/bin/beermat` | Tools → Install the beermat command…: a link in `~/.local/bin`, no password |

The app's Java has no `java` launcher of its own (Compose leaves the JDK's launchers out), so the build puts it back in the runtime image (about 300 KB), and adds the command line's code to the app (about 2 MB). The release workflow runs `beermat eval examples/symbolic.bbm` from each system's package.

The separate zips stay, for machines without the desktop app (a server, a build machine); they need Java 21 for now.

## Building

`./gradlew :cli:installDist` puts a launcher in `cli/build/install/beermat/bin/` (`beermat` and `beermat.bat`). The distribution carries the bundled Python with SymPy in `python/`, beside `lib/` ([symbolic.md](symbolic.md#bundled-sympy)), so symbolic evaluation does as much as in the app; that makes it one zip per system (`beermat-cli-windows.zip`, `-macos`, `-linux`), about 30 MB each. It needs Java 21 for now.
