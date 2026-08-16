---
id: FRO_015
uid: FRO
number: 15
client: FrontierMode
status: done
title: DefaultBorderRules crashes on empty path tip
context: growPathCriteria/updateFinderItems both used .orElseThrow() on PATH.tip(),
  which is legitimately empty before any border has ever grown -- crashed the server
  on first eligible tick/placement in a fresh world.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Sixth real crash found in this run sequence — first one past bundle hydration entirely (previous
fix, [SAT_023](SAT_023_scopeengine-shared-config.md), got both `tracker_bundle` and
`borders_bundle` created without error; this crash is during tick, not load):

```
java.util.NoSuchElementException: No value present
	at java.base/java.util.Optional.orElseThrow(Optional.java:377)
	at com.arryn.frontiermode.border.server.rules.DefaultBorderRules.updateFinderItems(DefaultBorderRules.java:164)
	at com.arryn.frontiermode.border.server.rules.BordersTriggers.updateFinderItems(BordersTriggers.java:37)
	at com.arryn.satchel.common.lifecycle.SatchelEventBus.post(SatchelEventBus.java:84)
	at com.arryn.satchel.common.lifecycle.ScopeLifecycleDispatcher.emitTick(ScopeLifecycleDispatcher.java:105)
	at com.arryn.satchel.common.lifecycle.ScopeLifecycleDispatcher.signalScopeTick(ScopeLifecycleDispatcher.java:73)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.onTick(ASatchelJig.java:171)
	...
```

Thrown from the server tick handler, so it takes the whole server down (uncaught exception in a
Forge event listener during `ServerTickEvent`), not just the one feature.

**Root cause:** `BordersPathFacet.tip()` returns `Optional<Border>`, legitimately empty before any
border has ever been grown (fresh world, no border path yet — confirmed by
`BordersPathFacet.grow()`'s own handling of this exact state a few lines away: `tipOpt.isPresent()
? fixture.logic.grow(tipOpt.get()) : fixture.logic.getInitial()`, i.e. "no tip" is a first-class,
expected input there, not an error). But `DefaultBorderRules.updateFinderItems()` and
`DefaultBorderRules.growPathCriteria()` both called `.orElseThrow()` on the same `Optional`,
labelled only with the comment `//no path tip` — turning the expected "nothing grown yet" state
into a hard crash. `updateFinderItems` runs on a gated interval every scope tick
(`BordersTriggers.updateFinderItems`, gated by `FINDER_ITEMS_UPDATE_MONITOR`), so it crashes the
server automatically, with no player action required, the first time it's eligible to run in a
world with no border path yet — i.e. every fresh world. `growPathCriteria` has the identical
pattern, not yet hit in this run's logs but definitely reachable: the very first gold block any
player ever places, in a world with no border path yet, would hit it next.

**Fix applied:**
- `updateFinderItems`: no path tip means nothing to point players toward yet — changed to return
  early (no-op) instead of throwing.
- `growPathCriteria`: no path tip means there's nothing to be "close enough to" yet, and
  `BordersPathFacet.grow()`'s own `getInitial()` fallback ignores the placement position entirely
  (`DefaultBorderRules`' own class doc: "initial border is centered at the level spawn position")
  — so the correct behavior is to treat any gold block placement as valid to trigger that initial
  growth. Changed the empty case to `return true` instead of throwing.

**Left `in-progress`, not `done`:** same as the five before it — need a real re-run to confirm.
Since `growPathCriteria`'s crash wasn't actually observed yet (only reasoned about from source),
confirming it needs a run where a player places a gold block before any border exists, not just a
server boot.

## Log

- 2026-08-14: Fully confirmed — border growth from a fresh world (no prior path tip) has now run
  successfully multiple times with no crash, confirming `growPathCriteria`'s empty-tip branch is
  correct. Closing.
- 2026-08-14: Partially confirmed — a full play session never hit `updateFinderItems`'s empty-tip
  case (or if it did, it no-op'd silently as intended; no crash either way). `growPathCriteria`
  still unconfirmed — the user didn't report placing a gold block this run. Left `in-progress`;
  will close once a run confirms a gold block placed before any border exists doesn't crash.
- 2026-08-14: Root cause traced (see above), fix applied to `DefaultBorderRules.java` (both
  methods). Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
