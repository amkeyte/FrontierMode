---
id: RM_SAT_003
uid: RM_SAT
number: 3
kind: work
status: resolved
title: Introduce the jig/foundation runtime
owner: Arryn
depends_on:
- RM_SAT_002
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Introduce the jig/foundation runtime

- 2026-08-11: Node opened, backfilled as resolved history.

Directly evidenced by `common/jig/guts/*` (`LogicalFoundation`, `SatchelJig`, `ScopeCoupler`,
`ScopeEngine`, `SatchelScope`, `JigInfo`) and by both diagrams under `Satchel/design/`
(`satchelArchitectureOverview.svg`, `satchelDataAvailabilty.svg`), which depict exactly this
machinery — foundation boot, jig/coupler installation, key registration, event bus wiring — as a
already-working system, not a proposal. This is the layer that turned the plain bundle model
(RM_SAT_002) into a real per-side (client/server) runtime: `LogicalFoundation` owns the jig
registry and installs the event bus / lifecycle dispatchers; `SatchelJig` is the per-scope-type
contract a mod (e.g. FrontierMode's `LevelJig`) implements; `ScopeCoupler`/`ScopeEngine` bind jig
instances to concrete scopes. Consumed downstream by FrontierMode's Border feature
(RM_FRO_003 in the FrontierMode graph — see that node for the concrete wiring example).

## Required By

<!-- required-by:start -->
- [**RM_SAT_007**](RM_SAT_007_charles.md) — Satchel 2.0 persistence model
- [**RM_SAT_008**](RM_SAT_008_thomas.md) — Jig Config compiler rewrite
<!-- required-by:end -->
