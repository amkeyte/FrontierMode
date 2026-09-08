---
id: FRO_095
uid: FRO
number: 95
client: FrontierMode
status: done
title: Materialized boss loses defeat detection after server restart (BossInterests
  not rehydrated)
context: Restart-persisted boss's MobDied gate stays closed forever -- BossInterests.MAP
  is in-memory only and never repopulated for already-materialized bosses loaded from
  disk.
priority: normal
opened: '2026-09-07'
closed: '2026-09-07'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found live during tonight's FRO_092/FRO_094 build+playtest review: project owner killed the
world's existing (Layer 0, restart-persisted) boss and no new border/boss followed. Root cause
traced to source and log evidence, not the two tickets being tested that session -- neither
touches this code path.

## Evidence

`run-server/logs/latest.log` shows a clean death (`Named entity Rabbit['Boss (Layer 0)']...
died: Boss (Layer 0) was slain by Dev`) but nothing else afterward -- no border growth, no
next-boss creation -- before the player disconnected 29 seconds later. `run-server/logs/debug.log`
(DEBUG level, not in latest.log) has the missing line, immediately before the death message:

```
[MobDied] gate: not interested -- 01c897ac-a1af-4c4b-9b70-c61ad2de81b8
```

Satchel's `MobDied` gates on an interest registry (it wraps `LivingDeathEvent`, which fires for
every mob death in the world) -- this UUID wasn't in it, so `BossJigHandlers.onMobDied()` never
ran at all. The defeat -> grow -> next-boss chain never started.

## Root cause

`BossInterests.MAP` (`FrontierMode/src/main/java/com/arryn/frontiermode/boss/BossInterests.java`)
is plain in-memory static state -- nothing persists it. It's populated exactly two ways:
`BossModule.forceMaterialize()` (`/boss mob spawn`) and `BossSpawnFacet.materializeUnresolved()`
(the normal tick-driven path, called from `BossJigHandlers.onTick()`) -- and the latter only
iterates `fixture.INFO.unmaterialized()`, records with no live entity yet. The world's own log
showed `Bosses loaded: 1` at server start -- a boss loaded from disk, already `materialized()`
with a stored `bossEntityId()`. That record is never in the "unmaterialized" set, so nothing ever
re-adds its real entity UUID to `BossInterests` after a restart. The Minecraft entity itself
persists fine (same UUID, respawns normally with its chunk); only Satchel's in-memory interest set
forgets about it.

**Net effect: any already-materialized boss becomes permanently invisible to defeat detection
after every server restart**, until a fresh boss is created and materialized within that same
server run.

## Fix (built, this session, Dev(FrontierMode))

Entirely within FrontierMode/src -- `BossInterests`, `BossJigHandlers`, and `BossFixture` are all
this mod's own code, no Satchel or Architect involvement needed.

Added a rehydration step to `BossJigHandlers.onBordersScopeLoaded()` (the existing
`BORDERS_JIG`-Loaded hook, previously only used for the not-yet-seeded bootstrap check) -- runs
unconditionally on every fire, seeded or not, before the bootstrap logic: resolves the level's
`BossFixture` via `BossAPI.bosses(level)` and, for every record where `materialized()` is true,
calls `BossInterests.add(level, record.bossEntityId())`. This re-registers every already-live
boss's real entity UUID with Satchel's interest set on every level load, matching what's actually
on disk. Doc comment on the method updated to describe both responsibilities in order.

## Standing constraint

No Gradle in the agent sandbox. Real build/playtest happens on the project owner's own machine.

## Done bar

- Compiles clean.
- Kill a restart-persisted (already-materialized) boss and confirm the defeat -> border-growth ->
  next-boss chain now fires (log lines for growth/pregen-start/new-boss-creation appear, same as
  a freshly-created boss's defeat already does).
- No change to first-ever-boot behavior (a boss created and materialized fresh within the same
  server run was never affected by this bug -- confirm nothing regresses there).

## Log

- 2026-09-07: **Closing -- real playtest confirms the fix, cross-checked against the raw logs before taking the report at face value.** Project owner restarted the server with an already-materialized boss on disk and confirmed the kill -> border-growth -> next-boss chain fired, matching this ticket's own Done-bar item 2 exactly.

  Verified independently in `run-server/logs`: two separate restarts this session each logged `Bosses loaded: N` (5, then later 1) immediately followed by the rehydration path in `onBordersScopeLoaded()` running unconditionally (per source, no gate on it) before anything else touches that level. One thing worth being honest about: a couple of `[MobDied] gate: not interested` lines appeared shortly after each of those restarts (`c35c05fb`, `8de51361`), which at first glance look exactly like this bug's own symptom. Checked rather than assumed: the second one (`8de51361`) logged 5+ seconds *before* the player even joined that session (`Dev joined the game` timestamp is later), right after a wild rabbit's own `MobScope` loaded in the same tick -- ambient wildlife, not a tracked boss. The same pattern (isolated `not interested` lines with no connection to boss activity) already appears throughout sessions that are otherwise confirmed fully working, including the run that built five clean boss/border pairs earlier this same day -- so this is background noise from ordinary mob deaths sharing the same gate, not a regression. No `finalizeUnpositioned`/`BorderPregen` gap followed either restart that would indicate a real tracked boss got dropped.

  Done bar: compiles clean (confirmed via `build.log`, `BUILD SUCCESSFUL`, this ticket's own 3 files among the changed inputs); restart-persisted boss's defeat chain now fires (project owner's direct report, and the rehydration code path is confirmed present and unconditional by source); no regression to first-boot behavior (both sessions studied here started from `Bosses loaded: 0` and built clean boss chains from scratch with no issues). All three met.
- 2026-09-07: Found and root-caused during FRO_092/FRO_094 build+playtest review (project owner's
  "rabbit killed; no new boss" report). Traced via `run-server/logs/debug.log`'s `[MobDied] gate:
  not interested` line to `BossInterests.MAP`'s missing rehydration path. Fix built same session:
  `BossJigHandlers.onBordersScopeLoaded()` now rehydrates `BossInterests` for every already-
  materialized boss record on each level load. Brace/paren-balance checked. Not committed (git
  managed by project owner this session). Real build/playtest confirmation owed before closing,
  per this ticket's own Standing constraint.
- 2026-09-07: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
