---
id: RM_SAT_007
uid: RM_SAT
number: 7
kind: convergence
status: reached
title: Satchel 2.0 persistence model
owner: Arryn
depends_on:
- RM_SAT_003
- RM_SAT_004
- RM_SAT_005
- RM_SAT_006
- RM_SAT_008
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Satchel 2.0 persistence model

- 2026-08-11: Node opened, backfilled as **reached** history (convergence).

Directly evidenced by [Persistence](../wiki/satchel/architecture/persistence.md) (now `verified`)
and by source: `BundleSavedData`
(`common/newstuff/BundleSavedData.java`) replaced the older `PlayerBundleSavedData`/
`ServerWorldBundleSavedData` split with one `SavedData` record per `BundleKey`. Confirmed via
grep — neither old class exists in `src/` anymore. As part of resolving
[Persistence](../wiki/satchel/architecture/persistence.md)'s drift flag, also confirmed the doc's
stated package (`server.persistence`) is stale: the live code sits
in `common.newstuff`, and `server/persistence/ServerPersistenceContext.java` is entirely
commented-out dead code left over from an earlier design (it even implements a
`ServerPersistentStitch` interface that no longer exists in source). Marked as a convergence node
(not plain "work") because it represents a cross-cutting architectural milestone — identity,
dirty propagation, and hydration all had to land together — rather than a single deliverable.
Depends on both the jig/foundation runtime (RM_SAT_003, since hydration is orchestrated through
`FixtureHydrator`/`LogicalFoundation`) and the fixture rename (RM_SAT_004, since persistence
operates on fixtures by name).

- 2026-08-15: `depends_on` widened to also name RM_SAT_005 (networking layer), RM_SAT_006
  (DocletProject tooling), and RM_SAT_008 (Jig Config compiler) — a convergence node is meant to
  be the single funnel everything downstream passes through, not one gate sitting alongside other
  parallel edges a dependent can route around. RM_SAT_009 previously depended on this node *and*
  005/006/008 directly, which defeated that purpose; those edges moved here instead, so RM_SAT_009
  now depends on this node alone. Not all four are true blocking requirements in the same sense:
  RM_SAT_006 in particular is named for ordering/traceability (RM_SAT_009 supersedes it, doesn't
  require it finished) — same nuance that used to live on RM_SAT_009's own page, carried over
  rather than lost in the move.

## Required By

*(computed — nothing depends on this yet)*
