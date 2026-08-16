---
id: SAT_023
uid: SAT
number: 23
client: Satchel
status: done
title: ScopeEngine_Server shared config clobbered across jigs
context: resolveServerLevel() read the engine's own single policies field, clobbered
  per-jig by installJigConfig(); plus LevelJigConfig never set referenceLevelResolver.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fifth real crash found in this run sequence, immediately after
[SAT_022](SAT_022_tracker-bundlefactory-missing.md)'s fix cleared the crash ahead of it:

```
com.arryn.satchel.common.jig.guts.SatchelException$AccessFailed: A jig request has failed:
Capability PERSISTENCE requires Level, but no referenceLevel available: LevelScope[minecraft:overworld]
	at com.arryn.satchel.server.jig.guts.ScopeEngine_Server.resolveServerLevel(ScopeEngine_Server.java:386)
	at com.arryn.satchel.server.jig.guts.ScopeEngine_Server.hydrateBundle(ScopeEngine_Server.java:289)
	at com.arryn.satchel.server.jig.guts.ScopeEngine_Server.create(ScopeEngine_Server.java:...)
	at com.arryn.satchel.common.jig.guts.AScopeCoupler.getOrCreate(...)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.getOrCreate(...)
	at com.arryn.satchel.common.newconfig.TrackingModule.onScopeLoaded(TrackingModule.java:123)
```

Two genuinely separate bugs found tracing this, both fixed:

**Bug A — `ScopeEngine_Server` is a per-side singleton with a single shared config.**
`resolveServerLevel()` read `this.policies().capabilities()` — the engine's *own* `policies`
field. But `installJigConfig(CompiledJigConfig)` on that field is called once per jig, every time,
on the same shared instance: `LogicalFoundation.installConfigs()` loops over every jig config and
calls `JigConfigCompiler.instantiateCoupler(key, cfg, booter().engine())`, which does
`engine.installJigConfig(config)` — a plain overwrite, not accumulation. `booter().engine()`
returns the identical `ScopeEngine_Server` instance for every jig on that side. So the engine's
`policies` (and `binding`/`execution`/`bundles`) field, at any moment, reflects whichever jig was
installed *last* in that loop — not the jig actually calling `resolveServerLevel()` right now.

`TrackingModule.init()` never sets `capabilities()` (defaults to `requiresPersistence=false`,
confirmed correct by [SAT_013](SAT_013_scopeengine-server-null-as-skip-vs-null.md)'s own log:
"leave `requiresPersistence` at its default `false` ... that's the correct value for it"). Border's
config (via [FRO_014](FRO_014_border-persistence-crash.md)) sets `requiresPersistence=true`. If
Border installs after TrackingModule in `installConfigs()`'s loop, the engine's shared `policies`
field ends up holding Border's capabilities by the time TrackingModule's own scope tries to
hydrate — so TrackingModule gets checked against a requirement it never declared. This is a
different bug from SAT_013 (which was about `null` meaning two things to the same caller); this is
about the caller reading the wrong jig's config entirely. Not a re-open of SAT_013 — that fix
(skip on `null` when not required) is still correct and untouched.

**Fix:** `ScopeInfo` (the per-scope, per-jig record — installed once in `JigInfo.addScope()`, never
overwritten) already carries its own correctly-scoped `policies()`, independent of the engine.
`resolveServerLevel(ScopeInfo info, ...)` already takes `info` as a parameter and already uses
`info.referenceLevel()` a few lines down — it just never used `info.policies()` for the capability
check. Changed `policies().capabilities()` to `info.policies().capabilities()`.

**Bug B — no `LevelJigConfig`-based jig ever set `referenceLevelResolver`.** Independent of Bug A:
`ScopeInfo.referenceLevel()` returns `null` whenever `binding().referenceLevelResolver()` is unset,
and neither `LevelJigConfig.createPresets()`, `BorderModule.init()`, nor `TrackingModule.init()`
ever set it — grepped, zero assignments anywhere. So even with Bug A fixed, the first jig that
*genuinely* requires persistence (Border) would hit the exact same crash for real, since
`resolveServerLevel()` would correctly determine persistence is required, then find no
referenceLevel to resolve it against. `LevelScope` already holds its own `Level` directly
(`private final Level level`, set at construction, exposed via `level()`) — no registry lookup is
needed. **Fix:** set `p.binding.referenceLevel = LevelScope::level;` in
`LevelJigConfig.createPresets()`, as the correct default for every `LevelJigConfig`-based jig
(Border, TrackingModule, and any future ones) rather than requiring each consumer to wire it
individually.

Same shape as the four bugs before it: invisible to `javac`, invisible to `JigConfigValidator`,
only surfaces when a scope with a real persistence requirement actually tries to hydrate.

**Left `in-progress`, not `done`:** same as the four before it — need a real re-run to confirm, and
this one specifically needs a run that gets Border's own scope to hydrate (not just
TrackingModule's) to confirm Bug B's fix is also correct, not just Bug A's.

## Log

- 2026-08-14: Confirmed — a full session ("loads and plays," per the user, three crashes later at
  [FRO_016](FRO_016_null-level-on-exit-crash.md)) ran with both `tracker_bundle` and
  `borders_bundle` hydrating on every level with no persistence/referenceLevel error. Closing.
- 2026-08-14: Root cause traced (two separate bugs, see above), fixes applied to
  `ScopeEngine_Server.java` and `LevelJigConfig.java`. Left `in-progress` pending a real re-run to
  confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
