# Headless restart check (E3-S10, #200)

A gametest lives one server lifetime, so anything that has to survive a restart is checked by
`tools/restart-check/restart-check.ps1`. It starts `./gradlew runServer` headless, runs a command list, stops the
server, optionally runs a hook while it is down, starts it again, runs a second list, stops it, and exits 0 only if
every step passed. It is the gate S14 (`docs/TEAM_REQUIREMENTS.md`) puts on every dimension PR, and the headless
line of ADR 0002's "NeoForge bumps" section.

Run it on Nick's PC, like every other build (`CLAUDE.md`, Build).

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restart-check/restart-check.ps1 `
    -First tools/restart-check/smoke-first.txt -Second tools/restart-check/smoke-second.txt `
    -Between tools/restart-check/smoke-between.ps1
```

`-ExecutionPolicy Bypass` applies to that one process only. Optional parameters: `-Between <script.ps1>` (run
while the server is down, given `-WorldDir`; non-zero fails the run), `-World <folder>` (a folder under
`run/server`; default is a fresh `restart-check-<timestamp>`; name an existing one to run against a prepared
world or to add a third lifetime by running the script again), `-StartTimeoutSec` (300), `-StopTimeoutSec`
(120), `-LogTimeoutSec` (30), `-GamePort` (25598), `-RconPort` (25599).

## How it talks to the server

Console stdin does not reach the server under Gradle's `runServer`, so commands go over RCON on 127.0.0.1 with
a password generated per run. RCON runs commands at permission level 4, like the console, and before anyone has
connected. The script waits for the RCON port: the server opens it after `Done`, so a connection means start-up
finished.

Vanilla's RCON reader takes one packet per socket read and drops the connection if a read holds more
(`RconClient.run`), so the script sends nothing until the previous reply is in. A command is capped at 1440 bytes
for the same reason. Replies of 4096 characters or more come in several packets; the script then sends a packet
of an unknown type and reads until its answer, which comes after the rest. `smoke-first.txt` runs `help`, whose
reply is longer than one packet, so that path runs every time.

## What it changes, and puts back

For the run it edits two files and restores them afterwards, byte for byte:

- `run/server/server.properties`: `level-name` (the run's world), `level-seed`, `server-ip=127.0.0.1`, the two
  ports, `enable-rcon`, `rcon.password`, and `pause-when-empty-seconds=0`. An empty server otherwise stops
  ticking after 60 seconds, which would stall any check that needs time to pass.
- `run/server/config/orevault-server.toml`: `enable_debug_commands = true`, so `/orevault break` exists.

The originals sit in `run/server/restart-check-backup/` during the run. If a run is killed, the next run puts
them back before doing anything else. The dev `world` folder is never opened. Each run's worlds stay in
`run/server/restart-check-<timestamp>/` (about 2 MB each) and logs in `run/server/restart-check-logs/<timestamp>/`:
`transcript.txt`, `gradle-N.out` and `gradle-N.err` for each lifetime, and `server-N.log`, a copy of
`latest.log`. All of `run/` is ignored by git; delete old folders by hand.

## Command lists

One directive per line; blank lines and `#` lines are skipped.

| Directive | Meaning |
| --- | --- |
| `run <command>` | Send the command, no leading `/`. `stop` is refused: the script stops the server. |
| `expect <regex>` | The reply to the last `run` must match. Every `run` needs at least one. |
| `reject <regex>` | The reply to the last `run` must not match. |
| `log <regex>` | `latest.log` of this lifetime matches within `-LogTimeoutSec`. |
| `nolog <regex>` | `latest.log` does not match now. |
| `sleep <seconds>` | Wait. |

Regexes are .NET and case-sensitive. A list that does not parse fails the run before any server starts.

## What fails the run

Exit 1, with `FAIL:` and the reason, on any of: a list that does not parse; a reply Brigadier gives to a command
it could not parse (`Unknown or incomplete command`, `Incorrect argument`, `<--[HERE]`), whatever the list says;
an `expect`, `reject`, `log` or `nolog` miss; `runServer` exiting before RCON is up, or no RCON within
`-StartTimeoutSec`; the server still running `-StopTimeoutSec` after `stop`; a non-zero `runServer` exit; a
lifetime whose log has no `All dimensions are saved` or has a `FATAL` line; a new file in
`run/server/crash-reports`; a hook exiting non-zero; the run's ports already in use; a dev server already on the
`server-port` in `server.properties`, since it shares `run/server`.

## No server left behind

Killing the Gradle client does not stop the server JVM its daemon forked, and a failure during start-up can come
before that JVM exists. On any failure the script kills every process on the run's two ports and every JVM started
during the run whose command line has `--nogui` and this repo's `build/classes/java/main` (only `runServer` passes
`--nogui`, so a client or gametest server is spared). It keeps doing so until the Gradle client has exited, then
restores the files, then checks again and fails the run if anything is still alive. If the script itself is
killed, nothing can clean up: the next run restores the files and then refuses to start, naming the PID on the
port. Stop that PID by hand.

## Taking turns

Do not run it while another Gradle build runs in the same checkout. Two builds at once failed
`:neoFormRecompile` with "Unable to delete directory" on 2026-11-01, and the script reported it as
`runServer exited with 1 before RCON came up`. Check first:

```
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'gradle-wrapper|--nogui' }
```

## The lists in the repo

- `smoke-first.txt`, `smoke-second.txt`, `smoke-between.ps1`: prove the script against what exists today. The
  Nether stands in for a Vault. Lifetime 1 force-loads a chunk, places a gold block, checks it, and unloads the
  ticket. Lifetime 2, with nobody connected, runs `execute in minecraft:the_nether run orevault break 64 100 64`,
  the form the Vault check uses, and expects `destroyBlock=true gone=true` in the reply and in the log, then
  checks the block is air.
- `smoke-fail.txt`: a list that must fail. Pass it as `-First` or `-Second` to see a failing run.

## What waits

- The Vault lists for ADR 0002's NeoForge-bump check: creating a Vault by debug command needs #237 (E3-S6a) and
  the S44 create command; `execute in orevault:vault_<hex>` needs a Vault level; the tick-logger `log` line needs
  the E3-S11 spike's listener (#245). The lists are written with those stories, as
  `run execute in orevault:vault_<hex> run orevault break <x> <y> <z>` plus `expect ... gone=true` and a `log`
  line for the key.
- The damaged-file cases A, B and C in ADR 0005 section 6 (#200's 2026-10-31 comment): `-Between` is the slot
  that overwrites a team or type file while the server is down, and `-World` with the same folder adds the
  third lifetime case A needs. They need a Vault, so they wait for #237 too.
- ADR 0002's restore fixture and the forced-ticket check (QA-6) use the same script with a prepared `-World`.

## Sample run

2026-11-02, HEAD 17715ec. The `help` reply is cut here; the full transcript is in the logs folder.

```
restart-check 20261102-160641, world run/server/restart-check-20261102-160641, HEAD 17715ec
== lifetime 1: starting runServer
   up after 12s, pid 13348
> execute in minecraft:the_nether run forceload add 64 64
< Marked chunk [4, 4] in The Nether to be force loaded

> execute in minecraft:the_nether run setblock 64 100 64 minecraft:gold_block
< Changed the block at 64, 100, 64

> execute in minecraft:the_nether if block 64 100 64 minecraft:gold_block
< Test passed

> execute in minecraft:the_nether run forceload remove 64 64
< Unmarked chunk [4, 4] in The Nether for force loading

  log matched /Done \(/
  log free of /\[orevault-debug\]/
> help
< /advancement (grant|revoke)
[... 100 lines ...]
/orevault (diag|break)

== lifetime 1: stop
   exit 0, final save logged
== server down: E:\Github\Ore-Vault_2\tools\restart-check\smoke-between.ps1
level.dat is in E:\Github\Ore-Vault_2\run\server\restart-check-20261102-160641
== lifetime 2: starting runServer
   up after 12s, pid 15268
> execute in minecraft:the_nether run orevault break 64 100 64
< [orevault-debug] break minecraft:the_nether 64 100 64 minecraft:gold_block: destroyBlock=true gone=true

  log matched /\[orevault-debug\] break minecraft:the_nether 64 100 64/
> execute in minecraft:the_nether run forceload add 64 64
< Marked chunk [4, 4] in The Nether to be force loaded

> execute in minecraft:the_nether if block 64 100 64 minecraft:gold_block
< Test failed

> execute in minecraft:the_nether if block 64 100 64 minecraft:air
< Test passed

== lifetime 2: stop
   exit 0, final save logged
RESTART CHECK PASSED: 12 steps. Logs: E:\Github\Ore-Vault_2\run\server\restart-check-logs\20261102-160641
```

A failing run, `-First smoke-fail.txt`:

```
killing leftover server pid 26232
FAIL: expected /Test passed/ from 'execute in minecraft:the_nether if block 0 300 0 minecraft:gold_block', got: That position is not loaded
RESTART CHECK FAILED after 0 steps passed. Logs: E:\Github\Ore-Vault_2\run\server\restart-check-logs\20261101-233036
```
