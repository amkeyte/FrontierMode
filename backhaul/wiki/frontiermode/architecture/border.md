---
id: frontiermode/architecture/border
category: frontiermode/architecture
slug: border
title: Border
summary: FrontierMode's world-border system -- the mod's one substantial feature,
  built on Satchel's fixture/facet and jig/scope model.
keywords: null
status: verified
updated: '2026-08-28'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border

FrontierMode's world-border system is, at present, the entire substance of the mod: a
server-authoritative set of world borders that players can query, propose changes to, and see
rendered client-side, built end-to-end on Satchel's bundle/fixture/facet model and jig/scope
runtime (see [Bundle](../../satchel/architecture/bundle.md),
[Fixture](../../satchel/architecture/fixture.md), and Satchel's `common/jig/guts/*`).

Border is also FrontierMode's proof-of-concept module, not just its current entire substance —
nearly everything else in the mode's design (bosses, guardian mobs, discovery tools, loot/reward
density, Nether/End difficulty gating) is defined in terms of Border's Layer, Path, or
distance-from-origin concepts (see [Progression & Frontier
Mechanics](../design/progression.md)). Future modules are expected to build on Border's public
surface, which is exactly why `BorderAPI` already being trigger-agnostic — any caller can propose
a change; nothing is tied to command-handling specifically, see "Commands and client surface"
below — matters more than it would for a module nothing else depends on. [Boss](boss.md) is the
first such consumer: its bootstrap and record-creation hooks reach Border exclusively through
`BorderAPI`'s facet resolvers (see "Runtime wiring" below) — `BordersFixture` itself is never
handed to another module.

## Design vocabulary bridge

[Border Vocabulary](border-vocabulary.md) is the canon mapping between Sasha's design-side terms
and this page's architecture terms. Short version, for a reader arriving from the design side:

- **Border** = a single cylindrical range of blocks (this page's subject) = what a player is told
  is one **level** ([level is player-facing only](../design/progression.md#terminology-level-is-player-facing-only)
  by design ruling, not an architecture term).
- **Layer** (`Border.layer()`) = a Border's immutable sort key, assigned once at creation —
  see "Data model" below. Only *coincidentally* tied to Path under normal growth, not structurally
  bound to it — see [Known gaps](#known-gaps) below.
- **Path** (`BordersPathFacet`'s ordered `borderPath`) = the order a player has actually
  progressed through the game loop.
- **Relevance** (`DefaultBorderRules.getRelevant()`) = the single Border that's effective for a
  runtime decision at a given point — see "Runtime wiring" below.
- **Difficulty** = broader than Layer; not yet a real concept in this codebase (no
  `DifficultyRules` exists) — see [Border Vocabulary's "Difficulty"
  section](border-vocabulary.md#difficulty).
- **Frontier** = the union of all Borders established so far. **Not currently a named object or
  computed aggregate anywhere in this architecture** — still genuinely open, not just historically
  unconfirmed.

Full definitions, the Layer/Path/Difficulty split, and the open questions each one raises live on
[Border Vocabulary](border-vocabulary.md) — this section only orients a reader, it doesn't restate
that page.

## Data model

`BordersFixture` (`border/common/fixture/BordersFixture.java`) is the persisted-state unit — a
`SatchelFixture` holding the list of `Border` objects, a `borderPath` (an ordered progression of
border UUIDs), and a `seeded` boolean (see "Runtime wiring" below). It registers its state with
`registerCustom(...)`, hand-rolling save/load for the border list and path rather than using the
primitive field helpers, since both are collections of structured objects.

It exposes four **facets** — small accessor classes constructed with a back-reference to the
owning fixture, each covering one slice of the fixture's API:

- `PATH` (`BordersPathFacet`) — border progression order
- `CRUD` (`BordersCrudFacet`) — create/read/update/delete on individual borders
- `RULES` (`BordersRulesFacet`) — rule evaluation surface
- `INFO` (`BordersInfoFacet`) — read-only queries (scope, level, UUID, revision, `seeded()`)

`BordersFixture` stays a public Java type — Satchel's `FixtureKey<T extends SatchelFixture>`
requires `T` accessible everywhere its key is built and consumed, and `FrontierKeys`,
`BordersBundle`, and `BorderModule.init()`'s own `FixtureDecl` registration all reference the
class by name from outside `border.common.fixture`, so literal package-privacy isn't available
here. Encapsulation is enforced the way it actually matters instead: `BorderAPI.borders(Level)` is
gone, and `BorderAPI.PATH(Level)`, `.CRUD(Level)`, `.RULES(Level)`, `.INFO(Level)` — each resolving
the level's fixture internally and handing back the requested facet, never the fixture itself —
are the only path in from outside the package. Nothing outside `border.common.fixture` holds a
`BordersFixture` reference in practice, even though the class itself is public.

This is the canonical example of Satchel's fixture/facet split (see
[Fixture](../../satchel/architecture/fixture.md)): the fixture is the one persisted unit; facets
are non-persisted grouped views onto it, not sub-fixtures in their own right.

`BordersFixture` lives inside `BordersBundle` (`border/common/bundle/BordersBundle.java`), a
world-scoped (`LevelScope`) bundle described in its own source comment as "intentionally boring:
no logic, no state beyond fixtures."

### Layer and Path can legitimately diverge

`Border.layer()` is immutable, set once at creation. `BordersPathFacet.moveUp()`/`moveDown()` are
op-exposed commands that reorder `borderPath` without touching any border's layer — so the two
orderings can disagree, and `DefaultBorderRules.getRelevant()` sorts strictly by layer.
`BordersPathFacet.fixLayers()` reconciles them on demand, delegating to a bulk
`BordersFixture.reassignLayers(Map)`; see [Border Path & Layer
Reconciliation](path-layer-reconciliation.md) for the mechanism.

**Layer values are not required to be unique.** Layer and Path are definitionally unrelated (see
[Border Vocabulary](border-vocabulary.md#layer)), and `getRelevant()`'s nearest-center tie-break
resolves a same-layer overlap on its own — so two borders, on-path or off, can share a layer value
without anything downstream breaking.

## Runtime wiring

Border registers through Satchel's declarative `JigConfig`/`EventHandlers` system.

`BorderModule.init()` (`border/BorderModule.java`) is the subsystem's single entry point, called
once from `FrontierMode`'s constructor. It:

1. Declares a `JigBundles.Schema` (`bundles.schema(...)`) for `BordersBundle`/`BordersFixture` —
   the sole construction path `ScopeEngine_Server.create()`/`ScopeEngine_Client.create()` read.
   `BordersFixture` registration is schema-only — there is no separate `BundleFactories` call,
   matching `TrackingModule.init()` (see
   [Jig & Scope Runtime](../../satchel/architecture/runtime.md#worked-example-trackingmodule)).
2. Builds a `JigBundles.BundleDecl<LevelScope, BordersBundle>` wrapping that fixture decl, then a
   `JigBundles.Schema<LevelScope>` wrapping the bundle decl — the two-level `FixtureDecl` →
   `BundleDecl` → `Schema` shape `JigConfigValidator` expects.
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
5. Builds an `EventHandlers` (`EventHandlers.builder().on(ScopeEvent.Tick.class, ...).on(ScopeEvent.Loaded.class, ...).build()`)
   subscribing `BordersTriggers::updateFinderItems` and `Rendering::onClientTick` to `Tick`, and
   `onBordersScopeLoaded` (see below) to `Loaded` — attached via `config.execution().eventHandlers(...)` —
   replaces the old `BorderStrap` entirely; no wrapper abstraction needed,
   `LogicalFoundation.installConfigs()` calls `.install(bus)` on it directly.
6. Registers the finished config with `Satchel.registerJigConfig(config)`.

**A fresh level's first border bootstraps itself, without any command or trigger.**
`BordersFixture.INFO.seeded()` (backed by the persisted `seeded` boolean set once inside
`BordersPathFacet.grow()`'s own path append, and never cleared afterward — including by later
removing every border) is what distinguishes a level that has never had a border from one an
admin has cleared down to empty; `PATH.isEmpty()` alone can't make that distinction, so it isn't
what this checks. `onBordersScopeLoaded`, subscribed to `BORDERS_JIG`'s own `ScopeEvent.Loaded`
and filtered to the overworld, calls `BorderAPI.grow(level)` when `seeded()` is false, then hands
the resulting `Border` to `BossAPI.createBoss(level, border)` in the same handler. Server-side
only, guarded the same way `BordersTriggers`' own `Tick` handlers already are
(`Satchel.require().side() == LogicalSide.CLIENT`) — `BORDERS_JIG` is `BOTH`-applicability, so
`ScopeEvent.Loaded` fires on the client's own `LevelJig` too, and both calls here mutate
persisted state — see
[Boss § Defeat detection and the border-growth gap](boss.md#defeat-detection-and-the-border-growth-gap)
for the paired-call convention this follows, and why the call lives here rather than on Boss's own
side (Boss depends on Border, never the reverse; `BossModule.init()` runs after
`BorderModule.init()` for the same reason).

Rule evaluation is server-side: `DefaultBorderRules` (`border/server/rules/*`, reached through
the shared `BorderRules.ACTIVE` singleton) supplies the values — center, radius, next layer —
that `BordersPathFacet`/`BordersCrudFacet` turn into a proposal and apply directly;
`BordersTriggers` hooks world events (e.g. block placement) to drive border growth.

## Mutation surface

Every mutation goes through a proposal: `BordersCrudFacet.getProposal()` returns a fresh,
unconfigured `BorderProposal`; the caller configures it (`center()`, `radius()`, `layerIndex()`,
or `insert(Border)` to seed all three from an existing border) and hands it back to
`CRUD.applyProposal(proposal)`, which validates it against `BorderConstants`' radius bounds and
applies it atomically if it passes. `BorderProposal`'s own constructor is package-private — only
`CRUD.getProposal()` can mint one — but the type and its configuration methods are otherwise
public and general-purpose, on the same footing as `getProposal()`/`applyProposal()` themselves.
`BorderAPI`'s named operations (`grow`, `addBorder`, `transformBorder`, `growCenteredOn`) are the
safe, easy path for the common cases, not a closed set — any caller can build and apply its own
proposal directly for a case the named operations don't cover.

`applyProposal()` — and every other mutating `BorderAPI` operation (`grow`, `addBorder`,
`transformBorder`, `removeBorder`, `growCenteredOn`) — returns a `Result` rather than throwing or
returning `Optional.empty()`: an outcome enum, a failure-kind enum (populated only on failure,
distinguishing a transient not-ready state from a permanent validation rejection from a not-found
lookup), a message string, and the `Border` itself on success. `BorderSelectorResult`
(`border/server/commands/BorderSelectorResult.java`) is the established in-repo shape this
follows — static factories, final fields, tagged by an enum.

`bordersContaining(Level, BlockPos)` is the one deliberate exception: it's a zero-to-many query,
not a single-outcome mutation, and `Result`'s "the `Border` itself on success" shape doesn't fit
a query. It throws `SatchelException.ScopeNotReady` on a not-ready level, same as it always has —
`Result` is reserved for a rejected mutation a caller must act on, never for query absence.

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
  renderers read from (`BorderAPI`'s facet resolvers, refreshed every 20 ticks via
  `BordersRevisionMonitor`). `BorderView` is dead — fully commented out, not part of the live
  pipeline despite the name suggesting otherwise.
- **Readiness**: not-ready degrades differently depending on who's calling, not through one
  uniform mechanism. Every `BorderAPI` facet resolver (`PATH`/`CRUD`/`RULES`/`INFO`) proactively
  checks `Satchel.isReady()` ([SAT_032](../../../tickets/SAT_032_isready-gate.md)) before doing
  anything else, since they're reachable from the client render path before the world-identity
  token has necessarily arrived — there, not-ready is routine and silent: the resolver returns
  `Optional.empty()`, same as it always has. Server-side command dispatch is different: by the
  time a player can type a command, Border is expected to already be resolvable, so a not-ready
  state there means something else is already wrong, not a state to paper over —
  `bordersContaining(Level, BlockPos)`, the query `BorderSelector`'s `@containing`/`@coord`
  selectors call, throws `SatchelException.ScopeNotReady` rather than returning anything. `Result`
  (see "Mutation surface" above) is reserved for a rejected mutation a caller must act on and
  covers neither of these readiness cases. `RenderContext.getInstance()` goes through
  `LevelResolver.resolveScope` rather than constructing a `LevelScope` directly, for the same
  render-path reason — direct construction now throws `SatchelException.NotReady` pre-readiness
  instead of silently falling back, which would corrupt `RenderContext.CACHE`'s key stability if
  it were ever hit. See
  [Forge Integration & Sidedness Contract](../../satchel/spec/forge-integration.md#sidedness--the-contract-not-just-the-mechanism)
  for the general contract this follows.

## Known gaps

**Per-player evaluation** is a second, independent `JigConfig` alongside the world-scoped one
described under "Runtime wiring" above — `BorderPlayerBundle`/`BorderPlayerStatusFixture`
(`border/common/player/*`), player-scoped via Satchel's `PlayerJig`/`PlayerScope` rather than
`LevelScope`, registered and wired in `BorderModule.init()` the same way. `BorderModule`'s
`onPlayerScopeTick` handler recomputes each player's `BorderPlayerStatusFixture` every tick
against their current level's live border list — a derive-only snapshot (nearest border, distance,
inside flag), deliberately not persisted or networked, since it's cheap to recompute and has no
restart-survival requirement. `BorderAPI.getRelevant(ServerPlayer)` and the `@relevant` command
selector both read off this fixture. Tracked as
[RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md).

The code side is settled; the *design* side isn't. This per-player layer doesn't map onto any named
concept in FrontierMode's design vocabulary — there is no design-side answer to "what is a player's
own relationship to the Frontier," only an architecture-side mechanism for computing one. Architect's
question rather than Game Designer's, and deliberately not worth resolving until something needs it:
the likeliest forcing function is multiplayer's "whose frontier is it," parked in [Multiplayer Sketch
(Parked)](../design/multiplayer-sketch.md).

**Mutation validation, render lifecycle, and fixture/item robustness gaps.** A source-level
resilience pass found several structural gaps in the mutation, rendering, and persistence paths
described above — tracked as roadmap work rather than restated here:
[RM_FRO_011](../../../roadmap/RM_FRO_011_betty.md) (border proposal/path validation),
[RM_FRO_012](../../../roadmap/RM_FRO_012_carolyn.md) (client render lifecycle), and
[RM_FRO_013](../../../roadmap/RM_FRO_013_judy.md) (fixture load and compass robustness).

## Related pages

- [FrontierMode mod summary](../frontiermode.md)
- [Jig & Scope Runtime](../../satchel/architecture/runtime.md) — the registration/dispatch
  machinery "Runtime wiring" above plugs into
- [Bundle](../../satchel/architecture/bundle.md)
- [Fixture](../../satchel/architecture/fixture.md)
- [Border Vocabulary](border-vocabulary.md) — the canon Relevance/Layer/Path/Difficulty
  definitions this page's "Design vocabulary bridge" section summarizes
- [Border Path & Layer Reconciliation](path-layer-reconciliation.md) — design for the
  `fixLayers()` gap noted above
- [Boss](boss.md) — Tier 1's boss entity/spawn system, the first consumer of `BorderAPI.addBorder()`
  outside Border's own command layer
