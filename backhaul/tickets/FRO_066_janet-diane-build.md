---
id: FRO_066
uid: FRO
number: 66
client: FrontierMode
status: open
title: Build Border Curve + Pregen
context: RM_FRO_027 (Janet) + RM_FRO_028 (Diane). Known open items on Diane -- proceed
  anyway, flag back.
priority: high
opened: '2026-08-31'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

**For Curtis (Lead Dev) -- the remaining two of this cluster's three shared-infrastructure
nodes. Pushed regardless of open blockers listed below; don't wait for them to close first.**

**[RM_FRO_027](../roadmap/RM_FRO_027_janet.md) ("Janet" -- Border Curve).** Spec:
[Border Curve](../wiki/frontiermode/architecture/border-curve.md). `BorderCurveFixture`, a new
sibling fixture in `BordersBundle`: zero-to-many named intensity curves per border, evaluated
through `BorderMath`. No known blockers to starting; exact `BorderMath` signatures and curve
parameter shape are Lead Dev's call within the spec's shape, per that page's own Open Questions.

**[RM_FRO_028](../roadmap/RM_FRO_028_diane.md) ("Diane" -- Border Pregeneration).** Spec:
[Border Pregeneration](../wiki/frontiermode/architecture/border-pregeneration.md). The least
settled of the trio -- known open items, listed on the node itself and its spec page's Open
Questions, are **not blockers**: build against the spec as written, flag back (a ticket to the
Architect, not a wiki edit) on anything that doesn't hold up in practice rather than working
around it silently. Worth knowing before starting: this page **partially supersedes
[Boss](../wiki/frontiermode/architecture/boss.md)'s** Spawn algorithm/Data model/Three questions
sections (`boss.md` already reflects the superseding text) -- if Boss code already exists, this
is a real behavior change to it, not new-territory-only work.

Neither node depends on [RM_FRO_026](../roadmap/RM_FRO_026_dorothy.md) ("Dorothy" -- Navigator,
ticketed separately as [FRO_065](FRO_065_dorothy-build.md)) at the code level -- all three are
independent sibling fixtures in `BordersBundle` minted together only because they were designed
together. Work on any order that's convenient.

## Log

- 2026-08-31: Ticket opened, immediately after RM_FRO_026/FRO_065 -- second half of the Donna
  epoch shared-infrastructure cluster, pushed per project owner's explicit instruction to fill out
  and push regardless of remaining blockers.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
