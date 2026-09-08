---
id: RM_FRO_036
uid: RM_FRO
number: 36
kind: work
status: open
title: Donna epoch maintenance 4
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-09-07'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Donna epoch maintenance 4

**Maintenance container, not a feature deliverable.** Middle-of-epoch container -- opened now
because two real siblings, [RM_FRO_031](RM_FRO_031_joan.md) ("Beacons") and
[RM_FRO_033](RM_FRO_033_marilyn.md) ("Marilyn," Special Compass), each carry a hard `## Blocker`
section in prose -- a pending attunement game-rules pass -- that had never been represented as a
`depends_on` edge. Per [BHRM -- Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
("wiring is real, not decorative"), a real blocker earns a real edge; this container exists to
give that blocker a graph node to wire onto instead of leaving it as text a reader has to notice
on their own. No `ticket:` field on purpose -- this container tracks a pending design ruling, not
a build; a ticket only makes sense once the game-rules pass itself is scoped as real work.

**What's blocked and why:** both nodes need a Game Designer / Project Owner ruling on attunement's
game-rules mechanics (where state lives, TTL/reset behavior, conflict resolution across multiple
bosses) before implementation can proceed -- a design decision, not a technical gap. Neither node's
own infrastructure dependencies (Navigator, Border Curve, Border Pregeneration) are in question --
both are otherwise ready.

**Deliberately not pulled in:** [RM_FRO_032](RM_FRO_032_elizabeth.md) (Tracker) carries a
`## Deferral`, not a blocker -- intentionally deferred in priority, correctly ACTIONABLE as-is.
[RM_FRO_034](RM_FRO_034_virginia.md) (Warps) carries `## Open Questions` that say outright "not a
formal blocker" -- also correctly ACTIONABLE. Neither belongs on this container.

- 2026-09-07: Node opened, wired as a `depends_on` edge onto [RM_FRO_031](RM_FRO_031_joan.md) and
  [RM_FRO_033](RM_FRO_033_marilyn.md) in the same pass. Both nodes' existing `## Blocker` prose
  already described exactly this gate at mint time (2026-08-31) -- the blocker itself isn't new,
  only its graph representation was missing. Found during a routine dependency-graph review:
  `bhrm frontier`/`render` were reporting both ACTIONABLE despite the hard prose blocker, since
  nothing wired it as an edge.

## Required By

<!-- required-by:start -->
- [**RM_FRO_031**](RM_FRO_031_joan.md) — Beacons epoch 1
- [**RM_FRO_033**](RM_FRO_033_marilyn.md) — Special Compass epoch 1
<!-- required-by:end -->
