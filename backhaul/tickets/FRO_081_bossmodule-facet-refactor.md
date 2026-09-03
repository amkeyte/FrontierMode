---
id: FRO_081
uid: FRO
number: 81
client: FrontierMode
status: open
title: BossModule facet refactor
context: '[Donna_02] Extract BossModule into Crud/Rules/Info facets, mirroring BordersFixture.
  FRO_074#8'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

BossModule facet refactor

Split from [FRO_074](FRO_074_cartographer-findings.md#8-bossmodule-has-become-a-grab-bag----find-real-homes-for-most-of-what-s-hanging-off-it)
finding 8. `BossModule` has grown to 707 lines / 18 methods spanning at least seven separate
concerns as static methods on one class (two independent jig lifecycles, the `BOSS_JIG` tick
pipeline, defensive reconciliation, interest-registry bookkeeping, mob-scope attach handling, the
defeat-detection cascade, and an admin escape hatch) -- the same shape of concern the original
`BorderModule` signal-flow diagram raised, which Boss has now grown into as well.

**Architect's ruling (FRO_074#8):** extract state-management logic into `BossFixture` facets,
mirroring `BordersFixture`'s `BordersCrudFacet`/`BordersRulesFacet`/`BordersInfoFacet` pattern:

- **`BossCrudFacet`** -- record lifecycle/mutations: `create(layer)`, `get(bossId)`,
  `materialize(bossId, entityId)`, `finalizePosition(bossId, pos)`, `markDefeated(bossId)`,
  `remove(bossId)`.
- **`BossRulesFacet`** -- pluggable strategy: `BossRules`/`DefaultBossRules`, exposed as an
  explicit facet for consistency; position validation (flatness, hazard scoring).
- **`BossInfoFacet`** -- queries/diagnostics: `isAlive(bossId)`, `getAllAlive()`, reconciliation
  diagnostics (`reconcilePathAgainstBossRecords()`), interest-registry bookkeeping, log-friendly
  debug accessors.

`BossModule` becomes a thin orchestration layer: jig registration stays as wiring (not
orchestration), and event handlers (`onBordersScopeLoaded`, `onBossJigTick`,
`onBossMobScopeLoaded`/`Unloaded`, `onLivingDeath`) delegate their actual work to the new facets
rather than doing it inline.

**Sequencing: do not action before [FRO_075](FRO_075_bootstrap-ownership.md) (finding 1) lands.**
Once FRO_075 moves the bootstrap listener into `BossModule`, that call site feeds directly into
the new `BossCrudFacet`, making the new structure immediately useful rather than adding one more
static method to the class this ticket is shrinking.

## Log

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 8 for scheduling; carries the Architect's facet-design ruling verbatim. Sequencing dependency on FRO_075 landing first noted above -- not yet actionable. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
