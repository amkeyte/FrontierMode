---
id: RM_FRO_030
uid: RM_FRO
number: 30
kind: work
status: open
title: Environmental Tells epoch 1
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

## Environmental Tells epoch 1

**Passive visual and audio signals embedded in terrain,** revealing progression and attunement
state without requiring player action. See [Boss Discovery § Environmental Tells](../wiki/frontiermode/design/boss-discovery.md#environmental-tells)
for the design intent, [Boss Discovery Systems § Environmental Tells](../wiki/frontiermode/architecture/discovery-systems.md#environmental-tells)
for the technical shape.

Now that infrastructure is complete, Environmental Tells can be scoped as a separate fixture in
`BordersBundle` with its own tick handler and a `"tell"`-purpose `BorderCurve` record, mirroring
Guardian Mobs' shape without sharing it.

## Implementation Notes

Environmental Tells are stateless discovery signals (unlike Beacons, which hold attunement state).
Each Tell is a placed entity or particle effect that reflects the ambient difficulty and/or boss
attunement state of its border, updated by a tick handler that reads the relevant fixture state.

## Related pages

- [Boss Discovery § Environmental Tells](../wiki/frontiermode/design/boss-discovery.md#environmental-tells) — the design intent
- [Boss Discovery Systems § Environmental Tells](../wiki/frontiermode/architecture/discovery-systems.md#environmental-tells) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
