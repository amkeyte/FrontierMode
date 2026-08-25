---
id: FRO_024
uid: FRO
number: 24
client: FrontierMode
status: done
title: Rendering eager static crashes dedicated server
context: WorldBordersRenderer built in Rendering's <clinit>; RuntimeDistCleaner blocks
  MultiBufferSource on DEDICATED_SERVER. First real runServer crash. Confirmed fixed
  -- 10+ dedicated-server sessions since, all reaching "Done" with zero recurrence.
priority: high
opened: '2026-08-16'
closed: '2026-08-16'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Reported by the project owner from `run-server/logs/latest.log` — first real dedicated-server run
(separate client/server JVMs, not integrated/singleplayer) crashed on the first server tick.

**Root cause:** `Rendering.java` (`com.arryn.frontiermode`) had an eager static field:

```java
private static final WorldBordersRenderer BORDERS_RENDERER = new WorldBordersRenderer();
```

[BorderModule](../wiki/frontiermode/architecture/border.md#runtime-wiring)'s `EventHandlers`
wires `Rendering::onClientTick` (and, as of today's
[RM_FRO_012](../roadmap/RM_FRO_012_carolyn.md) pass, `Rendering::onClientUnload`) into a
**BOTH**-applicability `LevelJigConfig` — so `ScopeEvent.Tick` invokes those methods on the server
too. Both methods have their own client-side guard (`Satchel.foundation().filter(f ->
f.side().isClient())`), but the guard runs too late: merely *entering* either static method forces
`Rendering.<clinit>` to run first, and that unconditionally constructed a `WorldBordersRenderer` —
a class whose method signatures reference `net.minecraft.client.renderer.MultiBufferSource`, which
Forge's `RuntimeDistCleaner` refuses to load on `DEDICATED_SERVER`. Crash, every time, on the first
`ScopeEvent.Tick` after world load — see stack trace in `run-server/logs/latest.log`
(`java.lang.ExceptionInInitializerError` → `RuntimeException: Attempted to load class
net/minecraft/client/renderer/MultiBufferSource for invalid dist DEDICATED_SERVER`, at
`Rendering.<clinit>(Rendering.java:33)`).

**Why this is surfacing only now:** invisible under every prior singleplayer/integrated test —
client-only classes are legitimately loadable there (`LogicalFoundation`'s own docs: "both
[foundations] are alive simultaneously in single-player"). This is the first session with a real
dedicated server + separate client JVM, and it's exactly the class of risk the
[Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md) vision page already
named: "dedicated-server deployment removes the accidental same-JVM safety net... it doesn't
introduce the risk." Pre-existing bug, not introduced by today's RM_FRO_012 pass — that pass added
a second method reference to the same already-broken class, which is what put it on the project
owner's radar, not what caused it.

**Fix applied:** `BORDERS_RENDERER` is no longer a static-final eager field. Replaced with a
lazily-initialized `bordersRenderer()` accessor, called only from `onRenderLevel` — which is
never invoked server-side (`RenderLevelStageEvent` doesn't fire on a dedicated server, and this
class's own `@Mod.EventBusSubscriber(value = Dist.CLIENT)` keeps Forge from even registering it
there). `Rendering.<clinit>` now does nothing dist-sensitive, so the class loads safely on either
side; `WorldBordersRenderer` (and by extension `MultiBufferSource`) never loads on the server at
all. Checked `GrowthTriggerRenderer`, `RenderContext`, and `RingColorPalette` for the same eager
-static-client-construction shape — none of their static fields construct anything client-
restricted (primitives, `RandomSource`, `HashMap`, `org.joml.Vector3f` — none are dist-restricted
types), so this was the only instance.

**Not yet verified against a real re-run** — no Forge/Mojang maven access in this session's
sandbox (see [FRO_023](FRO_023_playtest-checklist-batch2.md)). Project owner: please re-run
`runServer` and confirm the server reaches "Done" without this crash; if a *different* crash
surfaces afterward (same shape as SAT_027 following FRO_018 — fixing one gate often reveals the
next one that was hiding behind it), that's expected next-step information, not a sign this fix is
wrong.

## Log

- 2026-08-24: Normalized `status: closed` -> `done` per [BKHL_006](BKHL_006_closed-status.md) —
  outside BHT's `open/in-progress/blocked/done` vocabulary, no distinct meaning intended.
- 2026-08-16: **Confirmed fixed, closed.** Every dedicated-server session since the fix (10+
  rolled log files, plus the freshest `latest.log` from today's RM_FRO_013 malformed-entry test)
  reaches `Done (...)!` with zero occurrences of `Rendering.<clinit>`, `MultiBufferSource`, or
  `RuntimeDistCleaner` anywhere in the logs. No recurrence, no different crash surfacing behind
  it either.
- 2026-08-16: Root cause traced from `run-server/logs/latest.log`, fix applied to `Rendering.java`
  (Lead Dev, Curtis). Left `open`, not `done` — pending a real re-run to confirm, same pattern as
  FRO_018.
- 2026-08-16: Ticket opened, reported by project owner from a real dedicated-server crash log.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
