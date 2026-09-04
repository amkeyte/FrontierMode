---
id: FRO_077
uid: FRO
number: 77
client: FrontierMode
status: done
title: BorderPregenFixture sibling access
context: '[Donna_02] Switch BorderPregenFixture to direct sibling access, not the
  facade. FRO_074#4'
priority: low
opened: '2026-09-03'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

BorderPregenFixture sibling access

Split from [FRO_074](FRO_074_cartographer-findings.md#4-borderpregenfixture-reaches-back-into-borderapicrud-from-inside-its-own-bundle----verify-if-intentional)
finding 4. `BorderPregenFixture` lives inside `BordersBundle` as a sibling of `BordersFixture`,
but reaches it via the public `BorderAPI.CRUD()` facade instead of direct sibling access -- the
only fixture found so far that calls the facade from *inside* its own bundle.

**Architect's ruling (FRO_074#4):** switch to direct sibling access. The facade exists for
external callers, not internal bundle communication. Change `BorderPregenFixture`'s calls from:

    BorderAPI.CRUD().get(borderId)          // reaching back out through the facade

to:

    bundle.fixture(BordersFixture.KEY).get(borderId)   // direct sibling

or the equivalent direct-bundle-access pattern for this fixture's own scope/bundle structure.

## Log
- 2026-09-03: Built by Lead Dev. `BorderPregenFixture.runBatch`'s one `BorderAPI.CRUD(level).flatMap(...)` call replaced with direct sibling access: `this.<BordersBundle>getBundle().get(FrontierKeys.BORDERS).flatMap(fixture -> fixture.CRUD.get(...))`, per the Architect's ruling. `BorderAPI` import dropped from the file (no longer used). No sandbox compile available (see FRO_079's log) -- verified by manual review.

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 4 for scheduling; carries the Architect's ruling verbatim. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
