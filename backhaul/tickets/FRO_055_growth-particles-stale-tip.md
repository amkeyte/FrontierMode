---
id: FRO_055
uid: FRO
number: 55
client: FrontierMode
status: open
title: Growth-trigger particles don't follow new boss loc
context: '[Susan_02] Playtest (owner): after a boss defeat grows the border, GrowthTriggerRenderer''s
  particles stay at the previous location instead of the new tip.'
priority: normal
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found live during [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen") playtesting, against
[FRO_045](FRO_045_karen-build.md)'s freshly-built defeat -> grow -> next-boss loop. Owner's report:
after a tagged boss is defeated and the border grows, the growth-trigger particle effect keeps
rendering at the previous (now-cleared) location instead of relocating to the new tip / next boss
location.

**Where this lives:** `border/client/render/level/GrowthTriggerRenderer.tick()` — client-only,
purely visual, no gameplay authority. Reads its anchor via
`RenderContext.pathTip() -> BordersPathFacet.tip()`, which returns
`fixture.borderPath.get(fixture.borderPath.size() - 1)` — the last entry in the client's own
synced `borderPath` list.

**Server-side check (done, looks correct):** `BordersPathFacet.grow(BlockPos)`
(FRO_045's new overload) does `fixture.borderPath.add(result.border().id())` — appends, so the
just-grown border becomes the new last element / new `tip()` result — then calls
`fixture.markSeeded()` / `fixture.markPathDirty()`, same as the pre-existing no-arg `grow()`. On
the server side alone, `tip()` should already report the new border immediately after a defeat.

**Not yet diagnosed further — needs Architect/Lead-Dev investigation, not assumed here:**
whether this is a client-sync timing/delivery gap (the dirty-mark not reaching the client fast
enough, or not at all, after `markPathDirty()`) or a client-side render-cache issue in
`RenderContext` (`standby()`'s `revisionMonitor.poll(...)` throttle, or a stale
`cachedPathTip`/`cachedBorders` not being invalidated on this particular mutation path).

**Worth checking together with [FRO_051](FRO_051_border-load-count-mismatch.md)** — an existing
open, unrelated-looking client/server `BordersFixture` sync-count mismatch on a fresh world,
confirmed not caused by FRO_047's diff. Different symptom, same general area (client-side
`BordersFixture` state trailing the server's) — worth a shared look rather than assuming these are
two unrelated bugs.

Not fixed here per project owner's own instruction ("let's ticket this") — logged, not
investigated live, while playtesting continues.

## Log

- 2026-08-28: Ticket opened, diagnosed against source during live playtest. Symptom, render entry
  point, and the server-side path confirmed correct on read-through above; root cause (client sync
  timing vs. render-cache staleness) still open. Possibly related to
  [FRO_051](FRO_051_border-load-count-mismatch.md) — flagged, not confirmed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
