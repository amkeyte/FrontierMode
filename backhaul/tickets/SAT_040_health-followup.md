---
id: SAT_040
uid: SAT
number: 40
client: Satchel
status: open
title: Absorb LevelJig/PlayerJig health checks into SatchelHealth
context: SAT_039's MobJig slice landed in SatchelHealth (common/tracking/); LevelJig/PlayerJig
  health absorption was deliberately deferred, tracked here.
priority: normal
opened: '2026-08-23'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

[SatchelHealth](../wiki/satchel/architecture/satchel-health.md) (SAT_039, `common/tracking/`) is meant to be
the permanent home for every jig kind's run-monitoring/self-verification, not a MobJig-only
module. SAT_039's own build only lands the `MobJig` slice — widening its `sideApplicability` to
`BOTH` and adding the canary-mob regression check — because that was the ticket's single most
urgent gap. `LevelJig` (via the old `TrackingModule`) and `PlayerJig` (via
`PlayerTrackingModule`) still run as their own separate classes, unabsorbed, with no equivalent
"backing state still valid at teardown" violation check of their own.

## What this ticket is asking for

1. Relocate `TrackingModule`'s and `PlayerTrackingModule`'s registration/event-handler logic into
   `SatchelHealth`, same pattern SAT_039 already used for `MobTrackingModule` — same bundle/
   fixture/jig key IDs, no behavior change to what's already proven working.
2. Design and add each jig kind's own violation check, analogous to `onMobScopeUnloaded`'s
   `isRemoved()` check — what "torn down for a reason other than the two the jig's contract
   allows" concretely means differs per jig kind (`LevelJig` ties to dimension load/unload,
   `PlayerJig` to login/logout) and needs its own reasoning, not a copy-paste of MobJig's.
3. Retire `TrackingModule`/`PlayerTrackingModule` the same way SAT_039 retired
   `MobTrackingModule` (empty stub, or deleted outright if whoever picks this up has file-delete
   access).

## Not in scope

Any production-safety gate on the crash-on-violation behavior — SAT_039 left that as its own
explicit follow-up too; whoever builds one should cover all three jig kinds at once rather than
per-kind.

## Log

- 2026-08-23: Ticket opened, split out of SAT_039 at the project owner's direction (SatchelHealth
  is meant to absorb every jig kind's health checks, not just MobJig's) so SAT_039 itself could
  stay scoped to the one gap it was actually opened to close.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
