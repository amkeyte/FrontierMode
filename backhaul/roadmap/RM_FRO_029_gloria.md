---
id: RM_FRO_029
uid: RM_FRO
number: 29
kind: work
status: resolved
title: Guardian Mobs epoch 1
owner: Arryn
depends_on:
- RM_FRO_026
- RM_FRO_027
- RM_FRO_035
created: '2026-08-31'
superseded_by: null
ticket: FRO_096
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
for the technical shape, and [Guardian Mobs (architecture)](../wiki/frontiermode/architecture/guardian-mobs.md)
for the dedicated build-ready page this node is now ticketed against.

Now that [RM_FRO_026](RM_FRO_026_dorothy.md) (Navigator), [RM_FRO_027](RM_FRO_027_janet.md) (Border
Curve), and [RM_FRO_028](RM_FRO_028_diane.md) (Border Pregeneration) are built, this node can be
scoped against real code: the placement and difficulty curves that `BorderCurveFixture` now
provides, `BordersBundle`'s fixture collection, and `BorderPregenFixture`'s validated terrain.

## Open Questions

**Which borders introduce Guardian Mobs, and with what intensity curves?** Border Curve infrastructure
is ready; the Game Designer decision on how many of the frontier's borders should spawn Guardians,
at what placement/difficulty curve settings, still needs to land. Not a blocker on implementation,
just on which borders' config actually uses this tool.

## Log

- 2026-09-07: Dedicated architecture page [Guardian Mobs](../wiki/frontiermode/architecture/guardian-mobs.md)
  published, working the technical shape out against `BossTellFixture`'s real, shipped code.
  Dependency chain confirmed fully resolved (RM_FRO_026/027/028/035 all `resolved`) -- ticketed to
  Lead Dev the same day as [FRO_096](../tickets/FRO_096_gloria-build.md).

- 2026-09-07: Done bar met and playtest-verified via FRO_096's full build+playtest arc -- `BossGuardiansFixture` shipped as `BossBundle`'s third sibling, spawn hook confirmed against real Forge sources, curve seeding wired into all three paired-creation call sites, name-tag+glow marker and stat scaling shipped in v1. Placement sizing (`guardianPlacementRadiusFraction()` = 0.5) and spawn rate (`GUARDIAN_PLACEMENT_COEFFICIENT` = 0.6) settled from live tuning across five playtest rounds, including a self-healing fix for a real zero-spawn regression hit mid-session. Wiki drift between the shipped shape and border-curve.md's worked example / this page's own Open Questions section corrected in the same close; guardian-mobs.md flipped draft -> verified. This node's own remaining item -- which Borders actually turn Guardian Mobs on, Game Designer's call -- was never a blocker on the mechanism itself. Resolved.

- 2026-09-07: Roadmap graph-hygiene pass: pruned redundant `depends_on` edge(s) -- [RM_FRO_028](RM_FRO_028_diane.md) -- implied via [RM_FRO_035](RM_FRO_035_donna-03.md), which depends on 028 directly. No change to actual gating (the pruned target still has to resolve before this node can, just via the remaining edge rather than a direct one); this only removes duplicate lines from the rendered graph. `bhrm downstream`/`dependents` remain the way to see the full transitive picture now that `depends_on` lists only immediate blockers.

## Related pages

- [Boss Discovery § Guardian Mobs](../wiki/frontiermode/design/guardian-mobs.md) — the design intent
- [Boss Discovery Systems § Guardian Mobs](../wiki/frontiermode/architecture/discovery-systems.md#guardian-mobs) — the technical shape (hub page)
- [Guardian Mobs (architecture)](../wiki/frontiermode/architecture/guardian-mobs.md) — the dedicated, build-ready technical shape this ticket is scoped against
- [Border Curve § Guardian Mobs worked example](../wiki/frontiermode/architecture/border-curve.md#guardian-mobs-worked-example) — the concrete shape in code
- [RM_FRO_026](RM_FRO_026_dorothy.md) (Navigator), [RM_FRO_027](RM_FRO_027_janet.md) (Border Curve), [RM_FRO_028](RM_FRO_028_diane.md) (Border Pregeneration) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05

## Required By

<!-- required-by:start -->
- [**RM_FRO_023**](RM_FRO_023_kathleen.md) — Tier 2: Guided loop operational
<!-- required-by:end -->
