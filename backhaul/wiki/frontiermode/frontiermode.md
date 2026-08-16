---
id: frontiermode/frontiermode
category: frontiermode
slug: frontiermode
title: FrontierMode
summary: Gameplay and world-tuning modifications for Minecraft; depends on Satchel
  for core data/utility support.
keywords: null
status: verified
updated: '2026-08-16'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · frontiermode
<!-- bh-header:end -->

# FrontierMode

Gameplay and world-tuning modifications for Minecraft.

## Identity

Pulled from `FrontierMode/src/main/resources/META-INF/mods.toml` (the actual source of truth —
`README.txt` in this repo is stock Forge MDK installation boilerplate and does not describe the
mod; see [FRO_001](../../tickets/FRO_001_readme-boilerplate.md)).

- **Mod ID**: `frontiermode`
- **Display name**: Frontier Mode
- **Version**: 1.0.0
- **Author**: Arryn
- **License**: All Rights Reserved
- **Minecraft**: `[1.20.1,1.21)` on Forge `[47,)`

## Dependencies

- **Satchel** (`satchel`, `[0.0.1,)`) — mandatory, ordering `AFTER`. FrontierMode loads after
  Satchel and relies on it for core data/utility support. See [Satchel](../satchel/satchel.md). The dependency itself
  is legitimate; how it's wired in `build.gradle` (a hardcoded absolute-path `flatDir` repo) is
  flagged as known debt — see [FRO_004](../../tickets/FRO_004_satchel-dep-debt.md). **Not**
  fixed by unifying FrontierMode and Satchel into one Gradle build — ForgeGradle constraints
  make combining multiple mod subprojects in one multi-project build a known source of friction,
  so the two stay as separate Gradle projects deliberately (see FRO_004 log for the full
  reasoning). They do share one git repo at the mcRepos root, which is a separate,
  version-control-only decision.

## Architecture

- [Border](architecture/border.md) — the world-border system, FrontierMode's one substantial
  feature.

## Design

Creative vision and player-experience intent for the mode, maintained by the Game Designer role
(no source access — see [Game Designer](../../roles/game-designer.md)):

- [Frontier Mode Overview](design/overview.md) — mode identity and design pillars;
  start here.
- [Progression and Frontier Mechanics](design/progression.md)
- [Boss Discovery](design/boss-discovery.md)
- [Guardian Mobs](design/guardian-mobs.md)
- [Nether and End](design/nether-and-end.md)
- [Multiplayer Sketch (Parked)](design/multiplayer-sketch.md) and
  [Nethack Ideas (Parked)](design/nethack-ideas-parked.md) — explicitly out of
  current scope, kept on record.

The existing [Border](architecture/border.md) architecture already maps closely onto the
progression design's expansion model (ordered border path, per-change rule evaluation,
client-side rendering) — worth Architect confirming how directly it can be built on.

## Roadmap

Tracked under `RM_FRO` — see [ROADMAP_INDEX.md](../../ROADMAP_INDEX.md) for current status.
Convergence nodes follow the tier framework in [FrontierMode Operational
Tiers](../plans/operational-tiers.md) — player-facing experience thresholds that cross module
lines, rather than one convergence per module.

## Related pages

- [Satchel](../satchel/satchel.md)
- [Border](architecture/border.md)
- [FrontierMode Operational Tiers](../plans/operational-tiers.md)
