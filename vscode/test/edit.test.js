// The extension's edits (docs/vscode.md): the page sends the whole text; only the part that changed goes
// into the document, at the right line and column, \r\n kept. Run with `node --test vscode/test/edit.test.js` (CI does).
const test = require('node:test');
const assert = require('node:assert');
const Module = require('node:module');

// Just enough of the vscode module: positions, ranges, and an edit that applies to a text.
class Position { constructor(line, character) { this.line = line; this.character = character; } }
class Range { constructor(start, end) { this.start = start; this.end = end; } }
let applied;
const vscode = {
  Position, Range,
  EndOfLine: { LF: 1, CRLF: 2 },
  WorkspaceEdit: class { replace(uri, range, text) { this.change = { range, text }; } },
  workspace: { applyEdit: async e => { applied = e.change; return true; } },
};
const resolve = Module._resolveFilename;
Module._resolveFilename = function (request, ...rest) { return request === 'vscode' ? 'vscode' : resolve.call(this, request, ...rest); };
require.cache.vscode = { id: 'vscode', filename: 'vscode', loaded: true, exports: vscode };
const { edit } = require('../extension.js');

/** The document's text after the edit, applied by line and column as VS Code would. */
function apply(document, change) {
  const lines = document.split(document.includes('\r\n') ? '\r\n' : '\n');
  const offset = p => lines.slice(0, p.line).reduce((n, l) => n + l.length + (document.includes('\r\n') ? 2 : 1), 0) + p.character;
  return document.slice(0, offset(change.range.start)) + change.text + document.slice(offset(change.range.end));
}

async function check(document, after) {
  applied = undefined;
  const eol = document.includes('\r\n') ? vscode.EndOfLine.CRLF : vscode.EndOfLine.LF;
  await edit({ uri: 'sheet.bbm', eol }, after, document.replace(/\r\n/g, '\n'));
  return applied && apply(document, applied);
}

test('only the changed part is replaced', async () => {
  const before = '# Beam\n\n```calc\nL := 6 m\n```\n';
  const after = '# Beam\n\n```calc\nL := 7 m\nq := 2\n```\n';
  assert.strictEqual(await check(before, after), after);
  assert.deepStrictEqual([applied.range.start.line, applied.range.start.character], [3, 5]);
});

test('a document with \\r\\n keeps them', async () => {
  const before = 'a\r\nb\r\nc\r\n';
  assert.strictEqual(await check(before, 'a\nB\nnew\nc\n'), 'a\r\nB\r\nnew\r\nc\r\n');
});

test('the same text is no edit', async () => {
  assert.strictEqual(await check('x\n', 'x\n'), undefined);
});

test('text added at the end, and everything taken away', async () => {
  assert.strictEqual(await check('x\n', 'x\ny\n'), 'x\ny\n');
  assert.strictEqual(await check('x\ny\n', ''), '');
});
