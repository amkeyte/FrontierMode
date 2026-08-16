---
id: SAT_024
uid: SAT
number: 24
client: Satchel
status: done
title: ScopeEngine_Client.get() throws wrong exception type
context: Threw IllegalStateException instead of SatchelException.BundleNotFound, so
  AScopeCoupler.getOrCreate()'s catch-and-fall-through-to-create never engaged on
  the client engine.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Seventh real crash found in this run sequence — first client-side (render thread) crash; every
previous one was server-side:

```
com.arryn.satchel.common.jig.guts.SatchelException$AccessFailed: A jig request has failed:
Failed to resolve BordersFixture for level minecraft:overworld
	at com.arryn.frontiermode.border.BorderAPI.borders(BorderAPI.java:117)
	at com.arryn.frontiermode.border.client.render.level.RenderContext.fixture(RenderContext.java:67)
	at com.arryn.frontiermode.border.client.render.level.RenderContext.standby(RenderContext.java:116)
	at com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer.render(WorldBordersRenderer.java:30)
	at com.arryn.frontiermode.Rendering.onRenderLevel(Rendering.java:47)
Caused by: java.lang.IllegalStateException: No bundles registered for scope: LevelScope[minecraft:overworld]...
	at com.arryn.satchel.client.jig.guts.ScopeEngine_Client.get(ScopeEngine_Client.java:122)
	at com.arryn.satchel.common.jig.guts.AScopeCoupler.get(AScopeCoupler.java:81)
	at com.arryn.satchel.common.jig.guts.AScopeCoupler.getOrCreate(AScopeCoupler.java:102)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.getOrCreate(ASatchelJig.java:212)
	at com.arryn.frontiermode.border.BorderAPI.borders(BorderAPI.java:102)
```

Good news first: [FRO_015](FRO_015_empty-path-tip-crash.md)'s fix held — no crash
on the server tick path at all this run, and both `borders_bundle` instances (overworld,
the_end, the_nether) hit the same benign `BundleNotFound ignored; falling through to create`
warn-and-recover path server-side that's always been there. This crash is new ground: first render
tick that calls `BorderAPI.borders(level)` client-side.

**Root cause:** `AScopeCoupler.getOrCreate()` (`common/jig/guts/AScopeCoupler.java:101-105`) is
meant to be resilient to "bundle doesn't exist yet" — it calls `get()`, catches
`SatchelException.BundleNotFound` specifically, logs `"BundleNotFound ignored; falling through to
create"`, and proceeds to `create()`. `ScopeEngine_Server.get()` throws exactly that type in both
of its failure branches (confirmed — this is why the server-side warn-and-recover log lines exist
at all). But `ScopeEngine_Client.get()` (`client/jig/guts/ScopeEngine_Client.java`) throws plain
`IllegalStateException` in both of *its* failure branches — never the `SatchelException.
BundleNotFound` subtype `getOrCreate()`'s catch clause is actually looking for. So on the client
engine, the fallback-to-create path has never worked: any legitimate "no client-local bundle yet"
state (the normal first-render condition, same shape as the server's own first-hydrate state)
propagates as an uncaught `IllegalStateException` instead of transparently creating the bundle.

Invisible until now because this is the first `getOrCreate()` call site in either repo that
actually runs on the client with a genuinely-missing bundle — `RenderContext`/`WorldBordersRenderer`
is the only client-side consumer of `BorderAPI.borders()`'s `getOrCreate()`-backed path.

**Fix applied:** changed both throw sites in `ScopeEngine_Client.get()` from `IllegalStateException`
to `SatchelException.BundleNotFound`, exactly mirroring `ScopeEngine_Server.get()`'s existing
pattern. Added the missing `SatchelException` import.

**Left `in-progress`, not `done`:** same as the six before it — need a real re-run to confirm. This
one specifically needs the client render path to actually reach a frame (not just server boot) to
confirm the fallback now engages instead of crashing.

## Log

- 2026-08-14: Confirmed — client-side render bundle creation and a full play session both worked
  with no `BundleNotFound`-related crash. Closing.
- 2026-08-14: Root cause traced (see above), fix applied to `ScopeEngine_Client.java`. Left
  `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
