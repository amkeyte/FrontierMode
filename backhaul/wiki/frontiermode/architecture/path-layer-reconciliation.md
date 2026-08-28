---
id: frontiermode/architecture/path-layer-reconciliation
category: frontiermode/architecture
slug: path-layer-reconciliation
title: Border Path & Layer Reconciliation
summary: How fixLayers() reconciles Border.layer() to borderPath order after a manual
  reorder, and why the two are allowed to diverge in the first place.
keywords: null
status: draft
updated: '2026-08-27'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border Path & Layer Reconciliation

How `BordersPathFacet.fixLayers()` brings `Border.layer()` back in line with `borderPath` order
after an op reorders the path by hand. See [Border](border.md) for the surrounding data model this
extends, and [Border Vocabulary](border-vocabulary.md) for what Layer and Path each mean.

## Why the two can diverge

`Border.layer()` is immutable — set once at creation, either `0` for a level's initial border
(`BordersPathFacet.grow()`'s own empty-path branch) or `previous.layer() + 1` for organic growth
(the same method's normal branch).
Under ordinary play that makes it equal to the border's position in `BordersFixture`'s canonical
`borderPath`. The equality is a consequence of how borders get made, not an enforced invariant.

It matters because `DefaultBorderRules.getRelevant()` — the oldest-ring-wins resolver behind both
`BorderAPI.getRelevant(ServerPlayer)` and the `@relevant` command selector — sorts strictly by
`layer`, never by path position.

`BordersPathFacet.moveUp()`/`moveDown()` are live, op-exposed commands
(`/border path moveup|movedown <selector>`) that reorder `borderPath` (a `List<UUID>`) without
touching any `Border`'s `layer` — there is no mutator to touch, short of replacing the border
outright via a fresh `BorderProposal`. So an op can put path order and layer order out of step, and
`getRelevant()` keeps resolving off the layer values rather than the intended new order.
`fixLayers()` is the reconciliation.

## Layer is not unique, and does not need to be

Nothing enforces `layer` uniqueness anywhere in the fixture. A border created off-path (via
`/border add`) can freely hold the same layer value as a path member, and `fixLayers()` neither
knows nor cares. `getRelevant()`'s own nearest-center tie-break resolves a same-layer overlap
correctly on its own.

This is the direct consequence of Layer and Path being definitionally unrelated — see
[Border Vocabulary](border-vocabulary.md#layer). Reconciliation aligns them because an op asked for
it, not because anything downstream requires them to match.

## Design

**Target semantics:** after `fixLayers()` runs, every border in `borderPath` has a `layer` equal to
its index in `borderPath` (`0` = oldest = path head). That is the only reading of "layer order
matches path order" that keeps `getRelevant()`'s oldest-ring-wins result consistent with what the
path visibly shows.

**Mechanism: one fixture-internal bulk reassignment, not a loop of public proposals.** A
package-private `BordersFixture.reassignLayers(Map<UUID, Integer> pathTargets)`:

1. Computes target values for every path member: `target[borderPath.get(i)] = i`.
2. Applies the whole batch as one atomic replace against the fixture's internal `borders` list
   (same shape as `accept()` — remove-by-id then re-add, but for the batch rather than one border),
   with a single `markDirty()` and a single revision bump rather than `N` sequential mutations.
3. Removes any `borderPath` entry whose UUID has no matching `Border`, logging loudly when it does.
   A stale path entry is real data corruption rather than a normal transient state, and leaving it
   in place would re-trigger the same warning on every future call forever — there is nothing else a
   dangling reference can usefully do once found. Same detect-and-log stance
   [Boss](boss.md#three-questions-three-different-mechanisms)'s own reconciliation check takes.
4. Returns the count of borders whose layer actually changed, plus any entries self-healed by step
   3 — `0` meaning the path was already consistent. An `int`, not a `boolean`, so the command layer
   can report a real number.

**Off-path borders are never touched.** A path member's layer is set to its path index directly;
whatever an off-path border's layer happens to be is irrelevant and left alone.

## Command-layer behavior

`BorderCommandHandler.pathFixLayers` reports the two real outcomes distinctly: a changed result
("Reconciled N border layer(s) with path order") versus an already-consistent one ("Path and layer
order already match -- no changes made").

## Related pages

- [Border](border.md) — the data model this extends
- [Border Vocabulary](border-vocabulary.md) — the Layer/Path split this reconciliation sits on top of
- [RM_FRO_015](../../../roadmap/RM_FRO_015_margaret.md) — roadmap tracker
- [RM_FRO_011](../../../roadmap/RM_FRO_011_betty.md) — the validation pass this design came out of
