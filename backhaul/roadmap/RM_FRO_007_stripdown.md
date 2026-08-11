---
id: RM_FRO_007
uid: RM_FRO
number: 7
kind: work
status: resolved
title: Bare-necessity strip-down and sanitization pass
owner: Arryn
depends_on:
- RM_FRO_003
- RM_FRO_004
- RM_FRO_005
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Bare-necessity strip-down and sanitization pass

- 2026-08-11: Node opened, backfilled as resolved history — this one is real, not reconstructed.

Executed 2026-08-11 per [backhaul/wiki/plans/strip-down.md](../wiki/plans/strip-down.md) and
tickets FRO_002/FRO_003/FRO_005: `.git/`, IDE/build artifacts, `maven-publish`, and the
`apiDumpFrontier` task removed; `DocletProject`, the stray Forge MDK zip, and `.vs/` removed from
the mcRepos root; a unified `.gitignore` written (FRO_005). Known debt flagged during this pass
and *not* resolved by it, tracked as ticket FRO_004 rather than a roadmap node: FrontierMode's
dependency on Satchel is wired through a hardcoded absolute-path `flatDir` repo
(`C:/_local/mcRepos/Satchel/build/libs/`) rather than a real project/module reference — legitimate
functional coupling (mods.toml declares it mandatory, `AFTER`), fragile mechanism.

## Required By

*(computed — nothing depends on this yet)*
