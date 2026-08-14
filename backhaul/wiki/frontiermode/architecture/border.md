---
id: frontiermode/architecture/border
category: frontiermode/architecture
slug: border
title: Border
summary: FrontierMode's world-border system -- the mod's one substantial feature,
  built on Satchel's fixture/facet and jig/scope model.
keywords: null
status: verified
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border

*Written 2026-08-11 as part of the post-strip-down documentation/roadmap-backfill pass. This is
FrontierMode's first `architecture/*` page — none existed before this (see
[FrontierMode](../frontiermode.md)). Everything below is read directly from current
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

**Rewritten 2026-08-13** following [FRO_012](../../../tickets/FRO_012_port-border-to-jigconfig-eventhandlers.md),
which ported this off the dead `SatchelJigRegistrar`/`SatchelStrap` pattern onto Satchel's
declarative `JigConfig`/`EventHandlers` system. Confirmed by a real `gradlew build` on both repos:
`BUILD SUCCESSFUL`, zero errors, first time in this investigation. Full before/after story:
[Jig & Strap Registration](../../satchel/architecture/jig-registration-break.md) and its
[recovery plan](../../satchel/architecture/jig-registration-recovery-plan.md).

`BorderModule.init()` (`border/BorderModule.java`) is the subsystem's single entry point, called
once from `FrontierMode`'s constructor. It:

1. Registers `BordersBundle`/`BordersFixture` with Satchel's `BundleFactories` — still the
   registry `ScopeEngine.create()` actually reads for construction.
2. Also declares a `JigBundles.Schema` (`bundles.schema(...)`) mirroring that same registration —
   required by `JigConfigValidator`, but not itself consumed for construction. Both exist
   deliberately, not redundantly; see the source comment on this if it looks like duplication.
3. Builds one `LevelJigConfig` for `FrontierKeys.BORDERS_JIG` with
   `sideApplicability = JigPolicies.SideApplicability.BOTH` — a single declared config, but
   `compileForSide()` runs independently per side at each side's own foundation boot, so this
   still yields two independent `LevelJig` instances (one per side), the same outcome as the old
   pattern's two separate `.register(SERVER, ...)`/`.register(CLIENT, ...)` calls, from one
   declaration instead of two.
4. Sets `config.policies().capabilities(new JigPolicies.Capabilities(true, false, false))` —
   `requiresPersistence = true`. Without this, `LevelJigConfig`'s default (`false`) makes
   `ScopeEngine_Server`'s hydrate/flush path throw `AccessFailed` the first time a border loads.
   Border is persisted state; this flag has to be set explicitly, it doesn't follow from having a
   bundle schema. **A second, separate call is also required and easy to miss:**
   `config.policies().persistence(new JigPolicies.Persistence(true, ...))`.
   `JigConfigValidator.validateCapabilities` cross-checks `capabilities().requiresPersistence`
   against `policies().persistence().persistent()` — declaring the capability alone isn't enough;
   without the matching policy call, validation rejects the config at foundation boot (on both
   sides, since this config is `BOTH`-applicability) with "requires persistence but persistence
   policy is not persistent," before any level ever loads. Found via a real `runClient` crash
   ([FRO_014](../../../tickets/FRO_014_border-persistence-crash.md)), not by reading source alone —
   `LevelJigConfig`'s default `Persistence` policy (`persistent = false`) compiles cleanly with
   javac, so this was invisible to every prior "does it compile" check in this project's history.
5. Builds an `EventHandlers` (`EventHandlers.builder().on(ScopeEvent.Tick.class, ...).build()`)
   subscribing `BordersTriggers::updateFinderItems` and `Rendering::onClientTick`, attached via
   `config.execution().eventHandlers(...)` — replaces the old `BorderStrap` entirely; no wrapper
   abstraction needed, `LogicalFoundation.installConfigs()` calls `.install(bus)` on it directly.
6. Registers the finished config with `Satchel.registerJigConfig(config)`.

Rule evaluation is server-side: `BorderLogic` + `DefaultBorderRules` (`border/server/rules/*`)
decide whether a proposed border change is legal; `BordersTriggers` hooks world events (e.g.
block placement) to drive border growth.

## Commands and client surface

- **Commands**: `border/server/commands/*` — a real custom Brigadier argument type
  (`BorderSelectorArgumentType`, backed by `BorderSelectorArgumentTypeInfo`, registered with
  Forge's `COMMAND_ARGUMENT_TYPES` registry in `FrontierMode.java`) lets commands select borders
  by more than raw UUID.
- **Rendering**: `border/client/render/level/*` draws borders and growth-trigger effects
  client-side. Two different drivers, not one: `WorldBordersRenderer` draws the actual ring
  geometry off Forge's own `RenderLevelStageEvent` directly (once per render frame, not through
  Satchel's tick), while `GrowthTriggerRenderer` spawns the `frontier_ring` particle effect
  (`assets/frontiermode/particles/frontier_ring.json`) off `ScopeEvent.Tick` via `Rendering.onClientTick`,
  subscribed through the `EventHandlers` wiring described above. `RenderContext` is the shared per-level cache both
  renderers read from (`BordersFixture` pulled via `BorderAPI`, refreshed every 20 ticks via
  `BordersRevisionMonitor`). `BorderView` is dead — fully commented out, not part of the live
  pipeline despite the name suggesting otherwise.

## Known gap: per-player evaluation is unfinished

`border/common/player/*` contains real logic classes — `BorderPlayerEval`, `BorderPlayerLogic`,
`BorderPlayerStatus`, `BorderPlayerStatusFixture`, `BorderPlayerStatusProposal` — but the bundle
meant to host them, `BorderPlayerBundle`, is entirely commented out, including a stubbed
`return null; //getOrCreateFacet(...)`. Nothing in `BorderModule.init()` constructs or registers
it. This reads as an abandoned or paused player-scoped extension to the (working) world-scoped
border system, not a design decision — see `RM_FRO_006` in the roadmap.

## Related pages

- [FrontierMode mod summary](../frontiermode.md)
- [Jig & Scope Runtime](../../satchel/architecture/runtime.md) — the registration/dispatch
  machinery "Runtime wiring" above plugs into
- [Bundle](../../satchel/architecture/bundle.md)
- [Fixture](../../satchel/architecture/fixture.md)
- [Border-Frontier Reconciliation](frontier-reconciliation.md) — how this architecture maps onto
  Sasha's Frontier design vocabulary, and what's still open
