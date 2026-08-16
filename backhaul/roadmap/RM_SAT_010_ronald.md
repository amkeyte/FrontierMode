---
id: RM_SAT_010
uid: RM_SAT
number: 10
kind: work
status: resolved
title: Migrate in-source architecture docs into the wiki
owner: Arryn
depends_on:
- RM_SAT_009
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Migrate in-source architecture docs into the wiki

- 2026-08-11: Node opened, backfilled as resolved history — this one is real, not reconstructed.

Ticket SAT_003. Five in-source `.md` files (`bundle/notes.md`, `fixture/notes.md`,
`fixture/SatchelFacet.md`, `net/notes.md`, `server/persistence/BundlePersistence.md`) migrated
into `satchel/architecture/*` as [Bundle](../wiki/satchel/architecture/bundle.md),
[Fixture](../wiki/satchel/architecture/fixture.md), [Networking](../wiki/satchel/architecture/net.md),
and [Persistence](../wiki/satchel/architecture/persistence.md) — the fixture
pair merged per an in-source `<!-- include -->` directive, an editorial artifact stripped from
`bundle/notes.md`, and two drift flags raised for Architect review. Both flags have since been
resolved (2026-08-11): [Fixture](../wiki/satchel/architecture/fixture.md) and
[Persistence](../wiki/satchel/architecture/persistence.md) are now `verified` — see RM_SAT_004 and
RM_SAT_007 for the resolutions.

## Required By

*(computed — nothing depends on this yet)*
