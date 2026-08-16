---
id: FRO_014
uid: FRO
number: 14
client: FrontierMode
status: done
title: 'Border crashes: persistence policy unset'
context: 'Client crashes at boot: capabilities.requiresPersistence=true but policies().persistence()
  never set.'
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found via a real `./gradlew runClient` run (FrontierMode, reported by the human directly —
reproducible, confirmed persistent). Client crashes hard at foundation boot, before any level
loads:

```
java.lang.IllegalStateException: Jig JigKey[..., name="frontiermode:borders_jig", scopeType=LevelJig]
requires persistence but persistence policy is not persistent
	at com.arryn.satchel.common.newconfig.newnew.JigConfigValidator.validateCapabilities(JigConfigValidator.java:209)
	at com.arryn.satchel.common.newconfig.newnew.JigConfigValidator.validate(JigConfigValidator.java:22)
	at com.arryn.satchel.common.newconfig.newnew.JigConfigCompiler.compileForSide(JigConfigCompiler.java:84)
	at com.arryn.satchel.client.jig.guts.ClientFoundationBooter.installFoundation(ClientFoundationBooter.java:62)
```

**Root cause**, traced directly in source: `BorderModule.init()`
(`FrontierMode/.../border/BorderModule.java`) sets
`config.policies().capabilities(new JigPolicies.Capabilities(true, false, false))` —
`requiresPersistence = true` — but never calls `config.policies().persistence(...)`.
`LevelJigConfig`'s default `Persistence` policy is `(persistent=false, FlushPolicy.NONE,
MissingDataPolicy.IGNORE)`. `JigConfigValidator.validateCapabilities` (line 206-213) cross-checks
`capabilities().requiresPersistence()` against `policies().persistence().persistent()` and throws
if they disagree — which they did, since only one of the two related knobs was ever set. Since
Border's config is `sideApplicability = BOTH`, `JigConfigCompiler.compileForSide` runs this same
validation independently for both sides, so this would equally crash a headless `runServer`, not
just the client — same root cause, same shared `JigConfig` object.

This was **invisible to every prior "does it compile" check** in this project's history —
`javac` has no opinion on runtime policy consistency, and `LevelJigConfig`'s default compiles
clean. Only actually booting the game (`does it run`, not `does it build`) surfaced it. Also
notable: this crash happens at foundation boot, before any level loads — upstream of
[SAT_020](SAT_020_jiginfo-null.md)'s NPE, which needs a level to actually
load to be reachable. Fixing this is a prerequisite to ever exercising SAT_020 for real.

**Fix applied:** added the missing `config.policies().persistence(new
JigPolicies.Persistence(true, JigPolicies.FlushPolicy.UNLOAD, JigPolicies.MissingDataPolicy.WARN))`
call to `BorderModule.init()`, mirroring the existing `capabilities()` call's style and comment
density. `flushOn`/`missingPolicy` aren't read anywhere yet (grepped both repos — no consumer),
so their exact values don't change behavior today, but were set to real values rather than left at
defaults so the policy reads as intentional. Updated
[border.md](../wiki/frontiermode/architecture/border.md)'s "Runtime wiring" section (item 4) to
document both required calls — it previously only described `capabilities()`, which is how this
went unnoticed through the whole SAT_008/FRO_011/FRO_012 compile-fix chain.

**Left `in-progress`, not `done`:** I can't build or run either mod from this sandbox (Java 11
only, Forge's maven network-blocked) — need the human to re-run `./gradlew runClient` (or
`runServer`) and confirm the crash is actually gone before this closes.

## Log

- 2026-08-14: Confirmed fixed by a real re-run. The persistence-policy crash is gone — client got
  substantially further this time (full integrated-server boot, world generation, spawn-chunk
  prep, first server tick) before hitting a *different* crash, ticketed separately as
  [SAT_021](SAT_021_jig-key-never-installed.md). Closing this one.
- 2026-08-14: Root cause traced (see above), fix applied to `BorderModule.java`, `border.md`
  updated. Left `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
