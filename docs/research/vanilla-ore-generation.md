# How vanilla 26.1 places ore, and the Vault baseline it implies

Research for Nick's answer N4 (`docs/TEAM_REQUIREMENTS.md`): measure how vanilla chunk generation places
ore, use it to set the Vault's baseline, which reopens the 3%/6% figures in DESIGN_LOG 2026-09-27, then
retune Guide Vein so a player can feel it. Owners: Balancer and Architect, Developer review. Written
2026-10-26.

**Decided 2026-10-26: Nick kept 3% stone and 6% deepslate** over the proposed 4%. The rest of section 3
(mixes, vein sizes, gravel and clay, the rarity rule) and the Guide Vein retune in section 4 are carried
into the spec at 3%. At 3% the figures that differ from section 3's 4% tables are:

| | At 3% (decided) |
|---|---|
| Veins per chunk, stone band | about 141 common, 82 uncommon, 33 rare **(D)** |
| Ore per hour, solo | 220, as before (§4.3) |
| Weighted Resonance per ore at 68/25/7 | 3.485 **(D)** |
| Total Resonance over 100 h / level 1 cost | about 76,700 / about 114 **(D)** |
| S11 ore band | 176–264, unchanged |
| Rare ore share of the volume | 0.21%, just under vanilla's best diamond layer (0.24%) |
| Floor headroom to 60% ore | stone 20x, deepslate 10x |
| Guide Vein | +32% of a chunk's ore, about +18% Resonance; about +7% at Vein Expansion and Proliferation T5 **(D)** |

## 1. Method

- **Source data.** Every overworld ore `placed_feature` and `configured_feature` JSON from vanilla
  **26.1.2**, taken from the `26.1.2-data` tag of misode/mcmeta (the extracted vanilla datapack), plus the
  biome files that say which biomes list which features. The mod targets 26.1 (`neo_version=26.1.2.95`).
- **Vein size.** A configured `size` is not a block count. `vanilla-ore/orefeature.py` is a port of
  `OreFeature.place`/`doPlace` (the ellipsoid chain), run 20,000 times per size in solid stone to get the
  mean blocks per vein and how far a vein spreads above and below its origin.
- **Per-chunk profile.** `vanilla-ore/profile.py` convolves each feature's count (`count`,
  `rarity_filter`, uniform-int count) and height provider (uniform, trapezoid, `above_bottom`,
  `below_top`) with the vein spread, clipped to the world's Y=-64..319.
- **What it assumes.** Solid rock: no caves, no aquifers. So air-exposure discard (`discard_chance_on_air_exposure`,
  0.5 to 1.0 on the "buried" diamond, lapis, gold and coal features) never fires, and the surface-height check
  that stops veins above the terrain is ignored. These are **upper bounds** for rock that exists; rows above
  Y=64 describe mountains only. The large copper and iron **ore veins** from the noise router (copper Y0..50,
  iron Y-60..-8) are **not included**.
- **Solid rock is the intended model.** The Vault is solid rock, so Nick chose (2026-10-26) not to
  measure a real vanilla world with caves. The vein port was checked line by line against the
  decompiled 26.1 source (section 7) and matches it.

Raw output is in `vanilla-ore/results.txt`. To rerun: download the JSONs named in `profile.py` from
`https://raw.githubusercontent.com/misode/mcmeta/26.1.2-data/data/minecraft/worldgen/` into `mc/p` and
`mc/c`, then run `veinstats.py`, `profile.py`, `bands.py`, `extra.py` in that order.

## 2. What vanilla does

### Blocks per vein

| Configured size | Used by | Mean blocks | 10th–90th percentile |
|---|---|---|---|
| 3 | emerald | 0.46 | 0–2 |
| 4 | diamond (small), iron (small) | 1.99 | 0–4 |
| 7 | lapis | 4.97 | 4–8 |
| 8 | redstone, diamond (medium, buried) | 5.70 | 4–8 |
| 9 | iron, gold | 6.79 | 4–9 |
| 10 | copper | 8.39 | 6–11 |
| 12 | diamond (large) | 11.59 | 9–14 |
| 17 | coal | 22.15 | 18–26 |
| 20 | copper (large, dripstone caves) | 32.91 | 26–39 |
| 33 | gravel, clay, dirt | 112.2 | 99–124 |

### Features, per chunk, solid rock

| Feature | Ore | Veins/chunk | Origin Y | Blocks/chunk | Where |
|---|---|---|---|---|---|
| ore_coal_upper | coal | 30 | 136..319 uniform | 665 | all biomes |
| ore_coal_lower | coal | 20 | 0..192 trapezoid | 443 | all |
| ore_iron_upper | iron | 90 | 80..384 trapezoid | 558 | all |
| ore_iron_middle | iron | 10 | -24..56 trapezoid | 68 | all |
| ore_iron_small | iron | 10 (size 4) | -64..72 uniform | 20 | all |
| ore_copper | copper | 16 | -16..112 trapezoid | 134 | all but dripstone caves |
| ore_copper_large | copper | 16 (size 20) | -16..112 trapezoid | 527 | dripstone caves only |
| ore_gold | gold | 4 | -64..32 trapezoid | 27 | all |
| ore_gold_lower | gold | 0–1 | -64..-48 uniform | 3 | all |
| ore_gold_extra | gold | 50 | 32..256 uniform | 340 | badlands only |
| ore_redstone | redstone | 4 | -64..15 uniform | 22 | all |
| ore_redstone_lower | redstone | 8 | -96..-32 trapezoid | 21 | all |
| ore_lapis | lapis | 2 | -32..32 trapezoid | 10 | all |
| ore_lapis_buried | lapis | 4 | -64..64 uniform | 20 | all |
| ore_diamond | diamond | 7 (size 4) | -144..16 trapezoid | 7 | all |
| ore_diamond_medium | diamond | 2 | -64..-4 uniform | 11 | all |
| ore_diamond_large | diamond | 1 in 9 chunks | -144..16 trapezoid | 0.6 | all |
| ore_diamond_buried | diamond | 4 | -144..16 trapezoid | 11 | all |
| ore_emerald | emerald | 100 (size 3) | -16..480 trapezoid | 36 | mountain biomes only |
| ore_gravel | gravel | 14 | -64..319 uniform | 1,565 | all |
| ore_clay | clay | 46 | -64..256 uniform | 5,138 | lush caves only |

Trapezoids with no plateau are triangles peaking at the midpoint. Diamond's -144..16 puts half its veins
below the world floor, which is why its peak is at the bottom. Iron's and emerald's ranges run past the
top, so in practice they peak around Y=232 in mountains.

### Ore by depth band, plains, solid rock

| Band | Coal | Iron | Copper | Gold | Redstone | Lapis | Diamond | Total | Density |
|---|---|---|---|---|---|---|---|---|---|
| Y-64..-1 (deepslate) | 0 | 23 | 5 | 25 | 40 | 15 | 29 | 136 | **0.83%** |
| Y0..31 | 27 | 47 | 35 | 6 | 4 | 10 | 1 | 129 | 1.58% |
| Y32..63 | 75 | 16 | 58 | 0 | 0 | 5 | 0 | 154 | 1.88% |
| Y64..127 (hills) | 244 | 34 | 37 | 0 | 0 | 0 | 0 | 314 | 1.92% |
| Y128..319 (mountains) | 761 | 525 | 0 | 0 | 0 | 0 | 0 | 1,286 | 2.62% |
| **Y-64..63, where players mine** | 103 | 87 | 98 | 30 | 44 | 30 | 30 | **420** | **1.28%** |

Other biomes over Y-64..63: badlands 1.44% (extra gold), dripstone caves 2.15% (large copper), mountains
1.29% (emerald adds about 2.5 blocks below Y=64 and about 34 above).

Best single depth for each ore, as density of that ore alone over its best 16 layers: coal 2.3% (Y135..150),
iron 1.5% (Y223..238), copper 0.76% (Y39..54), redstone 0.50% and diamond 0.24% (Y-64..-49, which is
mostly the bedrock layers' neighbours, so in practice Y-59..-49), gold 0.20% (Y-25..-10), lapis 0.16%
(Y-9..6).

### What this changes

1. **Vanilla is about 1.3% ore in solid rock where players mine, not 0.5%.** The 0.5% figure in the
   2026-09-27 entry was never measured, and it was a whole-world figure that counts caves and air. So 3% is
   about 1.7x the Y0..63 band and 2.3x the Y-64..63 average, not "about six times vanilla". 6% deepslate is
   about 7x vanilla's 0.83%.
2. **The spec's 70/25/5 mix is close to vanilla's.** Below Y=64 vanilla is 68% common, 25% uncommon, 7%
   rare by block count. Deepslate alone is 21/58/21, because that is where gold, redstone and diamond live.
3. **The spec's vein sizes are close to vanilla's.** Vanilla means below Y=64: common 8.1 blocks a vein
   (coal much bigger, 22), uncommon 5.7, diamond 3.9. The spec says 6–12, 4–8, 2–5.
4. **Vanilla places by count, size and height, never toward a target fraction.** About 7.6 veins every 16
   layers of a chunk below Y=64, roughly 23 of them copper or iron.
5. **Gravel is about 1.6% of the volume**, 14 blobs of about 112 blocks a chunk. Underground clay is
   lush-caves-only and, at 46 blobs a chunk, would flood a Vault at vanilla's rate.
6. **Guide Vein as written is invisible.** One copper or iron vein of about 9 blocks against about 1,900
   ore a chunk at 3% is +0.5%, at a random height in 245 layers.
7. **The old classifier misread vanilla.** It read only uniform heights with absolute anchors, so every
   trapezoid and every `above_bottom`/`below_top` feature was dropped: coal, iron, copper, lapis, redstone
   and emerald all fell back to uncommon at mid-depth, and gold came out common off badlands'
   `ore_gold_extra`. `OreClassifierTest` tested only `classify()`, so nothing caught it.

## 3. Proposed baseline (Balancer, after review); Nick chose 3% over this 4%

**(D)** marks a derived number; everything in section 2 is computed from vanilla's data. Counts are per
rarity per band. Sizes are vanilla's configured `size`, which is not a block count (section 2); block
figures come from the vein port, and overlap between veins (about 2% of blocks at 4%) is already allowed
for in the counts.

| | Stone band (Y1..245) | Deepslate (Y-63..-1, after Vault Expansion) |
|---|---|---|
| Placed density | **4%** (now 3%) | **6%** (unchanged) |
| Mix by placed blocks, S12 target ±2 points per band | **68/25/7** (now 70/25/5) | **60/30/10** |
| Common: configured size 9–12, mean 9.2 blocks, never empty | 189 veins **(D)** | 65 **(D)** |
| Uncommon: configured size 7–9, mean 5.8 blocks | 110 **(D)** | 52 **(D)** |
| Rare: configured size 5–7, mean 4.05 blocks, 3% empty | 44 **(D)** | 25 **(D)** |
| Gravel, configured size 33 (about 112 blocks) | 9 blobs, 1.6% | 2 blobs, 1.4% |
| Clay, configured size 33 | 3 blobs, 0.5% | 1 blob, 0.7% |

Why each line:
- **4% stone** is 2.1 to 2.5x vanilla's Y0..63 band and 3.1x the Y-64..63 average. The Vault has no caves,
  so every ore is found by tunnelling, and vanilla's ore per hour gets a lot of help from cave walls. One
  ore in 25 blocks, so stone still dominates.
- **68/25/7** is vanilla's own mix below Y=64. Rare ore at 7% of 4% is 0.28% of the volume, about
  vanilla's best diamond layer (0.24%): the zero-spend Vault matches vanilla's best strip mine for rare ore
  without beating it.
- **Vein sizes** match vanilla's block means (common 8.1, uncommon 5.7, diamond 3.9). Rare uses size 5–7
  rather than vanilla diamond's 4, because size 4 is empty in 24% of rolls.
- **Deepslate 60/30/10** puts rare ore at 0.6% of the volume, 3.3x vanilla deepslate diamond **(D)**.
  Vanilla's own deepslate mix (21/58/21) would give that band 2.6x the stone band's Resonance per hour;
  60/30/10 gives 1.7x **(D)**.
- **Order:** common, uncommon, rare, then gravel and clay into stone only, so overlaps cost common ore and
  gravel never lowers ore density. Gravel and clay count as stone for the 40% floor.
- **Clay** has no vanilla anchor: underground clay is lush-caves-only, at a rate that would flood the
  Vault. 0.5% is a choice.

**Rejected numbers.** The 2026-09-27 entry rejected 8% and 15% as the zero-spend Vault, for reading as
finished at one ore in twelve. 4% is one in 25 and is not that decision. The tree still reaches about 8%
in the stone band (Vein Expansion T3 with Proliferation T2, 4% x 1.5 x 1.4 = 8.4% **(D)**, about level 7).
**Fallback: 3%**, whose feel argument holds without the 0.5% figure. Whichever Nick picks, the spec's
"roughly six times the vanilla overworld" is wrong and goes.

**Vein Expansion units.** Its percentages are blocks per vein; the placer maps a percentage to a
configured size from the port's table (doubling the configured size would give about 4x the blocks).
Expansion T5 with Proliferation T5 requests 17.6% in the stone band and places 16.1% after overlap **(D)**.

**Rarity rule** (replaces "count ≥ 15 and range ≥ 128", which makes emerald Common on real data): an ore is
Common with at least 100 expected blocks per chunk over Y-64..319; otherwise Rare if its mean is under 4.5
blocks a vein, else Uncommon. On vanilla: coal 1,108, iron 646, copper 134 blocks a chunk are Common; gold
(6.8 a vein), redstone (5.7), lapis (5.0) Uncommon; diamond (3.9) and emerald (0.46) Rare. Lapis has the
thinnest margin; the golden fixture pins it.

### What moves with it

| Number | Now (3%) | Proposed (4%) |
|---|---|---|
| Ore per hour, solo (7,200 blocks/h x placed density) | 220 | **288 (D)** |
| Weighted Resonance per ore (2 / 5 / 12.5) | 3.275 | **3.485 (D)** |
| Total Resonance over 100 h | 72,050 | **about 100,400 (D)** |
| Level 1 cost | about 107 | **about 149 (D)** |
| Time to level 1 / level 2, §4.3 milestone hours | 8.9 / 19.3 min | **unchanged**: thresholds scale with the rate |
| S11 ore band (±20%) | 176–264 an hour | **230–346** |
| S12 mix tolerance | ±2 points on 70/25/5 | **±2 points per band, 68/25/7 and 60/30/10** |
| Floor headroom to the 60% ore cap | stone 20x, deepslate 10x | **stone 15x**, deepslate 10x |

The curve and `/orevault density` read placed blocks from a JUnit count over the real placer, not the
closed form. After the keystone, mining deepslate earns about 1.7x the Resonance per hour **(D)**; T14
measures that separately.

## 4. Guide Vein retune (Balancer, after review)

**Now.** One vein of about 9 blocks a chunk: +0.36% of a chunk's ore at 4%, indistinguishable from a
baseline vein, at a random height. Nobody can feel it.

**Proposed.** "Twelve large copper or iron veins, about 50 blocks each, generate in every Vault chunk,
spread evenly through the stone band's height." Cost 1, level requirement 0.
- **Unmistakable.** Configured size 24, mean 51 blocks, 90% of them 43 or more. Baseline common veins are
  14 blocks or fewer 90% of the time.
- **Strength.** 12 x 51 = 607 blocks, +24% of a chunk's ore, all common, about +14% Resonance per hour;
  very roughly one guide vein every 10 to 15 minutes of tunnelling **(D)**.
- **Spread through the whole band**, not a slice, because players dig straight down for rare ore
  (Playtester). They meet guide veins at the same rate at any depth.
- **Fades, never switches off (N5).** A flat count that Vein Expansion does not enlarge; with Expansion and
  Proliferation at T5 it adds about 6% **(D)**.
- **Visible.** The Survey panel shows "Guide Vein ore mined this trip: N".
- **Spec text.** Delete "while team level < 15" and the cutoff sentence; reword §6's `[GROWTH]`
  definition to "a flat reward that matters less as throughput rises; never switches off".

**Chunks that already exist (decided).** The first point arrives at level 1, about minute 9, and by then view
distance 10 has generated 441 chunks around the arrival point, more rock than a player mines in thousands
of hours. A node that applies only to "newly explored chunks" therefore misses everything near spawn. The
Balancer proposes placing guide veins into buried stone when an older chunk loads (main thread, never into
a block touching air, seeded from team id and chunk, with a per-chunk marker). That is new persisted chunk
state and main-thread world edits, which the Architect has not reviewed; the alternative is to say "chunks
first generated after purchase" in the description. **This affects every generation node, not only Guide
Vein** (Vein Expansion, Proliferation, Deep Veins), so it went to Nick. **He decided on 2026-10-26: generated chunks are never changed; only the reset regenerates them.** The retrofit was not adopted.

## 5. How the generator should hold it (Architect)

- **Express the baseline as veins per chunk x vein size x height profile, per rarity and band**, in a pure
  record in `core.gen` (say `OreBaseline`), not as a density percentage. Counts are fractional, as vanilla's
  are (1/9 for large diamond). The density and the rarity shares are *derived* from it in closed form
  (Σ count x mean blocks ÷ band volume), and `LevelCurve` and the generator read that one source. No stored
  0.68/0.25/0.07 beside the counts.
- **Counts are per rarity, not per ore.** Copying vanilla's per-ore counts would make every modded ore add
  its own veins, so density would grow with the modpack and the mix would drift past S12's ±2 points.
- **This is what S13 asks for**: the generator only rolls veins, with no fill-to-budget loop like the old
  `placeBand`. When modifiers would push past 60% ore, every rarity's count scales down by one factor
  before rolling, and that factor is in the snapshot; a per-band hard stop stays as a safety net only.
- **Port vanilla's vein shape to pure Java** (`core.gen.VeinShape`); don't call `OreFeature`, which needs a
  `WorldGenLevel`, writes blocks instead of returning extents, and spills into neighbouring chunks. For
  S20, roll the shape relative to its origin, take its actual bounding box, place the origin uniformly
  where the box fits inside the chunk, and return a `VeinExtent`. A box fits up to about size 37, so Vein
  Expansion stays under that or the placer splits the vein. The cost is that ore thins by one or two blocks
  toward chunk borders; Nick's ten-minute T4 mine should look for it. The Python sim's blocks-per-vein table
  is the test oracle (size 3 gives 0.46, 9 gives 6.79, 17 gives 22.15).
- **The classifier samples height providers instead of switching on their type**: about 4,096 fixed-seed
  samples per provider against an overworld context (min -64, height 384), dropping samples outside
  -64..319. That handles trapezoids, `above_bottom`/`below_top`, iron's and emerald's ranges past the top
  and diamond's below the floor, and modded providers, in one path. Counts use expected values (mean x
  `rarity_filter` chance). Each ore gets one block-weighted Y profile summed over its features, rather than
  the midpoint of merged ranges, which put iron at about Y=160. Biome-only features (`ore_gold_extra`,
  `ore_copper_large`, emerald) are used only when an ore has no all-biome feature. Stone and deepslate
  variants of one ore are grouped so they are not counted twice. A JUnit golden fixture pins vanilla's eight
  ores from these 26.1.2 values.
- **Open for the Balancer**: the spec's rarity rule (count ≥ 15 and range ≥ 128 is Common) makes emerald
  Common on real data (count 100, 0.46 blocks a vein). It should run on expected blocks per chunk.
- **Guide Vein at generation needs only node ownership from the snapshot**, no level cutoff, since it
  never switches off. A generation-time rule must not depend on where a player is tunnelling, on
  neighbouring chunks, on generation order or a running count, or on per-player state; chunks generate
  ahead of the player, off-thread, in any order. It may use chunk coordinates, band and Y window, vein
  count and size, and team level. S12's mix check runs with Guide Vein off, since it adds common ore. The
  retrofit into existing chunks (section 4) is outside this and not yet reviewed.
- **Decision records**: ADR 0009 "Baseline ore as vein count, size and height profile"
  (`docs/adr/0009-baseline-ore-model-and-classifier.md`: the model, derived shares, floor clip, sampling
  classifier). It was planned as 0008, but S47 reserved 0001 to 0007 and commit bf13b82 numbered the tool
  wear record 0008. The chunk-border shape rule goes in the planned ADR 0004.

## 6. What a playtest must check

**Rig for both sessions.** Solo, zero points. Diamond pickaxe with Efficiency V and Unbreaking III, no
Fortune, no Silk Touch, Ultimine not installed. `VaultDiag` logs the player's Y every 10 seconds.

**Session A, about 60 minutes, at T4/T5**
1. Blocks per hour and ore per hour, and how far ore broken beats density x blocks broken. Outside 230–346
   ore an hour, the curve is re-derived.
2. Level 1 inside 7–11 minutes and level 2 inside 15–23.
3. Feel at 4% and then 3%: two builds on the same copied save with the Vault deleted (same team id, so
   the same seeds). Did stone dominate, was a vein an event, did anything feel like a treasure room?
4. Gravel collapses into tunnels, counted.
5. `/orevault density` over 81+ chunks: placed density within ±5% per band, mix within ±2 points per
   band, counts per ore per 32-layer band.
6. Ore thinning near chunk borders.

**Session B, after T9**
7. Guide Vein blind: someone else toggles it between two 15-minute runs and the player names the run
   that had it. Read the Survey count too.
8. Is Gravel Purge worth a point?
9. After the keystone, does deepslate read as the richest band?

Comparing rare ore per hour with a vanilla strip mine uses section 2's solid-rock figures; Nick chose not
to measure a real vanilla world (2026-10-26), since the Vault is solid rock.

## 7. Reviews and how they were answered

- **Developer: approve with comments.** The vein port matches vanilla `OreFeature` line by line (set
  instead of BitSet, same result); height resolution and every (D) number re-derived. Raised: configured
  size is not blocks (answered: sizes now in configured units); closed-form density overcounts at high
  density through overlap (answered: counts allow for it, curve reads placed blocks); per-rarity counts
  cannot make deepslate "tilt rarer" (answered: deepslate has its own counts and mix); ore before gravel
  (adopted). Classifier notes for ADR 0009 (planned as 0008 when this was written): build the `WorldGenerationContext` from the real overworld
  rather than hard-coding -64/384; read private placement fields by encoding through
  `PlacementModifier.CODEC` instead of new access transformers; "all-biome" means present in the
  underground-ores step of every overworld biome source biome; custom-config ores (Mekanism's) and
  `#c:ores` members with no overworld feature need a fallback; Guide Vein's band comes from
  `VaultLayerConfig`, not literals. API points are from memory, to confirm in the sources jar.
- **Playtester: request changes,** all answered in sections 4 and 6: players dig straight down (Guide
  Vein spread through the band), spawn chunks exist before the first point (Nick: never changed, reset only), a
  9-block vein cannot be traced to the node (now 51-block veins and a Survey count), the playtest list did
  not fit one session (split, rig added, "same seed" corrected).
- **Architect** flagged the rarity rule (answered in section 3).
- **Source check (2026-10-26).** The port was compared with the decompiled `OreFeature`,
  `OreConfiguration`, `TrapezoidHeight`, `UniformHeight`, `HeightRangePlacement`, `CountPlacement`,
  `RarityFilter` and `VerticalAnchor` from `neoforge-26.1.2.95-sources.jar` on Nick's PC. Every step
  matches: the direction and spread (`size/8`), the endpoint heights (`origin.y + nextInt(3) - 2`), the
  radius formula, the pairwise cull, the box clamps, the strict ellipsoid tests, trapezoid sampling, the
  `below_top` resolution (`genDepth - 1 + minGenY - offset`, so 319) and `rarity_filter`
  (`nextFloat() < 1/chance`). The only differences are `Mth.sin`'s lookup table against exact `sin`, and
  vanilla's BitSet index, which can alias on the very edge of a vein's box and skip a block; both are far
  below the precision quoted here. In solid rock every target test passes, so `canPlaceOre` and the air
  check do not change the counts.

