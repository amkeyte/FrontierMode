---
id: satchel/architecture/runtime
category: satchel/architecture
slug: runtime
title: Jig & Scope Runtime
summary: The jig/scope/foundation tick-and-event delivery machinery underneath Satchel
  -- foundations, the dispatch chain, JigConfig registration, and the three jig kinds.
keywords: null
status: verified
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Jig & Scope Runtime

Everything in this page lives under `common/jig/guts/*`, `client/jig/guts/*` / `server/jig/guts/*`,
`common/lifecycle/*`, and `common/newconfig/*` (both the top-level package and its `newnew`
subpackage). [Satchel mod summary](../satchel.md) already covers the foundation/tick/event flow at
a high level and the fragility of `LogicalSideContext`; this page goes one layer deeper — how a mod
actually registers a jig and hooks its own code to the events this machinery produces — and adds
the two things `satchel.md` doesn't cover: bundle-level lifecycle and the three concrete jig kinds.

## Foundations

A `LogicalFoundation` (`common/jig/guts/LogicalFoundation.java`) is a per-side runtime instance:
it owns the `SatchelEventBus`, the three lifecycle dispatchers (below), the jig registry
(`Map<JigKey<?>, JigInfo>`), and a reference to its `ASatchelFoundationBooter`. Exactly two exist
per JVM — `Satchel.FOUNDATIONS` is a real `Map<LogicalSide, LogicalFoundation>` — and both are
alive simultaneously in single-player. `ClientFoundationBooter`/`ServerFoundationBooter`
(`client/jig/guts/`, `server/jig/guts/`) are the only code that constructs one: each builds a
`LogicalFoundation`, calls `Satchel.installFoundation(foundation)`, installs the event bus and all
three dispatchers, then compiles and installs that side's `JigConfig`s (see below). Installation is
idempotent-guarded (`Satchel.installFoundation` throws if a side is already installed) but nothing
prevents calling `installFoundation()` before `bindFoundation()` — the two booter methods are
separate calls, not one atomic step.

`LogicalSideContext` (`common/jig/guts/LogicalSideContext.java`) is a bare `ThreadLocal<LogicalSide>`
with `bind`/`current`/`require`/`runWith`. It has no knowledge of Satchel at all — it only answers
"which side is this thread". `ClientForgeIngress`/`ServerForgeIngress` (`client/lifecycle/`,
`server/lifecycle/`) call `BOOTER.bindFoundation()` — which just calls
`LogicalSideContext.bind(LogicalSide.CLIENT|SERVER)` — at the top of *every* `@SubscribeEvent`
handler, not once at startup. That's what makes `Satchel.require()` resolve correctly per-call: it
reads `LogicalSideContext.require()` and looks the side up in `Satchel.FOUNDATIONS`. Any code that
reaches Satchel from a thread that was never bound (a worker thread, an async callback) throws
`IllegalStateException` from `LogicalSideContext.require()`, not a wrong-side answer — the failure
mode is loud, not silent, for *that* specific case. It's not loud for the related-but-different bug
`satchel.md` already documents (`requireClient()`'s side check, tracked in
[SAT_006](../../../tickets/SAT_006_fix-inverted-requireclient-side-check.md)) — that's about which
side a bound thread claims to be, not whether one is bound at all.

## Ingress: how a raw Forge event becomes a jig tick

`ServerForgeIngress`/`ClientForgeIngress` are the *only* classes in Satchel that touch raw Forge
events — every other class reads `ScopeEvent`/`SatchelEvent`/`BundleEvent` off `SatchelEventBus`
instead. Both ingress classes are structurally identical, one per side:

1. `LevelEvent.Load` (priority `HIGHEST`) → `bindFoundation()` + `ensureInstalled()` — first-ever
   level load on this side triggers `BOOTER.installFoundation()` exactly once (`INSTALLED` is a
   static boolean guard).
2. `LevelEvent.Load` (normal priority) → `bindFoundation()`, then
   `foundation.introduceSource(level)`. `LogicalFoundation.introduceSource(Object)` walks every
   registered `JigInfo`, asks each one's binding to `resolveScope(source)` (via `JigInfo.resolveScope`,
   which checks `binding().sourceType()` first — a jig whose `sourceType()` isn't `Level` silently
   ignores the source, no error), and calls `JigInfo.addScope(scope, source)` for the first time a
   scope is seen.
3. `LevelEvent.Unload` → rebinds the side and calls `introduceSource(level)` again — the source
   itself doesn't change, so this is effectively a no-op re-announcement, not a teardown call.
   `LogicalFoundation.tryRemoveSource(Object)` is the method that would actually drive `onUnload`
   for a jig, but nothing calls it — see "Known gaps" below.
4. `TickEvent.ServerTickEvent`/`ClientTickEvent` (`Phase.END`) → `bindFoundation()` +
   `ensureInstalled()`, then `foundation.foundationLifecycle().pulse()`.

`FoundationLifecycleDispatcher.pulse()` (`common/lifecycle/FoundationLifecycleDispatcher.java`) is
the actual per-tick driver. First pulse transitions `NEW → STARTED` and posts
`SatchelEvent.Started`. Every pulse after that walks **every** `JigInfo` the foundation knows about,
and for each one, every `ScopeInfo` it owns, calling `jig.handleExecutionPulse(scopeInfo)` then
`jig.onTick(scopeInfo)` — unconditionally, not filtered by lifecycle config at this layer (the
filtering happens one level down, inside `ASatchelJig`).

`ASatchelJig.handleExecutionPulse` (`common/jig/guts/ASatchelJig.java`) is where phase-dependent
branching actually happens: a `NEW` scope tries to converge to ready (`tryMarkScopeReady` →
`coupler().tryMarkReady`) and, if accepted, calls `onLoad` — which calls
`coupler().onScopeLoad(info)` then `foundation().scopeLifecycle().signalScopeLoaded(info)`, which is
the call that actually posts `ScopeEvent.Loaded` onto the bus. A `LOADED` scope runs
`coupler().onExecutionPulse(info)` if `execution().lifecycle().participatesInExecutionPulse()` is
true (off by default — `JigPolicies.Lifecycle.defaults()` sets it `false`). This is a sharp edge in
practice: `ScopeEngine_Server.flushIfDirty()`/`scheduleSync()` (persistence flush + client sync)
and `ScopeEngine_Client.applyIncomingParcels()` (parcel drain) only ever run from inside an engine's
own `onExecutionPulse`, so a jig that calls `.withTick(true)` without *also* calling
`.withExecutionPulse(true)` ticks normally but never persists or syncs anything — no error, no
warning, just silent inertness. Border hit exactly this
([FRO_018](../../../tickets/FRO_018_border-executionpulse-disabled.md)): persistence-required and
tick-enabled, but execution-pulse was left at its off-by-default value, so — independent of
[SAT_026](../../../tickets/SAT_026_network-register-never-called.md)'s separate network
registration bug — no flush or sync ever happened at all. `ASatchelJig.onTick`
checks `participatesInTick()` (also config-driven), calls `coupler().onJigTick(info)`, then
`foundation().scopeLifecycle().signalScopeTick(info)` — the call that posts `ScopeEvent.Tick`. So
the full chain from Forge to a subscriber's callback is:

```
TickEvent.ServerTickEvent/ClientTickEvent
  → ServerForgeIngress/ClientForgeIngress.onExecutionPulse
  → foundation.foundationLifecycle().pulse()
  → FoundationLifecycleDispatcher walks every JigInfo/ScopeInfo
  → ASatchelJig.handleExecutionPulse / .onTick
  → ScopeLifecycleDispatcher.signalScopeLoaded / signalScopeTick
  → SatchelEventBus.post(ScopeEvent.Loaded / Tick)
  → every subscriber registered via EventHandlers.install(bus)
```

`ScopeEngine_Server`/`ScopeEngine_Client` (one per side, constructed by each booter's `engine()`)
are the thing `AScopeCoupler.onExecutionPulse`/`onJigTick` actually delegate to — this is where
bundle-level work (hydration, dirty-flush, parcel sync) happens, underneath the jig/scope layer
this page describes. See [Persistence](persistence.md) and [Networking](net.md) for that layer.

## JigInfo, ScopeInfo, and SatchelJig

`SatchelJig<S extends SatchelScope>` (`common/jig/guts/SatchelJig.java`) is the interface a jig
*kind* implements — `LevelJig`, `ModelJig`, and (dead) `PlayerJig` are the three that exist.
`ASatchelJig<S>` is the concrete base every real jig extends; it owns lifecycle dispatch
(above) and delegates bundle access (`get`/`ask`/`getOrCreate`) to its `ScopeCoupler`.

`AScopeCoupler.getOrCreate()`'s resilience depends on both engines throwing the *same* exception
type on a missing bundle: it calls `get()`, catches `SatchelException.BundleNotFound` specifically,
and falls through to `create()` on that catch alone — any other exception type propagates
uncaught. `ScopeEngine_Server.get()` always has; `ScopeEngine_Client.get()` threw plain
`IllegalStateException` in both its failure branches until
[SAT_024](../../../tickets/SAT_024_client-engine-wrong-exception.md), meaning the client-side
fallback never actually engaged — any legitimate "no client-local bundle yet" state (the normal
first-render condition) crashed instead of transparently creating the bundle. Found via
`BorderAPI.borders()`'s client-side `getOrCreate()` call from `RenderContext`/
`WorldBordersRenderer` — the first client-side `getOrCreate()` call site in either repo to hit a
genuinely-missing bundle.

`JigInfo` (`common/jig/guts/JigInfo.java`) is the runtime record for one *installed jig* — it
pairs a `JigKey<?>`, a `SatchelJig<?>` instance, and a `ScopeCoupler`, and owns the
`Map<SatchelScope, ScopeInfo>` of every scope that jig currently tracks. One `JigInfo` exists per
compiled `JigConfig`, created by `LogicalFoundation.installConfigs(List<CompiledJigConfig>)` during
foundation boot.

`ScopeInfo` (`common/jig/guts/ScopeInfo.java`) is the runtime record for one *scope instance* under
a specific jig — its `Phase` (`NEW → LOADED → UNLOADING → UNLOADED`), its readiness, and (via
`IJigConfigurable`) a live reference to that jig's compiled config categories
(`binding()`/`execution()`/`policies()`/`bundles()`). A `JigInfo` can own many `ScopeInfo`s (one
`LevelJig` tracks one `ScopeInfo` per loaded dimension); a `ScopeInfo` belongs to exactly one
`JigInfo`.

`ScopeInfo.jigInfo()` is the back-reference from a scope to its owning `JigInfo`, installed once
by `JigInfo.addScope()` right after construction (`installJigInfo(this)` — the only place a
`ScopeInfo` is ever created, and the only place its owning `JigInfo` is naturally in scope).
Previously a stub (`return null`) — see [SAT_020](../../../tickets/SAT_020_jiginfo-null.md), fixed
during the documentation-coverage runtime-verification pass.

## The `JigConfig` declarative registration system

This is the layer that answers "how does a mod plug a jig into the runtime above" —
`common/newconfig/newnew/*`. `JigConfig<S, SRC>` is an abstract preset tree with four category
lenses, each a thin, purpose-grouped view over shared state (same pattern as `Bundle`'s
facets — see [Fixture](fixture.md)):

- **`JigBindingConfig<S, SRC>`** — identity: `jigType`, `couplerType`, `scopeType`, `sourceType`,
  `sideApplicability` (`CLIENT`/`SERVER`/`BOTH`), the `scopeResolver` function, the
  `uuidDeterminer`, and `referenceLevelResolver` (used by `ScopeEngine_Server` to find the
  `ServerLevel` a persistence/networking capability needs — see
  `ScopeEngine_Server.resolveServerLevel`). `LevelJigConfig.createPresets()` defaults this to
  `LevelScope::level` (a `LevelScope` already holds its own `Level` directly, no lookup needed) —
  it was unset entirely until [SAT_023](../../../tickets/SAT_023_scopeengine-shared-config.md), so
  every capability check unconditionally threw `AccessFailed` regardless of whether a `Level` was
  genuinely reachable.
- **`JigExecutionConfig`** — *when* the jig participates: `lifecycle` (a `JigPolicies.Lifecycle`
  record of four booleans — load/unload/tick/executionPulse participation), `executionPriority`,
  `tickOrder`, and `eventHandlers` (an `EventHandlers` instance, or built inline via
  `eventHandlersBuilder()`).
- **`JigPoliciesConfig`** — constraints, not behavior: `readiness`, `sync`, `persistence`, `errors`,
  `diagnostics`, and `capabilities` (`requiresPersistence`/`requiresNetworking`/`requiresClock` —
  each one, if true, makes `ScopeEngine_Server` require a resolvable `ServerLevel` for that
  operation, throwing `SatchelException.AccessFailed` if none is available).
- **`JigBundlesConfig<S>`** — the `JigBundles.Schema<S>` this jig's scopes expose.

`LevelJigConfig` (`common/newconfig/newnew/LevelJigConfig.java`) is the one concrete `JigConfig`
subclass that exists — it pins `jigType`/`couplerType` to `LevelJig`/`LevelScopeCoupler`,
`scopeType`/`sourceType` to `LevelScope`/`Level`, wires `scopeResolver`/`uuidDeterminer` to
`LevelResolver`, defaults `sideApplicability` to `SERVER`, and leaves `bundles.schema` unset
(a consumer must call `.bundles().schema(...)` before compiling, or `JigConfigValidator` rejects
it). Both real consumers — Border and `TrackingModule` (below) — construct a `LevelJigConfig`,
override the fields they need, and register it.

**Registration and compilation**, driven by `JigConfigCompiler` (`common/newconfig/newnew/JigConfigCompiler.java`):

1. **Mod init**: a consumer calls `Satchel.registerJigConfig(config)` →
   `JigConfigCompiler.register(config)`, which stores it in a static
   `Map<JigKey<?>, JigConfig<?,?>>`. Exactly one config per `JigKey` — a second `register()` call
   for the same key throws.
2. **Foundation boot** (both booters, after installing the event bus and dispatchers):
   `JigConfigCompiler.compileForSide(side)` freezes the registry (`register()` throws after this),
   filters configs by `sideApplicability`, runs `JigConfigValidator.validate(...)` on each survivor
   (missing binding/bundle-schema/execution/policy fields all throw `IllegalStateException` here,
   loudly, before anything runs), and wraps each into an immutable `CompiledJigConfig`.
3. **`LogicalFoundation.installConfigs(List<CompiledJigConfig>)`**: for each compiled config,
   instantiates the jig (`JigConfigCompiler.instantiateJig` — no-arg constructor +
   `installJigConfig` + `installKey`), instantiates the coupler and wires it to that side's
   `ScopeEngine`
   (`instantiateCoupler` — also registers and freezes the bundle schema on the engine here), builds
   the `JigInfo`, and — the line SAT_011 added — calls `cfg.execution().eventHandlers().install(eventBus())`
   if handlers were declared. This is the step that connects a jig's `EventHandlers` to the live
   `SatchelEventBus`; without it, a jig's config compiles and ticks but nothing is subscribed.

   Note the loop runs once per jig, but `instantiateCoupler` calls `engine.installJigConfig(config)`
   against `booter().engine()` — the **same shared per-side `ScopeEngine_Server`/`ScopeEngine_Client`
   singleton** every iteration. That call plainly overwrites the engine's own
   `binding`/`execution`/`policies`/`bundles` fields rather than accumulating per jig, so those
   fields on the engine itself only ever reflect whichever jig installed last. Any engine method
   that needs a specific jig's config must read it off the `ScopeInfo`/`JigInfo` passed in, not off
   `this` — see [SAT_023](../../../tickets/SAT_023_scopeengine-shared-config.md), which found
   `resolveServerLevel` doing exactly the wrong one.

`JigConfigCompiler.compileForSide` runs independently per side at each side's own foundation boot —
one `sideApplicability = BOTH` config compiles into two separate `CompiledJigConfig` instances (one
per booter invocation), which is why one shared `LevelJigConfig` produces two genuinely independent
`LevelJig` instances rather than one shared object leaking across sides. See
[Border](../../frontiermode/architecture/border.md#runtime-wiring) for the concrete example — it's
the only place in either repo where `sideApplicability = BOTH` is actually exercised.

### `EventHandlers`

`EventHandlers` (`common/newconfig/EventHandlers.java`) is a plain builder over a list of
`Consumer<SatchelEventBus>` installers — `.on(EventType.class, handler)` records
`bus -> bus.subscribe(EventType.class, handler)` without touching a bus yet; `.install(bus)` runs
every recorded installer against a real bus. It has no lifecycle awareness of its own — it's a
declarative recipe, executed exactly once by `LogicalFoundation.installConfigs` as described above.

### Worked example: `TrackingModule`

`TrackingModule` (`common/newconfig/TrackingModule.java`) is Satchel's own internal jig consumer,
initialized unconditionally from `SatchelMod`'s constructor (`TrackingModule.init()`). It's the
clearest end-to-end example of the pattern any consumer follows: **register a `BundleFactories`
entry** (`registerFactory(BUNDLE, ...).registerFixture(TRACKER, ...)` — the actual
bundle-construction registry `ScopeEngine_Server.create()` reads; easy to skip since it's a
separate call from the schema below and nothing enforces doing both, see the bug this caught,
below), build a `JigBundles.Schema` (one bundle, one `TrackerFixture` — required by
`JigConfigValidator`, but *not* itself consumed for construction, same duality
[border.md](../../frontiermode/architecture/border.md) documents for `BordersFixture`), build an
`EventHandlers` subscribing `ScopeEvent.Loaded`/`Unloaded`/`Tick`, construct a `LevelJigConfig`,
attach both via `.bundles().schema(...)` and `.execution().lifecycle(...).eventHandlers(...)`,
then `Satchel.registerJigConfig(config)`.

**`TrackingModule` caught two real bugs this way, both now fixed**, surfaced one after the other
by successive real `runClient` runs during the documentation-coverage's runtime-verification pass
(the point of that pass — see the [plan page](../../plans/doc-coverage.md)):

1. All three of `TrackingModule`'s event handlers (`onScopeLoaded`/`onScopeUnloaded`/`onScopeTick`)
   start with `var jig = info.jigInfo().jig;`, which NPE'd every time — `ScopeInfo.jigInfo()`
   unconditionally returned `null` (see [SAT_020](../../../tickets/SAT_020_jiginfo-null.md),
   confirmed by a real crash log: the first `ScopeEvent.Loaded` posted for any server-side level
   load reached `TrackingModule.onScopeLoaded` and null-derefed immediately). `Border`'s own
   `EventHandlers` happen not to use this pattern (`BordersTriggers::updateFinderItems` and
   `Rendering::onClientTick` reach their jig through `BorderAPI` instead), which is why this hadn't
   surfaced as a Border-side failure. Fixed by wiring `ScopeInfo`'s back-reference in
   `JigInfo.addScope()` — see "JigInfo, ScopeInfo, and SatchelJig" above.
2. With (1) fixed, the next run reached `TrackingModule.onScopeLoaded`'s
   `jig.getOrCreate(info.scope(), BUNDLE)` call and threw `IllegalStateException: No
   BundleFactory registered for BundleKey[...tracker_bundle...]`, from
   `ScopeEngine_Server.create()`. Root cause: `TrackingModule.init()` built the
   `JigBundles.Schema` but never called `BundleFactories.registerFactory(...)` — the schema alone
   satisfies `JigConfigValidator`, so nothing forced the second, separate registration that
   construction actually depends on (see [SAT_022](../../../tickets/SAT_022_tracker-bundlefactory-missing.md)).
   Fixed by adding the `BundleFactories.registerFactory(...).registerFixture(...)` call shown
   above, mirroring `BorderModule.init()`'s existing pattern for `BordersBundle`.
3. With (2) fixed, the next run reached bundle hydration itself and threw
   `SatchelException.AccessFailed: Capability PERSISTENCE requires Level, but no referenceLevel
   available`, from `ScopeEngine_Server.resolveServerLevel`, called via `hydrateBundle` →
   `create`. Two separate root causes, both fixed (see
   [SAT_023](../../../tickets/SAT_023_scopeengine-shared-config.md) for full detail): (a)
   `resolveServerLevel` read the shared engine's own `policies()` instead of the specific scope's
   `info.policies()`, so it evaluated `TrackingModule`'s capability check against whichever jig
   (Border) had installed into the shared `ScopeEngine_Server` singleton last; (b)
   `LevelJigConfig` never defaulted `referenceLevelResolver`, so `info.referenceLevel()` returned
   `null` unconditionally regardless of (a) — fixed by defaulting it to `LevelScope::level` in
   `LevelJigConfig.createPresets()`.

## Bundle-level lifecycle

`BundleLifecycleDispatcher`/`BundleEvent` (`common/lifecycle/BundleLifecycleDispatcher.java`,
`BundleEvent.java`) look like a sibling of `ScopeLifecycleDispatcher`/`ScopeEvent`, and share the
same `SatchelEventBus` (`BundleLifecycleDispatcher` posts through
`foundation.eventBus().post(...)`, exactly like `ScopeLifecycleDispatcher`), but they're a
different granularity, driven from a different layer:

- **`ScopeEvent`** (`Loaded`/`Tick`/`Unloaded`) is emitted once per *jig+scope*, from
  `ASatchelJig`/`ScopeLifecycleDispatcher` — the layer this page has covered above.
- **`BundleEvent`** (`Loaded`/`Tick`/`Unloaded`) is emitted once per *individual bundle* within a
  scope, from the side-specific `ScopeEngine` (`ScopeEngine_Server`/`ScopeEngine_Client`), not from
  the jig layer at all. `ScopeEngine_Server.hydrateBundle` calls `signalBundleLoaded` only after a
  bundle actually finishes hydrating (`!hydratedBefore && bundle.isHydrated()`) — so a
  `BundleEvent.Loaded` can fire well after its owning scope's `ScopeEvent.Loaded`, on whatever pulse
  hydration happens to complete on. `ScopeEngine_Client.applyIncomingParcels` fires the client-side
  equivalent the same way, gated on parcel arrival rather than local hydration.
  `ScopeEngine_Server.onJigTick`/`ScopeEngine_Client.onJigTick` fire `BundleEvent.Tick` once per
  bundle, per jig tick — so a scope with three bundles emits one `ScopeEvent.Tick` and three
  `BundleEvent.Tick`s on the same jig tick.

No config category currently exposes bundle-level event subscription the way
`JigExecutionConfig.eventHandlers()` does for scope events — nothing in `newnew/*` builds
`EventHandlers` against `BundleEvent`. A consumer that wants per-bundle granularity would have to
subscribe directly against `foundation.eventBus()`, bypassing the declarative config path
entirely; no code in either repo currently does this.

## The two jig kinds (`ModelJig` deleted, see below)

Only `LevelJig` is exercised by anything real. Reading the current state directly, not by
inference:

- **`LevelJig`** (`common/jig/level/*` — `LevelJig`, `LevelResolver`, `LevelScope`,
  `LevelScopeCoupler`) — scoped to a Minecraft `Level`/dimension. Identity used to be derived
  purely from `dimension().toString()`; as of
  [RM_SAT_019](../../../roadmap/RM_SAT_019_dennis.md) it folds in a server-issued world-identity
  token when one is bound (`WorldIdentityContext`), falling back to the dimension-only UUID when
  it isn't — see [Forge Integration & Sidedness Contract](../spec/forge-integration.md) for the
  full sidedness picture this participates in. `LevelScopeCoupler.markReady` always returns
  `true` — no readiness gate beyond the scope existing. This is the only jig kind with a
  `JigConfig` subclass (`LevelJigConfig`) and the only one either repo's real consumers (Border,
  `TrackingModule`) use.
- **`ModelJig`** — **deleted**, along with `ModelCoupler`/`ModelScope`/`ModelSource`
  (`common/jig/model/*`) and `ServerModelIngress` (`server/jig/guts/model/*`). This page
  previously described it as structurally complete but unreachable (no `JigConfig` subclass, no
  registration path). Confirmed by the project owner during
  [RM_SAT_015](../../../roadmap/RM_SAT_015_george.md): it was built as a sample/reference model, not
  in-progress scaffolding — dead weight, not a gap. See
  [Jig & Strap Registration — Recovery Plan](jig-registration-recovery-plan.md#rot-to-remove-already-gone).
- **`PlayerJig`** (`common/jig/player/*` — `PlayerJig`, `PlayerResolver`, `PlayerScope`,
  `PlayerScopeCoupler`, `PlayerLoadEvent`, `PlayerTickEvent`, `PlayerUnloadEvent`) — **entirely
  commented out**, all seven files, package declaration included (each file's first line is
  `//package com.arryn.satchel.common.requireJig.player;`, itself referencing a `requireJig`
  package name that doesn't match the current `jig` package layout — this predates the
  jig/requireJig rename entirely). Not a partial stub: full class bodies, all commented, no live
  declaration a compiler or `grep -r "class PlayerJig"` would find as active code. Scaffolding
  history, not current runtime.

## Known gaps

- **`PlayerJig` and its whole package are dead** — see above. Still open;
  [RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md) tracks building a real one.
- **No bundle-level `EventHandlers` equivalent** — see "Bundle-level lifecycle" above. Still open.
- **Resolved: `ModelJig` had no registration path.** Fixed by deletion, not by wiring one — see
  "The two jig kinds" above.
- **Resolved: a `LevelJig` scope was never actually unloaded once loaded.**
  `ServerForgeIngress`/`ClientForgeIngress`'s `LevelEvent.Unload` handlers now call
  `LogicalFoundation.tryRemoveSource`, fixed by
  [RM_SAT_014](../../../roadmap/RM_SAT_014_joseph.md) — see
  [Forge Integration & Sidedness Contract](../spec/forge-integration.md) for the current-state
  contract this participates in.
- **Confirmed, not fixed: client-side bundle activation is interval-only, no proactive push.**
  [RM_SAT_013](../../../roadmap/RM_SAT_013_gary.md) traced `SatchelBundle.pulseSync` directly and
  confirmed there's no fast path — a freshly created bundle's first sync rides the same
  `syncIntervalTicks` counter as every later one (up to `DEFAULT_SYNC_INTERVAL_TICKS`, 5s at 20
  TPS, of latency), not "sits inert forever" as this page previously speculated, but not
  immediate either. A boot-time and runtime health-check (`LogicalFoundation.installConfigs`'s
  `checkExecutionPulseHealth`, `SatchelBundle.healthCheckPulse`) now logs a warning if a bundle
  sits below `ACTIVE` past a grace window, catching the FRO_018 shape (execution-pulse silently
  off) at the source instead of requiring a human to notice. A real fast path (e.g. an immediate
  `scheduleSync` call right after a bundle first reaches `ACTIVE`) remains unbuilt.

## Related pages

- [Satchel mod summary](../satchel.md)
- [Bundle](bundle.md)
- [Fixture](fixture.md)
- [Persistence](persistence.md)
- [Networking](net.md)
- [Border](../../frontiermode/architecture/border.md) — the concrete consumer this page's
  registration walkthrough is built from
- [Jig & Strap Registration — History](jig-registration-break.md) — the break/fix history behind
  this runtime's current shape
