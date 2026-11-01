# Player stories

Written by the Playtester. Each story says what the player does, sees and should feel, and what would
confuse or bore them. Every player-visible ticket from T2 to T8 is checked against this file, and a T9 node
ticket answers its "how does the player know it worked?" question here first.

This replaces "The first ten minutes" in the Playtester's section of `docs/TEAM_REQUIREMENTS.md`, which was
written for the tiered tree (12 points a level, clusters, "Prospecting lit"). The Manager marks that section
superseded in E0-S2.

Nothing below has been played. Every time and count is derived from the spec, mostly from the unmeasured
220 ore an hour (§4.3), and stays a prediction until Nick's sessions measure it. Where a judgement rests on
a reading of the spec rather than on play, it says so.

Sources: spec §3.1, §3.2, §3.3, §4.2, §4.3, §4.4, §6 (Notation, Wheel, Ring), §6.1, §8, §11, §12;
`DESIGN_LOG.md` 2026-10-27 and both 2026-10-26 entries on the tree and the baseline;
`docs/TEAM_REQUIREMENTS.md` "Nick's answers" (Nb) and "Nick's answers, 2026-10-27" (Q1 to Q17).

---

## The first ten minutes

The story of a solo player on a fresh world, buying freely. A second member changes little in these
minutes; where it does, it is noted.

### First login: the Tome

The player logs in for the first time and finds the Tome of the Deep Seam in their inventory. It is given
once, on the vanilla login event, and recorded as `tome_granted` so it is never given again (§11 FTB Teams
Event Hooks; §8 Item). It can also be crafted from cobblestone and a book, so a lost Tome is not a lost mod.

They right-click it. The Resonance tab opens on an opaque page (§8 Tab 1). The header shows level 0, no
unspent points and an empty progress bar; there is no Animus row (§8 Team; Nd). In the middle is the start
node, already owned and with no effect (§6 Notation, Start). Around it sit at least four growth nodes,
placed there "so level 1 has obvious picks" (§6 Wheel), plus the other ring-0 nodes. None can be bought
yet, because the team has no points (§4.4: "the team has an unspent point"). A faint level ring marks where
level 1 opens (§8 Tab 1, Layout). While the team's pool is 0 and its level is 0, an empty-state line says
"Mine ore inside your Vault to earn Resonance." (UI/UX's screen inventory in `docs/TEAM_REQUIREMENTS.md`; the
Writer adds the sentence defining it to §8). It goes away with the first Resonance the team earns, which
needs no new data on the wire.

Hovering a node shows its name, its effect as a "Next:" line because the team owns none of it, its ring and
whether the team has reached it, and why it cannot be bought now (§8 Tooltip). A generation node adds
"Only in chunks generated after you buy it." This is the first place the player reads what the mod offers,
so every word has to make sense without the spec.

What the player should take away: there is a tree, it costs points, and points come from mining in a
Vault. The Tome says nothing about how to build the portal. Whether anything should is the Q17 guide
question, below.

### Iron, frame and key

The player needs 10 Vault Frames for the smallest portal: a 2 by 3 interior with corners left empty, as a
nether portal allows (§3.2 VaultPortalShape Scanner; Nb). One frame is 8 iron ingots round 1 redstone dust,
one frame per craft (§3.2 Vault Frame Block). The Crude Vault Key is one iron ingot and one redstone
dust (§3.3). That is 81 iron and 11 redstone before the mod does anything. The recipes are in JEI; the
frame's shape is not.

They build the frame and right-click it with the key. A valid frame fills with the green portal of the
common tier (§3.2 Ore Vault Portal Block). An invalid one plays a failure sound and nothing else: nothing
says which side is wrong, the same as a nether portal (§3.2; Nick, Q6). The frame needs a pickaxe to drop,
so a player who punches out a misplaced frame by hand loses 8 iron (§3.2: "Requires correct tool to drop").

### Arrival

They step in. The screen wobbles for four seconds, and they can still step back out (§3.2, 80 ticks). They
arrive at X=0, Z=0, standing on grass at Y=250 under full daylight, with the return portal, a 4 by 5 frame,
standing on the same spot (§3.1 Entry point; §3.2 Teleportation Logic). Open air above, no mobs, no torches
needed (§3.1 shared attributes). Every later trip lands on the same block (§3.2). It should feel theirs,
safe and quiet.

### The first minute of mining

They dig down through four blocks of dirt (Y=246 to 249) into stone, which runs from Y=245 to the bedrock
floor at Y=0 (§3.1 Base stack). Ore sits only in the stone. At the 3% baseline and about 7,200 blocks an hour
(§3.1, §4.3 step 4), a player breaks about 120 blocks a minute, of which about 3.6 are ore. At about 7.45
blocks a vein that is about one vein broken into every two minutes, while a 1 by 2 tunnel exposes about two
veins a minute in its walls (the Balancer's figures, BAL-12). So a vein shows in the tunnel wall within about
a minute; the first one broken into takes about two. The session logs the time of the first ore broken. This
rests on the same ore density at every height, which Nick chose on 2026-10-27 (same density everywhere, ore
type still by height). Gravel and clay are in the rock too
(§3.1, Gravel and clay), so the odd blob of gravel falls on a player digging under it.

There are no caves, aquifers or lava (§3.1), so the only things that can hurt them are a fall into their
own shaft and hunger.

### The orb and the bar

The first ore drops its vanilla XP exactly as in the Overworld (§3.4 Vanilla XP is untouched) and, beside it,
a Resonance orb tinted blue or cyan that flies to them from up to 8 blocks away, the same base radius as a
vanilla XP orb (§4.2; §11 Resonance Orb Entity; §13 Resonance System). The orb is the first new thing the mod
shows, so it must read as different from the green vanilla orb at a glance. Common ore pays 2, uncommon 5,
rare 12.5 (§4.2).

While they are inside the Vault, a small on-screen bar shows the team's level and progress to the next
level, so the Resonance they earn is visible without opening the Tome (§8 On-screen Resonance bar; Nick,
Q11). It is on by default and each player can turn it off with `hud.show_resonance_bar` in the client
config (§10). The Tome header shows the same numbers from the same sync. Where the bar sits and how it shows
the share that came from bonuses is decided on a rendered preview with Nick's yes (§8).

### Level 1, at 7 to 11 minutes

Level 1 costs about 113 Resonance, about 32 ore at the 68/25/7 mix and 3.48 Resonance an ore (S11 in
`docs/TEAM_REQUIREMENTS.md`; the Balancer's figures from ADR 0009's closed form, 218.9 ore an hour). At that
rate it arrives around minute 9; §4.3's pacing check sets the band at 7 to 11 minutes, and level 2 at 15 to
23. About 32 ore is only four or five veins, so one player's level-1 time can easily land outside the band
by luck (QA's estimate: about ±50%). A session records the time as an observation; pacing is judged on ore an
hour from the per-minute readout (S44), not on a stopwatch. A toast tells every online member (§4.3 Skill
point award; §8 Team). The pool is not reset (§4.3). The team now has 5 unspent points (§4.3 step 2,
`POINTS_PER_LEVEL = 5`; §4.4: unspent is `5 × level − owned visible nodes`).

Nothing opens by itself: the level-up is a toast, and the player opens the Tome when they choose. This is
the same rule Nick set for the reset vote (Q16).

### The first five points: the ring-0 picks

Every node costs 1 point (§4.4). A node can be bought when it touches the start or an owned node, the
team's level is at least its ring, and a point is free (§4.4). At level 1 that means nodes of ring 0 or 1.
The layout puts every node at a distance of at least `max(1, ring)` (§6 Notation, Ring), so both ring-0 and
ring-1 nodes may sit next to the start; which ones do is the layout file's decision (UI/UX, T8).

The ring-0 nodes in §6.1:

| Node | Class | Effect at ring 0 (§6.1) | Seen near the arrival point? |
|---|---|---|---|
| Vein Expansion, tier 1 | small | +15% vein size | No. Generation node |
| Stone Memory, tier 1 | small | Stone drops 1 XP when mined | Yes, on the next stone block |
| Miner's Constitution, tier 1 | small | −20% hunger drain inside the Vault | Barely. Nothing shows in ten minutes |
| Novice's Luck | growth | `(30 − teamLevel)%` bonus ore drops: +29% at level 1 | Yes, as extra drops on about one ore in three or four |
| Apprentice's Ledger | growth | The first 100 ore of each trip pay double Resonance | Yes, on the on-screen bar |
| Shallow Grace | growth | +50% Resonance from all ore while every member inside has an all-time deepest Y above 100 | Yes on the bar, but only if nobody inside has been below Y=100 |
| Guide Vein | growth | 12 extra copper or iron veins of about 51 blocks per newly generated chunk | No. Generation node |

Also buyable at level 1 if the layout puts them within reach, both ring 1: Gravel Purge (no gravel or clay
in newly generated chunks; generation node, not seen near arrival) and Salvager's Eye (5% of stone broken
drops a raw common ore while the team is below level 12; seen at once, about one stone block in twenty).
Travel nodes have no ring in §6.1's table; whether any sits at distance 1 is also the layout's call.

Seven ring-0 nodes and up to two ring-1 nodes against 5 points is a real first choice, and no point is left
banked with nothing to take it. The tiered tree's problem, 12 points against 9 buyable nodes, is gone.

Each pick should show the player it worked before the Tome is closed for long. Read from the spec, without
play:

- Stone Memory and Salvager's Eye show on the next few blocks.
- Novice's Luck shows within a vein or two, though a player cannot tell its extra drop from Fortune's.
- Apprentice's Ledger and Shallow Grace show only as the bar filling faster. Without the on-screen bar
  (Q11) both would be invisible until the Tome was opened.
- Miner's Constitution shows nothing a player can see in these minutes.
- Vein Expansion, Guide Vein and Gravel Purge show nothing near the arrival point (next section).

Two traps a new player can walk into:

- Shallow Grace can be outgrown before it is bought. Y=100 is 150 blocks below the arrival point, and
  players dig straight down (`DESIGN_LOG.md` 2026-10-26, baseline entry). A player who went below Y=100
  in their first nine minutes buys a node that will never pay them. §8 labels a growth node Outgrown
  "once its condition can no longer be met"; the tooltip has to say so before purchase too, not only once
  it is owned.
- Apprentice's Ledger counts "the first 100 ore blocks of each Vault trip" (§6.1). By level 1 the player
  has mined about 32 ore on this trip. The spec does not say whether ore mined before the node was bought
  uses up the 100. Open question, below.

### Generation nodes and the chunks round the arrival point

A generation node changes only chunks generated after it is bought. A chunk already generated is never
changed by a later purchase; only a dimension reset regenerates it (§3.1: "A generated chunk is never
changed by a later purchase"; §4.4; §11 Dynamic Chunk Generator; `DESIGN_LOG.md` 2026-10-26, "The baseline
measured against vanilla, and Guide Vein made visible": "Generated chunks are never changed").

By level 1 the view distance has already generated every chunk round the arrival point and round the
player's tunnel. So Vein Expansion, Guide Vein and Gravel Purge bought at level 1 show nothing where the
player is digging. They show only once the player tunnels past the edge of everything already loaded.
Without help this reads as a broken node, which is why Nick took Q13:

- each generation node's tooltip says "Only in chunks generated after you buy it." (§8 Tooltip);
- the Ore Memory tab's Survey panel says whether the chunk the player stands in was generated with the
  team's current generation nodes (§8 Tab 3; §11 Vein Index, the chunk stamp).

The player story for these nodes is therefore: buy it, read the tooltip line, keep mining outward, and see
the Survey panel change from "not current" to "current" when a new chunk is reached. If Nick buys Guide Vein
at level 1 and reports it as broken, that is a screen defect for UI/UX (the line was not seen or not
understood), not a reason to reopen the 2026-10-26 rule.

### Refunds while XP levels are few

A refund costs 3 Minecraft XP levels for any of these nodes, travel included (§4.4; a pact or keystone costs
more, and none sits in ring 0). Refunds happen outside the Vault only. A node bought in the last 60 seconds
refunds free, but the same outside-only rule applies, so a player who misclicks while deep in a tunnel cannot
reach the portal at X=0, Z=0 in time, and the misclick costs 3 levels. Buying is not limited to outside the
Vault (§4.4 lists no such condition), so the first purchase will often happen underground.

At minute 9 a player's XP comes from vanilla ore XP only. Raw iron, copper and gold drop no XP when mined in
vanilla; coal, redstone, lapis, diamond and emerald do. From about 32 ore that is a few levels at most
(an estimate, not measured). A player with fewer than 3 levels cannot refund at all, and the Tome must say
how many levels the refund needs and how many they have (§4.4: "When a node cannot be refunded, the Tome
always says why").

If they bought Stone Memory, this changes within a minute: at about 115 stone a minute and 1 XP a block, 3
levels take seconds. The first ten minutes session asks whether a refund was wanted and whether the price
felt fair.

### What would bore them, and what would confuse them

Bored: no vein in the first minute; level 1 at 25 minutes instead of 9; a first purchase whose effect
cannot be seen; a Vault that feels finished before the tree has done anything (`DESIGN_LOG.md`
2026-09-27).

Confused: a frame that does not light, with only a sound to go on; an orb that looks like vanilla XP;
Resonance with no readout outside the Tome (answered by the on-screen bar, Q11); a node shown as locked when
the only thing missing is a point; a generation node that "does nothing"; Shallow Grace bought after it was
already outgrown; a refund refused with no reason.

### Nick's 2026-10-27 answers in these minutes

- Q6. A wrong frame plays the failure sound and nothing else (§3.2). The session watches whether Nick
  needed more than that.
- Q11. Both bars ship: the Tome header bar and the on-screen bar inside the Vault, on by default and
  switchable off per player (§4.1, §8, §10). In these minutes the on-screen bar is what makes Apprentice's
  Ledger and Shallow Grace visible at all.
- Q16. The reset vote reaches other members as a toast and a clickable chat line, and no screen opens by
  itself (§3.5, §8 Vault Reset Button). A reset needs a Tier 4 key (§3.3), so it never happens in the
  first ten minutes, but the same rule holds for everything that does: the level-up toast and the "bought
  by" notices never take the screen from a player mid-swing.
- Q17. 1.0 ships no advancements and no Vault Recall item. Whether it ships a guide is decided after the
  first-ten-minutes playtest, from what the player needed explained, such as how to build the portal; if
  one is needed, a guide opened by hovering an item in JEI and holding a key is preferred over a book
  (§12; `DESIGN_LOG.md` 2026-10-27). Nick designed the mod and cannot play as a new player, so the session
  asks him what a new player would have needed at each step, not only whether he got stuck.

### The first-ten-minutes session

Run once the ring-0 node effects exist (the first T9 work), on a fresh world, buying freely. It answers Q17.
A session at T8 can check buying through the Tome, but the effects are not there yet, and Nick has to be
told that before he plays.

```
Session: first ten minutes, <date>
Build: <commit>, mods: vanilla plus FTB Teams and JEI, team size: 1
Kind: first ten minutes (buy freely)
Focus: 1. At which step would a new player have needed to be told something, and what?
       2. Which nodes did you buy at level 1, why, and how did you know each worked?
Setup: fresh world, iron and redstone for 10 frames and a key in a chest, stone pickaxe or better
Steps (QA's, QA-34):
        1. Log in; the Tome is in the inventory.
        2. Open it: level 0, the empty-state line.
        3. Hover a ring-0 node; the tooltip says why it cannot be bought.
        4. Craft 10 frames and a key.
        5. Build the frame and light it; the green portal fills it.
        6. Enter; arrive at X=0, Z=0 on grass with the return portal there.
        7. Dig to stone; note the time of the first ore broken.
        8. See an orb fly in and be absorbed; the on-screen bar moves.
        9. Mine to the level-1 toast; note the time.
       10. Open the Tome: 5 points.
       11. Buy; note the order and how each node showed it worked.
       12. Open the Survey panel and read the chunk line.
       13. Leave the Vault.
       14. Refund one node; note XP levels before and after.
       15. Attach latest.log.
Watch for: the confusion and boredom list above
Record: time of the first vein seen in a wall, time of the first ore broken (from the log), time of
        level 1 (an observation, not a pass or fail), ore an hour from the readout, the nodes bought in
        order, XP levels at the refund
After the session, answer in your own words:
  1. <focus 1>
  2. <focus 2>
  3. What did you expect to happen that didn't?
  4. Was there a moment you didn't know what to do next?
Attach: latest.log, screenshots of anything wrong
```

---

## Player stories, T2 to T8

The task names are the shared task list in `docs/TEAM_REQUIREMENTS.md`. Each world-facing task is seen by
Nick in a client before the next opens (§12). Each story says what Nick will not see yet, so a missing
later feature is not reported as a bug.

### T2: Vault Frame and Crude Vault Key

The player finds both items in the creative tab and in JEI, with recipes: 8 iron round 1 redstone for one
frame, iron and redstone in a row for the key (§3.2, §3.3). At slot size the frame reads as a metal
frame block and the key as a key, not as blobs (`CLAUDE.md` Textures; Designer's `mc-asset` drafts,
Q5). Placed, the frame mines at stone speed with a pickaxe and sounds like metal, and drops nothing without
one (§3.2).

Not yet there: right-clicking a frame with the key does nothing until T3.

Confusing: a purple cube or an invisible item; an item missing from JEI; a frame that drops nothing when
broken by hand, with no hint why (vanilla's iron blocks behave the same way, so this is watched, not
changed).

How the player knows it worked: the items appear, craft as JEI shows, and the frame drops itself when mined
with a pickaxe.

Session focus: How long did the iron for 10 frames and a key take, and did 81 iron feel like a fair
price of entry? Can you tell both items apart from vanilla ones at slot size?

### T3: The dimension path

The player builds a frame, unlocks it with the key, and sees the green portal fill it (§3.2). A wrong
frame plays the failure sound only (Q6). They step in, wait four seconds through the wobble, and arrive on
grass at X=0, Z=0 with the return portal on the same spot (§3.1, §3.2). Stepping back through the return
portal puts them just outside the Overworld portal they used, walked out far enough that it does not fire
again (§3.2: "walked out along the approach direction"). Logging out inside the Vault, stopping and
starting the dedicated server, and logging back in leaves them inside it, on the same block, not falling
through the Overworld sky (§3.1 Restoration is eager; `CLAUDE.md` rule 2). A plain relog without the restart
does not test this. A teleport that does not complete (QA's bounced-teleport step) leaves the return
position where it was, because it is saved only after a teleport succeeds (§3.2; `CLAUDE.md` rule 2). In a
Vault created while the server is running, the first trip of a new team, blocks break normally instead of
stalling (`CLAUDE.md` rule 2, `markWorldsDirty()`).

Not yet there: ore. The baseline comes at T4, and Nick should not judge the Vault's richness here.

Should feel: theirs, safe, lit, and the same place every time.

Confusing: arriving somewhere other than X=0, Z=0; a return portal that is not visible on arrival; coming
back to a different Overworld spot; the portal firing again on return; any relog that moves the player.

How the player knows it worked: the portal lights, the trip lands at the same anchor each time, and the
return lands where they left.

Session focus: Did you build a valid frame without being told the shape, and if not, what would you have
needed (feeds Q17)? After logging out inside the Vault and restarting the server, were you exactly where you
left?

### T4: Generator, classifier and baseline

The player arrives, digs through four blocks of dirt into stone, and mines for ten minutes with nothing
bought (§3.1 baseline; T4 done-when). They should see a first vein in the tunnel wall within about a
minute, break into one within about two, and mine about 36 ore in ten minutes at the derived 218.9 an hour
(§4.3; ADR 0009). Commons are most of it. Ore is as dense at every height, and which ore a vein is follows
its vanilla height, so diamond, gold and redstone turn up only in the 16 layers above bedrock, and above
them rare ore is emerald and uncommon ore almost all lapis (§3.1 Where each ore sits). A player after
diamonds strip-mines that floor, but it holds no more ore than any other height. Nick chose the density
on 2026-10-27 and the ore type by height on 2026-10-31 (question 4).
Gravel and clay appear as blobs in the stone (§3.1).

Not yet there: Resonance and orbs (T5). Vanilla XP drops as in the Overworld (§3.4).

Should feel: rich enough to come back to, lean enough that the tree has something to sell (`DESIGN_LOG.md`
2026-09-27, rejecting 8% and 15% because the unspent Vault already felt finished).

Boring: long stretches of bare stone, or ore so common it is no longer an event.

Session focus: After ten minutes at zero points, does the Vault feel rich enough to come back to and lean
enough that the tree has something to sell? How many ore did you count, which kinds at which heights, and
how long before the first vein?

### T5: The Resonance loop

The player breaks ore. Vanilla XP still drops, and a blue or cyan Resonance orb flies to them and is absorbed
(§3.4, §4.2). Level 1 arrives at 7 to 11 minutes and level 2 at 15 to 23 (§4.3 Pacing check). Until the Tome
shell lands at T7, the level-up is an overlay message to every online member rather than a toast (E5-S4, #274;
the toast comes with E7-S6, #291, §13 Resonance System). Stone Memory tier 1 is bought by command and its XP
seen on stone (T5 done-when).

Not yet there: the Tome and the on-screen bar (T7). The readout is the per-minute debug readout (S44).

A pacing session never buys anything, because the growth nodes change the rate being measured
(`docs/TEAM_REQUIREMENTS.md` Playtester, Session template).

Confusing: an orb that looks like vanilla XP; an orb that drifts to the wrong player in a two-session test
(§11 Resonance Orb Entity; Q10); vanilla XP missing.

How the player knows it worked: the orb flies in, the level-up message arrives, and ore an hour from the
readout is near 218.9.

Session focus: What ore an hour did the per-minute readout (S44) show over the session, and when did level
1 and level 2 arrive (recorded as observations; one session's level times vary too much to pass or fail the
bands)? Could you tell the Resonance orb from vanilla XP at a glance?

### T6: Persistence

The player has earned some Resonance and a level. They log out, inside the Vault and outside it, restart
the server, and log back in. Pool, level, points and return position are as they left them (§6 Saved
state; §3.2). Nick's playtest world from T5 carries over to every later build up to 1.0 (Q4), so a build
that loses progress is a bug in his own save.

Not yet there: the Tome (T7). Check through the debug dump (S44).

How the player knows it worked: nothing changed.

Session focus: After a logout inside the Vault and a server restart, was anything different?

### T7: Network, the Tome shell and the on-screen bar

On first login the player has the Tome (§11 FTB Teams Event Hooks). Opened, it shows the header (level,
unspent points, progress bar, the pool on hover) once the team data has arrived, and "Waiting for team data"
before that, never a row of zeros (§13 UI). No Animus row (Nd). The empty-state line shows while the team's
pool is 0 and its level is 0, so in Nick's carried-over world (Q4), where the team already has Resonance, it
does not show; a fresh world shows it. The Ore Memory tab holds the Survey panel: depth gauge with band edges,
a death marker, and the line saying whether this chunk was generated with the team's current generation nodes
(§8 Tab 3; Q13). A chunk has three readings: current, generated with older nodes, and "Generated before this
was tracked" for a chunk with no stamp (ADR 0006 `chunkStamp`; §11 Vein Index). What Nick sees at T7 in his
carried-over world: every chunk generated before the stamp existed reads "Generated before this was tracked".
Only a chunk generated after the stamp is written reads as current, since no generation node is owned yet. If
the stamp has not landed by T7 (§13 lists the line under E7-S7, #295, and E9-S3, #307), every chunk reads
"Generated before this was tracked". Neither is a bug. The reset button is absent without a Tier 4 key
(§3.5).

Inside the Vault the on-screen bar shows level and progress while mining, and turning
`hud.show_resonance_bar` off hides it at once (§10; Q11). The level-up is now a toast to every online
member (§4.3, §8 Team).

Not yet there: the tree's nodes (T8).

Confusing: zeros before sync; a header that disagrees with the on-screen bar; a bar that cannot be turned
off; a Tome that gives no hint of what to do at level 0.

Session focus: Could you follow your progress while mining without opening the Tome? At level 0, did the
Tome tell you what to do next?

### T8: Tree layout, renderer and the first purchase

The player opens the Resonance tab at level 1 with 5 points. The start sits at the centre, the ring-0
nodes around it as icons with no text, faint rings marking where each level opens (§8 Tab 1). Hovering a
node shows its tooltip with a "Next:" line from the constants (Q12) and, for a generation node, "Only in
chunks generated after you buy it." Clicking buys it; every online member is told who bought what (§4.4).
Shift-click buys a highlighted path after one confirmation naming the points (§8). The travel totals panel
lists every travel stat the tree shows, with how many of its nodes the team owns, so a stat with none owned
shows as 0 of N. The page zooms between a fifth and one and a half times, anchored on
the pointer (§8; `DESIGN_LOG.md` 2026-09-07). Leaving the Vault, the player refunds a node for 3 XP levels,
or free within 60 seconds of buying it (§4.4).

The tree's shape is approved by Nick on a rendered preview before renderer code (§6.1; T8 done-when).

Not yet there: node effects (T9). A bought node changes nothing in the world yet, and Nick is told so.

Confusing: a node that reads as locked when only a point is missing (the level-0 state, below); a lock
with no named reason; a raw translation key; a refund refused without the XP it needs; a growth node
outgrown before purchase with no sign of it.

How the player knows it worked: the node is drawn owned, the header's points drop by one, the "bought by"
notice arrives.

Session focus: Could you find the next thing to buy in a few seconds? Which node would you buy first, and
why?

---

## Session steps for later tasks

Stories for T9 and later are written with their tickets. These steps are recorded now because a review
round asked for them.

### T11: a player whose team changes while inside a Vault

Two dev client sessions on Nick's PC with different dev usernames (Q10), both in one party, both inside the
party's Vault. The owner kicks the other, or the other leaves the party. The player who left is sent at once
to their saved return position in the Overworld, with a chat line saying why (Manager triage RV-8; §3, a
Vault belongs to one team). Their personal records stay with the old team (Q3). Ore they break afterwards
pays only their new team, in their new team's Vault. Ticket: E11-S8 (#301).

The offline case, as a second run: the other player logs out while inside the party's Vault; with them
offline, the owner removes them from the party (FTB Teams posts `PlayerChangedTeamEvent` for an offline
player, `docs/research/neoforge-26.1-sources.md` "FTB Teams party join and disband"); then they log back in.
They arrive outside the Vault, at their saved return position in the Overworld, with the same chat line
saying why, and not at Vault coordinates in the Overworld sky (ADR 0002 login case 2). A player whose
team changes while online cannot still be inside to log out, since they are moved at once, so this is the
only way to log in to a Vault of a team one has left.

Confusing: being moved with no message; staying inside and mining for a team they have left; logging in
after being removed offline and finding no explanation.

Session focus: When you were moved out, did the message tell you why, and did it feel fair?

---

## Open questions

For the Manager, from writing these stories:

1. The state of a node that is in reach and at its ring when the team has no unspent point. §8 lists owned,
   buyable and three locked states; this one is none of them, and it is every node round the start at
   level 0. UI/UX's screen inventory records it as open for T8. It must not read as locked. UI/UX and the
   Writer. Ruled 2026-10-27 (Manager triage M18): a fourth unlocked state, "in reach", drawn as buyable
   without the buyable highlight, its tooltip naming the level that brings the next point; the Writer adds
   it to §8 States, and Nick says yes or no on the E8-S5 (#283) preview.
2. Shallow Grace bought after it is already outgrown. Should the tooltip of an unowned growth node say
   Outgrown before purchase? Recommendation: yes; §8 can be read either way. UI/UX. Ruled 2026-10-27
   (Manager triage M20): yes. The server sends an `outgrown` bit for every growth node, owned or not
   (ADR 0006), and §8 Tooltip shows "Outgrown: <why>" on an unowned node, with one purchase confirmation
   that names it.
3. Apprentice's Ledger and ore mined before the purchase. Does the trip's count include ore mined before
   the team owned the node? Recommendation: count only ore mined while owned, so buying it at minute 9
   gives the full 100. Balancer, then the Writer for §6.1 and §11. Ruled 2026-10-27 (Manager triage
   PT-S17-Q3; Balancer and Writer agree): only ore mined while the team owns the node counts, written into
   §6.1 and §11 Vault Session State.
4. Which ring-1 and travel nodes sit at distance 1 from the start. The layout file decides it at T8;
   Salvager's Eye and a mining speed node there would give level 1 two more picks that show at once.
   UI/UX. Ruled 2026-10-27 (Manager triage PT-S17-Q4): deferred, as UI-UX answered, to the layout story
   E8a-S3a (#239) and Nick's E8-S5 (#283) preview, with no ticket amendment needed.
5. Miner's Constitution tier 1 shows nothing in the first ten minutes. Not a change request yet; the
   first-ten-minutes session asks whether Nick noticed it.
6. Stone Memory and the refund prices. At about 115 stone a minute, tier 1 pays roughly 115 XP a minute and
   tier 5 roughly 575. The refund prices in §4.4 assume XP levels are scarce; at tier 5 the 30 levels of a
   keystone refund (1,395 XP from level 0) take under three minutes of stone. Derived, not measured.
   Balancer. Ruled 2026-10-27 (Manager triage PT-S17-Q6, the Balancer's answer): no change now. The
   interaction is recorded as a "Still open" line in `DESIGN_LOG.md`, and E9-S11 (#315) and E9-S64 (#367)
   carry amendments naming a rebuy cooldown as the fix if Nick's play shows refunds are toggled freely.
