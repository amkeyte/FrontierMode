---
id: FRO_082
uid: FRO
number: 82
client: FrontierMode
status: open
title: 'Boss-less path layers: pendingAttach + /boss attach build'
context: '[Susan_02] Lead Dev build for FRO_063''s ruling -- borderId, pendingAttach,
  /boss attach, reconciliation update.'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [FRO_063](FRO_063_boss-can-a-path-layer-legitimately-be-bo.md)'s ruling --
Architect's ruling written onto
[boss.md](../wiki/frontiermode/architecture/boss.md#boss-less-path-layers-and-attach)'s
"Boss-less path layers and attach" section and
[boss-commands.md](../wiki/frontiermode/architecture/boss-commands.md)'s command tree/selector
scheme. This ticket is the build itself, same split as
[FRO_058](FRO_058_boss-mutation-validation-reconciliation.md) -> [FRO_060](FRO_060_boss-border-mutation-validation-build.md)
and [FRO_074](FRO_074_cartographer-findings.md) -> FRO_075..FRO_081.

## What to build

Per boss.md's "Boss-less path layers and attach" section, no new scoping decisions -- straight
implementation:

1. **`Optional<UUID> borderId` on `BossRecord`/`BossFixture`.** `empty()` for a hand-placed
   off-path boss; set once at creation for anything paired with real border-growth (bootstrap
   grow, defeat-triggered grow, and the new `attach` command below). Per
   [boss-commands.md](../wiki/frontiermode/architecture/boss-commands.md)'s existing "Selector
   scheme" ruling -- this ticket is what makes building it necessary now.
2. **`Set<UUID> pendingAttach` on `BossFixture`** -- a persisted collection of border ids, separate
   from the boss-record list (it tracks borders with *no* record).
3. **`BorderCommandHandler.pathGrow()`** adds the newly-grown border's id to `pendingAttach` on
   success.
4. **`BossAPI.removeBoss()`** (backing `/boss delete`) adds the removed record's `borderId` (if
   present and still on-path) to `pendingAttach`, as its own new step after the existing
   despawn/delete/no-growth-event steps.
5. **New `/boss attach <border-selector>` command** (`BorderCommandHandler`/`BossCommands`,
   nesting `BorderSelectorArgumentType` the same way `@border` in the selector scheme already
   does) -- creates a boss record via `BossCrudFacet.create(border.layer())` (or today's
   `BossFixture.create(int layer)` if FRO_081's facet refactor hasn't landed yet), sets its
   `borderId` to the selected border, and removes that border's id from `pendingAttach`.
6. **`BossModule.reconcilePathAgainstBossRecords()`** checks `pendingAttach` before logging a
   path-layer-with-no-boss gap: a gap covered by `pendingAttach` logs at a lighter level (info,
   "awaiting attach") instead of the existing loud "real data bug" warning.

**Not touched:** the reverse-direction reconciliation check (still deferred, per boss.md's "Known
gaps" -- unrelated to this ticket's forward-direction fix); `/boss transform defeat`'s cascade
(unaffected by `pendingAttach`, since a `transform defeat` never removes a record).

## Standing constraint

Same as every ticket this pass: no Gradle in the agent sandbox. Whatever gets built here needs
real build/playtest evidence before closing.

## Log

- 2026-09-03: Ticket opened. Split from FRO_063's ruling for scheduling; carries the Architect's
  ruling verbatim. Parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02"), same
  home as the rest of this session's found-along-the-way Boss items.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
