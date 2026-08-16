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
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Prototype hardening

- 2026-08-14: Node opened as **WIP**, paired with [RM_SAT_017](RM_SAT_017_paul.md) under the
  "convergence pairs for now" convention (see that node).

Currently thin by design: the only FrontierMode-side hardening item identified so far is
[RM_FRO_009](RM_FRO_009_judith.md) (dead `BorderView` cleanup). Two smaller SAT_031 items stayed
off the roadmap on purpose rather than padding this node: recommendation #1 (confirm FRO_016 via a
real disconnect cycle) is closing out an existing ticket, not new roadmap-shaped work, and
recommendation #6 (gold-block growth-trigger radius gut-check) is a cosmetic gameplay-feel
question for the Game Designer role, not a structural one. Expect this node to gain real siblings
to RM_FRO_009 as more FrontierMode-side hardening surfaces — it's the thinner half of the pair
right now because most of this pass's structural debt was Satchel-side.

## Required By

*(computed — nothing depends on this yet)*
