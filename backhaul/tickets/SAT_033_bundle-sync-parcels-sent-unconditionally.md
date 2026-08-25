---
id: SAT_033
uid: SAT
number: 33
client: Satchel
status: done
title: Bundle sync parcels sent unconditionally, ignoring dirty state and connected
  players
context: 'Observed in a real server log with no client connected: pulseSync() fires
  scheduleSync() every syncIntervalTicks regardless of bundle.isDirty() and regardless
  of whether any player is in the dimension. Design question answered by project owner
  (keep the heartbeat -- parcels are unreliable/unacked, no client resend-request
  path) -- fixed by gating on player presence only, not dirty state.'
priority: normal
opened: '2026-08-16'
closed: '2026-08-16'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner shared a log snippet from a real dedicated-server session with **no client
connected**, showing the same bundle being sent repeatedly, seconds apart:

```
[11:38:06] [Server thread/DEBUG] [Tracer/]: [TRACE] [server engine] Sending Parcel for bundle: 0d41215d-4989-305f-9e31-e64a2cc23f5a
[11:38:11] [Server thread/DEBUG] [Tracer/]: [TRACE] [server engine] Sending Parcel for bundle: 0d41215d-4989-305f-9e31-e64a2cc23f5a
[11:38:11] [Server thread/DEBUG] [Tracer/]: [TRACE] [server engine] Sending Parcel for bundle: 0d41215d-4989-305f-9e31-e64a2cc23f5a
[11:38:11] [Server thread/DEBUG] [Tracer/]: [TRACE] [server engine] Sending Parcel for bundle: 0d41215d-4989-305f-9e31-e64a2cc23f5a
```

**Traced (read-only, not yet fixed):**
- `SatchelBundle.pulseSync(ScopeInfo)` (`common/bundle/SatchelBundle.java:299-307`) increments a
  tick counter and, once it reaches `syncIntervalTicks` (`DEFAULT_SYNC_INTERVAL_TICKS`, resets to
  0 each fire), unconditionally calls `syncDelegate.sync(info, key, this)` — **no
  `bundle.isDirty()` check at all**. This is the delegate `ScopeEngine_Server.scheduleSync()`
  attaches at bundle creation (`ScopeEngine_Server.java:204`), which itself unconditionally builds
  an NBT snapshot (`bundle.saveAll()`) and calls `ParcelEgressSink.forBundle(info,
  key).emit(...)` → `SatchelNetwork.send(...)` — every single time, whether or not anything
  changed.
- Contrast with the *persistence* path on the same class: `flushIfDirty()`
  (`ScopeEngine_Server.java:340-361`) explicitly checks `if (!bundle.isDirty()) return;` before
  writing to disk. The network-sync path has no equivalent guard — this looks like an asymmetry
  that was probably meant to be there and got missed, not an intentional "always broadcast"
  design (broadcasting unconditionally on a timer while a border bundle's state genuinely hasn't
  changed since the last flush is pure waste).
- `SatchelNetwork.send()` uses `PacketDistributor.DIMENSION.with(level::dimension)`, which Forge
  handles as a safe no-op with zero connected players in that dimension — so this isn't a crash
  risk, just wasted NBT-serialization + packet-construction work every `syncIntervalTicks` ticks,
  per bundle, forever, regardless of player presence. Log-spam at DEBUG/TRACE level is a secondary
  symptom of the same root cause, not a separate issue.
- Not yet checked: whether `attachSyncDelegate`'s delegate is *meant* to double as a periodic
  "resync in case a parcel got dropped" mechanism (which would argue for keeping it timer-based
  regardless of dirty state, just gating it on "at least one player present" instead) or whether
  it was genuinely supposed to mirror `flushIfDirty`'s dirty-gating and just didn't. That's a real
  design call, not something to guess at — see [RM_SAT_020](../roadmap/RM_SAT_020_jerry.md)'s
  sibling tickets for the project's established pattern of flagging this kind of question rather
  than picking an answer unilaterally.

**Not actioned.** Pinned per project owner's request; no fix applied, no build access this
session to verify one regardless. Normal priority — wasteful, not broken (nothing crashes, no
data corruption), but worth cleaning up given it runs on every bundle, every session, forever.

## Log

- 2026-08-24: Normalized `status: closed` -> `done` per [BKHL_006](../tickets/BKHL_006_closed-status.md) —
  outside BHT's `open/in-progress/blocked/done` vocabulary, no distinct meaning intended.
- 2026-08-16: **Design question answered by project owner, fixed, closed.** Keep the periodic
  resend un-gated by `bundle.isDirty()` — parcels have no delivery acknowledgment and there's no
  client-side "my state might be stale, please resend" request path, so the timer is the only
  thing that heals a dropped parcel or catches up a client that connected mid-session before the
  next real change. Confirmed the "waste" in the original log wasn't network traffic either way —
  `SatchelNetwork.send()` uses `PacketDistributor.DIMENSION`, which Forge safely no-ops with zero
  players — just wasted NBT-serialization/packet-construction CPU work for a send nobody receives.
  **Fix:** `ScopeEngine_Server.scheduleSync()` now checks a new `noOneWouldReceiveThis(ScopeInfo)`
  helper first and skips entirely when the scope's dimension has zero connected players — dirty
  state still isn't checked, matching the decision to keep the heartbeat. Only meaningful for
  `LevelScope`-backed bundles today (`SatchelNetwork.send()`'s only supported scope kind per
  SAT_028); any other scope kind conservatively assumes someone might receive it rather than
  guessing at a different audience shape. Unverified pending rebuild — no build access this
  session.
- 2026-08-16: Pinned by Lead Dev (Curtis) from a log snippet the project owner shared, traced to
  `SatchelBundle.pulseSync()` calling its sync delegate unconditionally (no dirty check, unlike
  the sibling `flushIfDirty()` persistence path). Left open, not actioned — see Summary for the
  one open design question before this is a straightforward fix.
- 2026-08-16: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
