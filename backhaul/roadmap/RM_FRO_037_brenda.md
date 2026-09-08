---
id: RM_FRO_037
uid: RM_FRO
number: 37
kind: work
status: open
title: Frontier Sickness epoch 1
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-09-08'
superseded_by: null
ticket: FRO_099
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Frontier Sickness epoch 1

**The Exterior's core mechanism:** the Frontier-distance query (`BorderMath.distanceOutside()`),
the two material-free sensory tells (Entry Cue, Sick Wildlife), and Frontier Sickness's own
climbing debuff. See [Exterior § Frontier Sickness](../wiki/frontiermode/design/exterior.md#frontier-sickness)
for design intent and [Exterior -- architecture](../wiki/frontiermode/architecture/exterior.md)
for the technical shape (distance-to-Frontier as a min-over-all-Borders reduction, riding
`BorderModule`'s existing per-player tick -- no new fixture, no new tick).

## Notes

**First of two proposed nodes from the Exterior cluster** (see [Exterior -- architecture §
Proposed build sequencing](../wiki/frontiermode/architecture/exterior.md#proposed-build-sequencing))
-- project owner direction sequences this ahead of [RM_FRO_038](RM_FRO_038_martha.md) ("Martha,"
Feral) since it's the cheaper, fully material-free half of the cluster. Not a hard dependency in
either direction -- Martha doesn't structurally need this node's code to exist first, per that
page's own framing; it's a build-order choice.

**Parked, not scoped into this node: Edge Stone and the Diorite Wick/lantern** (see [Exterior §
Detection](../wiki/frontiermode/design/exterior.md#detection)). Deliberately sequenced after both
Exterior nodes -- the one piece of this cluster with real art/asset dependency (a new material,
item model, modified lantern recipe) rather than pure mechanism.

**Flagged for Architect, not yet resolved:** the irregular-boundary distance problem (already
answered by [Exterior -- architecture](../wiki/frontiermode/architecture/exterior.md#the-distance-to-frontier-query)
-- distance, not nearest-point, reduces to a simple min-over-Borders) and the Border Pregeneration
interaction question (whether a player-altered pocket that later falls inside a new Border needs
special candidate-validation handling) -- the latter is still genuinely open, not wired as a
`depends_on` edge since it isn't a confirmed blocker, just flagged.

## Related pages

- [Exterior](../wiki/frontiermode/design/exterior.md) -- design intent
- [Exterior -- architecture](../wiki/frontiermode/architecture/exterior.md) -- technical shape,
  including the build-sequencing direction this node follows
- [RM_FRO_038](RM_FRO_038_martha.md) ("Martha") -- the second Exterior node, Feral
- [FRO_097](../../tickets/FRO_097_exterior-design-ready.md) -- the design-ready ticket that routed
  this cluster to Architect/PM
- [FRO_098](../../tickets/FRO_098_design-note-for-sick-wildlife-passive-mo.md) -- Sick Wildlife's
  density-scaling addition, already folded into the design page this node builds from

## Log

- 2026-09-08: Build ticket opened: [FRO_099](../../tickets/FRO_099_frontier-sickness-core-build.md) (Dev (FrontierMode)). Includes an up-front fix to a live `BorderMath.distanceToSurface()` bug (squared distance minus linear radius) found while checking this node's readiness -- unrelated to Exterior itself, but sitting right next to the new `distanceOutside()` primitive this build adds.
- 2026-09-08: Node opened. Minted per project owner direction in [Exterior -- architecture §
  Proposed build sequencing](../wiki/frontiermode/architecture/exterior.md#proposed-build-sequencing)
  -- first of two proposed nodes split out of the Exterior cluster
  ([FRO_097](../../tickets/FRO_097_exterior-design-ready.md)). Wired to
  [RM_FRO_017](RM_FRO_017_donna.md) directly (Tier 1) rather than to Navigator/BorderCurve/Pregen
  (026/027/028/035) -- the architecture page is explicit this cluster has "no Boss dependency at
  all" and doesn't reach across the dependency line, unlike the Tier 2 discovery-gradient siblings
  that do use those fixtures.

## Required By

<!-- required-by:start -->
*(computed — nothing depends on this yet)*
<!-- required-by:end -->

