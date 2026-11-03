// Beermat's VS Code extension (docs/vscode.md): .bbm files open on their page, drawn by the web version
// (media/, the same build as beermat.pub/try) in a web view. The document VS Code holds is the truth:
// the page gets its text, sends back every change as the whole text, and shows changes made elsewhere,
// so saving, undo, git and other extensions all work on the same text.
const vscode = require('vscode');
const childProcess = require('child_process');
const crypto = require('crypto');

const VIEW = 'beermat.sheet';

function activate(context) {
  python.saved = context.globalState;
  context.subscriptions.push(
    vscode.window.registerCustomEditorProvider(VIEW, new SheetEditor(context), {
      // The page keeps its state (zoom, the cursor) while its tab is in the background.
      webviewOptions: { retainContextWhenHidden: true },
      supportsMultipleEditorsPerDocument: false,
    }),
    vscode.commands.registerCommand('beermat.openOnPage', uri => {
      uri = uri ?? vscode.window.activeTextEditor?.document.uri;
      if (uri) vscode.commands.executeCommand('vscode.openWith', uri, VIEW);
    }),
    vscode.commands.registerCommand('beermat.importXmcd', importXmcd),
    vscode.commands.registerCommand('beermat.openAsText', uri => {
      uri = uri ?? activeSheet();
      if (uri) vscode.commands.executeCommand('vscode.openWith', uri, 'default', vscode.ViewColumn.Beside);
    }),
  );
}

/**
 * Imports an .xmcd worksheet (docs/xmcd.md) with the installed `beermat import`, beside it as a .bbm
 * (or where the user says, when that exists), and opens it on its page, telling what didn't come across.
 */
async function importXmcd(uri) {
  uri ??= (await vscode.window.showOpenDialog({ filters: { 'Worksheets': ['xmcd'] }, title: 'Import an .xmcd worksheet' }))?.[0];
  if (!uri) return;
  if (!(await helper.found())) {
    const pick = await vscode.window.showErrorMessage('Importing .xmcd needs the Beermat desktop app (its beermat command).', 'Download');
    if (pick) vscode.env.openExternal(vscode.Uri.parse('https://beermat.pub/download'));
    return;
  }
  let target = uri.with({ path: uri.path.replace(/\.xmcd$/i, '') + '.bbm' });
  try {
    await vscode.workspace.fs.stat(target);
    target = await vscode.window.showSaveDialog({ defaultUri: target, filters: { 'Beermat worksheet': ['bbm'] }, title: 'Save the imported worksheet' });
    if (!target) return;
  } catch {
    // Not there yet: the place beside the .xmcd is free.
  }
  const warnings = await new Promise(resolve => {
    let err = '';
    const p = helper.spawn(['import', uri.fsPath, '--out', target.fsPath]);
    p.stderr.on('data', d => (err += d));
    p.on('error', e => resolve({ failed: e.message }));
    p.on('exit', code => resolve(code === 0 ? { lines: err.split(/\r?\n/).filter(l => l.trim()) } : { failed: err.trim() || `beermat import stopped (${code})` }));
  });
  if (warnings.failed) return vscode.window.showErrorMessage(`The import didn't work: ${warnings.failed}`);
  await vscode.commands.executeCommand('vscode.openWith', target, VIEW);
  if (warnings.lines.length > 0) vscode.window.showWarningMessage(`Imported; ${warnings.lines.length} thing(s) didn't come across as they were.`, { detail: warnings.lines.slice(0, 20).join('\n') });
}

/** The sheet in the active tab, when it is open on its page. */
function activeSheet() {
  const input = vscode.window.tabGroups.activeTabGroup.activeTab?.input;
  return input instanceof vscode.TabInputCustom && input.viewType === VIEW ? input.uri : undefined;
}

class SheetEditor {
  constructor(context) {
    this.context = context;
  }

  resolveCustomTextEditor(document, panel) {
    const media = vscode.Uri.joinPath(this.context.extensionUri, 'media');
    panel.webview.options = { enableScripts: true, localResourceRoots: [media] };
    panel.webview.html = page(panel.webview, media);

    // The page works in \n; a file written with \r\n keeps them.
    const text = () => document.getText().replace(/\r\n/g, '\n');
    const load = () => panel.webview.postMessage({ type: 'load', text: text() });
    const changes = vscode.workspace.onDidChangeTextDocument(e => {
      if (e.document.uri.toString() === document.uri.toString() && e.contentChanges.length > 0) load();
    });
    panel.onDidDispose(() => changes.dispose());

    // Edits one after another, each against the text the last one left.
    let edits = Promise.resolve();
    panel.webview.onDidReceiveMessage(async m => {
      switch (m.type) {
        case 'ready':
          load();
          // The sheet's folder, where its data files and pictures are (a file on disk only).
          if (document.uri.scheme === 'file') panel.webview.postMessage({ type: 'folder', text: require('path').dirname(document.uri.fsPath) });
          // SymPy through the installed beermat, when there is one (docs/vscode.md#step-3-in-detail).
          if (vscode.workspace.isTrusted && await helper.found()) panel.webview.postMessage({ type: 'helper', yes: true });
          return;
        case 'file': return panel.webview.postMessage({ type: 'filed', id: m.id, ...(await readFile(m.text)) });
        case 'consent': return panel.webview.postMessage({ type: 'consented', id: m.id, yes: await python.allow(m.text) });
        case 'bridge': {
          // A Python function runs only from a file the user said may run, as it is now.
          const text = (await python.mayRun(m.text)) ? await helper.ask(m.text) : JSON.stringify({ error: "this Python file hasn't been allowed to run" });
          return panel.webview.postMessage({ type: 'bridged', id: m.id, text });
        }
        case 'edit': return (edits = edits.then(() => edit(document, m.text, text())));
        case 'save': return document.save();
        case 'open': return vscode.env.openExternal(vscode.Uri.parse(m.text));
        case 'text': return vscode.commands.executeCommand('vscode.openWith', document.uri, 'default', vscode.ViewColumn.Beside);
        case 'word': return saveWord(document, m.text);
        case 'ask': return panel.webview.postMessage({ type: 'answer', id: m.id, ...(await ask(m.text)) });
      }
    });
  }
}

/** Replaces the document's text [before] with [after], changing only the part between what they share at each end. */
async function edit(document, after, before) {
  if (after === before) return;
  let start = 0;
  while (start < before.length && start < after.length && before[start] === after[start]) start++;
  let end = 0;
  while (end < before.length - start && end < after.length - start && before[before.length - 1 - end] === after[after.length - 1 - end]) end++;
  const crlf = document.eol === vscode.EndOfLine.CRLF;
  // Offsets in the \n text, as positions in the document (which may have \r\n).
  const position = offset => {
    const head = before.slice(0, offset);
    const line = head.split('\n').length - 1;
    return new vscode.Position(line, offset - (head.lastIndexOf('\n') + 1));
  };
  const inserted = after.slice(start, after.length - end);
  const change = new vscode.WorkspaceEdit();
  change.replace(document.uri, new vscode.Range(position(start), position(before.length - end)), crlf ? inserted.replace(/\n/g, '\r\n') : inserted);
  await vscode.workspace.applyEdit(change);
}

/** Export to Word: the page made the document ([base64]); it goes where the user says, beside the sheet by default. */
async function saveWord(document, base64) {
  const target = await vscode.window.showSaveDialog({
    defaultUri: document.uri.with({ path: document.uri.path.replace(/\.bbm(\.md)?$/, '') + '.docx' }),
    filters: { 'Word document': ['docx'] },
    title: 'Export to Word',
  });
  if (target) await vscode.workspace.fs.writeFile(target, Buffer.from(base64, 'base64'));
}

/**
 * A data file or picture the sheet names (READCSV, a linked picture), for the page, as base64: in a
 * trusted workspace only (VS Code's Restricted Mode reads nothing for a sheet).
 */
async function readFile(path) {
  if (!vscode.workspace.isTrusted) return { text: '', yes: false };
  try {
    const bytes = await vscode.workspace.fs.readFile(vscode.Uri.file(path));
    return { text: Buffer.from(bytes).toString('base64'), yes: true };
  } catch {
    return { text: '', yes: false };
  }
}

/**
 * The user's consent to run a sheet's Python files (docs/symbolic.md#python-functions-in-a-sheet), as the
 * desktop app asks it: once per file, showing its path; a yes is kept for that file and its contents (a
 * changed file asks again), a no until VS Code closes. Never in Restricted Mode.
 */
const python = {
  saved: undefined,
  refused: new Set(),

  /** The file's path and contents as one key. */
  async key(path) {
    const bytes = await vscode.workspace.fs.readFile(vscode.Uri.file(path));
    return 'python.' + crypto.createHash('sha256').update(path).update('\0').update(bytes).digest('hex');
  },

  /** Whether [path] may run, asking the user the first time. */
  async allow(path) {
    if (!vscode.workspace.isTrusted) return false;
    let key;
    try {
      key = await this.key(path);
    } catch {
      return false;
    }
    if (this.saved?.get(key)) return true;
    if (this.refused.has(key)) return false;
    const pick = await vscode.window.showWarningMessage(
      'This sheet runs Python code from a file on your computer. Run it?',
      { modal: true, detail: `${path}\n\nOnly say yes if you trust where the file came from.` },
      'Run it',
    );
    if (pick === 'Run it') {
      await this.saved?.update(key, true);
      return true;
    }
    this.refused.add(key);
    return false;
  },

  /** Whether a bridge [request] may go: anything but a Python function, or one from a file allowed as it is now. */
  async mayRun(request) {
    let r;
    try {
      r = JSON.parse(request);
    } catch {
      return false;
    }
    if (!r.op) return true;
    if (!vscode.workspace.isTrusted || typeof r.path !== 'string') return false;
    try {
      return !!this.saved?.get(await this.key(r.path));
    } catch {
      return false;
    }
  },
};

/** The page's questions, which a web view can't ask itself: a line of text, a matrix's size, or how to resize one. */
async function ask(kind) {
  if (kind.startsWith('text\n')) {
    const [, prompt, value] = kind.split('\n');
    const text = await vscode.window.showInputBox({ prompt, value });
    return { text: text ?? '', yes: text !== undefined };
  }
  if (kind === 'matrix') {
    const text = await vscode.window.showInputBox({ title: 'Insert matrix', prompt: 'Rows and columns', value: '3, 1' });
    return { text: text ?? '', yes: false };
  }
  const pick = await vscode.window.showQuickPick(['Insert', 'Delete'], { title: 'Resize matrix' });
  if (!pick) return { text: '', yes: false };
  const text = await vscode.window.showInputBox({ title: `${pick} rows and columns`, prompt: "Rows and columns, after the cursor's cell (Insert) or from it (Delete)", value: '1, 0' });
  return { text: text ?? '', yes: pick === 'Insert' };
}

/** The web version's page, its files found through the web view's own addresses. */
function page(webview, media) {
  const base = webview.asWebviewUri(media).toString() + '/';
  const source = webview.cspSource;
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${source} data: blob:; style-src ${source} 'unsafe-inline'; font-src ${source} data:; script-src ${source} 'wasm-unsafe-eval' 'unsafe-eval'; connect-src ${source};">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <base href="${base}">
  <style>html, body { margin: 0; height: 100%; overflow: hidden; background: #e8e8e8; }</style>
  <script src="${base}web.js"></script>
</head>
<body></body>
</html>`;
}

/**
 * The installed beermat as a helper (docs/vscode.md#step-3-in-detail): `beermat bridge`, one process for
 * every sheet, started at the first request and kept; one JSON request per line, answered in order.
 * The command is the `beermat.command` setting, or `beermat` on the PATH (the desktop app puts it there).
 */
const helper = {
  process: undefined,
  waiting: [],
  buffer: '',
  checked: undefined,

  command() {
    return vscode.workspace.getConfiguration('beermat').get('command') || 'beermat';
  },

  /** Runs [command] with [args]; on Windows through the shell, which finds beermat.cmd. */
  spawn(args, options = {}) {
    // On Windows one command line for the shell: fixed words, and paths the user chose, in quotes (a Windows path has none).
    if (process.platform === 'win32') {
      const line = [this.command(), ...args].map(a => (/^[\w.-]+$/.test(a) ? a : `"${a}"`)).join(' ');
      return childProcess.spawn(line, { shell: true, windowsHide: true, ...options });
    }
    return childProcess.spawn(this.command(), args, options);
  },

  /** Whether beermat runs here, asked once. */
  found() {
    this.checked ??= new Promise(resolve => {
      try {
        const p = this.spawn(['--version']);
        p.on('error', () => resolve(false));
        p.on('exit', code => resolve(code === 0));
      } catch {
        resolve(false);
      }
    });
    return this.checked;
  },

  /** The answer to [request], as bridge.py writes it; an error answer when beermat isn't running. */
  ask(request) {
    return new Promise(resolve => {
      if (!this.process) this.start();
      this.waiting.push(resolve);
      this.process.stdin.write(request + '\n');
    });
  },

  start() {
    // Python functions too: every request for one passes python.mayRun first.
    const p = this.spawn(['bridge', '--allow-python']);
    this.process = p;
    p.stdout.setEncoding('utf8');
    p.stdout.on('data', chunk => {
      this.buffer += chunk;
      let end;
      while ((end = this.buffer.indexOf('\n')) >= 0) {
        const line = this.buffer.slice(0, end).trim();
        this.buffer = this.buffer.slice(end + 1);
        if (line) this.waiting.shift()?.(line);
      }
    });
    const stopped = () => {
      if (this.process !== p) return;
      this.process = undefined;
      this.buffer = '';
      for (const resolve of this.waiting.splice(0)) resolve(JSON.stringify({ error: 'beermat stopped; it starts again with the next request' }));
    };
    p.on('error', stopped);
    p.on('exit', stopped);
  },

  stop() {
    this.process?.kill();
    this.process = undefined;
  },
};

function deactivate() {
  helper.stop();
}

module.exports = { activate, deactivate, edit, helper, python };
