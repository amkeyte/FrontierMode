---
id: SAT_030
uid: SAT
number: 30
client: Satchel
status: done
title: 'Client bundle refresh: hydrateAll() only ever fires once'
context: ScopeEngine_Client.applyIncomingParcels() called bundle.hydrateAll() for
  every incoming parcel, but hydrateAll()'s CREATED->HYDRATED transition throws once
  the bundle is already LOADED/ACTIVE. Only the very first parcel a client bundle
  ever receives was applied; every sync after that (e.g. every border growth) silently
  failed and was dropped, logged every ~5s. Client fixture data was permanently stuck
  at whatever the first snapshot captured.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Not a crash — user reported the client survived a full run with no exceptions this time
([SAT_029](SAT_029_send-sidedness-classcast.md) confirmed fixed), but still saw no border rings
or growth particles, and asked to check `latest.log`. It had the answer:

```
[Render thread/INFO] Borders loaded: 0
...
[Render thread/ERROR] [engine] CLIENT failed to apply parcel to BordersBundle[...]:
  java.lang.IllegalStateException: Operation not allowed in state ACTIVE (expected CREATED)
```
repeating every ~5 seconds (`DEFAULT_SYNC_INTERVAL_TICKS`), for the entire rest of the session —
interleaved with real evidence the server side was working correctly the whole time: growth chat
messages arriving (`[Border] Advanced border progression`, twice) and `/border info`-style output
showing two real borders (`Thornwall`, `Skyreach`).

**Root cause:** `ScopeEngine_Client.applyIncomingParcels()` called `bundle.hydrateAll(parcel.
data())` unconditionally for every incoming parcel. `hydrateAll()` (via
[SAT_027](SAT_027_server-hydrate-bypasses-lifecycle.md)'s `hydrateFrom()`) does a *strict*
`CREATED -> HYDRATED` transition — by design, since it's meant to fire exactly once, paired with
the one-time `onLoaded()` call right after it in `applyIncomingParcels()`. The very first parcel a
bundle ever receives applies fine (`CREATED -> HYDRATED -> LOADED -> ACTIVE`, logged as `Borders
loaded: 0` since no growth had happened yet at that moment). Every parcel after that — i.e. every
regular sync update once the bundle is already `ACTIVE`, which is the normal steady state for the
rest of a session — hit the same strict transition and threw, was caught, logged, and dropped.
Client fixture data has been permanently frozen at whatever the very first snapshot captured,
which was correctly "no borders yet" (or, if you were mid-growth by then, `borders_bundle`, not
the tracker) — nothing since has ever reached it, so nothing existed for
`WorldBordersRenderer`/`GrowthTriggerRenderer` to draw, even though the server-side data was
completely correct.

**Fix applied:**
- Added `SatchelBundle.refreshFrom(FixtureHydrationSource)` — applies a source to every existing
  fixture with *no* lifecycle transition, requiring `LOADED`/`ACTIVE` (mirrors `saveAll()`'s own
  requirement, since both operate on a bundle already considered live).
- `ScopeEngine_Client.applyIncomingParcels()` now branches on `bundle.isHydrated()`: first parcel
  ever → `hydrateAll()` (unchanged, still pairs with `onLoaded()`); every parcel after that →
  `refreshFrom(new NbtFixtureHydrationSource(parcel.data()))` (updates fixture data in place, no
  transition, no duplicate `onLoaded()`).

**Left `in-progress`, not `done`:** need a real re-run to confirm — this should be the one where
rings and particles finally render, since the client should now actually track the server's real,
growing border state instead of a single frozen snapshot.

## Log

- 2026-08-14: Confirmed — growth ritual particles rendered correctly on the very next run,
  proving the client fixture is now tracking real, updated server state instead of a single
  frozen snapshot. Closing. (Border rings still didn't show on that same run, but for a fully
  separate reason — a hardcoded render height, see
  [FRO_019](FRO_019_ring-hardcoded-height.md) — not a regression of this fix.)
- 2026-08-14: Root cause traced from `latest.log` (see above), fix applied to `SatchelBundle.java`
  and `ScopeEngine_Client.java`. Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
