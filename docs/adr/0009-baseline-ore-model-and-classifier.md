# 0009. Baseline ore as vein count, size and height profile, and the sampling classifier

Status: proposed, 2026-10-27, for T4 (E0-S7, #182). Spec: §3.1 Baseline ore distribution, §4.2 rarity
classification, §4.3 step 4, §11 Ore Rarity Classification. Research: `docs/research/vanilla-ore-generation.md`
sections 2, 3 and 5.

Numbered 0009, not 0008: S47 reserved 0001 to 0007 for the planned records, and commit bf13b82 numbered the
tool wear record 0008 after them. The research doc's section 5 planned this record as "ADR 0008"; that
reference now points here.

## Context

The previous build got the baseline wrong twice. The generator from old #15 placed two coal veins a chunk,
0.013% of the volume, for a month (`docs/LESSONS_LEARNED.md`, "What went poorly"). Old PR #153's rewrite
(`worldgen/OreDistribution.java`, never merged) held a density, a 70/25/5 mix and an ore budget per band,
and filled toward the budget; S13 rules out filling toward a target. Its depth was the midpoint of each
ore's merged height range, which put iron at about Y=160.

The old `ore/OreClassifier.java` (1e7cdad) read `CountPlacement.count` and the bounds of a
`HeightRangePlacement` only when the provider was a `UniformHeight` with absolute anchors, through four
access transformers. Every trapezoid and every `above_bottom`/`below_top` feature was dropped, so coal,
iron, copper, lapis, redstone and emerald fell back to Uncommon, and gold came out Common off badlands'
`ore_gold_extra` (research section 2, point 7). `OreClassifierTest` tested only the threshold function, so
nothing caught it. Its `getRarity` defaulted to Uncommon for any block, which would have paid 5 Resonance
for plain stone until PR #117 added `isClassifiedOre` (`docs/research/issue-harvest.md`, #15 with PR #117).
26.1 has no `OreBlock`; ores are found through the `#c:ores` tag.

The research measured vanilla 26.1.2 in solid rock, Nick kept 3% stone and 6% deepslate, and §3.1 now
states the baseline as counts per rarity and band (about 141, 82 and 33 veins in the stone band; 65, 52
and 25 in deepslate), configured sizes (9–12, 7–9, 5–7) and gravel and clay blobs. §4.2 states the rarity
rule: Common at 100 or more expected blocks a chunk over every overworld feature placing the ore,
otherwise Rare under 4.5 blocks a vein, else Uncommon. This record decides how the code holds the
baseline and how the classifier reads a pack.

## Decision

### One pure record: `core.gen.OreBaseline`

`OreBaseline` holds, for each band (stone, deepslate) and each rarity (common, uncommon, rare): veins per
chunk as a `double` (counts are fractional, as vanilla's are), and the configured size range (minimum and
maximum vanilla `size`, drawn uniformly per vein). It also holds the gravel and clay blob counts and size
per band. The values are §3.1's; the Balancer owns them, and a change to them is a §3.1 change in the same
PR. The band heights it divides by (245 layers of stone, 63 of deepslate) are the third copy of the
dimension heights (`CLAUDE.md` rule 8): the layer config's load check compares them too, so the three fail
together.

Nothing else is stored. Every figure below is a method on the record, computed from those fields and from
`core.gen.VeinShape.meanBlocks(size)`, the mean blocks a vein of a configured size places in solid rock,
from the pure port of vanilla's `OreFeature` (research section 5; ADR 0004 owns where the shape sits in the
chunk):

- blocks per chunk for a rarity in a band: count × mean blocks over its size range;
- the rarity shares in a band: each rarity's blocks over the band's total, by placed blocks;
- the band's density: total blocks over 256 × band height.

`LevelCurve` reads these and no copy of them. `averageOresPerHour` in §4.3 step 4 becomes 7,200 blocks an
hour (the constant) × the stone band's density, and `weightedAverageResonancePerOre` is the stone shares ×
each `Rarity`'s base Resonance (§4.2). No 0.68/0.25/0.07 constant exists beside the counts. The generator
reads the same record. A change to any count or size moves the curve with it, which is the point of the
2026-09-27 lesson.

The closed form ignores overlap between veins (about 1.5% of blocks at 3%, §3.1 "Veins per chunk"). A JUnit test
runs the real pure placer over at least 81 chunks with a fixed team id and checks placed blocks against
the closed form within S12's tolerances: each band's density within 5% relative, each rarity share within
2 points. If it fails, the counts are re-derived; the curve's input is not patched. The tests pin
`VeinShape` against the research oracle (size 3 gives 0.46 blocks, 9 gives 6.79, 17 gives 22.15).

### Counts per rarity, and the floor clip

Counts are per rarity, never per ore. Which ore a vein is comes after the count (below), so a kitchen-sink
pack with forty ores places the same number of veins as vanilla, and S12's ±2 points hold whatever the
pack. Copying vanilla's per-ore counts would make density grow with the modpack.

The generator only rolls veins. For each band and rarity it takes the baseline count, applies the
generation nodes' changes (T9), multiplies by the band's clip factor, and rolls the fractional part from the
chunk seed: `floor(c)` veins, plus one when a draw falls under `c - floor(c)`. There is no fill-to-budget
loop.

The clip factor is one number per band, the same for every rarity, so clipping never shifts the mix. It is
computed on the main thread when the snapshot is built (ADR 0003): the band's requested density, in closed
form from `OreBaseline` with the team's generation nodes applied (including veins those nodes add, such as
Guide Vein's), against the 60% ore cap of §3.1; the factor is `min(1, 0.60 / requested)`. It travels in
`GenSnapshot`, so the generator never computes it from team state. Gravel and clay are not clipped; they
count as stone. A per-band hard stop in the placer stays as a safety net only, and S13's test, at the full
deepslate stack (132% requested), asserts the clip holds the band under 60% without the stop firing.

### Height: one profile per rarity and band

Each classified ore carries a block-weighted height profile over the overworld's generation range, in 24
bins of 16 layers (Y-64..319), summed over the features the classifier uses for it (below).

The profiles are mapped onto the Vault by one linear map: the overworld range Y-64..319 onto the expanded
type's mineralizable column, Y-63..245, the same map for both dimension types, so an ore sits at the same
Vault height whichever type a team has. In the base type, whose column starts at Y=1, the mass the map puts
below Y=1 goes into the stone band's lowest 16 layers. That is §3.1's "clamped into it rather than
skipped", stated as arithmetic.

For each band and rarity, the rarity's profile is the sum over its ores of each ore's weight × its mapped
profile, restricted to the band. An ore's weight is its expected blocks a chunk in the overworld. If a
rarity has no mass in a band, its whole mapped profile is moved into the band's nearest bins. A vein
draws its bin from the rarity's band profile, a layer uniformly inside the bin, and then its ore from the
rarity's ores in proportion to weight × that ore's mapped profile at the bin. That reproduces vanilla's
mix at each depth, rescaled, and places a modded ore where its own features put it.

These profiles are built once at server start from the classifier's table, and `OreBaseline` with them is
immutable, so the generator thread reads it safely. The profiles never change a count, a share or the
density, so the curve does not depend on the pack.

### The classifier

`ore.OreClassifier` (adapter) gathers numbers; the rule and the table are pure, in `core.ore`. It runs at
every server start, before any Vault is restored (ADR 0002 orders the two), and its result is held in
memory for the session, never on disk (§11). A `/reload` does not reclassify.

1. Candidates are the blocks in `#c:ores`.
2. The features are those of the overworld's biome source: for each biome in its `possibleBiomes()`, the
   `UNDERGROUND_ORES` entry of `getGenerationSettings().features()`. In 26.1.2.95 `Biome#getGenerationSettings()`
   returns `modifiableBiomeInfo().get().generationSettings()`, so features added by NeoForge biome
   modifiers are included (`net/minecraft/world/level/biome/Biome.java` lines 207–208), and the list is
   indexed by `GenerationStep.Decoration` ordinal (`BiomeGenerationSettings.java` lines 80–81). A placed
   feature counts when its configured feature's config is an `OreConfiguration`; both `minecraft:ore` and
   `minecraft:scattered_ore` use it (`levelgen/feature/Feature.java` lines 105 and 134).
3. A feature is all-biome when more than half of those biomes list it. An ore uses its all-biome features;
   only when it has none does it use its biome-only ones. A strict "every biome" reading would fail on
   copper: `ore_copper` is absent from dripstone caves, so copper would fall to its biome-only features and
   pull in `ore_copper_large` (527 blocks a chunk, research section 2). With the majority reading,
   badlands' `ore_gold_extra` and `ore_copper_large` are ignored and emerald, which has only the mountain
   feature, keeps it.
4. Stone and deepslate variants are grouped: blocks targeted by the same feature belong to one ore, so a
   feature is counted once per ore, not once per target. Which variant each band places is found by
   testing each target's `RuleTest` against stone's and deepslate's default states (`RuleTest#test(BlockState,
   RandomSource)` is public, `levelgen/structure/templatesystem/RuleTest.java` line 11; `targetStates`,
   `target` and `state` are public fields of `OreConfiguration`). A single-variant ore places that block
   in both bands.
5. Placement fields are read by encoding each `PlacementModifier` through `PlacementModifier.CODEC` with
   `JsonOps` and decoding the field it carries: `count` (an `IntProvider`, codec
   `IntProviders.codec(0, 4096)`), `chance` on `rarity_filter`, and `height` (a `HeightProvider`, decoded
   with `HeightProvider.CODEC`). All three are codec fields (`CountPlacement.java` line 11,
   `RarityFilter.java` line 9, `HeightRangePlacement.java` lines 14–15), so no access transformer is
   needed. A modifier type the classifier does not know is passed over and logged once per type.
6. Heights are sampled, not switched on by provider type: 4,096 draws from a fixed seed per feature, with
   the public `HeightProvider#sample(RandomSource, WorldGenerationContext)` (`HeightProvider.java` line 19),
   against `new WorldGenerationContext(overworldGenerator, overworld)`, whose public constructor takes the
   real overworld's limits (`WorldGenerationContext.java` lines 10–13) rather than hard-coded -64 and 384.
   Draws outside the context's range are dropped and lower the expected count, as diamond's range below the
   floor does in vanilla. One path then handles uniform, trapezoid, `above_bottom`, `below_top` and modded
   providers. The expected count is the mean of 4,096 draws of the count provider, times `1/chance` for a
   rarity filter (`shouldPlace` is `random.nextFloat() < 1.0F / this.chance`, `RarityFilter.java` line 22),
   times the share of height draws kept.
7. Blocks per vein: `VeinShape.meanBlocks(size)` for `minecraft:ore`; `size / 2` for
   `minecraft:scattered_ore`, which tries `random.nextInt(config.size + 1)` placements
   (`ScatteredOreFeature.java` line 23), each placing in solid rock. Vein spread is not convolved into the
   profile; it moves blocks a few layers and changes no vanilla ore's class.
8. Per ore: expected blocks a chunk is the sum over its features of veins × blocks per vein; its mean
   blocks a vein is that over the sum of veins. The §4.2 rule in `core.ore` classifies it. Config overrides
   (§10 `ore_classification.overrides`) apply after. An override naming an ore with no feature data gives
   it a flat profile and the median weight of its rarity.
9. An ore in `#c:ores` with no usable overworld feature and no override is not classified and is not
   placed (§3.1, §11). Ancient debris has no overworld feature and falls out here; Ancient Traces handles it.

If decoding a vanilla modifier through its codec fails on Nick's machine, the fallback is old #15's access
transformers. Of its four lines, `CountPlacement.count` and `HeightRangePlacement.height` would return;
`UniformHeight.minInclusive` and `maxInclusive` are no longer needed, because heights are sampled through
the public `sample`, and `RarityFilter.chance` (private in the tree read) would be a new line. Each carries
its ticket comment (Reviewer gate 7).

A note on the sources tree read for this record: in it, `CountPlacement.count`, `HeightRangePlacement.height`,
`UniformHeight.minInclusive`, `UniformHeight.maxInclusive` and `MinecraftServer.storageSource` all read as
public, which is exactly the five lines of the last build's `accesstransformer.cfg`, while
`RarityFilter.chance`, never transformed, is private. The tree was probably decompiled with those
transformers applied, so field visibility cannot be read from it. The codec path does not depend on it.
Unverified: to be read by E1-S5 (#195): the visibility of those fields in a clean NeoForge 26.1.2.95
decompile, and the `#c:ores` tag key in NeoForge's `Tags` class (the tree read holds no NeoForge classes).

### A block that is not a classified ore pays nothing

`OreTable` answers a lookup with the ore's entry or nothing (`@Nullable`); there is no default rarity.
`mining` pays Resonance, counts an ore for any node, or indexes it, only when the broken block has an
entry. Both variants of a grouped ore have one. Stone, deepslate, gravel, clay and a `#c:ores` block
with no overworld feature have none. This is old PR #117's fix made the shape of the API, so the bug
cannot come back through a default.

### Tests

- Pure golden fixture: vanilla 26.1.2's 21 features from research section 2, entered as plain numbers,
  give coal, iron and copper Common, gold, redstone and lapis Uncommon, diamond and emerald Rare, with
  expected blocks within 10% of the research figures. Lapis (4.97 blocks a vein against 4.5) has the
  thinnest margin and is asserted on its own.
- A gametest on a dedicated server runs the real classifier on vanilla and asserts the same eight classes.
  The old bug was in reading the features, not in the rule, so only this test would have caught it.
- The placer count above, the clip test at the full deepslate stack, and a test that a block with no entry
  pays 0.

## Consequences

- `core.gen` holds `OreBaseline` and `VeinShape`; `core.ore` holds `Rarity`, the rule, `OreTable` and
  `HeightProfile`; `ore` is a new adapter package (ADR 0001).
- `LevelCurve` depends on `core.gen`. A Balancer change to a count or size reprices the curve in the same
  commit, and the curve tests show it.
- Node effects that change generation (T9) express themselves as changes to counts and sizes per rarity
  and band, so the clip factor can be computed for them. A node that cannot be written that way needs this
  record amended first.
- The linear height map decides where diamond sits. In the base type it piles into the stone band's lowest
  16 layers; in the expanded type most diamond moves into deepslate, and the stone band's rare veins are
  diamond near its floor and emerald near its top. That is "diamond generates low because diamond
  generates low", but it is a feel question the Balancer should confirm and Nick's T4 ten-minute mine
  should look at.
- A pack whose ores are placed by a feature type that is not `OreConfiguration`-based (Mekanism's custom
  config is the known case, research section 7) generates those ores only through an override.
- The classifier's sampling costs 4,096 draws per height provider at start-up, a few hundred features in a
  large pack; well under a second.

## Rejected alternatives

- Holding the baseline as a density and a mix and filling toward a block budget, as old PR #153 did. S13
  rules it out, and the generated amount then depends on fill order and overlap rather than on counts the
  tests can pin.
- Per-ore counts copied from vanilla. Density would grow with the modpack, and the mix would drift past
  S12.
- Switching on height provider type, as the old classifier did. It silently drops any provider it does not
  know, which is how most of vanilla went missing.
- Running the placement chain (`PlacementModifier#getPositions`) on a fake context. It needs a
  `PlacementContext` over a `WorldGenLevel`, and a biome filter reads the level.
- Calling vanilla's `OreFeature` to place veins. It needs a `WorldGenLevel`, writes blocks instead of
  returning extents, and spills into neighbouring chunks (research section 5; ADR 0004).
- The midpoint of merged height ranges as an ore's depth, as old PR #153 did. It put iron at about Y=160.
- A default rarity for unclassified blocks. It is the stone-pays-5 bug.
- Computing shares at start-up by running the placer. It would give the same number as the test but hide a
  drift between the shape code and the curve instead of failing on it.

## Spec sections changed

§3.1 (the 40% floor bullet and "Where each ore sits"), §4.2 (the rarity classification paragraph), §4.3
step 4 and §11 Ore Rarity Classification. The exact old and new text is in the run's
`spec-changes/E0-S7.md` for the Writer to apply.

## Reviews

To be filled: developer, reviewer, qa, writer. Each verdict lists what it checked.
