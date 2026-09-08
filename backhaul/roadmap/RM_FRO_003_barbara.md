---
id: RM_FRO_003
uid: RM_FRO
number: 3
kind: work
status: resolved
title: Wire Border into Satchel's jig runtime
owner: Arryn
depends_on:
- RM_FRO_002
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Wire Border into Satchel's jig runtime

- 2026-08-11: Node opened, backfilled as resolved history.

Evidenced by `BorderModule.init()`: registers `BordersBundle`/`BordersFixture` via
`BundleFactories`, registers a `SatchelStrap` (`BorderStrap`) that subscribes to
`ScopeEvent.Tick`, and registers a `LevelJig` for both `SERVER` and `CLIENT` logical sides via
`SatchelJigRegistrar`. This is FrontierMode's concrete instance of the jig/scope/foundation
runtime built on Satchel's side (RM_SAT_003 in the Satchel graph) — cited there as the
consuming example. Not a cross-graph dependency edge (each UID's roadmap graph is independent
per BHRM convention), just a functional one worth recording in prose: this node cannot be
understood without RM_SAT_003 having already landed.

## Update (2026-08-13)

The files cited above do exist as described — that part of this node's evidence stands. But the
wiring itself does not currently function: `SatchelJigRegistrar` (the class `BorderModule.java`
calls to register the `LevelJig`) doesn't exist anywhere in Satchel, dead or alive, and
`SatchelStrap`/`SatchelStrapRegistrar` are dead scaffolding. The repo does not currently compile.
See [Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md) for the
full picture. Flagging for PM: unclear whether `resolved` still applies to a step that was
genuinely reached (the architecture was introduced, per RM_SAT_003) but whose consumer wiring has
since broken or was never finished.

## Update 2 (2026-08-13)

Fixed. `BorderModule.init()` now registers `LevelJig` through a single `JigConfig`
(`sideApplicability = BOTH`), replacing `SatchelJigRegistrar`/`SatchelStrap` entirely — see
[FRO_012](../tickets/FRO_012_port-border-to-jigconfig-eventhandlers.md) and
[Border's runtime wiring](../wiki/frontiermode/architecture/border.md#runtime-wiring). Both repos
build clean (`BUILD SUCCESSFUL`, confirmed by real `gradlew build`). `resolved` fits again — this
time against the current, not the dead, mechanism.

## Required By

<!-- required-by:start -->
- [**RM_FRO_007**](RM_FRO_007_nancy.md) — Bare-necessity strip-down and sanitization pass
<!-- required-by:end -->
