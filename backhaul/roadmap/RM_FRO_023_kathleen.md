---
id: RM_FRO_023
uid: RM_FRO
number: 23
kind: convergence
status: WIP
title: 'Tier 2: Guided loop operational'
owner: Arryn
depends_on:
- RM_FRO_029
- RM_FRO_030
- RM_FRO_031
- RM_FRO_032
- RM_FRO_033
- RM_FRO_034
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

**Real Tier 2 siblings now exist — `depends_on` repointed onto them directly.**
[RM_FRO_029](RM_FRO_029_gloria.md) (Guardian Mobs epoch 1),
[RM_FRO_030](RM_FRO_030_janice.md) (Environmental Tells epoch 1),
[RM_FRO_031](RM_FRO_031_joan.md) (Beacons epoch 1),
[RM_FRO_032](RM_FRO_032_elizabeth.md) (Ender-eye-style Tracker epoch 1),
[RM_FRO_033](RM_FRO_033_marilyn.md) (Special Compass epoch 1), and
[RM_FRO_034](RM_FRO_034_virginia.md) (Player-built Warps epoch 1) are boss-discovery.md's own
clue-gradient tools, each already scoped against real, built infrastructure
([RM_FRO_026](RM_FRO_026_dorothy.md)/[RM_FRO_027](RM_FRO_027_janet.md)/[RM_FRO_028](RM_FRO_028_diane.md)) —
exactly the real Tier 2 siblings this node was always meant to fold in once they existed. Listing
[RM_FRO_017](RM_FRO_017_donna.md) ("Donna") here directly, on top of these six, would bypass the
funnel (same convergence-gate correction as every other sibling list in this project — see
[BKHL_002](../tickets/BKHL_002_convergence-gate.md)): all six already depend on
RM_FRO_026/027/028, which themselves depend on RM_FRO_017, so the ancestor edge is redundant once
the real descendants are here.

**Still not actionable, still not close to reaching.** All six are themselves `open`, unbuilt
work, each carrying its own open design questions (see each node's own "Open Questions" section).
**This is a placeholder to real intermediate nodes, not a claim Tier 2 is scoped — and expect it
to be interrupted further:** more nodes will likely land between these six and this convergence as
they get built out, the same way Donna and Susan both grew real intermediate structure over time
rather than staying a flat sibling list. Fold those in here too when they land, per the same
[BKHL_002](../tickets/BKHL_002_convergence-gate.md) discipline — don't let them feed some future
Tier 3 node directly and skip this one.

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

- 2026-09-05: **Corrected, project owner's direct call — the wiring above was wrong.**
  [RM_FRO_025](RM_FRO_025_donna-02.md) ("Donna_02") is Donna epoch's own end-of-epoch maintenance
  container, not a Tier 2 sibling — it was never real Tier 2 scope to fold in, and gating this
  node on it (2026-08-31 entry above) conflated "the previous epoch's cleanup gate" with "this
  epoch's real intermediate work," the two things this node's own standing instruction is
  supposed to keep separate. `depends_on` corrected to
  [RM_FRO_029](RM_FRO_029_gloria.md)/[RM_FRO_030](RM_FRO_030_janice.md)/[RM_FRO_031](RM_FRO_031_joan.md)/[RM_FRO_032](RM_FRO_032_elizabeth.md)/[RM_FRO_033](RM_FRO_033_marilyn.md)/[RM_FRO_034](RM_FRO_034_virginia.md)
  — the actual real Tier 2 siblings (opened 2026-08-31, the same day as the wrong edge, and
  missed) — dropping both RM_FRO_017 (funnels through the six now) and RM_FRO_025 (never a real
  prerequisite of this node's own content). RM_FRO_025 itself stays `resolved` in its own right —
  this correction is about what Kathleen depends on, not a reversal of Donna_02's own closure.

## Required By

<!-- required-by:start -->
*(computed — nothing depends on this yet)*
<!-- required-by:end -->
