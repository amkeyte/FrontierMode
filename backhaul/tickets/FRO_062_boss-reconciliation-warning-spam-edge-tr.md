---
id: FRO_062
uid: FRO
number: 62
client: FrontierMode
status: done
title: Boss reconciliation warning spam (edge-triggered logging)
context: Boss reconciliation warning spam fixed via edge-triggered logging (was firing
  every tick).
priority: normal
opened: '2026-08-29'
closed: '2026-09-01'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Boss reconciliation warning spam (edge-triggered logging)

## Log

- 2026-08-29: **Found and fixed same-session, live, given the severity.** Project owner reported
  a server console spamming an identical `[Boss] Reconciliation: ...` warning dozens of times per
  second during FRO_060's own `/boss transform defeat @all` playtest pass -- all 12 path layers
  flagged as missing a boss record. Root cause: `reconcilePathAgainstBossRecords()` runs on
  `BOSS_JIG`'s `ScopeEvent.Tick` (every server tick) and called `OUT.warn()` unconditionally
  whenever `missing` was non-empty, with no de-duplication against the previous tick's own
  identical result -- a persistent mismatch (which per `boss.md`'s own design is never
  self-healed) warned forever, once per tick, until resolved or the server restarted.

  Fix: `BossModule` now keeps a per-level `LAST_RECONCILIATION_MISMATCH` map and only logs when
  this tick's mismatch set differs from the last one actually reported for that level -- a new
  mismatch, a changed one, or a resolution (logged at `.info`, once, when the set goes back to
  empty). Still "logged loudly" per `boss.md`'s own ruling (nothing here silently self-heals),
  just not logged on an infinite loop for an unchanged state.

  **Separately, the underlying mismatch itself (all 12 layers) is very likely explained by the
  project owner's own testing this session** -- `/boss delete`/`/boss transform defeat @all`
  runs during FRO_060's playtest pass plausibly cleared every boss record on this world without
  clearing the borders that already progressed past them. Not confirmed; asked the project owner
  directly rather than assumed. If confirmed test-debris, no further action needed on the data
  itself (the check did its job -- it's diagnostic-only, doesn't break the border/path system);
  if it turns out unexplained by testing, that would point at a real regression worth its own
  investigation, separate from this ticket's logging-hygiene fix.

  Not build-verified -- same standing constraint as every ticket this pass.

- 2026-08-29: **Root cause confirmed by the project owner:** yes, all bosses were deleted via
  `/boss delete` during this session's own playtest cleanup -- test debris, not a regression.
  Nothing further needed on the data itself. The design question this surfaced (whether a path
  layer can legitimately be boss-less, and an eventual attach/detach mechanism) is real but
  separate from this ticket's own logging-hygiene scope -- tracked on
  [FRO_063](FRO_063_boss-can-a-path-layer-legitimately-be-bo.md) for a future project owner +
  Architect conversation.
- 2026-09-01: Closed -- project owner confirms the fix is holding, no further reconciliation warning spam.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
