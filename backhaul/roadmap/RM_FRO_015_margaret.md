---
id: RM_FRO_015
uid: RM_FRO
number: 15
kind: work
status: open
title: Border command-surface completion
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Border command-surface completion

- 2026-08-16: Node opened, sibling of [RM_FRO_009](RM_FRO_009_judith.md)/[RM_FRO_011](RM_FRO_011_betty.md)/
  [RM_FRO_012](RM_FRO_012_carolyn.md)/[RM_FRO_013](RM_FRO_013_judy.md)/[RM_FRO_006](RM_FRO_006_sandra.md)
  under [RM_FRO_010](RM_FRO_010_susan.md) ("Susan"), same convergence-gate shape (see
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md)) — depends on RM_FRO_008 directly, folded
  into Susan's own `depends_on` rather than feeding Shirley in parallel.

  Not found fresh — RM_FRO_011's own log explicitly flagged this: `BordersPathFacet.fixLayers()`
  reorders nothing and always returns `false`, because reordering `layerIndex` to match a manual
  `/border path moveup`/`movedown` reorder "interacted unsafely with [RM_FRO_011] item 2's new
  collision check" and needs "a real two-pass (or bypass) design, not something safe to guess at."
  RM_FRO_011 deliberately didn't file this node itself ("a design call, not a mechanical one... the
  project owner said not to route around architectural decisions without a ticket") — this is that
  ticket.

  Scope widened, project owner's call: rather than a single-issue "fix fixLayers" node, this is the
  standing home for the `/border` admin/dev command surface's outstanding rough edges, since more
  commands are expected here over time and opening one node per edge case doesn't scale. Currently
  in scope:
  1. **`fixLayers()` reorder design** (the trigger for this node) — `Border.layerIndex()` is
     immutable (only a fresh `BorderProposal` sets it, and `BordersCrudFacet`'s RM_FRO_011
     collision check now rejects a colliding `layerIndex`), but `pathMoveUp`/`pathMoveDown` are
     live, op-exposed commands that reorder the *path list* only — so an op can already desync path
     order from the `layerIndex` order `DefaultBorderRules.getRelevant()` (oldest-ring-wins) sorts
     by. Needs a real design: a two-pass reassignment (clear to a non-colliding scratch range, then
     assign final values) or a temporary validation bypass scoped to a single reconciliation
     transaction — Architect to specify before Lead Dev touches this again.
  2. Whatever else turns up in the `/border` command tree as new commands get added — this node is
     the standing home for that category of work, not a one-shot.

  **Explicitly not in scope, project owner's call (2026-08-16), not manufactured into work here:**
  `BorderCommandHandler.pathInsert`'s "probably crashes, but it's a TODO anyway" comment and
  `BordersFixture.requireServerSide()`'s "move this into super" dedup note. Both are real,
  pre-existing, self-documented, low-severity, and not evidenced to have caused an actual problem —
  left alone rather than folded in or ticketed. Noting them here only so a future pass over this
  node's neighborhood doesn't treat them as newly discovered.

**Design/spec first.** Per this session's steer: Architect produces the design (and a spec page if
the reorder logic ends up being a boundary contract other code depends on) before this gets handed
to Lead Dev, same as RM_FRO_011's own text asked for.

## Required By

*(computed — nothing depends on this yet)*
