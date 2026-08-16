---
id: SAT_020
uid: SAT
number: 20
client: Satchel
status: done
title: ScopeInfo.jigInfo() unconditionally null
context: NPEs TrackingModule's handlers on first ScopeEvent. Found while writing runtime.md.
priority: high
opened: '2026-08-13'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found while writing [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) (SAT_015):
`ScopeInfo.jigInfo()` (`common/jig/guts/ScopeInfo.java`) unconditionally `return null`.
`TrackingModule`'s three event handlers dereference it directly (`info.jigInfo().jig`), and
`TrackingModule.init()` runs unconditionally from `SatchelMod`'s constructor with a
`LevelJigConfig` that matches every server-side level — so this isn't latent, it will NPE on the
very first `ScopeEvent.Loaded` posted for any level load.

A second, currently-unreachable instance of the same bug sits in
`ScopeLifecycleDispatcher.signalScopeUnloaded` (`if (ji==null) throw new
IllegalStateException("how!!?!?")` — the message suggests the original author already suspected
it), but that path is dead for now since nothing calls `LogicalFoundation.tryRemoveSource`.

Out of scope for this ticket, worth naming here rather than losing: `ModelJig` is fully wired
code with no way to ever be instantiated (no `JigConfig` subclass targets it, nothing calls
`ServerModelIngress`) — may be intentional in-progress work or abandoned scaffolding, not
determinable from source alone. Not a bug, just flagged for whoever picks this ticket up in case
it's adjacent.

This is a real runtime bug, not a doc gap — filed under Satchel but this is Lead Dev work
(implementation fix), not something resolved by documentation. Flagged high priority since it's
reachable on first tick, not theoretical.

## Log

- 2026-08-14: Confirmed by the next re-run — the `jigInfo()` NPE is gone; boot progressed past it
  to a different, unrelated crash ([SAT_022](SAT_022_tracker-bundlefactory-missing.md)). Closing.
- 2026-08-14: Confirmed by a real crash log (FrontierMode `runClient`, after
  [FRO_014](FRO_014_border-persistence-crash.md) and [SAT_021](SAT_021_jig-key-never-installed.md)
  cleared the two crashes ahead of it in the boot sequence): `NullPointerException: Cannot read
  field "jig" because the return value of "ScopeInfo.jigInfo()" is null`, at
  `TrackingModule.onScopeLoaded(TrackingModule.java:109)` — exactly as predicted from source. Fixed
  by adding a `jigInfo` field + `installJigInfo(JigInfo)` to `ScopeInfo`, called from
  `JigInfo.addScope()` (the only place a `ScopeInfo` is ever constructed, and the only place its
  owning `JigInfo` is naturally in scope as `this`). Updated
  [runtime.md](../wiki/satchel/architecture/runtime.md) (the `ScopeInfo` description, the
  `TrackingModule` worked example, and "Known gaps") to reflect the fix. Left `in-progress`, same
  as the two crashes before it in this sequence — need a real re-run to confirm before closing.
- 2026-08-13: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
