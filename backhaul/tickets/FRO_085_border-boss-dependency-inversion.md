---
id: FRO_085
uid: FRO
number: 85
client: FrontierMode
status: done
title: BorderCommandHandler now imports BossAPI -- pendingAttach write inverts Boss-never-reaches-back-into-Border
  rule
context: '[Susan_02] BossAPI import inverts Boss-never-reaches-Border rule; ruled
  named exception, see Log.'
priority: normal
opened: '2026-09-03'
closed: '2026-09-07'
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
- 2026-09-07: Architect stitched the ruled paragraph into `boss.md`'s "Defeat detection and the
  border-growth gap" section, as a named carve-out alongside the "Boss depends on Border, never
  the reverse" statement. Closing.
- 2026-09-07: PM housekeeping: context trimmed to board length standard (was 159 chars, over the ~100-char limit in bht.md). Full detail already lives in Problem/Decision/Log above; no substantive change.
- 2026-09-06: Project owner ruling (standing in for Architect): **Option 1, named exception.** `BorderCommandHandler.pathGrow()`'s `BossAPI` import is accepted as a deliberate, documented one-off, not redesigned via an event. Rationale: it's a single, narrow call site that already degrades gracefully (Optional-guarded, doesn't roll back the border grow if Boss data isn't resolvable yet), and this codebase's own convention (see Effects module's stance in FRO_089/effects.md against a registered descriptor system "not ahead of the second real consumer") is to not build cross-module event machinery for one consumer. No code change needed -- `pathGrow()` already implements this shape and already carries an inline comment explaining it.

  Wiki-ready paragraph for the Architect to stitch into `boss.md` (suggested home: alongside the "Defeat detection and the border-growth gap" section's existing "Boss depends on Border, never the reverse" statement, as a named carve-out):

  > **One named exception to "Boss depends on Border, never the reverse":** `BorderCommandHandler.pathGrow()` (a boss-less path grow, see "Boss-less path layers and attach" above) imports `BossAPI` directly to mark the newly-grown border `pendingAttach`. This is the one place Border code calls into Boss. It's accepted as a bounded, deliberate exception rather than redesigned via an event: the call is Optional-guarded (a missing `BossAPI.CRUD(level)` is a silent no-op, same "standby, don't crash" discipline every other `BossAPI` resolution failure in this codebase follows), and the border grow itself is not rolled back if it fails.

  Handing this back to PM/Architect to fold into the wiki -- not edited here, per Dev(FrontierMode)'s own lane.
- 2026-09-04: Parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02") -- traces
  to FRO_082/FRO_063's work, that container's own lineage. PM audit found it unparked. Susan_02
  stays deliberately unwired to any convergence, so this does not gate Kathleen (RM_FRO_023).
- 2026-09-03: Ticket opened.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
