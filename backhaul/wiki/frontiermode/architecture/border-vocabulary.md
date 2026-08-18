---
id: frontiermode/architecture/border-vocabulary
category: frontiermode/architecture
slug: border-vocabulary
title: Border Vocabulary (Parked)
summary: 'Open idea: four distinct concepts (Relevance, Layer, Path, Difficulty) proposed
  to replace overloaded use of ''level'' across the wiki and code.'
keywords: null
status: draft
updated: '2026-08-18'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border Vocabulary (Parked)

*Parked, 2026-08-18 — Architect/Ziltoid noodling session, continued from
[Border-Frontier Reconciliation](frontier-reconciliation.md) and
[Border Path & Layer Reconciliation](path-layer-reconciliation.md), both of which already flagged
"level" as an overloaded, inconsistently-used word without proposing a replacement. This is
vocabulary Arryn is settling on, not a locked spec — confident enough to write down, not yet
scoped into real work. No roadmap node exists yet; see "Tracking" below.*

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

`Border.layerIndex()` as it exists today: immutable, assigned once at creation (`0` for the initial
border, `previous.layerIndex() + 1` on growth). Its only job is to be Relevance's sort key —
confirmed against source, not assumed: `layerIndex` has exactly one live consumer anywhere in
either repo, `getRelevant()`'s comparison. Works like layers in an image editor: a metric for
ordering, closest-to-0-wins, with no inherent geometric or gameplay meaning of its own.

**Only coincidentally tied to Path.** Under normal growth, `BorderLogic.grow()` assigns the next
path slot and the next `layerIndex` from the same call, so they move together — but nothing
structurally binds them. `BordersPathFacet.moveUp()`/`moveDown()` reorder the path without ever
touching `layerIndex`, which is exactly the gap [Border Path & Layer
Reconciliation](path-layer-reconciliation.md) tracks. `fixLayers()` isn't an enforced invariant —
it's a "re-coincide these on request" operation for after a deliberate manual reorder pulls them
apart, not a guarantee they always agree.

### Path

`BordersPathFacet`'s ordered `borderPath` list — the order a player progresses through the game
loop. `PATH.tip()` is the current frontier edge. Growing the path pushes the
[Frontier](frontier-reconciliation.md#terminology-mapping-confirmed) outward, letting players
explore farther — that's the entire mechanism behind "the frontier's shape is a record of where
the player has actually been."

**Aspirational vs. actual, worth being honest about:** the live growth trigger today is gold-block
placement near the path tip (`BordersTriggers.growPath`) — a debug-shaped stand-in, not player
progression in the sense this definition means. Path-as-implemented is currently just "creation
order"; Path-as-intended is "player progression order." They become the same thing once Boss
(RM_FRO_018/019) lands and growth is actually centered on a boss's death location, not before.

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
`layerIndex()` — not from a live "what's the tip right now" query. Same shape of trap as Layer and
Path's own coincidental relationship, one layer further up the stack; worth not repeating it.

## What this means for Nether/End

Under this vocabulary, [Nether and End](../design/nether-and-end.md)'s "player's highest attained
frontier level" — previously ambiguous relative to `layerIndex` vs. path length — reads as **Path**,
not Layer: it's about how far the player has progressed through the loop. The two numbers only
coincide because Layer and Path generally coincide. Worth restating that page in these terms
whenever it's next touched.

## Resolved by Game Designer (2026-08-18)

Whether "level" survives anywhere as *player-facing* language, left open above: yes — and
strictly so. [Progression & Frontier
Mechanics](../design/progression.md#terminology-level-is-player-facing-only) rules "level" in as
the player-facing term for progression count ("level 1," "level 2," ...) and rules it back out
everywhere else: every Frontier Mode design page now says **Border**, **Path**, **Layer**, or
**Difficulty** when describing the underlying mechanic, applying this page's own internal
vocabulary rather than working around it — the design pages had exactly the same "level" ambiguity
problem this page exists to fix, and are fixing it the same way. `Border`'s flavor `displayName`s
(Ashring, Dawnmark, ...) still coexist alongside the level count; a level number says *how far*, a
place name says *which one* — they're not competing for the same job.

## Tracking

No roadmap node exists yet — **RM_FRO[TBD]** is a placeholder, not a real ID, for whenever this
gets scoped into actual work (most likely touching `boss.md`'s stat-scaling design and a
`DifficultyRules`-shaped seam alongside `BorderRules`). Not opened this session, per the project
owner's call to keep this parked for now.

## Related pages

- [Border](border.md) — `layerIndex`, `getRelevant()`, and the data model these terms describe
- [Boss](boss.md) — the stat-scaling design this vocabulary is meant to unblock
- [Border-Frontier Reconciliation](frontier-reconciliation.md) — where "level = one Border" was
  first confirmed, and where this page's terminology gap was first flagged
- [Border Path & Layer Reconciliation](path-layer-reconciliation.md) — the Path/Layer divergence
  this page's "Layer" section builds on
- [Progression and Frontier Mechanics](../design/progression.md) — the design pillars this
  vocabulary is trying to make precisely implementable
- [Nether and End](../design/nether-and-end.md) — the page this vocabulary resolves an open question
  for
