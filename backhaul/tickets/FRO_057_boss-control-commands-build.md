---
id: FRO_057
uid: FRO
number: 57
client: FrontierMode
status: done
title: Boss control commands build
context: Lead Dev build ticket for RM_FRO_022 (Joyce) -- scope finalized on FRO_056
  after a full Architect design pass (wiki/frontiermode/architecture/boss-commands.md).
priority: normal
opened: '2026-08-29'
closed: '2026-08-29'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build ticket for [RM_FRO_022](../roadmap/RM_FRO_022_joyce.md) ("Joyce"). Scoping and
design lives on [FRO_056](FRO_056_boss-control-commands.md) — this ticket is the build itself and
the record of what actually landed, same split as [FRO_044](FRO_044_karen-prep.md) →
[FRO_045](FRO_045_karen-build.md) for Karen. Full design space (wider than this build) is on
[Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md).

**Readiness checked before opening this ticket:** [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)
("Karen") is `resolved` with real playtest evidence; [RM_FRO_022](../roadmap/RM_FRO_022_joyce.md)'s
own log already confirms neither [RM_FRO_020](../roadmap/RM_FRO_020_susan-01.md) ("Susan_01","
`resolved`, nothing outstanding) nor [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02",
four open tickets, all Border-side) hold anything this build's six items actually touch. The two
open design notes logged on Susan_02 this same pass (multi-boss cardinality, border-move
reconciliation) are explicitly flagged non-blocking to this build on both that node's own log and
the wiki page's "Open questions" section. `depends_on` stays Karen only.

## What to build

Six items, per [FRO_056](FRO_056_boss-control-commands.md)'s finalized scope — selector scope
deliberately minimal (bare position / `@nearest` / `@all` / `@none` only, no `borderId` field, no
new `BorderSelectorResult` modes needed this pass), permission flat
`.requires(src -> src.hasPermission(2))`, same as `Border`:

1. `info <selector>` — full dump of matched boss(es); default selector `@all`, matching Border's
   own default. Backed by existing `BossFixture.all()`/`get()`.
2. `add <pos> <layer>` — plant a boss record; `<pos>` accepts `here`, same as Border's own
   `add here`. Backed by existing `BossFixture.create()`.
3. `delete <selector>` — delete a record outright. Net-new: `BossFixture.remove(UUID)` doesn't
   exist yet — the one new fixture-level method this build needs.
4. `mob spawn <selector>` — force materialization now, regardless of chunk-loaded state. A real
   new mechanism: exposes what's currently a private tick-handler concern.
5. `transform defeat <selector>` — force `markDefeated` without combat; the item that actually
   motivated this whole node, from Karen's own playtest session. Backed by existing
   `BossFixture.markDefeated(UUID)`.
6. `debug goto <selector>` — teleport to a boss's stored position, spawned or not. Touches the
   player, not `BossFixture`.

**New classes, `Border`'s registration/handler split:** `BossCommands` (Brigadier registration) +
`BossCommandHandler` (handler logic), per [FRO_056](FRO_056_boss-control-commands.md)'s cited
precedent (`BorderCommands`/`BorderCommandHandler`).

**Out of scope for this pass** (documented design space on the wiki page, not this build):
`@id`/`@name`/`@border` selector modes, `@status` filter, `transform fastforward`/`reset`,
`mob respawn`/`damage`/`heal`, `debug validate`/`tick`/`simulate-death`/`setmob`/`dump`, the
border-move reconciliation handler, and the multi-boss cardinality question.

Done bar: compiles clean, all six commands registered and reachable in-game, `transform defeat`
verified against a real boss record (the one item this whole node exists for), real playtest
evidence per this project's standing convention — not closed on code-review alone.

## Standing constraint

**No Gradle in the agent sandbox.** Real build/playtest happens on the project owner's own
machine — confirm via a real `build.log` and/or in-game session evidence before closing, same as
every other Lead Dev ticket this project.

## Log

- 2026-08-29: Ticket opened. Readiness verified (Karen resolved, Susan_01/02 checked clean, both
  new design notes on Susan_02 confirmed non-blocking) before opening. "What to build" copied
  forward from FRO_056's finalized six-item scope. Not yet built.

- 2026-08-29: **Two build-scope decisions confirmed with the project owner before writing code:**
  (1) `transform defeat` runs the full defeat -> grow -> next-boss cascade (via new
  `BossAPI.forceDefeat(Level, UUID)`, mirroring `BossModule.onLivingDeath`'s own shape but keyed
  off a selector-resolved id and the record's stored `position()` rather than a live mob) -- not
  a bare `markDefeated` flag-flip, so the command reproduces the real "boss defeated" effect on
  demand. (2) `mob spawn` on an already-materialized target is a clean no-op, not an error.

- 2026-08-29: **Code written** (device-bridge session, no Gradle in the agent sandbox -- not yet
  compiled or playtested; both owed before this ticket can close, per the standing constraint
  below). New: `BossSelectorResult`/`BossSelector`/`BossSelectorArgumentType(Info)`
  (`boss/server/commands/`, four modes -- bare position/`@nearest`/`@all`/`@none`, no
  `@id`/`@name`/`@border`, no `borderId` on `BossRecord`, per this ticket's own selector scoping),
  `BossCommands`/`BossCommandHandler` (same package, Border's registration/handler split),
  `BossDisplay` (`boss/common/fixture/`, presentation-only, mirrors `BorderDisplay`). Modified:
  `BossFixture.remove(UUID)` (the one new fixture method this build needed); `BossAPI
  .forceDefeat(Level, UUID)` (the defeat cascade above); `BossModule.forceMaterialize(ServerLevel,
  UUID)` (force-loads the target chunk synchronously, then reuses the exact same
  `BossRules.materialize`/fixture-write/interest-registration steps
  `materializeUnresolvedBosses` already uses -- one materialization codepath, not two) and
  `BossModule.onRegisterCommands` (registers `/boss`, same delegation shape
  `BorderModule.onRegisterCommands` already has); `FrontierMode.java` (registers
  `BOSS_SELECTOR`'s `ArgumentTypeInfo` and calls `BossModule.onRegisterCommands` alongside
  `BorderModule`'s, mirroring `BORDER_SELECTOR`'s wiring exactly). `BossModule.onLivingDeath`
  (Karen's own already-verified path) deliberately left untouched -- `forceDefeat` duplicates its
  ~10-line cascade rather than refactoring shared code out of tested code outside this ticket's
  scope.

  One line flagged for the build-verification pass: `debugGoto` calls
  `ServerPlayer.teleportTo(double, double, double)` -- believed correct for 1.20.1/Forge but not
  compiler-checked in this session; first thing to look at if the build fails there.

- 2026-08-29: **Playtest evidence, five of six confirmed via real client chat log**: `info`,
  `add here`, `delete`, `debug goto`, and `transform defeat` all fired with exactly the expected
  feedback text -- `transform defeat` specifically showed the full cascade ("defeated -- border
  grown, next boss ... queued"), confirming the build-scope decision above landed correctly, not
  a bare flag flip. `mob spawn` remained unconfirmed after several attempts: one run was rejected
  by Brigadier as incomplete (no selector supplied -- expected, `mob spawn` has no bare-selector
  default unlike `info`), and a separate attempt to manufacture an unmaterialized-and-unloaded
  test target via `add <pos> <layer>` at a remote coordinate hit vanilla's own
  `BlockPosArgument.getLoadedBlockPos` rejection ("That position is not loaded") -- inherited
  directly from `/border add`'s own explicit-position branch, not a Boss-specific bug. `add`
  cannot place a record at an unloaded position at all; the real path to an unmaterialized,
  unloaded record is `transform defeat`'s own random next-boss position.

- 2026-08-29: **Two playtest-driven fixes, both in `mob spawn`'s and `info`'s feedback, not its
  underlying mutation logic:** (1) `BossModule.forceMaterialize` now returns a
  `MaterializeOutcome` enum (`SPAWNED` / `ALREADY_MATERIALIZED` / `NO_RECORD` / `DECLINED`)
  instead of a collapsed boolean, and `BossCommandHandler.mobSpawn` switches on it for a distinct
  chat line per case -- the single generic "could not force-spawn" message was masking which of
  three unrelated situations was actually happening. Also added two temporary `OUT.info` lines
  (chunk-force and spawn-success) on that path for an extra server-log trail during this
  verification pass -- fine to drop to `.debug` once confirmed. (2) `/boss info` now shows each
  record's list index (`BossDisplay.fullInfo` gained a `listIndex` param) -- the bare-position
  selector mode was otherwise unusable without counting list order by hand.

- 2026-08-29: **Added `/boss debug distance <selector>`, requested mid-playtest, not part of
  FRO_057's original six and not yet on
  [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md).** Reports Euclidean
  distance from the command source to each resolved boss's stored position (same position
  `debug goto` teleports to) -- makes locating an unmaterialized/unloaded target practical without
  teleporting blind. Small, presentation-only, no data-model change; flagged here rather than
  editing the wiki page directly, per this role's standing rule -- worth a short Architect note on
  that page once this pass closes.

- 2026-08-29: **Real bug found and fixed via chat-log evidence:** `transform defeat`'s success
  message reported `BorderAPI.grow`'s `Result.border().id()` labeled as "next boss X" -- that's
  the newly grown *border's* UUID, not the new *boss's* UUID, and a live test caught the
  mismatch directly (`/boss info` showed the real new boss at a different id than the chat line
  had just named). Fixed by giving `BossAPI.forceDefeat` a proper `DefeatOutcome` return
  (`borderResult` + `nextBoss: Optional<BossRecord>`) instead of a bare `Result`, so
  `BossCommandHandler.transformDefeat` reports the border id and the boss id as the two distinct
  things they are. Same playtest also confirmed, via real chat evidence, that `mob spawn`'s new
  `ALREADY_MATERIALIZED` outcome message fires correctly, and that `debug distance` reports a
  sane value (688.3 blocks). `mob spawn`'s `SPAWNED` (force-load) outcome still hasn't fired in
  any session so far -- every boss encountered by the tester has so far landed in already-loaded
  territory by the time it's checked. Also, separately: quieted `SatchelHealth`'s ambient
  LOADED/UNLOADED/TICK logging from INFO to DEBUG (Satchel repo, outside this ticket's scope,
  logged only as a note here since it surfaced during this same playtest session).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->

- 2026-08-29: TICK logging in Satchel's SatchelHealth.java (mob + player, both already
  downgraded to `.debug` earlier this session) commented out entirely -- still reaching the
  dev console because `forge.logging.console.level=debug` in both build.gradle files, and OUT
  funnels every call site through one shared logger (`com.arryn.satchel.common.util.out.OUT`),
  so there's no per-class filter to silence it with instead. Out of FRO_057's scope (Satchel
  repo, not FrontierMode) same as the earlier downgrade. Noted for the Architect: OUT's
  single-shared-logger design blocks any future per-class log filtering; may be worth its own
  ticket if that's wanted later.

- 2026-08-29: `mob spawn`'s SPAWNED (force-chunk-load) outcome confirmed via real playtest --
  the last unconfirmed item on this ticket's done bar. Boss `a6a746d1-84e3-48d2-8f6d-a559ed7b8e4c`
  (layer 10, pos 3,66,-208) came back from `transform defeat` far enough out to land genuinely
  unloaded (`materialized=false` on the immediate follow-up `info`). `mob spawn` on that id
  produced both expected server log lines (`forcing chunk load at BlockPos{x=3, y=66, z=-208}`
  -> `spawned bossId=... entity=1e16c61a... at BlockPos{x=3, y=64, z=-208}`), no MobScope
  warning, and the matching client chat line ("Force-spawned boss a6a746d1..."). All six
  `/boss` commands (info, add, delete, mob spawn, transform defeat, debug goto/distance) now
  have confirmed real-playtest evidence. Ready for Architect/QA review.

- 2026-08-29: **Architect reconciliation done.** [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md)
  updated with a new "What's actually built" section (the six shipped items, `debug distance`,
  the two confirmed behavioral decisions, the DefeatOutcome bug fix, the list-index/MaterializeOutcome
  additions), the Command tree marked `[BUILT]` where real, "Existing server-side surface"
  extended with the new methods, and "What's real vs. net-new" updated to match -- explicit that
  `borderId`/`@id`/`@name`/`@border` remain entirely unbuilt proposal, this pass didn't touch
  them. [boss.md](../wiki/frontiermode/architecture/boss.md)'s "Defeat detection and the
  border-growth gap" caller list gained `forceDefeat` as a real command-triggered sibling to the
  combat-triggered cascade; page bumped to `verified`.
