---
id: FRO_078
uid: FRO
number: 78
client: FrontierMode
status: open
title: Add BorderMath to BorderAPI surface
context: '[Donna_02] Route BorderMath''s geometry ops through BorderAPI''s surface.
  FRO_074#5'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Add BorderMath to BorderAPI surface

Split from [FRO_074](FRO_074_cartographer-findings.md#5-add-bordermath-to-borderapis-surface-for-consistency----per-project-owner)
finding 5. `BorderMath` (stateless geometry helpers -- containment, distance,
`randomPointInAnnulus`) is the one thing a cross-module consumer (`DefaultBossRules`) reaches in
the Border package without going through `BorderAPI` -- everything else Boss touches in Border
has a facade entry point.

**Per project owner (stated decision, no open ruling question):** route `BorderMath`'s
operations through `BorderAPI`'s own surface, so every cross-module touch point -- stateful or
not -- goes through the one facade consistently.

**For Lead Dev to decide at implementation time:**
- Whether `BorderAPI` wraps/delegates to the existing `BorderMath` methods, or `BorderMath`
  moves under `BorderAPI` outright.
- Whether `BorderMath` stays public afterward or drops to package-private once nothing external
  calls it directly.

## Log

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 5 for scheduling; stated decision, implementation-shape left to Lead Dev. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
