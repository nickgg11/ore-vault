# Art and visual tooling for the Designer

Researched 2026-10-25. Nick asked for MCP tools that help with pixel art (textures), 3D or models, and
drawing the skill tree so it looks good. This is what exists, what each needs, and what the Designer agent
should use. Claims marked *unverified* come from the project's own README and were not run here.

## The short version

| Need | Use | Runs where | Cost |
|---|---|---|---|
| Minecraft textures, checked against the 64×64 rule | **mc-asset** (MCP + CLI) | Anywhere with Node 22 | Free, MIT |
| Hand-finishing pixel art: palettes, dithering, outlines | **Aseprite + pixel-plugin** | Nick's computer | Aseprite licence (about $20, or build from source) |
| First drafts and reference images | **Retro Diffusion MCP** | Remote, any session | Pay per image, roughly $0.01 to $0.18, free starter credits |
| Block and item models | **Blockbench MCP plugin** | Nick's computer, inside Blockbench | Free |
| Skill tree layout and look | **No MCP.** A layout JSON plus a preview script in the repo | Anywhere | Free |
| Sketching tree ideas with Nick | **Excalidraw MCP** (optional) | Remote, claude.ai | Free |

Blender is not on the list for 1.0. See the Blender section for why.

## Why tools alone will not fix the textures

The old textures were 16×16 when the rule said 64×64, and nothing checked. A generator does not fix that;
a gate does. Whatever tool makes a texture, the Designer agent's process is:

1. Collect references first: the vanilla blocks it will sit next to, and at least two mod textures Nick
   likes. Write down why each one works.
2. Fix the palette before drawing: 4 to 6 shades per material, hue-shifted (shadows cooler, highlights
   warmer), light from the top left like vanilla.
3. Draw or generate at the target size. Never upscale a 16×16.
4. Render a contact sheet: the texture at 1×, at 4×, tiled 3×3 for blocks, and in context (an inventory
   slot, a placed block beside vanilla stone and iron ore).
5. Check it: size, palette count, no stray semi-transparent pixels, seams tile.
6. Show Nick the contact sheet. Nothing ships without his yes.

The tools below make steps 3 to 5 faster. They do not replace 1, 2 or 6.

## Pixel art

### mc-asset (recommended)

A Minecraft-specific texture toolchain with a CLI and an MCP server. It writes sprites from character grids,
pixelizes and colour-quantizes reference art, makes seamless tiling textures, packs animation strips,
nine-slice scales GUI sprites, diffs two PNGs, previews block models, and validates a whole resource pack.
Same input and seed give byte-identical output, which matters when an agent regenerates a file. MIT,
27 MCP tools (*unverified*).

- Setup: `claude mcp add mc-asset -- npx -y mc-asset mcp`. Needs Node.js 22.
- Source: https://github.com/smile-minecraft/mc-asset
- Note: a trial run in this cloud session was blocked by the sandbox's policy on running downloaded code,
  so none of this has been exercised yet. First use should be on a throwaway texture.

### Aseprite with pixel-plugin

Aseprite is the standard pixel-art editor. `willibrandon/pixel-plugin` is a Claude Code plugin that drives it
through a bundled MCP server (`pixel-mcp`, 40+ tools *unverified*): palette presets, Floyd-Steinberg and
Bayer dithering, pixel-perfect scaling, PNG and spritesheet export.

- Needs Aseprite 1.3+ installed on the same machine (paid on Steam or itch, or free to compile from source).
- Setup: `claude plugin marketplace add willibrandon/pixel-plugin`, then
  `claude plugin install pixel-plugin`, then `/pixel-setup` to find the Aseprite binary.
- Only works in a Claude Code session on Nick's computer, not in a cloud session.
- Source: https://github.com/willibrandon/pixel-plugin

### Retro Diffusion MCP

A hosted pixel-art generator with 90+ styles, including Minecraft items and textures, plus edit tools that
are useful on their own: palette conversion, colour reduction, seam tiling, and a free `fix_pixel_art` that
snaps blurry or upscaled art back onto a grid. Sizes 16 to 384 px depending on style.

- Needs an API key (`rdpk-…`) from a free account; generation is prepaid per image. `estimate_inference_cost`
  is free, so the Designer prices a batch before running it.
- Setup: `claude mcp add --transport http retro-diffusion https://mcp.retrodiffusion.ai/mcp --header "Authorization: Bearer rdpk-…"`
- Remote, so it works from cloud sessions too.
- Treat output as a draft. Generated pixel art usually needs palette and outline cleanup before it matches
  vanilla, and the gate above still applies.

### PixelLab MCP (not recommended first)

Also a hosted generator (`pixellab-mcp` on npm, API key, credits). It leans toward characters, rotation and
animation rather than block textures. Worth trying only if Retro Diffusion's output disappoints.

## Models: Blockbench, not Blender

Minecraft block and item models are Blockbench JSON. **Blockbench MCP plugin**
(`jasonjgardner/blockbench-mcp-plugin`) adds an MCP server inside the Blockbench desktop app.

- Load it in Blockbench via File → Plugins → Load Plugin from URL, then connect Claude Code to
  `http://localhost:3000/bb-mcp` (port and path set in Blockbench's settings).
- Only works on Nick's computer while Blockbench is open.

Ore Vault 1.0 has few custom models: the Vault Frame and Anchor are full cubes, the portal is a flat plane,
and items are flat sprites. Blockbench covers all of it.

**Blender** has two MCP servers. The official one from Blender Lab (Blender 5.1+, GPL, 26 tools, backs
Anthropic's Blender connector) is for scene work and scripting and runs model-written code unguarded, so
its own docs suggest a VM. The community "MCP for Blender" (`ahujasid`, Blender 3.0+, MIT, 36 tools) adds
Poly Haven, Sketchfab and generation hooks. Either is useful for promo renders or a mod icon later. Neither
makes Minecraft models more easily than Blockbench, so neither is in the 1.0 setup.

## The skill tree

No MCP draws a good skill tree for us, and the old tree's problem was not drawing. It was building the
renderer before agreeing what the tree should look like, three times. The fix is process plus one small tool:

1. **Layout is data.** Node positions, sizes and frame classes live in a JSON file that the mod reads, the
   way Pufferfish's Skills does it (see `puffish-skills.md`). The Java renderer draws what the file says and
   computes nothing about placement.
2. **A preview script renders that same file to a PNG** outside the game, using HTML or SVG and the
   Playwright Chromium that cloud sessions already have. It draws at the real GUI sizes (scale 2, 3 and 4 on
   a 1920×1080 window) with real node names and the real frame art, so what Nick approves is what ships.
3. **A starting layout can be generated, then hand-tuned.** `elkjs` (the Eclipse Layout Kernel in
   JavaScript) does layered and radial layouts and keeps edges off boxes. The Designer generates, adjusts by
   hand against the Diablo 4 reference in §8, and checks the rules the old tree broke: no edge crosses a
   box, vertical position never implies a prerequisite that does not exist, nothing smaller than legible at
   the minimum zoom.
4. **Excalidraw MCP** is optional, for sketching an idea with Nick before it is worth putting in the JSON.
   Hosted at `https://excalidraw-mcp-app.vercel.app/mcp`, added as a connector on claude.ai. Its source is
   listed under the `excalidraw` GitHub organization but builds from a personal account, so check it
   before connecting.

## What Nick needs to do

Nothing is needed to start. When the first texture or tree ticket comes up, the Manager will ask for one of:

- **mc-asset**: nothing to sign up for. Approve it running, or add it on your computer with the command above.
- **Retro Diffusion**: make an account, create an API key, and add it as a connector or with the command
  above. Spend is per image and you can cap it by only buying a small credit pack.
- **Aseprite and Blockbench**: only if you want to do art sessions on your own computer. They do not work
  from the cloud.

Connectors are added at https://claude.ai/customize/connectors and take effect in the next session.
