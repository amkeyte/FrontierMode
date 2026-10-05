# FRO_217 closing notes — DRAFT (nothing written to the board or tickets)

Curtis, 2026-10-03. All nine builds are written, compile, and pass `./gradlew build` including the new unit
tests (`BorderWaveRadiusTest`, `LadderMathTest`, `StrikerMathTest`, `KeeperLootTablesTest`, `SitingMathTest`).
**None has been played.** The owner waived the items-1-3 checkpoint; every playtest item is on
[FRO_221](../backhaul/tickets/FRO_221_playtest3.md) (#1-#45). Per FRO_186's rule a ticket closes after its item
passes and the build is reviewed, so these are **status notes, not closures** — nothing here should go to `done`
until the owner's results are in.

## Where each ticket stands

| # | Ticket | Built | Playtest items | Close when |
|---|---|---|---|---|
| 1 | FRO_209 Boss bar on milestone Keepers | yes | #1-3 | #1-3 pass |
| 2 | FRO_210 Guardian → Sentinel | yes | #4-5 | #4-5 pass; Douglas's page pass already done |
| 3 | FRO_068 Ambient mob difficulty | yes | #6-11 | #6-11 pass |
| 4 | FRO_208 Keeper stat tables | yes | #12-19 | #12-19 pass |
| 5 | FRO_215 Allies and Keeper credit | yes | #20-25 | #20-25 pass |
| 6 | FRO_211 Diorite Striker | yes, owner's textures in | #26-31 | #26-31 pass |
| 7 | FRO_213 Keeper loot chart | yes | #32-37 | #32-37 pass |
| 8 | FRO_212 Starting site search | yes, timed in the log | #38-42 | #38-42 pass; paste the `[Siting]` lines |
| 9 | FRO_219 Move mob touches to MobData | yes | #43-45 | #43-45 pass |

Roadmap nodes these feed (Walter's to move): RM_FRO_043 (068), RM_FRO_044 (208), RM_FRO_045 (209 + 213),
RM_FRO_046 (211), RM_FRO_047 (212), RM_FRO_049 (215); RM_FRO_048 (Tier 3) gathers 043/044/045/049.

## Per-ticket closing text (paste into each ticket's log when its playtest passes)

- **FRO_209** — Milestone Keepers (the last Border of a growth wave) show the health bar; the gate is
  `BossAPI.isMilestone` (`waveStep(pathPosition) == borderWaveLength`), `bossBarMinHealth` is gone,
  `BossRecord.pathPosition` is persisted, `levelOf(layer)` became `pathPositionOf(record)`. Ride-along:
  `PresentBosses.forEach` shared by the bar and defense handlers.
- **FRO_210** — guardian → Sentinel across `FrontierMode/src` (classes, keys, ids, methods, 12 Tunables, save
  keys, log tag, Inspect line); vanilla `Guardian` untouched (no references existed). Tunables reset; seed rows
  renamed; stale `guardian*` rows linger in old live CSVs.
- **FRO_068** — new `difficulty` module: ordinary and Sentinel rungs on `frontiermode:ladder`, damage scaled at
  `LivingHurtSignal`, `applyStatScaling`/`applySentinelStatScaling` deleted.
- **FRO_208** — five TuningTables on `DefaultBossRules`, body chosen once at record creation, Keeper rung from the
  tables, `keeperScale` replaces `scaleForLayer`, Evoker body (its vexes on the Keeper-summon rung), Ravager out.
- **FRO_215** — ally rung (golems, tamed wolves), allies never target a live Keeper, Sentinels attack allies
  first (`SentinelAllyTargetGoal`), credit rule (`keeperPlayerShare` 0.5 of max health, tallied from
  `LivingDamageSignal`).
- **FRO_211** — `diorite` module: dust, shard (loot-table override), Striker (spark colour/count from Difficulty
  and wave step, grey sputter in the Wilds, explosion off the Overworld).
- **FRO_213** — 12 vanilla-format loot tables under `data/frontiermode/loot_tables/keeper/`; `KeeperLootTables`
  picks them, `KeeperLoot.pay` rolls them; old diamond payout and imports removed.
- **FRO_212** — `siting` module: shared terrain checks moved out of Boss, `ringCandidates`, starting-site search
  on `CreateWorldSpawnSignal` with bonus chest and a try/catch fallback to vanilla.
- **FRO_219** — Feral stat scaling on the `feral` rung, Feral follow range on `MobData.setFollowRange`, Death
  Touch on `LivingHurtSignal`; the Boss/Sentinel sites were already covered by 068/208.

## Hand-offs

**Douglas (all logged on the tickets; none changes behaviour):**
1. `keeperVexDamage` lives in `DefaultDifficultyRules`, not `DefaultBossRules` (FRO_208). The Zombie's growth past
   20 is the `keeper-bodies` `sizeGrowth` column, not a `keeperZombieSizeGrowth` Tunable. `keeper-size`'s seed is
   the owner's authored sizes. `keeperPlayerShare` is declared (FRO_215).
2. `BorderRules.borderWaveLength()` added beside `waveStep` (FRO_209); border.md may want the line.
3. Retuned `borderWaveLength`: only milestones at path positions 5/10/15/20 have their own loot table, any other
   milestone rolls `milestone_late` (FRO_213).
4. A throw inside a `CreateWorldSpawnSignal` handler fails world creation, so the starting-site handler catches it
   and leaves vanilla's spawn alone (FRO_212). The stone test is "natural stone within 4 blocks below ground".
5. Keepers stood on higher-Difficulty ground briefly carried the ordinary rung between FRO_068 and FRO_208;
   FRO_208 closed that gap (Keepers claim their rung before they join).
6. forge-integration.md: the `AdminToolsModule` `LivingHurtEvent` row can go (FRO_219).
7. Sentinel records into MobData is FRO_218, still yours to spec; it is now buildable after that.

**Angela (Satchel files):** seed rows for the new Tunables are not in `satchel-tunables.csv` (they fill from live
runs): the six ladder growth Tunables plus `keeperHealthGrowth`/`keeperDamageGrowth`/`keeperVexDamage`,
`keeperPlayerShare`, `sentinelScaleStep`, the four `strikerSpark…`/`strikerBlast…` ones and the eight
`startingSite…` ones. The seed also still holds two rows with no code behind them: `sentinelHealthScale` (its code was
deleted in FRO_068) and `sentinelTierCount` (renamed from `guardianTierCount` in FRO_210, never used).

**Jeff:** FRO_216 (tuning-table copy-back) is what lets the owner's hand edits to the live Keeper tables ship in
the jar; until then edits live only in `run*/config/satchel-tables/frontiermode/`.

**Owner:** the playtest sheet FRO_221. All three diorite textures and the Evoker eyes are done; `ravager.png` is
unused and can be deleted. World-creation time on the water-world seed is the one number worth reporting back.

## Things to watch in the first playtest

- Wolf taming: the ally rung applies just before vanilla finishes taming, which may reset a wolf's health to 20
  below its scaled maximum (#20 asks which).
- The starting-site log is INFO-chatty on purpose; trim it to one line once creation time is known to be fine.
- Any world made before FRO_210/208 is not valid (save keys, Tunables and the Keeper body field changed).
