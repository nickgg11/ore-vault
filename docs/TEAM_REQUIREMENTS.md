# Team requirements

This is what each of the ten roles in `.claude/agents/` needs from the others, what it checks, and what it
produces, task by task. The roles wrote their own sections on 2026-10-26, critiqued each other's in two
rounds, and the Manager merged the result, settled the disputes the documents or measurable facts could
settle, and sent the rest to Nick.

How to use it: before starting work, read your own section and the section of every role whose work you
review (the review matrix in `CLAUDE.md` says which). The settled decisions below bind every role; a
section that disagrees with one is wrong and should be fixed, not followed. When a decision changes,
whether from Nick, a playtest or a ruling, the Manager updates this file in the same PR that records the
change, and the Writer adds the dated `DESIGN_LOG.md` entry.

Sources of truth stay in the order `CLAUDE.md` gives. Spec references are `§N` of
`OreVault_Design_and_Spec.md`; log references are dates in `DESIGN_LOG.md`. Task numbers T1 to T14 are this
plan's, not the pre-restart `[NN]` ids or issue numbers.

## Settled decisions

Each row was disputed in the drafts or critiques. "Settled by" names the source that decided it. Anything
that changes what the player experiences, changes scope, or cannot be undone went to Nick instead (next
section).

| # | Dispute | Ruling | Settled by |
|---|---|---|---|
| S1 | Does the spec still need the baseline, 220 ore/h, hub layout and 2026-09 log entries added? | No. They are present. The docs PR covers only the corrections listed further down. | §3.1, §4.3 step 5, §6.1 "Shape of the tree", §8 Tab 1; log 2026-09-07, 09-09, 09-27 |
| S2 | Is there a review matrix? | Yes, in `CLAUDE.md` "Review matrix". It is the only one; this file does not copy it. The Designer reviews screen work through that table's ui-ux row ("designer, art fits"). | `CLAUDE.md` |
| S3 | Is the hub layout playtested? Is the 3%/6% baseline? | **Amended 2026-10-26:** the hub layout was replaced by the PoE tree (`DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree"). What carries over from the two hub playtests is in §8 Tab 1: the opaque page, edges border to border, zoom from 0.2x to 1.5x on the pointer. Not yet played: the PoE layout, node icons, the path highlight, the level rings and named lock reasons as built, per-class art. Those are T8's session. Was: the hub concept was played twice and is not reopened. The baseline is decided but unplayed; T4's session checks it. Neither is a question for Nick. | Log 2026-09-07 and 09-09 entries headed "playtest of the hub renderer"; lessons doc ("none of the three has been playtested"); log 2026-09-27 "Still open" |
| S4 | Where builds and runs happen; QA's request to allowlist Maven and Mojang hosts for cloud sessions | On Nick's PC, through a Remote Control session. The allowlist request and the Xvfb plan are withdrawn. No result is reported unless it came from his machine. | `CLAUDE.md` Build (Nick, 2026-10-26) |
| S5 | "Vault Resonance" prefix in UI text | No prefix. Plain "Resonance" everywhere; renaming is a late-development question. | `CLAUDE.md` Conventions (Nick, 2026-10-26) |
| S6 | Anchor gates as stated points (10/25/45/55/70/100) or as fractions | **Retired 2026-10-26:** the PoE tree has no anchors or gates; see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree". Was: Fractions of `totalTreeCost`, rounded up: Prospecting 0, Excavation 15, Assay 39, Broad Cut 39, Metallurgy 71, Claim 89, Deep Lore 110, Mastery 160. The point figures date from the 225-point tree. | §6.1 ("pinned to a fraction"); log 2026-09-07; the §6.1 cost columns sum to 354 (parsed) |
| S7 | `totalTreeCost` over the full registry (354) or excluding conditional content (311) | **Retired 2026-10-26:** every node costs 1 point and points per level are pinned at 5 (§4.3); see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree". Was: Full registry, every pack: 354, so `pointsPerLevel` is 12. With 311 the tree is not buyable at level 30 (330 points against 347 holdable). The Writer withdrew 311. | §4.3 steps 1 and 3; arithmetic |
| S8 | A stub node at T5 or the full node data first | The full node data first, as T8a, before T5's point award. **Amended 2026-10-26:** T8a is the node data model and the tree layout file together (§12 T8a): node and skill ids, rings, classes, marks, edges, petals, mod conditions, reserved ids, and the layout tests of §6; no effects. There are no costs (every node is 1 point) and no level requirement besides the ring. Points per level are pinned at 5 (§4.3 step 2), so "with one node, `pointsPerLevel` is 1" is retired. | §4.3 step 3; amended by §4.3 step 2, §6, §12 |
| S9 | First purchasable node for the loop | Stone Memory T1, bought by debug command at T5 as a technical check: visible on the next stone block, no Resonance or baseline effect. The player-facing loop milestone is the first node bought through the Tome (T8). Vein Expansion T1 is the first T9 generation node, verified with the density command. Guide Vein is not used. | Vein Expansion T1 is +15% over about five veins in ten minutes and a modifier before the measured baseline. Guide Vein is a generation node: it changes only chunks generated after purchase (§6.1 Guide Vein), so it shows nothing near arrival. **Evidence restated 2026-10-27:** the earlier "about +0.5% of a chunk's ore" is retired; since `DESIGN_LOG.md` 2026-10-26 "The baseline measured against vanilla, and Guide Vein made visible", §6.1 puts it at about +32% of a chunk's ore. The ruling stands |
| S10 | A provisional tree view for the T5 node | None. Command purchase until T8's approved renderer. | Lessons doc: renderer built three times |
| S11 | T5 pacing check "level 1 inside the first hour" | Replaced by a measurement: solo, in fresh chunks, zero points, buying nothing, tool named by the Balancer. **Amended 2026-10-27:** 60 minutes, or two 30-minute sessions pooled, and pacing is judged on ore an hour from the S44 per-minute readout; level times are reported as observations, not pass or fail. Bands of ±20%: blocks 5,760 to 8,640 an hour, ore 176 to 264 an hour; observed level 1 expected at about 7 to 11 minutes, level 2 at about 15 to 23. Ore per hour outside its band means the curve is re-derived before any T9 node merges. Was: one 30-minute session with the level times as pass or fail. | §4.3 curve (about 76,259 total at 218.9 ore an hour on the 68/25/7 mix; level 1 costs about 113; Balancer, 2026-10-27); a 2.7x mispricing still passes the old question; log 2026-09-27 "Still open"; QA's variance estimate (2026-10-27): 30 minutes is about 16 veins, roughly ±28%, wider than the band, and one level-1 time varies by roughly ±50% |
| S12 | How density is measured | Not `/orevault testore`, which fills a cube. A pure JUnit count over the placement function, then a read-only `/orevault density [chunks]` on a dedicated server. Tolerance: each band within ±5% relative over at least 81 chunks; rarity shares by placed blocks within ±2 points of 68/25/7 in the stone band and 60/30/10 in deepslate, on a vanilla-only pack, with Guide Vein off. | Old `VaultDiag` (fact); Balancer owns numbers |
| S13 | Generation model and the 40% floor | Veins are placed by count and size, never filled toward a target. The floor clips proportionally across vein counts, every clipped modifier is in the snapshot, and the floor test uses the full deepslate stack: it computes the stack's request from `OreBaseline` and asserts it is above 0.9, so the clip really fires (ADR 0009). **Amended 2026-10-27:** "132% requested" was a hub-tree figure nobody re-derived; the Balancer's estimate is 100 to 140% until T9 pins Vein Shaping and Stone Reduction, and the number is recorded then. | §3.1; Vein Expansion plus Proliferation alone reach 26.4%, so a test on those passes vacuously; Balancer (BAL-4) |
| S14 | T3 as three PRs or one; how the logout case is checked | One PR, three acceptance criteria, plus a login check that sends a player whose `in_vault` names a Vault they are not in to their return position. The gate is a scripted headless restart on every dimension PR; Nick confirms once in a client. | `CLAUDE.md` rule 2; lessons doc (#89's regression) |
| S15 | Who verifies the generator never reads SavedData | A static rule: ArchUnit, owned by the Reviewer. QA does not sign it as observed. `GenSnapshot` lives in `core.gen`, built on the main thread. | `CLAUDE.md` rule 1; the old `SkillSnapshot.of(OreVaultTeamData)` made worldgen depend on `data` |
| S16 | Persisted shape basics | `data_version` from the first field; the team-data shape agreed in an ADR before T5 opens. Available points derived, not stored. `level_reached` stored and monotonic. `deepest_y` absent means never entered: no sentinel, no flag. No stored derived totals. | `CLAUDE.md` rule 6; §4.3 step 4 (thresholds must not move under teams that passed them); Architect owns the data model |
| S17 | Save-shape tests as JUnit or gametests | JUnit through `CompoundTag` if T1 confirms it loads, as the old `PlayerStatsVersionTest` did; one gametest per storage mechanism (SavedData, chunk attachment, player attachment across death). `CLAUDE.md` Tests is corrected after T1 confirms. | Old test suite (fact) |
| S18 | Team data and the Tome grant at team creation | Team data is created on first write, never on `FTBTeamsEvent.TeamCreated` (the FTB Teams 26.1.2.4 name, §11 FTB Teams Event Hooks). The Tome is granted on first login and recorded as `tome_granted`. | `CLAUDE.md` rule 2's reasoning; §13 records that `PLAYER_JOINED` misses solo players |
| S19 | Disband and reset mechanics, whatever Nick picks | No level is deleted or moved while the server runs. Disband sets `disbanded_at` and evacuates online players; the folder is handled at the next start, before restore, and restore skips it. | §11 registry-desync warning; Architect, Developer, Reviewer |
| S20 | Vein index timing and storage | T4's placement returns vein extents as values (ADR 0004 decided in T4). The index is persisted in a versioned chunk attachment by its own ticket ahead of Vein Sight, written only for the chunk being generated; veins never cross a chunk border; player-placed ore is never indexed; chunks generated before it never complete. Chapter's End state lives on the chunk, not in `PlayerStats`. | §11 Vein Index; no player worlds exist before 1.0; a reset wipes chunks but not player files |
| S21 | What "machine-broken" means; machines and vein completion | A break pays only when the breaker is a `ServerPlayer` that is not a `FakePlayer`; no breaker pays nothing. A vein whose last block a machine broke fires no completion effect. | §3.4 ("never award Resonance, in any quantity, under any node") |
| S22 | §13 "Cross-dimension block-break Resonance award" | Deleted. | §3.4 |
| S23 | Silent Stone's rate as config | A constant. Its value is open for Nick. | `CLAUDE.md` rule 7; log 2026-09-27; §1 "config-light" |
| S24 | §9 "no direct import of Ultimine classes" | Ultimine is `compileOnly`; every Ultimine type is referenced only from `integration.ultimine`, entered after `ModList.get().isLoaded("ftbultimine")`. §9 is rewritten. | The 26.1 API is typed registration events, so §9 cannot be built as written; `CLAUDE.md` rule 5 keeps §1's intent |
| S25 | Whose Ultimine flag the UI follows | The server's, sent in the sync payload. Never the client's `ModList`. | §2; client and server mod lists can differ |
| S26 | Tome toolkit | Vanilla screens for 1.0. No FTB Library, no FTB `TeamProperty`. If FTB Library is ever used, `CLAUDE.md` gets an import boundary for it first. | The accepted shell was vanilla `TabNavigationBar` (log 2026-09-03, §13); rule 5 defines no FTB Library boundary; `TeamProperty` would import `ftbteams` outside the team package |
| S27 | Layout as a committed coordinate file or computed | **Superseded 2026-10-26 by §6 Layout data:** positions are authored in the layout file, a JSON resource in the mod jar that UI-UX owns (§6.1 Shape of the tree), with every node, edge, ring and petal anchor. `TreeLayout` reads it (§11 Tree Layout Data); the renderer holds no placement code. Nodes are icons with no text on the page (§8), so nothing is computed from text widths and there is no structure file, width function or golden layout output; the tests are the layout tests §6 lists. The Designer's `style.json` holds no geometry, and frames follow class. Was: computed at runtime by a ported `TreeLayout` from a structure file with no coordinates, with English golden files. | §6 Layout data, §8 Tab 1, §11 Tree Layout Data; `DESIGN_LOG.md` 2026-10-26; commit 953c9e6 moved layout to UI-UX |
| S28 | Nine-slice borders in `style.json` | In each sprite's `.mcmeta` under `textures/gui/sprites/`, not duplicated, once the Developer confirms the 26.1 sprite API in the sources jar at T7. | Vanilla GUI sprite system |
| S29 | QA's "no vertical placement without a prerequisite" property | **Retired 2026-10-26:** the PoE tree has no prerequisites besides adjacency, and §8 no longer has the "one outside the other" rule. Position is checked by the §6 layout tests instead: every node at distance at least its ring, the copies of a skill in ring order, no edge through another node. Was: replaced by the polar invariants and a radial test of §8's rule. | §6 Layout data; `DESIGN_LOG.md` 2026-10-26 |
| S30 | Node frames in T8 or T13 | T8. A layout cannot be approved with placeholder frames. | Anything visual needs Nick's yes on a rendered preview |
| S31 | Drawing Hoarder's Instinct larger because it is strong | No. Class decides visual weight; strength is a number for the Balancer. | §8 class table |
| S32 | Tooltip and name limits | Tooltips at most 240 GUI units wide and 20 lines; description wrapped at 220 px, at most 8 lines. Previews include 1280x720, at GUI scales 2 and 3 only: `Window#calculateScale` offers no scale 4 at 720 high (amended 2026-10-27). **Amended 2026-10-26:** nodes are icons with no text on the page (§8 Tab 1), so the box widths are retired: names at most 196 px in a 208 px box, anchors 228 in 240. "Prerequisites at most 4" is retired with prerequisites; the tooltip names the path cost, the highest ring on it and the lock reason instead (§8 Tooltip). A name's limit is now the tooltip's width. | Vanilla picks the largest GUI scale leaving at least 320x240; log 2026-09-07, 09-09; amended by §8 Tab 1 and log 2026-10-26 |
| S33 | What "matches the preview" means | In each named view: the same nodes visible, icons within 2 GUI px of their layout position, no truncated tooltip text, no edge across an icon, zoom readout present, tooltip line count equal, every class and state identifiable in greyscale. **Amended 2026-10-26:** boxes became icons (§8 Tab 1); "with names hidden" is moot, since no name is drawn on the page. | A browser render cannot match the game pixel for pixel |
| S34 | Reset button in the top bar; who must hold the Tier 4 igniter | Not in the top bar (its location is open). It shows when the viewing player holds a Tier 4 igniter, per §8; §3.5 changes to match. | Log 2026-09-03 ("accepted as-is"); §13 Polish; a client can see only its own inventory |
| S35 | Refunding a fork parent inside the Vault | Refused inside the Vault, since it clears the option. | §6.1 Forks (the restriction is on changing an option) |
| S36 | Lang key format | `node.orevault.<id>` and `node.orevault.<id>.desc`, written into §8. **Amended 2026-10-31 (settled, WRITER-17):** effect lines are `node.orevault.<skill id>.effect.<tier>` (tier 1 to copies; single nodes, notables, keystones and travel stats use `.effect.1`), numeric positional arguments only, units and signs in the lang string, one client formatter; shared `tooltip.orevault.next` ("Next: %s"), `tooltip.orevault.tier_owned` and `tooltip.orevault.travel_total`; the Outgrown reason is `node.orevault.<skill id>.outgrown`; every rendered effect line at most 2 lines at 240 GUI units; search matches every tier's text. | Writer owns lang; vanilla's order; the old keys; Nick's Q8 (Stone Memory's tiers differ in wording, so a tier argument cannot give a translator a whole sentence); Manager's round 2 ruling |
| S37 | "Ultimine Gambit" or "Volatile Veins: Ultimine Gambit" | "Volatile Veins: Ultimine Gambit". | §6.1; log 2026-09-07 moved the box cap to fit it |
| S38 | Banned description words | The old `NodeLangTest` list plus "slightly", "intensifies", "near-", matched as whole words. "About" and "roughly" pass when they qualify a number. Shown failing on "The mirror image." and "a moderate frequency". | Log 2026-09-09 |
| S39 | A new composition rule or an amendment | Amend §11: its contributor table lists every node that pays or costs Resonance or mining speed. How penalties apply is open for Nick. | §11 already says percentages sum and multipliers multiply |
| S40 | Disagreeing Resonance values | One constant for rare ore (12 and 12.5 exist today); the Balancer picks it and §4.2, §13, curve and orb read it. Vault Echo is 25/35/50. Stone Memory T3 pays on deepslate, not stone. | Log 2026-09-27 (one constant); log 2026-09-09 (the node's own text is the description) |
| S41 | Where per-tier effect numbers live | One effects-constants source beside `NodeCosts`, read by effect code, the lang test and the balance tests. | Lessons doc ("one constant read by everything") |
| S42 | Port the old pure classes or rewrite | Port, one ticket per class through normal review, into `core.*` with no Minecraft imports, without stored derived totals and without `NodeMigration`. Minecraft-facing adapters are rewritten. Nick can overrule. | `CLAUDE.md` (read the old commits before rebuilding); lessons doc "What went well"; Architect's purity findings |
| S43 | ArchUnit strictness | At T1, each rule shown failing once against a planted violation. | ArchUnit fails a rule matching nothing unless told not to, and a vacuous pass is not evidence |
| S44 | Debug tooling | The `/orevault` root is always registered; only the debug subcommands sit behind `[debug].enable_debug_commands`: density count, create a team's Vault, dump team data, wipe a team's progress, a per-minute blocks, ore and Resonance readout held in session state (never persisted), named-view client command. **Added 2026-10-27:** a snapshot readout printing the team's current generation `stamp`, applied generation tradeoffs, `vault_generation` and clip factor, and the `gen_stamp` of the chunk the player stands in, so each ADR 0003 rebuild trigger has a step (do it, read the stamp); and fault points that halt the process (`Runtime.halt`) after reset steps 3, 6, 7, 8 and 9 of ADR 0007, so a crash mid-reset can be tested. **Added 2026-10-31 (QA-38):** a break command that breaks the block at a given position in a Vault through a fake `ServerPlayer`'s `gameMode.destroyBlock` and prints whether the block is gone, so the headless restart check can show a restored Vault ticks (a gametest player exists only inside one `GameTestServer` lifetime). Removed at T14. | `CLAUDE.md` rule 7; no persisted diagnostic |
| S45 | When the docs PR lands | Before T3, not with T4. | A spec sample that shows the race should not survive into a ticket citing §11 |
| S46 | First-pass textures at T2 | Allowed at 16x16 when they pass the checks and Nick says yes on a contact sheet. No placeholder art is committed. | `CLAUDE.md` Textures (N2) |
| S47 | ADRs | ADRs 0001 to 0007 land as docs PRs ahead of T1, T3, T4, T6, T7 and T11, reviewed by the matrix row for system design. | `CLAUDE.md` matrix |

## Nick's answers

Nick answered `docs/OPEN_DECISIONS.md` on 2026-10-26. These bind every role the same way the settled
decisions do; the dated log entry is `DESIGN_LOG.md` 2026-10-26.

| # | Question | Answer | Work it changes |
|---|---|---|---|
| N0 | Pufferfish's Skills | Borrow its ideas, own the tree. Not a dependency. | T7, T8 as planned |
| N1 | Old saves | Pre-restart saves are not supported: data starts at `data_version` 1, and the old node ids go on the reserved list. Saved data stays versioned from its first field (S-rules unchanged). Writing migrations for tree changes after release is a stretch goal: Nick does not want an update to cost progression, but the mod may never be updated except to follow a Minecraft version. | Docs PR drops the §6.1 migration paragraphs; T6 |
| N2 | Texture size | **Blocks and items are 16×16, as vanilla.** The aim is that the 16×16 reads clearly as the thing it is, not a blob. GUI art is drawn at one texel per GUI unit. The 64×64 rule and the 32×32 floor are gone. | `CLAUDE.md`, §13, the Designer's asset table, the PNG size test (T13) |
| N3 | Reset button and readouts | A small Ore Memory tab in 1.0 with a Survey panel (depth gauge with band edges, death marker, Seismic Sense's readouts) and the reset button bottom right, shown only to a Tier 4 igniter holder. Stats panels stay deferred. | §8; T7, T11 |
| N4 | Gravel Purge and Guide Vein | The Vault generates gravel and clay by default and Gravel Purge removes them. Guide Vein waits on research into how vanilla chunk generation places ore (amount, depth, vein size and count per chunk); that research sets the baseline, which reopens the 2026-09-27 3%/6% figures, and Guide Vein is then retuned to be felt. **Done 2026-10-26** (`docs/research/vanilla-ore-generation.md`): Nick kept 3%/6%; mix, vein sizes, gravel and clay, the rarity rule and Guide Vein updated in §3.1, §4.2 and §6.1. | Research before T4; T4 layer config; T8a totals |
| N5 | Growth nodes | Growth nodes never switch off; their reward stops mattering as the team grows. Apprentice's Ledger stays as written. Shallow Grace counts only the team members currently inside the Vault and ignores those outside. No chat line when a node stops mattering. | §6 notation `[GROWTH]` wording; Shallow Grace's condition; T9 |
| N6 | Penalties | Multiply after the bonuses are summed, floored at zero. | §11 composition; T9 |
| N7 | "About 100 hours" | A team that buys nodes as it goes. Nodes keep their strength; the curve stretches after T14's measurements. | §4.3; T14 |
| N8 | Disband | Archive everything kept for the team: the Vault world, the tree, the stats. An admin command can purge archives. | §3.1, §9, §11; T11 |
| Na | Cloud build check | No. The automated Claude review on PRs stays; it does not compile, so builds stay on Nick's PC. | T1 |
| Nb | Portal cost | Corners optional; one Vault Frame per craft; no nether-style travel logic. | §3.2; T2, T3 |
| Nc | Art tools | `mc-asset` approved. Nick asked what the preview tool and Retro Diffusion are for before approving them. | T2, T8 |
| Nd | Animus row | Hidden until Animus is built, behind one switch that is easy to turn back on. | T7 |
| Ne | Resonance gain display | Nick read it as a second segment inside the Resonance bar showing the extra gain. To confirm with him before T5. **Answered 2026-10-27 (Q11):** both the Tome header bar and an on-screen bar inside the Vault, the on-screen one switchable off by each player; how the bonus share is drawn is settled on the preview. | T5, T7 |
| Nf | Prospector's Eye | Requires Vein Sight. | T8a |
| Ng | Silent Stone | Each active member below Y=0 earns 0.30 Resonance a second, raised by that member's counted Resonance bonuses (no penalties), and the pool is team-scaled like ore: sum / teamSize × (1 + 0.1 × (teamSize − 1)). About 1.4x bare ore at any team size (Nick, 2026-10-26, after he asked that it feel fair to both a solo player and a team of five). Which nodes count is in §6.1 Silent Stone. | T9; deepslate ore rate measured at T14 |
| Nh | Players inside at reset | Sent to the Vault's spawn point, where the portal arrives. Their saved return position may be inside rock that the reset replaced. | §3.5; T11 |
| Ni | Live Vault cannot unload | Everyone is sent out and the Vault regenerates at the next restart, with a message saying so. | T11 |
| Nj | Mekanism | Tags only. FTB Materials (in Nick's pack) ships Mekanism-style clumps, shards and crystals under `c:` tags with no Mekanism code, so Ore Doubling's Mekanism tiers resolve through tags; see `docs/research/ftb-materials.md`. | §2, §9; T12 |
| Nk | Cross-cluster prerequisites | **Retired 2026-10-26.** Nick first ruled out cross-cluster prerequisites, then replaced the hub tree with a Path of Exile style tree (one point per node, adjacency, no anchors or clusters), which makes the rule moot. The spec's §4.3, §4.4, §6 and §8 Tab 1 are rewritten for it; anchor, gate and cluster text elsewhere in this file is superseded. See `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree". | §6.1; T8a, T8 |

## Nick's answers, 2026-10-27

Nick answered the Manager's seventeen questions about the draft tickets on 2026-10-27. These bind every role
the same way; the dated log entry is `DESIGN_LOG.md` 2026-10-27, and the spec is changed to match. Where he
left no comment, the recommendation stands and the row says so.

| # | Question | Answer | Work it changes |
|---|---|---|---|
| Q1 | Whose switch decides Volatile Veins and Molten Seam, which change generation | A team vote. Each member's switch is their vote; it passes with more than half the team's members, or one of two in a two-member team; the result applies only once nobody is inside the Vault, then holds for every member. Every other tradeoff stays per player. Counting all members, online or not, is the Writer's reading of "majority", confirmed by the Manager on 2026-10-27: "50% works if there are only 2 people" counts team size, not who is online. The change is applied at the end of the tick in which the Vault becomes empty, counting non-spectator players. | §6.1, §11; ADR 0003 (snapshot), ADR 0005 (team field); T9 |
| Q2 | New vein positions on reset | Yes: the reset count (`vault_generation`) is part of the seed. | §3.1, §3.5, §11; ADR 0003, ADR 0007; T4, T11 |
| Q3 | Personal progress on a team change | Stays with the old team; the player starts fresh in the new one (option b). Rejoining finds it where it was left. | §9, §11; ADR 0005; T6 |
| Q4 | Playtest world carried from T5 to 1.0 | No comment; recommendation stands: carry over, added fields read a default, renames migrate. | §11; T6 onward |
| Q5 | Reference textures | The Designer drafts with `mc-asset` first; Nick reviews and says how to improve. No reference textures needed before T2. | Designer's inputs; T2 |
| Q6 | Portal frame says why it failed | No. Failure sound only, as with a nether portal. | §3.2 unchanged; T3 |
| Q7 | FTB Evolution 2 version | No comment; recommendation stands: pin the version current when the dimension path lands (T3), re-check at release. A copy of Nick's instance for QA is unanswered. | T3, T14 |
| Q8 | Stone Memory T3 and T5 | T3 pays 0.05 Resonance per deepslate block; T5's burst fires on 0.05% of stone. | §4.2, §6.1, §11; T8a constants, T9 |
| Q9 | Apprentice's Ledger trip | A trip starting within 10 minutes of the end of the last one continues its count. | §6.1, §11 Vault Session State; T9 |
| Q10 | Team tests | Several temporary dev client sessions on Nick's PC with different dev usernames. No second person is needed, before 1.0 or after. | QA, Playtester; T5, T8, T11 |
| Q11 | On-screen Resonance bar | Both: the Tome header bar and an on-screen bar inside the Vault, which each player can turn off (client config `hud.show_resonance_bar`). Answers Ne. | §4.1, §8, §10; T7 |
| Q12 | Tooltip current tier plus "Next:" | Yes. Current tier's effect, then a dimmer "Next:" line, both from the constants; the every-tier list leaves the tooltip. | §8; UI-UX tooltip rules; T8, T9 |
| Q13 | Show whether the current chunk was generated with current nodes | Yes, in the Ore Memory tab's Survey panel. Generation nodes' tooltips say "Only in chunks generated after you buy it." | §8, §11 Vein Index (chunk stamp); T7, T9 |
| Q14 | Limit the free respec window after a reset | Yes: a reset opens it only if none opened in the previous 24 hours of server time (`free_respec_opened_at`). | §3.5, §4.4, §11; ADR 0007; T11 |
| Q15 | Vault Anchor as entry point | Takes one of the player's entry-point slots. | §3.3, §3.4; T10 |
| Q16 | Reset vote screen opening by itself | No. A toast and a clickable chat line; the vote screen opens from the line. | §3.5, §8; T11 |
| Q17 | Advancements, guide book, Vault Recall | No advancements and no Vault Recall in 1.0. A guide is decided after the first-ten-minutes playtest; if one is needed, a hover-in-JEI-and-hold-a-key guide is preferred over a book. | §12; T13 |
| — | Party join and the player's solo team | A party join never disbands the solo team (FTB Teams 26.1.2.4 source read, `docs/research/neoforge-26.1-sources.md`, 2026-10-27), so nothing is archived and no exemption is needed. Nick left no comment on the conditional question, which the read made moot. | ADR 0007; T11 |

## Shared task list

Owner is the role that produces the work; reviewers are the matrix's. "Done when" is the gate the Manager
checks before "ready to merge". Every build, test and client result is from Nick's PC.

| Task | Owner | Reviewers | Done when | Needs from other roles |
|---|---|---|---|---|
| T1 Toolchain, empty mod | Architect (ADR 0001), then Developer | Developer, Reviewer, QA on the ADR; Reviewer, QA on code | `build`, `test --rerun-tasks` with XML counts, `runServer`, `runGameTestServer` with one passing and one deliberately failing gametest, `runClient` to the title screen; no committed `org.gradle.java.installations.paths`; ArchUnit rules each shown failing; `CompoundTag`-in-JUnit result recorded | Versions re-checked (Ultimine 26.1.2.6 in FTB Evolution 2); Writer's mod description; Nick on the CI question |
| Docs PR | Writer | Balancer, Developer | **Done by #158** (see "Spec corrections needed before T3"). Every item under "Spec corrections" fixed, or marked with the open question it waits on; log entries for the restart and the 2026-10-26 decisions | Balancer's rare constant and tolerances; the cross-cluster restructure (Nk, retired 2026-10-26) and the ore research (N4) for the items that wait on them |
| T2 Vault Frame and Crude Igniter | Developer; Designer for textures | Reviewer, QA; UI-UX and QA on art; Nick | Id-aware registration, creative tab, `items/` definitions, pickaxe tag under `data/minecraft/`; gametests for tag, drop and recipe; Nick sees both in creative and JEI and approves the contact sheet | Designer's `mc-asset` drafts for Nick to review (Q5); art tool approvals; portal cost answer; Writer's lang |
| T3 Dimension path | Architect (ADR 0002), then Developer | Matrix design row; Reviewer, QA, Architect on code; Designer, UI-UX on the portal texture | S14 and S19 hold; lazy create only through `findOrCreate`; one world-map insert that calls `markWorldsDirty()`; restore survives a bad folder; return position saved after success; scripted restart passes; level-unload spike result written down; Nick confirms logout-inside once | Designer's portal strip and four tints; Playtester's arrival checks |
| T4 Generator, classifier, baseline | Architect (ADR 0003 snapshot, ADR 0004 vein extents), then Developer | Matrix design row; Reviewer, QA, Architect, Balancer on code | S12 and S13 tests; seeding determinism; `Variant` against `dimension_type` JSON; density command within tolerance; Nick mines ten minutes in a fresh Vault and answers the T4 question | Balancer's tolerances (S12); QA's density procedure |
| T8a Node data model | Architect, then Developer | Balancer, Reviewer, QA, Writer | Tests pin 354, the gates in S6, 12 points per level, every fork option covering every parent tier, each gate reachable from the clusters above it. **Amended 2026-10-26:** 354, the gates, 12 a level and Nk are retired with the PoE tree; the tests are now the layout tests in spec §6 and 5 points a level (§4.3); see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree" | Balancer's node list; N4 and Nf |
| T5 Resonance loop | Developer | Reviewer, QA, Balancer, Architect | Orb modelled on `ExperienceOrb`; three-breaker gametest (S21); vanilla XP still drops; curve and scaling tests; pool persisted with `data_version` from its first commit; Stone Memory T1 bought by command and its XP seen; Nick's pacing session per S11 (60 minutes, or two of 30 pooled), its ore an hour inside the band and the level times reported as observations | T6 ADR agreed first; Balancer's rare constant and milestones; Designer's orb tint check; Playtester and QA's script |
| T6 Persistence | Architect (ADR 0005); Developer | Matrix design row; Reviewer, QA | Lands with T5's first field; golden key-set test per shape; fixtures load, including wrong-type keys; absent fields read their documented default; `SavedData` and the chunk attachment checked across a scripted dedicated-server stop and start, compared through the S44 dump (a gametest cannot restart), the player attachment across death by a gametest if the mock player respawns through `PlayerList#respawn`, else a playtest step; an unreadable team file is copied aside, never overwritten (ADR 0005); reserved ids | N1 |
| T7 Network and Tome shell | Architect (ADR 0006), Developer; UI-UX for screen structure | Matrix rows; Designer, Developer, Playtester on screens; Nick | Payload set in ADR 0006 (`docs/adr/0006-network-channel-and-tome-sync.md`); server validates every mutation; refusal payload with a lang key; pending state; header drawn after first sync; accepted top bar reproduced; `runGameTestServer` after each payload change; Nick's yes | N2, N3 and Nd; Writer's UI strings; Designer's shell art |
| T8 Tree layout and renderer | UI-UX (the authored layout file, named views, layout tests); Designer (node icons, class frames, state tints, badges, `style.json`); Developer (renderer over `TreeLayout`) | Designer, Developer, Playtester; then Nick | Nick's yes on the preview set drawn from the layout file with `tools/tree-preview` (§6.1); the §6 layout tests pass; renderer holds no placement code; client matches the preview (S33) at scales 2, 3 and 4 at 1920x1080 and 2 and 3 at 1280x720; first node bought through the Tome. **Amended 2026-10-26:** golden layout files and geometry proofs with random widths are retired with the computed layout (S27) | Node list, rings and layout file from T8a; Writer's names, descriptions and every tooltip line; Nick's preview approval |
| T9 Node effects | Developer, a batch of nodes at a time: the ring-0 nodes first, then §6.1 order (the tree has no clusters since 2026-10-26) | Reviewer, QA, Balancer, Writer; UI-UX if it shows in the Tome | Balancer's stacking review before the batch's tickets open; per-tier pure tests; gametest where a block or drop is touched; Fortune-before-Quantity test; every node answers "how does the player know it worked"; descriptions pass the lang test; the first-ten-minutes session (E9-S59, #363) runs after the ring-0 batch and answers Q17 | T5 pacing inside its band; N5 to N7; UI-UX mockups for Tome-visible effects |
| T10 Chunk loading | Developer | Reviewer, QA, Architect, Balancer | `TicketController` on the mod bus; tickets validated on load; a restored Vault's saved tickets reactivated at restore, as `prepareLevels` does for levels present at start (ADR 0002), checked by a restart with nobody joining; the ceiling has a reader; Anchor only inside the Vault; igniter returned as a crafting remainder; S21 holds | Designer's Anchor texture; Writer's messages |
| T11 Reset and disband | Architect (ADR 0007), Developer; UI-UX for screens | Matrix rows; Nick | Pure vote machine tested at 1 to 5 members online with ADR 0007's cases; S44 fault points after reset steps 3, 6, 7, 8 and 9, each followed by a start and a check; scripted reset with backup on and off, region files checked on disk; S19 holds; a reset within 24 h of the last free window opens none (Q14); the reset count changes the seed (Q2); other members get a toast and chat line, never a forced screen (Q16) | N3, N8, Nh and Ni; T3's spike result |
| T12 Integrations | Developer | Reviewer, QA, Architect | Dedicated server boots with no soft dependencies, with Ultimine only, with Mekanism; import greps pass; the Ultimine nodes hidden without Ultimine (Broad Cut, a cluster, retired 2026-10-26) | Nick on Mekanism; Balancer on Mekanism-only tiers |
| T13 Content | Developer, Designer, Writer | Matrix rows; Nick on art | Datagen recipes, loot, tags, blockstates; full `en_us.json`; lang coverage and PNG size tests; every texture batch approved on a contact sheet | N2 sets the PNG test's scope |
| T14 Release | Manager, with QA, Playtester, Balancer | All | Load test in FTB Evolution 2 on Nick's PC, on the version pinned at T3 and re-checked (Q7); debug block removed; `KNOWN_ISSUES.md`; §12 and §13 match what merged; full playtest against the milestone table; curve re-derived if measured rates are outside band | Every role |

Order rules. The playable loop comes first: no world-facing task T(n+1) opens until Nick has seen T(n) in a
client. Pure-logic work for later tasks (curve, classifier, node data, layout maths) may proceed while a
client gate waits. No T9 ticket opens before T4's baseline is measured and T5's pacing is inside band. No
renderer code before Nick approves the T8 preview.

## Spec corrections needed before T3

**Done by #158.** Re-checked against the spec on 2026-10-27 (E0-S2): every item below is fixed, or retired
because the PoE tree removed what it corrected (`DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a
Path of Exile tree"): §4.4's 354 points and 1,062 levels (a refund now costs XP levels per node, §4.4), the
anchor class in §6 notation, the §6.1 gates and cluster intros, and the cross-cluster item. The last open
item, §13 "Mod icon (`pack.png`)", was settled by E0-S16: §13 now reads "Mod logo as `logoFile` in
`neoforge.mods.toml`, not `pack.png`", and the Designer names the file `logo.png` at the resources root
(2026-10-27). Nothing in this list is open. The list below is kept as the record of what #158 did.

The Writer fixes these in one docs-only PR before T3. Each was checked against the current spec on
2026-10-26. Nick answered the questions these waited on (see "Nick's answers"); each item names the answer it
follows. Items that wait on the ore research or the cross-cluster restructure are stated as open in the
PR rather than waiting.

Header and footer. The header says version 1.1, last updated 2026-08-28; the footer says v1.0. Use one
version and name the restart.

§2. "Forge Version" and `mods.toml` become NeoForge and `neoforge.mods.toml`. Mekanism's "reflection or the
Forge ore dictionary" goes: tags only (Nj).

§3.1. The seeding paragraph cites "regenerated on reset (§7)"; reset is §3.5. The disband paragraph is
rewritten to S19, archiving the team's world, tree and stats (N8).

§3.2. Add that the return position is saved only after the teleport succeeds. State the Vault Frame
recipe's yield: one frame per craft; corners optional (Nb).

§3.3. "Runic Attunement node (§6.1, Fortune branch)" is in Metallurgy.

§3.4. "Forge Ticket System" becomes NeoForge's `TicketController`. "Vault Echo grants vanilla XP equal to
the Resonance" is Echo Chamber; "the Ore Quality branch" no longer exists, so name the nodes. Define
machine-broken as in S21, and add that a machine-finished vein fires no completion effect.

§3.5. Reset visibility follows §8 (S34). Players inside at countdown end go to the Vault's spawn point
(Nh); the unload fallback per Ni.

§4.2. Rare "10–15" becomes the Balancer's one value. Stone row: Stone Memory T3 pays 0.5 on deepslate, not
stone (0.05 since Q8, 2026-10-27). Vault Echo burst "25–40" becomes 25/35/50. Delete "configurable as multipliers": no such key exists.

§4.4. "A full 225-point respec is ~675 levels" becomes 354 points, 1,062 levels. (Retired 2026-10-26: refunds
cost 3, 12 or 30 XP levels a node, §4.4.)

§5.3. "One point per level" contradicts §4.3's `pointsPerLevel` (deferred section, still wrong).

§6 notation. "Four kinds of node" precedes ten markers; state classes (small, anchor, notable, keystone,
pact, growth, fork parent, fork option) and marks (tradeoff, exclusive, Ultimine only). "The 100-point
Mastery gate" becomes the derived 160. "Reading a node" omits `[ANCHOR]`, `[PACT]` and `[GROWTH]`.
`[GROWTH]` "worthless later by construction" becomes "their reward stops mattering as the team grows";
growth nodes never switch off (N5).

§6.1 gates. The cluster table, line "spent 100 points", and every cluster intro ("Anchor unlocks at 10 / 25
/ 45 / 55 / 70 / 25 / 100") change to S6's values, with the rounding stated and the note that with neither
soft dependency Mastery opens at 160 of 304 buyable points. "`NodeCosts` derives the point values" should
match where the code derives them. (Retired 2026-10-26 with the anchors, gates and clusters; the §6
notation's anchor class went with them.)

§6.1 "What the code still has to catch up on". Describes deleted code. Replace with the reserved-id list
(`common_ore_boost`, `uncommon_ore_boost`, `rare_ore_boost`, `ore_sense`, `motherlode`, `efficient_miner`,
`disturbed_zone_unlock`); drop the migration paragraphs (N1).

§6.1 node text still vague or wrong:
- Vein Shaping T2 "applies at double strength" is true of none of its options as their tables state them.
- Vein Singularity T1 "1–3 veins" has no distribution; T2 "Consolidation intensifies" and "~40% of chunks".
- Stratified T2 "near-single-ore".
- Long Delve's table says "20 unbroken minutes"; the text and guard say any break in the last 60 seconds.
- Deep Veins says "standard ore density"; it means the band's baseline.
- Calloused Hands: the +40% cap arrives at about 3.0 million blocks at tier 3, not 10.8 million; the table
  should say the `0.03 × ln(...)` term is a fraction.
- Twin Veins "next to it" has no offset. Deep Harvest's extra orb has no value. Homeward Seam's cast has no
  duration. Hoarder's Instinct's "resting" is undefined. Molten Seam gives no pool rate or size. Wanderer's
  Cache's "a tool repair" is not an item. Brittle Stone does not say which adjacent ore is destroyed.
  Highwater Mark's "up to 9" depends on rounding. Kindred Rock's "attuned types" collides with Attuned ore.
- Volatile Veins: Ultimine Gambit "counts 1 extra block toward the disappearance roll" has no meaning when
  the roll is once per operation; its table says "Riskier Ultimine".
- Silent Stone: "the rate is a config value" (S23) and "3/second is roughly 1.5× a solo player's observed
  rate": nothing was observed, and solo is 720.5 an hour, 0.20 a second, so 3 a second is 15 times; it becomes 0.30 a second
  per member below Y=0, after team scaling (Ng).
- Prospector's Eye requires Vein Sight (Nf). Gravel Purge removes the gravel and clay the Vault now
  generates (N4). Guide Vein waits on the vanilla ore research (N4).
- Every cross-cluster prerequisite goes (Nk); the node moves into its prerequisite's cluster or the
  prerequisite moves into its. Waits on the restructure. **Retired 2026-10-26:** no clusters remain; see
  `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree".

§8. Three tabs and a reset button "in the Ore Memory tab" while §12 defers that tab: a small Ore Memory tab
ships with the Survey panel and the reset button (N3).
"Zero ... is also the correct starting value" for new `PlayerStats` fields is false for `deepest_y`; say an
absent field reads as its documented default. Chapter's End's per-chunk tally moves to the chunk (S20).
Add the lang key format (S36).

§9. Ultimine per S24; "Ultimine branch" becomes Broad Cut. FTB Teams "dimension deleted on team disband"
per S19 and N8. Mekanism reflection goes (Nj).

§10. The "takes effect" table cites pre-restart readers ([30], [52], [80]); each value is added with its
reader in the ticket that reads it. "Level cap (derived from tree structure)" contradicts §4.3's constant
30.

§11. The generator sample reads SavedData; show the snapshot supplier (rule 1). "SavedData Pattern" says
"Forge's" and stores derived totals (`resonanceLevel`, `resonanceSkillPoints`, `chunkLoadingTickets`); keep
the `dataVersion` paragraph. Rarity classification names `ForgeRegistries.BLOCKS`, `OreBlock` and
`DeepslateOreBlock`; candidates come from `#c:ores`. "FTB Teams Event Hooks" per S18 and S19. Volatile
Veins' pity counters are listed under `PlayerStats` but reset on logout; they are in-memory. "Vein Index"
says four nodes need completion data; list every consumer. "Drop Pipeline" says nine nodes and lists
fourteen, and misses drop-changing nodes such as Resonant Overload, Last Ore and Salvager's Eye; re-derive
from §6.1. The composition table per S39. "Vault Session State" cites "§5.1 counters"; §5.1 is Animus
Gain. "Node Cost Constants" uses "// Vein Branch" and sums `_BASE_COST` times tier count, which is wrong for
uneven tiers such as Vein Expansion's 1, 1, 2, 2, 3.

§12. Phases 0 to 2 are marked complete; nothing is. Replace the table with T1 to T14.

§13. 77 lines are ticked for code that no longer exists; untick all. Delete the Chunk Loading Resonance
line (S22). The orb line's 2/5/12 follows the S40 constant. Silent Stone "(rate is a config value)" goes.
The node-art item lists six frame classes; add pact. "Disturbed Zone Unlock ... Core branch" names a branch
that no longer exists. Pre-restart `#N` and `[N]` references are labelled as pre-restart; #69 and [68]
name one ticket twice.

Same PR, additions rather than corrections: the S12 tolerances in §3.1, the S11 pacing bands in §4.3, the
reserved-id list, the lang key format.

Design log, same PR: the 2026-09-07 "Pacts" entry sits below a 2026-09-03 entry under "Newest first"; the
2026-09-03 vanilla XP entry says the rule is in §4.2, and it is in §3.4; add a restart entry saying the
2026-09 decisions stand though their code is gone, and entries for Nick's 2026-10-26 decisions (builds on
his PC, name clashes accepted, compatibility and performance roles deferred, three roles added).

---

## Manager

I decide order, owners and reviewers, run the review rounds, settle disputes from the documents, and carry
questions to Nick. My plan is reviewed by the Balancer (baseline before modifiers), QA (each step
verifiable) and the Architect (foundations first).

### What I need

| From | What | By when |
|---|---|---|
| Writer | The docs PR above (done by #158) | Before T3 |
| Architect | Each ADR as a docs PR, and a sketch review (a paragraph and a class list from the Developer) before code on T1 and T3 to T8 | Before each task's code |
| Developer | A feasibility comment on each ticket naming the `CLAUDE.md` rule at risk and the cost | Before I assign it |
| Balancer | Tolerances (given in S12), the rare constant, the milestone table for teams of one and five marked derived or measured, a stacking review per T9 batch | Docs PR, T5, each T9 batch, T14 |
| QA | Per ticket, which criteria were seen on Nick's PC and which are unverified; the playtest steps | Every PR |
| UI-UX and Designer | A preview set Nick has approved | Before any T7 or T8 renderer code |
| Playtester | The player story and session questions per player-visible ticket; Nick's feedback sorted and quoted | Before the ticket opens; after each session |
| Nick | The open decisions, one at a time | In the order listed above |

### Running a review round

Per `CLAUDE.md`: every matrix reviewer gets the work at the same time and reviews against its own checklist;
a verdict lists what was checked; the owner answers each objection with a fix or evidence; two rounds, then
me. Quick agreement means I look harder. I do not summarise an objection more softly than it was made.

### Per task

T1. The ticket's gate is S4 plus the T1 row. Versions are re-checked here once, then frozen.

T2. One block and one item in Nick's client before T3 opens.

T3. One PR (S14). The `orevault_return` data, or the player attachment that replaces it, is versioned from
the first commit.

T4. Cites §3.1 and S12 and S13. I refuse any generation-node ticket until Nick has mined a fresh Vault for
ten minutes and the density command is inside tolerance.

T8a then T5. The node data model lands first (S8). T5 does not merge if it persists the pool without
`data_version` (S16).

T7 and T8. Not ticketed for renderer code until the preview set is approved. No toolkit spike (S26).

T9. A batch of nodes at a time: the ring-0 nodes first, since the first-ten-minutes session needs their
effects (`docs/PLAYER_STORIES.md`), then §6.1 order; each batch's stacking review before its tickets.
(Was "cluster by cluster, Prospecting first"; the tree has no clusters since 2026-10-26.) No node ticket
opens until its description can be written with every tier's number.

T10 and T11. Reset and disband destroy worlds; their tickets wait for Nick's answers.

T12 to T14. Each texture batch reaches Nick as a contact sheet. Release needs the FTB Evolution 2 run on his
PC, `KNOWN_ISSUES.md`, the debug block removed, and every §13 box matching merged work.

### My gates

- A ticket cites a §, says why before what, names one owner and the matrix reviewers, and every criterion
  names how it is checked. A criterion asking for a unit test on Minecraft-side code becomes a gametest or
  a playtest step.
- QA's report quotes counts from `build/test-results/test/*.xml` produced on Nick's PC, states that
  `runGameTestServer` booted, and lists what was not seen in a client as unverified.
- No persisted shape changes without a version bump and migration; no config without a reader.
- A number change names what moves for the player and is flagged to Nick, never merged quietly.
- Anything visual has Nick's yes on a rendered preview, linked in the PR.
- "Ready to merge" appears only after every matrix reviewer signed off or I overruled them with the reason
  written down. A PR opens only when its commits are in; PRs say "don't merge yet" until then. No stacked
  PRs without a merge order.

### What I will push back on

A modifier before its baseline is measured. Anything in the lessons doc's "Design lessons" table or the
log's rejected lists, which I check before ruling. A renderer before an agreed preview. A world-facing task
opened before Nick has seen the previous one. Config that encodes a measurement. A ruling of mine that
rests on consensus rather than a source.

### Conflicts I expect

The Developer against QA and me on the serial client gates, which run at the pace of Nick's sessions; the
Developer works on pure logic for the next task meanwhile. The Balancer against the Writer on stale
numbers: the Balancer owns the number, the Writer where it is written. UI-UX and the Designer against the
Developer on preview cycles at T7 and T8.

## Architect

I decide the shape systems fit into before code is written, so the second feature does not undo the first.
The last build's worst bugs were structural: the lazy-creation fix removed the only restore path, the
generator sat one call away from SavedData, and node ids were renamed in a save format whose migration came
afterwards.

### What I need

From the Manager, the sketch step on T1 and T3 to T8 (S47). From the Developer, each read on the "Still to
read" list of `docs/research/neoforge-26.1-sources.md` (E1-S5, #195), before the story that item names. From
the Balancer, for each node: what state it reads (team, player, trip, chunk) and whether that state outlives
logout, death, reset or a team change. From UI-UX, the inputs the tree screen needs, so I can check they are
in the sync payload.

### Package layout

The package table and its import rules are ADR 0001 (`docs/adr/0001-toolchain-and-package-layout.md`,
proposed 2026-10-27), which replaced the table that stood here. The split that matters is pure against
Minecraft-facing: `core.*` holds no Minecraft import and runs in JUnit; adapters call into it. Each import
column is an ArchUnit rule, shown failing once (S43). No interfaces for single implementations.

Retired 2026-10-26 from the old table, with the PoE tree: `Cluster` and `NodeCosts` in `core.tree` (no
clusters; every node costs 1 point), and "`TreeLayout` and its geometry proofs" in `core.layout`, which now
reads the authored layout file (§6 Layout data, §11 Tree Layout Data). ADR 0001 also renames
`integration.mekanism` to `integration.processing`, so no package name contains "mekanism".

### Persisted data

Every persisted shape, its keys, defaults, versioning and load rules are ADR 0005
(`docs/adr/0005-persisted-data-and-versioning.md`, proposed 2026-10-27): one key convention, one clock and
one id form (its section 1), where everything lives (2: team data in the server's storage,
`<world>/data/orevault/`, not the overworld's), team data (3), `PlayerStats` (4), the player attachment
(5), versioning (6), the tree's load rules (7), what is in memory only (8), a team change (9), the orb (10)
and item components (11). The tree's saved state is §6 Saved state; the vein index is ADR 0004's
(`docs/adr/0004-vein-index.md`), and its Amendment 1 holds `orevault:explored_map`, stored in the Vault
level's own storage. This section no longer restates the
shapes. The table that stood here stored node state as `nodes {id: tier}`, which the PoE tree retired on
2026-10-26: a skill's tier is the count of its owned nodes and is never stored (§6), so team data saves
`owned[]` and the fork choices.

My rules for any shape still hold: `data_version` from the first field (N1 starts it at 1), every field
reads correctly from a tag without it, removed node ids are reserved, nothing derived is stored, and a
stored position whose `vault_generation` is behind the team's counts as cleared by a reset, so offline
players' files never need editing.

### Payloads

The channel, the trust rules, size bounds, the layout hash, every serverbound and clientbound payload, when
each is sent and the refusal lang keys are ADR 0006 (`docs/adr/0006-network-channel-and-tome-sync.md`,
proposed 2026-10-27). What is synced follows §6 Layout data: the layout hash at login, ownership and the
hidden nodes as bitsets in layout order, fork choices, the pin, resent on `OnDatapackSyncEvent`. The client
sends intent, never a cost, tier or level; the server validates every mutation.

Retired 2026-10-26: the `TomeState` snapshot that stood here carried tiers and per-tier costs, which the
PoE tree no longer has, and its list of payloads predates path buying, the pin, the generation vote and
the layout hash.

### Per task

T1: package skeleton, ArchUnit rules, ADR 0001. T3: ADR 0002 (lifecycle): `VaultLevels.register` is the only
insert; restore scans `<world>/dimensions/orevault/`, reads the type from the Vault folder and `disbanded_at`
from team data, and survives a bad folder (amended 2026-10-31: the Vault type file replaced `vault_variant` in
team data, so a damaged team file costs progress, never terrain; a missing or unreadable type file is logged
and that Vault skipped); restore reactivates the level's saved tickets as `prepareLevels` does; the `in_vault`
login check, including a Vault whose team is not the player's current team; I review the level-unload spike.
T4: ADR 0003 (`GenSnapshot` and `TeamEffects`, rebuilt on the main thread on the triggers that record lists;
team level is not in the snapshot since 2026-10-27, as no 1.0 generation node reads it) and ADR 0004 (vein
extents as values, veins inside their chunk). T5: pool and `level_reached` on the T6 codec from the first
field; orb award goes through `mining`. T6: ADR 0005, golden key sets, fixtures. T7: ADR 0006 (sync) with a
"waiting for sync" client state. T8a and T8: `core.layout.TreeLayout` read from the authored layout file (§6
Layout data, §11 Tree Layout Data), with the hidden set from the server's mod and tag checks; no width
function, since nodes are icons (S27, superseded 2026-10-26). T9: each node's state checked against ADR 0005;
nothing grows without bound. T10: `TicketController` on the mod bus. T11: ADR 0007, reset and disband share
archive and regenerate.

### My gates

- A persisted shape changes only with a version bump, a migration, a fixture and a golden key-set update.
- No generation code reaches `data`, `team` or `SavedData`; the snapshot is rebuilt on every trigger ADR
  0003 lists.
- One world-map insert, with `markWorldsDirty()`. No level created off the portal path; none removed while
  the server runs unless the spike proves it safe.
- Every mutation validated on the server, and refused inside the Vault where §6.1 says so.
- Every new state names its owner and its lifetime across logout, death, reset and team change.

### What I will push back on

Stored totals that can drift from their source. Sentinels in save files. Per-chunk state in player data.
New layers: a service interface, a mod-internal event bus, a repository over `SavedData`.

### Conflicts I expect

With the Developer over ArchUnit strictness and the sketch step. With the Balancer when a mechanic needs
state the generator cannot see, or a per-player number pays the team. With the Manager over ADR review time.

## Developer

I write the Java, Gradle, datapack JSON and resources. Builds and runs happen on Nick's PC through a Remote
Control session (S4).

### What I need

- A ticket per PR from the Manager, citing §, with criteria that name their check.
- From the Balancer, every number as a per-tier constant with its formula and assumption, before the ticket
  that uses it, plus the missing rows of §11's composition table.
- From UI-UX, the layout file (§6 Layout data) and the named views; from the Designer, node icons, class
  frames and a `style.json` of colours and state tints, approved by Nick on the preview, before renderer
  code.
- From the Writer, the docs PR before T3.

### Per task

T1. NeoForge 26.1 empty mod on the old pins (`neo_version` 26.1.2.95, FTB Teams 26.1.2.4, Ultimine
re-checked against 26.1.2.6), no committed `org.gradle.java.installations.paths`, ArchUnit at test scope. I
confirm whether `CompoundTag` loads in JUnit (S17).

T2. Vault Frame and Crude Igniter through `registerBlock(name, factory, props)`, creative tab, `items/`
definitions, `pack.mcmeta`, the pickaxe tag under `data/minecraft/tags/block/mineable/`.

T3. One PR (S14): lazy create in `findOrCreate` only, through the single insert that calls
`markWorldsDirty()`; eager restore in `ServerStartedEvent`; teleport with the return position saved after
success, the team anchor (X=0, Z=0) and an idempotent return portal. The `storageSource` AT returns with
its comment: the field is protected (E1-S5, #195; ADR 0002). I spike
level unload here, because T11 depends on it.

T4. The generator reads team state only through the snapshot supplier. Layer JSON validated against the
heights duplicated in `Variant`. Placement is a pure function returning ore indices and vein extents (S13,
S20); candidates come from `c:ores`. Placement fields are read through their codecs; an AT only as ADR
0009's fallback, with its comment. The density command (S12).

T8a and T5. The node data and the layout file read into `TreeLayout` with a `NodeClass` enum and marks, and
the §6 layout tests (§12 T8a). (Was: `NodeDefs` with gates as `ceil(fraction × totalTreeCost)`; retired
2026-10-26 with the gates, since every node costs 1 point and the ring is the only level floor.)
`VaultOrbEntity` modelled on `ExperienceOrb`, server-authoritative, carrying `orb_count`, the
number of orbs merged into it, which is Hoarder's Instinct's N (§6.1: "N counts orbs, not Resonance"; ADR
0005 section 10). `LevelCurve` and `TeamScaling` pure, team size
never below 1. Breaker rules per S21. Available points derived. Stone Memory T1 bought by debug command
(S9).

T6. `data_version` first on every shape, codec via `CompoundTag.CODEC.xmap`, defaults when absent, fixtures
in `src/test/resources`. Before the vein index ticket I check in the sources jar, then prove with a
gametest, whether a `ProtoChunk` attachment carries over to the `LevelChunk`; if not, a pending map drained
on the main thread at chunk load.

T7. `@Mod(dist = CLIENT)` entry, payloads on one channel, server validation, refusal payload, header after
first sync, vanilla screens (S26).

T8. The renderer draws the positions the layout file holds and places nothing itself (§8 Tab 1); the hover
path, shift-click path buy, search, pin and travel totals read `TreeLayout` and the synced state. A client
command opens named views. (Was: `TreeLayout` ported as a pure function of a structure file and a width
function, with a width table from `ascii.png` and golden JSON; retired 2026-10-26, S27, since positions
are authored and nodes carry no text on the page.)

T9. Each effect a pure function plus a zero-logic adapter, into the single `BlockDropsEvent` pipeline and
the `ResonanceBonuses` accumulator. The old build's `FoodData.exhaustionLevel` AT (old PR #150 in
`docs/research/issue-harvest.md`) returns with the first node that changes hunger drain, which is Miner's
Constitution at ring 0, not Long Delve at ring 7; Long Delve uses the same hook. Settled 2026-10-27: 26.1
still needs it. `FoodData.exhaustionLevel` is private with no accessor (`FoodData.java` line 16; the old AT
never touched it, so the tree's `private` is reliable) and `Player#causeFoodExhaustion` posts no event.

T10. `TicketController` on the mod bus; `max_loaded_chunks_per_team` gets its reader here. A restored
Vault's saved tickets, Anchor tickets included, load deactivated and only `prepareLevels` reactivates them
for levels present at start, so restore reactivates them (ADR 0002): `getDataStorage().get(TicketStorage.TYPE)`,
`activateAllDeactivatedTickets()`, then NeoForge's `ForcedChunkManager.activateAllDeactivatedTickets(level,
saved)`. E1-S5 (#195) read that it is `@ApiStatus.Internal` and runs the
`TicketController` validation, so step 13 needs no extra call.

T11. Vote state machine pure and tested; deletion depends on the spike and on Nick's disband answer (N8).
Joining a party never disbands the player's own team (FTB Teams 26.1.2.4, read 2026-10-27,
`docs/research/neoforge-26.1-sources.md`), so T11 needs no exemption for it.

T12. Ultimine per S24, on the 26.1 registration events; the server tells the client whether it is active.
Mekanism per Nick's answer.

T13. Recipes, loot tables, tags and blockstates through datagen. Lang keys per S36, with a test reading a
pure list of every block, item, entity and node id.

T14. Boot in Nick's FTB Evolution 2 instance; remove the debug block.

### My gates

- Every number names its constant and per-tier values; no literal in a proposal I am asked to build.
- Every proposed effect states its per-event cost. Nothing scans the world or flood-fills per block break.
- Every proposal says what state it persists, for whom, and whether it grows without bound.
- Every config value has a reader in the same ticket and says when it takes effect.
- The §6 layout tests pass before I touch the renderer.

### What I will push back on

A modifier ticket before T4's baseline is mined. A grid, lanes, or the whole tree on one screen. §11's
generator sample. Placement code in the renderer. Save sentinels. Config before its reader.

### Conflicts I expect

With the Balancer: the curve is provisional until a playtest measures blocks per hour, and T5 marks it so.
With QA: client checks wait on Nick's sessions. With the Reviewer and the Architect: an ADR 0001 import row
that a later record needs is changed by an ADR amendment, never widened in a code PR. (Was: the Ultimine
package against §9, stale since #158.)

## QA

The last build had about 223 unit tests and still shipped without a client run, with a Vault at 0.013% ore
for a month, and with a dimension fix that killed players who logged out inside a Vault. No unit test
caught any of them. I check that a claim was checked, by whom and on what machine.

### What I need

From the Developer: at T1, a gametest harness with one passing and one failing test, so a failure is
known to be reported (the old build had no gametests); from T3 on, the debug commands in S44. From the
Manager: criteria that name their means of checking. From the Balancer: the S12 tolerances and the S11
bands. From UI-UX and the Designer: the approved preview with named views. From Nick: playtest replies by
step number, with `logs/latest.log` and screenshots for failures.

### Per task

T1. I check that `runServer` and `runGameTestServer` boot an empty mod with FTB Teams and log no error, on
Nick's PC. Nick answered the GitHub Actions question no (Na); if a check is ever added, it must fail when it
cannot run, since a skipped job reporting green is the 2026-08-29 problem again. ArchUnit (ADR 0001): each
fixture assertion checks that the failure report names its planted class, and the main test asserts the
imported class count is above zero, which is also the check that ArchUnit reads Java 25 class files. The T1
PR quotes `git grep -n installations.paths` returning nothing, and records whether `CompoundTag` loads in
JUnit, here and in `CLAUDE.md` Tests (S17; E1-S3, #194).

T2. Frame mines at stone speed with a pickaxe and drops (#87); gametests for tag, drop and recipe; creative
and JEI visibility are Nick's.

T3. No level is created by a login or a team event (the Reviewer's ArchUnit rule, plus the scripted run); a
Vault created after the first tick ticks (a mock player breaks a block in it); a bounced teleport leaves the
return position unchanged. The scripted restart: create a Vault by debug command, stop, start, then
`execute in orevault:vault_<id>` from the console before anyone connects, then the S44 break command breaks a
block at a given position in it and prints that it is gone, and the E3-S11 spike's tick logger shows lines
for the key (QA-38). The folder-name and key functions are pure `core` functions with unit cases (round trip,
upper-case hex, 31 and 33 hex digits, a hyphenated name, `vault_<32 hex>.deleting` not parsing). A restore
fixture (amended 2026-10-31, the type file in the folder): one good folder, one `vault_zzz`, one whose Vault
type file is garbage bytes, one with no type file, one whose type file names an unknown type
(`orevault:nonsense`), one whose team file is garbage bytes; pass is two restored (the last under its own
type, with an ERROR naming the team file), four skipped and logged (WARN for the name, ERROR naming each
type file), and "restored 2, skipped 4", as ADR 0002 Tests. Login case 1
is checked by renaming the folder while the server is down. The E3-S11 level-unload spike passes only if
every check passes on both runs (one per variant order); one failed check, even intermittent, is Result B
(ADR 0002).

T4. Unit tests for 3% and 6%, the 68/25/7 stone-band and 60/30/10 deepslate mix (S12; 70/25/5 retired
2026-10-26 by the vanilla ore research), vein sizes, the floor with the full deepslate stack (S13) and
seeding determinism; golden tests for the seed and the generation stamp (ADR 0003); each per-chunk rate
over 10,000 chunk seeds within three standard errors, and a salt-isolation test; the classifier gametest
also asserts each ore's expected blocks within 10% and the features it used (ADR 0009); then the density
command over at least 81 chunks on a dedicated server inside tolerance, quoted in the PR. For the vein
index (ADR 0004): the E6-S4 attachment spike as a gametest half over at least 81 chunks plus a scripted
dev-server restart half, a mixed result counting as Result B; the size measurement against 73,728 bytes
(raw `long[]` plus palette), the maximum over 81 chunks deciding.

T5. `LevelCurve` sums to the total with ratio 100, about 76,259 at 218.9 ore an hour (7,200 blocks an hour
times ADR 0009's closed-form density) on the 68/25/7 mix with rare at 12.5 (3.4845 Resonance an ore, §4.2,
§4.3); thresholds stored cumulative, each rounded from the cumulative value; `curve_divisor` scales every
threshold equally; level 1 costs about 113 and arrives at 0.15 h, level 2 at about 245 cumulative and 0.32 h
(derived, not measured; Balancer, 2026-10-27). Pool unit cases from ADR 0005 section 3: rounded once, half up,
at pool entry after team scaling; 20 payments of 0.05 sum to exactly 1,000 thousandths. `TeamScaling` 1.0,
1.1, 1.2, 1.4 and size 0 or unknown read as 1. Gametests for the orb paying only its team, the three breakers
(S21) and vanilla XP. The pacing session per S11 (60 minutes, or two of 30 pooled), judged on ore an hour from
the S44 readout.

T6. The tests of ADR 0005 section 6: a golden key set per shape, a fixture per shape and version
(including one without `deepest_y` that reads as never entered), an absent-field test per field, and per
key a wrong-type fixture (`resonance_pool` as a string, `owned` as an int, `player_stats` as a list) loading
to its default; the unit cases for the pool, `curve_divisor`, the Q14 window at 1,727,999 and 1,728,000
ticks, and an older generation's team-scoped position reading absent. `SavedData` and the chunk attachment
across a restart are a scripted dedicated-server stop and start, with named fields compared through the
S44 dump, because a gametest is one server lifetime.

The damaged-file steps (amended 2026-10-31, Manager's round 2 ruling; a correct build passes them). A
scripted dedicated dev server (E3-S10, #200), fields compared through the S44 dump. Case A needs an expanded
Vault: use the S44 create command if it can make one under the expanded type; until it or a reset exists,
case A runs on a base Vault and steps A4 and A6's deepslate checks are listed as unverified.

Case A, damaged team file: play goes on, terrain kept.
1. Make a team with an expanded Vault, mine some ore, stop the server. Note the team's level and the Vault
   type file's content.
2. Overwrite `<world>/data/orevault/team_<hex>.dat` with garbage bytes. Record its SHA-256.
3. Start. Pass: one ERROR naming the team file path; the restore log lists this Vault restored under the
   expanded type; no `.unreadable-` copy exists yet.
4. Log in and `/execute in orevault:vault_<hex> run tp @s 0 -30 0`. Pass: deepslate there, not void.
5. Mine one ore. Pass: `team_<hex>.dat.unreadable-<gametime>` exists with the recorded SHA-256; an ERROR
   names the path; the dump shows the team at level 0.
6. Back in the Overworld, toggle a tradeoff or pin a node (a team write outside the Vault). Stop, start.
   Pass: no ERROR for the team file; restore lists the Vault under the expanded type; Y=-30 is still
   deepslate; the ore mined in step 1 below Y=0 is still gone (the same chunks, not regenerated).
Fail: any start restores the Vault under the base type, or the original bytes are not on disk.

Case B, damaged Vault type file.
1. Stop; overwrite (then, as a second run, delete) the Vault's type file.
2. Start. Pass: an ERROR naming the type file; the Vault is not registered; a player who logged out inside
   is placed at `return_pos` with a log line (login case 1); the portal refuses with the lang message;
   nothing is written into the folder (its file list is unchanged).
3. Restart. Pass: the same, nothing written.
4. Put the original file back and restart. Pass: the Vault restores under its type.

Case C, new Vault. A team with no Vault enters by portal. Pass: the type file exists with `data_version` 1
and the base type before any region file is written (check the folder right after entry). Creation writes no team data (S18,
ADR 0005 section 6); the team's file appears only with its first recorded field.

T7. `runGameTestServer` after every payload or screen change. ADR 0006's checks: a refused purchase
resyncs; `/reload` with a tag change hides or shows the Mekanism-tag nodes on an open Tome; a second dev
client (Q10) sees a purchase, a pin and a vote within one second; a mismatched layout disconnects with the
message, produced by running the client from a build with one node moved in the layout file. Added: the
header reads "Waiting for team data", not zeros, on a slow join; no level-up toast on the first
`TeamProgress` after joining; the rate bucket and coalescing are pure `core` rules with unit cases (11
payloads in one tick, a refusal at most once a second); `canonicalHash()` keeps its value under CRLF,
whitespace and edge reordering, and changes when an edge is added, a node moves or nodes are reordered; one
gametest per bounded field decoding an oversize buffer; a golden test pinning `PROTOCOL_VERSION` with the
hash of the sorted node ids.

T8a and T8. Every fork option covers every parent tier (§6.1 Forks); the §6 layout tests (distance at least
the ring, copies of a skill in ring order, mod-gated nodes on dead ends, exclusive leaves off one junction,
petals with no edges, no edge through another node) with and without the mod-gated nodes; each test shown
failing against broken input. (Was: gates from fractions and the polar invariants; retired 2026-10-26.)

T9. Per batch of nodes: pure tests per tier, a gametest where a block or drop is touched, the Balancer's stacking
case (Volatile Veins at 3% against Ultimine Safety, never zero, is the template), Fortune before Quantity.

T10 and T11. Gametests for tickets and the ceiling; an Anchor set, the server restarted with nobody
joining, and the anchored chunk ticking (a furnace in it advances), which proves restore reactivated the
tickets. Vote unit cases at 1 to 5 members online with ADR 0007's list (a late joiner not counted, an early
fail once yes cannot reach the threshold, a second start refused, a disbanded team refused, the timeout at
exactly its tick) and the Q14 window as a pure `core.reset` function; the S44 fault points after reset steps
3, 6, 7, 8 and 9, each followed by a start and a check of the folder, `vault_generation`, the window and the
log, with the backup-abort branch induced by making `orevault_backups` a regular file; scripted reset with
backup on and off, region files checked on disk.

T12. Three dedicated-server boots (no soft dependencies, Ultimine only, Mekanism present); greps fail on
`import mekanism` and on `ftbteams` outside the team package.

T13. Lang coverage and banned-word tests (S38); a PNG size test scoped per Nick's GUI answer.

### My gates

Every PR, each item pass, fail, or "not verified" with the reason:

1. `./gradlew build` passes on Nick's PC, output from that run.
2. `./gradlew test --rerun-tasks`, with counts read from the XML.
3. `runGameTestServer` boots and every gametest passes, counts quoted.
4. Every new pure-logic class has tests, and the PR shows one failing against deliberately broken code.
5. Assets, GUIs, worldgen or dimensions: seen in a client, or listed unverified with a playtest script.
6. Persisted shapes unchanged, or a version bump, a migration and a fixture that loads.
7. The regression items the change could touch, each listed as checked.
8. Every acceptance criterion ticked with evidence, or unticked with the blocker.
9. Every new config value has a reader and says when it takes effect.
10. "Ready to merge" appears only after 1 to 9 are recorded.

### What I will push back on

"Should work" in place of a run. `BUILD SUCCESSFUL` as a test count. A worldgen PR without a measured
density, or with `testore` cited as one. Tests written after the code and presented as test-first. A
dimension fix with no restart test.

### Playtest loop with Nick

Nick sees what no test can: textures, creative and JEI, the portal overlay, lighting, the Tome at each GUI
scale, whether the tree reads, how mining feels. Each such PR carries a numbered script of at most fifteen
steps, written with the Playtester. Pacing sessions are measured (S11); I provide the log line format and a
parser that turns `latest.log` into the Balancer's table, and a procedure for a team session with several
temporary dev client sessions on Nick's PC, each with its own dev username (Q10); no second person is needed. Bugs become tickets and regression items; design feedback goes to the Playtester and Writer.

### Conflicts I expect

With the Developer and Manager: gametests and the restart script cost time on tickets that feel finished; I
block on them for T3, T4 and T6. With the Balancer: 218.9 ore an hour and the stacking model are
derivations until measured, and I say so in every PR that depends on them.

## Reviewer

I review every PR the matrix gives me, with file, line and triggering scenario. QA runs the game; I read
the diff. Most of my checks should become failing tests.

### What I need

From the Writer, the docs PR before T3. From the Manager, cited § and checkable criteria per ticket. From
QA, per PR, the XML counts and the `runGameTestServer` result from Nick's PC. From the Balancer, before T9,
every tier number in the constants and each Resonance node's composition bucket. From UI-UX, confirmation
that no server code reads layout or style files.

### Per task

T1. No machine-local JDK path; versions pinned; ArchUnit rules land while the tree is small, each shown
failing (S43).

T2. Id-aware registration, creative tab, `items/` definition, `pack.mcmeta`, pickaxe tag in the
`minecraft` namespace (#87), block and item textures 16x16 (N2).

T3. Only `findOrCreate` creates a Vault; no team event reaches it (#82). Every folder restored in
`ServerStartedEvent`, one bad folder not aborting the rest, and each restored level's saved tickets
reactivated as `prepareLevels` does (ADR 0002). `markWorldsDirty()` after every insert and every removal.
Return position saved only after success. Registry unfreeze and refreeze paired in a `finally`. No level
removed while the server runs (S19) unless E3-S11 took Result A (ADR 0002). A player whose team changes
while inside a Vault that is not their new team's is sent to `return_pos` with a chat line saying why; the
login check treats a Vault whose team is not the player's current team the same way; mining in a Vault pays
only that Vault's team.

T4. Team state reaches the generator only through the snapshot. One `OreBaseline` record of counts and
sizes (ADR 0009), read by the generator and `LevelCurve`; no mix constant beside it (was one constant for
the mix, and before that 70/25/5, retired 2026-10-26). The floor enforced, the clip factor in the snapshot.
`Variant` heights match the JSON. ATs carry their task comments.

T5. Orb modelled on `ExperienceOrb`, not a subclass. Team size never 0. Breaker rules per S21. Vanilla XP
untouched. `averageOresPerHour` a constant; `curve_divisor` and `target_play_hours` `worldRestart()`.
`POINTS_PER_LEVEL` a constant of 5, not derived (§4.3 step 2); `totalTreeCost` is retired with node costs
(2026-10-26).

T6. Any save-data risk is a block. `data_version` and `migrate()` on each shape of ADR 0005 section 6 (the
Vault type file is one since 2026-10-31), each moving independently; snake_case keys; every new field reads
correctly from a tag without it; `deepest_y` absent means never entered (S16); every rename or removal ships a
migration tested against the previous version; reserved ids never reused. Read paths use `get`, never
`computeIfAbsent`; an existing but unreadable team file is copied aside with an ERROR before anything writes
(ADR 0005). The vein index is a versioned attachment written only on its own chunk, its ore-group palette
stored as block ids, and an unknown palette id never throws (ADR 0004).

T7. Mutations validated on the server; client cost and tier never trusted; tradeoff, fork swap and every
refund refused inside the Vault (§4.4, S35), the generation vote excepted (§8); client code behind the client entry; the Ultimine flag from
the server; 26.1 GUI names; tooltips wrapped with `splitIgnoringLanguage`.

T8. `SkillTree` pure, `NodeClass` an enum, the code reads the authored ring and never the distance (§6),
the renderer computes no placement, the §6 layout tests present. (Fraction gates and the golden layout test
retired 2026-10-26.)

T9. One `BlockDropsEvent` listener; numbers from the constants; Resonance through `ResonanceBonuses`; trip
counters in session state; tradeoff state on `PlayerStats`, the applied Volatile Veins and Molten Seam state on the team (Q1); snapshot refreshed on purchase; stacked risks
checked.

T10 to T14. `TicketController` on the mod bus, ceiling with a reader, Anchor inside the Vault only. Reset:
evacuation before deletion, online votes only, the free respec window at most once per 24 h (Q14), a gametest or scripted run. Every soft
path gated; Ultimine types only in classes loaded after the check; no `mekanism` imports; `c:` tags.
Attuned ore's component id treated as save data. Debug block and `VaultDiag` removed at release.

### My gates

Every code PR, listed in the verdict as checked or failed:

1. No `SavedData` reachable from generator code. (ArchUnit)
2. A Vault is created only from `findOrCreate`; every world-map insert and every removal goes through one
   method calling `markWorldsDirty()`. (ArchUnit)
3. Gameplay listeners on `NeoForge.EVENT_BUS`; registries and config on the mod bus. (Review)
4. No common-code dependency on `client`; state mutated through `ServerLevel` and `ServerPlayer`. (First
   part ArchUnit)
5. `ftbteams` only in the `team` package; no `mekanism`; Ultimine only in its integration package.
   (ArchUnit)
6. No pre-26.1 names; no JetBrains or NeoForge `@Nullable`. (Grep)
7. Every access-transformer line preceded by a comment naming its task. (Lint)
8. A persisted shape change means a version bump plus a migration test; a change to a seed or stamp
   function is a save-data change (ADR 0003). (Golden key-set test)
9. No config value without a reader; start-time reads `worldRestart()`. (Mostly automatable)
10. No number literal where the value lives in a constants class. (Review)
11. A behaviour change comes with its spec change and §13 tick in the same PR. (Review)
12. Nothing outside the ticket; no `neo_version` or FTB bump in a `feat:` PR. (Diff check)
13. A diff that touches a codec under `network/` or a node id bumps `PROTOCOL_VERSION`, and every decoded
    string and collection is bounded (ADR 0006). (Review, plus QA's golden protocol test)
14. Any mixin beyond ADR 0008's has its own ADR, and the mixin config keeps `defaultRequire: 1` (ADR 0001).
    (Review)

Also automated: a grep for `popExperience` and `data/orevault/tags/block/mineable`, a test comparing
`dimension_type/*.json` with `Variant`, the PNG size test, `NodeLangTest`.

### What I will push back on

Dimension creation on any team event, and "cleaning up" the restore as dead code. Config ahead of its
reader. Ore density, ore per hour or Silent Stone's rate as config. A rejected design returning through
code. Removing §11's `data_version` paragraph along with the old-id table.

### Conflicts I expect

With the Developer over ArchUnit scope. With the Balancer: a node that needs a flood fill, or SavedData
during generation, cannot be built as written. With QA: on side safety and save data I find it in the diff
and QA proves it at runtime. With the Manager: PRs stay draft, marked "don't merge yet", until every
reviewer signs off.

## Designer

I own the art: world textures, GUI sprites, the Tome's window and page, node icons, class frames, state
tints and badges. Layout rules and the layout format belong to UI-UX (commit 953c9e6). The first build's textures broke
the size rule it had then, and the tree renderer was built three times because code came before an agreed
look. So I prove the art on a preview at real size, get Nick's yes, and hand the Developer sprites and
`style.json`.

### What I need

| Input | From | Form | By when |
|---|---|---|---|
| Review of my `mc-asset` drafts, saying what to improve (Q5: I draft first, no reference textures) | Nick, via the Manager | Comments on a contact sheet | Each texture batch, from T2 |
| The accepted Tome top bar | Developer, from the old `TomeScreen` | PNG at scales 2 and 3 | Before T7 |
| Node list with skills, classes and marks: one icon per skill, travel stat, notable, pact and keystone, and per fork option (§8) | Balancer, via T8a | The layout file's node list | Before T8 |
| The layout file and named views | UI-UX | The authored layout file (§6 Layout data) and a view list | Before T8 preview |
| Approval for `mc-asset` and the preview tools on Nick's PC | Nick, via the Manager | Yes or no | Before T2 |
| Client screenshots at scales 2, 3 and 4 at 1920x1080, and 2 and 3 at 1280x720 | Nick or a session on his PC | PNG, same named views | Before a visual PR is ready |

### Per task

T2. Vault Frame and Crude Vault Igniter at 16x16, palettes in the ticket, and a contact sheet: 1x and 4x,
the frame tiled 3x3, both in a slot at scales 2, 3 and 4, the frame beside vanilla stone and iron ore at
the 3% band (S46).

T3. The portal: one near-white translucent animated strip tinted per tier (§3.2). Green and orange
converge under deuteranopia, so tints also differ in lightness.

T5. I check the tinted orb reads unlike vanilla XP. T5 announces a level-up with an overlay message, not a
toast.

T7. Window, tab and top-bar art on vanilla screens, reproducing the accepted top bar. I sign off the
level-up toast, which arrives with the Tome shell (E7-S6, #291).

T8. Node icons, class frames at §8's icon sizes, the state treatments, the badge sheet, the faint level rings
and edge style, and `style.json`, drawn into UI-UX's preview set (S30). No tree-drawing code until Nick says
yes. Retired 2026-10-26: anchor frames, gate badges, tier pips and nine-slice washes sized to text boxes;
the PoE tree has no anchors or gates, a node is one tier, and nodes are fixed-size icons with no text.

T9 to T13. Mockups before build for each effect shown in the Tome (the inert fork parent, Outgrown, toggles
disabled in the Vault, Seismic Sense, Stratified's bands); the Vault Anchor; reset screens with UI-UX; the
rest of the inventory and the logo. The Wanderer's Cache item texture has its own story, E13-S2d (#392),
scheduled in the T9 sequence before E9-S33 (#338), not with the T13 polish work. T14: client against the
approved previews at every scale the preview set names.

### Asset inventory for 1.0

Block and item textures are 16x16, as vanilla (N2): every pixel has to earn its place so the item reads as
what it is at slot size. GUI art is drawn at one texel per GUI unit, the only density that is pixel-exact
beside the vanilla font at scales 2, 3 and 4.

| Asset | Path under `textures/` | Size | Notes |
|---|---|---|---|
| Vault Frame | `block/vault_frame.png` | 16x16 | Metal, all faces |
| Ore Vault Portal | `block/vault_portal.png` | 16x16 per frame, animated | Near-white, tinted per tier, translucent |
| Vault Anchor | `block/vault_anchor_side.png`, `_top.png` | 16x16 each | §3.4 |
| Four igniters | `item/*vault_igniter.png` | 16x16 each | One ladder, tier readable at slot size |
| Resonance Crystal | `item/resonance_crystal.png` | 16x16 | §3.3 |
| Tome of the Deep Seam | `item/tome_of_the_deep_seam.png` | 16x16 | Also toast and tab icon |
| Attuned raw ore, orb, particles | none | none | Data component with glint; tinted vanilla sprites |
| Tome window, top bar, progress bar | `gui/sprites/tome/*.png` | Nine-slice, one texel per unit | Top bar reproduces the accepted one |
| Tabs | `gui/sprites/tome/tab_*.png` | 28x32 units | Resonance and Ore Memory (N3) |
| Tree page | `gui/tree/page.png` | Tiled, opaque, one texel per unit | Low contrast under one-pixel lines |
| Wanderer's Cache | `item/wanderers_cache.png` | 16x16 | One item for both tiers. Reads at slot size as supplies a traveller left (a strapped pack or small crate with a torch showing), never as vanilla's bundle; a light, warm accent so the dropped item shows on stone and deepslate in a torch-lit tunnel. Its own story, E13-S2d (#392), before E9-S33 (#338); the file names are frozen with the item id `orevault:wanderers_cache` (§6.1) |
| Node icons: one per skill (every copy shares it), travel stat, notable, pact and keystone; one per fork option at petal size (12 units), plus a 9x9 badge of the same motif on the badge sheet for the chosen option on its parent (amended 2026-10-31, DES-R2-2: pixel art is not scaled, so the badge is its own drawing) | `gui/sprites/tree/`, names set at T8 | Travel 14; small, growth and fork parent 18; notable 26; pact 26; keystone 36; petal 12 (provisional, DES-3 ruling 2026-10-27, final on Nick's E8-S5 #283 preview); one texel per unit | No Retro Diffusion unless absolutely necessary (Nick, 2026-10-26) |
| Class frames: small, travel, notable, keystone, pact, growth, fork parent; the fork option petal; a buyable outline per frame size (14, 18, 26 in both the notable and pact silhouettes, 36, 12) | `gui/sprites/tree/frame_<class>.png`, outlines named at T8 | Fixed size per class, as the icon row, one texel per unit; each outline one texel outside its frame | Pact at notable size with keystone-style ornament and colder, so it differs from a keystone in size and ornament, not only hue; growth plain while live. The buyable outline is brighter than any frame and is the only thing that tells buyable from in reach (§8 States), so the two differ in greyscale (amended 2026-10-31, DES-R2-1) |
| State treatments | `style.json` (colours) and the badge sheet | Colours, no geometry | Every state has a badge shape or a luminance step no other state uses; hue is a second signal. Out of reach: frame and icon dimmed, no badge. Outgrown, owned: frame at full luminance, icon desaturated, Outgrown badge. Outgrown, unowned: the Outgrown badge with no desaturation, so the badge means "this will not pay for you" and desaturation means "a point spent on it"; the bit is per viewer, so two members may see it differently (amended 2026-10-31, DES-R2-4; final on Nick's E8-S5 #283 preview). Inert fork parent: owned frame with a hatched overlay and a "no option" badge (§8) |
| Badges: level lock, exclusive lock, toggle on, toggle off, disabled in the Vault, one chosen-option badge per fork option (ten on the current layout: three Vein Shape, three Ore Focus, two Yield, two Orb Collection), pending, pin, Outgrown, no option | `gui/tree/icons.png` | 9x9 units each, one sheet | Shape, not colour alone. The lock badge stays 9x9 and the opening level is drawn as text beside it ("26" alone is 11 px in the vanilla font); UI-UX places the number. The list is checked against §8's states by UI-UX at T8 |
| Seismic arrow, its "nothing nearby" variant, chunk cell | `gui/tome/icons.png` | Arrow 9x9; chunk cell 4 units, so the 33-cell map is 132 units, inside the 240 units of height at scale 3 on 720 high | The "nothing nearby" variant is a different shape, not the arrow greyed. T9 |
| Seismic map shades | `style.json` | Colours | One hue stepped in luminance, five steps (UI-UX sets the boundaries); unexplored is bare page; explored with unknown density (tier 3) is a two-texel hatch, never a shade. T9 |
| On-screen Resonance bar (§8, Q11) | `gui/sprites/hud/`, names set at E5-S11 (#281) | Frame and fill, one texel per unit | Includes the bonus-share segment; E5-S11 (#281) preview |
| Survey panel: depth gauge with band edges, death marker | `gui/sprites/tome/`, names set at E7-S4 (#213) | One texel per unit | The death marker in both treatments, plain and The Vault Keeps It (§8 Tab 3) |
| Mod logo | `logo.png` at the resources root, named by `logoFile="logo.png"` in `neoforge.mods.toml` | 256x256, drawn at 64x64 and scaled by whole numbers | Not a texture, so not under `textures/`. §13 says `logoFile`, not `pack.png` (E0-S16). `logoBlur=false` is needed, or the Mods screen smooths it (E1-S5, #195) |

About 11 block and item textures, about 8 frames and 6 buyable outlines, the badge and Tome icon sheets
(the badge sheet holds 19 badges, ten of them fork-option badges), the HUD bar and Survey sprites, and one
icon per skill, travel stat, notable, pact, keystone and fork option (§6.1 Totals gives the node counts),
with state carried by badges and luminance steps. Was: 7 frames and 7 nine-slice washes with
pips, retired 2026-10-26.

### Preview workflow, art side

UI-UX's preview tool (`tools/tree-preview`, §6.1) draws the authored layout file with my sprites, `style.json`
and `en_us.json` (for tooltips) at GUI scales 2, 3 and 4 at 1920x1080 and 2 and 3 at 1280x720 (no scale 4
exists at 720 high), in UI-UX's named views, on Nick's PC. A debug command opens the Tome on the
same views for comparison.

### My gates

- Every PNG under `textures/block/` and `textures/item/` is size-checked at 16x16 (animated strips at 16 wide).
- Each texture ticket shows a hex palette, a contact sheet and Nick's yes. Only the portal has partial
  alpha; blocks tile seamlessly; portal tiers survive colour-blind simulation.
- Every class is identifiable at its §8 icon size without hovering. Icon art is legible from effective
  scale 1 upward; at 0.2x, class (by size and frame silhouette) and owned or not are still told apart.
- A pact differs from a keystone in size and ornament, and reads colder (the old pact colour, warm red
  `0xFFC0563C`, was the reverse of §8). An inert fork parent reads as deliberately inert.
- Every state has a badge shape or a luminance step no other state uses, checked on a greyscale copy of the
  preview (S33). The pairs checked include buyable against in reach, which differ only by the buyable
  outline, and an unowned Outgrown node against an unowned live one.
- The top bar matches the accepted one; the page is opaque.

### What I will push back on

A busy fill or a translucent page. A strong node drawn larger (S31). Hiding or refunding Outgrown nodes;
greying Ultimine nodes instead of hiding them. State by colour alone. Upscaled, pillow-shaded or noise
textures. Changing the accepted top bar.

### Conflicts I expect

With UI-UX over where a visual rule belongs (geometry is theirs, sprites and colours mine). With the
Manager over review cycles at T2, T7 and T8.

## UI/UX

I decide how the Tome's screens work and whether a player can read them, and I own the tree's layout rules
and the layout file (§6 Layout data; §6.1 Shape of the tree). The tree is the Path of Exile tree of §6 and
§8 Tab 1 (`DESIGN_LOG.md` 2026-10-26). What stands from the hub playtests (S3, amended): edges border to
border, an opaque page, zoom 0.2x to 1.5x on the pointer, a highlighted path. Retired 2026-10-26: hubs on a
spine, ring equal to in-cluster depth, the corridor wedge and boxes sized to their widest line. The top bar
accepted on 2026-09-03 does not change without Nick.

### What I need

- From the Balancer, via T8a: the final node list with skill, class, marks, ring, mod or tag condition and
  exclusive group, and each fork's options. (Was: per-tier prerequisites and the derived gates that set
  anchor widths; retired 2026-10-26.)
- From the Writer, before T8 sign-off: every name, description and tooltip line (the worst-case effect line
  and "Next:" line, option names on fork parents, "Outgrown", every lock reason and refusal).
- From the Developer, at T7: `TreeLayout` read from the layout file, the refusal payload, the named-view
  command.
- From Nick: the open questions and a yes on the preview.

### Per task

T5: level-up as an overlay message to every online member; the toast arrives with T7 (E7-S6, #291). T7: screen
shell on vanilla `TabNavigationBar`, reproducing the accepted top bar, with the interaction model below; no
tree drawing until T8's preview is approved; the level-up toast; the on-screen Resonance bar inside the Vault
and its client toggle, on a preview first (Q11), built by E5-S13 (#288) after E7-S2a (#255), because the bar
reads `TeamProgress` (T5's pacing session uses the E5-S5 per-minute readout instead); the Survey panel's
current-chunk line (Q13). T8a and T8: the layout file, the preview set and the §6 layout tests. T9:
Tome-visible effects (inert parent, Outgrown, toggles disabled in the Vault, Seismic Sense, Stratified's
bands, the death marker, trip counters); Vein Sight outlines checked against stone and deepslate. T10: an
action-bar message naming the limit when an Anchor is refused. T11: reset button, initiator confirmation with
the backup checkbox, vote screen opened from a toast and chat line (Q16), countdown. T12: the Ultimine nodes
hidden on the server's flag (S25); Ore Working's Mekanism-tag nodes hidden when their tags are empty (§6.1
Forks). (Was: Broad Cut hidden whole and tier pips, retired 2026-10-26.) T13 and T14: text fit with the
Writer; a UI section, by view and scale, in every playtest script.

### Screen inventory for 1.0

Tome screen. Full-window vanilla screen with the accepted tab bar and header: label, level, unspent points,
progress bar, pool on hover. Esc closes; Ctrl+Tab switches tabs. A small Ore Memory tab ships in 1.0 with the
Survey panel and the reset button (N3).

Resonance tree tab (§8 Tab 1). Opaque pannable page with faint level rings. Drag to pan with 4 px slop; wheel
zooms on the pointer; arrow keys pan, plus and minus zoom, Home centres the start. The page consumes arrow
keys, Home, plus and minus before the screen's focus navigation, except while the search box has focus
(vanilla `Screen#keyPressed` otherwise moves focus to a tab); Esc in the search box unfocuses it before it
closes the Tome. Search box, focused with Ctrl+F. Travel totals panel. Zoom readout bottom right. When the
free-respec window is open, a countdown line at the bottom. Pan and zoom persist for the session. An
empty-state line ("Mine ore inside your Vault to earn Resonance.") while the team's pool is 0 and its level is
0 (`TeamProgress`), which needs no field on the wire (amended 2026-10-27; was "until the team first enters a
Vault", which nothing synced could tell). Retired 2026-10-26: Page Up and Page Down jumping hub to hub, and
the cluster index of eight names with gate met or unmet.

Node. An icon with no text on the page (§8): class by frame and size (travel 14; small, growth and fork
parent 18; notable 26; pact 26 with keystone-style ornament; keystone 36; petal 12; provisional until Nick's
E8-S5 #283 preview), state by badge shape or a luminance step no other state uses. States per §8: owned,
buyable, in reach, out of reach, level-locked (lock badge, opening level as text beside it), excluded, inert
fork parent, Outgrown, tradeoff on, tradeoff off, toggle disabled in the Vault, pending; and the team's pin
(§8 Shared pin). In reach (settled 2026-10-27): adjacent and at its ring but no unspent point, which is every
node next to the start at level 0 and most frontier nodes for a team that spends as it earns. Drawn as
buyable without the buyable highlight (a lit outline, shape plus brightness), never dimmed or badged; its
tooltip reads "No unspent points. Next point at level N."; a click is answered with
`refusal.orevault.no_points`. Nick sees it on the E8-S5 (#283) preview. Retired 2026-10-26: the two-line
node box (name; tier pips plus "2 pts · Lv 5").

Actions. Left-click buys the node, one point; pacts, keystones and exclusives confirm first, naming the
downside or the partner they lock. Shift-click buys the highlighted path after one confirmation naming the
points (§8). Left-click on a petal picks it (free, outside the Vault); right-click a picked petal to unpick.
Buying an unowned growth node that is already Outgrown asks one confirmation naming why. Right-click opens an
action list: refund (3, 12 or 30 XP levels, or free inside the 60-second undo window or the respec window,
with its time left), toggle, pick, unpick or swap a fork option, pin. Inside the Vault, refund, toggle, unpick
and swap show disabled with "Leave the Vault to change this" (§4.4, S35); the vote on Volatile Veins and
Molten Seam works anywhere (§8). A click shows the node pending until sync; a refusal flashes it and shows the
server's reason.

Before sync. The tree draws neutral under "Waiting for team data" and takes no clicks.

Cross-cluster prerequisites. **Retired 2026-10-26:** the PoE tree has no clusters or prerequisite edges,
only adjacency; see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree".

Reset screens. Initiator: a vanilla confirmation screen over the Tome naming what is deleted and kept
(§3.5), with the backup checkbox. Other online members: a chat message with a clickable vote and a toast,
never a screen forced open; the vote screen shows who voted, the threshold, the backup choice read-only,
and a cancel for the initiator. Countdown: title plus action-bar seconds for players inside.

Trip counters (Deep Habit, Long Delve, Vein Discipline, Kindred Rock, Second Wind) show live in tooltips,
from the counters the server sends to the player while the Tome is open; Outgrown comes from a
server-computed bitset in `TreeState`, in layout order, owned or not, so the client holds no growth rule
(ADR 0006).

Survey panel and Seismic Sense map (§8 Tab 3). The shade value stays full on the wire; the map draws it in
five steps by ore left against a new chunk (under 0.25 mined out, 0.25 to 0.75 thin, 0.75 to 1.25 like new
ground, 1.25 to 1.75 rich, 1.75 and above very rich), so the steps can move on the preview with no protocol
bump. Unexplored is blank page; explored with unknown density (tier 3) is hatched, never a shade; at tier 2
every explored cell is one flat fill. A one-line legend under the map: "Shade: ore left, compared with a
new chunk". At "no denser rock nearby" the compass is a still needle with that text. A chunk with no
generation stamp reads "Generated before this was tracked", not "not current".

### Tooltip rules

The order is §8 Tab 1 "Tooltip": name; the description (at most 8 lines, S32); class and marks; on an unowned
growth node that can no longer pay, "Outgrown: <why>"; the effect at the tier the team owns, then a dimmer
"Next:" line, both from the constants (Q12); "Only in chunks generated after you buy it" on a generation node
(Q13); the vote count, threshold and pending change on Volatile Veins and Molten Seam (Q1); a fork parent's
option or that it does nothing yet; the ring and whether it is reached; on hover, the path's point cost and
highest ring; the lock or refund reason naming the node, level or partner in the way; who last bought it; for
a travel node, the team total. Limits per S32. The worst case, and so "the longest tooltip" in the gates, is
an owned Volatile Veins inside the Vault: name, description at 8 lines, class and marks, effect 2, Next 2, the
generation line, vote 2, ring, refund reason, bought by, which reaches 20. Shown beside the node, flipped to
stay on screen, wrapped with `font.splitIgnoringLanguage`. Never a raw key. Retired 2026-10-26: "next tier's
cost", "prerequisites with tier and cluster" and a name cut with an ellipsis on the box.

### Zoom and text

Glyphs and icons come out even only at a whole-number effective scale (zoom times GUI scale). Proposed: the
wheel moves between stops, 0.2 and one or two overview stops, then every k divided by GUI scale from
effective scale 1 up to 1.5. No text is drawn on the page, so the old text thresholds are retired; icons,
frames, tints and thickened lines carry the picture at every stop. Nick judges it on the preview. Minimum
stays 0.2.

### Layout data

Superseded 2026-10-26 by §6 Layout data (S27). One authored JSON resource in the mod jar lists every node
(id, skill, class, marks, ring, required mod or tag, exclusive group, drawing position), every edge and each
fork's `petalAnchor`. I author it; positions are for drawing only, and the rules read edges and rings.
`TreeLayout` reads it (§11 Tree Layout Data); the renderer places nothing. Hidden nodes are left out by the
server's flags, so there is no separate no-Ultimine layout. Retired: the structure file with no coordinates,
`TreeLayout` as a function of a width table, and the golden `resonance_layout_en.json` files.

Position means distance from the start and nothing else: a node sits at distance at least `max(1, ring)` and
is drawn in its ring's band (§6 Ring). No node goes nearer the start to tidy the picture, and copies of a
skill run outward in ring order.

### My gates

- Nick's yes on the preview set at scales 2, 3 and 4 on 1920x1080 and 2 and 3 on 1280x720, with real names
  in the tooltips: the start area at zoom 1, level 0 at first login (every node next to the start in
  reach), the whole tree at 0.2x, every class in every state, a hover with its path highlighted, the
  longest tooltip (owned Volatile Veins inside the Vault), the tree with the mod-gated nodes hidden, a
  mid-game state (level 15, 75 nodes owned, two forks chosen, two growth nodes Outgrown, a tradeoff on, a pin
  set), the Survey panel with the Seismic map and its legend, and the Tome opened inside the Vault with
  toggles disabled.
- The §6 layout tests, shown failing once: every node at distance at least its ring, copies of a skill in
  ring order, every mod-gated node on a dead end, exclusive leaves off one junction, petals with no edges,
  no edge through another node; plus no two icons overlapping and edges meeting icon borders. (Was: the
  polar geometry tests over hubs, rings, corridor and gutters, with random widths; retired 2026-10-26.)
- A pure test that every lock cause produces a specific reason, and a tooltip test over every English node.
- Every click visibly answered: pending, then a state change or a stated refusal.

### What I will push back on

Grids, lanes, fit-to-screen zoom. Any top-bar change. Placement code in the renderer. State by colour alone.
A screen forced open on another player. Clicks with no refusal path. A provisional tree view.

### Conflicts I expect

With the Writer on name length and line limits. With the Balancer if a strong fork option is to be drawn
bigger. With the Manager on preview cycles at T7 and T8.

## Writer

I own the spec, the design log, lang text, tickets and PR bodies. A number goes into a description only
when I can trace it to a constant.

### What I need

From the Balancer, before T5 and before each T9 batch: every tier's number, its constant, and whether it
is derived or measured. From UI-UX: the limits in S32 and the top-bar label slot as reproduced. From the
Developer, in each PR body, the ids registered or renamed and any behaviour that differs from the cited §.
From QA: counts from the XML and playtest results in Nick's words. From the Manager: every decision Nick
makes, dated and in his wording, the same day.

### Per task

Docs PR: done by #158; the `pack.png` item is settled (§13 names `logoFile`; the Designer names the file
`logo.png`). T1: the `neoforge.mods.toml` description and an `en_us.json` skeleton; check `CLAUDE.md` Build
against the rebuilt Gradle setup. T2: lang for Vault Frame, Crude Vault Igniter, the creative tab and the
igniter tooltip. T3: §3.1, §3.2 and §11 checked against the code. T4: tolerances in §3.1, marked unplaytested.
T5: the level-up overlay message (the toast's text comes with T7, E7-S6); §4.3 marks 7,200 blocks an hour as
derived until measured. T6: §11 "SavedData Pattern" matching what is persisted. T7: tab names, top-bar labels,
waiting-for-sync text, lock reasons, refusal, pending, swap and "Leave the Vault to change this" strings, each
naming the node, number or level in the way, including every key in ADR 0006's refusal table and
`disconnect.orevault.layout_mismatch`, whose text I own. T8: final ids, display names, reserved ids, lang keys
(S36, S37). T9: per node, an effect-line lang template per tier whose numbers are arguments filled from the
effects constants (§8 Tooltip, Q12), and a description that says in words what the node does and repeats no
number the effect line shows; no number is typed into a lang string; a node whose table hedges goes back to
the Balancer before I write it; §13 ticked per node as each merges. T10 to T12: Anchor, reset and integration
text; no non-Ultimine string names an Ultimine node. T13: the complete `en_us.json`. T14: playtest notes, log
entries, release notes, `KNOWN_ISSUES.md`, §12 and §13 in step with what shipped.

### My gates

1. Every registered block, item, entity and node has a name key, and every node a description key.
2. No description contains a banned word (S38).
3. Every effect-template argument is a constant, per tier; no number is typed into a lang string.
4. No description copies rationale; the node's text is the description and the notes are not.
5. Every tradeoff and fork-option description says it can only be changed outside the Vault, except
   Volatile Veins and Molten Seam, whose say they are team-voted and change only once nobody is inside
   (Q1); Molten Seam's says voting it off leaves existing pools.
6. A behaviour change has its spec change in the same PR, and every `§N` cited in code exists and says
   what the code claims.
7. A PR body opens with the spec reference, says why before what, quotes QA's counts, names what was not
   verified, and carries `Closes #N`.
8. "Ready to merge" appears only when the Manager has said so.
9. Any decision Nick or a playtest changed has a dated log entry naming what was rejected.

### What I will push back on

Descriptions written from rationale notes rather than the table ("The mirror image."). A vague-but-honest
description for an unspecified node. A test that every description has a digit. Code following §11's
generator sample. §13 ticked from memory. Reopening the hub tree (retired 2026-10-26).

### Conflicts I expect

With the Balancer: a formula is fine in text if the code runs it and the cap is stated; a word in place of
a number is not. With UI-UX: when a description outgrows the tooltip, the number stays and the sentence
shrinks. With the Developer: behaviour changes without the spec edit; I block rather than file a follow-up.

## Balancer

I own every number, the level curve and node interactions. Hours come from the §4.3 curve: 30 levels, ratio
100, 218.9 ore an hour (7,200 blocks an hour times ADR 0009's closed-form density) times 3.4845 Resonance per
ore (68/25/7, rare at 12.5) = about 762.6 an hour, about 76,259 total (re-derived 2026-10-27; was 220, 767
and 76,700). Purchase
timings come from a model over every §6.1 node row with its ring, at 5 points a level (§4.3 step 2). (Was:
gates at S6 and 12 points a level, retired 2026-10-26 with the tiered tree.)

### What I need

- From the Manager: no T9 ticket until the T4 baseline is measured and T5's pacing is inside band; T8a
  before T5's point award.
- From the Developer: density, mix, vein sizes and ore rate each in one place read by generator and curve;
  per-tier effect values in one constants source (S41); the per-minute readout (S44).
- From QA and Nick: level-up timestamps with block and ore counts, solo and for two or more.
- From the Writer: every spec edit touching a number before merge.

### Per task

T4. Pure tests pin 3% stone, 6% deepslate, 68/25/7 in stone and 60/30/10 in deepslate, vein sizes as vanilla's
configured `size` (common 9 to 12, uncommon 7 to 9, rare 5 to 7), and the floor (S13). The mean stone-band
vein is about 7.4 ore, about 26 Resonance (derived from `docs/research/vanilla-ore-generation.md`). Tolerances per S12.

T5. `LevelCurve` and `TeamScaling` tests: per-level costs, 5 points per level (§4.3 step 2; 12 a level retired
2026-10-26), multipliers 1.0, 1.1, 1.2, 1.4, unknown size as 1, milestones at 68/25/7 with rare at 12.5 and
218.9 ore an hour, as cumulative thresholds: level 1 costs about 113 Resonance and arrives at 0.15 h, level 2
at about 245 cumulative and 0.32 h, level 10 at about 2,555 and 3.35 h, level 18 at about 10,784 and 14.1 h,
level 29 at about 64,966 and 85.2 h, level 30 at about 76,259 and 100 h. Thresholds are stored cumulative,
each rounded from the cumulative value, and the pool is rounded once, half up, at entry after team scaling
(ADR 0005 section 3). The hours do not move with the mix, because the total is the hourly rate times 100
hours; the thresholds do. All derived, none measured. One rare constant (S40), which I set in the docs PR. The
pacing session per S11, with the tool named in the script.

T6. `deepest_y` must not read as 0, or Shallow Grace starts Outgrown and Highwater Mark counts free steps;
absence covers it (S16).

T8a. The node list with skills, classes, marks and rings, and §6.1 Totals checked against the layout file:
the share of the tree owned at level 30 at 5 points a level. (Was: pin 354 and the gates, and test each
gate reachable from the clusters above it; retired 2026-10-26 with the anchors, `DESIGN_LOG.md` 2026-10-26,
"The Resonance tree becomes a Path of Exile tree".)

T9. A stacking review per batch of nodes before its tickets, and pure tests for every per-tier value.

T10: Vault Presence's 4/12/28 under the ceiling of 32, Far Reach included. T11: repeated resets open the
respec window at most once per 24 h of server time (Q14). T12: Volatile Veins under
Ultimine; Mekanism tiers hidden without it. T14: the measurements, then a re-derived curve.

### Tree cost and gates

**Retired 2026-10-26.** The cluster costs (354 in all), the anchor gates of S6 and 12 points a level described
the tiered cluster tree, replaced by the PoE tree (`DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a
Path of Exile tree"). The figures are in git history before this change. What replaces them: every node costs
1 point, 5 points a level pinned (§4.3 step 2), 150 points at level 30 against 193 to 199 nodes in a vanilla
pack and 207 to 213 in FTB Evolution 2 (§6.1 Totals), and a node's ring is its only level floor (§6 Ring).

### Derived, not measured

- 218.9 ore an hour is 7,200 blocks an hour times ADR 0009's closed-form density (was 220, from a flat 3%).
  Density measurement confirms the density, not the rate; only a timed session measures blocks per hour.
- Calloused Hands' cap arrives at (e^(0.4/0.07) − 1) × 10,000, about 3.0 million blocks at tier 3.
- Silent Stone pays 0.30 a second per active member below Y=0, raised by that member's counted bonuses and
  team-scaled like ore (Ng; §6.1 Silent Stone): 1,080 an hour solo against about 763 for bare ore, about
  1.4 times. (Was 3 a second, about 14 times solo; retired 2026-10-26.)
- Stone Memory T5's 0.5% burst paid about 1,745 Resonance an hour, about 2.3 times the curve's rate. Lowered
  to 0.05%, and T3 to 0.05 per deepslate block, about a fifth of each band's ore rate (Q8).
- Every value pinned on 2026-09-09 is unplaytested.

### Stacking that I think breaks something

These are my findings for the T9 reviews; Nick answered items 1, 2, 5 and Silent Stone (N7, N6, N5, Ng), the rest come back
to the Manager as proposals per T9 batch. The hours in item 1 and the multiples in items 2 and 6 come from
the tiered-tree model (level requirements, 12 points a level) and are re-run on the PoE tree before the
first T9 batch opens.

1. The curve is priced for a miner who buys nothing. With density nodes bought at their level requirement,
   level 30 arrives solo at about 23.7 h, 13.2 h with the summed Resonance bonuses, 7.6 h with Vault Echo
   and Stone Memory T5 (N7: the curve stretches to fit).
2. Summed, the Resonance bonuses reach about 5.3x and the penalties total −135%, enough to go negative; a
   summed −50% Greedy Seams against +200% of bonuses costs a sixth (N6: multiply after, floored at zero).
3. Brittle Stone's instant break makes Bedrock Communion's speed halving worthless.
4. Vault's Blessing keeps beacon Haste II permanent, so Vault Fever's cost is never worth paying; Vault's
   Purity would strip Fever's Haste and Constitution's Regeneration.
5. Apprentice's Ledger never retires; Shallow Grace reads one member's depth and pays the team (N5: Ledger stays; Shallow Grace counts members inside the Vault).
6. Echo's flat burst favours small veins: with Abundance, Echo T3 turns a 14-Resonance common vein into 64.
7. Hoarder's Instinct's ×3 then ×4 is the tree's largest Resonance multiplier, on a free option.
8. Volatile Veins: the spec does not say whether a vanished vein counts as completed. Settled 2026-10-27:
   neither; a vanished vein closes silently, with no Vein Discipline change, no Chapter's End count and no
   Last Ore or Clean Cut (ADR 0004 `VANISHED`; §6.1 Volatile Veins).
9. Silent Stone does not say whether it pays per member or per team, or where scaling applies.

### My gates

Every number is read from a constants source and pinned per tier by a test. No T9 ticket merges before its
combinations are checked against the composition rule and the list above. Spec, constants and lang quote
the same number. A derived number carries its assumption and confirming playtest in its Javadoc. Nothing
pays Resonance from machine breaks, directly or through vein completion, and every time-based bonus has an
AFK guard.

### What I will push back on

Modifiers before the measured baseline. Linear Calloused Hands, a hard streak reset, 8% or 15% density,
density as config, Attunement slots. Growth nodes kept alive by behaviour. Any "(balance TBD)". Nick likes
being overpowered, so I price the curve rather than weaken nodes.

### Conflicts I expect

With the Developer over the Resonance accumulator. With the Writer over who fixes stale numbers. With the
Manager over T9 starting alongside T4 and T5.

## Playtester

I stand in for a player who has never read the spec, and for Nick. I cannot run the game; every judgement
about feel here is a prediction until Nick has played it.

### What I need

From Nick: playtest replies quoted in his own words, plus `logs/latest.log` for pacing sessions. From QA:
the numbered steps that check the build works; I add the questions that check it is good, and we ship one
script. From the Balancer: predicted times and tolerances (S11), and the milestone table before T14. From
the Developer: the per-minute readout and the progress-wipe command (S44). From UI-UX and the Designer: the
preview Nick approved. From the Writer: a dated log entry for every design-shaped point I hand over.

### The first ten minutes

**Superseded 2026-10-27 by `docs/PLAYER_STORIES.md` (E0-S17).** Every T2 to T8 ticket is checked against
that file's "The first ten minutes" and its per-task stories, and a T9 node ticket answers its "how does the
player know it worked?" question there first. The story that stood here was written for the tiered tree,
retired 2026-10-26 (`DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree"): level 1
with 12 points, "Prospecting lit" and a cluster of nine buyable nodes. Under the PoE tree, level 1 brings 5
points and the ring-0 nodes next to the start (§4.3, §6). The old text is in git history before this change.

### Per task

T2: did Nick have enough iron, and how long did it take? T3: the return portal is visible on arrival and
returning lands where you left. T4: after ten minutes at zero points, does the Vault feel rich enough to come
back to and lean enough that the tree has something to sell? T5: the pacing session; the orb, and the level-up
message arriving, its time recorded as an observation, are on my checklist, with ore an hour inside the S11
band (the toast comes with T7). T6: log out inside the
Vault, log back in, open the Tome; nothing changed. T7: the level-up toast; the empty-state line, shown while
the team's pool and level are 0 (§8); the Tome reaching solo players; every disabled control saying why on the
control itself. T8: can Nick find the next thing to buy in a few seconds, and at 0.2x find a searched or
pinned node and zoom back in one gesture? (Was "a named cluster", retired 2026-10-26.) The answer to "is the
loop playable" comes here, from the first node bought through the Tome. T9: every node ticket answers "how
does the player know this worked?" After the ring-0 batch, the first-ten-minutes session
(`docs/PLAYER_STORIES.md`, E9-S59 #363), which answers Q17. T10: placing an Anchor, and a refused one, tell
the player what happened and why. T11: two temporary dev sessions on Nick's PC (Q10), one votes; the vote,
countdown and respec window must reach players who did not press the button; one session leaves the party
while inside its Vault and is sent home with a chat line saying why. T12: on a pack without Ultimine, does
anything mention it? T13: read every tooltip as a new player; any line that needs the spec is a screen defect
for UI-UX. T14: the full playtest against the milestone table.

### Session template

```
Session: <ticket or build>, <date>
Build: <commit>, mods: <list>, team size: <n>
Kind: pacing (buy nothing) / first ten minutes (buy freely)
Focus (at most two): <question 1>, <question 2>
Setup: <fresh world or debug wipe>, <tool and enchantments>, <points spent>
Steps: <QA's numbered steps, at most fifteen>
Watch for: <the confusion and boredom risks from the player story>
Record: <readout numbers, level-up timestamps>
After the session, answer in your own words:
  1. <focus question 1>
  2. <focus question 2>
  3. What did you expect to happen that didn't?
  4. Was there a moment you didn't know what to do next?
Attach: latest.log, screenshots of anything wrong
```

A pacing session never includes purchases, because the growth nodes change the rate being measured.

### Sorting Nick's feedback

```
| Quote | Shape | Why | Goes to | Record |
| "<Nick's words>" | bug / design / pacing / screen | <the spec line it breaks or follows> | Developer / Writer / Balancer / UI-UX | ticket #, or log entry date |
```

Bug-shaped: the build does not do what the spec says; a ticket with repro steps and a regression item, fixed
directly. Design-shaped: the build does what the spec says and that is wrong, or the spec is silent; a dated
log entry quoting Nick and naming what is rejected, then a spec PR, then code. Pacing: a measured time
outside tolerance; to the Balancer, and the curve is re-derived before more modifiers land. Screen: Nick
could not find, read or understand something; to UI-UX as a defect in the screen, never a player mistake.
Before filing a design-shaped point I check the log's rejected lists, and if Nick asks for something already
rejected I bring him the entry and ask whether it still stands.

### My gates

A player-visible ticket has its player story before it opens and an answer to "how does the player know it
worked?" A session has at most two focus questions, each with an answer that would make us change
something. Pacing checks use numbers with tolerances. Nothing a player can buy is invisible in play: no node
without a visible sign that it fired, no lock without a named reason, no disabled control without a reason
on it. Every design-shaped point reaches the Writer the same day, with Nick's words.

### What I will push back on

A node that works and shows nothing. A growth node that retires silently. A screen that needs the spec to
read. A first hour with nothing to buy, or points piling up that nothing will take. A rejected design
returning under a new name, including a "map mode" fitting the whole tree on one screen. Anything that
punishes exploring. Anything Nick has said he does not want.

### Conflicts I expect

With QA on "measured, not felt": both, from separate sessions. With the Balancer on banked early points: I
ask it in the first-ten-minutes session rather than change it before play.
