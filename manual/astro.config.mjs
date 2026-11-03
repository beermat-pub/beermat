// The manual (docs/manual.md): Starlight, published at beermat.pub/docs by the release workflow.
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://beermat.pub',
  base: '/docs',
  integrations: [
    starlight({
      title: 'Beermat manual',
      description: 'How to use Beermat, the open-source engineering worksheet.',
      logo: { src: './src/assets/logo.svg', alt: 'Beermat' },
      customCss: ['./src/beermat.css'],
      components: { SiteTitle: './src/components/SiteTitle.astro' },
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/beermat-pub/beermat' }],
      editLink: { baseUrl: 'https://github.com/beermat-pub/beermat/edit/main/manual/' },
      sidebar: [
        { label: 'Getting started', items: ['start/install', 'start/first-worksheet'] },
        {
          label: 'Reference',
          items: [
            {
              label: 'Functions',
              items: [
                { label: 'A to Z', link: '/reference/functions/' },
                'reference/functions/numbers',
                'reference/functions/trigonometry',
                'reference/functions/arrays',
                'reference/functions/calculus',
                'reference/functions/statistics',
                'reference/functions/strings',
              ],
            },
            'reference/units',
            'reference/settings',
            'reference/keys',
            'reference/command-line',
          ],
        },
        {
          label: 'About',
          items: [
            'whats-new',
            { label: 'Roadmap', link: 'https://github.com/beermat-pub/beermat/blob/main/ROADMAP.md' },
            { label: 'Licence and terms', link: 'https://beermat.pub/terms' },
            { label: 'Support Beermat', link: 'https://beermat.pub/beer' },
            { label: 'Try it in your browser', link: 'https://beermat.pub/try' },
          ],
        },
      ],
    }),
  ],
});
