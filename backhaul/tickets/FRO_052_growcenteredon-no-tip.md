---
id: FRO_052
uid: FRO
number: 52
client: FrontierMode
status: done
title: growCenteredOn no-tip semantics
context: 'Owner call: no-tip should bootstrap like grow(), not fail -- contradicts
  RM_FRO_019''s 2026-08-24 ruling text.'
priority: normal
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s 2026-08-24 ruling (also restated in
[FRO_045](FRO_045_karen-build.md)'s "What to build") calls `growCenteredOn`'s no-tip case a
data-corruption state, distinct from `grow()`'s own empty-path bootstrap branch, and rules it
should surface a failed `Result` (a new `FailureKind`) rather than bootstrap.

Project owner's direct call during FRO_045's build-planning session: that's backwards.
`growCenteredOn` is meant to be the same mechanism as `grow()`, just with an assigned center
instead of a rules-chosen one -- there is no reason a caller supplying an explicit center should
get different bootstrap-vs-fail behavior than a caller that doesn't. An absent tip should
bootstrap exactly like `grow()`'s own empty-path branch does (rules-driven radius, layer 0), just
using the caller's center instead of `chooseInitialCenter()`. No new `Result.FailureKind` is
warranted for this.

FRO_045 is being built to this corrected behavior directly (owner instruction, not deferred).
This ticket exists to reconcile the roadmap node's own "ruled, not merely recommended" text
against what actually shipped -- `RM_FRO_019`'s log should get a dated correction entry (never a
silent rewrite of the existing 2026-08-24 entry) reconciling this, which is Architect's/PM's call
to make formally, not Lead Dev's to do silently while building.

## Log

- 2026-08-28: Ticket opened.

- 2026-08-28: **Closed (Architect).** Owner's correction confirmed and formalized as a dated entry
  on [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) (no-tip bootstraps like `grow()`, no new
  `Result.FailureKind`) — and, per the same discussion, the method itself collapses into a
  `grow(BlockPos center)` overload rather than staying a separately-named `growCenteredOn` (not
  originally part of this ticket's ask, folded in here since it's the same correction pass).
  [FRO_045](FRO_045_karen-build.md)'s "What to build" rewritten to match.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
