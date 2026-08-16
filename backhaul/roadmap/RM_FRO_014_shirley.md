---
id: RM_FRO_014
uid: RM_FRO
number: 14
kind: convergence
status: WIP
title: 'Tier 0: Substrate operational'
owner: Arryn
depends_on:
- RM_FRO_010
- RM_FRO_006
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Tier 0: Substrate operational

- 2026-08-16: Node opened, WIP — forward-looking target, not backfilled history (same convention
  as [RM_SAT_017](RM_SAT_017_paul.md)'s original open). First node under a new organizing
  structure: see [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md) for the full
  rationale — roadmap convergences from here on cut across modules by player-facing experience
  threshold rather than one convergence per module. This is Tier 0: the underlying runtime and
  data model (Satchel's foundation/jig/scope runtime, Border's world-border mechanics) working
  end-to-end and hardened. Infrastructure, not gameplay — nothing about this tier makes the mode
  playable yet, since nothing currently calls `BorderAPI.grow()`/`addBorder()` except an admin
  command and a debug trigger (see [Border-Frontier
  Reconciliation](../wiki/frontiermode/architecture/frontier-reconciliation.md)'s "missing caller,
  not a missing capability" finding). That's Tier 1's job, once it exists.

Depends on both halves of Border being real and solid, not just the world-scoped half:
[RM_FRO_010](RM_FRO_010_susan.md) ("Susan" — the hardening batch: dead-code cleanup, mutation
validation, render lifecycle, fixture/compass robustness) and
[RM_FRO_006](RM_FRO_006_sandra.md) (per-player border evaluation). RM_FRO_006 is real,
evidenced, unfinished work, not part of what RM_FRO_008 already verified — see that node's own
log for why it's a genuine prerequisite here rather than padding. It's also the harder of the two
to predict a timeline for: it's graph-actionable off RM_FRO_008 already, but genuinely blocked on
Satchel's [RM_SAT_020](../../roadmap/RM_SAT_020_jerry.md) (`PlayerJig`/`PlayerScope`, not yet
built) — don't expect this node to reach quickly.

Nothing currently depends on this node. Once it reaches, Tier 1 ("Core loop operational") becomes
the next real convergence to open — but not before, per the operational-tiers page's own note
about not inventing convergences before there's real sibling work to gather.

## Required By

*(computed — nothing depends on this yet)*
