# 0009. Baseline ore as vein count, size and height profile, and the sampling classifier

Status: accepted 2026-10-31 (E0-S7, #182), after the round 2 verdicts below and Nick's answer to question 4
the same day; proposed 2026-10-27, for T4. The height placement below is Nick's answer to question 1 on
2026-10-27: same density at every height. Which ore a vein is by height was reopened on 2026-10-31 (BAL-16),
because the consequence he was told for it was wrong, and put to him again as question 4. He answered
option A, the rule as first written, on the corrected consequence; the section on it below states that
answer. Spec: §3.1 Baseline ore distribution, §4.2 rarity classification, §4.3 step 4, §11 Ore Rarity
Classification. Research: `docs/research/vanilla-ore-generation.md` sections 2, 3 and 5.

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

The stone band bounds and slabs below (Y1..245, Y0..245, Y225..245, Y224..245) are superseded 2026-11-21 by the stone band at Y1..95 and Y0..95 (amendment at the end).

`OreBaseline` holds, for each band (stone, deepslate) and each rarity (common, uncommon, rare): veins per
chunk as a `double` (counts are fractional, as vanilla's are), and the configured size range (minimum and
maximum vanilla `size`, drawn uniformly per vein). It also holds the gravel and clay blob counts and size
per band. The values are §3.1's; the Balancer owns them, and a change to them is a §3.1 change in the same
PR. The band heights it divides by (245 layers of stone, 63 of deepslate) are the third copy of the
dimension heights (`CLAUDE.md` rule 8): the layer config's load check compares them too, so the three fail
together.

The stone band is every stone layer of the type's stack: Y1..245 in the base type (245 layers) and Y0..245 in
the expanded type (246), where Y=0 is stone (§3.1). The bounds are per type, on `VaultVariant`, and the load
check compares each type's own figure. `OreBaseline`'s counts and the 245 above stay one set, the base
type's, and the curve reads them. The expanded type spreads the same stone-band counts over 246 layers, about
0.4% thinner, well inside S12's 5%; the edge factor takes that band's `H`. Without this Y=0 would be a flat
layer no vein reaches, between two ore bands that §3.1 says nothing divides, and the height map below already
counts it in its column Y-63..245.

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

The closed form ignores overlap between veins: a later vein takes an earlier one's positions, so placed blocks come out
about 1.7% under it in the stone band and about 3.3% in deepslate (modelled by the Reviewer for E4-S1b; §3.1 "Veins per
chunk"). A JUnit test
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

Superseded 2026-11-21 by Nick's playtest of #422: each ore picks its own height (amendment at the end).

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
computed from `VeinShape`. With block-weighted mean box heights of 2.0 (rare) to 2.6 (common) that is about +0.4 to +0.65%
veins in the stone band and about +1.6 to +2.5% in deepslate (Balancer, BAL-E4-5). The closed form's blocks, the band densities,
the shares and the curve do not change; §3.1's vein counts stay the counts the closed form uses. The
Balancer confirms the factor.

### Which ore a vein is: by height, as first written

Superseded 2026-11-21 by Nick's playtest of #422: each ore picks its own height (amendment at the end).

Nick's answer to question 4, 2026-10-31: option A, "as written". Question 1 had been put to him with the
consequence that, under this rule, "some gold or redstone appears high up". The Balancer's run of the
repo's port of vanilla's `OreFeature` (round 2) showed the rule does the opposite, so the question went back
to him with the measured consequence below, and he kept the rule. The recommendation was option B; he chose
A over it. His density answer was not reopened, and the identity rule changes none of the counts, the
density, the shares or the curve, since Resonance is paid by rarity and a vein's ore is picked after its
rarity and its layer.

The profiles and the map. Each classified ore carries a block-weighted height profile over the overworld's
generation range, per layer (Y-64..319; amended 2026-11-20, below), summed over the features the classifier uses for it
(below), and the profiles are mapped onto the Vault by one linear map: the overworld
range Y-64..319 onto the expanded type's mineralizable column, Y-63..245, the same map for both dimension
types, so an ore sits at the same Vault height whichever type a team has. In the base type, whose column
starts at Y=1, the mass the map puts below Y=1 goes into the stone band's lowest 16 layers. The ore weights
and the group id are below.

The rule. Once a vein has its rarity and its layer, its ore is drawn from that rarity's ores in proportion
to each ore's weight × its mapped profile in the layer's slab. Where no ore of the rarity has mass in that
slab, the draw uses the nearest slab in the band that has some (the lower one on a tie). Nothing in the pick
reads the band-wide mix on its own.

What the rule gives on vanilla, measured by the Balancer (derived, unplaytested), in place of the "some
gold or redstone high up" first stated. The map puts diamond's vanilla range below Vault Y=1.4, redstone's
below 0.6, gold's below 14 and buried lapis's below 40, while emerald spans Vault Y-24..245. In the base
type:

- Rare: above Y=16 only emerald has mass, so emerald is about 93.5% of rare veins and diamond about 6.5%,
  all in Y1..16, where diamond is about 99.6% of rare. About 9 diamond blocks a chunk against about 124
  emerald.
- Uncommon: above about Y=40 no uncommon ore has mass and the nearest slab with mass holds lapis only, so
  lapis is about 95% of uncommon veins, gold about 2% and redstone about 3%, those two all in Y1..16.
- Expanded type: deepslate is almost all diamond for rare and mixed for uncommon; its stone band is about
  99% emerald for rare and about 98% lapis for uncommon.

So away from the floor a player mines no diamond, gold or redstone. A player who wants diamonds
strip-mines the stone band's lowest 16 layers, or the deepslate band of an expanded Vault: the floor is
where those items are, though not where more ore is, since density and Resonance an hour are the same at
every height. Nick chose A on this consequence. E4-S1 (#234) writes the pick, and Nick's T4 ten-minute
mine is the first look at it in play.

An ore's weight is its expected blocks a chunk in the overworld × the share of the overworld's
`possibleBiomes()` that list the feature, summed over its features. An all-biome feature (step 3) counts at
1.0, since the classifier already treats it as everywhere; a biome-only feature counts at its share, about
0.18 for emerald's mountain feature on vanilla. Without the share, emerald's single mountain feature
counted every chunk as a mountain and made 55% of rare blocks emerald (Balancer, BAL-2). With it, emerald
is about 18% of the band-wide rare mix, its weight over the sum of the rare weights. That is not its share
of rare veins: height decides the pick, so in the base type emerald is every rare vein above Y=16 and about
93.5% of all rare veins. The share changes only the weight used to pick an ore; the §4.2 rule classifies
on the unweighted expected blocks of step 8. On vanilla the common weights are unchanged, coal 1,108,
iron 646 and copper 134, the figures §6.1 Salvager's Eye cites.

### Publication to the generator threads

The classifier's table and the profiles are built on the main thread at server start and read on the
generator's worker threads. The classifier publishes them as one immutable object through a `volatile`
field, written once, before ADR 0002's restore registers any Vault level, so no chunk of any Vault can
generate before it. The volatile write happens-before every later read, so a worker that sees the object
sees all of it. Nothing writes the field again in the session; `/reload` does not reclassify. The field is cleared at
`ServerStoppedEvent`, so a second world in one client classifies its own pack. `OreBaseline` itself is a constant
record. A feature the classifier cannot read (a size below 1, a provider or rule test that throws) is passed over with a
WARN; an exception that still escapes the classifier is a bug, and it stops the server start rather than let any Vault
be restored or generate without a table (E4-S2 review, S2-AR-1).

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
   When that overworld's biome source has a single biome, or the classification it gives leaves some rarity with
   no ore, the classifier classifies against the `minecraft:normal` world preset's overworld instead, with that
   preset's dimension type for its heights, and the result replaces the first (Manager's triage ruling 6, amended
   2026-11-20 below). The preset is read from the server's own registries, so its biomes are the same holders and
   biome modifiers still reach them.
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
6. Heights are sampled, not switched on by provider type: 4,096 draws from a seed fixed by the feature's id
   (FNV-1a 64 over its UTF-8 bytes, `core.ore.SamplingSeed`), with
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
`RarityFilter.chance`, never transformed, is private. That tree carries those transformers, so field visibility
cannot be read from it. `javap -p` on the compiled 26.1.2.95 jar settles it (E1-S5, #195;
`docs/research/neoforge-26.1-sources.md`, "The five members, in a clean 26.1.2.95"): `count`, `height`,
`minInclusive` and `maxInclusive` are private, as `chance` is, and NeoForge widens none of them. So the fallback
above needs its transformers as listed, and the codec path does not depend on them. The `#c:ores` key is
`Tags.Blocks.ORES` (`Tags.java` line 201), not its item twin `Tags.Items.ORES`.

### A block that is not a classified ore pays nothing

`OreTable` answers a lookup with the ore's entry or nothing (`@Nullable`); there is no default rarity.
`mining` pays Resonance, counts an ore for any node, or indexes it, only when the broken block has an
entry. Both variants of a grouped ore have one. Stone, deepslate, gravel, clay and a `#c:ores` block
with no overworld feature have none. This is old PR #117's fix made the shape of the API, so the bug
cannot come back through a default.

### Tests

The stone band bounds and slabs below (Y1..245, Y0..245, Y225..245, Y224..245) are superseded 2026-11-21 by the stone band at Y1..95 and Y0..95 (amendment at the end).

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
  counted up from each band's floor, and a partial top slab is merged into the one below: the base stone band
  (Y1..245) has fourteen 16-layer slabs from Y1 and a top slab Y225..245 of 21 layers; the expanded stone band
  (Y0..245) has fourteen from Y0 and a top slab Y224..245 of 22 layers; deepslate (Y-63..-1)
  has two 16-layer slabs from Y-63 and a top slab Y-31..-1 of 31 layers. The lowest slab and the top slab
  are included. For a box of height `h`, every layer of the band is covered by exactly `h` of the allowed
  positions, and no position outside the band is placed or indexed; the edge factor at `h` = 1 is 1. The
  group id is the stone-band block, and the deepslate one for a group with none.
- Identity unit cases in `core.ore`, for Nick's answer to question 4 (option A): the base type's below-Y=1
  mass lands in the identity profile of the lowest 16 stone layers (QA-29's band-floor case); a slab where
  a rarity has no ore mass draws from the nearest slab with mass, the lower on a tie; and on the golden
  fixture's profiles, a base-type rare pick at any layer above Y=16 (Y17..245) is emerald with
  probability 1.
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
- Which ore a vein is follows height (Nick, question 4, 2026-10-31). The Balancer's model gives the
  figures in that section: diamond, gold and redstone only in the base type's lowest 16 stone layers or in
  deepslate, emerald and lapis almost everywhere else. A player who wants diamonds strip-mines the floor,
  though the curve pays no more there. Nick's T4 ten-minute mine looks at the mix by height.
- Veins at a band's edge are partly cut, and the count is about 0.4 to 2.5% higher than §3.1's figures to
  make up the cut blocks; placed blocks then match the closed form less the overlap (about 1.7% stone,
  3.3% deepslate), inside S12.
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

Rejected by Nick's answer to question 4 (2026-10-31), "as written", each figure the Balancer's, derived and
unplaytested:

- Half of each vein's ore pick by height and half by the band-wide vanilla mix (question 4 option B,
  recommended by the Balancer and the Manager). Base type rare would come out about 44% diamond overall,
  91% at the floor and 41% above it; uncommon above the floor about 64% lapis, 21% redstone and 15% gold.
  It was recommended because it keeps diamonds deeper without making the floor the only place worth
  mining for them (Manager's round-2 triage, section 3). Nick chose A over the recommendation.
- Height ignored, the band-wide vanilla mix everywhere (option C): rare about 82% diamond and 18% emerald
  at every height. It drops "diamonds are deeper" altogether, which B was put forward to keep.

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
band's floor (BAL-15); §3.1 "Where each ore sits" and `DESIGN_LOG.md` row 43 took interim text: the
density rule as Nick chose it, which ore a vein is reopened pending question 4, the measured consequence of
the rule as first written, and the 18% emerald figure as a share of the band-wide rare mix; the "If Nick
chooses vanilla's profile" sentence is removed (WRITER-19).

After Nick's answer to question 4 (2026-10-31, the Writer): §3.1 "Where each ore sits" states the identity
rule as first written, with its consequence for the player, in place of the interim text; `DESIGN_LOG.md`'s
2026-10-27 emerald row and the 2026-10-31 question 4 row record the answer, with options B and C rejected.
Same PR as this record.

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
  density half of the band-floor case is the per-slab check, written for Nick's answer of 2026-10-27 (same
  density at every height). Round 2 reopened the identity half (question 4), which Nick answered A on
  2026-10-31; its case, the base type's below-Y=1 mass landing in the lowest 16 stone layers, is in Tests
  as QA-29's band-floor case.
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
  consequence stated wrongly in three documents): merged into BAL-16; the measured consequence replaces it here.
- Balancer: request changes. Checked the counts, sizes, weights and the 1.0 all-biome weight (confirmed)
  and ran the port by height. BAL-15 (a) and (b): fixed, the per-slab test counts from each band's floor,
  and the placer's overhang with the edge factor keeps every layer flat; the Balancer confirms the scaling
  line. BAL-16 (ore identity by height leaves the stone band almost without diamond, gold and redstone): to
  Nick as question 4 (Manager's triage section 3), answered A on 2026-10-31; the identity section above
  states his answer and the measured consequence, and B and C are under Rejected alternatives.
- Architect (cross-read): approve with comments. ARCH-R2-5 (stale line): fixed with WRITER-19.

Question 4, 2026-10-31: Nick answered A, "as written", over the recommended B. The identity section, its
unit cases (QA-29's band-floor case among them) and the Consequences line are rewritten to that answer,
BAL-16 is closed by it, and B and C are under Rejected alternatives.

Amended 2026-10-31 by E1-S5 (#195), from `docs/research/neoforge-26.1-sources.md`: the placement fields are private
in a clean 26.1.2.95, so the fallback's AT lines stand, and the tag key is `Tags.Blocks.ORES`. Nothing else changed.

Amended 2026-11-01 for E3-S5 (#233) (Architect): the expanded stone band is Y0..245, 246 layers ("One pure record", Tests). The generator's stack was right and this record was short by one layer; a load check written from the old text would have failed the correct expanded stack. The Balancer confirms the 0.4% in E4-S1 (#234). If it is refused, the one alternative is the expanded count × 246/245, the same bounds. The Writer adds the expanded top slab to §3.1's per-slab sentence, in E4-S1 at the latest.

Amended 2026-11-20 for E4-S1b (#234), from the Epic 4 plan's reviews and the Manager's triage (Architect, Balancer):
- Profiles are held per overworld layer, not in 24 bins of 16. Vanilla ranges end on bin edges (diamond at 16, gold at
  32, buried lapis at 64), and binning spread a sliver of each over the next 12.9 Vault layers: in the expanded type's
  stone band rare came out about 97% emerald against the 99% above, and some gold appeared above Y=16 in the base type
  (BAL-E4-4). Each overworld layer's mass is spread over the stretch it maps to and split between slabs by exact
  overlap. Memory only, so no save shape changes.
- "The layer's bin" is the 16-layer slab counted up from the band's floor, the top partial slab merged, the same slabs
  as the per-slab test (ARCH-E4-5, BAL-E4-3). A vein's layer is its rolled box's middle layer, `minY + (sizeY - 1) / 2`
  before the cut, clamped into the band; with it diamond is about 6.8% of the base type's rare veins rather than 6.5%,
  and about 6.5% by placed blocks.
- The edge factor averages `H / (H + h - 1)` weighted by blocks, with `h` the block-weighted box height of each size;
  the figures in "Height" are corrected to +0.4 to +0.65% and +1.6 to +2.5% (BAL-E4-5).
- A pack with no ore of a rarity: that rarity's veins keep their count and size and take their ore from the nearest
  rarity that has ores (rare from uncommon, then common; uncommon from common, then rare; common from uncommon, then
  rare), so density does not change. Resonance per ore falls, about 15% with no rare ore and 22% with no uncommon; the
  curve is not re-derived for it (Manager's triage ruling 5, confirmed by the Balancer). The classifier logs a WARN
  naming the empty rarity (E4-S2).
- Where no ore of a rarity maps into a band at all, though the pack has some, every slab of that band draws the rarity's
  ores by weight alone. Vanilla never reaches it.

Amended 2026-11-20 for E4-S2 (#238) (Developer, for the Architect to confirm), from the Manager's triage ruling 6 and its
round 2 conditions:
- A non-noise overworld. A skyblock or superflat overworld lists one biome or none, which would classify almost nothing
  and bring back the empty Vault. When the server's overworld's biome source has a single biome (or none), or its
  classification leaves a rarity with no ore, the classifier classifies against the `minecraft:normal` preset's
  overworld (`WorldPresets.NORMAL`, its `LevelStem`'s generator, and heights from that stem's dimension type), logs a
  WARN naming the reason and the source, and the result replaces the first; it is not merged with it. If the preset is
  missing, the classifier logs an ERROR and keeps the server's own overworld, never crashes. A rarity still empty after
  that is a WARN naming it (triage ruling 5 covers its veins); no ore at all is an ERROR. The published record names its
  source. The gametest classifies the preset's overworld directly and checks the published source when the gametest
  server's overworld has one biome.
- The sampling seed is a fixed function of the feature id (step 6), and biomes and features are iterated sorted by id, so
  every start gives the same table (Epic 4 triage, DEV-E4-5, RV-E4-6).
- Placement modifiers are encoded through `RegistryOps` over `JsonOps` (step 5) in a method generic in the element type,
  so the `ore` package names no JSON class (ADR 0001's `ore` row allows Minecraft and `core` only).
- What is passed over, each logged at WARN: an unnamed placed feature (no id to seed from), a feature with no
  `height_range` modifier, a `minecraft:ore` feature whose size is past the vein shape's table (size 40), and a feature
  whose modifier does not encode or whose count, chance or height does not decode. An unknown modifier type is passed over
  and the feature kept, logged once per type (step 5). A feature that expects no blocks (every height draw outside the
  overworld) is not usable for step 9. A target outside `#c:ores` is not an ore, and a feature with no target in the tag
  is not read further.
- A group none of whose targets matches stone or deepslate takes its least block id as its id and places it in both
  bands, as a single-variant ore does.
- Overrides (§10): an entry naming a block of a grouped ore moves the whole group; two entries naming one ore with two
  rarities apply neither, and a block named twice with two rarities is rejected at parse. An override naming an ore with
  no feature data takes the median weight of the feature-backed ores of its rarity, or of all of them when its rarity has
  none, or 1 when there are none. The config's default is no override: §10's former default, `minecraft:diamond_ore=rare`,
  would hide a classifier that got diamond wrong, and stays in the comment as the example.

Amended 2026-11-21 for E4-S3 (#247) (Developer, for the Architect and Balancer to confirm), from the Epic 4 plan's S3 points:
- The clip factors are built by `ClipFactors.of(OreBaseline, VaultVariant, GenerationTiers)` when the snapshot is built
  (`GenSnapshots`, both `fill` and `rebuild`), with `GenerationTiers.NONE` until E9-S3 hands it the team's tiers
  (ARCH-E4-16), and travel in `GenSnapshot.clip`. `densest` is computed layer by layer: each request's closed-form
  density (count × mean blocks over its region's volume, before the edge factor, which only makes up cut blocks) is added
  to every layer of its region, and the band's factor is `min(1, 0.60 / ` the densest layer`)`. A region a node
  concentrates ore into is therefore counted on top of the band-wide veins. With no generation node both factors are 1.
- The clip test runs on synthetic requests, the deepslate band asking for 90%, 100% and 132% (QA-E4-2; the full stack moves
  to the T9 ticket that pins Vein Shaping and Stone Reduction, and #247's criterion is amended to say so). It asserts the
  factor is `0.60 / asked`, that the clipped requests keep the mix exactly (one factor per band), that the clipped closed
  form sits on 60%, that every deepslate slab placed over 81 chunks is at or under 60% within 5% relative, that the hard
  stop never fires with the clip, and that it fires in every chunk without it (S3-QA-1). It prints the largest per-chunk
  request over the ceiling and its spread.
- The hard stop counts requested blocks inside the band, before overwrites (triage, Hard stop), against
  `ClipFactors.CEILING × ClipFactors.HARD_STOP_HEADROOM`, the headroom 1.10 (S3-RV-1, S3-BAL-1; Manager, S3 round 1). At
  exactly 60% a correct clip puts the mean request on the stop, so it would fire in about half of clipped chunks and take
  about 6% of deepslate rare there, a second clip aimed at the rarity placed last. At 1.10 it is about seven standard
  deviations out (derived, sigma about 1.4% of the request); if the printed spread is above 2%, the headroom becomes
  1 + 5 sigma and the figure is recorded here. Placed ore after overlap stays at most about 48%. The placement records the
  band and rarity of the first refusal, and the generator logs them once a session. ADR 0004's printed (non-gate) "both
  bands at 60%" bounds rise by at most 10%; the gate, the pinned full stack, is held by the clip and unaffected.
- The mix by placed blocks shifts toward rare at high density, because later veins overwrite earlier ones and rare is
  placed last: about 53/34/13 by placed blocks for a 60/30/10 request at 60%, and 59.3/30.5/10.3 in deepslate at the
  baseline (derived, random overlap; Balancer, S3 review). The clip's contract is one factor per band, so the mix is held
  and tested on the requests; §3.1 states the placed drift.
- Publication: `VaultChunkGenerator.forVault` reads the published record and throws when there is none, and
  `VaultDimensions.restoreAll` first runs `VaultChunkGenerator.checkOreInputs()`, which reads the record and resolves both
  types' ore inputs, before touching any Vault and outside its per-team catch (S3-AR-1), a gametest showing it throws with
  no table (S3-QA-4), so the exception leaves
  `ServerStartedEvent` and the server does not start, as for a classifier failure (S2-AR-1). Since the classifier stops the
  start when it fails, this is reachable only through a bug in the mod. A pack with no classified ore at all publishes
  empty pickers, and its Vaults get gravel and clay and no ore (the classifier has logged an ERROR).


Amended 2026-11-21 after Nick's playtest of the generator (#422) and his choice of a surface at Y=100 the same day
(Balancer, for the Architect to confirm). The figures are the Balancer's, closed form from the counts and the repo's port
of vanilla's `OreFeature`, derived and unplaytested; the script is `docs/research/vanilla-ore/heights.py` and its output
`heights.txt`.

- What Nick saw. Lapis at Y=220. That was question 4's option A working as written: each rarity's veins were spread
  evenly over the band and a vein's ore was picked by its height, with the nearest slab's ores where none of the rarity
  had mass, so uncommon veins above about Y=40 were about 95% lapis and rare veins above Y=16 were emerald.
- Each ore picks its own height (Nick, 2026-11-21, on a card of four options). It supersedes question 4 and the
  sections "Height: veins spread evenly through the band" and "Which ore a vein is: by height, as first written". A
  vein's rarity comes from the band's count, unchanged. Its ore is drawn next from that rarity's ores in proportion to
  weight × the ore's profile mass inside the band (the band-wide mix per rarity, read inside the band; nothing else
  reads it). Its box middle layer is drawn last from that ore's profile inside the band, by inverse cumulative mass
  per layer over one draw; the box may overhang the band and only its blocks inside are placed, as now. The draw
  order becomes size index, size fraction, shape, origin x, origin z, the ore's `nextDouble`, then the middle layer's
  `nextDouble`: today's layer draw, `nextInt(regionLayers + sizeY - 1)`, which `SplittableRandom` may serve with more
  than one seed for a bound that is not a power of two, becomes a `nextDouble` drawn after the ore's. The golden
  draw-sequence test is re-pinned (ARCH-E4-7). What holds is that the same pack and the same chunk always give the same
  veins. A change of pack can move any vein in chunks generated after it: the edge factor now depends on the pack's
  profiles and multiplies the count, and `VeinPlacer.rollCount` turns a changed fraction into a changed vein count,
  which shifts every later draw in the chunk (Architect, A2). The earlier claim that a pack's ores never move a vein's
  column is withdrawn.
- Edge factor. Drawing the middle from the profile does not remove it: any ore with mass at a band's floor or top
  loses the box's overhang there. It stays one factor per band and rarity, now computed from the profiles: the
  blocks the rarity's ores keep inside the band, mixed by their pick shares. Under 1% in the stone band (base type: common
  0.55%, uncommon 0.28%, rare 0.65%; expanded 0.54%, 0.27%, 0.63%) and at most about 2.6% in deepslate (common, whose iron peaks at the band's top). A factor
  per ore is not needed: the largest difference between an ore's kept share and its rarity's is 4.4%, on copper and
  emerald at the deepslate top, where emerald is 0.1% of rare. `OreBaseline.edgeFactor`'s uniform form
  `H / (H + h - 1)` is replaced; it reads the pickers' profiles, built on the main thread and published with them.
- The vertical kernel (D2). The edge factor, `ClipFactors.densest` and the expected slabs all need a vein's blocks by
  layer about its box middle, not only the scalar `VeinShape.blockWeightedBoxHeight`. A new generated table,
  `VeinShapeTables.LAYER_KERNEL`: per configured size, the mean blocks at each offset from the box middle
  `y0 + (sizeY - 1) / 2`, written by the same fixed-seed run of the port that writes `MEAN_BLOCKS` and checked by
  `VeinShapeTablesTest#theTablesMatchAFreshRun`. It is the one input of all three. The placer's box is the extent of the
  placed blocks (`VeinShape.Rolled.of`), so its middle is the middle of the blocks' extent, which is how
  `heights.py`'s kernel is defined; the figures here stand on it. A slab's expected placed blocks are the closed form,
  profile × kernel summed over the slab, times one minus the band's overlap (about 1.7% in stone, 3.3% in
  deepslate), so the slab check and the band check use one overlap figure.
- Mass outside a band. Dropped, in both types. An ore with no mass in a band is never picked there. Where no ore of a
  rarity has mass in a band though the pack has some, that rarity's veins draw by weight alone and spread evenly
  through the band (the existing fallback, now also for height). A pack with no ore of a rarity keeps the
  nearest-rarity rule of 2026-11-20. `foldBelow` is retired with the old column: it existed to fold the map's mass below
  Y=1 into the base type's lowest slab, and at vanilla's own Y that mass is the deepslate band's.
- Requests with a region of their own (D1, W1; Manager's ruling, 2026-11-21). A request over part of a band, such as
  Deep Veins' lowest 30 layers, Stratified's bands, Guide Vein's veins or any T9 request, draws its ore by weight × the
  ore's profile mass inside the region, and its box middle from that ore's profile cut to the region and renormalised.
  An ore with no mass in the region draws its middle uniformly in the region; where no ore of the rarity has mass in
  it, the ore is drawn by weight alone. The box may overhang the region into the band; only the band's edges cut it.
  `VeinRequest`'s contract says so, and `ClipFactors.densest` sums each request by the same rule. Two consequences,
  derived (`heights.txt`, last section):
  - Deep Veins is restated as a request: tier 1 adds, per rarity, 0.5 × the band's count × 30 / the band's layers
    veins in the lowest 30 layers of the band above bedrock; tier 2, 1.0 ×. That is the old "1.5× (2×) the band's
    density" as an addition, now on top of a baseline that is already densest there. Base Y1..16: 6.1% at tier 1
    (2.88 times the curve's rate), 7.7% at tier 2 (3.50). Deepslate Y-63..-48: 7.9% (3.65) and 11.1% (4.90). T9
    (E9-S15, #320) pins the count and the 30 layers, now 32% of the base stone band.
  - Stratified under this reading changes which ores a Vault has: base, tier 1, no blend, rare about 89% diamond,
    uncommon about 93% lapis with no redstone (redstone lies only in Y1..15, inside the rare band), common about 74%
    coal, 23% copper and 3% iron. The §6.1 note says so; T9 redesigns its bands or states the cost. The note it
    replaces ("mostly pulls emerald down") held only if heights were uniform in a region, which is not the rule.
- Measured first on the old column, the rule failed Nick's 2026-10-27 answer. With stone to Y=245 and the overworld
  stretched onto Y-63..245, the base type's lowest 16 layers came out 15.9% ore (68% uncommon, 17% rare), 8.7 times the
  curve's rate, about 11.5 hours for the 100: the rarity-placed-by-profile floor rejected on 2026-10-27 (about 15%,
  13 hours) returns, because a rarity's veins placed by its ores' profiles are placed by the rarity's profile.
- Nick then asked, the same day, for the Vault to start at about Y=64 or Y=100 and grow deeper, and chose Y=100 from
  this comparison (each with the rule above and §3.1's densities, 3% stone and 6% deepslate, held):

  | | Old column shifted down (min_y -192) | Surface Y=100 (chosen) | Surface Y=64 |
  |---|---|---|---|
  | Stone band, base / expanded | Y-191..53 / Y-192..53 | Y1..95 / Y0..95 | Y1..59 / Y0..59 |
  | Height map | linear, as now | vanilla Y, 1:1 | vanilla Y, 1:1 |
  | Grass to the diamond layers (top of Y1..16) | about 234 blocks | 84 | 48 |
  | Base lowest slab, ore and rate × the curve | 15.9%, 8.7 | 4.6%, 2.25 | 3.7%, 1.73 |
  | Curve's 100 h mining only there | about 11.5 h | about 44 h | about 58 h |
  | Best 1 by 2 tunnel, rate and hours | 9.1, 11 h | 2.95, 34 h | 2.27, 44 h |
  | Iron share of common veins, base | 34% | 17% | 27% |
  | Stone-band counts per chunk | unchanged | × 95/245 | × 59/245 |

  The shift keeps every count and test but changes only the numbers on the F3 screen: the map stays linear, since
  245 layers of stone cannot hold vanilla's -64..319 one to one, so diamonds stay at the bottom and the floor stays at
  8.7 times. The Balancer recommended Y=64 as nearer and flatter; Nick chose Y=100.
- The floor at Y=100, accepted. Base type by slab: Y1..16 4.57% (28/58/14, 2.25 times the curve's rate), Y17..32 2.95%
  (0.90), Y33..48 2.74% (0.74), Y49..64 2.84% (0.79), Y65..95 2.56% (0.65). The best 1 by 2 tunnel, Y2..3, is 2.95
  times. Mining only Y1..16 takes the 100 hours in about 44 (32 for a team of five against its 71); the tunnel in about
  34 (24). Nick was told the floor pays about 2.3 times with no mitigation and that it would stand unless he asked for it
  flatter; he did not ask, so it stands, as the deepslate band's 2.33 times average was accepted for Vault Expansion.
  T14 measures both. The deepslate band is 4.7%, 3.7% and 8.3% by slab under 1:1 (2.4, 1.9 and 2.5 times).
- The layout. Base: bedrock Y=0, stone Y1..95, dirt Y96..99, grass Y=100, air Y101..319. Expanded: bedrock Y=-64,
  deepslate Y-63..-1, stone Y0..95, the same above. The dimension type files keep `min_y` and `height`; the layer stacks
  (`vault_layers/*.json`, `LayerStack`'s built-ins), `VaultVariant.STONE_TOP_Y` (95) and
  `OreBaseline.STONE_BAND_LAYERS` (95) change together (rule 8), and the stone-band counts become 54.67, 31.80 and 12.80
  veins, gravel 3.49 and clay 1.16 blobs, stored in the exact form, `141 * 95 / 245.0` and so on, with the 245-layer
  originals in a comment, so §4.3's 3.04% stays exact. `OrePickers` maps the profiles at vanilla's Y (column -64..319 onto itself);
  a pack whose overworld is taller or deeper than vanilla's maps at its own Y as well, and what lies outside a band is
  dropped. `SafeArrival.DEFAULT_FEET_Y` becomes 101, and a test pins it to the first air
  above the grass of `LayerStack`'s built-in base stack, so it cannot drift from the stack. The expanded type spreads the base counts over 96 stone layers, 1.0% thinner, inside S12's 5%.
- Tests. S12's per-slab rule changes from "within ±10% of the band's density" to "within ±10% of the slab's expected
  density, and rarity shares within ±4 points of the slab's expected shares", the expected figures computed from the
  pickers' profiles by the same code the placer reads, over 81 chunks. At Y=100 the largest slab standard deviation over
  81 chunks is about 3.4% of its expected blocks (the expanded type's Y64..79; 3.2% in the base type), so ±10% is about
  three. The shares are 4 points, not 3 (#425 review, B-1): over 81 chunks the common and uncommon shares of base
  Y17..32 spread by 1.2 to 1.3 points, so ±3 was about 2.2 standard deviations, and a Monte Carlo of a correct generator
  failed a slab in about 1 fresh-Vault run in 16 to 20; ±4 is about three. Band density ±5% and band shares ±2 points
  stand. Slabs: base Y1..16, 17..32, 33..48, 49..64 and 65..95 (31 layers, merged); expanded Y0..15 to Y80..95, six;
  deepslate unchanged. The identity unit cases of question 4 (a rare pick above Y=16 is emerald with probability 1; the
  below-Y=1 fold) are replaced by: on the golden fixture, base type, the pick shares by ore within 0.5 points of share and the
  expected slab densities above within 2% relative; no vein of an ore lands where its profile has no mass; the layer draw's
  histogram over 10,000 draws matches the profile.
- The floor clip. `ClipFactors.densest` adds the baseline's requests layer by layer as height now spreads them, from
  the profiles, not as flat bands, so a node that concentrates ore low (Deep Veins) is summed on top of the densest
  baseline layer. No baseline slab is near 60%: the densest is 4.6% in the base stone band, 8.3% in deepslate. The
  factors are computed once on the main thread into the generation snapshot (`EdgeFactors`, beside `ClipFactors`), not
  per chunk (#425 review, R-6).
- The clip's reach under height (#425 review, B-2; for T9, E9-S15 #320, where Nick is asked). Because `densest` is now
  the densest layer, not the band average, the clip fires when the band-wide request is about 23% in deepslate and about
  35% in stone, not at 60% band-wide: densest over average is 16.28 / 6.22 = 2.62 in deepslate, 1.71 in base stone and
  1.68 in expanded stone. The "100 to 140%" estimate above was derived before the densest-layer rule, for a flat band.
  Run through it, a full stack reads 260 to 370% at Y=-2, the clip comes out at 0.16 to 0.23, and the deepslate band
  places about 23% ore on average; §3.1's "working toward the 40% floor" holds only at Y=-2. That is a lower ceiling on
  late-game density than the tree was priced against, set by iron's peak rather than by a choice of Nick's. T9 puts it
  to him when it pins Vein Shaping and Stone Reduction, and its full-stack test asserts on the layer, not the band.
  `ClipFactors.HARD_STOP_HEADROOM`'s figures hold for a flat request only.
- What moves with it. §4.3's ore an hour and curve do not move, since the band densities and shares are unchanged; the
  pacing session (S11) names the layers it mines, and Y1..16 is about 329 ore an hour, above S11's 264. Seismic Sense's
  `COMPASS_MARGIN` was derived for 245 stone layers and falls to about 3.2 standard deviations in the base type; the
  Balancer re-derives it in E9-S32a. ADR 0004's size gate gets easier (95/245 of the stone band's veins a chunk), and its
  comparator (the chunk's block data at the type's full height) does not change. Node anchors re-set in §6.1, each with
  its reason there and in `DESIGN_LOG.md`: Shallow Grace's deepest Y above 100 becomes above 32; Bedrock Communion's
  penalty band Y=32..245 becomes Y=32..95 and its exemption Y=246 becomes Y=96; Stratified's bands become Y≤24, 25..58
  and 59..95 with blends of 6 and 2 blocks. Deep Breath's Y=32 and Highwater Mark's Y=64 stand. Deep Veins is restated
  (above). ADR 0004's gate: with the stone band at 95 layers the gate stack's stone veins fall to 95/245 of today's;
  `VeinIndexSizeTest`'s figures are re-measured and its bound changed on purpose in the code PR, not loosened.
- The expanded deepslate's top slab (P1), accepted by Nick on 2026-11-22. Y-31..-1 is 8.31% ore (iron 390 blocks a
  chunk), 2.53 times the stone curve's rate, and its best 1 by 2 tunnel, Y-4..-3, is about 15.5% ore at 3.74 times, the
  100 hours in about 27. E4-S5 measures it; the counts are retuned if it plays too fast.
- Existing Vaults (A1; Architect to confirm). The dimension type is shared by every Vault of its type, so a change to
  `min_y` or `height` would apply to every Vault on disk at the next start; Y=100 changes neither, so old Vaults would
  keep their generated chunks with stone to Y=245 and new chunks would generate 150 blocks lower beside them. The type
  file gains `layout`, an int: absent reads as 1, new files are written as 2 (ADR 0005 section 3a). ADR 0007's
  start-of-server pass archives a layout-1 folder through the existing `VaultFolders` move to
  `<world>/orevault_archive/<teamUuid>/layout-1-<time>/`, keeping its bytes and the team's data, and the team's next
  portal entry makes a fresh Vault (ADR 0002 creation step 2, restore step 6). A player who logged out inside is sent to
  their return position by login case 0 with the new key `message.orevault.vault_layout_archived` (§11 gives its text;
  not "damaged", which would be untrue). The login reads `in_vault`'s own `layout`, added in #425 (absent reads 1, new
  entries write 2; ADR 0005), so it holds at any later start and after a teammate has made the fresh Vault, which a
  session list of archived teams did not (#425 review, R-1/A-1). A layout-1 folder the pass could not move aside is
  refused at the portal and at login with `message.orevault.vault_layout_archive_failed` (§11), not as damaged. ADR
  0002's restore fixture gains case (g), and `tools/restart-check/vault-layout-*.txt` runs it at a real start. The code
  PR's body tells Nick his development Vaults are archived on first start.
- Code and tests the change touches (D3), for sizing the code ticket:
  - `core.gen`: `VaultVariant.STONE_TOP_Y`; `OreBaseline` (`STONE_BAND_LAYERS`, counts and blobs in exact form,
    `edgeFactor` taking the pickers); `LayerStack` built-ins and a check that the layer at `stoneTopY + 1` is not the
    stone filler, so an override written for the old column falls back loudly; `OrePickers` (column -64..319, no
    `foldBelow`); `VeinPlacer` (the ore then middle draws, the region rule, its class Javadoc's draw order,
    `baselineRequests`); `VeinRequest`'s contract; `ClipFactors.of` and `densest` (profiles and kernel);
    `VeinShapeTables.LAYER_KERNEL`; `GenSnapshot.of` and `GenSnapshots` (they take the published pickers or an
    immutable profile table from them, so the snapshot depends on classification, which ADR 0007 already orders first;
    with no classifier output a rebuild throws, as `forVault` does).
  - `core.ore`: `OrePicker` (constructor loses `columnFloorY`, `columnTopY`, `foldBelow`; `pick` becomes an ore draw
    then a layer draw; exposes each ore's in-band and in-region cumulative mass and the edge factor's inputs);
    `HeightProfile.slabs` (no linear map or fold; a per-layer in-band mass); both classes' Javadoc.
  - Density readout: `DensityTally` (takes the pickers for expected slabs and shares; a per-slab share verdict with
    `SLAB_SHARE_TOLERANCE` 0.04, from 0.03 after the #425 review) and the `/orevault density` adapter.
  - Persistence: `data.VaultTypeFile` (`layout`); ADR 0007's pass (the layout archive and `layoutArchivedTeamIds`);
    `in_vault`'s `layout` and login case 0's message; `frame.SafeArrival.DEFAULT_FEET_Y`; the two `vault_layers/*.json`; the lang key.
  - Unit tests: `VaultVariantTest` (245; bounds 1..245, 0..245), `OreBaselineTest` (245/246 becomes 95/96),
    `VeinPlacerTest` (golden sequence; the 141.37 mean), `ClipFactorsTest` (region 1..245), `DensityTallyTest` (14
    slabs, Y225..245, Y224..245, Y=246), `HeightProfileTest`, `OrePickerTest` (`-63, 245, 1, 245, true`),
    `VeinIndexSizeTest`, `VeinShapeTablesTest`, `ReturnPortalTest` (GRASS 250), `SafeArrivalTest` (GRASS 250, columns
    240..260), `LayerStackTest`, `VaultDataFilesTest`, and the type file's key-set test.
  - Gametests: `WorldgenTests` (SURFACE_TOP 251, grass at 250, the bad stack with 245 and 69), `ReturnPortalTests` (BASE
    251), `VaultLifecycleTests` (BASE_SURFACE 251, `setPos(0.5, 251, 0.5)`), `DebugCommandTests` (`break 0 250 0`
    expects grass; `density` positioned at Y=250).
