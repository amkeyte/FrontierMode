---
id: frontiermode/architecture/effects
category: frontiermode/architecture
slug: effects
title: Effects
summary: Cross-cutting server/client effect dispatch (particle sends, area sound broadcasts)
  that other modules call into rather than rolling their own -- see FRO_089.
keywords: null
status: draft
updated: '2026-09-06'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Effects

`EffectsMod` owns server/client effect dispatch -- targeted particle sends, area sound
broadcasts, possibly screen/title effects later -- so other modules (Boss, Border, Discovery,
future systems) call into it instead of each rolling their own. A deliberately narrow module: it
dispatches effects, it doesn't decide game logic, and it doesn't own anyone else's domain data.

## Module shape

Structured the same as any Satchel-registered module (Border, Boss), not a bare utility class:
`client/`/`common/`/`server/` packages under `effects/`, an `EffectsAPI` facade in `common/`
mirroring `BorderAPI`/`BossAPI` as the one sanctioned entry point other modules call through, and
an `EffectsMod.init()` matching `BorderModule.init()`/`BossModule.init()`'s call convention.

This holds even though there's nothing to wire yet -- no fixture, no bundle, no `JigConfig`
registration, since effect dispatch has no persisted or scope-bound state identified so far.
`EffectsMod`/`EffectsAPI` are expected to start empty or near-empty: the module shape (packages,
facade, `init()`) is followed for consistency and a stable place to grow into, not because
there's jig/bundle machinery to register today. [`BorderMath`](border-curve.md#evaluation-bordermath-not-a-service-class)
(pure static math, no fixture) is the closer precedent for the dispatch layer itself than
`BorderModule`/`BossModule`'s own registered-module shape. The trigger for this module to grow
real Satchel wiring is a future per-effect-type throttle/cooldown -- that would be `PlayerJig`-
scoped state, not `LevelScope`, and isn't needed by anything identified so far.

## Two dispatch paths, chosen per effect

Not one dispatch mechanism -- two, living side by side as ordinary static methods on `EffectsAPI`
(and its client-side counterpart), so picking one over the other is a one-line call-site choice,
never a structural one:

- **Server-broadcast** -- the server makes a real decision (a probability roll, any check
  depending on server-only state) once and pushes the result to nearby players. Required whenever
  the occurrence itself is non-deterministic or depends on something not synced to every client --
  otherwise different players observe different outcomes for what's supposed to be one shared
  event.
- **Client-derived (parity)** -- no roll, no broadcast. Every client computes the same answer
  independently off state it already has synced, and decides locally whether to fire. Valid only
  when the effect's occurrence is a pure function of already-synced inputs (a stable id, the
  world's own game time) -- built on the parity primitive described in
  [Utilities § Honorable mention](../../satchel/architecture/utilities.md#honorable-mention-simulation-parity-proposed).

Not a registered effect-descriptor system where callers declare effect types and the module
dispatches generically -- named static methods first, matching this codebase's own "safe
baseline, replace later" convention (see Guardian Mobs' variant-selection strategy in
[Boss Discovery Systems](discovery-systems.md#guardian-mobs)). A descriptor system is the right
move once there are enough effect *kinds* that callers need to compose them generically, not
ahead of the second real consumer.

## Sound

Sound gets the same treatment as particles, in the same first pass, not a later phase, and the
same two dispatch paths apply. **One exception:** an effect gated on server-only state
(attunement, team membership, anything a client isn't told) can't be derived client-side at all --
that one always stays server-broadcast regardless of how the rest of a given effect sorts out.
Nothing currently built needs this exception, but the API shape shouldn't fight reaching for it
when something eventually does.

## Boundary: a generic utility, not a domain-render owner

Other modules call in through `EffectsAPI` with plain parameters. `EffectsMod` never reaches back
into a calling module's own data model to decide what to draw or play -- it doesn't need to know
what a border's radius is or where a boss's home position sits. Continuous, domain-specific
rendering that requires that kind of intimate data access (Border's own ring geometry,
[`WorldBordersRenderer`](border.md#commands-and-client-surface)) stays owned by the module whose
domain it is; only discrete, triggered effects (a particle spawn, a sound broadcast) are
`EffectsMod`'s to own. [`GrowthTriggerRenderer`](border.md#commands-and-client-surface)'s particle
spawn is the kind of thing that migrates in here -- a triggered effect, not continuous domain
rendering.

## Related pages

- [FRO_089](../../../tickets/FRO_089_effectsmod-proposal.md) -- the full design conversation and
  settled open questions this page canonicalizes
- [Utilities](../../satchel/architecture/utilities.md) -- the Satchel-level parity primitive the
  client-derived path is built on
- [Border](border.md), [Boss](boss.md) -- the first consumers migrating existing effect calls in
- [Border Curve](border-curve.md) -- `BorderMath`, the closer precedent for this module's own
  dispatch-layer shape
