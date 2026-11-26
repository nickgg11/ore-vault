# Two dev clients against one dev server

E5-S10 (#250). Team scaling (§4.2), the orb paying only its own team, the shared pin and `boughtBy` (§8 Tab 1,
"Team") and the reset vote (§3.5) all need two players in one team. These steps run two temporary `runClient`
instances with their own usernames against one `runServer`, all on Nick's PC, so no second person is needed (Nick,
Q10). They were run once on 2026-11-25 (see "Checked" below).

Nothing here changes the repo's build. The usernames and game folders come from a Gradle init script kept outside
the repo and passed with `-I` only on these runs.

## One-time setup

1. `run/server/eula.txt` holds `eula=true`, and `run/server/server.properties` has `online-mode=false` (dev
   clients have no Mojang account). Both files are untracked.
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
3. To op both players, write `run/server/ops.json` while the server is stopped. Offline UUIDs follow from the name,
   so these stay the same:

   ```json
   [
     { "uuid": "2a717c8f-118e-3474-bc2d-d61b924015ff", "name": "Dev1", "level": 4, "bypassesPlayerLimit": false },
     { "uuid": "21cc3e25-481e-3cfa-8770-c5693a3e93a4", "name": "Dev2", "level": 4, "bypassesPlayerLimit": false }
   ]
   ```

## Each session

Run each command in its own terminal, from the repo root. Add `-Pruntime_profile=...` to all three if the session
needs Ultimine or the materials mod.

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
4. Put both in one team. Each player starts in a single-member team (§2), so as Dev1 run
   `/ftbteams party create test`, then `/ftbteams party invite Dev2`; as Dev2 run `/ftbteams party join test`.
   Each player can be in only one party, so to test two teams, skip this step.
5. Switch between the windows with Alt+Tab. A window that loses focus pauses its menus but stays connected.
6. To finish, quit both clients from their menus, then type `stop` in the server terminal so the world saves. If the
   terminal does not take input, press Ctrl+C there instead; the dev world may lose the last few seconds.

## Checked

Run on Nick's PC on 2026-11-25 at `main` 04b026a. The server log showed `Dev1 joined the game` and
`Dev2 joined the game` 15 seconds apart, each with its own offline UUID, and the clients logged
`Game directory: ...\run\client-Dev1` and `...\run\client-Dev2`. Steps 4 and 5 were not played: no person was at
the keyboard. The team commands come from FTB Teams 26.1.2.4's `PartyTeamCommand` (`create`, `invite`, `join`), and
were not typed in game.
