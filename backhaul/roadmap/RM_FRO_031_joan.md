---
id: RM_FRO_031
uid: RM_FRO
number: 31
kind: work
status: open
title: Beacons epoch 1
owner: Arryn
depends_on:
- RM_FRO_026
- RM_FRO_027
- RM_FRO_035
- RM_FRO_036
created: '2026-08-31'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Beacons epoch 1

**Player-activatable waypoints that reveal boss attunement state.** Beacons hold persistent
attunement records and are the primary attunement-system interface for players discovering the
frontier. See [Boss Discovery § Beacons](../wiki/frontiermode/design/boss-discovery.md#beacons)
for the design intent, [Boss Discovery Systems § Beacons](../wiki/frontiermode/architecture/discovery-systems.md#beacons)
for the technical shape.

## Blocker

**Blocked on attunement game-rules pass.** Beacons hold persistent attunement state, which depends
on a final decision about how attunement works at the game-rules level (e.g., stored on player or
on the boss, TTL/reset behavior, conflict resolution when multiple bosses exist). This is a
Game Designer / Project Owner call, not a technical gap. Once attunement rules are finalized,
this node can proceed to implementation.

The technical infrastructure (Navigator, Border Curve, Border Pregeneration) is ready; waiting on
design decisions, not code.

- 2026-09-07: `depends_on` corrected to include [RM_FRO_036](RM_FRO_036_donna-04.md)
  ("Donna_04") -- the `## Blocker` section above described this gate in prose since this node's
  own mint (2026-08-31) but was never wired as a graph edge, so `bhrm frontier`/`render` were
  incorrectly reporting this node ACTIONABLE. Correction, not new information -- the blocker
  itself hasn't changed.

- 2026-09-07: Roadmap graph-hygiene pass: pruned redundant `depends_on` edge(s) -- [RM_FRO_028](RM_FRO_028_diane.md) -- implied via [RM_FRO_035](RM_FRO_035_donna-03.md), which depends on 028 directly. No change to actual gating (the pruned target still has to resolve before this node can, just via the remaining edge rather than a direct one); this only removes duplicate lines from the rendered graph. `bhrm downstream`/`dependents` remain the way to see the full transitive picture now that `depends_on` lists only immediate blockers.

## Related pages

- [Boss Discovery § Beacons](../wiki/frontiermode/design/boss-discovery.md#beacons) — the design intent
- [Boss Discovery Systems § Beacons](../wiki/frontiermode/architecture/discovery-systems.md#beacons) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05
- [RM_FRO_036](RM_FRO_036_donna-04.md) ("Donna_04") — the wired attunement game-rules blocker, added 2026-09-07

## Required By

<!-- required-by:start -->
- [**RM_FRO_023**](RM_FRO_023_kathleen.md) — Tier 2: Guided loop operational
<!-- required-by:end -->
