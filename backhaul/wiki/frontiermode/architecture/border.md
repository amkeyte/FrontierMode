---
id: frontiermode/architecture/border
category: frontiermode/architecture
slug: border
title: Border
summary: FrontierMode's world-border system -- the mod's one substantial feature,
  built on Satchel's fixture/facet and jig/scope model.
keywords: null
status: verified
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border

*Written 2026-08-11 as part of the post-strip-down documentation/roadmap-backfill pass. This is
FrontierMode's first `architecture/*` page — none existed before this (see
[FrontierMode](../../mods/frontiermode.md)). Everything below is read directly from current
source, not migrated from anywhere.*

FrontierMode's world-border system is, at present, the entire substance of the mod: a
server-authoritative set of world borders that players can query, propose changes to, and see
rendered client-side, built end-to-end on Satchel's bundle/fixture/facet model and jig/scope
runtime (see [Bundle](../../satchel/architecture/bundle.md),
[Fixture](../../satchel/architecture/fixture.md), and Satchel's `common/jig/guts/*`).

## Data model

`BordersFixture` (`border/common/fixture/BordersFixture.java`) is the persisted-state unit — a
`SatchelFixture` holding the list of `Border` objects and a `borderPath` (an ordered progression
of border UUIDs). It registers its state with `registerCustom(...)`, hand-rolling save/load for
the border list and path rather than using the primitive field helpers, since both are
collections of structured objects.

It exposes four **facets** — small accessor classes constructed with a back-reference to the
owning fixture, each covering one slice of the fixture's API:

- `PATH` (`BordersPathFacet`) — border progression order
- `CRUD` (`BordersCrudFacet`) — create/read/update/delete on individual borders
- `RULES` (`BordersRulesFacet`) — rule evaluation surface
- `INFO` (`BordersInfoFacet`) — read-only queries (scope, level, UUID, revision)

This is the concrete example used to resolve Satchel's own fixture/facet terminology drift (see
[Fixture](../../satchel/architecture/fixture.md)): the fixture is the one persisted unit; facets
are non-persisted grouped views onto it, not sub-fixtures in their own right.

`BordersFixture` lives inside `BordersBundle` (`border/common/bundle/BordersBundle.java`), a
world-scoped (`LevelScope`) bundle described in its own source comment as "intentionally boring:
no logic, no state beyond fixtures."

## Runtime wiring

`BorderModule.init()` (`border/BorderModule.java`) is the subsystem's single entry point, called
once from `FrontierMode`'s constructor. It:

1. Registers `BordersBundle`/`BordersFixture` with Satchel's `BundleFactories`.
2. Registers a `SatchelStrap` (`BorderStrap`) that subscribes to `ScopeEvent.Tick` for both
   gameplay logic (`BordersTriggers::updateFinderItems`) and client rendering
   (`Rendering::onClientTick`).
3. Registers a `LevelJig` with Satchel's `SatchelJigRegistrar`, once for `SERVER` and once for
   `CLIENT` — i.e. the border system runs its own jig instance on each logical side rather than
   assuming a single shared one.

Rule evaluation is server-side: `BorderLogic` + `DefaultBorderRules` (`border/server/rules/*`)
decide whether a proposed border change is legal; `BordersTriggers` hooks world events (e.g.
block placement) to drive border growth.

## Commands and client surface

- **Commands**: `border/server/commands/*` — a real custom Brigadier argument type
  (`BorderSelectorArgumentType`, backed by `BorderSelectorArgumentTypeInfo`, registered with
  Forge's `COMMAND_ARGUMENT_TYPES` registry in `FrontierMode.java`) lets commands select borders
  by more than raw UUID.
- **Rendering**: `border/client/render/level/*` (`WorldBordersRenderer`,
  `GrowthTriggerRenderer`, `BorderView`, `RingColorPalette`) draws borders and growth-trigger
  effects client-side, driven by the `frontier_ring` particle
  (`assets/frontiermode/particles/frontier_ring.json`).

## Known gap: per-player evaluation is unfinished

`border/common/player/*` contains real logic classes — `BorderPlayerEval`, `BorderPlayerLogic`,
`BorderPlayerStatus`, `BorderPlayerStatusFixture`, `BorderPlayerStatusProposal` — but the bundle
meant to host them, `BorderPlayerBundle`, is entirely commented out, including a stubbed
`return null; //getOrCreateFacet(...)`. Nothing in `BorderModule.init()` constructs or registers
it. This reads as an abandoned or paused player-scoped extension to the (working) world-scoped
border system, not a design decision — tracked as open roadmap work, not a documentation gap:
see `RM_FRO_006` in the roadmap.

## Related pages

- [FrontierMode mod summary](../../mods/frontiermode.md)
- [Bundle](../../satchel/architecture/bundle.md)
- [Fixture](../../satchel/architecture/fixture.md)
