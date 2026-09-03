---
id: FRO_083
uid: FRO
number: 83
client: FrontierMode
status: open
title: Boss defeat cascade gating build
context: '[Susan_02] Lead Dev build for FRO_064''s ruling -- borderId-gated cascade,
  on-path + last-sibling check. Depends on FRO_082.'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [FRO_064](FRO_064_boss-defeat-cascade-grows-border-level-r.md)'s ruling --
Architect's ruling written onto
[boss.md](../wiki/frontiermode/architecture/boss.md#cascade-gating-on-path-and-last-one-standing)'s
new "Cascade gating: on-path, and last one standing" section (plus a fix to the `getInitial()`
bullet above it, and removal of a stale "Event wiring for death-driven border growth" section that
described a `BossDeathEvent`/`BordersTriggers` design that was never actually built). This ticket
is the build itself, same split as
[FRO_058](FRO_058_boss-mutation-validation-reconciliation.md) -> [FRO_060](FRO_060_boss-border-mutation-validation-build.md)
and [FRO_063](FRO_063_boss-can-a-path-layer-legitimately-be-bo.md) -> [FRO_082](FRO_082_boss-attach-build.md).

**Depends on [FRO_082](FRO_082_boss-attach-build.md)** -- both tickets need `Optional<UUID>
borderId` on `BossRecord`; FRO_082 is the one building it. Sequence this after FRO_082 lands.

## What to build

Per boss.md's "Cascade gating: on-path, and last one standing" section, no new scoping decisions --
straight implementation:

1. **`BossModule.onLivingDeath()`** (Karen's `LivingDeathEvent` handler): after `markDefeated`, and
   before calling `BorderAPI.grow(level, deathLocation)`, check the defeated record's `borderId`.
   Empty -> stop, no `grow`, no `createBoss`. Non-empty -> check whether any other `BossRecord`
   sharing that `borderId` still has `alive: true`; if so, stop. Only proceed to `grow` +
   `createBoss` once this was the last living record on that `borderId`.
2. **`BossAPI.forceDefeat(Level, UUID)`** gets the identical check, in the same place in its own
   cascade (after the existing FRO_058 already-defeated guard, before `BorderAPI.grow`).
3. **No change needed to `BossAPI.removeBoss()` (`/boss delete`)** -- it already never reaches
   `grow`/`createBoss` (see "Despawn and record removal" step 3), so this gating doesn't touch it.
4. **No change needed to the bootstrap call site** (`BossModule`'s own `ScopeEvent.Loaded` handler,
   per FRO_075) -- it never goes through a defeat, so it was never in scope for this gating to begin
   with; boss.md's updated `getInitial()` bullet says so explicitly now.

**Not touched:** `/boss attach` and `pendingAttach` (FRO_082's own scope); the reverse-direction
reconciliation check (still deferred, per boss.md's "Known gaps" -- a `layer`-only check remains
imprecise even with `borderId` gating built, since that's a different question: "does this record's
border still exist," not "should this defeat gate growth").

## Standing constraint

Same as every ticket this pass: no Gradle in the agent sandbox. Whatever gets built here needs
real build/playtest evidence before closing.

## Log

- 2026-09-03: Ticket opened. Split from FRO_064's ruling for scheduling; carries the Architect's
  ruling verbatim. Parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02"), same
  home as FRO_082 and the rest of this session's found-along-the-way Boss items. Sequenced after
  FRO_082 -- both share the `borderId` field.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
