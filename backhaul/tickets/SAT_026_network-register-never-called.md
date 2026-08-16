---
id: SAT_026
uid: SAT
number: 26
client: Satchel
status: done
title: SatchelNetwork.register() never called -- S2C sync dead entirely
context: SatchelNetwork.register() adds S2cBundleParcel's codec to CHANNEL and was
  never invoked anywhere in either repo. Every SatchelNetwork.send() call (server
  flush -> sync path) was sending an unregistered message type, so no client bundle
  has ever received real hydration data -- root cause of border rings/growth particles
  never rendering.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Not a crash, and not the growth-radius question from earlier — this is why "none of the particles
or border rings are rendering," reported after confirming growth itself now works (one border
exists, per `/border info @all`).

**Root cause:** `SatchelNetwork.register()` (`common/net/SatchelNetwork.java`) is the only place
`S2cBundleParcel`'s encoder/decoder/handler ever get added to `SatchelNetwork.CHANNEL` via
`CHANNEL.messageBuilder(S2cBundleParcel.class, id())...add()`. Grepped both repos for
`SatchelNetwork.register` and for `.register()` generally in Satchel's `net` package — **zero
callers, anywhere.** `SatchelMod`'s constructor calls `TrackingModule.init()` but never
`SatchelNetwork.register()`. So `SatchelNetwork.CHANNEL` was constructed but never told about its
one message type.

Traced the consequence through the full sync path, which is otherwise structurally sound:
`ScopeEngine_Server.onExecutionPulse()` → `flushIfDirty()` (fires because `SatchelBundle.onCreated()`
calls `markDirty()`) → `scheduleSync()` → `ParcelEgressSink.emit()` → `SatchelNetwork.send()` →
`CHANNEL.send(...)` with an unregistered message class. Forge's `SimpleChannel` has no codec to
serialize an unregistered type, so the send either throws internally or is silently dropped
depending on Forge's own handling — either way, no parcel reaches
`S2cBundleParcel.handle()`/`ParcelInbox.enqueue()`/`ScopeEngine_Client.applyIncomingParcels()` on
the client, ever. This is the actual explanation for the "known follow-on" flagged (but not
diagnosed) in [SAT_025](SAT_025_client-tick-missing-active-guard.md) — not a question of *whether*
the server proactively pushes an initial sync, but that no sync of any kind could ever have
reached the client, proactive or interval-based, since the wire format was never registered.

Directly explains the user's report: `RenderContext.fixture()`/`.borders()`/`.pathTip()` all read
the client-side `BordersFixture`, which only ever gets real data via a hydrated parcel — with none
ever arriving, the fixture stays at whatever empty/default state it starts in, so
`WorldBordersRenderer`/`GrowthTriggerRenderer` have nothing to draw regardless of how correct
their own rendering logic is.

**Fix applied:** added `SatchelNetwork.register();` to `SatchelMod`'s constructor, alongside the
existing `TrackingModule.init()` call. Corrected [net.md](../wiki/satchel/architecture/net.md)'s
"All packet handlers are explicitly registered" claim, which was describing the intended design,
not the actual (until now) state of the code — same class of doc drift as `persistence.md`'s false
claim caught in [SAT_017](SAT_017_persistence-followup.md).

**Left `in-progress`, not `done`:** need a real re-run to confirm — border rings and growth
particles should now actually render once a border exists.

## Log

- 2026-08-14: Fully confirmed — user reports rings and borders working as intended end to end.
  Closing.
- 2026-08-14: Confirmed necessary but not sufficient — user re-ran and still saw no rings/
  particles. Traced the actual missing link to a second, independent bug:
  [FRO_018](FRO_018_border-executionpulse-disabled.md) — Border's jig config never enabled
  `executionPulse` participation, so the entire flush-then-sync call path (which is where this
  ticket's fix would have mattered) was never reached in the first place, on either side. This
  ticket's fix is still correct and still needed — registration has to work for the pipeline to
  function once it's actually invoked — just not independently sufficient. Left `in-progress`
  pending a combined re-run with FRO_018's fix.
- 2026-08-14: Root cause traced (see above), fix applied to `SatchelMod.java`, `net.md` corrected.
  Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
