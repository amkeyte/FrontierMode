---
id: RM_SAT_008
uid: RM_SAT
number: 8
kind: work
status: resolved
title: Jig Config compiler rewrite
owner: Arryn
depends_on:
- RM_SAT_003
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Jig Config compiler rewrite

- 2026-08-11: Node opened, backfilled as resolved history.

Evidenced by `common/newconfig/newnew/*` (`JigConfigCompiler`, `JigConfigValidator`,
`JigPolicies`, `CompiledJigConfig`, `JigBinding`) and by `LogicalFoundation.installConfigs`,
which calls into this compiler for real during foundation boot — this is live, wired
infrastructure, not a stub. Marked resolved rather than open despite the package name
(`newconfig.newnew`, an evidently unrenamed working title) and a couple of "find me a home
later" comments in `JigConfigCompiler` — those read as code-hygiene notes on an already-shipped
subsystem, not signs of missing functionality. Package rename is loose thread worth a ticket, not
a reason to call the work unfinished. Depends on the jig/foundation runtime (RM_SAT_003), which
it configures.

## Required By

*(computed — nothing depends on this yet)*
