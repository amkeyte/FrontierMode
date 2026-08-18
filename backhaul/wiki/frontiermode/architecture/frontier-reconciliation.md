---
id: frontiermode/architecture/frontier-reconciliation
category: frontiermode/architecture
slug: frontier-reconciliation
title: Border-Frontier Reconciliation
summary: Maps Sasha's Frontier design vocabulary onto the existing Border architecture
  and flags what's confirmed vs. still open.
keywords: null
status: draft
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border-Frontier Reconciliation

*First pass, 2026-08-12. Ziltoid (project owner) answered a round of Architect questions
comparing [Border](border.md) against Sasha's design pages
([Frontier Mode Overview](../design/overview.md),
[Progression & Frontier Mechanics](../design/progression.md)); this page captures
what that reconciled, what it confirmed was already aligned, and what's still open. Several
answers are stated from memory ("double check at some point") rather than fresh source
verification — flagged individually below. The goal is a shared vocabulary and a clean list
of open items, as the basis for a future actual spec, not a spec itself yet. The
commands-vs.-levers and `BorderPlayerBundle` items below were subsequently checked against
current source and against [Border](border.md)'s runtime-wiring and per-player-evaluation
sections; everything else on this page is still the original memory-recalled, not
source-verified, pass.*

## Terminology mapping (confirmed)

- **Border** = any single cylindrical range of blocks. **Each design "level" is one Border.**
- **Frontier** = the sum of all overlaying Borders. Not currently a named object or computed
  aggregate anywhere in [Border](border.md)'s documented architecture — open question below.
- The `PATH` facet's "border progression order" (see [Border](border.md#data-model)) is very
  likely the level sequence (level 1, level 2, level 3...) in design terms, though this specific
  equivalence wasn't asked/confirmed explicitly and is worth double-checking before it's load-bearing.

## Geometry (confirmed against source)

Each Border is tracked as a **coordinate + radius** (`Border.center()`/`Border.radius()`).
Height is not stored — `BorderMath` computes containment and distance in X/Z space only
("Y is intentionally ignored," per its own class doc), matching the design's "full world height,
min to max" cylinder. Confirmed directly from `Border.java`/`BorderMath.java`, not recalled from
memory.

## Oldest-ring-wins overlap resolution (confirmed against source)

The single biggest previously-unconfirmed assumption in this page: yes,
`DefaultBorderRules.getRelevant(List<Border> containing, BlockPos pos)` implements exactly this —
given the set of borders containing a point (`BorderLogic.containing()`), it returns the one with
the lowest `layerIndex` (oldest), tie-broken by nearest center. This backs both
`BorderAPI.getRelevant(ServerPlayer)` and the `@relevant` command selector, not just a stub —
wired end-to-end and not a placeholder. Real, evidenced gap in the same neighborhood: reordering
`Border.layerIndex()` to match a *manually reordered* path (`/border path moveup`/`movedown`)
isn't implemented yet — see [Border](border.md#known-gaps) and
[Border Path & Layer Reconciliation](path-layer-reconciliation.md) — but the resolution query
itself, for borders as currently laid out, is real and correct.

## Persistence layering (confirmed — already aligned, no reconciliation needed)

Satchel is the **generic, domain-agnostic persistence backend**, built on Minecraft's native
`SavedData` mechanism (see [Persistence](../../satchel/architecture/persistence.md)). FrontierMode's
Border module is a **consumer** that defines Border-specific meaning on top of Satchel's generic
bundle/fixture model — this is exactly what [Bundle](../../satchel/architecture/bundle.md) already
describes ("Satchel trusts the surrounding environment to interpret and support bundle
capabilities correctly"). Confirmed design intent: Satchel is meant to back **the entire
FrontierMode ecosystem's data**, not just Border specifically — Border is the first, proof-of-concept
consumer, not the only one Satchel is meant to serve.

## Border creation/mutation: commands vs. levers (already largely resolved)

`BorderCommands`/`BorderSelector` (see [Border](border.md#commands-and-client-surface)) are
**op/dev-test controls only** — not the final player-facing mechanism. The real design requirement
was **agnostic levers**: a reusable capability to initiate and mutate a Border that isn't tied to
any single trigger. That capability already exists: `BorderAPI` (`border/BorderAPI.java`) is a
side-agnostic, trigger-agnostic surface (`grow`, `addBorder`, `transformBorder`, `removeBorder`)
that both the command path and a non-command path already call into. Concretely, block placement
already drives border growth independently of commands — `BordersTriggers.growPath`, wired via
`BorderModule.onBlockPlaced` (see [Border](border.md#runtime-wiring)), checks
`DefaultBorderRules.growPathCriteria` and calls `BorderAPI.grow(level)`, the exact same call
`BorderCommandHandler.pathGrow` makes for `/border path grow`. So the underlying capability is
already decoupled from command-handling code, not embedded inside it.

What a boss-defeat trigger would need is not a new API — it's a new caller of the existing one,
following the same shape `BordersTriggers.growPath` already demonstrates (a Forge/game event
handler that decides *whether* to act, then calls into `BorderAPI`). `BorderAPI.addBorder(level,
center, radius, layerIndex)` already accepts an arbitrary center, which is what a boss-defeat
handler would need (centering the new level on the boss's death location rather than the player).
No boss-defeat detection or invocation exists yet — that's a real gap, but it's a missing *caller*,
not a missing *capability*.

## Ecosystem shape (confirmed, raises the stakes on the item above)

FrontierMode is conceived as **modular**. Border is explicitly the **proof-of-concept module** —
foundational because nearly everything else in the design (bosses, guardian mobs, discovery
tools, loot/reward density, Nether/End difficulty gating) is defined in terms of "level" or
"distance from origin," both Border concepts. Future modules should be expected to build on
Border's public surface, which is exactly why `BorderAPI` already being trigger-agnostic matters:
getting that initiate/mutate surface right early was more valuable than it would be for a module
nothing else depends on, and it's already in a shape other triggers can reuse.

## Open items

- **`BorderPlayerBundle`'s purpose, at the source level, is no longer a mystery, and it's now
  wired up** — see [Border](border.md#known-gaps). Real per-player logic exists
  (`BorderPlayerEval`, `BorderPlayerLogic`, `BorderPlayerStatus`, `BorderPlayerStatusFixture`,
  `BorderPlayerStatusProposal`) and, per `BorderModule.init()`, the bundle is now constructed and
  registered against a real `PlayerJig`/`PlayerScope` (see `RM_FRO_006`) — this was still described
  as fully commented out and unregistered in this page's earlier pass, which is now stale. The
  project owner's earlier recollection ("might be junk... probably the layer meant to track
  per-player border-specific data") was the right guess. What's still genuinely open is the
  *design* side, deliberately not resolved here: this per-player layer isn't mapped to any current
  Frontier design concept yet. Per the project owner (2026-08-16): that's Architect's question to
  answer, not Game Designer's, but not worth resolving until something actually needs it — likely
  multiplayer's "whose frontier is it" question in [Multiplayer
  Sketch](../design/multiplayer-sketch.md), maybe sooner. Left open on purpose.
- The boss/ambient-difficulty-mismatch warning-signal feasibility question from
  [Progression & Frontier Mechanics](../design/progression.md#bosses-can-appear-in-old-territory)
  — explicitly deferred by the project owner, not part of this reconciliation pass.

## Related pages

- [Border](border.md)
- [Border Path & Layer Reconciliation](path-layer-reconciliation.md)
- [Boss](boss.md) — partially corrects this page's "missing caller, not a missing capability"
  finding (path/center gap found scoping the real caller)
- [Frontier Mode Overview](../design/overview.md)
- [Progression & Frontier Mechanics](../design/progression.md)
- [Bundle](../../satchel/architecture/bundle.md)
- [Persistence](../../satchel/architecture/persistence.md)
