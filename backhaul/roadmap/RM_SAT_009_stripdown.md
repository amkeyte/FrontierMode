---
id: RM_SAT_009
uid: RM_SAT
number: 9
kind: work
status: resolved
title: Bare-necessity strip-down and sanitization pass
owner: Arryn
depends_on:
- RM_SAT_005
- RM_SAT_006
- RM_SAT_007
- RM_SAT_008
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
`satchel/architecture/*`. Deliberately does **not** depend on RM_SAT_006 (DocletProject
integration) in the normal sense — it's the node that *supersedes* RM_SAT_006, not one that
requires it done first. Depends on everything else built by this point (net layer, persistence
2.0, jig config) since the strip preserved and organized all of it rather than touching behavior.
Known debt flagged during this pass and *not* resolved by it: FrontierMode's hardcoded
`flatDir`/absolute-path dependency on Satchel's build output (tracked as ticket FRO_004, not a
roadmap node — it's Lead Dev implementation work, not a design milestone).

## Required By

*(computed — nothing depends on this yet)*
