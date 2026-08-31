---
id: satchel/architecture/jig-registration-recovery-plan
category: satchel/architecture
slug: jig-registration-recovery-plan
title: Jig & Strap Registration — Recovery Plan
summary: Proposed direction and first cleanup step to get FrontierMode compiling again
  against Satchel's newer declarative config system, deprecating the dead imperative
  Registrar/Strap pattern.
keywords: null
status: verified
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Jig & Strap Registration — Recovery Plan

*Written 2026-08-13, immediately following
[Jig & Strap Registration — History](jig-registration-break.md). Working from an
explicit call: `common/newconfig/newnew/*` is the direction this was headed — assume it's being
phased in, assume the old imperative Registrar/Strap pattern is deprecated. This page is a
design/direction proposal, not a spec — the goal here is "what's the shape of the fix," not
line-level instructions.*

**Executed, 2026-08-13.** Every numbered item under "What 'recognizable as usable' requires"
below has been implemented and closed as a real ticket — item 1 as
[FRO_011](../../../tickets/FRO_011_fix-borderapi-leveljig-bad-method-call.md), items 2–4 together as
[FRO_012](../../../tickets/FRO_012_port-border-to-jigconfig-eventhandlers.md) — against a
Satchel-side prerequisite fixed by [SAT_008](../../../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md)
and [SAT_011](../../../tickets/SAT_011_wire-eventhandlers-install-into-boot.md).

**Pointer, 2026-08-13:** [Jig & Scope Runtime](runtime.md) is now the standalone reference for how
the resulting system works day to day. This page stays as the design record for *why* it was built
this way; read the page above for the current mechanism itself.

Both open questions
below are answered by the real implementation: `sideApplicability = BOTH` on one shared
`JigConfig` does produce two independent per-side jig instances (confirmed — see
[Border](../../frontiermode/architecture/border.md#runtime-wiring)), and `EventHandlers.install(bus)`
is called from `LogicalFoundation.installConfigs()` exactly where predicted. One thing this plan
didn't anticipate: `LevelJigConfig` needs `capabilities().requiresPersistence` set explicitly for
persisted bundles, or `ScopeEngine_Server` throws `AccessFailed` — not visible from reading the
config classes alone, only surfaced once persistence was exercised for real. Filed as its own
finding: [SAT_012](../../../tickets/SAT_012_trackingmodule-missing-persistence-capab.md) tracks the
same gotcha for `TrackingModule`, still open.

## A real compile changes the priority order

A real `gradlew build` (Java 17) against Satchel alone — see
[Jig & Strap Registration](jig-registration-break.md#confirmed-by-a-real-compile-2026-08-13) —
found Satchel doesn't compile standalone, before FrontierMode's Border wiring is even reached.
Most notably, `Satchel.java`'s own `ask()`/`get()`/`getOrCreate()` call methods
(`askJig`/`requireJig`) that don't exist on `LogicalFoundation` anymore (it has
`askJigInfo`/`requireJigInfo`, returning a different type). That has to be fixed before anything
in this plan is reachable at all — it's more foundational than the Border-specific jig-config work
below. Also found: `ClientFoundationBooter`'s `List<CompiledJigConfig>` vs `CompiledJigConfig`
mismatch (simple fix, matches `ServerFoundationBooter`'s already-correct pattern),
`ScopeEngine_Client` missing a `freezeBundleSchema()` override, and `TrackingModule.java` calling
a `JigConfig.builder()` that no longer exists. None of these were visible to the earlier static
sweep — worth remembering that method-level sweeps like that one can't catch type/signature
mismatches, only dead-or-missing symbols.

## Direction

`common/newconfig/newnew` (`JigConfig` / `JigConfigCompiler` / `CompiledJigConfig`) is the
sanctioned path forward. The old imperative pattern (`SatchelJigRegistrar`,
`SatchelJigRegistrar2`, `SatchelStrap`, `SatchelStrapRegistrar`) is deprecated and should be
deleted, not resurrected — even where a piece of it (`SatchelJigRegistrar2`) is complete enough
to technically finish. Reintroducing it would mean maintaining two parallel registration systems
long-term for no benefit; better to spend that effort finishing the one that's already wired into
boot.

## Why this direction (design rationale)

Reconstructed from the config classes' own doc comments, not from memory — worth recording since
none of this was written down anywhere else.

`JigConfig` applies the same pattern already proven by Bundle/Fixture/Facet (see
[Fixture](fixture.md)) to jig *configuration* instead of runtime state: one shared preset tree,
with thin, purpose-grouped "lens" classes over it rather than one flat object. The class doc says
this directly: *"This class owns the preset tree and exposes category lenses... Categories may be
migrated out of the top-level config incrementally."* `JigBindingConfig` (identity/scope),
`JigExecutionConfig` ("when" a jig participates), `JigPoliciesConfig` ("constraints and
guarantees, not behavior"), and `JigBundlesConfig` (bundle schema) each carry near-identical
language: *"a category lens over shared preset state... owns no data and performs no
validation."* Same shape as `BordersFixture`'s `PATH`/`CRUD`/`RULES`/`INFO` facets, just applied
one layer up the stack.

The problem it replaces: under the old `SatchelJigRegistrar` pattern, side-correctness was pure
convention — two separate manual `.register(LogicalSide.SERVER, ...)` /
`.register(LogicalSide.CLIENT, ...)` calls, and whether a jig ticked at all depended on whether
someone remembered to wire a `SatchelStrap` subscription. Nothing validated any of it.
`JigConfigCompiler`'s doc comment describes compilation as a strict one-way process — *"Validation
is performed, jigs are instantiated, couplers are instantiated, raw configuration never escapes
this class"* — with a real validator (`JigConfigValidator`) that fails loudly on a missing
binding or bundle schema before anything runs, and side-applicability collapsed into one declared
enum (`SideApplicability.CLIENT/SERVER/BOTH`) that the compiler filters on mechanically, rather
than trusting two hand-written call sites to agree. This is exactly the class of bug found and
ticketed separately as [SAT_006](../../../tickets/SAT_006_fix-inverted-requireclient-side-check.md)
(`requireClient()`'s inverted side check) — the new system is structurally harder to get wrong in
that specific way.

The "incrementally" language in `JigConfig`'s own doc comment also explains why both systems still
coexist rather than one replacing the other in a single pass: this was deliberately meant to grow
in alongside the old system, not land as a big-bang rewrite. That's consistent with where we found
it — half-finished, old callers not yet ported, by design rather than by accident.

## Key finding: the new path is closer to done than it looks

Tracing the actual call chain (not assuming from file names) turned up something that changes the
shape of this plan: **tick delivery is already fully automatic under the new system.** Once a jig
is registered through `JigConfig`, nothing else needs to be built to get `ScopeEvent.Tick`
flowing:

`JigConfigCompiler.register(config)` (mod init) → `compileForSide()` (foundation boot, already
called by both `ClientFoundationBooter`/`ServerFoundationBooter`) → `LogicalFoundation.installConfigs()`
instantiates the jig + coupler and stores a `JigInfo` → every tick,
`FoundationLifecycleDispatcher.pulse()` (already the live driver behind
`foundation.foundationLifecycle().pulse()`) walks every registered `JigInfo` and calls
`jig.handleExecutionPulse()` then `jig.onTick()` → `ASatchelJig.onTick()` checks
`execution().lifecycle().participatesInTick()` (set via `Lifecycle.withTick(true)` in the config)
and, if true, calls `foundation().scopeLifecycle().signalScopeTick(info)` — which is exactly the
call that posts `ScopeEvent.Tick` onto `SatchelEventBus`.

**Correction, 2026-08-13** (found while working [SAT_008](../../../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md),
filed as [SAT_010](../../../tickets/SAT_010_fix-eventhandlers-claim-in-jig-wiki-page.md)): the
original version of this section said `SatchelStrap` needed no replacement at all, since tick
delivery is automatic. That's true for *posting* the event, but wrong about *subscribing* to it —
a real declarative replacement already exists and didn't need designing.
`JigExecutionConfig.eventHandlers()` is exactly that: a slot for a builder-made `EventHandlers`
object (`common/newconfig/EventHandlers.java`) with a working `.install(SatchelEventBus)` method.
`TrackingModule.java` already uses it correctly — its own comment reads "Build eventHandlers
handlers (replaces Strap)" — subscribing to `ScopeEvent.Loaded`/`Unloaded`/`Tick` exactly the way
`BorderStrap` was trying to. The one real gap: **nothing ever calls `.install(bus)`** on the
`EventHandlers` a compiled config carries. It's built, stored, and never read again — most likely
a single missing line in `LogicalFoundation.installConfigs()`, right after each `JigInfo` is
built. See [Jig & Strap Registration](jig-registration-break.md#the-new-path-fully-built-wired-and-empty)
for the full correction.

## What "recognizable as usable" requires

1. **`BorderAPI.levelJig()` calls `foundation().jigInfo(...)`, which doesn't exist.** Confirmed by
   a real FrontierMode compile, 2026-08-13 — see
   [Jig & Strap Registration](jig-registration-break.md#confirmed-by-a-real-compile-frontiermode-2026-08-13).
   Same root cause as the `Satchel.java` façade break SAT_008 already fixed, different call site.
   One-line rename to `requireJigInfo(...)` — its existing try/catch already expects the exception
   type `requireJigInfo` throws directly.
2. **A `JigConfig` for Border's `LevelJig`.** `LevelJigConfig` (`newconfig/newnew/`) is already
   shaped for exactly this — `LevelJig`/`LevelScope` binding, tick lifecycle, resolver wiring —
   but it's a template nobody instantiates, it hardcodes `sideApplicability = SERVER`, and its
   `bundles.schema` is unset. Border needs configs for **both** sides (it currently registers
   `LevelJig` separately for `SERVER` and `CLIENT`), and needs its actual bundle schema
   (`BordersBundle`/`BordersFixture`) wired into `bundles.schema` — real design decision: two
   config instances (one per side) vs. one config with `sideApplicability = BOTH`, needs whoever
   builds this to check how `compileForSide`'s per-side filtering interacts with a single shared
   instance.
3. **`BorderModule.init()` calls `JigConfigCompiler.register(...)` instead of
   `SatchelJigRegistrar.register(...)`.** Mechanical, once (2) exists. `TrackingModule.java` is
   now a live, working template for the whole shape of this call — copy its pattern
   (`LevelJigConfig` + `.bundles().schema(...)` + `.execution().lifecycle(...).eventHandlers(...)`)
   rather than building it from scratch.
4. **Build an `EventHandlers` via `EventHandlers.builder().on(ScopeEvent.Tick.class, ...)...build()`**
   for `BordersTriggers::updateFinderItems` and `Rendering::onClientTick`, and attach it with
   `config.execution().eventHandlers(...)` — replacing the `BorderStrap`/`SatchelStrapRegistrar`
   block entirely. This is Satchel-pattern-consistent, not a one-off; the only Satchel-side
   prerequisite is the `.install(bus)` wiring gap noted above, which isn't Border-specific and
   should get fixed once for every consumer, not worked around per-mod.

That's the whole path back to a compiling, ticking Border module under the sanctioned direction.

## Rot to remove — already gone

This section previously listed `SatchelJigRegistrar2.java`, `SatchelStrap.java`/
`SatchelStrapRegistrar.java`, and a commented-out `Satchel.activateRegistrations(LogicalSide)`
block as safe-to-delete but still present. Re-checked while closing
[RM_SAT_015](../../../roadmap/RM_SAT_015_george.md): none of it exists in current `Satchel/src` —
grepped case-insensitively for `Registrar`, `SatchelStrap`, and `activateRegistrations`, zero hits.
Most likely removed during the 2026-08-13 compile-fix pass
([SAT_008](../../../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md)/
[SAT_011](../../../tickets/SAT_011_wire-eventhandlers-install-into-boot.md)) without this page being
updated to match — a real drift, now corrected rather than left as a stale to-do list.

`ModelJig`/`ModelCoupler`/`ModelScope`/`ModelSource`/`ServerModelIngress` (`common/jig/model/*`,
`server/jig/guts/model/*`) are also gone as of the same pass — deleted as dead weight (a
sample/reference model, not in-progress scaffolding), not part of this page's original scope but
recorded here since it was the other half of the same cleanup. See
[Jig & Scope Runtime](runtime.md#the-three-jig-kinds) for the current, corrected two-jig-kind
picture.

Already tracked elsewhere, not duplicating here: `common/fixture/NbtFixtureHydrationSource.java` +
`server/persistence/ServerPersistenceContext.java` (SAT_004), Border's dead per-player cluster
(`RM_FRO_006`).

## Explicitly not touching

`common/newconfig/` (non-`newnew` top level: `EventHandlers`, `JigBundles`, `TrackerFixture`,
`TrackingModule`) looked like a candidate for "old generation" at first glance, but it is **live,
shared infrastructure** — imported directly by `ScopeEngine`, `ScopeCoupler`/`AScopeCoupler`, both
`ScopeEngine_Client`/`ScopeEngine_Server`, `SatchelMod.java`, and by `newconfig/newnew` itself
(`JigBundlesConfig` imports `JigBundles`, `JigExecutionConfig` imports `EventHandlers`). Read this
as a foundation layer the newer config DSL is built on top of, not a competing old system. Leave
the package alone — **except** `TrackingModule.java` specifically, which the real compile (above)
caught calling a `JigConfig.builder()` that no longer exists. That one file is stale relative to
`JigConfig`'s current subclass-based shape and needs its own look, separate from the rest of the
package.

`common/exp/*` (`BaseConfig`, `CategoryAConfig`, `HomeCategoryAConfig`, `HomeConfig`, `UseCase`) —
confirmed unreferenced by anything outside itself. Genuinely scratch. Separate, lower-stakes
cleanup; not blocking anything, not part of this plan's critical path.

## Open questions for Lead Dev / whoever picks this up

- Whether `sideApplicability = BOTH` on a single `JigConfig` actually produces two independent
  jig instances (one per side) the way Border's current two-separate-registrations approach does,
  or whether that needs two distinct `JigKey`s/configs regardless.
- Where exactly `EventHandlers.install(bus)` should be called from inside `LogicalFoundation.installConfigs()`
  — right after `JigInfo` construction looks correct from reading the code, but wants confirming
  against real behavior once it's wired in.

## Related pages

- [Jig & Scope Runtime](runtime.md) — current-state reference for the system this plan designed
- [Jig & Strap Registration — History](jig-registration-break.md)
- [Satchel mod summary](../satchel.md)
- [Border](../../frontiermode/architecture/border.md)
