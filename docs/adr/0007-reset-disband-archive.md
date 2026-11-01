# 0007. Reset, disband and the archive

Status: proposed, 2026-10-27 (E0-S10, #258). Round 1 and round 2 reviews are recorded below and their
changes applied, round 2 by the Manager's ruling of 2026-10-31 (the Vault's type is kept in its folder, ADR
0005 section 3a). Accepted once the level-unload spike (E3-S11, #245) is written into ADR 0002's pending section and the
branch it picks is confirmed below, and the matrix reviewers have signed. Spec: §3.1 Disband, §3.5, §9 FTB
Teams, §11 FTB Teams Event Hooks; S19, N8, Nh, Ni (`docs/TEAM_REQUIREMENTS.md`); Nick's 2026-10-27 answers
Q2, Q14 and Q16 (`DESIGN_LOG.md`).

FTB Teams facts are from `ftb-teams-26.1.2.4-sources.jar`, and Minecraft facts from the decompiled,
NeoForge-patched sources of `neoforge-26.1.2.95`, both read on 2026-10-27 (E1-S5, #195). Paths are inside
those jars.

## Context

Reset and disband destroy or move worlds, and neither can be undone. §3.5 gives the vote thresholds and the
steps; the old tracker held the constraints that make them safe: the vote machine's rules (old #94), the
countdown and a respec window that survives a restart (old #95), a backup taken only from closed storage
(old #96), and the removal steps (old #93). The first build never wrote any of it: `FtbEvents#onTeamDeleted`
at 1e7cdad logged "Vault dimension deletion not implemented yet" and deleted the team's data file, which N8
replaced with an archive.

What the sources settle:

- Disband is a party losing its last member. FTB Teams deletes a team only in `PartyTeam#leave` when
  `getMembers().isEmpty()` (`manager.deleteTeam(this)`), and `TeamDeletedEvent` is posted only when
  `onPlayerChangeTeam`'s `deleted` argument is true (`AbstractTeam#onPlayerChangeTeam`). `forceDisband`
  kicks every other member and then calls `leave(owner)`. When the event fires, every former member is back
  in their personal team.
- A party join never disbands a solo team. `PartyTeam#join` and `TeamManagerImpl#createParty` point the
  personal team at the party with `setEffectiveTeam` and pass `deleted` false. The personal team's id is the
  player's UUID and it persists; leaving the party sets it back.
- A Vault's chunk tickets, NeoForge's forced tickets included, are saved inside the Vault's folder, at
  `<world>/dimensions/orevault/vault_<hex>/data/minecraft/chunk_tickets.dat`
  (`net/minecraft/server/level/ServerChunkCache.java` lines 94 to 103;
  `net/minecraft/world/level/TicketStorage.java` lines 37 to 43).
- Team `SavedData` lives in the server's storage at `<world>/data/<namespace>/<path>.dat`:
  `MinecraftServer#getDataStorage` (`net/minecraft/server/MinecraftServer.java` lines 2225 to 2227), with
  files at `id.withSuffix(".dat").resolveAgainst(dataFolder)`
  (`net/minecraft/world/level/storage/SavedDataStorage.java` lines 65 to 72). `SavedDataStorage#get` caches
  what it reads, an absent file included, and has no way to drop a cached entry (lines 43 and 85 to 94).
  Its public API is `computeIfAbsent`, `get`, `set`, `readTagFromDisk`, `scheduleSave`, `saveAndJoin` and
  `close`. ADR 0005 names the id and settles on this storage.
- A saved file's root is `{data: {...}, DataVersion}` (`SavedDataStorage#encodeUnchecked` puts the codec's
  output under `data`), so a raw read of the file reads the `data` compound.
- There is no per-file save. "Save the file at once" is `server.getDataStorage().saveAndJoin()`, which
  writes every dirty server-wide `SavedData` and blocks until `Util.ioPool()` has written them.

## Decision

### S19, restated

Nothing is deleted or moved while the server runs, except a live reset of a Vault if ADR 0002's unload
spike passes. Disband sets `disbanded_at` and evacuates; the folder and the team's data file move to the
archive at the next start, before restore, and restore skips a disbanded team. A reset that cannot unload
is finished at the next start the same way.

### The start-of-server pass

One pass in `vault`, called first by the root listener (the `ServerStartedEvent` listener in the root
package, ADR 0001's root row; RV2-2), before the classifier and the restore (ADR 0002 "Restore"). First it deletes any `vault_<32 hex>.deleting` folder left under `<world>/dimensions/orevault/`
by a delete that was cut short (reset step 8). Then, for each `vault_<hex>` folder and for each team data
file it finds:

1. Read the team data file as raw NBT, with `SavedDataStorage#readTagFromDisk` on the path
   `data.DataPaths` gives (ADR 0005 section 2), and take its `data` compound (ADR 0005's team reader on a
   bare `CompoundTag`). Never through `SavedDataStorage#get`, which would cache a `SavedData` for a file
   this pass then moves, and a later save would write it back at the old path. A file that fails to read is
   logged at ERROR naming its path and left alone, and the pass does nothing for that team; restore
   restores the Vault under its type file (ADR 0002 restore step 4).
2. `disbanded_at` set: move to the archive (below). A failed move is logged at ERROR and left in place;
   restore still skips it.
3. `pending_reset` set: finish the reset with steps 7 to 9 of the sequence below (backup if asked, delete,
   then in one write the generation, the window and the cleared marker), and add the team id
   to the list the pass hands to restore. Step 9's write goes through ADR 0005's ordinary team-data
   accessor, since this file is not moved. The pass does not run step 10: building a generator and a
   snapshot before the classifier has run would give them nothing to read (ADR 0009). Restore registers
   every team on the list whether or not a folder exists (ADR 0002 restore step 1), so a player who logged
   out inside is placed at the team anchor (X=0, Z=0) by the login check, not sent home.
4. Otherwise nothing; restore handles it.

This pass is the shared archive and regenerate code. The live reset calls the same `VaultFolders` helper
for backup, delete and move, and the same `VaultDimensions#ensureTeamDimension` for the new Vault (ADR
0002), so disband, a live reset and a deferred reset differ only in when they run.

### The vote (old #94; §3.5)

A pure, headless state machine, `core.reset.ResetVote` (ADR 0001's `core.reset`), with no Minecraft types:
member ids, booleans and tick counts in, a state out.

- One open session per team. A second start while one is open, while a reset is running or pending, or for
  a team with `disbanded_at`, is refused with a lang key (ADR 0006's refusal list).
- The session opens with the initiator's yes and the set of members online at that moment. Members who
  connect later are not added: the toast and chat line went to the members online at the start.
- A member who disconnects leaves the denominator and their vote is dropped.
- It passes at once when: one member is online, with the initiator's yes; two are online and both said
  yes; three or more are online and more than half said yes. A no from enough members that the threshold
  can no longer be reached fails it at once.
- A timeout fails it: 120 seconds (2,400 ticks), a constant, not config (the Balancer's number, BAL-8;
  unplaytested, Nick's E11 session confirms). §3.5 states the same.
- The backup flag rides on the session, set by the initiator, and is forced off when
  `reset.allow_backup_on_reset` (§10) is false.

Who may start one is checked on the server: the sender holds a Tier 4 Vault Key (§3.5), outside or inside
the Vault. Other online members get a toast and a clickable chat line, never a screen (Q16). The line runs
`/orevault reset vote` (ADR 0006). The `/orevault` root and its `reset vote` and `archive` subcommands are
registered whatever `[debug].enable_debug_commands` says; only the debug subcommands sit behind it (§13).

### The reset sequence

When the vote passes, on the server thread:

1. Lock: the team's portal entry is refused with a message until the sequence ends, and the session state
   becomes `COUNTDOWN`.
2. Countdown of 10 seconds (§3.5) shown to anyone in the Vault: action bar, and a sound on each of the last
   five (old #95). The initiator can cancel it; a cancel ends the session and changes nothing. §3.5 step 2
   states the cancel.
3. Write `pending_reset {requested_at, backup}` to the team's data (ADR 0005) and save at once with
   `server.getDataStorage().saveAndJoin()`. From here a crash or a stop is finished by the start-of-server
   pass, so a reset is never half done.
4. Evacuate: every player in the Vault is sent to their `return_pos` in the Overworld (the respawn point
   when it is absent), without overwriting it. They are held there until step 11. `in_vault` is cleared.
5. Leave the team's snapshot holder, effects view and Vault Anchor tickets as they are. Generation tasks
   queued while players were inside may still be on the worker threads and will call the snapshot supplier
   (ADR 0003); an emptied holder would make them throw there (RV-4). ADR 0002's `unload` releases the
   tickets (its step 2) and clears the holders (its step 6) once the level has closed. If the level is not
   unloaded (Result B, or the Ni fallback), they stay until the server stops.
6. Unload, ADR 0002 `unload`, if the spike passed; the state becomes `UNLOADING` and waits for its per-tick
   drain. If the spike failed, or this unload throws or passes its 200-tick bound: stop here. Everyone is
   already out; send a message to the team's online members that the Vault regenerates at the next restart
   (Ni); keep entry refused; the start-of-server pass does steps 7 to 9 and restore does step 10.
7. Backup, if the session's flag is set and the folder exists (a folder that is absent was deleted before a
   crash, and step 8 is then already done; RV-5). Copy the closed folder, its type file with it (ADR 0005
   section 3a), to `<world>/orevault_backups/<teamUuid>/<timestamp>/` (§3.5). Storage is closed by step 6 or by the stop,
   so a half-flushed region file is never copied (old #96). In the live sequence the copy runs on
   `Util.ioPool()` while the state is `BACKING_UP` and the players stay held, since a large Vault is
   gigabytes; the main thread goes on to step 8 once the copy reports success. The start-of-server pass
   copies inline and logs the time taken. Log the path and the bytes written.
   If the copy fails, abort: delete nothing and clear `pending_reset`. Live, re-register the old Vault
   unchanged, open entry, tell the initiator, and log at ERROR. In the start-of-server pass nobody is
   online to tell: the ERROR line names the team and the backup path, and the folder, still in place,
   is restored as the old Vault (QA-22). A partial copy is left where it is, named in the log.
8. Delete `<world>/dimensions/orevault/vault_<hex>/`, which takes the old chunk tickets with it: rename it
   to `vault_<hex>.deleting` in the same directory, then delete that. A folder that is already absent is
   done (RV-5). The rename means a delete cut short never leaves a half-deleted folder under the Vault's
   own name for step 7 to back up or restore to load; the start-of-server pass removes the leftover.
   A failed rename is a failed delete, handled as a failed backup in step 7: nothing is deleted,
   `pending_reset` is cleared and an ERROR names the team and the folder. Live, the old Vault is
   re-registered unchanged, entry opens and the initiator is told; in the start-of-server pass the folder,
   still under its own name, restores as the old Vault. On Windows, Nick's platform, a folder with a handle
   still open cannot be renamed. Without this rule step 9 would raise `vault_generation` over an old folder
   that still exists, and the next start would restore the old terrain under a new seed. A rename that
   succeeds followed by a delete that fails is done: the leftover `.deleting` folder is removed at the next
   start.
9. In the team's data, in one write: `vault_generation` + 1; the respec window (below); `pending_reset`
   cleared. Save at once with `saveAndJoin()`. No variant is written: the Vault's type is in its folder,
   and step 8 deleted the old one with it.
10. Re-register: `ensureTeamDimension` (ADR 0002). There is no folder, so its step 2 computes the variant
    from ownership, expanded if the team owns Vault Expansion and base otherwise (§3.5 step 5), and writes
    the new folder's type file at once, before the level is registered. It rebuilds the snapshot with the
    new `vault_generation`, so the seed changes and the veins are new (Q2, §3.1). No Anchor ticket
    survives, since the ticket file went with the folder, and ADR 0002's ticket reactivation (creation step
    13) finds no file. If a backup failure or a failed rename re-registers the old Vault after a live unload, that Vault's
    Anchor tickets were released by the unload; E10-S3 (#373) decides how a placed Anchor re-adds its
    ticket, and this case is one of its checks.
11. Players held in step 4 who are still online are sent to the team anchor (X=0, Z=0), where the portal
    arrives (Nh), and the return portal is built there. Not to their saved spot, which may be inside the
    new rock.
12. Open entry, mark the team's state for sync (ADR 0006, through `data.SyncOutbox`), and log the team id,
    the time, the new generation, the variant and the backup path and bytes (§3.5 step 8).

### Pending E3-S11 (#245): live reset or restart reset

The spike and its checks are in ADR 0002 ("Pending E3-S11"). What each result decides here:

Result A, a live Vault unloads safely. Steps 6 to 12 run as soon as each finishes, and without a backup the
team is back in a fresh Vault a few seconds after the countdown. The Ni path stays as the handling for an
unload that throws or passes its bound.

Result B, it does not. Step 6 always stops: every reset sends everyone out, closes the Vault with the
message, the start-of-server pass runs steps 7 to 9 at the next start, and restore runs step 10 from the
pass's list; step 11 does not happen, since nobody is held across a restart. QA's scripted reset test
becomes "reset, restart, check the folder and the team file".

The result and its date are copied here from ADR 0002 when that section is filled.

The return portal's tier is held by its placed blocks (PR #109), so after a reset it starts at the tier of
the next Vault Key that enters and climbs again. Colour only.

Players offline inside the Vault at a reset are not touched. Their file still names the Vault's dimension key; on login
ADR 0002's check sees a `vault_generation` above the one in their `in_vault` and places them at the team
anchor (X=0, Z=0).

### The respec window (§3.5 step 7, §4.4; Q14; old #95)

At step 9, if no window opened in the previous 24 hours of server time (`free_respec_opened_at` absent,
or `now − free_respec_opened_at ≥ 1,728,000` ticks), set `free_respec_until = now + 12,000` and
`free_respec_opened_at = now`; otherwise change neither. Both are saved in the team file, so the window
survives a restart. Times are game time, ADR 0005's one clock, which only advances while the server runs,
which is what "server time" means here. This replaces the older rule that repeated resets never reopen the
window (DESIGN_LOG 2026-10-27, "What this supersedes"). The rule is a pure function in `core.reset`.

### What a reset invalidates without editing anyone's file

Entry points, the Homeward Seam anchor and the death marker carry the `vault_generation` they were set in
(ADR 0005). Any with a generation below the team's current one is treated as absent when read, and is
dropped the next time that player's attachment is written. Offline players' files are never opened by the
reset. The same holds for `in_vault` (ADR 0002).

### Disband (§3.1, §9, §11; N8)

On `FTBTeamsEvent.TeamDeleted` for a team (only a party, per the source above):

1. Set `disbanded_at = now` in the team's data and save with `saveAndJoin()`. If the team never wrote data
   and has no Vault folder, there is nothing to archive and nothing is written.
2. Evacuate any online player in that Vault to `return_pos` (respawn point when absent), and refuse entry to
   it from then on. No current member exists to enter it: every former member is in their personal team.
3. Log the team id and that the archive happens at the next start.

The level stays loaded, and empty, until the server stops; the server still ticks it. It is never unloaded
at run time, whatever the spike shows, and its snapshot holder stays with it (step 5 above).

At the next start, the start-of-server pass moves the Vault folder (its type file inside it) and the team's
data file, with any
`team_<hex>.dat.unreadable-<gametime>` copies ADR 0005 set aside for it, into
`<world>/orevault_archive/<teamUuid>/<disbandedAt>/`, where `<disbandedAt>` is the game time written in step
1. The log line gives the wall-clock time beside it. Reset backups are kept apart, in
`<world>/orevault_backups/<teamUuid>/<timestamp>/` (§3.5). A move is a rename on the same disk; if it fails
the pass copies, checks the byte count and then deletes the source, and on any error leaves the source and
logs.

Former members keep nothing from the party: their `PlayerStats` stay in the archived file (Q3).

### Party join

Settled by the FTB Teams read: joining or creating a party never deletes the personal team, so no disband
fires and nothing is archived. The solo Vault, keyed by the player's UUID, stays on disk and is restored at
each start (ADR 0002); the player's portal trips go to the party's Vault while they are in it, and to their
solo Vault again after they leave, since the personal team's id never changes. The conditional exemption
recorded on 2026-10-27 has nothing to apply to, and §9 says so.

### The admin commands

`/orevault archive list` prints each archived team, its disbanded time and its size.
`/orevault archive purge <teamUuid> [<disbandedAt>]` deletes that archive. Both require
`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` and are registered whatever the debug flag says (above);
the purge asks to be run a second time within 30 seconds with `confirm` appended, and logs what it deleted
and the bytes. They touch only `orevault_archive/`, never a live level or `orevault_backups/`, which an
admin manages by hand (§3.5 calls backups a safety net the mod does not use).

### Tests

- `ResetVote` unit cases at 1, 2, 3, 4 and 5 online, with abstentions, a disconnect at each point, and
  also: a late joiner is not counted; an early fail once the yeses can no longer reach the threshold; a
  second start refused; a disbanded team refused; the timeout at exactly tick 2,400 and not at 2,399.
- The respec window function: at 1,727,999 ticks since the last window it opens none, at 1,728,000 it opens
  one; an absent `free_respec_opened_at` opens one.
- Fault points (S44, debug-only, behind `[debug].enable_debug_commands`): the process halts with
  `Runtime.halt` after step 3, 6, 7, 8 or 9. Each run is followed by a start and a check of the folder,
  `vault_generation`, the window and the log. Expected: after 3 and after 6, the pass backs up if asked,
  deletes, writes step 9 and restore registers the new Vault; after 7, the same, with a second complete
  backup beside the first; after 8, step 7 is skipped and the pass writes step 9 and restore registers;
  after 9, the folder is gone and the generation is already raised, so nothing is on the pass's list and
  the Vault is created again on the next portal trip (a player who was offline inside goes to their return
  position, since login case 1 finds no level). Wherever a new Vault is registered, its folder holds a type
  file with the variant the team's ownership gives. The backup abort is induced by making `orevault_backups`
  a regular file: the folder stays, `pending_reset` is cleared, the ERROR line names the team and the path,
  and the old Vault restores with its old generation. The failed rename is induced by an S44 fault point
  that makes step 8's rename report failure, live and in the pass: the same expected result as the backup
  abort, and no `.deleting` folder.
- Archive: a disbanded party's folder and team file are under `orevault_archive/<uuid>/<time>/` after a
  restart, and restore skipped them; a leftover `vault_<hex>.deleting` is gone after a start.
- Two dev-client steps for T11, deferred to E11-S8 (#301): B logs out inside, A resets, B logs in and lands
  at the team anchor (X=0, Z=0) of the new Vault; a party whose last member leaves while inside is
  evacuated, and after a restart its folder and team file are in the archive.

## Consequences

- A reset cut short at any point ends in one of three states: the old Vault untouched (a cut before step
  3, or a failed backup or rename); a `pending_reset` that the next start finishes (a cut after step 3 and
  before step 9's write); or, after step 9's write, a complete reset whose Vault is made on the next portal
  trip. In the third, a player offline inside goes to `return_pos` (ADR 0002 login case 1, since there is
  no level to place them in), not to the team anchor that §3.5 step 3 names. A trip home is harmless, and it
  is accepted (RV2-4). A finished live reset always has its new folder, because step 10 writes the type file
  at once. The Ni fallback is the same code run later. The fault points check each state.
- An unreadable team file hides `pending_reset` and `disbanded_at` from the pass, which logs ERROR and does
  nothing for that team: the Vault restores as it is on disk, and for a disbanded party, which has no
  members, ADR 0002 login case 2 sends anyone inside out. A reset cut between steps 8 and 9 whose team file
  is then also unreadable makes the next Vault from generation 0's seed; that needs two faults at once and
  is logged. Both are accepted (ADR 0002 Consequences).
- If the spike fails, every reset needs a restart to finish, and the team cannot enter its Vault until
  then. That is Nick's answer Ni, and the message says so.
- Disk grows with archives and backups until an admin purges them. Accepted (N8: disk is cheaper than a
  lost world).
- A disbanded party's level costs memory, and a tick, until the next stop.
- A live backup costs time on an I/O thread, not on the server thread; the players wait at their return
  positions while it runs.
- `saveAndJoin()` writes every dirty server-wide `SavedData`, not only the team's, and blocks until done.
  It runs twice a reset and once a disband.
- `pending_reset` and the raw-NBT read are constraints on ADR 0005, which carries both: the field, with
  its default (absent), and a team reader that works on a bare `CompoundTag`.
- `core.reset` is a pure package, listed in ADR 0001's table.

## Rejected alternatives

- Deleting a disbanded team's Vault (the old spec, and `FtbEvents` at 1e7cdad): N8.
- Unloading and archiving a disbanded Vault at run time: nothing is gained by doing it before the next
  start, and S19 forbids it unless proven.
- Sending players inside at a reset to world spawn (old §3.5) or to their saved return position as the end
  point (Nick's answer Nh): the return position is only where they wait; Nh puts them at the team anchor.
- Editing offline players' attachments at a reset: the generation tag makes it unnecessary, and those
  files are not loaded.
- A backup taken from the live folder: old #96, a copy of a half-flushed region file is worse than none.
- Copying the backup on the server thread: a gigabyte Vault stalls every player on the server (DEV-16).
- Adding late joiners to an open vote's denominator: they received no toast, and a member could block a
  reset by logging in and voting no.
- A 60-second vote timeout (the first draft): a member mid-vein needs time to reach chat (BAL-8).
- Clearing the snapshot holder before the unload: queued generation tasks would read an empty holder on a
  worker thread (RV-4).
- Running step 10 in the start-of-server pass: it runs before the classifier (RV-3).
- Archiving a solo Vault on a party join: the solo team is never deleted, so the event never comes.

## Spec sections changed

§3.1 Disband, §3.5 (the vote rules, the 120-second timeout, the initiator's cancel, the pending marker, the
evacuation hold, the backup abort, the archive apart from backups, the team anchor (X=0, Z=0)), §9 FTB Teams
(the archive path; a party join never disbands), §10 and §13 (the `/orevault` root always registered, only
debug subcommands gated), §11 FTB Teams Event Hooks (disband is a party losing its last member; the data
file is archived, not deleted) and §11 SavedData Pattern (the `pending_reset` field, for ADR 0005). Applied
in the same PR (#160).

Round 2 (2026-10-31), applied by the Writer in the same PR: §3.5 step 3 gains, in a parenthesis, the one
exception of Consequences (a reset complete before the stop sends a player offline inside to their return
position), and §3.5 and §11 no longer have a reset write the variant to team data; the new Vault's folder
gets its type file when it is made (ADR 0002, ADR 0005 section 3a).

## Reviews

Matrix row "System design before code": developer (buildable), reviewer (rules), qa (testable); the
ticket adds writer and balancer. Round 1, 2026-10-27, settled by the Manager's triage. Round 2 confirms each
change.

- developer: approve with comments. Checked that `SavedDataStorage` has no way to drop a cached entry, so
  the raw read is required; the ticket storage path; that the vote rules are pure and testable; the admin
  commands' 26.1 permission names. DEV-16 (backup on the server thread): fixed, step 7 copies on
  `Util.ioPool()` live and inline at start with the time logged. DEV-17 ("unticked"): fixed, "empty".
  DEV-13, raised on ADR 0005: fixed, the pass reads the `data` compound and every "save at once" names
  `saveAndJoin()`. DEV-15, raised on ADR 0006: fixed, the commands are registered regardless of the flag.
- reviewer: request changes. Checked S19; the vote rules of old #94; the sequence against §3.5 steps 1 to
  8; backup only from closed storage and abort on failure; the Q14 window on game time; generation tags
  instead of editing offline files; disband keyed on `TeamDeleted`; the archive path and the purge's
  permission and confirm; the raw-NBT read. RV-3 (who registers after a deferred reset): fixed, the pass
  runs steps 7 to 9 and hands restore its list (ADR 0002 restore step 1 takes it). RV-4 (holder cleared
  before the level is gone): fixed, step 5 clears nothing. RV-5 (resume after a crash between 8 and 9):
  fixed, step 7 skips an absent folder, step 8 counts it done, and the fault points test each crash point.
- qa: request changes. Checked the pass, the vote rules, the twelve steps, the spike branches, the respec
  window, disband, party join and the admin commands. QA-21: fixed, fault points under Tests. QA-22:
  fixed, step 7 states the ERROR line and the old Vault restoring. QA-23: fixed, unit cases and the
  window function under Tests. QA-24: fixed, under Tests, the two dev-client steps deferred to E11-S8
  (#301).
- writer: request changes. Checked the vote against §3.5, the steps against §3.5 steps 1 to 8 and Nh, Ni,
  the window against Q14 and §4.4, the archive path against §3.1, §9 and §11. WRITER-1: fixed, "Applied in
  the same PR". WRITER-2: fixed, "the team anchor (X=0, Z=0)" in steps 11 and the offline paragraph.
  WRITER-11: fixed, the commands are registered regardless of the flag. WRITER-12: fixed, the timeout is
  120 seconds here and the cancel is stated, with §3.5 carrying both. WRITER-13: fixed, "Nick's answer Nh".
- balancer: approve with comments. Checked the thresholds against §3.5, 24 h as 1,728,000 ticks, 10
  minutes as 12,000, a window at most once per 1,728,000 ticks, the respec-abuse path. BAL-8 (timeout 120
  seconds, a constant): fixed, the vote section.

Round 2, 2026-10-31, settled by the Manager's round-2 triage.

- developer: approve. Checked the `.deleting` rename then delete as plain `Files` work and
  `readTagFromDisk` as public. DEV2-4 (raised on ADR 0005): pass step 1 now reads the path from
  `data.DataPaths`.
- reviewer: approve with comments. Checked the `.deleting` rename against the pass's cleanup, rule 7
  against `reset` calling `ensureTeamDimension`, and ticket reactivation after step 8 as a no-op. RV2-2:
  fixed, the pass intro names the root listener. RV2-4 (the after-9 fault point sends an offline-inside
  player home): fixed with QA's comment, Consequences names the three outcomes and accepts the trip home.
  The other case the Reviewer named, a finished live reset with no folder written before the stop, no longer
  exists under the section 1 ruling: step 10 writes the folder's type file at once.
- qa: approve with comments. Checked the fault points, the backup abort, the vote and window cases. The
  Consequences comment (the third outcome missing): merged with RV2-4 and fixed.
- writer: approve. Checked "the team anchor (X=0, Z=0)" at steps 11 and the offline paragraph, and the
  command gating.
- balancer: approve. Checked the thresholds, the timeout and the window constants; nothing new.
- architect: approve with comments. Addition 2 (a), no failure path for the rename: fixed, step 8 and a
  fault point under Tests. Addition 2 (b) is in ADR 0002's `VaultKeys` cases. Addition 3 (Anchor tickets
  after a backup-abort re-registration) is carried by the #373 comment (Manager, WP-R2-6); step 10 already
  names that case.
- Section 1 ruling (RV2-1, ARCH-R2-1), applied here: pass step 1 no longer has restore skip a Vault whose
  team file is unreadable, step 9 writes no variant, step 10 writes the new folder's type file, backup and
  archive carry it with the folder, and Consequences states the accepted residuals.
