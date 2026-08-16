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

## Geometry (recalled, not yet verified)

Each Border is tracked as a **coordinate + radius**. Height is not stored — it spans whatever the
Minecraft engine's own world-height bounds are, matching the design's "full world height, min to
max" cylinder. Stated from memory by the project owner ("double check at some point"); `BorderMath.java`
hasn't been checked against this claim, and [Border](border.md) doesn't currently document
geometry at all. Treat as the working assumption, not a verified fact, until a code-focused pass
confirms it.

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

- **Does the existing Border/`BordersFixture` model actually support permanent, immutable
  historical regions with an age-based overlap-resolution query (oldest-ring-wins)?** This is the
  single biggest unconfirmed assumption underlying the whole progression design — everything about
  safe pockets and "old territory stays old" depends on it — and it wasn't part of this round of
  answers. Needs a source-level check before more spec work builds on top of it.
- **`BorderPlayerBundle`'s purpose, at the source level, is no longer a mystery** — see
  [Border](border.md#known-gap-per-player-evaluation-is-unfinished). Real per-player logic exists
  (`BorderPlayerEval`, `BorderPlayerLogic`, `BorderPlayerStatus`, `BorderPlayerStatusFixture`,
  `BorderPlayerStatusProposal`), but the bundle meant to host them is entirely commented out and
  nothing constructs or registers it — an evidenced, tracked gap (see `RM_FRO_006` in the
  roadmap), not junk and not a design decision. The project owner's earlier recollection ("might
  be junk... probably the layer meant to track per-player border-specific data") turns out to be
  the right guess. What's still genuinely open is the *design* side: this per-player layer isn't
  mapped to any current Frontier design concept, so whether it's an early stab at something the
  design will need later (e.g. once multiplayer's "whose frontier is it" question in [Multiplayer
  Sketch](../design/multiplayer-sketch.md) gets unparked) or should be finished/removed on its own
  merits is a design call, not a source-reading one.
- Whether geometry is truly coordinate+radius as recalled, per above.
- The boss/ambient-difficulty-mismatch warning-signal feasibility question from
  [Progression & Frontier Mechanics](../design/progression.md#bosses-can-appear-in-old-territory)
  — explicitly deferred by the project owner, not part of this reconciliation pass.

## Related pages

- [Border](border.md)
- [Frontier Mode Overview](../design/overview.md)
- [Progression & Frontier Mechanics](../design/progression.md)
- [Bundle](../../satchel/architecture/bundle.md)
- [Persistence](../../satchel/architecture/persistence.md)
