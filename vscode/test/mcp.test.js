// The AI tools on the sheets open in VS Code (docs/vscode.md#step-4-in-detail): MCP over HTTP on
// 127.0.0.1, refusing web pages; the tools run in `beermat mcp --host` on the live document. The full
// round trip needs a built command line, so it runs only with BEERMAT_COMMAND set:
//   ./gradlew :cli:installDist
//   BEERMAT_COMMAND=cli/build/install/beermat/bin/beermat.bat node --test vscode/test/mcp.test.js
const test = require('node:test');
const assert = require('node:assert');
const Module = require('node:module');

const command = process.env.BEERMAT_COMMAND;
let text = '# Live\n\n```calc\nL := 6 m\nq := 12 kN/m\n```\n';
const document = {
  uri: { scheme: 'file', fsPath: '/sheets/beam.bbm' },
  eol: 1,
  isClosed: false,
  getText: () => text,
};
class Position { constructor(line, character) { this.line = line; this.character = character; } }
class Range { constructor(start, end) { this.start = start; this.end = end; } }
const offset = p => text.split('\n').slice(0, p.line).reduce((n, l) => n + l.length + 1, 0) + p.character;
const vscode = {
  Position, Range,
  EndOfLine: { LF: 1, CRLF: 2 },
  WorkspaceEdit: class { replace(uri, range, t) { this.change = { range, t }; } },
  workspace: {
    isTrusted: true,
    textDocuments: [document],
    getConfiguration: () => ({ get: key => (key === 'command' ? command : key === 'mcpPort' ? 7399 : undefined) }),
    applyEdit: async e => { const { range, t } = e.change; text = text.slice(0, offset(range.start)) + t + text.slice(offset(range.end)); return true; },
  },
};
const resolve = Module._resolveFilename;
Module._resolveFilename = function (request, ...rest) { return request === 'vscode' ? 'vscode' : resolve.call(this, request, ...rest); };
require.cache.vscode = { id: 'vscode', filename: 'vscode', loaded: true, exports: vscode };
const { mcp } = require('../extension.js');

const post = (body, headers = {}) => fetch('http://127.0.0.1:7399/mcp', { method: 'POST', body: JSON.stringify(body), headers: { 'Content-Type': 'application/json', ...headers } });
const call = (id, name, args) => post({ jsonrpc: '2.0', id, method: 'tools/call', params: { name, arguments: args } }).then(r => r.json());

test.before(async () => {
  mcp.active = document;
  mcp.start();
  await new Promise(r => setTimeout(r, 200));
});
test.after(() => mcp.stop());

test('a web page is refused, and only POST is served', async () => {
  assert.strictEqual((await post({}, { Origin: 'https://example.com' })).status, 403);
  assert.strictEqual((await fetch('http://127.0.0.1:7399/mcp')).status, 405);
});

test('the tools read the live sheet, and their edits go into the document', { skip: !command && 'BEERMAT_COMMAND not set' }, async () => {
  const init = await post({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }).then(r => r.json());
  assert.strictEqual(init.result.serverInfo.name, 'beermat');
  const read = await call(2, 'read_worksheet', {});
  assert.match(read.result.content[0].text, /q := 12 kN\/m/);
  const inserted = await call(3, 'insert_region', { kind: 'calc', content: 'M := q*L^2/8 = ?' });
  assert.notStrictEqual(inserted.result.isError, true, JSON.stringify(inserted));
  assert.match(text, /M := q\*L\^2\/8 = \?\s+#= 54 kN\*m/);
});
