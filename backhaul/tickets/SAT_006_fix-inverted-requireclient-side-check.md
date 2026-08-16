---
id: SAT_006
uid: SAT
number: 6
client: Satchel
status: done
title: Fix inverted requireClient() side check
context: Satchel.requireClient() checks side != SERVER instead of != CLIENT; guard
  passes on server.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fix inverted requireClient() side check

### Context

`Satchel.java`'s `requireClient()` guards `if (side != LogicalSide.SERVER)` — the same check as
`requireServer()`, just copy-pasted without updating the comparison. As written it silently
passes when called from the server and throws "this operation is client-only" when actually
called from the client, i.e. it does the opposite of its name. Found while investigating a
recalled client/server context-mixing bug from an earlier single-player session; the broader
side-routing mechanism (`LogicalSideContext` ThreadLocal, per-side `LogicalFoundation` in
`Satchel.FOUNDATIONS`) is sound, this is an isolated inverted comparison.

### Suggested fix

Change the condition to `side != LogicalSide.CLIENT`. One-line fix; worth a quick grep for any
other `require{Server,Client}`-style guards in case the same copy-paste happened elsewhere.

## Log

- 2026-08-13: Ticket opened.
- 2026-08-13: Fixed as suggested (`side != LogicalSide.CLIENT`). Grepped for other
  `require{Server,Client}`-style guards — no other copies of this pattern exist; `requireServer()`
  is the only sibling and its comparison is already correct. Note: `requireClient()` is called
  from five places in `ScopeEngine_Client.java` (constructor + `create`/`onExecutionPulse`/
  `onJigTick`/`unload`), none of which should ever legitimately run server-side in practice
  (`ScopeEngine_Client` is only ever constructed via `ClientFoundationBooter`, itself only
  triggered by client-only Forge events) — so this fix shouldn't change behavior on any currently
  working path, just makes the guard actually guard. Confirmed by a real `gradlew build`:
  `BUILD SUCCESSFUL`. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
