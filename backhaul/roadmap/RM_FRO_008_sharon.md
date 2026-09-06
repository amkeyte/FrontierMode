---
id: RM_FRO_008
uid: RM_FRO
number: 8
kind: convergence
status: reached
title: Border prototype verified end-to-end
owner: Arryn
depends_on:
- RM_FRO_002
- RM_FRO_003
- RM_FRO_004
- RM_FRO_005
- RM_FRO_007
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Border prototype verified end-to-end

- 2026-08-14: Node opened, backfilled as **reached** history (convergence) — mirrors
  [RM_SAT_011](RM_SAT_011_larry.md) on the Satchel side.

Marks the point where Border — FrontierMode's one substantial feature and Satchel's first real
consumer — stopped being "compiles clean" and became "confirmed working end to end by a real
runtime-verification pass," not just static reading. Depends on the feature actually existing
(RM_FRO_002), being wired into the jig runtime (RM_FRO_003), having a command/selector surface
(RM_FRO_004), rendering client-side (RM_FRO_005), and having been through the bare-necessity
strip-down (RM_FRO_007) — **deliberately not** RM_FRO_006 (per-player evaluation), which is real,
evidenced, *unfinished* work and stays outside what this node claims is verified.

Evidenced by four real runtime bugs found and fixed on the FrontierMode side of the same pass:
[FRO_015](../tickets/FRO_015_empty-path-tip-crash.md) (empty path-tip crash),
[FRO_017](../tickets/FRO_017_growpath-never-registered.md) (`growPath` never registered),
[FRO_018](../tickets/FRO_018_border-executionpulse-disabled.md) (execution-pulse disabled —
silent, no persistence or sync at all), and [FRO_016](../tickets/FRO_016_null-level-on-exit-crash.md)
(null-level crash on disconnect — fixed and, per that ticket's log, confirmed by a real
"join, play, quit" cycle). Also confirmed by a real `gradlew build` on both repos
(`BUILD SUCCESSFUL`) — see [Border](../wiki/frontiermode/architecture/border.md#runtime-wiring).
This convergence is now airtight — no open questions left against it.

Cross-linked with [RM_SAT_011](RM_SAT_011_larry.md) — see that node for why this is modeled as
two paired convergence nodes rather than one node with a cross-graph edge.

## Required By

<!-- required-by:start -->
- [**RM_FRO_006**](RM_FRO_006_sandra.md) — Per-player border evaluation
- [**RM_FRO_009**](RM_FRO_009_judith.md) — Clean up dead BorderView code
- [**RM_FRO_011**](RM_FRO_011_betty.md) — Border mutation validation hardening
- [**RM_FRO_012**](RM_FRO_012_carolyn.md) — Client render lifecycle cleanup
- [**RM_FRO_013**](RM_FRO_013_judy.md) — Border fixture & compass robustness
- [**RM_FRO_015**](RM_FRO_015_margaret.md) — Border command-surface completion
<!-- required-by:end -->
