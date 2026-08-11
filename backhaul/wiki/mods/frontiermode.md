---
id: mods/frontiermode
category: mods
slug: frontiermode
title: FrontierMode
summary: Gameplay and world-tuning modifications for Minecraft; depends on Satchel
  for core data/utility support.
keywords: null
status: draft
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · mods
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
  Satchel and relies on it for core data/utility support. See [Satchel](satchel.md). The dependency itself
  is legitimate; how it's wired in `build.gradle` (a hardcoded absolute-path `flatDir` repo) is
  flagged as known debt — see [FRO_004](../../tickets/FRO_004_satchel-dep-debt.md). **Not**
  fixed by unifying FrontierMode and Satchel into one Gradle build — ForgeGradle constraints
  make combining multiple mod subprojects in one multi-project build a known source of friction,
  so the two stay as separate Gradle projects deliberately (see FRO_004 log for the full
  reasoning). They do share one git repo at the mcRepos root, which is a separate,
  version-control-only decision.

## Sanitization

**Update ([FRO_002](../../tickets/FRO_002_strip-bare.md)/[FRO_003](../../tickets/FRO_003_root-cleanup.md),
2026-08-11):** repo stripped to bare necessity — git history removed (clean init pending), IDE/
build artifacts removed, `maven-publish` and the `apiDumpFrontier` task (which depended on
DocletProject) removed. DocletProject itself deleted from the workspace. See
[backhaul/wiki/plans/strip-down.md](../plans/strip-down.md).

## Architecture

- [Border](../frontiermode/architecture/border.md) — the world-border system, FrontierMode's
  one substantial feature (added 2026-08-11; first architecture page for this mod).

## Roadmap

Tracked under `RM_FRO`. Backfilled 2026-08-11 with reconstructed history
(`RM_FRO_001`–`RM_FRO_007`) reflecting the border feature, its jig/command/rendering wiring, and
the strip-down pass — see [backhaul/wiki/plans/strip-down.md](../plans/strip-down.md)'s deferred
"roadmap backfill" note. One node, `RM_FRO_006` (per-player border evaluation), is left genuinely
open: real logic classes exist but the bundle that would wire them in is dead/commented-out code.
That is currently the only actionable node in this graph.

## Related pages

- [Satchel](satchel.md)
- [Border](../frontiermode/architecture/border.md)
