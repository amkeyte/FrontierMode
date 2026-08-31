---
id: RM_FRO_032
uid: RM_FRO
number: 32
kind: work
status: deferred
title: Ender-eye-style Tracker epoch 1
owner: null
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

## Related pages

- [Boss Discovery Systems § Ender-eye-style Tracker](../wiki/frontiermode/architecture/discovery-systems.md#ender-eye-style-tracker) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
