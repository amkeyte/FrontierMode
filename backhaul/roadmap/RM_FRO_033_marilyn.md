---
id: RM_FRO_033
uid: RM_FRO
number: 33
kind: work
status: open
title: Special Compass epoch 1
owner: Arryn
depends_on:
- RM_FRO_026
- RM_FRO_027
- RM_FRO_028
- RM_FRO_035
created: '2026-08-31'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Special Compass epoch 1

**An attunement-aware navigation tool that points toward the nearest boss whose attunement the
player has unlocked.** Distinct from the Ender-eye-style Tracker (which is stateless); the Compass
holds and consults persistent attunement state. See [Boss Discovery Systems § Special Compass](../wiki/frontiermode/architecture/discovery-systems.md#special-compass)
for the technical shape.

## Blocker

**Blocked on attunement game-rules pass,** same as Beacons (RM_FRO_031). The Compass must store and
query player attunement state, which depends on final decisions about how attunement is structured
at the game-rules level. Once those decisions are made, this node can proceed to implementation.

## Related pages

- [Boss Discovery Systems § Special Compass](../wiki/frontiermode/architecture/discovery-systems.md#special-compass) — the technical shape
- [RM_FRO_031](RM_FRO_031_joan.md) (Beacons) — shares the same attunement blocker
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05
