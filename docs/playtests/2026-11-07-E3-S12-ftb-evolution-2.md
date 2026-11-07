# FTB Evolution 2 boot smoke test (E3-S12, #246)

First run of the mod inside Nick's pack, at the end of the dimension path (spec checklist line "Load test in
FTB Evolution 2 at the version pinned when the dimension path landed"; `docs/TEAM_REQUIREMENTS.md` Q7). It is
repeated after E9 and in E14-S1 (#382).

```
Date: 2026-11-07
Pack: FTB Evolution 2 1.7.0 (FTB App, versionId 100594), Minecraft 26.1.2, NeoForge 26.1.2.126
Pack's FTB mods: FTB Teams 26.1.2.5, FTB Library 26.1.2.11, FTB Ultimine 26.1.2.6
Jar: orevault-0.1.0.jar built from main at 1dcb577 (PR #409 merged); no main-source change since
Instance: a copy of Nick's instance without saves, backups or logs, named "FTB Evolution 2 (Ore Vault test)",
  427 mods with ours; Nick's own instance was not touched
Played by: Nick, single player, new creative world
```

## Pinned version

FTB Evolution 2 **1.7.0** is the pinned pack version (Q7). It is re-checked at release in E14-S1.

The pack runs newer patch versions than the mod builds against (`gradle.properties`, ADR 0001 pin table):
NeoForge 26.1.2.126 against our 26.1.2.121, and FTB Teams 26.1.2.5 against our 26.1.2.4. Both loaded with no
error, so no bump is needed now. A bump would be its own PR (CLAUDE.md, Build).

## What was done

Nick launched the copy, made a world, built a frame ring, pressed a worn signet on the door, walked into his
Vault and quit.

## Log

`logs/latest.log` of the copy, 9,681 lines: 204 lines at ERROR, 2,868 at WARN. No ERROR or WARN line and no stack
frame names `com.orevault` or `orevault@`. The mod's own lines, all INFO:

```
[17:29:05.671] [Server thread/INFO] [com.orevault.orevault.vault.VaultDimensions/]: Restored 0 Vaults, skipped 0
[17:31:20.932] [Server thread/INFO] [com.orevault.orevault.vault.VaultDimensions/]: Registered Vault orevault:vault_b5a3a7e5115341eb8512c1fcf132d4f3 (orevault:ore_vault, Y=0..319) for team b5a3a7e5-1153-41eb-8512-c1fcf132d4f3 at server tick 2021
[17:31:21.215] [Server thread/INFO] [com.orevault.orevault.vault.ReturnPortals/]: Return portal of orevault:vault_b5a3a7e5115341eb8512c1fcf132d4f3 at Y=251, tier 1: wrote 6 cells and 14 frames
```

The Vault level then saved with the others at quit (`Saving chunks for level ... orevault:vault_b5a3...`).

The 204 ERROR lines by logger. Each names another mod's content:

| Count | Logger | Example |
| --- | --- | --- |
| 170 | Modonomicon | `Checked visibility for an entry with null book (entry: 'animusnv:compatibility/magi')` |
| 18 | `RecipeManager` and `SimpleJsonResourceReloadListener` | `Error parsing recipe replication:matter_values/c/tags/nuts: Missing tag: 'c:nuts'` |
| 5 | Selling Bin | `Selling Bin Processor selling_bin:quality_foods_processor is not registered!` |
| 11 | JEI timing (2), FTB Library entity icons (2), CreativeCore (2), Configured (2), fzzy_config, Cyclops Core, KubeJS | `Received an invalid recipe request ... integrateddynamics:crafting/proto_chorus` |

Outside ERROR there are 113 lines of a `FileSystemNotFoundException` stack after `Failed to load config screen
metadata for mod id: stepcrafter`, and an Anima `NullPointerException ... "this.spearItem" is null`.

Nick's own instance log is from 2026-10-20 and an older pack version, so it is no exact baseline. It has the
same 112 `FileSystemNotFoundException` and the Anima `spearItem` error.

## Found in play: Traveler's Titles shows "???"

On entering the Vault, Traveler's Titles (in the pack) showed "???" as the dimension title, beside our own
"entered the Vault" message.

Cause, from its bytecode (`TitleRenderManager#updateDimensionTitle`, Traveler's Titles 26.1.2-6.1.0): the title
is the translation key `travelerstitles.<namespace>.<path>` of the dimension id, and when `Language#has` is false
it draws the literal `???`. A Vault's id is `orevault:vault_<team id>` (`VaultKeys#dimensionPath`), so the key is different for
every team and no lang file can hold it. The mod's "Blacklisted Dimensions" config is a list of exact ids, so a
pack cannot hide it either. Not a crash and not a blocker for this test; ticketed as #414.

## Result

Pass. The pack boots with the mod, a Vault is created, entered and saved, and the log has no error from the mod.
