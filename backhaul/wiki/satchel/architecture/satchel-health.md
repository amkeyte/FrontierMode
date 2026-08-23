---
id: satchel/architecture/satchel-health
category: satchel/architecture
slug: satchel-health
title: SatchelHealth
summary: Satchel's run-monitoring / self-verification home -- live regression checks
  per jig kind, checked every real client/server run rather than gated behind gradle
  test.
keywords: null
status: draft
updated: '2026-08-23'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# SatchelHealth

`common/tracking/SatchelHealth.java` is the home for Satchel's run-monitoring and self-
verification modules: live checks that a major service each jig kind provides actually still
works, run every time a real client/server session runs rather than gated behind `gradle test`.
That's a deliberate trade against build-time (JUnit/GameTest) coverage: it reaches a live entity
resolution path JUnit-without-Forge can't, at the cost of only catching a regression on a session
where someone actually connects a client, not on every commit.

Absorbs what used to be separate, single-purpose tracking modules (`TrackingModule`,
`PlayerTrackingModule`, and the now-retired `MobTrackingModule`) into one home, rather than one
throwaway diagnostic class per jig kind. Currently covers `MobJig` only; `LevelJig`/`PlayerJig`
health checks are still their own separate classes, unabsorbed.

## Mechanism

No new raw Forge touch points -- everything rides `ScopeEvent`, the same mechanism every other
tracking module already uses.

A `LevelJigConfig` registered `BOTH` (`COORDINATOR_JIG`) is the always-on coordinator: it gets a
real `ScopeEvent.Tick` on both sides for the overworld regardless of whether anything is scoped
under `MobJig` yet, which `MobJig` itself has no side-agnostic entry point to provide (its own
`reconcile()` only runs once something is already registered). Server-side, on the overworld
`LevelJig` scope loading, the coordinator finds or spawns one tagged, harmless canary (a vanilla `Bat`,
referenced only via `EntityType.BAT` and handled as a plain `Mob` -- the class never imports or
type-names `Bat` itself, so swapping the species later is a one-line change) near world
spawn (`setNoAi`/`setInvulnerable`/`setPersistenceRequired`, deliberately never killable or
wandering off) and registers it through the normal interest surface
(`SatchelHealth.watch`/`unwatch`). Client-side, throttled on the coordinator's own tick, it scans
for the same tagged canary by custom name and calls `MobScope.getFor()` on it directly -- the
documented fast-path attachment -- to get a client-side `MobScope` onto `MobJig`'s machinery
without needing a client-side interest-registration mechanism (that's `MobEntityLookup`'s job,
see [Jig & Scope Runtime](runtime.md#mobjig)).

The check itself lives in the `MOB_JIG` config's own `ScopeEvent.Unloaded` handler: a `MobScope`
whose backing `Mob.isRemoved()` is still `false` at teardown time has been torn down for a reason
other than the two `MobJig`'s reason-agnostic contract allows (chunk unload, genuine removal --
both set `isRemoved()` true by construction). That's a direct violation, not an inference from log
lines, and it throws -- unconditionally today, on both sides. No dev/production gate exists yet to
make that safe to ship live; building one is its own future scope, not attempted here.

## Known limitation

Entity tracking to a given client requires that client's player be within tracking range of the
canary, not merely that its chunk is server-loaded. Standing near world spawn while connected is
what actually exercises the client-side half -- the coordinator can't force that from server-side
alone without a second mechanism (forced chunk-loading does not imply per-player entity tracking).

## `MobJig`'s `sideApplicability`

`SatchelHealth`'s `MobJigConfig` registers `BOTH`, not `SERVER` -- this is what actually exercises
`MobJig`'s client-side reconciliation path at all, which nothing in either repo previously did
(see [Jig & Scope Runtime](runtime.md#mobjig) for the poll mechanism this exercises, and
[MobScope.getFor() Contract](../spec/mobscope-getfor.md) for the fast-path attachment the
client-side canary discovery uses).

## Related pages

- [Jig & Scope Runtime](runtime.md) -- the poll/reconciliation machinery this module exercises
- [MobScope.getFor() Contract](../spec/mobscope-getfor.md)
- [Satchel mod summary](../satchel.md)
