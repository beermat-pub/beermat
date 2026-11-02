// Before every build (docs/manual.md): the icons from ../branding, and What's new from ../CHANGELOG.md, so
// neither is kept twice. Versions not released yet are left out: the manual covers only what a version does.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });
mkdirSync('src/assets', { recursive: true });
copyFileSync('../branding/logo-small.svg', 'public/favicon.svg');
copyFileSync('../branding/logo.svg', 'src/assets/logo.svg');

const changelog = readFileSync('../CHANGELOG.md', 'utf8').replace(/\r/g, '');
const sections = changelog.split(/^(?=## )/m).slice(1).filter(s => !/^## .*not released yet/.test(s));
// Links to the repository's own files go to GitHub; the manual is published on its own.
const body = sections.join('').replace(/\]\((?!https?:|#)([^)]+)\)/g, '](https://github.com/beermat-pub/beermat/blob/main/$1)');
writeFileSync('src/content/docs/whats-new.md', `---
title: What's new
description: What changed in each version of Beermat.
editUrl: false
---

${body}`);
