# E3-S13 (#401) door swing preview

Stylised oblique mock-ups built from the shipped door, frame and portal textures (`vault_door_*`, `vault_frame_*`, `vault_portal`, frame 0 at alpha 196). Not in-game screenshots. Proposed, not decided, until Nick says yes.

## Questions and recommended answers

- Sizes: one door that scales by structure (two leaves, a strap about every 5 rows, one seat above the bottom strap, as `DoorPart` already does); never stretched, never tiled.
- Swing: each leaf hinges on its jamb and swings 90 degrees towards the viewer in 10 ticks with an ease-out; swinging away hides the leaves behind the portal, so the swing is lost.
- Through the open door: the portal strip in its tier colour, with the far side faintly behind it.
- Behind a closed door, from either side: the door itself on both faces (the back shows the mirrored tiles, `DoorPart#back`); the portal is hidden from both sides.
- Open distance: within 6 blocks of the nearest door cell, or half the width + 2 if that is more (12.5 at 21 wide), closing 2 blocks further out; worked out and drawn by each client for its own player only, nothing sent or stored.
- Entry: the sealed door stays solid as shipped; the door drawn over a lit portal has no collision and is open before you reach it; the 80-tick charge and Tier 3 instant travel are unchanged.
- Tints while closed: the frame runes in the tier colour (T1 runes #148031) and a new tier-coloured signet gem in the seat; open, the portal strip (T1 #0D7026, T2 #316CFF, T3 #A038D0, T4 #FF8000). A sealed door has grey runes and an empty seat.
- Return portal: no door; the player arrives 3 blocks from it, inside the open distance, so it would only ever be seen open, and it is built lit, so it is never sealed.

## Files

- `01_states_5x7.png`: closed, mid-swing, open, and swinging away for comparison (Tier 2)
- `02_sizes_closed.png`, `03_sizes_open.png`: 2x3, 5x7 and 21x21 at the same scale
- `04_tiers.png`, `05_tiers_greyscale.png`: sealed and T1 to T4, closed and open (4x5)
- `06_plan_distance_and_sides.png`: open distance, two players, client-side only, entry
- `07_swing_5x7_t3.gif`: the swing, open and close
- `08_return_portal.png`: return portal without a door
- `render.py`: the script that made them (`py -3.12 render.py <repo root>`, needs Pillow)
