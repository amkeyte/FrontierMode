---
id: RM_SAT_009
uid: RM_SAT
number: 9
kind: work
status: resolved
title: Bare-necessity strip-down and sanitization pass
owner: Arryn
depends_on:
- RM_SAT_007
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Bare-necessity strip-down and sanitization pass

- 2026-08-11: Node opened, backfilled as resolved history — this one is real, not reconstructed.

Executed 2026-08-11 per [backhaul/wiki/plans/strip-down.md](../wiki/plans/strip-down.md) and
tickets SAT_002/SAT_003: `.git/`, IDE/build artifacts, `maven-publish`, the `apiDumpSatchel` task,
and `DocletProject` all removed; the five in-source architecture `.md` files migrated into
`satchel/architecture/*`. Known debt flagged during this pass and *not* resolved by it:
FrontierMode's hardcoded `flatDir`/absolute-path dependency on Satchel's build output (tracked as
ticket FRO_004, not a roadmap node — it's Lead Dev implementation work, not a design milestone).

- 2026-08-15: `depends_on` narrowed to [RM_SAT_007](RM_SAT_007_charles.md) alone — previously also
  named RM_SAT_005/006/008 directly, alongside RM_SAT_007, which is exactly the "route around a
  convergence node's own gate" shape a convergence exists to prevent. Those three edges moved onto
  RM_SAT_007 itself instead (see that node's 2026-08-15 log entry), including the "supersedes,
  doesn't require" nuance this page used to carry about RM_SAT_006 — preserved there, not dropped.
  Net effect on this node unchanged: still resolved after net layer, persistence 2.0, and jig
  config were all in place, just expressed as a single edge instead of four.

## Required By

*(computed — nothing depends on this yet)*
