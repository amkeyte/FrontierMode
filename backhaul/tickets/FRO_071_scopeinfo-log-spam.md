---
id: FRO_071
uid: FRO
number: 71
client: FrontierMode
status: done
title: 'Satchel: squash per-mob ScopeInfo/introduceSource DEBUG spam'
context: 'Satchel: squash per-mob ScopeInfo/introduceSource DEBUG log spam on every
  spawn. Authorized.'
priority: normal
opened: '2026-09-01'
closed: '2026-09-01'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Satchel: squash per-mob ScopeInfo/introduceSource DEBUG spam

## Log

- 2026-09-01: Ticket opened.
- 2026-09-01: Fixed. Removed three `OUT.debug` calls, all in Satchel (not FrontierMode-specific):
  `ScopeInfo`'s constructor ("CREATED scope=..."), `ScopeInfo.setPhase`'s transition line ("PHASE
  transition ... NEW -> LOADED"), and `LogicalFoundation.introduceSource`'s first-time-introduction
  line ("[JigLifecycle][introduceSource] ..."). All three fire once per newly created scope for
  *every* mob Satchel's MobJig reconcile loop sees -- ambient bats included, not just bosses --
  and with mobs constantly spawning/despawning, "once per new scope" is itself a permanent stream
  at DEBUG. The `introduceSource` line had already been gated to first-time-only by SAT_042 (an
  earlier fix for a *different* spam source -- repeat polls of an already-tracked mob); this pass
  removes the log outright rather than gating further, since the underlying volume (new scopes,
  not repeat polls) was never SAT_042's target. The `hasScope()` guard and its real mutation
  (`ji.addScope(scope, source)`) are untouched -- only the logging inside it was removed. Also
  dropped `ScopeInfo.java`'s now-unused `OUT` import. Same "confirmed routine, remove rather than
  downgrade" call as this session's earlier `AScopeCoupler.BundleNotFound` removal (FRO_066's own
  log). One-time boot/install `OUT.debug` lines elsewhere in `LogicalFoundation` (EventBus/
  ScopeLifecycle/FoundationLifecycle/BundleLifecycle installed, etc.) were left alone -- those
  fire once per side per session, not per-entity.
  **Not yet build-verified from this session** -- same device-bridge limitation as FRO_069/070.
  Brace/paren balance checked on both touched files.
- 2026-09-01: Closed -- project owner confirms the DEBUG log spam is gone.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
