---
id: RM_FRO_009
uid: RM_FRO
number: 9
kind: work
status: open
title: Clean up dead BorderView code
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Clean up dead BorderView code

- 2026-08-14: Node opened, FrontierMode-side pair to
  [RM_SAT_015](RM_SAT_015_george.md).

`BorderView` (`border/client/render/level/*` — check exact path against source) is dead: fully
commented out, not part of the live rendering pipeline despite the name suggesting otherwise (see
[Border](../wiki/frontiermode/architecture/border.md#commands-and-client-surface)). `RenderContext`
is the real shared per-level cache both live renderers (`WorldBordersRenderer`,
`GrowthTriggerRenderer`) actually read from. Pure deletion, no design call attached.

## Required By

*(computed — nothing depends on this yet)*
