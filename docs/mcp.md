# MCP server

> Status: **built**: from the CLI (milestone 9) and inside the app (with the editor, milestone 5).

Claude Code, or any MCP client, can read, evaluate and edit worksheets through Beermat's Model Context Protocol server. It runs two ways, with the same tools:

- **In the app** (when a sheet is open): over MCP's streamable HTTP transport on `http://127.0.0.1:7341/mcp`. Tools work on the open sheet *live*: Claude reads it with your unsaved changes, and its edits show at once, unsaved, and undo with Ctrl+Z. `path` may be left out for the open sheet. Tools → "Connect Claude Code" copies the command to run once:

  ```
  claude mcp add --transport http beermat http://127.0.0.1:7341/mcp
  ```

  It listens on 127.0.0.1 only and refuses requests from web pages (an `Origin` other than localhost). `BEERMAT_MCP_PORT` picks another port; a second window can't share the port, so its server is off (the Tools menu says so).
- **In VS Code** (the extension, docs/vscode.md#step-4-in-detail): the same tools on `http://127.0.0.1:7342/mcp`, on the sheets open there, live as in the app; **Beermat: Connect Claude Code** copies the command. The tools run in `beermat mcp --host`, which asks the extension for each sheet.
- **From the CLI**, `beermat mcp`, on stdin/stdout, editing files (the app follows the file on disk):

  ```
  claude mcp add beermat -- beermat mcp
  ```

The protocol and tools live in the `mcp` module (`Mcp`, with a `Documents` interface for where sheets are read and written); the CLI and the app are its two transports.


## Other AI tools, and self-hosted models

MCP is an open protocol, not a Claude feature: any MCP client can use Beermat, with the same tools. Claude Code is the one we test with; others work the same way (at the time of writing, for example: Claude Desktop, VS Code's agent mode, Cursor, Windsurf, Zed, Cline, Continue, Goose, LM Studio, and Open WebUI). Most accept either form:

- a **command** (stdio): `beermat mcp`;
- a **URL** (streamable HTTP), while the app is open: `http://127.0.0.1:7341/mcp`.

Self-hosted models work through such a client: a local model in LM Studio, or one served by Ollama behind a client that speaks MCP, uses the same server. How well it goes depends on the model's skill with tools; small local models may need the guide spelled out.

**Getting a new session up to speed:** the server's instructions tell every client to call the `guide` tool first. It returns [ai-guide.md](ai-guide.md): the file format, the syntax at a glance, the tools and good practice. Nothing else is needed; there is no separate setup per AI tool.
## Tools

Every tool takes `path`, the worksheet file (optional in the app, for the open sheet). Regions are named by their id (`r1` or `#r1`) or by kind and position (`calc 2`, `plot 1`, `text 3`), as `read_worksheet` lists them.

| Tool | Does |
|---|---|
| `guide` | The guide for a new session ([ai-guide.md](ai-guide.md)): call it first |
| `read_worksheet` | The regions in order, with every statement's fresh result (`#=`) or error (`#!`) and plot problems |
| `evaluate` | Evaluates an expression in the sheet's context, after all its statements or after a given `region`; nothing is written. `q*L^2/8 = ? kN*m` picks the unit; `diff(x^3, x) -> ?` evaluates symbolically ([symbolic.md](symbolic.md)) |
| `check` | What `beermat check` reports |
| `update_region` | Replaces a region's content (for a fence, the lines between its fence lines) |
| `insert_region` | Adds a `calc`, `plot` or `text` region after another one, or at the end |
| `delete_region` | Removes a region |
| `export` | The sheet as Markdown with display math, or as a LaTeX document |

The editing tools write the file with fresh results and an updated `results:` hash, and return the sheet as `read_worksheet` would.

## Protocol

JSON-RPC 2.0, one message per line: `initialize` (with server instructions describing the file format), `ping`, `tools/list`, `tools/call`. JSON is read with the YAML parser, which the format module already uses, so the server adds no dependency.

Later: `get_plot_image` (needs the app's renderer), `move_region` (with positions), and the in-app server.
