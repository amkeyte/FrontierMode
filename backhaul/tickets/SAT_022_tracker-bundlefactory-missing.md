---
id: SAT_022
uid: SAT
number: 22
client: Satchel
status: done
title: TrackingModule never registered its bundle
context: BundleFactories.registerFactory never called - only the validator-facing
  JigBundles.Schema was.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fourth real crash found in this run sequence, immediately after
[SAT_020](SAT_020_jiginfo-null.md)'s fix cleared the crash ahead of it:

```
java.lang.IllegalStateException: No BundleFactory registered for BundleKey[id=..., name="satcheltracker:tracker_bundle", scopeType=SatchelBundle]
	at com.arryn.satchel.server.jig.guts.ScopeEngine_Server.create(ScopeEngine_Server.java:181)
	at com.arryn.satchel.common.jig.guts.AScopeCoupler.getOrCreate(AScopeCoupler.java:112)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.getOrCreate(ASatchelJig.java:212)
	at com.arryn.satchel.common.newconfig.TrackingModule.onScopeLoaded(TrackingModule.java:111)
```

**Root cause**, traced directly in source: `TrackingModule.init()` built a `JigBundles.Schema`
(`config.bundles().schema(bundles)`) and registered the `JigConfig` — but never called
`BundleFactories.registerFactory(BUNDLE, ...)`. `ScopeEngine_Server.create()` looks up
`BundleFactories.entryFor(key)` to actually construct a bundle; if no entry exists, it throws
exactly this. The two registrations are genuinely separate and both required — confirmed by
`BorderModule.java`'s own comment on the identical pattern for `BordersBundle`: "Still the live
bundle/fixture-construction registry -- JigBundles.Schema below is required by
JigConfigValidator but isn't consumed for actual construction." `BorderModule.init()` does both
calls; `TrackingModule.init()` only ever did the validator-facing half — an omission, not a design
difference, since nothing in the validator or compiler cross-checks that a schema's bundles also
have a registered factory.

Same general shape as the last three: invisible to `javac`, invisible to `JigConfigValidator`
(the schema alone satisfies it), only surfaces when a scope actually tries to create its bundle
for real — i.e., "does it run," not "does it build."

**Fix applied:** added `BundleFactories.registerFactory(BUNDLE, scope -> new SatchelBundle(scope,
BUNDLE)).registerFixture(TRACKER, TrackerFixture::new)` to `TrackingModule.init()`, mirroring
`BorderModule.init()`'s exact pattern. Updated
[runtime.md](../wiki/satchel/architecture/runtime.md)'s `TrackingModule` worked example to
describe the registration step correctly (it previously only mentioned the schema) and to log
both bugs this worked example caught, in the order they were found.

**Left `in-progress`, not `done`:** same as the three before it — need a real re-run to confirm.

## Log

- 2026-08-14: Confirmed — `tracker_bundle` has created without error across every subsequent run
  in this session (first verified alongside [SAT_023](SAT_023_scopeengine-shared-config.md)).
  Closing.
- 2026-08-14: Root cause traced (see above), fix applied to `TrackingModule.java`, `runtime.md`
  updated. Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
