# 0010. The sealed door, and how a frame shows its portal's colour

Status: proposed, 2026-11-01 (E3-S4, #229). Waits on the matrix reviewers and on Nick's yes to the door render.
Spec: §3.2, §3.3. `DESIGN_LOG.md` 2026-11-01, "A valid frame shows a sealed door". Depends on ADR 0001's package
table and on `VaultPortalShape` (#207).

The facts below were read from the patched Minecraft sources of `neoforge-26.1.2.95`, NeoForge's own sources jar
of the same version, and FTB Teams 26.1.2.4. `javap` on the pinned 26.1.2.121 jar confirmed the signatures of
`BlockTintSource`, `ModelManager#requiresRender`, `BlockBehaviour#onPlace`, `#tick`, `#updateShape` and
`Block#getShapeForEachState`.

## Context

Nick approved: a completed ring fills with a sealed door; a signet swaps door or frame for the portal of that
tier; every frame block's strands are a grey tint, dormant until lit, then the tier colour, and a dissolve returns
them to grey. The Designer proposed a `tier` property with five values on the frame.

What the sources say:

- A tint source can read state, and more. `BlockTintSource` has `color(BlockState)` and
  `colorInWorld(BlockState, BlockAndTintGetter, BlockPos)`, whose default calls `color`
  (`net/minecraft/client/color/block/BlockTintSource.java` lines 13 to 17). `BlockAndTintGetter` is a
  `BlockGetter`, so it can read other blocks. In a section build it is `RenderSectionRegion`, a snapshot with
  `RADIUS = 1` section, and the value is computed once per block and tint index
  (`ModelBlockRenderer`, around line 278).
- A multipart file can omit a property. `KeyValueCondition#instantiate` tests only the keys the file lists, and
  throws only on a key the block lacks (lines 31 to 44). A selector with no `when` matches every state
  (`Selector` line 24). The frame's file (`src/generated/.../blockstates/vault_frame.json`) is multipart.
- That omission has a trap. `LevelRenderer#setBlockDirty` (line 1456) redraws only if
  `ModelManager#requiresRender` (line 386) says so, and that compares model groups. `ModelGroupCollector` (line
  32) keys a group on the model's `visualEqualityGroup`, which for multipart is the set of selectors that match
  (`MultiPartModel` line 197), plus `BlockColors#getColoringProperties`, which is the tint source's
  `relevantProperties()`, empty by default. So a `tier` change that the model file ignores is invisible until
  something else rebuilds the section, unless the tint source returns the property from `relevantProperties()`.
- A section redraw reaches next door. `setBlocksDirty` widens by one block each way (lines 1446 to 1454), so a block
  changing beside a section boundary dirties the section across it.
- `onPlace` runs on the server when a block arrives by any `Level#setBlock`: `LevelChunk#setBlockState` line 327,
  unless flag 512 is set. Player placement captures snapshots, which skips it there, and
  `CommonHooks#onPlaceItemIntoWorld` then calls it itself (line 700). A piston's finish arrives as
  `setBlock(pos, newState, 67)` (`PistonMovingBlockEntity` line 324). Generation does not reach it: `ProtoChunk`
  has no `onPlace`. Vanilla fire opens a nether portal the same way, from `onPlace`, with an `oldState` check
  (`BaseFireBlock` lines 153 to 168), and fills with flags 18 (`PortalShape#createPortalBlocks`).
- A block with no collision holds fluid unless named: `FlowingFluid#canHoldAnyFluid` (lines 402 to 419) names
  `Blocks.NETHER_PORTAL` by identity, and `state.blocksMotion()` is false for a no-collision block.
- A wither ignores hardness. NeoForge's `canEntityDestroy` asks `WitherBoss.canDestroy`, which only tests
  `minecraft:wither_immune` (`IBlockExtension` lines 750 to 757, `WitherBoss` line 361). The dragon tests
  `minecraft:dragon_immune` the same way.
- A palette entry with no value for a property reads the default state's value (`StateDefinition#appendPropertyCodec`,
  line 134), so a property can be added later with no migration.

## Decision

### The frame has no `tier` property

A frame's colour is not stored. It is worked out from the blocks beside it when the section is drawn, so the
frame keeps its 64 states and nothing writes to it when a portal opens or dissolves.

**The rule.** A frame block shows the highest tier among the portal cells in its plane around it, and grey if
there are none. On axis X those are the eight cells at (x±1, y), (x, y±1) and the diagonals in that plane, on
axis Z the same with z, 14 blocks in all. A cell counts only if it is a portal whose own axis is that plane's.
Every block around an opening cell in its plane is another cell, a side or a corner position, so a frame block
that passes this test belongs to that ring. Doors have no tier and count for nothing.

The pure half is `core.frame.FrameLight`: `int tierAround(x, y, z, PortalView)`, returning 0 for grey, where
`PortalView#tierAt(x, y, z, axis)` is 0 for anything that is not a portal on that axis. It imports nothing, so
ADR 0001's `core.frame` row stands. The tint source is `client.VaultFrameTint`: `color(state)` is grey, and
`colorInWorld` calls `FrameLight` over `level` and maps the tier to the rune colour. `VaultPortalBlock#tier()`
gives it the tier. It is registered through `RegisterColorHandlersEvent.BlockTintSources` on the mod bus, and
`relevantProperties()` stays empty, because no frame property affects it. The portal's change redraws the frame,
through the widening above.

The model gains strand faces with `"tintindex": 0` over the base, as `grass_block` does. The item definition
needs a `minecraft:constant` tint of the same grey, or the item's strands draw white.

### Corners

There is no lighting pass, so nothing has to reach them. A corner block is at the in-plane diagonal of the
opening's corner cell, so the rule lights it, including one added after the portal opened. The same rule releases
it: remove the portal and the cell around it is no longer a portal. Breaking a corner leaves the portal standing
(§3.2) and changes no other block's colour.

### A frame shared by two portals

The highest tier wins. Dissolving one leaves the shared block showing the other's colour, or grey if there is no
other. It follows from the rule with no code of its own, and it matches the return portal's "upgrading, never
downgrading" (§3.2). The block is not released while another portal still uses it. Breaking the shared block
dissolves both, since both rings are then invalid.

### When the door appears

`VaultFrameBlock#onPlace`, when `!oldState.is(this)`, calls `VaultOpenings.seal(level, pos)` once. It runs
`VaultPortalShape.findAll` from that block and, for each shape whose cells are all `AIR`, sets the door with flags
18, from `DoorPart.of(column, row, width, height)`.

| Way a frame arrives | Result |
|---|---|
| Player placement, `/setblock`, `/fill`, a piston, a structure placed at run time | Scans, once, from the block that arrived. Completes a ring: door |
| A piston moving a frame out of a ring | The ring is broken: dissolve, below |
| A frame generated by a structure, or a chunk loading | No scan and no door. The ring works: a signet opens it |
| A neighbour changing, an obstruction cleared, a connection property changing | No scan |

`oldState.is(this)` is what stops a connection change scanning: updating a neighbour's booleans goes through
`onPlace` too. Nothing scans from the neighbours of the placed block, so a ring costs one scan per block placed,
and the last one finds it. A scan from a block in open air, the worst case, is 8 starts of 4 runs of 21, about 700
block reads. A start that is not open returns on its first read.

An opening that is not all air when the ring completes gets no door. Nothing sees it clear later: a break
anywhere would have to look for a frame, which puts a scan on the mining path, and `neighborChanged` on the frame
fires for every neighbour of every ring block and misses a cell mid-opening. So the door is presentation only.
The signet's scan accepts air, so an empty valid ring opens with no door, and placing any frame block of the ring
again seals it. The same covers a ring from before this change, a generated one, and the player who pillars up
inside a ring to place the top bar.

**Threading (rule 1).** All of this runs on the server thread and reads no `SavedData`. The generator never reaches
it, and no `ChunkEvent.Load` listener scans. The scan's `View` answers `OTHER` when `!level.hasChunkAt(x, z)`, so
that it never loads a chunk synchronously from `onPlace`. `VaultFrameTint` runs on the section build thread and
reads only the `BlockAndTintGetter` it is given.

### The door is its own block

`VaultDoorBlock`, one block, not one per tier: `HORIZONTAL_AXIS` and an `IntegerProperty` `part` 0 to 12, 26 states,
hardness -1, blast resistance 3,600,000, `PushReaction.BLOCK`, no loot, a 4 px shape for outline and collision. It
does not implement `Portal`. A shared block would need its collision, light, `entityInside` and portal behaviour
each switched on a state, for one swap.

`DoorPart` is a pure enum in `core.frame`. `StringRepresentable` cannot be imported there, so the property holds an
explicit `DoorPart#id()`, not the ordinal. Those ids are save data from the first door, pinned by a golden unit test.

**`View` and the tags.** The door is not in `orevault:vault_portals`: §3.2 says everything that recognises a portal
block uses that tag, and those readers mean something you can travel through. A new tag, `orevault:vault_openings`,
holds `orevault:vault_door` and `#orevault:vault_portals`. The scanner's adapter answers `Kind.PORTAL` for it:

```java
if (!level.hasChunkAt(x, z)) return Kind.OTHER;
if (state.is(ModBlocks.VAULT_FRAME)) return Kind.FRAME;
if (state.isAir()) return Kind.AIR;
return state.is(ModTags.VAULT_OPENINGS) ? Kind.PORTAL : Kind.OTHER;
```

The door and the portals also go in `minecraft:wither_immune` and `minecraft:dragon_immune`.

### Pressing a signet

`SignetItem#useOn`. On a frame, `VaultPortalShape.find`. On a door or portal, a new
`VaultPortalShape.around(view, axis, x, y, z)`, the existing `openingAround` from that cell on the block's own axis.
The press is refused with the failure sound if no shape is found or any filled cell has a different axis: two
crossing rings share a cell, and the other ring's door is not this press's to replace. Otherwise every cell becomes
that tier's portal with flags 18. The frames need nothing.

### Dissolve

A door and a portal behave alike, in `VaultOpenings`. `updateShape` returns the state unchanged and, when an in-plane
neighbour is neither Vault Frame nor `vault_openings`, calls `ticks.scheduleTick(pos, this, 1)`, since `updateShape`
has only a `LevelReader` to write with. `tick` runs `around`; if the opening is still valid it stops. If not, it
collects every connected cell of the same axis, then removes them all with `UPDATE_ALL` so nearby water and redstone
hear it (old PR #108). This collect reads the level directly: a synchronous load beats an unbreakable orphan. A
frame goes grey by itself.

The door cannot be obstructed once placed: nothing replaces it short of a command, a piston refuses it, and
fluid cannot enter because its 4 px shape is full height, so it counts as solid. The portal has no collision, so it
implements `LiquidBlockContainer` with `canPlaceLiquid` false, or water washes it away.

### Save data

Nothing beyond block state. `DoorPart` ids and the door's `axis` are in the chunk palette. The frame's save format
does not change, so a frame placed before this loads as the same state, dormant, and a ring built before this
has no door until a signet or a re-placed block. No `data_version`: no team, player or chunk file changes. A
scheduled dissolve tick is vanilla's and is saved with the chunk.

## Consequences

- Dissolve, reset and return portal tier upgrades need no frame work: the return portal's ring shows its tier
  when its cells change, and a frame pushed out of a ring is grey wherever it lands, with no piston rule.
- The frame's colour exists only on the client. No gametest can see it, so `FrameLight` has the unit cases (each
  of the 14 offsets, a wrong-axis cell, two tiers, none) and the rest is a `runClient` step.
- A tint cannot glow. A lit frame gives no block light and is not full bright, because `light_emission` is fixed per
  model. If Nick wants that, a `lit` property is added then, with no migration, per the palette default above.
- A frame's section build reads 14 more blocks per frame block, once per rebuild.
- A ring that spans a chunk not loaded at completion gets no door until something places a block again.
- `/fill` with `strict`, or any writer with flag 16, can leave a door or portal with no ring. Creative plus sneak
  (#229) clears it.
- Test list: unit `DoorPart` ids and `FrameLight`; gametests for a ring sealing on the last block, a piston
  completing a ring, an obstructed ring getting no door and still opening by signet, a swap, a side break dissolving
  door and portal, a corner break not, two crossing rings refusing, and a shared bar surviving a dissolve of one
  portal; `runClient` for colour, including a corner placed after opening.

## Rejected alternatives

- A `tier` property on the frame (the Designer's): 320 states and it works if `relevantProperties()` returns it. It
  still needs a pass over ring and corners at each open and dissolve, a way to find the ring once its side is gone,
  a shared-block rule that re-derives from neighbours anyway, flags, a piston rule so a lit frame cannot leave its
  ring lit, a write per block for return portal upgrades, and it freezes a property name as save data. All of
  that holds a fact the world already holds.
- A block entity on the frame, or five frame blocks: a block entity per ring block for one colour; five blocks break
  `is(this)` in the connected texture and every check that asks for the frame.
- A door scan in `neighborChanged`, on `ChunkEvent.Load`, or on `BlockEvent.BreakEvent`: above.
- Putting the door in `orevault:vault_portals`: every travel reader would need a door exception.
- An enum property for `part`, with a wrapper enum in `vault`: thirteen lines to repeat a list.
- Dissolving by returning air from `updateShape`, as vanilla does: the first build's cascade left cells standing,
  and PR #108 replaced it with collect then remove, which #229 requires.

## Spec sections changed

§3.2, by the Writer once Nick has said yes to the render: Vault Frame Block (colour, dormant to lit, not stored),
Scanner (door on completion, signet on door or frame, the door's tag), a new Sealed Door block, Ore Vault Portal
Block (`updateShape` schedules; `LiquidBlockContainer`; the immune tags). `DESIGN_LOG.md`: the row "Each frame needs
its own tier state" is superseded, with this record's reason. #229's criteria change with it, and `VaultPortalShape`'s
Javadoc names the tag.

## Reviews

Matrix row "System design before code": developer (buildable), reviewer (rules), qa (testable), with the Writer for
the registry and lang entries. Not yet reviewed.
