# FTB Materials 26.1.2.6: what it registers

Read from `ftb-materials-26.1.2.6.jar` in Nick's FTB Evolution 2 mods folder, 2026-10-26.

modId `ftbmaterials`, licence All Rights Reserved. It requires NeoForge 26.1.2.22-beta or later, MC 26.1 and FTB Library 26.1.1.8 or later. It has **no dependency on Mekanism**, required or optional, and contains no Mekanism code.

## Ores

Block IDs follow `ftbmaterials:<material>_<variant>_ore`, where the variant is `stone`, `deepslate`, `nether` or `end`. Raw ore items are `ftbmaterials:<material>_raw_ore`, not `raw_<material>`.

| Group | Materials |
| --- | --- |
| All four variants (stone, deepslate, nether, end) | aluminum, antimony, apatite, bauxite, cinnabar, dimensional_shard, fluorite, iridium, lead, monazite, nickel, niter, osmium, platinum, resonating_ore, ruby, salt, sapphire, silver, sulfur, tin, titanium, tungsten, uranium, zinc |
| Stone, deepslate and end only | quartz |
| Nether and end only (vanilla already has stone and deepslate) | coal, copper, diamond, emerald, gold, iron, lapis_lazuli, redstone |

Metal ores drop `<material>_raw_ore`. Gem ores drop the gem directly, for example `ruby_stone_ore` drops `ruby_gem`. Both use vanilla fortune (`ore_drops`) and silk touch, so Ore Vault's drop pipeline treats them like vanilla ores.

Harvest level: osmium, dimensional_shard and the nether/end diamond, gold and emerald ores are in `minecraft:needs_iron_tool`. Most others are in `needs_stone_tool`. Everything is in `mineable/pickaxe`.

## Tags

All in the `c:` namespace, which is what §9 already assumes.

- Block and item ore tags: `c:ores`, `c:ores/<material>`. Note the tag name is `lapis`, not `lapis_lazuli`. Also `c:ores_in_ground/{stone,deepslate,netherrack,end_stone}`.
- `c:dusts/<material>` exists for every ore material except `dimensional_shard` and `resonating_ore`. For those two, the §9 dust fallback goes to raw ore or gem.
- `c:raw_materials/<material>` for the metals.
- `c:gems/<material>` for apatite, cinnabar, dimensional_shard, fluorite, niter, resonating_ore, ruby, salt, sapphire, sulfur, and also odd items `gold_gem`, `silver_gem`, `brass_gem`, `electrum_gem`, `chromium_gem`, `plutonium_gem`, `silicon_gem`, `coal_coke_gem`.
- Also `c:ingots`, `c:nuggets`, `c:storage_blocks`, `c:plates`, `c:gears`, `c:rods`, `c:wires`, `c:blades`, `c:small_dusts`, `c:tiny_dusts`, `c:chunks`, `c:clusters`.

So Ore Vault's classifier should key off tag paths (`c:ores/<name>`), not item ID strings.

## Mekanism-related items

FTB Materials ships Mekanism's whole ore-processing chain as its own items, for 24 metals: `<material>_clump`, `<material>_dirty_dust`, `<material>_shard` and `<material>_crystal`. They are tagged `c:clumps/<material>`, `c:dirty_dusts/<material>`, `c:shards/<material>`, `c:crystals/<material>`, and the shards are also tagged `mekanism:shards/<material>`. The metals are aluminum, antimony, brass, chromium, copper, diamond, gold, iridium, iron, lead, lumium, nickel, osmium, platinum, plutonium, quartz, silicon, silver, sulfur, tin, titanium, tungsten, uranium and zinc.

For Ore Doubling tiers 4 to 6 (§9), this means the Mekanism-tier outputs can be found through `c:clumps/`, `c:shards/` and `c:crystals/` tags, with no reflection and no `mekanism.*` import. I did not see the Mekanism jar in this pack, so whether Mekanism's own tags line up is still unchecked.

## Unification, which can affect Ore Vault

FTB Materials has a startup config, `ftbmaterials-startup`, with three switches:

- `tweak_worldgen`: a mixin on `BulkSectionAccess.getSection` wraps chunk sections so that modded ores placed through it are swapped for FTB Materials equivalents. Vanilla ores are untouched. Ore Vault's chunk generator does not place ores through `BulkSectionAccess`, so it should not be affected, but this is worth a gametest in the pack.
- `tweak_loot_tables`: a global loot modifier, `ftbmaterials:unifier`, swaps modded drops for FTB Materials items. Ore Vault's bonus drops pass through loot, so in this pack they come out as FTB Materials items. That's fine as long as Ore Vault works from tags.
- `tweak_recipes`: rewrites recipe inputs and outputs.

Blacklists exist for blocks, items, tags and recipes. They accept wildcards such as `somemod:*`.

## Caveat

The jar has no block loot table for `monazite_*_ore` or for the `coal_nether_ore` and `coal_end_ore` blocks, even though they are registered and tagged. They may be generated at runtime, or they may drop nothing. Check in game before relying on them.
