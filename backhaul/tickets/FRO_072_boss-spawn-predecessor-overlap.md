---
id: FRO_072
uid: FRO
number: 72
client: FrontierMode
status: done
title: Boss spawn biased toward border's edge
context: Boss placement now biased toward each border's own outer edge, not the whole
  disk.
priority: normal
opened: '2026-09-01'
closed: '2026-09-02'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner's live-playtest report: bosses were "very heavily grouping in the center" instead
of spreading out, and over time borders overlap enough to block player movement -- the intended
design is an organically "crawling" border path, not overlapping blobs.

**The actual mechanism (confirmed by reading source):** `BossModule.onLivingDeath` grows the next
border centered on the *just-killed* boss's own death position
(`BorderAPI.grow(level, deathLocation)`), then `DefaultBossRules.choosePosition` places the
*next* boss somewhere inside that freshly-grown border. So each new border's center is really
"wherever the previous boss ended up" -- if a boss is placed near its own border's center,
the *next* border's center barely moves from the one before it, and successive growths nest
almost concentrically instead of the path actually walking across the map. That's the real
mechanism behind "heavily grouping in the center," not a bad-RNG bug --
`BorderMath.randomPointInDisk` already samples uniformly by *area* (`sqrt(rng.nextDouble()) *
radius`), not uniformly by radius, so the classic center-clustering bug was never present.

**First pass at this ticket (superseded, see Log) wrongly assumed borders are concentric with
their predecessor** (same center, growing radius) and tried to exclude the predecessor's disk
from sampling. Project owner corrected this directly: `DefaultBorderRules.chooseNextCenter` hops
the center a fixed 30 blocks in a random direction every growth -- borders overlap, but around
two different center points, not one shared one, so there's no such thing as "the predecessor's
disk is fully inside this one" to exclude. More importantly, excluding the predecessor's disk
wasn't even the right target: since each border's *own* center is just wherever its predecessor's
boss died, the actual fix is biasing placement toward *this same border's own* outer edge, so
this boss's own position (which becomes the *next* border's center) reliably lands well away from
where this one started.

## Fix

1. `BorderMath.randomPointInAnnulus(rng, center, innerRadius, outerRadius)` -- same area-uniform
   angle/distance shape as `randomPointInDisk`, generalized to an annulus of one border's own
   disk (one center, two radii -- a real closed-form region, unlike trying to exclude a
   different, differently-centered border). `innerRadius == 0` reproduces `randomPointInDisk`'s
   own distribution exactly.
2. `DefaultBossRules.choosePosition` now samples `border`'s own annulus
   `[EDGE_BIAS_INNER_FRACTION * border.radius(), border.radius()]` (constant currently `0.6`,
   "safe baseline, replace later" per this file's own framing) instead of the whole disk --
   every candidate lands in the outer 40% of the radius, so wherever this boss ends up (and the
   next border centers on) is reliably away from this border's own center. No predecessor
   lookup needed at all -- this only ever reads `border`'s own center/radius.

**Not yet build-verified from this session** -- same device-bridge limitation as this session's
other Java fixes (no Gradle/network in this sandbox). Brace/paren balance checked on both touched
files. Needs a real playtest across several boss defeat/grow cycles to confirm the border path
actually walks outward now rather than nesting -- flagged for the project owner to confirm the
same way FRO_069/070/071 were. `EDGE_BIAS_INNER_FRACTION = 0.6` is a first guess, not a settled
number -- may need retuning once there's a real path shape to look at.

## Log

- 2026-09-01: Ticket opened, first pass fixed same session per project owner's live report --
  wrongly diagnosed as "predecessor's disk not excluded" (assumed concentric borders). Superseded
  same session, see next entry.
- 2026-09-01: **Correction, same session, per project owner's direct correction of both the
  geometry assumption and the actual design intent.** Borders are not concentric
  (`chooseNextCenter` hops 30 blocks randomly each growth); the real fix is biasing placement
  toward each border's *own* outer edge, not excluding a predecessor's disk. Reworked both
  `BorderMath.randomPointInAnnulus` (now takes one border's own center/radii) and
  `DefaultBossRules.choosePosition` (drops the predecessor lookup entirely, adds
  `EDGE_BIAS_INNER_FRACTION`). See Summary/Fix above for the corrected shape.
- 2026-09-01: Playtested by project owner across several real boss defeat/grow cycles --
  confirmed satisfactory for now. Border path walks outward rather than nesting/grouping in the
  center; EDGE_BIAS_INNER_FRACTION = 0.6 left as-is, no retuning requested. Closed.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
