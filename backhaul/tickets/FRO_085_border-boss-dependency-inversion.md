---
id: FRO_085
uid: FRO
number: 85
client: FrontierMode
status: open
title: BorderCommandHandler now imports BossAPI -- pendingAttach write inverts Boss-never-reaches-back-into-Border
  rule
context: '[Susan_02] FRO_082''s pendingAttach write inverts Boss-never-reaches-into-Border.
  Architect call: accept as named exception, or redesign via an event. See body.'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

`BorderCommandHandler` imports `BossAPI` to write the `pendingAttach` field introduced in
FRO_082. This inverts the "Boss never reaches back into Border" dependency rule. Architect call
required: accept as a named exception, or redesign the handoff via an event.

## Problem

FRO_082's `pendingAttach` build required `BorderCommandHandler` to call into `BossAPI` to record
that a border is expecting a boss attach. That import crosses the established dependency direction --
Border modules are not supposed to call into Boss modules; the rule exists so Boss can be reasoned
about as a consumer of Border, not a peer or dependency of it.

## Decision needed

Two paths:

1. **Named exception** -- accept this specific import as an acknowledged violation, document it
   on the relevant architecture page, and move on. Low ceremony; the coupling is real but bounded.

2. **Event-based redesign** -- Border fires an event (e.g. `PendingAttachEvent`) that Boss
   listens for, eliminating the direct import. Higher ceremony; keeps the dependency graph clean.

Architect to rule. If (1): specify where the exception gets documented. If (2): sketch the event
shape so a build ticket can be scoped against it.

## Log
- 2026-09-04: Parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02") -- traces
  to FRO_082/FRO_063's work, that container's own lineage. PM audit found it unparked. Susan_02
  stays deliberately unwired to any convergence, so this does not gate Kathleen (RM_FRO_023).
- 2026-09-03: Ticket opened.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
