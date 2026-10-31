# 0006. The network channel and Tome sync

Status: accepted 2026-10-31 (E0-S9, #183), after the round 2 verdicts below; proposed 2026-10-27. Owed to
E1-S5 (#195), read on Nick's PC before E7-S2a (#255): the NeoForge payload registration reads (client-only
handler registration, `PayloadRegistrar#optional()`, decode failure) and whether registered payloads are
held to vanilla's payload caps, with the `RegisterConfigurationTasksEvent` task API (sources doc "Still to
read" items 5 and 16). Every bound below keeps a payload under vanilla's caps whatever that read finds. Spec: §4.4, §6 Layout data and Saved state, §8, §9 FTB
Teams and FTB Ultimine, §3.5; `docs/TEAM_REQUIREMENTS.md` S25. Persisted shapes are ADR 0005's (E0-S8,
#225, written in parallel); reset and vote rules are ADR 0007's (E0-S10, #258); the Seismic Sense map's
storage is the ADR 0004 amendment's (E0-S12, #231). This record fixes only what crosses the wire.

## Context

Everything worth cheating at goes over this channel: buying, refunding, fork choices, tradeoffs, the
generation vote and the reset vote. The server is the only authority for all of them (`CLAUDE.md`
rule 6, Architect role). The pre-restart build had a working channel (`ModNetwork`, PR #126, #33, #35,
#40, #134, all before commit 86360a3), and four of its lessons stand:

- Payloads register on the mod bus through `RegisterPayloadHandlersEvent`. Registered on the game bus,
  the listener never fires and every packet is an unknown-channel disconnect, with no error logged
  (old PR #126; `CLAUDE.md` rule 3). The old `OreVault` constructor did
  `modEventBus.addListener(ModNetwork::register)` (`git show 86360a3^:src/main/java/com/orevault/orevault/OreVault.java`).
- Serverbound payloads carry intent, never a cost or a result, and handlers run through
  `IPayloadContext#enqueueWork` because `SavedData` is main-thread-only (old PR #126).
- The sync packet existed and was never sent (old #35). It must go out on login, after Resonance gain and
  after every purchase.
- The server syncs after a refused purchase too, and tree state travels in its own packet so the packet
  sent on every orb stays small (old PR #134).

The `TomeState` in `docs/TEAM_REQUIREMENTS.md` (Architect, Payloads) predates the PoE tree: it carries
tiers, which are now derived (§6 Skill and tier), and none of the state §6 Layout data, §8 and §9 now name:
the layout hash, ownership and hidden bitsets in layout order, the pin and `boughtBy`, the generation vote,
the Ascending Edge ladder, the Survey panel.

### What was read from the 26.1 sources

The decompiled NeoForge 26.1.2.95 sources (patched Minecraft) were read on 2026-10-27. Paths are inside
that source tree, which on Nick's PC is under
`.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/26.1.2.95/` (`CLAUDE.md`, MC 26.1 API names).

- `net/minecraft/network/codec/StreamCodec.java` has `composite` overloads from 1 to 12 field pairs
  (lines 129 to 554). The six-field ceiling old #35 hit is gone in 26.1. This record still nests (see
  Decision), but no longer because the API forces it.
- `net/minecraft/network/codec/ByteBufCodecs.java`: `stringUtf8(int maxStringLength)` (line 267);
  `collection(IntFunction, StreamCodec, int maxSize)` (line 411), which calls `readCount(input, maxSize)`
  before allocating; `list(int maxSize)` (line 444); `byteArray(int maxSize)` (line 251); `optional`
  (line 373); `VAR_INT`, `VAR_LONG`, `LONG`. `STRING_UTF8` is `stringUtf8(32767)` (line 168) and
  `BYTE_ARRAY` and `LONG_ARRAY` are unbounded, so none of those three is used on a decoded field here.
- `net/minecraft/resources/Identifier.java` line 19: `Identifier.STREAM_CODEC` maps over `STRING_UTF8`.
  `net/minecraft/core/BlockPos.java` line 39: `BlockPos.STREAM_CODEC`.
- `net/minecraft/network/protocol/common/ServerboundCustomPayloadPacket.java` line 14:
  `MAX_PAYLOAD_SIZE = 32767`; `ClientboundCustomPayloadPacket.java` line 16: `MAX_PAYLOAD_SIZE = 1048576`.
  These are vanilla's caps for a payload it does not know (`DiscardedPayload.codec(id, …)`). Whether
  NeoForge's registered payloads are held to the same numbers or split was not read; every bound below
  keeps a payload under both anyway.
- `net/minecraft/server/players/PlayerList.java` line 189: in `placeNewPlayer`,
  `NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, player))` runs after the login packet and before
  the player is added to `players`; `EventHooks.firePlayerLoggedIn(player)` is the method's last line.
  Line 864: `reloadResources()` posts `new OnDatapackSyncEvent(this, null)` once for all players, before the
  tag packet is broadcast. Flushing is suspended across `placeNewPlayer` (`suspendFlushing` /
  `resumeFlushing`), so payloads sent from the event are queued, not lost.
- `net/minecraft/server/MinecraftServer.java` lines 1580 to 1590 and
  `net/minecraft/server/ReloadableServerResources.java` line 174: on `/reload`,
  `TagsUpdatedEvent.ServerDataLoad` and `DefaultDataComponentsBoundEvent` both fire in
  `updateComponentsAndStaticRegistryTags()`, before `PlayerList#reloadResources()` posts the sync event. A
  ladder rebuilt on either is ready when the sync fires.
- `net/minecraft/server/network/ServerCommonPacketListenerImpl.java` line 212: `disconnect(Component)`.
- `net/minecraft/server/network/ServerConfigurationPacketListenerImpl.java` line 141: NeoForge posts
  `RegisterConfigurationTasksEvent` during the configuration phase (see Rejected alternatives).

NeoForge's own network classes (`RegisterPayloadHandlersEvent`, `PayloadRegistrar`, `IPayloadContext`,
`PacketDistributor`) are not in the unpacked tree, which holds patched Minecraft only. Their use here
follows the pre-restart `ModNetwork`, which compiled and ran against the same pin, `neo_version=26.1.2.95`
(`git show 86360a3^:gradle.properties`): `event.registrar(PROTOCOL_VERSION)`,
`registrar.playToServer(TYPE, CODEC, handler)`, `registrar.playToClient(...)`,
`context.enqueueWork(...)`, `context.player()`, `PacketDistributor.sendToPlayer(player, payload)`.
Unverified: to be read by E1-S5 (#195): whether NeoForge 26.1 offers a client-only payload handler
registration (so the common side need not hold the client callback described below), whether
`PayloadRegistrar#optional()` exists and defaults to required, and what NeoForge does with a payload whose
decode throws (expected: the connection is closed).

FTB Teams 26.1.2.4 was read on 2026-10-27 (`docs/research/neoforge-26.1-sources.md`, "FTB Teams party
join and disband"): the personal team's id is the player's UUID (`TeamManagerImpl#createPlayerTeam`); `getTeamForPlayerID` reads
`knownPlayers`, loaded with the server; `PlayerChangedTeamEvent` fires on every team change, for offline
players too, while `PlayerJoinedPartyTeamEvent` does not fire for an offline player.

## Decision

### The channel

One channel, registered on the mod bus from the `@Mod` constructor's `modEventBus` through
`RegisterPayloadHandlersEvent`, with `event.registrar(PROTOCOL_VERSION)`. Every payload is required: a
client without Ore Vault, or with another `PROTOCOL_VERSION`, is refused at connection by NeoForge's
channel negotiation.

`PROTOCOL_VERSION` is a string holding an integer, starting at `"1"`. It bumps, in the same PR, when:

- a payload is added, removed or renamed, including one added later by a node story (Survey readouts,
  outlines, anything E9 or E10 adds);
- any field of any payload, nested records included, is added, removed, reordered or changes type or
  codec, or a size bound is lowered;
- a node id in the layout file changes or is removed, so a client from before the rename is refused at
  negotiation instead of decoding bitsets against the wrong ids. Moving, re-ringing or re-edging a node
  needs no bump: the layout hash catches it (below).

It does not bump for a handler change, a new refusal lang key, or a raised size bound. The constant's
Javadoc keeps one line per bump saying what changed, as the old one did. The Reviewer checks that any diff
under `network/` touching a codec, or any diff to the layout file's ids, bumps it.

Payload records nest. 26.1's `composite` takes up to 12 fields, but a record of six or fewer fields, each
group a thing that changes together, is what the old code ran with and reads cleanly in review; this
record keeps that as the house rule. A record over six fields is split by meaning, not by count.

### Code layout

- `network`: payload records, codecs, registration and server handlers. Handlers decode, check the sender
  and call `TomeActions` on the main thread; they hold no rules. `network` may import `reset` (for
  `StartReset` and `CastResetVote`) and the vein-index leaf `worldgen.index` (ADR 0001).
- Outbound sync from packages that may not import `network` (`team`, `mining`, `vault`) goes through the
  dirty set `data.SyncOutbox` that ADR 0001 defines: those packages mark a team or a player dirty for a payload
  kind (`TeamProgress`, `TreeState`, `EntryPoints`, `ResetVoteState`), or queue a `VeinOutline` for a
  player, and `network` drains the set at the end of the server tick and sends. A mark is a flag, so
  several marks in one tick send once. `reset`, `chunkload` and `debug` may import `network` and send
  directly.
- `TomeActions` (in `network`, main thread only): resolves the sender's team through `TeamHelper`, reads
  `OreVaultTeamData`, calls the pure rules, writes, and sends the syncs below. Gametests call it directly
  with a mock player, so the rules are covered without a network round trip.
- `core.tree`: the pure checks (buy, path, refund connectivity, fork swap, tradeoff and vote eligibility),
  the `Refusal` enum, each value holding its lang key, and the Outgrown rule per growth node (below). They
  take booleans such as `insideVault`, never a `ServerPlayer` (old PR #111's `TradeoffToggle` shape). The
  same package holds the two sync timing rules as plain classes, so they are unit-tested: the per-player
  rate bucket (below) and the `TeamProgress` coalescer (Triggers). They take tick counts, not a server.
- `core.layout`: `TreeLayout` and its `canonicalHash()`.
- `client.net` and `ClientTomeState` under `client/`: clientbound handling and the cached state. Common
  code never names them. As before, `network` holds a `ClientHandler` callback that the
  `@Mod(dist = Dist.CLIENT)` entrypoint installs; a dedicated server registers the clientbound types so
  the channel matches and never receives one. If E1-S5 finds a client-side registration event, it replaces
  the callback with no wire change.

### Trust rules

- No serverbound payload carries a team id, a player UUID, a cost, a tier, a level, a point count or a
  price. The team is always the sender's, from `context.player()` through `TeamHelper`.
- Every serverbound handler runs inside `context.enqueueWork(...)`. Clientbound handlers do the same on
  the client, so `ClientTomeState` is written only on the client main thread.
- Every mutation is re-checked in full on the server against its own `OreVaultTeamData`, layout, level and
  the sender's position. A refused mutation changes nothing.
- Every mutating payload, accepted or refused, ends with a `TreeState` sync to its recipients; a refusal
  also sends `ActionRefused` to the sender. The client never updates a node optimistically: a click shows
  the node pending until the next `TreeState` (old PR #134). If no `TreeState` arrives within 40 ticks
  of the click, the client clears the pending mark and redraws the last state it has, so a click the
  rate limit dropped is still visibly answered. The server does not answer a dropped payload with a
  `TreeState`: that would let a flood buy a full sync per packet, which is what the limit is for.
- Serverbound mutations are rate limited per player: a bucket of 10, refilled at 10 a second. Over the
  limit the payload is dropped and the sender gets `refusal.orevault.too_fast` at most once a second.
  `RequestSurvey` and `RequestTripState` are each answered at most once per 20 ticks per player.
- Until the client's `LayoutAck` matches, every mutating payload from it is refused with
  `refusal.orevault.layout_unconfirmed`.

### Size bounds

Every decoded string and collection is bounded by its codec, so an oversize field fails decode before
allocation. Constants live beside `PROTOCOL_VERSION`:

| Constant | Value | Why |
|---|---|---|
| `MAX_ID` | 64 characters | node, fork, option and skill ids are snake_case words |
| `MAX_NODES` | 1024 | the layout's node count (213 at most in §6.1 Totals); bitsets are at most 16 longs, and the layout test fails above it |
| `MAX_PATH` | 150 | `LEVEL_CAP × POINTS_PER_LEVEL` (§4.3): no path can cost more points than a team ever has |
| `MAX_FORKS` | 16 | §6.1 has four forks |
| `MAX_MEMBERS` | `MAX_NODES + 1` | the attribution table holds every UUID that a pin or `boughtBy` names |
| `MAX_NAME` | 16 characters | vanilla player name length |
| `MAX_REFUSAL_NODES` | 8 | the Tome writes "and N more" past that (`docs/TEAM_REQUIREMENTS.md` S32) |
| `MAX_OUTLINE` | 4096 positions | the bound on one outline payload, 32 KB of packed longs |
| `MAX_MAP_SIDE` | 33 chunks | the Seismic map window, 1,089 cells |
| `MAX_RUNGS` | 1024 per ladder | Ascension ladder rungs |
| `MAX_ENTRY_POINTS` | 8 | three slots today (§3.3) |
| `MAX_RESOURCE_ID` | 256 characters | item and block ids (`namespace:path`), with room for long modded paths |

Strings use `ByteBufCodecs.stringUtf8(max)`. Collections use `ByteBufCodecs.list(max)` or
`collection(…, max)`. A bitset is a `List<Long>` bounded at 16, and the decoder also refuses a set bit at
or past the layout's node count. Item and block ids use `stringUtf8(MAX_RESOURCE_ID)`, not
`Identifier.STREAM_CODEC`, which reads up to 32767 characters. The serverbound worst case, `BuyPath` at 150 ids of 64 characters, is about 10 KB,
under 32767; the largest clientbound payload, an outline at 4096 positions, is about 33 KB.

### The layout hash

`TreeLayout#canonicalHash()` (pure, in `core.layout`) is the first 8 bytes of SHA-256 over a canonical
serialisation of the parsed layout: nodes in file order with id, skill, class, marks, ring, requirement,
exclusive group and position; edges sorted; forks with options and `petalAnchor`. It hashes the parsed
form, not the file's bytes, so line endings and whitespace in a checkout cannot change it. Node index `i`
on the wire is the `i`th node of that order; petals are not nodes and are named by id.

At join the server sends `LayoutCheck`. The client compares it with its own hash and always answers
`LayoutAck` with its own hash. On a mismatch the server logs both hashes and the player name, and
disconnects the player with `Component.translatable("disconnect.orevault.layout_mismatch", serverHash,
clientHash)`, the hashes as 16 hex digits. The text is the client's own lang file, which is present since
the channel negotiated.

### Serverbound payloads

All carry intent only.

| Payload | Fields | Server checks, in order | Refusal keys |
|---|---|---|---|
| `LayoutAck` | `long layoutHash` | equals the server's hash | disconnect, above |
| `BuyNode` | `String nodeId` | known; not hidden; not owned; adjacent to the start or an owned node; ring at most team level; an unspent point; no exclusive partner owned (§4.4) | `unknown_node`, `hidden`, `already_owned`, `not_adjacent`, `ring_above_level`, `no_points`, `exclusive_owned` |
| `BuyPath` | `List<String> nodeIds`, 1 to `MAX_PATH`, in the order the Tome highlighted | the whole list before anything is bought: no duplicates, none owned, each known and visible; each node adjacent to the start, an owned node or an earlier node of the list; each ring at most team level; length at most unspent points; no exclusive partner owned or earlier in the list. Then buys in order, all or nothing. The server buys exactly these ids, never a different equal-cost route (§8 Shift-click) | as `BuyNode`, plus `path_duplicate`, `path_not_connected`, `path_crosses_ring`, `path_too_costly`, naming the first failing node |
| `RefundNode` | `String nodeId`, `boolean expectFree` | known; owned; not the start; sender outside every Vault; every other owned node still connected to the start (§4.4 BFS); price from the undo window, the respec window or the class price; `expectFree` true and the price is not zero refuses rather than charges; sender holds the XP levels | `unknown_node`, `not_owned`, `start_node`, `inside_vault`, `refund_strands`, `refund_window_closed`, `refund_xp` |
| `ChooseForkOption` | `String forkId`, `Optional<String> optionId` (empty clears) | fork and option known; a parent node owned; sender outside every Vault; no owned node depends on the current option (§4.4) | `unknown_fork`, `unknown_option`, `fork_parent_not_owned`, `inside_vault`, `fork_dependents_owned` |
| `SetTradeoff` | `String nodeId`, `boolean on` | known; a `[TRADEOFF]` node; owned and visible; not Volatile Veins or Molten Seam; sender outside every Vault. Idempotent: `on` is the desired state, so a replayed packet repeats the same state | `unknown_node`, `not_a_tradeoff`, `not_owned`, `tradeoff_is_voted`, `inside_vault` |
| `SetGenerationVote` | `String nodeId`, `boolean on` | Volatile Veins or Molten Seam; owned and visible. Allowed anywhere, since nothing applies until the Vault is empty (§6.1 Volatile Veins). Idempotent | `unknown_node`, `not_voted_tradeoff`, `not_owned` |
| `SetPin` | `Optional<String> nodeId` (empty clears) | known and visible. Anywhere; a pin has no effect on play | `unknown_node`, `hidden` |
| `StartReset` | `boolean backup` | sender holds a Tier 4 Sovereign Vault Igniter in their inventory; the team has a Vault; no session open for the team; ADR 0007's further rules | `reset_no_igniter`, `reset_no_vault`, `reset_session_open` |
| `CastResetVote` | `int sessionId`, `boolean approve` | the session is the sender's team's and still voting | `reset_no_session` |
| `SelectEntryPoint` | `int slot`; −1 is the team anchor (X=0, Z=0) | slot under the capacity of the best igniter the sender holds (§3.3); the slot is set for the team's current `vault_generation` | `entry_no_capacity`, `entry_empty` |
| `RequestSurvey` | none | sender is answered with `SurveyState` | none |
| `RequestTripState` | none | sender is answered with `TripState` | none |

`SetTradeoff` carries the desired state. The old `ToggleTradeoff` had the server flip, so that a replayed
packet could not force a state; but a flip turns a replayed or duplicated packet into an undo, and the
inside-Vault check, not the packet shape, is what stops the Tithe exploit (E0-S9 ticket, settled).

`SetGenerationVote` is its own payload rather than `SetTradeoff` with a branch, because the rules are
opposite: a tradeoff is refused inside the Vault and a vote never is. The member's vote is their entry in
`active_tradeoffs` on `PlayerStats` (§11 SavedData Pattern; shape in ADR 0005).

`SelectEntryPoint`'s shape is what §3.3 needs today (three slots, selectable as a list). E10-S7's design
confirms it; any field it adds is a protocol bump.

`RequestSurvey` is sent when the Ore Memory tab opens and every 40 ticks while it stays open, so Survey
data is computed only for a player looking at it. `RequestTripState` follows the same pattern for the
Tome as a whole: sent when the Tome opens and every 40 ticks while it is open, so the trip counters the
tooltips show are live to within two seconds and are never sent to a player who is not looking.

### Clientbound payloads

| Payload | Fields | To whom | When |
|---|---|---|---|
| `LayoutCheck` | `long layoutHash`, `int nodeCount` | the joining player | `OnDatapackSyncEvent` with a player (each join), first |
| `ServerFeatures` | `int flags`: bit 0 FTB Ultimine loaded, bit 1 `c:clumps/` filled, bit 2 `c:shards/` filled, bit 3 `c:crystals/` filled | each player | each join and each `/reload` (`OnDatapackSyncEvent`) |
| `AscensionLadder` | `List<Rung> pickaxes`, `List<Rung> paxels`, each at most `MAX_RUNGS`; `Rung(String item, Optional<String> next, int oresToNext)` | each player | each join and each `/reload` |
| `TeamProgress` | `ResonanceProgress(long pool, int level, int unspentPoints, long levelStart, long nextThreshold, long recentBonus)`; `nextThreshold` is −1 at the cap | every online member | see triggers |
| `TreeState` | six nested records, below | each online member, with that member's own tradeoffs | see triggers |
| `PlayerContext` | `boolean inVault`, `boolean holdsSovereignIgniter`, `int entryCapacity` | the player | on change |
| `EntryPoints` | `List<EntryPoint(int slot, BlockPos pos, boolean anchor)>` up to `MAX_ENTRY_POINTS`, `int selected` | the player | join, any change, team change, reset |
| `SurveyState` | five fields, below | the requester | answer to `RequestSurvey`; once, unrequested, when the chunk stamp changes while the tab is open |
| `TripState` | three nested records, below | the requester | answer to `RequestTripState` |
| `VeinOutline` | `int kind` (0 Vein Sight, 1 Prospector's Eye, 2 Chapter's End), `int durationTicks`, `List<Long> positions` packed with `BlockPos#asLong`, at most `MAX_OUTLINE` | the breaking player | when the node fires |
| `ActionRefused` | `String reasonKey` (at most 128), `Optional<String> nodeId` (the node to flash), `RefusalArgs(List<String> nodeIds, int number, String playerName)` | the sender | each refused mutation, before the corrective `TreeState` |
| `ResetVoteState` | `int sessionId`, `int phase` (0 none, 1 voting, 2 countdown, 3 passed, 4 failed, 5 cancelled), `VoteTally`, `String initiator`, `boolean backup`, `int secondsLeft` | every online member | open, each vote, each member join, leave or disconnect, each phase change, each second of the countdown, and on join while a session is open |
| `OpenResetVote` | `int sessionId` | the player | answer to the clickable chat line (below) |

`TreeState`, the six nested records:

| Record | Fields | Notes |
|---|---|---|
| `Ownership` | `List<Long> owned`, `List<Long> hidden` | bitsets in layout order (§6 Layout data). Owned and hidden together is dormant: owned, hidden because its mod or tag is gone, no effect, not counted (§6 Mod-gated nodes). Hidden is the server's view (S25) |
| `Forks` | `List<ForkChoice(String forkId, String optionId)>`, at most `MAX_FORKS` | forks with no choice are absent |
| `Marks` | `Optional<Pin(int nodeIndex, int member)>`, `List<Integer> boughtBy`, `List<Member(UUID id, String name)>` | `boughtBy` has one entry per set bit of `owned`, in order: an index into the member table, or −1 for none. The table holds every member a pin or `boughtBy` names, current or former. Names come from `server.services().nameToIdCache().get(UUID)`, which returns `Optional<NameAndId>` (26.1 has no `GameProfileCache`; `Services.java` line 19, read by the Developer); an empty result is sent as an empty name and the Tome says "a former member". The start node's entry is −1 |
| `PlayerView` | `List<Long> active`, `List<Long> outgrown` | `active` is this player's `active_tradeoffs` (ADR 0005 `PlayerStats`) as a bitset, which for Volatile Veins and Molten Seam is this player's vote. `outgrown` is a bitset in layout order over every `[GROWTH]` node, owned or not, set when the node's condition can no longer be met for this player (§6 Notation, `[GROWTH]`; §8 Tab 1, class table), so an unowned node can warn before purchase |
| `GenerationVotes` | `List<GenerationVote(int nodeIndex, int votesOn, int needed, int members, boolean applied, boolean pending)>`, at most 8 | `needed` is the server's threshold (§6.1 Volatile Veins: more than half, or one of two); `pending` means the result differs from `applied` and waits for the Vault to empty. Only nodes the team owns are listed |
| `Windows` | `int respecSecondsLeft`, `List<UndoEntry(int nodeIndex, int secondsLeft)>`, at most `MAX_PATH` | the free respec window after a reset (§4.4, from the team's `free_respec_until`, ADR 0005) and each node still in its 60-second undo window. The client counts both down itself |

`TeamProgress` carries the level the server's rules use, the unspent points (`5 × level − owned visible
nodes`, §4.4) and the two thresholds around the pool, so neither the header nor the on-screen bar (§8)
computes anything from the curve, which depends on server config the client lacks. `pool`, `levelStart`,
`nextThreshold` and `recentBonus` are in ADR 0005's unit, thousandths of a Resonance; `level` is the curve's
level raised to `level_reached` (ADR 0005). `recentBonus` is the part of the Resonance paid since the
previous `TeamProgress` that came from bonuses (§11 `ResonanceBonuses`, after team scaling), so the bar
draws the gain segment (the pool's rise between two payloads) with its bonus share, as §8 On-screen
Resonance bar and §13 UI ask. It is 0 on the first payload after joining. The field is in the protocol
from `"1"` (UI-UX, 2026-10-27): nothing is built yet, so adding it now spends no bump. Animus is not
carried; the Animus epic adds it with a bump (Nd).

The Outgrown rule lives in `core.tree`, one condition per `[GROWTH]` node as §6.1 states it, from plain
values: team level for the level-bound nodes (Novice's Luck, Salvager's Eye) and the player's own
`deepest_y` for Shallow Grace (a player whose record is at or below Y=100 stops the bonus whenever they are
inside, §6.1). Novice's Luck is outgrown at level 30 and Salvager's Eye at level 12. Apprentice's Ledger
and Guide Vein state no condition that stops being reachable, so their bits stay clear until §6.1 states
one. The server computes the bitset per recipient; the client never evaluates a growth condition. Which
nodes are `[GROWTH]` and each one's wording on the tooltip are §6.1's and §8's.

`SurveyState`, five fields: `int chunkStamp` (0 not in a Vault, 1 generated with the team's current
generation nodes, 2 generated with older ones, 3 no stamp, drawn "Generated before this was tracked";
§8 Ore Memory, §11 Vein Index; the server compares the chunk's `gen_stamp` with the current `GenSnapshot`
`stamp`, ADR 0003 and 0004). Value 3 is kept apart from 2 because a team that owns no generation node,
standing in a chunk made before stamps existed, would otherwise be told its chunk is "not current", which
reads as a fault (UI-UX, 2026-10-27);
`int vaultVariant` (0 none yet, 1 base, 2 expanded), for the depth gauge's band edges;
`int guideVeinOreThisTrip` (§6.1 Guide Vein); `Optional<BlockPos> deathMarker`, from `last_vault_death`
(ADR 0005), sent only when its team and `vault_generation` match the sender's team's current ones (§8 Tab
3); `Optional<SeismicReadout(int tier, int towardChunkX, int towardChunkZ, Optional<SeismicMap(int
originChunkX, int originChunkZ, int side, byte[] cells)>)>`, present only when the team owns Seismic Sense
and the sender is in the team's current Vault. `cells` is
`byteArray(MAX_MAP_SIDE²)`, row-major by z then x: 0 not explored, 1 explored with no shade (tier 2, or
density unknown), 2 to 255 a shade at tier 3; the map is absent at tier 1. The window is 33 chunks a side
centred on the sender's chunk, and the decoder refuses a `side` above 33 or a `cells` length other than
`side²`. What "explored" and the shade are read from, and how the compass picks its chunk, are ADR 0004
Amendment 1's (E0-S12).

`TripState`, three nested records, all about the requesting player (§11 Vault Session State and
`PlayerStats`):

| Record | Fields | Notes |
|---|---|---|
| `Trip` | `boolean inTrip`, `int blocksThisTrip`, `int delveStacks`, `int delveTicksToNext`, `boolean secondWindUsed` | Deep Habit, Long Delve, Second Wind. `delveTicksToNext` is −1 at the stack cap or while the AFK guard holds accrual (no block broken in the last 60 seconds, §6.1 Long Delve). Outside a trip `inTrip` is false and the rest are 0 |
| `Discipline` | `int streak` | Vein Discipline's `vein_discipline_streak`, which persists across trips |
| `Kindred` | `int kindredTypes`, `Optional<String> nextOre` (at most `MAX_RESOURCE_ID`), `int nextCount` | Kindred Rock: how many ore types have reached 1,000, and the not-yet-kindred type with the most `ores_mined` with its count, for "progress toward the next" (§8 stats) |

These are the counters `docs/TEAM_REQUIREMENTS.md` (UI-UX) says the tooltips show live. They are sent
whether or not the team owns the nodes, so a tooltip on an unowned node can show what it would read. A
counter a later node adds is a field and a bump.

The Vault Keeps It's marker is this same death marker: §8 Tab 3 says there is one marker, with or
without the node. The client draws it with the node's treatment while the team owns the node, which it
knows from `Ownership`, so the node needs no field of its own.

`VeinOutline` positions, when more than `MAX_OUTLINE`, are cut nearest-first from the breaking block,
and the cut is logged at debug. The client drops every outline on a dimension change. Vein Sight's
duration is 600 ticks and Prospector's Eye's 100 (§6.1). Chapter's End's is 600 ticks, 30 seconds: it
fires once per chunk and must last long enough to tunnel to the nearest vein (Balancer, 2026-10-27,
unplaytested; the Writer states it in §6.1). The duration travels in `durationTicks`, so a change to any
of the three is no bump. One client renderer draws all three (old #101).

`ActionRefused` is drawn as `Component.translatable(reasonKey, names, number, playerName)`: argument 1
is the node names joined, from `node.orevault.<skill id>` through the client's layout, with "and N more"
past `MAX_REFUSAL_NODES`; an id the client's layout does not know (`unknown_node`, `unknown_fork`,
`unknown_option`) is shown as the raw id; argument 2 is the number (a level, points, XP levels or a count), −1 when
unused; argument 3 a player name. A key the client's lang file lacks shows
`refusal.orevault.generic` instead of the raw key.

`VoteTally` is `int yes`, `int no`, `int needed`, `int online`,
`List<Voter(String name, int vote)>` at most `MAX_MEMBERS` (vote 0 none, 1 yes, 2 no). The countdown is
phase 2: every member gets it and the client draws the warning only while `inVault` is true. The toast is
the client's, on the first `ResetVoteState` of a new `sessionId` in phase 1 for a member who is not the
initiator (§3.5, Q16). The clickable chat line is a server system message whose click runs
`/orevault reset vote`; that command answers its sender with `OpenResetVote`, and the client opens the
vote screen only then. The `/orevault` root and `reset vote` are registered on every server, regardless of
`[debug].enable_debug_commands`; only the debug subcommands sit behind that flag (§10, §13). Nothing opens a screen unasked. Vote rules, timeout and who counts are ADR 0007's.

### Triggers

| Trigger | Sends |
|---|---|
| `OnDatapackSyncEvent` with a player (each join; posted in `PlayerList#placeNewPlayer` before `PlayerLoggedInEvent`) | to that player, in order: `LayoutCheck`, `ServerFeatures`, `AscensionLadder`, `TeamProgress`, `TreeState`, `PlayerContext`, `EntryPoints`, and `ResetVoteState` if a session is open. The team lookup is safe this early: a party member is in FTB's `knownPlayers` from server start, and a first-ever player's team id is their UUID, which is what FTB's personal team will use. A first-ever player's team does not exist in FTB yet (`playerLoggedIn` creates it later), so the read path is `SavedDataStorage#get` (ADR 0005): a missing file reads as every field at its default and creates no file, and the unknown team has size 1 (`CLAUDE.md` rule 5) |
| `OnDatapackSyncEvent` with no player (each `/reload`) | to every online player: `ServerFeatures`, `AscensionLadder`, `TreeState` (the hidden set can change with tags). Not `LayoutCheck`: the layout is in the jar and a reload cannot change it |
| `FTBTeamsEvent.PlayerChangedTeam` | to the player who moved, if online: `TeamProgress`, `TreeState`, `EntryPoints`, `ResetVoteState` (none, or the new team's); to the online members of the old and the new team: `TreeState` (member table and vote thresholds change) and `ResetVoteState` if a session is open. Listened through the `team` package, the only FTB importer, which marks these in `data.SyncOutbox`; `network` sends them at the end of the tick. It fires for offline players too |
| Any serverbound mutation accepted | `TreeState` to every online member; `TeamProgress` to every online member when unspent points moved (buy, path, refund); a tradeoff changes only the sender's `TreeState` |
| Any serverbound mutation refused | `ActionRefused`, then `TreeState`, to the sender only (old PR #134) |
| Resonance gain | `TeamProgress` to every online member, coalesced: at most once per 10 ticks per team, sent at the end of the tick, and at once on a level-up (old #35). `mining` marks the team in `data.SyncOutbox`; the coalescer in `core.tree` decides whether this tick sends. Never a `TreeState`, except as the next row says |
| A level-up, or a player's `deepest_y` crossing a growth node's line (Shallow Grace, Y=100) | `TreeState` to each online member whose `outgrown` bitset changed. Marked in `data.SyncOutbox` by `mining` (the award and the stat record) |
| A pending generation vote applied (the last member leaves the Vault) | `TreeState` to every online member, marked in `data.SyncOutbox` by `vault` |
| A node fires an outline (Vein Sight, Prospector's Eye, Chapter's End) | `VeinOutline` to the breaking player, queued in `data.SyncOutbox` by `mining` and sent at the end of the same tick |
| Reset completes (respec window opens, `vault_generation` + 1) | `TeamProgress`, `TreeState`, `EntryPoints` to every online member |
| Dimension change, respawn, and a check every 20 ticks of each player's igniters | `PlayerContext` to that player when any field differs from the last one sent |
| Seismic Sense, Survey | `SurveyState` on `RequestSurvey` only |
| Trip counters | `TripState` on `RequestTripState` only |

The level-up toast (§4.3) is the client's, shown when `level` rises between two `TeamProgress` payloads
in one session, never on the first after joining. "Bought by" and "refunded by" messages to the team (§4.4)
are system chat built on the server, not payloads.

The client draws the tree neutral under "Waiting for team data" and takes no clicks until both
`TeamProgress` and `TreeState` have arrived (`docs/TEAM_REQUIREMENTS.md`, UI-UX). `ClientTomeState` is
cleared on disconnect.

### Refusal lang keys

Every key is `refusal.orevault.<reason>`, owned in text by the Writer and E7-S8; the arguments are as above.

| Key | When | Arguments |
|---|---|---|
| `unknown_node` | id not in the layout | the id |
| `hidden` | node hidden on this server | node |
| `already_owned` | buy of an owned node | node |
| `not_adjacent` | no edge to the start or an owned node | node |
| `ring_above_level` | ring above team level | node, ring |
| `no_points` | no unspent point, or unspent below zero (§4.4) | node, points needed |
| `exclusive_owned` | partner owned, or earlier in the path | node, partner |
| `path_duplicate` | an id twice in a path | node |
| `path_not_connected` | a path node joins nothing before it | node |
| `path_crosses_ring` | a path node's ring above team level | node, ring |
| `path_too_costly` | path longer than unspent points | points needed |
| `not_owned` | refund, tradeoff or vote on an unowned node | node |
| `start_node` | refund of the start | none |
| `inside_vault` | refund, fork change or tradeoff inside a Vault ("Leave the Vault to change this") | node |
| `refund_strands` | refund would disconnect nodes | the stranded nodes, their count |
| `refund_window_closed` | `expectFree` and the free window has ended | node, XP levels it now costs |
| `refund_xp` | too few XP levels | node, XP levels |
| `unknown_fork`, `unknown_option` | id not in the layout | the id |
| `fork_parent_not_owned` | no parent node owned | parent |
| `fork_dependents_owned` | owned nodes need the current option | those nodes |
| `not_a_tradeoff` | `SetTradeoff` on a non-tradeoff | node |
| `tradeoff_is_voted` | `SetTradeoff` on Volatile Veins or Molten Seam | node |
| `not_voted_tradeoff` | `SetGenerationVote` on another node | node |
| `reset_no_igniter` | no Tier 4 igniter in the inventory | none |
| `reset_no_vault` | the team has no Vault yet | none |
| `reset_session_open` | a reset session is already open | initiator |
| `reset_no_session` | vote for a closed or another team's session | none |
| `entry_no_capacity` | slot past the held igniter's capacity | capacity |
| `entry_empty` | no entry point in that slot | slot |
| `layout_unconfirmed` | mutation before a matching `LayoutAck` | none |
| `too_fast` | rate limit | none |
| `generic` | client fallback for a key it lacks | none |

Disconnect key: `disconnect.orevault.layout_mismatch` (server hash, client hash). A JUnit test reads
`assets/orevault/lang/en_us.json` as plain JSON and fails when a `Refusal` value's key is missing from it.

## Consequences

- The server holds every rule. A modified client can send any intent; the worst it can do is be refused.
- One full `TreeState` per mutation per online member, a few hundred bytes for a typical team (two 27-byte
  bitsets, up to 213 `boughtBy` varints, a small member table); about 34 KB in the absurd worst case. Full
  state beats deltas: nothing can drift and a lost payload is repaired by the next one.
- The packet sent on every orb is `TeamProgress`, about 40 bytes, at most twice a second per team.
- `TripState` is about 30 bytes plus one block id, every 40 ticks to a player with the Tome open.
- Adding a node payload in a later story is a protocol bump, so clients and servers from mixed builds
  are refused at negotiation with NeoForge's message, and a layout edit with no id change is caught by the
  hash with Ore Vault's.
- Gametests cover `TomeActions` directly; `runGameTestServer` after every payload change catches a client
  class on the common path (`docs/TEAM_REQUIREMENTS.md`, QA, T7).
- Codec bounds are `StreamCodec`s, which do not load in JUnit, so each bounded field gets one gametest
  that decodes a hand-built oversize buffer and expects the decode to fail: every bounded string and
  list in the size-bounds table, plus a bitset with a bit set at the layout's node count and a
  `SeismicMap` with `side` 34.
- A golden JUnit test pins the pair (`PROTOCOL_VERSION`, a hash of the layout file's node ids, sorted).
  A node id renamed or removed without a bump fails it; the fix is to bump and update the pinned pair in
  the same PR. This makes the third bump rule a test, not the Reviewer's eye.
- Unit tests in `core.tree` for the timing rules: 11 mutations in one tick accept 10 and drop 1; a second
  drop within 20 ticks sends no second `too_fast`; the bucket refills at 10 a second; two gains within 10
  ticks send one `TeamProgress`; a level-up sends at once.
- `canonicalHash()` unit cases: CRLF line endings and whitespace keep the hash; reordering edges in the
  file keeps it; adding an edge, moving a node, or reordering nodes in the file changes it (node order is
  the wire index).
- QA's T7 checks: a refused purchase resyncs; `/reload` with a tag change hides or shows the
  Mekanism-tag nodes on an open Tome; a second dev client (Q10) sees a purchase, a pin and a vote within
  one second; a mismatched layout disconnects with the message, produced by running the client from a
  build with one node moved in the layout file; the header reads "Waiting for team data", not zeros, on
  a slow join; no level-up toast on the first `TeamProgress` after joining; a click dropped by the rate
  limit clears its pending mark within two seconds.
- No new ArchUnit rule: ADR 0001's rule 5 already forbids any import of `..orevault.client..` outside
  `client`, which covers `client.net` and `ClientTomeState` (QA and Reviewer, 2026-10-27).
- `docs/TEAM_REQUIREMENTS.md`'s Architect payload list becomes a pointer to this record (E0-S2).
- The six-field nesting is a style rule now, not an API limit. If a reviewer finds a payload clearer
  flat, it may go flat up to 12 fields with a bump.

## Rejected alternatives

- A server-side flip for tradeoffs (`ToggleTradeoff` in the old code): a duplicated or replayed packet
  becomes an undo. The desired state with the inside-Vault check is idempotent and no weaker.
- One `TomeState` snapshot for everything, as in `docs/TEAM_REQUIREMENTS.md`: the progress numbers change
  on every orb and the tree rarely, so one packet would send the tree twice a second (old PR #134).
- Deltas instead of full state: more code paths and a dropped or reordered delta drifts until relog.
- Node ids as strings in the clientbound tree: about 3 KB a sync instead of a few hundred bytes, and the
  hash already guarantees both sides agree on the index.
- Folding the layout hash into `PROTOCOL_VERSION`: the disconnect would carry NeoForge's version text, not
  a message naming the layout, and every layout edit would be a protocol bump.
- The layout check as a configuration-phase task (`RegisterConfigurationTasksEvent`): it would refuse the
  player before the world loads, which is better, but needs a configuration-phase payload pair and a task
  whose API was not read. Worth revisiting after E1-S5; the wire would change by one payload.
- The client disconnecting itself on a mismatch: the server log would not say why, and Nick reads the
  server log when two dev sessions disagree.
- Pushing Survey data on every chunk crossing: it is computed for players who never open the tab.
  `RequestSurvey` limits it to a player looking at it.
- The client deriving level and thresholds from the curve: `curve_divisor` and `target_play_hours` are
  server config (§4.3, §10) the client does not have.
- Carrying Animus in `TeamProgress` from the start, as the old `SyncTeamProgress` did: zeroes on the wire
  for a post-1.0 system; the epic adds it with a bump.
- A separate payload for The Vault Keeps It's marker: §8 says it is the same spot as the death marker.
- Answering a payload dropped by the rate limit with a `TreeState` (UI-UX's second option for UIUX-16): a
  flood would then buy one full sync per packet, which is what the limit stops. The client-side 40-tick
  clear answers the click instead.
- The client evaluating growth conditions for Outgrown: Shallow Grace reads `deepest_y`, which the client
  does not have, and the rules would exist twice. The server sends the bitset.
- Pushing trip counters on every change: Deep Habit's count changes on every block. They are sent on
  request while the Tome is open, as Survey data is.
- `chunkStamp` 2 covering both "older nodes" and "no stamp" (the first draft): see `SurveyState` above.

## Spec sections changed

Applied in the same PR:

- §6 Layout data: a layout mismatch disconnects with a message, pointing here.
- §11: a short "Network and Sync" subsection pointing here.
- §13 UI: the stale `ModNetwork` checklist line is replaced.
- §8 Tab 3 and §11 Vein Index: the "Generated before this was tracked" state (`chunkStamp` 3).
- §8: an unowned node's tooltip shows "Outgrown: <why>" (the `outgrown` bitset), with one purchase
  confirmation.
- §10 and §13: only the debug subcommands of `/orevault` are behind `[debug].enable_debug_commands`.
- §6.1 Chapter's End: the outline lasts 30 seconds.

## Reviews

Round 1, 2026-10-27. Matrix reviewers: developer, reviewer, qa, writer; ui-ux reviewed the screen-facing
part because the Tome draws from these payloads. Rulings are the Manager's triage of the same date.

- Developer: approve with comments. Checked the 12 `composite` overloads (`StreamCodec.java`, last at
  line 554); the `ByteBufCodecs` bounds (lines 168, 251, 267, 407 to 411); the payload caps
  (`ServerboundCustomPayloadPacket` line 14, `ClientboundCustomPayloadPacket` line 16); the
  `placeNewPlayer` order (line 189, flushing suspended at 166 and resumed at 224). DEV-14 (name the 26.1
  profile lookup): fixed, `Marks` names `server.services().nameToIdCache().get(UUID)`. DEV-15 (the
  `/orevault` root must not sit behind the debug flag): fixed, the root and `reset vote` are always
  registered.
- Reviewer: approve with comments. Checked mod-bus registration; intent-only serverbound payloads;
  `enqueueWork` both ways; full resync after accepted and refused mutations; bounds within vanilla's
  caps; bump rules; the hash on the parsed form; `SetTradeoff` idempotent and refused inside; the vote
  payload apart; rate limit; refusal keys; client code only through the installed callback. RV-15 (the
  join read path on a first-ever player): fixed, `SavedDataStorage#get`, defaults, size 1. RV-16 (import
  rows): fixed, sends from `team`, `mining` and `vault` go through `data.SyncOutbox`; `network` may
  import `reset` and `worldgen.index` (ADR 0001).
- QA: approve with comments. Checked the trust rules, bounds, hash, every payload's checks and keys, the
  triggers, the lang-key test and the asks of QA. QA-18 (bounds untestable in JUnit): fixed, one gametest
  per bounded field. QA-19 (id-change bump rests on review): fixed, golden test pinning the version with
  the node-id hash. QA-20 (timing rules pure; T7 steps; hash cases): fixed, bucket and coalescer in
  `core.tree` with unit cases, three T7 steps and the `canonicalHash` cases added. QA's answer that ADR
  0001 rule 5 covers the client import rule is taken; the ArchUnit ask is withdrawn.
- Writer: request changes. Checked the payload tables and refusal keys against the ticket; bounds against
  §6.1 Totals and the four forks; §11 Network and Sync against the 10-tick coalescing; the §6 Layout data
  sentence. WRITER-1 (pointers into the session folder): fixed at the FTB Teams read, which now cites
  `docs/research/neoforge-26.1-sources.md`, and in Spec sections changed. WRITER-2 (one name for the
  anchor): fixed, "the team anchor (X=0, Z=0)". WRITER-11 (debug flag): fixed with DEV-15. Comment on
  unknown ids as refusal argument 1: fixed, the raw id is shown.
- UI-UX: request changes on what the screen can show. Checked every Tome element in §8 and the UI/UX
  section against the clientbound payloads. UIUX-11 (stamp 2 conflates "older" and "none"): fixed,
  `chunkStamp` 3. UIUX-15 (no field for the bar's bonus share): fixed, `recentBonus` in `TeamProgress`
  from `"1"`. UIUX-16 (a dropped click stays pending): fixed, the client clears pending after 40 ticks;
  answering a dropped payload with a `TreeState` is not taken, since it would defeat the limit.
  UIUX-17 with UIUX-10 (trip counters and Outgrown not on the wire): fixed, `PlayerView.outgrown` and
  `TripState`.
- Also applied from the triage: Chapter's End outline at 600 ticks (Balancer, answering this record's
  open question 3).

Round 2, 2026-10-31, against HEAD 0c5736c; ruled in the Manager's round 2 triage, with no round 3. Every
round 1 objection above was reported resolved by the role that raised it.

- Developer: approve. Checked DEV-14 (`nameToIdCache` in `Marks`) and DEV-15 (the `/orevault` root and
  `reset vote` always registered, with ADR 0007) at their lines.
- Reviewer: approve. Checked RV-15 (`SavedDataStorage#get`, defaults, size 1 on a first-ever join) and RV-16
  (answered with RV-9 in ADR 0001), and the outbox drain against "at once on a level-up". ADR 0001 now runs
  that drain last in one root `ServerTickEvent.Post` listener (DEV2-3), which is what keeps the promise.
- QA: approve. Checked QA-18 to QA-20 (per-field bound gametests, the version golden test, the pure bucket
  and coalescer with unit cases, the T7 steps and `canonicalHash` cases).
- Writer: approve. Checked WRITER-1 (this record cites `docs/research/neoforge-26.1-sources.md`), WRITER-2
  (the team anchor in `SelectEntryPoint`), WRITER-11 (only debug subcommands gated) and the raw id as
  refusal argument 1.
- UI-UX: approve with comments, screen-facing. Checked UIUX-10, 11 and 15 to 17 against `TripState`,
  `chunkStamp` 3, `recentBonus`, the 40-tick pending clear and `PlayerView.outgrown`.
  - UIUX-R2-1, Outgrown per viewer here and per team in spec §8: no change here. This record computes the
    bit for the viewing player from their own `deepest_y`, which matches §6.1 Shallow Grace counting only
    members inside; the Writer aligns §8 Tooltip and the class table (Manager, round 2).
  - UIUX-R2-2, holding the last non-zero `recentBonus` segment on screen: deferred to E5-S11 (#281), a
    client display rule set on the preview. Nothing on the wire changes.
- Architect (read across ADRs 0001 to 0009): approve; `data.SyncOutbox`, the triggers and `chunkStamp` 3
  read the same in every record.

Deferred elsewhere and noted here because it would amend this record: PT-R2-2, a "N of 50 new chunks toward
the next cache" line, needs one int in `TripState`. The Manager deferred it to E9-S33 (#338), whose
amendment lists the Architect as reviewer of the ADR 0006 amendment that adds the field; adding it before
release costs a `PROTOCOL_VERSION` bump and no save migration. Not changed now.
