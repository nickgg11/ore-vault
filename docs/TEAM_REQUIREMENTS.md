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
| S3 | Is the hub layout playtested? Is the 3%/6% baseline? | The hub concept was played twice and is not reopened. Not yet played: the 944 by 6008 page, the 0.2x minimum, the chain highlight and named lock reasons as built, per-class art. Those are T8's session. The baseline is decided but unplayed; T4's session checks it. Neither is a question for Nick. | Log 2026-09-07 and 09-09 entries headed "playtest of the hub renderer"; lessons doc ("none of the three has been playtested"); log 2026-09-27 "Still open" |
| S4 | Where builds and runs happen; QA's request to allowlist Maven and Mojang hosts for cloud sessions | On Nick's PC, through a Remote Control session. The allowlist request and the Xvfb plan are withdrawn. No result is reported unless it came from his machine. | `CLAUDE.md` Build (Nick, 2026-10-26) |
| S5 | "Vault Resonance" prefix in UI text | No prefix. Plain "Resonance" everywhere; renaming is a late-development question. | `CLAUDE.md` Conventions (Nick, 2026-10-26) |
| S6 | Anchor gates as stated points (10/25/45/55/70/100) or as fractions | **Retired 2026-10-26:** the PoE tree has no anchors or gates; see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree". Was: Fractions of `totalTreeCost`, rounded up: Prospecting 0, Excavation 15, Assay 39, Broad Cut 39, Metallurgy 71, Claim 89, Deep Lore 110, Mastery 160. The point figures date from the 225-point tree. | §6.1 ("pinned to a fraction"); log 2026-09-07; the §6.1 cost columns sum to 354 (parsed) |
| S7 | `totalTreeCost` over the full registry (354) or excluding conditional content (311) | **Retired 2026-10-26:** every node costs 1 point and points per level are pinned at 5 (§4.3); see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree". Was: Full registry, every pack: 354, so `pointsPerLevel` is 12. With 311 the tree is not buyable at level 30 (330 points against 347 holdable). The Writer withdrew 311. | §4.3 steps 1 and 3; arithmetic |
| S8 | A stub node at T5 or the full node data first | Full node data model (ids, tiers, costs, level requirements, class, marks, prerequisites, reserved ids; no effects, no layout) as T8a, before T5's point award. With one node, `pointsPerLevel` is 1. | §4.3 step 3 |
| S9 | First purchasable node for the loop | Stone Memory T1, bought by debug command at T5 as a technical check: visible on the next stone block, no Resonance or baseline effect. The player-facing loop milestone is the first node bought through the Tome (T8). Vein Expansion T1 is the first T9 generation node, verified with the density command. Guide Vein is not used. | Vein Expansion T1 is +15% over about five veins in ten minutes and a modifier before the measured baseline; Guide Vein is about +0.5% of a chunk's ore (Balancer, Playtester) |
| S10 | A provisional tree view for the T5 node | None. Command purchase until T8's approved renderer. | Lessons doc: renderer built three times |
| S11 | T5 pacing check "level 1 inside the first hour" | Replaced by a measurement: solo, 30 minutes in fresh chunks, zero points, buying nothing, tool named by the Balancer. Bands of ±20%: blocks 5,760 to 8,640 an hour, ore 176 to 264 an hour, level 1 at 7 to 11 minutes, level 2 at 15 to 23. Ore per hour outside its band means the curve is re-derived before any T9 node merges. | §4.3 curve (about 76,700 total at the 68/25/7 mix; level 1 costs about 114); a 2.7x mispricing still passes the old question; log 2026-09-27 "Still open" |
| S12 | How density is measured | Not `/orevault testore`, which fills a cube. A pure JUnit count over the placement function, then a read-only `/orevault density [chunks]` on a dedicated server. Tolerance: each band within ±5% relative over at least 81 chunks; rarity shares by placed blocks within ±2 points of 68/25/7 in the stone band and 60/30/10 in deepslate, on a vanilla-only pack, with Guide Vein off. | Old `VaultDiag` (fact); Balancer owns numbers |
| S13 | Generation model and the 40% floor | Veins are placed by count and size, never filled toward a target. The floor clips proportionally across vein counts, every clipped modifier is in the snapshot, and the floor test uses the full deepslate stack (132% requested). | §3.1; Vein Expansion plus Proliferation alone reach 26.4%, so a test on those passes vacuously |
| S14 | T3 as three PRs or one; how the logout case is checked | One PR, three acceptance criteria, plus a login check that sends a player whose `in_vault` names a Vault they are not in to their return position. The gate is a scripted headless restart on every dimension PR; Nick confirms once in a client. | `CLAUDE.md` rule 2; lessons doc (#89's regression) |
| S15 | Who verifies the generator never reads SavedData | A static rule: ArchUnit, owned by the Reviewer. QA does not sign it as observed. `GenSnapshot` lives in `core.gen`, built on the main thread. | `CLAUDE.md` rule 1; the old `SkillSnapshot.of(OreVaultTeamData)` made worldgen depend on `data` |
| S16 | Persisted shape basics | `data_version` from the first field; the team-data shape agreed in an ADR before T5 opens. Available points derived, not stored. `level_reached` stored and monotonic. `deepest_y` absent means never entered: no sentinel, no flag. No stored derived totals. | `CLAUDE.md` rule 6; §4.3 step 4 (thresholds must not move under teams that passed them); Architect owns the data model |
| S17 | Save-shape tests as JUnit or gametests | JUnit through `CompoundTag` if T1 confirms it loads, as the old `PlayerStatsVersionTest` did; one gametest per storage mechanism (SavedData, chunk attachment, player attachment across death). `CLAUDE.md` Tests is corrected after T1 confirms. | Old test suite (fact) |
| S18 | Team data and the Tome grant at team creation | Team data is created on first write, never on `TeamEvent.CREATED`. The Tome is granted on first login and recorded as `tome_granted`. | `CLAUDE.md` rule 2's reasoning; §13 records that `PLAYER_JOINED` misses solo players |
| S19 | Disband and reset mechanics, whatever Nick picks | No level is deleted or moved while the server runs. Disband sets `disbanded_at` and evacuates online players; the folder is handled at the next start, before restore, and restore skips it. | §11 registry-desync warning; Architect, Developer, Reviewer |
| S20 | Vein index timing and storage | T4's placement returns vein extents as values (ADR 0004 decided in T4). The index is persisted in a versioned chunk attachment by its own ticket ahead of Vein Sight, written only for the chunk being generated; veins never cross a chunk border; player-placed ore is never indexed; chunks generated before it never complete. Chapter's End state lives on the chunk, not in `PlayerStats`. | §11 Vein Index; no player worlds exist before 1.0; a reset wipes chunks but not player files |
| S21 | What "machine-broken" means; machines and vein completion | A break pays only when the breaker is a `ServerPlayer` that is not a `FakePlayer`; no breaker pays nothing. A vein whose last block a machine broke fires no completion effect. | §3.4 ("never award Resonance, in any quantity, under any node") |
| S22 | §13 "Cross-dimension block-break Resonance award" | Deleted. | §3.4 |
| S23 | Silent Stone's rate as config | A constant. Its value is open for Nick. | `CLAUDE.md` rule 7; log 2026-09-27; §1 "config-light" |
| S24 | §9 "no direct import of Ultimine classes" | Ultimine is `compileOnly`; every Ultimine type is referenced only from `integration.ultimine`, entered after `ModList.get().isLoaded("ftbultimine")`. §9 is rewritten. | The 26.1 API is typed registration events, so §9 cannot be built as written; `CLAUDE.md` rule 5 keeps §1's intent |
| S25 | Whose Ultimine flag the UI follows | The server's, sent in the sync payload. Never the client's `ModList`. | §2; client and server mod lists can differ |
| S26 | Tome toolkit | Vanilla screens for 1.0. No FTB Library, no FTB `TeamProperty`. If FTB Library is ever used, `CLAUDE.md` gets an import boundary for it first. | The accepted shell was vanilla `TabNavigationBar` (log 2026-09-03, §13); rule 5 defines no FTB Library boundary; `TeamProperty` would import `ftbteams` outside the team package |
| S27 | Layout as a committed coordinate file or computed | Computed at runtime by the ported pure `TreeLayout`, from an authored structure file UI-UX owns. A Gradle task writes English golden files that the preview draws and Nick approves; a test fails when they drift; the geometry proofs also run with random widths. The Designer's `style.json` holds no geometry, and frames follow class. | Translated widths (the old `ResonanceTreeTab` measured `font.width`); commit 953c9e6 moved layout to UI-UX |
| S28 | Nine-slice borders in `style.json` | In each sprite's `.mcmeta` under `textures/gui/sprites/`, not duplicated, once the Developer confirms the 26.1 sprite API in the sources jar at T7. | Vanilla GUI sprite system |
| S29 | QA's "no vertical placement without a prerequisite" property | Replaced by the polar invariants and a radial test of §8's rule. | §8 ("one outside the other only when one requires the other") |
| S30 | Node frames in T8 or T13 | T8. A layout cannot be approved with placeholder frames. | Anything visual needs Nick's yes on a rendered preview |
| S31 | Drawing Hoarder's Instinct larger because it is strong | No. Class decides visual weight; strength is a number for the Balancer. | §8 class table |
| S32 | Tooltip and name limits | At most 240 GUI units wide and 20 lines; description wrapped at 220 px, at most 8 lines; prerequisites at most 4, then "and N more". Names at most 196 px of text in the 208 px box (anchors 228 in 240). Previews include 1280x720. | Vanilla picks the largest GUI scale leaving at least 320x240; log 2026-09-07, 09-09 |
| S33 | What "matches the preview" means | In each named view: the same nodes visible, boxes within 2 GUI px of the layout, no truncated text, no edge across a box, zoom readout present, tooltip line count equal, every state identifiable in greyscale with names hidden. | A browser render cannot match the game pixel for pixel |
| S34 | Reset button in the top bar; who must hold the Tier 4 igniter | Not in the top bar (its location is open). It shows when the viewing player holds a Tier 4 igniter, per §8; §3.5 changes to match. | Log 2026-09-03 ("accepted as-is"); §13 Polish; a client can see only its own inventory |
| S35 | Refunding a fork parent inside the Vault | Refused inside the Vault, since it clears the option. | §6.1 Forks (the restriction is on changing an option) |
| S36 | Lang key format | `node.orevault.<id>` and `node.orevault.<id>.desc`, written into §8. | Writer owns lang; vanilla's order; the old keys |
| S37 | "Ultimine Gambit" or "Volatile Veins: Ultimine Gambit" | "Volatile Veins: Ultimine Gambit". | §6.1; log 2026-09-07 moved the box cap to fit it |
| S38 | Banned description words | The old `NodeLangTest` list plus "slightly", "intensifies", "near-", matched as whole words. "About" and "roughly" pass when they qualify a number. Shown failing on "The mirror image." and "a moderate frequency". | Log 2026-09-09 |
| S39 | A new composition rule or an amendment | Amend §11: its contributor table lists every node that pays or costs Resonance or mining speed. How penalties apply is open for Nick. | §11 already says percentages sum and multipliers multiply |
| S40 | Disagreeing Resonance values | One constant for rare ore (12 and 12.5 exist today); the Balancer picks it and §4.2, §13, curve and orb read it. Vault Echo is 25/35/50. Stone Memory T3 pays on deepslate, not stone. | Log 2026-09-27 (one constant); log 2026-09-09 (the node's own text is the description) |
| S41 | Where per-tier effect numbers live | One effects-constants source beside `NodeCosts`, read by effect code, the lang test and the balance tests. | Lessons doc ("one constant read by everything") |
| S42 | Port the old pure classes or rewrite | Port, one ticket per class through normal review, into `core.*` with no Minecraft imports, without stored derived totals and without `NodeMigration`. Minecraft-facing adapters are rewritten. Nick can overrule. | `CLAUDE.md` (read the old commits before rebuilding); lessons doc "What went well"; Architect's purity findings |
| S43 | ArchUnit strictness | At T1, each rule shown failing once against a planted violation. | ArchUnit fails a rule matching nothing unless told not to, and a vacuous pass is not evidence |
| S44 | Debug tooling | Behind `[debug].enable_debug_commands`: density count, create a team's Vault, dump team data, wipe a team's progress, a per-minute blocks, ore and Resonance readout held in session state (never persisted), named-view client command. Removed at T14. | `CLAUDE.md` rule 7; no persisted diagnostic |
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
| Ne | Resonance gain display | Nick read it as a second segment inside the Resonance bar showing the extra gain. To confirm with him before T5. | T5, T7 |
| Nf | Prospector's Eye | Requires Vein Sight. | T8a |
| Ng | Silent Stone | Each active member below Y=0 earns 0.30 Resonance a second, raised by that member's counted Resonance bonuses (no penalties), and the pool is team-scaled like ore: sum / teamSize × (1 + 0.1 × (teamSize − 1)). About 1.4x bare ore at any team size (Nick, 2026-10-26, after he asked that it feel fair to both a solo player and a team of five). Which nodes count is in §6.1 Silent Stone. | T9; deepslate ore rate measured at T14 |
| Nh | Players inside at reset | Sent to the Vault's spawn point, where the portal arrives. Their saved return position may be inside rock that the reset replaced. | §3.5; T11 |
| Ni | Live Vault cannot unload | Everyone is sent out and the Vault regenerates at the next restart, with a message saying so. | T11 |
| Nj | Mekanism | Tags only. FTB Materials (in Nick's pack) ships Mekanism-style clumps, shards and crystals under `c:` tags with no Mekanism code, so Ore Doubling's Mekanism tiers resolve through tags; see `docs/research/ftb-materials.md`. | §2, §9; T12 |
| Nk | Cross-cluster prerequisites | **Retired 2026-10-26.** Nick first ruled out cross-cluster prerequisites, then replaced the hub tree with a Path of Exile style tree (one point per node, adjacency, no anchors or clusters), which makes the rule moot. The spec's §4.3, §4.4, §6 and §8 Tab 1 are rewritten for it; anchor, gate and cluster text elsewhere in this file is superseded. See `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree". | §6.1; T8a, T8 |

## Shared task list

Owner is the role that produces the work; reviewers are the matrix's. "Done when" is the gate the Manager
checks before "ready to merge". Every build, test and client result is from Nick's PC.

| Task | Owner | Reviewers | Done when | Needs from other roles |
|---|---|---|---|---|
| T1 Toolchain, empty mod | Architect (ADR 0001), then Developer | Developer, Reviewer, QA on the ADR; Reviewer, QA on code | `build`, `test --rerun-tasks` with XML counts, `runServer`, `runGameTestServer` with one passing and one deliberately failing gametest, `runClient` to the title screen; no committed `org.gradle.java.installations.paths`; ArchUnit rules each shown failing; `CompoundTag`-in-JUnit result recorded | Versions re-checked (Ultimine 26.1.2.6 in FTB Evolution 2); Writer's mod description; Nick on the CI question |
| Docs PR | Writer | Balancer, Developer | Every item under "Spec corrections" fixed, or marked with the open question it waits on; log entries for the restart and the 2026-10-26 decisions | Balancer's rare constant and tolerances; the cross-cluster restructure (Nk, retired 2026-10-26) and the ore research (N4) for the items that wait on them |
| T2 Vault Frame and Crude Igniter | Developer; Designer for textures | Reviewer, QA; UI-UX and QA on art; Nick | Id-aware registration, creative tab, `items/` definitions, pickaxe tag under `data/minecraft/`; gametests for tag, drop and recipe; Nick sees both in creative and JEI and approves the contact sheet | Nick's liked textures and art tool approvals; portal cost answer; Writer's lang |
| T3 Dimension path | Architect (ADR 0002), then Developer | Matrix design row; Reviewer, QA, Architect on code; Designer, UI-UX on the portal texture | S14 and S19 hold; lazy create only through `findOrCreate`; one world-map insert that calls `markWorldsDirty()`; restore survives a bad folder; return position saved after success; scripted restart passes; level-unload spike result written down; Nick confirms logout-inside once | Designer's portal strip and four tints; Playtester's arrival checks |
| T4 Generator, classifier, baseline | Architect (ADR 0003 snapshot, ADR 0004 vein extents), then Developer | Matrix design row; Reviewer, QA, Architect, Balancer on code | S12 and S13 tests; seeding determinism; `Variant` against `dimension_type` JSON; density command within tolerance; Nick mines ten minutes in a fresh Vault and answers the T4 question | Balancer's tolerances (S12); QA's density procedure |
| T8a Node data model | Architect, then Developer | Balancer, Reviewer, QA, Writer | Tests pin 354, the gates in S6, 12 points per level, every fork option covering every parent tier, each gate reachable from the clusters above it. **Amended 2026-10-26:** 354, the gates, 12 a level and Nk are retired with the PoE tree; the tests are now the layout tests in spec §6 and 5 points a level (§4.3); see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree" | Balancer's node list; N4 and Nf |
| T5 Resonance loop | Developer | Reviewer, QA, Balancer, Architect | Orb modelled on `ExperienceOrb`; three-breaker gametest (S21); vanilla XP still drops; curve and scaling tests; pool persisted with `data_version` from its first commit; Stone Memory T1 bought by command and its XP seen; Nick's pacing session inside the S11 bands | T6 ADR agreed first; Balancer's rare constant and milestones; Designer's orb tint check; Playtester and QA's script |
| T6 Persistence | Architect (ADR 0005); Developer | Matrix design row; Reviewer, QA | Lands with T5's first field; golden key-set test per shape; fixtures load; absent fields read their documented default; one gametest per storage mechanism; reserved ids | N1 |
| T7 Network and Tome shell | Architect (ADR 0006), Developer; UI-UX for screen structure | Matrix rows; Designer, Developer, Playtester on screens; Nick | Payload set in the Architect's section; server validates every mutation; refusal payload with a lang key; pending state; header drawn after first sync; accepted top bar reproduced; `runGameTestServer` after each payload change; Nick's yes | N2, N3 and Nd; Writer's UI strings; Designer's shell art |
| T8 Tree layout and renderer | UI-UX (structure, views, geometry tests); Designer (style, frames, washes, badges); Developer (`TreeLayout` port, renderer) | Designer, Developer, Playtester; then Nick | Nick's yes on the preview set; golden files match `TreeLayout`; geometry proofs pass, also with random widths; renderer holds no placement code; client matches the preview (S33) at scales 2, 3 and 4; first node bought through the Tome | Final node list and gates from T8a; Writer's names and every line a box can draw; Nick's preview approval |
| T9 Node effects | Developer, cluster by cluster in §13 order | Reviewer, QA, Balancer, Writer; UI-UX if it shows in the Tome | Balancer's stacking review before the cluster's tickets open; per-tier pure tests; gametest where a block or drop is touched; Fortune-before-Quantity test; every node answers "how does the player know it worked"; descriptions pass the lang test | T5 pacing inside its band; N5 to N7; UI-UX mockups for Tome-visible effects |
| T10 Chunk loading | Developer | Reviewer, QA, Architect, Balancer | `TicketController` on the mod bus; tickets validated on load; the ceiling has a reader; Anchor only inside the Vault; igniter returned as a crafting remainder; S21 holds | Designer's Anchor texture; Writer's messages |
| T11 Reset and disband | Architect (ADR 0007), Developer; UI-UX for screens | Matrix rows; Nick | Pure vote machine tested at 1, 2 and 3+ online; scripted reset with backup on and off, region files checked on disk; S19 holds; repeated resets do not reopen the respec window | N3, N8, Nh and Ni; T3's spike result |
| T12 Integrations | Developer | Reviewer, QA, Architect | Dedicated server boots with no soft dependencies, with Ultimine only, with Mekanism; import greps pass; Broad Cut absent without Ultimine | Nick on Mekanism; Balancer on Mekanism-only tiers |
| T13 Content | Developer, Designer, Writer | Matrix rows; Nick on art | Datagen recipes, loot, tags, blockstates; full `en_us.json`; lang coverage and PNG size tests; every texture batch approved on a contact sheet | N2 sets the PNG test's scope |
| T14 Release | Manager, with QA, Playtester, Balancer | All | Load test in FTB Evolution 2 on Nick's PC; debug block removed; `KNOWN_ISSUES.md`; §12 and §13 match what merged; full playtest against the milestone table; curve re-derived if measured rates are outside band | Every role |

Order rules. The playable loop comes first: no world-facing task T(n+1) opens until Nick has seen T(n) in a
client. Pure-logic work for later tasks (curve, classifier, node data, layout maths) may proceed while a
client gate waits. No T9 ticket opens before T4's baseline is measured and T5's pacing is inside band. No
renderer code before Nick approves the T8 preview.

## Spec corrections needed before T3

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
stone. Vault Echo burst "25–40" becomes 25/35/50. Delete "configurable as multipliers": no such key exists.

§4.4. "A full 225-point respec is ~675 levels" becomes 354 points, 1,062 levels.

§5.3. "One point per level" contradicts §4.3's `pointsPerLevel` (deferred section, still wrong).

§6 notation. "Four kinds of node" precedes ten markers; state classes (small, anchor, notable, keystone,
pact, growth, fork parent, fork option) and marks (tradeoff, exclusive, Ultimine only). "The 100-point
Mastery gate" becomes the derived 160. "Reading a node" omits `[ANCHOR]`, `[PACT]` and `[GROWTH]`.
`[GROWTH]` "worthless later by construction" becomes "their reward stops mattering as the team grows";
growth nodes never switch off (N5).

§6.1 gates. The cluster table, line "spent 100 points", and every cluster intro ("Anchor unlocks at 10 / 25
/ 45 / 55 / 70 / 25 / 100") change to S6's values, with the rounding stated and the note that with neither
soft dependency Mastery opens at 160 of 304 buyable points. "`NodeCosts` derives the point values" should
match where the code derives them.

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
| Writer | The docs PR above | Before T3 |
| Architect | Each ADR as a docs PR, and a sketch review (a paragraph and a class list from the Developer) before code on T1 and T3 to T8 | Before each task's code |
| Developer | A feasibility comment on each ticket naming the `CLAUDE.md` rule at risk and the cost | Before I assign it |
| Balancer | Tolerances (given in S12), the rare constant, the milestone table for teams of one and five marked derived or measured, a stacking review per T9 cluster | Docs PR, T5, each T9 cluster, T14 |
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

T9. Cluster by cluster, Prospecting first, each cluster's stacking review before its tickets. No node ticket
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

From the Manager, the sketch step on T1 and T3 to T8 (S47). From the Developer, the sources-jar checks
named under T4 and T11 before those tickets are sized. From the Balancer, for each node: what state it
reads (team, player, trip, chunk) and whether that state outlives logout, death, reset or a team change.
From UI-UX, the inputs the tree screen needs, so I can check they are in the sync payload.

### Package layout

Base `com.orevault.orevault`. The split that matters is pure against Minecraft-facing.

| Package | Holds | May import |
|---|---|---|
| `core.curve` | `LevelCurve`, `TeamScaling` | `java.*`, JSpecify |
| `core.tree` | `NodeDef`, `NodeDefs`, `NodeCosts`, effect constants, `SkillTree` rules, `NodeClass`, `Cluster`, reserved ids | same |
| `core.ore` | `Rarity`, classification thresholds, `OreDistribution` | same |
| `core.gen` | `VeinPlacer`, `GenSnapshot`: team id, chunk x/z, snapshot and an integer ore table to placements and vein extents | same |
| `core.layout` | `TreeLayout` and its geometry proofs | same |
| `core.mining` | `ResonanceBonuses`, drop-stage outcome, `MiningSpeedModifiers` | same |
| `core.reset` | vote state machine | same |
| `registry` | `DeferredRegister` holders, data components, attachments, tags, creative tab | MC, NeoForge |
| `team` | `TeamHelper` and FTB event registration; the only `dev.ftb.mods.ftbteams` importer | FTB Teams |
| `data` | `OreVaultTeamData`, `PlayerStats`, attachment codecs, migrations, `GenSnapshots` (built on the main thread) | MC, `core` |
| `vault` | `VaultLevels` (the one world-map insert, `markWorldsDirty()`), `VaultDimensions` (find-or-create, restore, archive), teleport, portal, layer config | MC, `data`, `worldgen` |
| `worldgen` | `VaultChunkGenerator`, codec | MC, `core`; never `data` or `team` |
| `mining` | the single `BlockDropsEvent` listener, orb award, stat recording, vein-index updates | MC, `data`, `core` |
| `network` | payload records, server handlers | MC, `data`, `core` |
| `chunkload`, `reset`, `config`, `debug` | adapters | MC |
| `integration.ultimine` | everything naming an Ultimine type, entered only after the `isLoaded` check | Ultimine |
| `integration.mekanism` | tag lookups | MC |
| `client` | `@Mod(dist = CLIENT)` entry, `client.tome`, `client.tree`, renderers, `ClientTomeState` | anything |

Each import column is an ArchUnit rule (S43). No interfaces for single implementations.

### Persisted data

All shapes start at `data_version` 1 (N1). Every field reads correctly from a tag without
it; removed node ids are reserved.

| Data | Where | Fields |
|---|---|---|
| Team | `SavedData` `orevault_team_<uuid>`, overworld storage, created on first write (S18) | `data_version`, `resonance_pool` (long), `level_reached` (monotonic), `nodes` {id: tier}, `forks` {parent: option}, `vault_variant`, `vault_generation` (+1 per reset), `free_respec_until`, `disbanded_at` (absent unless disbanded), `player_stats` |
| PlayerStats | nested in team data, own `data_version` | `ores_mined` {ore: count}, `stone_broken`, `blocks_broken`, `deepest_y` (absent = never entered), `vault_ticks`, `active_tradeoffs`, `veins_completed`, `vein_discipline_streak`, `deaths_in_vault` |
| Player | NeoForge attachment `orevault:player`, `copyOnDeath` | `data_version`, `return_pos` {dim, x, y, z}, `entry_points` [{team, generation, pos}] up to 3, `homeward` {team, generation, pos}, `in_vault`, `tome_granted` |
| Vein index | chunk attachment `orevault:vein_index` | `v`, veins [{local id, ore id, packed positions, remaining}], `chapter_fired` |
| Chunk tickets | NeoForge `TicketController` storage, validated on load against the Anchor | none of ours; the team's count is derived |
| Attuned ore | item data component `orevault:attuned` | marker; its id is save data from the first release |

Derived, never stored: available points, team deepest Y (minimum over members), the ticket count, a chunk's
completed veins. In memory only: generation snapshots, trip state (§11), the vote, the classifier table,
the curve array, Volatile Veins' pity. A stored position whose `vault_generation` is behind the team's
counts as cleared by a reset, so offline players' files never need editing.

### Payloads

One channel; `PROTOCOL_VERSION` bumps when a payload or a `NodeDefs` id changes. The client sends intent,
never a cost, tier or level.

- To the server: `PurchaseNode(id)`, `RefundNode(id)`, `ChooseForkOption(parent, option or none)`,
  `SetTradeoff(id, on)`, `StartReset(backup)`, `CastResetVote(approve)`.
- To the client: `TomeState`, a full snapshot (pool, level, progress, available points, tiers, forks, this
  player's tradeoffs, in-Vault flag, respec seconds left, Ultimine and Mekanism as the server sees them,
  whether a Tier 4 igniter is held), sent at login, on team change and after every accepted mutation; about
  87 entries is under 2 KB, so full state beats deltas. `ActionRefused(reason, args)` maps to a lang key.
  `ResetVoteStatus` and `ResetCountdown` for the vote.

### Per task

T1: package skeleton, ArchUnit rules, ADR 0001. T3: ADR 0002 (lifecycle): `VaultLevels.register` is the
only insert; restore scans `<world>/dimensions/orevault/`, reads `vault_variant` and `disbanded_at`, and
survives a bad folder; the `in_vault` login check; I review the level-unload spike. T4: ADR 0003
(`GenSnapshot`: only what placement reads, including team level, rebuilt on the main thread after purchase,
refund, fork change, level-up, reset and restore) and ADR 0004 (vein extents as values, veins inside their
chunk). T5: pool and `level_reached` on the T6 codec from the first field; orb award goes through
`mining`. T6: ADR 0005, golden key sets, fixtures. T7: ADR 0006 (sync) with a "waiting for sync" client
state. T8: `core.layout.TreeLayout` from the node list, the server's feature flags and a width function
(S27). T9: each node's state checked against the tables above; nothing grows without bound. T10:
`TicketController` on the mod bus. T11: ADR 0007, reset and disband share archive and regenerate.

### My gates

- A persisted shape changes only with a version bump, a migration, a fixture and a golden key-set update.
- No generation code reaches `data`, `team` or `SavedData`; the snapshot is rebuilt on every trigger above.
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
- From UI-UX, the structure file schema and named views; from the Designer, sprites with `.mcmeta` borders
  and a `style.json` of colours and state tints, approved by Nick on the preview, before renderer code.
- From the Writer, the docs PR before T3.

### Per task

T1. NeoForge 26.1 empty mod on the old pins (`neo_version` 26.1.2.95, FTB Teams 26.1.2.4, Ultimine
re-checked against 26.1.2.6), no committed `org.gradle.java.installations.paths`, ArchUnit at test scope. I
confirm whether `CompoundTag` loads in JUnit (S17).

T2. Vault Frame and Crude Igniter through `registerBlock(name, factory, props)`, creative tab, `items/`
definitions, `pack.mcmeta`, the pickaxe tag under `data/minecraft/tags/block/mineable/`.

T3. One PR (S14): lazy create in `findOrCreate` only, through the single insert that calls
`markWorldsDirty()`; eager restore in `ServerStartedEvent`; teleport with the return position saved after
success, the fixed anchor at X=0 Z=0 and an idempotent return portal. The `storageSource` AT returns with
its comment. I spike level unload here, because T11 depends on it.

T4. The generator reads team state only through the snapshot supplier. Layer JSON validated against the
heights duplicated in `Variant`. Placement is a pure function returning ore indices and vein extents (S13,
S20); candidates come from `c:ores`. The placement ATs return with comments. The density command (S12).

T8a and T5. `NodeDefs` as plain Java with a `NodeClass` enum and marks, gates as `ceil(fraction ×
totalTreeCost)`. `VaultOrbEntity` modelled on `ExperienceOrb`, server-authoritative, carrying a source-ore
count so Hoarder's Instinct counts ore, not orb entities. `LevelCurve` and `TeamScaling` pure, team size
never below 1. Breaker rules per S21. Available points derived. Stone Memory T1 bought by debug command
(S9).

T6. `data_version` first on every shape, codec via `CompoundTag.CODEC.xmap`, defaults when absent, fixtures
in `src/test/resources`. Before the vein index ticket I check in the sources jar, then prove with a
gametest, whether a `ProtoChunk` attachment carries over to the `LevelChunk`; if not, a pending map drained
on the main thread at chunk load.

T7. `@Mod(dist = CLIENT)` entry, payloads on one channel, server validation, refusal payload, header after
first sync, vanilla screens (S26).

T8. `TreeLayout` ported as a pure function of node list, UI-UX's structure file and a width function. A
Gradle task runs it with an English width table, which a small tool derives from `ascii.png` into `build/`
(nothing from Mojang is committed), and writes the golden JSON; a test fails on drift. The renderer calls
`TreeLayout` with `font.width` on resource reload and only draws. A client command opens named views.

T9. Each effect a pure function plus a zero-logic adapter, into the single `BlockDropsEvent` pipeline and
the `ResonanceBonuses` accumulator. The `FoodData exhaustionLevel` AT returns only with Long Delve.

T10. `TicketController` on the mod bus; `max_loaded_chunks_per_team` gets its reader here.

T11. Vote state machine pure and tested; deletion depends on the spike and on Nick's disband answer. Before
T11 I confirm in FTB Teams' sources whether joining a party disbands the player's own team.

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
- The golden layout test and the geometry proofs pass before I touch the renderer.

### What I will push back on

A modifier ticket before T4's baseline is mined. A grid, lanes, or the whole tree on one screen. §11's
generator sample. A committed layout read without measuring. Save sentinels. Config before its reader.

### Conflicts I expect

With the Balancer: the curve is provisional until a playtest measures blocks per hour, and T5 marks it so.
With QA: client checks wait on Nick's sessions. With the Reviewer: the Ultimine package looks like a §9
violation until the docs PR lands.

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
Nick's PC. If Nick wants a GitHub Actions check as well (open), it must fail when it cannot run; a skipped
job reporting green is the 2026-08-29 problem again.

T2. Frame mines at stone speed with a pickaxe and drops (#87); gametests for tag, drop and recipe; creative
and JEI visibility are Nick's.

T3. No level is created by a login or a team event (the Reviewer's ArchUnit rule, plus the scripted run); a
Vault created after the first tick ticks (a mock player breaks a block in it); a bounced teleport leaves the
return position unchanged. The scripted restart: create a Vault by debug command, stop, start, then
`execute in orevault:vault_<id>` from the console before anyone connects.

T4. Unit tests for 3% and 6%, 70/25/5, vein sizes, the floor with the full deepslate stack (S13) and
seeding determinism; then the density command on a dedicated server inside tolerance, quoted in the PR.

T5. `LevelCurve` sums to the total with ratio 100; `curve_divisor` scales every threshold equally; level 2
at 0.32 h; `TeamScaling` 1.0, 1.1, 1.2, 1.4 and size 0 or unknown read as 1. Gametests for the orb paying
only its team, the three breakers (S21) and vanilla XP.

T6. A fixture per persisted shape, including one without `deepest_y` that reads as never entered.

T7. `runGameTestServer` after every payload or screen change.

T8. Every fork option covers every parent tier; gates derive from the fractions with derived points pinned;
the layout passes the polar invariants in both Ultimine variants; each test shown failing against broken
input.

T9. Per cluster: pure tests per tier, a gametest where a block or drop is touched, the Balancer's stacking
case (Volatile Veins at 3% against Ultimine Safety, never zero, is the template), Fortune before Quantity.

T10 and T11. Gametests for tickets and the ceiling; vote tests at 1, 2 and 3+ online; scripted reset with
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
parser that turns `latest.log` into the Balancer's table, and a procedure for a two-client team session on
one machine. Bugs become tickets and regression items; design feedback goes to the Playtester and Writer.

### Conflicts I expect

With the Developer and Manager: gametests and the restart script cost time on tickets that feel finished; I
block on them for T3, T4 and T6. With the Balancer: 220 ore an hour and the stacking model are derivations
until measured, and I say so in every PR that depends on them.

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
`ServerStartedEvent`, one bad folder not aborting the rest. `markWorldsDirty()` after every insert. Return
position saved only after success. Registry unfreeze and refreeze paired in a `finally`. No level removed
while the server runs (S19).

T4. Team state reaches the generator only through the snapshot. One 70/25/5 constant. The floor enforced,
every clipped modifier in the snapshot. `Variant` heights match the JSON. ATs carry their task comments.

T5. Orb modelled on `ExperienceOrb`, not a subclass. Team size never 0. Breaker rules per S21. Vanilla XP
untouched. `averageOresPerHour` a constant; `curve_divisor` and `target_play_hours` `worldRestart()`.
`totalTreeCost` over the full registry.

T6. Any save-data risk is a block. `data_version` and `migrate()` on team data and on `PlayerStats`
separately; every new field reads correctly from a tag without it; `deepest_y` absent means never entered
(S16); every rename or removal ships a migration tested against the previous version; reserved ids never
reused. The vein index is a versioned attachment written only on its own chunk.

T7. Mutations validated on the server; client cost and tier never trusted; tradeoff, fork swap and fork
parent refund refused inside the Vault (S35); client code behind the client entry; the Ultimine flag from
the server; 26.1 GUI names; tooltips wrapped with `splitIgnoringLanguage`.

T8. `SkillTree` pure, `NodeClass` an enum, gates derived from fractions, the renderer computes no
placement, the golden layout test present.

T9. One `BlockDropsEvent` listener; numbers from the constants; Resonance through `ResonanceBonuses`; trip
counters in session state; tradeoff state on `PlayerStats`; snapshot refreshed on purchase; stacked risks
checked.

T10 to T14. `TicketController` on the mod bus, ceiling with a reader, Anchor inside the Vault only. Reset:
evacuation before deletion, online votes only, no respec reopening, a gametest or scripted run. Every soft
path gated; Ultimine types only in classes loaded after the check; no `mekanism` imports; `c:` tags.
Attuned ore's component id treated as save data. Debug block and `VaultDiag` removed at release.

### My gates

Every code PR, listed in the verdict as checked or failed:

1. No `SavedData` reachable from generator code. (ArchUnit)
2. A Vault is created only from `findOrCreate`; every world-map insert goes through one method calling
   `markWorldsDirty()`. (ArchUnit)
3. Gameplay listeners on `NeoForge.EVENT_BUS`; registries and config on the mod bus. (Review)
4. No common-code dependency on `client`; state mutated through `ServerLevel` and `ServerPlayer`. (First
   part ArchUnit)
5. `ftbteams` only in the `team` package; no `mekanism`; Ultimine only in its integration package.
   (ArchUnit)
6. No pre-26.1 names; no JetBrains or NeoForge `@Nullable`. (Grep)
7. Every access-transformer line preceded by a comment naming its task. (Lint)
8. A persisted shape change means a version bump plus a migration test. (Golden key-set test)
9. No config value without a reader; start-time reads `worldRestart()`. (Mostly automatable)
10. No number literal where the value lives in a constants class. (Review)
11. A behaviour change comes with its spec change and §13 tick in the same PR. (Review)
12. Nothing outside the ticket; no `neo_version` or FTB bump in a `feat:` PR. (Diff check)

Also automated: a grep for `popExperience` and `data/orevault/tags/block/mineable`, a test comparing
`dimension_type/*.json` with `Variant`, the PNG size test, `NodeLangTest`.

### What I will push back on

Dimension creation on any team event, and "cleaning up" the restore as dead code. Config ahead of its
reader. Ore density, ore per hour or Silent Stone's rate as config. A rejected design returning through
code. Removing §11's `dataVersion` paragraph along with the old-id table.

### Conflicts I expect

With the Developer over ArchUnit scope. With the Balancer: a node that needs a flood fill, or SavedData
during generation, cannot be built as written. With QA: on side safety and save data I find it in the diff
and QA proves it at runtime. With the Manager: PRs stay draft, marked "don't merge yet", until every
reviewer signs off.

## Designer

I own the art: world textures, GUI sprites, the Tome's window and page, node frames, washes, badges and
icons. Layout rules and the layout format belong to UI-UX (commit 953c9e6). The first build's textures broke
the size rule it had then, and the tree renderer was built three times because code came before an agreed
look. So I prove the art on a preview at real size, get Nick's yes, and hand the Developer sprites and
`style.json`.

### What I need

| Input | From | Form | By when |
|---|---|---|---|
| Two or more liked textures per material, with why | Nick, via the Manager | Links or a resource pack | Before T2 art |
| The accepted Tome top bar | Developer, from the old `TomeScreen` | PNG at scales 2 and 3 | Before T7 |
| Node list with classes and marks | Balancer, via T8a | `NodeDefs` export | Before T8 |
| Layout output and named views | UI-UX | Golden JSON and view list | Before T8 preview |
| The vanilla width table | Developer | Generated under `build/` | Before the first preview |
| Approval for `mc-asset` and the preview tools on Nick's PC | Nick, via the Manager | Yes or no | Before T2 |
| Client screenshots at scales 2, 3 and 4 | Nick or a session on his PC | PNG, same named views | Before a visual PR is ready |

### Per task

T2. Vault Frame and Crude Vault Igniter at 16x16, palettes in the ticket, and a contact sheet: 1x and 4x,
the frame tiled 3x3, both in a slot at scales 2, 3 and 4, the frame beside vanilla stone and iron ore at
the 3% band (S46).

T3. The portal: one near-white translucent animated strip tinted per tier (§3.2). Green and orange
converge under deuteranopia, so tints also differ in lightness.

T5. I check the tinted orb reads unlike vanilla XP and sign off the toast.

T7. Window, tab and top-bar art on vanilla screens, reproducing the accepted top bar.

T8. Frames, washes, the badge sheet and `style.json`, drawn into UI-UX's preview set (S30). No tree-drawing
code until Nick says yes.

T9 to T13. Mockups before build for each effect shown in the Tome (the inert fork parent, Outgrown, toggles
disabled in the Vault, Seismic Sense, Stratified's bands); the Vault Anchor; reset screens with UI-UX; the
rest of the inventory and the logo. T14: client against the approved previews at all three scales.

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
| Node frames: small, anchor, notable, keystone, pact, fork parent, fork option | `gui/sprites/tree/frame_<class>.png` | Nine-slice, one texel per unit | Anchor framed twice over (§8); growth uses small while live |
| Node wash per class | `gui/sprites/tree/wash_<class>.png` | Nine-slice, one texel per unit | State is a tint of the wash |
| Badges: lock, toggle on, off, disabled in the Vault, fork, inert, free, gate met, gate unmet, Outgrown, pending, swap, refund | `gui/tree/icons.png` | 9x9 units each, one sheet | Shape, not colour alone |
| Tier pips | same sheet | 4x4 units | |
| Seismic arrow, chunk cell | `gui/tome/icons.png` | 9x9 units, one sheet | T9 |
| Mod logo | `logo.png` (`logoFile`) | 256x256 | §13 calls it `pack.png` |

About 10 world textures and 20 GUI files: 7 frames, 7 washes and icon sheets, with state carried by wash
tint, text colour, pips and badges.

### Preview workflow, art side

UI-UX's preview tool draws the golden layout with my sprites, `style.json` and `en_us.json` at 1920x1080 and
1280x720, GUI scales 2, 3 and 4, in UI-UX's named views, on Nick's PC. A debug command opens the Tome on the
same views for comparison.

### My gates

- Every PNG under `textures/block/` and `textures/item/` is size-checked at 16x16 (animated strips at 16 wide).
- Each texture ticket shows a hex palette, a contact sheet and Nick's yes. Only the portal has partial
  alpha; blocks tile seamlessly; portal tiers survive colour-blind simulation.
- Every class is identifiable with the name hidden. A pact reads colder than a keystone (the old pact
  colour, warm red `0xFFC0563C`, was the reverse of §8). An inert fork parent reads as deliberately inert.
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
and layout data. The playtested geometry stands (S3): hubs on a spine, ring equals in-cluster depth, the
corridor wedge, border-to-border edges, an opaque page, boxes sized to their widest line, zoom 0.2x to
1.5x, the chain highlight. The top bar accepted on 2026-09-03 does not change without Nick.

### What I need

- From the Balancer, via T8a: the final node list with class and per-tier prerequisites, and the derived
  gates (anchors print their gate, so gate values set anchor widths).
- From the Writer, before T8 sign-off: every name and every line each box can draw (the worst-case tier
  line, option names on fork parents, "Not specialised", "Outgrown"), plus descriptions.
- From the Developer, at T7: the ported `TreeLayout`, the width table, the refusal payload, the named-view
  command.
- From Nick: the open questions and a yes on the preview.

### Per task

T5: level-up as a one-line vanilla toast. T7: screen shell on vanilla `TabNavigationBar`, reproducing the
accepted top bar, with the interaction model below; no tree drawing until T8's preview is approved. T8: the
structure file, the preview set and the geometry tests. T9: Tome-visible effects (inert parent, Outgrown,
toggles disabled in the Vault, Seismic Sense, Stratified's bands, the death marker, trip counters); Vein
Sight outlines checked against stone and deepslate. T10: an action-bar message naming the limit when an
Anchor is refused. T11: reset button, initiator confirmation with the backup checkbox, vote screen,
countdown. T12: Broad Cut hidden whole on the server's flag; Ore Working draws pips only for tiers the pack
has. T13 and T14: text fit with the Writer; a UI section, by view and scale, in every playtest script.

### Screen inventory for 1.0

Tome screen. Full-window vanilla screen with the accepted tab bar and header: label, level, unspent points,
progress bar, pool on hover. Esc closes; Ctrl+Tab switches tabs. A small Ore Memory tab ships in 1.0 with the
Survey panel and the reset button (N3).

Resonance tree tab. Opaque pannable page. Drag to pan with 4 px slop; wheel zooms on the pointer; arrow keys
pan, plus and minus zoom, Home centres the first hub, Page Up and Page Down jump hub to hub. A cluster index
down the left edge: eight names, the current one marked, gate met or unmet, click to centre. Zoom readout
bottom right. When the free-respec window is open, a countdown line at the bottom. Pan and zoom persist for
the session. An empty-state line ("Mine ore inside your Vault to earn Resonance.") until the team first
enters a Vault.

Node box. Line 1: name. Line 2: tier pips plus "2 pts · Lv 5" for the next tier; a fork parent shows its
option or "Not specialised"; a growth node "Outgrown". Class by frame and size; state by wash, border and a
badge shape. States: locked, met but unaffordable, buyable, owned, maxed, excluded, inert, outgrown,
tradeoff on, tradeoff off, toggle disabled in the Vault, pending.

Actions. Left-click buys the next tier; pacts, keystones and exclusives confirm first, naming the downside
or the partner they lock. Right-click opens an action list: refund this tier (XP cost, or "free" with the
window's time left), toggle, unpick, swap to a sibling. Inside the Vault, toggle, unpick, swap and fork
parent refund show disabled with "Leave the Vault to change this" (S35). A click shows the node pending
until sync; a refusal flashes it and shows the server's reason.

Before sync. The tree draws neutral under "Waiting for team data" and takes no clicks.

Cross-cluster prerequisites. Nick ruled there are none (Nk), so every prerequisite edge stays inside its
cluster and no tag or gutter route is needed. **Retired 2026-10-26:** the PoE tree has no clusters or
prerequisite edges, only adjacency; see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree".

Reset screens. Initiator: a vanilla confirmation screen over the Tome naming what is deleted and kept
(§3.5), with the backup checkbox. Other online members: a chat message with a clickable vote and a toast,
never a screen forced open; the vote screen shows who voted, the threshold, the backup choice read-only,
and a cancel for the initiator. Countdown: title plus action-bar seconds for players inside.

Trip counters (Deep Habit, Long Delve, Vein Discipline, Kindred Rock, Second Wind) show live in tooltips.

### Tooltip rules

Per S32. Order: name; class and marks; effect with every tier's number and the current tier highlighted;
state; next tier's cost and level; prerequisites with tier and cluster, each met or unmet; the lock reason
naming the node, number or level in the way; last, the available action. Shown beside the node, flipped to
stay on screen. Never a raw key; a non-English name over the cap is cut with an ellipsis on the box and
given in full here.

### Zoom and text

Glyphs come out even only at a whole-number effective scale (zoom times GUI scale). Proposed: the wheel moves
between stops, 0.2 and one or two text-free overview stops, then every k divided by GUI scale from effective
scale 1 up to 1.5. Names draw from effective scale 1, the second line from 2; below that, boxes, frames,
wash and thickened lines carry the picture. Nick judges it on the preview. Minimum stays 0.2.

### Layout data

1. `assets/orevault/tree/resonance_structure.json`, authored by me: a `view` block (zoom stops, text
   thresholds, gaps, corridor half-angle, box minimum and maximum); per cluster, spine order and optional
   ring-gap overrides; per node, `id`, `side`, `order` within its sector, optional `nudge_deg`. No
   coordinates, widths or rings: ring is derived from prerequisites, so the file cannot contradict the depth
   rule.
2. `TreeLayout`, a pure function of node list, structure and a width function, returning hubs, ring radii,
   boxes (`id`, `cluster`, `class`, `marks`, `ring`, `angle`, `x`, `y`, `w`, `h`), edges (`from`, `to`,
   `from_tier`, `kind`, `route`).
3. A Gradle task writes `resonance_layout_en.json` and `_no_ultimine_en.json` as golden files; the preview
   draws them; a test fails when they drift. At runtime the renderer runs `TreeLayout` with `font.width`.

### My gates

- Nick's yes on the preview set at scales 2, 3 and 4 on 1920x1080 and 1280x720, with real names: each hub at
  zoom 1, the page at 0.2x, every class in every state, a hover on Twin Veins, the widest box and longest
  tooltip, the no-Ultimine tree, a mid-game state (142 points spent, two forks chosen, two growth nodes
  Outgrown, a tradeoff on), and the Tome opened inside the Vault with toggles disabled.
- Geometry tests, shown failing once: disjoint annuli, no angular overlap on a ring, empty corridor and
  gutters, every segment a ray, ring-gap arc, corridor or gutter run ending on borders; ring equals
  in-cluster depth; the centre ray of every node on ring 2 or beyond passes through one of its in-cluster
  prerequisites on the ring inside and through no inner box it does not require; fork options adjacent
  outside their parent; every node placed once; both variants; all again with random widths.
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

From the Balancer, before T5 and before each T9 cluster: every tier's number, its constant, and whether it
is derived or measured. From UI-UX: the limits in S32 and the top-bar label slot as reproduced. From the
Developer, in each PR body, the ids registered or renamed and any behaviour that differs from the cited §.
From QA: counts from the XML and playtest results in Nick's words. From the Manager: every decision Nick
makes, dated and in his wording, the same day.

### Per task

Docs PR: the corrections list above. T1: the `neoforge.mods.toml` description and an `en_us.json` skeleton;
check `CLAUDE.md` Build against the rebuilt Gradle setup. T2: lang for Vault Frame, Crude Vault Igniter, the
creative tab and the igniter tooltip. T3: §3.1, §3.2 and §11 checked against the code. T4: tolerances in
§3.1, marked unplaytested. T5: the level-up toast; §4.3 marks 7,200 blocks an hour as derived until
measured. T6: §11 "SavedData Pattern" matching what is persisted. T7: tab names, top-bar labels,
waiting-for-sync text, lock reasons, refusal, pending, swap and "Leave the Vault to change this" strings,
each naming the node, number or level in the way. T8: final ids, display names, reserved ids, lang keys
(S36, S37). T9: one description per node from the effect table and constants; a node whose table hedges
goes back to the Balancer before I write it; §13 ticked per node as each merges. T10 to T12: Anchor, reset
and integration text; no non-Ultimine string names an Ultimine node. T13: the complete `en_us.json`. T14:
playtest notes, log entries, release notes, `KNOWN_ISSUES.md`, §12 and §13 in step with what shipped.

### My gates

1. Every registered block, item, entity and node has a name key, and every node a description key.
2. No description contains a banned word (S38).
3. Every number in a description equals the constant, per tier.
4. No description copies rationale; the node's text is the description and the notes are not.
5. Every tradeoff and fork-option description says it can only be changed outside the Vault; Molten
   Seam's says switching off leaves existing pools.
6. A behaviour change has its spec change in the same PR, and every `§N` cited in code exists and says
   what the code claims.
7. A PR body opens with the spec reference, says why before what, quotes QA's counts, names what was not
   verified, and carries `Closes #N`.
8. "Ready to merge" appears only when the Manager has said so.
9. Any decision Nick or a playtest changed has a dated log entry naming what was rejected.

### What I will push back on

Descriptions written from rationale notes rather than the table ("The mirror image."). A vague-but-honest
description for an unspecified node. A test that every description has a digit. Code following §11's
generator sample. §13 ticked from memory. Reopening the hub layout.

### Conflicts I expect

With the Balancer: a formula is fine in text if the code runs it and the cap is stated; a word in place of
a number is not. With UI-UX: when a description outgrows the tooltip, the number stays and the sentence
shrinks. With the Developer: behaviour changes without the spec edit; I block rather than file a follow-up.

## Balancer

I own every number, the level curve and node interactions. Hours come from the §4.3 curve: 30 levels, ratio
100, 220 ore an hour times 3.485 Resonance per ore (68/25/7, rare at 12.5) = about 767 an hour, about 76,700 total. Purchase
timings come from a model over every §6.1 tier row, gates at S6, 12 points per level.

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

T5. `LevelCurve` and `TeamScaling` tests: per-level costs, 12 points per level, multipliers 1.0, 1.1, 1.2,
1.4, unknown size as 1, milestones (level 1 at 0.15 h, level 2 at 0.32 h, level 10 at 3.35 h, level 18 at
14.1 h, level 30 at 100 h). One rare constant (S40), which I set in the docs PR. The pacing session per S11,
with the tool named in the script.

T6. `deepest_y` must not read as 0, or Shallow Grace starts Outgrown and Highwater Mark counts free steps;
absence covers it (S16).

T8a. I pin 354 and the gates, and test that each gate is reachable from the clusters above it. (Retired
2026-10-26 with the anchors; see `DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree".)

T9. A stacking review per cluster before its tickets, and pure tests for every per-tier value.

T10: Vault Presence's 4/12/28 under the ceiling of 32, Far Reach included. T11: repeated resets must not
reopen the respec window (worth 1,062 XP levels of refunds at 354 points). T12: Volatile Veins under
Ultimine; Mekanism tiers hidden without it. T14: the measurements, then a re-derived curve.

### Tree cost and gates

**Retired 2026-10-26.** These figures describe the tiered cluster tree, replaced by the PoE tree; see
`DESIGN_LOG.md` 2026-10-26, "The Resonance tree becomes a Path of Exile tree" and spec §6.1 Totals.

Prospecting 42, Excavation 41, Assay 46, Metallurgy 76 (32 of them Mekanism-only Ore Working tiers), Claim
59, Deep Lore 35, Broad Cut 11, Mastery 44: 354. The two exclusive pairs cost one side each, so at most 347
can be held with both mods, 315 without Mekanism and 304 without either. Twelve per level gives 360 by
level 30.

| Cluster | Fraction | Old points | Gate (S6) | First level spendable | Hours |
|---|---|---|---|---|---|
| Excavation | 4% | 10 | 15 | 4 | 0.76 |
| Assay, Broad Cut | 11% | 25 | 39 | 7 | 1.75 |
| Metallurgy | 20% | 45 | 71 | 9 | 2.73 |
| Claim | 25% | 55 | 89 | 9 | 2.73 |
| Deep Lore | 31% | 70 | 110 | 10 | 3.35 |
| Mastery | 45% | 100 | 160 | 14 | 7.1 |

Points spendable at levels 1 to 9 are 9, 11, 13, 20, 22, 34, 62, 69, 108: until level 9, gates and level
requirements bind, not points. The Mastery anchor never binds, since every keystone needs level 18 to 26, so
each keystone's second line must show its level and the tooltip name it as the lock. Without either soft
dependency everything is bought by level 26 (52.6 h), leaving 47 of the 100 hours with nothing to buy.
Deep Harvest needs Vault Expansion in Mastery, so its real gate is 160 points plus level 20.

### Derived, not measured

- 220 ore an hour is 7,200 blocks an hour times 3%. Density measurement confirms the 3%, not the rate; only
  a timed session measures blocks per hour.
- Calloused Hands' cap arrives at (e^(0.4/0.07) − 1) × 10,000, about 3.0 million blocks at tier 3.
- Silent Stone's 3 a second is 14.1 times solo (0.213 a second); at that rate levels 25 to 30, 62 hours on the
  curve, take 4.1.
- Stone Memory T5's 0.5% burst pays about 1,745 Resonance an hour, about 2.3 times the curve's rate.
- Every value pinned on 2026-09-09 is unplaytested.

### Stacking that I think breaks something

These are my findings for the T9 reviews; Nick answered items 1, 2, 5 and Silent Stone (N7, N6, N5, Ng), the rest come back
to the Manager as proposals per cluster.

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
8. Volatile Veins: the spec does not say whether a vanished vein counts as completed.
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

Every T2 to T9 ticket on this path is checked against this story, which starts once the player has the
iron.

The player spawns holding the Tome. They open it and see the Resonance tab: level 0, no points, an empty
bar, Prospecting lit, everything else dark, and the empty-state line telling them to mine in a Vault. They
craft Vault Frames and a Crude Igniter from JEI, build a frame and right-click it. If the frame is wrong,
§3.2 plays a failure sound and nothing else, which says that something failed and not what. They step in,
the screen wobbles for four seconds, and they arrive at X=0, Z=0 on grass under full daylight with the
return portal behind them. No mobs. It should feel theirs, and safe.

They dig through four blocks of dirt into stone and should see a first vein within a minute. Ore drops its
vanilla XP and a tinted orb that flies to them; the orb is the first new thing the mod shows, so it has to
look different from vanilla XP. Around minute 9 a toast announces level 1 and 12 points. They open the Tome,
hover a Prospecting node, read what it does with its numbers, buy it, and see the effect on the next block.

What would bore them: level 1 at 25 minutes instead of 9. What would confuse them: a portal that fails
silently; Resonance with no readout outside the Tome; a first node whose effect cannot be seen; 12 points
against 9 buyable nodes, so 3 sit banked with every remaining node saying "needs level 2".

### Per task

T2: did Nick have enough iron, and how long did it take? T3: the return portal is visible on arrival and
returning lands where you left. T4: after ten minutes at zero points, does the Vault feel rich enough to come
back to and lean enough that the tree has something to sell? T5: the pacing session; the orb and toast are
on my checklist. T6: log out inside the Vault, log back in, open the Tome; nothing changed. T7: the
empty-state line, the Tome reaching solo players, every disabled control saying why on the control itself.
T8: can Nick find the next thing to buy in a few seconds, and at 0.2x find a named cluster and zoom back in
one gesture? The answer to "is the loop playable" comes here, from the first node bought through the Tome.
T9: every node ticket answers "how does the player know this worked?" T11: two players, one votes; the vote,
countdown and respec window must reach players who did not press the button. T12: on a pack without
Ultimine, does anything mention it? T14: the full playtest against the milestone table.

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
