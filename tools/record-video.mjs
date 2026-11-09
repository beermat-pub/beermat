// Records a feature video (docs/web.md#the-video-page) from the web version, with no one at the screen: headless
// Edge opens /try?video and plays a script at a human pace, while Chrome's screencast hands over the page's
// frames (1920×1080) with their times. Nothing on the screen is captured. The frames are then played again, at
// their times, on a plain canvas in a second page, which the browser records as WebM (VP9, 30 fps): no other
// program needed. (Recording the page's own canvas directly gave broken files.) The key badge and the captions
// are drawn by the page itself (VideoApp), so they are in the frames.
//
//   ./gradlew :web:wasmJsBrowserDistribution
//   (cd web/build/dist/wasmJs/productionExecutable && python -m http.server 8765)
//   node tools/record-video.mjs http://127.0.0.1:8765/index.html videos/01-type-math.txt out/01-type-math.webm [--frames 6,14] [--square]
//
// Script steps, one per line (# for comments): `caption <text>` (empty hides it), `type <keys>` (letters as
// typed; «enter», «esc», «bs», «tab», «space», arrows «left» «right» «up» «down», chords «ctrl-g»,
// «ctrl-shift-4»), `pause <ms>`, `click <x> <y>` and `hover <x> <y>` (page pixels, 1280×720), `key <text>` (show a badge by hand),
// `zoom <percent>` (the whole video at that zoom). The page draws a mouse pointer, which glides to each click and hover.
import { spawn } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { tmpdir } from 'node:os';

const [url, scriptPath, outPath, ...options] = process.argv.slice(2);
// --frames 6,14,22: also save the frames at those seconds (out/01-type-math.webm.6s.jpg), to look at.
const sample = (options[options.indexOf('--frames') + 1] ?? '').split(',').filter(Boolean).map(Number);
// --square: the caption and key badge drawn inside the square x 240–960 (page pixels), so that cropping
// 1080×1080 at x 360 of the video gives a square cut with both (for LinkedIn).
const square = options.includes('--square');
const steps = readFileSync(scriptPath, 'utf8').split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith('#'));
const browser = process.env.BROWSER ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const profile = join(tmpdir(), `beermat-video-${process.pid}`);
const edge = spawn(browser, [
  '--headless=new', '--remote-debugging-port=9335', `--user-data-dir=${profile}`, '--autoplay-policy=no-user-gesture-required',
  '--allow-file-access-from-files',
  // A headless page stops drawing about ten seconds in, unless it draws as fast as it can (the recorder keeps a
  // frame every 1/30 s) and believes it has the focus (below); the rest keep it from being throttled as hidden.
  '--disable-frame-rate-limit', '--disable-features=CalculateNativeWinOcclusion', '--disable-renderer-backgrounding',
  '--disable-background-timer-throttling', '--disable-backgrounding-occluded-windows',
  '--window-size=1280,720', 'about:blank',
], { stdio: 'ignore' });
const sleep = ms => new Promise(r => setTimeout(r, ms));

let target;
for (let i = 0; i < 50 && !target; i++) {
  await sleep(200);
  try { target = (await (await fetch('http://127.0.0.1:9335/json')).json()).find(t => t.type === 'page'); } catch {}
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  if (m.method === 'Runtime.consoleAPICalled' && process.env.BEERMAT_VIDEO_DEBUG) console.log('console:', m.params.type, m.params.args.map(a => a.value ?? a.description).join(' ').slice(0, 300));
  if (m.method === 'Runtime.exceptionThrown') console.log('exception:', m.params.exceptionDetails.exception?.description ?? m.params.exceptionDetails.text);
  if (m.method === 'Page.screencastFrame') {
    // One frame each 1/30 s is enough: the page draws far faster.
    if (recording && (!frames.length || m.params.metadata.timestamp - frames.at(-1).t >= 1 / 30)) frames.push({ data: m.params.data, t: m.params.metadata.timestamp });
    ws.send(JSON.stringify({ id: ++id, method: 'Page.screencastFrameAck', params: { sessionId: m.params.sessionId } }));
  }
});
// The page's frames, as the screencast sends them: JPEG, and the time each was drawn.
const frames = [];
let recording = false;
const send = (method, params = {}) => new Promise(r => { const n = ++id; pending.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
const evaluate = async (expression, awaitPromise = false) => {
  const m = await send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true });
  if (m.result?.exceptionDetails) throw new Error(m.result.exceptionDetails.exception?.description ?? 'evaluation failed');
  return m.result?.result?.value;
};

await send('Runtime.enable');
await send('Emulation.setFocusEmulationEnabled', { enabled: true });
await send('Page.bringToFront');
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 720, deviceScaleFactor: 1.5, mobile: false });
// `zoom 125` anywhere in the script: the page at that zoom instead of 175 %, for a sheet that needs room.
const zoom = steps.find(s => s.startsWith('zoom '))?.slice(5).trim();
await send('Page.navigate', { url: url + (url.includes('?') ? '&' : '?') + 'video' + (zoom ? '=' + zoom : '') + (square ? '&square=240' : '') });
for (let i = 0; i < 100; i++) {
  await sleep(200);
  if (await evaluate('!!window.beermatVideo')) break;
}
await sleep(1500);

// The page keeps the keyboard focus after a click; the first click also puts the crosshair at the top of the sheet.
// The screencast has no mouse pointer, so the page draws one (a plain arrow above the canvas), and the
// mouse glides to each spot in small steps: the viewer sees where it goes, and Compose sees it enter a button.
let mouseX = 640, mouseY = 400;
const pointer = (x, y) => evaluate(`(() => {
  let p = document.getElementById('video-pointer');
  if (!p) {
    p = document.createElement('div');
    p.id = 'video-pointer';
    p.style.cssText = 'position:fixed;left:0;top:0;width:15px;height:21px;pointer-events:none;z-index:2147483647';
    p.innerHTML = '<svg width="15" height="21" viewBox="0 0 22 30"><path d="M1 1 L1 23 L7 17.5 L11 27 L15 25.3 L11 16 L19 16 Z" fill="#fff" stroke="#000" stroke-width="1.6" stroke-linejoin="round"/></svg>';
    document.body.appendChild(p);
  }
  p.style.transform = 'translate(' + ${x} + 'px,' + ${y} + 'px)';
})()`);
const moveTo = async (x, y) => {
  const steps = 14;
  const [x0, y0] = [mouseX, mouseY];
  for (let i = 1; i <= steps; i++) {
    const t = i / steps, e = t * t * (3 - 2 * t);
    const [mx, my] = [x0 + (x - x0) * e, y0 + (y - y0) * e];
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: mx, y: my });
    await pointer(mx, my);
    await sleep(22);
  }
  [mouseX, mouseY] = [x, y];
};
const click = async (x, y) => {
  await moveTo(x, y);
  await sleep(120);
  for (const type of ['mousePressed', 'mouseReleased']) await send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 });
};
const badge = text => evaluate(`window.beermatVideo.key(${JSON.stringify(text)})`);
const named = {
  enter: [13, 'Enter', '\r', 'Enter'], esc: [27, 'Escape', '', 'Esc'], bs: [8, 'Backspace', '', 'Backspace'], tab: [9, 'Tab', '\t', 'Tab'],
  left: [37, 'ArrowLeft', '', '←'], right: [39, 'ArrowRight', '', '→'], up: [38, 'ArrowUp', '', '↑'], down: [40, 'ArrowDown', '', '↓'],
  space: [32, ' ', ' ', 'Space'],
};
const typeKeys = async keys => {
  for (const part of keys.match(/«[^»]+»|[^«]/g) ?? []) {
    if (part.startsWith('«')) {
      const k = part.slice(1, -1);
      const chord = k.match(/^ctrl-(shift-)?(.)$/);
      if (chord) {
        const c = chord[2];
        const modifiers = 2 | (chord[1] ? 8 : 0);
        const code = /[a-z]/i.test(c) ? `Key${c.toUpperCase()}` : /\d/.test(c) ? `Digit${c}` : c === '.' ? 'Period' : c === '/' ? 'Slash' : c === '=' ? 'Equal' : c === '-' ? 'Minus' : '';
        const vk = /[a-z]/i.test(c) ? c.toUpperCase().charCodeAt(0) : /\d/.test(c) ? c.charCodeAt(0) : { '.': 190, '/': 191, '=': 187, '-': 189 }[c] ?? 0;
        await badge('Ctrl+' + (chord[1] ? 'Shift+' : '') + c.toUpperCase());
        await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', modifiers, key: c, code, windowsVirtualKeyCode: vk });
        await send('Input.dispatchKeyEvent', { type: 'keyUp', modifiers, key: c, code, windowsVirtualKeyCode: vk });
        await sleep(450);
        continue;
      }
      const [vk, key, text, shown] = named[k] ?? (() => { throw new Error(`unknown key «${k}»`); })();
      await badge(shown);
      await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', windowsVirtualKeyCode: vk, key, code: key === ' ' ? 'Space' : key });
      if (text) await send('Input.dispatchKeyEvent', { type: 'char', text, key });
      await send('Input.dispatchKeyEvent', { type: 'keyUp', windowsVirtualKeyCode: vk, key, code: key === ' ' ? 'Space' : key });
      await sleep(k === 'enter' ? 650 : 300);
      continue;
    }
    // Letters and digits are plain typing; operators and the like are worth showing.
    if (!/[\p{L}\p{N}]/u.test(part)) await badge(part === ' ' ? 'Space' : part);
    await send('Input.dispatchKeyEvent', { type: 'keyDown', text: part, key: part, unmodifiedText: part });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', key: part });
    await sleep(125);
  }
};

await send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: 1920, maxHeight: 1080, everyNthFrame: 1 });
if (process.env.BEERMAT_VIDEO_DEBUG) {
  await evaluate(`(() => { window.rafs = 0; const f = () => { window.rafs++; requestAnimationFrame(f); }; requestAnimationFrame(f); })()`);
  setInterval(async () => console.log(`frames from the screencast ${frames.length}, animation frames ${await evaluate('window.rafs')}`), 2000);
}
recording = true;
const begun = Date.now() / 1000;

for (const step of steps) {
  const [verb, ...rest] = step.split(' ');
  const arg = rest.join(' ');
  if (verb === 'caption') await evaluate(`window.beermatVideo.caption(${JSON.stringify(arg)})`);
  else if (verb === 'type') await typeKeys(arg);
  else if (verb === 'pause') await sleep(Number(arg));
  else if (verb === 'click') await click(...arg.split(' ').map(Number));
  else if (verb === 'hover') await moveTo(...arg.split(' ').map(Number));
  else if (verb === 'key') await badge(arg);
  else if (verb === 'zoom') {}
  else throw new Error(`unknown step: ${step}`);
}
await sleep(800);
recording = false;
const ended = Date.now() / 1000;
await send('Page.stopScreencast');
// The page as it stands at the end, beside the video (out/01-type-math.webm.end.png), to compare with its last frame.
writeFileSync(outPath + '.end.png', Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));
console.log(`${frames.length} frames over ${(ended - begun).toFixed(1)} s`);
for (const t of options.includes('--frames') ? sample : []) {
  const f = frames.filter(f => f.t - frames[0].t <= t).at(-1) ?? frames[0];
  writeFileSync(`${outPath}.${t}s.jpg`, Buffer.from(f.data, 'base64'));
}

// The frames played again on a plain canvas at their times, redrawn 30 times a second, and recorded.
const work = join(tmpdir(), `beermat-video-frames-${process.pid}`);
mkdirSync(work, { recursive: true });
const list = frames.map((f, k) => {
  writeFileSync(join(work, `${k}.jpg`), Buffer.from(f.data, 'base64'));
  return { file: `${k}.jpg`, t: Math.max(0, f.t - frames[0].t) };
});
const length = ended - frames[0].t;
writeFileSync(join(work, 'encode.html'), `<body style="margin:0"><script>
const list = ${JSON.stringify(list)};
window.encode = async () => {
  const canvas = document.createElement('canvas');
  canvas.width = 1920; canvas.height = 1080;
  document.body.appendChild(canvas);
  const ctx = canvas.getContext('2d');
  const load = f => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = f; });
  let current = await load(list[0].file);
  ctx.drawImage(current, 0, 0, 1920, 1080);
  const recorder = new MediaRecorder(canvas.captureStream(30), { mimeType: 'video/webm;codecs=vp9', videoBitsPerSecond: 8000000 });
  const chunks = [];
  recorder.ondataavailable = e => chunks.push(e.data);
  const timer = setInterval(() => ctx.drawImage(current, 0, 0, 1920, 1080), 1000 / 30);
  recorder.start();
  const start = performance.now();
  const wait = t => new Promise(r => setTimeout(r, Math.max(0, start + t * 1000 - performance.now())));
  let next = load(list[1]?.file ?? list[0].file);
  for (let k = 1; k < list.length; k++) {
    const img = await next;
    next = load(list[k + 1]?.file ?? list[k].file);
    await wait(list[k].t);
    current = img;
    ctx.drawImage(current, 0, 0, 1920, 1080);
  }
  await wait(${length.toFixed(3)});
  clearInterval(timer);
  return new Promise(resolve => {
    recorder.onstop = () => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result.split(',')[1]);
      reader.readAsDataURL(new Blob(chunks, { type: 'video/webm' }));
    };
    recorder.stop();
  });
};
</script></body>`);
await send('Emulation.clearDeviceMetricsOverride');
await send('Page.navigate', { url: pathToFileURL(join(work, 'encode.html')).href });
await sleep(1000);
const base64 = await evaluate('window.encode()', true);
rmSync(work, { recursive: true, force: true });
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, Buffer.from(base64, 'base64'));
console.log(`wrote ${outPath} (${Math.round(Buffer.from(base64, 'base64').length / 1024)} KB)`);
ws.close();
edge.kill();
process.exit(0);
