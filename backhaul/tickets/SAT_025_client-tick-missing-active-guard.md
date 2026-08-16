---
id: SAT_025
uid: SAT
number: 25
client: Satchel
status: done
title: ScopeEngine_Client.onJigTick() missing ACTIVE guard
context: Ticked freshly-created client bundles unconditionally; SatchelBundle.onJigTick()
  hard-requires ACTIVE, which a client bundle only reaches via a server parcel arriving
  (applyIncomingParcels). Crashed the render thread on the tick right after any client-side
  create().
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Eighth real crash found in this run sequence, immediately after
[SAT_024](SAT_024_client-engine-wrong-exception.md)'s fix let the client-side `getOrCreate()`
fallback actually create a `borders_bundle` on the render thread for the first time:

```
java.lang.IllegalStateException: Operation not allowed in state CREATED (expected ACTIVE)
	at com.arryn.satchel.common.bundle.LifecycleGuard.require(LifecycleGuard.java:21)
	at com.arryn.satchel.common.bundle.SatchelBundle.onJigTick(SatchelBundle.java:208)
	at com.arryn.satchel.client.jig.guts.ScopeEngine_Client.onJigTick(ScopeEngine_Client.java:264)
	at com.arryn.satchel.common.jig.guts.AScopeCoupler.onJigTick(AScopeCoupler.java:152)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.onTick(ASatchelJig.java:170)
	at com.arryn.satchel.common.lifecycle.FoundationLifecycleDispatcher.pulse(FoundationLifecycleDispatcher.java:63)
	at com.arryn.satchel.client.lifecycle.ClientForgeIngress.onExecutionPulse(ClientForgeIngress.java:96)
```

Confirms SAT_024's fix worked as intended (log shows `BundleNotFound ignored; falling through to
create` on the render thread right before this) — this is new ground one tick further.

**Root cause:** `SatchelBundle`'s lifecycle is `CONSTRUCTED → CREATED → HYDRATED → LOADED →
ACTIVE` (`onCreated()` reaches `CREATED`; `onLoaded()` — called only from
`ScopeEngine_Client.applyIncomingParcels()`, after a server parcel has actually arrived and
`hydrateAll()` succeeds — advances `HYDRATED → LOADED → ACTIVE` in one call). A client bundle
therefore only reaches `ACTIVE` once the server has synced it over the network; `onJigTick()`
hard-requires `ACTIVE` and throws otherwise. `ScopeEngine_Client.onJigTick()` ticked every bundle
in its map unconditionally — no check for lifecycle state — so the very next client tick after
`create()` (still sitting in `CREATED`, no parcel possibly having arrived yet) crashed.

`ScopeEngine_Server.onJigTick()` already guards against exactly this
(`bundle.lifeCycleState() != LifecycleState.ACTIVE) continue;`) — this is a real, precedented
pattern in the same class shape one file over, just missing on the client side. Server bundles
don't normally hit this window because `ScopeEngine_Server.create()` calls `hydrateBundle()`
synchronously inline, so a server bundle reaches its terminal state (`ACTIVE` or otherwise) before
`create()` even returns; client bundles have no such synchronous path — `onLoaded()` is reachable
only through network sync timing.

**Fix applied:** added the same `bundle.lifeCycleState() != LifecycleState.ACTIVE` skip to
`ScopeEngine_Client.onJigTick()`, mirroring the server engine's existing code exactly.

**Known follow-on, not fixed here, flagged per the user's own note that client-side sync is
mid-refactor:** this fix stops the crash, but a client bundle now just sits inert (never ticks,
never calls fixture logic) until a parcel actually arrives. Whether the server currently sends an
*initial* parcel proactively on scope load (vs. only syncing already-dirty bundles on interval) is
unverified — if it doesn't, a client bundle could sit in `CREATED` indefinitely with nothing to
prompt the first sync. Worth a dedicated look whenever client-side sync gets its next real pass,
not a "does it run" blocker by itself since it degrades to inert rather than crashing.

**Left `in-progress`, not `done`:** same as the seven before it — need a real re-run to confirm.

## Log

- 2026-08-14: Confirmed — a full play session ticked client bundles with no lifecycle-state
  crash. The "sits inert until synced" follow-on noted below remains open but unconfirmed either
  way (not a crash, so not directly observable from a log); left as a known gap in `runtime.md`
  rather than its own ticket for now. Closing this ticket on the crash fix.
- 2026-08-14: Root cause traced (see above), fix applied to `ScopeEngine_Client.java`, mirroring
  `ScopeEngine_Server.onJigTick()`'s existing guard. Left `in-progress` pending a real re-run to
  confirm. Flagged a follow-on concern (initial client sync timing) for later, out of scope for
  this ticket.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
