# 0009. Baseline ore as vein count, size and height profile, and the sampling classifier

Status: proposed, 2026-10-27, for T4 (E0-S7, #182); stays proposed until Nick answers question 4. The
height placement below is Nick's answer to question 1 on 2026-10-27: same density at every height. Which ore
a vein is by height was reopened on 2026-10-31 (BAL-16), because the consequence he was told for it was
wrong; it is the Manager's question 4 to Nick, and the section on it below is interim text until he answers.
Accepted once that section states his answer and the matrix reviewers confirm it. Spec: §3.1 Baseline ore
distribution, §4.2 rarity classification, §4.3 step 4, §11 Ore Rarity Classification. Research:
`docs/research/vanilla-ore-generation.md` sections 2, 3 and 5.

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
rule: Common at 100 or more expected blocks a chunk over the features the classifier uses for it (its
all-biome features, or its biome-only ones when it has none; step 3 below), otherwise Rare under 4.5
blocks a vein, else Uncommon. This record decides how the code holds the baseline and how the classifier
reads a pack.

In round 1 the Balancer modelled the first draft's height placement per 16-layer slab (BAL-1): with each
rarity's veins placed by its mapped vanilla profile, the base type's lowest 16 stone layers came out about
15% ore and everything above 1.7 to 2.7%. A player strip-mining at the floor would level about 7.5 times
faster than the §4.3 curve, which prices the band average, so the 100 hours would become about 13, and the
arrival height would read thinner than 3%. That is the 2026-09-27 failure again: a curve priced for a Vault
nobody mines. The height decision below answers it.

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
- the band's density: total blocks over 256 × band height;
- each rarity's edge factor in a band (below, "Height"), from `VeinShape`'s box heights.

`LevelCurve` reads these and no copy of them. `averageOresPerHour` in §4.3 step 4 becomes 7,200 blocks an
hour (the constant) × the stone band's density, and `weightedAverageResonancePerOre` is the stone shares ×
each `Rarity`'s base Resonance (§4.2). No 0.68/0.25/0.07 constant exists beside the counts. The generator
reads the same record. A change to any count or size moves the curve with it, which is the point of the
2026-09-27 lesson.

The closed form ignores overlap between veins (about 1.5% of blocks at 3%, §3.1 "Veins per chunk"). A JUnit test
runs the real pure placer over at least 81 chunks with a fixed team id and checks placed blocks against
the closed form within S12's tolerances: each band's density within 5% relative, each rarity share within
2 points. If it fails, the counts are re-derived; the curve's input is not patched. The same test reports
the per-chunk standard deviation of standing ore for each dimension type, which ADR 0004 Amendment 1's
compass margin is checked against (0.10 at least 4.5 times that deviation over the mean). The tests pin
`VeinShape` against the research oracle (size 3 gives 0.46 blocks, 9 gives 6.79, 17 gives 22.15).

### Counts per rarity, and the floor clip

Counts are per rarity, never per ore. Which ore a vein is comes after the count (below), so a kitchen-sink
pack with forty ores places the same number of veins as vanilla, and S12's ±2 points hold whatever the
pack. Copying vanilla's per-ore counts would make density grow with the modpack.

The generator only rolls veins. For each band and rarity it takes the baseline count, applies the
generation nodes' changes (T9), multiplies by the band's clip factor and by the rarity's edge factor in that
band (below), and rolls the fractional part from the chunk seed: `floor(c)` veins, plus one when a draw
falls under `c - floor(c)`. There is no fill-to-budget loop.

There are two clip factors, one for the stone band and one for the deepslate band, each the same for every
rarity in its band, so clipping never shifts the mix. Each is computed on the main thread when the
snapshot is built (ADR 0003) as `min(1, 0.60 / densest)`, where `densest` is the highest ore density, in
closed form from `OreBaseline` with the team's generation nodes applied (including veins those nodes add,
such as Guide Vein's), that the nodes request in any one region of that band: a band-wide request, a
layer range a node concentrates ore into (Deep Veins' lowest 30 layers), or one chunk outcome of a
per-chunk roll (a Vein Singularity chunk). The §3.1 cap is on every block of the Vault, not the band
average, so the factor is set by the densest region (Balancer, BAL-3). It travels in `GenSnapshot`, so the
generator never computes it from team state. Gravel and clay are not clipped; they count as stone. A
per-band hard stop in the placer stays as a safety net only.

The clip test computes the full stack's request for each band from `OreBaseline` with every generation
node at its top tier, asserts that the deepslate request is above 0.9 (so the clip really fires), and then
runs the placer over 81 chunks for each region outcome and asserts that every slab (as defined under Tests),
averaged over those chunks, is at or under 60% within S12's 5% relative tolerance, and that the hard stop
never fired.
The record quotes no figure for the full stack: the earlier 132% came from the tiered tree and was never
re-derived, and the Balancer's estimate is 100 to 140% until T9 pins Vein Shaping and Stone Reduction
(BAL-4). The figure is recorded once T9 pins them.

### Height: veins spread evenly through the band

Nick's answer to question 1, 2026-10-27: "Same density at every height".

A vein's height is drawn uniformly over its band, for every rarity, with the box allowed to overhang the
band's floor and top and only the positions inside the band placed (ADR 0004, "Placement returns extents").
Each band then holds its density and its mix at every height: 3% at 68/25/7 in every slab of the stone
band, 6% at 60/30/10 in deepslate. The curve holds wherever a player mines. Going deeper pays through the
nodes priced for it (Deep Veins, Deep Harvest, Bedrock Communion; Shallow Grace and Highwater Mark from the
other side), not through the baseline.

The overhang is what makes "every slab" true at the band edges. With every box kept inside the band (round
1 text), boxes 2 to 4 layers tall left Y=1 and Y=245 at about 38% of the band density and Y=2 and Y=244 at
about 89%; the stone band's 5-layer top slab came out at 86% and failed the per-slab test on a correct
generator, and a 1 by 2 tunnel on bedrock got 64% of the band's ore (Balancer, BAL-15; QA-40). With the
overhang every layer is covered equally. The positions that fall outside the band are made up by the
count: a vein whose box is `h` layers tall, in a band of `H` layers, places on average `H / (H + h − 1)` of
its blocks, so each rarity's count in a band is multiplied by its edge factor, the inverse of the
block-weighted mean of `H / (H + h − 1)` over the rarity's configured sizes, a method on `OreBaseline`
computed from `VeinShape`. With mean box heights of 2.0 (rare) to 2.5 (common) that is about +0.4 to +0.6%
veins in the stone band and about +1.6 to +2.4% in deepslate. The closed form's blocks, the band densities,
the shares and the curve do not change; §3.1's vein counts stay the counts the closed form uses. The
Balancer confirms the factor.

### Which ore a vein is: reopened 2026-10-31 (BAL-16), pending Nick's answer

Question 1 was put to Nick with the consequence that, under the rule below, "some gold or redstone appears
high up". The Balancer's run of the repo's port of vanilla's `OreFeature` (round 2) shows the rule does the
opposite, so the question went back to him as question 4 on 2026-10-31. Nick's density answer is not
reopened, and no option changes the counts, the density, the shares or the curve, since Resonance is paid
by rarity and a vein's ore is picked after its rarity and its layer. This section is interim text; the
record is rewritten to his answer and only then accepted.

What is settled whichever option he picks: each classified ore carries a block-weighted height profile over
the overworld's generation range, in 24 bins of 16 layers (Y-64..319), summed over the features the
classifier uses for it (below), and the profiles are mapped onto the Vault by one linear map: the overworld
range Y-64..319 onto the expanded type's mineralizable column, Y-63..245, the same map for both dimension
types, so an ore sits at the same Vault height whichever type a team has. In the base type, whose column
starts at Y=1, the mass the map puts below Y=1 goes into the stone band's lowest 16 layers. The ore weights
(below) and the group id are also settled. Under option C the profiles are not read by the generator, but
the classifier still builds them.

The rule as first written (option A). Once a vein has its rarity and its layer, its ore is drawn from that
rarity's ores in proportion to each ore's weight × its mapped profile at the layer's bin. Where no ore of
the rarity has mass at that bin, the draw uses the nearest bin in the band that has some (the lower one on
a tie).

What that rule gives on vanilla, measured by the Balancer (derived, unplaytested), in place of the "some
gold or redstone high up" first stated. The map puts diamond's vanilla range below Vault Y=1.4, redstone's
below 0.6, gold's below 14 and buried lapis's below 40, while emerald spans Vault Y-24..245. In the base
type:

- Rare: above Y=16 only emerald has mass, so emerald is about 93.5% of rare veins and diamond about 6.5%,
  all in Y1..16, where diamond is about 99.6% of rare. About 9 diamond blocks a chunk against about 124
  emerald.
- Uncommon: above about Y=40 no uncommon ore has mass and the nearest bin with mass holds lapis only, so
  lapis is about 95% of uncommon veins, gold about 2% and redstone about 3%, those two all in Y1..16.
- Expanded type: deepslate is almost all diamond for rare and mixed for uncommon; its stone band is about
  99% emerald for rare and about 98% lapis for uncommon.

So away from the floor a player mines no diamond, gold or redstone, and the floor becomes the item
treasure layer question 1 set out to remove.

The options put to Nick (question 4), each unplaytested:

- A, as written above: diamond, gold and redstone only at the floor or in deepslate; emerald and lapis
  everywhere else.
- B, recommended by the Balancer and the Manager: half of each vein's ore pick by height, as A, and half
  by the band-wide vanilla mix (the weights alone). Base type rare comes out about 44% diamond overall, 91%
  at the floor and 41% above it; uncommon above the floor about 64% lapis, 21% redstone and 15% gold.
  Diamond still sits low, and every height yields every ore.
- C: height ignored; the band-wide mix everywhere (rare about 82% diamond, 18% emerald).

Blocked on the answer: the identity unit cases (Tests), QA-29's band-floor case, and E4-S1 (#234), which
writes the generator's pick. Nothing before T4 picks an ore.

An ore's weight is its expected blocks a chunk in the overworld × the share of the overworld's
`possibleBiomes()` that list the feature, summed over its features. An all-biome feature (step 3) counts at
1.0, since the classifier already treats it as everywhere; a biome-only feature counts at its share, about
0.18 for emerald's mountain feature on vanilla. Without the share, emerald's single mountain feature
counted every chunk as a mountain and made 55% of rare blocks emerald (Balancer, BAL-2). With it, emerald
is about 18% of the band-wide rare mix, its weight over the sum of the rare weights. That is the share of
rare veins only where height does not decide the pick (option C, and the band-wide half of option B); under
option A emerald's share of rare veins is set by height, about 93.5% above Y=16 in the base type. The share
changes only the weight used to pick an ore; the §4.2 rule classifies on the unweighted expected blocks of
step 8. On vanilla the common weights are unchanged, coal 1,108, iron 646 and copper 134, the figures §6.1
Salvager's Eye cites.

### Publication to the generator threads

The classifier's table and the profiles are built on the main thread at server start and read on the
generator's worker threads. The classifier publishes them as one immutable object through a `volatile`
field, written once, before ADR 0002's restore registers any Vault level, so no chunk of any Vault can
generate before it. The volatile write happens-before every later read, so a worker that sees the object
sees all of it. Nothing writes the field again in the session; `/reload` does not reclassify. `OreBaseline`
itself is a constant record.

### The ore group's id

Stone and deepslate variants are one ore (step 4 below). The group's id, the string every other record
stores for it (ADR 0004's vein palette, ADR 0005 section 1), is the `Identifier` string of the block the
group places in the stone band: the target whose `RuleTest` matches stone's default state. A group with no
stone-band block uses its deepslate-band block. If a group has more than one stone-band block (two features
merged it), the id is the least of them by string order. The id is a block id, not a name derived from the
grouping, so a mod that later adds a feature targeting `minecraft:iron_ore` cannot change what a stored
iron vein is (ADR 0004, "What a vein's ore matches").

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
   feature, keeps it. The share of biomes listing each feature is kept for the weight above.
4. Stone and deepslate variants are grouped: blocks targeted by the same feature belong to one ore, so a
   feature is counted once per ore, not once per target. Which variant each band places is found by
   testing each target's `RuleTest` against stone's and deepslate's default states (`RuleTest#test(BlockState,
   RandomSource)` is public, `levelgen/structure/templatesystem/RuleTest.java` line 11; `targetStates`,
   `target` and `state` are public fields of `OreConfiguration`). A single-variant ore places that block
   in both bands. The group's id is as stated above.
5. Placement fields are read by encoding each `PlacementModifier` through `PlacementModifier.CODEC` with
   `RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())`, not bare `JsonOps`, and decoding the
   field it carries: `count` (an `IntProvider`, codec `IntProviders.codec(0, 4096)`), `chance` on
   `rarity_filter`, and `height` (a `HeightProvider`, decoded with `HeightProvider.CODEC`). Registry ops are
   needed because modifiers that hold holder sets (block predicate and environment scan filters, and
   modded ones) fail to encode without them and would be passed over for the wrong reason (Developer,
   DEV-20). All three fields are codec fields (`CountPlacement.java` line 11, `RarityFilter.java` line 9,
   `HeightRangePlacement.java` lines 14–15), so no access transformer is needed. A modifier type the
   classifier does not know is passed over and logged once per type.
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
  thinnest margin and is asserted on its own. The fixture also asserts the weights: emerald's biome share
  applied, common weights 1,108, 646 and 134 within 10%.
- A gametest on a dedicated server runs the real classifier on vanilla and asserts the same eight
  classes, each ore's expected blocks within 10% of research section 2, and the features used: copper
  without `ore_copper_large`, gold without `ore_gold_extra`. A class can come out right from the wrong
  features, and the old bug was in reading the features, not in the rule, so only this test would have
  caught it.
- Unit cases in `core.gen`: the fractional roll's mean, over 10,000 seeds, is `c` within three standard
  errors, as ADR 0003's per-chunk rolls are tested; with no generation nodes, vein heights are uniform in
  the band, and every slab is within 10% relative of the band density over 81 chunks. Slabs are 16 layers
  counted up from each band's floor, and a partial top slab is merged into the one below: the stone band
  (Y1..245) has fourteen 16-layer slabs from Y1 and a top slab Y225..245 of 21 layers; deepslate (Y-63..-1)
  has two 16-layer slabs from Y-63 and a top slab Y-31..-1 of 31 layers. The lowest slab and the top slab
  are included. For a box of height `h`, every layer of the band is covered by exactly `h` of the allowed
  positions, and no position outside the band is placed or indexed; the edge factor at `h` = 1 is 1. The
  group id is the stone-band block, and the deepslate one for a group with none.
- Identity unit cases, written for the option Nick picks in question 4. Under A they are: the base type's
  below-Y=1 mass lands in the identity profile of the lowest 16 stone layers; a bin where a rarity has no
  ore mass draws from the nearest bin with mass, the lower on a tie. Under B or C they are restated in the
  same PR as his answer.
- The placer count above, the clip test above, and a test that a block with no entry pays 0.
- On a dedicated server, `/orevault density` over at least 81 generated chunks checks S12's tolerances
  and the per-slab rule against blocks the generator really wrote. The pure tests cannot catch a generator
  that writes blocks wrong; this is S12's second step (`docs/TEAM_REQUIREMENTS.md` S12).

## Consequences

- `core.gen` holds `OreBaseline` and `VeinShape`; `core.ore` holds `Rarity`, the rule, `OreTable` and
  `HeightProfile`; `ore` is a new adapter package (ADR 0001).
- `LevelCurve` depends on `core.gen`. A Balancer change to a count or size reprices the curve in the same
  commit, and the curve tests show it.
- The curve's ore an hour holds at any Y, so the §4.3 pacing check needs no height. A player who mines at
  the floor finds deep ore types there, not more ore.
- Node effects that change generation (T9) express themselves as changes to counts and sizes per rarity
  and band, or a region of the band they concentrate into, so the clip factor can be computed for them. A
  node that cannot be written that way needs this record amended first.
- Which ore a vein is by height waits on question 4. Under the rule as first written, the Balancer's
  model gives the figures in that section: diamond, gold and redstone only in the base type's lowest 16
  stone layers or in deepslate, emerald and lapis almost everywhere else. Whichever option Nick picks,
  Nick's T4 ten-minute mine looks at the mix by height.
- Veins at a band's edge are partly cut, and the count is about 0.4 to 2.4% higher than §3.1's figures to
  make up the cut blocks; placed blocks match the closed form.
- A pack whose ores are placed by a feature type that is not `OreConfiguration`-based (Mekanism's custom
  config is the known case, research section 7) generates those ores only through an override.
- The classifier's sampling costs 4,096 draws per height provider at start-up, a few hundred features in a
  large pack; well under a second.

## Rejected alternatives

Rejected by Nick's answer to question 1 (2026-10-27):

- Placing each rarity's veins by its mapped vanilla height profile (the first draft of this record, and
  question 1 option B). The floor becomes a treasure layer: about 15% ore in the base type's lowest 16
  layers against 1.7 to 2.7% above, so floor-mining levels about 7.5 times faster than the curve and the
  100 hours become about 13. Pricing the curve on the richest slab instead makes everyone who does not mine
  at the floor about 7 times slower.

Rejected:

- Holding the baseline as a density and a mix and filling toward a block budget, as old PR #153 did. S13
  rules it out, and the generated amount then depends on fill order and overlap rather than on counts the
  tests can pin.
- Per-ore counts copied from vanilla. Density would grow with the modpack, and the mix would drift past
  S12.
- One clip factor from the band's average request. The cap is on every block, and a Vein Singularity chunk
  or Deep Veins' layers can exceed 60% while the average is under it (BAL-3).
- An ore's weight from its expected blocks alone. A biome-only feature then counts every chunk as its
  biome, which made emerald over half of rare ore (BAL-2).
- Switching on height provider type, as the old classifier did. It silently drops any provider it does not
  know, which is how most of vanilla went missing.
- Encoding modifiers with bare `JsonOps`. Holder-set modifiers fail to encode (DEV-20).
- Running the placement chain (`PlacementModifier#getPositions`) on a fake context. It needs a
  `PlacementContext` over a `WorldGenLevel`, and a biome filter reads the level.
- Calling vanilla's `OreFeature` to place veins. It needs a `WorldGenLevel`, writes blocks instead of
  returning extents, and spills into neighbouring chunks (research section 5; ADR 0004).
- The midpoint of merged height ranges as an ore's depth, as old PR #153 did. It put iron at about Y=160.
- A default rarity for unclassified blocks. It is the stone-pays-5 bug.
- A group id derived from the classifier's grouping. It would change when a mod's feature joins or leaves
  a group, and every stored vein of that ore would read as foreign (Reviewer, RV-2).
- Computing shares at start-up by running the placer. It would give the same number as the test but hide a
  drift between the shape code and the curve instead of failing on it.

## Spec sections changed

Applied in the same PR: §3.1 (the 40% floor bullet: two factors set by the densest region; the weight's
biome share in "Where each ore sits"), §4.2 (the rarity classification paragraph, "over the features the
classifier uses for it"), §4.3 step 4 and §11 Ore Rarity Classification.

Applied on 2026-10-27 after Nick's answer to question 1: §3.1 "Where each ore sits" says veins are spread
evenly through each band's height and the profile picks only which ore a vein is, with a dated
`DESIGN_LOG.md` entry.

Round 2 (2026-10-31, the Writer, WP-R2-5): §3.1's per-slab sentence says slabs are counted up from each
band's floor (BAL-15); §3.1 "Where each ore sits" and `DESIGN_LOG.md` row 43 take the interim text: the
density rule as Nick chose it, which ore a vein is reopened pending question 4, the measured consequence of
the rule as first written, and the 18% emerald figure as a share of the band-wide rare mix; the "If Nick
chooses vanilla's profile" sentence is removed (WRITER-19). When Nick answers question 4, §3.1 and the log
take his answer in the same PR as this record.

## Reviews

Round 1, 2026-10-27. Matrix reviewers: developer, reviewer, qa, writer; the balancer reviewed because the
record holds the baseline's numbers. Rulings are the Manager's triage of the same date.

- Developer: approve with comments. Checked `CountPlacement.CODEC` line 11, `HeightRangePlacement.CODEC`
  lines 14 to 15, `RarityFilter` lines 9 to 10, `HeightProvider#sample` line 19, the public
  `WorldGenerationContext` constructor, `Biome#getGenerationSettings` lines 207 to 208,
  `ScatteredOreFeature` line 23. DEV-20 (encode with registry ops): fixed in step 5.
- Reviewer: approve with comments. Checked one pure record with derived figures; counts per rarity; one
  clip factor per band in the snapshot; the codec path needing no access transformer; the access
  transformer caveat; no default rarity; the gametest that would have caught the old bug. RV-19 (publish
  the table and profiles safely to the generator threads): fixed, one `volatile` write before any Vault
  registers. RV-2's group-id part (merged M8): fixed, the group id is the stone-band block id.
- QA: approve with comments. Checked the baseline record, the 81-chunk placer test and its S12 tolerances,
  the clip test, the classifier steps and the Tests section against S12, S13 and old #15. QA-27 (a class
  can be right from the wrong features): fixed, the gametest asserts expected blocks and the features
  used. QA-28 (tests stop at the pure placer): fixed, `/orevault density` over 81 chunks on a dedicated
  server is in Tests. QA-29 (unit cases for the roll, the clamp, the empty bin): fixed for option A; the
  band-floor case is the per-slab check, written for Nick's answer of 2026-10-27 (same density at every
  height). Round 2 reopened the identity half (question 4).
- Writer: request changes. Checked counts, sizes and band heights against §3.1; the height map and the
  16-layer clamp against §3.1 "Where each ore sits"; §4.3 step 4; the research doc's numbering note.
  WRITER-7 (§4.2's "every overworld feature" is the rule this record rejects): fixed in Context. WRITER-1
  (pointer into the session folder): fixed in Spec sections changed.
- Balancer: request changes. Checked §3.1's counts against density and mix (stone 3.04%, 68.0/24.9/7.0;
  deepslate 6.2%, 59.8/30.1/10.1), band heights, mean blocks and Resonance a vein, the clip formula, the
  height map and clamp, the classifier weights, the 132% figure and the 37 split. BAL-1 (the height map
  breaks the curve for anyone who picks a Y): written as Nick's question 1 option A, which he chose on
  2026-10-27; question 1 option B is under Rejected by his answer. BAL-2 (emerald over half of rare):
  fixed, the weight takes the biome share; all-biome features count at 1.0, as the Balancer wrote, so the
  common weights §6.1 cites stand.
  BAL-3 (the clip from the band average): fixed, `min(1, 0.60 / densest)`, still one factor per band, and
  the clip test checks every slab. BAL-4 (132% never re-derived): fixed, the test asserts the request is
  above 0.9 and the record quotes no figure. BAL-5 is ADR 0004's; BAL-6 is ADR 0003's.

Round 2, 2026-10-31. Every role reported its round 1 items resolved or its rebuttal accepted; the new
items were ruled by the Manager's round 2 triage (two rounds done, no round 3; each role confirms its named
lines after this pass).

- Developer: approve. Checked DEV-20's registry ops in step 5 and the codec lines; nothing new.
- Reviewer: approve with comments. Checked the group id against ADR 0004's palette. RV2-3 (one ore's veins
  can carry two palette ids) is answered in ADR 0004 "What consumers get"; this record's group-id rule is
  unchanged.
- QA: request changes. Checked QA-27 to QA-29 and the per-slab test against the placement. QA-40 (the
  per-slab test fails on a correct generator at the band edges): fixed with BAL-15, slabs counted from each
  band's floor with the partial top slab merged, and the overhang placement. Comment on the fractional
  roll: fixed, 10,000 seeds within three standard errors, as ADR 0003.
- Writer: request changes. Checked the record against §3.1 and the log, and grepped for open-question
  wording. WRITER-19 (stale "waiting for Nick" text at Spec sections changed and in two review lines):
  fixed in the past tense with the date; the identity half points to question 4. WRITER-23 (two meanings
  of "all-biome") is spec text, the Writer's; step 3 here is the definition it points to. WRITER-24 (a
  consequence stated wrongly in three documents): merged into BAL-16; the interim text replaces it here.
- Balancer: request changes. Checked the counts, sizes, weights and the 1.0 all-biome weight (confirmed)
  and ran the port by height. BAL-15 (a) and (b): fixed, the per-slab test counts from each band's floor,
  and the placer's overhang with the edge factor keeps every layer flat; the Balancer confirms the scaling
  line. BAL-16 (ore identity by height leaves the stone band almost without diamond, gold and redstone): to
  Nick as question 4 (Manager's triage section 3); the interim text above states his density answer, the
  reopened identity question, the measured consequence and the options.
- Architect (cross-read): approve with comments. ARCH-R2-5 (stale line): fixed with WRITER-19.

Still owed before acceptance: Nick's answer to question 4, the identity section and its unit cases
rewritten to it, and the matrix reviewers' confirmation of that text.
