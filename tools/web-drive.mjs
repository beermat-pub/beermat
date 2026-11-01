// Checks the web version in a real browser, with no one at the screen (docs/web.md): headless Edge (or
// Chrome, through $BROWSER), driven over the DevTools protocol. It opens the page, prints its console and
// errors, clicks and types keys into it, reloads it, and saves screenshots of the page itself (never the screen).
//
//   ./gradlew :web:wasmJsBrowserDistribution
//   (cd web/build/dist/wasmJs/productionExecutable && python -m http.server 8765)
//   node tools/web-drive.mjs http://127.0.0.1:8765/index.html out/web "@330,700" "x:3m{Enter}x=mm{Enter}"
//
// Keys: text, and {Enter}, {Tab}, {Escape}, {Backspace}; a first argument "@x,y" is where to click first.
// Saves <prefix>-loaded.png, then (with keys) <prefix>-typed.png and <prefix>-reloaded.png.
import { spawn } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';

const [url, prefix, ...keys] = process.argv.slice(2);
const profile = prefix + '-profile';
mkdirSync(profile, { recursive: true });
const browser = process.env.BROWSER ?? 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const edge = spawn(browser, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=9333',
  `--user-data-dir=${profile}`, '--window-size=1300,900', 'about:blank',
], { stdio: 'ignore' });
const sleep = ms => new Promise(r => setTimeout(r, ms));

let target;
for (let i = 0; i < 50 && !target; i++) {
  await sleep(200);
  try { target = (await (await fetch('http://127.0.0.1:9333/json')).json()).find(t => t.type === 'page'); } catch {}
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
  if (m.method === 'Runtime.consoleAPICalled') console.log('console:', m.params.args.map(a => a.value ?? a.description).join(' '));
  if (m.method === 'Runtime.exceptionThrown') console.log('exception:', m.params.exceptionDetails.exception?.description ?? m.params.exceptionDetails.text);
});
const send = (method, params = {}) => new Promise(r => { const n = ++id; pending.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
const shot = async name => {
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(`${prefix}-${name}.png`, Buffer.from(data, 'base64'));
  console.log('saved', `${prefix}-${name}.png`);
};

await send('Runtime.enable');
await send('Page.enable');
await send('Page.navigate', { url });
await sleep(8000);
await shot('loaded');
// Click on the page, so it has the keyboard focus (and the crosshair goes there).
const click = async (x, y) => {
  for (const type of ['mousePressed', 'mouseReleased']) await send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 });
};
if (keys.length) {
  const [cx, cy] = keys[0].startsWith('@') ? keys.shift().slice(1).split(',').map(Number) : [400, 600];
  await click(cx, cy);
  await sleep(300);
  const named = { Enter: [13, 'Enter', '\r'], Tab: [9, 'Tab', '\t'], Escape: [27, 'Escape', ''], Backspace: [8, 'Backspace', ''] };
  for (const k of keys) {
    const parts = k.match(/\{[^}]+\}|[^{]+/g);
    for (const part of parts) {
      if (part.startsWith('{')) {
        const name = part.slice(1, -1);
        const [code, key, text] = named[name];
        await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', windowsVirtualKeyCode: code, key, code: key });
        if (text) await send('Input.dispatchKeyEvent', { type: 'char', text, key });
        await send('Input.dispatchKeyEvent', { type: 'keyUp', windowsVirtualKeyCode: code, key, code: key });
      } else {
        for (const ch of part) {
          await send('Input.dispatchKeyEvent', { type: 'keyDown', text: ch, key: ch, unmodifiedText: ch });
          await send('Input.dispatchKeyEvent', { type: 'keyUp', key: ch });
          await sleep(30);
        }
      }
      await sleep(80);
    }
  }
  await sleep(1500);
  await shot('typed');
  // Then the page loaded again in the same browser: the sheet should come back from its storage.
  await send('Page.reload');
  await sleep(8000);
  await shot('reloaded');
}
ws.close();
edge.kill();
process.exit(0);
