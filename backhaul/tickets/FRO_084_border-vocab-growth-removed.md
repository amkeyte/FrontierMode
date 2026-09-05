---
id: FRO_084
uid: FRO
number: 84
client: FrontierMode
status: done
title: 'border-vocabulary.md: update gold-block growth as removed, not aspirational'
context: '[Donna_02] FRO_076 removed gold-block growth; border-vocabulary.md still
  calls it aspirational, not removed. Wiki/architecture is Architect-owned, flagging
  not editing.'
priority: normal
opened: '2026-09-03'
closed: '2026-09-05'
---

<!-- board:start -->
<!-- board:end -->

## Summary

border-vocabulary.md: update gold-block growth as removed, not aspirational

## Log

- 2026-09-03: Ticket opened.
- 2026-09-04: Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02") -- traces to FRO_076, one of that container's own items. PM audit found it unparked.
- 2026-09-05: **Fixed.** border-vocabulary.md's "Aspirational vs. actual" paragraph rewritten -- gold-block growth's removal (FRO_076) and boss-defeat growth's live deathLocation-centered mechanism confirmed against current source (`BossModule.onLivingDeath` -> `BorderAPI.grow(level, deathLocation)`), plus the deliberate manual-path-grow-stays-boss-less exception (FRO_048/FRO_063/FRO_082) noted so the page doesn't overclaim. `updated` bumped to 2026-09-05.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
