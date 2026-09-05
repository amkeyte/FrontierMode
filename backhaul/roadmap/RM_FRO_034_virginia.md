---
id: RM_FRO_034
uid: RM_FRO
number: 34
kind: work
status: open
title: Player-built Warps epoch 1
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

## Player-built Warps epoch 1

**Player-constructed fast-travel waypoints,** placed at discovered boss locations to enable return
without repeating discovery steps. See [Boss Discovery Systems § Player-built Warps](../wiki/frontiermode/architecture/discovery-systems.md#player-built-warps)
for the technical shape.

## Open Questions

**Is this boss-specific infrastructure, or a reuse of existing Satchel waypoint fixtures?**
The current proposal assumes player-built Warps reuse `TargetRef::RawPos` (raw coordinate storage)
with attunement-state tracking, but an existing Satchel waypoint fixture (`WaypointFixture` or
similar) may already exist. This needs a check against the Satchel codebase before proceeding.

**Attunement interaction.** Like Beacons and Compass, Warps depend on knowing which bosses the
player has discovered/attuned to. The attunement game-rules pass (currently blocking Beacons and
Compass) will inform this.

Not a formal blocker on minting (no showstopper technical gap), just an open question needing
a quick Satchel audit before scoping.

## Related pages

- [Boss Discovery Systems § Player-built Warps](../wiki/frontiermode/architecture/discovery-systems.md#player-built-warps) — the technical shape
- [RM_FRO_026](RM_FRO_026_dorothy.md), [RM_FRO_027](RM_FRO_027_janet.md), [RM_FRO_028](RM_FRO_028_diane.md) — the infrastructure this depends on
- [RM_FRO_035](RM_FRO_035_donna-03.md) ("Donna_03") — Border Pregeneration's carried-forward open items (retry/reroll, throttle budget, stalled-trigger watchdog, retroactive pregen), added 2026-09-05
