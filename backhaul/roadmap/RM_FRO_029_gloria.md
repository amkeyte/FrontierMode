---
id: RM_FRO_029
uid: RM_FRO
number: 29
kind: work
status: open
title: Guardian Mobs epoch 1
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

## Guardian Mobs epoch 1

**First discovery-gradient tool, scoped against completed infrastructure.** The ambient-difficulty
signaler that starts a player on the discovery gradient -- a mob or small group placed at
calculated distances (via Border Curve placement curve) with scaled stats (via Border Curve
difficulty curve). See [Boss Discovery § Guardian Mobs](../wiki/frontiermode/design/guardian-mobs.md)
for the design intent, [Boss Discovery Systems § Guardian Mobs](../wiki/frontiermode/architecture/discovery-systems.md#guardian-mobs)
for the technical shape.

Now that [RM_FRO_026](RM_FRO_026_dorothy.md) (Navigator), [RM_FRO_027](RM_FRO_027_janet.md) (Border
Curve), and [RM_FRO_028](RM_FRO_028_diane.md) (Border Pregeneration) are built, this node can be
scoped against real code: the placement and difficulty curves that `BorderCurveFixture` now
provides, `BordersBundle`'s fixture collection, and `BorderPregenFixture`'s validated terrain.

## Open Questions

**Which borders introduce Guardian Mobs, and with what intensity curves?** Border Curve infrastructure
is ready; the Game Designer decision on how many of the frontier's borders should spawn Guardians,
at what placement/difficulty curve settings, still needs to land. Not a blocker on implementation,
just on which borders' config actually uses this tool.

## Related pages

- [Boss Discovery § Guardian Mobs](../wiki/frontiermode/design/guardian-mobs.md) — the design intent
- [Boss Discovery Systems § Guardian Mobs](../wiki/frontiermode/architecture/discovery-systems.md#guardian-mobs) — the technical shape
- [Border Curve § Guardian Mobs worked example](../wiki/frontiermode/architecture/border-curve.md#guardian-mobs-worked-example) — the concrete shape in code
- [RM_FRO_026](RM_FRO_026_dorothy.md) (Navigator), [RM_FRO_027](RM_FRO_027_janet.md) (Border Curve), [RM_FRO_028](RM_FRO_028_diane.md) (Border Pregeneration) — the infrastructure this depends on
