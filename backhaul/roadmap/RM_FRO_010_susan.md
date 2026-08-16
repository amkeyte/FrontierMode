---
id: RM_FRO_010
uid: RM_FRO
number: 10
kind: convergence
status: WIP
title: Prototype hardening
owner: Arryn
depends_on:
- RM_FRO_009
- RM_FRO_011
- RM_FRO_012
- RM_FRO_013
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Prototype hardening

- 2026-08-16: **`depends_on` widened to add three real siblings to RM_FRO_009** —
  [RM_FRO_011](RM_FRO_011_betty.md) (border mutation validation hardening),
  [RM_FRO_012](RM_FRO_012_carolyn.md) (client render lifecycle cleanup), and
  [RM_FRO_013](RM_FRO_013_judy.md) (border fixture & compass robustness). Found via a
  project-owner-requested source-level resilience pass over `border/*` — same spirit as
  SAT_031's retrospective on the Satchel side, done here by reading the actual implementation
  against its own claimed behavior rather than waiting for a bug report. Eight distinct findings,
  grouped into three nodes by root cause rather than filed one-for-one, per the project owner's
  explicit steer to keep this convergence's fan-out manageable. All three siblings depend on
  RM_FRO_008 directly (not on this node), matching how RM_SAT_012-016/019 relate to RM_SAT_017 —
  named here in `depends_on` so nothing routes around this node's own gate (see
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md), the convergence-bypass check this
  project's own tooling gained after finding exactly that mistake made twice in RM_SAT). This
  node is no longer "thin by design" — it now carries real, evidenced structural work on par with
  RM_SAT_017's own batch.
- 2026-08-14: Node opened as **WIP**, paired with [RM_SAT_017](RM_SAT_017_paul.md) under the
  "convergence pairs for now" convention (see that node).

Originally thin by design: the only FrontierMode-side hardening item identified at open was
[RM_FRO_009](RM_FRO_009_judith.md) (dead `BorderView` cleanup). Two smaller SAT_031 items stayed
off the roadmap on purpose rather than padding this node: recommendation #1 (confirm FRO_016 via a
real disconnect cycle) is closing out an existing ticket, not new roadmap-shaped work, and
recommendation #6 (gold-block growth-trigger radius gut-check) is a cosmetic gameplay-feel
question for the Game Designer role, not a structural one. That prediction ("expect this node to
gain real siblings to RM_FRO_009") held — see the 2026-08-16 entry above.

## Required By

*(computed — nothing depends on this yet)*
