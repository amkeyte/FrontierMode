---
id: RM_FRO_006
uid: RM_FRO
number: 6
kind: work
status: open
title: Per-player border evaluation
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

## Per-player border evaluation

- 2026-08-11: Node opened, status **left open** — this reflects genuinely unfinished code, not
  invented forward planning.

`border/common/player/*` has real content — `BorderPlayerEval`, `BorderPlayerLogic`,
`BorderPlayerStatus`, `BorderPlayerStatusFixture`, `BorderPlayerStatusProposal` all exist — but
the bundle that would host them, `BorderPlayerBundle.java`, is entirely commented out, including
a `return null; //getOrCreateFacet(...)` stub inside the one method it defines. Nothing currently
constructs or registers this bundle: `BorderModule.init()` only wires the world-scoped
`BordersBundle` (RM_FRO_002/003). Left open deliberately rather than marked resolved or deleted —
it's a real, evidenced gap in the current system, exactly the kind of thing this backfill pass
is meant to surface. Not chained into RM_FRO_007 (strip-down): the strip preserved this dead code
as-is rather than requiring it finished first.

## Required By

*(computed — nothing depends on this yet)*
