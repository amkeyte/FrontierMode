---
id: RM_FRO_023
uid: RM_FRO
number: 23
kind: convergence
status: WIP
title: 'Tier 2: Guided loop operational'
owner: Arryn
depends_on:
- RM_FRO_017
- RM_FRO_025
created: '2026-08-30'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Tier 2: Guided loop operational

Per [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md#the-tiers): finding a boss
becomes a designed, teach-through-play experience — [Boss
Discovery](../wiki/frontiermode/design/boss-discovery.md)'s clue gradient (ambient density,
environmental tells, beacons, ender-eye-style tracking, compasses) and [Guardian
Mobs](../wiki/frontiermode/design/guardian-mobs.md) both exist — instead of blind wandering, which
is all Tier 1 ("Donna") requires. Also where guardian-mobs.md's two open questions (which level
first introduces them, how they signal a boss that landed in old territory) get resolved.

**Intermediate nodes not yet defined or inserted.** No real Tier 2 work has been scoped yet — this
node holds only the placeholder `depends_on` edge onto [RM_FRO_017](RM_FRO_017_donna.md) ("Donna").
Per operational-tiers.md's own stated general policy, a tier convergence normally only gets created
once real sibling work exists under `RM_FRO` to gather (Tiers 2-3 are explicitly named as not yet
warranting a node on that basis). **Opened early anyway, project owner's explicit call, same
override Donna's own node used for Tier 1** — the tier sequence should be structurally present on
the graph now, not rediscovered later. This node is scaffolding, not a claim that Tier 2 is scoped.
**Do not treat this node as actionable or close to reaching.** When real Tier 2 work gets scoped
(boss-discovery.md, guardian-mobs.md), fold those siblings into this node's own `depends_on` the
same way Donna and Susan absorbed theirs, per
[BKHL_002](../tickets/BKHL_002_convergence-gate.md) and
[BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
— don't let them feed some future Tier 3 node directly and skip this one.

**Also depends on [RM_FRO_025](RM_FRO_025_donna-02.md) ("Donna epoch review/fix") clearing,
project owner's call** — the standard BHRM shape for an epoch's end container: the next planned
node depends on it so nothing from the Donna epoch carries forward unaddressed into Tier 2. Wired
even though RM_FRO_025 is still empty; it will need to actually clear before this node can reach,
same as any other real dependency.

- 2026-08-30: **Node opened as a placeholder skeleton, project owner's explicit call** — mirrors
  [RM_FRO_017](RM_FRO_017_donna.md) ("Donna")'s own precedent-setting entry for Tier 1: opened ahead
  of any real sibling work existing, on the tip of the roadmap, in prep for the next epoch once
  Tier 1 reaches. `depends_on` set to Donna directly (the only real prerequisite that currently
  exists); expect this to gain real intermediate nodes over time the same way Donna did, not stay a
  direct edge forever.

- 2026-08-31: **`depends_on` gains [RM_FRO_025](RM_FRO_025_donna-02.md) ("Donna epoch
  review/fix"), project owner's call.** Standard BHRM shape for an epoch's end container gating
  the next planned node — this node is Donna epoch's next planned node, so it depends on the end
  container clearing rather than carrying anything forward unaddressed. (An earlier attempt wired
  this edge in the opposite direction, on RM_FRO_025 instead — reverted same day, see that node's
  own log.)

## Required By

*(computed — nothing depends on this yet)*
