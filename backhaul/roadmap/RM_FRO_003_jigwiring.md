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

## Required By

*(computed — nothing depends on this yet)*
