# T3 steps owed by E3-S6a (#237)

These are steps for E3-S7's (#253) T3 script to take in. A gametest could not check them: a fake player cannot die,
respawn through `PlayerList#respawn` or log out. Nothing below has been played yet.

## Death inside a Vault, then a relog (old #108)

The first build left players on a death screen that never cleared, or standing invulnerable and sideways after it.
That was downstream of a Vault that did not exist at login, while `ServerPlayer#isInvulnerableTo` returned true for as
long as `isChangingDimension()` held (commit 9e9d8ae). E3-S6a restores Vaults at start, clears `in_vault` at respawn,
and runs the login check before the first tick.

Setup: survival, Allow Commands on, debug commands on, a Vault entered once by portal, so you have a return position.

1. Go into your Vault through the portal. Note the Overworld spot you left from.
2. Die inside the Vault: `/kill @s`, or jump off a pillar onto stone.
   Pass: the death screen shows the Respawn button within a few seconds.
3. Respawn.
   Pass: you stand at the Overworld spawn or your bed, the screen is normal, and you take damage from a fall or a hit
   (you are not invulnerable). Fail: the death screen comes back, the view is tilted, or nothing can hurt you.
4. Log out and log back in.
   Pass: you are where you respawned, not moved home and not in the Vault. The server log has no line saying you were
   "sent to their return position" for this login.
5. Go into the Vault again, log out inside it, and log back in.
   Pass: you are where you logged out, inside the Vault.
6. Note anything odd about the screen in any step, even if it cleared by itself.
