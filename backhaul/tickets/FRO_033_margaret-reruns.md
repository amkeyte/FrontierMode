---
id: FRO_033
uid: FRO
number: 33
client: FrontierMode
status: done
title: Margaret's owed re-run sequences
context: Three specific in-game sequences RM_FRO_015 lists as owed. A green build
  does not answer them.
priority: normal
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) ("Margaret") is `resolved`, and several of its fixes
name a specific in-game sequence still owed beyond a build. Those sequences were never collected in
one place, so each is buried in the entry that produced it. A clean build (see
[FRO_032](FRO_032_build-of-0821.md)) does not answer any of them.

## The sequences

**1. `/border path grow` on a genuinely fresh level (0 borders).**
`BorderCommandHandler.pathGrow` had its own `if (borders.isEmpty()) throw` guard sitting in front of
`BorderAPI.grow(level)`, intercepting exactly the case `BordersPathFacet.grow()` exists to handle —
an absent path tip branches internally to `BorderLogic.getInitial()`. The guard was removed. Confirm
a fresh level now creates its initial border instead of reporting "No borders exist to grow."

**2. Growing into a colliding off-path layer, after the layer-uniqueness reversal.**
The project owner's 2026-08-20 ruling divorced Layer from Path definitionally and removed
`BordersCrudFacet.validateProposal`'s layer-collision check (the radius-bounds half stands).
`BordersFixture.reassignLayers` no longer bumps off-path borders clear of the reserved path range.
Confirm `/border path grow` — and the gold-block trigger, which routes through the same code —
now succeed against the world state that previously rejected, and that `/border info` and
`@relevant` still resolve sensibly with two borders legitimately sharing a layer.

**3. `@relevant` re-check after a `fixLayers()` reconciliation.**
The reconciliation itself was confirmed precisely (2-border reassignment, honest no-op case, and
the off-path bump-and-not-lost case all verified against real logs), but `@relevant` was never
re-run immediately after one. Margaret's own read is that this is low-risk — the layer values
`getRelevant()` sorts by were confirmed correct by the same pass, and `@relevant`'s own correctness
was established independently under [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md) /
[FRO_026](FRO_026_sandra-implementation.md). Included here for completeness rather than because
it's suspected broken.

Also worth folding into the same session, since it needs the same world state:
**4. The phantom-path-entry self-heal.** `reassignLayers` now removes a `borderPath` UUID with no
matching `Border` rather than logging and skipping it. Confirming that needs a world with a genuine
phantom entry, which may no longer exist — if it can't be reproduced, say so and close the item
rather than leaving it ambiguous.

## Done bar

Each sequence run against the current build, results logged on
[RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) itself (not here) so the node's own record stays
complete. Any failure gets its own ticket rather than reopening Margaret — per that node's own
standing rule, further `/border` command-surface findings get a new sibling, not a reopen.

## Log
- 20260821 - [Arryn] These have all been closed. ticket to be closed.

- 2026-08-21: **Closed per Arryn — all four sequences confirmed done.** Recording which they were,
  since this ticket existed to collect them from four separate "Still owed" clauses scattered
  through [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md)'s log: `/border path grow` on a fresh
  zero-border level; growing into a colliding off-path layer after the layer-uniqueness reversal;
  the `@relevant` re-check after a `fixLayers()` reconciliation; and the phantom-path-entry
  self-heal.

  With this and [FRO_032](FRO_032_build-of-0821.md) both closed, the "not independently
  build-verified" caveat that appeared five times across
  [RM_FRO_010](../roadmap/RM_FRO_010_susan.md)'s subtree is fully discharged — the build half on
  2026-08-21, and now the playtest half that a green build didn't answer.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Collected from four separate
  "Still owed" clauses across Margaret's log; nothing here is a new finding.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
