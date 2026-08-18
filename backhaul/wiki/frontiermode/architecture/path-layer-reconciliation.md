---
id: frontiermode/architecture/path-layer-reconciliation
category: frontiermode/architecture
slug: path-layer-reconciliation
title: Border Path & Layer Reconciliation
summary: Design for reconciling Border.layerIndex to path order after a manual /border
  path reorder -- the fixLayers() gap RM_FRO_015 tracks.
keywords: null
status: draft
updated: '2026-08-16'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border Path & Layer Reconciliation

*Design pass for [RM_FRO_015](../../../roadmap/RM_FRO_015_margaret.md), written before any code
changes — this is what Lead Dev builds against, not a description of shipped behavior. See
[Border](border.md) for the surrounding data model this extends.*

## Context

`Border.layerIndex()` is immutable — set once, either `0` for a level's initial border
(`BorderLogic.getInitial()`) or `previous.layerIndex() + 1` for organic growth
(`BorderLogic.grow()`), so under ordinary play it always equals a border's position in
`BordersFixture`'s canonical `borderPath`. That equality is load-bearing:
`DefaultBorderRules.getRelevant()` — the oldest-ring-wins resolver backing both
`BorderAPI.getRelevant(ServerPlayer)` and the `@relevant` command selector — sorts strictly by
`layerIndex`, not by path position. The two are only the same value by construction, not by any
enforced invariant.

`BordersPathFacet.moveUp()`/`moveDown()` are live, op-exposed commands
(`/border path moveup|movedown <selector>`) that reorder `borderPath` (a `List<UUID>`) without
touching any `Border`'s `layerIndex` — there's no mutator to touch, short of replacing the border
entirely via a fresh `BorderProposal`. So path order and layer order can now legitimately diverge,
and `getRelevant()` would keep resolving off the stale `layerIndex` values, not the op's intended
new path order. `fixLayers()` is where that reconciliation is supposed to happen; today it's a
hardcoded `return false` (see [Border](border.md#known-gaps)).

## Why this isn't a one-line fix

`BordersCrudFacet.applyProposal()` — the only path that can currently change a `layerIndex` — now
rejects (RM_FRO_011) any proposal whose `layerIndex` collides with a *different* border's. Calling
it once per path member, straight down the new path order (`0, 1, 2, ...`), will transiently
collide with the *next* member still holding its old value the first time two members need to
swap ranges — the exact case a manual reorder produces. Reassignment has to be planned as a batch,
not applied one proposal at a time against the live validation path.

The other wrinkle: `layerIndex` uniqueness is enforced across **every** border in the fixture, not
just path members. A border can exist without ever joining the path (e.g. one created directly via
`/border add`), and its `layerIndex` still occupies a slot `getRelevant()` will compare against.
Reassigning path members to `0..pathSize-1` has to account for off-path borders that already sit
in that range.

## Design

**Target semantics:** after `fixLayers()` runs, for every border in `borderPath`, its
`layerIndex` equals its index in `borderPath` (`0` = oldest = path head). This is the only
definition of "layer order matches path order" that keeps `getRelevant()`'s oldest-ring-wins
reading consistent with what the path visibly shows.

**Mechanism: a dedicated fixture-internal bulk reassignment, not a loop of public proposals.**
`BordersCrudFacet.applyProposal()`'s validation (radius bounds, collision) exists to protect
against an external, potentially-careless caller — a single op-typed command. `fixLayers()` isn't
that: it's the fixture re-establishing its own already-owned invariant across borders it already
holds. Route it through a new package-private method on `BordersFixture` (e.g.
`reassignLayerIndices(Map<UUID, Integer> targets)`) that:

1. Computes target values for every path member: `target[borderPath.get(i)] = i`.
2. Finds off-path borders whose *current* `layerIndex` falls inside `[0, pathSize)` — these
   collide with a path target and must move. Reassign them, in their existing relative
   `layerIndex` order (stable), to values starting at
   `max(pathSize, 1 + current max layerIndex across all borders)`, so they land clear of the
   reserved path range without needing to know anything about path semantics themselves.
3. Applies every reassignment as one atomic replace against the fixture's internal `borders` list
   (same shape as `accept()` — remove-by-id then re-add, but for the whole batch, not one border),
   one `markDirty()`/one revision bump — not `N` sequential validated proposals, so no
   intermediate state is ever visible to a concurrent reader or re-validated against itself.
4. Returns `true` if any border's `layerIndex` actually changed, `false` if the path was already
   consistent (a real, honest no-op — not the current unconditional `false`).

`fixLayers()` itself becomes a thin wrapper: build the target map per step 1-2 above, call
`reassignLayerIndices`, return its result.

**Off-path borders keep their relative order, not a semantic ranking.** This design only
guarantees off-path borders don't collide with the newly-assigned path range — it does not attempt
to answer where an off-path border's `layerIndex` *should* sit relative to path members in
oldest-ring-wins terms. That's a pre-existing modeling question (an off-path border has no defined
"age" relative to the path at all today) and is explicitly out of scope here; flagging it rather
than quietly picking an answer.

## Command-layer behavior

`BorderCommandHandler.pathFixLayers` should report the two real outcomes distinctly: a changed
result ("Reconciled N border layer(s) with path order") versus an already-consistent one ("Path
and layer order already match — no changes made"), replacing the current unconditional "No
changes made -- layer/path reconciliation isn't implemented yet." string once this lands.

## Done bar

Real build + real command sequence, same standard as RM_FRO_011: reorder a path via
`moveup`/`movedown`, confirm `getRelevant()` (via `@relevant` or a direct query at a point covered
by the reordered borders) reflects the new order, confirm an off-path border with a colliding
`layerIndex` gets bumped and not lost, confirm a no-op case reports honestly.

## Related pages

- [Border](border.md) — the data model this extends, including the current `fixLayers()` gap
- [RM_FRO_015](../../../roadmap/RM_FRO_015_margaret.md) — roadmap tracker
- [RM_FRO_011](../../../roadmap/RM_FRO_011_betty.md) — the validation hardening pass that found
  this gap and flagged it for a real design pass instead of guessing
