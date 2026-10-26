# Manager notes: unresolved items and follow-ups

Things outside Nick's decision list that still need someone. Written 2026-10-26 after merging the ten drafts
and nine critiques into `docs/TEAM_REQUIREMENTS.md`.

## Facts found while merging

- The restart is not on `main`. Commits 86360a3 to a3cfd5e (restart, spec and log, three new roles,
  builds on Nick's PC) exist only on `claude/project-thread-4rxx6n`. `main` still holds the pre-restart
  code at 1e7cdad. Every "the spec now says" in this round refers to the branch. It needs a PR and Nick's
  merge before T1 tickets cite anything.
- The session context injected an older `CLAUDE.md` (no team section, no matrix, old build commands). The
  file on disk has the review matrix, rules 1 to 9 and the 2026-10-26 Build rule. The drafts that said
  "there is no matrix" probably read the injected copy. Worth checking why the harness injects a stale
  copy, or the next session will repeat the mistake.
- §13 has 77 ticked lines, not 71 (Writer) or about 60 (my round 1).
- §3.1's seeding paragraph cites §7 for reset; reset is §3.5. Nobody had flagged it; added to the docs list.

## Numbers I verified and numbers I did not

Verified by my own arithmetic or by parsing §6.1: the tree total of 354 and every per-cluster total (42, 41,
46, 76, 59, 35, 11, 44); the gates 15/39/71/89/110/160 under ceiling rounding; 12 points per level; 720.5
Resonance an hour (220 × 3.275 with rare at 12.5); level 1 at about 107 Resonance (8.9 minutes) and level 2
at 0.32 h; Silent Stone at 15 times solo; Calloused Hands' cap at about 3.0 million blocks at tier 3 (no
coefficient gives 10.8 million); 1,062 refund levels at 354 points; the portal's 10 or 14 frames.

Not verified, taken from the role that computed them, and marked as theirs in the merged doc:

- The Balancer's purchase-timing model: points spendable at levels 1 to 9, the "first level spendable"
  column, everything bought by level 26 without soft dependencies, and the stacking hours (23.7, 13.2, 8.6,
  7.6 h solo; 16.9 to 5.4 h for five). Question 7 to Nick rests on these. The Balancer should commit the
  model as a script under `tools/` or a test so others can rerun it.
- 347 / 315 / 304 holdable points, and "9 buyable nodes at level 1".
- UI-UX's 23 nodes and about 27 cross-cluster edges, against the Designer's 16. The layout depends on
  which; UI-UX recounts from the T8a data, not the prose.
- The 132% full-deepslate stack, the 59,000 indexed positions per chunk, Stone Memory T5's 1,745 an hour
  (roughly checked), Hoarder's Instinct as the largest multiplier, Salvager's Eye at about 350 raw ore an
  hour.
- The mid-game preview state (142 points, two forks, two Outgrown) comes from the Balancer's model.

## Settled by me, worth a second look

- S16, `level_reached` monotonic. I settled it from §4.3 step 4's reasoning. The Architect asked the
  Balancer and no answer was recorded; the Balancer should confirm before T6's ADR.
- S42, porting the old pure classes. No document forbids it and the lessons doc praises those classes, but
  Nick restarted from scratch and may have meant fresh code. Mention it when the T4 ticket opens.
- S28, nine-slice borders in `.mcmeta`. Conditional on the Developer confirming the 26.1 sprite API.
- S17, `CompoundTag` in JUnit. Conditional on T1. If it holds, `CLAUDE.md` Tests and the lessons doc need
  "registries, block states and `Level`" in place of "NBT".

## Questions owned by a role, not Nick

- Architect to Balancer: does Vein Discipline's streak survive logout, and does logout count as a trip
  ending or as abandoning? Needed before T6's `PlayerStats` shape.
- Balancer and Architect: does a vein that Volatile Veins vanishes count as completed? The vein index needs
  the answer before the Excavation and Deep Lore clusters.
- Balancer: the rare Resonance constant (12 or 12.5), for the docs PR.
- Balancer: proposals for stacking items 3, 4, 6 and 7 (Brittle Stone against Bedrock Communion; Vault's
  Blessing and Purity against Vault Fever and Constitution; Echo favouring small veins; Hoarder's
  Instinct's size), each flagged to Nick as a balance change if it moves a number.
- Balancer: name the tool and enchantments for pacing sessions; §4.3 assumes "an enchanted pickaxe".
- Developer: does joining an FTB party disband the player's own team? Feeds question 8.
- Developer: does a `ProtoChunk` attachment reach the `LevelChunk` in NeoForge 26.1? Sources first, then a
  gametest, before the vein index ticket.
- Developer, T3: the level-unload spike. Its result decides smaller question i.
- Writer: which nodes the §11 Drop Pipeline is missing, re-derived from §6.1 (I confirmed Resonant Overload,
  Last Ore and Salvager's Eye change drops; did not check every node).
- Writer: the remaining vague node rows (Guide Vein's and Salvager's Eye's ore choice) I did not recheck.
  I dropped the Writer's §4.3 item about Ancient Traces at 20 hours: it needs level 21, which the curve
  reaches near 20 hours, so it is not a contradiction.

## Process and tooling to sort out

- Builds on Nick's PC: nobody has yet tried a Remote Control session that runs `runClient` and captures a
  screenshot. If it can, QA and the Designer get client screenshots at three GUI scales without waiting for
  Nick to play. QA should try it during T1.
- Whether Node, Playwright and `mc-asset` exist on Nick's PC is unknown. The preview pipeline assumes them
  (smaller question c).
- `CLAUDE.md` edits that wait on Nick's answers: Textures (question 2), rule 5 Mekanism (question j), Tests
  (S17). They go through me, not inside feature PRs.
- `.claude/agents/designer.md` line 48 still says "Vertical position means depth into the craft and nothing
  else. Never place one node above another unless...", the grid-era rule; UI-UX flagged it. Since 953c9e6
  the layout is UI-UX's, so that bullet should move to `ui-ux.md` in its radial form or go.
- About 60 pre-restart issues are open. The Writer proposes closing them `NOT_PLANNED`, naming the restart.
  Reversible but visible in Nick's repo; raise with him when the T1 tickets are created.
- Debug commands are removed at T14. The Reviewer asked whether playtest builds after release need them;
  nobody answered. Decide before T14.
- Compatibility and performance roles are deferred (Nick, 2026-10-26). Two items they would own are already
  visible: chunk-generation cost per chunk at the full deepslate stack, and the vein index's size per chunk.
  The Architect's T4 measurement of index size covers the second for now.
