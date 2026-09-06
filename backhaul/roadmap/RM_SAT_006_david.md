---
id: RM_SAT_006
uid: RM_SAT
number: 6
kind: work
status: superseded
title: Integrate DocletProject API-dump tooling
owner: Arryn
depends_on:
- RM_SAT_001
created: '2026-08-11'
superseded_by: RM_SAT_009
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Integrate DocletProject API-dump tooling

- 2026-08-11: Node opened, backfilled as **superseded** history (see `superseded_by`).

Evidenced by [SAT_002](../tickets/SAT_002_strip-bare.md) and the strip-down plan
([backhaul/wiki/plans/strip-down.md](../wiki/plans/strip-down.md)): Satchel once ran an
`apiDumpSatchel` Gradle task (`gradle/apiDump.gradle`) that fed a separate `DocletProject`
workspace. Sequenced early (depends only on RM_SAT_001) since API-dump tooling only needs the mod
to exist, not any of its later internal architecture. Superseded by RM_SAT_009: the task, its
Gradle module, and DocletProject itself were all removed in the 2026-08-11 bare-necessity pass —
this node stays on record as real prior work rather than being deleted, per Backhaul's
work-status conventions.

## Required By

<!-- required-by:start -->
- [**RM_SAT_007**](RM_SAT_007_charles.md) — Satchel 2.0 persistence model
<!-- required-by:end -->
