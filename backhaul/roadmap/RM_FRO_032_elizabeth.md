---
id: RM_FRO_032
uid: RM_FRO
number: 32
kind: work
status: open
title: Ender-eye-style Tracker epoch 1
owner: Arryn
depends_on:
- RM_FRO_026
- RM_FRO_027
- RM_FRO_035
created: '2026-08-31'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Ender-eye-style Tracker epoch 1

**A consumable item that guides players toward discovered bosses,** similar to Minecraft's
Ender Eye finding Strongholds. Stateless discovery aid, held in player inventory and activated
on demand. See [Boss Discovery Systems § Ender-eye-style Tracker](../wiki/frontiermode/architecture/discovery-systems.md#ender-eye-style-tracker)
for the technical shape.

## Deferral

**Deferred out of this design pass.** The Tracker is technically ready to implement (no attunement
state, no special infrastructure needed beyond what Navigator and Border Curve already provide),
but is intentionally deferred in priority behind Guardian Mobs and Environmental Tells, which are
earlier in the discovery gradient experience. Will be scoped and minted in a future pass once those
two are implemented and playtested.

- 2026-09-07: Roadmap graph-hygiene pass: pruned redundant `depends_on` edge(s) -- [RM_FRO_028](RM_FRO_028_diane.md) -- implied via [RM_FRO_035](RM_FRO_035_donna-03.md), which depends on 028 directly. No change to actual gating (the pruned target still has to resolve before this node can, just via the remaining edge rather than a direct one); this only removes duplicate lines from the rendered graph. `bhrm downstream`/`dependents` remain the way to see the full transitive picture now that `depends_on` lists only immediate blockers.

## Related pages

- [Boss Discovery Systems § Ender-eye-style Tracker](../wiki/frontiermode/architecture/discovery-systems.md#ender-eye-style-tracker) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05

## Required By

<!-- required-by:start -->
- [**RM_FRO_023**](RM_FRO_023_kathleen.md) — Tier 2: Guided loop operational
<!-- required-by:end -->
