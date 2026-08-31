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
- RM_FRO_028
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

## Related pages

- [Boss Discovery § Beacons](../wiki/frontiermode/design/boss-discovery.md#beacons) — the design intent
- [Boss Discovery Systems § Beacons](../wiki/frontiermode/architecture/discovery-systems.md#beacons) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
