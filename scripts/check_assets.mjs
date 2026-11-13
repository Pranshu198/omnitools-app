#!/usr/bin/env node
/**
 * Asset-completeness checker for the standalone (bundled-PWA) build.
 *
 * Proves the APK's assets/www is a faithful, complete copy of the PWA
 * build output, so the app never needs the live website:
 *   1. Every file in the PWA dist/ exists under assets/www (same count).
 *   2. Every src/href referenced by dist/index.html resolves to a bundled file.
 *   3. Every concrete data/med/names/assets path string found in the built
 *      JS chunks and in sw.js resolves to a bundled file.
 *
 * Usage: node scripts/check_assets.mjs [distDir] [assetsWwwDir]
 * Exit 0 = complete. (Byte-identity is checked separately with rsync -rcn.)
 */
import { readdirSync, existsSync, statSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const dist = process.argv[2] || join(process.cwd(), '..', 'omnitools', 'dist');
const www = process.argv[3] || join(process.cwd(), 'app', 'src', 'main', 'assets');

function walk(dir, base = dir, out = []) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name);
    if (e.isDirectory()) walk(p, base, out);
    else out.push(relative(base, p).split(sep).join('/'));
  }
  return out;
}

let fail = 0;
const distFiles = walk(dist);
const wwwSet = new Set(walk(www));

// 1. file-set equality
const missing = distFiles.filter((f) => !wwwSet.has(f));
const extra = [...wwwSet].filter((f) => !new Set(distFiles).has(f));
console.log(`dist files: ${distFiles.length}, bundled files: ${wwwSet.size}`);
console.log(`missing from bundle: ${missing.length}, extra in bundle: ${extra.length}`);
missing.slice(0, 10).forEach((f) => console.log('  MISSING', f));
if (missing.length || extra.length) fail = 1;

// 2. index.html references
const html = readFileSync(join(dist, 'index.html'), 'utf8');
const refs = new Set();
for (const m of html.matchAll(/(?:src|href)="([^"]+)"/g)) {
  const u = m[1];
  if (/^https?:\/\//.test(u) || u.startsWith('data:')) continue;   // fonts etc. = online extras
  refs.add(u.replace(/^\.\//, '').replace(/^\//, '').split(/[?#]/)[0]);
}
let badRefs = 0;
for (const r of refs) {
  if (!r) continue;
  if (!existsSync(join(www, r))) { console.log('  BAD index.html ref:', r); badRefs++; }
}
console.log(`index.html local refs: ${refs.size}, unresolved: ${badRefs}`);
if (badRefs) fail = 1;

// 3. concrete path strings inside built JS + sw.js
const jsFiles = distFiles.filter((f) => f.endsWith('.js'));
const pathRe = /["'`]((?:\/?(?:data|med|names|assets)\/[A-Za-z0-9_.\-/]+))["'`]/g;
const found = new Set();
for (const f of jsFiles) {
  const src = readFileSync(join(dist, f), 'utf8');
  for (const m of src.matchAll(pathRe)) {
    const p = m[1].replace(/^\//, '');
    if (p.includes('${')) continue;                 // runtime-built names: covered by check 1
    if (/\.(json|js|css|png|svg|webmanifest)$/.test(p)) found.add(p);
  }
}
let badPaths = 0;
for (const p of found) {
  if (!existsSync(join(www, p))) { console.log('  BAD js path:', p); badPaths++; }
}
console.log(`concrete data/med/names/assets paths in JS+sw: ${found.size}, unresolved: ${badPaths}`);
if (badPaths) fail = 1;

// sizes
const bytes = distFiles.reduce((a, f) => a + statSync(join(dist, f)).size, 0);
console.log(`dist total bytes: ${bytes}`);
console.log(fail ? 'CHECK FAILED' : 'CHECK PASSED — bundle is complete');
process.exit(fail);
