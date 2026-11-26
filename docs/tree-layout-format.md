# The tree layout file format

Status: proposed (E8a-S9, #189), round 1 answered. Owner: UI/UX, with the Architect. Reviewers: Architect,
Developer, Reviewer, QA. Spec: §6 Notation (Layout data, Saved state, Ring, Fork, Exclusive pair, Mod-gated
nodes), §6.1 Shape of the tree, §8 Tab 1, §11 Tree Layout Data. Records:
`docs/adr/0005-persisted-data-and-versioning.md` ("Registry ids are save data", section 6 on migrations,
section 7 load rules), `docs/adr/0006-network-channel-and-tome-sync.md` (bump rules, size bounds, the layout
hash; amended 2026-11-25 with this format), `docs/adr/0001-toolchain-and-package-layout.md` (the
`core.layout` row).

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

`src/main/resources/orevault/tree_layout/resonance.json`, in the mod jar (Architect, round 1). It is not in a
datapack or a resource pack, so a server and its clients read the same bytes (§6 Layout data), and it sits
outside both `assets/` and `data/` so that neither kind of pack can shadow it. It is read with
`Class#getResourceAsStream` on a class in the mod jar. A missing file fails start with a message naming the
path. The parser in `core.layout` takes a `Reader`, so tests feed it files from
`src/test/resources/tree_layout/`. `TreeLayout` is immutable and never re-read: `/reload` does not touch it
(ADR 0006, "Not `LayoutCheck`: the layout is in the jar"). The post-1.0 Animus tree would be a second file in
the same format.

## Units and coordinates

- Every position is in GUI px (GUI units) at zoom 1, measured from the centre of the start node. Multiply by
  zoom and by GUI scale to get screen pixels.
- `x` grows to the right and `y` grows downward, as in every Minecraft screen and in the preview SVG.
- A position is the centre of the icon, written as `[x, y]` with integer values. Every icon size in §8 Tab 1
  is even (12, 14, 18, 26, 36), so an integer centre puts the icon's corner on a whole GUI unit. Whether the
  icon then lands on whole screen pixels at every crisp zoom stop is a rendering claim, checked by eye
  (below).
- The start node is at `[0, 0]`. Nothing else may be.
- Ring radii (below) are integers in the same units, measured from the start.

Position means distance from the start and nothing else (§6 Ring; `docs/TEAM_REQUIREMENTS.md`, UI/UX
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

The file is strict JSON, read through Gson's `JsonReader` with `Strictness.STRICT` (no comments, single
quotes or trailing commas). The parser walks each object itself and keeps the names it has seen, so a
duplicate key is refused rather than silently overwritten. Every object refuses keys it does not
define, so a misspelt `petalAncor` fails at load instead of going missing. A key that starts with `_` is a
comment: allowed on any object, ignored by the parser and not hashed. Int fields refuse fractions and
strings: the loader requires `peek() == JsonToken.NUMBER` and parses `nextString()` with `Integer.parseInt`,
because `JsonReader#nextInt()` would accept `"5"` and `1.0`. Both have a refusal test (#212). Gson's
`Strictness` needs 2.11 or later; #212 confirms the version the 26.1 classpath resolves, and on an older one
uses `setLenient(false)`.

## Nodes

```json
{ "id": "vein_expansion_2", "skill": "vein_expansion", "class": "small", "ring": 2, "pos": [0, -96] }
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | string | yes | The node's id, unique among nodes. Snake case, `[a-z0-9_]`, at most 64 characters (`MAX_ID`, ADR 0006). Option ids follow the same form and may not equal any node or skill id |
| `skill` | string | yes, except on the start | The skill this node is a copy of (§6 Skill and tier). Its lang keys are `node.orevault.<skill>` (§8 Tab 1, Lang keys) and its effects constants are keyed by it (§11 Tree Layout Data). A skill's tier is how many of its nodes the team owns. A travel stat is one skill (§6.1 Travel stats). `start` is not a valid skill id |
| `class` | string | yes | One of `start`, `small`, `travel`, `notable`, `keystone`, `pact`, `growth`, `fork_parent` |
| `marks` | array of string | no, default `[]` | Any of `tradeoff`, `exclusive`, `requires`, each at most once |
| `ring` | int | yes, except on the start | The level floor, 0 to 30 (`LEVEL_CAP`, §4.3). Authored, never derived from distance (§6 Ring) |
| `requires` | condition | when marked `requires` | What must be present on the server for the node to be shown (below) |
| `exclusiveGroup` | string | when marked `exclusive` | The pair this node belongs to. Exactly two nodes share a group |
| `forOption` | `{ "fork": string, "option": string }` | no | The node is shown only while that option is the team's choice (below) |
| `pos` | `[int, int]` | yes | The icon's centre (above) |

The classes are §6's seven node classes plus `start`, which is a token of this format and not a node class.
The start has no skill, ring, marks, `requires`, `exclusiveGroup` or `forOption`, and no effect. It is owned
from the beginning and never written to the save (`owned` holds every other node; §11 SavedData Pattern,
"the start is implicit"). On the wire it is node 0, with bit 0 of `owned` always set, never set in `hidden`,
`active` or `outgrown`, and a `boughtBy` entry of −1 (ADR 0006, `Ownership` and `Marks` rows). Its id is
`start`. Fork options are not nodes and never appear in `nodes` (§6 Fork).

The marks follow §6's mark table. `marks` must contain `requires` exactly when `requires` is present and
`exclusive` exactly when `exclusiveGroup` is present; the loader refuses a node where they disagree. The
`tradeoff` mark makes the node a toggle; whether that toggle is per player or a team vote (Volatile Veins,
Molten Seam) is code in `core.tree`, not layout. `forOption` has no mark: §6's `[REQUIRES]` mark stays a mod
or a tag.

Ids. A single-node skill's node id equals its skill id (`greedy_seams`). A skill with copies names them
`<skill>_<n>`, numbered from 1 in ring order when they are first authored (`vein_expansion_1` to `_5`). The
number is only a name: it is not the tier, the code never parses it, and a copy keeps its id if it is later
re-ringed.

Every node of one skill has the same class and the same `tradeoff` mark, so its copies draw with one icon
and one frame (§6 Skill and tier). `requires`, `exclusiveGroup` and `forOption` are per node: Ore Working's
nodes 4 to 6 carry a condition and a fork option that nodes 1 to 3 do not (§6.1 Ore Working).

### Conditions (`requires`)

A mod-gated node is hidden when its condition fails (§6 Mod-gated nodes). The server evaluates every
`requires` into the hidden set at start and again on each `/reload`, except `energyItem`, which is
evaluated once at start and reused across reloads (#248, E8a-S7). The result is sent as the `hidden` bitset
(ADR 0006); the client never evaluates a condition. An owned node hidden this way is dormant: no effect, not
counted as spent (§6 Mod-gated nodes).

A condition is an object with exactly one key besides any `_` comment keys:

| Form | True when | Used by |
|---|---|---|
| `{ "mod": "ftbultimine" }` | the mod id is loaded | FTB Ultimine, Paraglider, Hephaestus Tools, GeOre nodes |
| `{ "tag": "c:tools/paxel" }` | the item tag has an entry. The value is `namespace:path` | Hammer and Paxel Affinity |
| `{ "tagPrefix": "c:clumps/" }` | some item tag whose full id starts with the string has an entry. The value is `namespace:path` ending in `/`, so the parent tag `c:clumps` itself does not match | Ore Working 4 to 6 (§6.1 Ore Working, §9) |
| `{ "energyItem": true }` | some registered item has energy storage. Only `true` is accepted | Resonant Dynamo, Galvanic Seam |
| `{ "lootTable": "<namespace:path>" }` | the loot table is loaded and has at least one pool, after datapacks load | Gem Seam, as `anyOf` over its three tables |
| `{ "allOf": [ ... ] }` | every listed condition is true. Not empty | Ore Working 4 to 6 |
| `{ "anyOf": [ ... ] }` | at least one listed condition is true. Not empty | Hammer and Paxel Affinity's four tags, Gem Seam's three tables |

Ore Working 4 to 6, for example, carry
`"requires": { "allOf": [ { "tagPrefix": "c:clumps/" }, { "tagPrefix": "c:shards/" }, { "tagPrefix": "c:crystals/" } ] }`
and `"forOption": { "fork": "ore_working", "option": "ore_doubling" }`, where `ore_doubling` is the option id
#208 fixed (`core.tree.ForkOption`). The exact node ids, tags and loot tables
each node names come from §6.1 and §9 and are written into the file at #239. If a §6.1 condition fits none of
these forms, #248 adds one in the PR that needs it, updating this table and the loader's list of known forms.

### Fork-gated nodes (`forOption`)

A node with `forOption` is shown only while that option is the team's choice. `core.tree` evaluates it per
team from `fork_choice`, on the server and on the client (which has the layout and the `Forks` payload). It is
never part of the server's hidden set, because that set is per server and is resent only on join and
`/reload` (ADR 0006, "Triggers"), and because `owned` plus `hidden` reads as dormant (ADR 0006,
`Ownership` row). A node hidden by its fork option is not dormant: it still counts as spent, and the option
cannot be swapped away while the team owns it (§6 Mod-gated nodes, §4.4; refusal `fork_dependents_owned`,
ADR 0006). Manager ruling, round 1, on the Developer's design.

A node is visible to a team when it is not in the hidden set and its `forOption`, if any, is the team's
choice. `BuyNode`, `BuyPath` and `SetPin` require a visible node: a hidden one is refused with `hidden`, a
fork-gated one whose option is not chosen with `option_not_chosen` (ADR 0006, amended 2026-11-25). A pin on a
node that stops being visible is dropped, whether the hidden set or the team's fork choice changed (ADR 0005
section 7, amended 2026-11-25).

These are JUnit cases in `core.tree`, owed to #212:

- a `forOption` node is hidden under every other option and with no choice, and shown under its own;
- a swap away from its option is refused with `fork_dependents_owned` while the team owns it;
- `BuyNode`, `BuyPath` and `SetPin` on a fork-gated node whose option is not chosen are refused with
  `option_not_chosen`, and a node that is both mod-hidden and fork-gated is refused with `hidden`, the
  earlier check;
- a pin on a fork-gated node is dropped when the team's fork choice changes away from its option.

`SetTradeoff` and `SetGenerationVote` also require a visible node. When #212 builds the `Refusal` enum it
either lists `hidden` and `option_not_chosen` on those two rows of ADR 0006 or states there why they cannot
occur.

## Edges

```json
["vein_expansion_1", "mining_speed_1"]
```

An edge is an array of exactly two node ids. Edges are undirected: `[a, b]` and `[b, a]` are the same edge,
and the file may list it either way, but only once in either direction. No edge joins a node to itself.
Neither end may be a petal, since a petal has no edges (§6 Fork). Edges are drawn as straight lines from icon
border to icon border (§8 Tab 1); the renderer clips each line at the two icon boxes and adds no bends.

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

These are the real option ids: #208 fixed every option id and its parent in `core.tree.ForkOption`, and the
file must use them.

| Field | Type | Meaning |
|---|---|---|
| `parent` | string | The fork parent's skill id, which must name a skill in `nodes`. It is also the fork's id: the key of `fork_choice` in the save (ADR 0005, `fork_choice` is keyed by fork parent skill id) and the `forkId` of `ChooseForkOption` (ADR 0006) |
| `petalAnchor` | string | The node the petals are drawn beside (§6 Fork): a node of the parent skill at the lowest ring among its nodes. If two copies share that ring, either is accepted, and the author's choice stands |
| `options` | array, at least 2 | Each petal's option id and the centre of its icon, in the same units as nodes. Option ids are unique across all forks in the file |

Petals are drawn where `pos` says, not computed from the anchor; "beside" is the `petalMaxGap` test (gate 9,
below). Petals have no ring: an option can be picked once the team owns any node of its parent (§6 notation,
`[FORK OPTION]`). Every node of a fork's parent skill has class `fork_parent`, and every `fork_parent` skill
has exactly one fork. At most 16 forks (`MAX_FORKS`, ADR 0006); §6.1 has four.

## Rings

```json
{ "level": 12, "radius": 480, "labelAngle": -90 }
```

One entry for each level from 1 to 30, in order, integer radii strictly increasing. `radius` is the faint
circle centred on the start where that level opens; `labelAngle` is where its label sits, in degrees
clockwise from the positive `x` axis (so -90 is straight up), authored like any other position because the
renderer places nothing. Level 0 has no ring: the start area inside level 1's circle is ring 0's band.

A node of ring `r` has its centre in ring `r`'s band: `x² + y² >= radius(r)²` (0 for ring 0) and
`x² + y² < radius(r + 1)²` (no upper bound for ring 30), computed in `long` arithmetic. This is the "drawn in its
ring's band" half of §6 Ring, and it is part of the first layout test below. #212 writes that half; it runs
against real positions from #259, which turns it on.

The radii are UI/UX's, set against the preview at #259. The 2026-10-26 preview drew the whole tree about
2,400 tree pixels across (§6.1 Shape of the tree), roughly 40 px a level; that is a starting point, not a
value.

## View

```json
"view": {
  "iconSize": { "start": 26, "travel": 14, "small": 18, "growth": 18, "fork_parent": 18,
                "notable": 26, "pact": 26, "keystone": 36, "petal": 12 },
  "minIconGap": 6,
  "petalMaxGap": 20,
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
| `iconSize` | Side of each class's square icon box in GUI px, plus `petal`. Every key above is required; each value is a positive even integer. Used for clipping edges and for the gap and edge tests | §8 Tab 1 class table, provisional until Nick's yes on the E8-S5 (#283) preview (Designer). `start` has no row in §8; 26 is a placeholder for the Designer to set |
| `minIconGap` | The least space between any two icon boxes, nodes and petals alike, in GUI px at zoom 1. For two square boxes with half-sides `a` and `b` and centres `dx`, `dy` apart, the gap is `max(abs(dx), abs(dy)) - a - b` | Provisional 6, set by UI/UX with the Designer at #259 |
| `petalMaxGap` | The most space between each petal's box and its `petalAnchor`'s box, measured the same way | Provisional 20, UI/UX at #259 |
| `edgeClearance` | How far an edge must stay from the box of any icon that is not one of its two ends, in GUI px at zoom 1 | Provisional 2, UI/UX |
| `edgeWidth` | Edge line width in GUI px at zoom 1 | Provisional 2 (the preview script's stroke), Designer, with the edge style |
| `edgeMinScreenPx` | The thinnest an edge is drawn on screen, so lines thicken rather than vanish when zoomed out (`DESIGN_LOG.md` 2026-09-07, "Descriptions, prerequisite chains, and zoom") | Provisional 2, Designer; Nick judges on the preview |
| `zoom.min`, `zoom.max` | The zoom range; `min` below `max` | 0.2 and 1.5, §8 Tab 1 ("between a fifth and one and a half times full size") |
| `zoom.overviewStops` | Zoom values the wheel stops at below the crisp range, used as written; ascending, each within `[min, max]` | Provisional, UI/UX; Nick judges on the preview (`docs/TEAM_REQUIREMENTS.md` UI/UX "Zoom and text": "0.2 and one or two overview stops") |
| `zoom.crispFromEffective` | The lowest effective scale (zoom times GUI scale) of the crisp stops; a whole number, at least 1 | 1, from the same section |
| `text.ringLabelsFromEffective` | The lowest effective scale at which ring labels ("Level 12 opens here", §8 Tab 1) are drawn | Provisional 2, UI/UX; Nick judges on the preview |
| `text.badgeNumbersFromEffective` | The lowest effective scale at which the opening level beside a lock badge is drawn | Provisional 2, UI/UX; Nick judges on the preview |

Gap, clearance and width values may not be negative.

### Zoom stops

The wheel moves between stops (`docs/TEAM_REQUIREMENTS.md` UI/UX "Zoom and text"). At GUI scale `g`:

1. The crisp stops are `k / g` for every whole `k` from `crispFromEffective` up to `floor(max × g + 1e-9)`. A crisp
   stop below `min` is dropped.
2. An overview stop is kept only if it lies in `[min, max]` and below the smallest crisp stop that was kept.
   If no crisp stop was kept, every overview stop in `[min, max]` is kept.
3. `min` is always a stop.
4. Stops are compared with a tolerance of 1e-9, so 0.2 × 5 meets 1/5.

A pure `ZoomStops.of(view, g)` returns the list, and a JUnit test pins it for GUI scales 1 to 9 with the
values above:

| GUI scale | Stops |
|---|---|
| 1 | 0.2, 0.35, 1.0 |
| 2 | 0.2, 0.35, 0.5, 1.0, 1.5 |
| 3 | 0.2, 0.333, 0.667, 1.0, 1.333 |
| 4 | 0.2, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5 |
| 5 | 0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4 |
| 6 | 0.2, 0.333, 0.5, 0.667, 0.833, 1.0, 1.167, 1.333, 1.5 |
| 7 | 0.2, 0.286, 0.429, 0.571, 0.714, 0.857, 1.0, 1.143, 1.286, 1.429 |
| 8 | 0.2, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0, 1.125, 1.25, 1.375, 1.5 |
| 9 | 0.2, 0.222, 0.333, 0.444, 0.556, 0.667, 0.778, 0.889, 1.0, 1.111, 1.222, 1.333, 1.444 |

1.5 is not reachable at GUI scales 1, 3, 5, 7 and 9. That is intended: 1.5 at those scales is a fractional
effective scale, at which icons and glyphs come out uneven, and the point of the crisp stops is that every
stop above the overview range is a whole effective scale. §8's 1.5 is the upper bound, not a promised stop.
At scale 9 the stops 0.2 and 0.222 sit close together; Nick judges that on the preview. The readout shows
the zoom (§8 Tab 1).

### Text thresholds

No node text is drawn on the page (§8 Tab 1, "Nodes are icons"), so the old per-node text thresholds stay
retired. Two kinds of text are still on the page, the ring labels and the lock badge's opening level, and
each is drawn only at or above its threshold, because below it the glyphs are smaller than the shapes they
sit beside and read as noise (`DESIGN_LOG.md` 2026-09-07: "Three-pixel text is not small text, it is
noise"). Below its threshold the ring has no label and the badge no number (§8 Tab 1, "The page is opaque and
zooms"). Thresholds are in effective scale, not zoom, because legibility depends on screen pixels per font
texel. Tooltips, the zoom readout and the top bar are not page text and are unaffected.

## What is checked, and where

### The loader

`TreeLayout`'s parser refuses a malformed file. A refusal fails start on both the server and the client. The
exception message names the file, the rule broken, and the offending id, edge or JSON path, for example:

```
orevault/tree_layout/resonance.json: edge listed twice: ["vein_expansion_1", "mining_speed_1"] at $.edges[7], first at $.edges[2]
```

It refuses:

- unknown `format`; a duplicate key in one object; an unknown key; a missing required field; an int field
  holding a fraction or a string;
- an unknown class, mark or condition form; a repeated mark;
- class `start` on any node but the first, or a first node that is not `start`; the start with `skill`,
  `ring`, `marks`, `requires`, `exclusiveGroup` or `forOption`; the start not at `[0, 0]`, or any other node
  at `[0, 0]`;
- a non-start node missing `skill` or `ring`; a ring outside 0 to 30; skill id `start`;
- a duplicate node id; an id not in snake case or over 64 characters; more than 1024 nodes (`MAX_NODES`);
- `pos` or an edge that is not exactly two elements; a non-integer `pos`;
- an edge naming no node, joining a node to itself, or listed twice in either direction;
- marks that disagree with `requires` or `exclusiveGroup`; an `exclusiveGroup` without exactly two members;
- copies of a skill with different classes or `tradeoff` marks;
- a condition without exactly one key besides `_` keys; an empty `allOf` or `anyOf`; `energyItem` other than
  `true`; a `tag`, `tagPrefix` or `lootTable` that is not `namespace:path`, or a `tagPrefix` not ending in
  `/`;
- a fork whose `parent` names no skill or with fewer than two options; an option id that repeats any option
  id in the file, or equals a node or skill id; more than 16 forks; a `fork_parent` skill without exactly one fork, or a fork whose
  parent's nodes are not `fork_parent`; a `petalAnchor` naming no node, or one that is not a node of the
  parent at its lowest ring;
- a `forOption` naming no fork or no option of that fork;
- rings not one per level from 1 to 30, or radii not strictly increasing;
- a view missing an `iconSize` key, or one that is odd or not positive; `zoom.min` not below `zoom.max`;
  overview stops not ascending or outside `[min, max]`; `crispFromEffective` not a whole number of at least 1;
  a negative gap, clearance or width; `petalMaxGap` below `minIconGap`.

Each refusal has a JUnit test that parses a small bad file from `src/test/resources/tree_layout/` and expects
that refusal, and one good file with an `_` key on every object parses (#212).

### The layout tests

JUnit tests over the shipped file pin the layout's rules. Each reports every offender with the value it
measured (`vein_expansion_4: ring 7, distance 6`), not only the first. These are the six §6 Layout data
tests, with what each reads from this format:

1. Every node is reachable from `start` over `edges`. Every node except the start has a distance
   (breadth-first from `start`) of at least `max(1, ring)`, and its `pos` in its ring's band (`rings`, in
   integers as above).
2. The copies of a skill are in ring order: no copy is nearer the start, by distance, than a copy with a
   lower ring.
3. Every node with `requires` or `forOption` is on a dead end: removing every such node changes no other
   node's distance or reachability (§6 Mod-gated nodes; §6.1 Ore Working, "a dead-end spur").
4. The two nodes of each `exclusiveGroup` are leaves (one edge each) off the same junction node and have the
   same ring.
5. Petals have no edges (by construction here, since edges name nodes only; the test checks that no option
   id appears in `edges`).
6. No edge passes through another node: the segment between two centres keeps `edgeClearance` from every
   other icon box, petals included.

UI/UX's gates add more over the same data (`docs/TEAM_REQUIREMENTS.md` UI/UX "My gates"):

7. No two icon boxes, nodes and petals, closer than `minIconGap`. A JUnit test.
8. Edges meet icon borders. The renderer clips at the box, so this is checked on the `tools/tree-preview`
   render and in `runClient`, not in JUnit.
9. Each petal within `petalMaxGap` of its anchor. A JUnit test.

The reserved-id test, that no node, skill or option id in the file is one of the §6.1 reserved ids, is owed
to #239, which writes the first real file.

### Checked by eye

The rendering claims are checked on the preview and in `runClient`, at GUI scales 2, 3 and 4 at 1920x1080 and
2 and 3 at 1280x720, at each zoom stop, and are unverified until seen there: whole-pixel icons at the crisp
stops, badges not overhanging into the gap (the Designer's 9x9 badges, `docs/TEAM_REQUIREMENTS.md` Designer
table), `edgeMinScreenPx` keeping edges visible at 0.2, and the text thresholds.

## Frozen ids and free fields

Saved tree state is node ids only (§6 Saved state), so where a node is drawn costs a save nothing. What a
save or a payload names is frozen.

| Field | Status | Why, and what a change costs |
|---|---|---|
| Node `id` | Frozen from T5 | Save data: `owned`, `bought_by`, `pinned_node`, `generation_tradeoffs` and `PlayerStats` `active_tradeoffs` hold node ids (ADR 0005, "Registry ids are save data"). A rename is a `PROTOCOL_VERSION` bump in the same PR (ADR 0006, third bump rule) and, from T5, a migration mapping old to new in each of those keys, with their `data_version` bumps (ADR 0005 section 6). A removal is a bump and needs no migration: the load rules drop the id and return its point (ADR 0005 section 7). Either way the old id joins the §6.1 reserved list in the same PR |
| A node's `skill` | Frozen with the node | An owned node id must keep meaning a copy of the same skill, or a save silently changes build. To move a copy to another skill, remove the node and add one with a new id |
| `skill` ids | Frozen from T5 | Save data (ADR 0005); they key the lang file (§8 Tab 1, Lang keys), the effects constants (§11) and tier derivation. A rename or removal is a bump (ADR 0006, amended 2026-11-25), and the old id joins the reserved list |
| `parent` (fork id) and option `id`s | Frozen from T5 | Save data: `fork_choice` maps fork parent skill id to option id (ADR 0005); both travel as strings in `ChooseForkOption` and `Forks` (ADR 0006). A rename is a bump plus a migration of `fork_choice`; a removal is a bump, and the load rules drop the choice (ADR 0005 section 7). The old id joins the reserved list |
| `start` | Frozen | Edges name it, the wire puts it at index 0, and saves imply it |
| `pos`, option `pos` | Free | In the hash, not the protocol. Moving a node needs no bump and no migration (ADR 0006, "Moving, re-ringing or re-edging a node needs no bump"); an older client is disconnected with the layout mismatch message until it updates |
| `ring`, `class`, `marks`, `requires`, `exclusiveGroup`, `forOption`, `edges`, `petalAnchor` | Free for the format | In the hash. Each is still a design change with its own reviewers (the Balancer for rings and node design, UI/UX for anything that moves the tree; `CLAUDE.md` review matrix). A change to a node a save can own must leave every save in a state ADR 0005 section 7 settles: a node cut off from the start is pruned free, an owned node that gains `requires` is dormant wherever its condition fails. Three changes have no load rule today and need one in the same PR: making two ownable nodes exclusive (a team may own both); removing `tradeoff` from a node that saves hold in `active_tradeoffs` or `generation_tradeoffs`; and adding or changing `forOption` on an ownable node (a team may own it under another option) |
| Node order in the file | Free | In the hash, since it is the wire index. Adding a node needs no bump (ADR 0006 bumps only on a changed or removed id); appending at the end keeps diffs readable but is not required |
| `rings`, `view`, `format` | Free | Not in the hash. Read by the renderer and the layout tests, and `format` by the loader; no rule reads them |

ADR 0006's golden test holds `PROTOCOL_VERSION` and literal lists of the node, skill and option ids (a fork's
id is its parent's skill id). An id on the list missing from the file at the pinned version fails with "bump,
and migrate if it was renamed"; an id in the file not on the list fails with "add it to the list"; and when the version is
re-pinned, every id dropped from the lists must be on the §6.1 reserved list (ADR 0006, amended 2026-11-25).
The test is owed to #212 with the layout loader, against the provisional file's ids; #239 updates its lists
when it writes the real ids.

ADR 0005 freezes these ids from T5. The file lands at T8a with provisional positions (#189's 2026-10-27
amendment), and T5 work is already merging while the file is still unwritten (#239 is open), so in
practice an id is frozen from the PR that first adds it to the file. Skill and option ids were fixed earlier, at
#208, in `core.tree.SkillId` and `core.tree.ForkOption`, so #239 uses those and chooses only node ids, as if they
can never change.

## What the layout hash covers

`TreeLayout#canonicalHash()` (ADR 0006, "The layout hash") is SHA-256 over a canonical serialisation of the
parsed file, reduced to `ByteBuffer.wrap(sha256, 0, 8).getLong()` and shown as 16 lowercase hex digits,
zero-padded. It covers exactly:

- every node, in file order, with `id`, `skill`, `class`, `marks` (sorted), `ring`, `requires`,
  `exclusiveGroup`, `forOption` (`fork`, then `option`) and `pos`, in that key order;
- every edge, each pair written with its two ids in code-point order, the list sorted in code-point order;
- every fork, sorted by `parent`, with `parent`, `petalAnchor` and its options sorted by id, each with `id`
  and `pos`.

It does not cover `format`, `rings`, `view`, comment keys, whitespace, line endings, the order of edges, of
forks, of options, of marks or of `allOf` and `anyOf` members, or which way round an edge is written.

The serialisation is Ore Vault's own writer, not `Gson#toJson` (Architect, round 1; ADR 0006 amended to
match). It is UTF-8 JSON with no whitespace and the fixed key order above. Strings escape only `"`, `\` and
U+0000 to U+001F, the last as `\u00xx` in lowercase hex. Integers are written with `Integer.toString`. An
absent optional field is written as `null`, and absent `marks` as `[]`. A condition is written with its one
key, and inside `allOf` and `anyOf` its members are sorted by their own canonical text. One golden unit case
pins a small layout's canonical text and its hash.

`canonicalHash()` cases owed to #212, beyond the ones ADR 0006 lists (CRLF and whitespace keep the hash,
reordering edges keeps it, adding an edge, moving a node or reordering nodes changes it):

- keep the hash: writing an edge the other way round; reordering forks, options, marks, or the members of an
  `allOf` or `anyOf`; adding or changing an `_` comment key; changing `rings`, `view` or `format`;
- change the hash: moving a petal; adding, removing or changing a `forOption` or a `requires`; changing a
  ring, class, mark or `exclusiveGroup`; changing a `petalAnchor`; renaming any id.

## A worked example

A fragment near the start, with the ring radii of a 40 px a level sketch. It is not the real tree: the real
start area has at least four growth nodes next to the start (§6 Wheel). The travel stat id `mining_speed` and
the option ids are the real ones, fixed at #208 (`SkillId`, `ForkOption`).

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

Checked against the rules (the `rings` list is cut at level 4 here and `view` is elided; the real file has
all 30 rings and a full view, and the loader would refuse this one):

- Distances are 1, 2, 3, 4 and 4. Vein Expansion's copies are at rings 0 and 2 with distances 1 and 3, so
  both meet `max(1, ring)` and run outward in ring order (§6.1 Vein Expansion: rings 0 and 2). Ore Attunement
  1 (ring 3, §6.1) and Ultimine Expansion 1 (ring 3, §6.1) sit at distance 4.
- Bands, in squared integers: `vein_expansion_1` has 784, below 40² = 1600, as ring 0 needs;
  `mining_speed_1` has 3364, in [1600, 6400); `vein_expansion_2` has 9216, in [6400, 14400);
  `ore_attunement_1` has 17284 and `ultimine_expansion_1` has 16900, both in [14400, 25600).
- `ultimine_expansion_1` has one edge, so hiding it without FTB Ultimine changes nothing else.
- Gaps with the provisional sizes: start (half-side 13) to `vein_expansion_1` (9) is 28 - 13 - 9 = 6;
  `uncommon_focus` to `rare_focus` (petals, 6 each) is max(18, 14) - 12 = 6. Both meet `minIconGap`.
- Petal to anchor (half-side 9): 11, 9 and 17, all within `petalMaxGap`.
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

- #212 (E8a-S4): `TreeLayout` parses this format, with the loader refusals and their bad-file tests, the six
  tests and gates 7 and 9, `ZoomStops`, and the `canonicalHash()` cases above.
- #239 and #259 (E8a-S3a and S3b): the real file, graph first, then positions against the preview; #239 also
  owes the reserved-id test.
- #248 (E8a-S7): `requires` into the hidden set.
- #252 (E8-S1): `tools/tree-preview` reads this format. Its `sample-layout.json` today uses a different
  shape (top-left `x`, `y`, `w`, `h`, `label` and `frame`) and fills missing positions with elkjs; reading
  this format, it draws centres from `pos`, sizes from `view.iconSize` by class, and places nothing. Gate 8
  is checked on its render.
