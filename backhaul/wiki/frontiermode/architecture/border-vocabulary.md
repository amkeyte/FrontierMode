---
id: frontiermode/architecture/border-vocabulary
category: frontiermode/architecture
slug: border-vocabulary
title: Border Vocabulary
summary: 'Canon terminology for FrontierMode''s Border system: Relevance, Layer, Path,
  and Difficulty, replacing overloaded use of ''level'' across the wiki and code.'
keywords: null
status: verified
updated: '2026-09-05'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border Vocabulary

The canon mapping between FrontierMode's design-side terms and the Border architecture's own —
Relevance, Layer, Path, Difficulty. Other pages in `frontiermode/*` are stated in these terms and
defer here for the definitions.

## Why this page exists

"Level" has been used across this project's design docs, architecture pages, and code comments to
mean at least four different things: a `Border` itself, the `layerIndex` field, a position in
`borderPath`, and (in [Nether and End](../design/nether-and-end.md)) a player-scoped progress
scalar that isn't a `Border` property at all. Conflating these — including by the project owner,
in earlier design passes — has produced at least one real consequence: `boss.md`'s stat-scaling
proposal names `layerIndex` as its input without being explicit about *which* border's layerIndex,
which is exactly the kind of ambiguity that reads fine until someone implements it against the
wrong one. This page names four distinct concepts so "level" can retire from architecture
vocabulary in favor of precise terms.

## The four concepts

### Relevance

The single `Border` that is effective for any runtime game decision at a given point. Already real
in code, not a proposed addition: `DefaultBorderRules.getRelevant(List<Border> containing, BlockPos
pos)`, backing `BorderAPI.getRelevant(ServerPlayer)` and the `@relevant` command selector. Resolved
fresh per query — lowest Layer wins, tie-broken by nearest center — not stored state.
(`RenderContext` caches the *result* client-side on a refresh interval; that's a performance detail,
not a change to what Relevance conceptually is.)

### Layer

`Border.layer()`: immutable, assigned once at creation (`0` for the initial border,
`previous.layer() + 1` on growth). Its only job is to be Relevance's sort key — it has exactly one
live consumer anywhere in either repo, `getRelevant()`'s comparison. Works like layers in an image
editor: a metric for ordering, closest-to-0-wins, with no inherent geometric or gameplay meaning of
its own.

**Only coincidentally tied to Path.** Under normal growth, `BordersPathFacet.grow()` assigns the next
path slot and the next `layer` from the same call, so they move together — but nothing
structurally binds them. `BordersPathFacet.moveUp()`/`moveDown()` reorder the path without ever
touching `layer` — see [Border Path & Layer Reconciliation](path-layer-reconciliation.md) for how
`fixLayers()` brings them back together. `fixLayers()` isn't an enforced invariant —
it's a "re-coincide these on request" operation for after a deliberate manual reorder pulls them
apart, not a guarantee they always agree.

**Not required to be unique.** Layer values can legitimately repeat, and nothing validates against
it. Two borders sharing a layer — even two that geometrically overlap — resolve deterministically
via the nearest-center tie-break below. This is the direct, load-bearing consequence of Layer being
"only coincidentally tied to Path" above, not a separate fact: if Layer really is independent of
Path, nothing about growing the path should ever need to avoid a layer some unrelated off-path
border happens to already hold.

### Path

`BordersPathFacet`'s ordered `borderPath` list — the order a player progresses through the game
loop. `PATH.tip()` is the current frontier edge. Growing the path pushes the
[Frontier](border.md#design-vocabulary-bridge) outward, letting players
explore farther — that's the entire mechanism behind "the frontier's shape is a record of where
the player has actually been."

**Resolved, 2026-09-04 — Path-as-implemented now matches Path-as-intended.** The
gold-block-placement growth trigger (`BordersTriggers.growPath`, the raw block-placed listener,
and `growPathCriteria`) was removed entirely
([FRO_076](../../../tickets/FRO_076_gold-block-growth-removal.md)) once Boss (RM_FRO_018/019)
made boss-defeat growth the live mechanism: `BossModule.onLivingDeath`'s defeat cascade calls
`BorderAPI.grow(level, deathLocation)`, so path growth is now centered on where a boss actually
died — player progression, not creation order.

**One deliberate, documented exception:** `/border path grow` (the manual admin command) still
grows the path boss-less by design, per project owner's ruling
([FRO_048](../../../tickets/FRO_048_pathgrow-no-boss.md),
[FRO_063](../../../tickets/FRO_063_boss-can-a-path-layer-legitimately-be-bo.md)) — a deliberate
two-step admin workflow (grow, then `/boss attach`,
[FRO_082](../../../tickets/FRO_082_boss-attach-build.md)), not a stand-in awaiting a fix.

### Difficulty

Broader than Layer, deliberately. Layer is *one* legitimate input to a difficulty determination,
not the definition of difficulty — difficulty can exist with no Layer or Border involved at all (a
boss's own hand-tuned stat block, an item's danger rating, a biome effect).

In practice this splits into two distinct questions, with two distinct selectors feeding what can
still be a single shared difficulty formula:

- **Ambient difficulty at a point** — `Point → Relevant Border → Layer → difficulty`. Dynamic,
  re-evaluated per query. Governs ordinary mob toughness and environmental danger for just standing
  somewhere.
- **Boss difficulty** — `Border (the boss's own home border) → Layer → difficulty`. No spatial
  resolution at all — deliberately does not go through Relevance.

These two are allowed, by design, to disagree. That's the actual mechanism behind [Progression &
Frontier Mechanics](../design/progression.md#bosses-can-appear-in-old-territory)'s "bosses can
appear in old territory" pillar — not a bug to reconcile, the point of the rule. It also gives that
page's still-open fairness-signal item a real trigger condition instead of a vague threshold: a
boss "significantly exceeding ambient difficulty" is now the checkable comparison
`difficultyOf(bossHomeBorder) > difficultyOf(getRelevant(bossSpawnPos))`, not a TBD feeling. Worth
handing back to Sasha as the concrete condition to signal against, whenever that item gets picked
up.

## Implementation trap worth flagging now

Under the current loop shape (one active boss at a time, growth only after that boss dies), "the
boss's home border" and "the current path tip" are the same border for the boss's entire lifetime —
so it would be easy, and would even test fine today, to compute boss difficulty by asking
`PATH.tip()` for the current layer instead of looking up the specific border the boss's own fixture
points to. Those give the same answer today only because of the loop's current single-boss
invariant, not because they're the same concept. The correct-by-construction form is to resolve
from the boss's own recorded identity — `BossMobFixture`'s Border UUID → that `Border`'s
`layer()` — not from a live "what's the tip right now" query. Same shape of trap as Layer and
Path's own coincidental relationship, one layer further up the stack; worth not repeating it.

## What this means for Nether/End

Under this vocabulary, [Nether and End](../design/nether-and-end.md)'s "player's highest attained
frontier level" reads as **Path**, not Layer: it's about how far the player has progressed through
the loop. The two numbers only coincide because Layer and Path generally coincide.

## Where "level" survives

"Level" does survive as *player-facing* language — and strictly there. [Progression & Frontier
Mechanics](../design/progression.md#terminology-level-is-player-facing-only) rules "level" in as
the player-facing term for progression count ("level 1," "level 2," ...) and rules it back out
everywhere else: every Frontier Mode design page now says **Border**, **Path**, **Layer**, or
**Difficulty** when describing the underlying mechanic, applying this page's own internal
vocabulary rather than working around it — the design pages had exactly the same "level" ambiguity
problem this page exists to fix, and are stated the same way. `Border`'s flavor `displayName`s
(Ashring, Dawnmark, ...) still coexist alongside the level count; a level number says *how far*, a
place name says *which one* — they're not competing for the same job.

## Difficulty's rules surface

`layerToDifficulty` and `ambientDifficultyAt` live directly on `BorderRules`/`DefaultBorderRules`
rather than in a separate `DifficultyRules` interface — one rules helper per module. See
[Difficulty](difficulty.md) for the seam itself. The conformance sweep that established this page's
terms across the wiki and the `border/*` package is
[FRO_029](../../../tickets/FRO_029_border-vocab-conformance.md).

## Related pages

- [Border](border.md) — `layer()`, `getRelevant()`, the data model these terms describe, and
  the "Design vocabulary bridge" section carrying this page's terminology mapping into Border's
  own page
- [Boss](boss.md) — the stat-scaling design this vocabulary is meant to unblock
- [Border Path & Layer Reconciliation](path-layer-reconciliation.md) — the Path/Layer divergence
  this page's "Layer" section builds on
- [Progression and Frontier Mechanics](../design/progression.md) — the design pillars this
  vocabulary is trying to make precisely implementable
- [Nether and End](../design/nether-and-end.md) — the page this vocabulary resolves an open question
  for
