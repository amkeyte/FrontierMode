---
id: SAT_021
uid: SAT
number: 21
client: Satchel
status: done
title: Jig key never installed on jig instances
context: instantiateJig() never calls jig.installKey() -- jigKey() throws on first
  real use.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found via a real `./gradlew runClient` run (FrontierMode), immediately after
[FRO_014](FRO_014_border-persistence-crash.md)'s fix let boot get further. First server tick
crashes:

```
java.lang.IllegalStateException: Jig key accessed before installation
	at com.arryn.satchel.common.jig.guts.ASatchelJig.jigKey(ASatchelJig.java:81)
	at com.arryn.satchel.common.jig.level.LevelJig.jigKey(LevelJig.java:38)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.coupler(ASatchelJig.java:90)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.onLoad(ASatchelJig.java:103)
	at com.arryn.satchel.common.jig.guts.ASatchelJig.handleExecutionPulse(ASatchelJig.java:134)
	at com.arryn.satchel.common.lifecycle.FoundationLifecycleDispatcher.pulse(FoundationLifecycleDispatcher.java:62)
	at com.arryn.satchel.server.lifecycle.ServerForgeIngress.onExecutionPulse(ServerForgeIngress.java:106)
```

**Root cause**, traced directly in source: `ASatchelJig` has two separate installation steps —
`installJigConfig(CompiledJigConfig)` (sets `binding`/`execution`/`policies`/`bundles`) and
`installKey(JigKey<?>)` (sets the private `key` field that `jigKey()` returns). Only the first is
ever called. `JigConfigCompiler.instantiateJig()` calls `jig.installJigConfig(config)` right after
construction but never `jig.installKey(jigKey)`. `LogicalFoundation.installConfigs()` (the only
caller of `instantiateJig`) also never calls it — it calls `info.installJigConfig(cfg)`, but
that's `JigInfo`'s *own* separate copy of the config (a different object entirely from the actual
jig instance's fields), which has nothing to do with the jig's key. Grepped both repos for
`installKey(` — **zero callers, anywhere.** So every `ASatchelJig` instance's `key` field stays
`null` forever, and the first real call to `jigKey()` (here: `coupler()` → `jigKey()` during a
scope's first `onLoad()`) throws.

This is upstream of and unrelated to [SAT_020](SAT_020_jiginfo-null.md) — different bug, different
class, same general "runtime failure invisible to `javac`" shape. Also not something the earlier
static-sweep/compile-fix chain could have caught: `installJigConfig` being called and
`requireConfig()`'s `binding == null` check both passing made every prior check look complete;
only `jigKey()` actually being invoked exposes the gap.

**Fix applied:** added `jig.installKey(jigKey)` to `JigConfigCompiler.instantiateJig()`, right
after `jig.installJigConfig(config)` and before the `JigKey.validateTypes(jigKey, jig)` call
already there (which implicitly assumes the jig's identity is tied to that key — makes sense for
key installation to happen in the same method, at construction time). Updated
[runtime.md](../wiki/satchel/architecture/runtime.md)'s registration-and-compilation section
(step 3) to mention `installKey` alongside `installJigConfig`.

**Left `in-progress`, not `done`:** same as FRO_014 — can't build/run from this sandbox, need a
real re-run to confirm.

## Log

- 2026-08-14: Confirmed by a real re-run — the "Jig key accessed before installation" crash is
  gone; boot progressed to the next stage and hit a different, unrelated crash
  ([SAT_020](SAT_020_jiginfo-null.md)'s NPE). Closing.
- 2026-08-14: Root cause traced (see above), fix applied to `JigConfigCompiler.java`, `runtime.md`
  updated. Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
