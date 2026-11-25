# The tree layout file format

Status: proposed (E8a-S9, #189). Owner: UI-UX, with the Architect. Reviewers: Architect, Developer,
Reviewer, QA. Spec: §6 Notation (Layout data, Saved state, Ring, Fork, Exclusive pair, Mod-gated nodes),
§6.1 Shape of the tree, §8 Tab 1, §11 Tree Layout Data. Records: `docs/adr/0005-persisted-data-and-versioning.md`
("Registry ids are save data", section 7 load rules), `docs/adr/0006-network-channel-and-tome-sync.md` (bump
rules, size bounds, the layout hash), `docs/adr/0001-toolchain-and-package-layout.md` (the `core.layout`
row).

This is the format of the one JSON file that holds the Resonance tree's shape (§6 Layout data). It lists
every node, every edge and every fork's petals, with the drawing position of each, plus a ring block and a
view block that only the renderer and the layout tests read. `core.layout.TreeLayout` parses it (ADR 0001);
the renderer draws what it says and places nothing itself (§8 Tab 1). The content, which nodes exist and
where they sit, is authored in #239 (E8a-S3a, the graph) and #259 (E8a-S3b, the positions). This document
fixes only the shape.

The idea of separating what a skill is from where its copies sit, with positions as plain authored numbers,
is borrowed from Pufferfish's Skills (`docs/research/puffish-skills.md`: definitions placed as skills at an
explicit `x`, `y`). Nothing else of its format is used.

## Where the file lives

One file in the mod jar, not in a datapack or a resource pack, so a server and its clients read the same
bytes (§6 Layout data). Proposed path: `src/main/resources/orevault/tree_layout/resonance.json`, read through
the class loader, outside both `assets/` and `data/` so that neither a resource pack nor a datapack can
shadow it. The Architect confirms the path when #212 (E8a-S4) lands. The post-1.0 Animus tree would be a
second file in the same format.

## Units and coordinates

- Every position is in GUI px (GUI units) at zoom 1, measured from the centre of the start node. Multiply by
  zoom and by GUI scale to get screen pixels.
- `x` grows to the right and `y` grows downward, as in every Minecraft screen and in the preview SVG.
- A position is the centre of the icon, written as `[x, y]` with integer values. Every icon size in §8 Tab 1
  is even (12, 14, 18, 26, 36), so an integer centre puts the icon's corner on a whole GUI unit and the
  icon lands on whole screen pixels at every crisp zoom stop (below).
- The start node is at `[0, 0]`. Nothing else may be.
- Ring radii (below) are in the same units, measured from the start.

Position means distance from the start and nothing else (§6 Ring; `docs/TEAM_REQUIREMENTS.md`, UI-UX
"Layout data"). A node is drawn in its ring's band, and no node is moved nearer the start to tidy the
picture.

## Top level

```json
{
  "format": 1,
  "nodes": [ ... ],
  "edges": [ ... ],
  "forks": [ ... ],
  "rings": [ ... ],
  "view": { ... }
}
```

| Key | Type | Required | Meaning |
|---|---|---|---|
| `format` | int | yes | The version of this format. The loader refuses any value it does not know. Bumped only when a field's meaning changes, never for content |
| `nodes` | array of node | yes | Every node, the start first. File order is layout order: node index `i` on the wire is the `i`th entry (ADR 0006, "The layout hash") |
| `edges` | array of edge | yes | Every edge between two nodes |
| `forks` | array of fork | yes, may be empty | Each fork's parent skill, `petalAnchor` and petals |
| `rings` | array of ring | yes | One faint level ring per level, 1 to 30, with its radius (§8 Tab 1) |
| `view` | object | yes | Icon sizes, gaps, edge width, zoom stops and text thresholds |

Every object rejects keys it does not define, so a misspelt `petalAncor` fails at load instead of
silently going missing. A key that starts with `_` is a comment: allowed on any object, ignored by the
parser and not hashed.

## Nodes

```json
{ "id": "vein_expansion_2", "skill": "vein_expansion", "class": "small", "ring": 2, "pos": [0, -96] }
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | string | yes | The node's id, unique among nodes. Snake case, `[a-z0-9_]`, at most 64 characters (`MAX_ID`, ADR 0006). Option ids follow the same form and may not equal any node or skill id |
| `skill` | string | yes, except on the start | The skill this node is a copy of (§6 Skill and tier). Its lang keys are `node.orevault.<skill>` (§8 Tab 1, Lang keys) and its effects constants are keyed by it (§11 Tree Layout Data). A skill's tier is how many of its nodes the team owns |
| `class` | string | yes | One of `start`, `small`, `travel`, `notable`, `keystone`, `pact`, `growth`, `fork_parent` |
| `marks` | array of string | no, default `[]` | Any of `tradeoff`, `exclusive`, `requires`, each at most once |
| `ring` | int | yes, except on the start | The level floor, 0 to 30 (`LEVEL_CAP`, §4.3). Authored, never derived from distance (§6 Ring) |
| `requires` | condition | when marked `requires` | What must be present for the node to be shown (below) |
| `exclusiveGroup` | string | when marked `exclusive` | The pair this node belongs to. Exactly two nodes share a group |
| `pos` | `[int, int]` | yes | The icon's centre (above) |

The classes are §6's seven node classes plus `start`, which is a token of this format and not a node class:
the start has no skill, no ring, no marks and no effect, is owned from the beginning and is never written to
the save (`owned` holds every other node; §11 SavedData Pattern, "the start is implicit"). Its id is
`start`. Fork options are not nodes and never appear in `nodes` (§6 Fork).

The marks follow §6's mark table. `marks` must contain `requires` exactly when `requires` is present and
`exclusive` exactly when `exclusiveGroup` is present; the loader refuses a node where they disagree. The
`tradeoff` mark makes the node a toggle; whether that toggle is per player or a team vote (Volatile Veins,
Molten Seam) is code in `core.tree`, not layout.

Ids. A single-node skill's node id equals its skill id (`greedy_seams`). A skill with copies names them
`<skill>_<n>`, numbered from 1 in ring order when they are first authored (`vein_expansion_1` to `_5`). The
number is only a name: it is not the tier, the code never parses it, and a copy keeps its id if it is later
re-ringed.

Every node of one skill has the same class and the same `tradeoff` mark, so its copies draw with one icon
and one frame (§6 Skill and tier). `requires` and `exclusiveGroup` are per node: Ore Working's nodes 4 to 6
carry a condition that nodes 1 to 3 do not (§6.1 Ore Working).

### Conditions

A mod-gated node is hidden when its condition fails (§6 Mod-gated nodes). The server evaluates conditions
into the hidden set at start and on `/reload` (#248, E8a-S7) and sends it as a bitset (ADR 0006); the client
never evaluates one. A condition is an object with exactly one key:

| Form | True when | Used by |
|---|---|---|
| `{ "mod": "ftbultimine" }` | the mod id is loaded | FTB Ultimine, Paraglider, Hephaestus Tools, GeOre nodes |
| `{ "tag": "c:tools/paxel" }` | the item tag has an entry | Hammer and Paxel Affinity |
| `{ "tagPrefix": "c:clumps/" }` | some item tag under that path has an entry | Ore Working 4 to 6 (§6.1 Ore Working, §9) |
| `{ "energyItem": true }` | some registered item has energy storage, checked once at start | Resonant Dynamo, Galvanic Seam |
| `{ "lootTable": "<id>" }` | the loot table exists with a pool, after datapacks load | Gem Seam |
| `{ "forkOption": { "fork": "ore_working", "option": "ore_doubling" } }` | that option is the team's choice | Ore Working 4 to 6 |
| `{ "allOf": [ ... ] }` | every listed condition is true | Ore Working 4 to 6 |
| `{ "anyOf": [ ... ] }` | at least one listed condition is true | Hammer and Paxel Affinity's four tags |

Ore Working 4 to 6, for example, is
`{ "allOf": [ { "forkOption": { "fork": "ore_working", "option": "ore_doubling" } }, { "tagPrefix": "c:clumps/" }, { "tagPrefix": "c:shards/" }, { "tagPrefix": "c:crystals/" } ] }`.
A `forkOption` condition is the one condition evaluated per team rather than per server; the difference
between a node hidden by a fork choice and one hidden by a missing mod (dormant or still spent, §6
Mod-gated nodes) is `core.tree`'s, not the format's. The exact ids, tags and loot table each node names
come from §6.1 and §9 and are written into the file at #239; #248 may add a form here if a §6.1 condition
fits none of these, in the same PR as this table.

## Edges

```json
["vein_expansion_1", "mining_speed_1"]
```

An edge is a two-element array of node ids. Edges are undirected: `[a, b]` and `[b, a]` are the same edge,
and the file may list it either way, but only once. No edge joins a node to itself. Neither end may be a
petal, since a petal has no edges (§6 Fork). Edges are drawn as straight lines from icon border to icon
border (§8 Tab 1); the renderer clips each line at the two icon boxes and adds no bends.

## Forks

```json
{
  "parent": "ore_attunement",
  "petalAnchor": "ore_attunement_1",
  "options": [
    { "id": "common_focus", "pos": [-56, -122] },
    { "id": "uncommon_focus", "pos": [-54, -146] },
    { "id": "rare_focus", "pos": [-36, -160] }
  ]
}
```

| Field | Type | Meaning |
|---|---|---|
| `parent` | string | The fork parent's skill id. It is also the fork's id: the key of `fork_choice` in the save (ADR 0005, `fork_choice` is keyed by fork parent skill id) and the `forkId` of `ChooseForkOption` (ADR 0006) |
| `petalAnchor` | string | The node the petals are drawn beside: the parent's lowest-ring node (§6 Fork). Stored rather than derived so a tie between two copies at the same ring is settled by the author, and checked at load |
| `options` | array, at least 2 | Each petal's option id and the centre of its icon, in the same units as nodes |

Petals are drawn where `pos` says, not computed from the anchor. They have no ring: an option can be picked
once the team owns any node of its parent (§6 notation, `[FORK OPTION]`). Every node of a fork's parent skill
has class `fork_parent`, and every `fork_parent` skill has exactly one fork. At most 16 forks (`MAX_FORKS`,
ADR 0006); §6.1 has four.

## Rings

```json
{ "level": 12, "radius": 480, "labelAngle": -90 }
```

One entry for each level from 1 to 30, in order, radii strictly increasing. `radius` is the faint circle
centred on the start where that level opens; `labelAngle` is where its label sits, in degrees clockwise
from the positive `x` axis (so -90 is straight up), authored like any other position because the renderer
places nothing. Level 0 has no ring: the start area inside level 1's circle is ring 0's band.

A node of ring `r` has its centre in ring `r`'s band: at least `radius(r)` from the start (0 for ring 0)
and less than `radius(r + 1)` (no upper bound for ring 30). This is the "drawn in its ring's band" half of
§6 Ring, and it is part of the first layout test below.

The radii are UI-UX's, set against the preview at #259. The 2026-10-26 preview drew the whole tree about
2,400 tree pixels across (§6.1 Shape of the tree), roughly 40 px a level; that is a starting point, not a
value.

## View

```json
"view": {
  "iconSize": { "start": 26, "travel": 14, "small": 18, "growth": 18, "fork_parent": 18,
                "notable": 26, "pact": 26, "keystone": 36, "petal": 12 },
  "minIconGap": 6,
  "edgeClearance": 2,
  "edgeWidth": 2,
  "edgeMinScreenPx": 2,
  "zoom": { "min": 0.2, "max": 1.5, "overviewStops": [0.2, 0.35], "crispFromEffective": 1 },
  "text": { "ringLabelsFromEffective": 2, "badgeNumbersFromEffective": 2 }
}
```

The view block is read by the renderer, by `tools/tree-preview` and by the layout tests, and by nothing that
decides a rule. It is not in the layout hash (below).

| Field | Meaning | Value and who sets it |
|---|---|---|
| `iconSize` | Side of each class's square icon box in GUI px, plus `petal`. Used for clipping edges and for the gap and edge tests | §8 Tab 1 class table, provisional until Nick's yes on the E8-S5 (#283) preview (Designer). `start` has no row in §8; 26 is a placeholder for the Designer to set |
| `minIconGap` | The least space between any two icon boxes, nodes and petals alike, in GUI px at zoom 1. For two square boxes with half-sides `a` and `b` and centres `dx`, `dy` apart, the gap is `max(abs(dx), abs(dy)) - a - b` | Provisional 6, set by UI-UX with the Designer at #259. Badges (9x9, `docs/TEAM_REQUIREMENTS.md` Designer table) that overhang an icon must fit inside the gap, or the Designer says how far they overhang |
| `edgeClearance` | How far an edge must stay from the box of any icon that is not one of its two ends, in GUI px at zoom 1 | Provisional 2, UI-UX |
| `edgeWidth` | Edge line width in GUI px at zoom 1 | Provisional 2 (the preview script's stroke), Designer, with the edge style |
| `edgeMinScreenPx` | The thinnest an edge is drawn on screen, so lines thicken rather than vanish when zoomed out (`DESIGN_LOG.md` 2026-09-07, "Descriptions, prerequisite chains, and zoom") | Provisional 2, Designer; Nick judges on the preview |
| `zoom.min`, `zoom.max` | The zoom range | 0.2 and 1.5, §8 Tab 1 ("between a fifth and one and a half times full size") |
| `zoom.overviewStops` | Zoom values the wheel stops at below the crisp range, used as written | Provisional, UI-UX; Nick judges on the preview (`docs/TEAM_REQUIREMENTS.md` UI-UX "Zoom and text": "0.2 and one or two overview stops") |
| `zoom.crispFromEffective` | The lowest effective scale (zoom times GUI scale) of the crisp stops | 1, from the same section |
| `text.ringLabelsFromEffective` | The lowest effective scale at which ring labels ("Level 12 opens here", §8 Tab 1) are drawn | Provisional 2, UI-UX; Nick judges on the preview |
| `text.badgeNumbersFromEffective` | The lowest effective scale at which the opening level beside a lock badge is drawn | Provisional 2, UI-UX; Nick judges on the preview |

Zoom stops. The wheel moves between stops (`docs/TEAM_REQUIREMENTS.md` UI-UX "Zoom and text"). At GUI scale
`g` the crisp stops are `k / g` for every whole `k` from `crispFromEffective` up to `floor(max × g)`, so the
icons and the font land on whole screen pixels. The overview stops are added below the smallest crisp
stop; an overview stop at or above it is dropped. At GUI scale 2 that gives 0.2, 0.35, 0.5, 1.0 and 1.5; at
3 it gives 0.2, 0.333, 0.667, 1.0 and 1.333 (no whole effective scale lands on 1.5); at 4 it gives 0.2,
0.25, 0.5, 0.75, 1.0, 1.25 and 1.5. The readout shows the zoom (§8 Tab 1).

Text thresholds. No node text is drawn on the page (§8 Tab 1, "Nodes are icons"), so the old per-node text
thresholds stay retired. Two kinds of text are still on the page, the ring labels and the lock badge's
opening level, and each is drawn only at or above its threshold, because below it the glyphs are smaller
than the shapes they sit beside and read as noise (`DESIGN_LOG.md` 2026-09-07: "Three-pixel text is not
small text, it is noise"). Thresholds are in effective scale, not zoom, because legibility depends on screen
pixels per font texel. Tooltips, the zoom readout and the top bar are not page text and are unaffected.

## What is checked, and where

The loader, in `TreeLayout`'s parse, refuses a file that is malformed, so the mod fails at start rather than
drawing a broken tree: unknown `format`; an unknown key; a missing required field; a duplicate id; an id not
in snake case or over 64 characters; an edge or `petalAnchor` naming no node; an edge to itself or listed
twice; more than 1024 nodes (`MAX_NODES`) or 16 forks; the start missing, not first or not at `[0, 0]`;
marks that disagree with `requires` or `exclusiveGroup`; an `exclusiveGroup` without exactly two members;
copies of a skill with different classes or `tradeoff` marks; a `fork_parent` skill without exactly one
fork, or a fork whose parent's nodes are not `fork_parent`; a `petalAnchor` that is not the parent's
lowest-ring node; ring radii out of order or not one per level.

JUnit tests over the shipped file pin the layout's rules. These are the six §6 Layout data tests, with
what each reads from this format:

1. Every node's distance (breadth-first from `start` over `edges`) is at least `max(1, ring)`, and its
   `pos` is in its ring's band (`rings`).
2. The copies of a skill are in ring order: no copy is nearer the start, by distance, than a copy with a
   lower ring.
3. Every node with `requires` is on a dead end: removing it and every node with `requires` changes no
   other node's distance or reachability.
4. The two nodes of each `exclusiveGroup` are leaves (one edge each) off the same junction node and have the
   same ring.
5. Petals have no edges (by construction here, since edges name nodes only; the test checks that no option
   id appears in `edges`).
6. No edge passes through another node: the segment between two centres keeps `edgeClearance` from every
   other icon box, petals included.

UI-UX's gates add two more over the same data (`docs/TEAM_REQUIREMENTS.md` UI-UX "My gates"): no two icon
boxes closer than `minIconGap`, and edges meeting icon borders, which holds by construction since the
renderer clips at the box. A further test, owed to #212 with the reserved list in `core.tree`, checks that
no node, skill, fork or option id is one of the §6.1 reserved ids.

## Frozen ids and free fields

Saved tree state is node ids only (§6 Saved state), so where a node is drawn costs a save nothing. What a
save or a payload names is frozen.

| Field | Status | Why, and what a change costs |
|---|---|---|
| Node `id` | Frozen from T5 | Save data: `owned`, `bought_by`, `pinned_node`, `generation_tradeoffs`, `active_tradeoffs` hold node ids (ADR 0005, "Registry ids are save data"). Renaming or removing one is a `PROTOCOL_VERSION` bump in the same PR (ADR 0006, third bump rule), and the old id goes on the §6.1 reserved list so it is never reused. Without a migration a team that owned it loses the node and gets its point back (§6 Saved state) |
| A node's `skill` | Frozen with the node | An owned node id must keep meaning a copy of the same skill, or a save silently changes build. To move a copy to another skill, remove the node and add one with a new id |
| `skill` ids | Frozen from T5 | Save data (ADR 0005); they key the lang file (§8 Tab 1, Lang keys), the effects constants (§11) and tier derivation. A rename is treated as a node id change, with a bump |
| `parent` (fork id) and option `id`s | Frozen from T5 | Save data: `fork_choice` maps fork parent skill id to option id (ADR 0005); both travel as strings in `ChooseForkOption` and `Forks` (ADR 0006). A rename is treated as a node id change, with a bump |
| `start` | Frozen | Edges name it, and saves imply it |
| `pos`, option `pos` | Free | In the hash, not the protocol. Moving a node needs no bump and no migration (ADR 0006, "Moving, re-ringing or re-edging a node needs no bump"); an older client is disconnected with the layout mismatch message until it updates |
| `ring`, `class`, `marks`, `requires`, `exclusiveGroup` | Free for the format | In the hash. Each is still a design change with its own reviewers (the Balancer for rings and node design, UI-UX for anything that moves the tree; `CLAUDE.md` review matrix) |
| `edges`, `petalAnchor` | Free | In the hash. A node that a layout change cuts off from the start is pruned free on load (ADR 0005 section 7) |
| Node order in the file | Free | In the hash, since it is the wire index. Adding a node needs no bump (ADR 0006 bumps only on a changed or removed id); appending at the end keeps diffs readable but is not required |
| `rings`, `view`, `format` | Free | Not in the hash. Drawing only |

ADR 0006's bump rule names node ids, and its golden test pins `PROTOCOL_VERSION` against a hash of the
sorted node ids. This format treats skill, fork and option ids the same way, since ADR 0005 freezes them
alongside node ids; the Architect confirms that reading in ADR 0006, and the golden test then covers all
four kinds of id.

ADR 0005 freezes these ids from T5. The file lands at T8a with provisional positions (#189's 2026-10-27
amendment), and T5 work is already merging while the file is still unwritten (#239 is open), so in
practice an id is frozen from the PR that first adds it to the file. Choose ids at #239 as if they can never
change.

## What the layout hash covers

`TreeLayout#canonicalHash()` (ADR 0006, "The layout hash") is the first 8 bytes of SHA-256, read as a
big-endian long and shown as 16 hex digits, over a canonical serialisation of the parsed file. It covers
exactly:

- every node, in file order, with `id`, `skill`, `class`, `marks` (sorted), `ring`, `requires`,
  `exclusiveGroup` and `pos`;
- every edge, each pair written with its two ids in code-point order, the list sorted;
- every fork, sorted by `parent`, with `parent`, `petalAnchor` and its options sorted by id, each with `id`
  and `pos`.

It does not cover `format`, `rings`, `view`, comment keys, whitespace, line endings, the order of edges, of
forks, of options, of marks, or which way round an edge is written.

Proposed canonical serialisation, for the Architect to confirm in ADR 0006: UTF-8 JSON with no whitespace,
object keys in the order listed above, integers without a decimal point, an absent optional field written
as `null` (`marks` as `[]`), and a condition written with its one key and, inside `allOf` and `anyOf`, its
members sorted by their own canonical text.

This keeps ADR 0006's test cases true: CRLF and whitespace keep the hash, reordering edges keeps it, and
adding an edge, moving a node or reordering nodes changes it. Petal positions are hashed with the nodes'
for the same reason the nodes' are: ADR 0006 hashes "forks with options and `petalAnchor`", and this reads
"options" as each option with its position.

## A worked example

A fragment near the start, with the ring radii of a 40 px a level sketch. It is not the real tree: the real
start area has at least four growth nodes next to the start (§6 Wheel), and the travel stat id
`mining_speed` and the option ids stand in for the ids #239 and the Writer settle.

```json
{
  "format": 1,
  "nodes": [
    { "id": "start", "class": "start", "pos": [0, 0] },
    { "id": "vein_expansion_1", "skill": "vein_expansion", "class": "small", "ring": 0, "pos": [0, -28] },
    { "id": "mining_speed_1", "skill": "mining_speed", "class": "travel", "ring": 1, "pos": [0, -58] },
    { "id": "vein_expansion_2", "skill": "vein_expansion", "class": "small", "ring": 2, "pos": [0, -96] },
    { "id": "ore_attunement_1", "skill": "ore_attunement", "class": "fork_parent", "ring": 3, "pos": [-30, -128] },
    { "id": "ultimine_expansion_1", "skill": "ultimine_expansion", "class": "small", "ring": 3,
      "marks": ["requires"], "requires": { "mod": "ftbultimine" }, "pos": [32, -126] }
  ],
  "edges": [
    ["start", "vein_expansion_1"],
    ["vein_expansion_1", "mining_speed_1"],
    ["mining_speed_1", "vein_expansion_2"],
    ["vein_expansion_2", "ore_attunement_1"],
    ["vein_expansion_2", "ultimine_expansion_1"]
  ],
  "forks": [
    { "parent": "ore_attunement", "petalAnchor": "ore_attunement_1",
      "options": [
        { "id": "common_focus", "pos": [-56, -122] },
        { "id": "uncommon_focus", "pos": [-54, -146] },
        { "id": "rare_focus", "pos": [-36, -160] }
      ] }
  ],
  "rings": [
    { "level": 1, "radius": 40, "labelAngle": -90 },
    { "level": 2, "radius": 80, "labelAngle": -90 },
    { "level": 3, "radius": 120, "labelAngle": -90 },
    { "level": 4, "radius": 160, "labelAngle": -90 }
  ],
  "view": { "...": "as in View above" }
}
```

Checked against the rules (the `rings` list is cut at level 4 here; the real file has all 30, and the
loader would refuse this one):

- Distances are 1, 2, 3, 4 and 4. Vein Expansion's copies are at rings 0 and 2 with distances 1 and 3, so
  both meet `max(1, ring)` and run outward in ring order (§6.1 Vein Expansion: rings 0 and 2). Ore Attunement
  1 (ring 3, §6.1) and Ultimine Expansion 1 (ring 3, §6.1) sit at distance 4.
- Bands: `vein_expansion_1` is 28 from the start, inside level 1's circle at 40, as ring 0 needs;
  `mining_speed_1` at 58 is in [40, 80); `vein_expansion_2` at 96 is in [80, 120); `ore_attunement_1` at
  about 131.5 and `ultimine_expansion_1` at about 130 are in [120, 160).
- `ultimine_expansion_1` has one edge, so hiding it without FTB Ultimine changes nothing else.
- Gaps with the provisional sizes: start (half-side 13) to `vein_expansion_1` (9) is 28 - 13 - 9 = 6;
  `uncommon_focus` to `rare_focus` (petals, 6 each) is max(18, 14) - 12 = 6. Both meet `minIconGap`.
- The petals have no edges, and `petalAnchor` is Ore Attunement's lowest-ring node.

An exclusive pair, as an excerpt with the path to the junction left out. The pacts are ring 6 (§6.1 Greedy
Seams, Resonant Overload); the junction is a ring 5 travel node; with 40 px a level, ring 6's band is
[240, 280).

```json
{ "id": "mining_speed_4", "skill": "mining_speed", "class": "travel", "ring": 5, "pos": [200, -100] },
{ "id": "greedy_seams", "skill": "greedy_seams", "class": "pact", "ring": 6,
  "marks": ["exclusive"], "exclusiveGroup": "seam_pact", "pos": [236, -128] },
{ "id": "resonant_overload", "skill": "resonant_overload", "class": "pact", "ring": 6,
  "marks": ["exclusive"], "exclusiveGroup": "seam_pact", "pos": [250, -96] }
```

with the edges `["mining_speed_4", "greedy_seams"]` and `["mining_speed_4", "resonant_overload"]`. Both
pacts are leaves off one junction at the same ring; each is about 268 from the start; the two 26 px icons
are max(14, 32) - 26 = 6 apart; and neither edge comes within 2 px of the other pact's box.

## Related work

- #212 (E8a-S4): `TreeLayout` parses this format, with the loader checks and the six tests.
- #239 and #259 (E8a-S3a and S3b): the real file, graph first, then positions against the preview.
- #248 (E8a-S7): conditions into the hidden set.
- #252 (E8-S1): `tools/tree-preview` reads this format. Its `sample-layout.json` today uses a different
  shape (top-left `x`, `y`, `w`, `h`, `label` and `frame`) and fills missing positions with elkjs; reading
  this format, it draws centres from `pos`, sizes from `view.iconSize` by class, and places nothing.
