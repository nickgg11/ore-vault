# Libraries in FTB Evolution 2 that Ore Vault could lean on

Scanned 2026-10-26. Source: `C:\Users\itsni\AppData\Local\.ftba\instances\ftb evolution 2\mods`, 370 jars. I read
`META-INF/neoforge.mods.toml`, the bundled licence files and the class layout of the 100 jars that looked like
libraries, APIs or relevant content mods. Everything here is NeoForge for MC 26.1.x. Versions and licences
are what the jars declare.

Ore Vault already hard-depends on FTB Teams, which itself requires FTB Library, so anything from FTB Library
costs nothing extra for players.

## Recommended

| Mod (modId) | Version | Licence | Dependency type | What it replaces in Ore Vault |
| --- | --- | --- | --- | --- |
| FTB Library (`ftblibrary`) | 26.1.2.9 | All Rights Reserved | Already required, through FTB Teams | The Tome of the Deep Seam GUI (§8). It ships a full widget toolkit: `BaseScreen`, `Panel`, `Button`, `ScrollBar`, `PanelScrollBar`, `ContextMenu`, `ModalPanel`, `DropDownMenu`, tooltips, icons and a theme. FTB Quests draws its pannable quest graph with it, which is the same problem as a skill-tree canvas. Also has `NetworkHelper` for payloads and SNBT config with an in-game editor and server-to-client sync. |
| FTB Teams API (`ftbteams`) | 26.1.2.4 | All Rights Reserved | Already required | Custom team properties (`TeamProperty`, registered through `CollectTeamPropertiesEvent`, synced to clients automatically). Small team-wide values the Tome's top bar needs, such as Resonance level and unspent points, could ride on this instead of a hand-written sync packet. Keep `SavedData` as the source of truth for progression; properties are a display mirror. Events cover team created, deleted, player joined or left, which §3.1 and §9 already need. |
| FTB Ultimine API (`ftbultimine`) | 26.1.2.6 | All Rights Reserved | `compileOnly`, soft | Already planned. Worth noting the 26.1 API is now registration events: `RegisterBlockBreakHandlerEvent`, `RegisterRestrictionHandlerEvent`, `RegisterShapeEvent`, plus `CanUltimineResult`. That is the hook for the Volatile Veins once-per-operation roll and the Broad Cut cluster. It requires FTB Library too. |
| Jade API (`jade`) | 26.1.12 | CC-BY-NC-SA-4.0 (the API is meant for plugins) | `compileOnly`, soft | An optional plugin (`IWailaPlugin`, `IBlockComponentProvider`) that shows an ore's rarity class or vein state when looking at it inside the Vault. Saves building a custom HUD overlay for that. Polish, not core. |
| JEI (`jei`) | 29.47.0.111 | MIT | `compileOnly`, soft | Info pages for the Vault Key tiers and the Resonance Crystal gate. Optional polish. |

## Worth a look, not recommended as dependencies

| Mod (modId) | Version | Licence | Why not, or what to take from it |
| --- | --- | --- | --- |
| Pufferfish's Skills (`puffish_skills`) | 0.19.2 | All Rights Reserved | Already under consideration in the rebuild thread. It is data-driven (JSON categories, skills, connections, an expression-based XP curve, `exclusive` connections) and has a public API for custom rewards (`RewardFactory`) and XP sources (`ExperienceSourceFactory`). The mismatch is that skill state is per player (`server/data/PlayerData`); its only team feature is sharing XP within a distance (`ExperienceTeamSharingConfig`). Ore Vault's tree is owned by the FTB team, so every unlock would have to be mirrored onto every member, including members who were offline. It also has no notion of fork parents with free options, pacts, growth nodes going Outgrown, or anchors gated on points spent, and the Tome layout rules in §8 and DESIGN_LOG (clustered spine, stained-paper frames per class) would be fighting its canvas. Using its reward and XP-source API as a soft integration is reasonable; building the core tree on it is not. |
| Pufferfish's Attributes (`puffish_attributes`) | 0.8.2 | LGPL-3.0 | Adds `fortune`, `pickaxe_speed`, `mining_speed`, `breaking_speed` and others as real attributes, which would let speed and fortune nodes be attribute modifiers. A hard dependency for that breaks "modpack-aware but not modpack-dependent" (§1). Vanilla 26.1 already has `block_break_speed` and `mining_efficiency`, which cover the speed nodes. |
| Apothic Attributes (`apothic_attributes`) + Placebo | 3.1.0 / 10.0.2 | MIT | Same idea as above, heavier, pulls Placebo with it. Same objection. |
| Modonomicon (`modonomicon`) | 2.17.1 | MIT and CC-BY-4.0 | Data-driven guidebook with a node-graph category view and unlock conditions. Could carry the player-facing explanation of the tree, but the Tome is the spec's UI and should not be split across two books. Patchouli (26.1-94, CC BY-NC-SA 3.0) and GuideME (26.1.15-beta) are the same answer. |
| Configured (`configured`) | 2.7.5 | LGPL-3.0 | Config GUI on top of NeoForge config. NeoForge 26.1 already gives `orevault-server.toml` an in-game screen and `worldRestart()` warnings (§10), so nothing to gain. Same for Cloth Config 26.1.154 (LGPL), YACL 3.9.7 (LGPL), Fzzy Config 0.7.7 (custom TDL-M licence, Kotlin), MezzConfig 0.6.8 (MIT), Jupiter 2.4.3 (LGPL), ExoConfig 0.3.6 (LGPL), EclipseUI 1.0.4 (MIT). |
| Just Another Mining Dimension (`jamd`) | 26.1.1.1 | All Rights Reserved, FTB/CurseForge redistribution only | The spec's layer stack is already "jamd-style". Useful to read for behaviour, not to depend on or copy from, given the licence. |
| Lithostitched (`lithostitched`) | 1.8.1 | MIT | Worldgen config and compatibility library. Ore Vault uses its own `ChunkGenerator` and does not use placed features, so there is nothing for it to hook. |
| GeckoLib (`geckolib`) | 5.5.2 | MIT | Animated models. The orb deliberately reuses the vanilla XP-orb sprite and the portal is a tinted plane, so no use today. |
| Scannable Reforged (`scannable`) | 1.0.1 | MIT | Has a scanner API (`li/cil/scannable/api/scanning`). A Seismic Sense or Vein Sight soft integration could register a module, but that is a post-1.0 nicety. |

## Skipped

Multi-loader abstraction layers and author-specific cores add a dependency without covering anything Ore
Vault needs: Architectury 20.1.17, Balm, Puzzles Lib, Framework, Collective, Cyclops Core, Titanium, Cucumber,
Iceberg, Konkrete, PolyLib, LimaCore, MINT Lib, Iron's Lib, ShatterLib, Glodium, Deimos, Trenzalore,
SuperMartijn642's libs, Nanite Library, Iskandert Library (its team and stage code overlaps FTB Teams),
BBL Core, CakeStickLib, Sophisticated Core, XyCraft Core, Kotlin for Forge, KubeJS, Curios, TerraBlender,
YUNG's API, Moog's Structure Lib. Several are All Rights Reserved besides.

Two name collisions to keep in mind: the pack contains a mod called **Resonance** (`resonance`, amethyst
content) and one called **Animus: NeoVitae** (`animusnv`). Neither clashes with the `orevault` namespace,
but translation keys, item names and tooltips that say just "Resonance" or "Animus" will read ambiguously in
this pack. Prefixing them in lang (for example "Vault Resonance") is cheap.

## Net effect

The real saving is FTB Library's GUI toolkit for the Tome, at zero dependency cost, plus FTB Teams
properties for client sync. Everything else either duplicates what NeoForge 26.1 already does or would make
Ore Vault depend on mods the spec says it must not need.
