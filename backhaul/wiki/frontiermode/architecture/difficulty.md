---
id: frontiermode/architecture/difficulty
category: frontiermode/architecture
slug: difficulty
title: Difficulty
summary: 'Design pass for the Difficulty seam on BorderRules (FRO_029 Phase 4): the
  Layer-to-Difficulty formula, the ambient-vs-boss selector split, and the BorderPlayerStatus
  reshape it depends on.'
keywords: null
status: draft
updated: '2026-08-20'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Difficulty

The Difficulty seam on `BorderRules` — `layerToDifficulty` and `ambientDifficultyAt` — and the
`BorderPlayerStatus` shape it reads from. See [Border Vocabulary](border-vocabulary.md#difficulty)
for the concept this implements, and [Border](border.md#design-vocabulary-bridge) for where it fits
alongside Relevance/Layer/Path. Tracked on
[FRO_029](../../../tickets/FRO_029_border-vocab-conformance.md).

## Why this needs its own seam, not a raw Layer read

Border Vocabulary is explicit that Layer is *one* legitimate input to a difficulty determination,
not the definition of difficulty — difficulty can exist with no Layer or Border involved at all.
Today, every difficulty-shaped decision in code reads `layerIndex` directly instead: `boss.md`'s
stat-scaling note (which reads the boss's own recorded `layer`, not a live lookup) and
`BorderPlayerStatus.layerIndex()` (see "Fixing item 2.4" below) both
hand a raw Layer to their caller and expect the caller to know what to do with it. Nothing names
"convert a Layer into a difficulty" as its own step, which is exactly what item 2.1 found missing
and item 2.2 found nothing to plug into.

There are two genuinely different selectors feeding what can still be one shared formula, per
Border Vocabulary's own split:

- **Ambient difficulty at a point** — `Point → Relevant Border → Layer → difficulty`. Dynamic,
  re-evaluated per query.
- **Boss difficulty** — `Border (the boss's own home border) → Layer → difficulty`. No spatial
  resolution at all — deliberately skips Relevance, per the earlier noodling session on why a boss
  shouldn't get a difficulty discount just because it happened to land in a safe pocket.

## Two new methods on `BorderRules` — not a separate interface

**Revised, 2026-08-20:** the first version of this page proposed a sibling `DifficultyRules`
interface, mirroring `BorderRules`'s own shape one-for-one. Project owner's call: one rules helper
per module, not two — and there's no concrete need today that actually requires the split.
`ambientDifficultyAt` is already just `getRelevant()` plus one more step, so keeping it apart from
`BorderRules` split something that's naturally one lookup into two interfaces to import for no
current benefit. The two methods below land directly on `BorderRules`/`DefaultBorderRules`
instead — grouped under their own doc-comment section (`// --- Difficulty ---` or equivalent) so
the vocabulary distinction (Difficulty is broader than Layer, not defined by it) stays visible in
the source even though it's one Java type now. `BorderRules` grows to ten methods as a result — a
real, if minor, cost, worth naming rather than pretending away — and if a genuinely non-Border-
sourced difficulty need ever shows up (something with no Layer at all), that's the point to split
it back out, not before.

Two methods, matching the two selectors above — deliberately not one:

- **`int layerToDifficulty(int layer)`** — the one shared formula. Placeholder body for this pass:
  identity (`return layer;`) — a "safe baseline, replace later" default in the same spirit as
  `DefaultBorderRules.GROWTH_FACTOR` and `boss.md`'s own placeholder stat table, not a locked
  curve. Boss code calls this directly with its own recorded `layer` — no Border, no position, no
  Relevance involved.
- **`OptionalInt ambientDifficultyAt(List<Border> containing, BlockPos pos)`** — the ambient-case
  convenience wrapper: resolves Relevance internally via this same interface's own
  `getRelevant(containing, pos)`, and if a Relevant border exists, feeds its layer into
  `layerToDifficulty` and returns the
  result. Returns empty when nothing is Relevant at that point (mirrors `getRelevant()`'s own
  `null`-for-nothing-contains-this-point contract, just typed as `OptionalInt` rather than a
  nullable). **Deliberately does not take a raw layer** — the whole point of exposing this as a
  second method instead of making every ambient caller compose `getRelevant()` +
  `layerToDifficulty()` themselves is to keep the Relevance-resolution step from being
  reimplemented, or skipped, at each call site. Exact return type (`OptionalInt` vs. a small
  dedicated wrapper) is Lead Dev's call — the requirement is that "no Relevant border here" and "a
  Relevant border here with difficulty N" are two distinguishable outcomes, not that a sentinel
  int is picked.

## Fixing item 2.4 first — `BorderPlayerStatus`'s overloaded `layerIndex()`

This has to land before `ambientDifficultyAt` is useful to per-player code, because right now
there's no way for a caller of `BorderPlayerStatus`/`BorderPlayerEval` to tell which of two
different things `layerIndex()` is reporting. Confirmed against current source
(`BorderPlayerLogic.evaluate()`): when `insideNearest` is true, `layerIndex` is the *Relevant*
border's layer (a real `getRelevant()` resolution). When `insideNearest` is
false, `layerIndex` is whichever border's *surface* is nearest instead — a different, weaker
selection that Border Vocabulary doesn't define Relevance for at all — returned under the exact
same field name.

**Proposed reshape**, on both `BorderPlayerEval` (record) and `BorderPlayerStatus` (its fixture
wrapper):

- Split the single `layerIndex` field into two: **`relevantLayer`** (`OptionalInt`, or an
  equivalent nullable-int shape — populated only when `insideNearest` is true, i.e. only when a
  real Relevance resolution happened) and **`nearestLayer`** (`int`, always populated — the
  fallback border's layer, kept under a name that doesn't imply Relevance).
- `BorderPlayerLogic.evaluate()`'s two branches change accordingly: the `relevant != null` branch
  populates `relevantLayer` (present) and can leave `nearestLayer` equal to the same value, since
  the Relevant border *is* the nearest border in that branch; the fallback branch populates
  `nearestLayer` only, leaving `relevantLayer` empty.
- `BorderPlayerStatusFixture.compute()` is the only real caller in `FrontierMode/src`, so the
  split is a shape change making existing behavior visible in the type, not a behavior change.

Once this lands, `ambientDifficultyAt`'s per-player use becomes direct: `status.relevantLayer()`
already *is* the `OptionalInt` `ambientDifficultyAt` expects as its resolved-Relevance case — a
caller with a `BorderPlayerStatus` in hand doesn't need to re-derive Relevance at all, just feed
`relevantLayer` (if present) through `layerToDifficulty` directly.

## The fairness-signal formula, now implementable

[Progression & Frontier
Mechanics](../design/progression.md#bosses-can-appear-in-old-territory)'s "boss significantly
exceeds ambient difficulty" fairness signal composes directly from these two methods:

```
BorderRules.ACTIVE.layerToDifficulty(bossFixture.layer)
    > BorderRules.ACTIVE.layerToDifficulty(relevantLayerAtBossSpawnPos)
```

where the right-hand side comes from `ambientDifficultyAt(containing, bossSpawnPos)` evaluated at
the boss's own spawn position, not the player's current position — the signal is about the
*boss's* neighborhood, not wherever the player happens to be standing when it fires. What counts as
"significantly" exceeds (a flat threshold on the two `int` values, a ratio, something else) is
still genuinely open — this page unblocks the comparison, it doesn't pick the threshold. That's
Game Designer/playtest territory, same as `layerToDifficulty`'s own curve.

## Radius growth (item 2.2) — a named seam, not required by this pass

`DefaultBorderRules.chooseNextRadius`'s flat `GROWTH_FACTOR` and `chooseInitialRadius`'s own
`// Future: could vary based on level type, difficulty, etc.` comment are both named in the
checklist as a place the Difficulty seam could eventually plug in — radius and Difficulty are
vocabulary-distinct concepts that happen to both currently key off Layer/creation-order, not the
same thing. Nothing in this pass requires wiring `layerToDifficulty` into radius growth; flagging
it here as a legitimate future extension point so it doesn't get rediscovered from scratch, not
specifying it now. `chooseNextRadius`/`chooseInitialRadius` stay exactly as they are for this
ticket.

## Related pages

- [Border Vocabulary](border-vocabulary.md) — the concept this implements
- [FRO_029](../../../tickets/FRO_029_border-vocab-conformance.md) — the conformance sweep whose
  Section 2 findings this page addresses; its working checklist page was retired into the ticket's
  own log once dispositioned (Phase 6)
- [Border](border.md) — `BorderRules`, `getRelevant()`, and the Design vocabulary bridge section
- [Boss](boss.md) — the `layer`-scaling consumer this seam is built for
- [Progression and Frontier Mechanics](../design/progression.md) — the fairness-signal design item
  this unblocks
