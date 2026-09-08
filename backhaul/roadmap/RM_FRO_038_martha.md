---
id: RM_FRO_038
uid: RM_FRO
number: 38
kind: work
status: open
title: Feral mobs epoch 1
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-09-08'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Feral mobs epoch 1

**Corrupted, distance-scaling hostile-mob variant native to the Exterior** -- visually and
mechanically distinct from Guardian Mobs (no boss tie, no placement/difficulty curve, just
continuous scaling with raw Frontier-distance). See [Exterior §
Feral](../wiki/frontiermode/design/exterior.md#feral) for design intent and [Exterior --
architecture § Consumers](../wiki/frontiermode/architecture/exterior.md#consumers-frontier-sickness-and-feral)
for the technical shape.

## Notes

**Second of two proposed nodes from the Exterior cluster** (see [Exterior -- architecture §
Proposed build sequencing](../wiki/frontiermode/architecture/exterior.md#proposed-build-sequencing))
-- sequenced after [RM_FRO_037](RM_FRO_037_brenda.md) ("Brenda") as a build-order choice (more
build surface: tagging, team glow, its own spawn-hook wiring), not a hard dependency either
direction.

**Reuses, doesn't require completion of:** the same mob-spawn-finalization event hook [Guardian
Mobs](RM_FRO_029_gloria.md) already proved out (`FrontierMode.onMobSpawnFinalize` ->
`BossModule.onMobSpawnFinalize`), as a known-working baseline -- not a promise the two stay
mechanically identical. Reads Frontier-distance via a `BorderAPI` accessor rather than the
player-status fixture directly, since this is a mob-spawn concern, not a player-status one.

**Open, per design and architecture pages, not resolved here:** Feral's team color (sickly green
proposed) and name-tag format are Lead Dev's call; whether Feral replaces ordinary hostiles at
distance or spawns alongside them is undecided (Game Designer).

## Related pages

- [Exterior](../wiki/frontiermode/design/exterior.md) -- design intent
- [Exterior -- architecture](../wiki/frontiermode/architecture/exterior.md) -- technical shape
- [RM_FRO_037](RM_FRO_037_brenda.md) ("Brenda") -- the first Exterior node, Frontier Sickness core
- [RM_FRO_029](RM_FRO_029_gloria.md) ("Gloria") -- Guardian Mobs, the spawn-hook precedent this
  reuses
- [FRO_097](../../tickets/FRO_097_exterior-design-ready.md) -- the design-ready ticket that routed
  this cluster to Architect/PM

## Log

- 2026-09-08: Node opened. Minted per project owner direction in [Exterior -- architecture §
  Proposed build sequencing](../wiki/frontiermode/architecture/exterior.md#proposed-build-sequencing)
  -- second of two proposed nodes split out of the Exterior cluster
  ([FRO_097](../../tickets/FRO_097_exterior-design-ready.md)). Wired to
  [RM_FRO_017](RM_FRO_017_donna.md) directly (Tier 1), same reasoning as
  [RM_FRO_037](RM_FRO_037_brenda.md).

## Required By

<!-- required-by:start -->
*(computed — nothing depends on this yet)*
<!-- required-by:end -->

