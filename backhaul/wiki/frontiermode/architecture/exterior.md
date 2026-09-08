---
id: frontiermode/architecture/exterior
category: frontiermode/architecture
slug: exterior
title: Exterior
summary: 'Technical shape for the Frontier''s computed boundary and the Exterior past
  it: the distance-to-Frontier query, and how Frontier Sickness and Feral are proposed
  to ride Border/Boss''s existing per-player and spawn-hook infrastructure.'
keywords: null
status: draft
updated: '2026-09-08'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Exterior

Architecture counterpart to [Exterior](../design/exterior.md) — that page settles what a player
finds past the Frontier (nothing, plus Frontier Sickness and Feral); this page settles the one
piece it explicitly hands to the Architect: **how "distance past the Frontier" is actually
computed**, given that the Frontier is the irregular, overlapping union of every Border
established so far, not a single circle. It also sketches, at proposal stage, how Frontier
Sickness and Feral are meant to plug into infrastructure Border and Boss already have, rather than
standing up new machinery — the same "reuse before you build" discipline [Boss Discovery
Systems](discovery-systems.md) already applies to Tier 2's six tools.

**Everything on this page is proposal, not ruling** — no `RM_FRO` node exists for any of it yet,
consistent with [Boss Discovery Systems](discovery-systems.md)'s own precedent of working shape
out in the wiki before minting one.

## Frontier becomes a named, computed aggregate

[Border](border.md#design-vocabulary-bridge)'s own vocabulary bridge names **Frontier** as "the
union of all Borders established so far" and flags it as "not currently a named object or
computed aggregate anywhere in this architecture — still genuinely open." This page closes that:
Frontier is not persisted state and never needs to be — it's a query over `BordersFixture`'s
existing border list (`PATH`/`CRUD`/`INFO` already expose everything a query needs; no new fixture
field), answerable on demand the same way `getRelevant()` already answers "which Border applies
here." **Every established Border counts, not just on-path ones** — the design's own definition is
the union of every Border, and an off-path or hand-placed one (a debug trigger, an admin-issued
`addBorder`) has just as much claim to being "claimed land" as one sitting on `borderPath`. A query
over the fixture's full border list, not the path, is what that definition actually asks for.

## The distance-to-Frontier query

**This is simpler than it looks, and the "irregular boundary" framing is worth being precise
about why.** Finding the *exact nearest point* on an irregular, overlapping union's boundary is
real computational geometry. Finding the *distance* to that boundary is not the same problem, and
doesn't require solving the harder one first: for any point outside every Border (which is exactly
the condition for being in the Exterior at all), the distance from that point to the union of all
Borders is the minimum, over every individual Border, of the distance to that Border's own edge.
This holds regardless of how the Borders overlap, how irregular the combined shape is, or even
whether their union encloses an unclaimed pocket nowhere near the outermost edge — a point sitting
in such a pocket is still correctly answered by the same minimum-over-all-Borders rule, because
nothing about "distance to a union of shapes" depends on the union's overall topology, only on each
member shape individually. The felt difficulty in the design page's framing is about locating
*where* on the boundary a player would cross back in, which genuinely would need the harder
computation — but Frontier Sickness and Feral (per [Exterior](../design/exterior.md#frontier-sickness))
only ever consume the scalar distance, never a crossing point, so the harder problem is never
actually needed.

**`BorderMath` gains one new primitive:** a disc-edge distance, `distanceOutside(BlockPos p,
BlockPos center, int radius)` returning `max(0, distanceTo(p, center) - radius)` — zero when `p`
is inside or on the disc, the XZ gap otherwise (vertical position doesn't enter into it, matching
every other Border geometry query — a Border is a full-height cylinder). This sits alongside
`isInside` and the other point/disc primitives `BorderMath` already owns; it isn't a new kind of
math for this codebase, just a gap in the existing set.

**Frontier distance at a point, then, is:** `min` over every `Border` in `BordersFixture`'s list
of `distanceOutside(point, border.center(), border.radius())`. A result of `0` (or the point
matching `isInside` for at least one Border) means the point isn't in the Exterior at all; a
positive result is exactly the raw-distance value [Exterior](../design/exterior.md#frontier-sickness)
specifies as the single input driving both Frontier Sickness's severity target and Feral's
scaling. `O(n)` in the number of established Borders — the same cost `getRelevant()` and
`bordersContaining()` already pay per query, not a new performance category this design
introduces.

## Where this lives

**Proposed home: an additional derived field on `BorderPlayerStatusFixture`, computed in the same
per-player, per-tick pass `BorderModule`'s `onPlayerScopeTick` handler already runs** (see
[Border § Known gaps](border.md#known-gaps)) — not a new `JigConfig`, not a new tick. That handler
already walks the level's live border list every tick to derive each player's nearest-border/
distance/inside snapshot; a Frontier-distance value is a second reduction over the same list on
the same pass, not a second walk. Worth being precise that it's a *second* reduction, not a reuse
of the existing one: today's nearest-border distance is (per [Border
Vocabulary](border-vocabulary.md#relevance)'s Relevance definition) resolved only among Borders
*containing* the point, tie-broken by Layer — the right notion for "which Border applies to me
right now," wrong for "how far past every Border am I," which has to consider every Border whether
or not it contains the point. Two aggregations, one shared iteration.

**No Boss dependency at all — the cleanest dependency shape of anything in this cluster.**
Guardian Mobs still has to ask "where's the nearest boss" (a `BossFixture` read). Frontier
distance only ever asks Border about Border's own data; Feral, built on top of it (see below),
inherits that same independence. This is strictly cleaner than the one named exception already on
record for Boss→Border (see [Boss § Defeat detection and the border-growth
gap](boss.md#defeat-detection-and-the-border-growth-gap)) — nothing here reaches across the
dependency line in either direction.

## Consumers: Frontier Sickness and Feral

**One value, two readers**, matching [Exterior](../design/exterior.md#frontier-sickness)'s own
"one throttle, two expressions" framing: Frontier Sickness's severity target and Feral's
distance-scaling both read the same `BorderPlayerStatusFixture` field (Feral, being a mob-spawn
concern rather than a player-status concern, would read it via a `BorderAPI` accessor the way
`getRelevant()` is already read, not the fixture directly). Neither is designed in full here —
[Exterior](../design/exterior.md)'s own Open Questions leave the exact thresholds, curve, and
climb/decay rates to Lead Dev and playtest — but both have a natural fit with infrastructure this
epoch already built:

- **Frontier Sickness's severity** is stateful (a target-and-catch-up climb, not a pure function
  of the current distance snapshot alone), unlike the plain distance value above — it needs
  somewhere to persist per-player between ticks. Whether that's a second field riding
  `BorderPlayerStatusFixture` (matching its existing "cheap to recompute, no restart-survival
  requirement" precedent — losing accumulated severity on a crash/restart is a defensible
  trade-off, not obviously wrong) or its own small fixture is genuinely open, not decided here.
- **The climb/decay shape** — a value that approaches a moving target rather than snapping to it —
  is the same kind of curve [Border Curve](border-curve.md) already exists to evaluate. Whether
  Frontier Sickness's severity literally is a `BorderCurve`-style intensity curve keyed on
  Frontier-distance, or just shares the same mathematical shape without sharing the mechanism, is
  an open question worth Lead Dev checking before inventing a second curve evaluator for one more
  case.
- **Feral's spawn mechanism** — [Exterior](../design/exterior.md#feral) already proposes starting
  from the same mob-spawn-finalization hook [Guardian Mobs](guardian-mobs.md) uses. The one
  structural difference worth naming: Guardian Mobs' `BossGuardiansFixture` lives in `BossBundle`
  because its gating curve is keyed off a specific boss's home Border. Feral has no boss to key
  off of at all — its gating value is Frontier-distance, a pure Border-level query — so a
  `FeralFixture` (or equivalent) has no reason to live in `BossBundle` and every reason to sit
  alongside `BordersBundle`'s other siblings instead, the same placement logic
  [Navigator](discovery-systems.md#navigation-lives-in-border) already used to justify living in
  Border rather than standing up its own module.

## Proposed build sequencing

Project owner direction: this cluster splits into two proposed nodes, sequenced so the cheap,
material-free signals ship before the more content-heavy mob variant, and so the two detection
items are visibly parked rather than silently dropped.

- **First node — the distance infrastructure plus both tells and the debuff itself:** the
  Frontier-distance query above, the Entry Cue and Sick Wildlife tells (see
  [Exterior § Sensory design](../design/exterior.md#sensory-design) — both are already
  material-free, needing no new item or craft), and Frontier Sickness's actual debuff. Sick
  Wildlife also picks up a density note ([FRO_098](../../../tickets/FRO_098_design-note-for-sick-wildlife-passive-mo.md),
  routed to Game Designer) on top of the individual-mob glitching already designed: passive mobs
  spawning more densely with distance, not just behaving strangely once present.
- **Second node — Feral.** A full mob-variant system (tagging, team glow, its own spawn-hook
  wiring) is more build surface than the first node's three pieces combined, and nothing in the
  first node depends on it existing — sequencing it second is a build-order choice, not a
  dependency one.
- **Parked, not dropped: Edge Stone and the Diorite Wick/lantern.** [Exterior §
  Detection](../design/exterior.md#detection) stays exactly as designed; the two craftable
  detection items are deliberately sequenced after both nodes above rather than cut, since they're
  the one piece of this cluster with real art/asset dependency (a new material, a new item model,
  a modified lantern recipe) rather than pure mechanism — mechanics first, art later, not a scope
  reduction.

No `RM_FRO` node minted for either yet, consistent with this page's own proposal-stage status.

## Related pages

- [Exterior](../design/exterior.md) — the design intent this page gives technical shape to,
  including the two items ("the irregular-boundary distance problem," "Feral's spawn mechanism")
  this page answers
- [Progression & Frontier Mechanics](../design/progression.md) — the Frontier's own definition as
  the union of established Borders
- [Border](border.md) — `BordersFixture`'s border list, `BorderMath`'s existing primitives, and
  the "Design vocabulary bridge" section naming Frontier as an open aggregate
- [Border Vocabulary](border-vocabulary.md) — the Relevance/Layer distinction this page's "Where
  this lives" section leans on to explain why Frontier-distance needs its own reduction
- [Boss](boss.md) — the one named Boss→Border dependency exception this page's mechanism avoids
  needing any counterpart of
- [Boss Discovery Systems](discovery-systems.md) — the "house it in Border, not a new module"
  precedent this page's placement choices follow, and Guardian Mobs' `BossGuardiansFixture` as the
  structural comparison Feral's placement argument turns on
- [Border Curve](border-curve.md) — the intensity-curve mechanism Frontier Sickness's severity
  shape may or may not turn out to reuse
