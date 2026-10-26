// Renders a skill tree layout JSON to PNGs at Minecraft GUI scales 2, 3 and 4 on a
// 1920x1080 window, so a layout can be judged at the size it will ship before any
// Java renderer exists. See docs/setup/art-tooling-setup.md.
//
// Usage: node render.mjs [layout.json] [--out dir] [--scales 2,3,4]
//
// Nodes without x/y are placed by elkjs (layered, top to bottom) and the placed layout
// is written next to the PNGs as layout.placed.json, to be hand-tuned and fed back in.
// Coordinates are GUI pixels, the unit the mod's renderer works in.

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import ELK from 'elkjs/lib/elk.bundled.js';
import { chromium } from 'playwright';

const WINDOW = { width: 1920, height: 1080 };
const FRAME_SIZE = { minor: 26, notable: 32, keystone: 40 };
const FRAME_COLOUR = { minor: '#8b8b8b', notable: '#c9a43a', keystone: '#7a4fd1' };

function parseArgs(argv) {
  const args = { layout: 'sample-layout.json', out: 'out', scales: [2, 3, 4] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--out') args.out = argv[++i];
    else if (argv[i] === '--scales') args.scales = argv[++i].split(',').map(Number);
    else args.layout = argv[i];
  }
  return args;
}

async function place(layout) {
  const nodes = layout.nodes.map((n) => {
    const size = FRAME_SIZE[n.frame] ?? FRAME_SIZE.minor;
    return { ...n, w: n.w ?? size, h: n.h ?? size };
  });
  if (nodes.every((n) => n.x != null && n.y != null)) return { ...layout, nodes };

  const elk = new ELK();
  const graph = await elk.layout({
    id: 'tree',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'DOWN',
      'elk.spacing.nodeNode': '40',
      'elk.layered.spacing.nodeNodeBetweenLayers': '48',
    },
    // Label width is reserved so names don't collide; the box itself stays frame-sized.
    children: nodes.map((n) => ({ id: n.id, width: Math.max(n.w, n.label.length * 6), height: n.h + 12 })),
    edges: layout.edges.map((e, i) => ({ id: `e${i}`, sources: [e.from], targets: [e.to] })),
  });
  const pos = new Map(graph.children.map((c) => [c.id, c]));
  return {
    ...layout,
    nodes: nodes.map((n) => {
      const p = pos.get(n.id);
      return n.x != null && n.y != null ? n : { ...n, x: Math.round(p.x + (p.width - n.w) / 2), y: Math.round(p.y) };
    }),
  };
}

// Segment-vs-rectangle test for the rule the old tree broke: no edge passes through a box.
function segmentHitsRect(a, b, r) {
  const steps = 64;
  for (let i = 1; i < steps; i++) {
    const t = i / steps;
    const x = a.x + (b.x - a.x) * t;
    const y = a.y + (b.y - a.y) * t;
    if (x > r.x && x < r.x + r.w && y > r.y && y < r.y + r.h) return true;
  }
  return false;
}

function check(layout) {
  const byId = new Map(layout.nodes.map((n) => [n.id, n]));
  const centre = (n) => ({ x: n.x + n.w / 2, y: n.y + n.h / 2 });
  const problems = [];
  for (const e of layout.edges) {
    const a = byId.get(e.from);
    const b = byId.get(e.to);
    if (!a || !b) { problems.push(`edge ${e.from} -> ${e.to} names a missing node`); continue; }
    for (const n of layout.nodes) {
      if (n === a || n === b) continue;
      if (segmentHitsRect(centre(a), centre(b), n)) problems.push(`edge ${e.from} -> ${e.to} crosses box ${n.id}`);
    }
  }
  return problems;
}

function svg(layout, guiWidth, guiHeight) {
  const byId = new Map(layout.nodes.map((n) => [n.id, n]));
  const xs = layout.nodes.flatMap((n) => [n.x, n.x + n.w]);
  const ys = layout.nodes.flatMap((n) => [n.y, n.y + n.h + 12]);
  // Centre the tree in the window, as the Tome opens centred.
  const dx = Math.round((guiWidth - (Math.max(...xs) - Math.min(...xs))) / 2 - Math.min(...xs));
  const dy = Math.round((guiHeight - (Math.max(...ys) - Math.min(...ys))) / 2 - Math.min(...ys));
  const edges = layout.edges.map((e) => {
    const a = byId.get(e.from);
    const b = byId.get(e.to);
    if (!a || !b) return '';
    return `<line x1="${a.x + a.w / 2}" y1="${a.y + a.h / 2}" x2="${b.x + b.w / 2}" y2="${b.y + b.h / 2}" stroke="#5a4a32" stroke-width="2"/>`;
  });
  const nodes = layout.nodes.map((n) => {
    const colour = FRAME_COLOUR[n.frame] ?? FRAME_COLOUR.minor;
    return `<rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" fill="#2b2b2b" stroke="${colour}" stroke-width="2"/>`
      + `<text x="${n.x + n.w / 2}" y="${n.y + n.h + 10}" text-anchor="middle">${n.label}</text>`;
  });
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${guiWidth}" height="${guiHeight}" shape-rendering="crispEdges">`
    + `<g transform="translate(${dx},${dy})">${edges.join('')}${nodes.join('')}</g></svg>`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const layout = await place(JSON.parse(await readFile(args.layout, 'utf8')));
  await mkdir(args.out, { recursive: true });
  await writeFile(path.join(args.out, 'layout.placed.json'), JSON.stringify(layout, null, 2));

  const problems = check(layout);
  for (const p of problems) console.warn(`WARN ${p}`);

  const browser = await chromium.launch();
  try {
    for (const scale of args.scales) {
      // Minecraft's GUI scale divides the window into GUI pixels; render at that
      // logical size with a matching device scale so the PNG is the real 1920x1080.
      const guiWidth = Math.floor(WINDOW.width / scale);
      const guiHeight = Math.floor(WINDOW.height / scale);
      const page = await browser.newPage({ viewport: { width: guiWidth, height: guiHeight }, deviceScaleFactor: scale });
      await page.setContent(`<!doctype html><html><body style="margin:0;background:#1a1410">
        <style>text{font:8px monospace;fill:#e8dcc0;paint-order:stroke;stroke:#1a1410;stroke-width:3px}</style>${svg(layout, guiWidth, guiHeight)}</body></html>`);
      const file = path.join(args.out, `tree-gui${scale}.png`);
      await page.screenshot({ path: file });
      await page.close();
      console.log(`wrote ${file} (${guiWidth}x${guiHeight} GUI px)`);
    }
  } finally {
    await browser.close();
  }
  process.exitCode = problems.length ? 2 : 0;
}

await main();
