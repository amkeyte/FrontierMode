---
id: RM_FRO_002
uid: RM_FRO
number: 2
kind: work
status: resolved
title: Build the World Border feature
owner: Arryn
depends_on:
- RM_FRO_001
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Build the World Border feature

- 2026-08-11: Node opened, backfilled as resolved history.

The one substantial gameplay feature evidenced in FrontierMode's current source: a world-border
system built on Satchel's fixture model. `BordersFixture`
(`border/common/fixture/BordersFixture.java`) is the persisted-state unit, composed of four
facets — `PATH` (`BordersPathFacet`), `CRUD` (`BordersCrudFacet`), `RULES`
(`BordersRulesFacet`), `INFO` (`BordersInfoFacet`) — the concrete example cited when resolving
Satchel's own facet/fixture terminology drift (see Satchel graph, RM_SAT_004). Server-side rule
evaluation lives in `border/server/rules/*` (`BorderLogic`, `BorderRules`,
`DefaultBorderRules`, `BordersTriggers`). This node is the root the rest of the border-specific
work (jig wiring, commands, rendering) hangs off of.

## Required By

<!-- required-by:start -->
- [**RM_FRO_003**](RM_FRO_003_barbara.md) — Wire Border into Satchel's jig runtime
- [**RM_FRO_004**](RM_FRO_004_patricia.md) — Command and selector interface
- [**RM_FRO_005**](RM_FRO_005_carol.md) — Client-side border rendering
- [**RM_FRO_008**](RM_FRO_008_sharon.md) — Border prototype verified end-to-end
<!-- required-by:end -->
