// The extension's consent to run a sheet's Python files (docs/vscode.md#step-3-in-detail): asked once
// per file and its contents, never in Restricted Mode, and checked again for every Python request.
// Run with `node --test vscode/test/python.test.js` (CI does).
const test = require('node:test');
const assert = require('node:assert');
const Module = require('node:module');

const files = { '/sheets/tools.py': 'def f(x):\n    return 2 * x\n' };
let asked = 0;
let reply = 'Run it';
const vscode = {
  Uri: { file: path => ({ path }) },
  workspace: {
    isTrusted: true,
    fs: { readFile: async uri => { if (!(uri.path in files)) throw new Error('no file'); return Buffer.from(files[uri.path]); } },
    getConfiguration: () => ({ get: () => undefined }),
  },
  window: { showWarningMessage: async () => { asked++; return reply; } },
};
const resolve = Module._resolveFilename;
Module._resolveFilename = function (request, ...rest) { return request === 'vscode' ? 'vscode' : resolve.call(this, request, ...rest); };
require.cache.vscode = { id: 'vscode', filename: 'vscode', loaded: true, exports: vscode };
const { python } = require('../extension.js');

const store = new Map();
python.saved = { get: k => store.get(k), update: async (k, v) => store.set(k, v) };
const call = JSON.stringify({ op: 'call', path: '/sheets/tools.py', version: '1', name: 'f', args: [2] });

test('a symbolic request always passes; a Python one only once the file is allowed', async () => {
  assert.strictEqual(await python.mayRun('{"expr": ["v", 0], "keywords": [], "words": {}}'), true);
  assert.strictEqual(await python.mayRun(call), false);
  assert.strictEqual(await python.allow('/sheets/tools.py'), true);
  assert.strictEqual(asked, 1);
  assert.strictEqual(await python.mayRun(call), true);
  // Asked once: the yes is kept.
  assert.strictEqual(await python.allow('/sheets/tools.py'), true);
  assert.strictEqual(asked, 1);
});

test('a changed file asks again, and a no is not asked again', async () => {
  files['/sheets/tools.py'] += '# changed\n';
  assert.strictEqual(await python.mayRun(call), false);
  reply = undefined;
  assert.strictEqual(await python.allow('/sheets/tools.py'), false);
  assert.strictEqual(await python.allow('/sheets/tools.py'), false);
  assert.strictEqual(asked, 2);
});

test('nothing runs in Restricted Mode, nor a file that is not there', async () => {
  vscode.workspace.isTrusted = false;
  assert.strictEqual(await python.allow('/sheets/tools.py'), false);
  assert.strictEqual(await python.mayRun(call), false);
  vscode.workspace.isTrusted = true;
  assert.strictEqual(await python.allow('/sheets/nothing.py'), false);
  assert.strictEqual(await python.mayRun('not json'), false);
});
