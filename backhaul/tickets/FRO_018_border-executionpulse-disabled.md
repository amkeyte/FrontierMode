---
id: FRO_018
uid: FRO
number: 18
client: FrontierMode
status: done
title: Border config never enabled executionPulse -- flush/sync never ran
context: JigPolicies.Lifecycle.defaults() has executionPulse=false. BorderModule.init()
  set withTick(true) but never withExecutionPulse(true), so AScopeCoupler.onExecutionPulse()'s
  participation gate always skipped -- ScopeEngine_Server.flushIfDirty/scheduleSync
  and ScopeEngine_Client.applyIncomingParcels were both unreachable for Border's jig
  on either side, independent of SAT_026's network registration fix.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[SAT_026](SAT_026_network-register-never-called.md)'s network registration fix was necessary but
not sufficient — the user re-ran and still saw no border rings or growth particles, only the
unconditional `debugFlame()` marker above the player's head (which needs no fixture data at all).
That ruled out "packet arrives but client can't parse it" and pointed back at whether a packet was
ever sent in the first place.

**Root cause:** `JigPolicies.Lifecycle.defaults()` (`common/newconfig/newnew/JigPolicies.java`) is
`(participatesInLoad=true, participatesInUnload=true, participatesInTick=false,
participatesInExecutionPulse=false)`. `BorderModule.init()` built its lifecycle as
`JigPolicies.Lifecycle.defaults().withTick(true)` — sets tick participation, but
`executionPulse` stays at its default `false`. `AScopeCoupler.onExecutionPulse()` gates on exactly
that flag (`if (!execution().lifecycle().participatesInExecutionPulse()) return;`) before ever
calling into the engine. Both halves of the sync pipeline live *only* inside engine
`onExecutionPulse` methods: `ScopeEngine_Server.onExecutionPulse()` → `flushIfDirty()` →
`scheduleSync()` (server: persist + emit parcel) and `ScopeEngine_Client.onExecutionPulse()` →
`applyIncomingParcels()` (client: drain + hydrate). With the gate always closed, `SatchelNetwork.
send()` was never even called — SAT_026 fixed a real bug (the packet type wasn't registered), but
the send site upstream of it was unreachable the entire time regardless, so that fix alone
couldn't have produced a visible result. `ScopeEngine_Server.unload()` does call its own
`onExecutionPulse(info)` directly as a "terminal maintenance flush," bypassing this gate — but
that's moot, since (per `runtime.md`'s Known gaps) a `LevelJig` scope is never actually unloaded
in either repo, so that path is equally unreachable in practice.

**Fix applied:** changed `BorderModule.init()`'s lifecycle to
`JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true)`.

**Left `in-progress`, not `done`:** need a real re-run to confirm — border rings and growth
particles should now actually render. If they still don't, the remaining suspects are
`RenderContext.standby()`'s gating (`f.isReady()`, `revisionMonitor.poll(...)`) or
`WorldBordersRenderer`'s actual draw calls, not the sync pipeline itself.

## Log

- 2026-08-14: Fully confirmed — user reports rings and borders working as intended end to end
  (downstream of [SAT_027](SAT_027_server-hydrate-bypasses-lifecycle.md) and
  [FRO_019](FRO_019_ring-hardcoded-height.md)). Closing.
- 2026-08-14: Confirmed this fix worked exactly as predicted — the next crash was
  `flushIfDirty()`'s `saveAll()` throwing, meaning `onExecutionPulse` (and the flush path inside
  it) finally ran for the first time. That crash was a separate, real, pre-existing bug of its
  own, not a flaw in this fix — see [SAT_027](SAT_027_server-hydrate-bypasses-lifecycle.md).
- 2026-08-14: Root cause traced (see above), fix applied to `BorderModule.java`. Left
  `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
