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

- 2026-09-03: **[FRO_086](../tickets/FRO_086_boss-mobdied-migration.md) lands here** -- split out
  of [SAT_044](../tickets/SAT_044_mob-lifecycle-signals-build.md) (Satchel's Mob Lifecycle Signals
  build), which mixed a Satchel-side signal build with this FrontierMode-side consumer migration
  (Boss's defeat detection + `BOSS_MOB_JIG` onto the new signals). Depends on SAT_044 landing
  first. Normal priority.

- 2026-09-04: **All seven of this container's own items (FRO_075-081) plus FRO_086 are now
  `done`/`closed`, build-verified against real playtest** (see each ticket's own closing log).
  One late item found unparked during a PM sweep: [FRO_084](../tickets/FRO_084_border-vocab-growth-removed.md)
  (border-vocabulary.md's "Aspirational vs. actual" paragraph needs updating now that FRO_076
  actually removed gold-block growth) -- traces to FRO_076, so it belongs here. Tagged and
  parked. It's a pure wiki-drift fix (Architect-owned page, no functional stakes), not a blocker
  on anything.

  [FRO_084](../tickets/FRO_084_border-vocab-growth-removed.md) closed same day (see its own
  log) -- border-vocabulary.md's "Aspirational vs. actual" paragraph rewritten to match current
  source.

- 2026-09-05: **Resolved, project owner's call.** Every item ever logged on this container
  (FRO_075-081, FRO_086, FRO_084) is now `done`/`closed`, each with real playtest evidence on its
  own ticket. Nothing left open here. Per
  [BHRM's own rule](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers) ("flip resolved once
  nothing currently gating a dependent remains open"), `status` flips to `resolved`. This clears
  [RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen")'s first of its two `depends_on` edges
  (RM_FRO_017 already `reached`) -- Kathleen's own text is explicit that this alone doesn't make
  it actionable; real Tier 2 discovery-system work still needs scoping before that node moves.

- 2026-09-05: **Reopened, project owner's direct call — the 2026-09-05 resolve above was
  premature.** [RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen") never actually depended on this
  container — that edge was itself a misuse, corrected on Kathleen's own log the same day (this
  container was conflated with a real Tier 2 sibling; it's Donna epoch's own end-of-epoch cleanup
  gate, unrelated in kind). So "resolved" here was answering a dependency that shouldn't have
  existed in the first place, not a real clearing event. `status` reopens to `open` -- this
  container is expected to keep collecting real Donna-epoch-adjacent maintenance work as Tier 2
  gets built out and more of it turns up, the normal shape for an end-of-epoch container, not an
  exception. Per [BHRM's own reopening-is-expected-practice guidance](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers),
  this is a correction on real evidence, not a reversal to second-guess later. FRO_075-081/086/084
  stay closed -- their own work is real and done; only this container's own status is affected.

## Required By

*(computed — nothing depends on this yet)*
