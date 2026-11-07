"""Writes the E3-S11 spike's restart-check lists, one pair per variant order, beside this file.

Apply docs/spikes/e3-s11/e3-s11-unload-spike.patch (git apply) and run restart-check.ps1 with -First, -Second and
-Between spike-between.ps1 (ADR 0002, "Result so far"). Outside every source set; nothing here ships.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
A = "0000000a-0000-4000-8000-00000000000a"
B = "0000000b-0000-4000-8000-00000000000b"
KA = "orevault:vault_0000000a00004000800000000000000a"
KB = "orevault:vault_0000000b00004000800000000000000b"
TYPE = {"base": "orevault:ore_vault", "expanded": "orevault:ore_vault_expanded"}
# A block that is bedrock in that type and not in the other.
FLOOR = {"base": "0 0 0", "expanded": "0 -64 0"}


def esc(s):
    return s.replace(".", r"\.").replace("(", r"\(").replace(")", r"\)")


def first(old, new):
    return f"""# E3-S11 (#245) spike, lifetime 1: {old} Vault unloaded, deleted, made again as {new}.
run orevault vault create {A} {old}
expect type={esc(TYPE[old])}$
run orevault vault create {B} base
expect type=orevault:ore_vault$
# Generate and edit chunks in A, one of them far out (region r.1.1) and force-loaded, so check 4 has a file to look for.
run execute in {KA} run forceload add 600 600
expect Marked chunk
run execute in {KA} run setblock 600 251 600 minecraft:gold_block
expect Changed the block
run execute in {KA} run orevault break 0 250 0
expect gone=true
log \\[orevault-debug\\] tick {esc(KA)} server_tick=
log \\[orevault-debug\\] tick {esc(KB)} server_tick=
run save-all flush
expect Saved the game
# Removal steps 1 to 5, the drain advanced once a tick (check 6).
run orevault vault unload {A}
expect unloading
log Unloaded Vault {esc(KA)} of team {A} \\(spike\\): drain \\d+ ticks
# The old level wrote its far chunk, so the check after the stop has a file to miss.
run orevault vault folder {A}
expect exists=true deleting=false far_region=true
# Check 2: the folder moves and deletes at once, straight after the removal line, with no wait.
run orevault vault delete {A}
expect deleted
run orevault vault folder {A}
expect exists=false deleting=false far_region=false
# Check 1, after the delete: sixty seconds on, no tick line for A after its removal, B still ticking, nothing thrown.
sleep 60
nolog (?s)Unloaded Vault {esc(KA)}.*tick {esc(KA)} server_tick
log (?s)Unloaded Vault {esc(KA)}.*tick {esc(KB)} server_tick
nolog (?s)Unloading Vault {esc(KA)}.*(/ERROR\\]|Exception)
# Check 4, first half: a full save writes nothing back into the deleted folder.
run save-all flush
expect Saved the game
run orevault vault folder {A}
expect exists=false deleting=false far_region=false
# Check 3: a new level under the same key, from a fresh stem, under the other type.
run orevault vault create {A} {new}
expect type={esc(TYPE[new])}$
log Registered Vault {esc(KA)} \\({esc(TYPE[new])},
run execute in {KA} run forceload add 0 0
expect Marked chunk
run execute in {KA} if block {FLOOR[new]} minecraft:bedrock
expect Test passed
# Check 5, headless form: a command in the key reaches the new level and breaks a block in it.
run execute in {KA} run orevault break 0 250 0
expect gone=true
run execute in {KA} if block 600 251 600 minecraft:gold_block
expect Test failed|That position is not loaded
log (?s)Registered Vault {esc(KA)} \\({esc(TYPE[new])},.*tick {esc(KA)} server_tick=
nolog (?s)Unloading Vault {esc(KA)}.*(/ERROR\\]|Exception)
"""


def second(new):
    return f"""# E3-S11 (#245) spike, lifetime 2: the remade Vault restores under its new type with its new terrain.
log Registered Vault {esc(KA)} \\({esc(TYPE[new])},
run orevault vault create {A}
expect type={esc(TYPE[new])}$
run execute in {KA} if block {FLOOR[new]} minecraft:bedrock
expect Test passed
run execute in {KA} if block 0 250 0 minecraft:air
expect Test passed
run execute in {KA} run orevault break 0 249 0
expect gone=true
log \\[orevault-debug\\] tick {esc(KA)} server_tick=
log \\[orevault-debug\\] tick {esc(KB)} server_tick=
nolog Exception
"""


for old, new in (("base", "expanded"), ("expanded", "base")):
    with open(os.path.join(HERE, f"spike-{old}-first.txt"), "w", newline="\n") as f:
        f.write(first(old, new))
    with open(os.path.join(HERE, f"spike-{old}-second.txt"), "w", newline="\n") as f:
        f.write(second(new))
print("written")
