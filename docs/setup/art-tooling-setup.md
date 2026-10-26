# Art tooling setup

How the two approved art tools are installed on Nick's Windows PC, and how to set them up on another
machine. The reasons for picking them are in `docs/research/art-tooling.md`. Retro Diffusion, Aseprite,
Blockbench and Excalidraw are not set up; none of them is approved yet.

Set up and checked on 2026-10-26 with Node 24.20.0 and npm 10.8.3.

## mc-asset (texture CLI and MCP server)

Free, MIT, from https://github.com/smile-minecraft/mc-asset. It runs through `npx`, so there is nothing
to install beyond Node 22 or newer. The version is pinned to 0.5.0 so a new release can't change
behaviour under an agent mid-ticket; bump it on purpose.

Register it once per machine, at user scope so every Claude Code session on the machine sees it,
including worktrees. Windows needs the `cmd /c` wrapper because `npx` is a `.cmd` shim:

```
claude mcp add -s user mc-asset -- cmd /c npx -y mc-asset@0.5.0 mcp
```

On macOS or Linux drop `cmd /c`. Then restart Claude Code and check with `claude mcp list`; mc-asset
should show as connected. It exposes 27 tools (`generate_asset`, `validate_asset`, `tile_asset`,
`preview_asset`, `compare_assets` and so on).

The same commands work from a shell, which is the quickest way to check it:

```
npx -y mc-asset@0.5.0 generate veins --size 16 --palette stone --seed 1 --profile minecraft:block --output out/veins.png --mkdir
npx -y mc-asset@0.5.0 validate out/veins.png
```

`validate` exits 3 when an asset fails, so it can gate a texture before it is committed. Output is
deterministic: the same input and seed give the same bytes.

## Skill tree preview (`tools/tree-preview`)

Renders a skill tree layout JSON to PNGs at Minecraft GUI scales 2, 3 and 4 on a 1920×1080 window, using
Playwright's headless Chromium. Nodes that have no `x`/`y` are placed by elkjs, and the placed layout is
written out so it can be hand-tuned and fed back in. It also warns when an edge passes through a node box
and exits 2 if any do.

One-time setup, from `tools/tree-preview`:

```
npm run setup
```

That installs elkjs and Playwright from `package-lock.json` and downloads Chromium into
`%LOCALAPPDATA%\ms-playwright` (about 720 MB on disk with the headless shell and ffmpeg, shared by every
checkout). Cloud sessions already have
Chromium and should not run `playwright install`; there, `npm install` is enough.

Render:

```
npm run preview -- sample-layout.json --out out
```

This writes `out/tree-gui2.png`, `tree-gui3.png`, `tree-gui4.png` and `out/layout.placed.json`. Pass
`--scales 2,3` to render fewer. `node_modules/` and `out/` are gitignored.

### Layout format

```json
{
  "nodes": [
    { "id": "root", "label": "Attunement", "frame": "keystone", "x": 0, "y": 0 }
  ],
  "edges": [ { "from": "root", "to": "veins" } ]
}
```

Coordinates are GUI pixels. `frame` is `minor` (26 px), `notable` (32 px) or `keystone` (40 px), and
`w`/`h` override the size. `sample-layout.json` is a placeholder for testing the script, not the real
Resonance tree. The format is a starting point for the layout-as-data work in the research doc; when the
mod gains a layout file, this script should read that file's format rather than keep its own.
