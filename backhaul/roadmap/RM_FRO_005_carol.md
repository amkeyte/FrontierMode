---
id: RM_FRO_005
uid: RM_FRO
number: 5
kind: work
status: resolved
title: Client-side border rendering
owner: Arryn
depends_on:
- RM_FRO_002
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Client-side border rendering

- 2026-08-11: Node opened, backfilled as resolved history.

Evidenced by `border/client/render/level/*` (`WorldBordersRenderer`, `GrowthTriggerRenderer`,
`BorderView`, `RenderContext`, `RingColorPalette`) and by the `frontier_ring` particle asset
(`assets/frontiermode/particles/frontier_ring.json` + texture). Client-only, hooked via
`border/client/hooks/PlayerTickHandler.java` and `BorderStrap`'s subscription to
`ScopeEvent.Tick` → `Rendering::onClientTick`.

## Update (2026-08-13)

Two corrections. `border/client/hooks/PlayerTickHandler.java` is entirely commented out — dead,
not an active hook, despite being cited above. And `BorderStrap`'s subscription only matters if
`SatchelStrap`/`SatchelStrapRegistrar` are live — they aren't (both dead scaffolding in Satchel);
the repo does not currently compile. `BorderView` is also dead (fully commented out) — rendering
currently runs off `WorldBordersRenderer`/`RenderContext`/`RingColorPalette`/`GrowthTriggerRenderer`
directly, not through `BorderView`. See
[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md) for the
registration-layer picture. Flagging for PM re: whether `resolved` still fits.

## Update 2 (2026-08-13)

Fixed at the registration layer. `BorderStrap`'s subscription is gone, replaced by an
`EventHandlers` builder (`.on(ScopeEvent.Tick.class, Rendering::onClientTick)`) attached to the
same `JigConfig` described in [FRO_012](../tickets/FRO_012_port-border-to-jigconfig-eventhandlers.md);
`LogicalFoundation.installConfigs()` now actually calls `.install(bus)` on it
([SAT_011](../tickets/SAT_011_wire-eventhandlers-install-into-boot.md)). The dead-code findings
above (`PlayerTickHandler`, `BorderView`) stand — those weren't part of the fix and are still not
live. `resolved` fits for the rendering pipeline itself; the dead-hook cleanup is separate,
lower-priority work, not a blocker.

## Required By

*(computed — nothing depends on this yet)*
