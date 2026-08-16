---
id: SAT_013
uid: SAT
number: 13
client: Satchel
status: done
title: 'ScopeEngine_Server: persistence null is skip or error'
context: resolveServerLevel() returns null for two meanings; callers can't tell them
  apart, always throw.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

ScopeEngine_Server: persistence null is skip or error

### Context

Split out of [SAT_012](SAT_012_trackingmodule-missing-persistence-capab.md), which misdiagnosed
this as a per-consumer config gap. It's a `ScopeEngine_Server` bug, not a `TrackingModule` one.

### Detail

`resolveServerLevel(info, requirement)` returns `null` in two situations that mean opposite
things: the capability wasn't required at all (`caps.requiresX() == false` — a legitimate skip),
or the capability was required but no `ServerLevel` was actually available (a real failure). Both
`hydrateBundle()` and `flushIfDirty()` treat any `null` the same way — unconditional
`SatchelException.AccessFailed` — with no branch for the first case. So in practice
`requiresPersistence = false` (the `Capabilities.defaults()` value) isn't usable by any
`LevelJigConfig`-based bundle that ever calls `create()` (which runs `hydrateBundle()` on every
scope load) — it throws on the very first load regardless of whether persistence was ever wanted.

Confirmed against `TrackerFixture` (`common/newconfig/TrackerFixture.java`): four `long` counters,
no `registerCustom(...)` or any field-persistence call — genuinely ephemeral by design, not an
oversight. Setting `requiresPersistence = true` on it (mirroring Border's [FRO_012](FRO_012_port-border-to-jigconfig-eventhandlers.md)
fix) would be treating the symptom, not the cause, and would force a diagnostic-only bundle through
save/load machinery it has no use for.

### Suggested fix

Give `hydrateBundle()`/`flushIfDirty()` a real no-op branch for "capability not required" instead
of routing every `null` through the same throw. Cleanest shape: have `resolveServerLevel` (or its
callers) check `caps.requiresX()` before deciding whether `null` means skip or error, rather than
using one sentinel value for both — an `Optional<ServerLevel>` return, or an early
`if (!required) return;` in each caller before ever calling `resolveServerLevel`, both work.
`TrackingModule.init()` should NOT be changed — leave `requiresPersistence` at its default `false`
once this is fixed, that's the correct value for it.

### Log

- 2026-08-13: Split from SAT_012 by Architect. See SAT_012's log for the full diagnosis and why
  the fix belongs here instead of in `TrackingModule`.
- 2026-08-13: Fixed without touching `resolveServerLevel` at all. Re-traced its actual exit
  paths: once `required == true`, every branch either throws `AccessFailed` or returns a real
  `ServerLevel` — it can only ever return `null` when the capability wasn't required in the first
  place. So there's no genuine two-meanings ambiguity in the sentinel itself, just a caller-side
  bug: both `hydrateBundle()` and `flushIfDirty()` were throwing on `null` when they should have
  been skipping. Changed both to skip (return / no-op) on `null` instead. Left `TrackingModule`
  untouched as instructed. Confirmed by a real `gradlew build`: `BUILD SUCCESSFUL`. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
