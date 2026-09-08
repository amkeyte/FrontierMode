---
id: RM_FRO_030
uid: RM_FRO
number: 30
kind: work
status: resolved
title: Environmental Tells epoch 1
owner: Arryn
depends_on:
- RM_FRO_026
- RM_FRO_027
- RM_FRO_028
created: '2026-08-31'
superseded_by: null
ticket: FRO_087
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

- 2026-09-06: Done bar met and playtest-verified via FRO_087's full playtest arc -- fixture registered and builds/loads cleanly, "tell" curve evaluates end-to-end driving real particle/sound spawns against live boss placements, tick handler fires on cadence with no lag warnings. Real bugs found and fixed live along the way (SAT_048's inverted `isServer()`, which had also been silently blocking the whole new-map boss/border bootstrap; intensity computed from border center instead of boss position; particle stacking at one point instead of scattering). Baseline tuning confirmed good by the project owner in playtest ("that's about where I want it"); further tuning is ordinary constant-dialing, not open epoch-1 scope. Resolved.

## Related pages

- [Boss Discovery § Environmental Tells](../wiki/frontiermode/design/boss-discovery.md#environmental-tells) — the design intent
- [Boss Discovery Systems § Environmental Tells](../wiki/frontiermode/architecture/discovery-systems.md#environmental-tells) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05

## Required By

<!-- required-by:start -->
- [**RM_FRO_023**](RM_FRO_023_kathleen.md) — Tier 2: Guided loop operational
<!-- required-by:end -->
