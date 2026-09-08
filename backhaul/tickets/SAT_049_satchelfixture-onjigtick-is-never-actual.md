---
id: SAT_049
uid: SAT
number: 49
client: Satchel
status: open
title: SatchelFixture.onJigTick() is never actually invoked -- dead per-fixture tick
  dispatch
context: 'Discovered while building RM_FRO_037 (FrontierMode''s Sick Wildlife). SatchelBundle.onJigTick()
  correctly loops fixtures.values() and calls fixture.onJigTick() (code reads right),
  and the per-tick driver chain (FoundationLifecycleDispatcher.pulse() -> jig.onTick(info)
  -> coupler().onJigTick(info) -> engine().onJigTick(info)) also reads right on paper
  -- but in a real play session it never actually reaches any fixture. Proof: Satchel''s
  own TrackerFixture (com.arryn.satchel.common.newconfig.TrackerFixture) increments
  internalTicks only from onJigTick() and externalTicks from a separate countExternalTick()
  call; after a session that logged externalTicks=2708 on unload, internalTicks stayed
  at 0. This isn''t isolated to FrontierMode''s new code -- BorderPregenFixture (FrontierMode,
  LevelJig-scoped) relies on the exact same dispatch and has apparently never once
  ticked either: zero "[BorderPregen]" log lines exist anywhere across this world''s
  whole play history despite 7 borders on record needing pregeneration. Suspect the
  break is somewhere between engine().onJigTick(info)''s bundle sweep and it actually
  being reached per real scope -- possibly bundlesFor(info) never getting populated
  the way onJigTick''s sweep expects, a lifecycle-state check silently continuing
  past every bundle, or FoundationLifecycleDispatcher.pulse()''s jInfo.scopeInfos()
  not including the scopes onJigTick''s sweep needs. FrontierMode worked around this
  locally for RM_FRO_037 (ExteriorTellFixture is now driven by a direct manual call
  from BorderModule.onPlayerScopeTick, same pattern BorderPlayerStatusFixture already
  used), but BorderPregenFixture has no such workaround yet and its whole pregeneration
  pipeline may be silently non-functional in real play. Recommend instrumenting the
  dispatch chain (or reproducing with TrackerFixture in isolation) to find exactly
  where internalTicks stops incrementing.'
priority: high
opened: '2026-09-08'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

SatchelFixture.onJigTick() is never actually invoked -- dead per-fixture tick dispatch

## Log

- 2026-09-08: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
