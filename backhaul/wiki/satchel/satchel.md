---
id: satchel/satchel
category: satchel
slug: satchel
title: Satchel
summary: Core data & utility mod for FrontierMode -- bundles/fixtures, networking,
  and per-bundle persistence.
keywords: null
status: verified
updated: '2026-09-03'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · satchel
<!-- bh-header:end -->

# Satchel

Core data & utility mod for FrontierMode.

Satchel's founding purpose is to organize Forge's tick and lifecycle events into something a mod
can consume consistently, instead of every mod hooking raw Forge `TickEvent`/`LevelEvent` calls
directly. `ServerForgeIngress` and `ClientForgeIngress` are the only classes that touch Forge
events at all; each installs its own `LogicalFoundation` (Satchel tracks client and server as
fully separate foundations, keyed by side, so both can run in-process at once — as they do in
single-player). Every tick, the active foundation's lifecycle pulses and posts a semantic
`ScopeEvent` (`Loaded` / `Tick` / `Unloaded`) onto `SatchelEventBus`, a synchronous, ordered
pub/sub bus. Mod code subscribes to these events rather than to Forge — FrontierMode's Border
module, for example, drives both its growth logic and its client-side ring rendering off
`ScopeEvent.Tick`, with no direct Forge dependency of its own.

"The active foundation" is resolved per-thread, not carried by the scope object. `Satchel.FOUNDATIONS`
is a real `Map<LogicalSide, LogicalFoundation>` (both sides' foundations coexist in the same JVM in
single-player), but which one `Satchel.require()`/`Satchel.foundation()` hands back depends on
`LogicalSideContext`, a `ThreadLocal<LogicalSide>` rebound at the top of every Forge event handler
in `ServerForgeIngress`/`ClientForgeIngress`. `LevelScope` itself carries no side information at all
— its identity is derived purely from `dimension().toString()`. So side-correctness is thread
discipline, not type-level enforcement: reliable as long as every call into Satchel happens on a
thread a booter has already bound, fragile if one doesn't. Concretely fragile, not just
theoretically: `Satchel.requireClient()`'s side check is inverted (fixed in
[SAT_006](../../tickets/SAT_006_fix-inverted-requireclient-side-check.md)) — it silently passed on
the server rather than actually guarding client-only code.

On the data side, Satchel provides a `Bundle`/`Fixture` model for scoped, persisted state: a
`Bundle` aggregates state for a given `SatchelScope` (e.g. a level), and a `Fixture` is the
modder-facing unit within it — typically exposing several grouped facets (a CRUD-style mutation
API, read-only info, domain rules) rather than raw field access. Mutation generally goes through
a proposal pattern instead of direct writes. Underneath, this is persisted per-bundle via
`BundleSavedData`, built on Minecraft's own `SavedData` mechanism rather than a bespoke file
format.

Server and client don't get separate object models for the same state. The networking layer
(see [Networking](architecture/net.md)) is deliberately transport-only: it serializes a bundle's
facet data server-side and, client-side, does nothing more than `bundle.loadAll(data)` —
server-authoritative sync, no logic in the packet path. Once loaded, client code reads that state
through the same Fixture/facet API a server-side consumer would use, not a hand-written mirror
class. FrontierMode's client-side border rendering, for example, pulls border and path-tip data
off the identical `CRUD`/`PATH` facets, reached via `BorderAPI`'s facet resolvers, that
server-side growth logic uses.
That's a deliberate goal of the facade: avoid duplicating a `client`/`common`/`server` triad of
near-identical objects for every module. It isn't absolute, though — some work has no server-side
equivalent to unify against. Border's rendering package (`border/client/render/level/*`) is real
client-only code sitting outside the shared facade, because there's no meaningful "server-side"
version of drawing a ring.

Satchel itself stays deliberately agnostic to game content — Border, in FrontierMode, is the
first real consumer exercising this runtime end to end.

## Identity

Pulled from `Satchel/gradle.properties` (the actual source of truth — `Satchel/mods.toml` is
templated off these properties, and `README.txt` is stock Forge MDK installation boilerplate
that doesn't describe the mod; see [SAT_001](../../tickets/SAT_001_readme-boilerplate.md)).

- **Mod ID**: `satchel`
- **Display name**: Satchel
- **Version**: 0.0.2
- **Group**: `com.arryn`
- **Author**: Arryn
- **License**: MIT
- **Minecraft**: `[1.20.1,1.21)` on Forge `[47,)`

## Dependencies

- Depends only on `minecraft` directly. **FrontierMode depends on Satchel** (mandatory, ordering
  `AFTER`) — see [FrontierMode](../frontiermode/frontiermode.md).

## Architecture

- [Bundle](architecture/bundle.md) — primary unit of state aggregation
- [Fixture](architecture/fixture.md) — modder-facing unit of persistent state
- [Networking](architecture/net.md) — transport-only networking layer
- [Persistence](architecture/persistence.md) — server-side per-bundle persistence
- [Jig & Scope Runtime](architecture/runtime.md) — foundations, the tick/event dispatch chain,
  `JigConfig` registration, bundle-level lifecycle, and the three jig kinds
- [Jig & Strap Registration — History](architecture/jig-registration-break.md) — break/fix
  history behind the runtime's current shape; superseded as a live reference by the page above
- [Jig & Strap Registration — Recovery Plan](architecture/jig-registration-recovery-plan.md) —
  the design reasoning that held up through that fix
- [Utilities](architecture/utilities.md) — cross-cutting `common/util`/`server/util` helpers:
  structured logging (`OUT`/`Tracer`), tick-interval gating (`TickThrottler`), side-marking
  (`SideToken`)

All pages above are `verified`.

- [Universal Sidedness Facade](architecture/facade-vision.md) — `draft`, deliberately: a
  vision/direction page holding open questions, not a completed description. No roadmap node
  tracks it; one gets opened once it decomposes into concretely scoped work.
- [SatchelHealth](architecture/satchel-health.md) — `draft`: run-monitoring / self-verification
  home, covering `MobJig`, `LevelJig`, and `PlayerJig`.

## Spec

Boundary contracts — narrower and stricter than the architecture pages above; see
[BHW — Wiki Conventions](../meta/bhw.md#spec-pages-a-stricter-sibling-of-architecture-pages) for
what qualifies:

- [Forge Integration & Sidedness Contract](spec/forge-integration.md) — which classes may touch
  Forge's event buses directly, and the sidedness/thread-binding rules any code reaching into
  Satchel must follow.

## Design docs

No diagrams currently exist for Satchel. New ones are produced by the
[Cartographer](../../roles/cartographer.md) role as Mermaid source, filed under
`satchel/diagrams/` once they exist.

## Roadmap

Tracked under `RM_SAT` — see [ROADMAP_INDEX.md](../../ROADMAP_INDEX.md) for current status.

## Related pages

- [FrontierMode](../frontiermode/frontiermode.md)
