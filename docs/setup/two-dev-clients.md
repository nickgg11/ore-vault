# Two dev clients against one dev server

E5-S10 (#250). Team scaling (§4.2), the orb paying only its own team, the shared pin and `boughtBy` (§8 Tab 1,
"Team") and the reset vote (§3.5) all need two players in one team. These steps run two temporary `runClient`
instances with their own usernames against one `runServer`, all on Nick's PC, so no second person is needed (Nick,
Q10). They were run once on 2026-11-25 (see "Checked" below).

Nothing here changes the repo's build. The usernames and game folders come from a Gradle init script kept outside
the repo and passed with `-I` only on these runs.

## One-time setup

1. Run `./gradlew runServer` once so it writes `run/server/eula.txt` and `run/server/server.properties`, then set
   `eula=true` in the first and `online-mode=false` in the second (dev clients have no Mojang account). Both files
   are untracked.
2. Save the init script to your Gradle folder, outside the repo:

   ```powershell
   @'
   // Temporary dev clients for Ore Vault (E5-S10, #250). Not part of the repo's build.
   // ./gradlew -I <this file> runClient -PdevUser=Dev2 [-PdevJoin=localhost:25565]
   allprojects {
       def user = findProperty('devUser')
       if (user == null) return
       def join = findProperty('devJoin') ?: 'localhost:25565'
       def gameDir = project.layout.projectDirectory.dir('run').dir("client-${user}").asFile
       tasks.matching { it.name == 'runClient' }.configureEach {
           doFirst { gameDir.mkdirs() }
           args '--username', user.toString(), '--gameDir', gameDir.absolutePath, '--quickPlayMultiplayer', join.toString()
       }
   }
   '@ | Set-Content -Encoding utf8 "$env:USERPROFILE\.gradle\orevault-devclient.gradle"
   ```

   Without `-PdevUser` the script does nothing, so a plain `runClient` is unchanged. Each user gets its own game
   folder, `run/client-Dev1`, `run/client-Dev2` and so on (already ignored with the rest of `run/`), so options,
   logs and screenshots do not collide. `--gameDir` is used because setting the task's working directory from an
   init script does not take: NeoGradle sets it again later.
3. To op both players, add these two entries to the list in `run/server/ops.json` while the server is stopped,
   keeping any entries already there. Offline UUIDs follow from the name, so these stay the same:

   ```json
   [
     { "uuid": "2a717c8f-118e-3474-bc2d-d61b924015ff", "name": "Dev1", "level": 4, "bypassesPlayerLimit": false },
     { "uuid": "21cc3e25-481e-3cfa-8770-c5693a3e93a4", "name": "Dev2", "level": 4, "bypassesPlayerLimit": false }
   ]
   ```

## Each session

Run each command in its own terminal, from the repo root. Add `-Pruntime_profile=...` to all three if the session
needs Ultimine or the materials mod. This is three game JVMs and their Gradle daemons at once; close other heavy
programs first. Each client is a full game, so a third one is the first thing to drop if the PC struggles.

1. Start the server and wait for `Done (` in its output:

   ```powershell
   ./gradlew runServer
   ```

2. Start the first client. It joins `localhost:25565` by itself:

   ```powershell
   ./gradlew -I "$env:USERPROFILE\.gradle\orevault-devclient.gradle" runClient -PdevUser=Dev1
   ```

3. Start the second client once the first window is up:

   ```powershell
   ./gradlew -I "$env:USERPROFILE\.gradle\orevault-devclient.gradle" runClient -PdevUser=Dev2
   ```

   A third player is the same command with `-PdevUser=Dev3`.
4. Put both in one team. Each player starts in a single-member team (§2). As Dev1 run `/ftbteams party create test`,
   then `/ftbteams party invite Dev2`. As Dev2, click the invite in chat, or type `/ftbteams party join ` and press
   Tab to fill in the team: its name is `test#` followed by eight characters, so `join test` alone is refused.
   Check with `/ftbteams info` that the team lists both players. Each player can be in only one party, so to test
   two teams, skip this step.
5. Switch between the windows with Alt+Tab. The window you leave opens its pause menu, but in multiplayer that player
   stays in the world and can be hurt, so park the idle player somewhere safe before switching.
6. To finish, have Dev1 run `/stop` in game so the world saves, then close both clients. Typing in the server
   terminal does nothing (Gradle gives the server no input), and Ctrl+C there kills it without a save.

## Checked

Run on Nick's PC on 2026-11-25 at `main` 04b026a. The server log showed `Dev1 joined the game` and
`Dev2 joined the game` 15 seconds apart, each with its own offline UUID, and the clients logged
`Game directory: ...\run\client-Dev1` and `...\run\client-Dev2`. Steps 4 to 6 were not played: no person was at
the keyboard. The team commands, and the `test#` short name `join` expects, come from FTB Teams 26.1.2.4's
`PartyTeamCommand` and team classes, and were not typed in game.
