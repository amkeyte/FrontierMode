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

## Required By

*(computed — nothing depends on this yet)*
