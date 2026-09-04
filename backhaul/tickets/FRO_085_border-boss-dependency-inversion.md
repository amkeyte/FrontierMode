---
id: FRO_085
uid: FRO
number: 85
client: FrontierMode
status: open
title: BorderCommandHandler now imports BossAPI -- pendingAttach write inverts Boss-never-reaches-back-into-Border
  rule
context: 'FRO_082''s own literal spec (pathGrow marks the new border pendingAttach
  on BossFixture) requires BorderCommandHandler to import and call BossAPI.CRUD(level).addPendingAttach(...)
  -- built exactly as specified, flagged here rather than silently reworked. This
  is the one place Border now depends on Boss, inverting the border.md/FRO_075 rule
  (''Boss depends on Border, never the reverse''). Architect call: accept as a named,
  documented exception, or redesign (e.g. an event Boss listens for) so pendingAttach
  bookkeeping doesn''t require Border to know about Boss.'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

BorderCommandHandler now imports BossAPI -- pendingAttach write inverts Boss-never-reaches-back-into-Border rule

## Log

- 2026-09-03: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
