---
id: FRO_053
uid: FRO
number: 53
client: FrontierMode
status: closed
title: 'Doc pass: Karen''s grow(BlockPos) overload + defeat handler'
context: boss.md 'Defeat detection' + border.md mutation surface need updating once
  FRO_045 ships; deferred this session.
priority: normal
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[FRO_045](FRO_045_karen-build.md) ("Karen" build) item 5 calls for documenting the
`grow(BlockPos center)` overload on [Border](../wiki/frontiermode/architecture/border.md) and the
new `LivingDeathEvent` handler on [Boss](../wiki/frontiermode/architecture/boss.md)'s "Defeat
detection and the border-growth gap" section as part of the same build, mirroring how FRO_043
updated `boss.md` with Boss's own shipped shape rather than leaving the wiki describing pre-build
design only.

Wiki edits are out of scope for this Lead Dev session (owner instruction) -- this ticket carries
that doc pass forward instead of it happening inline with the code. Once done, `boss.md`'s
"Defeat detection..." section and `border.md`'s "Mutation surface" section should describe the
`grow(BlockPos center)` overload, its bootstrap-on-no-tip behavior, and the defeat handler as they
now exist -- current-state prose, no dated narration, per
[BHW — Wiki Conventions](../wiki/meta/bhw.md).

## Log

- 2026-08-28: Ticket opened.

- 2026-08-28: **Naming corrected (Architect), no status change.** `growCenteredOn` throughout this
  ticket meant a method that was never built and is no longer the plan -- see
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s 2026-08-28 correction entry and
  [FRO_052](FRO_052_growcenteredon-no-tip.md) (closed): the method is now the `grow(BlockPos center)`
  overload, and no-tip bootstraps rather than failing. Still gated on FRO_045 shipping, not on
  FRO_052 -- FRO_052 only settled what this doc pass needs to say once it starts.

- 2026-08-28: **Status corrected to `blocked` (Architect).** Was sitting as `open` with the real
  gate only stated in prose (`context`) -- `blocked` is a real status on this project's own
  lifecycle (`open -> in-progress | blocked -> done`, see `bht.md`), not just a description, and
  this ticket genuinely has nothing to write until [FRO_045](FRO_045_karen-build.md) ships:
  `grow(BlockPos center)` doesn't exist in source yet. Reopens to `open` when FRO_045 closes.

- 2026-08-28: **Closed -- superseded by a process change, not by being done as scoped.** Project
  owner's ruling: deferring the wiki update until code ships means Lead Dev builds against no
  reliable spec in the meantime -- exactly the gap this ticket's own existence was evidence of.
  Architecture pages are now written as the spec ahead of the build, not caught up after; see
  [architect.md](../roles/architect.md)'s "Wiki discipline" section. `Border` § Mutation surface
  and `Boss` § Defeat detection are updated now, directly, to describe the `grow(BlockPos center)`
  overload and the defeat handler -- not held for FRO_045 to ship first. No future ticket should
  defer a wiki update to "once code ships"; if Lead Dev's build finds the spec doesn't hold, that's
  a new "doesn't work as designed" ticket back to the Architect, same as always.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
