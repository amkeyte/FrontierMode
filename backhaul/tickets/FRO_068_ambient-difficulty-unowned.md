---
id: FRO_068
uid: FRO
number: 68
client: FrontierMode
status: open
title: Ambient mob difficulty scaling unowned
context: Design promises normal-mob difficulty scaling; nothing wires layerToDifficulty
  to mob stats yet.
priority: normal
opened: '2026-08-31'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Ambient mob difficulty scaling unowned

## Full report

Found during Game Designer's consistency scan of `frontiermode/design/*` against
`frontiermode/architecture/*` (2026-08-31). [Progression & Frontier
Mechanics](../wiki/frontiermode/design/progression.md#the-core-loop) asserts normal (non-boss)
mobs get harder with each new Border, alongside bosses. That half of the claim has no owner:

- [Difficulty](../wiki/frontiermode/architecture/difficulty.md) gives `BorderRules` a real
  `layerToDifficulty`/`ambientDifficultyAt` seam, but every named consumer of it is either Boss's
  own stat-scaling table (a *different*, boss-specific mechanism — see
  [Boss](../wiki/frontiermode/architecture/boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules))
  or the old-territory fairness-signal comparison. Nothing applies `ambientDifficultyAt` to
  ordinary hostile-mob spawns/stats anywhere.
- It's also not named in [FrontierMode Operational
  Tiers](../wiki/plans/operational-tiers.md#the-tiers) — Tier 3 lists reward/loot density, the
  fairness signal, Nether/End gating, and boss variety/tells, but not ambient mob toughness. So
  this isn't just unbuilt, it's currently untracked anywhere.

Two stacked action items, in order:

1. **PM** — decide whether/when this becomes real roadmap work (most likely folds into Tier 3
   alongside the other progression-curve items already listed there), or whether it's intentionally
   out of scope and the design page should be corrected instead.
2. **Architect**, downstream of #1 once scoped — design the actual seam connecting
   `ambientDifficultyAt` to vanilla mob spawn/stat modification, the same kind of pass
   [Boss](../wiki/frontiermode/architecture/boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules)'s
   own stat-scaling table already got. Not worth starting before item 1 is decided.

Stacked into one ticket rather than two since #2 only exists because of #1 and both trace back to
the same gap.

## Log

- 2026-08-31: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
