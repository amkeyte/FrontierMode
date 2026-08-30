---
id: RM_FRO_025
uid: RM_FRO
number: 25
kind: work
status: open
title: Donna epoch review/fix
owner: Arryn
depends_on:
- RM_FRO_024
created: '2026-08-30'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Donna epoch review/fix

**Maintenance container, not a feature deliverable.** The epoch's review/fix gate — things too
small or too unrelated to block whatever's currently being built get dropped here instead of
stalling it, per
[BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers).
No `ticket:` field on purpose, same reasoning as [RM_FRO_024](RM_FRO_024_donna-01.md). Depends on
RM_FRO_024 directly — sequential within the epoch's own containers, same shape as
[RM_FRO_021](RM_FRO_021_susan-02.md) ("Susan epoch review/fix") depending on
[RM_FRO_020](RM_FRO_020_susan-01.md).

**Not wired onto [RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen," Tier 2 skeleton) by default** —
per [BHRM's corollary](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers), learned the hard
way on Susan_02: a container earns a `depends_on` edge onto the next convergence only once it
actually holds something that blocks it, not automatically because it's the epoch's end container.
Empty at open; wire it if and when that changes.

- 2026-08-30: Node opened, alongside [RM_FRO_024](RM_FRO_024_donna-01.md) ("Donna epoch
  maintenance 1") and Donna's own `reached` flip — see [RM_FRO_017](RM_FRO_017_donna.md)'s own
  2026-08-30 log entry. Nothing held yet.

## Required By

*(computed — nothing depends on this yet)*
