---
id: RM_SAT_016
uid: RM_SAT
number: 16
kind: work
status: resolved
title: Write new-Satchel-module checklist
owner: Arryn
depends_on:
- RM_SAT_012
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Write new-Satchel-module checklist

- 2026-08-15: **Resolved by Lead Dev (Curtis).** New page:
  [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md), under
  `satchel/architecture` alongside [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md).
  Five items, each tied to a real mistake rather than a hypothetical one: schema-only registration
  (post-RM_SAT_012, no more `BundleFactories` duality), setting `executionPulse` when persistence/
  networking is required (RM_SAT_013's health-check now catches the silent version of this at
  runtime), guarding every `ScopeEvent` handler by jig key (the exact RM_SAT_012 crash root cause
  from this session — `SatchelEventBus` is one shared bus per side, not per-jig), `LogicalSideContext`
  thread discipline (`require()` vs `current()`), and the per-side jig-instance split. Points at
  `TrackingModule` as the worked example, cross-referencing
  [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md)'s existing worked-example section
  rather than duplicating its narrative. Added via `bhw new`, indexed via `bhw refresh`.
- 2026-08-14: Node opened, per
  [SAT_031](../tickets/SAT_031_handoff-retrospective-recommendations.md) recommendation #5.
  Deliberately sequenced after [RM_SAT_012](RM_SAT_012_donald.md) rather than in parallel
  with it, so the checklist documents the real post-consolidation pattern instead of the
  BundleFactories/Schema duality it would otherwise have to warn people about.

Given the ambition already discussed (fixtures as a shared base across many planned modules) and
that two of the runtime-verification pass's bugs
([SAT_022](../tickets/SAT_022_tracker-bundlefactory-missing.md) and the identical shape in
`BorderModule`) were the *same* omission made independently in two different modules, a short wiki
checklist — register schema *and* factory (or just schema, once RM_SAT_012 lands), set
`executionPulse` if sync is needed, wire Forge listeners, remember `LogicalSideContext` thread
discipline — is cheap insurance against every future module rediscovering the same handful of
footguns one at a time.

## Required By

<!-- required-by:start -->
- [**RM_SAT_017**](RM_SAT_017_paul.md) — Prototype hardening
<!-- required-by:end -->
