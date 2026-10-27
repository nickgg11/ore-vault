# 0007. Reset, disband and the archive

Status: proposed, 2026-10-27 (E0-S10, #258). Accepted once the level-unload spike (E3-S11, #245) is written
into ADR 0002's pending section and the branch it picks is confirmed below, and the matrix reviewers have
signed. Spec: §3.1 Disband, §3.5, §9 FTB Teams, §11 FTB Teams Event Hooks; S19, N8, Nh, Ni
(`docs/TEAM_REQUIREMENTS.md`); Nick's 2026-10-27 answers Q2, Q14 and Q16 (`DESIGN_LOG.md`).

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
  ADR 0005 names the id and settles on this storage.

## Decision

### S19, restated

Nothing is deleted or moved while the server runs, except a live reset of a Vault if ADR 0002's unload
spike passes. Disband sets `disbanded_at` and evacuates; the folder and the team's data file move to the
archive at the next start, before restore, and restore skips a disbanded team. A reset that cannot unload
is finished at the next start the same way.

### The start-of-server pass

One pass in `vault`, run first in the `ServerStartedEvent` listener, before the classifier and the restore
(ADR 0002). For each `vault_<hex>` folder and for each team data file it finds:

1. Read the team data file as raw NBT, with `SavedDataStorage#readTagFromDisk`, never through
   `SavedDataStorage#get`, which would cache a `SavedData` for a file this pass then moves, and a later save
   would write it back at the old path.
2. `disbanded_at` set: move to the archive (below). A failed move is logged at ERROR and left in place;
   restore still skips it.
3. `pending_reset` set: finish the reset with steps 7 to 10 of the sequence below (backup if asked,
   delete, increment, window, clear the marker), then let restore register the new Vault.
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
- A timeout fails it. Proposed 60 seconds, a number for the Balancer to confirm.
- The backup flag rides on the session, set by the initiator, and is forced off when
  `reset.allow_backup_on_reset` (§10) is false.
- Unit tests at 1, 2, 3, 4 and 5 online, with abstentions, a disconnect at each point, and the timeout.

Who may start one is checked on the server: the sender holds a Tier 4 igniter (§3.5), outside or inside
the Vault. Other online members get a toast and a clickable chat line, never a screen (Q16).

### The reset sequence

When the vote passes, on the server thread:

1. Lock: the team's portal entry is refused with a message until the sequence ends, and the session state
   becomes `COUNTDOWN`.
2. Countdown of 10 seconds (§3.5) shown to anyone in the Vault: action bar, and a sound on each of the last
   five (old #95). The initiator can cancel it; a cancel ends the session and changes nothing.
3. Write `pending_reset {requested_at, backup}` to the team's data (ADR 0005) and save that file at once.
   From here a crash or a stop is finished by the start-of-server pass, so a reset is never half done.
4. Evacuate: every player in the Vault is sent to their `return_pos` in the Overworld (the respawn point
   when it is absent), without overwriting it. They are held there until step 11. `in_vault` is cleared.
5. Release the team's Vault Anchor tickets through the mod's `TicketController`, then clear the team's
   snapshot holders and effects view (ADR 0003) and its pending vein-index entries if ADR 0004 took that
   branch.
6. Unload, ADR 0002 `unload`, if the spike passed. If the spike failed, or this unload throws: stop here.
   Everyone is already out; send a message to the team's online members that the Vault regenerates at the
   next restart (Ni); keep entry refused; the start-of-server pass does steps 7 to 10.
7. Backup, if the session's flag is set: copy the closed folder to
   `<world>/orevault_backups/<teamUuid>/<timestamp>/` (§3.5). Storage is closed by step 6 or by the stop,
   so a half-flushed region file is never copied (old #96). If the copy fails, abort: delete nothing, clear
   `pending_reset`, re-register the old Vault unchanged, open entry, and tell the initiator. Log the path and
   the bytes written.
8. Delete `<world>/dimensions/orevault/vault_<hex>/`, which takes the old chunk tickets with it.
9. In the team's data, in one write: `vault_generation` + 1; `vault_variant` set to expanded if the team
   owns Vault Expansion, otherwise base (§3.5 step 5); the respec window (below); `pending_reset` cleared.
   Save the file at once.
10. Re-register: `ensureTeamDimension` with the saved variant (ADR 0002), which rebuilds the snapshot with
    the new `vault_generation`, so the seed changes and the veins are new (Q2, §3.1). Re-validate Anchor
    tickets: none survive, since their chunks were deleted, and the `TicketController`'s loading validation
    drops any stale entry.
11. Players held in step 4 who are still online are sent to the new Vault's spawn point at X=0, Z=0, where
    the portal arrives (Nh), and the return portal is built there. Not to their saved spot, which may be
    inside the new rock.
12. Open entry, sync team state (ADR 0006), and log the team id, the time, the new generation, the variant
    and the backup path and bytes (§3.5 step 8).

### Pending E3-S11 (#245): live reset or restart reset

The spike and its five checks are in ADR 0002 ("Pending E3-S11"). What each result decides here:

Result A, a live Vault unloads safely. Steps 6 to 12 run at once and the team is back in a fresh Vault
about ten seconds after the vote. The Ni path stays as the handling for an unload that throws.

Result B, it does not. Step 6 always stops: every reset sends everyone out, closes the Vault with the
message, and the start-of-server pass runs steps 7 to 10 at the next start; step 11 does not happen, since
nobody is held across a restart. QA's scripted reset test becomes "reset, restart, check the folder and the
team file".

The result and its date are copied here from ADR 0002 when that section is filled.

The return portal's tier is held by its placed blocks (PR #109), so after a reset it starts at the tier of
the next igniter that enters and climbs again. Colour only.

Players offline inside the Vault at a reset are not touched. Their file still names the Vault key; on login
ADR 0002's check sees a `vault_generation` above the one in their `in_vault` and places them at the spawn
point.

### The respec window (§3.5 step 7, §4.4; Q14; old #95)

At step 9, if no window opened in the previous 24 hours of server time (`free_respec_opened_at` absent,
or `now − free_respec_opened_at ≥ 1,728,000` ticks), set `free_respec_until = now + 12,000` and `free_respec_opened_at = now`; otherwise change
neither. Both are saved in the team file, so the window survives a restart. Times are game time, ADR 0005's
one clock, which only advances while the server runs, which is what "server time" means here. This
replaces the older rule that repeated resets never reopen the window (DESIGN_LOG 2026-10-27, "What this
supersedes").

### What a reset invalidates without editing anyone's file

Entry points, the Homeward Seam anchor and the death marker carry the `vault_generation` they were set in
(ADR 0005). Any with a generation below the team's current one is treated as absent when read, and is
dropped the next time that player's attachment is written. Offline players' files are never opened by the
reset. The same holds for `in_vault` (ADR 0002).

### Disband (§3.1, §9, §11; N8)

On `FTBTeamsEvent.TeamDeleted` for a team (only a party, per the source above):

1. Set `disbanded_at = now` in the team's data and save. If the team never wrote data and has no Vault
   folder, there is nothing to archive and nothing is written.
2. Evacuate any online player in that Vault to `return_pos` (respawn point when absent), and refuse entry to
   it from then on. No current member exists to enter it: every former member is in their personal team.
3. Log the team id and that the archive happens at the next start.

The level stays loaded, unticked by anyone, until the server stops. It is never unloaded at run time,
whatever the spike shows.

At the next start, the start-of-server pass moves the Vault folder and the team's data file into
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
`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`; the purge asks to be run a second time within 30
seconds with `confirm` appended, and logs what it deleted and the bytes. They touch only
`orevault_archive/`, never a live level or `orevault_backups/`, which an admin manages by hand (§3.5 calls
backups a safety net the mod does not use).

## Consequences

- A crash at any point of a reset leaves either the old Vault untouched or a `pending_reset` the next start
  finishes. The Ni fallback is the same code run later.
- If the spike fails, every reset needs a restart to finish, and the team cannot enter its Vault until
  then. That is Nick's answer Ni, and the message says so.
- Disk grows with archives and backups until an admin purges them. Accepted (N8: disk is cheaper than a
  lost world).
- A disbanded party's level costs memory until the next stop.
- `pending_reset` and the raw-NBT read are constraints on ADR 0005, which carries both: the field, with
  its default (absent), and a team reader that works on a bare `CompoundTag`.
- `core.reset` is a pure package, listed in ADR 0001's table.

## Rejected alternatives

- Deleting a disbanded team's Vault (the old spec, and `FtbEvents` at 1e7cdad): N8.
- Unloading and archiving a disbanded Vault at run time: nothing is gained by doing it before the next
  start, and S19 forbids it unless proven.
- Sending players inside at a reset to world spawn (old §3.5) or to their saved return position as the end
  point (smaller question h): the return position is only where they wait; Nh puts them at the new Vault's
  spawn point.
- Editing offline players' attachments at a reset: the generation tag makes it unnecessary, and those
  files are not loaded.
- A backup taken from the live folder: old #96, a copy of a half-flushed region file is worse than none.
- Adding late joiners to an open vote's denominator: they received no toast, and a member could block a
  reset by logging in and voting no.
- Archiving a solo Vault on a party join: the solo team is never deleted, so the event never comes.

## Spec sections changed

§3.1 Disband, §3.5 (the vote rules, the pending marker, the evacuation hold, the backup abort, the archive
apart from backups), §9 FTB Teams (the archive path; a party join never disbands), §11 FTB Teams Event Hooks
(disband is a party losing its last member; the data file is archived, not deleted) and §11 SavedData
Pattern (the `pending_reset` field, for ADR 0005). The exact text is in `spec-changes/E0-S10.md` for the
Writer to apply.

## Reviews

To be filled. Matrix row "System design before code": developer (buildable), reviewer (rules), qa
(testable); the ticket adds writer and balancer. Each verdict lists what it checked.

- developer:
- reviewer:
- qa:
- writer:
- balancer:
