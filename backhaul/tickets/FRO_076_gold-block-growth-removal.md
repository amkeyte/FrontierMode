---
id: FRO_076
uid: FRO
number: 76
client: FrontierMode
status: open
title: Remove gold-block path growth
context: '[Donna_02] Remove gold-block path-growth trigger, superseded by boss-defeat
  growth. FRO_074#3'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Remove gold-block path growth

Split from [FRO_074](FRO_074_cartographer-findings.md#3-deprecate-gold-block-placement-path-growth-entirely----superseded-by-boss-defeat-growth)
finding 3. `border-vocabulary.md`'s own "Path" section already called the gold-block-placement
growth trigger (`BorderModule.onBlockPlaced` -> `BordersTriggers.growPath` ->
`DefaultBorderRules.growPathCriteria`) a "debug-shaped stand-in," retiring once boss-defeat
growth (`BossModule.onLivingDeath` -> `BorderAPI.grow(level, deathLocation)`) landed for real --
that condition is now met. **Per project owner: remove the feature entirely**, not deprioritize
it.

**Removal surface** (naming what's reachable from this trigger and nothing else -- not
prescribing the fix):

- `MinecraftForge.EVENT_BUS.addListener(...)` call in `BorderModule.init()`
- `BorderModule.onBlockPlaced`
- `BordersTriggers.growPath`
- `growPathCriteria` on both `BorderRules` (interface) and `DefaultBorderRules` (impl)
- `GROWTH_RING_RADIUS`, if nothing else reads it once the above is gone

**Also needs:** `border-vocabulary.md`'s own "Aspirational vs. actual" paragraph about this
trigger updated alongside the removal -- Architect/wiki-owner territory, not Lead Dev's to edit,
flagging here so it isn't missed.

## Log

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 3 for scheduling; stated project-owner decision, no open question. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
