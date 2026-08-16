---
id: SAT_027
uid: SAT
number: 27
client: Satchel
status: done
title: Server bundles never left CREATED -- hydrateBundle bypassed the lifecycle
context: ScopeEngine_Server.hydrateBundle() called FixtureHydrator directly instead
  of SatchelBundle.hydrateAll()/hydrateFrom() -- FixtureHydrator explicitly documents
  itself as performing no lifecycle transitions, so no server bundle ever reached
  HYDRATED/LOADED/ACTIVE, with or without existing saved data. Crashed the moment
  flushIfDirty()'s saveAll() call actually ran (once FRO_018 fixed executionPulse),
  since saveAll() requires LOADED or ACTIVE.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

The very next crash after [FRO_018](FRO_018_border-executionpulse-disabled.md)'s
`withExecutionPulse(true)` fix actually let `flushIfDirty()` run for the first time ever:

```
java.lang.IllegalStateException: Operation not allowed in state CREATED (allowed [LOADED, ACTIVE])
	at com.arryn.satchel.common.bundle.LifecycleGuard.requireAny(LifecycleGuard.java:31)
```

(`SatchelBundle.saveAll()` requires `EnumSet.of(LOADED, ACTIVE)` — called from
`ScopeEngine_Server.flushIfDirty()` → `SavedDataEgressSink...store(bundle.saveAll())`.)

**Root cause:** `ScopeEngine_Server.hydrateBundle()` called
`new FixtureHydrator(bundle, source).hydrateExisting()` directly. `FixtureHydrator`'s own class
doc: *"This class performs no lifecycle transitions and enforces no correctness policy."* The
actual CREATED → HYDRATED transition, and setting `bundle.hydrated = true` (which the
`!hydratedBefore && bundle.isHydrated()` check right after depends on to call `bundle.onLoaded()`
at all), only ever happened inside `SatchelBundle.hydrateAll(CompoundTag)` — which
`hydrateBundle()` never called. Bypassing it meant `bundle.isHydrated()` stayed `false`
unconditionally, so `onLoaded()` was never called, so **every server bundle, in every scope, with
or without real saved data, has been permanently stuck in `CREATED`** since this hydration path
was written. Nothing surfaced this earlier because nothing that requires `LOADED`/`ACTIVE` had
ever actually run — `saveAll()` is only reached from `flushIfDirty()`, which is only reached from
`onExecutionPulse()`, which [FRO_018](FRO_018_border-executionpulse-disabled.md) just fixed from
being permanently gated off. Two independently-real bugs, chained: FRO_018 was necessary to reach
this one; this one was always there, just unreachable until FRO_018 landed.

**Fix applied:**
- Added `SatchelBundle.hydrateFrom(FixtureHydrationSource)` — factors the CREATED → HYDRATED
  transition, delegation to `FixtureHydrator`, and `hydrated = true` out of `hydrateAll(CompoundTag)`
  so both the client parcel path (`hydrateAll`, always NBT-backed) and the server SavedData path
  can share the one real state-machine-correct entry point.
- `hydrateAll(CompoundTag)` now just wraps `hydrateFrom(new NbtFixtureHydrationSource(root))`.
- `ScopeEngine_Server.hydrateBundle()` now calls `bundle.hydrateFrom(source)` instead of the raw
  `FixtureHydrator` call. When no saved data exists yet (first-ever creation — confirmed the
  common case, not an edge case, since every bundle in this whole run sequence has been a
  first-ever creation), it hydrates from an explicitly empty `NbtFixtureHydrationSource` rather
  than skipping — verified `NbtFixtureHydrationSource.hydrate()` on an empty root just returns
  `false` per fixture (no data found), a clean no-op, not an error.

**Left `in-progress`, not `done`:** need a real re-run to confirm — this is the point where
persistence flush, client sync, and (downstream of both) border rings/growth particles should all
finally work together for the first time in this whole run sequence.

## Log

- 2026-08-14: Fully confirmed — user reports rings and borders working as intended end to end,
  which requires server bundles to have correctly reached LOADED/ACTIVE and flushed. Closing.
- 2026-08-14: Root cause traced (see above), fix applied to `SatchelBundle.java` and
  `ScopeEngine_Server.java`. Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
