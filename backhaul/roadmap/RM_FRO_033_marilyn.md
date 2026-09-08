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
- RM_FRO_035
- RM_FRO_036
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
for the technical shape, and [Special Compass (architecture)](../wiki/frontiermode/architecture/special-compass.md)
for the dedicated, more detailed technical-shape page this node graduated to.

## Blocker

**Blocked on attunement game-rules pass,** same as Beacons (RM_FRO_031). The Compass must store and
query player attunement state, which depends on final decisions about how attunement is structured
at the game-rules level. Once those decisions are made, this node can proceed to implementation.

- 2026-09-07: `depends_on` corrected to include [RM_FRO_036](RM_FRO_036_donna-04.md)
  ("Donna_04") -- the `## Blocker` section above described this gate in prose since this node's
  own mint (2026-08-31) but was never wired as a graph edge, so `bhrm frontier`/`render` were
  incorrectly reporting this node ACTIONABLE. Correction, not new information -- the blocker
  itself hasn't changed.

- 2026-09-07: Dedicated architecture page [Special Compass](../wiki/frontiermode/architecture/special-compass.md)
  (published earlier the same day) linked in below as this node's technical-shape reference,
  alongside the original hub-page section it graduated from -- unrelated to the `depends_on` fix
  above.

- 2026-09-07: Roadmap graph-hygiene pass: pruned redundant `depends_on` edge(s) -- [RM_FRO_028](RM_FRO_028_diane.md) -- implied via [RM_FRO_035](RM_FRO_035_donna-03.md), which depends on 028 directly. No change to actual gating (the pruned target still has to resolve before this node can, just via the remaining edge rather than a direct one); this only removes duplicate lines from the rendered graph. `bhrm downstream`/`dependents` remain the way to see the full transitive picture now that `depends_on` lists only immediate blockers.

## Related pages

- [Boss Discovery Systems § Special Compass](../wiki/frontiermode/architecture/discovery-systems.md#special-compass) — the original hub-page section this graduated from
- [Special Compass (architecture)](../wiki/frontiermode/architecture/special-compass.md) — the dedicated, more detailed technical-shape page
- [RM_FRO_031](RM_FRO_031_joan.md) (Beacons) — shares the same attunement blocker
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05
- [RM_FRO_036](RM_FRO_036_donna-04.md) ("Donna_04") — the wired attunement game-rules blocker, added 2026-09-07

## Required By

<!-- required-by:start -->
- [**RM_FRO_023**](RM_FRO_023_kathleen.md) — Tier 2: Guided loop operational
<!-- required-by:end -->
