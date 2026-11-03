// The extension's helper (docs/vscode.md#step-3-in-detail): `beermat bridge` found, asked in order,
// and refusing Python functions. Needs a built command line, so it runs only with BEERMAT_COMMAND set:
//   ./gradlew :cli:installDist
//   BEERMAT_COMMAND=cli/build/install/beermat/bin/beermat.bat node --test vscode/test/helper.test.js
const test = require('node:test');
const assert = require('node:assert');
const Module = require('node:module');

const command = process.env.BEERMAT_COMMAND;
const vscode = { workspace: { getConfiguration: () => ({ get: () => command }) } };
const resolve = Module._resolveFilename;
Module._resolveFilename = function (request, ...rest) { return request === 'vscode' ? 'vscode' : resolve.call(this, request, ...rest); };
require.cache.vscode = { id: 'vscode', filename: 'vscode', loaded: true, exports: vscode };
const { helper } = require('../extension.js');

test('beermat bridge answers symbolic requests in order, and not Python functions', { skip: !command && 'BEERMAT_COMMAND not set' }, async () => {
  assert.strictEqual(await helper.found(), true);
  const [twice, python, series] = await Promise.all([
    helper.ask('{"expr": ["+", ["v", 0], ["v", 0]], "keywords": [["factor"]], "words": {}}'),
    helper.ask('{"op": "call", "path": "x.py"}'),
    helper.ask('{"expr": ["f", "sin", ["v", 0]], "keywords": [["series", ["v", 0], ["q", "4", "1"]]], "words": {}}'),
  ]);
  assert.deepStrictEqual(JSON.parse(twice), { ok: ['*', ['q', '2', '1'], ['v', 0]] });
  assert.ok(JSON.parse(python).error);
  assert.ok(JSON.parse(series).ok);
  helper.stop();
});

test('a command that is not there is not found', { skip: !command && 'BEERMAT_COMMAND not set' }, async () => {
  const saved = vscode.workspace.getConfiguration;
  vscode.workspace.getConfiguration = () => ({ get: () => 'no-such-beermat-command' });
  helper.checked = undefined;
  assert.strictEqual(await helper.found(), false);
  vscode.workspace.getConfiguration = saved;
});
