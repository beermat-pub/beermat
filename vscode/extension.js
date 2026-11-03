// Beermat's VS Code extension (docs/vscode.md): .bbm files open on their page, drawn by the web version
// (media/, the same build as beermat.pub/try) in a web view. The document VS Code holds is the truth:
// the page gets its text, sends back every change as the whole text, and shows changes made elsewhere,
// so saving, undo, git and other extensions all work on the same text.
const vscode = require('vscode');

const VIEW = 'beermat.sheet';

function activate(context) {
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
    vscode.commands.registerCommand('beermat.openAsText', uri => {
      uri = uri ?? activeSheet();
      if (uri) vscode.commands.executeCommand('vscode.openWith', uri, 'default', vscode.ViewColumn.Beside);
    }),
  );
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
        case 'ready': return load();
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

function deactivate() {}

module.exports = { activate, deactivate, edit };
