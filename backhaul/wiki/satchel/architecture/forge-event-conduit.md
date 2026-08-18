---
id: satchel/architecture/forge-event-conduit
category: satchel/architecture
slug: forge-event-conduit
title: Forge Event Conduit (Parked)
summary: 'Open idea: route a jig''s declared Forge gameplay events through Satchel-scoped
  dispatch instead of raw MinecraftForge.EVENT_BUS registration in module code.'
keywords: null
status: draft
updated: '2026-08-18'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Forge Event Conduit (Parked)

*Parked, 2026-08-18 — Architect/Ziltoid noodling session. This is an idea Arryn is turning over,
not a design commitment: nothing below is scheduled, and no roadmap node tracks it yet. Captured
here so it isn't lost, not because it's actionable now. Sits next to
[Universal Sidedness Facade](facade-vision.md), which already names the same gap from a different
angle — see "Relationship to the facade vision" below.*

## The itch

[`BorderModule.onBlockPlaced`](../../frontiermode/architecture/border.md#commands-and-client-surface)
is currently wired with a raw `MinecraftForge.EVENT_BUS.addListener(BorderModule::onBlockPlaced)`
call inside `BorderModule.init()` — legitimate per
[Forge Integration & Sidedness Contract](../spec/forge-integration.md)'s own table ("raw gameplay
input driving domain logic — not scope/bundle state"), but it was a one-off built for debugging
that's now load-bearing precedent: it's the shape any future module reaches for when it needs a
raw gameplay event, because it's the only shape that currently exists. Every side-correctness bug
the facade-vision page catalogs was found in single-player, where both `LogicalFoundation`s happen
to coexist in one JVM — a real dedicated-server split removes that accidental safety net, and a
module built on the `onBlockPlaced` pattern has no structural protection against exactly that class
of bug, only the same thread-discipline convention the rest of Satchel is already trying to move
away from.

## Why `SatchelEventBus` isn't actually the blocker

Worth recording since it wasn't obvious going in: `SatchelEventBus` (`common/lifecycle/`) is a
plain `Class<?> → List<Consumer<?>>` map — `subscribe(Class<E>, Consumer<? super E>)`, dispatch on
`event.getClass()`. Nothing about it is `ScopeEvent`-specific, and `EventHandlers.on(...)` already
works for any event type. The missing piece isn't a new bus — it's a producer that does the raw
Forge registration on a jig's behalf (inside the ingress layer, where that's already sanctioned)
and hands the result to the bus, or to jig-scoped dispatch, instead of a module reaching
`MinecraftForge.EVENT_BUS` itself.

## Two shapes, not obviously the same value

**A — thin passthrough.** A new ingress-adjacent class (`ServerForgeConduit`/`ClientForgeConduit`,
sitting next to `ServerForgeIngress`/`ClientForgeIngress` — one more member of the "only these
classes touch raw Forge" list, not a violation of it) calls `bindFoundation()` then
`bus.post(rawEvent)` for registered Forge event types. Modules subscribe with the same
`EventHandlers.on(BlockEvent.EntityPlaceEvent.class, handler)` shape already used for `ScopeEvent`.
Cheap, but it inherits `ScopeEvent`'s own wart: every subscriber gets every instance regardless of
which jig cares, and a raw Forge event carries no jig identity to guard against — there's no
`info.jigInfo().key` to check, so [New Module Checklist](new-module-checklist.md) item 3's manual
guard pattern doesn't even have something to read. Not clearly better than today, just relocated.

**B — scoped dispatch, no shared bus for this.** Reuse what `LogicalFoundation.introduceSource()`
already does — walk `JigInfo.resolveScope(source)` to find which jig recognizes an arbitrary
source object. A forwarded Forge event just needs one adapter step ahead of that (mostly
`event.getLevel()`, inconsistently named across Forge's event classes, but a one-liner per event
type). The conduit holds a registry built at `JigConfigCompiler.compileForSide` time —
`EventClass → List<(JigKey, sourceExtractor, handler)>`, populated from whatever each `JigConfig`
declared — and installs exactly one Forge listener per distinct event class. On fire: extract
source, resolve scope only against the jig that declared interest, call the handler directly with
`(ScopeInfo, event)`. No fan-out, no shared-bus collision, no manual guard needed — a handler
can't receive an event for the wrong jig, because dispatch was scoped at registration time, not
filtered at delivery time. That's the "guaranteed by construction" bar the facade vision sets for
itself, not just a second `ScopeEvent` with the same footgun.

The nicer wrinkle in B: no bespoke extractor per module. A `LevelJigConfig` already declares
`sourceType = Level` and a `scopeResolver` — a Forge-event extractor needs the same information,
just with one extra hop (event → `Level`) in front of it. The conduit's registry is really
`EventClass → source-extractor`, owned once wherever the first module needing that event type
declares it, composed with the jig's *existing* binding rather than a new resolution concept
bolted on per event per module.

Where this would live in `JigConfig`, if it ever gets built: `(EventClass, handler)` declarations
as a sibling to `JigExecutionConfig.eventHandlers()` — "which raw Forge events does this jig care
about" is the same "when do I participate" family as `.lifecycle()`. The extractor is closer to
`JigBindingConfig` territory conceptually, but doesn't obviously need its own presets field if the
conduit just keeps its own small lookup table.

## Relationship to the facade vision

[Universal Sidedness Facade](facade-vision.md) already names this exact gap under "What's
genuinely missing, not just unfinished" — "No generic side-bound event-forwarding registration...
This is the most concretely buildable piece of the vision." This page exists separately rather
than folding straight into that one because facade-vision.md is the broader "no module ever
touches Forge directly, for anything" ambition (rendering, command registration, config —
including things this page explicitly doesn't try to solve), while this page is narrower and more
concrete: just the raw-gameplay-event slice, sketched further than the vision page currently goes,
still not committed to. If this ever gets scoped into real roadmap work, it would sit under
[RM_SAT_018](../../../roadmap/RM_SAT_018_edward.md) alongside whatever else gets pulled out of the
facade vision's own open list — not a new convergence node of its own.

`BorderModule.onBlockPlaced` would be the natural first migration candidate once/if this gets
built — same role Border already plays for the rest of the jig/scope system.

## Open, not resolved here

- Whether a forwarded event needs its own `sideApplicability`-style declaration, or can infer side
  from the event type alone (a `BlockEvent` is server-only in practice today, but nothing enforces
  that — it just never fires client-side).
- Whether two jigs wanting the same Forge event type but resolving different scope types from it
  is a real case worth designing for, or one to explicitly defer the way frame-driven dispatch
  already is in the facade vision.
- Whether B's registry-at-compile-time approach composes cleanly with `JigConfigValidator`'s
  existing fail-loudly-before-anything-runs discipline, or needs its own validation pass.

## Related pages

- [Universal Sidedness Facade](facade-vision.md) — the broader vision this narrows
- [Forge Integration & Sidedness Contract](../spec/forge-integration.md) — current-state contract
  for who touches raw Forge and why
- [Jig & Scope Runtime](runtime.md) — `JigInfo.resolveScope`, `introduceSource`, and the
  `JigConfig` compilation pipeline this idea would extend
- [New Module Checklist](new-module-checklist.md) — the jig-key-guard footgun this idea would
  remove by construction
- [Border architecture](../../frontiermode/architecture/border.md) — `BorderModule.onBlockPlaced`,
  the concrete precedent this page is reacting to
