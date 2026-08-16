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

- 2026-08-16: **Deleted by Lead Dev (Curtis).** Confirmed fully commented out (whole file, package
  declaration included) and confirmed via grep that nothing else in either repo referenced
  `BorderView` before deleting. No behavior change — it was never part of the live rendering
  pipeline. Unverified this session (no build access — see
  [FRO_023](../../tickets/FRO_023_playtest-checklist-batch2.md)); low-risk pure deletion per this
  node's own text, but still owed a real `gradlew build` + a quick in-world check that rendering
  (ring + growth-trigger particle) is unaffected.

## Required By

*(computed — nothing depends on this yet)*
