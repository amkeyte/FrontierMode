---
id: satchel/architecture/new-module-checklist
category: satchel/architecture
slug: new-module-checklist
title: New Module Checklist
summary: Footguns every new Satchel jig/module consumer has hit at least once -- register
  schema only, wire executionPulse if sync is needed, wire Forge listeners, respect
  LogicalSideContext thread discipline.
keywords: null
status: draft
updated: '2026-08-15'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# New Module Checklist

A short, deliberately narrow checklist for anyone registering a new `JigConfig` consumer
(a "module," in the sense `TrackingModule`/`BorderModule` use the word) against Satchel's
runtime. Every item below is a mistake a real module has actually made — see
[Jig & Scope Runtime](runtime.md)'s worked example for the full trace of two of them. This page
doesn't re-derive the runtime's mechanics; read [Jig & Scope Runtime](runtime.md) first if
you haven't registered a jig config before.

## The checklist

1. **Register the bundle schema — and only the schema.** `JigBundlesConfig.bundles().schema(...)`
   is the sole construction path `ScopeEngine_Server.create()`/`ScopeEngine_Client.create()` read,
   since the schema/`BundleFactories` duality was removed. If you're copying an older module for
   reference, drop any `BundleFactories.registerFactory(...)` call it still has — that registry is
   gone, and a stale second registration is silently ignored, not an error, so it won't tell you
   it's dead weight.
2. **Set `executionPulse` if this module needs persistence or networking.** `.withTick(true)`
   alone makes a jig's scope tick; it does *not* make it flush to disk or sync to clients — that
   only happens from inside a `ScopeEngine`'s own `onExecutionPulse`. A module with
   `requiresPersistence`/`requiresNetworking` set but `executionPulse` left at its off-by-default
   value ticks forever with no error and no warning, just silent inertness. See
   [Jig & Scope Runtime](runtime.md#ingress-how-a-raw-forge-event-becomes-a-jig-tick) for the full
   chain, and [RM_SAT_013](../../../roadmap/RM_SAT_013_gary.md) for the health-check that now
   catches this case at runtime and logs a warning instead of failing silently.
3. **Wire `EventHandlers` against `ScopeEvent`, and guard every handler by jig key.**
   `SatchelEventBus` is the *one* shared bus per side — every jig's `ScopeEvent.Loaded`/
   `Unloaded`/`Tick` posts to the same bus, not a per-jig one. A handler that reads
   `info.jigInfo().jig` without first checking `info.jigInfo().key` against its own `JigKey` will
   fire for every other jig scoped to the same source (e.g. another `LevelJig` on the same
   dimension) and try to operate its own bundle logic against a foreign jig's engine. Put the
   guard first, before touching `info.jigInfo().jig` at all:
   ```java
   if (!MY_JIG_KEY.equals(info.jigInfo().key)) {
       return;
   }
   ```
4. **Don't assume `LogicalSideContext` is bound on threads you didn't get from a Forge event.**
   `ServerForgeIngress`/`ClientForgeIngress` bind it at the top of every `@SubscribeEvent` handler
   — that's the only place it's guaranteed set. Code reached from a worker thread, an async
   callback, or anything else outside that ingress chain will throw `IllegalStateException` from
   `LogicalSideContext.require()` the first time it tries to resolve a side. That's a loud, correct
   failure for genuinely-unbound code — but if you're writing a UUID-derivation helper or similar
   utility that might legitimately be called from either context, prefer
   `LogicalSideContext.current()` (an `Optional`) over `require()` so an unbound caller degrades
   instead of crashing.
5. **Remember the jig-per-side split.** A `sideApplicability = BOTH` config compiles into two
   independent `CompiledJigConfig`s — one per side's own foundation boot — not one instance shared
   across sides. Don't reach for cross-side state from inside a jig's own code; if two sides
   genuinely need to agree on something (see
   [Sync a Satchel world-identity token](../../../roadmap/RM_SAT_019_dennis.md) for a concrete
   example), that's a networking problem, not a shared-object one.

## Worked example

[Jig & Scope Runtime](runtime.md#worked-example-trackingmodule)'s `TrackingModule` walkthrough is
the fullest real trace of items 1 and 3 above: the same module hit both, one after the other,
across successive real `runClient` runs. `TrackingModule`
(`common/newconfig/TrackingModule.java`) is also just a good template to read end to end before
writing a new module — build a `FixtureDecl`/`BundleDecl`/`Schema`, build `EventHandlers` with the
jig-key guard from item 3, construct a `LevelJigConfig`, attach both, register.

## Related pages

- [Jig & Scope Runtime](runtime.md) — the mechanics this checklist assumes
- [Bundle](bundle.md)
- [Fixture](fixture.md)
- [Networking](net.md)
- [Persistence](persistence.md)
