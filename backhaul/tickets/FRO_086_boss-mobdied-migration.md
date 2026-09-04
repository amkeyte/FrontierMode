---
id: FRO_086
uid: FRO
number: 86
client: FrontierMode
status: closed
title: Migrate Boss defeat detection to signals
context: '[Donna_02] Migrate Boss''s defeat detection + BOSS_MOB_JIG onto the new
  Satchel signals (SAT_044).'
priority: normal
opened: '2026-09-03'
closed: '2026-09-04'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Split from [SAT_044](../tickets/SAT_044_mob-lifecycle-signals-build.md) (Satchel's Mob Lifecycle
Signals build) -- that ticket originally bundled this FrontierMode-side consumer migration
alongside its own Satchel-side signal-primitive build. Pulled into its own ticket since it's a
different client's code (`BossModule`/`BossMobFixture`), not because the scope itself changed.

**Depends on [SAT_044](../tickets/SAT_044_mob-lifecycle-signals-build.md) landing first** --
`MobDied`/`MobGainedInterest`/`MobLostInterest` need to exist before anything here can compile
against them.

## What to build

**1. Migrate `BossModule`'s defeat detection.** Its `LivingDeathEvent` handler
([RM_FRO_019](../roadmap/RM_FRO_019_karen.md) "Karen") moves off the raw `@SubscribeEvent` and
onto `EventHandlers.on(MobDied.class, ...)`. Internal cascade logic (`markDefeated`, the
`borderId`-gated grow cascade from
[FRO_064](FRO_064_boss-defeat-cascade-grows-border-level-r.md)/[FRO_083](FRO_083_boss-defeat-cascade-gating-build.md))
is unaffected -- only the trigger mechanism moves. `MobScope.getFor(mob)` is still called
synchronously, now from within the `MobDied` handler rather than directly inside the old raw
listener -- see [MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md)'s updated
wording; the "immediate attachment" guarantee is unchanged (`SatchelEventBus.post()` is
synchronous, same call stack, same tick).

**2. Migrate `BOSS_MOB_JIG`'s attach/release handlers** from `ScopeEvent.Loaded`/`Unloaded` onto
`MobGainedInterest`/`MobLostInterest`.

**Check before starting:** [FRO_083](FRO_083_boss-defeat-cascade-gating-build.md) has since
landed (closed 2026-09-03) and already touched `BossModule`'s defeat-cascade handling with its own
`borderId`-gated logic -- read its diff first so this migration moves the trigger mechanism onto
`MobDied` without clobbering that gating work.

## Standing constraint

Same as every ticket this pass: no Gradle in the agent sandbox. Whatever gets built here needs
real build/playtest evidence before closing.

## Log

- 2026-09-03: Ticket opened. Split from SAT_044's own build scope (see that ticket's log) --
  FrontierMode-side consumer migration, different client than SAT_044's Satchel-side signal
  build. Depends on SAT_044 landing first. Parked on
  [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
- 2026-09-04: **Closed.** Build complete and playtested.
  - **Defeat detection** migrated: `BossModule.onLivingDeath` (raw `@SubscribeEvent`) removed from
    `MinecraftForge.EVENT_BUS`. `EventHandlers.on(MobDied.class, BossModule::onMobDied)` wired
    into `registerBossJig()`. Handler confirmed receiving events for all 3 playtested boss kills;
    defeat cascade (border grow + next boss) fired correctly each time. FRO_083 gating logic
    (`borderId` guard) untouched.
  - **`BOSS_MOB_JIG` attach/release** migrated: `ScopeEvent.Loaded`/`Unloaded` handlers replaced
    with `ScopeEvent.MobGainedInterest`/`MobLostInterest`. Fires confirmed on boss spawn/death.
    Chunk-unload path not directly observed in `runClient` (environment limitation -- integrated
    server doesn't unload chunks at distance); the code path is the same `MobJig.onUnload` that
    fires on death, which is confirmed.
  - **Double-fire** of `MobGainedInterest`/`MobLostInterest` observed: both `BOSS_MOB_JIG` and
    `satchelmobtracker:mob_tracker_jig` (SatchelHealth's) use `MobJig`, so both fire the signals
    when tracking the same UUID. Pre-existing architecture gap; filed as separate ticket for
    Architect review.
  - Diagnostic `OUT.info` log in `onMobDied` entry stripped before close.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
