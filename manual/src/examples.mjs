// The manual's drawn examples (docs/manual.md#drawn-examples): each ```beermat fence in a page becomes
// the picture Beermat drew of it (written by ManualExamplesTest in the app module, into
// public/examples/<page>/<n>.svg), with its text, "Open this example" in the web version, and the
// .bbm to download. A fence with no picture fails the build: run the test with -Pbeermat.writeReference=true.
import { existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const docs = join(process.cwd(), 'src', 'content', 'docs');
const examples = join(process.cwd(), 'public', 'examples');

const escape = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/** The page's slug, as the test names its folder: `start/beam.md` is `start-beam`. */
const slug = file => relative(docs, file).replace(/\\/g, '/').replace(/\.mdx?$/, '').replace(/\//g, '-');

export function remarkBeermatExamples({ base = '/docs' } = {}) {
  return (tree, file) => {
    const path = file.path ?? file.history?.[0];
    if (!path) return;
    const page = slug(path);
    let n = 0;
    const visit = node => {
      if (!node.children) return;
      node.children = node.children.map(child => {
        if (child.type !== 'code' || child.lang !== 'beermat') {
          visit(child);
          return child;
        }
        n++;
        const svg = join(examples, page, `${n}.svg`);
        if (!existsSync(svg)) throw new Error(`${page} example ${n} isn't drawn: ./gradlew :app:test --tests '*ManualExamples*' -Pbeermat.writeReference=true`);
        const url = `${base}/examples/${page}/${n}`;
        const text = child.value;
        // `both`: the text shown open beside the picture, where the text is the point (the syntax reference).
        const open = (child.meta ?? '').split(/\s+/).includes('both') ? ' open' : '';
        return {
          type: 'html',
          value:
            `<figure class="beermat-example">` +
            // Its size is the SVG's own (ManualExamplesTest writes it 1.5 times the page's).
            `<img src="${url}.svg" alt="${escape(text)}" loading="lazy">` +
            `<figcaption><details${open}><summary>As text</summary><pre>${escape(text)}</pre></details>` +
            `<a href="/try?open=${encodeURIComponent(`${url}.bbm`)}">Open this example</a> · <a href="${url}.bbm" download>Download .bbm</a>` +
            `</figcaption></figure>`,
        };
      });
    };
    visit(tree);
  };
}
