# Sketch: Resonance orb on a VaultOrbEntity base (#260, E5-S2)

Status: approved with comments by all five reviewers; the Manager's rulings are applied below and listed at the end. Spec: §3.4 (vanilla XP untouched), §4.1, §4.2, §11 Resonance
Orb Entity, §11 SavedData Pattern ("Also save data", "`data_version` and migration"). Records: ADR 0001 (rows `mining`,
`core.mining`, `registry`, `client`; named rules 2, 5, 6), ADR 0005 sections 3 and 10. Old code: `66dc9c5`, `e20b7c8`,
`9059dd8` (`entity/VaultOrbEntity.java`). Read against NeoForge 26.1.2.95 sources (`ExperienceOrb`,
`ExperienceOrbRenderer`, `LivingEntity#take`, `ClientPacketListener#handleTakeItemEntity`) and `javap` on 26.1.2.121.

## The rule this sketch holds

The server alone decides who an orb follows and who it pays, and "member" has one definition everywhere:
`TeamHelper.teamId(player).equals(orb.team)`. The orb never touches player XP, `takeXpDelay` or anything else vanilla
XP reads (§3.4). Everything an orb decides that needs no `Level` is a pure function in `core.mining`.

## Classes

| Class | Package | What it is |
|---|---|---|
| `VaultOrbEntity` | `mining` | Abstract, `extends Entity`, not `ExperienceOrb`. Holds one `OrbFields`, the source of truth for team, value, `orb_count` and age, and an `InterpolationHandler` returned from `getInterpolation()` as `ExperienceOrb` does; synced `DATA_VALUE` (`EntityDataSerializers.LONG`, thousandths) mirrors `fields.value` for the renderer and is set wherever the fields are, `readAdditionalSaveData` included. Two abstract methods only: `pay(ServerPlayer collector, UUID team, long value, int orbCount)` and `int tint(float ageInTicks)`. One overridable: `double attractionRadius()`, returns `OrbRules.BASE_RADIUS` (#347 overrides it from the effects view) |
| `ResonanceOrbEntity` | `mining` | `final`. Registry constructor `(EntityType, Level)`; `static Optional<ResonanceOrbEntity> spawn(ServerLevel, Vec3, UUID team, long valueThousandths)`, empty for a value of 0 or less; `pay` calls `ResonanceAward.collect`; `tint(age)` returns `ResonanceColor.pulse(age)` |
| `ResonanceAward` | `mining` | Final helper, the one path into the pool. `static void collect(ServerPlayer collector, UUID team, long valueThousandths)`. #274 adds the level-up and `SyncOutbox` here, #264 the `debug.log_resonance_gain` reader, #347 the Hoarder multiplier |
| `OrbFields` | `mining` | Record `(Optional<UUID> team, long value, int orbCount, int age)`, `DATA_VERSION = 1`; `static OrbFields read(CompoundTag, Consumer<String> warn)`, `CompoundTag write()`, `migrate()` as a no-op chain. Plain class, CompoundTag only, unit-testable (ADR 0005, `CompoundTagLoadsTest`) |
| `OrbRules` | `core.mining` | Final helper, pure. `BASE_RADIUS = 8`, `LIFETIME = 6000`, `SCAN_PERIOD = 20`, `MERGE_REACH = 0.5`; `isMember(@Nullable UUID orbTeam, UUID playerTeam)`; `pull(double distance, double radius)` (vanilla's `(1 - d/r)^2 * 0.1`, 0 at or past `r`); `canMerge(@Nullable UUID a, @Nullable UUID b)`; `merge(...)` returning a `Merged(long value, int orbCount, int age)` record; `icon(long valueThousandths)` |
| `ResonanceColor` | `core.mining` | Final. `RESONANCE_COLOR = 0xFFB36BFF` (ARGB, light violet; Nick asked for purple on 2026-11-26, replacing cyan `0xFF4FC3F7`), the one constant the Tome header (T8) and the Resonance bar read; `RESONANCE_DEEP = 0xFF7A3DF0` beside it (was `0xFF1E88E5`), the far end of the orb's pulse; `pulse(float ageInTicks)` blends the two on vanilla's orb timing, `(sin(age / 2) + 1) / 2` (Designer, Manager ruling 1) |
| `ModEntities` | `registry` | `DeferredRegister.createEntities(MOD_ID)`; `RESONANCE_ORB = registerEntityType("resonance_orb", ResonanceOrbEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(2).fireImmune())`; `register(modEventBus)` from the `OreVault` constructor |
| `VaultOrbRenderer`, `VaultOrbRenderState` | `client` | One renderer for every orb type (§11). State adds `int icon`, `int tint` (from `tint(ageInTicks)` at extract); registered for `RESONANCE_ORB` in `OreVaultClient` on `EntityRenderersEvent.RegisterRenderers` (mod bus) |

No interface, no orb registry, no `OrbKind` enum: the base exists because §11 names it for the Animus orb (#192), and it
stays at two abstract methods. ArchUnit needs no new row: `mining`, `core.mining`, `registry` (which may import `mining`)
and `client` exist. `ResonanceColor` sits in `core.mining` rather than a new package because a new `core` package costs
an ADR 0001 row, a rule row and a planted test for one int; client code may import anything, so the Tome reaches it.

## Behaviour

**Tick.** Client: `interpolation.interpolate()` and return; no physics, no follow, no merge (§11: the client cannot
know membership, so it must not predict). Server: vanilla `ExperienceOrb#tick` copied as is (gravity 0.03, water and
lava handling, unstick, friction, bounce), with the follow and merge swapped for ours, and `age` advancing to
`LIFETIME` then `discard()`. `LIFETIME` is vanilla's 6000 ticks, a named constant: the Manager's default while Nick's
answer is open. A loaded orb with `value == 0` discards on its first server tick (Balancer).

**Attraction.** Each server tick: `level().getNearestPlayer(x, y, z, attractionRadius(), p -> p instanceof ServerPlayer sp
&& !(p instanceof FakePlayer) && !p.isSpectator() && !p.isDeadOrDying() && OrbRules.isMember(team, TeamHelper.teamId(sp)))`
(no cast; Reviewer), then vanilla's pull toward eye
height / 2 with `OrbRules.pull`. Rescanning every tick, rather than caching a `followingPlayer`, means a member who
leaves the team stops pulling the next tick. Cost is a team lookup per player in the level per orb, a handful in a
Vault. `XpOrbTargetingEvent` is not posted (it takes an `ExperienceOrb`), so other mods' XP magnets do not move ours.

**Pickup.** `playerTouch(Player)`: return unless `ServerPlayer`, not a `FakePlayer`, `team != null`, and
`OrbRules.isMember`. Then `pay(...)`, the sound, `discard()`. The whole orb pays in one touch; no `takeXpDelay`
(sharing it would slow vanilla XP pickup, §3.4). Trap: `LivingEntity#take` sends its packet only for `ItemEntity`,
`AbstractArrow` and `ExperienceOrb`, so the old code's `player.take(this, 1)` did nothing; and sending
`ClientboundTakeItemEntityPacket` ourselves makes the client play the item sound. So: no take packet, and the server
plays `SoundEvents.EXPERIENCE_ORB_PICKUP`, `SoundSource.PLAYERS`, volume 0.1, pitch `(r1 - r2) * 0.35 + 0.9` (vanilla's).

**Payment** (`ResonanceAward.collect`, main thread: `playerTouch` runs in the player's server tick):
1. `fields = OreVaultTeamData.read(server, team)`; if `fields.disbandedAt()` is present, pay nothing and return.
2. `scaled = TeamScaling.poolGainThousandths(value, TeamHelper.size(team))`; size counts offline members, floor 1.
   Rounding is half up, once, in integers, as `TeamScaling` already documents and `TeamScalingTest.anAmountIsRoundedOnceHalfUp`
   pins (1 thousandth on a team of 3 is 0.4, so 0; on a team of 2 it is 0.55, so 1). No behaviour change.
3. `OreVaultTeamData.forWrite(server, team).addResonance(scaled)`, the write API as it stands (QA #6), which creates the
   file on a team's first gain. A team with no file yet must be paid: that is every team's first orb. A fake player never
   gets here: pickup refuses it first.

An orb whose team is disbanded is still consumed on touch; it pays nothing. Only a non-member cannot touch it at all.

**Merge** (vanilla's, minus the equal-value rule): every `SCAN_PERIOD` ticks on the server, orbs of the same entity
type within `MERGE_REACH` with `OrbRules.canMerge` (both teams present and equal), skipping itself and any orb already
`isRemoved()`, fold into this one, and the absorbed orb is discarded: value summed
(saturating), `orbCount` summed (saturating), `age` the minimum. Summing value means any two same-team orbs merge, and
the sprite grows with the total. `spawn` does not merge at spawn time; the scan does it within a second. #347 widens
the reach and stops ageing for Hoarder's Instinct through `attractionRadius()` and the effects view.

**Damage.** `hurtServer` and `hurtClient` return false and are `final`; `isAttackable` false; type is `fireImmune()`.
Lava still bounces it as vanilla does. `/kill` and falling out of the world still remove it; the Vault floor is bedrock.

## Save data (ADR 0005 section 10, one amendment)

`addAdditionalSaveData` writes one compound, `output.store("orb", CompoundTag.CODEC, fields.write())`;
`readAdditionalSaveData` reads `input.read("orb", CompoundTag.CODEC)`, absent reading as an empty tag. Nesting is the
change from ADR 0005's flat keys: `ValueInput` cannot be built in a unit test, a `CompoundTag` can, so the reader is a
plain class with fixtures like `TeamFields`. Nothing has been written yet, so it costs no migration. Golden key set:

| Key | Type | Absent or bad reads as |
|---|---|---|
| `data_version` | int | 1; newer than 1 loads best-effort with one WARN |
| `team` | UUID string | none: follows nobody, pays nobody, ages out. A malformed string is none, never a throw |
| `value` | long, thousandths before team scaling | 0; negative reads 0 |
| `orb_count` | int | 1; below 1 reads 1. Orbs merged into this one, Hoarder's Instinct's N (§6.1, ADR 0005 s.10) |
| `age` | int ticks | 0; clamped to 0..`LIFETIME` |

The entity type id `orevault:resonance_orb` is save data from this PR: add it to `FrozenIdsTests`' `ENTITY_TYPE` list.

## The interface #264 calls

`ResonanceOrbEntity.spawn(ServerLevel level, Vec3 pos, UUID team, long valueThousandths)`, main thread only. `team`
comes from the dimension (`VaultKeys`), not the breaker. `valueThousandths` is the per-break amount after
`ResonanceBonuses`, before team scaling; 0 or less spawns nothing and returns empty. Nothing else in `mining` constructs
an orb.

## Dev command (QA, Manager ruling 2)

`/orevault orb spawn <thousandths> [team]` in `DebugCommands`, with the same operator and flag guard as the others; the
team defaults to the caller's (`TeamHelper.teamId`). It spawns at the source's position, so `execute positioned` moves
it. A plain `/summon orevault:resonance_orb` makes an orb of value 0, which is discarded on its first tick. `debug` may
not import `mining` (ADR 0001), so the root hands the spawn function over, as it hands over the flag. A value of 0 is
refused with a failure line.

## Renderer

Copy of `ExperienceOrbRenderer#submit` on vanilla's `textures/entity/experience/experience_orb.png` (nothing shipped,
so the 16x16 texture rule does not apply), with the vertex colour from `state.tint` (the violet pulse) instead of the green one, alpha
128, block light +7, shadow 0.15. `extractRenderState` sets `icon = OrbRules.icon(entity.value())` and `tint =
entity.tint(state.ageInTicks)`. `icon` steps at 3, 3.25, 3.5, 4, 7.5, 9, 10, 20, 50 and 100 Resonance (in thousandths): common 2
is sprite 0, uncommon 5 sprite 4, rare 12.5 sprite 7, merged orbs grow to 10. Vanilla's `getIcon` thresholds on whole
Resonance (sprites 0, 1, 2 for the three tiers) were replaced after Nick saw them in game (2026-11-26). An entity type with no renderer kills
`runClient` at startup while a server runs, so the renderer lands in this PR, not later.

## Tests

Unit (`src/test/java`): `OrbRulesTest` (icon for 2, 5 and 12.5 on sprites 0, 4 and 7, each band's floor and one below it, and the top; `pull` at
0, mid, `r`, past `r`; merge sums, saturation, minimum age; no-team never merges and is never a member); `OrbFieldsTest`
(golden key set; a fixture without each key loads its default; malformed `team`, negative `value`, `orb_count` 0,
wrong-type keys, newer `data_version` warns once; round-trip). One planted break per class shown failing in the PR.

Planted breaks, one per pure class (QA): `OrbRules.isMember` true for a null orb team; `OrbFields` keeping a negative
value; `ResonanceColor.RESONANCE_COLOR` changed. At least one is shown failing in the PR.

Gametests: (1) a non-member player two blocks away for 40 ticks: horizontal distance does not shrink, a direct
`playerTouch` leaves the orb alive, and the orb's team has no file (`hasRecord` false); (1b) the member counterpart: the
distance shrinks within 40 ticks by pull alone, then collection is by a direct `playerTouch` (the test player does not
tick, so it never touches anything itself), and the pool equals `TeamScaling.poolGainThousandths(value, 1)`; (2) pickup
of a disbanded team's orb and of a null-team orb pays nothing, leaves the pool unchanged and throws nothing; (3) the entity tag round-trips (team, value
12,500, `orb_count` 3, age 100) and a tag without `orb` loads at defaults; (4) `hurtServer` with generic, lava and
explosion damage returns false; (5) two same-team orbs merge within 21 ticks with total value and `orb_count` conserved,
and two different-team orbs do not; (6) `FrozenIdsTests` with the new id; (7) the registered type has `updateInterval`
2; (8) the dev command spawns an orb of the value asked and refuses 0; (9) a loaded tag with `value` 0 is discarded on
its first tick. `runGameTestServer` booting is the dedicated-server check on the renderer split.
In `runClient`: a Resonance orb beside a vanilla XP orb, screenshot for the Designer and Nick.

## Spec and record changes in the PR

ADR 0005 s.10: the nested `orb` compound; "has no data" becomes "is disbanded" (as written, a team's first orb would pay
nothing). Ticket #260 says "source ores"; ADR 0005 s.10, §6.1 and §13 say orbs merged, and this sketch follows them.
§4.2 and §11 Resonance Orb Entity: no take packet, so no fly-in pickup animation; the XP pickup sound plays. ADR 0005
s.10 gets a dated amendment line. The §13 line for the entity is ticked; the attraction line also names #347 and stays
open until it merges. Lang: `entity.orevault.resonance_orb` for the Writer.

## Manager rulings applied (2026-11-25)

1. Pulsing tint between `RESONANCE_COLOR` and `RESONANCE_DEEP` on vanilla's sine timing (Designer).
2. The dev command `/orevault orb spawn <thousandths> [team]` (QA).
3. Value 0 discarded on load and refused at spawn; `TeamScaling`'s rounding (half up, once) stated and already tested (Balancer).
4. `OreVaultTeamData.forWrite(server, teamId)` as it is (QA #6).
5. `InterpolationHandler` and `getInterpolation()`; `OrbFields` the source of truth, pushed into `DATA_VALUE` on load;
   merge discards the absorbed orb, skips removed orbs, and a gametest checks value is conserved (Developer).
6. Attraction and pickup by `instanceof ServerPlayer`, never a `FakePlayer` (Reviewer).
7. The tests above (QA).
8. Unpicked orbs despawn after 6000 ticks, a named constant. Nick confirmed on 2026-11-26 (DESIGN_LOG).
9. ADR 0005 s.10, §4.2, §11 and §13 in the same PR; `en_us` for the entity name.
