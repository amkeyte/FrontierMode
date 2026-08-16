---
id: FRO_016
uid: FRO
number: 16
client: FrontierMode
status: done
title: RenderContext.getInstance() crashes on level exit
context: Threw IllegalStateException when Minecraft.level is null, which happens for
  one or more client ticks after disconnect before the jig's tick participation unsubscribes.
  Author's own comment already flagged this as unexplained; root cause is a normal
  teardown-window race, not a real error.
priority: normal
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Ninth real crash found in this run sequence — but the first one that happened *after* an entire
successful play session. With [SAT_023](SAT_023_scopeengine-shared-config.md)/
[SAT_024](SAT_024_client-engine-wrong-exception.md)/
[SAT_025](SAT_025_client-tick-missing-active-guard.md) all in place, this run booted,
loaded, and played normally end to end — first full "does it run" confirmation for the combined
mods. This crash only hit on exiting the world:

```
[Render thread/INFO] Player was null
java.lang.IllegalStateException: Minecraft returned null level
	at com.arryn.frontiermode.border.client.render.level.RenderContext.getInstance(RenderContext.java:55)
	at com.arryn.frontiermode.border.client.render.level.GrowthTriggerRenderer.tick(GrowthTriggerRenderer.java:62)
	at com.arryn.frontiermode.Rendering.onClientTick(Rendering.java:65)
	at com.arryn.satchel.common.lifecycle.SatchelEventBus.post(SatchelEventBus.java:84)
	at com.arryn.satchel.common.lifecycle.ScopeLifecycleDispatcher.emitTick(ScopeLifecycleDispatcher.java:105)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.onTick(ASatchelJig.java:171)
	at com.arryn.satchel.client.lifecycle.ClientForgeIngress.onExecutionPulse(ClientForgeIngress.java:96)
```

**Root cause:** `RenderContext.getInstance()` (`client/render/level/RenderContext.java`) reads
`Minecraft.getInstance().level` and threw if null, with the author's own comment already flagging
it as unexplained: *"level cannot be null if client context is ready... oops somehow it is null
during tick. I don't think tick should be dispatching in satchel until the level has loaded and
ungated the lifetime."* Traced it: on disconnect, `Minecraft.level`/`.player` are cleared before
the client-side jig's `ScopeEvent.Tick` participation is unsubscribed (matches the "Known gaps"
note in [runtime.md](../wiki/satchel/architecture/runtime.md) that a `LevelJig` scope is never
actually unloaded — `LevelEvent.Unload` re-announces the source instead of removing it) — so
`ScopeEvent.Tick` fires at least once more with a live jig but a torn-down client context. Not a
"shouldn't happen" case at all: `GrowthTriggerRenderer.debugFlame()`, called one line above the
crash site in the same method, already has the correct pattern for this exact window (`mc.player
== null` → log "Player was null" and return, no crash — visible in the log immediately before the
stack trace) — the null-level check a few lines later just never got the same treatment.

**Fix applied:** changed `if (level == null) throw ...` to `if (level == null) return
Optional.empty();`, matching the `isNotClientSide()` gate immediately above it in the same method
and the `mc.player == null` pattern in `debugFlame()`. Verified both call sites
(`GrowthTriggerRenderer.tick()`, `WorldBordersRenderer.render()`) already handle
`Optional.empty()` via `.filter(...).orElse(null)` — no other changes needed.

**Left `in-progress`, not `done`:** need a real re-run to confirm — specifically, exiting a world
should now log nothing where it used to crash.

## Log

- 2026-08-14: Confirmed by the project owner via a real join/play/quit cycle — exiting a world no
  longer crashes. Closing. This was the one remaining item blocking RM_FRO_008 (Border prototype
  verified end-to-end) from being fully airtight — see FRO_020/SAT_031.
- 2026-08-14: Root cause traced (see above), fix applied to `RenderContext.java`. Left
  `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
