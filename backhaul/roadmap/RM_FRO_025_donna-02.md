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

**Doesn't depend on anything but [RM_FRO_024](RM_FRO_024_donna-01.md) — the wiring runs the
other direction.** [BHRM's corollary](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
about not wiring a container onto the next convergence automatically is a per-case judgment call,
not a blanket rule: [RM_FRO_021](RM_FRO_021_susan-02.md) ("Susan_02") stayed unwired in both
directions because that was a deliberate call to punt its low-value backlog rather than let it
gate anything. This epoch's call is the standard BHRM shape instead:
[RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen," Tier 2's convergence) now depends on *this*
container clearing, project owner's call — see Kathleen's own log.

- 2026-08-30: Node opened, alongside [RM_FRO_024](RM_FRO_024_donna-01.md) ("Donna epoch
  maintenance 1") and Donna's own `reached` flip — see [RM_FRO_017](RM_FRO_017_donna.md)'s own
  2026-08-30 log entry. Nothing held yet.

- 2026-08-31: **Correction, same day** -- the edge from the previous entry was wired backwards
  (this node depending on Kathleen). Reverted: this node's own `depends_on` stays just
  RM_FRO_024. The real edge is [RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen") depending on
  *this* container clearing, added there instead -- see its own log.

- 2026-09-03: **Seven items land -- [FRO_075](../tickets/FRO_075_bootstrap-ownership.md) through
  [FRO_081](../tickets/FRO_081_bossmodule-facet-refactor.md),** split from
  [FRO_074](../tickets/FRO_074_cartographer-findings.md) (Cartographer's diagramming-pass
  findings, Architect rulings filed on most). Project owner's call: parked here despite this
  node's edge onto [RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen") -- accepted consequence,
  not an oversight.
  - [FRO_075](../tickets/FRO_075_bootstrap-ownership.md) -- move level-bootstrap boss creation
    from `BorderModule` into `BossModule`. Architect ruling filed, ready for Lead Dev. Normal
    priority.
  - [FRO_076](../tickets/FRO_076_gold-block-growth-removal.md) -- remove gold-block-placement
    path growth entirely, superseded by boss-defeat growth. Stated decision, ready for Lead Dev.
    Normal priority.
  - [FRO_077](../tickets/FRO_077_pregen-sibling-access.md) -- `BorderPregenFixture` switches to
    direct sibling access instead of the `BorderAPI.CRUD()` facade. Architect ruling filed,
    ready for Lead Dev. Low priority.
  - [FRO_078](../tickets/FRO_078_bordermath-to-api.md) -- route `BorderMath`'s geometry ops
    through `BorderAPI`'s surface for facade consistency. Stated decision, ready for Lead Dev.
    Normal priority.
  - [FRO_079](../tickets/FRO_079_debug-create-deprecation.md) -- deprecate
    `BorderCommandHandler.debugCreate()` (`/border debug create`) and its escape-hatch
    accessors. Stated decision, ready for Lead Dev. Low priority.
  - [FRO_080](../tickets/FRO_080_grow-level-deprecation.md) -- deprecate `BorderAPI.grow(Level)`
    in favor of the explicit-center overload; `pathGrow` gets a new required command argument
    for its center (project owner's call, resolved). **Blocked on FRO_075 and FRO_076 landing
    first** -- not yet actionable. Normal priority.
  - [FRO_081](../tickets/FRO_081_bossmodule-facet-refactor.md) -- extract `BossModule`'s
    grab-bag of concerns into `BossCrudFacet`/`BossRulesFacet`/`BossInfoFacet`, mirroring
    `BordersFixture`. Architect ruling filed (facet design). **Blocked on FRO_075 landing
    first** -- not yet actionable. Normal priority.

  [FRO_074](../tickets/FRO_074_cartographer-findings.md) itself stays open as the running-log
  umbrella, same shape as [FRO_054](../tickets/FRO_054_mutation-data-security.md)'s own pattern.
  Finding 2 (`BorderPlayerBundle` facet discipline) needed no ticket -- Architect's ruling was
  "no action, keep as-is," closed out on FRO_074's own log instead.

## Required By

*(computed — nothing depends on this yet)*
