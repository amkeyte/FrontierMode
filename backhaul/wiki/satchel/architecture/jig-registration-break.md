---
id: satchel/architecture/jig-registration-break
category: satchel/architecture
slug: jig-registration-break
title: Jig & Strap Registration — History
summary: Historical record of the compile-blocking jig/strap registration regression
  found and fixed on 2026-08-13 -- investigation notes, root causes, and the fix chain.
  Current mechanism is documented in runtime.md.
keywords: null
status: verified
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Jig & Strap Registration — History

**Read [Jig & Scope Runtime](runtime.md) for how this machinery works today.** This page is the
historical investigation record — how a compile-blocking regression in Satchel's jig registration
layer was found and fixed on 2026-08-13 — kept for its root-cause detail, not as a live reference.
Retitled and moved to `verified` once the fix chain below was confirmed complete (was previously
titled "(currently broken)" and left `draft`, which had gone stale by the time this page was
audited during the documentation-coverage pass — the break described below was already fixed).

*Original note, kept for context: the sections below were initially written from a full-repo
static sweep (dead-file-reference scan + unresolved-import scan) rather than an actual build, since
no working Java 17 / Forge toolchain or network access to Forge's maven was available at the time.
That's since been superseded — see "Confirmed by a real compile" below for actual `javac` output.
The static-sweep findings (`SatchelJigRegistrar`, `SatchelStrap`/`SatchelStrapRegistrar`) held up
against the real compile.*

## Confirmed by a real compile (2026-08-13)

`Satchel/build.log` — a real `gradlew build` run, Java 17, against the Satchel module only. It
fails at `:compileJava` with 8 errors, all inside Satchel itself. **FrontierMode was never
reached** — Satchel doesn't compile standalone, so the `SatchelJigRegistrar`/`SatchelStrap` break
described below (which lives in FrontierMode's `BorderModule.java`) hasn't even been exercised by
a real compiler yet. The break is more upstream than originally scoped: Satchel's own public
façade doesn't currently compile, independent of any consumer.

The 8 errors, none of which the static sweep could have caught (they're type/signature mismatches
and a missing interface method, not dead-symbol references):

- **`Satchel.java` (4 errors, lines 112-174)** — `Satchel.ask()`, `.get()`, and `.getOrCreate()`
  (the library's primary public entry points) call `f.askJig(jigKey)` and `f.requireJig(jigKey)`
  on `LogicalFoundation`. Neither method exists. `LogicalFoundation` currently has
  `askJigInfo(JigKey<?>)` and `requireJigInfo(JigKey<?>)` instead — different name, and a
  different return type (`JigInfo`, a wrapper, not `SatchelJig<?>` directly — callers would need
  to go through `JigInfo.jig` to reach the actual jig instance). Reads like `LogicalFoundation`
  was refactored and `Satchel.java`, its own façade, was never updated to match. This is the
  single most foundational break found so far — it blocks the entire library, not just Border's
  registration.
- **`ClientFoundationBooter.java:52,54` (2 errors)** — declares
  `CompiledJigConfig config = JigConfigCompiler.compileForSide(LogicalSide.CLIENT);`, but
  `compileForSide` returns `List<CompiledJigConfig>`. `ServerFoundationBooter.java` has this
  correct (`List<CompiledJigConfig> configs = ...`) — looks like a simple copy-paste-and-forgot-to-pluralize
  on the client side, not a design question.
- **`ScopeEngine_Client.java:19`** — doesn't override `freezeBundleSchema()`, required by the
  `ScopeEngine` interface. `ScopeEngine_Server.java` implements it correctly. `ScopeEngine_Client`
  also has no `registerBundleSchema(...)` override either, from inspection — javac only reports
  one missing method per class at a time, so there may be a second error here once the first is
  fixed.
- **`TrackingModule.java:86`** (`common/newconfig/`, non-`newnew`) — calls
  `JigConfig.<LevelScope, Level>builder()`, a static factory method that doesn't exist on the
  current `JigConfig` (which uses a per-jig-type subclass pattern instead — see
  `LevelJigConfig`). This revises the [recovery plan](jig-registration-recovery-plan.md)'s
  "explicitly not touching `common/newconfig/`" guidance: that package is live and shouldn't be
  deleted, but this specific file is itself stale relative to `JigConfig`'s current shape and
  needs fixing or removing.

Satchel exposes two entirely different eras of "how does a mod plug itself into the jig/scope
runtime" — one old, one new — and neither currently connects FrontierMode's Border module (its
only real consumer) to a working runtime. This is very likely why the repo doesn't compile.

## The old path: dead or missing, still called

FrontierMode's `BorderModule.init()` (`border/BorderModule.java`) calls three APIs that no longer
exist in working form:

- **`SatchelJigRegistrar.register(...)`** (called twice, once per side) — there is no class named
  `SatchelJigRegistrar` anywhere in the Satchel source tree, dead or alive. Not commented out —
  simply absent. `FrontierKeys.java` also imports it (unused).
- **`SatchelStrap`** / **`SatchelStrapRegistrar`** (`common/jig/strap/*`) — both files exist but
  are entirely commented out, no live definition. `BorderModule.java` still implements
  `SatchelStrap` (a local `BorderStrap` inner class) and calls
  `SatchelStrapRegistrar.register(...)`.
- Even if both of the above existed, the one call site that would ever *activate* a side's
  registrations — `Satchel.activateRegistrations(LogicalSide)` — is itself fully commented out in
  `Satchel.java`, with its two lines (`SatchelJigRegistrar2.activateForSide(...)`,
  `SatchelStrapRegistrar.activateForSide(...)`) both referencing the dead/renamed classes above.

`SatchelJigRegistrar2.java` (`common/jig/guts/`, also fully commented out) is very likely the
missing piece's direct predecessor: same shape as `SatchelStrapRegistrar` — an
`EnumMap<LogicalSide, List<Entry>>` with `register()` at mod-init and `activateForSide()` at
foundation spool-up. Reads like the "2" was meant to be dropped once finished (i.e. renamed to
plain `SatchelJigRegistrar`), and callers (`BorderModule.java`, `FrontierKeys.java`) were already
updated to the future name before the rename — and the class itself — actually happened.

## The new path: fully built, wired, and empty

A parallel, declarative config system already exists and is live: `common/newconfig/newnew/*`
(`JigConfig`, `JigConfigCompiler`, `CompiledJigConfig`, `JigBindingConfig`, `JigExecutionConfig`,
`JigPoliciesConfig`, `JigBundlesConfig`, `IJigConfigurable`). `JigConfigCompiler.register(JigConfig)`
collects declarations into a static map; `JigConfigCompiler.compileForSide(side)` is a real call
site, invoked from both `ClientFoundationBooter.installFoundation()` and
`ServerFoundationBooter.installFoundation()` during foundation boot. This part of the pipeline
works end to end.

The gap: **nothing ever calls `JigConfigCompiler.register(...)`.** A full-repo search for anywhere
a `JigConfig` (or its `LevelJigConfig` subclass) is actually constructed turns up nothing outside
Satchel's own internal plumbing. `LevelJigConfig` (`common/newconfig/newnew/LevelJigConfig.java`)
is a complete, concrete template shaped exactly like what Border needs — `LevelJig`/`LevelScope`
binding, execution policy, tick lifecycle — but it's a template nobody instantiates. It also
hardcodes `sideApplicability = SERVER`, and its `bundles.schema` is explicitly left unset
("left for later"), so even a straight port of `BorderModule.init()` onto this system isn't a
drop-in — the config path itself is unfinished, not just unused.

**Correction, 2026-08-13 (found working [SAT_008](../../../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md),
filed as [SAT_010](../../../tickets/SAT_010_fix-eventhandlers-claim-in-jig-wiki-page.md)):** the
paragraph below originally said no new-generation equivalent exists for the *strap* half at all.
That's wrong. `JigExecutionConfig` already has an `eventHandlers` slot (`eventHandlers()` /
`eventHandlers(EventHandlers)` / `eventHandlersBuilder()`), backed by a real `EventHandlers` class
(`common/newconfig/EventHandlers.java`) — a declarative builder of `SatchelEventBus` subscriptions
with a working `.install(bus)` method. `TrackingModule.java` already uses it correctly (its own
comment even says "replaces Strap"), subscribing to `ScopeEvent.Loaded`/`Unloaded`/`Tick`. The
actual gap is narrower: **nothing ever calls `.install(bus)`** on the `EventHandlers` object once
built — it's populated and stored in the compiled config, then never read again.
`ScopeLifecycleDispatcher` posts events straight to `foundation.eventBus()`, with no code path
connecting a jig's configured `EventHandlers` to that bus at all. So: the strap-equivalent exists,
is designed correctly, and has one real working consumer already (`TrackingModule`) — it's short
exactly one missing call, most likely inside `LogicalFoundation.installConfigs()` right after each
`JigInfo` is built.

## What still works

Worth being precise about the blast radius: the tick/event propagation machinery underneath all
of this — `ServerForgeIngress`/`ClientForgeIngress` → `foundation.foundationLifecycle().pulse()`
→ `ScopeLifecycleDispatcher.signalScopeTick()` → `SatchelEventBus.post(ScopeEvent.Tick)` — is
real, live, and confirmed working by direct source read (see [Satchel mod summary](../satchel.md)).
So is the per-side `LogicalFoundation` registry (`Satchel.FOUNDATIONS`, a real
`Map<LogicalSide, LogicalFoundation>`), and the Bundle/Fixture/persistence stack. What's broken
is specifically the layer that lets a *consumer mod* register a jig and its event subscriptions
with that runtime — Border is Satchel's only real consumer, and it's stuck calling an API that no
longer exists.

## Resolved — 2026-08-13

Both repos build clean: `BUILD SUCCESSFUL`, zero errors, confirmed by real `gradlew build` runs
against Satchel and FrontierMode. Full chain of fixes, in order:
[SAT_008](../../../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md) (Satchel's own façade),
[SAT_005](../../../tickets/SAT_005_client-booter-type-mismatch.md) (duplicate, closed alongside),
[SAT_011](../../../tickets/SAT_011_wire-eventhandlers-install-into-boot.md) (`EventHandlers.install(bus)`
wiring), [FRO_011](../../../tickets/FRO_011_fix-borderapi-leveljig-bad-method-call.md) (`BorderAPI`'s
bad method call), and finally
[FRO_012](../../../tickets/FRO_012_port-border-to-jigconfig-eventhandlers.md) — the actual port of
Border off `SatchelJigRegistrar`/`SatchelStrap` onto `JigConfig`/`EventHandlers`. The dead old-path
scaffolding (`SatchelJigRegistrar2.java`, `SatchelStrap.java`, `SatchelStrapRegistrar.java`, the
commented `Satchel.activateRegistrations(...)` block) has been deleted for real, not just left
commented out. See [Border](../../frontiermode/architecture/border.md#runtime-wiring) for what the
finished implementation actually looks like, and the
[recovery plan](jig-registration-recovery-plan.md) for the design reasoning that held up through
implementation.

One open, unrelated risk worth flagging here since it's adjacent: **SAT_006**
(`Satchel.requireClient()`'s inverted side check, still open, still unfixed) is more reachable now
than when it was filed — `ScopeEngine_Client`'s constructor calls `requireClient()`, and Border's
`LevelJig` now genuinely instantiates client-side as part of this port, where before nothing
exercised that path for real. Worth a priority look, not because this port caused it, but because
this port is what makes it live.

## Confirmed by a real compile — FrontierMode (2026-08-13)

Satchel now compiles clean (`BUILD SUCCESSFUL`, [SAT_008](../../../tickets/SAT_008_fix-4-satchel-compile-blocking-errors.md)
fixed and verified) — so a real `FrontierMode/build.log` now reaches FrontierMode's own
`compileJava` for the first time this investigation. 16 errors. Almost all of them are exactly
what the original static sweep predicted, now compiler-confirmed rather than inferred:

- `SatchelJigRegistrar` — "cannot find symbol," both the import in `BorderModule.java` and
  `FrontierKeys.java`, and all three call sites in `BorderModule.java`. Matches the static-sweep
  finding exactly (never existed, dead or alive).
- `com.arryn.satchel.common.jig.strap` — "package ... does not exist." The files
  (`SatchelStrap.java`, `SatchelStrapRegistrar.java`) are still on disk, but since their `package`
  declarations are themselves commented out, javac sees no live package there at all — the
  compiler's way of confirming "fully dead, no active declaration," same conclusion the static
  sweep reached.
- Five `method does not override or implement a method from a supertype` errors on `BorderModule`'s
  stray `@Override` annotations — a direct cascade of `BorderStrap implements SatchelStrap`
  failing, not a separate bug.

One genuinely new finding, same root-cause family as the `Satchel.java` façade break fixed in
SAT_008 but a separate call site: **`BorderAPI.java:57`** (`BorderAPI.levelJig()`) calls
`foundation().jigInfo(FrontierKeys.BORDERS_JIG).jig` — `LogicalFoundation` has no method named
`jigInfo`; it has `askJigInfo(JigKey<?>)` (returns `Optional`) and `requireJigInfo(JigKey<?>)`
(throws `SatchelException.JigNotFound` directly). `levelJig()`'s existing try/catch already
converts a `RuntimeException` into that same exception type, so `requireJigInfo` is the natural
fit — a one-line rename, not a design question. This is the second façade-drift call site found
(after `Satchel.java` itself), suggesting `LogicalFoundation`'s rename left more than one caller
behind. Checked: grepped `.jigInfo(` project-wide — the only other hits are `ScopeInfo.jigInfo()`
(a different method on a different class, legitimate) — this is the only remaining instance of the
actual bug.

## Corrections to prior documentation

This supersedes claims made in earlier passes, written before this was discovered:

- [Border](../../frontiermode/architecture/border.md) — "Runtime wiring" section, items 2 and 3,
  describe `SatchelStrap`/`BorderStrap` and `SatchelJigRegistrar` registration as working fact.
  They aren't; both are broken as described above.
- [RM_FRO_003](../../../roadmap/RM_FRO_003_barbara.md) and
  [RM_FRO_005](../../../roadmap/RM_FRO_005_carol.md) — both backfilled as `resolved` history
  citing this same wiring as already-working. The underlying files these nodes cite do exist (that
  part of the roadmap's evidence is accurate as a record of intent), but the wiring itself does
  not currently function. Flagging for PM to decide whether `resolved` still applies to
  intent-established-but-non-functional work, or whether these need a status of their own.
- RM_FRO_005 additionally cites `border/client/hooks/PlayerTickHandler.java` as an active hook.
  That file is entirely commented out — dead, not wired to anything.

## Related pages

- [Jig & Scope Runtime](runtime.md) — current-state reference this page's history led to
- [Jig & Strap Registration — Recovery Plan](jig-registration-recovery-plan.md) — proposed
  direction and first cleanup step
- [Satchel mod summary](../satchel.md)
- [Border](../../frontiermode/architecture/border.md)
- [Bundle](bundle.md)
- [Fixture](fixture.md)
