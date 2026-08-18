---
id: RM_FRO_017
uid: RM_FRO
number: 17
kind: convergence
status: WIP
title: 'Tier 1: Core loop operational'
owner: Arryn
depends_on:
- RM_FRO_018
- RM_FRO_019
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Tier 1: Core loop operational

- 2026-08-16: **First real intermediate work scoped and folded in, `depends_on` repointed off
  RM_FRO_010 onto the two new nodes directly** (same convergence-gate correction as every sibling
  list in this project — see [BKHL_002](../tickets/BKHL_002_convergence-gate.md)):
  [RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley," boss entity/spawn system) and
  [RM_FRO_019](RM_FRO_019_karen.md) ("Karen," defeat-detection caller into `BorderAPI`), both of
  which already depend on RM_FRO_010/RM_FRO_018 respectively, so listing RM_FRO_010 here directly
  too would bypass the funnel. Design: [Boss](../wiki/frontiermode/architecture/boss.md). Also
  named, cross-graph, in Shirley's own text: Satchel's
  [RM_SAT_021](RM_SAT_021_frank.md) ("Frank," new `MobJig`/`MobScope` kind) — a real prerequisite,
  not a `depends_on` edge. Still not actionable and still not close to reaching — this is the first
  pass at real scope, not the complete one; expect more siblings (loot, discovery has its own tier)
  as Tier 1 gets built out.
- 2026-08-16: **`depends_on` repointed straight to [RM_FRO_010](RM_FRO_010_susan.md) ("Susan").**
  Originally depended on a separate Tier 0 convergence node (RM_FRO_014 "Shirley," then rebuilt as
  RM_FRO_016 "Karen") — both deleted, project owner's call: neither ever carried anything Susan
  didn't already have, so the indirection was cut. Susan now carries the Tier 0 designation
  directly (see her own node). This node sits directly behind Susan on purpose, expecting several
  real intermediate nodes to be scoped and inserted between the two over time — see below.
- 2026-08-16: **Node opened as a placeholder skeleton, project owner's explicit call** — the
  [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md) page originally argued
  against creating Tier 1+ convergences before real sibling work exists to gather ("these
  convergences get created once real sibling work exists, not as empty placeholders now"). That
  guidance is overridden here on purpose: the project owner wants the tier sequence structurally
  present now, not rediscovered later. This node is scaffolding, not a claim that Tier 1 is
  scoped.

**Intermediate nodes not yet defined or inserted.** Per [FrontierMode Operational
Tiers](../wiki/plans/operational-tiers.md#the-tiers), Tier 1 needs real work that doesn't exist as
roadmap nodes yet — at minimum a boss entity/spawn system, and a defeat-detection caller into
`BorderAPI.addBorder()` (already able to accept an arbitrary center, per [Border-Frontier
Reconciliation](../wiki/frontiermode/architecture/frontier-reconciliation.md)'s "missing caller,
not a missing capability" finding — new-caller work, not new-capability work). The single
`depends_on` edge below (this node → RM_FRO_010) is accurate as far as it goes — Tier 1 does need
Susan's six children resolved — but it is not a complete prerequisite list, and the project owner
expects it to grow real intermediate nodes over time, not stay a direct edge forever. **Do not
treat this node as actionable or close to reaching.** When real Tier 1 work gets scoped, fold
those siblings into this node's own `depends_on` the same way Susan absorbed Sandra/Margaret, per
[BKHL_002](../tickets/BKHL_002_convergence-gate.md) — don't let them feed some future Tier 2 node
directly and skip this one.

## Required By

*(computed — nothing depends on this yet)*
