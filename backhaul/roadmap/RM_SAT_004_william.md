---
id: RM_SAT_004
uid: RM_SAT
number: 4
kind: work
status: resolved
title: Rename facet to fixture
owner: Arryn
depends_on:
- RM_SAT_002
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Rename facet to fixture

- 2026-08-11: Node opened, backfilled as resolved history.
- 2026-08-11: **Architect review resolved a terminology question this node's history explains.**
  [Fixture](../wiki/satchel/architecture/fixture.md) was flagged `draft` because its two
  migrated source docs used "facet" and "fixture"
  inconsistently for what looked like the same thing. Checked against current code: `fixture`
  (`SatchelFixture`, `FixtureKey`) is Satchel's term for the atomic persisted-state unit — this
  node's namesake rename. "Facet" survives as a *different*, still-live concept: a mod-author
  convention for a grouped-accessor sub-view onto a fixture (see FrontierMode's `BordersFixture`,
  which exposes four facets — `PATH`, `CRUD`, `RULES`, `INFO` — as public fields, none of which
  extend `SatchelFixture` themselves).
  [Fixture](../wiki/satchel/architecture/fixture.md) has been updated to `verified` with this
  distinction spelled out.

Evidence this rename happened rather than facet/fixture being two names invented independently:
the field-registration error strings inside `SatchelFixture` still say `"Duplicate facet field"`
even though the class and its key type are named `Fixture`/`FixtureKey` throughout — exactly the
kind of leftover an incomplete rename leaves behind.

## Required By

<!-- required-by:start -->
- [**RM_SAT_005**](RM_SAT_005_richard.md) — Build the networking layer
- [**RM_SAT_007**](RM_SAT_007_charles.md) — Satchel 2.0 persistence model
<!-- required-by:end -->
