# Issue harvest: the old nickgg11/ore-vault tracker

Read 2026-10-26 by the manager. Everything below is data taken from issue and PR text, checked against the current `OreVault_Design_and_Spec.md`, `DESIGN_LOG.md` and `docs/LESSONS_LEARNED.md` by grep and by reading §3, §4, §8 to §13 of the spec. Nothing on GitHub was changed.

## Summary

The tracker holds 152 numbers: 122 issues (68 open, 54 closed) and 30 pull requests (#107 to #153, all closed now, including #146, #150 and #153, which the handoff still calls open). I read every issue body and comment, every PR body and conversation comment, and the review threads and reviews on #146, #150 and #153 (#146 and #150 each carry one bot review comment; #153 has none). Of the 152, 66 carry something a future ticket needs that the current docs do not hold. 86 are "nothing new": the spec, the design log or the lessons file already says it, or the item is superseded. The new material falls into five groups. Two real bugs were found in review and never fixed: Highwater Mark repairs a discarded copy of the tool, and the tree hit-test ignores the viewport. Several spec sections are stale, and the tickets hold the correct 26.1 answer: the §11 classifier, the §11 generator pattern, the §11 Tome hook, and the §3.1 dimension-type fields. Three designs were worked out in tickets and never written into the spec: the reset and deletion constraints, the cross-chunk vein design and the VeinIndex. There are a dozen implementation gotchas. And four questions are still open: Long Delve's hunger, the cross-cluster prerequisites, the per-tier descriptions, and Soul Harvest's shop.

## Verdict table

Roles: architect, developer, qa, reviewer, designer, ui-ux, writer, balancer, playtester. "Keep" names the role that should judge it first.

| # | Title (short) | State | Verdict |
|---|---|---|---|
| 1 | Epic: rebuild roadmap | open issue | nothing new (build order is in LESSONS "If you start over"; specifics live in #91, #126) |
| 2 | [01] Repo hygiene | closed | nothing new |
| 3 | [02] Mod scaffold | closed | nothing new |
| 4 | [03] NodeCosts | closed | nothing new |
| 5 | [04] NodeDef + NodeDefs | closed | nothing new |
| 6 | [05] SkillTree validation | closed | keep: qa |
| 7 | [06] PlayerStats | closed | nothing new |
| 8 | [07] OreVaultTeamData | closed | nothing new |
| 9 | [08] LevelCurve | closed | nothing new |
| 10 | [09] TeamHelper | closed | nothing new |
| 11 | [10] FtbEvents | closed | nothing new |
| 12 | [11] Server config | closed | nothing new |
| 13 | [12] Dimension type JSONs | closed | keep: architect |
| 14 | [13] VaultDimensions | closed | keep: developer |
| 15 | [14] OreClassifier | closed | keep: architect |
| 16 | [15] VaultChunkGenerator | closed | keep: architect |
| 17 | [16] Block registry + frame | closed | keep: developer |
| 18 | [17] VaultPortalBlock | closed | keep: developer |
| 19 | [18] VaultPortalShape | closed | keep: qa |
| 20 | [19] Item registry + igniter | closed | keep: developer |
| 21 | [20] VaultTeleport | closed | keep: developer (folded into the #20 entry) |
| 22 | [21] VaultOrbEntity base | closed | nothing new |
| 23 | [22] ResonanceOrbEntity | closed | nothing new |
| 24 | [23] ResonanceSystem | closed | keep: balancer |
| 25 | [24] AnimusSystem | open | nothing new (post-1.0) |
| 26 | [25] OreDropHandler | closed | keep: qa |
| 27 | [26] MobDeathHandler | open | nothing new (post-1.0) |
| 28 | [27] DisturbedZoneBlock | open | nothing new (post-1.0) |
| 29 | [28] DisturbedZoneManager | open | nothing new (post-1.0) |
| 30 | [29] VaultAnchorBlock | open | keep: designer |
| 31 | [30] VaultChunkLoader | open | nothing new |
| 32 | [31] VaultReset | closed not planned | nothing new (split into #93 to #96) |
| 33 | [32] ModNetwork | closed | nothing new (see #126) |
| 34 | [33] TomeItem | closed | nothing new (see #129) |
| 35 | [34] TomeScreen shell | closed | keep: developer |
| 36 | [35] Resonance tree tab | closed | nothing new (toggle-disabled item is in §13) |
| 37 | [36] Animus tree tab | open | nothing new (post-1.0) |
| 38 | [37] Ore Memory tab | open | nothing new (see #90) |
| 39 | [38] ResetVoteScreen | open | nothing new |
| 40 | [39] OreVaultClient | closed | nothing new (see #127) |
| 41 | [40] VaultOrbRenderer | closed | nothing new |
| 42 | [41] SoftDeps | open | nothing new |
| 43 | [42] FTB Ultimine integration | open | nothing new |
| 44 | [43] Ore doubling fallback | open | nothing new |
| 45 | [44] Vein Expansion/Proliferation/Deep Veins | open | keep: architect |
| 46 | [45] Ore Quality branch | open | nothing new |
| 47 | [46] Vein Fortune + Smelter's | open | nothing new |
| 48 | [47] Ore Doubling | open | nothing new |
| 49 | [48] Runic Attunement | open | nothing new |
| 50 | [49] Stone Memory + Ancient Knowledge | open | nothing new |
| 51 | [50] Efficient Miner (hunger) | open | nothing new (rename and hearts in spec) |
| 52 | [51] Magnetism + Hoarder's | open | nothing new |
| 53 | [52] Automated Extraction + Vault Presence | open | nothing new |
| 54 | [53] Volatile Veins + pity | open | nothing new |
| 55 | [54] Greedy Seams/Stone Curse/Vault Fever/Tithe | open | nothing new |
| 56 | [55] Abundance + Motherlode | closed not planned | nothing new |
| 57 | [56] Blessing + Purity | open | nothing new |
| 58 | [57] Twin Veins + Vault Echo | open | nothing new |
| 59 | [58] Vault Expansion keystone | open | nothing new |
| 60 | [59] Disturbed Zone Unlock | open | nothing new (post-1.0) |
| 61 | [60] Zone Frequency/Pack/Radius | open | nothing new (post-1.0) |
| 62 | [61] Mob Diversity | open | nothing new (post-1.0) |
| 63 | [62] Reaper's Claim + Plunderer's | open | nothing new (post-1.0) |
| 64 | [63] Corrupted Veins | open | nothing new (post-1.0) |
| 65 | [64] Animus Amplifier | open | nothing new (post-1.0) |
| 66 | [65] Soul Harvest | open | nothing new (see #90) |
| 67 | [66] Ultimine Expansion/Safety/Gambit | open | nothing new |
| 68 | [67] Crafting recipes | open | keep: developer |
| 69 | [68] Blockstates, models, textures | open | keep: designer |
| 70 | [69] Lang file + tooltips | open | nothing new |
| 71 | [70] Advancements + loot tables | open | keep: designer |
| 72 | [71] Particles + sounds | open | nothing new (§13 Polish list) |
| 73 | [72] Tests + KNOWN_ISSUES.md | open | nothing new (its migration note died with the old worlds) |
| 74 | [73] Vault Recall item | open | keep: designer |
| 75 | [74] Vault Return structure | closed not planned | nothing new |
| 76 | Configurable layer stack | closed | nothing new |
| 77 | Heightmap safe-spawn search | closed | keep: developer |
| 78 | Overlay messages for teleport | closed | keep: ui-ux |
| 79 | Frame hardness 1.5 | closed | nothing new |
| 80 | Team-required notification | closed | nothing new (gate removed) |
| 81 | Portal has no warp effect | closed | keep: developer |
| 82 | Blocks unbreakable mid-session | closed | keep: qa |
| 83 | Blue Vault sky | closed | keep: designer |
| 84 | Tier-coloured portals | closed | keep: developer |
| 85 | Exit portal 2 thick | closed not planned | nothing new |
| 86 | Exit portal colour | closed not planned | nothing new |
| 87 | Mineable tag namespace | closed | nothing new |
| 88 | Team gate regressed | closed not planned | nothing new |
| 89 | Mining stalls, log evidence | closed | nothing new |
| 90 | Epic: Animus, Zones, Ore Memory | open | keep: designer |
| 91 | [75] VeinIndex | open | keep: architect |
| 92 | [76] DropPipeline | closed | keep: developer |
| 93 | [77] Dimension deletion + re-creation | open | keep: architect |
| 94 | [78] Reset vote state machine | open | keep: architect |
| 95 | [79] Countdown, evacuation, respec window | open | keep: designer |
| 96 | [80] Reset backup export | open | keep: architect |
| 97 | [81] Curve, scaling, refund rebalance | closed | nothing new |
| 98 | [82] Fixed anchor + one return portal | closed | nothing new |
| 99 | [83] Dimension lifecycle | closed | nothing new |
| 100 | [84] Igniter tier capabilities | closed | nothing new |
| 101 | [85] New notables | open | keep: ui-ux |
| 102 | [86] Fork mechanic + four forks | open | keep: architect |
| 103 | [87] Node reworks | open | nothing new |
| 104 | [88] Tradeoff lock + SavedData versioning | closed | nothing new (detail is in PR #111) |
| 105 | [89] Load and performance test | open | keep: qa |
| 106 | [90] Disturbed Zone mob tagging | open | keep: architect (post-1.0) |
| 107 | PR: spec v1.1 | closed PR | nothing new |
| 108 | PR: four playtest blockers | closed PR | keep: developer |
| 109 | PR: dimension split + fixed anchor | closed PR | keep: developer |
| 110 | PR: curve, scaling, refund | closed PR | keep: developer |
| 111 | PR: tradeoff lock, versioning, return fix | closed PR | keep: architect |
| 112 | PR: Claude review workflow | closed PR | nothing new |
| 113 | PR: record skills and plugins | closed PR | nothing new |
| 114 | PR: tooling correction, merge protocol | closed PR | nothing new |
| 115 | PR: Resonance pool and levels | closed PR | keep: architect |
| 116 | PR: orb entity and base | closed PR | keep: developer |
| 117 | PR: drop pipeline, award, renderer | closed PR | keep: developer |
| 118 | PR: §13 sync | closed PR | nothing new |
| 119 | PR: Resonance readout, testore | closed PR | keep: qa |
| 120 | Remove VaultDiag before 1.0 | open issue | nothing new (§10 notes the removal) |
| 121 | PR: config screen | closed PR | keep: ui-ux |
| 122 | PR: igniter capabilities | closed PR | keep: developer |
| 123 | PR: ticket workflow | closed PR | nothing new |
| 124 | In-game guide book | open issue | keep: writer |
| 125 | PR: igniter tooltips | closed PR | keep: writer |
| 126 | PR: network channel | closed PR | keep: architect |
| 127 | PR: client entrypoint | closed PR | keep: developer |
| 128 | PR: teamSize returns 1 | closed PR | nothing new (in LESSONS) |
| 129 | PR: Tome item, first-join grant | closed PR | keep: architect |
| 130 | PR: Tome model | closed PR | nothing new |
| 131 | PR: texture standard | closed PR | nothing new |
| 132 | PR: Tome screen shell | closed PR | keep: developer |
| 133 | PR: debug commands on save, config audit | closed PR | nothing new |
| 134 | PR: Resonance tree tab | closed PR | keep: ui-ux |
| 135 | PR: clusters, pacts, growth, registry | closed PR | keep: architect |
| 136 | Tome cluster/stagger renderer | open issue | nothing new (in DESIGN_LOG) |
| 137 | Keystone rebalance, new mechanics | closed | nothing new (in DESIGN_LOG) |
| 139 | Growth nodes + Outgrown | open | keep: ui-ux |
| 140 | Counter breakpoints | open | nothing new (composition rule in §11; AT detail under #150) |
| 141 | Vein completion group | open | keep: developer |
| 142 | Exploration group | open | keep: developer |
| 143 | Survival group + hearts | open | keep: qa |
| 144 | Molten Seam | open | keep: qa |
| 145 | Mastery keystones | open | keep: balancer |
| 146 | PR: hub tree renderer | closed PR | keep: reviewer |
| 147 | Per-tier node descriptions | open | keep: ui-ux |
| 148 | Cross-cluster prerequisite edges | open | keep: designer |
| 149 | Vault session tracking | open | keep: architect |
| 150 | PR: session tracking + breakpoint nodes | closed PR | keep: reviewer |
| 151 | Long Delve: hunger at four stacks | open | keep: balancer |
| 152 | Baseline ore distribution | open | keep: playtester (folded into the #153 entry) |
| 153 | PR: baseline ore distribution | closed PR | keep: playtester |

Numbers 138 and 145-adjacent gaps: #138 does not exist in the tracker.

## Keep entries, by role

### Architect

**#13: 26.1 dimension-type fields.** The spec §3.1 still lists `fixed_time: 6000`, `bed_works`, `respawn_anchor_works`, `has_raids` and `natural: false`. The #13 comment records what 26.1 actually accepts: `has_fixed_time: true`; `attributes.minecraft:gameplay/bed_rule` with `can_set_spawn`/`can_sleep: "never"`; `minecraft:gameplay/respawn_anchor_works: false`; `minecraft:gameplay/can_start_raid: false`; and `monster_spawn_light_level` as an IntProvider set to 0 with `monster_spawn_block_light_limit: 0`. The `natural` field is gone, and mob spawning is actually disabled by the Vault biome's empty spawner lists. Lighting also needs `has_skylight: true` plus the visual attributes. This matters because a fresh reading of §3.1 writes JSON that fails to parse or does nothing. It belongs to T3, and the spec line needs correcting.

**#15 (with PR #117): the classifier as 26.1 forces it.** §11 "Ore Rarity Classification" still says to scan `ForgeRegistries.BLOCKS` for `OreBlock`/`DeepslateOreBlock`. Those classes do not exist in 26.1, where ores are `DropExperienceBlock`. The scan used the `#c:ores` tag, and reading vein count and height needed four access transformers (`CountPlacement.count`, `HeightRangePlacement.height`, `UniformHeight.minInclusive/maxInclusive`). Ores with non-extractable placement fell back to UNCOMMON. PR #117 then found that `getRarity` defaulting to UNCOMMON would have paid 5 Resonance for plain stone, so it added an `isClassifiedOre` check. This matters because the spec instructions are wrong for 26.1 and the fallback creates a payout bug. It belongs to T4, and §11 needs rewriting.

**#16: the generator pattern in §11 contradicts CLAUDE.md.** The §11 "Implementation pattern" snippet reads `OreVaultSavedData.getTeamSkills(teamId)` from inside the generator, which is the race CLAUDE.md item 1 forbids. #16 records the real shape: a `Supplier<SkillSnapshot>` refreshed on the main thread, `codec()` returning `MapCodec.unit(this)` because the generator is built per team in code, and `data/orevault/worldgen/biome/vault.json` with empty spawner lists. This matters because the spec snippet is the first thing a developer copies. It belongs to T4, and the snippet should be replaced.

**#45: cross-chunk vein generation.** This design is not in the spec. Each chunk derives the veins of its 3×3 neighbourhood from `seedFor(teamId, cx, cz)` in a fixed iteration order and writes only its own blocks. That is legal inside `fillFromNoise`, and the result does not depend on generation order. The design rests on one constraint: maximum horizontal vein extent stays under 16 blocks at full Vein Expansion, and that bound should be asserted in code. The 40% budget is checked per chunk, so a vein can be truncated differently in each chunk. The ticket's acceptance criteria are good test ideas: two adjacent chunks generated in either order produce identical shared veins, there is no grid artifact at chunk borders, and one vein record is emitted per vein. This matters because PR #153's veins are still chunk-local, and node effects such as Vein Expansion make the truncation visible. It belongs to T4, with the extent bound also relevant to T9.

**#91 (with the #141 comment): the VeinIndex design.** §11 says only to "persist a per-chunk index". The ticket specifies more:
- `veinId = (originChunkX, originChunkZ, ordinal)`.
- Storage is a NeoForge data attachment on `ChunkAccess`, in two parts: a membership map in every chunk the vein touches, and a remaining count only in the origin chunk.
- The origin chunk is resolved with a synchronous `level.getChunk`, so a decrement is never dropped.
- Member sets must be persisted, never recomputed from the seed, because budget truncation differs per chunk.
- Twin Veins clones take ordinals from a reserved range starting at `1 << 16`.
- Machine breaks decrement the index.

#141 adds what its consumers need: completion events rather than lookups, standing-ore count for abandonment, whether a non-ore block was broken inside the vein's bounds (Clean Cut), a per-chunk completed count, and resolution once per Ultimine operation. This matters because a whole node group is blocked on it, and the spec under-specifies it. It belongs to T4 (index built with generation) and T9 (consumers).

**#93: dimension deletion and re-creation.** The spec covers none of these steps:
- Call `markWorldsDirty()` on the removal path too, or a stale cache keeps ticking a deleted level.
- Close the level's chunk storage cleanly before touching files.
- Clear the cached generator, the snapshot and the key entries.
- Log the team id and the reason (disband or reset).

Most important, **persist the variant on team data**: restoring from disk hardcoded `Variant.BASE`, so an expanded Vault would come back after a restart with bedrock at Y=0 and its deepslate band unreachable. The ticket's acceptance test: create an expanded Vault, restart, and confirm bedrock is at Y=−64. This matters because it is a silent progression loss. It belongs to T11, with a T6 field addition.

**#94: the reset vote state machine.** Rules beyond §3.5:
- A member who disconnects mid-vote is removed from the denominator.
- A timeout fails the vote.
- Exactly one open session per team.
- The backup flag is carried on the session and gated on config.
- The machine is headless and pure, with tests at 1, 2, 3, 4 and 5 members, abstentions and disconnects.

This matters because §3.5 gives only the pass thresholds. It belongs to T11.

**#96: the reset backup.** The copy happens after chunk storage is closed and before deletion, because "a copy of a half-flushed region file is worse than no copy". If the backup fails, the reset aborts. The log records the path and the bytes written. There is no restore tooling, and disk usage belongs in server documentation. This matters because §3.5 states only the destination path. It belongs to T11.

**#102: fork mechanics that bit.**
- The free-option check must run before the point-balance check, or an empty pool refuses a 0-cost pick.
- Refunding a parent to zero clears its option.
- `ore_working` tiers 4 to 6 are hidden unless Ore Doubling is chosen and Mekanism is loaded.
- §6.1 still gives Ancient Traces the prerequisite "Rare Focus T2 *or* Full Spectrum, *and* Vault Expansion". A prerequisite list is an AND of node/tier pairs and cannot express that, and a fork option has no tier 2. The old registry required `ore_attunement` T2 plus `vault_expansion` and checked the chosen focus at effect time.

This matters because the spec line cannot be implemented as written, and the data model must decide how OR prerequisites work. It belongs to T8.

**#106: Disturbed Zone mob tagging (post-1.0).** The tag is a persistent data attachment that survives chunk unload and restart. It records the originating zone and the team, is cleared on death and despawn, and is never set on mobs that arrived another way. Test: a zone mob killed 100 blocks out still counts. This matters because the spec only says mobs stay zone mobs. It belongs to the post-1.0 epic, not to T1 to T14.

**PR #111: SavedData versioning decisions.** §11 has `dataVersion` but not these rules:
- `PlayerStats` and team data are versioned independently.
- Version 0 is reserved for unversioned legacy tags.
- A tag from a newer build loads best-effort and logs WARN instead of throwing.
- `migrate()` is a fall-through chain.

PR #111 also says `TradeoffToggle` keeps `SkillTree` pure: the core takes `insideVault` as a boolean, and the `ServerPlayer` overload is a zero-logic adapter. Both enabling and disabling a toggle are denied inside the Vault (disabling is the actual exploit), and a denial leaves state untouched. This matters because these are the persistence conventions T6 should adopt from day one. It belongs to T6 and T8.

**PR #115: level is derived from the pool.** The stored level is a display cache. Deriving it means a `curve_divisor` change re-levels a team without clawing back points. The PR's two regression tests are worth carrying: a pool that resets on level-up, and a gain that crosses several thresholds paying only one. This matters because it is not in the spec and decides the persistence shape. It belongs to T5 and T6.

**PR #126: network rules.**
- Payloads register on the mod bus via `RegisterPayloadHandlersEvent`. On the game bus the symptom is an unknown-channel disconnect, not an error.
- Serverbound packets carry intent only, never a cost or a result.
- `ToggleTradeoff` carries no desired state (the server flips), so a replayed packet cannot force a state.
- Handlers run through `enqueueWork`, because SavedData is main-thread only.
- Real purchase and toggle handlers, not stubs.

This matters because it is the security model of the only thing worth cheating at. It belongs to T7.

**PR #129: the §11 FTB hook list is wrong about the Tome.** §11 still says `TeamEvent.PLAYER_JOINED` grants the Tome. The only FTB match is `PlayerJoinedPartyTeamEvent`, which never fires for a solo player. The grant must hang off vanilla `PlayerLoggedInEvent` and be tracked with a persistent-data flag, not an inventory scan, so a player who binned the book is not re-granted one every login. The §13 checklist already notes the login event, but §11 contradicts it. It belongs to T7, and §11 needs a fix.

**PR #135: migration and registry tests.** A migration keeps frozen copies of the costs it migrates from, never live constants. Migration is idempotent, and a test asserts that a second pass refunds nothing. `NodeDefsConsistencyTest` checked:
- every prerequisite resolves to a real node at a reachable tier;
- keystones only in Mastery, pacts never there;
- exclusivity is mutual;
- level requirements are monotonic;
- every gate is reachable from earlier clusters.

This matters because these are the transcription-error guards for a registry of about 80 nodes. It belongs to T6 and T8.

**#149: session tracking traps.**
- Death exits do not fire `PlayerChangedDimensionEvent`, because respawn bypasses `ServerPlayer#teleport`. A per-tick guard ends any trip whose player is no longer in a Vault. Trips are keyed by UUID, so the respawned entity resolves to the same trip.
- `deepestY` used 0 as both "no record yet" and a real record. A record at exactly Y=0 was lost to the next break, which revived Shallow Grace. It needs a has-record flag.
- Creative breaks never reach `dropResources`, so they are not counted. That is the desired result, but it follows from the hook, not from a check.
- The `SkillSnapshot` carries tiers via `Map.copyOf`.

§11 "Vault Session State" says exits are watched through the dimension change, which misses death. It belongs to T6 and T9, and the spec needs the death case.

### Developer

**#14: the dimension creation path.** The steps that worked: look up the dimension type and biome holders, register the `LevelStem` by unfreezing, registering and refreezing the `MappedRegistry`, build the `ServerLevel` through NeoForge's public constructor with `DerivedLevelData`, then add the world border listener and fire `LevelEvent.Load`. It needs an access transformer on `MinecraftServer.storageSource`. This matters because §11 describes it in one sentence. It belongs to T3.

**#17: the startup crash cascade.** A missing block id throws during `RegisterEvent`, which rolls the whole registry back to vanilla and floods every later mod with "Trying to access unbound value". Symptom: removing Ore Vault makes other mods' errors disappear. LESSONS has the fix but not this misleading symptom. Also from PR #108: the frame needs a drop-self loot table, or it drops nothing regardless of tool. It belongs to T2.

**#18: portal block details.**
- Creative mode bypasses hardness −1, so cancel `BreakBlockEvent` for portal blocks, and keep creative-plus-sneak as an escape hatch for stuck portals.
- `Level.isClientSide` is a method in 26.1.
- The portal model is a 4px two-face slab matching the collision shape.

It belongs to T3.

**#20 and #21: 26.1 renames hit while building the portal path.** None of these is in the CLAUDE.md table:
- `Inventory.items` is private; use `getNonEquipmentItems()`.
- `InteractionResult.sidedSuccess` is gone; use `SUCCESS`/`SUCCESS_SERVER`.
- `BlockableEventLoop.tell` became `schedule`.
- `Player.displayClientMessage` split into `sendSystemMessage` and `sendOverlayMessage` (#78).
- `Level.random` is protected; use `getRandom()`.
- `Entity.getServer()` is gone; cast `level()` to `ServerLevel`.
- `CompoundTag.getCompound/getInt` return `Optional`.
- `contains(String, Tag)` became `contains(String)`.
- `ChunkPos.x/z` are private; use the accessors.
- World spawn is `getRespawnData().globalPos().pos()`.

It belongs to T1 to T3, and the CLAUDE.md table should grow.

**#35 (with PR #132): the sync packet nobody sent.** `SyncTeamProgress` existed and was never sent. It must go out on login, after every Resonance gain and after every purchase. The payload nests one `TreeProgress` per tree because `StreamCodec.composite` caps at six fields. `syncTeam` needs a direct player lookup when the team-member list comes back empty (the same hole as the teamSize bug). The Tome is not a pause screen, because pools move while it is open and pausing singleplayer would freeze the mining that feeds them. It belongs to T7.

**#68: anchor recipe remainder.** Crafting must return the same, possibly damaged or NBT-bearing, igniter rather than a fresh one. That may need `craftingRemainder` handling in code, because JSON alone may not express it. It belongs to T13.

**#77: safe surface search.** Force-generate the target chunk, read the `MOTION_BLOCKING` heightmap, scan down for a two-high air pocket, and fall back to the default entry Y. The return portal is placed on the real surface the same way. The spec mentions spawn safety only for personal entry points. It belongs to T3.

**#81: the warp overlay.** `entityInside` must call `entity.setAsInsidePortal(this, pos)` on the client as well. The wavy overlay is driven by the client-side `PortalProcessor`. All team and tier gating stays server-side. This matters because the bug is silent: the teleport still works, and the overlay simply never shows. It belongs to T3.

**#84: portal tint implementation.** One near-white texture, tinted through `RegisterColorHandlersEvent.BlockTintSources`. The colours are #66FF66 (common), #5555FF (uncommon), #CC33FF (rare) and #FF9933 (legendary). `DistExecutor` is gone. The spec has the variants but not the hex values. It belongs to T2/T3 and T13.

**#92 (with PR #117): drop pipeline details beyond §11.**
- Skip the Fortune re-roll entirely when bonus Fortune is 0, so drops other mods appended survive.
- Re-rolled stacks must be wrapped back into `ItemEntity`, keeping the original position and motion convention.
- Guard against an empty drop list. The first test caught a re-roll with no template entity.
- Nonsense contributions (negative Fortune, a 0× multiplier) are ignored.
- The team id comes from the dimension, not the breaker, so machine breaks resolve a team.
- A fake player is still a `Player`, which is how most machines break blocks. Treating them as machines needs runtime mod detection. This is an unsolved hole in the machine rule.
- Accepted gap: mods that build drops themselves never fire `BlockDropsEvent`.

It belongs to T5, with the fake-player hole also relevant to T12.

**PR #108: playtest-blocker lessons not in LESSONS.**
- Portal dissolve must collect every invalid portal block, then remove them all. The neighbour cascade cannot work because `updateShape` re-validates only on off-plane neighbours.
- The stuck death screen after the sky-spawn bug came from `isInvulnerableTo` returning true while `isChangingDimension()` holds.
- Restore parses directory names back to team UUIDs, skips unrecognised ones, and does not let one failure abort the rest.
- `markWorldsDirty()` and `forgeGetWorldMap()` are `@Deprecated // Forge internal`, and no supported API exists, so dimension creation should be re-tested on every NeoForge bump.

It belongs to T3.

**PR #109: return-portal tier storage.** No stored high-water mark is needed. Each tier is a distinct block, so the placed blocks record it, and `tierOfPortal` reads it back. This is simpler than #98's "store on team data". It belongs to T3.

**PR #110: refund robustness.** Persisted tiers load unvalidated, so `refundCost` needs a bound guard. `skillPointsInvested()` threw an NPE on a node id no longer in `NodeDefs`, which means a renamed or removed node would crash on load. That was flagged for its own ticket and never fixed. Separately, `ceil` gives 240 points for a 225-point tree (Animus 60 for 47); the owner never ruled on this. It belongs to T6 and T8, and the rounding question goes to the balancer.

**PR #116: orb entity specifics.**
- Orbs are immune to damage, because a lost orb is lost progression.
- An entity type registered without a renderer kills `runClient` at startup, because the renderer table is self-tested, while a dedicated server runs happily. Register at least a placeholder renderer.
- `updateInterval` 2 instead of vanilla's 20 keeps server-authoritative movement smooth.
- The startup log line `Resonance curve: 30 levels, N points each, X total Resonance over 100h` is a cheap check.

It belongs to T5.

**PR #122: igniter capabilities.** Call sites ask by capability (`entryPointCapacity`, `hasInstantTravel`, `canResetVault`), never by tier number, because the tier for a capability already moved once. Entry-point storage grew from `{x,y,z}` to `{points:[...], selected}` and must still read the old shape. Slots fill before they overwrite, and an overwrite replaces the selected slot. Until the waypoint UI exists, a right-click in the air cycles the selection. It belongs to T3.

**PR #127: the client entrypoint.** Common code installs nothing client-side. The client registers its handler via `ModNetwork.setClientHandler` and the Tome opener via `TomeItem.setScreenOpener`, so dependencies point one way. Client team progress is `null` until the first sync, so the screen shows "connecting" rather than level 0. Client state is cleared on `LoggingOut`, or the next world shows the previous server's numbers. It belongs to T7.

**#141: completion-group constraints.** Clean Cut needs the vein's Resonance withheld and paid retroactively on completion, or the bonus cannot reach blocks already mined. Ultimine sweeps resolve completion once per operation. Build Vein Sight first, in the same PR. It belongs to T9.

**#142: exploration-group constraints.** Frontier Bonus freshness is an in-memory map that expires after 10 minutes and does not survive a restart. Whether Pathfinder's Claim and Chapter's End share one fired-chunk structure is an open decision. The Homeward Seam anchor must be cleared on reset, so T11 has to know about it. It belongs to T9 and T11.

### QA

**#6: unlock-rule tests.** A table-driven test is needed for unlock without the prerequisite, below the level, and without enough points. The old tree went a week without one. `SkillTree` is pure, and its unlock validation is what stops a client claiming a node it has not earned. It belongs to T8.

**#19: portal-shape tests.** Keep the geometry pure behind a `View` interface. The scan must work from a click on any frame block, not only the bottom row. Over-long runs fail closed by returning `MAX_STEPS + 1`. The seven cases: 2×3 valid, 22×22 reject, incomplete side, obstructed interior, existing-portal interior accepted, Z axis, non-frame click. It belongs to T3.

**#26: machine rule needs a gametest.** "No orb for a machine-broken block, covered by a test" was left unticked because `VaultBreakContext` needs a `ServerLevel`. Write it as a gametest from the start. It belongs to T5.

**#82: diagnosis technique.** Register `LeftClickBlock`, `BreakSpeed` and `BreakBlockEvent` listeners at LOWEST priority to see what other mods did. Log the server tick at dimension creation. Use a `/orevault diag` that prints destroy progress, `mayInteract`, spawn protection, border and gamemode. This technique found #89, and it is worth keeping for any "the world is broken" report. It belongs to T3 and T14.

**#105: performance measurements (deferred by the owner).** The list: generation throughput at high density, vein-index size and save time, drop-pipeline latency with every stage on, 28 force-loaded chunks per team across teams, N ticking Vault levels, and Hoarder's non-despawning orb counts. Results go in `KNOWN_ISSUES.md` with the hardware. The owner said performance waits, so this is a backlog idea only. It belongs to T14.

**PR #119: `/orevault testore`.** A cube fill of radius up to 32 (65 blocks a side) around the player, so the loop takes a minute rather than an hour. Its safety rules:
- refuse outside a Vault;
- require gamemaster permission;
- skip bedrock, all portal variants, frames and the caller's two blocks, matched by type not holder;
- report the ore's live classification and payout.

Config accessors read inside a Brigadier `requires` predicate must guard on `SPEC.isLoaded()`, because a throwing predicate breaks every `/orevault` subcommand. It belongs to T5 and T14.

**#143: survival-group tests.** The max-heart modifier must be removed on leaving, on logout and on death, with health clamped. It must never apply twice on re-entry or relog. A dropped item's despawn exemption must be cleared when it is carried out of the Vault. None of this is unit-testable, so it goes in a gametest. It belongs to T9.

**#144: Molten Seam test.** A gametest that generates chunks and asserts no lava is exposed to an air-adjacent face. Ancient debris must never be smelted by lava proximity. It belongs to T9.

### Reviewer

**PR #150 review: Highwater Mark repaired a copy.** `BlockDropsEvent#getTool()` is a copy made before the live stack is damaged. Repairing it does nothing to the player's tool, and the tier-2 armour spillover was computed from the copy. The fix is to repair `player.getMainHandItem()`. The bug was never fixed. Any effect that wants to change the held tool must not use the event's tool. It belongs to T9, and it is a review checklist item.

**PR #146 review: the tree hit-test ignored the viewport.** `nodeAt` ran with the raw mouse position every frame. With the tree scrolled, hovering the header or tab bar hit nodes scrolled off-screen, lit their chains and overwrote the header tooltip. The fix is `area.containsPoint` before hit-testing. It belongs to T8.

**PR #150: other second-look items.**
- Long Delve needed an access transformer on `FoodData.exhaustionLevel`, because vanilla has no exhaustion event or getter. It refunds its share of each rise, ignores falls (vanilla spends exhaustion in 4-point chunks), and keeps the baseline current while unbought, so a mid-trip purchase does not refund the whole trip.
- Highwater's armour split gives the remainder one point each to the first pieces, because integer division would make the tier do nothing at shallow depth.
- `BreakSpeed` runs client-side without knowing Delve stacks, so the cracking animation can lag; this is cosmetic.

It belongs to T9.

### Designer

**#30: anchor waypoint slots.** Setting an anchor as a return point consumes one of the player's personal entry-point slots: one at Tier 2, three at Tier 3 and above. §3.4 does not say so. It belongs to T10.

**#69: art direction not in the spec.** Portal tiles are based on the iron-ore texture structure (stone body with embedded clusters), with the clusters in the tier colour. Igniter textures should use the same green, blue, purple and orange, not the placeholder gray, gold, cyan and purple. The portal is translucent and animated if possible. `pack.png` is 64×64 or 128×128. It belongs to T13.

**#71: advancement list.** First Vault entry, first skill point, Vault Expansion, and optionally fully invested tree. The Disturbed Zone advancement is post-1.0. It belongs to T13.

**#74: Vault Recall item.** Not in the spec at all. Right-click binds a point in the current dimension (auto-binding on first use). A second mode teleports back across dimensions, routing through the team Vault. It is stored as `orevault_recall`, is expensive (candidate recipe: ender pearl, Resonance Crystals and netherite scrap), has a use cooldown and a short delay, and reuses the safe-footing search. The owner requested it in playtest. It belongs to T13.

**#83: Vault sky colours.** Sky #78a7ff and fog #c0d8ff, set in both the biome effects and the dimension-type `visual/sky_color`/`visual/fog_color`. It belongs to T3.

**#90: post-1.0 epic notes.** Soul Harvest drops currency for a shop that was never designed: either design the shop or cut the node, and never ship a keystone whose reward has no use. The Ore Memory tab tracks 16+ stats, and "six or so would carry the same value". The spec still lists all of them. This is a scope decision for the post-1.0 epic.

**#95: reset countdown details.** Action bar plus a sound on each of the last 5 seconds. The countdown is cancellable. The respec window must survive a server restart. There is a discrepancy to settle: §3.5 step 3 sends players to Overworld spawn, while the ticket says their saved return position with spawn as fallback. It belongs to T11.

**#148: half the prerequisite edges cross clusters (open).** 27 of 52 edges cross cluster boundaries. Five point at Stone Memory and four at Vein Expansion, so long lines leave Prospecting across a 6008-pixel page. Three options were laid out and never decided:
1. Let anchor gates carry it and drop the edges, which needs a migration.
2. Keep the data and stop drawing cross-cluster lines (renderer only).
3. Move the load-bearing nodes to the spine.

A test should pin the chosen property. This matters because it is the open complaint about the tree's readability. It belongs to T8 and needs a design decision.

### UI-UX

**#78: teleport feedback.** Teleport feedback uses action-bar overlays (`sendOverlayMessage`), not chat, for arrival, return and failures. It belongs to T3.

**#101: one outline renderer.** Prospector's Eye (other veins nearby) and Vein Sight (the current vein) both outline blocks through stone from server-sent positions. Build one renderer and feed both. It belongs to T9.

**PR #121: config screen.** The Mods → Config button only appears if the mod registers an `IConfigScreenFactory`, using NeoForge's `ConfigurationScreen` behind the client gate. Every option gets an explicit `.translation(...)` key, because derived keys through nested sections can show raw keys. Debug defaults are off once the screen exists. It belongs to T1 (config) and T13.

**PR #134: tree-tab input rules.**
- A purchase fires on mouse release, and only if the pointer moved under 4px, or grabbing the canvas buys nodes.
- Left-click buys and right-click toggles, split by button so a maxed tradeoff stays toggleable.
- No optimistic update: a node changes only when a sync says so.
- The server syncs after a refused purchase too.
- `SyncSkillTree` is a separate per-player packet, so the per-orb packet stays small.
- Ultimine nodes are removed before layout so they leave no gap.

It belongs to T7 and T8.

**#139: the Outgrown state.** One `isOutgrown(nodeId, teamLevel, playerStats)` predicate, called by the renderer. Shallow Grace retires on `deepestY`. Apprentice's Ledger never draws as Outgrown, because its cap just stops mattering, and its counter resets on Vault entry, not login. It belongs to T9.

**#147: per-tier descriptions (open, owner feedback).** "I don't want redundant statements, vague statements, massive blocks explaining all levels of a skill." The target:
- The description shows the current tier's values, with a dimmer "Next:" line naming only the delta.
- Nothing repeats what the tooltip already prints (exclusivity, inert fork parent, costs, "inside the Vault").
- Under 160 characters for the current line, under 80 for the next.
- Templated `%s` filled from `NodeCosts` for scaling nodes; per-tier keys for nodes whose tiers add mechanics.

The ticket also asks for a survey of Diablo 4, WoW, PoE, Last Epoch and Grim Dawn. Rejected: static short text, dropping the next line, and a per-tier table. The acceptance criteria include `NodeLangTest` checks for length, all tiers present and no repeated phrases. This is a direct owner request that supersedes part of the 2026-09-09 log entry. It belongs to T8 (writer for the copy).

### Writer

**#124: the guide book.** Six chapters: getting in, your team's Vault, Resonance, the Tome, igniter tiers, getting out. A vanilla `written_book` is the cheap path, and Patchouli would be a new soft dependency. Acceptance criteria:
- granted once, on first join;
- all text in lang;
- re-obtainable;
- states plainly that machine-broken blocks pay zero Resonance, the rule tech-pack players hit by surprise.

The Tome chapter waits for the Tome. Not in the spec. It belongs to T13.

**PR #125: igniter tooltips.** Each tier lists its own capability plus every one below it, and the upgrade hint goes last. The lines come from the same `Tier` accessors as the behaviour, so a capability that moves tiers cannot leave stale text. It belongs to T13 and T3.

### Balancer

**#24: config ranges.** `target_play_hours` runs 1 to 10000 and `curve_divisor` 0.01 to 100. The spec gives defaults but no ranges. It belongs to T5.

**#145: Mastery keystone tensions.**
- Silent Stone's 3 Resonance per second is specified as a config value. That contradicts the 2026-09-27 rule of no config before a reader and no admin knobs for derived rates, so decide which wins.
- Full Spectrum needs each Ore Focus option's effect addressable independently of the chosen one. Confirm that shape before building the fork effects.
- Resonant Symbiosis must apply after the §4.2 team division, with a test pinning the order.

It belongs to T9.

**#151: Long Delve's hunger (open).** Four stacks × −25% hunger is 100%: hunger stops in the Vault at the tier-2 cap. The code clamped it at 100% and asked for a decision. Options: leave it as the reward, cap below 100% (75%), or make hunger stacks multiplicative (0.75⁴ ≈ 32%, a documented exception to §11). Acceptance: a log entry, §6.1 states the outcome, and a playtest at four stacks. It belongs to T9.

### Playtester

**#152 and PR #153: measuring the baseline.**
- Test in a fresh Vault, because old chunks keep their old generation.
- Check the startup log reads 72,050 total Resonance over 100h; 196,500 means the old 600/hour constant.
- Measure density with `/fill ~-8 40 ~-8 ~7 79 ~7 air replace #c:ores` (16×16×40). Expect about 300 replaced, 250 to 360 normal.
- In the expanded deepslate band, `/fill ~-8 -63 ~-8 ~7 -1 ~7 air replace #c:ores` should give about 950. Zero means `VaultLayerConfig` reports only the first stone layer.
- Check the rarity mix by eye (about 70/25/5), vein sizes by rarity, and diamond concentrated low.
- Time an hour of mining against the 7,200 blocks per hour assumption.

PR #153 also records implementation choices not in the spec:
- `OreClassifier` publishes a `volatile` reference to an immutable pre-sorted table, so generation never sees a half-built map.
- Bands are keyed on the filler block, not on layer position.
- There is a fallback that keeps density right in a pack with no rare ore.
- Quote test counts from `--rerun-tasks`, because a cached `test` reported success without running.

The #152 playtest criterion was never met. It belongs to T4 and T14 (the developer takes the implementation notes).

**PR #146 and PR #150: testing steps.** Both PRs carry step-by-step client checklists. For #146: hubs, pan and zoom limits, edges ending on box borders, "Volatile Veins: Ultimine Gambit" fitting, descriptions free of hedge words, Stratified naming depths, wrapping. For #150: reach level 20 with `testore 5 minecraft:diamond_ore`, which needs a RARE classification, about 1,331 ores and about 16,000 Resonance. The #150 list also gives level thresholds L6 988 to L18 10,189 (on the old 600/hour curve). The behaviour checks: Kindred Rock at 1,000 iron, Highwater at depth, Deep Habit reset on exit and relog, the Long Delve AFK guard, death ending the trip, and nothing counted outside the Vault or for machines. None of this was ever run, and it makes a ready template for the T8 and T9 playtests.
