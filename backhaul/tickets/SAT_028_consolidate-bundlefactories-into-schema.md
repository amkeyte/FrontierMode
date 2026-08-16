---
id: SAT_028
uid: SAT
number: 28
client: Satchel
status: done
title: Consolidate BundleFactories into JigBundles.Schema
context: 'JigBundles.BundleDecl already carries a real bundle Factory and FixtureDecl
  list -- functionally identical to what BundleFactories.registerFactory/.registerFixture
  store separately. ScopeEngine.create()/get() read BundleFactories.entryFor(key),
  never the already-populated bundleDecls map from registerBundleSchema(). Confirmed
  mid-refactor (user): the schema config was meant to do more than it currently does.
  Deferred -- not urgent, but worth doing before more modules are built on the current
  duality, since it has already caused two independent bugs (SAT_022, and the identical
  pattern in Border).'
priority: normal
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Deferred todo, raised during a conversational aside during the live runtime-verification pass —
**not to be worked now**, filed so it isn't lost.

`JigBundles.BundleDecl<S, B>` (`common/newconfig/JigBundles.java`) already carries a real
`Factory<S, B>` for constructing the bundle, plus a full `List<FixtureDecl<?>>`, each with its own
`FixtureFactory`. That's functionally identical to what `BundleFactories.registerFactory(key,
factory).registerFixture(key, factory)` stores in a separate static registry. Both
`ScopeEngine_Server`/`ScopeEngine_Client` keep a `bundleDecls` map populated by
`registerBundleSchema(schema)` — but `create()`/`get()` never read it; they go straight to
`BundleFactories.entryFor(key)`. So the schema has everything needed to construct bundles and
fixtures, it's just wired to `JigConfigValidator` only, not to actual construction.

User confirmed this is real, not intentional: the schema/config layer was mid-refactor and meant
to absorb more responsibility than it currently has — "that config was supposed to do a lot of
work, and it isn't doing it all yet."

**Why it matters, eventually:** the duality has already caused two independent, identical bugs —
[SAT_022](SAT_022_tracker-bundlefactory-missing.md) (`TrackingModule` forgot the `BundleFactories`
half) and the same shape in `BorderModule` (which got it right, but only because its own comment
explicitly warns future editors about the trap). Every future module built on this pattern
inherits the same footgun fresh, since nothing cross-validates that a schema's bundles also have a
registered factory.

**Suggested direction, not a committed plan:** point `ScopeEngine.create()`/`get()` at
`bundleDecls.get(key)` instead of `BundleFactories.entryFor(key)`; once that's proven out, delete
`BundleFactories` and every module's separate `.registerFactory(...)` call. Mechanical, not risky,
but deliberately scoped as its own pass rather than folded into a bugfix.

## Log

- 2026-08-14: Promoted onto the roadmap as
  [RM_SAT_012](../roadmap/RM_SAT_012_donald.md) (already cross-linked via that node's
  `ticket` field). The roadmap node is now the live tracker for this work; closing this ticket
  rather than leaving both open in parallel, per the same pattern SAT_031 used when it promoted
  its own recommendations. Kept on disk per BHT convention — findable by ID, off the active board.
- 2026-08-14: Ticket opened as a deferred todo, not scheduled for immediate work.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
